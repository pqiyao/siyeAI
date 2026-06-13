package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 阶段 5：列出某条消息的 swipe variants。
 */
public class AppChatListSwipesRequest {

    @NotNull
    private Long conversationId;

    @NotBlank
    private String messageId;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}

