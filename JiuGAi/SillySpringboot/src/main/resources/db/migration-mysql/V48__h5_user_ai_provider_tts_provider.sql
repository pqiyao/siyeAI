SET @tts_provider_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'tts_provider_source'
);
SET @tts_provider_sql := IF(
    @tts_provider_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN tts_provider_source VARCHAR(32) NOT NULL DEFAULT '''' AFTER tts_voice_name',
    'SELECT 1'
);
PREPARE stmt FROM @tts_provider_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tts_key_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'tts_api_key_cipher'
);
SET @tts_key_sql := IF(
    @tts_key_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN tts_api_key_cipher TEXT NULL AFTER tts_provider_source',
    'SELECT 1'
);
PREPARE stmt FROM @tts_key_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tts_url_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'tts_custom_url'
);
SET @tts_url_sql := IF(
    @tts_url_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN tts_custom_url VARCHAR(512) NOT NULL DEFAULT '''' AFTER tts_api_key_cipher',
    'SELECT 1'
);
PREPARE stmt FROM @tts_url_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
