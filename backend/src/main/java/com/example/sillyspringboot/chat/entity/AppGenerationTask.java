package com.example.sillyspringboot.chat.entity;

import java.time.LocalDateTime;

public class AppGenerationTask {

    private Long id;
    private Long userId;
    private Long conversationId;
    private String requestType;
    private String channel;
    private String model;
    private String clientMessageId;
    private String status;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer durationMs;
    private Integer httpStatus;
    private Integer promptTokens;
    private Integer completionTokens;
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private Long effectivePresetId;
    private Integer effectiveMaxContext;
    private Integer effectiveMaxTokens;
    private String effectiveProvider;
    private String effectiveApiSource;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(LocalDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getEffectivePresetId() { return effectivePresetId; }
    public void setEffectivePresetId(Long value) { this.effectivePresetId = value; }
    public Integer getEffectiveMaxContext() { return effectiveMaxContext; }
    public void setEffectiveMaxContext(Integer value) { this.effectiveMaxContext = value; }
    public Integer getEffectiveMaxTokens() { return effectiveMaxTokens; }
    public void setEffectiveMaxTokens(Integer value) { this.effectiveMaxTokens = value; }
    public String getEffectiveProvider() { return effectiveProvider; }
    public void setEffectiveProvider(String value) { this.effectiveProvider = value; }
    public String getEffectiveApiSource() { return effectiveApiSource; }
    public void setEffectiveApiSource(String value) { this.effectiveApiSource = value; }
}
