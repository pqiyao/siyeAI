package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppEntitlementAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppEntitlementAuditLogMapper {

    void insert(AppEntitlementAuditLog row);

    long countList(
            @Param("scopeType") String scopeType,
            @Param("actionType") String actionType,
            @Param("keyword") String keyword,
            @Param("targetUserId") Long targetUserId
    );

    List<Map<String, Object>> listPage(
            @Param("scopeType") String scopeType,
            @Param("actionType") String actionType,
            @Param("keyword") String keyword,
            @Param("targetUserId") Long targetUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByIds(@Param("ids") List<Long> ids);
}
