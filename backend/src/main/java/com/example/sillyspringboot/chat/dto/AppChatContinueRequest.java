package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 阶段 5：continue（续写）请求。
 * <p>
 * 注意：真正的 continue 语义依赖 ST chat 快照/最后一条 assistant 消息定位，需后续接通。
 */
public class AppChatContinueRequest {

    @NotNull
    private Long conversationId;

    @NotBlank
    private String clientMessageId;

    @NotBlank
    private String targetMessageId;

    private List<String> expressionHints;

    private List<String> avoidExpressionHints;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getTargetMessageId() {
        return targetMessageId;
    }

    public void setTargetMessageId(String targetMessageId) {
        this.targetMessageId = targetMessageId;
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
