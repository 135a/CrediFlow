import os
from langchain_openai import ChatOpenAI

def get_llm():
    """
    初始化并返回大语言模型实例
    通过环境变量 OPENAI_API_KEY 和 OPENAI_API_BASE 进行配置
    默认使用 DeepSeek 兼容接口或标准的 OpenAI 模型
    """
    # 默认值（如果环境变量没配置，为了保证不崩溃，这里可以留空或给默认值）
    api_key = os.environ.get("OPENAI_API_KEY", "dummy-key-for-local")
    api_base = os.environ.get("OPENAI_API_BASE", "https://api.openai.com/v1")
    model_name = os.environ.get("OPENAI_MODEL_NAME", "gpt-3.5-turbo")

    return ChatOpenAI(
        api_key=api_key,
        base_url=api_base,
        model=model_name,
        temperature=0.2, # 使用较低的温度保证输出稳定
        max_tokens=1000
    )
