import re
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

_MAX_HEADER_SCAN_LINES = 3
_HEADING_AND_ROLE_WORDS = frozenset(
    {
        "curriculum",
        "vitae",
        "resume",
        "cv",
        "summary",
        "profile",
        "objective",
        "experience",
        "education",
        "skills",
        "projects",
        "certifications",
        "contact",
        "references",
        "senior",
        "junior",
        "associate",
        "lead",
        "principal",
        "staff",
        "intern",
        "software",
        "backend",
        "frontend",
        "fullstack",
        "full",
        "stack",
        "web",
        "mobile",
        "data",
        "cloud",
        "devops",
        "qa",
        "systems",
        "technical",
        "engineer",
        "developer",
        "architect",
        "manager",
        "analyst",
        "consultant",
        "specialist",
        "director",
        "java",
        "python",
        "spring",
        "boot",
        "react",
        "node",
        "bsc",
        "msc",
        "beng",
        "meng",
        "phd",
        "mba",
        "ba",
        "ma",
        "bachelor",
        "master",
        "degree",
        "diploma",
        "honours",
        "honors",
        "computer",
        "science",
        "information",
        "technology",
        "engineering",
        "university",
        "college",
        "institute",
        "academy",
        "school",
        "polytechnic",
        "of",
        "in",
        "and",
        "sliit",
        "nsbm",
        "nibm",
        "ucsc",
        "uom",
        "uoc",
        "mit",
    }
)
_NAME_TOKEN_PATTERN = re.compile(r"^[A-Z][A-Za-z'.-]*$")
_SEGMENT_SPLIT_PATTERN = re.compile(r"[,|/\u2022]|(?:\s+-\s+)")


class RedactionRule(Protocol):
    """Strategy contract for a single ordered PII redaction step."""

    def apply(self, text: str) -> str:
        """Return text with this rule's target PII masked."""


@dataclass(frozen=True)
class RegexRule:
    """Reusable regex-based redaction handler."""

    pattern: re.Pattern[str]
    replacement: str

    @classmethod
    def from_pattern(cls, raw_pattern: str, replacement: str, flags: int = 0) -> "RegexRule":
        """Compile a regex pattern once at construction."""
        return cls(pattern=re.compile(raw_pattern, flags), replacement=replacement)

    def apply(self, text: str) -> str:
        return self.pattern.sub(self.replacement, text)


class NameHeaderRule:
    """Masks leading candidate name tokens across top header lines without touching headings."""

    def apply(self, text: str) -> str:
        lines = text.splitlines()
        inspected = 0
        for idx, line in enumerate(lines):
            if not line.strip():
                continue
            inspected += 1
            updated = self._redact_header_line(line)
            if updated is not None:
                lines[idx] = updated
                return "\n".join(lines)
            if inspected >= _MAX_HEADER_SCAN_LINES:
                break
        return text

    def _redact_header_line(self, line: str) -> str | None:
        prefix = ""
        remainder = line
        gender_prefix = "[GENDER_REDACTED] "
        if remainder.startswith(gender_prefix):
            prefix = gender_prefix
            remainder = remainder[len(gender_prefix) :]

        match = _SEGMENT_SPLIT_PATTERN.search(remainder)
        candidate = remainder[: match.start()] if match else remainder
        suffix = remainder[match.start() :] if match else ""
        if not self._looks_like_person_name(candidate.strip()):
            return None

        leading_ws = candidate[: len(candidate) - len(candidate.lstrip())]
        return f"{prefix}{leading_ws}[NAME_REDACTED]{suffix}"

    def _looks_like_person_name(self, segment: str) -> bool:
        tokens = segment.split()
        if not 2 <= len(tokens) <= 4:
            return False
        for token in tokens:
            if not _NAME_TOKEN_PATTERN.match(token):
                return False
            if token.rstrip(".,").casefold() in _HEADING_AND_ROLE_WORDS:
                return False
        return True


def _privacy_rules() -> tuple[RedactionRule, ...]:
    # Order matters: emails and URLs precede names so domain/profile tokens never linger.
    return (
        RegexRule.from_pattern(
            r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b",
            "[EMAIL_REDACTED]",
        ),
        RegexRule.from_pattern(
            r"(?:https?://[^\s<>\"']+|www\.[^\s<>\"']+|(?:linkedin\.com|github\.com)/[^\s<>\"']+)",
            "[URL_REDACTED]",
        ),
        RegexRule.from_pattern(
            r"(?<!\d)(?:\+\d{1,3}(?:[\s().-]*\d){7,12}"
            r"|\(\d{3}\)\s*\d{3}[\s.-]?\d{4}"
            r"|0\d{2}[\s.-]\d{3}[\s.-]?\d{4}"
            r"|0\d{9})(?!\d)",
            "[PHONE_REDACTED]",
        ),
        RegexRule.from_pattern(
            r"\b(?:\d{9}[VvXx]|(?:19|20)\d{10})\b",
            "[NIC_REDACTED]",
        ),
        RegexRule.from_pattern(
            r"(?:\b(?:male|female|he/him|she/her|they/them|non-binary)\b|\b(?:mr|mrs|ms)\.)",
            "[GENDER_REDACTED]",
            flags=re.IGNORECASE,
        ),
        NameHeaderRule(),
    )


class CvPiiRedactor:
    """Applies a composable privacy pipeline before sending CV text to external LLMs.

    Direct personal identifiers, contact details, URLs, and gender markers are always masked
    regardless of `blind_mode`, while university and degree text stays intact so the LLM can
    evaluate education requirements. Human-reviewer anonymization is handled by `blind_mode`.
    """

    def __init__(self, extra_rules: Sequence[RedactionRule] = ()):
        self._rules: tuple[RedactionRule, ...] = (*_privacy_rules(), *extra_rules)

    def redact(self, cv_text: str, *, blind_mode: bool = True) -> str:
        """Mask direct personal and contact identifiers while keeping education and timelines."""
        _ = blind_mode
        sanitized = cv_text
        for rule in self._rules:
            sanitized = rule.apply(sanitized)
        return sanitized
