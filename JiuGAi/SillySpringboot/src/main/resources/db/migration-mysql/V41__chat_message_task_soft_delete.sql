SET @jg_add_msg_deleted_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_message'
              AND COLUMN_NAME = 'deleted_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_message ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @jg_add_msg_deleted_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_task_deleted_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_generation_task'
              AND COLUMN_NAME = 'deleted_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_generation_task ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @jg_add_task_deleted_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_msg_deleted_idx = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_message'
              AND INDEX_NAME = 'idx_msg_conv_deleted_id'
        ),
        'SELECT 1',
        'CREATE INDEX idx_msg_conv_deleted_id ON app_message(conversation_id, deleted_at, id)'
    )
);
PREPARE stmt FROM @jg_add_msg_deleted_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_task_deleted_idx = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_generation_task'
              AND INDEX_NAME = 'idx_task_conv_deleted'
        ),
        'SELECT 1',
        'CREATE INDEX idx_task_conv_deleted ON app_generation_task(conversation_id, deleted_at)'
    )
);
PREPARE stmt FROM @jg_add_task_deleted_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
