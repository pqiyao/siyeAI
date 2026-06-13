package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.Locale;

public record ChatMessageContentPart(
        String type,
        String text,
        String url
) {

    public ChatMessageContentPart {
        type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        text = text == null ? "" : text;
        url = url == null ? "" : url.trim();
    }

    public static ChatMessageContentPart text(String text) {
        return new ChatMessageContentPart("text", text, "");
    }

    public static ChatMessageContentPart imageUrl(String url) {
        return new ChatMessageContentPart("image_url", "", url);
    }

    public boolean isText() {
        return "text".equals(type) && !text.isBlank();
    }

    public boolean isImageUrl() {
        return "image_url".equals(type) && !url.isBlank();
    }
}
