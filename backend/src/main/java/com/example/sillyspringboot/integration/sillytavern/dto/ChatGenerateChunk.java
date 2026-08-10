package com.example.sillyspringboot.integration.sillytavern.dto;

public record ChatGenerateChunk(
        Long conversationId,
        String messageId,
        int chunkIndex,
        String delta,
        boolean done,
        String reasoning,
        String metrics
) {}
