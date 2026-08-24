INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'story.create_outline', '1.0', '生成剧情大纲', 'CONTENT', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"story":{"type":"object"}},"additionalProperties":true}',
    '{"type":"object","required":["artifactKey","artifactId","chapterCount","endingCount","outline"]}',
    '["Plotter"]',
    '根据任务约束、世界书和角色前序结果生成可验证的互动剧情节点图，并保存为 JSON 产物。',
    120, 2, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
