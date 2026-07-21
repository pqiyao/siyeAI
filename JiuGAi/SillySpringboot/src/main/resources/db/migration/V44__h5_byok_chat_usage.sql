SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'daily_byok_chat_used'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN daily_byok_chat_used INT NOT NULL DEFAULT 0 AFTER daily_chat_used'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
