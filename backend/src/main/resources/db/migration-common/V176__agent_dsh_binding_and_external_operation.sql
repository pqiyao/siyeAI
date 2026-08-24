CREATE TABLE IF NOT EXISTS app_agent_dsh_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    session_id VARCHAR(160) NOT NULL,
    tenant_key VARCHAR(80) NOT NULL,
    workspace VARCHAR(500) NULL,
    preset VARCHAR(160) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_dsh_binding_task (task_id),
    KEY idx_agent_dsh_binding_session (session_id),
    KEY idx_agent_dsh_binding_tenant (tenant_key, status)
);

CREATE TABLE IF NOT EXISTS app_agent_external_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(160) NOT NULL,
    task_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    capability VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_id BIGINT NULL,
    rollback_ref VARCHAR(500) NULL,
    output_artifact VARCHAR(500) NULL,
    output_summary TEXT NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_external_operation_id (operation_id),
    KEY idx_agent_external_operation_task (task_id, created_at),
    KEY idx_agent_external_operation_capability (capability, status)
);
