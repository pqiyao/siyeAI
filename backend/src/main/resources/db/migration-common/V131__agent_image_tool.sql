INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'image.generate', '1.0', '生成 Agent 图片', 'MEDIA', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"imageCount":{"type":"integer","minimum":1,"maximum":4},"prompt":{"type":"string","maxLength":4000},"images":{"type":"array","maxItems":4}},"additionalProperties":true}',
    '{"type":"object","required":["operation","imageCount","images"]}',
    '["Media Producer"]',
    '通过当前系统生图引擎生成最多 4 张图片，保存为带文件地址和 SHA-256 的 Agent IMAGE 产物。',
    600, 2, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    timeout_seconds = VALUES(timeout_seconds),
    max_retries = VALUES(max_retries),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
