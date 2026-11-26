from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Environment configuration, lazy-loaded so tests do not require secrets at import."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    gemini_api_key: str = ""
    gemini_model_name: str = "gemini-2.5-flash"
    llm_timeout_seconds: float = Field(default=25.0, gt=0.0)
    max_cv_upload_bytes: int = Field(default=5 * 1024 * 1024, gt=0)
    max_docx_uncompressed_bytes: int = Field(default=25 * 1024 * 1024, gt=0)
    log_level: str = "INFO"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return cached settings singleton created on first use."""
    return Settings()
