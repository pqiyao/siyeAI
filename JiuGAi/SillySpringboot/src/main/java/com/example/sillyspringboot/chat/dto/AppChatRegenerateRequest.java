package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 阶段 5：regenerate（重生）请求。
 * <p>
 * 注意：真正的 regenerate 语义依赖 ST 对“目标 assistant 消息”的定位与 swipes 生成，需后续接通。
 */
public class AppChatRegenerateRequest {

    @NotNull
    private Long conversationId;

    /** 目标消息 id（由前端/服务端状态机定义，后续与 ST 映射） */
    @NotBlank
    private String targetMessageId;

    @NotBlank
    private String clientMessageId;

    private List<String> expressionHints;

    private List<String> avoidExpressionHints;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getTargetMessageId() {
        return targetMessageId;
    }

    public void setTargetMessageId(String targetMessageId) {
        this.targetMessageId = targetMessageId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public List<String> getExpressionHints() {
        return expressionHints;
    }

    public void setExpressionHints(List<String> expressionHints) {
        this.expressionHints = expressionHints;
    }

    public List<String> getAvoidExpressionHints() {
        return avoidExpressionHints;
    }

    public void setAvoidExpressionHints(List<String> avoidExpressionHints) {
        this.avoidExpressionHints = avoidExpressionHints;
    }
}

