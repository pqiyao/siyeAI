package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppUserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppUserMessageMapper {
    int insert(AppUserMessage row);

    List<AppUserMessage> listByUserId(@Param("userId") long userId, @Param("limit") int limit);
}
