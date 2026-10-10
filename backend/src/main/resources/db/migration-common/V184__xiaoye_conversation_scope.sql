CREATE TABLE IF NOT EXISTS app_agent_xiaoye_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(160) NOT NULL,
    admin_id BIGINT NOT NULL,
    tenant_key VARCHAR(80) NULL,
    preset VARCHAR(160) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_xiaoye_conversation_session (session_id),
    KEY idx_agent_xiaoye_conversation_owner (admin_id, tenant_key, status, updated_at)
);
