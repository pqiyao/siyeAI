SET @image_consistency_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_character_consistency_mode'
);
SET @image_consistency_sql := IF(
    @image_consistency_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_character_consistency_mode VARCHAR(16) NOT NULL DEFAULT ''balanced'' AFTER image_custom_url',
    'SELECT 1'
);
PREPARE stmt FROM @image_consistency_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @image_reference_source_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'image_reference_source_mode'
);
SET @image_reference_source_sql := IF(
    @image_reference_source_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN image_reference_source_mode VARCHAR(32) NOT NULL DEFAULT ''latest_generated_first'' AFTER image_character_consistency_mode',
    'SELECT 1'
);
PREPARE stmt FROM @image_reference_source_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
