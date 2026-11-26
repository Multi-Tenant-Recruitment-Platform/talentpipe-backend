from google import genai
from google.genai import errors as genai_errors
from google.genai import types

from app.exceptions import LlmEvaluationError
from app.llm.provider import LlmProvider
from app.models.screening_response import LlmRawEvaluation


class GeminiProvider(LlmProvider):
    """Adapter wrapping google-genai async SDK with temperature 0.0 JSON schema output."""

    def __init__(self, api_key: str, model_name: str = "gemini-2.5-flash"):
        self._model_name = model_name
        # Defer client creation when key is empty so startup & unit tests do not crash.
        self._client = genai.Client(api_key=api_key) if api_key else None

    async def evaluate(self, system_prompt: str, user_prompt: str) -> str:
        if self._client is None:
            raise LlmEvaluationError("GEMINI_API_KEY is not configured")
        try:
            response = await self._client.aio.models.generate_content(
                model=self._model_name,
                contents=user_prompt,
                config=self._build_config(system_prompt),
            )
        except genai_errors.APIError as exc:
            raise LlmEvaluationError(f"Gemini API error ({exc.code}): {exc.message}") from exc
        except (TimeoutError, OSError, ValueError) as exc:
            raise LlmEvaluationError(f"Gemini transport/response failure: {exc}") from exc

        if not response.text:
            raise LlmEvaluationError("Gemini returned empty response body")
        return response.text

    def _build_config(self, system_prompt: str) -> types.GenerateContentConfig:
        return types.GenerateContentConfig(
            system_instruction=system_prompt,
            temperature=0.0,
            response_mime_type="application/json",
            response_schema=LlmRawEvaluation,
        )

    def model_version(self) -> str:
        return self._model_name

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aio.aclose()
