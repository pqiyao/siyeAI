package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.NotNull;

public class AppChatStopRequest {

    @NotNull
    private Long conversationId;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }
}

