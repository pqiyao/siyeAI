package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.AdminH5UserCleanupMapper;
import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminH5UserLifecycleService {

    private final AdminH5UserMapper adminH5UserMapper;
    private final AdminH5UserCleanupMapper cleanupMapper;
    private final ExternalCleanupTaskService externalCleanupTaskService;
    private final TransactionTemplate transactionTemplate;

    public AdminH5UserLifecycleService(
            AdminH5UserMapper adminH5UserMapper,
            AdminH5UserCleanupMapper cleanupMapper,
            ExternalCleanupTaskService externalCleanupTaskService,
            PlatformTransactionManager transactionManager
    ) {
        this.adminH5UserMapper = adminH5UserMapper;
        this.cleanupMapper = cleanupMapper;
        this.externalCleanupTaskService = externalCleanupTaskService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> deleteUsers(List<Long> userIds) {
        int requested = userIds == null ? 0 : userIds.size();
        int deleted = 0;
        int skipped = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Map<String, Object>> cleanupWarnings = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                skipped++;
                continue;
            }
            Map<String, Object> detail = adminH5UserMapper.findDetail(userId);
            if (detail == null || detail.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                List<String> cleanupTaskIds = transactionTemplate.execute(status -> deleteDatabaseRecords(userId));
                deleted++;
                cleanupExternalResources(userId, cleanupTaskIds, cleanupWarnings);
            } catch (RuntimeException ex) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", userId);
                item.put("reason", clip(rootMessage(ex), 180));
                failed.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", requested);
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("failedCount", failed.size());
        result.put("failed", failed);
        result.put("cleanupWarningCount", cleanupWarnings.size());
        result.put("cleanupWarnings", cleanupWarnings);
        return result;
    }

    public Map<String, Object> deleteUserById(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        List<String> cleanupTaskIds = transactionTemplate.execute(status -> deleteDatabaseRecords(userId));
        List<Map<String, Object>> cleanupWarnings = new ArrayList<>();
        cleanupExternalResources(userId, cleanupTaskIds, cleanupWarnings);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("cleanupWarningCount", cleanupWarnings.size());
        result.put("cleanupWarnings", cleanupWarnings);
        return result;
    }

    private List<String> deleteDatabaseRecords(long userId) {
        List<Map<String, Object>> stChats = cleanupMapper.listConversationStRefs(userId);
        List<Map<String, Object>> ownedCharacters = cleanupMapper.listOwnedCharacterCleanupRows(userId);
        List<String> ownedUploadPaths = cleanupMapper.listOwnedUploadRelativePaths(userId);
        List<String> cleanupTaskIds = externalCleanupTaskService.enqueueUserDeletionTasks(
                userId,
                stChats,
                ownedCharacters,
                ownedUploadPaths == null ? Set.of() : Set.copyOf(ownedUploadPaths)
        );

        cleanupMapper.deleteSupportTicketMessagesByUser(userId);
        cleanupMapper.deleteSupportTicketsByUser(userId);
        cleanupMapper.deleteUserMessages(userId);
        cleanupMapper.deleteUserNoticeReads(userId);
        cleanupMapper.deleteUserNoticeReadState(userId);
        cleanupMapper.deleteCharacterFavorites(userId);
        cleanupMapper.deleteCharacterFavoritesForOwnedCharacters(userId);
        cleanupMapper.deleteCharacterVotes(userId);
        cleanupMapper.deleteCharacterVotesForOwnedCharacters(userId);
        cleanupMapper.deleteWalletLedger(userId);
        cleanupMapper.deletePaymentOrders(userId);
        cleanupMapper.deleteGenerationTasksByUser(userId);
        cleanupMapper.deleteMessagesByUser(userId);
        cleanupMapper.deleteConversationBindingsByUser(userId);
        cleanupMapper.deleteConversationArchivesByUser(userId);
        cleanupMapper.deleteConversationMemoryEntriesByUser(userId);
        cleanupMapper.deleteConversationMemoriesByUser(userId);
        cleanupMapper.deleteConversationIdempotencyByUser(userId);
        cleanupMapper.deleteConversationBranchesByUser(userId);
        cleanupMapper.deleteConversationsByUser(userId);
        cleanupMapper.deletePasswordResetTokensByUser(userId);
        cleanupMapper.deleteUserSessions(userId);
        cleanupMapper.deleteH5UserAiProvider(userId);
        cleanupMapper.deleteEntitlementAuditLogsByUser(userId);
        cleanupMapper.anonymizeH5SecurityEventsByUser(userId);
        cleanupMapper.deleteVisitorDevicesByUser(userId);
        cleanupMapper.deleteClientUidBindings(userId);
        cleanupMapper.deleteUserIdentities(userId);
        cleanupMapper.deleteH5Profile(userId);
        cleanupMapper.deleteH5ProfileExt(userId);
        cleanupMapper.deleteOwnedUploadAssetsByUser(userId);
        cleanupMapper.deleteCharacterReviewLogsByOwner(userId);
        cleanupMapper.deleteLorebookEntriesForOwnedCharacters(userId);
        cleanupMapper.deleteChatPreferencesRelatedToUser(userId);
        cleanupMapper.deleteOwnedCharacters(userId);
        cleanupMapper.deleteAppUser(userId);
        return cleanupTaskIds == null ? List.of() : List.copyOf(cleanupTaskIds);
    }

    private void cleanupExternalResources(
            long userId,
            List<String> cleanupTaskIds,
            List<Map<String, Object>> cleanupWarnings
    ) {
        if (cleanupTaskIds == null || cleanupTaskIds.isEmpty()) {
            return;
        }
        List<ExternalCleanupTaskService.CleanupAttempt> attempts;
        try {
            attempts = externalCleanupTaskService.processImmediately(cleanupTaskIds);
        } catch (RuntimeException ex) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", userId);
            item.put("status", "UNAVAILABLE");
            item.put("reason", clip("cleanup tasks remain queued: " + rootMessage(ex), 180));
            cleanupWarnings.add(item);
            return;
        }
        if (attempts == null) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", userId);
            item.put("status", "UNAVAILABLE");
            item.put("reason", "cleanup tasks remain queued: no immediate result");
            cleanupWarnings.add(item);
            return;
        }
        for (ExternalCleanupTaskService.CleanupAttempt attempt : attempts) {
            if (attempt.succeeded()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", userId);
            item.put("taskId", attempt.taskId());
            item.put("resourceType", attempt.resourceType());
            item.put("status", attempt.status());
            item.put("reason", clip(attempt.message(), 180));
            cleanupWarnings.add(item);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String clip(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
