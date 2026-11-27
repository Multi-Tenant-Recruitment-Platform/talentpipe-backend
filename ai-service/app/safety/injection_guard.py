import logging
import re

logger = logging.getLogger(__name__)

_FILTER_MARKER = "[FILTERED]"
_CONTROL_CHARS_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_XML_BREAKOUT_TAGS_RE = re.compile(
    r"</?(?:system|instruction|instructions|prompt|role|candidate_cv"
    r"|job_description|screening_criteria|assistant|developer|user)(?:\s[^>]*)?>",
    flags=re.IGNORECASE,
)
_CV_INJECTION_PATTERNS: tuple[re.Pattern[str], ...] = (
    _XML_BREAKOUT_TAGS_RE,
    re.compile(r"```\s*(?:system|instruction|instructions|prompt)\b", flags=re.IGNORECASE),
    re.compile(r"(?im)^(?:system|assistant|developer|user)\s*:"),
    re.compile(
        r"\b(?:ignore|forget|disregard)\s+(?:all\s+)?(?:previous|above|prior)\s+instructions?\b",
        flags=re.IGNORECASE,
    ),
    re.compile(r"\bsystem\s+(?:instruction\s+)?override\b", flags=re.IGNORECASE),
    re.compile(r"\bnew\s+rule\s*:[^\n]*", flags=re.IGNORECASE),
    re.compile(r"\b(?:pretend\s+to\s+be|you\s+are\s+now)\b", flags=re.IGNORECASE),
)


class PromptInjectionGuard:
    """Neutralizes prompt-delimiter breakouts and override phrases without logging CV text."""

    def sanitize_cv(self, cv_text: str) -> str:
        """Strip control chars and neutralize untrusted CV prompt-injection vectors."""
        cleaned = _CONTROL_CHARS_RE.sub("", cv_text)
        triggered = False
        for pattern in _CV_INJECTION_PATTERNS:
            cleaned, count = pattern.subn(_FILTER_MARKER, cleaned)
            if count > 0:
                triggered = True

        if triggered:
            logger.warning("Prompt injection pattern sanitized in CV payload")
        return cleaned

    def sanitize_job_description(self, job_description: str) -> str:
        """Strip control chars and delimiter breakout tags from recruiter job text."""
        cleaned = _CONTROL_CHARS_RE.sub("", job_description)
        cleaned, count = _XML_BREAKOUT_TAGS_RE.subn(_FILTER_MARKER, cleaned)
        if count > 0:
            logger.warning("Prompt delimiter tag sanitized in job description payload")
        return cleaned
