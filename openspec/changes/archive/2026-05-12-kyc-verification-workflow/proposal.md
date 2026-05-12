## Why

为了满足金融信贷业务的合规要求，并为风控模型（Agent）提供更丰富、准确的用户多维数据，我们需要引入完整的 KYC (Know Your Customer) 认证流程。缺乏实名认证、活体检测及收款账户绑定的用户将带来极大的欺诈风险与资金流失风险，因此必须在授信与借款前强制拦截。

## What Changes

- **新增 KYC 三步认证 API**：
  - 步骤一：录入用户基础信息（月收入、出生日期、居住地、职业）。
  - 步骤二：上传身份证正反面，调用大模型（LLM）进行证件 OCR 解析，自动提取身份证号、姓名和年龄落库；解析成功后对接人脸活体检测验证本人身份。
  - 步骤三：绑定收款方式（支付宝及绑定手机号，或银行卡号）。
- **拦截机制**：在授信申请与借款申请链路的入口增加 KYC 状态校验，未完成认证者直接拦截并返回“尚未通过kyc认证”。
- **风控数据对接**：将 KYC 环节获取的用户基础画像与实名信息全量对接到风控 Agent 的上下文提示词中，辅助大模型进行信贷裁决。

## Capabilities

### New Capabilities
- `user-kyc-authentication`: 定义三步 KYC 认证的完整生命周期及活体、OCR大模型解析的业务规则。

### Modified Capabilities
- `microservice-user`: 扩充用户模型实体，持久化 KYC 数据（基础信息、OCR提取结果、绑卡信息等），并暴露 KYC 相关 API。
- `microservice-credit-risk`: 在 `applyCredit` 接口前置增加 KYC 认证状态的强制校验；调用 Agent 时将 KYC 字段注入决策请求参数中。
- `microservice-loan-application`: 在 `applyLoan` 借款前置增加 KYC 认证状态的强制校验。

## Impact

- **API 变更**：新增 `/api/app/user/kyc/step1`, `/api/app/user/kyc/step2`, `/api/app/user/kyc/step3` 等接口。
- **数据库表结构**：`cf_user` 表需增加 KYC 相关字段（收入、职业、收款账号等），或抽取独立的 `cf_user_kyc` 表。
- **外部依赖**：新增活体检测第三方服务对接；需将现有的 LLM 能力复用于图片内容解析（OCR/Vision）。
- **授信/借款链路**：强制阻断未认证用户的授信与借款行为。
