-- H5 compat: bind legacy clientUid to app_user

CREATE TABLE app_h5_client_uid (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_h5_client_uid UNIQUE (client_uid),
    CONSTRAINT fk_h5_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_h5_user_id ON app_h5_client_uid(user_id);

