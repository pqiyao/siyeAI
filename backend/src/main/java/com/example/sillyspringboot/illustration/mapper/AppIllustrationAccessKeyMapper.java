package com.example.sillyspringboot.illustration.mapper;

import com.example.sillyspringboot.illustration.entity.AppIllustrationAccessKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppIllustrationAccessKeyMapper {

    long countAdminList(@Param("keyword") String keyword, @Param("active") Boolean active);

    List<AppIllustrationAccessKey> listAdminPage(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    AppIllustrationAccessKey findById(@Param("id") long id);

    AppIllustrationAccessKey findByCode(@Param("accessCode") String accessCode);

    void insert(AppIllustrationAccessKey row);

    int disableById(@Param("id") long id);

    int hardDeleteById(@Param("id") long id);
}
