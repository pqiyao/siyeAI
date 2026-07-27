package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.entity.ChatGenerationContext;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaleChatChargeService {

    private static final Logger log = LoggerFactory.getLogger(StaleChatChargeService.class);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES = 10;
    private static final int CLEANUP_BATCH_SIZE = 1000;
    private static final int CLEANUP_MAX_BATCHES = 10;

    private final AiChatModelMapper chatModelMapper;
    private final H5EntitlementService entitlementService;
    private final AppChatProperties chatProperties;

    public StaleChatChargeService(
            AiChatModelMapper chatModelMapper,
            H5EntitlementService entitlementService,
            AppChatProperties chatProperties
    ) {
        this.chatModelMapper = chatModelMapper;
        this.entitlementService = entitlementService;
        this.chatProperties = chatProperties;
    }

    @Scheduled(
            initialDelayString = "${app.chat.stale-charge-reconcile-initial-delay-ms:90000}",
            fixedDelayString = "${app.chat.stale-charge-reconcile-interval-ms:60000}"
    )
    public void scheduledReconcile() {
        int reconciled = 0;
        LocalDateTime cutoff = staleCutoff();
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<ChatGenerationContext> contexts =
                    chatModelMapper.listStaleChargeContexts(cutoff, BATCH_SIZE);
            for (ChatGenerationContext context : contexts) {
                try {
                    entitlementService.reconcileStaleChatCharge(context);
                    reconciled++;
                } catch (RuntimeException ex) {
                    log.error("Failed to reconcile stale chat charge contextId={}",
                            context == null ? null : context.getId(), ex);
                }
            }
            if (contexts.size() < BATCH_SIZE) break;
        }
        if (reconciled > 0) log.warn("Reconciled {} stale chat charge contexts", reconciled);
    }

    @Scheduled(
            initialDelayString = "${app.chat.generation-context-cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${app.chat.generation-context-cleanup-interval-ms:86400000}"
    )
    public void scheduledCleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = 0;
        for (int batch = 0; batch < CLEANUP_MAX_BATCHES; batch++) {
            int count = chatModelMapper.deleteTerminalGenerationContexts(cutoff, CLEANUP_BATCH_SIZE);
            deleted += count;
            if (count < CLEANUP_BATCH_SIZE) break;
        }
        if (deleted > 0) log.info("Cleaned {} terminal chat generation contexts", deleted);
    }

    private LocalDateTime staleCutoff() {
        long configuredWindow = (long) Math.max(1, chatProperties.getGenerationTimeoutSeconds())
                + Math.max(1, chatProperties.getMaxQueueWaitSeconds())
                + 120L;
        return LocalDateTime.now().minusSeconds(Math.max(300L, configuredWindow));
    }
}
