CREATE TABLE IF NOT EXISTS app_user_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    title VARCHAR(120) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    related_type VARCHAR(32) NULL,
    related_id BIGINT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    KEY idx_app_user_message_user_created (user_id, created_at),
    KEY idx_app_user_message_related (related_type, related_id)
);

SET @jg_add_character_review_status = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND COLUMN_NAME = 'review_status'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT ''APPROVED'' AFTER private_card'
    )
);
PREPARE stmt FROM @jg_add_character_review_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_character_review_reason = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND COLUMN_NAME = 'review_reason'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN review_reason VARCHAR(500) NULL AFTER review_status'
    )
);
PREPARE stmt FROM @jg_add_character_review_reason;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_character_reviewed_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND COLUMN_NAME = 'reviewed_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN reviewed_at DATETIME NULL AFTER review_reason'
    )
);
PREPARE stmt FROM @jg_add_character_reviewed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_character_reviewed_by = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND COLUMN_NAME = 'reviewed_by'
        ),
        'SELECT 1',
        'ALTER TABLE app_character ADD COLUMN reviewed_by VARCHAR(64) NULL AFTER reviewed_at'
    )
);
PREPARE stmt FROM @jg_add_character_reviewed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE app_character
SET review_status = 'APPROVED'
WHERE review_status IS NULL
   OR review_status = '';
