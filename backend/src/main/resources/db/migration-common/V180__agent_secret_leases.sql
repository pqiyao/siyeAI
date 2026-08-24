CREATE TABLE IF NOT EXISTS app_agent_secret_lease (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lease_id VARCHAR(160) NOT NULL,
    tenant_key VARCHAR(80) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_secret_lease (lease_id),
    KEY idx_agent_secret_lease_scope (tenant_key, provider, status, expires_at)
);
