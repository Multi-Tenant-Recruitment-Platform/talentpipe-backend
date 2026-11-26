import json
import re

from pydantic import ValidationError

from app.exceptions import LlmResponseParseError
from app.llm.provider import LlmProvider
from app.models.screening_request import CriterionWeight, ScreeningRequest
from app.models.screening_response import CriterionScore, LlmRawEvaluation, ScreeningResponse
from app.safety.injection_guard import PromptInjectionGuard
from app.safety.pii_redactor import CvPiiRedactor
from app.safety.screening_cache import ScreeningCache
from app.services.prompt_builder import PromptBuilder

PROMPT_VERSION = "v1"
_CODE_FENCE_RE = re.compile(r"^\s*```(?:json)?\s*|\s*```\s*$", re.IGNORECASE)


class AiScreeningService:
    """Facade orchestrating PII redaction, injection guard, caching, LLM scoring, and validation."""

    def __init__(
        self,
        llm_provider: LlmProvider,
        pii_redactor: CvPiiRedactor | None = None,
        injection_guard: PromptInjectionGuard | None = None,
        cache: ScreeningCache | None = None,
    ):
        self._llm = llm_provider
        self._pii_redactor = pii_redactor or CvPiiRedactor()
        self._injection_guard = injection_guard or PromptInjectionGuard()
        self._cache = cache or ScreeningCache()
        self._prompt_builder = PromptBuilder()

    async def screen(self, request: ScreeningRequest) -> ScreeningResponse:
        """Evaluate candidate CV against job requirements with blind-mode safety and caching."""
        prepared_cv = self._prepare_cv_text(request.cv_text, request.blind_mode)
        prepared_jd = self._injection_guard.sanitize_job_description(request.job_description)
        cache_key = ScreeningCache.build_key(
            cv_text=prepared_cv,
            job_description=prepared_jd,
            criteria_weights=request.criteria_weights,
            blind_mode=request.blind_mode,
            model_version=self._llm.model_version(),
            prompt_version=PROMPT_VERSION,
        )
        if not request.bypass_cache:
            cached_result = self._cache.get(cache_key)
            if cached_result is not None:
                return cached_result

        result = await self._evaluate_uncached(prepared_cv, prepared_jd, request.criteria_weights)
        self._cache.put(cache_key, result)
        return result

    async def aclose(self) -> None:
        """Delegate async cleanup to the configured LLM provider."""
        await self._llm.aclose()

    def _prepare_cv_text(self, cv_text: str, blind_mode: bool) -> str:
        redacted = self._pii_redactor.redact(cv_text, blind_mode=blind_mode)
        return self._injection_guard.sanitize_cv(redacted)

    async def _evaluate_uncached(
        self,
        cv_text: str,
        job_description: str,
        criteria_weights: list[CriterionWeight] | None,
    ) -> ScreeningResponse:
        parts = self._prompt_builder.build(
            cv_text=cv_text,
            job_description=job_description,
            criteria_weights=criteria_weights,
        )
        raw_response = await self._llm.evaluate(parts.system_prompt, parts.user_prompt)
        parsed = self._parse_evaluation(raw_response)
        overall_score = self._compute_weighted_score(parsed.criterion_scores, criteria_weights)
        return ScreeningResponse(
            overall_score=overall_score,
            summary=parsed.summary,
            strengths=parsed.strengths,
            gaps=parsed.gaps,
            criterion_scores=parsed.criterion_scores,
            model_version=self._llm.model_version(),
            prompt_version=PROMPT_VERSION,
            cached=False,
        )

    def _parse_evaluation(self, raw_response: str) -> LlmRawEvaluation:
        cleaned = _CODE_FENCE_RE.sub("", raw_response).strip()
        try:
            return LlmRawEvaluation.model_validate_json(cleaned)
        except (json.JSONDecodeError, ValidationError, ValueError, TypeError) as exc:
            raise LlmResponseParseError(f"Invalid LLM JSON payload: {exc}") from exc

    def _compute_weighted_score(
        self,
        scores: list[CriterionScore],
        weights: list[CriterionWeight] | None,
    ) -> int:
        if not weights:
            return round(sum(s.score for s in scores) / len(scores))

        score_by_name = {s.name.casefold().strip(): s.score for s in scores}
        weighted_sum = 0.0
        total_weight = 0.0
        for criterion in weights:
            key = criterion.name.casefold().strip()
            if key not in score_by_name:
                raise LlmResponseParseError(
                    f"Missing weighted criterion in LLM output: '{criterion.name}'"
                )
            weighted_sum += score_by_name[key] * criterion.weight
            total_weight += criterion.weight

        return round(weighted_sum / max(total_weight, 1e-6))
