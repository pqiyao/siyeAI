package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppMessageSemanticAnnotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AppMessageSemanticAnnotationMapper {
    void upsertPending(@Param("messageId") long messageId,
                       @Param("contentHash") String contentHash,
                       @Param("schemaVersion") int schemaVersion,
                       @Param("classifierVersion") String classifierVersion);

    int markReadyIfHash(@Param("messageId") long messageId,
                        @Param("contentHash") String contentHash,
                        @Param("segmentsJson") String segmentsJson,
                        @Param("confidence") BigDecimal confidence);

    int markFailedIfHash(@Param("messageId") long messageId,
                         @Param("contentHash") String contentHash,
                         @Param("errorCode") String errorCode);

    AppMessageSemanticAnnotation findByMessageId(@Param("messageId") long messageId);

    List<AppMessageSemanticAnnotation> listReadyByMessageIds(@Param("messageIds") List<Long> messageIds);
}
