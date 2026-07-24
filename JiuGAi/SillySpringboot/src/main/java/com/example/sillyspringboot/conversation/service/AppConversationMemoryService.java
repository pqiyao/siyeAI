package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshResult;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AppConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AppConversationMemoryService.class);
    private static final int MAX_H5_MEMORY_ENTRY_PAGE_SIZE = 50;
    private static final Set<String> H5_MEMORY_ENTRY_FILTERS = Set.of("all", "enabled", "disabled", "archived");
    private static final Comparator<AppConversationMemoryEntry> H5_MEMORY_ENTRY_ORDER = Comparator
            .comparingInt(AppConversationMemoryService::h5MemoryEntryGroup)
            .thenComparing(Comparator.comparingInt(AppConversationMemoryEntry::getPriority).reversed())
            .thenComparing(
                    AppConversationMemoryEntry::getUpdatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            )
            .thenComparing(AppConversationMemoryEntry::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final AppMessageMapper messageMapper;
    private final ConversationMemoryLlmService memoryLlmService;
    private final ConversationMemorySanitizer memorySanitizer;
    private final ConversationMemoryWorldbookSyncService worldbookSyncService;
    private final ConversationMemoryCapacityService memoryCapacityService;
    private final MemoryLlmProperties memoryLlmProperties;
    private final AppConversationBranchMapper branchMapper;
    private final ConversationMemoryApplyService memoryApplyService;
    private final ReentrantLock[] operationLocks = createOperationLocks(256);

    public AppConversationMemoryService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemoryLlmService memoryLlmService,
            ConversationMemorySanitizer memorySanitizer,
            ConversationMemoryWorldbookSyncService worldbookSyncService,
            ConversationMemoryCapacityService memoryCapacityService,
            MemoryLlmProperties memoryLlmProperties) {
        this(
                memoryMapper,
                entryMapper,
                messageMapper,
                memoryLlmService,
                memorySanitizer,
                worldbookSyncService,
                memoryCapacityService,
                memoryLlmProperties,
                null,
                null
        );
    }

    @Autowired
    public AppConversationMemoryService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemoryLlmService memoryLlmService,
            ConversationMemorySanitizer memorySanitizer,
            ConversationMemoryWorldbookSyncService worldbookSyncService,
            ConversationMemoryCapacityService memoryCapacityService,
            MemoryLlmProperties memoryLlmProperties,
            AppConversationBranchMapper branchMapper,
            ConversationMemoryApplyService memoryApplyService) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.messageMapper = messageMapper;
        this.memoryLlmService = memoryLlmService;
        this.memorySanitizer = memorySanitizer;
        this.worldbookSyncService = worldbookSyncService;
        this.memoryCapacityService = memoryCapacityService;
        this.memoryLlmProperties = memoryLlmProperties;
        this.branchMapper = branchMapper;
        this.memoryApplyService = memoryApplyService;
    }

    public ConversationMemoryRefreshResult refreshConversationMemory(long conversationId) {
        return refreshConversationMemory(conversationId, null);
    }

    public ConversationMemoryRefreshResult refreshConversationMemory(long conversationId, Long branchId) {
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        if (!lock.tryLock()) {
            return toRefreshResult(conversationId, branchId);
        }
        try {
            return refreshConversationMemoryInternal(conversationId, branchId);
        } finally {
            releaseOperationLock(lock);
        }
    }

    public void reconcileConversationMemoryAfterHistoryChange(
            long conversationId,
            long branchId,
            boolean shouldRebuild
    ) {
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        lock.lock();
        try {
            syncWorldbookKeepingFailureState(conversationId, branchId);
            if (shouldRebuild) {
                refreshConversationMemoryInternal(conversationId, branchId);
            }
        } finally {
            releaseOperationLock(lock);
        }
    }

    public ConversationMemoryRefreshResult refreshConversationMemoryManual(long conversationId, long branchId) {
        int visibleCount = countVisibleMemorySources(conversationId, branchId);
        int minimum = Math.max(1, memoryLlmProperties.getAutoMinVisibleMessages());
        if (visibleCount < minimum) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "至少需要 " + minimum + " 条有效聊天消息才能整理记忆"
            );
        }

        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆正在整理，请稍后再试");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        boolean acquired = false;
        try {
            memoryMapper.ensureForBranch(conversationId, branchId);
            LocalDateTime now = LocalDateTime.now();
            acquired = memoryMapper.tryAcquireManualRefresh(
                    conversationId,
                    branchId,
                    token,
                    now.minusSeconds(Math.max(10, memoryLlmProperties.getManualRefreshCooldownSeconds())),
                    now.minusSeconds(Math.max(60, memoryLlmProperties.getManualRefreshLeaseSeconds()))
            ) > 0;
            if (!acquired) {
                throw new BusinessException(
                        ErrorCode.RATE_LIMITED,
                        "整理过于频繁，请稍后再试"
                );
            }
            return refreshConversationMemoryInternal(conversationId, branchId);
        } finally {
            if (acquired) {
                memoryMapper.releaseManualRefresh(conversationId, branchId, token);
            }
            releaseOperationLock(lock);
        }
    }

    public Map<String, Object> retryWorldbookSync(long conversationId, long branchId) {
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆正在处理中，请稍后再试");
        }
        try {
            memoryMapper.ensureForBranch(conversationId, branchId);
            try {
                worldbookSyncService.syncWorldbook(conversationId, branchId);
            } catch (RuntimeException ignored) {
                // The sync service persists FAILED and the panel returns that exact state.
            }
            return toH5MemoryDetailMap(conversationId, branchId);
        } finally {
            releaseOperationLock(lock);
        }
    }

    @Transactional
    public boolean invalidateConversationMemoryAfterHistoryChange(long conversationId, long branchId) {
        if (conversationId <= 0 || branchId <= 0) {
            return false;
        }
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        lock.lock();
        try {
            if (memoryApplyService != null) {
                return memoryApplyService.invalidateAfterHistoryChange(
                        conversationId,
                        branchId,
                        Math.max(1, memoryLlmProperties.getAutoMinVisibleMessages())
                );
            }
            memoryMapper.ensureForBranch(conversationId, branchId);
            entryMapper.softDeleteGeneratedByConversationBranchId(conversationId, branchId);
            int entryCount = entryMapper.countAllByConversationBranchId(conversationId, branchId);
            int enabledEntryCount = entryMapper.countEnabledByConversationBranchId(conversationId, branchId);
            List<AppConversationMemoryEntry> enabledEntries =
                    entryMapper.listEnabledByConversationBranchId(conversationId, branchId);
            String summaryPreview = buildEnabledSummaryPreview(enabledEntries);
            String syncStatus = enabledEntryCount > 0
                    ? "PENDING"
                    : ConversationMemoryWorldbookSyncService.SYNC_SKIPPED;
            memoryMapper.upsertRefreshStateForBranch(
                    conversationId,
                    branchId,
                    summaryPreview,
                    enabledEntryCount,
                    null,
                    entryCount,
                    enabledEntryCount,
                    null,
                    0,
                    syncStatus,
                    null
            );
            return countVisibleMemorySources(conversationId, branchId)
                    >= Math.max(1, memoryLlmProperties.getAutoMinVisibleMessages());
        } finally {
            releaseOperationLock(lock);
        }
    }

    private ConversationMemoryRefreshResult refreshConversationMemoryInternal(long conversationId, Long branchId) {
        if (hasBranch(branchId) && branchMapper != null && memoryApplyService != null) {
            return refreshConversationMemoryWithRevisionFence(conversationId, branchId);
        }
        MemorySourceVersion expectedVersion = captureMemorySourceVersion(conversationId, branchId);
        StructuredRefreshOutcome structuredOutcome = refreshStructuredEntries(
                conversationId,
                branchId,
                expectedVersion
        );
        if (structuredOutcome == StructuredRefreshOutcome.APPLIED) {
            return toRefreshResult(conversationId, branchId);
        }
        if (structuredOutcome == StructuredRefreshOutcome.STALE
                || !isMemorySourceVersionCurrent(conversationId, branchId, expectedVersion)) {
            return staleRefreshResult(conversationId, branchId);
        }
        Optional<ConversationMemoryLlmService.MemoryRollup> llm = memoryLlmService.tryLlmRollup(conversationId, branchId);
        if (llm.isPresent()) {
            if (!isMemorySourceVersionCurrent(conversationId, branchId, expectedVersion)) {
                return staleRefreshResult(conversationId, branchId);
            }
            ConversationMemoryLlmService.MemoryRollup r = llm.get();
            if (hasBranch(branchId)) {
                memoryMapper.upsertRollupForBranch(conversationId, branchId, r.summaryPreview(), r.factsCount());
            } else {
                memoryMapper.upsertRollup(conversationId, r.summaryPreview(), r.factsCount());
            }
            return toRefreshResult(conversationId, branchId);
        }
        if (!isMemorySourceVersionCurrent(conversationId, branchId, expectedVersion)) {
            return staleRefreshResult(conversationId, branchId);
        }
        if (memoryLlmProperties.isFallbackToHeuristic()) {
            refreshRollupFromMessages(conversationId, branchId);
        } else {
            touchRefresh(conversationId, branchId);
        }
        return toRefreshResult(conversationId, branchId);
    }

    private ConversationMemoryRefreshResult refreshConversationMemoryWithRevisionFence(
            long conversationId,
            long branchId
    ) {
        ConversationMemoryRefreshSnapshot snapshot = captureStableRefreshSnapshot(conversationId, branchId);
        Optional<StructuredMemoryExtraction> structured = memoryLlmService.tryStructuredMemoryExtract(snapshot);
        if (structured.isPresent()) {
            ConversationMemoryApplyService.ApplyStatus status =
                    memoryApplyService.applyStructured(snapshot, structured.get());
            if (status == ConversationMemoryApplyService.ApplyStatus.STALE) {
                return staleRefreshResult(conversationId, branchId);
            }
            syncWorldbookKeepingFailureState(conversationId, branchId);
            return toRefreshResult(conversationId, branchId);
        }

        Optional<ConversationMemoryLlmService.MemoryRollup> rollup = memoryLlmService.tryLlmRollup(snapshot);
        if (rollup.isPresent()) {
            ConversationMemoryLlmService.MemoryRollup value = rollup.get();
            if (memoryApplyService.applyRollup(snapshot, value.summaryPreview(), value.factsCount())
                    == ConversationMemoryApplyService.ApplyStatus.STALE) {
                return staleRefreshResult(conversationId, branchId);
            }
            return toRefreshResult(conversationId, branchId);
        }

        RollupPreview fallback = memoryLlmProperties.isFallbackToHeuristic()
                ? buildHeuristicRollup(snapshot.messages())
                : new RollupPreview(snapshot.currentSummaryPreview(), snapshot.currentFactsCount());
        if (memoryApplyService.applyRollup(snapshot, fallback.summaryPreview(), fallback.factsCount())
                == ConversationMemoryApplyService.ApplyStatus.STALE) {
            return staleRefreshResult(conversationId, branchId);
        }
        return toRefreshResult(conversationId, branchId);
    }

    private ConversationMemoryRefreshSnapshot captureStableRefreshSnapshot(long conversationId, long branchId) {
        memoryMapper.ensureForBranch(conversationId, branchId);
        for (int attempt = 0; attempt < 3; attempt++) {
            com.example.sillyspringboot.conversation.entity.AppConversationBranch beforeBranch =
                    branchMapper.findByIdForConversation(conversationId, branchId);
            AppConversationMemory beforeMemory = memoryMapper.findByConversationBranchId(conversationId, branchId);
            if (beforeBranch == null || beforeMemory == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "conversation branch not found");
            }

            List<AppMessage> sourceRows = messageMapper.listRecentMemorySourceByConversationBranchAsc(
                    conversationId,
                    branchId,
                    Math.max(10, memoryLlmProperties.getMaxMessages())
            );
            List<AppConversationMemoryEntry> activeEntries =
                    entryMapper.listAllByConversationBranchId(conversationId, branchId);
            List<AppConversationMemoryEntry> deletedEntries =
                    entryMapper.listManualDeletedByConversationBranchId(conversationId, branchId);

            com.example.sillyspringboot.conversation.entity.AppConversationBranch afterBranch =
                    branchMapper.findByIdForConversation(conversationId, branchId);
            AppConversationMemory afterMemory = memoryMapper.findByConversationBranchId(conversationId, branchId);
            if (afterBranch == null || afterMemory == null) {
                continue;
            }
            if (beforeBranch.getMemorySourceRevision() != afterBranch.getMemorySourceRevision()
                    || beforeMemory.getManualRevision() != afterMemory.getManualRevision()
                    || beforeMemory.getMemoryRevision() != afterMemory.getMemoryRevision()) {
                continue;
            }

            List<ConversationMemoryRefreshSnapshot.MessageSnapshot> messages = sourceRows == null
                    ? List.of()
                    : sourceRows.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ConversationMemoryRefreshSnapshot.MessageSnapshot::from)
                    .toList();
            List<ConversationMemoryRefreshSnapshot.EntrySnapshot> existingEntries = new ArrayList<>();
            if (activeEntries != null) {
                activeEntries.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(ConversationMemoryRefreshSnapshot.EntrySnapshot::from)
                        .forEach(existingEntries::add);
            }
            if (deletedEntries != null) {
                deletedEntries.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(ConversationMemoryRefreshSnapshot.EntrySnapshot::from)
                        .forEach(existingEntries::add);
            }
            return new ConversationMemoryRefreshSnapshot(
                    conversationId,
                    branchId,
                    afterBranch.getMemorySourceRevision(),
                    afterMemory.getManualRevision(),
                    afterMemory.getMemoryRevision(),
                    messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId),
                    nullToEmpty(afterMemory.getSummaryPreview()),
                    afterMemory.getFactsCount(),
                    messages,
                    existingEntries
            );
        }
        throw new BusinessException(ErrorCode.SERVICE_BUSY, "聊天内容正在变化，请稍后重新整理记忆");
    }

    private static RollupPreview buildHeuristicRollup(
            List<ConversationMemoryRefreshSnapshot.MessageSnapshot> rows
    ) {
        StringBuilder summary = new StringBuilder();
        int userTurns = 0;
        int appended = 0;
        for (ConversationMemoryRefreshSnapshot.MessageSnapshot message : rows) {
            if (message == null) {
                continue;
            }
            String role = nullToEmpty(message.role());
            if ("user".equalsIgnoreCase(role)) {
                userTurns++;
            } else if (!"assistant".equalsIgnoreCase(role)) {
                continue;
            }
            String text = nullToEmpty(message.content()).trim();
            if (text.isEmpty() || appended >= 14) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            String line = text.replaceAll("\\s+", " ");
            if (line.length() > 100) {
                line = line.substring(0, 100) + "...";
            }
            summary.append('[')
                    .append("user".equalsIgnoreCase(role) ? "User" : "AI")
                    .append("] ")
                    .append(line);
            appended++;
        }
        String preview = summary.toString();
        if (preview.length() > 420) {
            preview = preview.substring(0, 420) + "...";
        }
        if (preview.isBlank()) {
            preview = "(No memorable turns yet)";
        }
        return new RollupPreview(preview, Math.min(Math.max(userTurns, 0), 99));
    }

    private record RollupPreview(String summaryPreview, int factsCount) {
    }

    private StructuredRefreshOutcome refreshStructuredEntries(
            long conversationId,
            Long branchId,
            MemorySourceVersion expectedVersion
    ) {
        List<AppConversationMemoryEntry> activeEntries = hasBranch(branchId)
                ? entryMapper.listAllByConversationBranchId(conversationId, branchId)
                : entryMapper.listAllByConversationId(conversationId);
        List<AppConversationMemoryEntry> deletedEntries = hasBranch(branchId)
                ? entryMapper.listManualDeletedByConversationBranchId(conversationId, branchId)
                : entryMapper.listManualDeletedByConversationId(conversationId);
        List<AppConversationMemoryEntry> existingEntries = new ArrayList<>();
        if (activeEntries != null) {
            existingEntries.addAll(activeEntries);
        }
        if (deletedEntries != null) {
            existingEntries.addAll(deletedEntries);
        }
        Optional<StructuredMemoryExtraction> structured = hasBranch(branchId)
                ? memoryLlmService.tryStructuredMemoryExtract(conversationId, branchId, existingEntries)
                : memoryLlmService.tryStructuredMemoryExtract(conversationId, existingEntries);
        if (structured.isEmpty()) {
            return StructuredRefreshOutcome.NOT_APPLIED;
        }
        if (!isMemorySourceVersionCurrent(conversationId, branchId, expectedVersion)) {
            return StructuredRefreshOutcome.STALE;
        }

        SourceRange sourceRange = resolveSourceRange(expectedVersion.rows());
        int totalVisibleCount = Math.max(sourceRange.messageCount(), expectedVersion.visibleCount());
        StructuredMemoryExtraction extraction = structured.get();
        Set<String> disabledKeys = new LinkedHashSet<>();
        for (String key : memorySanitizer.sanitizeDisableKeys(extraction.disableEntryKeys())) {
            if (disabledKeys.add(key)) {
                if (hasBranch(branchId)) {
                    entryMapper.disableByKeyForBranch(conversationId, branchId, key);
                } else {
                    entryMapper.disableByKey(conversationId, key);
                }
            }
        }

        if (extraction.entries() != null) {
            for (ExtractedMemoryEntry extracted : extraction.entries()) {
                for (String key : memorySanitizer.sanitizeReplaceKeys(extracted)) {
                    if (disabledKeys.add(key)) {
                        if (hasBranch(branchId)) {
                            entryMapper.disableByKeyForBranch(conversationId, branchId, key);
                        } else {
                            entryMapper.disableByKey(conversationId, key);
                        }
                    }
                }
                AppConversationMemoryEntry entity =
                        memorySanitizer.toEntity(conversationId, extracted, sourceRange.firstMessageId(), sourceRange.lastMessageId());
                if (entity != null) {
                    if (hasBranch(branchId)) {
                        entity.setBranchId(branchId);
                    } else if (entity.getBranchId() == null) {
                        entity.setBranchId(0L);
                    }
                    if (isBlockedByManualSuppression(entity, extracted, existingEntries)) {
                        continue;
                    }
                    entryMapper.upsert(entity);
                }
            }
        }

        if (hasBranch(branchId)) {
            memoryCapacityService.enforceAfterRefresh(conversationId, branchId);
        }

        int entryCount = hasBranch(branchId)
                ? entryMapper.countAllByConversationBranchId(conversationId, branchId)
                : entryMapper.countAllByConversationId(conversationId);
        List<AppConversationMemoryEntry> enabledEntries = hasBranch(branchId)
                ? entryMapper.listEnabledByConversationBranchId(conversationId, branchId)
                : null;
        int enabledEntryCount = hasBranch(branchId)
                ? (enabledEntries == null ? 0 : enabledEntries.size())
                : entryMapper.countEnabledByConversationId(conversationId);
        boolean hasManualSuppression = existingEntries.stream()
                .anyMatch(entry -> entry != null && (entry.isManualDeleted() || entry.isManualDisabled()));
        String summaryPreview = extraction.summaryPreview();
        if (hasBranch(branchId) || hasManualSuppression) {
            if (enabledEntries == null) {
                enabledEntries = entryMapper.listEnabledByConversationId(conversationId);
            }
            summaryPreview = buildEnabledSummaryPreview(enabledEntries);
        }
        String syncStatus = enabledEntryCount > 0 ? "PENDING" : ConversationMemoryWorldbookSyncService.SYNC_SKIPPED;
        if (hasBranch(branchId)) {
            memoryMapper.upsertRefreshStateForBranch(
                    conversationId,
                    branchId,
                    summaryPreview,
                    enabledEntryCount,
                    null,
                    entryCount,
                    enabledEntryCount,
                    sourceRange.lastMessageId(),
                    totalVisibleCount,
                    syncStatus,
                    null
            );
        } else {
            memoryMapper.upsertRefreshState(
                    conversationId,
                    summaryPreview,
                    enabledEntryCount,
                    null,
                    entryCount,
                    enabledEntryCount,
                    sourceRange.lastMessageId(),
                    totalVisibleCount,
                    syncStatus,
                    null
            );
        }
        try {
            if (hasBranch(branchId)) {
                worldbookSyncService.syncWorldbook(conversationId, branchId);
            } else {
                worldbookSyncService.syncWorldbook(conversationId);
            }
        } catch (RuntimeException ignored) {
            // syncWorldbook persists FAILED state first; return that state to H5 for retry UI.
        }
        return StructuredRefreshOutcome.APPLIED;
    }

    private MemorySourceVersion captureMemorySourceVersion(long conversationId, Long branchId) {
        List<AppMessage> sourceRows = hasBranch(branchId)
                ? messageMapper.listRecentMemorySourceByConversationBranchAsc(
                        conversationId,
                        branchId,
                        Math.max(10, memoryLlmProperties.getMaxMessages())
                )
                : messageMapper.listRecentMemorySourceByConversationAsc(
                        conversationId,
                        Math.max(10, memoryLlmProperties.getMaxMessages())
                );
        List<AppMessage> rows = sourceRows == null
                ? List.of()
                : sourceRows.stream().filter(java.util.Objects::nonNull).toList();
        List<MemorySourceRevision> revisions = rows.stream()
                .map(message -> new MemorySourceRevision(
                        message.getId(),
                        message.getRole(),
                        message.getStatus(),
                        message.getContent(),
                        message.getStMessageRef(),
                        message.getUpdatedAt()
                ))
                .toList();
        return new MemorySourceVersion(
                countVisibleMemorySources(conversationId, branchId),
                rows,
                revisions
        );
    }

    private boolean isMemorySourceVersionCurrent(
            long conversationId,
            Long branchId,
            MemorySourceVersion expected
    ) {
        MemorySourceVersion current = captureMemorySourceVersion(conversationId, branchId);
        return current.visibleCount() == expected.visibleCount()
                && current.revisions().equals(expected.revisions());
    }

    private ConversationMemoryRefreshResult staleRefreshResult(long conversationId, Long branchId) {
        log.info("conversation memory refresh discarded because source messages changed "
                        + "conversationId={} branchId={}",
                conversationId, branchId);
        return toRefreshResult(conversationId, branchId);
    }

    private SourceRange resolveSourceRange(List<AppMessage> rows) {
        Long firstId = null;
        Long lastId = null;
        int count = 0;
        if (rows != null) {
            for (AppMessage m : rows) {
                if (!isVisibleMemorySource(m)) {
                    continue;
                }
                if (m.getId() != null) {
                    if (firstId == null) {
                        firstId = m.getId();
                    }
                    lastId = m.getId();
                }
                count++;
            }
        }
        return new SourceRange(firstId, lastId, count);
    }

    private static boolean isVisibleMemorySource(AppMessage m) {
        if (m == null) {
            return false;
        }
        String st = m.getStatus() == null ? "" : m.getStatus();
        if ("FAILED".equalsIgnoreCase(st) || "DELETED".equalsIgnoreCase(st)) {
            return false;
        }
        String role = m.getRole() == null ? "" : m.getRole();
        if ("assistant".equalsIgnoreCase(role)) {
            return ("SUCCESS".equalsIgnoreCase(st) || "STOPPED".equalsIgnoreCase(st))
                    && m.getContent() != null
                    && !m.getContent().isBlank()
                    && isDisplayedRootMessage(m);
        }
        return "user".equalsIgnoreCase(role);
    }

    private static boolean isDisplayedRootMessage(AppMessage m) {
        if (m == null) {
            return false;
        }
        String ref = m.getStMessageRef();
        if (ref == null || !ref.startsWith("root:")) {
            return true;
        }
        try {
            long rootId = Long.parseLong(ref.substring("root:".length()));
            return m.getId() != null && m.getId().longValue() == rootId;
        } catch (Exception ignored) {
            return true;
        }
    }

    private enum StructuredRefreshOutcome {
        APPLIED,
        NOT_APPLIED,
        STALE
    }

    private record MemorySourceRevision(
            Long id,
            String role,
            String status,
            String content,
            String stMessageRef,
            LocalDateTime updatedAt
    ) {}

    private record MemorySourceVersion(
            int visibleCount,
            List<AppMessage> rows,
            List<MemorySourceRevision> revisions
    ) {}

    private record SourceRange(Long firstMessageId, Long lastMessageId, int messageCount) {}

    public void touchRefresh(long conversationId) {
        touchRefresh(conversationId, null);
    }

    public void touchRefresh(long conversationId, Long branchId) {
        if (hasBranch(branchId)) {
            memoryMapper.upsertTouchForBranch(conversationId, branchId);
        } else {
            memoryMapper.upsertTouch(conversationId);
        }
    }

    public void refreshRollupFromMessages(long conversationId) {
        refreshRollupFromMessages(conversationId, null);
    }

    public void refreshRollupFromMessages(long conversationId, Long branchId) {
        List<AppMessage> rows = hasBranch(branchId)
                ? messageMapper.listRecentMemorySourceByConversationBranchAsc(conversationId, branchId, 160)
                : messageMapper.listRecentMemorySourceByConversationAsc(conversationId, 160);
        StringBuilder sb = new StringBuilder();
        int userTurns = 0;
        int appended = 0;
        for (AppMessage m : rows) {
            if (m == null) {
                continue;
            }
            String st = m.getStatus() == null ? "" : m.getStatus();
            if ("FAILED".equalsIgnoreCase(st) || "DELETED".equalsIgnoreCase(st)) {
                continue;
            }
            String role = m.getRole() == null ? "" : m.getRole();
            if ("assistant".equalsIgnoreCase(role)) {
                if (!"SUCCESS".equalsIgnoreCase(st) && !"STOPPED".equalsIgnoreCase(st)) {
                    continue;
                }
                if (!isDisplayedRootMessage(m)) {
                    continue;
                }
            } else if (!"user".equalsIgnoreCase(role)) {
                continue;
            }
            if ("user".equalsIgnoreCase(role)) {
                userTurns++;
            }
            String text = m.getContent() == null ? "" : m.getContent().trim();
            if (text.isEmpty() || appended >= 14) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String previewLine = text.replaceAll("\\s+", " ");
            if (previewLine.length() > 100) {
                previewLine = previewLine.substring(0, 100) + "...";
            }
            sb.append('[')
                    .append("user".equalsIgnoreCase(role) ? "User" : "AI")
                    .append("] ")
                    .append(previewLine);
            appended++;
        }

        String preview = sb.toString();
        if (preview.length() > 420) {
            preview = preview.substring(0, 420) + "...";
        }
        if (preview.isBlank()) {
            preview = "(No memorable turns yet)";
        }
        if (hasBranch(branchId)) {
            memoryMapper.upsertRollupForBranch(conversationId, branchId, preview, Math.min(Math.max(userTurns, 0), 99));
        } else {
            memoryMapper.upsertRollup(conversationId, preview, Math.min(Math.max(userTurns, 0), 99));
        }
    }

    public Map<String, Object> toH5MemoryMap(long conversationId) {
        return toH5MemoryMap(conversationId, null);
    }

    public Map<String, Object> toH5MemoryMap(long conversationId, Long branchId) {
        AppConversationMemory m = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        if (m == null) {
            return null;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("summaryPreview", m.getSummaryPreview());
        out.put("branchId", m.getBranchId());
        out.put("factsCount", m.getFactsCount());
        out.put("entryCount", m.getEntryCount());
        out.put("enabledEntryCount", m.getEnabledEntryCount());
        out.put("memoryWorldName", nullToEmpty(m.getMemoryWorldName()));
        out.put("lastSourceMessageId", m.getLastSourceMessageId());
        out.put("lastRefreshedMessageCount", m.getLastRefreshedMessageCount());
        out.put("manualRefreshCooldownSeconds", Math.max(10, memoryLlmProperties.getManualRefreshCooldownSeconds()));
        if (m.getLastManualRefreshAt() != null) {
            out.put("lastManualRefreshAt", m.getLastManualRefreshAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        out.put("syncStatus", nullToEmpty(m.getSyncStatus()));
        out.put("syncError", nullToEmpty(m.getSyncError()));
        if (m.getLastSyncedAt() != null) {
            out.put("lastSyncedAt", m.getLastSyncedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (m.getUpdatedAt() != null) {
            out.put("updatedAt", m.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        return out;
    }

    public Map<String, Object> toH5MemoryDetailMap(long conversationId) {
        return toH5MemoryDetailMap(conversationId, null);
    }

    public Map<String, Object> toH5MemoryDetailMap(long conversationId, Long branchId) {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> summary = toH5MemoryMap(conversationId, branchId);
        if (summary != null) {
            out.putAll(summary);
        }
        List<AppConversationMemoryEntry> entries = hasBranch(branchId)
                ? entryMapper.listAllByConversationBranchId(conversationId, branchId)
                : entryMapper.listAllByConversationId(conversationId);
        List<Map<String, Object>> rows = entries == null
                ? List.of()
                : entries.stream().map(this::toH5MemoryEntryMap).toList();
        out.put("entries", rows);
        if (!out.containsKey("entryCount")) {
            out.put("entryCount", hasBranch(branchId)
                    ? entryMapper.countAllByConversationBranchId(conversationId, branchId)
                    : entryMapper.countAllByConversationId(conversationId));
        }
        if (!out.containsKey("enabledEntryCount")) {
            out.put("enabledEntryCount", hasBranch(branchId)
                    ? entryMapper.countEnabledByConversationBranchId(conversationId, branchId)
                    : entryMapper.countEnabledByConversationId(conversationId));
        }
        out.putIfAbsent(
                "manualRefreshCooldownSeconds",
                Math.max(10, memoryLlmProperties.getManualRefreshCooldownSeconds())
        );
        return out;
    }

    public Map<String, Object> toH5MemoryDetailMap(
            long conversationId,
            long branchId,
            String entryFilter,
            int page,
            int pageSize
    ) {
        if (conversationId <= 0 || branchId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId or branchId invalid");
        }
        String safeFilter = normalizeH5MemoryEntryFilter(entryFilter);
        if (page <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page invalid");
        }
        if (pageSize <= 0 || pageSize > MAX_H5_MEMORY_ENTRY_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "pageSize invalid");
        }

        Map<String, Object> out = new HashMap<>();
        Map<String, Object> summary = toH5MemoryMap(conversationId, branchId);
        if (summary != null) {
            out.putAll(summary);
        }

        List<AppConversationMemoryEntry> panelEntries = entryMapper.listPanelByConversationBranchId(
                conversationId,
                branchId
        );
        List<AppConversationMemoryEntry> stableEntries = panelEntries == null
                ? new ArrayList<>()
                : new ArrayList<>(panelEntries);
        stableEntries.removeIf(entry -> !isH5PanelVisibleMemoryEntry(entry));
        stableEntries.sort(H5_MEMORY_ENTRY_ORDER);

        int archivedEntryCount = (int) stableEntries.stream()
                .filter(AppConversationMemoryService::isH5ArchivedMemoryEntry)
                .count();
        int disabledEntryCount = (int) stableEntries.stream()
                .filter(AppConversationMemoryService::isH5DisabledMemoryEntry)
                .count();
        List<AppConversationMemoryEntry> filteredEntries = stableEntries.stream()
                .filter(entry -> matchesH5MemoryEntryFilter(entry, safeFilter))
                .toList();

        int entryTotal = filteredEntries.size();
        long offset = (long) (page - 1) * pageSize;
        int fromIndex = offset >= entryTotal ? entryTotal : (int) offset;
        int toIndex = Math.min(entryTotal, fromIndex + pageSize);
        List<Map<String, Object>> rows = filteredEntries.subList(fromIndex, toIndex).stream()
                .map(this::toH5MemoryEntryMap)
                .toList();

        out.put("entries", rows);
        out.put("entryFilter", safeFilter);
        out.put("entryPage", page);
        out.put("entryPageSize", pageSize);
        out.put("entryTotal", entryTotal);
        out.put("entryHasMore", toIndex < entryTotal);
        out.put("archivedEntryCount", archivedEntryCount);
        out.put("disabledEntryCount", disabledEntryCount);
        if (!out.containsKey("entryCount")) {
            out.put("entryCount", entryMapper.countAllByConversationBranchId(conversationId, branchId));
        }
        if (!out.containsKey("enabledEntryCount")) {
            out.put("enabledEntryCount", entryMapper.countEnabledByConversationBranchId(conversationId, branchId));
        }
        out.putIfAbsent(
                "manualRefreshCooldownSeconds",
                Math.max(10, memoryLlmProperties.getManualRefreshCooldownSeconds())
        );
        return out;
    }

    public Map<String, Object> disableMemoryEntry(long conversationId, long entryId) {
        return disableMemoryEntry(conversationId, null, entryId);
    }

    public Map<String, Object> disableMemoryEntry(long conversationId, Long branchId, long entryId) {
        return setMemoryEntryEnabled(conversationId, branchId, entryId, false);
    }

    public Map<String, Object> setMemoryEntryEnabled(
            long conversationId,
            Long branchId,
            long entryId,
            boolean enabled
    ) {
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆正在处理中，请稍后再试");
        }
        try {
            long safeBranchId = hasBranch(branchId) ? branchId : 0L;
            if (hasBranch(branchId) && memoryApplyService != null) {
                memoryApplyService.setMemoryEntryEnabled(
                        conversationId,
                        safeBranchId,
                        entryId,
                        enabled
                );
            } else if (hasBranch(branchId)) {
                memoryCapacityService.setManualEnabledWithCapacity(
                        entryId,
                        conversationId,
                        safeBranchId,
                        enabled
                );
            } else {
                AppConversationMemoryEntry existing = entryMapper.findByIdForConversationBranch(
                        entryId,
                        conversationId,
                        safeBranchId
                );
                if (existing == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
                }
                int updated = entryMapper.setManualEnabledById(
                        entryId,
                        conversationId,
                        safeBranchId,
                        enabled,
                        !enabled
                );
                if (updated <= 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
                }
            }
            if (memoryApplyService == null || !hasBranch(branchId)) {
                refreshSummaryPreview(conversationId, branchId);
            }
            syncWorldbookKeepingFailureState(conversationId, branchId);
            return toH5MemoryDetailMap(conversationId, branchId);
        } finally {
            releaseOperationLock(lock);
        }
    }

    public Map<String, Object> deleteMemoryEntry(long conversationId, Long branchId, long entryId) {
        String key = memoryKey(conversationId, branchId);
        ReentrantLock lock = operationLock(key);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆正在处理中，请稍后再试");
        }
        try {
            long safeBranchId = hasBranch(branchId) ? branchId : 0L;
            if (hasBranch(branchId) && memoryApplyService != null) {
                memoryApplyService.deleteMemoryEntry(conversationId, safeBranchId, entryId);
                syncWorldbookKeepingFailureState(conversationId, branchId);
                return toH5MemoryDetailMap(conversationId, branchId);
            }
            AppConversationMemoryEntry existing = entryMapper.findByIdForConversationBranch(
                    entryId,
                    conversationId,
                    safeBranchId
            );
            if (existing == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
            }
            int deleted = entryMapper.softDeleteManualById(
                    entryId,
                    conversationId,
                    safeBranchId
            );
            if (deleted <= 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
            }
            refreshSummaryPreview(conversationId, branchId);
            syncWorldbookKeepingFailureState(conversationId, branchId);
            return toH5MemoryDetailMap(conversationId, branchId);
        } finally {
            releaseOperationLock(lock);
        }
    }

    private Map<String, Object> toH5MemoryEntryMap(AppConversationMemoryEntry entry) {
        Map<String, Object> out = new HashMap<>();
        if (entry == null) {
            return out;
        }
        out.put("id", entry.getId());
        out.put("entryKey", nullToEmpty(entry.getEntryKey()));
        out.put("memoryType", nullToEmpty(entry.getMemoryType()));
        out.put("title", nullToEmpty(entry.getTitle()));
        out.put("content", nullToEmpty(entry.getContent()));
        out.put("keywords", memorySanitizer.readKeywords(entry.getKeywordsJson()));
        out.put("secondaryKeywords", memorySanitizer.readKeywords(entry.getSecondaryKeywordsJson()));
        out.put("priority", entry.getPriority());
        out.put("position", nullToEmpty(entry.getPosition()));
        out.put("constantInjection", entry.isConstantInjection());
        out.put("selective", entry.isSelective());
        out.put("enabled", entry.isEnabled());
        out.put("manualDisabled", entry.isManualDisabled());
        out.put("manualPinned", entry.isManualPinned());
        out.put("archived", isH5ArchivedMemoryEntry(entry));
        out.put("retiredReason", nullToEmpty(entry.getRetiredReason()));
        out.put("confidence", entry.getConfidence() == null ? null : entry.getConfidence().doubleValue());
        out.put("sourceMessageFromId", entry.getSourceMessageFromId());
        out.put("sourceMessageToId", entry.getSourceMessageToId());
        if (entry.getCreatedAt() != null) {
            out.put("createdAt", entry.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (entry.getUpdatedAt() != null) {
            out.put("updatedAt", entry.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (entry.getRetiredAt() != null) {
            out.put("retiredAt", entry.getRetiredAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        return out;
    }

    private static String normalizeH5MemoryEntryFilter(String entryFilter) {
        String filter = entryFilter == null || entryFilter.isBlank()
                ? "all"
                : entryFilter.trim().toLowerCase(Locale.ROOT);
        if (!H5_MEMORY_ENTRY_FILTERS.contains(filter)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "entryFilter invalid");
        }
        return filter;
    }

    private static boolean matchesH5MemoryEntryFilter(AppConversationMemoryEntry entry, String filter) {
        if (!isH5PanelVisibleMemoryEntry(entry)) {
            return false;
        }
        return switch (filter) {
            case "enabled" -> entry.isEnabled() && !isH5ArchivedMemoryEntry(entry);
            case "disabled" -> isH5DisabledMemoryEntry(entry);
            case "archived" -> isH5ArchivedMemoryEntry(entry);
            default -> true;
        };
    }

    private static boolean isH5DisabledMemoryEntry(AppConversationMemoryEntry entry) {
        return entry != null
                && !entry.isEnabled()
                && isH5PanelVisibleMemoryEntry(entry)
                && !isH5ArchivedMemoryEntry(entry);
    }

    private static boolean isH5PanelVisibleMemoryEntry(AppConversationMemoryEntry entry) {
        if (entry == null || entry.isManualDeleted()) {
            return false;
        }
        String reason = nullToEmpty(entry.getRetiredReason()).trim();
        if ("USER_DELETED".equalsIgnoreCase(reason) || "HISTORY_CHANGED".equalsIgnoreCase(reason)) {
            return false;
        }
        return entry.getDeletedAt() == null || isH5ArchivedMemoryEntry(entry);
    }

    private static boolean isH5ArchivedMemoryEntry(AppConversationMemoryEntry entry) {
        if (entry == null) {
            return false;
        }
        String reason = nullToEmpty(entry.getRetiredReason()).trim();
        return !entry.isManualDeleted()
                && !reason.isEmpty()
                && !"USER_DELETED".equalsIgnoreCase(reason)
                && !"HISTORY_CHANGED".equalsIgnoreCase(reason);
    }

    private static int h5MemoryEntryGroup(AppConversationMemoryEntry entry) {
        if (isH5ArchivedMemoryEntry(entry)) {
            return 2;
        }
        return entry != null && entry.isEnabled() ? 0 : 1;
    }

    private void syncWorldbookKeepingFailureState(long conversationId, Long branchId) {
        try {
            if (hasBranch(branchId)) {
                worldbookSyncService.syncWorldbook(conversationId, branchId);
            } else {
                worldbookSyncService.syncWorldbook(conversationId);
            }
        } catch (RuntimeException ignored) {
            // syncWorldbook persists FAILED state first; the panel will show that status.
        }
    }

    private void refreshSummaryPreview(long conversationId, Long branchId) {
        List<AppConversationMemoryEntry> enabledEntries = hasBranch(branchId)
                ? entryMapper.listEnabledByConversationBranchId(conversationId, branchId)
                : entryMapper.listEnabledByConversationId(conversationId);
        int factsCount = enabledEntries == null ? 0 : enabledEntries.size();
        String preview = buildEnabledSummaryPreview(enabledEntries);
        if (hasBranch(branchId)) {
            memoryMapper.upsertRollupForBranch(conversationId, branchId, preview, factsCount);
        } else {
            memoryMapper.upsertRollup(conversationId, preview, factsCount);
        }
    }

    private static String buildEnabledSummaryPreview(List<AppConversationMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder preview = new StringBuilder();
        for (AppConversationMemoryEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }
            String content = nullToEmpty(entry.getContent()).replaceAll("\\s+", " ").trim();
            if (content.isBlank()) {
                continue;
            }
            if (!preview.isEmpty()) {
                preview.append("；");
            }
            int remaining = 420 - preview.length();
            if (remaining <= 0) {
                break;
            }
            preview.append(content, 0, Math.min(content.length(), remaining));
            if (preview.length() >= 420) {
                break;
            }
        }
        return preview.toString();
    }

    private boolean isBlockedByManualSuppression(
            AppConversationMemoryEntry candidate,
            ExtractedMemoryEntry extracted,
            List<AppConversationMemoryEntry> existingEntries
    ) {
        if (candidate == null || existingEntries == null || existingEntries.isEmpty()) {
            return false;
        }
        String candidateKey = nullToEmpty(candidate.getEntryKey()).trim();
        String candidateContent = memoryFingerprint(candidate.getContent());
        for (AppConversationMemoryEntry existing : existingEntries) {
            if (existing == null || (!existing.isManualDeleted() && !existing.isManualDisabled())) {
                continue;
            }
            String existingKey = nullToEmpty(existing.getEntryKey()).trim();
            if (!candidateKey.isBlank() && candidateKey.equalsIgnoreCase(existingKey)) {
                return true;
            }
            String suppressedContent = memoryFingerprint(existing.getContent());
            if (!candidateContent.isBlank() && candidateContent.equals(suppressedContent)) {
                return true;
            }
            if (declaresReplacement(extracted, existingKey)) {
                continue;
            }
            if (sameMemoryType(candidate, existing) && isSemanticallySimilar(candidate, existing)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSemanticallySimilar(
            AppConversationMemoryEntry candidate,
            AppConversationMemoryEntry suppressed
    ) {
        Set<String> candidateKeywords = normalizedKeywordSet(candidate.getKeywordsJson());
        Set<String> suppressedKeywords = normalizedKeywordSet(suppressed.getKeywordsJson());
        int keywordOverlap = 0;
        for (String keyword : candidateKeywords) {
            if (suppressedKeywords.contains(keyword)) {
                keywordOverlap++;
            }
        }
        double contentSimilarity = bigramDiceSimilarity(
                memoryFingerprint(candidate.getContent()),
                memoryFingerprint(suppressed.getContent())
        );
        return keywordOverlap >= 2
                || (keywordOverlap >= 1 && contentSimilarity >= 0.45d)
                || contentSimilarity >= 0.72d;
    }

    private Set<String> normalizedKeywordSet(String keywordsJson) {
        Set<String> out = new LinkedHashSet<>();
        for (String keyword : memorySanitizer.readKeywords(keywordsJson)) {
            String normalized = memoryFingerprint(keyword);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static boolean declaresReplacement(ExtractedMemoryEntry extracted, String existingKey) {
        if (extracted == null || existingKey == null || existingKey.isBlank() || extracted.replaces() == null) {
            return false;
        }
        return extracted.replaces().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .anyMatch(existingKey::equalsIgnoreCase);
    }

    private static boolean sameMemoryType(
            AppConversationMemoryEntry left,
            AppConversationMemoryEntry right
    ) {
        return nullToEmpty(left.getMemoryType()).equalsIgnoreCase(nullToEmpty(right.getMemoryType()));
    }

    private static double bigramDiceSimilarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0d;
        }
        if (left.equals(right)) {
            return 1.0d;
        }
        Set<String> leftBigrams = characterBigrams(left);
        Set<String> rightBigrams = characterBigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0d;
        }
        int overlap = 0;
        for (String bigram : leftBigrams) {
            if (rightBigrams.contains(bigram)) {
                overlap++;
            }
        }
        return (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }

    private static Set<String> characterBigrams(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value.length() == 1) {
            out.add(value);
            return out;
        }
        for (int i = 0; i < value.length() - 1; i++) {
            out.add(value.substring(i, i + 2));
        }
        return out;
    }

    private static String memoryFingerprint(String value) {
        return nullToEmpty(value)
                .toLowerCase()
                .replaceAll("[\\p{P}\\p{S}\\s]+", "")
                .trim();
    }

    private ConversationMemoryRefreshResult toRefreshResult(long conversationId, Long branchId) {
        AppConversationMemory m = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        if (m == null) {
            return new ConversationMemoryRefreshResult(
                    conversationId,
                    "",
                    0,
                    0,
                    0,
                    "",
                    ConversationMemoryWorldbookSyncService.SYNC_SKIPPED,
                    "",
                    null
            );
        }
        return new ConversationMemoryRefreshResult(
                conversationId,
                nullToEmpty(m.getSummaryPreview()),
                m.getFactsCount(),
                m.getEntryCount(),
                m.getEnabledEntryCount(),
                nullToEmpty(m.getMemoryWorldName()),
                nullToEmpty(m.getSyncStatus()),
                nullToEmpty(m.getSyncError()),
                m.getUpdatedAt()
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasBranch(Long branchId) {
        return branchId != null && branchId > 0;
    }

    private int countVisibleMemorySources(long conversationId, Long branchId) {
        return hasBranch(branchId)
                ? messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)
                : messageMapper.countMemorySourceByConversationId(conversationId);
    }

    private static String memoryKey(long conversationId, Long branchId) {
        return conversationId + ":" + (hasBranch(branchId) ? branchId : 0L);
    }

    private ReentrantLock operationLock(String key) {
        return operationLocks[Math.floorMod(key.hashCode(), operationLocks.length)];
    }

    private void releaseOperationLock(ReentrantLock lock) {
        lock.unlock();
    }

    private static ReentrantLock[] createOperationLocks(int size) {
        ReentrantLock[] locks = new ReentrantLock[Math.max(16, size)];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }
}
