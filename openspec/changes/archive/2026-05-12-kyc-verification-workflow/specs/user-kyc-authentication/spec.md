## ADDED Requirements

### Requirement: 三步 KYC 认证生命周期
系统 MUST 提供一个多步骤的 KYC 认证流程，分为：基础信息填写、身份影像识别与活体验证、收款账号绑定。系统 MUST 记录用户的当前完成步骤，并支持用户在中断后恢复到上一个未完成的步骤。

#### Scenario: 用户首次发起 KYC
- **WHEN** 用户触发 KYC 流程，且 `step_status` 为 0
- **THEN** 系统 MUST 引导用户进入第一步（基础信息填写）

#### Scenario: 断点续传恢复 KYC
- **WHEN** 用户的 `step_status` 为 1（基础信息已填）并重新进入 KYC 流程
- **THEN** 系统 MUST 跳过第一步，直接引导进入第二步（影像与活体）

### Requirement: 大模型图像 OCR 解析
系统 MUST 接收用户上传的身份证正反面图片，并由系统内部多模态 Agent 解析出真实姓名、身份证号及年龄。

#### Scenario: 成功提取身份证信息
- **WHEN** Agent 成功从图片中解析出符合校验规则的身份信息
- **THEN** 系统 MUST 持久化姓名与身份证号，并计算存储真实年龄，随后允许进入活体检测

#### Scenario: 提取信息失败或模糊
- **WHEN** 上传的图片模糊导致 Agent 无法置信地提取关键信息
- **THEN** 系统 MUST 拒绝通过该步骤，并返回具体错误原因要求用户重新上传
