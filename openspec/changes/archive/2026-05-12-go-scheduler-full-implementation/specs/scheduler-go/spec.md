## ADDED Requirements

### Requirement: Go 分布式调度服务完整实现

batch-service MUST 支持 Redis 分布式锁防止多实例重复执行；MUST 实现全部 6 类定时任务；MUST 提供 HTTP 管理接口用于健康检查和手动触发。

#### Scenario: 多实例部署不重复执行

- **WHEN** 两个 batch-service 实例同时到达代扣触发时间
- **THEN** 仅有一个实例 MUST 成功获取锁并执行任务，另一个 MUST 跳过
