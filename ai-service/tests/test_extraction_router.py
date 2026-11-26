from unittest.mock import MagicMock

import pytest

from app.exceptions import ExtractionError, UnsupportedDocumentFormatError
from app.models.extraction_response import ExtractionResponse


@pytest.mark.asyncio
async def test_extract_endpoint_returns_extracted_text(client, mock_extractor_factory):
    mock_extractor_factory.extract.return_value = ExtractionResponse(
        extracted_text="Senior Java Developer with Spring Boot and PostgreSQL expertise.",
        char_count=62,
        page_count=1,
        format="PDF",
        extractor="pymupdf4llm",
        empty_or_scanned=False,
    )

    response = await client.post(
        "/api/v1/extract",
        files={"file": ("cv.pdf", b"%PDF-1.4\nfake-bytes", "application/pdf")},
    )

    assert response.status_code == 200
    assert response.json()["format"] == "PDF"
    assert response.json()["empty_or_scanned"] is False


@pytest.mark.asyncio
async def test_extract_endpoint_rejects_unsupported_media_with_415(client, mock_extractor_factory):
    mock_extractor_factory.extract.side_effect = UnsupportedDocumentFormatError(
        "Unsupported file format"
    )

    response = await client.post(
        "/api/v1/extract",
        files={"file": ("cv.txt", b"plain text", "text/plain")},
    )

    assert response.status_code == 415


@pytest.mark.asyncio
async def test_extract_endpoint_rejects_oversized_upload_with_413(client, mock_extractor_factory):
    oversized = b"%PDF-1.4\n" + (b"A" * (5 * 1024 * 1024 + 10))
    mock_extractor_factory.extract = MagicMock()

    response = await client.post(
        "/api/v1/extract",
        files={"file": ("huge.pdf", oversized, "application/pdf")},
    )

    assert response.status_code == 413
    mock_extractor_factory.extract.assert_not_called()


@pytest.mark.asyncio
async def test_extract_endpoint_maps_corrupt_file_to_422(client, mock_extractor_factory):
    mock_extractor_factory.extract.side_effect = ExtractionError("Corrupt PDF structure")

    response = await client.post(
        "/api/v1/extract",
        files={"file": ("broken.pdf", b"%PDF-broken", "application/pdf")},
    )

    assert response.status_code == 422
