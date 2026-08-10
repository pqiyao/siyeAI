SET @jg_add_memory_source_message_ids_json = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE app_conversation_memory_entry ADD COLUMN source_message_ids_json TEXT NULL AFTER source_message_to_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_conversation_memory_entry'
      AND column_name = 'source_message_ids_json'
);
PREPARE stmt FROM @jg_add_memory_source_message_ids_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
