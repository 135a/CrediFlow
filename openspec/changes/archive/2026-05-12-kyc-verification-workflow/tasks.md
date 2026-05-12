## 1. 数据库与数据模型层搭建

- [x] 1.1 在 MySQL 中创建 `cf_user_kyc` 表，包含外键 `user_id`、三步认证所需字段（月收入、出生日期、居住地、职业、真实姓名、身份证号、年龄、是否完成活体、收款方式、收款账号）以及断点续传状态 `step_status`。
- [x] 1.2 在 `user-service` 中创建对应的 `UserKyc` 实体类并映射数据库字段。
- [x] 1.3 在 `user-service` 中创建 `UserKycMapper` 及其 MyBatis-Plus 基础服务类（Service & Impl）。

## 2. Python Agent 视觉处理能力

- [x] 2.1 在 `data-agent` 端新增处理身份证 OCR 的路由 API `/api/v1/agent/ocr`。
- [x] 2.2 在 `data-agent` 实现图片接收并调用多模态大模型解析出姓名、身份证号、年龄等 JSON 信息。
- [x] 2.3 （可选）增加一个基础的模拟人脸活体验证网关。

## 3. Java 端 KYC 认证业务流实现

- [x] 3.1 在 `user-service` 中新增 `UserKycController` 提供对外网关接口。
- [x] 3.2 实现 **Step 1（基础信息填写）**：接收并保存月收入、出生日期、居住地、职业，并将 `step_status` 推进为 1。
- [x] 3.3 实现 **Step 2（证件影像与活体）**：实现文件上传逻辑、调用 Python Agent OCR 接口提取身份信息，记录解析成功后更新 `step_status` 为 2。
- [x] 3.4 实现 **Step 3（收款方式绑定）**：保存用户选择的收款方式（支付宝/银行卡）和对应账号，并将 `step_status` 推进为 3。

## 4. 核心链路准入拦截与风控联动

- [x] 4.1 重构 `credit-risk-service` 中的 `applyCredit`：在开头拦截检查该用户对应的 `cf_user_kyc.step_status` 是否为 3，如果不是则抛出“尚未通过kyc认证”异常。
- [x] 4.2 重构 `loan-application-service` 中的 `applyLoan`：同样在入口拦截检查 KYC 状态。
- [x] 4.3 在 `credit-risk-service` 发起 Agent 决策请求前，将获取到的 KYC 真实年龄、职业、月收入等字段拼接到传递给大模型的 `userData` Context 中。
