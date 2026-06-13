CREATE TABLE IF NOT EXISTS app_community_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content TEXT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'text',
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    open_comments TINYINT(1) NOT NULL DEFAULT 1,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_app_community_post_feed (status, deleted_at, created_at, id),
    KEY idx_app_community_post_user (user_id, deleted_at, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_post_media (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    media_key VARCHAR(512) NOT NULL,
    storage_provider VARCHAR(20) NOT NULL DEFAULT 'local',
    media_url_snapshot VARCHAR(1024) NULL,
    media_type VARCHAR(20) NOT NULL DEFAULT 'image',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_app_community_post_media_post (post_id, sort_order, id),
    KEY idx_app_community_post_media_user (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    reply_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_app_community_comment_post (post_id, deleted_at, created_at, id),
    KEY idx_app_community_comment_user (user_id, deleted_at, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_comment_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_app_community_reply_comment (comment_id, deleted_at, created_at, id),
    KEY idx_app_community_reply_post (post_id, deleted_at, created_at),
    KEY idx_app_community_reply_from_user (from_user_id, created_at),
    KEY idx_app_community_reply_to_user (to_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_community_like_post_user (post_id, user_id),
    KEY idx_app_community_like_user (user_id, created_at),
    KEY idx_app_community_like_to_user (to_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_community_follow_pair (from_user_id, to_user_id),
    KEY idx_app_community_follow_to_user (to_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS app_community_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    notice_type VARCHAR(32) NOT NULL,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    reply_id BIGINT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_app_community_notice_user (user_id, read_at, created_at),
    KEY idx_app_community_notice_post (post_id, created_at)
);
