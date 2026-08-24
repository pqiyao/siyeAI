INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'code.run', '1.0', '隔离 Python 代码执行', 'DEVELOPMENT', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["code"],"properties":{"code":{"type":"string","minLength":1,"maxLength":32000}},"additionalProperties":false}',
    '{"type":"object","required":["status","exitCode","stdout","stderr","sandbox"]}',
    '["Developer","Researcher"]',
    '在配置的 Docker Python 镜像中执行短代码；无网络、只读根文件系统、固定 CPU/内存/进程数和超时，执行前需要审批。',
    40, 0, 0, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
