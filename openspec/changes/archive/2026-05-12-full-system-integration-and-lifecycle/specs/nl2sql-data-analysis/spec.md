## ADDED Requirements

### Requirement: 大模型自然语言转 SQL (NL2SQL) 能力
系统 MUST 允许授权运营人员通过自然语言输入查询指令，系统 MUST 利用 LLM 将自然语言解析为 SQL 查询，在只读沙盒数据库中执行并返回结构化数据。

#### Scenario: 运营人员查询当日放款总额
- **WHEN** 运营人员输入“帮我查一下今天一共放了多少笔贷款，总金额是多少”
- **THEN** 系统 MUST 解析出对应的 SQL，执行查询并返回汇总的单数与总金额

#### Scenario: 防止破坏性操作
- **WHEN** 运营人员输入类似于“删除所有逾期用户的记录”的破坏性指令
- **THEN** 系统 MUST 拒绝生成 UPDATE/DELETE 等 DML/DDL 语句，并 MUST 提示没有修改权限
