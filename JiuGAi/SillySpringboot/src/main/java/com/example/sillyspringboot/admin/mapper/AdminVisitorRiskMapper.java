package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminVisitorRiskMapper {
    Map<String, Object> overview();
    long count(@Param("keyword") String keyword, @Param("riskOnly") boolean riskOnly);
    List<Map<String, Object>> list(@Param("keyword") String keyword, @Param("riskOnly") boolean riskOnly,
                                   @Param("offset") int offset, @Param("limit") int limit);
    List<Map<String, Object>> events(@Param("deviceId") long deviceId, @Param("limit") int limit);
    long countDevicesByIds(@Param("ids") List<Long> ids);
    int deleteEventsByDeviceIds(@Param("ids") List<Long> ids);
    int deleteDevicesByIds(@Param("ids") List<Long> ids);
}
