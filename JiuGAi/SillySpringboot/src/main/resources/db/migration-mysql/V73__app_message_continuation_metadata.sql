SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_message'
              AND column_name = 'message_kind'
        ),
        'SELECT 1',
        'ALTER TABLE app_message ADD COLUMN message_kind VARCHAR(32) NOT NULL DEFAULT ''NORMAL'' AFTER role'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_message'
              AND column_name = 'continue_from_message_id'
        ),
        'SELECT 1',
        'ALTER TABLE app_message ADD COLUMN continue_from_message_id BIGINT NULL AFTER message_kind'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

