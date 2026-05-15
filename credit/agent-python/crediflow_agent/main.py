from fastapi import FastAPI, Request
import uvicorn
import logging
from typing import List
from pydantic import BaseModel, Field
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import JsonOutputParser
from crediflow_agent.llm_core import get_llm

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
def post_loan_warning(data: dict = None):
    data = data or {}
    overdue_count = data.get("overdueCount", 15)
    overdue_amount = data.get("overdueAmount", 125000)
    risk_factors = data.get("riskFactors", [
        "多头借贷行为显著增加",
        "收入流水造假模型命中率提升"
    ])

    llm = get_llm()
    prompt = PromptTemplate(
        template="你是一个资深的消费金融贷后风控分析专家。请根据以下今日贷后逾期数据指标，撰写一份专业的“贷后风险智能预警报告”（Markdown格式）。\n\n"
                 "今日新增逾期人数: {overdue_count}\n"
                 "涉及逾期本金: {overdue_amount} 元\n"
                 "主要风险因子特征: {risk_factors}\n\n"
                 "要求：\n"
                 "1. 报告必须包含大标题“贷后风险智能预警报告”。\n"
                 "2. 包含“风险概览”段落总结数据。\n"
                 "3. 包含“风险因子归因分析”段落，结合风控领域知识对上述特征进行深度解读。\n"
                 "4. 包含“贷后催收与策略调整建议”段落，给出有针对性的行动方案。\n"
                 "5. 语气要严肃、客观、专业。\n",
        input_variables=["overdue_count", "overdue_amount", "risk_factors"]
    )

    try:
        chain = prompt | llm
        result = chain.invoke({
            "overdue_count": overdue_count,
            "overdue_amount": overdue_amount,
            "risk_factors": "\n- ".join([""] + risk_factors)
        })
        return {"report": result.content}
    except Exception as e:
        logger.error(f"LLM Error in post_loan_warning: {e}")
        return {"report": f"# 贷后风险智能预警报告\n生成报告失败，请稍后重试。\n错误详情: {e}"}

@app.post("/rag/ask")
def rag_ask(query: dict):
    # Mock RAG 问答
    # 真实实现：Milvus similarity search -> Context -> LLM -> Answer
    question = query.get("question", "")
    return {
        "answer": "根据《CrediFlow 逾期惩罚规定》，如果您逾期一天，将会按照未结清本金的万分之五收取日罚息。",
        "sources": ["规则文档_V1.pdf", "逾期罚金表.docx"]
    }

class ManualReviewResult(BaseModel):
    riskDetails: List[str] = Field(description="风险明细列表，每项是对风险的描述")
    defaultProbability: float = Field(description="预测的违约概率，范围 0.0 到 1.0")
    fraudProbability: float = Field(description="预测的欺诈概率，范围 0.0 到 1.0")
    suggestion: str = Field(description="人工复核建议，必须是以下之一：建议放行, 建议降额, 建议拒绝, 建议限制期数")

@app.post("/manual_review_assistant")
def manual_review_assistant(data: dict):
    user_id = data.get("userId")
    score_detail = data.get("scoreDetail", {})
    total_score = score_detail.get("totalScore", 50)
    risk_level = score_detail.get("riskLevel", "HIGH")
    scene_type = data.get("sceneType", "CREDIT")

    llm = get_llm()
    parser = JsonOutputParser(pydantic_object=ManualReviewResult)

    prompt = PromptTemplate(
        template="你是一个资深的信贷风控审核专家。请根据以下用户得分详情与场景类型，评估该用户的风险情况并给出结构化的建议。\n\n"
                 "用户场景类型: {scene_type}\n"
                 "风控模型总分: {total_score} (满分100，越低风险越高)\n"
                 "风控定级: {risk_level}\n\n"
                 "{format_instructions}\n",
        input_variables=["scene_type", "total_score", "risk_level"],
        partial_variables={"format_instructions": parser.get_format_instructions()},
    )

    try:
        chain = prompt | llm | parser
        result = chain.invoke({
            "scene_type": scene_type,
            "total_score": total_score,
            "risk_level": risk_level
        })
        return result
    except Exception as e:
        logger.error(f"LLM Error in manual_review_assistant: {e}")
        return {
            "riskDetails": [f"模型评分异常 (得分: {total_score})"],
            "defaultProbability": 0.8,
            "fraudProbability": 0.5,
            "suggestion": "建议拒绝"
        }

import requests

class ChatIntentResult(BaseModel):
    hasRisk: bool = Field(description="是否存在违约、欺诈或反催收风险意图")
    riskType: str = Field(description="风险类型，如 INTENT_EVASION, FRAUD_SUSPICION, NONE")
    agentSuggestions: str = Field(description="给人工审核人员的建议")

@app.post("/chat_intent_risk")
def chat_intent_risk(data: dict):
    user_id = data.get("userId")
    chat_logs = data.get("chatLogs", [])
    chat_text = "\n".join(chat_logs)

    llm = get_llm()
    parser = JsonOutputParser(pydantic_object=ChatIntentResult)

    prompt = PromptTemplate(
        template="你是一个专业的信贷反欺诈与催收分析专家。以下是用户最近的聊天记录，请分析其对话意图是否存在抗拒还款、欺诈等高危风险。\n\n"
                 "聊天记录:\n{chat_text}\n\n"
                 "{format_instructions}\n",
        input_variables=["chat_text"],
        partial_variables={"format_instructions": parser.get_format_instructions()},
    )

    try:
        chain = prompt | llm | parser
        result = chain.invoke({"chat_text": chat_text})
        
        if result.get("hasRisk"):
            signal = {
                "userId": user_id,
                "relevantChatLogs": chat_logs,
                "agentSuggestions": result.get("agentSuggestions"),
                "riskType": result.get("riskType")
            }
            try:
                requests.post("http://localhost:8080/api/internal/credit/risk-signal/escalate", json=signal)
            except Exception as e:
                logger.error(f"Failed to push risk signal: {e}")
            return {"hasRisk": True, "signal": signal}
            
        return {"hasRisk": False}
    except Exception as e:
        logger.error(f"LLM Error in chat_intent_risk: {e}")
        return {"hasRisk": False}

class RejectionInsightResult(BaseModel):
    userSafeInsight: str = Field(description="给用户看的拒件理由（需委婉、合规，不泄露风控规则底牌）")
    adminInsight: str = Field(description="给风控运营人员看的真实拒件洞察（直接指出触发的规则和风险点）")
    actionableAdvice: str = Field(description="建议风控人员或用户采取的后续行动")

@app.post("/credit_rejection_insight")
def credit_rejection_insight(data: dict):
    rule_summaries = data.get("ruleSummaries", [])
    rules_text = "\n".join(rule_summaries)

    llm = get_llm()
    parser = JsonOutputParser(pydantic_object=RejectionInsightResult)

    prompt = PromptTemplate(
        template="你是一个专业的信贷风控解释专家。以下是风控规则引擎刚刚拒绝了一名用户的授信申请，触发的拒件规则列表如下：\n\n"
                 "{rules_text}\n\n"
                 "请根据上述规则，生成三个维度的拒件洞察与解释：\n"
                 "{format_instructions}\n",
        input_variables=["rules_text"],
        partial_variables={"format_instructions": parser.get_format_instructions()},
    )

    try:
        chain = prompt | llm | parser
        result = chain.invoke({"rules_text": rules_text})
        return result
    except Exception as e:
        logger.error(f"LLM Error in credit_rejection_insight: {e}")
        return {
            "userSafeInsight": "综合评估未通过，请保持良好信用记录后重试",
            "adminInsight": f"模型或规则拒件，原因：{rule_summaries}",
            "actionableAdvice": "建议三个月后再试"
        }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
