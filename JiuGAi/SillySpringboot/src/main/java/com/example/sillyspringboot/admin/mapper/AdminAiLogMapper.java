package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Mapper
public interface AdminAiLogMapper {

    long countList(
            @Param("channel") String channel,
            @Param("status") String status,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("providerKey") String providerKey,
            @Param("model") String model,
            @Param("httpStatus") Integer httpStatus,
            @Param("startedAfter") LocalDateTime startedAfter,
            @Param("startedBefore") LocalDateTime startedBefore
    );

    List<Map<String, Object>> listPage(
            @Param("channel") String channel,
            @Param("status") String status,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("providerKey") String providerKey,
            @Param("model") String model,
            @Param("httpStatus") Integer httpStatus,
            @Param("startedAfter") LocalDateTime startedAfter,
            @Param("startedBefore") LocalDateTime startedBefore,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<Map<String, Object>> listAttemptsByTaskId(@Param("taskId") long taskId);

    long countStandaloneRequests(
            @Param("capability") String capability,
            @Param("status") String status,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("providerKey") String providerKey,
            @Param("model") String model,
            @Param("httpStatus") Integer httpStatus,
            @Param("startedAfter") LocalDateTime startedAfter,
            @Param("startedBefore") LocalDateTime startedBefore
    );

    List<Map<String, Object>> listStandaloneRequests(
            @Param("capability") String capability,
            @Param("status") String status,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("providerKey") String providerKey,
            @Param("model") String model,
            @Param("httpStatus") Integer httpStatus,
            @Param("startedAfter") LocalDateTime startedAfter,
            @Param("startedBefore") LocalDateTime startedBefore,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<Map<String, Object>> listStandaloneAttemptsByRequestId(@Param("requestId") String requestId);

}
