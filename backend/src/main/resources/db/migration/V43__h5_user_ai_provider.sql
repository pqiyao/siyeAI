CREATE TABLE IF NOT EXISTS app_h5_user_ai_provider (
    user_id BIGINT NOT NULL PRIMARY KEY,
    provider_mode VARCHAR(16) NOT NULL DEFAULT 'system',
    provider_source VARCHAR(32) NOT NULL DEFAULT '',
    model_name VARCHAR(255) NOT NULL DEFAULT '',
    api_key_cipher TEXT NULL,
    custom_url VARCHAR(512) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_h5_user_ai_provider_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
