INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'story.revise', '1.0', '修订章节正文', 'CONTENT', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"artifactKey":{"type":"string"}},"additionalProperties":true}',
    '{"type":"object","required":["operation","status","revisionApplied"]}',
    '["Writer"]',
    '读取模型一致性审阅结果；仅在发现具体问题时有限修订章节，并等待最终一致性复核。',
    180, 2, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
