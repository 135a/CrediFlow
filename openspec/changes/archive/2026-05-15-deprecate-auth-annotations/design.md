## Context

当前系统已向分布式网关架构演进，采用 APISIX 作为统一网关拦截外部流量，并通过约定 `/api/internal/` 路径作为内部服务调用的边界（配合 `InternalAuthFilter` 进行强验证）。在这一架构演变过程中，遗留了在 Controller 层修饰的 `@IgnoreAuth`（标记忽略登录校验）和 `@Inner`（标记仅限内网访问）注解。由于这两者目前在 Spring 容器中没有任何 AOP 或 Filter 来作为拦截器进行消费，它们已经沦为纯粹的“死代码”，存在给开发者传递误导性安全信号的风险。

## Goals / Non-Goals

**Goals:**
- 在代码层面全面移除不再生效的安全注解 `@IgnoreAuth` 和 `@Inner`，避免未来开发的混乱与误解。
- 确保所有的内外网边界与 JWT 安全控制完全委托给“路径约定（Path Convention）”与“网关路由配置（Gateway Configuration）”。
- 输出一份架构理念文档（如 `docs/auth-architecture-philosophy.md`），系统性地阐明基于注解的代码级安全与基于路径/基础设施控制在分布式架构下的核心冲突与权衡。

**Non-Goals:**
- 不改变现有的 `InternalAuthFilter` 逻辑和 `APISIX` 的声明式路由配置，因为现有的基础设施防线配置本身是正确的且已在运行。
- 不引入新的安全验证拦截器代码。

## Decisions

- **决议 1: 物理删除 `@IgnoreAuth` 与 `@Inner` 注解类**
  - **Rationale**: 与其通过 JavaDoc 标记 `@Deprecated`，不如直接物理删除，强迫引用这些类的调用方感知到安全架构的变迁。在零信任安全哲学下，删掉“看似能起防护作用但实际失效”的控制手段是消除隐患的唯一可靠做法。
- **决议 2: 撰写架构哲学文档指导开发者心智**
  - **Rationale**: 仅仅删除代码不能打消习惯了单体应用中 `@PreAuthorize` 类似注解开发者的疑虑。我们需要用一篇官方指导文档，将“为何网关拦截和 URL 规范优于应用层 AOP 注解”讲透，完成心智统一。

## Risks / Trade-offs

- **Risk: 编译报错影响范围广** 
  - **Mitigation**: `@IgnoreAuth` 与 `@Inner` 散落在多个微服务（如 `user-service`, `fund-flow-service` 等）中。通过全局检索并统一删除 import 与注解本身，之后重新跑一次完整的 `mvn clean compile` 即可规避遗漏风险。
- **Trade-off: 丢失了方法级别的显式可读性**
  - **Mitigation**: 确实牺牲了在方法签名上“一眼看出该方法免鉴权/属内网”的直观性。但这是采用全局网关强管控架构的必然妥协，即牺牲局部的代码可读性，换取系统全局安全上的无死角保障（网关层默认拒绝，不让恶意流量打进 JVM）。
