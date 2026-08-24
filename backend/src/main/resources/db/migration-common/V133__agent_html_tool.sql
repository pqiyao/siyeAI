INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'artifact.render_html', '1.0', '生成互动网页', 'DELIVERY', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{},"additionalProperties":true}',
    '{"type":"object","required":["operation","artifactKey","uri","contentHash"]}',
    '["Delivery Agent"]',
    '将结构化剧情、图片和音频整理为可直接打开的互动 HTML 网页，包含节点选择和媒体展示。',
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
