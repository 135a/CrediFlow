## 1. 核心控制器改造与文档补充 (BFF & System)

- [x] 1.1 `AdminLoanApplicationController.java`: 补充类和方法的详细 Javadoc；替换入参中的 `Map<String, Object>` 为具体的 DTO 类；在关键节点补充 `log.info` 和异常处理。
- [x] 1.2 `SystemAdminController.java`: 修复被无端注释的 `forceApprove` 代码逻辑，添加解释性注释和 TODO 标记；补充全类的 Javadoc 和访问日志记录。

## 2. 借款服务类可读性与长方法重构 (Loan Module)

- [x] 2.1 `LoanApplicationServiceImpl.java`: 修正含糊不清的变量名，将 `gate` 变更为 `eligibilityInfo`，`idmpToken` 变更为 `idempotencyToken`。
- [x] 2.2 `LoanApplicationServiceImpl.java`: 将魔法数字（如期数 3, 6, 12）抽取为静态不可变集合 `ALLOWED_TERMS`，并对过长的主流程方法 `applyLoan` 实施分段抽取重构，提高阅读体验。
- [x] 2.3 `LoanApplicationServiceImpl.java`: 统一组织类的 import，消除由于自动引入导致的乱序现象和全限定类名。

## 3. 资金系统与还款计划优化 (Fund Module)

- [x] 3.1 `RepaymentPlanServiceImpl.java`: 消除硬编码的魔法数字，尤其是 `0.05` 的利率数值，将其声明为 `private static final BigDecimal DEFAULT_MONTHLY_INTEREST_RATE` 并补充计息语义说明注释。

## 4. 用户与实名模块代码治理 (User Module)

- [x] 4.1 `RealnameVerificationService.java`: 找到现存的空 `catch` 代码块，统一加入 `log.warn` 来处理吞没的异常。
- [x] 4.2 `RealnameVerificationService.java`: 提取长方法，把魔法数字和未受控的依赖调用用私有辅助方法封装起来。
- [x] 4.3 `UserController.java` 及 `KycV2ServiceImpl.java`: 将不明就里的缩写 `rn` 还原为 `realName`，`idc` 还原为 `idCardNo`，并在方法之间留出合理的空行。

## 5. 公共基础设施的噪音清理 (Common Module)

- [x] 5.1 `ErrorCode.java`: 批量清除每行枚举定义尾部的纯复述性中英双语注释，消除视觉噪音。
- [x] 5.2 `InternalAuthFilter.java`: 清理多余未使用的 import 声明，并给 `Long.parseLong` 等高危转型调用补充必要的 try-catch 安全网。

## 6. 全局构建与验证

- [x] 6.1 运行 `mvn clean compile` 验证整个项目经过大范围重构后依然无编译错误。
