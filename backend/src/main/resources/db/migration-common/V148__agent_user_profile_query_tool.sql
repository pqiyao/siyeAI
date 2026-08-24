INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.profile.query', '1.0', '用户资料只读摘要', 'USER_OPERATIONS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["userId"],"properties":{"userId":{"type":"integer","minimum":1}},"additionalProperties":false}',
    '{"type":"object","required":["userId","exists"]}',
    '["Data Analyst","Reviewer","Support Agent"]',
    '读取用户资料、状态、VIP 和钱包摘要，不返回密码、凭据、完整登录信息或执行修改。',
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
