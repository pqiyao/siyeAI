package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ChatMessage(
        String role,
        String content,
        List<ChatMessageContentPart> contentParts
) {

    public ChatMessage {
        role = role == null || role.isBlank() ? "user" : role.trim().toLowerCase(Locale.ROOT);
        content = content == null ? "" : content;
        contentParts = contentParts == null ? List.of() : List.copyOf(contentParts);
    }

    public static ChatMessage text(String role, String content) {
        return new ChatMessage(role, content, List.of());
    }

    public static ChatMessage multimodalUser(String text, List<String> imageUrls) {
        List<ChatMessageContentPart> parts = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            parts.add(ChatMessageContentPart.text(text));
        }
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    parts.add(ChatMessageContentPart.imageUrl(imageUrl));
                }
            }
        }
        return new ChatMessage("user", text, parts);
    }

    public boolean hasStructuredContent() {
        return contentParts != null && !contentParts.isEmpty();
    }

    public boolean hasImageContent() {
        return contentParts != null && contentParts.stream().anyMatch(ChatMessageContentPart::isImageUrl);
    }
}
