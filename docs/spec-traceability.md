# 规格符合性与追踪矩阵 (13.1 & 13.2)

## 需求-测试映射表 (Traceability Matrix)

本文档将 `openspec/changes/crediflow-micro-credit-system/specs` 下定义的关键规格映射至对应的实现与测试验收标准。

| Spec 来源 | 规格条目 | 实现位置 | 测试方法/验收标准 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | 统一路由转发与限流 | `infra/apisix/ROUTES-*.md` | 使用 JMeter 模拟高并发触发 429 限流响应 | ✅ |
| **Auth** | JWT Token 鉴权 | `APISIX jwt-auth plugin` | 携带/不携带 Token 访问 `/api/app/*` 校验 401 拦截 | ✅ |
| **Data Storage** | 敏感字段强制加密 (手机/身份证) | `CryptoTypeHandler.java` | 数据库直连查询结果为密文，接口返回明文/脱敏文 | ✅ |
| **Data Storage** | 不采用分库分表 | `docs/sharding-sphere.md` | `V1__init.sql` 验证表结构为单表模式 | ✅ |
| **Integration HTTP** | 服务间 Feign 调用带 Fallback | `AgentClient.java` (Credit) | 宕机 `data-agent` 验证触发降级返回固定额度 | ✅ |
| **Integration MQ** | 消息处理幂等性 | `RocketMQConfig.java` / Redis | 重复发送相同放款回调消息，只入账一次 | ✅ |
| **Loan Contract** | 合同签署最简合规（PDF+日志） | `LoanContractServiceImpl.java`| 前端触发签署，服务器 `/tmp/crediflow/contracts` 生成文件 | ✅ |
| **Data Agent** | NL2SQL 阻止写库与数据越权 | `data-agent/nl2sql.py` | 输入 `UPDATE cf_user SET...` 校验拦截 | ✅ |

## 高风险项验收脚本指引 (13.2)

### 1. 幂等性测试 (Idempotency)
针对**主动还款接口 (`/api/app/repayment/active-repay`)**:
- 编写 Python 或 JMeter 脚本，使用相同的 `idmpToken` 并发发起 10 个还款请求。
- **预期结果**: 有且仅有 1 个请求返回 HTTP 200 (SUCCESS)，其余 9 个请求应被 Redis 分布式锁拦截，返回业务错误码 "请勿重复提交还款"。

### 2. 网关无业务逻辑校验
- 审查 APISIX 配置文件。
- **验收标准**: 网关层除了 `jwt-auth`, `limit-count`, `request-id` 等通用中间件外，未编写任何 lua 脚本用于组装/转换核心借款数据。

### 3. 操作审计追踪
- **验收标准**: 对于 `system-service` 和后台敏感提额操作，验证 `cf_audit_log` 成功记录操作人员 ID、客户端 IP 以及变更前后的 JSON 载荷。日志系统中可通过 MDC 中的 `request_id` 100% 串联。
