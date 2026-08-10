CREATE TABLE app_generation_task_daily_stat (
    stat_day DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT '',
    model VARCHAR(160) NOT NULL DEFAULT '',
    task_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_day, status, channel, model)
);

CREATE INDEX idx_generation_task_daily_status
    ON app_generation_task_daily_stat(status, stat_day);

CREATE TABLE app_generation_attempt_daily_stat (
    stat_day DATE NOT NULL,
    provider_key VARCHAR(80) NOT NULL,
    route_key VARCHAR(80) NOT NULL,
    model VARCHAR(255) NOT NULL DEFAULT '',
    character_id BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NOT NULL DEFAULT '',
    http_status INT NOT NULL DEFAULT 0,
    byok BOOLEAN NOT NULL DEFAULT FALSE,
    was_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count BIGINT NOT NULL DEFAULT 0,
    duration_sum_ms BIGINT NOT NULL DEFAULT 0,
    duration_sample_count BIGINT NOT NULL DEFAULT 0,
    ttft_sum_ms BIGINT NOT NULL DEFAULT 0,
    ttft_sample_count BIGINT NOT NULL DEFAULT 0,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_token_attempts BIGINT NOT NULL DEFAULT 0,
    total_cost_usd DECIMAL(24, 10) NOT NULL DEFAULT 0,
    priced_attempts BIGINT NOT NULL DEFAULT 0,
    unpriced_attempts BIGINT NOT NULL DEFAULT 0,
    estimated_cost_attempts BIGINT NOT NULL DEFAULT 0,
    partial_cost_attempts BIGINT NOT NULL DEFAULT 0,
    first_started_at TIMESTAMP(6) NOT NULL,
    last_finished_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (
        stat_day, provider_key, route_key, model, character_id,
        status, error_code, http_status, byok, was_fallback
    )
);

CREATE INDEX idx_generation_attempt_daily_provider
    ON app_generation_attempt_daily_stat(provider_key, stat_day);
CREATE INDEX idx_generation_attempt_daily_model
    ON app_generation_attempt_daily_stat(model, stat_day);
CREATE INDEX idx_generation_attempt_daily_character
    ON app_generation_attempt_daily_stat(character_id, stat_day);
CREATE INDEX idx_generation_attempt_daily_route
    ON app_generation_attempt_daily_stat(route_key, stat_day);
CREATE INDEX idx_generation_attempt_daily_error
    ON app_generation_attempt_daily_stat(error_code, stat_day);
