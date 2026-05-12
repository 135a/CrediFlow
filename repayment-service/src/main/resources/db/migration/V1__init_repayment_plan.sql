CREATE TABLE IF NOT EXISTS cf_repayment_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    term_index INT NOT NULL,
    total_terms INT NOT NULL,
    principal_amount DECIMAL(18,4) NOT NULL,
    interest_amount DECIMAL(18,4) NOT NULL,
    penalty_amount DECIMAL(18,4) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(18,4) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, OVERDUE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_application_id (application_id),
    INDEX idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款计划表';
