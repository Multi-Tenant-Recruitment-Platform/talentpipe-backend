import asyncio
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException

from app.dependencies import get_screening_service
from app.exceptions import LlmEvaluationError, LlmResponseParseError
from app.models.screening_request import ScreeningRequest
from app.models.screening_response import ScreeningResponse
from app.services.screening_service import AiScreeningService

router = APIRouter()
logger = logging.getLogger(__name__)

MAX_ATTEMPTS = 3
_RETRY_BACKOFF_SECONDS = 0.25


@router.post("/screen", response_model=ScreeningResponse)
async def screen_candidate(
    request: ScreeningRequest,
    service: Annotated[AiScreeningService, Depends(get_screening_service)],
) -> ScreeningResponse:
    """Evaluate a candidate CV, retrying transient LLM or parse errors before 502."""
    last_error: Exception | None = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            return await service.screen(request)
        except (LlmEvaluationError, LlmResponseParseError) as exc:
            last_error = exc
            logger.warning(
                "Screening attempt %d/%d failed (%s)",
                attempt,
                MAX_ATTEMPTS,
                type(exc).__name__,
            )
            if attempt < MAX_ATTEMPTS:
                await asyncio.sleep(_RETRY_BACKOFF_SECONDS * attempt)

    logger.error("All %d screening attempts failed", MAX_ATTEMPTS, exc_info=last_error)
    raise HTTPException(
        status_code=502,
        detail={"error": "SCORING_FAILED", "message": "LLM evaluation failed after retries."},
    )
