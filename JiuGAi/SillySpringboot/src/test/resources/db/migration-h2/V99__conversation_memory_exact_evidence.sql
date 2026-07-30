ALTER TABLE app_conversation_memory_entry
    ADD COLUMN IF NOT EXISTS source_message_ids_json TEXT NULL;
