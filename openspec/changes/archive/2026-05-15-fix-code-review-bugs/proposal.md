## Why

根据最新的《CrediFlow 项目代码审查报告》，系统中积压了若干 CRITICAL 和 HIGH 级别的严重代码缺陷。其中包含 DTO 重复定义导致核心数据丢失（如借款申请金额丢失）、跨微服务循环依赖、架构分层遭破坏（Controller 直接使用 MyBatis API）以及 Entity 结构通过 API 泄露等问题。如果不及时修复，这些问题将在未来导致数据丢失、级联宕机和安全漏洞。

## What Changes

1. **解决 CRITICAL 问题**：
   - 移除 `credit` 模块重复的 `LoanRiskEvaluateRequest`，统一使用 `common` 模块的三字段版，防止 `applyAmount` 序列化丢失。
   - 解除 `user-service` 和 `credit-risk-service` 之间的 Feign 循环依赖，重构风险验证或黑名单校验逻辑。
2. **解决 HIGH 问题**：
   - 将 `loan-application-service` 中的基础包名从 `com.crediflow.application` 纠正为 `com.crediflow.loan`。
   - 清理 `user-service` 中的冗余 `UserServiceApplication.java` 和 `Demo.java` 测试残留。
   - 统一 common 模块中的 `generateHmacSHA256` 逻辑到公共 `util` 包，并解决 `util` 和 `utils` 包名分裂问题。
   - 彻底修复 `LoanApplicationController` 等直接调用 `MyBatis-Plus` 方法的架构穿透问题，通过 DTO 屏蔽 Entity 返回。

## Capabilities

### Modified Capabilities

- 风控准入和借款评估 (Risk Control & Loan Application)
- 内部微服务调用契约 (Internal API Contracts)

## Impact

本修复方案旨在消除架构隐患，提升系统容错性与健壮性，确保代码符合标准的 MVC 分层结构并杜绝核心字段静默丢失。
