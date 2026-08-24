CREATE TABLE app_agent_tenant_budget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_key VARCHAR(120) NOT NULL,
    max_daily_tokens BIGINT NOT NULL DEFAULT 0,
    max_daily_cost DECIMAL(20, 6) NOT NULL DEFAULT 0,
    max_daily_tasks BIGINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_key),
    CONSTRAINT fk_agent_tenant_budget_tenant FOREIGN KEY (tenant_key) REFERENCES app_agent_tenant(tenant_key)
);
