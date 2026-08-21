CREATE TABLE IF NOT EXISTS app_successful_ai_response_dedupe (
    request_id VARCHAR(128) NOT NULL,
    response_kind VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, response_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_stats_counter (counter_key, counter_value)
SELECT
    'successful_ai_responses',
    COALESCE((
        SELECT SUM(task_count)
        FROM app_generation_task_daily_stat
        WHERE status = 'SUCCESS'
          AND channel IN ('CHAT_SYNC', 'CHAT_STREAM', 'CONTINUE', 'CONTINUE_STREAM', 'REGEN', 'REGEN_STREAM')
    ), 0)
    + COALESCE((
        SELECT COUNT(*)
        FROM app_generation_stat_event
        WHERE status = 'SUCCESS'
          AND channel IN ('CHAT_SYNC', 'CHAT_STREAM', 'CONTINUE', 'CONTINUE_STREAM', 'REGEN', 'REGEN_STREAM')
    ), 0)
    + COALESCE((
        SELECT COUNT(*)
        FROM app_generation_task t
        LEFT JOIN app_generation_stat_event e ON e.task_id = t.id
        WHERE e.id IS NULL
          AND t.status = 'SUCCESS'
          AND t.channel IN ('CHAT_SYNC', 'CHAT_STREAM', 'CONTINUE', 'CONTINUE_STREAM', 'REGEN', 'REGEN_STREAM')
          AND t.deleted_at IS NULL
    ), 0)
ON DUPLICATE KEY UPDATE
    counter_value = GREATEST(counter_value, VALUES(counter_value)),
    updated_at = CURRENT_TIMESTAMP;
