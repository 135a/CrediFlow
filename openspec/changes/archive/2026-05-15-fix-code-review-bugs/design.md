## Context

代码审查报告暴露出多个历史遗留技术债务，尤其是核心数据模型的命名冲突与跨服务环形调用，这对业务稳定性构成了严重威胁。同时，不规范的包名和代码存放位置加重了维护成本。

## Goals / Non-Goals

**Goals**:
- 修复 DTO 重叠问题，避免风控评估丢失 `applyAmount`。
- 重构 `loan-application-service` 包名至标准形式 `com.crediflow.loan`。
- 清除测试残留类（`Demo.java`、重复的 Application 类）。
- 整合加密工具类，统一为单数 `util` 包。
- 将直接调用 MyBatis 接口和返回 Entity 的逻辑用 DTO 和 Service 代理隔离开来。

**Non-Goals**:
- 暂时不全面处理 TODO 存根和细枝末节的拼写规范（将作为后续渐进式重构）。
- 暂不引入 Resilience4j 替换 Feign Fallback，仅从代码结构上切断逻辑环路。

## Decisions

- **DTO 统一**：直接删除 `credit` 模块下的 `LoanRiskEvaluateRequest.java`，让其自动引用 `common` 模块内的 `LoanRiskEvaluateRequest`（带 `applyAmount` 字段）。
- **工具类与包名统筹**：
  - 在 `common` 模块新建 `com.crediflow.common.util.HmacUtils`，收口散落的 `generateHmacSHA256` 逻辑。
  - 删除 `com.crediflow.common.utils` 文件夹，将内部的工具类迁移到 `util` 包。
- **Controller 瘦身**：
  - 涉及到返回 Entity 的接口，一律用对应的 ResponseDTO 包装，并只保留必需的字段。
  - 对于 `LoanApplicationController` 等直接进行 `.page()` 调用的情况，全部重构为由 Service 封装调用的方法。

## Risks / Trade-offs

- 批量修改包名（如 `com.crediflow.application` -> `com.crediflow.loan`）将引发较大的 Git 差异，但这对于模块的长远健康是值得的。
- 修复 Entity 直接暴露可能导致现有的部分直接强绑定内部字段的非标调用出错，需要谨慎转换 DTO。
