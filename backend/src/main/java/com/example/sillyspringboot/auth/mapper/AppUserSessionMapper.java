package com.example.sillyspringboot.auth.mapper;

import com.example.sillyspringboot.auth.entity.AppUserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserSessionMapper {

    void insertSession(AppUserSession session);

    AppUserSession findActiveBySessionId(@Param("sessionId") String sessionId);

    int revokeActiveByUserId(@Param("userId") long userId);
}

