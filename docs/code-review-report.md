# CrediFlow 项目代码审查报告

> 审查日期：2026-05-15
> 审查范围：全部 11 个 Java 模块、Go/Python 辅助服务，约 290 个 Java 源文件

---

## 项目架构概览

```
[App Client] ──HTTPS──▶ [APISIX Gateway]  (JWT 鉴权, X-User-Id 注入)
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
  [app-bff :8091]                   [admin-bff :8090]
              │                               │
              └───────────┬───────────────────┘
                          │ (Feign 同步调用)
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  [user-service :8081] ◀────────▶ [credit-risk-service :8082]   │
│       │                                │    │                   │
│       │                                │    └──▶ [Python AI Agent :8000]
│       │                                │                        │
│       ▼                                ▼                        │
│  [loan-application :8083] ────MQ────▶ [loan-contract :8084]    │
│                                            │                    │
│                                            ▼                    │
│                                     [fund-flow :8087]           │
│                                            │                    │
│                                            ▼                    │
│                                     [repayment :8085]           │
│                                            │                    │
│                                            ▼                    │
│                                     [post-loan :8086]           │
│                                                                 │
│  [system-service :8088]  (管理后台，独立模块)                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

通信方式：
  ────▶  同步 REST (OpenFeign + Nacos 服务发现)
  ──MQ──▶ 异步消息 (RocketMQ)
  ──▶  外部 HTTP (Go 资金网关)
```

### 模块清单

| 模块 | artifactId | 端口 | 数据库 | 消息队列 | 缓存 |
|------|-----------|------|--------|---------|------|
| Common 公共库 | `crediflow-common` | — | — | — | Redis |
| Admin BFF | `admin-bff-service` | 8090 | — | — | Redis |
| App BFF | `app-bff-service` | 8091 | — | — | Redis |
| 用户服务 | `user-service` | 8081 | MySQL | RocketMQ | Redis |
| 授信风控服务 | `credit-risk-service` | 8082 | MySQL | — | Redis |
| 借款申请服务 | `loan-application-service` | 8083 | MySQL | RocketMQ | Redis |
| 合同服务 | `loan-contract-service` | 8084 | MySQL | RocketMQ | Redis |
| 还款服务 | `repayment-service` | 8085 | MySQL | RocketMQ | Redis |
| 贷后服务 | `post-loan-service` | 8086 | MySQL | RocketMQ | Redis |
| 资金流程服务 | `fund-flow-service` | 8087 | MySQL | RocketMQ | Redis |
| 系统管理服务 | `system-service` | 8088 | MySQL | — | Redis |

### 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| Java | OpenJDK | 17 |
| Spring Boot | spring-boot-dependencies | 3.2.5 |
| Spring Cloud | spring-cloud-dependencies | 2023.0.1 |
| Spring Cloud Alibaba | spring-cloud-alibaba-dependencies | 2023.0.1.0 |
| 服务发现 | Nacos | 2.3.0 |
| API 网关 | Apache APISIX | 3.9.0 |
| ORM | MyBatis Plus | 3.5.7 |
| 数据库 | MySQL | 8.0 |
| 缓存/锁 | Redis 7 + Redisson | 3.21.0 |
| 消息队列 | Apache RocketMQ | 5.2.0 |
| 数据库迁移 | Flyway | (Spring Boot 管理) |
| HTTP 客户端 | OpenFeign + Spring Cloud LoadBalancer | — |
| 监控 | Micrometer + Prometheus | — |
| JWT | jjwt | 0.11.5 |
| 非 Java | Go 1.22+, Python 3.11+ (FastAPI) | — |

---

## 一、关键问题 (CRITICAL) — 功能性 Bug / 数据丢失

### 1.1 `LoanRiskEvaluateRequest` DTO 重复定义导致 `applyAmount` 字段静默丢失

**严重程度**: CRITICAL（影响风控评估正确性）

两个模块各自定义了同名但字段不同的 DTO：

| 模块 | 文件路径 | 字段 |
|------|---------|------|
| `common/api/credit/` | `LoanRiskEvaluateRequest.java` | `userId`, `applicationId`, **`applyAmount`** |
| `credit/dto/` | `LoanRiskEvaluateRequest.java` | `userId`, `applicationId` (**缺少** `applyAmount`) |

**调用链路**：

```
loan-application-service                       credit-risk-service
  LoanApplicationServiceImpl                     CreditInternalController
       │                                              │
       │  构造 evalRequest.setApplyAmount(amount)      │
       │  ↓                                           │
       │  Feign 调用 ──────────────────────────────────▶ 反序列化为 credit/dto 版本
       │                                              │  (applyAmount 字段不存在)
       │                                              │  ↓
       │                                              │  applyAmount 静默丢失
       │                                              │  ↓
       │                                              │  CreditServiceImpl.evaluateLoanRisk()
       │                                              │  在不知道申请金额的情况下做风控
```

**影响范围**: 所有贷款申请的风控评估都在缺少申请金额的情况下进行，可能批准本应拒绝的高风险申请。

**修复建议**:
1. 统一使用 `common/api/credit/` 中的 DTO（三字段版本），删除 `credit/dto/` 中的重复定义
2. 或者在 `credit/dto/` 版本中补充 `applyAmount` 字段
3. 添加单元测试验证 Feign 序列化/反序列化不丢失字段

---

### 1.2 `user-service` ↔ `credit-risk-service` 运行时循环依赖

**严重程度**: CRITICAL（级联故障风险）

```
user-service ────Feign───▶ credit-risk-service
     ▲                         │
     │                         │ (POST /api/internal/risk/blacklist/check)
     │                         │
     └────────Feign────────────┘
  (GET /api/internal/user/eligibility)
  (POST /api/internal/user/face/init)
```

两个服务之间存在同步调用环路。虽然通过 Feign Fallback 提供了一定容错，但：
- 如果 `user-service` 宕机 → `credit-risk-service` 调用失败 → 风控不可用
- 如果 `credit-risk-service` 宕机 → `user-service` 调用失败 → 黑名单检查不可用
- 循环调用增加延迟，降低系统整体可用性

**涉及的 Feign 客户端**：

| 调用方 | 被调用方 | Feign 客户端 |
|--------|---------|-------------|
| user-service | credit-risk-service | `RiskBlacklistClient` (含 Fallback) |
| credit-risk-service | user-service | `UserClient` |

**修复建议**:
1. 将黑名单检查改为异步（user-service 发 MQ，credit-risk-service 消费后回调）
2. 或将 eligibility 数据在 user-service 本地缓存，减少跨服务调用
3. 引入 Resilience4j 熔断器，设置合理的超时和熔断策略

---

## 二、高优先级 (HIGH) — 架构与设计问题

### 2.1 Loan 模块包名与目录不一致

**严重程度**: HIGH

Loan 模块目录为 `loan/loan-application-service`，但所有 Java 类使用包名 `com.crediflow.application.*`。

| 模块目录 | 期望包名 | 实际包名 |
|---------|---------|---------|
| `loan/loan-application-service` | `com.crediflow.loan.*` | `com.crediflow.application.*` |
| `contract/loan-contract-service` | `com.crediflow.contract.*` | `com.crediflow.contract.*` ✓ |
| `credit/credit-risk-service` | `com.crediflow.credit.*` | `com.crediflow.credit.*` ✓ |

其他所有模块都使用 `com.crediflow.{模块名}`，唯有 loan 模块偏离了此约定。"application" 一词过于通用，无法表达"借款申请"的业务含义。

**影响文件**（全部在 loan 模块下）：
- `com/crediflow/application/controller/*`
- `com/crediflow/application/entity/*`
- `com/crediflow/application/feign/*`
- `com/crediflow/application/mapper/*`
- `com/crediflow/application/scheduler/*`
- `com/crediflow/application/service/*`

---

### 2.2 `user-service` 中存在重复的 Application 启动类

**严重程度**: HIGH

user-service 中存在两个 Spring Boot 启动类：

| 类 | 包名 |
|----|------|
| `UserServiceApplication.java` | `com.crediflow.user`（主类，正确的包） |
| `UserServiceApplication.java` | `com.crediflow.users`（重复类，`users` 复数形式） |

`com.crediflow.users` 包下的类：
- `UserServiceApplication.java` — 带 `@MapperScan`，扫描所有 user mapper
- `messaging/LoanDisbursedEventPublisher.java` — 看起来是生产代码
- `messaging/DemoLoanDisbursedListener.java` — 文件名含 "Demo"

两个包共存会导致：
- 组件扫描混乱，Spring 可能加载两个 `UserServiceApplication`
- `DemoLoanDisbursedListener` 如果被加载，可能干扰正常的消息消费

---

### 2.3 持久层泄漏到 Controller 层

**严重程度**: HIGH

多处 Controller 直接使用 MyBatis-Plus 持久化 API，违反了分层架构原则。

**违规位置**：

| Controller | 文件 | 行号 | 违规内容 |
|-----------|------|------|---------|
| `LoanApplicationController` | `loan/.../controller/` | 26-36 | 在 Controller 中构建 `LambdaQueryWrapper` 并调用 `.page()` |
| `LoanApplicationController` | `loan/.../controller/` | 39-41 | 直接调用 `getById()` 返回 Entity |
| `LoanApplicationController` | `loan/.../controller/` | 47-67 | 直接调用 `updateById()`、`approve()` |
| `LoanAdminController` | `loan/.../controller/` | 33-53 | 构建 `LambdaQueryWrapper`，注入 `UserClient` 到 Controller |
| `CreditAdminController` | `credit/.../controller/` | 38 | 返回 `Page<CreditApplication>` 持久化分页对象 |
| `CreditAdminController` | `credit/.../controller/` | 73 | 返回 `Page<CreditReviewQueue>` 持久化分页对象 |
| `InternalUserController` | `user/.../controller/` | 30 | `new LambdaQueryWrapper<User>().eq(...)` |
| `UserKycController` | `user/.../controller/` | 46-66 | `saveOrUpdate()` 直接在 Controller 中调用 |

**正确做法**：Controller 只做参数校验和路由，业务逻辑和持久化操作封装在 Service 层。

---

### 2.4 Entity 直接作为 API 响应

**严重程度**: HIGH

多个 Controller 将 JPA/MyBatis Entity 直接返回给客户端，没有经过 DTO/VO 转换。

| Controller | 返回类型 | 风险 |
|-----------|---------|------|
| `CreditController.getActiveCredit()` | `CreditResult` (Entity) | 暴露数据库字段、密码等敏感信息风险 |
| `CreditController.applyCredit()` | `CreditApplication` (Entity) | 同上 |
| `CreditAdminController.listApplications()` | `Page<CreditApplication>` | 持久化分页对象暴露 |
| `CreditAdminController.listReviewQueue()` | `Page<CreditReviewQueue>` | 持久化分页对象暴露 |
| `LoanApplicationController` (多个方法) | `LoanApplication` (Entity) | 数据库结构泄漏 |
| `RepaymentController.pay()` | `RepaymentPlan` (Entity) | 数据库结构泄漏 |

**风险**：
- Entity 字段变更直接影响 API 契约
- 可能泄露内部实现细节（如 `createTime`、`updateTime`、`deleted` 等内部字段）
- 无法按前端需求定制返回数据格式
- Jackson 序列化可能触发懒加载、循环引用等问题

---

### 2.5 `generateHmacSHA256` 方法重复

**严重程度**: HIGH

common 模块内部存在两份完全相同的 HMAC-SHA256 实现：

| 文件 | 行号 |
|------|------|
| `common/.../filter/InternalAuthFilter.java` | 101-111 |
| `common/.../interceptor/InternalAuthRequestInterceptor.java` | 28-38 |

应提取到共享工具类（如 `HmacUtils`）中。

---

### 2.6 `util` vs `utils` 包名分裂

**严重程度**: HIGH

common 模块中存在两个功能相同但命名不一致的包：

```
common/crediflow-common/src/main/java/com/crediflow/common/
├── util/
│   └── MaskingUtil.java       ← 单数
└── utils/
    └── IdempotentUtils.java   ← 复数
```

应统一为一种命名（建议 `util`，与 Java 标准库 `java.util` 一致）。

---

### 2.7 `Demo.java` 遗留在生产代码目录

**严重程度**: HIGH

**文件**: `user/user-service/src/main/java/com/crediflow/Demo.java`

- 位于 `src/main/java`（生产代码目录），而非 `src/test/java`
- 所属包 `com.crediflow` 不属于任何模块约定
- 包含永不完成的并发测试代码（`CountDownLatch` 从未 countDown，死循环）

应删除或移至 test 目录。

---

## 三、中优先级 (MEDIUM) — 代码质量

### 3.1 状态字符串硬编码（全局性问题）

**严重程度**: MEDIUM

credit 模块使用了枚举（`CreditApplicationStatus`、`ReviewQueueStatus` 等），但其他模块大量使用原始字符串字面量。

**硬编码状态值分布**：

| 模块 | 硬编码状态值示例 |
|------|----------------|
| loan-application | `"PENDING"`, `"APPROVED"`, `"REJECTED"`, `"SUBMITTED"` |
| contract | `"INIT"`, `"SIGNED"`, `"PENDING"` |
| fund | `"PENDING"`, `"PROCESSING"`, `"SUCCESS"`, `"FAILED"` |
| repayment | `"PENDING"`, `"PAID"`, `"OVERDUE"`, `"ACTIVE"` |
| post-loan | `"PENDING"`, `"PROCESSING"`, `"COMPLETED"` |
| user | `"VERIFIED"`, `"UNBOUND"`, `"NOT_SUBMITTED"`, `"PASS"` |

以及更复杂的组合状态：
```
"PENDING_FACE", "PENDING_MANUAL_REVIEW", "CONTRACT_PROCESSING",
"CREDIT_CONTRACT", "LOAN_CONTRACT", "PENDING_HARD_RULES",
"PENDING_SCORING", "PENDING_ROUTING", "PENDING_SECONDARY_FACE"
```

**风险**：
- 无法编译期校验，拼写错误只能在运行时发现
- 状态值变更需要全局搜索替换，容易遗漏
- 无法通过 IDE 快速查看所有可能的状态值

---

### 3.2 长方法

**严重程度**: MEDIUM

| 方法 | 行数 | 文件 | 问题 |
|------|------|------|------|
| `applyCredit()` | 99 行 | `CreditServiceImpl.java` | 编排了资格检查→硬规则→评分→路由→额度生成，职责过多 |
| `applyLoan()` | 101 行 | `LoanApplicationServiceImpl.java` | 包含 4+ 个条件分支的状态路由逻辑 |
| `submitStep1()` | 81 行 | `KycV2ServiceImpl.java` | 校验+去重+资格检查+API调用+多分支结果处理 |
| `submitStep2()` | 97 行 | `RealnameVerificationService.java` | 校验+限流+幂等+配置检查+调用+多分支处理 |
| `activeRepay()` | 59 行 | `RepaymentServiceImpl.java` | Redis锁+验证+网关fallback+提交+3个catch块 |

建议将每个方法拆分为更小的私有方法，每个方法只做一件事。

---

### 3.3 日志缺失与不一致

**严重程度**: MEDIUM

**零日志的关键类**：
- `LoanApplicationServiceImpl.java`（loan 模块核心服务，101 行代码，零日志）
- `InternalAuthFilter.java`（内部认证过滤器，零日志）
- BFF 模块两个 Controller（零日志）
- `KycV2ServiceImpl.java`（KYC 核心服务，零日志）

**`@Slf4j` 使用不一致**：
- `credit-risk-service`、`fund-flow-service`、`repayment-service`、`post-loan-service`：良好，大部分使用 `@Slf4j`
- `user-service`：**11+ 个文件**使用手动 `LoggerFactory.getLogger()` 而非 `@Slf4j`
  - `BankCardBindingService.java`
  - `FaceVerificationService.java`
  - `FaceCallbackService.java`
  - `HttpBankCardProvider.java`
  - `HttpFaceVerifyProvider.java`
  - `BlacklistPolicy.java`
  - `GlobalExceptionHandler.java`
  - `KycPassedEventPublisher.java`
  - `InternalKycCallbackController.java`
  - `InternalKycTestController.java`
  - `RealnameAuditService.java`

---

### 3.4 异常静默吞没

**严重程度**: MEDIUM

**文件**: `RealnameVerificationService.java`

```java
// 行 79: Redis JSON 解析失败被静默忽略
catch (Exception ignored) { }  // 注释：fall through to re-verify

// 行 147: 幂等缓存存储失败被静默忽略
catch (Exception ignored) { }
```

至少应记录 warn 级别日志，便于排查问题。

**其他问题**：
- `ManualReviewAsyncService.insertQueueOnFailure()` (行 91)：仅 log 不 re-throw
- `CreditAppController` (BFF)：catch `Exception` 后返回硬编码 500，丢失原始错误信息

---

### 3.5 死代码

**严重程度**: MEDIUM

| 位置 | 问题描述 |
|------|---------|
| `IdempotentUtils.unlock()` | 方法定义完整但从未被调用。锁自然过期替代了主动释放 |
| `IdempotentAspect.java:73-80` | finally 块整段注释，且注释内容重复了两遍 |
| `RepaymentPlanServiceImpl.processRepayment()` | `@Deprecated` 标记，调用即抛异常 |
| `SystemAdminController` (行 23) | `// creditRiskService.forceApprove(id, userId);` 业务逻辑被注释，端点成了空操作 |

---

### 3.6 错误处理模式不一致

**严重程度**: MEDIUM

两种错误处理模式并存：

**模式 A**: 抛出 `BusinessException` → `GlobalExceptionHandler` 统一拦截
- 使用范围：大部分 Service 层
- `CreditServiceImpl`、`LoanApplicationServiceImpl`、`UserKycController` 等

**模式 B**: Controller 中 `try-catch` → 直接 `return Result.error(500, ...)`
- 使用范围：BFF 层 `CreditAppController` (行 31-36, 70-72)
- 绕过了 `GlobalExceptionHandler`，使用硬编码 HTTP 500

---

### 3.7 Lombok 使用不一致

**严重程度**: MEDIUM

手动 getter/setter（应使用 `@Data`）：
- `MqIdempotentLog.java` — 5 个字段，手动 getter/setter
- `LocalMessage.java` — 9 个字段，手动 getter/setter，注释写着 `// Getters and Setters`
- `Result.java` — 核心类，手动 getter/setter

其他所有 Entity 都使用 `@Data`，这三处偏离了项目规范。

---

### 3.8 BFF 返回类型无类型安全

**严重程度**: MEDIUM

所有 BFF Feign 客户端和 Controller 返回 `Result<Map<String, Object>>`：

```java
// app-bff: CreditClient.java
Result<Map<String, Object>> getCreditStatus(@RequestParam Long userId);
Result<Map<String, Object>> applyCredit(@RequestBody Map<String, Object> request);
Result<Map<String, Object>> getCreditQuota(@RequestParam Long userId);
Result<Map<String, Object>> faceCallback(@RequestBody Map<String, Object> request);

// admin-bff: LoanApplicationAdminClient.java
Result<Map<String, Object>> listLoanApplications(@RequestParam Map<String, Object> params);
Result<Map<String, Object>> getApplicationDetail(@RequestParam Long id);
Result<Map<String, Object>> approveLoan(@RequestBody Map<String, Object> request);
```

调用方完全失去编译期类型安全，必须用字符串 key 从 Map 中取值并手动转型。

---

### 3.9 `@EnableFeignClients` 扫描范围不一致

**严重程度**: MEDIUM

```java
// admin-bff: 严格限定扫描范围
@EnableFeignClients(basePackages = "com.crediflow.bff.admin.feign")

// app-bff: 扫描整个 classpath
@EnableFeignClients
```

应该统一为限定扫描范围的模式，避免误加载其他模块的 Feign 客户端。

---

### 3.10 魔法数字未外部化配置

**严重程度**: MEDIUM

以下 15+ 处硬编码值应移至 `application.yml`：

| 文件 | 行号 | 硬编码值 | 建议配置项 |
|------|------|---------|-----------|
| `CreditServiceImpl.java` | 105, 208 | `new BigDecimal("5000.00")` | `crediflow.credit.default-amount` |
| `CreditServiceImpl.java` | 211 | `30L * 24 * 3600 * 1000` | `crediflow.credit.expire-days` |
| `CreditServiceImpl.java` | 220-221 | `"1000.00"`, `"50000.00"` | `crediflow.credit.quota.{min,max}` |
| `CreditServiceImpl.java` | 223 | `60`, `100` | `crediflow.credit.score.{min,max}-clamp` |
| `CreditServiceImpl.java` | 246 | `3` (重试次数) | `crediflow.credit.deduct-retries` |
| `CreditServiceImpl.java` | 305-306 | `0.85`, `0.50` | `crediflow.credit.risk.{default,fraud}-probability` |
| `InternalAuthFilter.java` | 83 | `5 * 60 * 1000` | `crediflow.internal.replay-window-ms` |
| `RepaymentServiceImpl.java` | 99 | `10` (分钟) | `crediflow.repayment.lock-ttl-minutes` |
| `FundFlowServiceImpl.java` | 131-133 | `"10000.00"`, `"CNY"`, `12` | `crediflow.fund.defaults.{amount,currency,installments}` |
| `PostLoanServiceImpl.java` | 34, 40 | `3`, `7` (逾期天数) | `crediflow.post-loan.collection.{sms,phone}-threshold` |
| `LoanContractServiceImpl.java` | 86 | `"CTR"` (合同号前缀) | `crediflow.contract.number-prefix` |
| `CreditScoringEngineImpl.java` | 89 | `0.2, 0.4, 0.2, 0.2` | `crediflow.credit.scoring.weights.{s1,s2,s3,s4}` |
| `CreditScoringEngineImpl.java` | 115-118 | `85, 70, 90, 80` | 外部征信数据（整块逻辑待实现） |

---

### 3.11 中英文注释混用

**严重程度**: MEDIUM

| 模块 | 注释语言 |
|------|---------|
| loan-application | 全英文（如 `// Idempotent key deduplication`） |
| credit-risk | 主要是中文（如 `// 落库 cf_credit_application`） |
| fund-flow | 全英文 |
| contract | 中文 Javadoc |
| user | 混合（部分中文，部分英文） |
| common (`ErrorCode.java`) | 中英双语重复（如 `SUCCESS(200, "操作成功"), // 操作成功`） |

建议统一为中文（面向国内团队的中文项目）。

---

## 四、低优先级 (LOW) — 可读性与代码风格

### 4.1 未使用的 import

**文件**: `CreditServiceImpl.java:31`
```java
import java.util.Map;  // 文件中无任何 Map 类型引用
```

### 4.2 类声明格式断裂

**文件**: `FaceCallbackController.java:16-17`
```java
public class      // ← 异常换行
FaceCallbackController {
```
可编译但不符合 Java 代码格式规范。

### 4.3 import 与类声明间缺少空行

**文件**: `FeignTraceInterceptor.java:4-5`
import 语句结束后直接是类声明，缺少标准空行。

### 4.4 连续空行

**文件**: `RiskBlacklistInternalController.java:20-21, 40-41`
类体内存在连续空行（双空行）。

### 4.5 空的 `package-info.java`

以下 4 个文件存在但无实质内容：
- `user/bankcard/package-info.java`
- `user/face/package-info.java`
- `user/eligibility/package-info.java`
- `user/kyc/package-info.java`

### 4.6 缺失 Javadoc

| 文件 | 缺失内容 |
|------|---------|
| `LoanApplicationServiceImpl.java` | 类和所有公共方法均无 Javadoc |
| `LoanApplicationController.java` | 类和所有方法均无 Javadoc |
| `FundFlowServiceImpl.java` | 类和所有方法均无 Javadoc |
| `RepaymentServiceImpl.java` | 类级别无 Javadoc，`generatePlans()` 无 Javadoc |
| Entity 类 | 字段缺少业务含义说明（状态值含义等） |

### 4.7 `LoanContractApplication` 注解间夹 Javadoc

**文件**: `LoanContractApplication.java:14-21`
```java
@SpringBootApplication
/**            ← Javadoc 放在注解之间，语法正确但风格怪异
 * ...
 */
@EnableFeignClients
```

---

## 五、TODO 存根 — 核心功能未完成

### 按模块汇总

#### contract 模块 (3 个 TODO)

| 文件 | 行号 | 存根内容 |
|------|------|---------|
| `LoanContractServiceImpl.java` | 92 | `// TODO: 接入第三方电子签章平台（e签宝/法大大）` |
| `LoanContractServiceImpl.java` | 141 | `// TODO: 同步模拟，后续替换为前端跳电子签SDK → 回调更新SIGNED状态` |
| `LoanContractServiceImpl.java` | 163 | `// TODO: 替换为从OSS获取真实的合同下载链接或预览Token` |

**现状**: 合同创建后停留在 INIT 状态，PDF 未真实生成，签署流程为 mock，OSS 链接为假数据。

#### credit 模块 (3 个 TODO)

| 文件 | 行号 | 存根内容 |
|------|------|---------|
| `HardRuleEngineImpl.java` | 32 | `// TODO: Call user-service/loan-service to check blacklist, overdue` |
| `CreditServiceImpl.java` | 319 | `boolean hasOverdue = false; // TODO: check actual overdue loans` |
| `CreditScoringEngineImpl.java` | 81 | `// TODO: Fetch real external credit data (S1~S4 计算器目前为 Mock)` |

**现状**: 硬规则引擎只拒绝硬编码的 3 个 mock userId（999L, 888L, 777L），真实用户一律通过。评分引擎的 S1-S4 四个维度全部返回固定值（85, 70, 90, 80）。

#### user 模块 (3 个 TODO)

| 文件 | 行号 | 存根内容 |
|------|------|---------|
| `UserServiceImpl.java` | 29 | `// TODO: 对接真实短信网关（阿里云SMS/腾讯云短信）` |
| `UserServiceImpl.java` | 64 | `// TODO: 登录审计入库或发MQ` |
| `HttpFaceVerifyProvider.java` | 48 | `// TODO: 接入真实厂商时实现：applyTemplate → sign → POST → parse` |
| `HttpBankCardProvider.java` | 36 | `// TODO: 实现真实厂商协议` |

**现状**: 短信验证码接受任意输入（比较逻辑待实现），人脸识别和银行卡四要素验证仅 mock。

#### post-loan 模块 (1 个 TODO)

| 文件 | 行号 | 存根内容 |
|------|------|---------|
| `RepaymentSettledConsumer.java` | 71 | `log.info("TODO[profile-outbound] dispatch on-time-repayment tag userId=..."` |

**现状**: 用户画像出站通道仅打印日志，未实际发送标签。

---

## 六、问题优先级汇总

```
CRITICAL (影响功能正确性):  ██ 2
HIGH     (架构/设计问题):   ███████ 7
MEDIUM   (代码质量):        ███████████ 11
LOW      (可读性/风格):     ███████ 7
TODO     (功能存根):        ████ 4 个模块 / 11 条
```

### 建议修复顺序

1. **立即修复** — CRITICAL 级别
   - DTO 重复导致 `applyAmount` 丢失
   - 循环依赖风险评估

2. **近期修复** — HIGH 级别
   - 包名统一、删除重复 Application 类、删除 Demo.java
   - Controller 持久层泄漏重构
   - Entity → DTO 隔离

3. **迭代改善** — MEDIUM 级别
   - 状态枚举化
   - 长方法拆分
   - 日志补全、异常处理规范
   - 魔法数字配置化

4. **持续关注** — LOW 级别
   - 代码风格统一
   - Javadoc 补全

5. **业务里程碑** — TODO 存根
   - 电子签章对接
   - 外部征信接入
   - 短信/实名认证对接
