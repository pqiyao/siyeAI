INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.account.query', '1.0', '用户账户只读摘要', 'USER_OPERATIONS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["userId"],"properties":{"userId":{"type":"integer","minimum":1},"limit":{"type":"integer","minimum":1,"maximum":50}},"additionalProperties":false}',
    '{"type":"object","required":["userId","account","orders"]}',
    '["Data Analyst","Reviewer","Support Agent"]',
    '读取用户钱包余额、VIP 摘要和订单状态，不执行充值、退款、余额修改或权限修改。',
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
