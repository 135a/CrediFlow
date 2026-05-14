from fastapi import FastAPI, Request
import uvicorn
import logging

# 导入日志模块并配置基本设置，设置日志级别为INFO
logging.basicConfig(level=logging.INFO)
# 创建一个名为"crediflow_agent"的日志记录器实例
logger = logging.getLogger("crediflow_agent")

# 创建一个FastAPI应用实例
app = FastAPI()

@app.middleware("http")  # 使用 FastAPI 的 http 装饰器注册中间件
async def trace_id_middleware(request: Request, call_next):
    """
    这是一个 HTTP 中间件，用于处理请求和响应中的 trace_id。
    trace_id 用于跟踪请求的完整调用链，便于分布式系统中的问题排查。
    参数:
        request: FastAPI 的 Request 对象，表示当前的 HTTP 请求
        call_next: 一个 callable 对象，表示请求处理链中的下一个中间件或路由处理器
    返回:
        Response: 处理请求后的响应对象，会根据情况添加 trace_id 头部
    """
    # 从请求头中获取 trace_id
    trace_id = request.headers.get("X-Trace-Id")
    # 如果请求头中包含 trace_id，则记录日志
    if trace_id:
        logger.info(f"Received request with X-Trace-Id: {trace_id}")
    else:
        # TODO:后续在无 trace_id 时生成本地 trace_id
        logger.info("Received request without X-Trace-Id")
    
    # 将请求传递给下一个中间件或路由处理器
    response = await call_next(request)
    # 如果请求头中有 trace_id，则在响应头中也添加相同的 trace_id
    if trace_id:
        response.headers["X-Trace-Id"] = trace_id
    return response

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/nl2sql")
def nl2sql(query: dict):
    # Mock NL2SQL implementation
    # 真实实现：LangChain -> SQLDatabaseChain -> LLM -> SQL -> Execute
    return {
        "sql": "SELECT COUNT(*) FROM cf_loan_contract WHERE DATE(created_at) = CURDATE()",
        "result": [{"count": 42}],
        "explanation": "今日总共生成了 42 份借款合同。"
    }

@app.post("/post-loan-warning")
def post_loan_warning():
    # Mock 贷后预警
    # 真实实现：拉取 post-loan-service 数据 -> Prompt -> LLM -> Markdown 预警报告
    report = """
    # 贷后风险智能预警报告 (2026-05-12)
    
    ## 1. 风险概览
    今日新增逾期用户 15 人，涉及逾期本金 125,000 元。
    
    ## 2. 风险因子归因分析
    根据模型分析，本次逾期激增可能与以下因素有关：
    - 多头借贷行为显著增加 (占比 60%)
    - 收入流水造假模型命中率提升 (占比 25%)
    """
    return {"report": report}

@app.post("/rag/ask")
def rag_ask(query: dict):
    # Mock RAG 问答
    # 真实实现：Milvus similarity search -> Context -> LLM -> Answer
    question = query.get("question", "")
    return {
        "answer": "根据《CrediFlow 逾期惩罚规定》，如果您逾期一天，将会按照未结清本金的万分之五收取日罚息。",
        "sources": ["规则文档_V1.pdf", "逾期罚金表.docx"]
    }

@app.post("/manual_review_assistant")
def manual_review_assistant(data: dict):
    # Mock AI Agent analyzing user data and outputting the "Three-piece set" for manual review
    user_id = data.get("userId")
    score_detail = data.get("scoreDetail", {})
    
    total_score = score_detail.get("totalScore", 50)
    risk_level = score_detail.get("riskLevel", "HIGH")
    scene_type = data.get("sceneType", "CREDIT")
    
    # Generate risk details
    if scene_type == "LOAN":
        risk_details = [
            "借款金额占可用额度比例过高",
            "深夜（凌晨2点）发起借款，具有明显的风险特征",
            f"风控模型借款评分过低 ({total_score}分)，放款风险极大"
        ]
    else:
        risk_details = [
            "近期多头借贷频繁，命中外部多头借贷黑名单",
            "常用设备发生异常变更（跨省登录）",
            f"模型评分过低 ({total_score}分)，历史违约倾向较高"
        ]
    
    # Calculate probabilities
    default_prob = round(1.0 - (total_score / 100.0), 2)
    fraud_prob = 0.35 if "HIGH" in risk_level else 0.10
    
    # Suggestion logic (MUST be one of: 建议放行, 建议降额, 建议拒绝, 建议限制期数)
    if default_prob > 0.6:
        suggestion = "建议拒绝"
    elif fraud_prob > 0.3:
        suggestion = "建议降额"
    else:
        suggestion = "建议限制期数"
        
    return {
        "riskDetails": risk_details,
        "defaultProbability": default_prob,
        "fraudProbability": fraud_prob,
        "suggestion": suggestion
    }

import requests

@app.post("/chat_intent_risk")
def chat_intent_risk(data: dict):
    # Mock LLM intent identification
    user_id = data.get("userId")
    chat_logs = data.get("chatLogs", [])
    
    # Simple keyword mock logic
    chat_text = " ".join(chat_logs)
    if "没钱" in chat_text or "不想还" in chat_text or "投诉" in chat_text:
        signal = {
            "userId": user_id,
            "relevantChatLogs": chat_logs,
            "agentSuggestions": "用户表达强烈的反催收/拒还意愿，建议重点标记并人工复核额度或风控降级。",
            "riskType": "INTENT_EVASION"
        }
        
        # Post directly to credit-risk-service (6.2)
        try:
            requests.post("http://localhost:8080/api/internal/credit/risk-signal/escalate", json=signal)
        except Exception as e:
            print(f"Failed to push risk signal: {e}")
            
        return {"hasRisk": True, "signal": signal}
        
    return {"hasRisk": False}

@app.post("/credit_rejection_insight")
def credit_rejection_insight(data: dict):
    # Mock LLM insight generator
    rule_summaries = data.get("ruleSummaries", [])
    
    # Simple logic
    if "MULTIPLE_LOAN_BLACKLIST" in rule_summaries:
        user_safe_insight = "综合评估未通过，请保持良好信用记录后重试"
        admin_insight = "命中外部多头借贷黑名单，具有高违约风险。"
        actionable_advice = "建议拒绝，不提供申诉通道。"
    else:
        user_safe_insight = "由于您近期的风险评分波动，暂时无法为您提供额度"
        admin_insight = f"内部评分不足或模型拒件，原因：{rule_summaries}"
        actionable_advice = "建议三个月后再试"
        
    return {
        "userSafeInsight": user_safe_insight,
        "adminInsight": admin_insight,
        "actionableAdvice": actionable_advice
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
