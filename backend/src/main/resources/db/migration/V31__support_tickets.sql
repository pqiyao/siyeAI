CREATE TABLE IF NOT EXISTS app_support_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    client_uid_snapshot VARCHAR(80) NULL,
    ticket_type VARCHAR(32) NOT NULL,
    subject VARCHAR(120) NOT NULL,
    content TEXT NULL,
    order_no VARCHAR(64) NULL,
    character_id BIGINT NULL,
    character_name VARCHAR(120) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    source VARCHAR(24) NOT NULL DEFAULT 'H5',
    latest_message_preview VARCHAR(255) NULL,
    message_count INT NOT NULL DEFAULT 0,
    last_user_reply_at DATETIME NULL,
    last_admin_reply_at DATETIME NULL,
    last_message_at DATETIME NULL,
    closed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_support_ticket_no (ticket_no),
    KEY idx_app_support_ticket_user (user_id, created_at),
    KEY idx_app_support_ticket_status (status, created_at),
    KEY idx_app_support_ticket_type (ticket_type, created_at),
    KEY idx_app_support_ticket_order (order_no)
);

CREATE TABLE IF NOT EXISTS app_support_ticket_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    sender_type VARCHAR(16) NOT NULL,
    sender_name VARCHAR(60) NULL,
    content TEXT NOT NULL,
    attachments_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_app_support_ticket_message_ticket (ticket_id, created_at)
);
