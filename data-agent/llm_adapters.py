from abc import ABC, abstractmethod
from config import Config
import requests

class BaseLLMAdapter(ABC):
    @abstractmethod
    def generate(self, prompt: str) -> str:
        pass

class QwenAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        # 伪代码：实际需引入 dashscope 或 openai 兼容包
        if not Config.QWEN_API_KEY:
            return "Qwen: API Key not configured."
        return f"[Qwen Response] {prompt}"

class ZhipuAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        # 伪代码：实际需引入 zhipuai 包
        if not Config.ZHIPU_API_KEY:
            return "Zhipu: API Key not configured."
        return f"[Zhipu Response] {prompt}"

class ErnieAdapter(BaseLLMAdapter):
    def generate(self, prompt: str) -> str:
        if not Config.ERNIE_API_KEY:
            return "Ernie: API Key not configured."
        return f"[Ernie Response] {prompt}"

def get_active_llm() -> BaseLLMAdapter:
    provider = Config.ACTIVE_PROVIDER.lower()
    if provider == "zhipu":
        return ZhipuAdapter()
    elif provider == "ernie":
        return ErnieAdapter()
    else:
        return QwenAdapter()
