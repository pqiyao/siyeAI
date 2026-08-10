package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppUserChatPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserChatPreferenceMapper {
    Long lockUser(@Param("userId") long userId);
    AppUserChatPreference find(@Param("userId") long userId, @Param("characterId") long characterId);
    int countCharacterPreferences(@Param("userId") long userId);
    int updateIfRevision(@Param("row") AppUserChatPreference row, @Param("expectedRevision") int expectedRevision);
    int insert(AppUserChatPreference row);
    int deleteIfRevision(
            @Param("userId") long userId,
            @Param("characterId") long characterId,
            @Param("expectedRevision") int expectedRevision
    );
}
