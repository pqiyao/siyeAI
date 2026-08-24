INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'browser.open', '1.0', '受控网页读取', 'RESEARCH', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["url"],"properties":{"url":{"type":"string","maxLength":2048}},"additionalProperties":false}',
    '{"type":"object","required":["url","statusCode","title","text","links","status"]}',
    '["Researcher","Planner"]',
    '读取配置白名单公网网页的标题、纯文本摘要和链接；禁止重定向、脚本执行、点击、表单提交和内网地址。',
    20, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
