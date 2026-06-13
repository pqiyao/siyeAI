package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMessageMapper {

    void insert(AppMessage row);

    void incrementTotalMessageCounter();

    void updateStatusAndContent(@Param("id") long id,
                                @Param("status") String status,
                                @Param("content") String content,
                                @Param("errorCode") String errorCode,
                                @Param("traceId") String traceId);

    List<AppMessage> listByConversation(@Param("conversationId") long conversationId, @Param("limit") int limit);

    List<AppMessage> listByConversationAsc(@Param("conversationId") long conversationId, @Param("limit") int limit);

    List<AppMessage> listRecentByConversationAsc(@Param("conversationId") long conversationId, @Param("limit") int limit);

    List<AppMessage> listBeforeConversationAsc(@Param("conversationId") long conversationId,
                                               @Param("beforeId") long beforeId,
                                               @Param("limit") int limit);

    AppMessage findById(@Param("id") long id);

    void updateVariantMeta(@Param("id") long id,
                           @Param("stMessageRef") String stMessageRef,
                           @Param("swipeIndex") Integer swipeIndex,
                           @Param("traceId") String traceId);

    void updateContinuationMeta(@Param("id") long id,
                                @Param("messageKind") String messageKind,
                                @Param("continueFromMessageId") Long continueFromMessageId,
                                @Param("traceId") String traceId);

    Integer findMaxSwipeIndex(@Param("stMessageRef") String stMessageRef);

    List<AppMessage> listByStMessageRef(@Param("stMessageRef") String stMessageRef);

    AppMessage findByStMessageRefAndSwipeIndex(@Param("stMessageRef") String stMessageRef, @Param("swipeIndex") int swipeIndex);

    int countActiveByConversationId(@Param("conversationId") long conversationId);

    int countMemorySourceByConversationId(@Param("conversationId") long conversationId);

    Long findLatestMemorySourceMessageId(@Param("conversationId") long conversationId);

    void deleteByConversationId(@Param("conversationId") long conversationId);

    void deleteById(@Param("id") long id);

    void softDeleteByConversationId(@Param("conversationId") long conversationId, @Param("traceId") String traceId);

    void softDeleteBranchFromId(@Param("conversationId") long conversationId,
                                @Param("fromId") long fromId,
                                @Param("includeFromId") boolean includeFromId,
                                @Param("traceId") String traceId);
}

