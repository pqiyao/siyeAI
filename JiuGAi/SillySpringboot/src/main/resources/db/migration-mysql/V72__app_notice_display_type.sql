SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_notice'
              AND column_name = 'display_type'
        ),
        'SELECT 1',
        'ALTER TABLE app_notice ADD COLUMN display_type VARCHAR(24) NOT NULL DEFAULT ''inbox'' AFTER guest_visible'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
