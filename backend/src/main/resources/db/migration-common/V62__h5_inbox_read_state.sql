CREATE TABLE IF NOT EXISTS app_user_inbox_read_state (
    user_id BIGINT NOT NULL PRIMARY KEY,
    notice_baseline_initialized TINYINT NOT NULL DEFAULT 0,
    message_baseline_initialized TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user_notice_read (
    user_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, notice_id),
    KEY idx_app_user_notice_read_notice (notice_id)
);
