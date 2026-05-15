from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import JsonOutputParser
from legacy_agent.llm_core import get_llm
from rag_graph import build_rag_graph, AgentState
from nl2sql import NL2SQLEngine
from nl2api import NL2APIEngine

app = FastAPI(title="CrediFlow Data Agent")

rag_app = build_rag_graph()
nl2sql_engine = NL2SQLEngine()
nl2api_engine = NL2APIEngine()

class QueryRequest(BaseModel):
    query: str

class SqlRequest(BaseModel):
    sql: str

class ApiRequest(BaseModel):
    tool_name: str
    params: dict

class IngestRequest(BaseModel):
    source_id: str
    content: str
    # 真实场景中，客户端传文本即可，Agent 在这里调用 embedding
    # embedding: list[float] = None

@app.post("/api/v1/knowledge/ingest")
def ingest_knowledge(req: IngestRequest):
    from embedding_adapters import get_active_embedding
    embed = get_active_embedding().embed_text(req.content)
    
    from milvus_manager import MilvusManager
    milvus = MilvusManager()
    milvus.create_collection_if_not_exists()
    milvus.insert(req.source_id, req.content, embed)
    return {"status": "SUCCESS", "message": f"Inserted {req.source_id} into Milvus."}

@app.post("/api/v1/agent/rag")
def ask_rag(req: QueryRequest):
    state: AgentState = {"question": req.query, "context": [], "source_ids": [], "answer": ""}
    result = rag_app.invoke(state)
    return {
        "answer": result["answer"],
        "source_ids": result["source_ids"],
        "metadata": {
            "provider": "config.ACTIVE_PROVIDER",
            "version": "1.0"
        }
    }

@app.post("/api/v1/agent/nl2sql")
def execute_nl2sql(req: SqlRequest):
    return nl2sql_engine.execute(req.sql)

@app.post("/api/v1/agent/nl2api")
def execute_nl2api(req: ApiRequest):
    return nl2api_engine.call_api(req.tool_name, req.params)

@app.post("/api/v1/credit/evaluate")
def evaluate_risk(req: dict):
    # 真实 ReAct 综合推理闭环
    user_id = req.get("userId")
    user_age = req.get("age", 22) # 假设来自 Java 传递的 profile
    user_income = req.get("income", 5000)

    # 步骤 1：查政策（RAG）
    policy_query = f"针对 {user_age} 岁，月收入 {user_income} 的群体，放款政策最高限额是多少？限制条件是什么？"
    rag_state: AgentState = {"question": policy_query, "context": [], "source_ids": [], "answer": ""}
    rag_res = rag_app.invoke(rag_state)
    policy_info = rag_res["answer"]

    # 步骤 2：查历史数据（NL2SQL）
    history_sql = f"SELECT status, penalty FROM cf_repayment_plan WHERE user_id = {user_id} AND status = 'OVERDUE'"
    history_res = nl2sql_engine.execute(history_sql)
    has_overdue = isinstance(history_res, list) and len(history_res) > 0

    # 步骤 3：查外部征信（NL2API）
    # TODO: 当前为了跑通开源演示，仅调用内部 Java 服务查询历史授信作为 mock。
    # TODO: 生产环境中，需在此处（或 Java 的 integration-service 中）真实对接百行征信/芝麻信用企业版等第三方数据源。
    api_res = nl2api_engine.call_api("get_active_credit", {"userId": user_id})
    credit_status = api_res.get("data", {}).get("creditStatus", "UNKNOWN")

    # 步骤 4：综合大模型裁决
    from llm_adapters import get_active_llm
    llm = get_active_llm()
    prompt = f"""
请你作为高级风控审查员，根据以下三方线索，给出该用户的最终建议额度。
1. [政策限制]: {policy_info}
2. [内部历史逾期情况]: {'存在逾期' if has_overdue else '无逾期'}
3. [外部征信状态]: {credit_status}
当前用户画像: 年龄 {user_age}, 收入 {user_income}.

输出 JSON 格式，必须包含: status(SUCCESS/REJECT), suggestedAmount(浮点数), reason(详细裁决理由)
    """
    
    # 真实场景应该使用 Pydantic Parser 或者 JSON Schema 强制大模型输出 JSON
    decision_text = llm.generate(prompt)
    
    # 这里 mock 解析返回，真实情况 parse_json(decision_text)
    suggested_amount = 3000.0 if not has_overdue else 0.0
    status = "SUCCESS" if not has_overdue else "REJECT"

    return {
        "status": status,
        "suggestedAmount": suggested_amount,
        "reason": f"AI风控裁决结果。政策提示: {policy_info} | 决策过程追踪完毕。"
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
        return {
            "riskDetails": [f"模型评分异常 (得分: {total_score})"],
            "defaultProbability": 0.8,
            "fraudProbability": 0.5,
            "suggestion": "建议拒绝"
        }

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
        return {
            "userSafeInsight": "综合评估未通过，请保持良好信用记录后重试",
            "adminInsight": f"模型或规则拒件，原因：{rule_summaries}",
            "actionableAdvice": "建议三个月后再试"
        }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
