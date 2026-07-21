package com.example.sillyspringboot.auth.mapper;

import com.example.sillyspringboot.auth.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper {

    AppUser findByTelegramUserId(@Param("telegramUserId") long telegramUserId);

    AppUser findById(@Param("id") long id);

    int insert(AppUser user);

    int updateById(AppUser user);

    int updateLanguageById(@Param("id") long id, @Param("languageCode") String languageCode);

    int updateByTelegramUserId(AppUser user);
}
