ALTER TABLE app_chat_preset
    ADD COLUMN source_available TINYINT NOT NULL DEFAULT 1;

CREATE INDEX idx_app_chat_preset_source_available
    ON app_chat_preset(source_type, api_type, source_available);
