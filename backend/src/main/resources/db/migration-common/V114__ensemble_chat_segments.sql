ALTER TABLE app_character
    ADD COLUMN ensemble_chat_mode VARCHAR(16) NOT NULL DEFAULT 'NATURAL';

CREATE TABLE app_message_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    segment_index INT NOT NULL,
    segment_type VARCHAR(16) NOT NULL,
    speaker_member_id BIGINT NULL,
    speaker_name_snapshot VARCHAR(64) NULL,
    speaker_avatar_snapshot VARCHAR(512) NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_segment_message
        FOREIGN KEY (message_id) REFERENCES app_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_segment_member
        FOREIGN KEY (speaker_member_id) REFERENCES app_character_member(id) ON DELETE SET NULL,
    CONSTRAINT uk_message_segment_index UNIQUE (message_id, segment_index)
);

CREATE INDEX idx_message_segment_member
    ON app_message_segment(speaker_member_id, message_id);
