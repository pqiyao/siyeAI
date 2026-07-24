ALTER TABLE app_conversation_memory_entry
    ADD COLUMN manual_disabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_conversation_memory
    ADD COLUMN last_manual_refresh_at TIMESTAMP NULL;

ALTER TABLE app_conversation_memory
    ADD COLUMN manual_refresh_started_at TIMESTAMP NULL;

ALTER TABLE app_conversation_memory
    ADD COLUMN manual_refresh_token VARCHAR(64) NULL;

CREATE INDEX idx_memory_manual_refresh_guard
    ON app_conversation_memory(manual_refresh_token, manual_refresh_started_at);
