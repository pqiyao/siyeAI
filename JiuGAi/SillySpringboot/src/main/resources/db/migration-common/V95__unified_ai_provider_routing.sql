CREATE TABLE IF NOT EXISTS app_ai_provider_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    vendor VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    api_key_cipher VARCHAR(4096) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    connect_timeout_seconds INT NOT NULL DEFAULT 10,
    request_timeout_seconds INT NOT NULL DEFAULT 90,
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_provider_account_key UNIQUE (provider_key)
);

CREATE TABLE IF NOT EXISTS app_ai_provider_deployment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    capability VARCHAR(16) NOT NULL,
    protocol_type VARCHAR(32) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    voice_name VARCHAR(255) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    failure_threshold INT NOT NULL DEFAULT 3,
    cooldown_seconds INT NOT NULL DEFAULT 180,
    consecutive_failures INT NOT NULL DEFAULT 0,
    circuit_open_until TIMESTAMP NULL,
    last_health_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
    last_error VARCHAR(500) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_deployment_account FOREIGN KEY (account_id) REFERENCES app_ai_provider_account(id),
    CONSTRAINT uk_ai_deployment_model UNIQUE (account_id, capability, model_name, voice_name)
);

CREATE INDEX idx_ai_deployment_capability_enabled
    ON app_ai_provider_deployment(capability, enabled, id);

CREATE TABLE IF NOT EXISTS app_ai_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    capability VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_route_key UNIQUE (route_key)
);

CREATE TABLE IF NOT EXISTS app_ai_route_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    deployment_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_route_member_route FOREIGN KEY (route_id) REFERENCES app_ai_route(id),
    CONSTRAINT fk_ai_route_member_deployment FOREIGN KEY (deployment_id) REFERENCES app_ai_provider_deployment(id),
    CONSTRAINT uk_ai_route_member_deployment UNIQUE (route_id, deployment_id),
    CONSTRAINT uk_ai_route_member_order UNIQUE (route_id, sort_order)
);

CREATE INDEX idx_ai_route_member_route_order
    ON app_ai_route_member(route_id, sort_order);
