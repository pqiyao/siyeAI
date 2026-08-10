package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.model.ConversationMemoryRefreshMetric;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Applies an already-produced memory result in one short, revision-fenced transaction. */
@Service
public class ConversationMemoryApplyService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryApplyService.class);

    public enum ApplyStatus {
        APPLIED,
        STALE
    }

    private final AppConversationBranchMapper branchMapper;
    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final AppMessageMapper messageMapper;
    private final ConversationMemorySanitizer sanitizer;
    private final ConversationMemoryCapacityService capacityService;
    private final ConversationMemoryRefreshMetricsService metricsService;

    public ConversationMemoryApplyService(
            AppConversationBranchMapper branchMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemorySanitizer sanitizer,
            ConversationMemoryCapacityService capacityService
    ) {
        this(
                branchMapper,
                memoryMapper,
                entryMapper,
                messageMapper,
                sanitizer,
                capacityService,
                null
        );
    }

    @Autowired
    public ConversationMemoryApplyService(
            AppConversationBranchMapper branchMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemorySanitizer sanitizer,
            ConversationMemoryCapacityService capacityService,
            ConversationMemoryRefreshMetricsService metricsService
    ) {
        this.branchMapper = branchMapper;
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.messageMapper = messageMapper;
        this.sanitizer = sanitizer;
        this.capacityService = capacityService;
        this.metricsService = metricsService;
    }

    @Transactional
    public ApplyStatus applyStructured(
            ConversationMemoryRefreshSnapshot snapshot,
            StructuredMemoryExtraction extraction
    ) {
        LockedState locked = lockAndValidate(snapshot);
        if (locked == null) {
            return ApplyStatus.STALE;
        }

        List<AppConversationMemoryEntry> existingEntries =
                entryMapper.listAllIncludingDeletedForUpdate(snapshot.conversationId(), snapshot.branchId());
        int modelOutputEntryCount = Math.max(
                extraction.modelOutputEntryCount(),
                extraction.entries() == null ? 0 : extraction.entries().size()
        );
        int acceptedEntryCount = 0;
        int rejectedEntryCount = Math.max(0, extraction.parseRejectedEntryCount());
        int conflictCount = 0;
        Set<String> disabledKeys = new LinkedHashSet<>();
        List<String> requestedDisableKeys = sanitizer.sanitizeDisableKeys(extraction.disableEntryKeys());
        for (String key : requestedDisableKeys) {
            if (isPinnedKey(key, existingEntries)) {
                conflictCount++;
                continue;
            }
            if (disabledKeys.add(key)) {
                entryMapper.disableByKeyForBranch(snapshot.conversationId(), snapshot.branchId(), key);
            }
        }

        if (extraction.entries() != null) {
            for (ExtractedMemoryEntry extracted : extraction.entries()) {
                for (String key : sanitizer.sanitizeReplaceKeys(extracted)) {
                    if (isPinnedKey(key, existingEntries)) {
                        conflictCount++;
                        continue;
                    }
                    if (disabledKeys.add(key)) {
                        entryMapper.disableByKeyForBranch(snapshot.conversationId(), snapshot.branchId(), key);
                    }
                }
                AppConversationMemoryEntry entity = sanitizer.toEntity(
                        snapshot.conversationId(),
                        extracted,
                        snapshot.firstMessageId(),
                        snapshot.lastMessageId(),
                        snapshot.messageIds()
                );
                if (entity == null) {
                    rejectedEntryCount++;
                    continue;
                }
                entity.setBranchId(snapshot.branchId());
                if (isBlockedByManualSuppression(entity, extracted, existingEntries)) {
                    rejectedEntryCount++;
                    conflictCount++;
                    continue;
                }
                entryMapper.upsert(entity);
                acceptedEntryCount++;
            }
        }

        capacityService.enforceAfterRefresh(snapshot.conversationId(), snapshot.branchId());
        List<AppConversationMemoryEntry> enabledEntries =
                entryMapper.listEnabledByConversationBranchId(snapshot.conversationId(), snapshot.branchId());
        int enabledCount = enabledEntries == null ? 0 : enabledEntries.size();
        int entryCount = entryMapper.countAllByConversationBranchId(snapshot.conversationId(), snapshot.branchId());
        boolean hasManualSuppression = existingEntries != null && existingEntries.stream()
                .anyMatch(entry -> entry != null
                        && (entry.isManualPinned() || entry.isManualDeleted() || entry.isManualDisabled()));
        String summary = hasManualSuppression
                ? buildEnabledSummaryPreview(enabledEntries)
                : nullToEmpty(extraction.summaryPreview());
        int updated = memoryMapper.updateRefreshStateWithRevision(
                snapshot.conversationId(),
                snapshot.branchId(),
                summary,
                enabledCount,
                entryCount,
                enabledCount,
                snapshot.lastMessageId(),
                snapshot.visibleMessageCount(),
                syncStatus(enabledCount),
                locked.memory().getManualRevision(),
                locked.memory().getMemoryRevision(),
                snapshot.sourceRevision()
        );
        if (updated != 1) {
            throw new IllegalStateException("memory revision changed while branch row was locked");
        }
        recordStructuredMetric(
                snapshot,
                extraction,
                modelOutputEntryCount,
                acceptedEntryCount,
                rejectedEntryCount,
                conflictCount,
                requestedDisableKeys.size()
        );
        return ApplyStatus.APPLIED;
    }

    private void recordStructuredMetric(
            ConversationMemoryRefreshSnapshot snapshot,
            StructuredMemoryExtraction extraction,
            int modelOutputEntryCount,
            int acceptedEntryCount,
            int rejectedEntryCount,
            int conflictCount,
            int disableRequestedCount
    ) {
        if (metricsService == null) {
            return;
        }
        ConversationMemoryRefreshMetric metric = new ConversationMemoryRefreshMetric(
                extraction.requestId(),
                snapshot.conversationId(),
                snapshot.branchId(),
                snapshot.refreshMode(),
                snapshot.extractionMode(),
                "STRUCTURED_APPLIED",
                snapshot.messages().size(),
                snapshot.visibleMessageCount(),
                snapshot.existingEntries().size(),
                modelOutputEntryCount,
                acceptedEntryCount,
                rejectedEntryCount,
                conflictCount,
                disableRequestedCount,
                extraction.durationMs()
        );
        try {
            metricsService.record(metric);
        } catch (RuntimeException ex) {
            log.warn(
                    "memory refresh metric write failed conversationId={} branchId={}: {}",
                    snapshot.conversationId(),
                    snapshot.branchId(),
                    ex.getMessage()
            );
        }
    }

    @Transactional
    public ApplyStatus applyRollup(
            ConversationMemoryRefreshSnapshot snapshot,
            String summaryPreview,
            int factsCount
    ) {
        return applyRollupInternal(snapshot, summaryPreview, factsCount, null, 0L, null);
    }

    @Transactional
    public ApplyStatus applyRollup(
            ConversationMemoryRefreshSnapshot snapshot,
            String summaryPreview,
            int factsCount,
            String requestId,
            long durationMs,
            String outcome
    ) {
        return applyRollupInternal(
                snapshot,
                summaryPreview,
                factsCount,
                requestId,
                durationMs,
                outcome
        );
    }

    private ApplyStatus applyRollupInternal(
            ConversationMemoryRefreshSnapshot snapshot,
            String summaryPreview,
            int factsCount,
            String requestId,
            long durationMs,
            String outcome
    ) {
        LockedState locked = lockAndValidate(snapshot);
        if (locked == null) {
            return ApplyStatus.STALE;
        }
        int entryCount = entryMapper.countAllByConversationBranchId(snapshot.conversationId(), snapshot.branchId());
        int enabledCount = entryMapper.countEnabledByConversationBranchId(snapshot.conversationId(), snapshot.branchId());
        String currentSyncStatus = locked.memory().getSyncStatus();
        String rollupSyncStatus = currentSyncStatus == null || currentSyncStatus.isBlank()
                ? syncStatus(enabledCount)
                : currentSyncStatus;
        int updated = memoryMapper.updateRefreshStateWithRevision(
                snapshot.conversationId(),
                snapshot.branchId(),
                summaryPreview,
                Math.max(0, factsCount),
                entryCount,
                enabledCount,
                snapshot.lastMessageId(),
                snapshot.visibleMessageCount(),
                rollupSyncStatus,
                locked.memory().getManualRevision(),
                locked.memory().getMemoryRevision(),
                snapshot.sourceRevision()
        );
        if (updated != 1) {
            throw new IllegalStateException("memory revision changed while branch row was locked");
        }
        if (outcome != null && !outcome.isBlank()) {
            recordRollupMetric(snapshot, requestId, durationMs, outcome);
        }
        return ApplyStatus.APPLIED;
    }

    private void recordRollupMetric(
            ConversationMemoryRefreshSnapshot snapshot,
            String requestId,
            long durationMs,
            String outcome
    ) {
        if (metricsService == null) {
            return;
        }
        ConversationMemoryRefreshMetric metric = new ConversationMemoryRefreshMetric(
                requestId,
                snapshot.conversationId(),
                snapshot.branchId(),
                snapshot.refreshMode(),
                snapshot.extractionMode(),
                outcome,
                snapshot.messages().size(),
                snapshot.visibleMessageCount(),
                snapshot.existingEntries().size(),
                0,
                0,
                0,
                0,
                0,
                Math.max(0L, durationMs)
        );
        try {
            metricsService.record(metric);
        } catch (RuntimeException ex) {
            log.warn(
                    "memory refresh metric write failed conversationId={} branchId={}: {}",
                    snapshot.conversationId(),
                    snapshot.branchId(),
                    ex.getMessage()
            );
        }
    }

    @Transactional
    public void setMemoryEntryEnabled(long conversationId, long branchId, long entryId, boolean enabled) {
        LockedState locked = lockCurrent(conversationId, branchId);
        capacityService.setManualEnabledWithCapacity(entryId, conversationId, branchId, enabled);
        updateManualState(locked.memory());
    }

    @Transactional
    public void deleteMemoryEntry(long conversationId, long branchId, long entryId) {
        LockedState locked = lockCurrent(conversationId, branchId);
        int deleted = entryMapper.softDeleteManualById(entryId, conversationId, branchId);
        if (deleted != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
        }
        updateManualState(locked.memory());
    }

    @Transactional
    public long saveManualMemoryEntry(
            long conversationId,
            long branchId,
            Long entryId,
            String memoryType,
            String title,
            String content,
            List<String> keywords,
            List<String> secondaryKeywords,
            int priority,
            boolean constantInjection,
            boolean manualPinned
    ) {
        LockedState locked = lockCurrent(conversationId, branchId);
        String entryKey = "manual_" + UUID.randomUUID().toString().replace("-", "");
        if (entryId != null && entryId > 0) {
            AppConversationMemoryEntry existing = entryMapper.findByIdForConversationBranch(
                    entryId, conversationId, branchId);
            if (existing == null || existing.getDeletedAt() != null || existing.getRetiredAt() != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在或已归档");
            }
            entryKey = existing.getEntryKey();
        }

        AppConversationMemoryEntry entry;
        try {
            entry = sanitizer.toManualEntity(
                    conversationId, branchId, entryKey, memoryType, title, content, keywords,
                    secondaryKeywords, priority, constantInjection, manualPinned);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        }

        if (entryId != null && entryId > 0) {
            entry.setId(entryId);
            if (entryMapper.updateManualById(entry) != 1) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆内容已变化，请刷新后重试");
            }
        } else {
            entryMapper.insertManual(entry);
        }
        capacityService.enforceAfterRefresh(conversationId, branchId);
        updateManualState(locked.memory());
        return entry.getId();
    }

    @Transactional
    public boolean invalidateAfterHistoryChange(long conversationId, long branchId, int minimumVisibleMessages) {
        LockedState locked = lockCurrent(conversationId, branchId);
        entryMapper.softDeleteGeneratedByConversationBranchId(conversationId, branchId);
        List<AppConversationMemoryEntry> enabledEntries =
                entryMapper.listEnabledByConversationBranchId(conversationId, branchId);
        int enabledCount = enabledEntries == null ? 0 : enabledEntries.size();
        int entryCount = entryMapper.countAllByConversationBranchId(conversationId, branchId);
        int updated = memoryMapper.updateAfterHistoryInvalidation(
                conversationId,
                branchId,
                buildEnabledSummaryPreview(enabledEntries),
                enabledCount,
                entryCount,
                enabledCount,
                syncStatus(enabledCount),
                locked.memory().getMemoryRevision()
        );
        if (updated != 1) {
            throw new IllegalStateException("memory revision changed while branch row was locked");
        }
        return minimumVisibleMessages <= 1 || countVisibleSources(conversationId, branchId) >= minimumVisibleMessages;
    }

    private LockedState lockAndValidate(ConversationMemoryRefreshSnapshot snapshot) {
        LockedState locked = lockCurrent(snapshot.conversationId(), snapshot.branchId());
        if (locked.branch().getMemorySourceRevision() != snapshot.sourceRevision()
                || locked.memory().getManualRevision() != snapshot.manualRevision()
                || locked.memory().getMemoryRevision() != snapshot.baseMemoryRevision()) {
            return null;
        }
        return locked;
    }

    private LockedState lockCurrent(long conversationId, long branchId) {
        AppConversationBranch branch = branchMapper.findByIdForConversationForUpdate(conversationId, branchId);
        if (branch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation branch not found");
        }
        memoryMapper.ensureForBranch(conversationId, branchId);
        AppConversationMemory memory = memoryMapper.findByConversationBranchIdForUpdate(conversationId, branchId);
        if (memory == null) {
            throw new IllegalStateException("conversation memory state was not created");
        }
        return new LockedState(branch, memory);
    }

    private void updateManualState(AppConversationMemory memory) {
        long conversationId = memory.getConversationId();
        long branchId = memory.getBranchId();
        List<AppConversationMemoryEntry> enabledEntries =
                entryMapper.listEnabledByConversationBranchId(conversationId, branchId);
        int enabledCount = enabledEntries == null ? 0 : enabledEntries.size();
        int entryCount = entryMapper.countAllByConversationBranchId(conversationId, branchId);
        int updated = memoryMapper.updateAfterManualMutation(
                conversationId,
                branchId,
                buildEnabledSummaryPreview(enabledEntries),
                enabledCount,
                entryCount,
                enabledCount,
                syncStatus(enabledCount),
                memory.getManualRevision(),
                memory.getMemoryRevision()
        );
        if (updated != 1) {
            throw new IllegalStateException("memory revision changed while branch row was locked");
        }
    }

    private int countVisibleSources(long conversationId, long branchId) {
        return messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId);
    }

    private static String syncStatus(int enabledCount) {
        return enabledCount > 0 ? "PENDING" : ConversationMemoryWorldbookSyncService.SYNC_SKIPPED;
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
        String candidateContent = fingerprint(candidate.getContent());
        for (AppConversationMemoryEntry existing : existingEntries) {
            if (existing == null
                    || (!existing.isManualPinned() && !existing.isManualDeleted() && !existing.isManualDisabled())) {
                continue;
            }
            String existingKey = nullToEmpty(existing.getEntryKey()).trim();
            if (!candidateKey.isBlank() && candidateKey.equalsIgnoreCase(existingKey)) {
                return true;
            }
            if (!candidateContent.isBlank() && candidateContent.equals(fingerprint(existing.getContent()))) {
                return true;
            }
            if (existing.isManualPinned()) {
                if (declaresReplacement(extracted, existingKey)
                        || (sameType(candidate, existing) && sharesKeywordDomain(candidate, existing))) {
                    return true;
                }
                continue;
            }
            if (!declaresReplacement(extracted, existingKey)
                    && sameType(candidate, existing)
                    && isSemanticallySimilar(candidate, existing)) {
                return true;
            }
        }
        return false;
    }

    private boolean sharesKeywordDomain(
            AppConversationMemoryEntry candidate,
            AppConversationMemoryEntry protectedEntry
    ) {
        Set<String> candidateKeywords = normalizedKeywords(candidate.getKeywordsJson());
        Set<String> protectedKeywords = normalizedKeywords(protectedEntry.getKeywordsJson());
        return candidateKeywords.stream().anyMatch(protectedKeywords::contains)
                || isSemanticallySimilar(candidate, protectedEntry);
    }

    private static boolean isPinnedKey(String key, List<AppConversationMemoryEntry> existingEntries) {
        if (key == null || key.isBlank() || existingEntries == null) {
            return false;
        }
        return existingEntries.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(entry -> entry.isManualPinned()
                        && key.equalsIgnoreCase(nullToEmpty(entry.getEntryKey()).trim()));
    }

    private boolean isSemanticallySimilar(AppConversationMemoryEntry candidate, AppConversationMemoryEntry suppressed) {
        Set<String> leftKeywords = normalizedKeywords(candidate.getKeywordsJson());
        Set<String> rightKeywords = normalizedKeywords(suppressed.getKeywordsJson());
        int overlap = 0;
        for (String keyword : leftKeywords) {
            if (rightKeywords.contains(keyword)) {
                overlap++;
            }
        }
        double contentSimilarity = bigramDice(fingerprint(candidate.getContent()), fingerprint(suppressed.getContent()));
        return overlap >= 2 || (overlap >= 1 && contentSimilarity >= 0.45d) || contentSimilarity >= 0.72d;
    }

    private Set<String> normalizedKeywords(String json) {
        Set<String> out = new LinkedHashSet<>();
        for (String keyword : sanitizer.readKeywords(json)) {
            String normalized = fingerprint(keyword);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static boolean declaresReplacement(ExtractedMemoryEntry extracted, String key) {
        return extracted != null && key != null && !key.isBlank() && extracted.replaces() != null
                && extracted.replaces().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .anyMatch(key::equalsIgnoreCase);
    }

    private static boolean sameType(AppConversationMemoryEntry left, AppConversationMemoryEntry right) {
        return nullToEmpty(left.getMemoryType()).equalsIgnoreCase(nullToEmpty(right.getMemoryType()));
    }

    private static double bigramDice(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0d;
        }
        if (left.equals(right)) {
            return 1.0d;
        }
        Set<String> leftBigrams = bigrams(left);
        Set<String> rightBigrams = bigrams(right);
        int overlap = 0;
        for (String bigram : leftBigrams) {
            if (rightBigrams.contains(bigram)) {
                overlap++;
            }
        }
        return leftBigrams.isEmpty() || rightBigrams.isEmpty()
                ? 0.0d
                : (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value.length() == 1) {
            out.add(value);
        } else {
            for (int i = 0; i < value.length() - 1; i++) {
                out.add(value.substring(i, i + 2));
            }
        }
        return out;
    }

    private static String fingerprint(String value) {
        return nullToEmpty(value).toLowerCase().replaceAll("[\\p{P}\\p{S}\\s]+", "").trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record LockedState(AppConversationBranch branch, AppConversationMemory memory) {
    }
}
