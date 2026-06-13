CREATE TABLE IF NOT EXISTS app_human_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_key VARCHAR(80) NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    payload_json JSON NOT NULL,
    content_preview VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME NULL,
    recalled_at DATETIME NULL,
    client_msg_id VARCHAR(80) NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'local_ws',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_human_chat_from_client_msg (from_user_id, client_msg_id),
    KEY idx_human_chat_msg_conversation (conversation_key, created_at, id),
    KEY idx_human_chat_msg_to_read (to_user_id, is_read, status, created_at),
    KEY idx_human_chat_msg_from_created (from_user_id, created_at),
    KEY idx_human_chat_msg_pair_created (from_user_id, to_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS app_human_chat_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_key VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    peer_user_id BIGINT NOT NULL,
    last_message_id BIGINT NULL,
    last_message_type VARCHAR(20) NULL,
    last_message_payload JSON NULL,
    last_message_preview VARCHAR(255) NULL,
    last_message_at DATETIME NULL,
    unread_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_human_chat_conversation_user_peer (user_id, peer_user_id),
    KEY idx_human_chat_conversation_user_time (user_id, last_message_at, id),
    KEY idx_human_chat_conversation_key (conversation_key)
);

CREATE TABLE IF NOT EXISTS app_human_chat_delivery_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NULL,
    conversation_key VARCHAR(80) NULL,
    target_user_id BIGINT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'local_ws',
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_payload_json JSON NULL,
    response_payload_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_human_chat_delivery_msg (message_id, created_at),
    KEY idx_human_chat_delivery_target (target_user_id, created_at),
    KEY idx_human_chat_delivery_status (status, created_at)
);
