package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppGenerationTaskMapper {

    void insert(AppGenerationTask row);

    AppGenerationTask findByConversationAndClientMessageId(@Param("conversationId") long conversationId,
                                                           @Param("clientMessageId") String clientMessageId);

    void updateStatus(@Param("id") long id,
                      @Param("status") String status,
                      @Param("errorCode") String errorCode,
                      @Param("errorMessage") String errorMessage,
                      @Param("traceId") String traceId,
                      @Param("httpStatus") Integer httpStatus);

    int countActiveByConversationId(@Param("conversationId") long conversationId);

    int markStaleActiveByConversationId(@Param("conversationId") long conversationId,
                                        @Param("cutoff") java.time.LocalDateTime cutoff,
                                        @Param("traceId") String traceId);

    void deleteByConversationId(@Param("conversationId") long conversationId);

    void softDeleteByConversationId(@Param("conversationId") long conversationId);
}
