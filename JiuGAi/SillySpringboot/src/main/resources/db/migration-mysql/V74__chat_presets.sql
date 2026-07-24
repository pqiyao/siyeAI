CREATE TABLE IF NOT EXISTS app_chat_preset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NULL,
    scope VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    source_type VARCHAR(32) NOT NULL DEFAULT 'ST_PLATFORM',
    api_type VARCHAR(32) NOT NULL DEFAULT 'openai',
    source_name VARCHAR(160) NOT NULL DEFAULT '',
    name VARCHAR(160) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    bundle_json LONGTEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100,
    last_synced_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_chat_preset_source (source_type, api_type, source_name),
    KEY idx_app_chat_preset_scope_enabled (scope, enabled, sort_order),
    KEY idx_app_chat_preset_owner (owner_user_id, enabled)
);

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_conversation_st_binding'
              AND column_name = 'chat_preset_id'
        ),
        'SELECT 1',
        'ALTER TABLE app_conversation_st_binding ADD COLUMN chat_preset_id BIGINT NULL AFTER st_display_name_override'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
