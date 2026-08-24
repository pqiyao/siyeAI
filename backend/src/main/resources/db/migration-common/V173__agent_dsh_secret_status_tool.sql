INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.secret.status', '1.0', 'DSH 凭据状态', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher"]',
    '仅向 DSH 返回 API、Web Search、MCP 和 Code Runner 是否配置，不返回任何密钥值。',
    15, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), description = VALUES(description), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
