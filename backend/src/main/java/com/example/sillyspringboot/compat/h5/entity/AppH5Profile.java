package com.example.sillyspringboot.compat.h5.entity;

import java.time.LocalDateTime;

public class AppH5Profile {
    private Long id;
    private Long userId;
    private String displayName;
    private String stDisplayName;
    private String persona;
    private LocalDateTime updatedAt;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStDisplayName() {
        return stDisplayName;
    }

    public void setStDisplayName(String stDisplayName) {
        this.stDisplayName = stDisplayName;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

