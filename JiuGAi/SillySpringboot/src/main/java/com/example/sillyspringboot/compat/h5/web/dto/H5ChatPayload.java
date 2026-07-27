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
    /** 角色级 TTS 覆盖保存时的 BYOK 供应商作用域，用于阻止旧设备配置跨供应商误用。 */
    private String ttsProviderSource;
    private String ttsModelName;
    private String ttsVoiceName;
    private String ttsVoiceTemplateCode;
    /** 用户自建音色内部 ID，仅用于 TTS 媒体请求，不进入 ST 文本生成请求。 */
    private Long ttsUserVoiceId;
    private String ttsRequestId;
    private Integer ttsSegmentIndex;
    private Integer ttsSegmentCount;
    private Long speakerMemberId;
    private String voiceUrl;
    private Integer voiceDurationMs;
    private String attachmentMode;
    private String attachmentHint;
    private String replySplitMode;
    private String visionRequestId;
    /** 客户端生成并在同一次网络重试中复用的稳定请求 ID。 */
    private String generationRequestId;
    /** 仅允许 SYSTEM 或 BYOK；后端仍会重新校验归属和可用性。 */
    private String chatModelSource;
    /** SYSTEM 时为公开方案编码，BYOK 时为当前用户模型 ID。 */
    private String chatModelRef;
    /** 平台模型目录中的报价/配置版本；旧客户端可不传。 */
    private Long chatModelSelectionVersion;

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

    public String getTtsProviderSource() {
        return ttsProviderSource;
    }

    public void setTtsProviderSource(String ttsProviderSource) {
        this.ttsProviderSource = ttsProviderSource;
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

    public Long getTtsUserVoiceId() {
        return ttsUserVoiceId;
    }

    public void setTtsUserVoiceId(Long ttsUserVoiceId) {
        this.ttsUserVoiceId = ttsUserVoiceId;
    }

    public String getTtsRequestId() {
        return ttsRequestId;
    }

    public void setTtsRequestId(String ttsRequestId) {
        this.ttsRequestId = ttsRequestId;
    }

    public Integer getTtsSegmentIndex() {
        return ttsSegmentIndex;
    }

    public void setTtsSegmentIndex(Integer ttsSegmentIndex) {
        this.ttsSegmentIndex = ttsSegmentIndex;
    }

    public Integer getTtsSegmentCount() {
        return ttsSegmentCount;
    }

    public void setTtsSegmentCount(Integer ttsSegmentCount) {
        this.ttsSegmentCount = ttsSegmentCount;
    }

    public Long getSpeakerMemberId() { return speakerMemberId; }
    public void setSpeakerMemberId(Long speakerMemberId) { this.speakerMemberId = speakerMemberId; }

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

    public String getReplySplitMode() {
        return replySplitMode;
    }

    public void setReplySplitMode(String replySplitMode) {
        this.replySplitMode = replySplitMode;
    }

    public String getVisionRequestId() {
        return visionRequestId;
    }

    public void setVisionRequestId(String visionRequestId) {
        this.visionRequestId = visionRequestId;
    }

    public String getGenerationRequestId() { return generationRequestId; }
    public void setGenerationRequestId(String generationRequestId) { this.generationRequestId = generationRequestId; }
    public String getChatModelSource() { return chatModelSource; }
    public void setChatModelSource(String chatModelSource) { this.chatModelSource = chatModelSource; }
    public String getChatModelRef() { return chatModelRef; }
    public void setChatModelRef(String chatModelRef) { this.chatModelRef = chatModelRef; }
    public Long getChatModelSelectionVersion() { return chatModelSelectionVersion; }
    public void setChatModelSelectionVersion(Long chatModelSelectionVersion) {
        this.chatModelSelectionVersion = chatModelSelectionVersion;
    }
}

