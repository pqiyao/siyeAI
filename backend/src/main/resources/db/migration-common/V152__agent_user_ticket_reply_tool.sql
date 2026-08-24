INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.ticket.reply', '1.0', '审批后回复用户工单', 'USER_OPERATIONS', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["ticketNo","content"],"properties":{"ticketNo":{"type":"string","maxLength":64},"content":{"type":"string","minLength":1,"maxLength":20000},"adminName":{"type":"string","maxLength":80},"nextStatus":{"type":"string","enum":["OPEN","WAIT_USER","RESOLVED","CLOSED"]},"nextPriority":{"type":"string","enum":["LOW","NORMAL","HIGH","URGENT"]}},"additionalProperties":false}',
    '{"type":"object","required":["operation","ticketNo","ticket"]}',
    '["Support Agent","Reviewer"]',
    '在人工审批通过后回复用户工单并更新状态；复用既有客服通知、状态和优先级校验，不自动处理退款、余额或封禁。',
    30, 1, 0, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    risk_level = VALUES(risk_level),
    approval_required = VALUES(approval_required),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
