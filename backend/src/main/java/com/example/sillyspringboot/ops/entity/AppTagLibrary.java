package com.example.sillyspringboot.ops.entity;

import java.time.LocalDateTime;

public class AppTagLibrary {

    private Long id;
    private String code;
    private String name;
    private String category;
    private String color;
    private Boolean vipOnly;
    private Boolean adultOnly;
    private Boolean enabled;
    private Boolean discoverVisible;
    private Boolean discoverRecommended;
    private Boolean detailVisible;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getVipOnly() {
        return vipOnly;
    }

    public void setVipOnly(Boolean vipOnly) {
        this.vipOnly = vipOnly;
    }

    public Boolean getAdultOnly() {
        return adultOnly;
    }

    public void setAdultOnly(Boolean adultOnly) {
        this.adultOnly = adultOnly;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getDiscoverVisible() {
        return discoverVisible;
    }

    public void setDiscoverVisible(Boolean discoverVisible) {
        this.discoverVisible = discoverVisible;
    }

    public Boolean getDiscoverRecommended() {
        return discoverRecommended;
    }

    public void setDiscoverRecommended(Boolean discoverRecommended) {
        this.discoverRecommended = discoverRecommended;
    }

    public Boolean getDetailVisible() {
        return detailVisible;
    }

    public void setDetailVisible(Boolean detailVisible) {
        this.detailVisible = detailVisible;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
