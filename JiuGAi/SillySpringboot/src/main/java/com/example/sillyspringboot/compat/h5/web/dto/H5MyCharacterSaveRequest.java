package com.example.sillyspringboot.compat.h5.web.dto;

import java.util.List;

public class H5MyCharacterSaveRequest {
    private Long id;
    private String clientUid;
    private String name;
    private String tagline;
    private String bio;
    private String persona;
    private String scenario;
    private String firstMessage;
    private List<String> alternateGreetings;
    private String mesExample;
    private String systemPrompt;
    private String postHistoryInstructions;
    private String avatarUrl;
    private String coverUrl;
    private String tagsJson;
    private String occupationLabel;
    private String gameplayType;
    private Boolean vipOnly;
    private Boolean unlockedDefault;
    private String creatorName;
    private String creatorHandle;
    private String tokenDisplay;
    private String chatModesJson;
    private Integer sortOrder;
    private Integer likeCount;
    private Integer dislikeCount;
    private String cardType;
    private String ensembleChatMode;
    private List<MemberInput> members;
    private List<OpeningInput> openings;
    private List<LorebookInput> lorebookEntries;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientUid() {
        return clientUid;
    }

    public void setClientUid(String clientUid) {
        this.clientUid = clientUid;
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

    public List<String> getAlternateGreetings() {
        return alternateGreetings;
    }

    public void setAlternateGreetings(List<String> alternateGreetings) {
        this.alternateGreetings = alternateGreetings;
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

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public String getOccupationLabel() {
        return occupationLabel;
    }

    public void setOccupationLabel(String occupationLabel) {
        this.occupationLabel = occupationLabel;
    }

    public String getGameplayType() {
        return gameplayType;
    }

    public void setGameplayType(String gameplayType) {
        this.gameplayType = gameplayType;
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

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getEnsembleChatMode() { return ensembleChatMode; }
    public void setEnsembleChatMode(String ensembleChatMode) { this.ensembleChatMode = ensembleChatMode; }
    public List<MemberInput> getMembers() { return members; }
    public void setMembers(List<MemberInput> members) { this.members = members; }
    public List<OpeningInput> getOpenings() { return openings; }
    public void setOpenings(List<OpeningInput> openings) { this.openings = openings; }
    public List<LorebookInput> getLorebookEntries() { return lorebookEntries; }
    public void setLorebookEntries(List<LorebookInput> lorebookEntries) { this.lorebookEntries = lorebookEntries; }

    public static class MemberInput {
        private Long id;
        private String clientKey;
        private String name;
        private String tagline;
        private String persona;
        private String avatarUrl;
        private String voiceConfigJson;
        private String imageReferenceUrl;
        private Boolean primaryMember;
        private Boolean voiceBindingChanged;
        private Long userTtsVoiceId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getClientKey() { return clientKey; }
        public void setClientKey(String clientKey) { this.clientKey = clientKey; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTagline() { return tagline; }
        public void setTagline(String tagline) { this.tagline = tagline; }
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getVoiceConfigJson() { return voiceConfigJson; }
        public void setVoiceConfigJson(String voiceConfigJson) { this.voiceConfigJson = voiceConfigJson; }
        public String getImageReferenceUrl() { return imageReferenceUrl; }
        public void setImageReferenceUrl(String imageReferenceUrl) { this.imageReferenceUrl = imageReferenceUrl; }
        public Boolean getPrimaryMember() { return primaryMember; }
        public void setPrimaryMember(Boolean primaryMember) { this.primaryMember = primaryMember; }
        public Boolean getVoiceBindingChanged() { return voiceBindingChanged; }
        public void setVoiceBindingChanged(Boolean voiceBindingChanged) { this.voiceBindingChanged = voiceBindingChanged; }
        public Long getUserTtsVoiceId() { return userTtsVoiceId; }
        public void setUserTtsVoiceId(Long userTtsVoiceId) { this.userTtsVoiceId = userTtsVoiceId; }
    }

    public static class OpeningInput {
        private String title;
        private String summary;
        private String scenarioOverride;
        private Boolean defaultOpening;
        private List<OpeningSegmentInput> segments;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getScenarioOverride() { return scenarioOverride; }
        public void setScenarioOverride(String scenarioOverride) { this.scenarioOverride = scenarioOverride; }
        public Boolean getDefaultOpening() { return defaultOpening; }
        public void setDefaultOpening(Boolean defaultOpening) { this.defaultOpening = defaultOpening; }
        public List<OpeningSegmentInput> getSegments() { return segments; }
        public void setSegments(List<OpeningSegmentInput> segments) { this.segments = segments; }
    }

    public static class OpeningSegmentInput {
        private String speakerClientKey;
        private String speakerType;
        private String content;

        public String getSpeakerClientKey() { return speakerClientKey; }
        public void setSpeakerClientKey(String speakerClientKey) { this.speakerClientKey = speakerClientKey; }
        public String getSpeakerType() { return speakerType; }
        public void setSpeakerType(String speakerType) { this.speakerType = speakerType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class LorebookInput {
        private String title;
        private String memberClientKey;
        private List<String> keywords;
        private List<String> secondaryKeywords;
        private String matchMode;
        private String content;
        private Integer priority;
        private Boolean constantInjection;
        private Integer scanDepth;
        private String injectionPosition;
        private Boolean enabled;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getMemberClientKey() { return memberClientKey; }
        public void setMemberClientKey(String memberClientKey) { this.memberClientKey = memberClientKey; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public List<String> getSecondaryKeywords() { return secondaryKeywords; }
        public void setSecondaryKeywords(List<String> secondaryKeywords) { this.secondaryKeywords = secondaryKeywords; }
        public String getMatchMode() { return matchMode; }
        public void setMatchMode(String matchMode) { this.matchMode = matchMode; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Boolean getConstantInjection() { return constantInjection; }
        public void setConstantInjection(Boolean constantInjection) { this.constantInjection = constantInjection; }
        public Integer getScanDepth() { return scanDepth; }
        public void setScanDepth(Integer scanDepth) { this.scanDepth = scanDepth; }
        public String getInjectionPosition() { return injectionPosition; }
        public void setInjectionPosition(String injectionPosition) { this.injectionPosition = injectionPosition; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}

