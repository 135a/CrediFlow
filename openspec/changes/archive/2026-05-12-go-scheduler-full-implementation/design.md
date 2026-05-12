## 场景上下文

Go batch-service 当前仅有 deduct_job 和 overdue_job，不具备分布式安全、批量处理、事件驱动等能力。

## 目标

- 补全设计文档中规划的全部 6 类定时任务
- 引入 Redis 分布式锁保障多实例安全
- 增加 HTTP 管理接口用于运维操控
- 增强现有 Job 的健壮性（重试、超时、日志）

## 决策

1. 使用 `go-redis/v9` 直连项目已有的 Redis 实例，通过 `SetNX` 实现分布式锁
2. 使用标准库 `net/http` 暴露轻量管理接口（/health、/jobs、/trigger）
3. 所有 Job 统一走"抢锁→执行→释放→上报"的标准流程
