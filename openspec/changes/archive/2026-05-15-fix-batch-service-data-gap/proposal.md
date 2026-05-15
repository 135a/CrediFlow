## Why

根据《batch-service 数据流与端点对齐分析》报告，现有的 Go 定时调度器 `batch-service` 包含 6 个 Job，但其中 5 个 Job 在发起 HTTP 调用时，对应的 Java 微服务端点并不存在，导致端到端调度完全失效（可用率仅 12.5%）。同时，代码库中还遗留了一个无分布式锁、无防重放功能的简化版调度器 `scheduler-go`。为了彻底打通定时任务的数据闭环并清理历史技术债务，必须立即补齐 Java 端缺失的 API，并删除旧版调度器。

## What Changes

- **移除旧调度器**：安全删除冗余且功能低下的 `batch/scheduler-go/` 整个目录。
- **新增逾期状态扫描与罚息端点**：在 `post-loan-service` 中实现 `POST /api/internal/post-loan/overdue/scan`（可重命名原有的空实现）和 `POST /api/internal/post-loan/penalty/calculate`。
- **新增用户通知与催收端点**：在 `user-service` 中实现 `POST /api/internal/user/notify/repayment-reminder`（还款提醒）和 `POST /api/internal/user/notify/batch-push`（批量推送）。
- **废弃/忽略 Python 端点调度（暂缓）**：关于 `data-agent` 的批量风控端点，因缺少完整上下文，暂不在此次修改范围内提供真实实现，而是仅在业务层面进行端点打通与可用性对齐。

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Replace <name> with kebab-case identifier (e.g., user-auth, data-export, api-rate-limiting). Each creates specs/<name>/spec.md -->
- `batch-job-endpoints`: 提供所有支持核心定时任务运行的内部端点集合（包含逾期扫描、罚息计算和还款通知）。

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->

## Impact

- 彻底删除 `batch/scheduler-go`，清理无效代码。
- `post-loan-service` 增加逾期与罚息入口，将被 `batch-service` 的 `OverdueJob` 和 `PenaltyJob` 每天凌晨定时触发。
- `user-service` 增加通知入口，将被 `ReminderJob` 和 `NotificationJob` 触发。
- 完善内部安全：所有新增接口均继承自 `/api/internal/**` 路径，天然受到 `InternalAuthFilter` 的 HMAC 安全保护。
