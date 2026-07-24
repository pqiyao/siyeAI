package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ConversationMemoryCapacityService {

    public static final String RETIRED_DUPLICATE = "DUPLICATE";
    public static final String RETIRED_CAPACITY = "CAPACITY";
    public static final String RETIRED_SUPERSEDED = "SUPERSEDED";

    private static final Comparator<AppConversationMemoryEntry> BEST_FIRST =
            Comparator.<AppConversationMemoryEntry>comparingInt(entry -> entry.isManualPinned() ? 1 : 0).reversed()
                    .thenComparing(Comparator.comparingInt(ConversationMemoryCapacityService::typeWeight).reversed())
                    .thenComparing(Comparator.comparingInt(AppConversationMemoryEntry::getPriority).reversed())
                    .thenComparing(Comparator.comparingDouble(
                            ConversationMemoryCapacityService::confidenceValue
                    ).reversed())
                    .thenComparing(Comparator.comparingLong(
                            ConversationMemoryCapacityService::sourceMessageToIdValue
                    ).reversed())
                    .thenComparing(Comparator.comparing(
                            ConversationMemoryCapacityService::updatedAtValue
                    ).reversed())
                    .thenComparing(Comparator.comparingLong(
                            ConversationMemoryCapacityService::idValue
                    ).reversed());

    private static final Comparator<AppConversationMemoryEntry> WORST_FIRST = BEST_FIRST.reversed();

    private final AppConversationMemoryEntryMapper entryMapper;
    private final MemoryLlmProperties properties;

    public ConversationMemoryCapacityService(
            AppConversationMemoryEntryMapper entryMapper,
            MemoryLlmProperties properties
    ) {
        this.entryMapper = entryMapper;
        this.properties = properties;
    }

    @Transactional
    public CapacityResult enforceAfterRefresh(long conversationId, long branchId) {
        validateScope(conversationId, branchId);
        return enforceLocked(conversationId, branchId);
    }

    @Transactional
    public CapacityResult setManualEnabledWithCapacity(
            long entryId,
            long conversationId,
            long branchId,
            boolean enabled
    ) {
        validateScope(conversationId, branchId);
        if (entryId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "记忆条目 ID 不正确");
        }

        List<AppConversationMemoryEntry> locked = entryMapper.listCapacityEntriesForUpdate(conversationId, branchId);
        AppConversationMemoryEntry target = locked.stream()
                .filter(entry -> entry.getId() != null && entry.getId() == entryId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在"));
        if (target.isManualDeleted() || isUnrestorableHistoryEntry(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记忆条目不存在");
        }

        int preRetired = 0;
        if (enabled && !isActive(target)) {
            int activeCount = (int) locked.stream().filter(ConversationMemoryCapacityService::isActive).count();
            int slotsRequired = Math.max(0, activeCount + 1 - maxEnabledEntries());
            if (slotsRequired > 0) {
                List<AppConversationMemoryEntry> evictionCandidates = locked.stream()
                        .filter(ConversationMemoryCapacityService::isActive)
                        .filter(ConversationMemoryCapacityService::isAutomaticallyRetirable)
                        .filter(entry -> entry.getId() == null || entry.getId() != entryId)
                        .sorted(WORST_FIRST)
                        .toList();
                if (evictionCandidates.size() < slotsRequired) {
                    throw capacityConflict();
                }
                for (int i = 0; i < slotsRequired; i++) {
                    retire(conversationId, branchId, evictionCandidates.get(i), RETIRED_CAPACITY);
                    preRetired++;
                }
            }
        }

        int updated = entryMapper.setManualEnabledById(
                entryId,
                conversationId,
                branchId,
                enabled,
                !enabled
        );
        if (updated != 1) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆状态已变化，请刷新后重试");
        }

        CapacityResult result = enforceLocked(conversationId, branchId);
        if (preRetired == 0) {
            return result;
        }
        return new CapacityResult(
                result.managedEntryCount(),
                result.enabledCount(),
                result.constantCount(),
                result.archivedCount(),
                result.duplicateArchivedCount(),
                result.capacityArchivedCount() + preRetired,
                result.supersededArchivedCount(),
                result.physicallyDeletedCount()
        );
    }

    private CapacityResult enforceLocked(long conversationId, long branchId) {
        List<AppConversationMemoryEntry> locked = entryMapper.listCapacityEntriesForUpdate(conversationId, branchId);
        int supersededArchived = retireSystemDisabled(conversationId, branchId, locked);
        if (supersededArchived > 0) {
            locked = entryMapper.listCapacityEntriesForUpdate(conversationId, branchId);
        }
        List<AppConversationMemoryEntry> active = new ArrayList<>(locked.stream()
                .filter(ConversationMemoryCapacityService::isActive)
                .toList());

        int duplicateArchived = retireDuplicates(conversationId, branchId, active);
        int capacityArchived = retireCapacityOverflow(conversationId, branchId, active);
        enforceConstantLimit(conversationId, branchId, active);
        int physicallyDeleted = trimArchivedEntries(conversationId, branchId);

        List<AppConversationMemoryEntry> finalRows = entryMapper.listCapacityEntriesForUpdate(conversationId, branchId);
        int enabledCount = (int) finalRows.stream().filter(ConversationMemoryCapacityService::isActive).count();
        int constantCount = (int) finalRows.stream()
                .filter(ConversationMemoryCapacityService::isActive)
                .filter(AppConversationMemoryEntry::isConstantInjection)
                .count();
        int archivedCount = (int) finalRows.stream()
                .filter(ConversationMemoryCapacityService::isAutomaticArchive)
                .count();
        int managedEntryCount = (int) finalRows.stream()
                .filter(entry -> !entry.isManualDeleted())
                .filter(entry -> entry.getDeletedAt() == null || isAutomaticArchive(entry))
                .count();

        return new CapacityResult(
                managedEntryCount,
                enabledCount,
                constantCount,
                archivedCount,
                duplicateArchived,
                capacityArchived,
                supersededArchived,
                physicallyDeleted
        );
    }

    private int retireSystemDisabled(
            long conversationId,
            long branchId,
            List<AppConversationMemoryEntry> entries
    ) {
        int retired = 0;
        for (AppConversationMemoryEntry entry : entries) {
            if (!isSystemDisabled(entry)) {
                continue;
            }
            retire(conversationId, branchId, entry, RETIRED_SUPERSEDED);
            retired++;
        }
        return retired;
    }

    private int retireDuplicates(
            long conversationId,
            long branchId,
            List<AppConversationMemoryEntry> active
    ) {
        List<AppConversationMemoryEntry> ranked = active.stream().sorted(BEST_FIRST).toList();
        Set<Long> retiredIds = new HashSet<>();
        int retired = 0;

        for (int i = 0; i < ranked.size(); i++) {
            AppConversationMemoryEntry winner = ranked.get(i);
            if (isMarked(retiredIds, winner)) {
                continue;
            }
            for (int j = i + 1; j < ranked.size(); j++) {
                AppConversationMemoryEntry candidate = ranked.get(j);
                if (isMarked(retiredIds, candidate)
                        || !sameType(winner, candidate)
                        || !isHighlySimilar(winner.getContent(), candidate.getContent())
                        || !isAutomaticallyRetirable(candidate)) {
                    continue;
                }
                retire(conversationId, branchId, candidate, RETIRED_DUPLICATE);
                retiredIds.add(candidate.getId());
                retired++;
            }
        }

        if (!retiredIds.isEmpty()) {
            active.removeIf(entry -> isMarked(retiredIds, entry));
        }
        return retired;
    }

    private int retireCapacityOverflow(
            long conversationId,
            long branchId,
            List<AppConversationMemoryEntry> active
    ) {
        int overflow = Math.max(0, active.size() - maxEnabledEntries());
        if (overflow == 0) {
            return 0;
        }

        List<AppConversationMemoryEntry> candidates = active.stream()
                .filter(ConversationMemoryCapacityService::isAutomaticallyRetirable)
                .sorted(WORST_FIRST)
                .toList();
        if (candidates.size() < overflow) {
            throw capacityConflict();
        }

        Set<Long> retiredIds = new HashSet<>();
        for (int i = 0; i < overflow; i++) {
            AppConversationMemoryEntry candidate = candidates.get(i);
            retire(conversationId, branchId, candidate, RETIRED_CAPACITY);
            retiredIds.add(candidate.getId());
        }
        active.removeIf(entry -> isMarked(retiredIds, entry));
        return overflow;
    }

    private void enforceConstantLimit(
            long conversationId,
            long branchId,
            List<AppConversationMemoryEntry> active
    ) {
        List<AppConversationMemoryEntry> constants = active.stream()
                .filter(AppConversationMemoryEntry::isConstantInjection)
                .sorted(BEST_FIRST)
                .toList();
        int maxConstants = maxConstantEntries();
        for (int i = maxConstants; i < constants.size(); i++) {
            AppConversationMemoryEntry entry = constants.get(i);
            int updated = entryMapper.setConstantInjectionById(
                    requireId(entry),
                    conversationId,
                    branchId,
                    false
            );
            if (updated != 1) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆常驻状态已变化，请稍后重试");
            }
            entry.setConstantInjection(false);
        }
    }

    private int trimArchivedEntries(long conversationId, long branchId) {
        List<AppConversationMemoryEntry> archived = entryMapper
                .listCapacityEntriesForUpdate(conversationId, branchId)
                .stream()
                .filter(ConversationMemoryCapacityService::isAutomaticArchive)
                .sorted(BEST_FIRST)
                .toList();
        int maxArchived = maxArchivedEntries();
        if (archived.size() <= maxArchived) {
            return 0;
        }

        List<Long> ids = archived.subList(maxArchived, archived.size()).stream()
                .map(ConversationMemoryCapacityService::requireId)
                .toList();
        int deleted = entryMapper.deleteAutomaticRetiredByIds(conversationId, branchId, ids);
        if (deleted != ids.size()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆归档状态已变化，请稍后重试");
        }
        return deleted;
    }

    private void retire(
            long conversationId,
            long branchId,
            AppConversationMemoryEntry entry,
            String reason
    ) {
        int updated = entryMapper.retireAutomaticById(
                requireId(entry),
                conversationId,
                branchId,
                reason
        );
        if (updated != 1) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "记忆容量状态已变化，请稍后重试");
        }
    }

    private static boolean isActive(AppConversationMemoryEntry entry) {
        return entry.isEnabled()
                && entry.getDeletedAt() == null
                && entry.getRetiredAt() == null
                && entry.getRetiredReason() == null;
    }

    private static boolean isAutomaticallyRetirable(AppConversationMemoryEntry entry) {
        return isActive(entry)
                && !entry.isManualPinned()
                && !entry.isManualDisabled()
                && !entry.isManualDeleted();
    }

    private static boolean isAutomaticArchive(AppConversationMemoryEntry entry) {
        return entry.getRetiredAt() != null
                && entry.getDeletedAt() != null
                && (RETIRED_CAPACITY.equals(entry.getRetiredReason())
                || RETIRED_DUPLICATE.equals(entry.getRetiredReason())
                || RETIRED_SUPERSEDED.equals(entry.getRetiredReason()))
                && !entry.isManualPinned()
                && !entry.isManualDisabled()
                && !entry.isManualDeleted();
    }

    private static boolean isUnrestorableHistoryEntry(AppConversationMemoryEntry entry) {
        return entry.getDeletedAt() != null
                && !(RETIRED_CAPACITY.equals(entry.getRetiredReason())
                || RETIRED_DUPLICATE.equals(entry.getRetiredReason())
                || RETIRED_SUPERSEDED.equals(entry.getRetiredReason()));
    }

    private static boolean isSystemDisabled(AppConversationMemoryEntry entry) {
        return !entry.isEnabled()
                && entry.getDeletedAt() == null
                && entry.getRetiredAt() == null
                && entry.getRetiredReason() == null
                && !entry.isManualPinned()
                && !entry.isManualDisabled()
                && !entry.isManualDeleted();
    }

    private static boolean sameType(AppConversationMemoryEntry left, AppConversationMemoryEntry right) {
        return normalizeType(left.getMemoryType()).equals(normalizeType(right.getMemoryType()));
    }

    static boolean isHighlySimilar(String left, String right) {
        String a = normalizeContent(left);
        String b = normalizeContent(right);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }

        int shorter = Math.min(a.length(), b.length());
        int longer = Math.max(a.length(), b.length());
        if (shorter < 8) {
            return false;
        }
        if ((a.contains(b) || b.contains(a)) && (double) shorter / longer >= 0.72d) {
            return true;
        }

        Set<String> leftBigrams = bigrams(a);
        Set<String> rightBigrams = bigrams(b);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return false;
        }
        int common = 0;
        for (String bigram : leftBigrams) {
            if (rightBigrams.contains(bigram)) {
                common++;
            }
        }
        double dice = (2.0d * common) / (leftBigrams.size() + rightBigrams.size());
        return dice >= 0.86d;
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < value.length() - 1; i++) {
            result.add(value.substring(i, i + 2));
        }
        return result;
    }

    private static String normalizeContent(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "")
                .trim();
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int typeWeight(AppConversationMemoryEntry entry) {
        return switch (normalizeType(entry.getMemoryType())) {
            case "boundary" -> 700;
            case "identity" -> 680;
            case "relationship" -> 640;
            case "setting" -> 560;
            case "promise" -> 500;
            case "preference" -> 440;
            case "event" -> 320;
            default -> 200;
        };
    }

    private static double confidenceValue(AppConversationMemoryEntry entry) {
        BigDecimal confidence = entry.getConfidence();
        return confidence == null ? 0.0d : confidence.doubleValue();
    }

    private static long sourceMessageToIdValue(AppConversationMemoryEntry entry) {
        return entry.getSourceMessageToId() == null ? 0L : entry.getSourceMessageToId();
    }

    private static LocalDateTime updatedAtValue(AppConversationMemoryEntry entry) {
        return entry.getUpdatedAt() == null ? LocalDateTime.MIN : entry.getUpdatedAt();
    }

    private static long idValue(AppConversationMemoryEntry entry) {
        return entry.getId() == null ? 0L : entry.getId();
    }

    private static long requireId(AppConversationMemoryEntry entry) {
        if (entry.getId() == null || entry.getId() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "记忆条目缺少有效 ID");
        }
        return entry.getId();
    }

    private static boolean isMarked(Set<Long> ids, AppConversationMemoryEntry entry) {
        return entry.getId() != null && ids.contains(entry.getId());
    }

    private int maxEnabledEntries() {
        return Math.max(1, properties.getMaxEnabledEntries());
    }

    private int maxConstantEntries() {
        return Math.max(0, Math.min(maxEnabledEntries(), properties.getMaxConstantEntries()));
    }

    private int maxArchivedEntries() {
        return Math.max(0, properties.getMaxArchivedEntries());
    }

    private static void validateScope(long conversationId, long branchId) {
        if (conversationId <= 0 || branchId < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "会话或剧情分支不正确");
        }
    }

    private static BusinessException capacityConflict() {
        return new BusinessException(
                ErrorCode.CONFLICT,
                "启用记忆已达到上限，且没有可自动归档的普通记忆"
        );
    }

    public record CapacityResult(
            int managedEntryCount,
            int enabledCount,
            int constantCount,
            int archivedCount,
            int duplicateArchivedCount,
            int capacityArchivedCount,
            int supersededArchivedCount,
            int physicallyDeletedCount
    ) {
    }
}
