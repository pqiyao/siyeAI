package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.integration.sillytavern.StStreamControl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 阶段 4：运行中生成注册表，用于 stopGeneration。
 * <p>
 * 约束：只保存“可取消”的控制句柄，不保存 ST 原始连接对象。
 */
public class AppChatRuntimeRegistry {

    private final ConcurrentHashMap<Long, RuntimeEntry> byConversation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RuntimeEntry> byTask = new ConcurrentHashMap<>();

    public synchronized StStreamControl register(long conversationId, StStreamControl control) {
        if (conversationId <= 0 || control == null) {
            throw new IllegalArgumentException("conversationId and control are required");
        }
        RuntimeEntry next = new RuntimeEntry(conversationId, null, control);
        RuntimeEntry previous = byConversation.put(conversationId, next);
        if (previous != null && previous.control() != control) {
            removeTaskBinding(previous);
            previous.control().cancel();
        }
        return control;
    }

    public synchronized boolean bindTask(long conversationId, long taskId, StStreamControl control) {
        if (taskId <= 0 || control == null) {
            return false;
        }
        RuntimeEntry current = byConversation.get(conversationId);
        if (current == null || current.control() != control) {
            return false;
        }
        removeTaskBinding(current);
        RuntimeEntry bound = new RuntimeEntry(conversationId, taskId, control);
        byConversation.put(conversationId, bound);
        RuntimeEntry displaced = byTask.put(taskId, bound);
        if (displaced != null && displaced.control() != control) {
            byConversation.remove(displaced.conversationId(), displaced);
            displaced.control().cancel();
        }
        return true;
    }

    public synchronized void unregister(long conversationId, StStreamControl control) {
        RuntimeEntry current = byConversation.get(conversationId);
        if (current == null || current.control() != control) {
            return;
        }
        byConversation.remove(conversationId, current);
        removeTaskBinding(current);
    }

    public boolean cancel(long conversationId) {
        RuntimeEntry entry = byConversation.get(conversationId);
        if (entry == null) return false;
        entry.control().cancel();
        return true;
    }

    public boolean cancelTask(long taskId) {
        RuntimeEntry entry = byTask.get(taskId);
        if (entry == null) return false;
        entry.control().cancel();
        return true;
    }

    public RuntimeStatus status() {
        return new RuntimeStatus(
                byConversation.size(),
                byTask.size(),
                byConversation.keySet().stream().sorted().toList(),
                byTask.keySet().stream().sorted().toList()
        );
    }

    private void removeTaskBinding(RuntimeEntry entry) {
        if (entry != null && entry.taskId() != null) {
            byTask.remove(entry.taskId(), entry);
        }
    }

    private record RuntimeEntry(long conversationId, Long taskId, StStreamControl control) {}

    public record RuntimeStatus(
            int activeConversations,
            int activeTasks,
            java.util.List<Long> conversationIds,
            java.util.List<Long> taskIds
    ) {}
}

