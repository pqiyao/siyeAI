INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES
(
    'analytics.query', '1.0', '读取运营指标', 'ANALYSIS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":365}},"additionalProperties":false}',
    '{"type":"object","required":["rangeDays","generationSummary","providerStats"]}',
    '["Data Analyst"]', '使用固定参数化指标查询读取用户、生成、成本、供应商和角色数据，不接受任意 SQL。',
    60, 1, 1, 0, 1
),
(
    'analytics.review', '1.0', '复核运营数据', 'ANALYSIS', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object","required":["warnings","recommendations"]}',
    '["Reviewer"]', '对指标结果执行确定性质量检查，标记成功率和未计价数据风险。',
    30, 1, 1, 0, 1
),
(
    'system.health_check', '1.0', '系统只读巡检', 'INSPECTION', 'BUILTIN', 'READ_ONLY',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object","required":["jvm","database","storage"]}',
    '["DevOps Inspector"]', '读取应用 JVM、Agent、Flyway 和上传目录状态，不执行 Shell、SSH 或写操作。',
    30, 1, 1, 0, 1
),
(
    'artifact.render_report', '1.0', '生成执行报告', 'DELIVERY', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","additionalProperties":false}',
    '{"type":"object","required":["artifactKey"]}',
    '["Delivery Agent"]', '汇总任务产物元数据并保存可审计的 JSON 报告。',
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
