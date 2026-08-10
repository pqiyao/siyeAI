CREATE TABLE IF NOT EXISTS app_ai_chat_model_settings (
    id BIGINT PRIMARY KEY,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    shadow_enabled TINYINT(1) NOT NULL DEFAULT 1,
    canary_percent INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_ai_chat_model_settings (id, enabled, shadow_enabled, canary_percent)
SELECT 1, 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM app_ai_chat_model_settings WHERE id = 1);

CREATE TABLE IF NOT EXISTS app_ai_chat_offering (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    short_description VARCHAR(255) NOT NULL DEFAULT '',
    description VARCHAR(1000) NOT NULL DEFAULT '',
    tags VARCHAR(500) NOT NULL DEFAULT '',
    badge VARCHAR(64) NOT NULL DEFAULT '',
    context_label VARCHAR(64) NOT NULL DEFAULT '',
    speed_level INT NOT NULL DEFAULT 3,
    quality_level INT NOT NULL DEFAULT 3,
    route_key VARCHAR(64) NOT NULL,
    vip_min_level INT NOT NULL DEFAULT 0,
    recommended TINYINT(1) NOT NULL DEFAULT 0,
    default_offering TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    maintenance TINYINT(1) NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_chat_offering_code UNIQUE (offering_code)
);

CREATE INDEX idx_ai_chat_offering_visible
    ON app_ai_chat_offering(enabled, maintenance, sort_order, id);

CREATE TABLE IF NOT EXISTS app_ai_chat_offering_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_id BIGINT NOT NULL,
    vip_level INT NOT NULL DEFAULT 0,
    billing_mode VARCHAR(32) NOT NULL,
    quota_units INT NOT NULL DEFAULT 0,
    diamond_cost INT NOT NULL DEFAULT 0,
    gold_cost INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_chat_price_offering FOREIGN KEY (offering_id) REFERENCES app_ai_chat_offering(id),
    CONSTRAINT uk_ai_chat_price_vip UNIQUE (offering_id, vip_level)
);

CREATE TABLE IF NOT EXISTS app_h5_user_ai_chat_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 100,
    default_model TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    last_test_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
    last_tested_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_h5_user_ai_chat_model UNIQUE (user_id, model_name)
);

CREATE INDEX idx_h5_user_ai_chat_model_order
    ON app_h5_user_ai_chat_model(user_id, enabled, sort_order, id);

CREATE TABLE IF NOT EXISTS app_chat_model_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL DEFAULT 0,
    branch_id BIGINT NOT NULL DEFAULT 0,
    source_type VARCHAR(16) NOT NULL,
    offering_id BIGINT NULL,
    user_model_id BIGINT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_model_preference UNIQUE (user_id, conversation_id, branch_id)
);

CREATE TABLE IF NOT EXISTS app_chat_generation_context (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    generation_request_id VARCHAR(96) NOT NULL,
    action_type VARCHAR(24) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    offering_id BIGINT NULL,
    offering_code VARCHAR(64) NOT NULL DEFAULT '',
    offering_name VARCHAR(128) NOT NULL DEFAULT '',
    user_model_id BIGINT NULL,
    model_name_snapshot VARCHAR(255) NOT NULL DEFAULT '',
    route_key VARCHAR(64) NOT NULL DEFAULT '',
    billing_mode VARCHAR(32) NOT NULL DEFAULT 'FREE',
    quota_units INT NOT NULL DEFAULT 0,
    diamond_cost INT NOT NULL DEFAULT 0,
    gold_cost INT NOT NULL DEFAULT 0,
    charge_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    consume_biz_ref VARCHAR(96) NOT NULL DEFAULT '',
    first_content_emitted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_generation_context UNIQUE (user_id, generation_request_id, action_type)
);

CREATE INDEX idx_chat_generation_context_conversation
    ON app_chat_generation_context(conversation_id, created_at);

