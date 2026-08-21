package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMessageMapper {

    void insert(AppMessage row);

    void incrementTotalMessageCounter();

    void incrementSuccessfulAiResponseCounter(@Param("taskId") long taskId);

    int claimSuccessfulAiHelpCounter(@Param("requestId") String requestId);

    void incrementSuccessfulAiHelpCounter();

    void updateStatusAndContent(@Param("id") long id,
                                @Param("status") String status,
                                @Param("content") String content,
                                @Param("errorCode") String errorCode,
                                @Param("traceId") String traceId);

    List<AppMessage> listByConversation(@Param("conversationId") long conversationId, @Param("limit") int limit);

    List<AppMessage> listByConversationBranch(@Param("conversationId") long conversationId,
                                              @Param("branchId") long branchId,
                                              @Param("limit") int limit);

    List<AppMessage> listByConversationAsc(@Param("conversationId") long conversationId, @Param("limit") int limit);


    List<AppMessage> listByConversationBranchAsc(@Param("conversationId") long conversationId,
                                                 @Param("branchId") long branchId,
                                                 @Param("limit") int limit);

    List<AppMessage> listRecentByConversationAsc(@Param("conversationId") long conversationId, @Param("limit") int limit);

    List<AppMessage> listRecentByConversationBranchAsc(@Param("conversationId") long conversationId,
                                                       @Param("branchId") long branchId,
                                                       @Param("limit") int limit);

    List<AppMessage> listRecentMemorySourceByConversationAsc(@Param("conversationId") long conversationId,
                                                             @Param("limit") int limit);

    List<AppMessage> listRecentMemorySourceByConversationBranchAsc(@Param("conversationId") long conversationId,
                                                                   @Param("branchId") long branchId,
                                                                   @Param("limit") int limit);

    List<AppMessage> listBeforeConversationAsc(@Param("conversationId") long conversationId,
                                               @Param("beforeId") long beforeId,
                                               @Param("limit") int limit);

    List<AppMessage> listBeforeConversationBranchAsc(@Param("conversationId") long conversationId,
                                                     @Param("branchId") long branchId,
                                                     @Param("beforeId") long beforeId,
                                                     @Param("limit") int limit);

    AppMessage findById(@Param("id") long id);

    Long findLatestIdByConversationBranch(@Param("conversationId") long conversationId,
                                          @Param("branchId") long branchId);

    void updateVariantMeta(@Param("id") long id,
                           @Param("stMessageRef") String stMessageRef,
                           @Param("swipeIndex") Integer swipeIndex,
                           @Param("traceId") String traceId);

    void updateContinuationMeta(@Param("id") long id,
                                @Param("messageKind") String messageKind,
                                @Param("continueFromMessageId") Long continueFromMessageId,
                                @Param("traceId") String traceId);


    int updateSpeakerSnapshot(@Param("id") long id,
                              @Param("speakerMemberId") Long speakerMemberId,
                              @Param("speakerNameSnapshot") String speakerNameSnapshot,
                              @Param("speakerAvatarSnapshot") String speakerAvatarSnapshot);

    Integer findMaxSwipeIndex(@Param("stMessageRef") String stMessageRef);

    Integer findMaxSwipeIndexAndBranch(@Param("stMessageRef") String stMessageRef,
                                       @Param("branchId") long branchId);

    List<AppMessage> listByStMessageRef(@Param("stMessageRef") String stMessageRef);

    List<AppMessage> listByStMessageRefAndBranch(@Param("stMessageRef") String stMessageRef,
                                                 @Param("branchId") long branchId);

    AppMessage findByStMessageRefAndSwipeIndex(@Param("stMessageRef") String stMessageRef, @Param("swipeIndex") int swipeIndex);

    AppMessage findByStMessageRefAndSwipeIndexAndBranch(@Param("stMessageRef") String stMessageRef,
                                                        @Param("swipeIndex") int swipeIndex,
                                                        @Param("branchId") long branchId);

    int countActiveByConversationId(@Param("conversationId") long conversationId);

    int markStaleActiveByConversationId(@Param("conversationId") long conversationId,
                                          @Param("cutoff") java.time.LocalDateTime cutoff,
                                          @Param("traceId") String traceId);

    int markStaleActiveByTask(@Param("conversationId") long conversationId,
                              @Param("clientMessageId") String clientMessageId,
                              @Param("traceId") String traceId);

    int countMemorySourceByConversationId(@Param("conversationId") long conversationId);

    int countMemorySourceByConversationBranchId(@Param("conversationId") long conversationId,
                                                @Param("branchId") long branchId);

    Long findLatestMemorySourceMessageId(@Param("conversationId") long conversationId);

    Long findLatestMemorySourceMessageIdByBranch(@Param("conversationId") long conversationId,
                                                 @Param("branchId") long branchId);

    int countVisibleByConversationBranch(@Param("conversationId") long conversationId,
                                         @Param("branchId") long branchId);

    AppMessage findLatestVisibleByConversationBranch(@Param("conversationId") long conversationId,
                                                     @Param("branchId") long branchId);

    AppMessage findOpeningByConversationBranch(@Param("conversationId") long conversationId,
                                               @Param("branchId") long branchId);

    void deleteByConversationId(@Param("conversationId") long conversationId);

    void deleteById(@Param("id") long id);

    void softDeleteByConversationId(@Param("conversationId") long conversationId, @Param("traceId") String traceId);

    void softDeleteBranchFromId(@Param("conversationId") long conversationId,
                                @Param("fromId") long fromId,
                                @Param("includeFromId") boolean includeFromId,
                                @Param("traceId") String traceId);

    void softDeleteBranchFromIdInBranch(@Param("conversationId") long conversationId,
                                        @Param("branchId") long branchId,
                                        @Param("fromId") long fromId,
                                        @Param("includeFromId") boolean includeFromId,
                                        @Param("traceId") String traceId);
}

