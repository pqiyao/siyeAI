ALTER TABLE app_chat_preset
    ADD COLUMN global_default TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE app_generation_task
    ADD COLUMN effective_preset_id BIGINT NULL;
ALTER TABLE app_generation_task
    ADD COLUMN effective_max_context INT NULL;
ALTER TABLE app_generation_task
    ADD COLUMN effective_max_tokens INT NULL;
ALTER TABLE app_generation_task
    ADD COLUMN effective_provider VARCHAR(80) NULL;
ALTER TABLE app_generation_task
    ADD COLUMN effective_api_source VARCHAR(80) NULL;

ALTER TABLE app_generation_attempt
    ADD COLUMN effective_preset_id BIGINT NULL;
ALTER TABLE app_generation_attempt
    ADD COLUMN effective_max_context INT NULL;
ALTER TABLE app_generation_attempt
    ADD COLUMN effective_max_tokens INT NULL;
ALTER TABLE app_generation_attempt
    ADD COLUMN effective_provider VARCHAR(80) NULL;
ALTER TABLE app_generation_attempt
    ADD COLUMN effective_api_source VARCHAR(80) NULL;

UPDATE app_chat_preset
SET global_default = CASE
    WHEN scope = 'PUBLIC' AND owner_user_id IS NULL AND LOWER(name) = 'default' THEN 1
    ELSE 0
END
WHERE scope = 'PUBLIC' AND owner_user_id IS NULL;

CREATE INDEX idx_app_chat_preset_global_default
    ON app_chat_preset(scope, global_default, enabled, source_available, sort_order, id);
