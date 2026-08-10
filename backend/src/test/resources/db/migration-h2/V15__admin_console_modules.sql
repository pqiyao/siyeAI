ALTER TABLE app_notice ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE app_notice ADD COLUMN IF NOT EXISTS enabled TINYINT NOT NULL DEFAULT 1;
ALTER TABLE app_notice ADD COLUMN IF NOT EXISTS guest_visible TINYINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS app_admin_notice_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_username VARCHAR(64) NOT NULL,
    notice_id BIGINT NOT NULL,
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admin_notice_read UNIQUE (admin_username, notice_id),
    CONSTRAINT fk_admin_notice_read_notice FOREIGN KEY (notice_id) REFERENCES app_notice(id)
);

CREATE TABLE IF NOT EXISTS app_openrouter_generation_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    default_model VARCHAR(256) NOT NULL,
    default_temperature DOUBLE NOT NULL DEFAULT 0.85,
    default_max_output_tokens INT NOT NULL DEFAULT 2048,
    top_p DOUBLE NOT NULL DEFAULT -1,
    frequency_penalty DOUBLE NOT NULL DEFAULT -999,
    presence_penalty DOUBLE NOT NULL DEFAULT -999,
    stop_sequences VARCHAR(2048) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_h5_user_profile_ext (
    user_id BIGINT NOT NULL PRIMARY KEY,
    nickname VARCHAR(64) NULL,
    avatar VARCHAR(1024) NULL,
    bio TEXT NULL,
    vip_type INT NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0,
    gold_coin INT NOT NULL DEFAULT 0,
    need_edit INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'normal',
    gender INT NOT NULL DEFAULT 0,
    birthday VARCHAR(32) NULL,
    height VARCHAR(32) NULL,
    weight VARCHAR(32) NULL,
    country VARCHAR(64) NULL,
    characters VARCHAR(64) NULL,
    relation VARCHAR(128) NULL,
    occupation VARCHAR(64) NULL,
    label VARCHAR(128) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_h5_profile_ext_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE IF NOT EXISTS app_lorebook_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    keywords_csv VARCHAR(2048) NULL,
    content LONGTEXT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    constant_injection TINYINT NOT NULL DEFAULT 0,
    scan_depth INT NOT NULL DEFAULT 4,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lorebook_character FOREIGN KEY (character_id) REFERENCES app_character(id)
);

CREATE INDEX IF NOT EXISTS idx_lorebook_character_id ON app_lorebook_entry(character_id);

ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS channel VARCHAR(32) NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS model VARCHAR(160) NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS duration_ms INT NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS http_status INT NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS prompt_tokens INT NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS completion_tokens INT NULL;
ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS error_message VARCHAR(512) NULL;
