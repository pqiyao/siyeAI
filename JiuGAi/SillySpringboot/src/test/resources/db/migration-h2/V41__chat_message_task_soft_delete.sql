ALTER TABLE app_message ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL DEFAULT NULL;

ALTER TABLE app_generation_task ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_msg_conv_deleted_id ON app_message(conversation_id, deleted_at, id);

CREATE INDEX IF NOT EXISTS idx_task_conv_deleted ON app_generation_task(conversation_id, deleted_at);

