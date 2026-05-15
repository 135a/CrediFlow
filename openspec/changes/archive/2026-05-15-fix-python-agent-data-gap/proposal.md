## Why

根据《Python Agent 双模块数据流与业务对接分析》报告，当前项目存在两个功能重叠且端口冲突的 Python 模块（`agent-python` 和 `data-agent`）。其中，Java 端依赖调用的真实接口位于未容器化的 `agent-python` 中，而实际部署在 Nacos 网络中的却是功能更全但部分接口被静态 MOCK 的 `data-agent`。这导致了 Java 端请求直接 404，且 LLM Agent 无法正常提供服务。为了打通 AI 风控节点，需要将两个模块整合。

## What Changes

- **代码合流**：将 `agent-python` 中的 `POST /manual_review_assistant` 与 `POST /credit_rejection_insight` 核心接口及其真实 LLM 调用逻辑迁移至 `data-agent/app.py` 中。
- **清理与重构**：**BREAKING** 彻底移除 `credit/agent-python` 目录，消除两个模块的冲突和混淆。
- **去除存根**：移除 `data-agent` 中遗留的死端点（如 `/api/v1/agent/ocr` 等）。
- **完善配置**：将反向调用 Java 服务的 URL 提取为环境变量，并确保 `data-agent` 的 LLM 推理适配器被正确激活（目前可先复用 langchain-openai 模式）。

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Replace <name> with kebab-case identifier (e.g., user-auth, data-export, api-rate-limiting). Each creates specs/<name>/spec.md -->
- `ai-agent-endpoints`: 统一的大模型风控处理代理接口，融合之前的两个模块的功能。

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->

## Impact

- `credit/agent-python/` 整体删除。
- `data-agent` 承担原本 Java 调用的核心响应责任。
- Java 端 Feign 客户端的调用将被无缝接管（保持路径和入参一致），恢复核心链路的风控辅助解释功能。
