INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'planner.analyze', '1.0', '分析 Agent 需求', 'PLANNING', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":true}',
    '{"type":"object","required":["taskTitle","objective","result"]}',
    '["Planner"]', '将用户目标整理为执行上下文，不直接执行外部副作用。',
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
