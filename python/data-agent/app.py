from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
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

class OcrRequest(BaseModel):
    image_base64: str

@app.post("/api/v1/agent/ocr")
async def extract_id_card_ocr(req: OcrRequest):
    """
    接收前端或 Java 端传来的 base64 图片，调用多模态大模型进行 OCR 解析。
    返回姓名、身份证号和计算得出的年龄。
    """
    # 真实场景中，此处应调用 Qwen-VL 等视觉大模型：
    # llm = get_active_vl_model()
    # json_result = llm.generate("请提取图片中的真实姓名、身份证号，并计算年龄，以JSON格式输出", image=req.image_base64)
    
    # 这里模拟大模型解析成功的结构化数据
    # 根据常识，我们可以从模拟生成的身份证号计算年龄，这里直接 mock
    import random
    age = random.randint(20, 45)
    return {
        "status": "SUCCESS",
        "data": {
            "realName": "张三(OCR识别)",
            "idCardNo": f"11010519{90+age%10}01011234",
            "age": age
        }
    }

class FaceVerifyRequest(BaseModel):
    id_card_no: str
    face_image_base64: str

@app.post("/api/v1/agent/face_verify")
async def face_verify(req: FaceVerifyRequest):
    """模拟人脸活体比对网关"""
    # 生产环境中应调用公安部第一研究所或旷视等第三方人脸比对接口
    return {
        "status": "SUCCESS",
        "score": 98.5,
        "message": "活体检测通过，且与网纹照比对一致"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
