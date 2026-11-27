import pytest

from app.models.screening_request import CriterionWeight
from app.models.screening_response import CriterionScore, ScreeningResponse
from app.safety.screening_cache import ScreeningCache


def _sample_response(score: int = 85) -> ScreeningResponse:
    return ScreeningResponse(
        overall_score=score,
        summary="Strong backend engineer matching Java and PostgreSQL.",
        strengths=["Java 17", "PostgreSQL"],
        gaps=["Kubernetes"],
        criterion_scores=[CriterionScore(name="Backend", score=score, rationale="6 years Java")],
        model_version="gemini-2.5-flash",
        prompt_version="v1",
        cached=False,
    )


def test_cache_hit_returns_cached_true_without_mutating_stored_entry():
    cache = ScreeningCache(max_size=10)
    key = ScreeningCache.build_key(
        cv_text="Normalized CV",
        job_description="Senior Java Engineer",
        criteria_weights=[CriterionWeight(name="Backend", weight=1.0)],
        blind_mode=True,
        model_version="gemini-2.5-flash",
        prompt_version="v1",
    )
    original = _sample_response(88)
    cache.put(key, original)

    hit = cache.get(key)

    assert hit is not None
    assert hit.overall_score == 88
    assert hit.cached is True
    assert original.cached is False


def test_build_key_is_invariant_to_criteria_order_and_case_whitespace():
    key_a = ScreeningCache.build_key(
        cv_text="CV text",
        job_description="JD text",
        criteria_weights=[
            CriterionWeight(name="Backend", weight=0.6),
            CriterionWeight(name="PostgreSQL", weight=0.4),
        ],
        blind_mode=True,
        model_version="gemini-2.5-flash",
        prompt_version="v1",
    )
    key_b = ScreeningCache.build_key(
        cv_text="CV text",
        job_description="JD text",
        criteria_weights=[
            CriterionWeight(name=" postgresql ", weight=0.4),
            CriterionWeight(name="backend", weight=0.6),
        ],
        blind_mode=True,
        model_version="gemini-2.5-flash",
        prompt_version="v1",
    )

    assert key_a == key_b


@pytest.mark.parametrize(
    "override",
    [
        {"cv_text": "Different CV text"},
        {"job_description": "Frontend Engineer JD"},
        {"criteria_weights": [CriterionWeight(name="Backend", weight=0.5)]},
        {"blind_mode": False},
        {"model_version": "gemini-2.5-pro"},
        {"prompt_version": "v2"},
    ],
)
def test_build_key_differs_when_any_evaluation_dimension_changes(override: dict):
    base_kwargs = {
        "cv_text": "CV text",
        "job_description": "Backend Engineer JD",
        "criteria_weights": [CriterionWeight(name="Backend", weight=1.0)],
        "blind_mode": True,
        "model_version": "gemini-2.5-flash",
        "prompt_version": "v1",
    }
    base_key = ScreeningCache.build_key(**base_kwargs)
    changed_key = ScreeningCache.build_key(**{**base_kwargs, **override})

    assert changed_key != base_key


def test_cache_evicts_least_recently_used_entry_when_capacity_exceeded():
    cache = ScreeningCache(max_size=2)
    cache.put("k1", _sample_response(70))
    cache.put("k2", _sample_response(80))

    # Touch k1 so k2 becomes the least-recently-used item.
    assert cache.get("k1") is not None

    cache.put("k3", _sample_response(90))

    assert cache.get("k2") is None
    assert cache.get("k1").overall_score == 70
    assert cache.get("k3").overall_score == 90


def test_cache_rejects_non_positive_max_size():
    with pytest.raises(ValueError, match="max_size"):
        ScreeningCache(max_size=0)
