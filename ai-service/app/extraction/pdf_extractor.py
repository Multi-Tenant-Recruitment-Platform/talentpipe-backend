import pymupdf
import pymupdf4llm

from app.exceptions import ExtractionError
from app.extraction.extractor import DocumentExtractor
from app.extraction.text_normalizer import MIN_READABLE_CHARS, CvTextNormalizer
from app.models.extraction_response import ExtractionResponse


class PdfLayoutExtractor(DocumentExtractor):
    """Extracts layout-sorted text from Canva, LaTeX, and standard PDFs."""

    def __init__(self, normalizer: CvTextNormalizer):
        self._normalizer = normalizer

    def extract(self, data: bytes) -> ExtractionResponse:
        try:
            with pymupdf.open(stream=data, filetype="pdf") as doc:
                page_count = len(doc)
                raw_markdown = pymupdf4llm.to_markdown(doc)
        except Exception as exc:
            raise ExtractionError(f"Failed to parse PDF document: {exc}") from exc

        cleaned = self._normalizer.normalize(raw_markdown)
        char_count = len(cleaned)
        return ExtractionResponse(
            extracted_text=cleaned,
            char_count=char_count,
            page_count=page_count,
            format="PDF",
            extractor="pymupdf4llm",
            empty_or_scanned=char_count < MIN_READABLE_CHARS,
        )
