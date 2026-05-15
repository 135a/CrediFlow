# CrediFlow 移除 Go 技术栈方案

> 方案日期：2026-05-15
> 目标：将 Go 代码全部用 Java（Spring Cloud Alibaba）等价替代，统一技术栈

---

## 一、背景与动机

### 1.1 当前状态

项目使用 Java + Go + Python 三语言，其中 Go 代码分布如下：

```
Go 代码 = 31 个 .go 文件 / 2 个服务 / 2 个 Docker 容器

batch/
├── batch-service/           ← Go 定时调度器 (13文件, 1容器)
│                            cron调度 + Redis锁 + 内网签名
│                            + 6个Job + HTTP调用Java服务
│
└── scheduler-go/            ← 已删除 (仅 .gitignore 残留)

fund/
└── fund-channel-gateway/    ← Go 资金网关 (18文件, 1容器)
                             Gin HTTP + Redis 幂等 + MQ桥接
                             + Sentinel熔断 + 多Provider路由
```

### 1.2 Java 侧对 Go 的依赖

13 个 Java 文件引用了 Go 服务：

| Java 模块 | 依赖方式 | 依赖的 Go 服务 |
|-----------|---------|---------------|
| fund-flow-service | Feign + MQ 消费 | fund-channel-gateway |
| repayment-service | Feign + MQ 消费 | fund-channel-gateway, batch-service |
| post-loan-service | MQ 消费 | fund-channel-gateway |
| common (事件类) | Javadoc 引用 | fund-channel-gateway |

### 1.3 移除动机

- 统一技术栈，降低运维复杂度
- 面试叙事更清晰：纯 Java 微服务 + Python AI 的定位
- Go 代码使用的每项技术，项目 Java 栈都有现成等价物
- 减少容器数量（从 13 个减到 12 个）

---

## 二、Go → Java 技术映射

Go 使用的每一项技术，项目 Java 栈都有成熟等价物：

| Go 技术 | 用途 | Java 等价物 | 项目中是否已在用 |
|---------|------|-----------|----------------|
| `robfig/cron` | Cron 定时调度 | `@Scheduled` (Spring) | ✅ `loan-application-service` |
| `go-redis SETNX` | 分布式锁 | `Redisson RLock` | ✅ `repayment-service` |
| `HMAC-SHA256` | 内网签名 | `InternalAuthFilter` | ✅ `crediflow-common` |
| `net/http` | HTTP 客户端 | `OpenFeign` | ✅ 全部模块 |
| `rocketmq-client-go` | MQ 发布 | `RocketMQTemplate` | ✅ `user-service` 等 |
| `sentinel-golang` | 熔断降级 | `Sentinel` | ✅ Spring Cloud Alibaba |
| `Gin` | HTTP 路由 | `Spring MVC` | ✅ 全部模块 |
| `goroutine` | 并发 | `@Async` / `CompletableFuture` | ✅ `credit-risk-service` |
| `os/signal` | 优雅退出 | `@PreDestroy` (Spring) | ✅ Spring Boot 内置 |
| 自定义 `/health` | 健康检查 | `Actuator` | ✅ Spring Boot 内置 |

---

## 三、batch-service 替代方案

### 3.1 当前 Go 实现分析

```
batch-service 架构:
┌──────────────────────────────────────────────────────────┐
│ main.go                                                  │
│   cron.New() + 6个Job注册 + HTTP管理接口(:9090)           │
│                                                          │
│ DeductJob ──GET──▶ repayment-service /due-today          │
│           ──POST─▶ fund-channel-gateway /withhold        │
│                                                          │
│ OverdueJob ──POST──▶ post-loan-service /overdue/scan     │
│ PenaltyJob ──POST──▶ post-loan-service /penalty/calculate│
│ ReminderJob ──POST──▶ user-service /notify/remind        │
│ NotificationJob ─POST──▶ user-service /notify/push       │
│ RiskDispatchJob ─POST──▶ data-agent /credit/evaluate     │
│                                                          │
│ 基础设施: Redis锁 + 内网签名 + 重试 + 任务日志             │
└──────────────────────────────────────────────────────────┘
```

### 3.2 替代原则

**每个 Job 下沉到它调用的 Java 服务内部，数据不再跨 HTTP 传输。**

```
Go 模式 (跨网络):
  batch-service(Go) ──HTTP──▶ Java Service ──MySQL──▶ 查数据 ──返回──▶ Go处理

Java 模式 (同进程):
  Java Service @Scheduled ──MySQL──▶ 查数据 ──直接处理
```

**优势**：
- 省一次 HTTP 调用（降低延迟）
- 不需要跨进程传输数据
- 天然分布式锁（Redisson，项目已有）
- 不需要维护额外的 batch 进程

### 3.3 逐 Job 替代方案

#### DeductJob → repayment-service

```java
// 在 repayment-service 新增 DeductScheduler.java

@Component
@Slf4j
public class DeductScheduler {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private DeductDispatcher deductDispatcher;  // 新的 Service，替代 Go 的 dispatch()

    @Scheduled(cron = "${crediflow.repayment.deduct-cron:0 0 2 * * ?}")
    public void executeDeduct() {
        String lockKey = "lock:batch:deduct:" + LocalDate.now().toString();
        RLock lock = redissonClient.getLock(lockKey);

        if (!lock.tryLock()) {
            log.info("DeductJob skipped: lock held by another instance");
            return;
        }

        try {
            // 查今天待代扣的还款计划（直接查 MySQL，不再通过 HTTP）
            List<RepaymentPlan> duePlans = repaymentPlanMapper.selectList(
                new LambdaQueryWrapper<RepaymentPlan>()
                    .in(RepaymentPlan::getStatus, "PENDING", "OVERDUE")
                    .le(RepaymentPlan::getDueDate, endOfToday())
                    .last("LIMIT " + maxBatch)
            );

            if (duePlans.isEmpty()) {
                log.info("DeductJob: no due plans today");
                return;
            }

            // 并发代扣受理
            deductDispatcher.dispatch(duePlans);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**Go 的并发代扣逻辑 (goroutine + channel 信号量) → Java 替代**：

```java
// DeductDispatcher.java — 替代 Go 的 dispatch() + withholdOne()

@Service
@Slf4j
public class DeductDispatcher {

    @Autowired
    private FundChannelGatewayClient gatewayClient;  // Feign 客户端（已有）

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public void dispatch(List<RepaymentPlan> duePlans) {
        // 原子计数器：替代 Go 的 atomic.AddInt64
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger circuitOpen = new AtomicInteger(0);
        AtomicInteger transportErr = new AtomicInteger(0);

        // CompletableFuture 替代 Go 的 goroutine + WaitGroup
        List<CompletableFuture<Void>> futures = duePlans.stream()
            .map(plan -> CompletableFuture.runAsync(() -> {
                withholdOne(plan, accepted, rejected, circuitOpen, transportErr);
            }, executor))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("DeductJob summary: total={} accepted={} rejected={} circuit={} transport={}",
            duePlans.size(), accepted.get(), rejected.get(), circuitOpen.get(), transportErr.get());
    }

    private void withholdOne(RepaymentPlan plan, AtomicInteger accepted, ...) {
        FundChannelRepayRequest req = buildRequest(plan);
        try {
            FundChannelRepayResponse resp = gatewayClient.withhold(req);
            switch (resp.getState()) {
                case "ACCEPTED" -> accepted.incrementAndGet();
                case "REJECTED" -> rejected.incrementAndGet();
                default -> rejected.incrementAndGet();
            }
        } catch (FeignException.ServiceUnavailable e) {
            circuitOpen.incrementAndGet();
        } catch (FeignException e) {
            transportErr.incrementAndGet();
        }
    }
}
```

**Go → Java 映射总结**：

| Go | Java |
|----|------|
| `sync.WaitGroup` | `CompletableFuture.allOf()` |
| `atomic.AddInt64` | `AtomicInteger.incrementAndGet()` |
| `goroutine` | `CompletableFuture.runAsync()` |
| `sem <- struct{}{}` (信号量) | `ExecutorService` 固定线程池 |
| `time.Since(start)` | `System.nanoTime()` / `StopWatch` |

#### OverdueJob → post-loan-service

```java
// 在 post-loan-service 新增 OverdueScheduler.java

@Component
@Slf4j
public class OverdueScheduler {

    @Autowired
    private RepaymentPlanClient repaymentPlanClient;  // Feign 调 repayment-service
    @Autowired
    private CollectionTaskMapper collectionTaskMapper;
    @Autowired
    private RedissonClient redissonClient;

    @Scheduled(cron = "${crediflow.post-loan.overdue-cron:0 30 2 * * ?}")
    public void executeOverdueScan() {
        // 1. Redis 分布式锁
        RLock lock = redissonClient.getLock("lock:batch:overdue:" + LocalDate.now());
        if (!lock.tryLock()) return;
        try {
            // 2. 查询所有昨日期满但未还的还款计划
            List<RepaymentPlan> overduePlans = repaymentPlanClient.getOverduePlans();

            // 3. 标记逾期 + 创建催收任务
            overduePlans.forEach(plan -> {
                collectionTaskMapper.insert(buildCollectionTask(plan));
            });

            log.info("OverdueJob completed: {} overdue plans found", overduePlans.size());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

#### PenaltyJob → post-loan-service

```java
// 在 post-loan-service 新增 PenaltyScheduler.java

@Scheduled(cron = "${crediflow.post-loan.penalty-cron:0 0 3 * * ?}")
public void executePenaltyCalculation() {
    RLock lock = redissonClient.getLock("lock:batch:penalty:" + LocalDate.now());
    if (!lock.tryLock()) return;
    try {
        List<RepaymentPlan> overduePlans = repaymentPlanClient.getActiveOverduePlans();
        overduePlans.forEach(plan -> {
            BigDecimal penalty = calculatePenalty(plan);
            repaymentPlanClient.updatePenalty(plan.getId(), penalty);
        });
        log.info("PenaltyJob completed: {} plans updated", overduePlans.size());
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

#### ReminderJob → user-service

```java
// 在 user-service 新增 ReminderScheduler.java

@Scheduled(cron = "${crediflow.user.reminder-cron:0 0 9 * * ?}")
public void executeRepaymentReminder() {
    RLock lock = redissonClient.getLock("lock:batch:reminder:" + LocalDate.now());
    if (!lock.tryLock()) return;
    try {
        LocalDate dueDate = LocalDate.now().plusDays(3);
        List<User> users = getUsersWithDueDate(dueDate);
        users.forEach(user -> notificationService.sendReminder(user, dueDate));
        log.info("ReminderJob completed: {} users reminded", users.size());
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

#### NotificationJob → user-service

```java
// 在 user-service 新增 NotificationScheduler.java

@Scheduled(cron = "${crediflow.user.notification-cron:0 */30 * * * ?}")
public void executeBatchPush() {
    RLock lock = redissonClient.getLock("lock:batch:notif:" +
        LocalDate.now() + ":" + LocalTime.now().getHour());
    if (!lock.tryLock()) return;
    try {
        notificationService.batchPush(List.of("OVERDUE_WARN", "SYSTEM_NOTICE"));
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

#### RiskDispatchJob → credit-risk-service

```java
// 在 credit-risk-service 新增 RiskDispatchScheduler.java

@Scheduled(cron = "${crediflow.credit.risk-dispatch-cron:0 0 4 * * ?}")
public void executeRiskDispatch() {
    RLock lock = redissonClient.getLock("lock:batch:risk_dispatch:" + LocalDate.now());
    if (!lock.tryLock()) return;
    try {
        List<CreditApplication> highRiskUsers = creditApplicationMapper.selectList(
            new LambdaQueryWrapper<CreditApplication>()
                .eq(CreditApplication::getRiskLevel, "HIGH")
        );
        highRiskUsers.forEach(app ->
            agentClient.evaluateCredit(app.getUserId())  // 调 Python Agent
        );
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

### 3.4 替代后的架构

```
替代前:
  batch-service(Go, 独立进程)
       │
       ├──HTTP──▶ repayment-service
       ├──HTTP──▶ post-loan-service
       ├──HTTP──▶ user-service
       └──HTTP──▶ data-agent(Python)

替代后 (无独立 batch 进程):
  repayment-service   @Scheduled DeductJob        ← 直接查自己的 MySQL
  post-loan-service   @Scheduled OverdueJob        ← 直接查自己的 MySQL
  post-loan-service   @Scheduled PenaltyJob        ← 直接查自己的 MySQL
  user-service        @Scheduled ReminderJob       ← 直接查自己的 MySQL
  user-service        @Scheduled NotificationJob   ← 直接查自己的 MySQL
  credit-risk-service @Scheduled RiskDispatchJob   ← 直接查自己的 MySQL
```

### 3.5 依赖注入：替代 Go 的 config 模块

Go batch-service 使用 `config.LoadConfig()` 从环境变量加载配置。Java 直接使用已有的 `application.yml`：

```yaml
# 各 Java 服务 application.yml 中新增定时任务配置

crediflow:
  repayment:
    deduct-cron: "0 0 2 * * ?"       # 每日 02:00
    deduct-concurrency: 8
    deduct-max-batch: 500
  post-loan:
    overdue-cron: "0 30 2 * * ?"     # 每日 02:30
    penalty-cron: "0 0 3 * * ?"      # 每日 03:00
  user:
    reminder-cron: "0 0 9 * * ?"     # 每日 09:00
    notification-cron: "0 */30 * * * ?"  # 每 30 分钟
  credit:
    risk-dispatch-cron: "0 0 4 * * ?"  # 每日 04:00
```

---

## 四、fund-channel-gateway 替代方案

### 4.1 当前 Go 实现分析

```
fund-channel-gateway 架构:
┌──────────────────────────────────────────────────────────┐
│ main.go (Gin HTTP :8090)                                 │
│                                                          │
│ 路由:                                                     │
│   GET  /health                    健康检查                │
│   GET  /ready                     就绪检查                │
│   POST /internal/v1/disburse      放款受理                │
│   POST /internal/v1/repay          还款受理               │
│   POST /internal/v1/withhold       代扣受理                │
│   POST /fund/callback/:providerId  资金方异步回调          │
│                                                          │
│ 核心组件:                                                 │
│   幂等 (Redis SETNX)                                   │
│   混合路由 (明确ProviderId || 默认Provider)              │
│   资金方适配 (MockProvider / HTTPProvider)              │
│   熔断 (Sentinel-golang)                                 │
│   签名/加密 (HMAC SHA256 / Cipher存根)                   │
│   审计 (结构化日志)                                       │
│   MQ桥接 (RocketMQ FUND_DISBURSED / REPAYMENT_SETTLED)    │
│   日志脱敏 (卡号/身份证正则掩码)                           │
└──────────────────────────────────────────────────────────┘
```

### 4.2 新建 Java 模块

在 `fund/` 目录下创建新的 Maven 模块 `fund-channel-gateway`（Java 版）：

```
fund/fund-channel-gateway/               ← 替代 Go 版（旧 Go 代码删除）
├── pom.xml
├── src/main/java/com/crediflow/gateway/
│   ├── FundChannelGatewayApplication.java
│   │
│   ├── controller/
│   │   ├── InternalController.java       ← /internal/v1/disburse, repay, withhold
│   │   └── CallbackController.java       ← /fund/callback/{providerId}
│   │
│   ├── dto/
│   │   ├── SubmitRequest.java            ← 受理请求（与 Go DTO 对齐）
│   │   ├── SubmitResponse.java           ← 受理响应
│   │   ├── CallbackRequest.java          ← 回调请求
│   │   └── CallbackResponse.java         ← 回调响应
│   │
│   ├── provider/
│   │   ├── FundProviderClient.java       ← Java interface (替代 Go interface)
│   │   ├── MockFundProvider.java         ← Mock 实现
│   │   ├── HttpFundProvider.java         ← 真实 HTTP 调用实现
│   │   └── ProviderRegistry.java         ← 路由选择
│   │
│   ├── idempotency/
│   │   └── IdempotencyService.java       ← Redisson SETNX
│   │
│   ├── mq/
│   │   └── BridgeEventPublisher.java     ← RocketMQTemplate
│   │
│   ├── breaker/
│   │   └── ProviderBreakerService.java   ← Sentinel @SentinelResource
│   │
│   ├── crypto/
│   │   ├── Signer.java                   ← HMAC-SHA256 签名
│   │   └── Cipher.java                   ← 加密接口（阶段0存根）
│   │
│   ├── audit/
│   │   └── AuditRecorder.java            ← @Aspect 审计切面
│   │
│   └── support/
│       ├── SensitiveDataMasker.java      ← 日志脱敏
│       └── TraceContext.java             ← TraceId 传递
│
└── src/main/resources/
    └── application.yml
```

### 4.3 核心组件 Java 化详解

#### Provider 接口（策略模式）

```java
// 替代 Go: provider/client.go FundProviderClient interface

public interface FundProviderClient {
    /** 资金方唯一标识 */
    String getProviderId();

    /** 提交资金操作 */
    SubmitResult submit(SubmitInput input);
}

// Mock 实现 — 替代 Go: provider/mock.go
@Component
public class MockFundProvider implements FundProviderClient {
    @Override
    public String getProviderId() { return "mockProviderA"; }

    @Override
    public SubmitResult submit(SubmitInput input) {
        // 模拟 2 秒延迟后返回 ACCEPTED
        return SubmitResult.builder()
            .state(ReceiptState.ACCEPTED)
            .gatewayRequestId("GW-" + UUID.randomUUID())
            .build();
    }
}

// HTTP 实现 — 替代 Go: provider/http.go
@Component
public class HttpFundProvider implements FundProviderClient {

    private final RestTemplate restTemplate;  // 或 WebClient

    @Override
    @SentinelResource(value = "fund-provider-http", fallback = "submitFallback")
    public SubmitResult submit(SubmitInput input) {
        // 1. 加密 payload
        String encrypted = cipher.encrypt(input.getPayload());

        // 2. 签名
        String signature = signer.sign(encrypted);

        // 3. POST 调用资金方
        ResponseEntity<SubmitResponse> response = restTemplate.postForEntity(
            providerUrl + "/v1/" + input.getOperation().name().toLowerCase(),
            buildRequest(encrypted, signature),
            SubmitResponse.class
        );

        // 4. 验签 + 映射结果
        return mapToSubmitResult(response.getBody());
    }
}
```

#### Provider 路由

```java
// 替代 Go: provider/registry.go

@Service
public class ProviderRegistry {

    @Autowired
    private List<FundProviderClient> providers;  // Spring 自动注入所有实现

    @Value("${fund.gateway.default-provider-id:mockProviderA}")
    private String defaultProviderId;

    /** 混合路由：显式指定 → 默认值 */
    public FundProviderClient resolve(String explicitProviderId) {
        if (StringUtils.hasText(explicitProviderId)) {
            return providers.stream()
                .filter(p -> p.getProviderId().equals(explicitProviderId))
                .findFirst()
                .orElseThrow(() -> new ProviderNotFoundException(explicitProviderId));
        }
        return providers.stream()
            .filter(p -> p.getProviderId().equals(defaultProviderId))
            .findFirst()
            .orElseThrow(() -> new ProviderNotFoundException(defaultProviderId));
    }
}
```

#### 幂等服务（Redisson 替代 Go Redis）

```java
// 替代 Go: idempotency/redis.go (redisStore.SETNX)

@Service
public class IdempotencyService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String PREFIX = "fund:gw:idmp:";

    /**
     * 尝试声明幂等键。
     * @return true 表示首次处理，false 表示重复请求
     */
    public boolean tryClaim(String providerId, OperationType op, String businessOrderNo) {
        String key = PREFIX + providerId + ":" + op.name().toLowerCase() + ":" + businessOrderNo;
        RBucket<String> bucket = redissonClient.getBucket(key);
        return bucket.setIfAbsent("claimed", Duration.ofHours(24));
    }

    /** 回调幂等检查 */
    public boolean tryClaimCallback(String providerId, String providerTxnNo) {
        String key = "fund:gw:cb:" + providerId + ":" + providerTxnNo;
        return redissonClient.getBucket(key).setIfAbsent("ack", Duration.ofHours(24));
    }
}
```

#### MQ 桥接事件发布

```java
// 替代 Go: mq/publisher.go

@Service
public class BridgeEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /** 放款终态桥接 */
    public void publishDisbursed(FundDisbursedTerminalEvent event) {
        rocketMQTemplate.syncSend(
            "FUND_DISBURSED_EVENT",
            MessageBuilder.withPayload(event).build()
        );
    }

    /** 还款/代扣终态桥接 */
    public void publishRepaymentSettled(RepaymentSettledEvent event) {
        rocketMQTemplate.syncSend(
            "REPAYMENT_SETTLED_EVENT",
            MessageBuilder.withPayload(event).build()
        );
    }
}
```

#### 熔断服务（Sentinel 替代 sentinel-golang）

```java
// 替代 Go: provider/breaker.go

@Service
public class ProviderBreakerService {

    @SentinelResource(
        value = "fund-provider-dispatch",
        fallback = "dispatchFallback",
        blockHandler = "dispatchBlockHandler"
    )
    public SubmitResult dispatch(SubmitInput input) {
        return providerRegistry.resolve(input.getProviderId()).submit(input);
    }

    /** 熔断打开时的降级处理 */
    public SubmitResult dispatchFallback(SubmitInput input, Throwable t) {
        log.warn("Fund provider circuit breaker fallback: {}", t.getMessage());
        return SubmitResult.builder()
            .state(ReceiptState.CIRCUIT_OPEN)
            .errorCode("PROVIDER_CIRCUIT_OPEN")
            .build();
    }
}
```

#### 审计切面

```java
// 替代 Go: audit/audit.go

@Aspect
@Component
public class AuditRecorder {

    @Around("execution(* com.crediflow.gateway.provider..*(..))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        Object result;

        try {
            result = joinPoint.proceed();
            log.info("[AUDIT] traceId={} method={} success elapsedMs={}",
                traceId, joinPoint.getSignature().getName(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[AUDIT] traceId={} method={} failed elapsedMs={}",
                traceId, joinPoint.getSignature().getName(),
                System.currentTimeMillis() - start, e);
            throw e;
        }
        return result;
    }
}
```

#### HTTP 控制器

```java
// 替代 Go: api/handler.go

@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private ProviderRegistry providerRegistry;
    @Autowired
    private AuditRecorder auditRecorder;

    @PostMapping("/disburse")
    public ResponseEntity<SubmitResponse> disburse(@RequestBody SubmitRequest request) {
        return submit(request, OperationType.DISBURSE);
    }

    @PostMapping("/repay")
    public ResponseEntity<SubmitResponse> repay(@RequestBody SubmitRequest request) {
        return submit(request, OperationType.REPAY);
    }

    @PostMapping("/withhold")
    public ResponseEntity<SubmitResponse> withhold(@RequestBody SubmitRequest request) {
        return submit(request, OperationType.WITHHOLD);
    }

    private ResponseEntity<SubmitResponse> submit(SubmitRequest request, OperationType op) {
        // 幂等检查
        if (!idempotencyService.tryClaim(
                request.getProviderId(), op, request.getBusinessOrderNo())) {
            SubmitResponse dupResp = new SubmitResponse();
            dupResp.setState("ACCEPTED");
            dupResp.setErrorCode("DUPLICATE_RECEIPT");
            return ResponseEntity.ok(dupResp);
        }

        // 路由到资金方
        FundProviderClient provider = providerRegistry.resolve(request.getProviderId());
        SubmitResult result = provider.submit(SubmitInput.from(request, op));

        // HTTP 状态码映射（与 Go 一致）
        return switch (result.getState()) {
            case ACCEPTED     -> ResponseEntity.accepted().body(SubmitResponse.from(result));
            case REJECTED     -> ResponseEntity.unprocessableEntity().body(SubmitResponse.from(result));
            case CIRCUIT_OPEN -> ResponseEntity.status(503).body(SubmitResponse.from(result));
            case RETRYABLE    -> ResponseEntity.status(502).body(SubmitResponse.from(result));
            default           -> ResponseEntity.ok(SubmitResponse.from(result));
        };
    }
}

@RestController
@RequestMapping("/fund")
public class CallbackController {

    @PostMapping("/callback/{providerId}")
    public CallbackResponse callback(
            @PathVariable String providerId,
            @RequestBody CallbackRequest request) {

        // 回调幂等检查
        if (!idempotencyService.tryClaimCallback(providerId, request.getProviderTxnNo())) {
            return CallbackResponse.success();
        }

        // 桥接 MQ 事件
        bridgeEventPublisher.publish(request);  // 异步发布到 FUND_DISBURSED_EVENT

        return CallbackResponse.success();
    }
}
```

### 4.4 API 契约不变

Java 替代后的 HTTP API 完全与 Go 版一致，现有 Feign 客户端几乎不需要改：

```java
// 现有的 Feign 客户端（fund-flow-service、repayment-service）
// 只需要改 url 指向新服务

@FeignClient(
    name = "fund-channel-gateway",  // Nacos 服务名不变
    url = "${fund.channel.gateway.url:http://fund-channel-gateway:8090}"
)
public interface FundChannelGatewayClient {
    @PostMapping("/internal/v1/disburse")
    FundChannelDisburseResponse disburse(@RequestBody FundChannelDisburseRequest request);

    @PostMapping("/internal/v1/repay")
    FundChannelRepayResponse repay(@RequestBody FundChannelRepayRequest request);

    @PostMapping("/internal/v1/withhold")
    FundChannelRepayResponse withhold(@RequestBody FundChannelRepayRequest request);
}
```

---

## 五、需要删除的内容

### 5.1 文件/目录清单

```
删除:
  batch/batch-service/                ← 整个 Go 模块 (13 文件)
  batch/scheduler-go/                 ← 残留目录
  fund/fund-channel-gateway/          ← 整个 Go 模块 (18 文件)
  batch/                              ← 变成空目录后删除

  docker-compose.yml:
    - batch-service 容器定义 (行 234-242)
    - fund-channel-gateway 容器定义 (行 244-256)
    - fund-flow-service 中对 fund-channel-gateway 的 depends_on

  .gitignore:
    - scheduler-go/bin/ 残留条目

  .env / .env.example:
    - batch-service 相关环境变量
    - fund-channel-gateway 相关环境变量（如果仅 Go 使用）
```

### 5.2 Java 代码修改清单

| 文件 | 修改内容 | 影响 |
|------|---------|------|
| `fund-flow-service/.../FundChannelGatewayClient.java` | url 指向新 Java 服务 | 无，API 不变 |
| `repayment-service/.../FundChannelGatewayClient.java` | url 指向新 Java 服务 | 无，API 不变 |
| `common/.../FundDisbursedTerminalEvent.java` | 更新 Javadoc（移除 Go 文件引用） | 仅注释 |
| `common/.../RepaymentSettledEvent.java` | 更新 Javadoc（移除 Go 文件引用） | 仅注释 |
| `common/.../MqConstants.java` | 更新注释 | 仅注释 |

### 5.3 新增内容

| 新增 | 位置 | 说明 |
|------|------|------|
| `fund-channel-gateway-java/` | `fund/` 下 | 新的 Java 微服务模块（替代 Go 网关） |
| 各服务 `*Scheduler.java` | 见 3.3 节 | 6 个 `@Scheduled` 方法 |
| `application.yml` 配置项 | 各服务 | cron 表达式配置 |

---

## 六、实施步骤

```
阶段 1: 替代 batch-service（无 Java 侧依赖变更）
  Step 1: repayment-service 新增 DeductScheduler
  Step 2: post-loan-service 新增 OverdueScheduler + PenaltyScheduler
  Step 3: user-service 新增 ReminderScheduler + NotificationScheduler
  Step 4: credit-risk-service 新增 RiskDispatchScheduler
  Step 5: docker-compose 移除 batch-service 容器
  Step 6: 删除 batch/ 目录
  ⏱ 预计: 无风险，纯新增代码，不影响现有流程

阶段 2: 新建 Java fund-channel-gateway
  Step 1: 创建 Maven 模块 fund/fund-channel-gateway-java
  Step 2: 实现核心组件 (Provider, 幂等, MQ, 熔断, 审计)
  Step 3: 单元测试 + 集成测试
  Step 4: docker-compose 新增容器，与 Go 版并行运行
  Step 5: 灰度切换：先切换 repayment-service 的 Feign URL
  Step 6: 验证 MQ 事件正常消费
  Step 7: 切换 fund-flow-service 的 Feign URL
  Step 8: 下线 Go 版容器，删除 Go 代码
  ⏱ 预计: 需要完整测试，建议采用灰度切换

阶段 3: 清理
  Step 1: 清理 .gitignore 中 Go 相关条目
  Step 2: 清理 .env 中 Go 专用环境变量
  Step 3: 更新文档（agent-architecture.md 等）
  Step 4: 更新 docker-compose.yml depends_on 关系
  ⏱ 预计: 30 分钟
```

---

## 七、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| fund-channel-gateway 是资金链路核心，出错影响资金安全 | 🔴 高 | 灰度切换，Go/Java 双版本并行运行验证 |
| 分布式定时任务重复执行 | 🟡 中 | Redisson 分布式锁（已在项目中使用） |
| MQ 事件格式不兼容 | 🟡 中 | Java 事件类已存在，格式不变 |
| Sentinel 熔断策略差异 | 🟢 低 | Spring Cloud Alibaba Sentinel 成熟度高于 sentinel-golang |
| HTTP 签名不兼容 | 🟢 低 | 使用 common 模块已有的 `InternalAuthFilter` |

---

## 八、最终效果

```
移除前:
  Java:   11 个微服务
  Go:     2 个微服务 (31 文件)
  Python: 2 个 Agent (冲突)
  容器:   13 个

移除后:
  Java:   12 个微服务 (+fund-channel-gateway-java)
  Python: 1 个 Agent (credit-agent, 整合后)
  Go:     0
  容器:   12 个

技术栈: Java (Spring Cloud Alibaba) + Python (FastAPI + LLM)
定位:   企业级微服务信贷系统 + AI 辅助风控
```

面试叙事：

> "整个系统基于 Java Spring Cloud Alibaba 微服务架构，统一技术栈降低了运维复杂度。
> 资金网关模块采用策略模式抽象了多资金方接入，用 Redisson 做幂等、Sentinel 做熔断、
> RocketMQ 做异步回调桥接——这些都是 Spring Cloud 生态的原生能力。
> 定时调度直接使用 Spring @Scheduled，结合 Redisson 分布式锁防止多实例重复执行。
> AI 风控模块用 Python FastAPI 独立部署，通过 Feign + Nacos 服务发现与 Java 集成。"

---

## 九、Go → Java 完整对照表

| Go 文件 | 功能 | Java 替代位置 |
|---------|------|--------------|
| `batch-service/main.go` | 调度入口 | 分散到各服务的 `*Scheduler.java` |
| `batch-service/config/config.go` | 环境变量配置 | 各服务 `application.yml` |
| `batch-service/lock/redis_lock.go` | Redis 分布式锁 | `Redisson RLock`（已有） |
| `batch-service/internalsign/sign.go` | HMAC-SHA256 签名 | `InternalAuthFilter`（已有） |
| `batch-service/reporter/job_log.go` | 任务日志 | `@Slf4j` + `log.info()` |
| `batch-service/jobs/deduct_job.go` | 代扣 Job | `repayment-service DeductScheduler` |
| `batch-service/jobs/overdue_job.go` | 逾期扫描 Job | `post-loan-service OverdueScheduler` |
| `batch-service/jobs/penalty_job.go` | 罚息计算 Job | `post-loan-service PenaltyScheduler` |
| `batch-service/jobs/reminder_job.go` | 还款提醒 Job | `user-service ReminderScheduler` |
| `batch-service/jobs/notification_job.go` | 消息推送 Job | `user-service NotificationScheduler` |
| `batch-service/jobs/risk_dispatch_job.go` | 风控派单 Job | `credit-risk-service RiskDispatchScheduler` |
| `batch-service/jobs/http_util.go` | HTTP 重试工具 | OpenFeign + Retryer |
| `batch-service/gateway/client.go` | 资金网关 HTTP 客户端 | 已有 `FundChannelGatewayClient` |
| `batch-service/repaymentapi/client.go` | 还款 API 客户端 | 已有 `RepaymentInternalController` |
| `fund-channel-gateway/main.go` | Gin 启动入口 | `FundChannelGatewayApplication.java` |
| `fund-channel-gateway/api/server.go` | HTTP 路由注册 | Spring MVC `@RestController` |
| `fund-channel-gateway/api/handler.go` | 请求处理器 | `InternalController.java` |
| `fund-channel-gateway/api/middleware.go` | 中间件(签名/追踪/恢复) | `InternalAuthFilter` + `RequestTraceFilter`（已有） |
| `fund-channel-gateway/api/dto.go` | HTTP DTO | Java DTO（已有部分，补齐其余） |
| `fund-channel-gateway/provider/client.go` | Provider 接口 | `FundProviderClient.java` interface |
| `fund-channel-gateway/provider/mock.go` | Mock Provider | `MockFundProvider.java` |
| `fund-channel-gateway/provider/http.go` | HTTP Provider | `HttpFundProvider.java` |
| `fund-channel-gateway/provider/registry.go` | Provider 注册中心 | `ProviderRegistry.java` |
| `fund-channel-gateway/provider/crypto.go` | 签名/加密 | `Signer.java` + `Cipher.java` |
| `fund-channel-gateway/provider/breaker.go` | Sentinel 熔断器 | `@SentinelResource` 注解 |
| `fund-channel-gateway/idempotency/redis.go` | Redis 幂等 | `IdempotencyService.java` (Redisson) |
| `fund-channel-gateway/mq/events.go` | MQ 事件定义 | 已有 Java 事件类 |
| `fund-channel-gateway/mq/publisher.go` | RocketMQ 发布 | `BridgeEventPublisher.java` (RocketMQTemplate) |
| `fund-channel-gateway/audit/audit.go` | 审计记录 | `AuditRecorder.java` (`@Aspect`) |
| `fund-channel-gateway/logger/logger.go` | 日志脱敏 | `SensitiveDataMasker.java` |
| `fund-channel-gateway/config/config.go` | 配置加载 | Spring `application.yml` + `@ConfigurationProperties` |
