# auth-architecture-philosophy Specification

## Purpose

本规范确立了分布式架构下的安全治理理念，阐明了在微服务中使用基础设施级安全控制（即基于网关的声明式放行与基于路径的内网隔离）替代传统的应用层代码注解（如 `@IgnoreAuth` 和 `@Inner`）的核心原因与指导原则。

## Requirements

### Requirement: 认证与授权架构哲学指南
系统 MUST 在 `docs/` 目录下维护一份官方的架构哲学指南文档，明确指出在分布式架构下采用“基础设施/网关声明式安全”取代“微服务内 AOP 注解式安全”的核心逻辑。文档 MUST 澄清不再支持 `@IgnoreAuth` 与 `@Inner` 的原因，并指导开发者如何通过调整 API 路径前缀（如 `/api/internal/`）和配置 APISIX 路由来管理鉴权策略。

#### Scenario: 开发者查阅文档以了解鉴权规范
- **WHEN** 开发者尝试在新接口上添加 `@IgnoreAuth` 却发现该注解不存在时，通过查阅项目文档
- **THEN** 其 MUST 能够找到该哲学指南，并清楚地知道应当在 APISIX 配置中放行对应的 URL 路径。
