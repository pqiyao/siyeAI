package com.example.sillyspringboot.illustration.mapper;

import com.example.sillyspringboot.illustration.entity.AppIllustrationNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppIllustrationNoticeMapper {

    long countAdminList(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("enabled") Boolean enabled
    );

    List<AppIllustrationNotice> listAdminPage(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<AppIllustrationNotice> listPublic();

    AppIllustrationNotice findById(@Param("id") long id);

    void insert(AppIllustrationNotice row);

    void updateById(AppIllustrationNotice row);

    int hardDeleteById(@Param("id") long id);
}
