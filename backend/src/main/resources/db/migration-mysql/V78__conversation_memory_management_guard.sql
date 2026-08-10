ALTER TABLE app_conversation_memory_entry
    ADD COLUMN manual_disabled BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;

ALTER TABLE app_conversation_memory
    ADD COLUMN last_manual_refresh_at TIMESTAMP NULL AFTER last_refreshed_message_count,
    ADD COLUMN manual_refresh_started_at TIMESTAMP NULL AFTER last_manual_refresh_at,
    ADD COLUMN manual_refresh_token VARCHAR(64) NULL AFTER manual_refresh_started_at;

CREATE INDEX idx_memory_manual_refresh_guard
    ON app_conversation_memory(manual_refresh_token, manual_refresh_started_at);
