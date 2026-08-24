ALTER TABLE app_agent_tenant_budget
    ADD COLUMN daily_task_date DATE NULL;

ALTER TABLE app_agent_tenant_budget
    ADD COLUMN daily_task_reserved BIGINT NOT NULL DEFAULT 0;
