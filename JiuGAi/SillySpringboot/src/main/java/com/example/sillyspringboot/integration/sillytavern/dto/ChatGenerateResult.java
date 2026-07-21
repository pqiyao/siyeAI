package com.example.sillyspringboot.integration.sillytavern.dto;

public record ChatGenerateResult(
        Long conversationId,
        String assistantMessageId,
        String text,
        int swipeCount,
        String finishReason,
        String usage,
        String provider
) {}
