ALTER TABLE app_external_cleanup_task
    ALTER COLUMN source_user_id BIGINT NULL;

ALTER TABLE app_external_cleanup_task
    ADD COLUMN operation_id CHAR(36) NULL;

ALTER TABLE app_external_cleanup_task
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'USER_DELETION';

ALTER TABLE app_external_cleanup_task
    ADD COLUMN source_character_id BIGINT NULL;

ALTER TABLE app_external_cleanup_task
    ADD COLUMN context_json CLOB NULL;

CREATE INDEX idx_external_cleanup_operation
    ON app_external_cleanup_task(operation_id, created_at);

CREATE INDEX idx_external_cleanup_character
    ON app_external_cleanup_task(source_character_id, created_at);
