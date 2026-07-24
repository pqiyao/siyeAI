package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppInboxAd;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppInboxAdMapper {
    AppInboxAd findActive();

    List<AppInboxAd> listActive(@Param("limit") int limit);

    long countUnreadForUser(@Param("userId") long userId);

    int markAllReadForUser(@Param("userId") long userId);

    long countAdminList(@Param("title") String title, @Param("enabled") Boolean enabled);

    List<AppInboxAd> listAdminPage(
            @Param("title") String title,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    AppInboxAd findById(@Param("id") long id);

    int insert(AppInboxAd row);

    int updateById(AppInboxAd row);

    int deleteById(@Param("id") long id);
}
