CREATE TABLE IF NOT EXISTS app_agent_collaboration_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    from_role VARCHAR(80) NOT NULL,
    to_role VARCHAR(80) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    payload_json TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_collaboration_task (task_id, created_at)
);

INSERT INTO app_agent_tool_definition (
    tool_key, version, display_name, category, handler_type, risk_level,
    input_schema, output_schema, allowed_roles_json, description,
    timeout_seconds, max_retries, idempotent, approval_required, enabled
) VALUES (
    'agent.handoff', '1.0', 'Agent 角色协作交接', 'COLLABORATION', 'BUILTIN', 'WRITE_REVERSIBLE',
    '{"type":"object","required":["fromRole","toRole","summary"],"properties":{"fromRole":{"type":"string","maxLength":80},"toRole":{"type":"string","maxLength":80},"messageType":{"type":"string","enum":["HANDOFF","REQUEST","RESULT"]},"summary":{"type":"string","minLength":1,"maxLength":4000}},"additionalProperties":false}',
    '{"type":"object","required":["messageId","taskId","fromRole","toRole","status"]}',
    '["Planner","World Builder","Character Designer","Plotter","Writer","Reviewer","Media Producer","Delivery Agent","Data Analyst","DevOps Inspector","Researcher","Developer","Support Agent"]',
    '在固定 Agent 角色之间保存可审计的交接消息，不自动调度新任务，不接受任意角色。',
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
