INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.monitor.snapshot', '1.0', 'DSH 监控快照', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher"]',
    '通过 DSH 统一能力协议读取系统健康、构建、Git 和备份快照；不发送外部告警。',
    60, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), description = VALUES(description), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
