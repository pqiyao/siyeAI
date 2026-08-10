CREATE TABLE app_external_cleanup_task (
    id CHAR(36) NOT NULL PRIMARY KEY,
    task_key CHAR(64) NOT NULL,
    source_user_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    primary_ref CLOB NOT NULL,
    secondary_ref CLOB NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    next_attempt_at TIMESTAMP NULL,
    last_attempt_at TIMESTAMP NULL,
    locked_until TIMESTAMP NULL,
    lock_token CHAR(36) NULL,
    last_error CLOB NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_external_cleanup_task_key UNIQUE (task_key)
);

CREATE INDEX idx_external_cleanup_due ON app_external_cleanup_task(status, next_attempt_at);
CREATE INDEX idx_external_cleanup_lease ON app_external_cleanup_task(status, locked_until);
CREATE INDEX idx_external_cleanup_user ON app_external_cleanup_task(source_user_id, created_at);
