CREATE TABLE app_agent_role_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_key VARCHAR(80) NOT NULL,
    version INT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    system_prompt TEXT NULL,
    input_schema TEXT NOT NULL,
    output_schema TEXT NOT NULL,
    allowed_tools_json TEXT NULL,
    route_key VARCHAR(120) NULL,
    max_tokens BIGINT NOT NULL DEFAULT 0,
    max_loops INT NOT NULL DEFAULT 1,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
    enabled TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (role_key, version)
);

CREATE INDEX idx_agent_role_enabled ON app_agent_role_definition(enabled, role_key, version);

CREATE TABLE app_agent_workflow_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workflow_key VARCHAR(80) NOT NULL,
    version INT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    task_type VARCHAR(64) NOT NULL,
    steps_json TEXT NOT NULL,
    allowed_roles_json TEXT NULL,
    allowed_tools_json TEXT NULL,
    max_steps INT NOT NULL DEFAULT 50,
    max_runtime_seconds INT NOT NULL DEFAULT 3600,
    max_tokens BIGINT NOT NULL DEFAULT 0,
    max_cost DECIMAL(20, 6) NOT NULL DEFAULT 0,
    failure_policy VARCHAR(32) NOT NULL DEFAULT 'STOP',
    approval_required TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (workflow_key, version)
);

CREATE INDEX idx_agent_workflow_enabled ON app_agent_workflow_template(enabled, task_type, version);

INSERT INTO app_agent_role_definition (
    role_key, version, display_name, description, system_prompt, input_schema, output_schema,
    allowed_tools_json, route_key, max_tokens, max_loops, risk_level, enabled
) VALUES
('content_creator', 1, 'Content Creator', 'Creates characters, worldbooks, stories and delivery artifacts.', 'Produce structured creative assets from the task objective. Preserve consistency and return verifiable artifact references.', '{"type":"object","additionalProperties":true}', '{"type":"object","additionalProperties":true}', '["planner.analyze","worldbook.create","character.create","character.validate","story.create_outline","story.write_chapter","story.check_continuity","story.revise","artifact.render_html","artifact.package_zip","image.generate","tts.generate"]', 'CHAT', 12000, 3, 'WRITE_REVERSIBLE', 1),
('data_analyst', 1, 'Data Analyst', 'Queries platform facts and produces evidence-backed operational analysis.', 'Use read-only data tools, distinguish facts from inference, and include time windows and signals in the result.', '{"type":"object","additionalProperties":true}', '{"type":"object","additionalProperties":true}', '["analytics.query","analytics.review","artifact.render_report"]', 'CHAT', 10000, 3, 'READ_ONLY', 1),
('system_inspector', 1, 'System Inspector', 'Performs read-only service, build and runtime health inspection.', 'Inspect only allow-listed runtime data. Never execute arbitrary shell, mutate files or change production state.', '{"type":"object","additionalProperties":true}', '{"type":"object","additionalProperties":true}', '["system.health_check"]', 'CHAT', 8000, 2, 'READ_ONLY', 1),
('researcher', 1, 'Web Researcher', 'Collects public web sources and creates cited research summaries.', 'Search public sources, deduplicate evidence, and preserve source URLs with uncertainty clearly stated.', '{"type":"object","additionalProperties":true}', '{"type":"object","additionalProperties":true}', '["web.search","browser.open","artifact.render_report"]', 'CHAT', 10000, 3, 'READ_ONLY', 1),
('devops_operator', 1, 'DevOps Operator', 'Prepares controlled build and release proposals with approval gates.', 'Generate a reversible, auditable release proposal. Any test, build, upload or deployment action requires explicit approval and sandbox policy.', '{"type":"object","additionalProperties":true}', '{"type":"object","additionalProperties":true}', '["system.health_check","code.run","artifact.render_report"]', 'CHAT', 10000, 2, 'PRODUCTION_OPERATION', 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), description = VALUES(description), system_prompt = VALUES(system_prompt),
    input_schema = VALUES(input_schema), output_schema = VALUES(output_schema), allowed_tools_json = VALUES(allowed_tools_json),
    route_key = VALUES(route_key), max_tokens = VALUES(max_tokens), max_loops = VALUES(max_loops),
    risk_level = VALUES(risk_level), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;

INSERT INTO app_agent_workflow_template (
    workflow_key, version, display_name, description, task_type, steps_json, allowed_roles_json,
    allowed_tools_json, max_steps, max_runtime_seconds, max_tokens, max_cost, failure_policy, approval_required, enabled
) VALUES
('content_creation', 1, 'Content Creation', 'Create a complete interactive content package.', 'CONTENT_CREATION', '[{"key":"requirements","name":"Analyze requirements","agentRole":"Planner","stepType":"PLANNING","toolName":"planner.analyze","dependsOn":[]},{"key":"world","name":"Create worldbook","agentRole":"World Builder","stepType":"CONTENT","toolName":"worldbook.create","dependsOn":["requirements"]},{"key":"characters","name":"Create character","agentRole":"Character Designer","stepType":"CONTENT","toolName":"character.create","dependsOn":["world"]},{"key":"character_review","name":"Validate character","agentRole":"Reviewer","stepType":"REVIEW","toolName":"character.validate","dependsOn":["characters"]},{"key":"outline","name":"Create story outline","agentRole":"Plotter","stepType":"CONTENT","toolName":"story.create_outline","dependsOn":["character_review"]},{"key":"chapters","name":"Write chapters","agentRole":"Writer","stepType":"CONTENT","toolName":"story.write_chapter","dependsOn":["outline"]},{"key":"review","name":"Check continuity","agentRole":"Reviewer","stepType":"REVIEW","toolName":"story.check_continuity","dependsOn":["chapters"]},{"key":"revise","name":"Revise when needed","agentRole":"Writer","stepType":"CONTENT","toolName":"story.revise","dependsOn":["review"]},{"key":"review_final","name":"Final continuity check","agentRole":"Reviewer","stepType":"REVIEW","toolName":"story.check_continuity","dependsOn":["revise"]},{"key":"media","name":"Generate images","agentRole":"Media Producer","stepType":"MEDIA","toolName":"image.generate","dependsOn":["review_final"]},{"key":"voiceover","name":"Generate voiceover","agentRole":"Media Producer","stepType":"MEDIA","toolName":"tts.generate","dependsOn":["media"]},{"key":"web","name":"Render interactive HTML","agentRole":"Delivery Agent","stepType":"DELIVERY","toolName":"artifact.render_html","dependsOn":["voiceover"]},{"key":"delivery","name":"Package artifacts","agentRole":"Delivery Agent","stepType":"DELIVERY","toolName":"artifact.package_zip","dependsOn":["web"]}]', '["Planner","World Builder","Character Designer","Reviewer","Plotter","Writer","Media Producer","Delivery Agent"]', '["planner.analyze","worldbook.create","character.create","character.validate","story.create_outline","story.write_chapter","story.check_continuity","story.revise","image.generate","tts.generate","artifact.render_html","artifact.package_zip"]', 20, 3600, 30000, 0, 'CONTINUE_DEGRADED', 0, 1),
('operations_review', 1, 'Operations Review', 'Analyze platform operations and inspect runtime health.', 'OPERATIONS_REVIEW', '[{"key":"analytics","name":"Query operational metrics","agentRole":"Data Analyst","stepType":"ANALYSIS","toolName":"analytics.query","dependsOn":[]},{"key":"inspection","name":"Inspect system health","agentRole":"DevOps Inspector","stepType":"INSPECTION","toolName":"system.health_check","dependsOn":[]},{"key":"report","name":"Render evidence report","agentRole":"Delivery Agent","stepType":"REPORT","toolName":"artifact.render_report","dependsOn":["analytics","inspection"]}]', '["Data Analyst","DevOps Inspector","Delivery Agent"]', '["analytics.query","system.health_check","artifact.render_report"]', 12, 1800, 20000, 0, 'STOP', 0, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), description = VALUES(description), task_type = VALUES(task_type),
    steps_json = VALUES(steps_json), allowed_roles_json = VALUES(allowed_roles_json), allowed_tools_json = VALUES(allowed_tools_json),
    max_steps = VALUES(max_steps), max_runtime_seconds = VALUES(max_runtime_seconds), max_tokens = VALUES(max_tokens),
    max_cost = VALUES(max_cost), failure_policy = VALUES(failure_policy), approval_required = VALUES(approval_required),
    enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
