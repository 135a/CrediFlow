from langgraph.graph import StateGraph, END
from typing import TypedDict, List
from llm_adapters import get_active_llm

class AgentState(TypedDict):
    question: str
    context: List[str]
    source_ids: List[str]
    answer: str

def retrieve_node(state: AgentState):
    from milvus_manager import MilvusManager
    # 真实从 Milvus 检索
    milvus = MilvusManager()
    collection = milvus.create_collection_if_not_exists()
    
    from embedding_adapters import get_active_embedding
    embed = get_active_embedding().embed_text(state["question"])
    
    collection.load()
    search_params = {"metric_type": "L2", "params": {"nprobe": 10}}
    results = collection.search(
        data=[embed], 
        anns_field="embedding", 
        param=search_params, 
        limit=3, 
        output_fields=["source_id", "content"]
    )
    
    contexts = []
    sources = []
    for hits in results:
        for hit in hits:
            contexts.append(hit.entity.get("content"))
            sources.append(hit.entity.get("source_id"))
            
    if not contexts:
        state["context"] = ["Policy: No specific policy found. Proceed with standard caution."]
        state["source_ids"] = ["system_default"]
    else:
        state["context"] = contexts
        state["source_ids"] = sources

    return state

def generate_node(state: AgentState):
    llm = get_active_llm()
    prompt = f"Context: {state['context']}\nQuestion: {state['question']}\nAnswer:"
    answer = llm.generate(prompt)
    state["answer"] = answer
    return state

def build_rag_graph():
    workflow = StateGraph(AgentState)
    
    workflow.add_node("retrieve", retrieve_node)
    workflow.add_node("generate", generate_node)
    
    workflow.set_entry_point("retrieve")
    workflow.add_edge("retrieve", "generate")
    workflow.add_edge("generate", END)
    
    return workflow.compile()
