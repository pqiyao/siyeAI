CREATE TABLE app_conversation_memory_refresh_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(128) NULL,
    conversation_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    refresh_mode VARCHAR(32) NOT NULL,
    extraction_mode VARCHAR(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    input_message_count INT NOT NULL DEFAULT 0,
    visible_message_count INT NOT NULL DEFAULT 0,
    existing_entry_count INT NOT NULL DEFAULT 0,
    model_output_entry_count INT NOT NULL DEFAULT 0,
    accepted_entry_count INT NOT NULL DEFAULT 0,
    rejected_entry_count INT NOT NULL DEFAULT 0,
    conflict_count INT NOT NULL DEFAULT 0,
    disable_requested_count INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_memory_refresh_metric_conversation
    ON app_conversation_memory_refresh_metric(conversation_id, branch_id, created_at);
CREATE INDEX idx_memory_refresh_metric_request
    ON app_conversation_memory_refresh_metric(request_id);
CREATE INDEX idx_memory_refresh_metric_mode
    ON app_conversation_memory_refresh_metric(extraction_mode, created_at);
