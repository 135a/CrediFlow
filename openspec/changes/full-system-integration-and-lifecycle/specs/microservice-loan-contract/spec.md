## ADDED Requirements

### Requirement: 电子合同的自动生成
系统 MUST 监听借款通过事件，并自动基于用户的借款要素和模板生成具有法律约束力的电子合同文件记录。

#### Scenario: 成功生成借款合同
- **WHEN** 接收到 `LOAN_APPROVED_EVENT` 消息
- **THEN** 系统 MUST 落库一条完整的借款合同记录（包含合同号、借款总额、用户签名指引等），并在处理完成后 MUST 向 MQ 投递 `CONTRACT_READY_EVENT` 消息
