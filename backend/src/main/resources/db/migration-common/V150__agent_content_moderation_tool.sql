INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'content.moderation.review', '1.0', '角色内容只读复核', 'MODERATION', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","required":["characterId"],"properties":{"characterId":{"type":"integer","minimum":1}},"additionalProperties":false}',
    '{"type":"object","required":["characterId","exists","recommendation"]}',
    '["Reviewer","Data Analyst","Support Agent"]',
    '读取角色健康分数、审核状态、审核原因和可见性，生成确定性复核建议，不自动下架或修改审核状态。',
    30, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
