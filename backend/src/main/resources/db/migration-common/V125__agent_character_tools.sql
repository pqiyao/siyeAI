INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'character.create', '1.0', '创建角色草稿', 'CONTENT', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"character":{"type":"object"}},"additionalProperties":true}',
    '{"type":"object","required":["characterId","name","healthScore","reviewStatus"]}',
    '["Character Designer"]',
    '创建系统侧角色草稿，计算公开资料健康度，不发布、不同步 SillyTavern。',
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

INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'character.validate', '1.0', '校验角色质量', 'CONTENT', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","properties":{"characterId":{"type":"integer"}},"additionalProperties":true}',
    '{"type":"object","required":["characterId","healthScore","healthIssues","passed"]}',
    '["Reviewer","Character Designer"]',
    '读取角色公开资料健康度和质量问题，不修改角色、不发布。',
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
