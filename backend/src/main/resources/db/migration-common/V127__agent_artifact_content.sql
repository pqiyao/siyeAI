ALTER TABLE app_agent_artifact
    ADD COLUMN content_json LONGTEXT NULL AFTER metadata_json;
