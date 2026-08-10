-- Phase 3: 会话与 ST 绑定表（仅映射记录，不调用 ST）

CREATE TABLE app_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    -- 展示/搜索用（后续可由消息落库更新）
    title VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_app_conversation_user_id ON app_conversation(user_id);
CREATE INDEX idx_app_conversation_character_id ON app_conversation(character_id);

-- 每个业务会话显式绑定其对应 ST chat/runtime 资源（幂等创建）
CREATE TABLE app_conversation_st_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,

    st_runtime_profile VARCHAR(128) NOT NULL,
    st_character_ref VARCHAR(255) NOT NULL,
    st_chat_ref VARCHAR(255) NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    last_synced_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_binding_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_binding_conversation FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),
    CONSTRAINT uk_binding_conversation_id UNIQUE (conversation_id)
);

CREATE INDEX idx_binding_user_id ON app_conversation_st_binding(user_id);
CREATE INDEX idx_binding_character_id ON app_conversation_st_binding(character_id);

