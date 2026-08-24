INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES
(
    'character.publish', '1.0', '发布 Agent 角色', 'CONTENT', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"characterId":{"type":"integer"},"characterIds":{"type":"array","maxItems":4}},"additionalProperties":true}',
    '{"type":"object","required":["operation","characterIds","characterCount"]}',
    '["Reviewer","Delivery Agent"]', '将 Agent 生成的待审核系统角色发布为可见角色，不自动写入 SillyTavern。',
    60, 1, 1, 0, 1
),
(
    'character.sync_st', '1.0', '同步角色卡到 SillyTavern', 'CONTENT', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","properties":{"characterId":{"type":"integer"},"characterIds":{"type":"array","maxItems":4}},"additionalProperties":true}',
    '{"type":"object","required":["operation","characterIds","characterCount"]}',
    '["Delivery Agent"]', '将已经审核发布的 Agent 角色卡同步到 SillyTavern；ST 不可用时明确失败。',
    120, 1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    risk_level = VALUES(risk_level),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
