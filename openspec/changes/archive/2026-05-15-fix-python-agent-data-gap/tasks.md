## 1. 代码迁移与合并

- [x] 1.1 在 `data-agent` 目录下创建 `legacy_agent` 文件夹，并将 `credit/agent-python/crediflow_agent/llm_core.py` 的内容安全迁移至其中。
- [x] 1.2 将 `credit/agent-python/crediflow_agent/main.py` 中关于 `/manual_review_assistant` 与 `/credit_rejection_insight` 的 Pydantic 模型（Request/Response）以及路由处理逻辑合并至 `data-agent/app.py` 中。

## 2. 存根替换与无效接口清理

- [x] 2.1 修改 `data-agent/app.py`，删除 `/api/v1/agent/ocr` 和 `/api/v1/agent/face_verify` 两个 Mock 路由。
- [x] 2.2 修改 `data-agent/llm_adapters.py`，将原本返回静态字符串（`[Provider Response] ...`）的存根实现，改为调用 `legacy_agent` 中的真实大模型或者抛出 `NotImplementedError` 强制提示开发者实现真实的 API 调用。

## 3. 旧服务废除

- [x] 3.1 彻底删除冗余项目目录 `credit/agent-python`，确保后续只通过 `data-agent` 提供一切 AI 代理服务。
