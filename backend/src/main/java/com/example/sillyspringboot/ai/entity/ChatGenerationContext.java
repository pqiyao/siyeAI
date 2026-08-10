package com.example.sillyspringboot.ai.entity;

public class ChatGenerationContext {
    private Long id;
    private Long userId;
    private Long conversationId;
    private String generationRequestId;
    private String actionType;
    private String sourceType;
    private Long offeringId;
    private String offeringCode;
    private String offeringName;
    private Long userModelId;
    private String modelNameSnapshot;
    private String routeKey;
    private String billingMode;
    private Integer quotaUnits;
    private Integer diamondCost;
    private Integer goldCost;
    private String chargeStatus;
    private String consumeBizRef;
    private Boolean firstContentEmitted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getGenerationRequestId() { return generationRequestId; }
    public void setGenerationRequestId(String generationRequestId) { this.generationRequestId = generationRequestId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public String getOfferingCode() { return offeringCode; }
    public void setOfferingCode(String offeringCode) { this.offeringCode = offeringCode; }
    public String getOfferingName() { return offeringName; }
    public void setOfferingName(String offeringName) { this.offeringName = offeringName; }
    public Long getUserModelId() { return userModelId; }
    public void setUserModelId(Long userModelId) { this.userModelId = userModelId; }
    public String getModelNameSnapshot() { return modelNameSnapshot; }
    public void setModelNameSnapshot(String modelNameSnapshot) { this.modelNameSnapshot = modelNameSnapshot; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String routeKey) { this.routeKey = routeKey; }
    public String getBillingMode() { return billingMode; }
    public void setBillingMode(String billingMode) { this.billingMode = billingMode; }
    public Integer getQuotaUnits() { return quotaUnits; }
    public void setQuotaUnits(Integer quotaUnits) { this.quotaUnits = quotaUnits; }
    public Integer getDiamondCost() { return diamondCost; }
    public void setDiamondCost(Integer diamondCost) { this.diamondCost = diamondCost; }
    public Integer getGoldCost() { return goldCost; }
    public void setGoldCost(Integer goldCost) { this.goldCost = goldCost; }
    public String getChargeStatus() { return chargeStatus; }
    public void setChargeStatus(String chargeStatus) { this.chargeStatus = chargeStatus; }
    public String getConsumeBizRef() { return consumeBizRef; }
    public void setConsumeBizRef(String consumeBizRef) { this.consumeBizRef = consumeBizRef; }
    public Boolean getFirstContentEmitted() { return firstContentEmitted; }
    public void setFirstContentEmitted(Boolean firstContentEmitted) { this.firstContentEmitted = firstContentEmitted; }
}
