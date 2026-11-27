import hashlib
import json
from collections import OrderedDict

from app.models.screening_request import CriterionWeight
from app.models.screening_response import ScreeningResponse


class ScreeningCache:
    """Bounded SHA-256 keyed LRU cache for deterministic screening evaluations."""

    def __init__(self, max_size: int = 1000):
        if max_size <= 0:
            raise ValueError("max_size must be greater than zero")
        self._max_size = max_size
        self._entries: OrderedDict[str, ScreeningResponse] = OrderedDict()

    @staticmethod
    def build_key(
        *,
        cv_text: str,
        job_description: str,
        criteria_weights: list[CriterionWeight] | None,
        blind_mode: bool,
        model_version: str,
        prompt_version: str,
    ) -> str:
        """Hash every parameter that affects scoring output into a SHA-256 key."""
        canonical_criteria = sorted(
            (
                {"name": item.name.strip().casefold(), "weight": item.weight}
                for item in (criteria_weights or [])
            ),
            key=lambda entry: (entry["name"], entry["weight"]),
        )
        payload = json.dumps(
            {
                "cv_text": cv_text,
                "job_description": job_description,
                "criteria": canonical_criteria,
                "blind_mode": blind_mode,
                "model_version": model_version,
                "prompt_version": prompt_version,
            },
            sort_keys=True,
            ensure_ascii=False,
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def get(self, cache_key: str) -> ScreeningResponse | None:
        """Return a cached copy marked `cached=True` and promote it to MRU."""
        entry = self._entries.get(cache_key)
        if entry is None:
            return None
        self._entries.move_to_end(cache_key)
        return entry.model_copy(update={"cached": True})

    def put(self, cache_key: str, response: ScreeningResponse) -> None:
        """Store or refresh an evaluation entry and evict LRU entries past capacity."""
        self._entries[cache_key] = response.model_copy(update={"cached": False})
        self._entries.move_to_end(cache_key)
        if len(self._entries) > self._max_size:
            self._entries.popitem(last=False)
