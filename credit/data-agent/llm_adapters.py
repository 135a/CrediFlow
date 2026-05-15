from abc import ABC, abstractmethod
from config import Config
import requests

class BaseLLMAdapter(ABC):
    @abstractmethod
    def generate(self, prompt: str) -> str:
        pass

class QwenAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        if not Config.QWEN_API_KEY:
            raise NotImplementedError("Qwen API key is missing. Please implement the actual API call logic.")
        raise NotImplementedError("Qwen API logic needs to be implemented by calling the real LLM endpoint.")

class ZhipuAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        if not Config.ZHIPU_API_KEY:
            raise NotImplementedError("Zhipu API key is missing. Please implement the actual API call logic.")
        raise NotImplementedError("Zhipu API logic needs to be implemented by calling the real LLM endpoint.")

class ErnieAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        if not Config.ERNIE_API_KEY:
            raise NotImplementedError("Ernie API key is missing. Please implement the actual API call logic.")
        raise NotImplementedError("Ernie API logic needs to be implemented by calling the real LLM endpoint.")

class LegacyLLMAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        from legacy_agent.llm_core import get_llm
        llm = get_llm()
        result = llm.invoke(prompt)
        return result.content if hasattr(result, "content") else str(result)

def get_active_llm() -> BaseLLMAdapter:
    provider = Config.ACTIVE_PROVIDER.lower()
    if provider == "zhipu":
        return ZhipuAdapter()
    elif provider == "ernie":
        return ErnieAdapter()
    elif provider == "legacy":
        return LegacyLLMAdapter()
    else:
        return QwenAdapter()
