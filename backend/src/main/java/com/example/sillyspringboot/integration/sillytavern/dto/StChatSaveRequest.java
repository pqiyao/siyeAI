package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.List;
import java.util.Map;

/**
 * 对应 ST `/api/chats/save` 的最小请求参数（受控封装）。
 * <p>
 * chat 为 ST 的“标准 chat 序列化格式”数组（header + messages）。
 */
public record StChatSaveRequest(
        String avatarUrl,
        String fileName,
        List<Map<String, Object>> chat,
        Boolean force
) {}

