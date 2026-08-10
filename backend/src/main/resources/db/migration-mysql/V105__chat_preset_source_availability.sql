ALTER TABLE app_chat_preset
    ADD COLUMN source_available TINYINT(1) NOT NULL DEFAULT 1 AFTER enabled,
    ADD KEY idx_app_chat_preset_source_available (source_type, api_type, source_available);
