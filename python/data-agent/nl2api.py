import requests
from config import Config

# API 白名单
API_WHITELIST = {
    # TODO: 接入真实征信后，需替换为带 HTTPS+RSA 签名的真实外部征信接口网关
    "get_active_credit": "/api/app/credit/internal/active",
}

class NL2APIEngine:
    def call_api(self, tool_name: str, params: dict) -> dict:
        if tool_name not in API_WHITELIST:
            return {"error": f"API tool '{tool_name}' is not in the whitelist."}
        
        endpoint = API_WHITELIST[tool_name]
        url = f"{Config.JAVA_SERVICE_BASE}{endpoint}"
        
        try:
            # NL2API 默认全为 GET（安全设计），高危写操作需人工或特殊授权
            response = requests.get(url, params=params, timeout=5)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            return {"error": f"API call failed: {str(e)}"}
