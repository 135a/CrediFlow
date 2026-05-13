# kyc-eligibility-gate Specification

## Purpose

定义 KYC 准入闸门能力：在用户提交二要素后、调用第三方实名 Provider **之前**，统一执行格式与校验位、年龄区间、身份证唯一性、黑名单与限流五道校验。任何一道未过 MUST 直接拒绝且 MUST NOT 触发外呼，避免外呼浪费与撞库。

## Requirements

### Requirement: 18–55 年龄合规区间

系统 MUST 在 step1 提交时按服务器时间从身份证号自动解析出生日期与年龄，并按 Nacos 可配置区间（默认 `[18, 55]` 闭区间）判定合规性；超出区间 MUST 立即拒绝并 MUST NOT 调用任何外部 Provider；MUST 在 `cf_user_kyc_v2.eligibility_status` 写明确终态 `REJECTED_AGE`，并记录 `age_at_submit`。年龄计算 MUST 以服务端时区为准，MUST NOT 信任前端传入的出生日期。

#### Scenario: 未满 18

- **WHEN** 身份证号解析得到 17 岁
- **THEN** 系统 MUST 拒绝并写 `eligibility_status=REJECTED_AGE`；MUST NOT 调用实名 / 人脸 / 任何外部 Provider

#### Scenario: 超过 55

- **WHEN** 身份证号解析得到 56 岁
- **THEN** 系统 MUST 拒绝并写 `eligibility_status=REJECTED_AGE`

#### Scenario: 边界年龄

- **WHEN** 身份证号解析得到正好 18 或 55 岁
- **THEN** 系统 MUST 视为合规并继续后续校验

### Requirement: 一人一证一账号唯一性

系统 MUST 以 `id_card_fingerprint`（`HMAC-SHA256(salt, realName + idCardNo)`）为唯一键阻止同一身份证绑定多账号。新提交的二要素 MUST 在落库前查询 `cf_user_kyc_v2` 中是否存在 `id_card_fingerprint = ? AND user_id != ?` 的记录；命中 MUST 写 `eligibility_status=REJECTED_DUP` 并拒绝。系统 MUST NOT 明文比较身份证号；MUST NOT 因不同姓名输入绕过唯一性（指纹由姓名 + 身份证共同决定）。

#### Scenario: 同身份证不同账号

- **WHEN** 用户 A 已 KYC 通过，用户 B 在新账号提交相同身份证号
- **THEN** 系统 MUST 拒绝并写 `eligibility_status=REJECTED_DUP`

#### Scenario: 同账号同身份证重复提交（幂等）

- **WHEN** 同一 `userId` 用相同身份证 + `Idempotency-Key` 重复提交
- **THEN** 系统 MUST 视为幂等返回上次结果；MUST NOT 拒绝为 `REJECTED_DUP`

### Requirement: 黑名单双层校验

系统 MUST 先查询本地 `cf_id_card_blacklist`（按 `id_card_fingerprint`）；命中 MUST 写 `eligibility_status=REJECTED_BLACKLIST` 并拒绝。本地未命中时 MUST 通过内部 Feign 接口 `POST /api/internal/risk/blacklist/check` 调用 `credit-risk-service` 进行二次校验；命中 MUST 写 `REJECTED_BLACKLIST` 并拒绝。两层校验 MUST 仅传 `id_card_fingerprint`，MUST NOT 跨服务传明文姓名与身份证号。

#### Scenario: 本地黑名单命中

- **WHEN** 用户身份证指纹在 `cf_id_card_blacklist` 表中存在
- **THEN** 系统 MUST 拒绝并 MUST NOT 调用 `credit-risk-service`

#### Scenario: 本地未命中风控命中

- **WHEN** 本地黑名单未命中但 `credit-risk-service` 返回 `hit=true`
- **THEN** 系统 MUST 拒绝；MUST 在本次审计中标记拒绝来源为 `RISK_SERVICE`

#### Scenario: 风控服务不可用

- **WHEN** `credit-risk-service` 调用超时或 5xx
- **THEN** 系统 MUST NOT 默认放行；MUST 返回可重试错误码（`ELIGIBILITY_RISK_UPSTREAM_UNAVAILABLE`），由前端决定是否重试

### Requirement: 限流与防撞库

系统 MUST 对每个 `userId` 实施滑动窗口限流（默认窗口与阈值由 Nacos 配置，如 1 分钟内 5 次、24 小时内 20 次）。超限请求 MUST 直接拒绝并 MUST NOT 调用 Provider；同时 MUST 对短时间内相同 `id_card_fingerprint` 跨用户的重复提交执行额外冷却以防撞库。

#### Scenario: 同用户超频

- **WHEN** 单用户在 1 分钟内发起 6 次 step1 提交
- **THEN** 系统 MUST 拒绝第 6 次及之后请求并 MUST NOT 调用外部 Provider

#### Scenario: 撞库尝试

- **WHEN** 不同用户在短时间内提交相同 `id_card_fingerprint`
- **THEN** 系统 MUST 进入额外冷却并 MUST 写一条安全审计事件

### Requirement: 拒绝原因不外传内部码

系统 MUST 把准入闸门的所有拒绝结论以业务可读的统一文案返回给前端，仅区分至「年龄不符 / 身份证已被绑定 / 风险拦截 / 操作过于频繁」四档。`failure_code` 与 `reason_code` 等内部字段 MUST NOT 透出到 API 响应；MUST 仅写入审计日志供运营查询。

#### Scenario: 黑名单具体原因不暴露

- **WHEN** 用户被本地或风控黑名单拦截
- **THEN** API 返回的 message MUST 为统一「风险拦截」文案；MUST NOT 透出黑名单具体类型或 `reason_code`
