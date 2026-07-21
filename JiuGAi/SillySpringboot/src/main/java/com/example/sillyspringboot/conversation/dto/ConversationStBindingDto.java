package com.example.sillyspringboot.conversation.dto;

import java.util.List;

public record ConversationStBindingDto(
        String stRuntimeProfile,
        String stCharacterRef,
        String stChatRef,
        String stAvatarUrl,
        String stChatFileName,
        List<String> worldNames,
        String stDisplayNameOverride,
        String status
) {}

