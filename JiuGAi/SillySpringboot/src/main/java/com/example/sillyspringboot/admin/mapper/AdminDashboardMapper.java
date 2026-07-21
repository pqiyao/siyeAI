package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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
}
