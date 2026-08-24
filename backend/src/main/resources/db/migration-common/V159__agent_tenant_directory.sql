CREATE TABLE app_agent_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_key VARCHAR(120) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_key),
    INDEX idx_agent_tenant_status (status, updated_at)
);

CREATE TABLE app_agent_tenant_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_key VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_key, user_id),
    INDEX idx_agent_tenant_member_user (user_id, status),
    CONSTRAINT fk_agent_tenant_member_tenant FOREIGN KEY (tenant_key) REFERENCES app_agent_tenant(tenant_key)
);

INSERT INTO app_agent_tenant (tenant_key, display_name, status)
VALUES ('platform', 'Platform', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status), updated_at = CURRENT_TIMESTAMP;
