package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.entity.AppAdminPermissionChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppAdminPermissionChangeLogMapper {

    int insert(AppAdminPermissionChangeLog row);

    long countList(
            @Param("targetType") String targetType,
            @Param("action") String action,
            @Param("operator") String operator,
            @Param("keyword") String keyword
    );

    List<Map<String, Object>> listPage(
            @Param("targetType") String targetType,
            @Param("action") String action,
            @Param("operator") String operator,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
