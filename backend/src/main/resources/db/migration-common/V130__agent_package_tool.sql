INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'artifact.package_zip', '1.0', '打包作品 ZIP', 'DELIVERY', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{},"additionalProperties":true}',
    '{"type":"object","required":["operation","artifactKey","uri","contentHash","sizeBytes"]}',
    '["Delivery Agent"]',
    '将角色、世界书、剧情大纲和一致性报告整理为带 manifest 的可下载 ZIP 作品包。',
    120, 2, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
