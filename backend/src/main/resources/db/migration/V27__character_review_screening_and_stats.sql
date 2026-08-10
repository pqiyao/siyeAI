SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND COLUMN_NAME = 'event_type'
        ),
        'SELECT 1',
        'ALTER TABLE app_character_review_log ADD COLUMN event_type VARCHAR(32) NOT NULL DEFAULT ''MANUAL_REVIEW'' AFTER batch_no'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND COLUMN_NAME = 'screening_level'
        ),
        'SELECT 1',
        'ALTER TABLE app_character_review_log ADD COLUMN screening_level VARCHAR(16) NOT NULL DEFAULT ''NONE'' AFTER event_type'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND COLUMN_NAME = 'screening_flags'
        ),
        'SELECT 1',
        'ALTER TABLE app_character_review_log ADD COLUMN screening_flags VARCHAR(500) NULL AFTER screening_level'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND COLUMN_NAME = 'screening_hits'
        ),
        'SELECT 1',
        'ALTER TABLE app_character_review_log ADD COLUMN screening_hits INT NOT NULL DEFAULT 0 AFTER screening_flags'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND INDEX_NAME = 'idx_character_review_log_status_created'
        ),
        'SELECT 1',
        'CREATE INDEX idx_character_review_log_status_created ON app_character_review_log (review_status, created_at)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND INDEX_NAME = 'idx_character_review_log_event_created'
        ),
        'SELECT 1',
        'CREATE INDEX idx_character_review_log_event_created ON app_character_review_log (event_type, created_at)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character_review_log'
              AND INDEX_NAME = 'idx_character_review_log_screening_created'
        ),
        'SELECT 1',
        'CREATE INDEX idx_character_review_log_screening_created ON app_character_review_log (screening_level, created_at)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
