import re
import unicodedata

MIN_READABLE_CHARS = 50
_PUA_ICONS = re.compile(r"[\uE000-\uF8FF]")
_ZERO_WIDTH = re.compile(r"[\u200B-\u200D\uFEFF]")
_MULTI_SPACES = re.compile(r"[^\S\n]+")
_MULTI_BLANK_LINES = re.compile(r"\n{3,}")


class CvTextNormalizer:
    """Repairs typography ligatures and removes icon glyphs from extracted CV text."""

    def normalize(self, text: str) -> str:
        """Normalize Unicode ligatures while keeping logical line breaks intact."""
        normalized = unicodedata.normalize("NFKC", text)
        without_icons = _PUA_ICONS.sub(" ", _ZERO_WIDTH.sub("", normalized))
        return self._collapse_whitespace(without_icons)

    def _collapse_whitespace(self, text: str) -> str:
        lines = [_MULTI_SPACES.sub(" ", line).strip() for line in text.splitlines()]
        joined = "\n".join(lines)
        return _MULTI_BLANK_LINES.sub("\n\n", joined).strip()
