from pydantic import BaseModel, Field, model_validator


class CriterionWeight(BaseModel):
    """A single recruiter-defined screening criterion with relative importance."""

    name: str = Field(..., min_length=1, max_length=120)
    weight: float = Field(..., gt=0.0, le=1.0)


class ScreeningRequest(BaseModel):
    """Screening payload sent by the Spring Boot backend."""

    cv_text: str = Field(..., min_length=50, max_length=50_000)
    job_description: str = Field(..., min_length=20, max_length=15_000)
    criteria_weights: list[CriterionWeight] | None = None
    blind_mode: bool = Field(default=True)
    bypass_cache: bool = Field(default=False)

    @model_validator(mode="after")
    def validate_unique_criteria(self) -> "ScreeningRequest":
        if not self.criteria_weights:
            return self
        seen: set[str] = set()
        for item in self.criteria_weights:
            normalized = item.name.casefold().strip()
            if normalized in seen:
                raise ValueError(f"Duplicate criterion name: '{item.name}'")
            seen.add(normalized)
        return self
