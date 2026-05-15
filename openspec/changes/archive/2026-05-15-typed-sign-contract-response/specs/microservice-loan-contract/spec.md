## ADDED Requirements

### Requirement: 内部签署接口返回强类型契约

`loan-contract-service` 提供的内部合同签署 HTTP 接口（`POST /api/internal/contract/sign` 及其对应服务方法）在成功响应体中 MUST 使用可序列化的强类型对象（如专用 DTO）承载业务结果，MUST NOT 以无约束的 `Map<String, Object>` 作为服务层返回类型；对外 JSON 字段名与取值语义 SHOULD 与改造前保持一致（至少包含业务状态 `status` 及可选的人类可读 `message`），以降低调用方迁移成本。

#### Scenario: 成功签署返回稳定字段
- **WHEN** 用户已完成协议勾选且签署流程成功结束（含幂等命中「已签署」）
- **THEN** HTTP 200 且 `Result` 的 `data` MUST 可被反序列化为明确类型，且 MUST 包含表示业务成功的 `status` 字段；`message` 字段 MAY 用于说明幂等或提示文案。

#### Scenario: 编译期可校验返回结构
- **WHEN** 开发者在合同模块内调用签署服务方法
- **THEN** 返回类型 MUST 为具名 Java 类型而非原始 `Map`，以便 IDE 与编译器校验字段访问。
