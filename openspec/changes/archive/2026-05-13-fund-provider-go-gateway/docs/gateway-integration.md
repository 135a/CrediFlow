# 资金网关集成说明（补任务 8.3）

> 适用于 `fund-provider-go-gateway` change 的批次 3+ 落地内容。
> 本文档主要回答两个工程化问题：
> 1. Java 业务侧从 **受理（SUBMITTED）** 到 **终态（PAID / FAILED）** 之间的「中间态」如何查询与告警；
> 2. 还款链路、放款链路、定时代扣链路与网关之间的接口与事件矩阵，方便联调时对单。

## 1. 中间态查询策略

主动还款受理成功后，`cf_repayment_plan.status` 进入 `SUBMITTED`，并写入
`provider_id` / `gateway_request_id` / `submitted_at`。在收到 RocketMQ
`REPAYMENT_SETTLED_EVENT` 之前，业务侧 **MUST NOT** 重复发起还款。

### 1.1 用户侧

- APP / 小程序 GET `/api/app/repayment/plan/{planId}` 时，将 `SUBMITTED` 与
  `PAID` 区分展示，前端文案统一为「还款受理中」。
- 用户点击「确认还款」若命中 `SUBMITTED`，服务端直接抛 `"还款受理中，请稍后再试"`，
  绝对禁止再发一笔受理。`RepaymentServiceImpl#activeRepay` 已实现该闸口。

### 1.2 运营/客服侧

- 客服后台按 `gateway_request_id` 或 `business_order_no` 查询：
  - 后端 SHOULD 提供只读接口 `GET /api/internal/repayment/plan/by-gateway-req?gatewayRequestId=...`，
    后续批次补；
  - 紧急情况可直接到 Go 网关查询接口（待 Phase 1 引入 `GET /internal/v1/query`）。

### 1.3 巡检告警

- 每 5 分钟扫描 `cf_repayment_plan` 中 `status='SUBMITTED'` 且 `submitted_at < now() - 15min` 的记录：
  - **告警等级 P3**：> 15 分钟未收到终态；
  - **告警等级 P1**：> 60 分钟未收到终态，触发人工介入与对账。
- 巡检 Job 放在 `go/batch-service`，复用现有 `lock.Acquire` 分布式锁与
  `reporter.RunWithReport` 报告通道。**本文档为约定，巡检 Job 留待批次 4 落地。**

## 2. 接口与事件矩阵

| 链路 | 调用端 | 接口 / 事件 | 触发条件 | 终态来源 |
| --- | --- | --- | --- | --- |
| 放款 | `fund-flow-service` (ContractReadyConsumer) | `POST /internal/v1/disburse` | 消费 `CONTRACT_READY_EVENT` | Topic `FUND_DISBURSED_EVENT` (Go DisbursementEvent) |
| 主动还款 | `repayment-service` (RepaymentController) | `POST /internal/v1/repay` | 用户点击「确认还款」 + Redis 幂等锁 | Topic `REPAYMENT_SETTLED_EVENT` |
| 定时代扣 | `go/batch-service` (DeductJob) | `POST /internal/v1/withhold` | Cron 触发 | Topic `REPAYMENT_SETTLED_EVENT` (triggerSource=scheduler) |
| 网关 → 资金方 | `fund-channel-gateway` | 资金方 HTTPS API | 上述三个受理接口任一 | 资金方异步回调 `POST /fund/callback/:providerId` |

## 3. 关键防御点

- **bindCardId 必传 token**：所有内网请求体的 `bindCardId` 字段必须是绑卡引用 ID。
  网关侧（批次 2）接管真实卡号解密。Java 服务在任何场景都不得透传明文卡号。
- **签名一致**：内网签名同时支持 Java 风格（`X-Timestamp` 毫秒 + Base64 HMAC）
  与 Go 原生风格（`X-Internal-Timestamp` 秒 + `X-Internal-Nonce` + Hex HMAC）。
  目前 Java Feign 默认走前者，batch-service 也将复用 Java 风格保持一致。
- **幂等键**：
  - 受理侧：`(providerId, businessOrderNo, operation)`；
  - 回调/桥接事件：`gatewayRequestId`（Java 消费者）+ `providerTxnNo`（Go 网关）。

## 4. 集成测试 TODO（批次 4）

> 留作后续批次执行清单，本批次不阻塞主链路上线。

- [ ] T1：幂等 Token 重复点击 — 同一 `idmpToken` 并发 N 次，仅 1 次受理成功，其余抛
      `"请勿重复提交还款"`。
- [ ] T2：受理成功但终态延迟 — 模拟 Go 网关 5 分钟内不投递 `REPAYMENT_SETTLED_EVENT`，
      验证用户再次点击得到 `"还款受理中"` 而非二次受理。
- [ ] T3：终态失败回滚 — 网关投递 `FAILED` 终态，验证 `cf_repayment_plan.status` 从
      `SUBMITTED` 回到 `PENDING` 且释放 Redis 幂等锁可被下次主动还款复用。
- [ ] T4：定时代扣链路 — `go/batch-service` 触发 `DeductJob`，网关 mock provider
      回 `SUCCESS`，验证 Java repayment-service 消费 `REPAYMENT_SETTLED_EVENT`
      (`triggerSource=scheduler`) 并核销期次。
- [ ] T5：放款失败 — mock provider 回 `FAILED` 终态，验证 post-loan-service
      MUST NOT 进入「还款中」，fund-flow-service `cf_fund_flow.status` 置 `FAILED`。
