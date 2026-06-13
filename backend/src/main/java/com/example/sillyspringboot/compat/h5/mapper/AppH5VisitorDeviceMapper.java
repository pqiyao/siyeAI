package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppH5VisitorDeviceMapper {
    AppH5VisitorDevice findByDeviceToken(@Param("deviceToken") String deviceToken);

    void insert(AppH5VisitorDevice row);

    void touch(
            @Param("id") long id,
            @Param("latestClientUid") String latestClientUid,
            @Param("latestUserId") Long latestUserId,
            @Param("latestIp") String latestIp,
            @Param("uaHash") String uaHash,
            @Param("userAgent") String userAgent
    );

    void incrementAnonymousChatAttemptCount(@Param("id") long id);

    void incrementAnonymousConversationCreateCount(@Param("id") long id);

    void incrementAnonymousCharacterCreateCount(@Param("id") long id);
}
