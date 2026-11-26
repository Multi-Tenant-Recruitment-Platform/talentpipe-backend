from abc import ABC, abstractmethod

from app.exceptions import UnsupportedDocumentFormatError
from app.models.extraction_response import ExtractionResponse

_PDF_MAGIC = b"%PDF"
_ZIP_MAGIC = b"PK"


class DocumentExtractor(ABC):
    """Strategy interface for binary CV format extractors."""

    @abstractmethod
    def extract(self, data: bytes) -> ExtractionResponse:
        """Extract normalized text and metadata from binary document bytes."""


class DocumentExtractorFactory:
    """Dispatches validated binary CV payloads to the appropriate format extractor."""

    def __init__(self, pdf_extractor: DocumentExtractor, docx_extractor: DocumentExtractor):
        self._extractors: dict[str, DocumentExtractor] = {
            "PDF": pdf_extractor,
            "DOCX": docx_extractor,
        }

    def extract(self, data: bytes) -> ExtractionResponse:
        """Inspect magic bytes and delegate to the matching Strategy."""
        doc_format = self._detect_format(data)
        return self._extractors[doc_format].extract(data)

    def _detect_format(self, data: bytes) -> str:
        if data.startswith(_PDF_MAGIC):
            return "PDF"
        if data.startswith(_ZIP_MAGIC):
            return "DOCX"
        raise UnsupportedDocumentFormatError("Unsupported file format; expected PDF or DOCX")
