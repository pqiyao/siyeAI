INSERT INTO app_generation_stat_event (
    task_id,
    source_type,
    source_id,
    event_day,
    status,
    channel,
    model,
    created_at,
    updated_at
)
SELECT
    t.id,
    'TASK',
    t.id,
    DATE(COALESCE(t.queued_at, t.started_at, t.finished_at, CURRENT_TIMESTAMP)),
    COALESCE(NULLIF(t.status, ''), 'QUEUED'),
    t.channel,
    t.model,
    COALESCE(t.queued_at, CURRENT_TIMESTAMP),
    CURRENT_TIMESTAMP
FROM app_generation_task t
ON DUPLICATE KEY UPDATE
    event_day = VALUES(event_day),
    status = VALUES(status),
    channel = COALESCE(VALUES(channel), app_generation_stat_event.channel),
    model = COALESCE(VALUES(model), app_generation_stat_event.model),
    updated_at = CURRENT_TIMESTAMP;
