## ADDED Requirements

### Requirement: 纯内存合同模拟生成
当借款申请在风控与业务端审核通过进入合同处理阶段时，系统 MUST 自动生成一份基于数据库表 (`cf_loan_contract`) 存储的模拟合同，而不调用外部 PDF 模板渲染。该合同 MUST 处于 `INIT` 状态，并包含合同编号、用户信息、借款信息与利率。

#### Scenario: 模拟生成借款合同
- **WHEN** 借款申请状态被置为 CONTRACT_PROCESSING
- **THEN** 系统 MUST 在 `cf_loan_contract` 表中生成一条 `INIT` 状态的合同记录，并能够通过查询接口返回该合同的 JSON 数据

### Requirement: 模拟签署合同
系统 MUST 暴露一个模拟的签署接口（例如 `/sign`），允许调用方传入用户和合同关联信息确认签约。调用后，合同状态 MUST 从 `INIT` 变更为 `SIGNED`。

#### Scenario: 成功模拟签约
- **WHEN** 客户端获取到 `INIT` 状态的合同后，调用签约接口确认同意
- **THEN** 系统 MUST 将该合同状态更新为 `SIGNED`，并视作签约环节全部完成

### Requirement: 状态机关联与防重复签署
如果合同已经处于 `SIGNED` 状态，系统 MUST 拒绝重复的签署请求。合同未 `SIGNED` 时，系统 MUST NOT 触发后续的放款或借据生成逻辑。

#### Scenario: 重复调用签约接口
- **WHEN** 客户端对同一份已经 `SIGNED` 的合同再次发起签约请求
- **THEN** 系统 MUST 拦截该请求并正常返回已签署或幂等成功响应

### Requirement: 预留未来扩展点
代码中 MUST 通过显式的 `TODO` 注释标明未来替换为真实逻辑的位置，包括：PDF 渲染调用、E-sign 网关调用、OSS 上传调用。

#### Scenario: 审阅代码结构
- **WHEN** 开发者检视合同的 generate 和 sign 逻辑
- **THEN** 必须能清晰看到指向后续基础设施接入的 TODO 标记
