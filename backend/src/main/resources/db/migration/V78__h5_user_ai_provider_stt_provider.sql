SET @stt_provider_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'stt_provider_source'
);
SET @stt_provider_sql := IF(
    @stt_provider_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN stt_provider_source VARCHAR(32) NOT NULL DEFAULT '''' AFTER stt_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @stt_provider_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @stt_key_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'stt_api_key_cipher'
);
SET @stt_key_sql := IF(
    @stt_key_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN stt_api_key_cipher TEXT NULL AFTER stt_provider_source',
    'SELECT 1'
);
PREPARE stmt FROM @stt_key_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @stt_url_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'stt_custom_url'
);
SET @stt_url_sql := IF(
    @stt_url_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN stt_custom_url VARCHAR(512) NOT NULL DEFAULT '''' AFTER stt_api_key_cipher',
    'SELECT 1'
);
PREPARE stmt FROM @stt_url_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
