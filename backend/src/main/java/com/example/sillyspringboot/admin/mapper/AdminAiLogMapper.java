package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminAiLogMapper {

    long countList(
            @Param("channel") String channel,
            @Param("success") Boolean success,
            @Param("traceId") String traceId
    );

    List<Map<String, Object>> listPage(
            @Param("channel") String channel,
            @Param("success") Boolean success,
            @Param("traceId") String traceId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int snapshotBeforeDays(@Param("beforeDays") int beforeDays);

    int deleteBeforeDays(@Param("beforeDays") int beforeDays);
}
