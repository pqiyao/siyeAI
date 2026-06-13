ALTER TABLE app_message
    ADD COLUMN voice_url VARCHAR(255) NULL DEFAULT NULL AFTER content,
    ADD COLUMN voice_duration_ms INT NULL DEFAULT NULL AFTER voice_url;
