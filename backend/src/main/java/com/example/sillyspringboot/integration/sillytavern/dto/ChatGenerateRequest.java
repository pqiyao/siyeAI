package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.List;
import java.util.Set;

public record ChatGenerateRequest(
        Long conversationId,
        String userMessage,
        List<ChatMessage> messages,
        String clientMessageId,
        boolean stream,
        String mode,
        Set<String> allowedFeatures,
        String userName,
        String charName,
        List<String> groupNames,
        // StepA: ST runtime-chat identity, used to keep runtime chat as the source of truth.
        String stAvatarUrl,
        String stChatFileName,
        // Phase 4: message anchor used by regenerate/swipe/assistant sync flows.
        String stMessageRef,
        // B2: world info bindings that should stay active for this request.
        List<String> stWorldNames,
        // Conversation-scoped memory appended at the tail to preserve prompt cache stability.
        String tailSystemPrompt,
        UserModelOverride userModelOverride
) {

    public ChatGenerateRequest(
            Long conversationId,
            String userMessage,
            List<ChatMessage> messages,
            String clientMessageId,
            boolean stream,
            String mode,
            Set<String> allowedFeatures,
            String userName,
            String charName,
            List<String> groupNames,
            String stAvatarUrl,
            String stChatFileName,
            String stMessageRef,
            List<String> stWorldNames,
            UserModelOverride userModelOverride
    ) {
        this(
                conversationId,
                userMessage,
                messages,
                clientMessageId,
                stream,
                mode,
                allowedFeatures,
                userName,
                charName,
                groupNames,
                stAvatarUrl,
                stChatFileName,
                stMessageRef,
                stWorldNames,
                "",
                userModelOverride
        );
    }

    public boolean hasImageInput() {
        return messages != null && messages.stream().anyMatch(ChatMessage::hasImageContent);
    }
}
