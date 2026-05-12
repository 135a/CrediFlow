## Context

当前的信贷业务系统中缺乏对借款人真实身份、收入水平及有效收款账号的强校验（KYC 流程）。在推进自动授信与放款前，必须引入这套严密的 KYC 机制，通过三步走的引导流程采集并验证用户信息，同时利用系统中现有的 LLM/Agent 基础架构实现对身份证件的智能 OCR 解析。

## Goals / Non-Goals

**Goals:**
- 实现三步 KYC 流程的后端 API 支持与状态管理。
- 设计独立的 `cf_user_kyc` 数据表以隔离实名隐私信息，避免污染核心的 `cf_user` 表。
- 基于当前系统的多模态 LLM 基础能力（或独立视觉模型），实现身份证正反面的 OCR 数据提取。
- 建立全局路由/业务拦截，对所有需要放款或授信的操作前置校验 KYC 状态。

**Non-Goals:**
- 不包含真正对接公安联网的人脸比对系统（本版本将预留接口并采用模拟实现）。
- 不包含真正的支付宝/银联卡打款能力，仅实现账号信息的采集与绑定。

## Decisions

1. **KYC 数据存储策略**：
   - 决策：建立独立的 `cf_user_kyc` 表，以 `user_id` 为外键建立 1:1 关联。
   - 理由：KYC 包含高敏的 PII（个人身份信息）如身份证号、详细地址、收款卡号等，采用独立表便于后续实现集中加解密（如国密/AES），也降低了 `cf_user` 单表的体积。

2. **多步状态管理**：
   - 决策：在 `cf_user_kyc` 中增加 `step_status` 字段（0-未开始，1-基础信息完成，2-实名认证完成，3-全部完成）。
   - 理由：支持用户断点续传。用户中途退出后重新进入，可直接跳至未完成的步骤。

3. **OCR 与活体检测机制**：
   - 决策：在 `data-agent` 中新增 `/api/v1/agent/ocr` 接口，Java 端上传图片后，由 Python 端使用支持多模态的 LLM（如 Qwen-VL 等）解析并返回结构化 JSON (`name`, `id_no`, `age`)；人脸活体使用模拟鉴权策略返回通过。
   - 理由：复用现有 Python Agent 生态，最大化利用大模型能力减少传统 OCR 服务的引入成本。

4. **强制拦截与风控对接**：
   - 决策：在 `credit-risk-service` 和 `loan-application-service` 的入口 Service 层使用硬编码逻辑校验 `cf_user_kyc` 的状态。如果是 Agent 调用的接口，将 KYC 提取的真实年龄、职业、收入等信息拼接到 `nl2api` 或 `evaluate` 的 Context 中。

## Risks / Trade-offs

- **[Risk]** 多模态大模型 OCR 识别准确率可能受光线和照片质量影响。
  **[Mitigation]** 如果 OCR 解析失败，提供错误反馈并允许用户重新拍照；支持系统配置转为全手工输入模式（备用降级方案）。
- **[Risk]** 图片上传带来的带宽及存储压力。
  **[Mitigation]** 后端上传接口需对图片尺寸和格式（JPEG/PNG, <5MB）进行强校验，并异步上传至 OSS。

## Migration Plan

1. 在 MySQL 中执行 DDL 创建 `cf_user_kyc` 表。
2. 开发 `data-agent` 中的视觉解析能力及对应接口。
3. 更新 `user-service`，发布 KYC 三个步骤的业务 API。
4. 修改 `credit-risk` 及 `loan` 业务，部署拦截规则。
