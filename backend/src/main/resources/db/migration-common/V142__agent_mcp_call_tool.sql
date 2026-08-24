INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'mcp.call', '1.0', '受控 MCP 调用', 'RESEARCH', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["method"],"properties":{"method":{"type":"string","maxLength":200},"id":{"type":"string","maxLength":100},"params":{"type":"object"}},"additionalProperties":false}',
    '{"type":"object","required":["method","status"]}',
    '["Researcher","Planner"]',
    '向一个配置的 MCP JSON-RPC 端点调用已登记方法；端点、域名和方法均需白名单，执行前需要审批。',
    20, 1, 1, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
