SET @vision_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'vision_model_name'
);
SET @vision_sql := IF(
    @vision_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN vision_model_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER model_name',
    'SELECT 1'
);
PREPARE stmt FROM @vision_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @audio_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'audio_model_name'
);
SET @audio_sql := IF(
    @audio_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN audio_model_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER vision_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @audio_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @image_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_model_name'
);
SET @image_sql := IF(
    @image_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_model_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER audio_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @image_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
