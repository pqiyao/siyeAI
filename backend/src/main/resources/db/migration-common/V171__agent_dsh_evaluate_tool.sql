INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.evaluate.run', '1.0', 'DSH 任务评估', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Reviewer","Developer"]',
    '通过 DSH 统一能力协议读取持久化 Agent 执行事实并生成确定性评估；不修改任务事实。',
    60, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), description = VALUES(description), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
