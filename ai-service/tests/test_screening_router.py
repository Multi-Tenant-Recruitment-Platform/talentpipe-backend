from unittest.mock import AsyncMock

import pytest

from app.exceptions import LlmEvaluationError, LlmResponseParseError
from app.models.screening_response import CriterionScore, ScreeningResponse

VALID_PAYLOAD = {
    "cv_text": "Senior Backend Engineer with 6 years Java, Spring Boot, PostgreSQL, Docker. " * 2,
    "job_description": "Looking for Senior Java & Spring Boot Engineer with PostgreSQL.",
    "blind_mode": True,
}

SAMPLE_RESPONSE = ScreeningResponse(
    overall_score=84,
    summary="Strong Java/Spring Boot match with PostgreSQL experience.",
    strengths=["Spring Boot 3", "PostgreSQL"],
    gaps=["No Kubernetes mentioned"],
    criterion_scores=[CriterionScore(name="Backend", score=84, rationale="6 yrs Java")],
    model_version="gemini-2.5-flash",
    prompt_version="v1",
)


@pytest.mark.asyncio
async def test_health_check_returns_healthy(client):
    response = await client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "healthy"
    assert body["service"] == "ai-service"


@pytest.mark.asyncio
async def test_screen_returns_200_on_valid_request(client, mock_screening_service):
    mock_screening_service.screen.return_value = SAMPLE_RESPONSE

    response = await client.post("/api/v1/screen", json=VALID_PAYLOAD)

    assert response.status_code == 200
    assert response.json()["overall_score"] == 84
    assert response.json()["prompt_version"] == "v1"


@pytest.mark.asyncio
async def test_screen_returns_422_for_short_cv_or_duplicate_criteria(client):
    short_resp = await client.post("/api/v1/screen", json={**VALID_PAYLOAD, "cv_text": "too short"})
    assert short_resp.status_code == 422

    dup_resp = await client.post(
        "/api/v1/screen",
        json={
            **VALID_PAYLOAD,
            "criteria_weights": [
                {"name": "Java", "weight": 0.5},
                {"name": "java", "weight": 0.5},
            ],
        },
    )
    assert dup_resp.status_code == 422


@pytest.mark.asyncio
async def test_screen_recovers_on_retry_after_transient_parse_error(
    client,
    mock_screening_service,
    monkeypatch,
):
    monkeypatch.setattr("app.api.screening_router.asyncio.sleep", AsyncMock())
    mock_screening_service.screen.side_effect = [
        LlmResponseParseError("Malformed JSON"),
        SAMPLE_RESPONSE,
    ]

    response = await client.post("/api/v1/screen", json=VALID_PAYLOAD)

    assert response.status_code == 200
    assert mock_screening_service.screen.await_count == 2


@pytest.mark.asyncio
async def test_screen_returns_502_when_retries_exhausted(
    client,
    mock_screening_service,
    monkeypatch,
):
    monkeypatch.setattr("app.api.screening_router.asyncio.sleep", AsyncMock())
    mock_screening_service.screen.side_effect = LlmEvaluationError("Gemini timeout")

    response = await client.post("/api/v1/screen", json=VALID_PAYLOAD)

    assert response.status_code == 502
    assert response.json()["detail"]["error"] == "SCORING_FAILED"
    assert mock_screening_service.screen.await_count == 3
