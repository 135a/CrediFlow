## ADDED Requirements

### Requirement: MySQL 作为权威业务存储

核心业务数据 MUST 持久化在 MySQL；跨服务一致性通过本地事务 + 可靠消息/幂等补偿组合实现；关键写路径 MUST 具备幂等键。

#### Scenario: 借据创建幂等

- **WHEN** 同一放款幂等键重复提交
- **THEN** 数据库状态 MUST 仅反映一次成功借据创建

### Requirement: Redis 用途与分布式锁

Redis MUST 用于热点缓存与分布式锁；分布式锁 MUST 具备 TTL 与锁值校验以防止误删；锁粒度 MUST 以避免长时间阻塞为原则。

#### Scenario: 获取锁失败快速返回

- **WHEN** 某资源锁已被占用且等待超过配置阈值
- **THEN** 调用方 MUST 收到可重试错误且 MUST NOT 无限阻塞

### Requirement: 单库架构且不采用分库分表

系统 MUST 使用 **单一 MySQL 逻辑库** 承载核心业务表；**MUST NOT** 引入 ShardingSphere 或其它分库分表中间件作为交付范围的一部分。

#### Scenario: 部署不包含分片中间件

- **WHEN** 部署演示或生产拓扑
- **THEN** 应用 MUST 仅配置单数据源连接串且 MUST NOT 依赖分片路由组件启动

### Requirement: 敏感数据加密

对监管敏感字段 MUST 使用应用层加密或数据库透明加密策略之一；密钥 MUST 外部化管理；备份文件 MUST 与生产同等加密策略要求。

#### Scenario: 密钥缺失拒绝启动

- **WHEN** 生产配置缺少必需的数据加密密钥环境变量
- **THEN** 应用 MUST 拒绝启动并 MUST 输出明确配置错误
