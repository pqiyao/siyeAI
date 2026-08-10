-- H5：会话级「长期记忆」占位（摘要文案/刷新时间；真摘要可由后续 ST/向量链路填充）

CREATE TABLE app_conversation_memory (
    conversation_id BIGINT NOT NULL PRIMARY KEY,
    summary_preview VARCHAR(512) NULL,
    facts_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
