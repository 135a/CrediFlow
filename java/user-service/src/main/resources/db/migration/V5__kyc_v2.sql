-- KYC v2 事实表、四要素绑卡、本地身份证黑名单、人脸核验流水（OpenSpec kyc-realname-face-bankcard-rebuild）
-- 本脚本仅建表，不含历史数据回填（见后续 V6 backfill）。

CREATE TABLE IF NOT EXISTS cf_user_kyc_v2 (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    real_name VARCHAR(128) NULL COMMENT '真实姓名',
    id_card_no VARCHAR(512) NULL COMMENT '身份证号(AES密文，TypeHandler)',
    id_card_mask VARCHAR(32) NULL COMMENT '证件号脱敏展示',
    id_card_fingerprint CHAR(64) NULL COMMENT '证件+姓名指纹(HMAC)，一人一证',
    age_at_submit SMALLINT NULL COMMENT '提交时从身份证解析的年龄',
    eligibility_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '准入闸门终态',
    eligibility_decided_at DATETIME NULL,
    realname_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '二要素实名状态',
    realname_provider_txn_no VARCHAR(128) NULL,
    realname_verified_at DATETIME NULL,
    face_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '人脸实人状态',
    face_provider_id VARCHAR(64) NULL,
    face_provider_biz_no VARCHAR(128) NULL,
    face_provider_txn_no VARCHAR(128) NULL,
    face_verified_at DATETIME NULL,
    face_failure_code VARCHAR(64) NULL COMMENT '内部原因码，禁止对外',
    kyc_passed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '实名且实人均 VERIFIED',
    kyc_passed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_kyc_v2_user_id (user_id),
    UNIQUE KEY uk_user_kyc_v2_id_card_fp (id_card_fingerprint),
    KEY idx_kyc_passed (kyc_passed),
    KEY idx_realname_status (realname_status),
    KEY idx_face_status (face_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KYC v2 事实表';

CREATE TABLE IF NOT EXISTS cf_user_bank_card (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL,
    bind_card_id VARCHAR(64) NOT NULL COMMENT '对外 token，下游资金网关引用',
    bank_code VARCHAR(32) NULL,
    card_no VARCHAR(512) NULL COMMENT '卡号(AES密文)',
    card_no_mask VARCHAR(32) NULL,
    reserved_phone VARCHAR(512) NULL COMMENT '预留手机号(AES密文)',
    reserved_phone_mask VARCHAR(16) NULL,
    card_no_fingerprint CHAR(64) NOT NULL COMMENT '同卡防重',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED/FAILED/UNBOUND',
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    provider_id VARCHAR(64) NULL,
    provider_txn_no VARCHAR(128) NULL,
    verified_at DATETIME NULL,
    unbound_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_bank_card_bind_id (bind_card_id),
    UNIQUE KEY uk_user_bank_card_user_fp (user_id, card_no_fingerprint),
    KEY idx_user_bank_card_primary (user_id, is_primary, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='银行卡四要素绑卡';

CREATE TABLE IF NOT EXISTS cf_id_card_blacklist (
    id BIGINT NOT NULL COMMENT '主键ID',
    id_card_fingerprint CHAR(64) NOT NULL COMMENT '身份证指纹，不存明文',
    reason_code VARCHAR(64) NOT NULL,
    reason_desc VARCHAR(255) NULL,
    operator VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_card_blacklist_fp (id_card_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份证本地黑名单';

CREATE TABLE IF NOT EXISTS cf_face_verify_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    provider_biz_no VARCHAR(128) NOT NULL COMMENT '我方业务单号',
    provider_txn_no VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
    failure_code VARCHAR(64) NULL,
    failure_reason_internal VARCHAR(255) NULL,
    payload_digest VARCHAR(64) NULL,
    callback_received_at DATETIME NULL,
    duration_ms INT NULL,
    channel VARCHAR(16) NOT NULL COMMENT 'MOCK/WHITELIST/HTTP/BACKDOOR',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_verify_log_biz_no (provider_biz_no),
    KEY idx_face_verify_log_user (user_id),
    KEY idx_face_verify_log_provider_txn (provider_id, provider_txn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸核验流水';
