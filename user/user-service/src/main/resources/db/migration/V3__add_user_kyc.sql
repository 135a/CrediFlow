CREATE TABLE IF NOT EXISTS cf_user_kyc (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    monthly_income DECIMAL(10,2) COMMENT '月收入',
    birth_date DATE COMMENT '出生日期',
    residence VARCHAR(255) COMMENT '居住地',
    occupation VARCHAR(128) COMMENT '职业',
    real_name VARCHAR(128) COMMENT '真实姓名',
    id_card_no VARCHAR(128) COMMENT '身份证号',
    age INT COMMENT '年龄',
    face_verified TINYINT(1) DEFAULT 0 COMMENT '是否完成活体',
    payment_method VARCHAR(32) COMMENT '收款方式: ALIPAY/BANK_CARD',
    payment_account VARCHAR(128) COMMENT '收款账号',
    step_status INT NOT NULL DEFAULT 0 COMMENT '认证步骤: 0-未开始,1-基础信息,2-实名活体,3-收款绑定完成',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户KYC认证表';
