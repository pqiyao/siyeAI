package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppConversationMemoryMapper {

    AppConversationMemory findByConversationId(@Param("conversationId") long conversationId);

    AppConversationMemory findByConversationBranchId(@Param("conversationId") long conversationId,
                                                     @Param("branchId") long branchId);

    AppConversationMemory findByConversationBranchIdForUpdate(@Param("conversationId") long conversationId,
                                                               @Param("branchId") long branchId);

    List<AppConversationMemory> listByConversationId(@Param("conversationId") long conversationId);

    List<AppConversationMemory> listWorldbookSyncRetryCandidates(
            @Param("retryCutoff") LocalDateTime retryCutoff,
            @Param("leaseCutoff") LocalDateTime leaseCutoff,
            @Param("limit") int limit
    );

    int tryClaimWorldbookSync(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("retryCutoff") LocalDateTime retryCutoff,
            @Param("leaseCutoff") LocalDateTime leaseCutoff
    );

    void upsertTouch(@Param("conversationId") long conversationId);

    void upsertTouchForBranch(@Param("conversationId") long conversationId,
                              @Param("branchId") long branchId);

    void ensureForBranch(@Param("conversationId") long conversationId,
                         @Param("branchId") long branchId);

    void upsertRollup(
            @Param("conversationId") long conversationId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount
    );

    void upsertRollupForBranch(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount
    );

    void upsertRefreshState(
            @Param("conversationId") long conversationId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount,
            @Param("memoryWorldName") String memoryWorldName,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("lastSourceMessageId") Long lastSourceMessageId,
            @Param("lastRefreshedMessageCount") int lastRefreshedMessageCount,
            @Param("syncStatus") String syncStatus,
            @Param("syncError") String syncError
    );

    void upsertRefreshStateForBranch(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount,
            @Param("memoryWorldName") String memoryWorldName,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("lastSourceMessageId") Long lastSourceMessageId,
            @Param("lastRefreshedMessageCount") int lastRefreshedMessageCount,
            @Param("syncStatus") String syncStatus,
            @Param("syncError") String syncError
    );

    void updateSyncStatusForBranch(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("memoryWorldName") String memoryWorldName,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("syncStatus") String syncStatus,
            @Param("syncError") String syncError
    );

    int updateWorldbookSyncStatusWithRevision(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("memoryWorldName") String memoryWorldName,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("syncStatus") String syncStatus,
            @Param("syncError") String syncError,
            @Param("expectedMemoryRevision") long expectedMemoryRevision
    );

    int tryAcquireManualRefresh(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("token") String token,
            @Param("cooldownCutoff") LocalDateTime cooldownCutoff,
            @Param("leaseCutoff") LocalDateTime leaseCutoff
    );

    int releaseManualRefresh(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("token") String token
    );

    int updateRefreshStateWithRevision(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("lastSourceMessageId") Long lastSourceMessageId,
            @Param("lastRefreshedMessageCount") int lastRefreshedMessageCount,
            @Param("syncStatus") String syncStatus,
            @Param("expectedManualRevision") long expectedManualRevision,
            @Param("expectedMemoryRevision") long expectedMemoryRevision,
            @Param("appliedSourceRevision") long appliedSourceRevision
    );

    int updateAfterManualMutation(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("syncStatus") String syncStatus,
            @Param("expectedManualRevision") long expectedManualRevision,
            @Param("expectedMemoryRevision") long expectedMemoryRevision
    );

    int updateAfterHistoryInvalidation(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("summaryPreview") String summaryPreview,
            @Param("factsCount") int factsCount,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("syncStatus") String syncStatus,
            @Param("expectedMemoryRevision") long expectedMemoryRevision
    );

    void deleteByConversationId(@Param("conversationId") long conversationId);
}
