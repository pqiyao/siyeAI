CREATE TABLE app_character_system_promotion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_character_id BIGINT NOT NULL,
    source_user_id BIGINT NOT NULL,
    target_character_id BIGINT NOT NULL,
    keep_creator_attribution BOOLEAN NOT NULL DEFAULT TRUE,
    promoted_by VARCHAR(64) NOT NULL,
    promoted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_character_system_promotion_source UNIQUE (source_character_id),
    CONSTRAINT uk_character_system_promotion_target UNIQUE (target_character_id),
    CONSTRAINT fk_character_promotion_source
        FOREIGN KEY (source_character_id) REFERENCES app_character(id),
    CONSTRAINT fk_character_promotion_target
        FOREIGN KEY (target_character_id) REFERENCES app_character(id)
);

CREATE INDEX idx_character_promotion_user
    ON app_character_system_promotion(source_user_id, promoted_at);

CREATE INDEX idx_character_promotion_operator
    ON app_character_system_promotion(promoted_by, promoted_at);
