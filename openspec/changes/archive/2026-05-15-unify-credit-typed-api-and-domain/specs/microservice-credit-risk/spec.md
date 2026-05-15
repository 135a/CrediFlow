## ADDED Requirements

### Requirement: 授信服务内部 API 强类型契约

`credit-risk-service` 对外（含 `/api/internal/credit/*` 与 `/api/app/credit/*`）暴露的业务响应体，在 Java 服务层 MUST 使用具名 DTO/View 类型承载，MUST NOT 以无约束的 `Map<String, Object>` 作为 Service 接口的返回类型或核心业务方法的参数类型（框架层 `Result` 包装除外）。

#### Scenario: 内部申请接口返回明确结构
- **WHEN** 调用方请求 `POST /api/internal/credit/apply` 或等价内部申请能力
- **THEN** 响应 `data` MUST 可反序列化为包含 `applicationId` 与业务状态字段的明确类型，且字段语义与改造前 JSON 契约兼容。

#### Scenario: 查询最近申请状态
- **WHEN** 调用方请求用户最近授信申请状态
- **THEN** 服务 MUST 返回强类型视图对象；当用户无申请记录时 MUST 使用文档化的查询哨兵值（如 `NOT_APPLIED`），且该哨兵 MUST NOT 作为数据库 `status` 列的合法持久化值。

### Requirement: Agent 降级文案可配置与可维护

当 `agent-service` 不可用触发 Feign Fallback 时，返回给用户或管理员的固定中文说明 MUST 集中定义（常量类或配置文件），MUST NOT 散落在 Fallback 方法体内以难以检索的字符串字面量维护；降级用数值阈值 MUST 继续支持外部配置。

#### Scenario: 降级文案单点维护
- **WHEN** 运维或产品需要调整降级提示文案
- **THEN** MUST 能在不超过两处集中定义的位置完成修改（如 `AgentFallbackMessages` 或 `agent-fallback.properties`），而无需在多个 Fallback 方法中重复搜索替换。
