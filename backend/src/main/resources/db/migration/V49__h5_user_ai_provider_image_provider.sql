SET @image_provider_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_provider_source'
);
SET @image_provider_sql := IF(
    @image_provider_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_provider_source VARCHAR(32) NOT NULL DEFAULT '''' AFTER image_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @image_provider_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @image_key_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_api_key_cipher'
);
SET @image_key_sql := IF(
    @image_key_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_api_key_cipher TEXT NULL AFTER image_provider_source',
    'SELECT 1'
);
PREPARE stmt FROM @image_key_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @image_url_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_custom_url'
);
SET @image_url_sql := IF(
    @image_url_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_custom_url VARCHAR(512) NOT NULL DEFAULT '''' AFTER image_api_key_cipher',
    'SELECT 1'
);
PREPARE stmt FROM @image_url_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
