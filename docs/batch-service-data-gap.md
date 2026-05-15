# batch-service 数据流与端点对齐分析

> 分析日期：2026-05-15
> 分析对象：`batch/scheduler-go/` + `batch/batch-service/` 与 Java 微服务的接口对接状态

---

## 一、背景

项目 `batch/` 目录下存在三个 Go 定时调度相关组件：

```
batch/
├── scheduler-go/              ← 简化版调度器（冗余，待清理）
│   ├── cmd/scheduler/main.go  ← 空骨架（永久 sleep，无用）
│   └── main.go                ← 2 个 cron job 的简化版
│
└── batch-service/             ← 完整版调度器（架构正确，但依赖缺失）
    ├── main.go                ← 6 个 cron job + HTTP 管理接口
    ├── config/                ← 环境变量配置
    ├── lock/                  ← Redis 分布式锁
    ├── reporter/              ← 任务日志/上报
    ├── internalsign/          ← 内网签名
    ├── gateway/               ← Go 资金网关客户端
    ├── repaymentapi/          ← Java repayment-service 客户端
    └── jobs/                  ← 6 个定时任务实现
```

本报告聚焦 `batch-service` 每个 Job 所调用的 Java 端点是否真实存在、数据从何而来。

---

## 二、逐 Job 数据流追踪

### 2.1 DeductJob — ✅ 唯一完整闭环

```
cron: 每天 02:00

┌──────────────────┐                              ┌────────────────────────┐
│ batch-service/Go │  GET /due-today?limit=500    │ repayment-service/Java │
│                  │ ───────────────────────────▶ │ :8085                  │
│ DeductJob        │                              │                        │
│                  │◀───────────────────────────  │ RepaymentInternalCtrl  │
│                  │  Result<List<DueRepayment>>   │   ↓                    │
│                  │                              │ MySQL → repayment_plan │
│                  │  逐笔并发 (goroutine池)         │ WHERE status IN        │
│                  │  POST /internal/v1/withhold   │  ('PENDING','OVERDUE') │
│                  │ ───────────────────────────▶ │ AND due_date <= today  │
│                  │                              │ LIMIT 500              │
│                  │                            fund-channel-gateway/Go    │
│                  │◀───────────────────────────  :8090                    │
│                  │  ACCEPTED/REJECTED/熔断       │                        │
└──────────────────┘                              └────────────────────────┘
```

**Java 端点**: ✅ 存在
- 文件: `fund/repayment-service/.../controller/RepaymentInternalController.java`
- 路径: `GET /api/internal/repayment/due-today`
- Javadoc 明确标注: `"供 Go batch-service 在定时代扣前批量拉取"`
- 数据源: MySQL `cf_repayment_plan` 表，过滤 `PENDING`/`OVERDUE` 且 `due_date <= today`
- 返回结构: `DueRepaymentView` 包含 planId/userId/amount/bindCardId/businessOrderNo

**Go 侧客户端**: ✅ 存在
- 文件: `batch/batch-service/repaymentapi/client.go`
- 方法: `ListDueToday(traceID, limit)` → `[]DueRepayment`
- 带内网签名 (`internalsign.Apply`)、traceID、超时控制

**分布式锁**: ✅ Redis 锁 `lock:batch:deduct:YYYYMMDD`，TTL 30 分钟

**并发控制**: ✅ goroutine 信号量池 (可配置并发度，默认 8)，原子计数器跟踪 accepted/rejected/circuitOpen/transportErr

**结论**: 全链路闭环。这是整个 batch 模块中唯一能完整运行的 Job。

---

### 2.2 OverdueJob — ❌ 端点不存在

```
cron: 每天 02:30

batch-service/Go ──POST──▶ post-loan-service/Java
  OverdueJob               /api/internal/post-loan/overdue/scan
                                     │
                                     └── ❌ 这个端点不存在
```

**Go 侧调用的端点**: `POST /api/internal/post-loan/overdue/scan`

**Java 侧实际情况**:
- Java `PostLoanController` 唯一的端点是 `POST /api/internal/post-loan/trigger-overdue-inspection`
- 不仅端点名不匹配，而且这个 Java 端点的实现是**空的**：

```java
@PostMapping("/trigger-overdue-inspection")
public Result<Void> triggerOverdueInspection() {
    log.info("Triggering daily overdue inspection...");
    // 为简化演示，这里 Mock 业务逻辑完成
    log.info("Overdue inspection completed.");
    return Result.success(null);
}
```

- 注释自述: "应该调用 repayment-service 查出所有昨天到期但 PENDING 的计划，并在 post-loan 记录逾期记录，然后通知 repayment-service 修改状态和增加罚息。为简化演示，这里 Mock 业务逻辑完成。"

**结论**: Go 侧调用的端点不存在，Java 侧仅有的端点也是空实现。端到端不可用。

---

### 2.3 PenaltyJob — ❌ 端点不存在

```
cron: 每天 03:00

batch-service/Go ──POST──▶ post-loan-service/Java
  PenaltyJob               /api/internal/post-loan/penalty/calculate
                                     │
                                     └── ❌ 这个端点不存在
```

**Go 侧调用的端点**: `POST /api/internal/post-loan/penalty/calculate`

**请求体**: `{"calcDate":"2026-05-15","triggerSource":"scheduler"}`

**Java 侧实际情况**: `post-loan-service` 没有任何 `/penalty` 相关端点。

**结论**: 端点不存在。端到端不可用。

---

### 2.4 ReminderJob — ❌ 端点不存在

```
cron: 每天 09:00

batch-service/Go ──POST──▶ user-service/Java
  ReminderJob              /api/internal/user/notify/repayment-reminder
                                     │
                                     └── ❌ 这个端点不存在
```

**Go 侧调用的端点**: `POST /api/internal/user/notify/repayment-reminder`

**请求体**: `{"dueDate":"2026-05-18","reminderType":"PRE_DUE","triggerSource":"scheduler"}`

**Java 侧实际情况**: `user-service` 没有任何 `/notify` 相关端点。`UserServiceImpl` 中只有一行 TODO：`// TODO: 登录审计入库或发 MQ`，完全没有通知模块的实现。

**结论**: 端点不存在。端到端不可用。

---

### 2.5 NotificationJob — ❌ 端点不存在

```
cron: 每 30 分钟

batch-service/Go ──POST──▶ user-service/Java
  NotificationJob          /api/internal/user/notify/batch-push
                                     │
                                     └── ❌ 这个端点不存在
```

**Go 侧调用的端点**: `POST /api/internal/user/notify/batch-push`

**请求体**: `{"batchTime":"2026-05-15T10:30:00","types":["OVERDUE_WARN","SYSTEM_NOTICE"],"triggerSource":"scheduler"}`

**Java 侧实际情况**: 同上，`user-service` 没有任何通知推送端点。

**结论**: 端点不存在。端到端不可用。

---

### 2.6 RiskDispatchJob — ❓ 未确认

```
cron: 每天 04:00

batch-service/Go ──POST──▶ data-agent/Python
  RiskDispatchJob          /api/v1/credit/evaluate
                                     │
                                     └── ❓ 待确认
```

**Go 侧调用的端点**: `POST /api/v1/credit/evaluate`

**请求体**: `{"userId":0,"age":0,"income":0,"batchMode":true,"triggerSource":"scheduler"}`

注意请求体中的 userId/age/income 全是硬编码 0，注释写明 "实际场景中应先从 Java 端查询待评估用户列表"。所以即使 Python 端点存在，送过去的也是空数据。

**结论**: 端点的存在性待确认，但即使存在，请求体也是无效的空数据。

---

## 三、scheduler-go 的端点对齐情况

作为对比，`scheduler-go/main.go`（简化版）2 个 Job 的情况：

| Job | 调用的端点 | Java 状态 |
|-----|-----------|----------|
| 逾期检查 | `POST /api/internal/post-loan/trigger-overdue-inspection` | ✅ 端点存在，但**空实现** |
| 自动代扣 | `POST /api/internal/repayment/auto-deduct` | ❌ 端点不存在 |

`scheduler-go` 同样存在端点缺失问题，且没有任何分布式锁、重试、签名机制。

---

## 四、汇总

```
┌──────────────────────────────────────────────────────────────────┐
│                    batch 模块可用性全景                            │
├──────────┬───────────────────────────────────────┬────────────────┤
│ 组件      │ Job                   │ 端点存在?    │ 实际可用?     │
├──────────┼────────────────────────┼──────────────┼──────────────┤
│          │ DeductJob              │ ✅ 存在      │ ✅ 完整闭环   │
│          │ OverdueJob             │ ❌ 不存在    │ ❌           │
│ batch-   │ PenaltyJob             │ ❌ 不存在    │ ❌           │
│ service  │ ReminderJob            │ ❌ 不存在    │ ❌           │
│          │ NotificationJob        │ ❌ 不存在    │ ❌           │
│          │ RiskDispatchJob        │ ❓ 待确认    │ ❌ (空数据)  │
├──────────┼────────────────────────┼──────────────┼──────────────┤
│          │ overdue-inspection     │ ✅ 存在      │ ❌ (空实现)  │
│ scheduler│ auto-deduct            │ ❌ 不存在    │ ❌           │
│ -go      │                        │              │              │
├──────────┴────────────────────────┴──────────────┴──────────────┤
│ 可用率: 1/8 ≈ 12.5%                                              │
└──────────────────────────────────────────────────────────────────┘
```

---

## 五、Java 端需补充的端点清单

要使 `batch-service` 的 6 个 Job 全部可用，需在 Java 侧新建以下端点：

### 5.1 post-loan-service（2 个）

| 端点 | 方法 | 用途 | batch 对应 Job |
|------|------|------|---------------|
| `/api/internal/post-loan/overdue/scan` | POST | 扫描逾期计划，标记逾期状态，触发催收流程 | OverdueJob |
| `/api/internal/post-loan/penalty/calculate` | POST | 对所有逾期订单进行罚息累计计算 | PenaltyJob |

**注意**：Java 已有 `trigger-overdue-inspection`（空实现），需要重命名为 batch 调用的 `/overdue/scan` 或新增别名，并补充实际业务逻辑。

### 5.2 user-service（2 个）

| 端点 | 方法 | 用途 | batch 对应 Job |
|------|------|------|---------------|
| `/api/internal/user/notify/repayment-reminder` | POST | 查询 T+3 到期的计划，向用户推送还款提醒 | ReminderJob |
| `/api/internal/user/notify/batch-push` | POST | 统一调度待发送的用户通知（逾期提醒、系统公告等） | NotificationJob |

### 5.3 data-agent（1 个）

| 端点 | 方法 | 用途 | batch 对应 Job |
|------|------|------|---------------|
| `/api/v1/credit/evaluate` | POST | 批量风控评估（需先确认 Python 侧是否已有此端点） | RiskDispatchJob |

---

## 六、scheduler-go 清理建议

`scheduler-go` 整个目录可以安全删除：

1. **功能完全被 batch-service 覆盖** — batch-service 的 DeductJob + OverdueJob 已涵盖 scheduler-go 的 2 个 Job
2. **实现质量远不如 batch-service** — 无分布式锁、无重试、无签名、无管理接口
3. **`cmd/scheduler/main.go` 是空骨架** — 永久 sleep，从未被编译使用
4. **无法正常编译** — 两个 `main()` 函数冲突

---

## 七、结论

**batch-service 的 Go 层架构设计是正确的**：分布式锁 → 内网签名 → HTTP 调用 Java → 结果上报，这条链路清晰且工程化程度高。DeductJob 证明了这个架构可以跑通。

**但 5/6 的 Job 调用的 Java 端点不存在**，这不是 Go 侧的问题，而是 Java 侧的业务接口尚未实现。batch-service 本身不访问数据库，它作为纯调度层，所有数据来自 Java 服务的 HTTP 响应。当 Java 端点不存在时，这些 Job 运行时只会收到 HTTP 404。

**优先行动**：
1. 删除 `batch/scheduler-go/`（冗余）
2. 在 post-loan-service 补上 overdue/scan 和 penalty/calculate
3. 在 user-service 补上 notify/repayment-reminder 和 notify/batch-push
4. 确认 Python data-agent 的 evaluate 端点状态
