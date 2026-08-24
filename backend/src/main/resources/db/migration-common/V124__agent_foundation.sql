CREATE TABLE app_agent_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_key VARCHAR(80) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    objective TEXT NOT NULL,
    constraints_json TEXT NULL,
    status VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
    created_by BIGINT NULL,
    priority INT NOT NULL DEFAULT 0,
    max_steps INT NOT NULL DEFAULT 50,
    max_retries INT NOT NULL DEFAULT 3,
    max_runtime_seconds INT NOT NULL DEFAULT 3600,
    max_tokens BIGINT NOT NULL DEFAULT 0,
    max_cost DECIMAL(20, 6) NOT NULL DEFAULT 0,
    current_step_id BIGINT NULL,
    total_steps INT NOT NULL DEFAULT 0,
    completed_steps INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    token_usage BIGINT NOT NULL DEFAULT 0,
    cost_amount DECIMAL(20, 6) NOT NULL DEFAULT 0,
    artifact_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_key)
);

CREATE INDEX idx_agent_task_status_updated ON app_agent_task(status, updated_at);
CREATE INDEX idx_agent_task_creator_created ON app_agent_task(created_by, created_at);

CREATE TABLE app_agent_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    plan_version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json TEXT NOT NULL,
    input_hash VARCHAR(128) NULL,
    generated_by VARCHAR(80) NULL,
    approved_by BIGINT NULL,
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_id, plan_version)
);

CREATE INDEX idx_agent_plan_task_status ON app_agent_plan(task_id, status);

CREATE TABLE app_agent_step (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    step_key VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    agent_role VARCHAR(80) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    depends_on_json TEXT NULL,
    tool_name VARCHAR(160) NULL,
    input_json TEXT NULL,
    output_json TEXT NULL,
    output_summary TEXT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    sort_order INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error_code VARCHAR(80) NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_id, step_key)
);

CREATE INDEX idx_agent_step_ready ON app_agent_step(status, lease_until, updated_at);
CREATE INDEX idx_agent_step_task_order ON app_agent_step(task_id, sort_order, id);

CREATE TABLE app_agent_tool_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tool_key VARCHAR(160) NOT NULL,
    version VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(64) NOT NULL,
    handler_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    input_schema TEXT NOT NULL,
    output_schema TEXT NOT NULL,
    allowed_roles_json TEXT NULL,
    description TEXT NULL,
    timeout_seconds INT NOT NULL DEFAULT 120,
    max_retries INT NOT NULL DEFAULT 2,
    idempotent TINYINT NOT NULL DEFAULT 1,
    approval_required TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tool_key, version)
);

CREATE INDEX idx_agent_tool_enabled ON app_agent_tool_definition(enabled, category, risk_level);

CREATE TABLE app_agent_tool_call (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    tool_definition_id BIGINT NULL,
    attempt INT NOT NULL,
    idempotency_key VARCHAR(220) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_hash VARCHAR(128) NULL,
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    output_ref VARCHAR(500) NULL,
    error_code VARCHAR(80) NULL,
    error_message TEXT NULL,
    duration_ms BIGINT NULL,
    provider VARCHAR(80) NULL,
    model_name VARCHAR(160) NULL,
    token_usage BIGINT NOT NULL DEFAULT 0,
    cost_amount DECIMAL(20, 6) NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (idempotency_key)
);

CREATE INDEX idx_agent_tool_call_task ON app_agent_tool_call(task_id, created_at);
CREATE INDEX idx_agent_tool_call_step ON app_agent_tool_call(step_id, attempt);

CREATE TABLE app_agent_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    event_seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    summary TEXT NULL,
    payload_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_id, event_seq)
);

CREATE INDEX idx_agent_event_task_seq ON app_agent_event(task_id, event_seq);

CREATE TABLE app_agent_approval (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    approval_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by BIGINT NULL,
    reviewer_id BIGINT NULL,
    reason TEXT NULL,
    target_summary TEXT NOT NULL,
    parameter_hash VARCHAR(128) NULL,
    expires_at TIMESTAMP NULL,
    decided_at TIMESTAMP NULL,
    decision_comment TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_agent_approval_status_created ON app_agent_approval(status, created_at);
CREATE INDEX idx_agent_approval_task ON app_agent_approval(task_id, created_at);

CREATE TABLE app_agent_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    artifact_key VARCHAR(160) NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    uri VARCHAR(500) NULL,
    content_hash VARCHAR(128) NULL,
    size_bytes BIGINT NULL,
    mime_type VARCHAR(160) NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_id, artifact_key, version)
);

CREATE INDEX idx_agent_artifact_task_type ON app_agent_artifact(task_id, artifact_type, status);
