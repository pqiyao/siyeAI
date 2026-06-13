CREATE TABLE IF NOT EXISTS app_generation_stat_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'TASK',
    source_id BIGINT NULL,
    event_day DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    channel VARCHAR(32) NULL,
    model VARCHAR(160) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_generation_stat_task (task_id),
    UNIQUE KEY uk_generation_stat_source (source_type, source_id),
    KEY idx_generation_stat_day (event_day),
    KEY idx_generation_stat_status_day (status, event_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    NULL,
    'LEGACY_MESSAGE',
    m.id,
    DATE(m.created_at),
    'SUCCESS',
    'LEGACY_MESSAGE',
    NULL,
    COALESCE(m.created_at, CURRENT_TIMESTAMP),
    CURRENT_TIMESTAMP
FROM app_message m
WHERE m.role = 'assistant'
  AND COALESCE(m.status, '') != 'FAILED'
  AND NOT EXISTS (
      SELECT 1
      FROM app_generation_task t
      WHERE DATE(COALESCE(t.queued_at, t.started_at, t.finished_at, CURRENT_TIMESTAMP)) = DATE(m.created_at)
      LIMIT 1
  )
ON DUPLICATE KEY UPDATE
    event_day = VALUES(event_day),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP;
