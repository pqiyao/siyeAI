-- Phase 5 (运营级): 消息事实源 + 生成任务（排队/可恢复/可审计）

CREATE TABLE app_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL, -- user/assistant/system
    client_message_id VARCHAR(64) NULL, -- 前端侧消息幂等/归属

    -- 业务侧内容（对用户可见的最终文本）
    content MEDIUMTEXT NULL,

    -- 与 ST/运行时的映射（后续接通快照后补齐）
    st_message_ref VARCHAR(255) NULL,
    swipe_index INT NULL,

    -- 状态机：PENDING/QUEUED/GENERATING/SUCCESS/FAILED/STOPPED
    status VARCHAR(32) NOT NULL,

    error_code VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id)
);

CREATE INDEX idx_msg_conv_id ON app_message(conversation_id);
CREATE INDEX idx_msg_user_id ON app_message(user_id);
CREATE INDEX idx_msg_client_message_id ON app_message(client_message_id);

CREATE TABLE app_generation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    request_type VARCHAR(32) NOT NULL, -- generate/continue/regenerate/swipe_switch
    client_message_id VARCHAR(64) NOT NULL,

    status VARCHAR(32) NOT NULL, -- QUEUED/GENERATING/SUCCESS/FAILED/STOPPED
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,

    error_code VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,

    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),
    CONSTRAINT uk_task_conv_client UNIQUE (conversation_id, client_message_id)
);

CREATE INDEX idx_task_status ON app_generation_task(status);
CREATE INDEX idx_task_conv_id ON app_generation_task(conversation_id);

