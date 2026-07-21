package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppConversationMapper {

    void insert(AppConversation conversation);

    AppConversation findById(@Param("conversationId") long conversationId);

    AppConversation findByIdForUser(@Param("conversationId") long conversationId, @Param("userId") long userId);

    List<AppConversation> listByUser(@Param("userId") long userId);

    List<ConversationInboxItemDto> listInboxByUser(@Param("userId") long userId, @Param("limit") int limit);

    List<ConversationInboxItemDto> listInboxByUserExcludeArchived(@Param("userId") long userId, @Param("limit") int limit);

    List<Long> listIdsByUserAndCharacter(@Param("userId") long userId, @Param("characterId") long characterId);

    void touchUpdatedAt(@Param("conversationId") long conversationId);

    void setTitleIfNull(@Param("conversationId") long conversationId, @Param("title") String title);

    void setTitleToCharacterNameIfNull(@Param("conversationId") long conversationId);

    int countActiveForUser(@Param("userId") long userId);

    void deleteByIdForUser(@Param("conversationId") long conversationId, @Param("userId") long userId);
}

