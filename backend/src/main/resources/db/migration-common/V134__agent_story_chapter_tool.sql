INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'story.write_chapter', '1.0', '生成章节正文', 'CONTENT', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"story":{"type":"object"}},"additionalProperties":true}',
    '{"type":"object","required":["operation","artifactKey","artifactId","chapterCount"]}',
    '["Writer"]',
    '读取剧情大纲并生成章节正文产物；支持任务约束提供章节内容，未提供时生成可复现的章节草稿。',
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
