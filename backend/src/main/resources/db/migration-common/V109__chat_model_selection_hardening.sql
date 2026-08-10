CREATE INDEX idx_chat_generation_context_status_updated
    ON app_chat_generation_context(charge_status, updated_at, id);
