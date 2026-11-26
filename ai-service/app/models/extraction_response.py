from pydantic import BaseModel, Field


class ExtractionResponse(BaseModel):
    """Normalized plain text and document metadata returned from CV extraction."""

    extracted_text: str
    char_count: int = Field(..., ge=0)
    page_count: int = Field(..., ge=0)
    format: str
    extractor: str
    empty_or_scanned: bool = False
