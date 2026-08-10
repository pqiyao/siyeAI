package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5ClientUid;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppH5ClientUidMapper {
    AppH5ClientUid findByClientUid(@Param("clientUid") String clientUid);

    /** 管理端统计：将 owner_user_id 展示为某个绑定的 clientUid */
    String findAnyClientUidByUserId(@Param("userId") long userId);

    void insert(AppH5ClientUid row);

    void updateUserIdByClientUid(@Param("clientUid") String clientUid, @Param("userId") long userId);
}

