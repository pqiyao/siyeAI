CREATE TABLE app_agent_usage_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    tenant_key VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(220) NOT NULL,
    tool_name VARCHAR(160) NULL,
    attempt INT NOT NULL,
    token_usage BIGINT NOT NULL DEFAULT 0,
    cost_amount DECIMAL(20, 6) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (idempotency_key),
    INDEX idx_agent_usage_tenant_created (tenant_key, created_at),
    INDEX idx_agent_usage_task_created (task_id, created_at)
);
