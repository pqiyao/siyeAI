CREATE TABLE IF NOT EXISTS app_social_friend_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    request_message VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    handled_by_user_id BIGINT NULL,
    handled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_friend_request_pair (requester_user_id, target_user_id),
    KEY idx_social_friend_request_target_status (target_user_id, status, created_at),
    KEY idx_social_friend_request_requester_status (requester_user_id, status, created_at)
);

CREATE TABLE IF NOT EXISTS app_social_friend (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    friend_user_id BIGINT NOT NULL,
    source_request_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_social_friend_pair (user_id, friend_user_id),
    KEY idx_social_friend_user_status (user_id, status, updated_at),
    KEY idx_social_friend_peer_status (friend_user_id, status, updated_at)
);

CREATE TABLE IF NOT EXISTS app_social_block (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    reason VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_block_pair (user_id, blocked_user_id),
    KEY idx_social_block_user_status (user_id, status, updated_at),
    KEY idx_social_block_blocked_status (blocked_user_id, status, updated_at)
);

SET @jg_add_post_review_note = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_community_post'
              AND COLUMN_NAME = 'review_note'
        ),
        'SELECT 1',
        'ALTER TABLE app_community_post ADD COLUMN review_note VARCHAR(500) NULL'
    )
);
PREPARE stmt FROM @jg_add_post_review_note;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_post_reviewed_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_community_post'
              AND COLUMN_NAME = 'reviewed_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_community_post ADD COLUMN reviewed_at DATETIME NULL'
    )
);
PREPARE stmt FROM @jg_add_post_reviewed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_post_reviewed_by = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_community_post'
              AND COLUMN_NAME = 'reviewed_by'
        ),
        'SELECT 1',
        'ALTER TABLE app_community_post ADD COLUMN reviewed_by VARCHAR(64) NULL'
    )
);
PREPARE stmt FROM @jg_add_post_reviewed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
