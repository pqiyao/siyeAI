-- Phase 3: 会话创建幂等（禁止客户端控制数据库主键）

CREATE TABLE app_conversation_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    conversation_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conv_idem_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_conv_idem_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),

    CONSTRAINT uk_conv_idem UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_conv_idem_user_id ON app_conversation_idempotency(user_id);
CREATE INDEX idx_conv_idem_key ON app_conversation_idempotency(idempotency_key);

