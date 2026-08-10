package com.example.sillyspringboot.ops.generation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GenerationModelPricingAdminDto {

    private Long id;
    private String providerKey;
    private String modelPattern;
    private String version;
    private String currency;
    private BigDecimal inputUsdPerMillionTokens;
    private BigDecimal outputUsdPerMillionTokens;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Boolean enabled;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public String getModelPattern() { return modelPattern; }
    public void setModelPattern(String modelPattern) { this.modelPattern = modelPattern; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getInputUsdPerMillionTokens() { return inputUsdPerMillionTokens; }
    public void setInputUsdPerMillionTokens(BigDecimal value) { this.inputUsdPerMillionTokens = value; }
    public BigDecimal getOutputUsdPerMillionTokens() { return outputUsdPerMillionTokens; }
    public void setOutputUsdPerMillionTokens(BigDecimal value) { this.outputUsdPerMillionTokens = value; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
