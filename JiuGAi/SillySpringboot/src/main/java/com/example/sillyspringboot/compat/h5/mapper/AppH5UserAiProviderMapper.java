package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserAiProvider;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppH5UserAiProviderMapper {

    AppH5UserAiProvider findByUserId(long userId);

    void upsert(AppH5UserAiProvider row);
}
