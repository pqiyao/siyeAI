-- Align the persisted DevOps tool role allowlists with the fixed DSH release workflow.
UPDATE app_agent_tool_definition
SET allowed_roles_json = '["Developer","DevOps Inspector"]',
    updated_at = CURRENT_TIMESTAMP
WHERE tool_key IN (
    'dsh.devops.inspect',
    'dsh.devops.build',
    'dsh.devops.publish',
    'dsh.devops.deploy',
    'dsh.devops.health',
    'dsh.devops.rollback'
);
