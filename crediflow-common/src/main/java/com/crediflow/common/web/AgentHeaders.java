package com.crediflow.common.web;

/**
 * 内部 Agent (微服务) 之间通信使用的标准 Header
 */
public interface AgentHeaders {
    
    /**
     * 全局链路追踪 ID
     */
    String X_TRACE_ID = "X-Trace-Id";
    
    /**
     * 发起请求的源 Agent 名称 (如 agent-contract-process)
     */
    String X_AGENT_SOURCE = "X-Agent-Source";
}
