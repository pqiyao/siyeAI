INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.devops.inspect', '1.0', 'DSH DevOps 检查', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher"]',
    '通过 DSH 统一能力协议读取系统、构建、Git 和备份就绪状态；不执行构建、部署、SSH 或回滚。',
    60, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
