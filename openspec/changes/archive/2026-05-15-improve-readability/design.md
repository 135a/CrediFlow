## Context

CrediFlow 系统由于长期的迭代和历史遗留问题，部分模块（如 `bff`, `system`）出现了核心类无日志、无 Javadoc，且使用了诸多“魔法数字”、“沉默异常”的情况。可读性较差，且重述代码逻辑的无用注释占比较高，不利于新加入的开发人员理解业务意图。

## Goals / Non-Goals

**Goals:**
- 为得分最低的类（特别是 `AdminLoanApplicationController`, `SystemAdminController`, `LoanApplicationServiceImpl`）增加说明性 Javadoc 和必要的控制台/文件日志。
- 将魔法数字抽取为静态常量或枚举类型，并为其赋予业务含义的注释。
- 重命名过于简化的缩写变量（如 `gate` 改为 `eligibilityInfo`, `rn` 改为 `realName`）。
- 梳理多处注释：删除重述代码执行过程的无效注释，增加解释“为什么这样做”的业务背景注释。
- 保证出现空 `catch` 的地方必须通过 `log.warn()` 输出潜在风险。

**Non-Goals:**
- 不改变任何现有接口的签名、路径或底层逻辑架构。
- 不引入新的第三方依赖或组件。

## Decisions

- **文档规范**：每个类的顶部必须包含 Javadoc，说明其角色、调用方和核心职责。
- **变量重命名与常量化**：提取分期天数、还款状态字面量等为 `private static final` 变量。
- **方法提取**：利用现代 IDE 的重构功能或者手工对动辄百行的长方法（如 `applyLoan`, `applyCredit`）进行分段提取（`extract method`），使主方法的流程控制一目了然。

## Risks / Trade-offs

- **重构导致合并冲突**：大面积重命名变量和提取方法会引入大量的代码变动。建议在低峰期进行。
- **被忽略的隐式依赖**：极少数情况下，系统内可能有地方利用反射或硬编码的名字访问，必须使用安全的重命名重构手段。
