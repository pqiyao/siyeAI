CREATE TABLE IF NOT EXISTS app_st_model_provider (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    st_source VARCHAR(32) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    reverse_proxy VARCHAR(512) NOT NULL DEFAULT '',
    proxy_password VARCHAR(255) NOT NULL DEFAULT '',
    custom_url VARCHAR(512) NOT NULL DEFAULT '',
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    failure_threshold INT NOT NULL DEFAULT 3,
    cooldown_seconds INT NOT NULL DEFAULT 180,
    consecutive_failures INT NOT NULL DEFAULT 0,
    circuit_open_until DATETIME NULL,
    last_error VARCHAR(500) NOT NULL DEFAULT '',
    last_used_at DATETIME NULL,
    last_health_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_st_model_provider_key (provider_key)
);

CREATE TABLE IF NOT EXISTS app_st_model_route (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    primary_provider_key VARCHAR(64) NOT NULL,
    fallback_provider_keys VARCHAR(500) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_st_model_route_scene (scene_key)
);
