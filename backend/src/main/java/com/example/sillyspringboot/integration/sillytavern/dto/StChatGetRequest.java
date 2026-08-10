package com.example.sillyspringboot.integration.sillytavern.dto;

/**
 * 对应 ST `/api/chats/get` 的最小请求参数（受控封装）。
 */
public record StChatGetRequest(
        String avatarUrl,
        String fileName
) {}

