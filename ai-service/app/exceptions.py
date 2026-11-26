class LlmEvaluationError(Exception):
    """Raised when an upstream LLM call fails (missing key, network, quota, API error)."""


class LlmResponseParseError(Exception):
    """Raised when LLM output fails JSON parsing, schema validation, or criterion checks."""


class ExtractionError(Exception):
    """Raised when binary document text extraction fails or detects an unsafe archive."""


class UnsupportedDocumentFormatError(ExtractionError):
    """Raised when uploaded file magic bytes are neither PDF nor DOCX."""
