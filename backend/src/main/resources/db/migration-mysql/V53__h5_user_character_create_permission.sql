SET @jg_add_h5_user_character_create_allowed = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_h5_user_profile_ext'
              AND COLUMN_NAME = 'character_create_allowed'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN character_create_allowed TINYINT(1) NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @jg_add_h5_user_character_create_allowed;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
