package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppConversationMemoryEntryMapper {

    List<AppConversationMemoryEntry> listEnabledByConversationId(@Param("conversationId") long conversationId);

    List<AppConversationMemoryEntry> listAllByConversationId(@Param("conversationId") long conversationId);

    void upsert(AppConversationMemoryEntry entry);

    void disableByKey(@Param("conversationId") long conversationId, @Param("entryKey") String entryKey);

    void disableById(@Param("id") long id);

    void softDeleteByConversationId(@Param("conversationId") long conversationId);

    int countAllByConversationId(@Param("conversationId") long conversationId);

    int countEnabledByConversationId(@Param("conversationId") long conversationId);
}
