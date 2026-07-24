-- Conversation-internal story branches. Existing conversations are backfilled
-- with one default branch so old chat records stay visible without migration work
-- from the client.

CREATE TABLE app_conversation_branch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_branch_id BIGINT NULL,
    fork_message_id BIGINT NULL,
    title VARCHAR(80) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,

    CONSTRAINT fk_conv_branch_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),
    CONSTRAINT fk_conv_branch_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_conv_branch_conv ON app_conversation_branch(conversation_id, deleted_at, id);
CREATE INDEX idx_conv_branch_user ON app_conversation_branch(user_id, conversation_id);

ALTER TABLE app_conversation
    ADD COLUMN active_branch_id BIGINT NULL;

ALTER TABLE app_message
    ADD COLUMN branch_id BIGINT NULL,
    ADD COLUMN parent_message_id BIGINT NULL;

INSERT INTO app_conversation_branch (conversation_id, user_id, title, is_default)
SELECT c.id, c.user_id, '默认分支', TRUE
FROM app_conversation c
WHERE NOT EXISTS (
    SELECT 1
    FROM app_conversation_branch b
    WHERE b.conversation_id = c.id
      AND b.deleted_at IS NULL
);

UPDATE app_conversation c
JOIN app_conversation_branch b
  ON b.conversation_id = c.id
 AND b.is_default = TRUE
 AND b.deleted_at IS NULL
SET c.active_branch_id = b.id
WHERE c.active_branch_id IS NULL;

UPDATE app_message m
JOIN app_conversation c ON c.id = m.conversation_id
SET m.branch_id = c.active_branch_id
WHERE m.branch_id IS NULL;

ALTER TABLE app_message
    ADD CONSTRAINT fk_msg_branch FOREIGN KEY (branch_id) REFERENCES app_conversation_branch(id);

CREATE INDEX idx_msg_conv_branch_id ON app_message(conversation_id, branch_id, id);
CREATE INDEX idx_msg_parent_message_id ON app_message(parent_message_id);

ALTER TABLE app_conversation_memory
    ADD COLUMN branch_id BIGINT NULL;

UPDATE app_conversation_memory mem
JOIN app_conversation c ON c.id = mem.conversation_id
SET mem.branch_id = c.active_branch_id
WHERE mem.branch_id IS NULL;

ALTER TABLE app_conversation_memory
    DROP PRIMARY KEY,
    MODIFY branch_id BIGINT NOT NULL,
    ADD PRIMARY KEY (conversation_id, branch_id);

ALTER TABLE app_conversation_memory_entry
    ADD COLUMN branch_id BIGINT NULL;

UPDATE app_conversation_memory_entry e
JOIN app_conversation c ON c.id = e.conversation_id
SET e.branch_id = c.active_branch_id
WHERE e.branch_id IS NULL;

ALTER TABLE app_conversation_memory_entry
    MODIFY branch_id BIGINT NOT NULL,
    DROP INDEX uk_memory_entry_key,
    ADD CONSTRAINT uk_memory_entry_key UNIQUE (conversation_id, branch_id, entry_key);

CREATE INDEX idx_memory_entry_conv_branch_enabled
    ON app_conversation_memory_entry(conversation_id, branch_id, enabled, deleted_at);
