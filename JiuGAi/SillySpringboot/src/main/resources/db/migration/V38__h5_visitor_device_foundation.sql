CREATE TABLE app_h5_visitor_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_token VARCHAR(80) NOT NULL,
    first_client_uid VARCHAR(64) NULL,
    latest_client_uid VARCHAR(64) NULL,
    first_user_id BIGINT NULL,
    latest_user_id BIGINT NULL,
    first_ip VARCHAR(64) NULL,
    latest_ip VARCHAR(64) NULL,
    ua_hash VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_h5_visitor_device_token UNIQUE (device_token),
    CONSTRAINT fk_h5_visitor_first_user FOREIGN KEY (first_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_h5_visitor_latest_user FOREIGN KEY (latest_user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_h5_visitor_latest_user_id ON app_h5_visitor_device(latest_user_id);
CREATE INDEX idx_h5_visitor_latest_client_uid ON app_h5_visitor_device(latest_client_uid);
