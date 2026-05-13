import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    # LLM Configuration
    ACTIVE_PROVIDER = os.getenv("ACTIVE_PROVIDER", "qwen") # qwen, zhipu, ernie, openai
    QWEN_API_KEY = os.getenv("QWEN_API_KEY", "")
    ZHIPU_API_KEY = os.getenv("ZHIPU_API_KEY", "")
    ERNIE_API_KEY = os.getenv("ERNIE_API_KEY", "")
    ERNIE_SECRET_KEY = os.getenv("ERNIE_SECRET_KEY", "")

    # Embedding Configuration
    ACTIVE_EMBEDDING_PROVIDER = os.getenv("ACTIVE_EMBEDDING_PROVIDER", ACTIVE_PROVIDER)
    EMBEDDING_DIMENSION = int(os.getenv("EMBEDDING_DIMENSION", "1536"))
    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")

    # Milvus Configuration
    MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
    MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")

    # DB Configuration (NL2SQL Read-Only)
    DB_URI_RO = os.getenv("DB_URI_RO", "mysql+pymysql://readonly_user:password@localhost:3306/crediflow")

    # API Base
    JAVA_SERVICE_BASE = os.getenv("JAVA_SERVICE_BASE", "http://localhost:8080")
