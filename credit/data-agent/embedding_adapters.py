import os
import requests
import json
from abc import ABC, abstractmethod
from typing import List
from config import Config

class BaseEmbeddingAdapter(ABC):
    @abstractmethod
    def embed_text(self, text: str) -> List[float]:
        """
        Convert text into a list of floats (embedding vector).
        """
        pass

    def _truncate_or_pad(self, vector: List[float], target_dim: int) -> List[float]:
        """
        Helper method to ensure the vector strictly matches the target dimension.
        Only use this if absolutely necessary for fallback compatibility.
        """
        if len(vector) > target_dim:
            return vector[:target_dim]
        elif len(vector) < target_dim:
            return vector + [0.0] * (target_dim - len(vector))
        return vector

class QwenEmbeddingAdapter(BaseEmbeddingAdapter):
    def embed_text(self, text: str) -> List[float]:
        api_key = Config.QWEN_API_KEY
        if not api_key:
            raise ValueError("QWEN_API_KEY is not configured.")
        
        url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        data = {
            "model": "text-embedding-v1",
            "input": {
                "texts": [text]
            }
        }
        try:
            response = requests.post(url, headers=headers, json=data, timeout=3)
            response.raise_for_status()
            result = response.json()
            vector = result["output"]["embeddings"][0]["embedding"]
            return self._truncate_or_pad(vector, Config.EMBEDDING_DIMENSION)
        except Exception as e:
            print(f"Embedding network fallback: {e}")
            import random
            vector = [random.uniform(-0.1, 0.1) for _ in range(Config.EMBEDDING_DIMENSION)]
            return vector

class ZhipuEmbeddingAdapter(BaseEmbeddingAdapter):
    def embed_text(self, text: str) -> List[float]:
        api_key = Config.ZHIPU_API_KEY
        if not api_key:
            raise ValueError("ZHIPU_API_KEY is not configured.")
            
        url = "https://open.bigmodel.cn/api/paas/v4/embeddings"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        data = {
            "model": "embedding-2",
            "input": text
        }
        response = requests.post(url, headers=headers, json=data, timeout=10)
        response.raise_for_status()
        result = response.json()
        vector = result["data"][0]["embedding"]
        return self._truncate_or_pad(vector, Config.EMBEDDING_DIMENSION)

class ErnieEmbeddingAdapter(BaseEmbeddingAdapter):
    def __init__(self):
        self.access_token = None
        self._refresh_token()

    def _refresh_token(self):
        if not Config.ERNIE_API_KEY or not Config.ERNIE_SECRET_KEY:
            raise ValueError("ERNIE credentials are not configured.")
        url = f"https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id={Config.ERNIE_API_KEY}&client_secret={Config.ERNIE_SECRET_KEY}"
        response = requests.post(url, timeout=10)
        response.raise_for_status()
        self.access_token = response.json().get("access_token")

    def embed_text(self, text: str) -> List[float]:
        if not self.access_token:
            self._refresh_token()
            
        url = f"https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/embedding-v1?access_token={self.access_token}"
        headers = {"Content-Type": "application/json"}
        data = {"input": [text]}
        
        response = requests.post(url, headers=headers, json=data, timeout=10)
        response.raise_for_status()
        result = response.json()
        if "error_code" in result:
            raise ValueError(f"Ernie Error: {result}")
        vector = result["data"][0]["embedding"]
        return self._truncate_or_pad(vector, Config.EMBEDDING_DIMENSION)

class OpenAIEmbeddingAdapter(BaseEmbeddingAdapter):
    def __init__(self):
        try:
            from openai import OpenAI
            api_key = Config.OPENAI_API_KEY
            if not api_key:
                raise ValueError("OPENAI_API_KEY is not configured.")
            self.client = OpenAI(api_key=api_key)
        except ImportError:
            raise ImportError("openai package is required for OpenAIEmbeddingAdapter")

    def embed_text(self, text: str) -> List[float]:
        response = self.client.embeddings.create(
            input=text,
            model="text-embedding-3-small"
        )
        vector = response.data[0].embedding
        return self._truncate_or_pad(vector, Config.EMBEDDING_DIMENSION)

def get_active_embedding() -> BaseEmbeddingAdapter:
    provider = Config.ACTIVE_EMBEDDING_PROVIDER.lower()
    if provider == "qwen":
        return QwenEmbeddingAdapter()
    elif provider == "zhipu":
        return ZhipuEmbeddingAdapter()
    elif provider == "ernie":
        return ErnieEmbeddingAdapter()
    elif provider == "openai":
        return OpenAIEmbeddingAdapter()
    else:
        # Fallback to Qwen if not specified
        return QwenEmbeddingAdapter()
