SET @jg_add_attempt_request_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE app_generation_attempt ADD COLUMN request_id VARCHAR(128) NULL AFTER generation_task_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_attempt'
      AND column_name = 'request_id'
);
PREPARE stmt FROM @jg_add_attempt_request_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_attempt_trace_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE app_generation_attempt ADD COLUMN trace_id VARCHAR(64) NULL AFTER request_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_attempt'
      AND column_name = 'trace_id'
);
PREPARE stmt FROM @jg_add_attempt_trace_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_attempt_error_message = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE app_generation_attempt ADD COLUMN error_message VARCHAR(512) NULL AFTER error_code',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_attempt'
      AND column_name = 'error_message'
);
PREPARE stmt FROM @jg_add_attempt_error_message;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_attempt_trace_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_generation_attempt_trace ON app_generation_attempt(trace_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_attempt'
      AND index_name = 'idx_generation_attempt_trace'
);
PREPARE stmt FROM @jg_add_attempt_trace_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_attempt_request_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_generation_attempt_request ON app_generation_attempt(request_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_generation_attempt'
      AND index_name = 'idx_generation_attempt_request'
);
PREPARE stmt FROM @jg_add_attempt_request_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
