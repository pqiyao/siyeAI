package com.example.sillyspringboot.chat.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AppChatStreamRequest {

    @NotNull
    private Long conversationId;

    private String userMessage;

    private List<String> imageUrls;

    private String voiceUrl;

    private Integer voiceDurationMs;

    private String attachmentMode;

    private String attachmentHint;

    private List<String> expressionHints;

    private List<String> avoidExpressionHints;

    /** 客户端消息 ID，用于 chunk 对齐和流式归属。 */
    @NotBlank
    private String clientMessageId;

    @AssertTrue(message = "消息内容不能为空")
    public boolean isPayloadPresent() {
        if (userMessage != null && !userMessage.isBlank()) {
            return true;
        }
        return imageUrls != null && imageUrls.stream().anyMatch(url -> url != null && !url.isBlank());
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getVoiceUrl() {
        return voiceUrl;
    }

    public void setVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
    }

    public Integer getVoiceDurationMs() {
        return voiceDurationMs;
    }

    public void setVoiceDurationMs(Integer voiceDurationMs) {
        this.voiceDurationMs = voiceDurationMs;
    }

    public String getAttachmentMode() {
        return attachmentMode;
    }

    public void setAttachmentMode(String attachmentMode) {
        this.attachmentMode = attachmentMode;
    }

    public String getAttachmentHint() {
        return attachmentHint;
    }

    public void setAttachmentHint(String attachmentHint) {
        this.attachmentHint = attachmentHint;
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

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }
}
