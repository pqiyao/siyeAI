CREATE TABLE IF NOT EXISTS app_agent_alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_key VARCHAR(80) NOT NULL DEFAULT 'platform',
    fingerprint VARCHAR(160) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    code VARCHAR(80) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_by BIGINT NULL,
    acknowledged_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_alert_fingerprint (tenant_key, fingerprint),
    KEY idx_agent_alert_status_seen (status, last_seen_at)
);
