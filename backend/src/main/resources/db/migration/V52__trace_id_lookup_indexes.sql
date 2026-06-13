SET @task_trace_idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_task'
      AND index_name = 'idx_task_trace_id'
);
SET @task_trace_idx_sql := IF(
    @task_trace_idx_exists = 0,
    'CREATE INDEX idx_task_trace_id ON app_generation_task(trace_id)',
    'SELECT 1'
);
PREPARE stmt_task_trace_idx FROM @task_trace_idx_sql;
EXECUTE stmt_task_trace_idx;
DEALLOCATE PREPARE stmt_task_trace_idx;

SET @msg_trace_idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_message'
      AND index_name = 'idx_msg_trace_id'
);
SET @msg_trace_idx_sql := IF(
    @msg_trace_idx_exists = 0,
    'CREATE INDEX idx_msg_trace_id ON app_message(trace_id)',
    'SELECT 1'
);
PREPARE stmt_msg_trace_idx FROM @msg_trace_idx_sql;
EXECUTE stmt_msg_trace_idx;
DEALLOCATE PREPARE stmt_msg_trace_idx;
