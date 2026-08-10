CREATE TABLE app_message_semantic_annotation (
    message_id BIGINT PRIMARY KEY,
    content_hash VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    classifier_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    segments_json MEDIUMTEXT NULL,
    confidence DECIMAL(5,4) NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_semantic_annotation_message
        FOREIGN KEY (message_id) REFERENCES app_message(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_semantic_annotation_status
    ON app_message_semantic_annotation(status, updated_at);
