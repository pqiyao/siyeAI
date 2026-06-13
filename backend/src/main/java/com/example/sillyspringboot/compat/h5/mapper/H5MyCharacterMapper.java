package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.H5MyCharacter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface H5MyCharacterMapper {
    List<H5MyCharacter> listMine(@Param("userId") long userId, @Param("sort") String sort, @Param("limit") int limit);

    /** 按主键加载（不校验 owner；聊天侧再结合 owner 判断是否用完整编辑字段） */
    H5MyCharacter findById(@Param("id") long id);

    H5MyCharacter findEditor(@Param("id") long id, @Param("userId") long userId);

    H5MyCharacter findByStAvatarUrlAndOwner(@Param("stAvatarUrl") String stAvatarUrl, @Param("userId") long userId);

    H5MyCharacter findByStAvatarUrlAndOwnerAny(@Param("stAvatarUrl") String stAvatarUrl, @Param("userId") long userId);

    void insertMine(H5MyCharacter row);

    void updateMine(H5MyCharacter row);

    void softDelete(@Param("id") long id, @Param("userId") long userId);

    int countActiveByStAvatarUrlExcludingId(@Param("stAvatarUrl") String stAvatarUrl, @Param("id") long id);

    void softDeletePublicSyncShadowByStAvatarUrl(@Param("stAvatarUrl") String stAvatarUrl);

    void archiveStAvatarUrlAfterStDelete(@Param("id") long id, @Param("userId") long userId);

    int countMineActive(@Param("userId") long userId);
}

