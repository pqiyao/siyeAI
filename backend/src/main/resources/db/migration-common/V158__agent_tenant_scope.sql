ALTER TABLE app_agent_task
    ADD COLUMN tenant_key VARCHAR(120) NOT NULL DEFAULT 'platform' AFTER created_by;

UPDATE app_agent_task
SET tenant_key = CONCAT('user:', created_by)
WHERE created_by IS NOT NULL AND created_by > 0;

CREATE INDEX idx_agent_task_tenant_created ON app_agent_task(tenant_key, created_at);
