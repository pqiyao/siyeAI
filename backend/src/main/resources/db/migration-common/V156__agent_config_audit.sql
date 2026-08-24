CREATE TABLE app_agent_config_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_type VARCHAR(32) NOT NULL,
    config_id BIGINT NULL,
    config_key VARCHAR(80) NOT NULL,
    version INT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT NULL,
    summary VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_config_audit_key (config_type, config_key, created_at),
    INDEX idx_agent_config_audit_operator (operator_id, created_at)
);
