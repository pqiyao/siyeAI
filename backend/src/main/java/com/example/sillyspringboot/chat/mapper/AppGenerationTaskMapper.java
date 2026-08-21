package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppGenerationTaskMapper {

    void insert(AppGenerationTask row);

    AppGenerationTask findByConversationAndClientMessageId(@Param("conversationId") long conversationId,
                                                           @Param("clientMessageId") String clientMessageId);

    AppGenerationTask findById(@Param("id") long id);

    int updateStatus(@Param("id") long id,
                     @Param("status") String status,
                     @Param("errorCode") String errorCode,
                     @Param("errorMessage") String errorMessage,
                     @Param("traceId") String traceId,
                     @Param("httpStatus") Integer httpStatus);

    int updateEffectiveContext(@Param("id") long id,
                               @Param("presetId") Long presetId,
                               @Param("maxContext") Integer maxContext,
                               @Param("maxTokens") Integer maxTokens,
                               @Param("provider") String provider,
                               @Param("apiSource") String apiSource);

    int countActiveByConversationId(@Param("conversationId") long conversationId);

    int markStaleActiveByConversationId(@Param("conversationId") long conversationId,
                                         @Param("cutoff") java.time.LocalDateTime cutoff,
                                         @Param("traceId") String traceId);

    List<AppGenerationTask> listStaleActiveBefore(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);

    List<AppGenerationTask> listStaleActiveByConversationId(@Param("conversationId") long conversationId,
                                                             @Param("cutoff") LocalDateTime cutoff);

    void deleteByConversationId(@Param("conversationId") long conversationId);

    void softDeleteByConversationId(@Param("conversationId") long conversationId);
}
