import json

import pytest

from app.exceptions import LlmResponseParseError
from app.llm.provider import LlmProvider
from app.models.screening_request import CriterionWeight, ScreeningRequest
from app.services.screening_service import AiScreeningService


class StubLlmProvider(LlmProvider):
    def __init__(self, payload: str, model: str = "gemini-2.5-flash"):
        self._payload = payload
        self._model = model

    async def evaluate(self, system_prompt: str, user_prompt: str) -> str:
        return self._payload

    def model_version(self) -> str:
        return self._model

    async def aclose(self) -> None:
        return None


@pytest.mark.asyncio
async def test_screen_computes_case_insensitive_weighted_average():
    llm_json = json.dumps(
        {
            "summary": "Strong backend engineer with production Java experience.",
            "strengths": ["Java 17 & Spring Boot", "PostgreSQL indexing"],
            "gaps": ["Limited Kubernetes telemetry"],
            "criterion_scores": [
                {"name": "backend", "score": 90, "rationale": "6 years Java/Spring Boot"},
                {"name": "PostgreSQL", "score": 70, "rationale": "Used Flyway & Postgres"},
            ],
        }
    )
    service = AiScreeningService(StubLlmProvider(llm_json))
    request = ScreeningRequest(
        cv_text="A" * 60,
        job_description="Senior Java Backend Engineer role",
        criteria_weights=[
            CriterionWeight(name="Backend", weight=0.75),
            CriterionWeight(name="PostgreSQL", weight=0.25),
        ],
    )

    result = await service.screen(request)

    assert result.overall_score == 85  # 90*0.75 + 70*0.25 = 85.0
    assert result.model_version == "gemini-2.5-flash"
    assert result.cached is False
    assert len(result.criterion_scores) == 2


@pytest.mark.asyncio
async def test_screen_strips_markdown_fence_and_averages_unweighted():
    fenced_json = """```json
    {
      "summary": "Solid generalist developer matching core requirements.",
      "strengths": ["Clean Python"],
      "gaps": ["No AWS listed"],
      "criterion_scores": [
        {"name": "Technical Skills", "score": 80, "rationale": "FastAPI projects"},
        {"name": "Experience", "score": 60, "rationale": "2 years industry work"}
      ]
    }
    ```"""
    service = AiScreeningService(StubLlmProvider(fenced_json))
    request = ScreeningRequest(
        cv_text="B" * 60,
        job_description="Software Engineer role description",
    )

    result = await service.screen(request)

    assert result.overall_score == 70


@pytest.mark.asyncio
async def test_screen_raises_parse_error_on_invalid_json():
    service = AiScreeningService(StubLlmProvider("not valid json"))
    request = ScreeningRequest(cv_text="C" * 60, job_description="Backend Engineer position")

    with pytest.raises(LlmResponseParseError):
        await service.screen(request)


@pytest.mark.asyncio
async def test_screen_raises_parse_error_when_weighted_criterion_missing():
    llm_json = json.dumps(
        {
            "summary": "Partial response missing one requested criterion.",
            "strengths": ["Python"],
            "gaps": [],
            "criterion_scores": [
                {"name": "Unrelated", "score": 50, "rationale": "Wrong criterion name"},
            ],
        }
    )
    service = AiScreeningService(StubLlmProvider(llm_json))
    request = ScreeningRequest(
        cv_text="D" * 60,
        job_description="Backend Engineer position",
        criteria_weights=[CriterionWeight(name="Java", weight=1.0)],
    )

    with pytest.raises(LlmResponseParseError, match="Missing weighted criterion"):
        await service.screen(request)


@pytest.mark.asyncio
async def test_screen_raises_parse_error_on_out_of_range_score():
    llm_json = json.dumps(
        {
            "summary": "Invalid score over 100 boundary.",
            "strengths": [],
            "gaps": [],
            "criterion_scores": [
                {"name": "Fit", "score": 150, "rationale": "Out of bounds"},
            ],
        }
    )
    service = AiScreeningService(StubLlmProvider(llm_json))
    request = ScreeningRequest(cv_text="E" * 60, job_description="Backend Engineer position")

    with pytest.raises(LlmResponseParseError):
        await service.screen(request)
