# loan-application-manual-review

## Purpose
定义借款环节的 Agent 人工审核辅助机制。

## ADDED Requirements

### Requirement: 借款环节 Agent 人工审核辅助
针对高风险流入借款人工审核队列的用户，系统 MUST 异步调用 Agent，为其生成专属的借款级风险评估“三件套”。

#### Scenario: Agent 生成借款建议
- **WHEN** 一笔借款申请进入 `PENDING_MANUAL_REVIEW`
- **THEN** Agent MUST 异步分析用户的额度使用率、提款频次及画像，输出借款专属的风险明细、违约/欺诈概率，以及借款审批建议（如：建议放行、建议拒绝）
