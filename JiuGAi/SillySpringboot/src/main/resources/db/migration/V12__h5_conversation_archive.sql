-- H5 compat: archive/hide conversations instead of hard delete

CREATE TABLE app_conversation_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conv_archive_user_conv UNIQUE (user_id, conversation_id)
);

CREATE INDEX idx_conv_archive_user_id ON app_conversation_archive(user_id);
CREATE INDEX idx_conv_archive_conversation_id ON app_conversation_archive(conversation_id);

