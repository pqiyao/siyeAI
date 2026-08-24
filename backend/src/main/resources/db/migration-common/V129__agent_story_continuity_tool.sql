INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'story.check_continuity', '1.0', '检查剧情一致性', 'CONTENT', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","properties":{"artifactKey":{"type":"string"}},"additionalProperties":true}',
    '{"type":"object","required":["operation","passed","issues","warnings","reachableNodeCount"]}',
    '["Reviewer"]',
    '检查剧情大纲节点、分支、章节、结局及世界书和角色引用，保存确定性一致性报告。',
    60, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
