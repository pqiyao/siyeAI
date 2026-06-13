package com.example.sillyspringboot.humanchat.mapper;

import com.example.sillyspringboot.humanchat.entity.HumanChatDeliveryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface HumanChatDeliveryLogMapper {

    int insert(HumanChatDeliveryLog log);

    long countAdminDeliveryLogs(
            @Param("keyword") String keyword,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("targetUserId") Long targetUserId,
            @Param("messageId") Long messageId
    );

    List<Map<String, Object>> listAdminDeliveryLogs(
            @Param("keyword") String keyword,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("targetUserId") Long targetUserId,
            @Param("messageId") Long messageId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findAdminDeliveryLog(@Param("id") long id);
}
