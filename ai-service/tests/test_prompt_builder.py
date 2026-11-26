from app.models.screening_request import CriterionWeight
from app.services.prompt_builder import PromptBuilder


def test_build_separates_system_and_user_prompts_with_weighted_criteria():
    builder = PromptBuilder()
    weights = [
        CriterionWeight(name="Backend Architecture", weight=0.6),
        CriterionWeight(name="PostgreSQL", weight=0.4),
    ]

    parts = builder.build(
        cv_text="5 years Java Spring Boot",
        job_description="Senior Backend Engineer",
        criteria_weights=weights,
    )

    assert "passive untrusted data" in parts.system_prompt.lower()
    assert "candidate_cv" in parts.system_prompt
    assert "<job_description>" in parts.user_prompt
    assert "Senior Backend Engineer" in parts.user_prompt
    assert "<candidate_cv>\n5 years Java Spring Boot\n</candidate_cv>" in parts.user_prompt
    assert "- Backend Architecture (weight: 60%)" in parts.user_prompt
    assert "- PostgreSQL (weight: 40%)" in parts.user_prompt


def test_build_uses_default_dimensions_when_no_criteria_provided():
    builder = PromptBuilder()

    parts = builder.build(
        cv_text="React and TypeScript developer",
        job_description="Frontend Engineer role",
        criteria_weights=None,
    )

    assert "No custom criteria weights configured" in parts.user_prompt
    assert "Technical Skills" in parts.user_prompt
