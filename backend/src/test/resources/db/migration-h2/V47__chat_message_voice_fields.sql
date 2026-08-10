ALTER TABLE app_message
    ADD COLUMN voice_url VARCHAR(255) NULL DEFAULT NULL;

ALTER TABLE app_message
    ADD COLUMN voice_duration_ms INT NULL DEFAULT NULL;
