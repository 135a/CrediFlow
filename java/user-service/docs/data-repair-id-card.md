# 历史明文身份证号加密回填（一次性 / 可脚本化）

## 背景

Flyway `V4__realname_kyc_columns.sql` 将 `id_card_no` 列加宽为密文存储；实体 `UserKyc.idCardNo` 使用 `CryptoTypeHandler`。若历史行仍为**明文**，读路径会尝试 AES 解密，失败时 TypeHandler 会回退为原字符串展示，存在**脱敏与合规风险**。

## 建议修复步骤（在维护窗口执行）

1. **备份** `cf_user_kyc` 表。
2. 编写离线脚本（推荐在管控环境）：逐行读取 `id_card_no`，若判定为 18 位合法身份证明文，则调用与 `CryptoTypeHandler` **相同算法与密钥**写入密文；否则跳过并记录审计。
3. 回填 **`id_card_mask`**（前四后四掩码）与 **`id_card_fingerprint`**（HMAC，盐与线上一致）。
4. 抽样校验：应用读回解密后与源数据一致。

## 回滚

从备份表恢复 `id_card_no` 及相关列；或保留备份仅回滚应用版本（新列可空）。

## 参考

OpenSpec 变更：`openspec/changes/realname-thirdparty-http-backend`（tasks 2.3）。
