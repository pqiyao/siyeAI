package com.example.sillyspringboot.compat.h5.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AppH5SecurityEventMapper {

    void insert(
            @Param("deviceId") Long deviceId,
            @Param("eventType") String eventType,
            @Param("clientUid") String clientUid,
            @Param("userId") Long userId,
            @Param("ipAddress") String ipAddress,
            @Param("uaHash") String uaHash,
            @Param("endpointGroup") String endpointGroup,
            @Param("detail") String detail
    );

    int deleteOldestBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
