INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'worldbook.create', '1.0', '创建世界书', 'CONTENT', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","properties":{"worldbook":{"type":"object"}},"additionalProperties":true}',
    '{"type":"object","required":["worldbookName","entryCount"]}',
    '["World Builder"]',
    '通过现有 SillyTavern Adapter 创建或更新标准 World Info 世界书，返回稳定名称供角色步骤关联。',
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
