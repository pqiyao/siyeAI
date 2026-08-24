INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'content.moderation.action', '1.0', '审批后更新角色审核状态', 'MODERATION', 'BUILTIN', 'EXTERNAL_SIDE_EFFECT',
    '{"type":"object","required":["characterId","reviewStatus"],"properties":{"characterId":{"type":"integer","minimum":1},"reviewStatus":{"type":"string","enum":["APPROVED","REJECTED","PENDING"]},"reviewReason":{"type":"string","maxLength":1000}},"additionalProperties":false}',
    '{"type":"object","required":["operation","characterId","reviewStatus","reviewedCount"]}',
    '["Moderator","Reviewer"]',
    '在人工审批通过后调用既有角色审核服务更新审核状态并写审核日志；不直接下架、不删除角色。',
    30, 1, 0, 1, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    risk_level = VALUES(risk_level),
    approval_required = VALUES(approval_required),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
