-- Phase 3: Telegram initData login (user + session)

CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    username VARCHAR(64) NULL,
    first_name VARCHAR(128) NULL,
    last_name VARCHAR(128) NULL,
    language_code VARCHAR(16) NULL,
    photo_url VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_app_user_telegram_user_id ON app_user(telegram_user_id);

CREATE TABLE app_user_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT fk_app_user_session_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT uk_app_user_session_session_id UNIQUE (session_id)
);

CREATE INDEX idx_app_user_session_user_id ON app_user_session(user_id);
