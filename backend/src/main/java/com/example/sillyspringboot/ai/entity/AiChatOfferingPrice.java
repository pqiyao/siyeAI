package com.example.sillyspringboot.ai.entity;

public class AiChatOfferingPrice {
    private Long id;
    private Long offeringId;
    private Integer vipLevel;
    private String billingMode;
    private Integer quotaUnits;
    private Integer diamondCost;
    private Integer goldCost;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Integer getVipLevel() { return vipLevel; }
    public void setVipLevel(Integer vipLevel) { this.vipLevel = vipLevel; }
    public String getBillingMode() { return billingMode; }
    public void setBillingMode(String billingMode) { this.billingMode = billingMode; }
    public Integer getQuotaUnits() { return quotaUnits; }
    public void setQuotaUnits(Integer quotaUnits) { this.quotaUnits = quotaUnits; }
    public Integer getDiamondCost() { return diamondCost; }
    public void setDiamondCost(Integer diamondCost) { this.diamondCost = diamondCost; }
    public Integer getGoldCost() { return goldCost; }
    public void setGoldCost(Integer goldCost) { this.goldCost = goldCost; }
}

