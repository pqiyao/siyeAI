package com.example.sillyspringboot.ai.model;

import java.util.Locale;

public enum AiCapability {
    CHAT,
    IMAGE,
    TTS,
    STT;

    public static AiCapability parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("capability cannot be blank");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public String defaultRouteKey() {
        return name().toLowerCase(Locale.ROOT) + ".default";
    }
}
