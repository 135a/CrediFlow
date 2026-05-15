## Why

根据最新的《CrediFlow 项目可读性评估报告》（2026-05-15），项目的平均可读性评分为 7.1/10，其中 `bff` 模块和 `system` 模块因为文档缺失、零日志和设计缺陷成为重灾区。为了降低新开发人员的上手成本，消除“魔法数字”、“沉默异常”和“代码重述式注释”等反模式，必须立即进行一次针对性的代码可读性与结构性改进。

## What Changes

- **补充核心文档与日志**：为 `AdminLoanApplicationController`、`SystemAdminController` 等“零日志、零文档”的关键节点补充类级别和方法级 Javadoc，并添加必要的运行日志。
- **治理“魔法数字”与缩写**：清理散落的常量（如分期期数 `3, 6, 12` 等），统一使用有意义的常量名替换魔法数字；更正诸如 `gate`, `rn`, `idc` 这样不透明的变量命名。
- **重构过长方法**：拆分 `applyLoan`, `applyCredit`, `submitStep2`, `submitStep1`, `activeRepay` 等长方法，提取出短小、指责单一的私有方法。
- **移除注释掉的死代码**：对于被无故注释的业务逻辑（如 `forceApprove`），强制要求添加 `TODO` 编号或说明，拒绝“不知为何被注释”的代码留存。
- **规范异常处理**：消灭空 `catch` 块（如 `RealnameVerificationService` 中的沉默异常），强制在捕获处添加 `log.warn`。
- **修正中英双语冗余注释**：清理 `ErrorCode.java` 中纯视觉噪音的中英文重复注释，确保注释能解释“为什么”而不是重述代码。

## Capabilities

### New Capabilities
<!-- 无新的业务能力引入，本次全部为代码级重构 -->

### Modified Capabilities
<!-- 无具体业务需求的变更，故不需要修改 specs -->

## Impact

此次重构将影响 `bff`, `system`, `loan`, `user`, `fund`, `credit`, `post-loan`, `common` 的核心类。不改变底层业务逻辑的对外约定，仅改变内部的代码结构、命名、日志记录和代码注释。
