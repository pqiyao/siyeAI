ALTER TABLE app_generation_attempt ADD COLUMN IF NOT EXISTS request_id VARCHAR(128) NULL;
ALTER TABLE app_generation_attempt ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64) NULL;
ALTER TABLE app_generation_attempt ADD COLUMN IF NOT EXISTS error_message VARCHAR(512) NULL;

CREATE INDEX IF NOT EXISTS idx_generation_attempt_trace ON app_generation_attempt(trace_id);
CREATE INDEX IF NOT EXISTS idx_generation_attempt_request ON app_generation_attempt(request_id);
