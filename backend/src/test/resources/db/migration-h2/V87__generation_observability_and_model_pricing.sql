CREATE TABLE app_generation_model_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_key VARCHAR(80) NOT NULL,
    model_pattern VARCHAR(255) NOT NULL,
    version VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    input_usd_per_million_tokens DECIMAL(20, 10) NOT NULL DEFAULT 0,
    output_usd_per_million_tokens DECIMAL(20, 10) NOT NULL DEFAULT 0,
    effective_from TIMESTAMP(6) NOT NULL,
    effective_to TIMESTAMP(6) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_generation_pricing_version UNIQUE (
        provider_key,
        model_pattern,
        version,
        effective_from
    )
);

CREATE INDEX idx_generation_pricing_lookup
    ON app_generation_model_pricing(provider_key, enabled, effective_from, effective_to);

CREATE TABLE app_generation_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_task_id BIGINT NULL,
    conversation_id BIGINT NULL,
    character_id BIGINT NULL,
    attempt_no INT NOT NULL,
    provider_key VARCHAR(80) NOT NULL,
    route_key VARCHAR(80) NOT NULL,
    provider_source VARCHAR(80) NULL,
    model VARCHAR(255) NULL,
    byok BOOLEAN NOT NULL DEFAULT FALSE,
    was_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP(6) NOT NULL,
    first_token_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NOT NULL,
    ttft_ms INT NULL,
    duration_ms INT NOT NULL,
    http_status INT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    prompt_tokens_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    completion_tokens_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    pricing_id BIGINT NULL,
    pricing_version VARCHAR(64) NULL,
    currency CHAR(3) NULL,
    input_usd_per_million_tokens DECIMAL(20, 10) NULL,
    output_usd_per_million_tokens DECIMAL(20, 10) NULL,
    input_cost_usd DECIMAL(20, 10) NULL,
    output_cost_usd DECIMAL(20, 10) NULL,
    total_cost_usd DECIMAL(20, 10) NULL,
    cost_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    cost_partial BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_generation_attempt_started ON app_generation_attempt(started_at);
CREATE INDEX idx_generation_attempt_provider ON app_generation_attempt(provider_key, started_at);
CREATE INDEX idx_generation_attempt_model ON app_generation_attempt(model, started_at);
CREATE INDEX idx_generation_attempt_character ON app_generation_attempt(character_id, started_at);
CREATE INDEX idx_generation_attempt_route ON app_generation_attempt(route_key, started_at);
CREATE INDEX idx_generation_attempt_status ON app_generation_attempt(status, started_at);
CREATE INDEX idx_generation_attempt_task ON app_generation_attempt(generation_task_id);
