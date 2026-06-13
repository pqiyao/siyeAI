package com.example.sillyspringboot.conversation.dto;

public record ConversationDetailDto(
        Long conversationId,
        Long characterId,
        String title,
        ConversationStBindingDto stBinding
) {}

