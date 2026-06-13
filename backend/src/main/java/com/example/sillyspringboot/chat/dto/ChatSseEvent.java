package com.example.sillyspringboot.chat.dto;

import com.example.sillyspringboot.shared.error.ErrorCode;

/**
 * App 侧统一 SSE 事件（阶段 4 MVP）。
 * <p>
 * 注意：这是对 Mini App 的稳定协议，禁止透出 ST 原始流格式。
 */
public record ChatSseEvent(
        String type,              // "state" | "chunk" | "error"
        Long conversationId,
        String clientMessageId,
        String state,             // PENDING/GENERATING/SUCCESS/FAILED/STOPPED/QUEUED
        Integer chunkIndex,
        String delta,
        Boolean done,
        ErrorCode errorCode,
        String message,
        String traceId
) {
    public static ChatSseEvent state(Long conversationId, String clientMessageId, String state) {
        return new ChatSseEvent("state", conversationId, clientMessageId, state, null, null, null, null, null, null);
    }

    public static ChatSseEvent chunk(Long conversationId, String clientMessageId, int chunkIndex, String delta, boolean done) {
        return new ChatSseEvent("chunk", conversationId, clientMessageId, null, chunkIndex, delta, done, null, null, null);
    }

    public static ChatSseEvent error(Long conversationId, String clientMessageId, ErrorCode code, String message, String traceId) {
        return new ChatSseEvent("error", conversationId, clientMessageId, "FAILED", null, null, true, code, message, traceId);
    }
}

