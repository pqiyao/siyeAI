package com.example.sillyspringboot.auth.mapper;

import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserIdentityMapper {

    AppUserIdentity findByTypeAndKey(@Param("identityType") String identityType, @Param("identityKey") String identityKey);

    AppUserIdentity findByUserIdAndType(@Param("userId") long userId, @Param("identityType") String identityType);

    int insert(AppUserIdentity row);

    int updateById(AppUserIdentity row);
}
