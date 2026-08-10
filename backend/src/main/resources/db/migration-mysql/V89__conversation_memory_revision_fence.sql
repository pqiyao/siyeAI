-- P0 long-term-memory revision fences. LLM work stays outside transactions;
-- these revisions make the final short transaction reject stale results.

ALTER TABLE app_conversation_branch
    ADD COLUMN memory_source_revision BIGINT NOT NULL DEFAULT 0 AFTER is_default;

ALTER TABLE app_conversation_memory
    ADD COLUMN manual_revision BIGINT NOT NULL DEFAULT 0 AFTER enabled_entry_count,
    ADD COLUMN memory_revision BIGINT NOT NULL DEFAULT 0 AFTER manual_revision,
    ADD COLUMN applied_source_revision BIGINT NOT NULL DEFAULT 0 AFTER memory_revision;
