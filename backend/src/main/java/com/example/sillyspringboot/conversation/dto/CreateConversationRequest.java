package com.example.sillyspringboot.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateConversationRequest {

    /**
     * 客户端幂等键：用于“同一次创建请求”重复提交时返回同一个会话。
     * <p>
     * 强制禁止客户端传入数据库主键（conversationId）。
     */
    @NotBlank
    private String idempotencyKey;

    @NotNull
    private Long characterId;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }
}

