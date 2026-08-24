INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.session.prompt', '1.0', 'DeepSeek Harness Prompt', 'AGENT_RUNTIME', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["sessionId","prompt"],"properties":{"sessionId":{"type":"string","minLength":1,"maxLength":160},"prompt":{"type":"string","minLength":1,"maxLength":32000}},"additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher","Writer"]',
    '向已存在的 DeepSeek Harness 会话提交文本 Prompt；必须经过 Agent 工具审批、预算和审计链。',
    60, 1, 0, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
