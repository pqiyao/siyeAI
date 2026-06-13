package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminNoticeReadMapper {

    List<Long> listReadNoticeIds(@Param("adminUsername") String adminUsername, @Param("noticeIds") List<Long> noticeIds);

    void markRead(@Param("adminUsername") String adminUsername, @Param("noticeId") long noticeId);
}
