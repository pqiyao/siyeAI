CREATE TABLE app_user_chat_preference (
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL DEFAULT 0,
    bubble_json TEXT NULL,
    reading_json TEXT NULL,
    reply_format_json TEXT NULL,
    revision INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, character_id),
    CONSTRAINT fk_chat_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_chat_preference_character ON app_user_chat_preference(character_id);

CREATE TABLE app_h5_security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NULL,
    event_type VARCHAR(40) NOT NULL,
    client_uid VARCHAR(64) NULL,
    user_id BIGINT NULL,
    ip_address VARCHAR(64) NULL,
    ua_hash VARCHAR(64) NULL,
    endpoint_group VARCHAR(80) NULL,
    detail VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_h5_security_event_created ON app_h5_security_event(created_at);
CREATE INDEX idx_h5_security_event_device ON app_h5_security_event(device_id, created_at);
CREATE INDEX idx_h5_security_event_type ON app_h5_security_event(event_type, created_at);
CREATE INDEX idx_h5_security_event_client_uid ON app_h5_security_event(client_uid, created_at);
