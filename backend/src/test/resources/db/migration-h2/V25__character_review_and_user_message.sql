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

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS review_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS review_reason VARCHAR(500) NULL;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS reviewed_at DATETIME NULL;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(64) NULL;

UPDATE app_character
SET review_status = 'APPROVED'
WHERE review_status IS NULL
   OR review_status = '';
