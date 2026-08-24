INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.ticket.query', '1.0', '用户工单只读摘要', 'USER_OPERATIONS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["userId"],"properties":{"userId":{"type":"integer","minimum":1},"status":{"type":"string","maxLength":32},"limit":{"type":"integer","minimum":1,"maximum":50}},"additionalProperties":false}',
    '{"type":"object","required":["userId","count","tickets"]}',
    '["Data Analyst","Reviewer","Support Agent"]',
    '按用户读取工单状态、主题、优先级和消息数摘要，不返回工单正文，不执行回复、关闭或修改。',
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
