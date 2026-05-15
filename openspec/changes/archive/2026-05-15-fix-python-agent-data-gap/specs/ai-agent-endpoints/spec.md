# Capability: ai-agent-endpoints

## Overview
在信贷流程中，人工审核辅助（`manual_review_assistant`）与自动拒件解析（`credit_rejection_insight`）是 Java 端通过 Feign 调用 Python 侧的重要端点。本能力规范要求将原 `agent-python` 中的这两个接口平移并合并至 `data-agent` 中，提供统一的数据暴露并真实对接 LLM 模型。

## Requirements

1. **统一的服务入口**：
   - 所有的智能代理请求应统一下发给 `data-agent`（端口 8000），并通过 Docker Compose 确保对 Nacos 暴露 `agent-service` 的名称。

2. **保留并激活真实 LLM 端点**：
   - 接口 `POST /manual_review_assistant` 必须位于 FastAPI 的根路由中（无 `/api/v1` 前缀以兼容既有的 Java 客户端），且底层需触发真实的大语言模型 API 完成报告生成。
   - 接口 `POST /credit_rejection_insight` 也必须置于根路由中并实现真实的拒件理由提炼。

3. **剥离无效能力**：
   - 清理所有与业务脱节的死路由、Mock 路由以及在 Java 端缺乏调用的残留代码。

4. **配置集成**：
   - 提供必要的环境变量参数，保证 LLM 的正确验证和调用，不允许保留诸如 `"[Provider Response]"` 这类的空存根结果。
