CREATE TABLE app_agent_admin_tenant_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    tenant_key VARCHAR(120) NOT NULL,
    access_role VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_admin_tenant_scope (admin_id, tenant_key),
    KEY idx_agent_admin_tenant_scope_status (admin_id, status),
    CONSTRAINT fk_agent_admin_tenant_scope_tenant FOREIGN KEY (tenant_key)
        REFERENCES app_agent_tenant(tenant_key)
);
