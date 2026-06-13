package com.example.sillyspringboot.compat.h5.web.dto;

import java.util.List;

public class H5ChatPayload {
    private Long characterId;
    private String clientUid;
    private String content;
    private List<String> imageUrls;
    private List<String> expressionHints;
    private List<String> avoidExpressionHints;
    private Double temperature;
    private String model;
    private String ttsModelName;
    private String ttsVoiceName;
    private String ttsVoiceTemplateCode;
    private String voiceUrl;
    private Integer voiceDurationMs;
    private String attachmentMode;
    private String attachmentHint;

    /**
     * 可选：续写/重生时锚定的 AI 消息（H5 传 {@code db_123} 或 {@code 123}）。
     * 不传则后端按会话内最后一条成功/已停止的 assistant 推断。
     */
    private String targetAssistantMessageId;

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public String getClientUid() {
        return clientUid;
    }

    public void setClientUid(String clientUid) {
        this.clientUid = clientUid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTargetAssistantMessageId() {
        return targetAssistantMessageId;
    }

    public void setTargetAssistantMessageId(String targetAssistantMessageId) {
        this.targetAssistantMessageId = targetAssistantMessageId;
    }

    public String getTtsModelName() {
        return ttsModelName;
    }

    public void setTtsModelName(String ttsModelName) {
        this.ttsModelName = ttsModelName;
    }

    public String getTtsVoiceName() {
        return ttsVoiceName;
    }

    public void setTtsVoiceName(String ttsVoiceName) {
        this.ttsVoiceName = ttsVoiceName;
    }

    public String getTtsVoiceTemplateCode() {
        return ttsVoiceTemplateCode;
    }

    public void setTtsVoiceTemplateCode(String ttsVoiceTemplateCode) {
        this.ttsVoiceTemplateCode = ttsVoiceTemplateCode;
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
}

