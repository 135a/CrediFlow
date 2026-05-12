## 1. 基础设施：Redis 分布式锁

- [x] 1.1 更新 `go.mod`，引入 `go-redis/v9` 依赖
- [x] 1.2 扩展 `config/config.go`，增加 Redis 连接配置及全部 Job 的 Cron/开关配置
- [x] 1.3 创建 `lock/redis_lock.go`，实现基于 Redis SetNX 的分布式锁（含 TTL 和锁值校验）

## 2. 新增定时任务

- [x] 2.1 创建 `jobs/penalty_job.go`，实现罚息计算任务（调用 post-loan-service 罚息接口）
- [x] 2.2 创建 `jobs/reminder_job.go`，实现到期还款提醒任务（调用 user-service 通知接口）
- [x] 2.3 创建 `jobs/risk_dispatch_job.go`，实现风控异步分发任务（调用 data-agent 风控评估接口）
- [x] 2.4 创建 `jobs/notification_job.go`，实现消息推送调度任务（统一调度各类通知）

## 3. 增强现有任务

- [x] 3.1 重构 `jobs/deduct_job.go`，加入分布式锁、HTTP 重试机制（最多 3 次指数退避）和结构化日志
- [x] 3.2 重构 `jobs/overdue_job.go`，加入分布式锁、HTTP 重试机制和结构化日志

## 4. 管理接口与日志

- [x] 4.1 在 `main.go` 中启动一个轻量 HTTP 服务器，暴露 `/health`、`/jobs` 和 `/trigger/:jobName` 管理接口
- [x] 4.2 创建 `reporter/job_log.go`，实现任务执行结果的结构化日志上报（含执行时长、状态、错误信息）
- [x] 4.3 在 `main.go` 中注册全部 6 个 Job 到 cron 调度器，并更新 `docker-compose.yml` 增加 Redis 依赖
