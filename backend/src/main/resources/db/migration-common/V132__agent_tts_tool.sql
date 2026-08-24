INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'tts.generate', '1.0', '生成 Agent 配音', 'MEDIA', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","properties":{"tts":{"type":"object"},"audioSegments":{"type":"array","maxItems":4}},"additionalProperties":true}',
    '{"type":"object","required":["operation"]}',
    '["Media Producer"]',
    '通过现有系统 TTS 路由生成最多 4 段角色配音，保存为带文件地址和 SHA-256 的 Agent AUDIO 产物。',
    600, 2, 1, 0, 1
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    input_schema = VALUES(input_schema),
    output_schema = VALUES(output_schema),
    allowed_roles_json = VALUES(allowed_roles_json),
    description = VALUES(description),
    timeout_seconds = VALUES(timeout_seconds),
    max_retries = VALUES(max_retries),
    enabled = VALUES(enabled),
    updated_at = CURRENT_TIMESTAMP;
