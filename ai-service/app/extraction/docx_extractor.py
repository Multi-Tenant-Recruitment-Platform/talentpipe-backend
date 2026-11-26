import io
import zipfile
from collections.abc import Iterable

import docx
from docx.document import Document
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph

from app.exceptions import ExtractionError
from app.extraction.extractor import DocumentExtractor
from app.extraction.text_normalizer import MIN_READABLE_CHARS, CvTextNormalizer
from app.models.extraction_response import ExtractionResponse


class DocxExtractor(DocumentExtractor):
    """Extracts headers, body paragraphs, and tables from .docx resumes in XML order."""

    def __init__(
        self,
        normalizer: CvTextNormalizer,
        max_uncompressed_bytes: int = 25 * 1024 * 1024,
    ):
        self._normalizer = normalizer
        self._max_uncompressed_bytes = max_uncompressed_bytes

    def extract(self, data: bytes) -> ExtractionResponse:
        self._verify_archive_limits(data)
        try:
            document = docx.Document(io.BytesIO(data))
        except Exception as exc:
            raise ExtractionError(f"Failed to parse DOCX document: {exc}") from exc

        segments = list(self._collect_segments(document))
        cleaned = self._normalizer.normalize("\n".join(segments))
        char_count = len(cleaned)
        return ExtractionResponse(
            extracted_text=cleaned,
            char_count=char_count,
            page_count=max(len(document.sections), 1),
            format="DOCX",
            extractor="python-docx",
            empty_or_scanned=char_count < MIN_READABLE_CHARS,
        )

    def _verify_archive_limits(self, data: bytes) -> None:
        """Prevent zip-bomb inflation attacks before python-docx reads XML entries."""
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                total_size = sum(info.file_size for info in zf.infolist())
        except zipfile.BadZipFile as exc:
            raise ExtractionError("Corrupt DOCX archive") from exc

        if total_size > self._max_uncompressed_bytes:
            raise ExtractionError(
                f"DOCX uncompressed size ({total_size} bytes) exceeds limit"
            )

    def _collect_segments(self, document: Document) -> Iterable[str]:
        seen_headers: set[str] = set()
        for section in document.sections:
            header_lines = self._extract_container_lines(
                section.header.paragraphs,
                section.header.tables,
            )
            for part_text in header_lines:
                if part_text not in seen_headers:
                    seen_headers.add(part_text)
                    yield part_text

        for child in document.element.body.iterchildren():
            if isinstance(child, CT_P):
                text = Paragraph(child, document).text.strip()
                if text:
                    yield text
            elif isinstance(child, CT_Tbl):
                yield from self._extract_table_lines(Table(child, document))

    def _extract_container_lines(
        self, paragraphs: list[Paragraph], tables: list[Table]
    ) -> Iterable[str]:
        for paragraph in paragraphs:
            text = paragraph.text.strip()
            if text:
                yield text
        for table in tables:
            yield from self._extract_table_lines(table)

    def _extract_table_lines(self, table: Table) -> Iterable[str]:
        for row in table.rows:
            cells = [cell.text.strip() for cell in row.cells if cell.text.strip()]
            if cells:
                yield " | ".join(cells)
