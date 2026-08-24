INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.session.create', '1.0', 'DeepSeek Harness 会话', 'AGENT_RUNTIME', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","properties":{"agentPreset":{"type":"string","maxLength":160}},"additionalProperties":false}',
    '{"type":"object","required":["sessionId"]}',
    '["Planner","Developer","Researcher","Writer"]',
    '在固定 DSH workspace 根目录创建会话；不得由输入指定任意 cwd，执行前需要审批。',
    30, 0, 0, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
