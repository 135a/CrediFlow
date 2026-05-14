-- 信用评分表
CREATE TABLE IF NOT EXISTS cf_credit_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id VARCHAR(64) NOT NULL COMMENT '授信申请单号',
    s1_score INT NOT NULL COMMENT '维度1得分',
    s2_score INT NOT NULL COMMENT '维度2得分',
    s3_score INT NOT NULL COMMENT '维度3得分',
    s4_score INT NOT NULL COMMENT '维度4得分',
    total_score DECIMAL(5,2) NOT NULL COMMENT '总分',
    risk_level VARCHAR(32) NOT NULL COMMENT '风险等级(LOW, MEDIUM, HIGH)',
    rules_version VARCHAR(32) NOT NULL COMMENT '规则版本号',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_application_id (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用评分表';

-- 循环额度账户表
CREATE TABLE IF NOT EXISTS cf_user_credit_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '总额度',
    used_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已用额度',
    available_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '可用额度',
    frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '冻结额度',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户循环额度表';

-- 人工审核队列
CREATE TABLE IF NOT EXISTS cf_credit_review_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id VARCHAR(64) NOT NULL COMMENT '授信申请单号',
    source VARCHAR(32) NOT NULL COMMENT '来源(如: HIGH_RISK_FACE, CHAT_INTENT)',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING, REVIEWED)',
    risk_insight JSON COMMENT 'Agent生成的风险洞察与建议(三件套)',
    reviewer_id BIGINT COMMENT '审核员ID',
    review_result VARCHAR(32) COMMENT '审核结果(PASS, REJECT, DOWNGRADE)',
    review_comment VARCHAR(512) COMMENT '审核备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_application_id (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信人工审核队列';

-- 对话风控升级记录表
CREATE TABLE IF NOT EXISTS cf_credit_risk_escalation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id VARCHAR(128) NOT NULL COMMENT '对话ID',
    source VARCHAR(32) NOT NULL COMMENT '来源',
    severity VARCHAR(32) NOT NULL COMMENT '严重程度',
    actions_applied JSON COMMENT '采取的动作列表',
    payload_digest JSON COMMENT '相关上下文或证据',
    relevant_chat_logs JSON COMMENT '相关的聊天记录原话',
    agent_suggestions VARCHAR(512) COMMENT 'Agent给出的处置建议',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话意图风险升级记录';
