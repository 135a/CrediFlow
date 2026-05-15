## Purpose

TBD

## Requirements

### Requirement: 最简合规与协议展示

借款申请页面 MUST 提供“我已阅读并同意《个人借款协议》《征信授权书》”的明确勾选项；协议 MUST 提供可点击查看的链接。

#### Scenario: 用户同意并提交借款
- **WHEN** 用户勾选同意协议并提交借款申请
- **THEN** 系统 MUST 验证勾选状态，并拦截未勾选的请求

### Requirement: 协议生成与后台存证

借款合同服务 MUST 基于申请与授信结果生成协议的 PDF 文件；系统 MUST 留存“同意”操作日志与资金流水，作为合规凭证。

#### Scenario: 生成与存储 PDF
- **WHEN** 借款申请进入合同生成环节
- **THEN** 系统 MUST 生成包含借款人信息与借款要素的《个人借款协议》与《征信授权书》PDF 文件，并持久化存储

#### Scenario: 审计日志与流水记录
- **WHEN** 协议生成完成
- **THEN** 系统 MUST 记录用户签署（同意）该版本协议的操作审计日志（包含操作时间、IP、协议快照链接），并与资金流水绑定备查

### Requirement: 合同查询与链接访问

系统 MUST 提供按用户与申请单查询协议 PDF 链接的能力，供前端展示与后台管理调阅。

#### Scenario: 查询已生成协议
- **WHEN** 授权主体请求查询协议
- **THEN** 系统 MUST 返回该笔借款对应的有效 PDF 访问链接，且 MUST 记录查询审计

### Requirement: 电子合同的自动生成
系统 MUST 监听借款通过事件，并自动基于用户的借款要素和模板生成具有法律约束力的电子合同文件记录。

#### Scenario: 成功生成借款合同
- **WHEN** 接收到 `LOAN_APPROVED_EVENT` 消息
- **THEN** 系统 MUST 落库一条完整的借款合同记录（包含合同号、借款总额、用户签名指引等），并在处理完成后 MUST 向 MQ 投递 `CONTRACT_READY_EVENT` 消息

### Requirement: 内部签署接口返回强类型契约

`loan-contract-service` 提供的内部合同签署 HTTP 接口（`POST /api/internal/contract/sign` 及其对应服务方法）在成功响应体中 MUST 使用可序列化的强类型对象（如专用 DTO）承载业务结果，MUST NOT 以无约束的 `Map<String, Object>` 作为服务层返回类型；对外 JSON 字段名与取值语义 SHOULD 与改造前保持一致（至少包含业务状态 `status` 及可选的人类可读 `message`），以降低调用方迁移成本。

#### Scenario: 成功签署返回稳定字段
- **WHEN** 用户已完成协议勾选且签署流程成功结束（含幂等命中「已签署」）
- **THEN** HTTP 200 且 `Result` 的 `data` MUST 可被反序列化为明确类型，且 MUST 包含表示业务成功的 `status` 字段；`message` 字段 MAY 用于说明幂等或提示文案。

#### Scenario: 编译期可校验返回结构
- **WHEN** 开发者在合同模块内调用签署服务方法
- **THEN** 返回类型 MUST 为具名 Java 类型而非原始 `Map`，以便 IDE 与编译器校验字段访问。

