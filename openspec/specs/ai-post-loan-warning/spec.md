## ADDED Requirements

### Requirement: 智能贷后坏账预警
系统 MUST 能够定时拉取近期的逾期还款记录，并提交给 Python Agent，Agent MUST 结合模型分析用户的逾期特征，产出包含潜在坏账率与风险特征归因的预警报告。

#### Scenario: 生成每日逾期预警报告
- **WHEN** 每天凌晨批处理作业触发预警任务
- **THEN** 系统 MUST 收集新增逾期名单交由大模型分析，并生成包含高风险用户列表的预警报告，推送至后台管理系统
