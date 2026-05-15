CREATE TABLE IF NOT EXISTS cf_loan_receipt (
    id BIGINT NOT NULL PRIMARY KEY,
    receipt_no VARCHAR(64) NOT NULL UNIQUE,
    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    principal_amount DECIMAL(18,2) NOT NULL,
    annual_interest_rate DECIMAL(10,6) NOT NULL,
    total_terms INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, SETTLED, OVERDUE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_application_id (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借据表';

CREATE TABLE IF NOT EXISTS cf_repayment_plan (
    id BIGINT NOT NULL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    term_no INT NOT NULL,
    principal_amount DECIMAL(18,2) NOT NULL,
    interest_amount DECIMAL(18,2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, OVERDUE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_receipt_id (receipt_id),
    INDEX idx_user_id (user_id),
    INDEX idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款计划表';
