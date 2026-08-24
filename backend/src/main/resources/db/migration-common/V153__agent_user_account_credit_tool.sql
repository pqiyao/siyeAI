INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'user.account.credit', '1.0', '审批后补发钱包余额', 'USER_OPERATIONS', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["userId"],"properties":{"userId":{"type":"integer","minimum":1},"score":{"type":"integer","minimum":0,"maximum":1000000},"goldCoin":{"type":"integer","minimum":0,"maximum":1000000},"note":{"type":"string","maxLength":500}},"additionalProperties":false}',
    '{"type":"object","required":["operation","userId","score","goldCoin","idempotencyKey"]}',
    '["Finance Agent","Reviewer"]',
    '在人工审批通过后向用户钱包正向补发钻石或金币并写入独立 Agent 流水；不覆盖余额、不扣款、不退款。',
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
