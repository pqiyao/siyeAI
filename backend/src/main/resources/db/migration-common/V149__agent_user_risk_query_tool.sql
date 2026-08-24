INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.risk.query', '1.0', '用户风险只读摘要', 'USER_OPERATIONS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["userId"],"properties":{"userId":{"type":"integer","minimum":1}},"additionalProperties":false}',
    '{"type":"object","required":["userId","deviceCount","riskyDeviceCount","devices"]}',
    '["Data Analyst","Reviewer","Support Agent"]',
    '读取现有访客风险事实中的设备变化、限流、IP 变化和风险分数，不执行封禁、清理或删除。',
    30, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
