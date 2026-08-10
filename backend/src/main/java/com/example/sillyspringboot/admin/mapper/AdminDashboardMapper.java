package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Mapper
public interface AdminDashboardMapper {

    long totalCharacters();

    long systemCharacters();

    long userCharacters();

    long totalUsers();

    long totalConversations();

    long activeConversationsRecent(@Param("days") int days);

    long totalMessages();

    long totalTasks();

    long successTasks();

    long totalPaidOrders();

    long totalRevenueCents();

    List<Map<String, Object>> generationTrend(@Param("days") Integer days);

    List<Map<String, Object>> topActiveUsers(@Param("limit") int limit);

    List<Map<String, Object>> hotCharacters(@Param("limit") int limit);

    Map<String, Object> generationOpsSummary(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationLatencyTrend(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationProviderStats(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationModelStats(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationCharacterStats(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationErrorStats(@Param("startAt") LocalDateTime startAt);

    List<Map<String, Object>> generationRouteHealth(@Param("startAt") LocalDateTime startAt);
}
