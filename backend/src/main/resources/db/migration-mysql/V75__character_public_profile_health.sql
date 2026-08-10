SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_character'
              AND column_name = 'public_summary'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN public_summary VARCHAR(512) NULL AFTER bio'
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
              AND table_name = 'app_character'
              AND column_name = 'public_tags_json'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN public_tags_json TEXT NULL AFTER public_summary'
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
              AND table_name = 'app_character'
              AND column_name = 'public_warnings_json'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN public_warnings_json TEXT NULL AFTER public_tags_json'
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
              AND table_name = 'app_character'
              AND column_name = 'health_score'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN health_score INT NOT NULL DEFAULT 0 AFTER public_warnings_json'
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
              AND table_name = 'app_character'
              AND column_name = 'health_issues_json'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN health_issues_json TEXT NULL AFTER health_score'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
