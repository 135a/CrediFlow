ALTER TABLE cf_loan_application ADD COLUMN manual_reviewer_id BIGINT COMMENT '人工审核人ID';
ALTER TABLE cf_loan_application ADD COLUMN manual_review_reason VARCHAR(255) COMMENT '人工审核意见';
ALTER TABLE cf_loan_application ADD COLUMN manual_review_time TIMESTAMP COMMENT '人工审核时间';
