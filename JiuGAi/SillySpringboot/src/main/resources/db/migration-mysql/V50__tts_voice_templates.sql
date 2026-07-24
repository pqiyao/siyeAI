CREATE TABLE IF NOT EXISTS app_tts_voice_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    provider_source VARCHAR(32) NOT NULL DEFAULT 'siliconflow',
    tts_model_name VARCHAR(255) NOT NULL DEFAULT '',
    description VARCHAR(255) NOT NULL DEFAULT '',
    reference_audio_url VARCHAR(512) NOT NULL DEFAULT '',
    cover_image_url VARCHAR(512) NOT NULL DEFAULT '',
    sample_script VARCHAR(255) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_tts_voice_template_code UNIQUE (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_user_tts_voice_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    provider_source VARCHAR(32) NOT NULL DEFAULT 'siliconflow',
    base_url VARCHAR(255) NOT NULL DEFAULT '',
    model_name VARCHAR(255) NOT NULL DEFAULT '',
    config_fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    voice_uri VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    last_error VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_tts_voice_instance_user_template UNIQUE (user_id, template_code),
    KEY idx_app_user_tts_voice_instance_user (user_id, status, updated_at),
    KEY idx_app_user_tts_voice_instance_template (template_code, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE app_h5_user_ai_provider
    MODIFY COLUMN tts_voice_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider
    ADD COLUMN tts_voice_template_code VARCHAR(64) NOT NULL DEFAULT '' AFTER tts_voice_name;
