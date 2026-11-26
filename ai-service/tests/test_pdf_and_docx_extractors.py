import io
import zipfile

import docx
import pymupdf
import pytest

from app.exceptions import ExtractionError, UnsupportedDocumentFormatError
from app.extraction.docx_extractor import DocxExtractor
from app.extraction.extractor import DocumentExtractorFactory
from app.extraction.pdf_extractor import PdfLayoutExtractor
from app.extraction.text_normalizer import CvTextNormalizer


def _build_two_column_pdf_bytes() -> bytes:
    """Create a 2-column Canva/LaTeX-style PDF rendered in reverse stream order."""
    doc = pymupdf.open()
    page = doc.new_page(width=595, height=842)
    # Draw RIGHT column first in raw PDF stream, then LEFT column so a naive
    # stream reader mixes columns horizontally while pymupdf4llm orders by column.
    right_box = pymupdf.Rect(240, 70, 560, 220)
    page.insert_htmlbox(
        right_box,
        "<h2>EXPERIENCE SUMMARY</h2>"
        "<p>Senior Software Engineer at Acme</p>"
        "<p>Built microservices in Of\ufb01ce and \ufb02exible APIs.</p>",
    )
    left_box = pymupdf.Rect(40, 70, 200, 220)
    page.insert_htmlbox(
        left_box,
        "<h3>SKILLS</h3><p>Python FastAPI</p><p>Java Spring Boot</p>",
    )
    return doc.tobytes()


def _build_blank_pdf_bytes() -> bytes:
    doc = pymupdf.open()
    doc.new_page()
    return doc.tobytes()


def _build_docx_with_header_and_table() -> bytes:
    document = docx.Document()
    header = document.sections[0].header
    header.paragraphs[0].text = "Kasun Silva | Senior Java Engineer"
    document.add_paragraph(
        "Profile Summary: Experienced backend engineer delivering scalable APIs."
    )
    table = document.add_table(rows=2, cols=2)
    table.cell(0, 0).text = "Core Stack"
    table.cell(0, 1).text = "Java 17, Spring Boot, PostgreSQL"
    table.cell(1, 0).text = "Cloud"
    table.cell(1, 1).text = "Docker, AWS S3"
    buf = io.BytesIO()
    document.save(buf)
    return buf.getvalue()


def test_normalizer_repairs_ligatures_and_strips_pua_icons():
    normalizer = CvTextNormalizer()

    cleaned = normalizer.normalize("Of\ufb01ce \ufb02ow \ufb00ort \uf0e0\u200b   Skills")

    assert "Office flow ffort" in cleaned
    assert "\uf0e0" not in cleaned
    assert "\u200b" not in cleaned


def test_pdf_extractor_keeps_columns_separate_and_normalizes_ligatures():
    extractor = PdfLayoutExtractor(CvTextNormalizer())

    result = extractor.extract(_build_two_column_pdf_bytes())

    assert result.format == "PDF"
    assert result.page_count == 1
    assert "Office" in result.extracted_text
    assert "flexible" in result.extracted_text
    # Left column must stay grouped before right column experience block
    assert result.extracted_text.index("Java Spring Boot") < result.extracted_text.index(
        "Senior Software Engineer at Acme"
    )
    assert result.empty_or_scanned is False


def test_pdf_extractor_flags_blank_or_scanned_pdf():
    extractor = PdfLayoutExtractor(CvTextNormalizer())

    result = extractor.extract(_build_blank_pdf_bytes())

    assert result.empty_or_scanned is True
    assert result.char_count < 50


def test_docx_extractor_reads_headers_paragraphs_and_tables():
    extractor = DocxExtractor(CvTextNormalizer(), max_uncompressed_bytes=5_000_000)

    result = extractor.extract(_build_docx_with_header_and_table())

    assert result.format == "DOCX"
    assert "Kasun Silva" in result.extracted_text
    assert "Profile Summary" in result.extracted_text
    assert "Java 17, Spring Boot, PostgreSQL" in result.extracted_text


def test_docx_extractor_blocks_oversized_uncompressed_archive():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("word/document.xml", "A" * 10_000)
    extractor = DocxExtractor(CvTextNormalizer(), max_uncompressed_bytes=1024)

    with pytest.raises(ExtractionError, match="uncompressed"):
        extractor.extract(buf.getvalue())


def test_factory_dispatches_by_magic_bytes_and_rejects_unknown():
    factory = DocumentExtractorFactory(
        pdf_extractor=PdfLayoutExtractor(CvTextNormalizer()),
        docx_extractor=DocxExtractor(CvTextNormalizer()),
    )

    assert factory.extract(_build_two_column_pdf_bytes()).format == "PDF"
    assert factory.extract(_build_docx_with_header_and_table()).format == "DOCX"

    with pytest.raises(UnsupportedDocumentFormatError):
        factory.extract(b"not a pdf or docx file")
