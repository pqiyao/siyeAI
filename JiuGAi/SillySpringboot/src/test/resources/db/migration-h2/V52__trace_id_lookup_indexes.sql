CREATE INDEX IF NOT EXISTS idx_task_trace_id ON app_generation_task(trace_id);

CREATE INDEX IF NOT EXISTS idx_msg_trace_id ON app_message(trace_id);

