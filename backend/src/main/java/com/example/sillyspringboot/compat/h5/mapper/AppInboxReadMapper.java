package com.example.sillyspringboot.compat.h5.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppInboxReadMapper {
    int ensureInboxReadState(@Param("userId") long userId);

    int claimNoticeBaselineInitialization(@Param("userId") long userId);

    int claimMessageBaselineInitialization(@Param("userId") long userId);

    long countUnreadNotices(@Param("userId") long userId);

    long countUnreadUserMessages(@Param("userId") long userId);

    int markNoticesRead(@Param("userId") long userId);

    int markNoticeRead(@Param("userId") long userId, @Param("noticeId") long noticeId);

    int markUserMessagesRead(@Param("userId") long userId);
}
