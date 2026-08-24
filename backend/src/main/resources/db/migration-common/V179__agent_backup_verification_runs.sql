CREATE TABLE IF NOT EXISTS app_agent_backup_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NULL,
    step_id BIGINT NULL,
    backup_type VARCHAR(64) NOT NULL,
    backup_path VARCHAR(500) NULL,
    content_hash VARCHAR(128) NULL,
    size_bytes BIGINT NULL,
    verified TINYINT NOT NULL DEFAULT 0,
    restore_tested TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_backup_run_task (task_id, created_at),
    KEY idx_agent_backup_run_status (status, created_at)
);
