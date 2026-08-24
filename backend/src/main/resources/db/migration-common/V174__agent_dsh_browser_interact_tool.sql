INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'dsh.browser.interact', '1.0', 'DSH Browser 交互', 'AGENT_RUNTIME', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["action"],"properties":{"action":{"type":"string","enum":["open","click","input","extract","close"]},"sessionId":{"type":"string","maxLength":160},"url":{"type":"string","maxLength":2048},"selector":{"type":"string","maxLength":500},"value":{"type":"string","maxLength":4000}},"additionalProperties":false}',
    '{"type":"object"}',
    '["Planner","Developer","Researcher"]',
    '通过本机隔离 Playwright Worker 执行打开、点击、非密码输入、提取和关闭；所有调用需要 Agent 工具审批。',
    45, 0, 0, 1, 1
)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), allowed_roles_json = VALUES(allowed_roles_json), description = VALUES(description), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
