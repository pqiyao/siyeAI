package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserTtsVoiceBindingMapper {

    AppUserTtsVoiceBinding find(
            @Param("userId") long userId,
            @Param("scopeType") String scopeType,
            @Param("characterId") long characterId,
            @Param("memberId") long memberId
    );

    int updateVoice(AppUserTtsVoiceBinding row);

    int insert(AppUserTtsVoiceBinding row);

    void deleteScope(
            @Param("userId") long userId,
            @Param("scopeType") String scopeType,
            @Param("characterId") long characterId,
            @Param("memberId") long memberId
    );

    void deleteByVoiceId(@Param("voiceId") long voiceId);

    void deleteMemberScope(
            @Param("userId") long userId,
            @Param("characterId") long characterId,
            @Param("memberId") long memberId
    );

    void deleteMemberScopes(
            @Param("userId") long userId,
            @Param("characterId") long characterId
    );

    void deleteCharacterScopes(
            @Param("userId") long userId,
            @Param("characterId") long characterId
    );
}
