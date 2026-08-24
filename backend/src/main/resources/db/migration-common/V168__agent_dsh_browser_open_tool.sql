INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.browser.open', '1.0', 'DSH Browser 读取', 'AGENT_RUNTIME', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["url"],"properties":{"url":{"type":"string","minLength":1,"maxLength":2048},"urls":{"type":"array","maxItems":3},"followLinkIndexes":{"type":"array","maxItems":2}},"additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher","Writer"]',
    '通过 DSH 统一能力协议调用现有白名单 Browser 读取；不支持点击、输入或登录态。',
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
