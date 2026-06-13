package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.integration.sillytavern.StStreamControl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 阶段 4：运行中生成注册表，用于 stopGeneration。
 * <p>
 * 约束：只保存“可取消”的控制句柄，不保存 ST 原始连接对象。
 */
public class AppChatRuntimeRegistry {

    private final ConcurrentHashMap<Long, StStreamControl> byConversation = new ConcurrentHashMap<>();

    public StStreamControl register(long conversationId, StStreamControl control) {
        StStreamControl previous = byConversation.put(conversationId, control);
        if (previous != null && previous != control) {
            previous.cancel();
        }
        return control;
    }

    public void unregister(long conversationId) {
        byConversation.remove(conversationId);
    }

    public boolean cancel(long conversationId) {
        StStreamControl c = byConversation.get(conversationId);
        if (c == null) return false;
        c.cancel();
        return true;
    }
}

