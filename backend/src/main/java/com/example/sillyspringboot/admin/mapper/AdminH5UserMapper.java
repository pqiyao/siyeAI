package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminH5UserMapper {

    long countList(
            @Param("keyword") String keyword,
            @Param("vipType") Integer vipType,
            @Param("status") String status,
            @Param("needEdit") Integer needEdit
    );

    List<Map<String, Object>> listPage(
            @Param("keyword") String keyword,
            @Param("vipType") Integer vipType,
            @Param("status") String status,
            @Param("needEdit") Integer needEdit,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findDetail(@Param("userId") long userId);

    List<Map<String, Object>> listRecentConversationsByUser(
            @Param("userId") long userId,
            @Param("limit") int limit
    );
}
