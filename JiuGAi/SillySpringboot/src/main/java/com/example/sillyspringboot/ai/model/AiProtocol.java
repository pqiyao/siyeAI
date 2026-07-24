package com.example.sillyspringboot.ai.model;

import java.util.Locale;

public enum AiProtocol {
    OPENAI_CHAT,
    OPENAI_IMAGE,
    OPENAI_TTS,
    OPENAI_STT;

    public static AiProtocol parse(String raw, AiCapability capability) {
        AiProtocol expected = forCapability(capability);
        if (raw == null || raw.isBlank()) {
            return expected;
        }
        AiProtocol parsed = valueOf(raw.trim().toUpperCase(Locale.ROOT));
        if (parsed != expected) {
            throw new IllegalArgumentException("protocol does not match capability");
        }
        return parsed;
    }

    public static AiProtocol forCapability(AiCapability capability) {
        return switch (capability) {
            case CHAT -> OPENAI_CHAT;
            case IMAGE -> OPENAI_IMAGE;
            case TTS -> OPENAI_TTS;
            case STT -> OPENAI_STT;
        };
    }
}
