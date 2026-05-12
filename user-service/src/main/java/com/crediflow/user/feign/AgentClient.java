package com.crediflow.user.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "agent-service", url = "${agent.url:http://localhost:8000}", fallback = AgentClientFallback.class)
public interface AgentClient {

    @PostMapping("/api/v1/agent/ocr")
    Map<String, Object> extractOcr(@RequestBody Map<String, Object> req);

    @PostMapping("/api/v1/agent/face_verify")
    Map<String, Object> faceVerify(@RequestBody Map<String, Object> req);
}

@Component
class AgentClientFallback implements AgentClient {
    @Override
    public Map<String, Object> extractOcr(Map<String, Object> req) {
        return Map.of("status", "ERROR", "reason", "Agent Service Unavailable");
    }

    @Override
    public Map<String, Object> faceVerify(Map<String, Object> req) {
        return Map.of("status", "ERROR", "reason", "Agent Service Unavailable");
    }
}
