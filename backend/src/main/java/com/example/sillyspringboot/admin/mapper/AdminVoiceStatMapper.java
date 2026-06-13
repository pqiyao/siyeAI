package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminVoiceStatMapper {

    long countList(
            @Param("scope") String scope,
            @Param("status") String status,
            @Param("providerSource") String providerSource,
            @Param("modelName") String modelName,
            @Param("errorCode") String errorCode,
            @Param("cutoffAt") Timestamp cutoffAt
    );

    List<Map<String, Object>> listPage(
            @Param("scope") String scope,
            @Param("status") String status,
            @Param("providerSource") String providerSource,
            @Param("modelName") String modelName,
            @Param("errorCode") String errorCode,
            @Param("cutoffAt") Timestamp cutoffAt,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> summaryTotals(@Param("cutoffAt") Timestamp cutoffAt);

    List<Map<String, Object>> summaryByScope(@Param("cutoffAt") Timestamp cutoffAt);

    List<Map<String, Object>> topProviders(@Param("cutoffAt") Timestamp cutoffAt, @Param("limit") int limit);

    List<Map<String, Object>> topErrors(@Param("cutoffAt") Timestamp cutoffAt, @Param("limit") int limit);
}
