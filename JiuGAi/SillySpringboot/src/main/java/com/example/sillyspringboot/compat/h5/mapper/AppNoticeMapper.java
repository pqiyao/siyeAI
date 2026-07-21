package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppNoticeMapper {
    List<AppNotice> listLatest(@Param("limit") int limit);

    long countAdminList(@Param("title") String title);

    List<AppNotice> listAdminPage(@Param("title") String title, @Param("offset") int offset, @Param("limit") int limit);

    AppNotice findById(@Param("id") long id);

    int insert(AppNotice row);

    int updateById(AppNotice row);

    int deleteById(@Param("id") long id);

    List<AppNotice> listTopForAdmin(@Param("limit") int limit);
}
