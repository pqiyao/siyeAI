-- Phase 5 (运营级): ST chat 快照定位字段（avatar_url + file_name）

ALTER TABLE app_conversation_st_binding
    ADD COLUMN st_avatar_url VARCHAR(255) NULL;

ALTER TABLE app_conversation_st_binding
    ADD COLUMN st_chat_file_name VARCHAR(255) NULL;

CREATE INDEX idx_binding_st_avatar_url ON app_conversation_st_binding(st_avatar_url);
CREATE INDEX idx_binding_st_chat_file_name ON app_conversation_st_binding(st_chat_file_name);

