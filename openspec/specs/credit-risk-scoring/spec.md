# credit-risk-scoring

## Purpose

定义授信机审四维加权评分模型、风险等级划分与可追溯落库规则，作为 `credit-risk-service` 内确定性计算核心；与 Agent 柔性闸门及对话升级解耦。

## ADDED Requirements

### Requirement: 四维加权总分与风险等级

系统 MUST 计算 `S1`（基础信息，0~100）、`S2`（历史履约，0~100）、`S3`（借贷行为，0~100）、`S4`（设备环境，0~100），并 MUST 按可配置权重计算 `TotalScore = w1·S1 + w2·S2 + w3·S3 + w4·S4`；默认 `w1=0.2, w2=0.4, w3=0.2, w4=0.2`。系统 MUST 将 `TotalScore` 映射为 `risk_level`：`LOW`（`TotalScore ≥ threshold_low`，默认 85）、`MEDIUM`（`threshold_medium ≤ TotalScore < threshold_low`，默认 60~84）、`HIGH`（`TotalScore < threshold_medium`）。`threshold_*` 与权重 MUST 来自 Nacos 并可审计版本号。

#### Scenario: 权重热更新

- **WHEN** 运维在 Nacos 调整 `w2` 从 0.4 到 0.35 并发布新版本
- **THEN** 新申请评分 MUST 使用新版本权重；旧申请 MUST 保留历史 `rules_version` 不可被改写

#### Scenario: 边界分数

- **WHEN** `TotalScore` 恰好等于 `threshold_low`
- **THEN** 系统 MUST 归类为 `LOW`

### Requirement: S1 基础信息分规则（初版可配置）

系统 MUST 按以下可落地子规则计算 `S1`（满分 100，可为扣分制组合，细则以 `rules_version` 配置为准）：年龄合规 18~55 加分；`kyc_passed=1`（实名+人脸）加分；近 3 个月资料稳定加分；年龄不在区间或黑名单命中 MUST 使 `S1` 落入低分或 0 分档；未完成 KYC 人脸 MUST 显著扣分（不得给满分）。

#### Scenario: 黑名单命中

- **WHEN** 身份证或手机号命中黑名单规则
- **THEN** `S1` MUST 为 0 且硬规则阶段 MUST 已拒绝或评分阶段 MUST 标记为不可授信

### Requirement: S2 历史履约分（最高权重维度）

系统 MUST 基于内部还款 / 逾期事实（占位数据源允许）计算 `S2`：无逾期倾向满分；轻微逾期扣分；严重逾期或坏账类标签 MUST 将 `S2` 压至低分或 0。

#### Scenario: 近 90 天轻微逾期

- **WHEN** 用户在近 90 天存在 1 次 1~3 天轻微逾期记录
- **THEN** `S2` MUST 较满分显著下降且 MUST 在 `cf_credit_score` 记录子码

### Requirement: S3 借贷行为分

系统 MUST 综合近 7 天借款申请次数、撤单 / 重复提交、额度使用率、多头标记等计算 `S3`。

#### Scenario: 高频申请

- **WHEN** 近 7 天借款申请次数超过配置阈值
- **THEN** `S3` MUST 下降且 MUST NOT 静默通过为满分

### Requirement: S4 设备环境分

系统 MUST 综合常用设备、常用 IP 城市、是否异地、是否高危时段、是否模拟器 / ROOT 等信号计算 `S4`。

#### Scenario: 模拟器或 ROOT

- **WHEN** 设备指纹被识别为模拟器或 ROOT 环境
- **THEN** `S4` MUST 为 0 且 MUST 触发额外安全审计事件

### Requirement: 评分持久化与不可抵赖

每一笔授信申请 MUST 关联一条 `cf_credit_score` 记录，包含 `s1..s4`、`total_score`、`risk_level`、`rules_version`、`computed_at`；MUST NOT 在拒绝后修改历史评分行（允许追加新行若重试产生新 `application_id`）。

#### Scenario: 审计回放

- **WHEN** 审计方按 `applicationId` 查询
- **THEN** 系统 MUST 返回完整四维分与版本号且 MUST NOT 返回身份证全号
