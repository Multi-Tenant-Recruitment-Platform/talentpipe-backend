from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from app.config import get_settings
from app.dependencies import get_extractor_factory
from app.exceptions import ExtractionError, UnsupportedDocumentFormatError
from app.extraction.extractor import DocumentExtractorFactory
from app.models.extraction_response import ExtractionResponse

router = APIRouter()


@router.post("/extract", response_model=ExtractionResponse)
async def extract_cv_text(
    file: Annotated[UploadFile, File(...)],
    factory: Annotated[DocumentExtractorFactory, Depends(get_extractor_factory)],
) -> ExtractionResponse:
    """Extract normalized plain text from uploaded Canva/LaTeX/standard PDF or DOCX files."""
    max_bytes = get_settings().max_cv_upload_bytes
    content = await file.read(max_bytes + 1)
    if len(content) > max_bytes:
        raise HTTPException(status_code=413, detail="Uploaded CV exceeds 5 MB limit")

    try:
        return factory.extract(content)
    except UnsupportedDocumentFormatError as exc:
        raise HTTPException(status_code=415, detail=str(exc)) from exc
    except ExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
