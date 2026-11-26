from pydantic import BaseModel, Field


class CriterionScore(BaseModel):
    """Score for a single evaluation criterion supported by CV evidence."""

    name: str
    score: int = Field(..., ge=0, le=100)
    rationale: str = Field(..., min_length=1)


class LlmRawEvaluation(BaseModel):
    """Strict structured schema enforced on Gemini and validated after parsing."""

    summary: str = Field(..., min_length=10, max_length=1200)
    strengths: list[str]
    gaps: list[str]
    criterion_scores: list[CriterionScore] = Field(..., min_length=1)


class ScreeningResponse(BaseModel):
    """Validated AI screening response returned to the Java backend."""

    overall_score: int = Field(..., ge=0, le=100)
    summary: str
    strengths: list[str]
    gaps: list[str]
    criterion_scores: list[CriterionScore]
    model_version: str
    prompt_version: str
    cached: bool = False
