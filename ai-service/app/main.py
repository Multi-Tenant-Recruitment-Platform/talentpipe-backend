from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.extraction_router import router as extraction_router
from app.api.screening_router import router as screening_router
from app.config import get_settings
from app.dependencies import get_screening_service


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """Ensure async provider transports close cleanly when the container stops."""
    yield
    await get_screening_service().aclose()


app = FastAPI(
    title="TalentPipe AI Service",
    version="1.0.0",
    docs_url=None,
    redoc_url=None,
    lifespan=lifespan,
)
app.include_router(screening_router, prefix="/api/v1")
app.include_router(extraction_router, prefix="/api/v1")


@app.get("/health")
async def health_check() -> dict[str, str]:
    """Internal readiness probe for Docker Compose healthcheck."""
    return {
        "status": "healthy",
        "service": "ai-service",
        "model": get_settings().gemini_model_name,
    }
