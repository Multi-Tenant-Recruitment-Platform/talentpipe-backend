from unittest.mock import AsyncMock, MagicMock

import pytest
from httpx import ASGITransport, AsyncClient

from app.dependencies import get_extractor_factory, get_screening_service
from app.extraction.extractor import DocumentExtractorFactory
from app.main import app
from app.services.screening_service import AiScreeningService


@pytest.fixture
def mock_screening_service():
    """Provides a mocked AiScreeningService with zero real Gemini API calls."""
    return AsyncMock(spec=AiScreeningService)


@pytest.fixture
def mock_extractor_factory():
    """Provides a mocked DocumentExtractorFactory for router HTTP tests."""
    return MagicMock(spec=DocumentExtractorFactory)


@pytest.fixture
async def client(mock_screening_service, mock_extractor_factory):
    """FastAPI AsyncClient with mocked dependencies — no API key needed."""
    app.dependency_overrides[get_screening_service] = lambda: mock_screening_service
    app.dependency_overrides[get_extractor_factory] = lambda: mock_extractor_factory
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as async_client:
        yield async_client
    app.dependency_overrides.clear()
