SET @stt_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'stt_model_name'
);
SET @stt_sql := IF(
    @stt_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN stt_model_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER audio_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @stt_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tts_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'tts_model_name'
);
SET @tts_sql := IF(
    @tts_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN tts_model_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER stt_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @tts_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @voice_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_h5_user_ai_provider'
      AND COLUMN_NAME = 'tts_voice_name'
);
SET @voice_sql := IF(
    @voice_exists = 0,
    'ALTER TABLE app_h5_user_ai_provider ADD COLUMN tts_voice_name VARCHAR(64) NOT NULL DEFAULT '''' AFTER tts_model_name',
    'SELECT 1'
);
PREPARE stmt FROM @voice_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE app_h5_user_ai_provider
SET
    stt_model_name = CASE
        WHEN TRIM(stt_model_name) = '' THEN audio_model_name
        ELSE stt_model_name
    END,
    tts_model_name = CASE
        WHEN TRIM(tts_model_name) = '' THEN audio_model_name
        ELSE tts_model_name
    END
WHERE TRIM(audio_model_name) <> '';
