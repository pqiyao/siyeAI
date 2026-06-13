package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationIdempotency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppConversationIdempotencyMapper {

    AppConversationIdempotency findByUserAndKey(@Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    void insert(AppConversationIdempotency row);

    void deleteByConversationForUser(@Param("conversationId") long conversationId, @Param("userId") long userId);
}

