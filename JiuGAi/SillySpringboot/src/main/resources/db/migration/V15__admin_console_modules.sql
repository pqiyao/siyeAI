SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_notice'
              AND column_name = 'sort_order'
        ),
        'SELECT 1',
        'ALTER TABLE app_notice ADD COLUMN sort_order INT NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_notice'
              AND column_name = 'enabled'
        ),
        'SELECT 1',
        'ALTER TABLE app_notice ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_notice'
              AND column_name = 'guest_visible'
        ),
        'SELECT 1',
        'ALTER TABLE app_notice ADD COLUMN guest_visible TINYINT(1) NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
    constant_injection TINYINT(1) NOT NULL DEFAULT 0,
    scan_depth INT NOT NULL DEFAULT 4,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lorebook_character FOREIGN KEY (character_id) REFERENCES app_character(id)
);

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'app_lorebook_entry'
              AND index_name = 'idx_lorebook_character_id'
        ),
        'SELECT 1',
        'CREATE INDEX idx_lorebook_character_id ON app_lorebook_entry(character_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'channel'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN channel VARCHAR(32) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'model'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN model VARCHAR(160) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'duration_ms'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN duration_ms INT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'http_status'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN http_status INT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'prompt_tokens'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN prompt_tokens INT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'completion_tokens'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN completion_tokens INT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_generation_task'
              AND column_name = 'error_message'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN error_message VARCHAR(512) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
