package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppH5ProfileMapper {
    AppH5Profile findByUserId(@Param("userId") long userId);

    void upsert(
            @Param("userId") long userId,
            @Param("displayName") String displayName,
            @Param("persona") String persona,
            @Param("stDisplayName") String stDisplayName
    );
}

