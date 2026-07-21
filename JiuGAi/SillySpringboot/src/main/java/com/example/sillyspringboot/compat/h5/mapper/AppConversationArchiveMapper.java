package com.example.sillyspringboot.compat.h5.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppConversationArchiveMapper {
    void upsert(@Param("userId") long userId, @Param("conversationId") long conversationId);

    int existsByUserAndConversation(@Param("userId") long userId, @Param("conversationId") long conversationId);

    void deleteByUserAndConversation(@Param("userId") long userId, @Param("conversationId") long conversationId);
}

