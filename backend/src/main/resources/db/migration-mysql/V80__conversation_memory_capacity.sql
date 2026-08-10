-- Capacity governance and user-pinned protection for branch-scoped memories.

ALTER TABLE app_conversation_memory_entry
    ADD COLUMN manual_pinned BOOLEAN NOT NULL DEFAULT FALSE AFTER manual_deleted,
    ADD COLUMN retired_reason VARCHAR(32) NULL AFTER manual_pinned,
    ADD COLUMN retired_at TIMESTAMP NULL AFTER retired_reason;

CREATE INDEX idx_memory_entry_branch_capacity
    ON app_conversation_memory_entry(
        conversation_id,
        branch_id,
        enabled,
        manual_pinned,
        retired_at,
        deleted_at
    );
