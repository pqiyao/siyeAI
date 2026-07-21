package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppConversationMemoryMapper {

    AppConversationMemory findByConversationId(@Param("conversationId") long conversationId);

    void upsertTouch(@Param("conversationId") long conversationId);

    void upsertRollup(
            @Param("conversationId") long conversationId,
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

    void updateSyncStatus(
            @Param("conversationId") long conversationId,
            @Param("memoryWorldName") String memoryWorldName,
            @Param("entryCount") int entryCount,
            @Param("enabledEntryCount") int enabledEntryCount,
            @Param("syncStatus") String syncStatus,
            @Param("syncError") String syncError
    );

    void deleteByConversationId(@Param("conversationId") long conversationId);
}
