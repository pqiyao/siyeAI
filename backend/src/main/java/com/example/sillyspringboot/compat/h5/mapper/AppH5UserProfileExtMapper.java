package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppH5UserProfileExtMapper {

    AppH5UserProfileExt findByUserId(long userId);

    void upsert(AppH5UserProfileExt row);

    int upsertCharacterCreateAllowedForUsers(@Param("userIds") List<Long> userIds, @Param("allowed") int allowed);
}
