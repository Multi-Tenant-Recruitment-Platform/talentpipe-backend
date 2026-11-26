from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from google.genai import errors as genai_errors

from app.exceptions import LlmEvaluationError
from app.llm.gemini_provider import GeminiProvider
from app.models.screening_response import LlmRawEvaluation


@pytest.mark.asyncio
async def test_evaluate_without_api_key_raises_error():
    provider = GeminiProvider(api_key="", model_name="gemini-2.5-flash")

    with pytest.raises(LlmEvaluationError, match="GEMINI_API_KEY"):
        await provider.evaluate("system instructions", "user payload")


@pytest.mark.asyncio
async def test_evaluate_passes_system_instruction_and_json_schema():
    provider = GeminiProvider(api_key="test-key", model_name="gemini-2.5-flash")
    mock_generate = AsyncMock(return_value=SimpleNamespace(text='{"summary": "ok"}'))
    provider._client = SimpleNamespace(
        aio=SimpleNamespace(
            models=SimpleNamespace(generate_content=mock_generate),
            aclose=AsyncMock(),
        )
    )

    result = await provider.evaluate("sys-rules", "<candidate_cv>cv</candidate_cv>")

    assert result == '{"summary": "ok"}'
    call_kwargs = mock_generate.call_args.kwargs
    assert call_kwargs["model"] == "gemini-2.5-flash"
    assert call_kwargs["contents"] == "<candidate_cv>cv</candidate_cv>"
    config = call_kwargs["config"]
    assert config.system_instruction == "sys-rules"
    assert config.temperature == 0.0
    assert config.response_mime_type == "application/json"
    assert config.response_schema is LlmRawEvaluation
    assert provider.model_version() == "gemini-2.5-flash"

    await provider.aclose()
    provider._client.aio.aclose.assert_awaited_once()


@pytest.mark.asyncio
async def test_evaluate_wraps_sdk_api_error_as_llm_evaluation_error():
    provider = GeminiProvider(api_key="test-key", model_name="gemini-2.5-flash")
    api_error = genai_errors.APIError(429, {"error": {"message": "Quota exceeded"}}, MagicMock())
    provider._client = SimpleNamespace(
        aio=SimpleNamespace(
            models=SimpleNamespace(generate_content=AsyncMock(side_effect=api_error)),
            aclose=AsyncMock(),
        )
    )

    with pytest.raises(LlmEvaluationError, match="429"):
        await provider.evaluate("sys", "user")


@pytest.mark.asyncio
async def test_evaluate_empty_response_raises_llm_evaluation_error():
    provider = GeminiProvider(api_key="test-key", model_name="gemini-2.5-flash")
    provider._client = SimpleNamespace(
        aio=SimpleNamespace(
            models=SimpleNamespace(generate_content=AsyncMock(return_value=SimpleNamespace(text=""))),
            aclose=AsyncMock(),
        )
    )

    with pytest.raises(LlmEvaluationError, match="empty"):
        await provider.evaluate("sys", "user")
