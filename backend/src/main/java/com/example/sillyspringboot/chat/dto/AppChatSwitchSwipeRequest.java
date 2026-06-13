package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 阶段 5：切换某条消息的 swipe variant。
 */
public class AppChatSwitchSwipeRequest {

    @NotNull
    private Long conversationId;

    @NotBlank
    private String messageId;

    @Min(0)
    private int variantIndex;

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

    public int getVariantIndex() {
        return variantIndex;
    }

    public void setVariantIndex(int variantIndex) {
        this.variantIndex = variantIndex;
    }
}

