package com.example.sillyspringboot.illustration.mapper;

import com.example.sillyspringboot.illustration.entity.AppIllustrationWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppIllustrationWorkMapper {

    long countAdminList(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("contentLevel") String contentLevel,
            @Param("status") String status,
            @Param("source") String source
    );

    List<AppIllustrationWork> listAdminPage(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("contentLevel") String contentLevel,
            @Param("status") String status,
            @Param("source") String source,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPublicList(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("tag") String tag,
            @Param("allowR18") boolean allowR18
    );

    List<AppIllustrationWork> listPublicPage(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("tag") String tag,
            @Param("allowR18") boolean allowR18,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    AppIllustrationWork findById(@Param("id") long id);

    AppIllustrationWork findBySlug(@Param("slug") String slug);

    AppIllustrationWork findActiveBySlug(@Param("slug") String slug, @Param("allowR18") boolean allowR18);

    void insert(AppIllustrationWork row);

    void updateById(AppIllustrationWork row);

    int hardDeleteById(@Param("id") long id);

    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("auditNote") String auditNote,
            @Param("reviewed") boolean reviewed
    );
}
