package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ConversationMemoryAutoRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryAutoRefreshService.class);

    private static final int MIN_VISIBLE_MESSAGES = 6;
    private static final int MIN_NEW_MESSAGES = 20;
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(30);

    private final AppMessageMapper messageMapper;
    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryService memoryService;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "conversation-memory-auto-refresh");
        t.setDaemon(true);
        return t;
    });

    public ConversationMemoryAutoRefreshService(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryService memoryService
    ) {
        this.messageMapper = messageMapper;
        this.memoryMapper = memoryMapper;
        this.memoryService = memoryService;
    }

    public void maybeTriggerAfterGenerationSuccess(long conversationId) {
        if (conversationId <= 0) {
            return;
        }
        try {
            executor.execute(() -> runMaybeRefresh(conversationId));
        } catch (RejectedExecutionException ex) {
            log.debug("conversation memory auto refresh executor rejected conversationId={}", conversationId);
        }
    }

    private void runMaybeRefresh(long conversationId) {
        try {
            if (!shouldRefresh(conversationId) || !inFlight.add(conversationId)) {
                return;
            }
            try {
                memoryService.refreshConversationMemory(conversationId);
            } finally {
                inFlight.remove(conversationId);
            }
        } catch (RuntimeException ex) {
            log.warn("conversation memory auto refresh failed conversationId={}", conversationId, ex);
        }
    }

    public boolean shouldRefresh(long conversationId) {
        int visibleCount = messageMapper.countMemorySourceByConversationId(conversationId);
        if (visibleCount < MIN_VISIBLE_MESSAGES) {
            return false;
        }

        AppConversationMemory memory = memoryMapper.findByConversationId(conversationId);
        int lastRefreshedMessageCount = memory == null ? 0 : memory.getLastRefreshedMessageCount();
        if (visibleCount - lastRefreshedMessageCount < MIN_NEW_MESSAGES) {
            return false;
        }

        LocalDateTime updatedAt = memory == null ? null : memory.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        return !updatedAt.plus(MIN_REFRESH_INTERVAL).isAfter(LocalDateTime.now());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
