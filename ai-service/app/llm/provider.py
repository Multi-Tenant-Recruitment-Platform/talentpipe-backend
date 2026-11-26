from abc import ABC, abstractmethod


class LlmProvider(ABC):
    """Strategy contract decoupling screening logic from third-party LLM SDKs."""

    @abstractmethod
    async def evaluate(self, system_prompt: str, user_prompt: str) -> str:
        """Evaluate system + user prompt and return raw JSON response text."""

    @abstractmethod
    def model_version(self) -> str:
        """Return model identifier recorded for score auditing."""

    @abstractmethod
    async def aclose(self) -> None:
        """Release underlying client resources cleanly on shutdown."""
