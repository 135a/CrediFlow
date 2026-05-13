from fastapi import FastAPI
import uvicorn

app = FastAPI()

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

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
