ALTER TABLE app_conversation_memory_entry
    ADD COLUMN manual_deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER manual_disabled;

