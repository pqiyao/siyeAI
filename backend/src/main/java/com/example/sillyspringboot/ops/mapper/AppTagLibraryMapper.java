package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppTagLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppTagLibraryMapper {

    long countAdminList(@Param("keyword") String keyword, @Param("category") String category, @Param("enabled") Boolean enabled);

    List<AppTagLibrary> listAdminPage(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    AppTagLibrary findById(@Param("id") long id);

    AppTagLibrary findByCode(@Param("code") String code);

    List<AppTagLibrary> listEnabled();

    void insert(AppTagLibrary row);

    void updateById(AppTagLibrary row);

    void deleteById(@Param("id") long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
