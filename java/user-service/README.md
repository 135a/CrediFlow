# user-service

## 内部 API：实名状态（下游绑卡 / 授信 / 合同）

- **路径**：`GET /api/internal/user/kyc/realname-status?userId={id}`
- **响应**（`Result.data`）：
  - `verified`：`boolean`，当且仅当 `realnameStatus == VERIFIED` 时为 `true`
  - `realnameStatus`：`NOT_SUBMITTED` | `PROCESSING` | `VERIFIED` | `FAILED`

KYC 对外 App 接口仍以 `GET /api/app/user/kyc/status` 为准；其中 **`idCardMask`** 为脱敏证件号，**不包含**明文证件与内部原因码。

## 破坏性变更（PR 描述可引用）

- `POST /api/app/user/kyc/step2` 请求体由 `idCardBase64` / `faceBase64` 改为 **`realName`** + **`idCardNo`**（JSON）。
- 可选请求头 **`Idempotency-Key`**：在 TTL 内重复提交返回同一成功摘要。
- 第三步绑卡前要求 **`realnameStatus == VERIFIED`**。

OpenSpec：`openspec/changes/realname-thirdparty-http-backend`。
