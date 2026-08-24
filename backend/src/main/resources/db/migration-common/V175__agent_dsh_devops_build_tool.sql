INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.devops.build', '1.0', 'DSH DevOps 构建', 'AGENT_RUNTIME', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object"}',
    '["Developer","DevOps Inspector"]',
    '仅在配置的 Spring Boot 工程目录执行固定 Maven package，返回 JAR 制品大小与 SHA-256；不接受命令、目录或目标注入，调用需要审批。',
    900, 0, 1, 1, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), allowed_roles_json = VALUES(allowed_roles_json), description = VALUES(description), timeout_seconds = VALUES(timeout_seconds), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
