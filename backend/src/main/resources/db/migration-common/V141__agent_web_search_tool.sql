INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'web.search', '1.0', '白名单 Web 搜索', 'RESEARCH', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":500}},"additionalProperties":false}',
    '{"type":"object","required":["query","source","results","status"]}',
    '["Researcher","Planner"]',
    '调用一个配置的公网搜索端点，保留查询、来源和抓取时间；禁止任意 URL、登录、浏览器操作和写请求。',
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
