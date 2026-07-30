package com.example.sillyspringboot.integration.sillytavern.dto;

import com.example.sillyspringboot.ai.model.AiCapability;

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
        UserModelOverride userModelOverride,
        String tailSystemPrompt,
        String runtimePresetBundle,
        AiCapability routingCapability,
        String routingRouteKey,
        String userPersona,
        String studioLoreBeforeCharacter,
        String studioLoreAfterCharacter,
        String studioLoreBeforeHistory
) {

    public ChatGenerateRequest(
            Long conversationId, String userMessage, List<ChatMessage> messages, String clientMessageId,
            boolean stream, String mode, Set<String> allowedFeatures, String userName, String charName,
            List<String> groupNames, String stAvatarUrl, String stChatFileName, String stMessageRef,
            List<String> stWorldNames, UserModelOverride userModelOverride, String tailSystemPrompt,
            String runtimePresetBundle, AiCapability routingCapability, String routingRouteKey, String userPersona
    ) {
        this(conversationId, userMessage, messages, clientMessageId, stream, mode, allowedFeatures,
                userName, charName, groupNames, stAvatarUrl, stChatFileName, stMessageRef, stWorldNames,
                userModelOverride, tailSystemPrompt, runtimePresetBundle, routingCapability, routingRouteKey,
                userPersona, null, null, null);
    }

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
            UserModelOverride userModelOverride,
            String tailSystemPrompt,
            String runtimePresetBundle,
            AiCapability routingCapability,
            String routingRouteKey
    ) {
        this(conversationId, userMessage, messages, clientMessageId, stream, mode, allowedFeatures,
                userName, charName, groupNames, stAvatarUrl, stChatFileName, stMessageRef, stWorldNames,
                userModelOverride, tailSystemPrompt, runtimePresetBundle, routingCapability, routingRouteKey,
                null, null, null, null);
    }

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
            UserModelOverride userModelOverride,
            String tailSystemPrompt,
            String runtimePresetBundle,
            AiCapability routingCapability
    ) {
        this(conversationId, userMessage, messages, clientMessageId, stream, mode, allowedFeatures,
                userName, charName, groupNames, stAvatarUrl, stChatFileName, stMessageRef, stWorldNames,
                userModelOverride, tailSystemPrompt, runtimePresetBundle, routingCapability, null,
                null, null, null, null);
    }

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
            UserModelOverride userModelOverride,
            String tailSystemPrompt,
            String runtimePresetBundle
    ) {
        this(conversationId, userMessage, messages, clientMessageId, stream, mode, allowedFeatures,
                userName, charName, groupNames, stAvatarUrl, stChatFileName, stMessageRef, stWorldNames,
                userModelOverride, tailSystemPrompt, runtimePresetBundle, AiCapability.CHAT, null,
                null, null, null, null);
    }

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
                userModelOverride,
                null,
                null,
                AiCapability.CHAT,
                null,
                null,
                null,
                null,
                null
        );
    }

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
            UserModelOverride userModelOverride,
            String tailSystemPrompt
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
                userModelOverride,
                tailSystemPrompt,
                null,
                AiCapability.CHAT,
                null,
                null,
                null,
                null,
                null
        );
    }

    public ChatGenerateRequest withStudioLore(String beforeCharacter, String afterCharacter, String beforeHistory) {
        return new ChatGenerateRequest(
                conversationId, userMessage, messages, clientMessageId, stream, mode, allowedFeatures,
                userName, charName, groupNames, stAvatarUrl, stChatFileName, stMessageRef, stWorldNames,
                userModelOverride, tailSystemPrompt, runtimePresetBundle, routingCapability, routingRouteKey,
                userPersona, beforeCharacter, afterCharacter, beforeHistory
        );
    }

    public AiCapability routingCapabilityOrChat() {
        return routingCapability == null ? AiCapability.CHAT : routingCapability;
    }

    public String routingRouteKeyOrEmpty() {
        return routingRouteKey == null ? "" : routingRouteKey.trim();
    }

    public boolean hasImageInput() {
        return messages != null && messages.stream().anyMatch(ChatMessage::hasImageContent);
    }
}
