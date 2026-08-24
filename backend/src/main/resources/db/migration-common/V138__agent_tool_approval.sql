UPDATE app_agent_tool_definition
SET approval_required = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE tool_key IN ('character.publish', 'character.sync_st')
  AND enabled = 1;
