-- H2 test schema for the P0 long-term-memory revision fences.

ALTER TABLE app_conversation_branch
    ADD COLUMN memory_source_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_conversation_memory
    ADD COLUMN manual_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_conversation_memory
    ADD COLUMN memory_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_conversation_memory
    ADD COLUMN applied_source_revision BIGINT NOT NULL DEFAULT 0;
