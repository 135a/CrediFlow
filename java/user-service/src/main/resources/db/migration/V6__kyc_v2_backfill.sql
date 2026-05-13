-- 回填 KYC v2：把旧 cf_user_kyc 中已 VERIFIED 的实名数据复制到 cf_user_kyc_v2。
-- 旧字段（payment_method / payment_account / monthly_income / occupation 等）不搬运；
-- 旧表保留 90 天只读，应用层禁止写入，cleanup 由独立 change `kyc-legacy-cleanup` 完成。

INSERT INTO cf_user_kyc_v2 (
    id, user_id, real_name, id_card_no, id_card_mask, id_card_fingerprint, age_at_submit,
    eligibility_status, eligibility_decided_at,
    realname_status, realname_provider_txn_no, realname_verified_at,
    face_status, face_provider_id, face_provider_biz_no, face_provider_txn_no, face_verified_at, face_failure_code,
    kyc_passed, kyc_passed_at,
    created_at, updated_at
)
SELECT
    o.id,
    o.user_id,
    o.real_name,
    o.id_card_no,
    o.id_card_mask,
    o.id_card_fingerprint,
    o.age,
    'PASS',                       -- 旧用户视为闸门已过（如风控有新规则可后续补回填）
    o.realname_verified_at,
    'VERIFIED',
    o.realname_provider_txn_no,
    o.realname_verified_at,
    'NOT_SUBMITTED',              -- 实人核验需要重新走 step2
    NULL, NULL, NULL, NULL, NULL,
    0,                             -- kyc_passed=false，直到补做人脸
    NULL,
    o.created_at,
    o.updated_at
FROM cf_user_kyc o
WHERE o.realname_status = 'VERIFIED'
  AND NOT EXISTS (
        SELECT 1 FROM cf_user_kyc_v2 v WHERE v.user_id = o.user_id
  );
