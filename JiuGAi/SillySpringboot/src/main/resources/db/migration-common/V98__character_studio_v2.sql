ALTER TABLE app_character
    ADD COLUMN card_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE';

CREATE INDEX idx_character_card_type
    ON app_character(card_type, deleted_at, id);

CREATE TABLE app_character_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    tagline VARCHAR(255) NULL,
    persona TEXT NULL,
    avatar_url VARCHAR(512) NULL,
    voice_config_json TEXT NULL,
    image_reference_url VARCHAR(512) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_character_member_character
        FOREIGN KEY (character_id) REFERENCES app_character(id) ON DELETE CASCADE
);

CREATE INDEX idx_character_member_character
    ON app_character_member(character_id, enabled, deleted_at, sort_order, id);

CREATE TABLE app_character_opening (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    summary VARCHAR(255) NULL,
    scenario_override TEXT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_character_opening_character
        FOREIGN KEY (character_id) REFERENCES app_character(id) ON DELETE CASCADE
);

CREATE INDEX idx_character_opening_character
    ON app_character_opening(character_id, enabled, deleted_at, sort_order, id);

CREATE TABLE app_character_opening_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    opening_id BIGINT NOT NULL,
    speaker_member_id BIGINT NULL,
    speaker_type VARCHAR(16) NOT NULL DEFAULT 'CHARACTER',
    content TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opening_segment_opening
        FOREIGN KEY (opening_id) REFERENCES app_character_opening(id) ON DELETE CASCADE,
    CONSTRAINT fk_opening_segment_member
        FOREIGN KEY (speaker_member_id) REFERENCES app_character_member(id) ON DELETE SET NULL
);

CREATE INDEX idx_opening_segment_opening
    ON app_character_opening_segment(opening_id, sort_order, id);

ALTER TABLE app_lorebook_entry
    ADD COLUMN title VARCHAR(120) NULL;
ALTER TABLE app_lorebook_entry
    ADD COLUMN member_id BIGINT NULL;
ALTER TABLE app_lorebook_entry
    ADD COLUMN secondary_keywords_csv TEXT NULL;
ALTER TABLE app_lorebook_entry
    ADD COLUMN match_mode VARCHAR(16) NOT NULL DEFAULT 'ANY';
ALTER TABLE app_lorebook_entry
    ADD COLUMN injection_position VARCHAR(24) NOT NULL DEFAULT 'BEFORE_CHARACTER';

CREATE INDEX idx_lorebook_character_member
    ON app_lorebook_entry(character_id, member_id, enabled, priority, id);

ALTER TABLE app_message
    ADD COLUMN speaker_member_id BIGINT NULL;
ALTER TABLE app_message
    ADD COLUMN speaker_name_snapshot VARCHAR(64) NULL;
ALTER TABLE app_message
    ADD COLUMN speaker_avatar_snapshot VARCHAR(512) NULL;

CREATE INDEX idx_message_speaker_member
    ON app_message(speaker_member_id, conversation_id, branch_id, id);

ALTER TABLE app_conversation
    ADD COLUMN opening_id BIGINT NULL;

ALTER TABLE app_conversation_branch
    ADD COLUMN opening_id BIGINT NULL;

CREATE INDEX idx_conversation_opening
    ON app_conversation(opening_id);

CREATE INDEX idx_conversation_branch_opening
    ON app_conversation_branch(conversation_id, opening_id);
