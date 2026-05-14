-- 实名核验字段与证件密文列宽（CryptoTypeHandler Base64 密文）
ALTER TABLE cf_user_kyc
    ADD COLUMN realname_status VARCHAR(32) NULL COMMENT 'NOT_SUBMITTED,PROCESSING,VERIFIED,FAILED',
    ADD COLUMN realname_verified_at DATETIME NULL COMMENT '实名通过时间',
    ADD COLUMN realname_provider_txn_no VARCHAR(128) NULL COMMENT '第三方流水号',
    ADD COLUMN id_card_mask VARCHAR(32) NULL COMMENT '证件号脱敏展示',
    ADD COLUMN id_card_fingerprint CHAR(64) NULL COMMENT '证件+姓名指纹(HMAC)，用于幂等',
    ADD COLUMN realname_internal_reason VARCHAR(64) NULL COMMENT '内部原因码，不对外';

ALTER TABLE cf_user_kyc
    MODIFY COLUMN id_card_no VARCHAR(512) NULL COMMENT '身份证号(AES密文，TypeHandler)';

-- 历史数据：旧流程 step_status>=2 视为已完成实名（OCR+活体时代），避免线上用户被 step3 门禁误杀
UPDATE cf_user_kyc
SET realname_status = CASE WHEN step_status >= 2 THEN 'VERIFIED' ELSE 'NOT_SUBMITTED' END
WHERE realname_status IS NULL;
