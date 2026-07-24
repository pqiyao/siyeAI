ALTER TABLE app_conversation_branch
    ADD COLUMN opening_variant_index INT NULL AFTER fork_message_id;

CREATE UNIQUE INDEX uk_conversation_branch_opening_variant
    ON app_conversation_branch (conversation_id, opening_variant_index);
