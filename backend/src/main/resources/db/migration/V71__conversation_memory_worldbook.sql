-- Conversation-scoped long-term memory entries synced to ST worldinfo/lorebook.

ALTER TABLE app_conversation_memory
    ADD COLUMN memory_world_name VARCHAR(255) NULL,
    ADD COLUMN entry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN enabled_entry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_source_message_id BIGINT NULL,
    ADD COLUMN last_refreshed_message_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_synced_at TIMESTAMP NULL,
    ADD COLUMN sync_status VARCHAR(32) NULL,
    ADD COLUMN sync_error VARCHAR(512) NULL;

CREATE TABLE app_conversation_memory_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    entry_key VARCHAR(128) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NULL,
    content TEXT NOT NULL,
    keywords_json TEXT NOT NULL,
    secondary_keywords_json TEXT NULL,
    priority INT NOT NULL DEFAULT 100,
    position VARCHAR(32) NOT NULL DEFAULT 'before_char',
    constant_injection BOOLEAN NOT NULL DEFAULT FALSE,
    selective BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    confidence DECIMAL(5,2) NULL,
    source_message_from_id BIGINT NULL,
    source_message_to_id BIGINT NULL,
    last_activated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,

    CONSTRAINT fk_memory_entry_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),
    CONSTRAINT uk_memory_entry_key UNIQUE (conversation_id, entry_key)
);

CREATE INDEX idx_memory_entry_conv_enabled ON app_conversation_memory_entry(conversation_id, enabled, deleted_at);
CREATE INDEX idx_memory_entry_conv_type ON app_conversation_memory_entry(conversation_id, memory_type);
CREATE INDEX idx_memory_entry_priority ON app_conversation_memory_entry(conversation_id, priority);
