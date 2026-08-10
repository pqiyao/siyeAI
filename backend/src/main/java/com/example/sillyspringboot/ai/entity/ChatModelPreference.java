package com.example.sillyspringboot.ai.entity;

public class ChatModelPreference {
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long branchId;
    private String sourceType;
    private Long offeringId;
    private Long userModelId;
    private Long versionNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Long getUserModelId() { return userModelId; }
    public void setUserModelId(Long userModelId) { this.userModelId = userModelId; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
}

