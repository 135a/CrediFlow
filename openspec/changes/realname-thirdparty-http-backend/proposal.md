## Why

信贷业务需要可审计、可替换供应商的实名核验能力。继续依赖 OCR 拍照与活体 Agent 会增加链路复杂度且不等于权威二要素核验。本变更改为由用户提交姓名与身份证号，经第三方实名 HTTP 接口远程核验一致性后落库，并以此作为绑卡、授信、借款、签约等流程的前置条件。范围仅限后端实现与规格更新，不包含前端与 OCR。

## What Changes

- 移除 KYC 流程中对「身份证影像上传 + Agent OCR + 人脸活体」的依赖；第二步改为接收用户提交的姓名与身份证号，调用可配置的第三方实名 HTTP 接口完成二要素核验。
- 在 `user-service`（及共享配置约定）中新增：Nacos 托管的实名服务商参数（地址、密钥、超时）、测试环境 Mock 开关（跳过真实 HTTP 直接返回核验成功）、统一 HTTP 客户端封装（签名/加密按供应商策略可插拔）、接口限流与防重复提交、实名状态与流水号等字段持久化。
- 核验结论驱动业务权限：未实名或实名失败用户不得进入绑卡、授信、借款、签合同等流程（由后续设计明确网关与业务层拦截点；本提案仅锁定需求边界）。
- **BREAKING**：现有依赖 `UserKycController` `/step2` 图片 Base64 与 Agent OCR/人脸的客户端契约将失效，需改为提交文本二要素的 API（前端不在本变更范围，但 API 为破坏性变更）。

## Capabilities

### New Capabilities

- `user-realname-verification`：第三方实名 HTTP 核验、配置外置（Nacos）、测试 Mock、限流与幂等、核验结果与流水号落库、异常与失败分支语义。

### Modified Capabilities

- `user-kyc-authentication`：KYC 第二步由「影像 OCR + 活体」改为「姓名 + 身份证二要素第三方核验」；状态机与步骤语义同步调整（仍可与第一步基础信息、第三步收款绑定组合）。
- `microservice-user`：「KYC 认证成功」相关场景由「影像活体识别」改为与实名核验结论及 `step_status` 一致的新描述。

## Impact

- **代码**：`java/user-service` 中 `UserKycController`、`UserKycServiceImpl`、`UserKyc` 实体及 `cf_user_kyc` 表结构；新增实名客户端与配置属性类；可选依赖 `crediflow-common` 中的加解密、幂等、限流组件。
- **配置**：Nacos DataId（或共享 `application`）增加实名相关键；各环境区分 Mock 与真实调用。
- **数据**：`cf_user_kyc` 增加或调整实名状态、核验时间、第三方流水号、证件掩码/密文字段（与现有敏感字段策略一致）。
- **其他微服务**：授信、借款、合同、绑卡服务在后续任务中对接「已实名」前置校验（本变更提案要求规格层体现依赖，具体拦截实现见 `tasks.md`）。
- **不在范围**：前端实现、OCR/视觉 Agent、真实公安直连。
