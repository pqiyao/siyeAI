CREATE TABLE IF NOT EXISTS app_character_image_policy (
    character_id BIGINT NOT NULL PRIMARY KEY,
    image_enabled TINYINT(1) NULL,
    default_mode VARCHAR(16) NULL,
    allowed_modes_json VARCHAR(128) NULL,
    reference_source_mode VARCHAR(32) NULL,
    reference_images_enabled TINYINT(1) NULL,
    negative_prompt VARCHAR(2000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_character_image_policy_character
        FOREIGN KEY (character_id) REFERENCES app_character(id) ON DELETE CASCADE
);

CREATE INDEX idx_character_image_policy_updated
    ON app_character_image_policy(updated_at);
