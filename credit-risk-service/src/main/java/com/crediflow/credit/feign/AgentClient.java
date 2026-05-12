package com.crediflow.credit.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "agent-service", url = "${agent.url:http://localhost:8000}", fallback = AgentClientFallback.class)
public interface AgentClient {
    
    @PostMapping("/api/v1/credit/evaluate")
    Map<String, Object> evaluateRisk(@RequestBody Map<String, Object> userData);
}

@Component
class AgentClientFallback implements AgentClient {
    @Override
    public Map<String, Object> evaluateRisk(Map<String, Object> userData) {
        // 超时降级逻辑：返回默认基础额度或触发人工审核
        return Map.of(
            "status", "FALLBACK",
            "suggestedAmount", 5000.00,
            "reason", "Agent服务超时，采用降级策略"
        );
    }
}
