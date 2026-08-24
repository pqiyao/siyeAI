INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.backup.inspect', '1.0', 'DSH 备份检查', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher"]',
    '通过 DSH 统一能力协议读取备份文件存在性、大小、时间和 SHA-256；不创建、删除或恢复备份。',
    60, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), description = VALUES(description), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
