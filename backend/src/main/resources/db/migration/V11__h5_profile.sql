-- H5 compat: chat persona/profile per user

CREATE TABLE app_h5_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(64) NULL,
    persona TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_h5_profile_user UNIQUE (user_id)
);

CREATE INDEX idx_h5_profile_updated_at ON app_h5_profile(updated_at);

