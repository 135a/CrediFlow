# 合同与授信域架构及实现问题探索 (Explore 记录)

本文档整理自对 `loan-contract-service` 和 `credit-risk-service` 的一次架构巡检与探索记录。主要盘点了 11 个涉及边界路由、分布式事务、代码规范与数据完整性的核心问题，并为后续的修复（Change）提供理论支持。

## 1. 路由与边界的安全溃堤

### 问题：LoanContractController 暴露 `/api/app/`
- **现状**：`@RequestMapping("/api/app/loan-contract")`。在我们的 APISIX 网关规范中，`/api/app/**` 流量被路由至 `app-bff-service`。这意味着底层微服务使用该路径将无法接收到外部请求。
- **修复思路**：底层微服务不应暴露对外契约。改为 `/api/internal/contract`，由 BFF 通过内网 Feign 发起调用。

### 问题：CreditClient 伪装的 Internal 路径
- **现状**：Feign 客户端路径定义为 `@PostMapping("/api/app/credit/internal/quota/deduct")`。
- **风险**：由于路径不以 `/api/internal/` 开头，`InternalAuthFilter` 拦截器不会进行拦截，这意味着 HMAC 内网安全签名校验被完全绕过。若网关层未做强限制，这是一个高危越权漏洞。
- **修复思路**：统一更改为 `/api/internal/credit/quota/deduct`，同时需同步修改 `credit-risk-service` 中的暴露接口。

## 2. 经典的分布式事务与幂等灾难

### 问题：MQ 消费者事务风险 + 幂等缺失
在 `LoanApprovedConsumer` 的 `onMessage()` 中，发生了如下调用：
1. `generateContract(...)` (本地数据库写入)
2. `generateReceiptAndPlan(...)` (本地数据库写入)
3. `creditClient.deductQuota(req)` (远程 Feign 同步调用)

- **风险**：如果第三步 Feign 调用发生网络超时或额度不足而抛出异常，会导致 MQ 返回消费失败并触发重试。然而前两步的本地写入可能已经提交。重试时，如果缺乏**幂等性约束**，将会导致重复创建合同与借据，产生双份脏数据。
- **修复思路**：
  - 增加幂等校验：`generateContract` 和 `generateReceiptAndPlan` 执行前先查询是否存在记录。
  - 更优设计：考虑将额度扣减等动作改为基于事务消息或状态机驱动的异步补偿机制，避免本地长事务与远程调用混用。

### 问题：签署合同双入口冲突
- **现状**：HTTP 接口 `signContract()` 和 MQ 消费者 `generateContract()` 存在潜在执行重叠，可能导致同一笔贷款申请生成重复数据。
- **修复思路**：明确生命周期职责：MQ 消费者仅负责生成 `INIT` 状态的合同草稿；HTTP 接口仅负责改变合同状态并写入用户签署信息。

## 3. 数据一致性与系统健壮性

### 问题：数据库迁移缺失
- **现状**：`cf_loan_receipt`（借据）和 `cf_repayment_plan`（还款计划）表缺乏建表脚本，且 `cf_loan_contract` 缺少对应的 `contract_type` 列。
- **风险**：直接阻断应用启动或导致相关写入链路报 SQL 异常。
- **修复思路**：增加 Flyway 的 V2 迁移脚本补齐缺失的表与字段。

### 问题：状态枚举不一致
- **现状**：代码注释、Java Enum 逻辑、以及 SQL 默认值对于合同状态的定义相互矛盾（如 `INIT` 与 `GENERATED` 混用）。
- **修复思路**：对齐状态机流转为 `INIT` -> `SIGNED` -> `ARCHIVED`，同步修改数据库默认值。

### 问题：幽灵代码与资源泄漏
- **PdfGeneratorUtil**：零引用，将文件写入极易丢失的 `/tmp` 目录，并返回不可用的 `localhost` 硬编码地址，完全背离了 OSS 存储的设计初衷。建议直接删除。
- **无用配置**：`crediflow.contract.interest-rate` 未被任何代码引用，应清理。

## 4. 规范与债务
- **异常处理**：大量使用 `throw new RuntimeException()` 而非约定的 `BusinessException`，导致全局异常处理器无法进行友好的错误转译与拦截。
- **代码书写**：过度使用全限定名（如 `java.util.HashMap`）而非 import 语句，可读性极差。

---

**总结**：在架构演进的过程中，遗留的这批“单体思维”代码亟待重构，尤其是在跨服务调用的**一致性保障**和**接口鉴权**方面。接下来应考虑启动一个综合修复 Change 来安全地落地这些改动。
