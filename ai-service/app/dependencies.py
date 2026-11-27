from functools import lru_cache

from app.config import get_settings
from app.extraction.docx_extractor import DocxExtractor
from app.extraction.extractor import DocumentExtractorFactory
from app.extraction.pdf_extractor import PdfLayoutExtractor
from app.extraction.text_normalizer import CvTextNormalizer
from app.llm.gemini_provider import GeminiProvider
from app.safety.injection_guard import PromptInjectionGuard
from app.safety.pii_redactor import CvPiiRedactor
from app.safety.screening_cache import ScreeningCache
from app.services.screening_service import AiScreeningService


@lru_cache(maxsize=1)
def get_screening_service() -> AiScreeningService:
    """Lazy singleton factory for screening orchestration (overrideable in tests)."""
    settings = get_settings()
    provider = GeminiProvider(
        api_key=settings.gemini_api_key,
        model_name=settings.gemini_model_name,
    )
    return AiScreeningService(
        llm_provider=provider,
        pii_redactor=CvPiiRedactor(),
        injection_guard=PromptInjectionGuard(),
        cache=ScreeningCache(max_size=settings.cache_max_size),
    )


@lru_cache(maxsize=1)
def get_extractor_factory() -> DocumentExtractorFactory:
    """Lazy singleton factory for Canva/LaTeX/PDF/DOCX document extractors."""
    settings = get_settings()
    normalizer = CvTextNormalizer()
    return DocumentExtractorFactory(
        pdf_extractor=PdfLayoutExtractor(normalizer),
        docx_extractor=DocxExtractor(
            normalizer,
            max_uncompressed_bytes=settings.max_docx_uncompressed_bytes,
        ),
    )
