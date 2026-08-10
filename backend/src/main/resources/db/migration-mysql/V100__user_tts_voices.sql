CREATE TABLE IF NOT EXISTS app_user_tts_voice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    provider_source VARCHAR(32) NOT NULL DEFAULT 'siliconflow',
    model_name VARCHAR(255) NOT NULL DEFAULT '',
    config_fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    voice_uri VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    last_error VARCHAR(255) NOT NULL DEFAULT '',
    disabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT uk_app_user_tts_voice_request UNIQUE (user_id, request_id),
    KEY idx_app_user_tts_voice_owner (user_id, deleted_at, status, updated_at),
    KEY idx_app_user_tts_voice_admin (status, disabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_user_tts_voice_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    character_id BIGINT NOT NULL DEFAULT 0,
    member_id BIGINT NOT NULL DEFAULT 0,
    voice_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_tts_voice_binding_scope UNIQUE (user_id, scope_type, character_id, member_id),
    KEY idx_app_user_tts_voice_binding_voice (voice_id),
    KEY idx_app_user_tts_voice_binding_owner (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
