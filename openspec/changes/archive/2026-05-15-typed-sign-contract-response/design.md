## Context

内部合同签署接口 `POST /api/internal/contract/sign` 当前经 `Result.success(Map)` 返回，服务层 `signAndGenerateContract` 以 `Map<String, Object>` 拼装 `status`、`message`。键名字符串散落，重构与静态检查能力弱。

## Goals / Non-Goals

**Goals:**
- Java 侧以不可随意增删键的 **DTO** 表达签约成功/幂等已签等结果，并保持与现有 JSON 字段一致（默认 `status`、`message`），降低对调用方的破坏性。
- Controller 与 Service 接口签名同步为强类型。

**Non-Goals:**
- 本次不改造 `getContractLink` 的 `Map` 返回（可另立变更）。
- 不改变签约业务步骤（生成借据、扣额度等）与事务边界。

## Decisions

- **决议 1：DTO 命名与放置**  
  - 类名 `SignContractResult`（或同义），包路径 `com.crediflow.contract.dto`（若已存在 `dto` 包则对齐；否则新建）。  
  - 使用 Lombok `@Data` 与项目其余实体风格一致。

- **决议 2：字段与序列化**  
  - 至少包含：`String status`（如 `SUCCESS`）、`String message`（人类可读说明，含幂等提示）。  
  - 使用常规 JavaBean 命名；Jackson 默认序列化为 `status`/`message`，与当前 Map 键一致。  
  - **备选**：若未来需区分「本次签署」与「此前已签」，可再增加枚举字段；本变更以平移现有语义为先。

- **决议 3：幂等与空 message**  
  - 成功路径 `message` 可为 `null` 或空串时，与前端约定：本阶段保持与现实现一致（已签路径有文案，新签路径可无 message）。

## Risks / Trade-offs

- **Risk**：仓库外 Java Feign 若以 `Map` 解析返回值会编译失败 —— **Mitigation**：proposal 已标 **BREAKING（弱）**；HTTP 契约优先保持稳定。  
- **Risk**：DTO 与 OpenAPI/网关文档未同步 —— **Mitigation**：若有生成式文档 pipeline，在 apply 阶段检查是否需补注解（非必须则不扩 scope）。

## Migration Plan

1. 新增 DTO 类。  
2. 改接口与实现、Controller。  
3. 全量或模块 `mvn compile` 验证。  
4. 若有外部 Java 客户端，发布前通知其改用 DTO 或解析 JSON。

## Open Questions

- 是否在下一迭代将 `status` 提升为枚举并在 API 文档中列出合法取值（本变更可维持 `String` 以降低 diff）。
