CREATE TABLE IF NOT EXISTS app_password_reset_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    account_key VARCHAR(128) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_password_reset_request UNIQUE (request_id),
    CONSTRAINT fk_app_password_reset_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_app_password_reset_account_created (account_key, created_at),
    KEY idx_app_password_reset_expiry (expires_at)
);

CREATE TABLE IF NOT EXISTS app_password_reset_throttle (
    account_key CHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_app_password_reset_throttle_time (requested_at)
);
