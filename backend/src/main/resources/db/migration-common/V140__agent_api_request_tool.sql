INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'api.request', '1.0', '白名单 API 只读请求', 'RESEARCH', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["url"],"properties":{"url":{"type":"string","maxLength":2048},"method":{"type":"string","enum":["GET"]}},"additionalProperties":false}',
    '{"type":"object","required":["statusCode","body","status"]}',
    '["Researcher","Data Analyst","Planner"]',
    '仅允许配置白名单公网域名的 GET 请求，禁止重定向、写操作、内网地址和超大响应。',
    15, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
