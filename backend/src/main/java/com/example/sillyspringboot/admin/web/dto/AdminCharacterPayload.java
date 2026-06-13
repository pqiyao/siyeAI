package com.example.sillyspringboot.admin.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 管理端角色表单（与 RuoYi jiugai/character 页字段对齐）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminCharacterPayload {
    private Long id;
    private String name;
    private String tagline;
    private String bio;
    private String persona;
    private String scenario;
    private String firstMessage;
    private String alternateGreetingsJson;
    private String mesExample;
    private String systemPrompt;
    private String postHistoryInstructions;
    private String creatorNotes;
    private String stExtraJson;
    private String avatarUrl;
    private String coverUrl;
    private String chatBackgroundUrl;
    private List<String> stWorldNames;
    private String stAvatarUrl;
    private String occupationLabel;
    private String tagsJson;
    private Boolean vipOnly;
    private Boolean unlockedDefault;
    private Boolean clientVisible;
    private Integer previewBlurVipLevel;
    private Integer likeCount;
    private Integer dislikeCount;
    private String creatorName;
    private String creatorHandle;
    private String tokenDisplay;
    private String gameplayType;
    private String chatModesJson;
    private Integer sortOrder;
    private Boolean privateCard;
    private Long ownerUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(String firstMessage) {
        this.firstMessage = firstMessage;
    }

    public String getAlternateGreetingsJson() {
        return alternateGreetingsJson;
    }

    public void setAlternateGreetingsJson(String alternateGreetingsJson) {
        this.alternateGreetingsJson = alternateGreetingsJson;
    }

    public String getMesExample() {
        return mesExample;
    }

    public void setMesExample(String mesExample) {
        this.mesExample = mesExample;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getPostHistoryInstructions() {
        return postHistoryInstructions;
    }

    public void setPostHistoryInstructions(String postHistoryInstructions) {
        this.postHistoryInstructions = postHistoryInstructions;
    }

    public String getCreatorNotes() {
        return creatorNotes;
    }

    public void setCreatorNotes(String creatorNotes) {
        this.creatorNotes = creatorNotes;
    }

    public String getStExtraJson() {
        return stExtraJson;
    }

    public void setStExtraJson(String stExtraJson) {
        this.stExtraJson = stExtraJson;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getChatBackgroundUrl() {
        return chatBackgroundUrl;
    }

    public void setChatBackgroundUrl(String chatBackgroundUrl) {
        this.chatBackgroundUrl = chatBackgroundUrl;
    }

    public List<String> getStWorldNames() {
        return stWorldNames;
    }

    public void setStWorldNames(List<String> stWorldNames) {
        this.stWorldNames = stWorldNames;
    }

    public String getStAvatarUrl() {
        return stAvatarUrl;
    }

    public void setStAvatarUrl(String stAvatarUrl) {
        this.stAvatarUrl = stAvatarUrl;
    }

    public String getOccupationLabel() {
        return occupationLabel;
    }

    public void setOccupationLabel(String occupationLabel) {
        this.occupationLabel = occupationLabel;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public Boolean getVipOnly() {
        return vipOnly;
    }

    public void setVipOnly(Boolean vipOnly) {
        this.vipOnly = vipOnly;
    }

    public Boolean getUnlockedDefault() {
        return unlockedDefault;
    }

    public void setUnlockedDefault(Boolean unlockedDefault) {
        this.unlockedDefault = unlockedDefault;
    }

    public Boolean getClientVisible() {
        return clientVisible;
    }

    public void setClientVisible(Boolean clientVisible) {
        this.clientVisible = clientVisible;
    }

    public Integer getPreviewBlurVipLevel() {
        return previewBlurVipLevel;
    }

    public void setPreviewBlurVipLevel(Integer previewBlurVipLevel) {
        this.previewBlurVipLevel = previewBlurVipLevel;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(Integer dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getCreatorHandle() {
        return creatorHandle;
    }

    public void setCreatorHandle(String creatorHandle) {
        this.creatorHandle = creatorHandle;
    }

    public String getTokenDisplay() {
        return tokenDisplay;
    }

    public void setTokenDisplay(String tokenDisplay) {
        this.tokenDisplay = tokenDisplay;
    }

    public String getGameplayType() {
        return gameplayType;
    }

    public void setGameplayType(String gameplayType) {
        this.gameplayType = gameplayType;
    }

    public String getChatModesJson() {
        return chatModesJson;
    }

    public void setChatModesJson(String chatModesJson) {
        this.chatModesJson = chatModesJson;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getPrivateCard() {
        return privateCard;
    }

    public void setPrivateCard(Boolean privateCard) {
        this.privateCard = privateCard;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }
}
