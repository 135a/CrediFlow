## Context

`batch-service` (Go) 作为全局的定时任务调度中心，负责触发 `post-loan-service` (逾期、罚息) 和 `user-service` (通知)。目前 Java 侧尚未准备好这些接收调用的内部端点，导致 Go 的定时任务发起调用后出现 404 错误。同时，废弃的 `scheduler-go` 需要被移除以避免代码库的混乱。

## Goals / Non-Goals

**Goals:**
- 在 `post-loan-service` 增加 `POST /api/internal/post-loan/overdue/scan`。
- 在 `post-loan-service` 增加 `POST /api/internal/post-loan/penalty/calculate`。
- 在 `user-service` 增加 `POST /api/internal/user/notify/repayment-reminder`。
- 在 `user-service` 增加 `POST /api/internal/user/notify/batch-push`。
- 完全删除 `batch/scheduler-go`。

**Non-Goals:**
- 不涉及真实业务逻辑的过度实现，当前阶段的目标是“端点打通”，即建立正确的路由、参数接收（DTO 定义）、日志记录以及符合要求的成功响应。对于如“真实查询 T+3 数据并发短信”等深水区业务，允许使用 TODO 和打印日志 Mock 代替。

## Decisions

- **DTO 设计**：将参考 Go 侧发送的数据体（如 `{"calcDate":"2026-05-15","triggerSource":"scheduler"}` 等），在 Java 侧相应的包下建立 DTO，如 `PenaltyCalculateRequest`、`RepaymentReminderRequest` 等，以便强类型接收参数。
- **重命名空实现**：将现有的 `PostLoanController.triggerOverdueInspection` 重命名/映射为 `/overdue/scan`，以此直接迎合 `batch-service` 的需求。

## Risks / Trade-offs

- 因为只做 Mock 级别的接口打通，后续真正实现业务时需要二次开发，但目前保障了调度框架的正常闭环，避免了“找不到端点”带来的微服务健康度降低。
