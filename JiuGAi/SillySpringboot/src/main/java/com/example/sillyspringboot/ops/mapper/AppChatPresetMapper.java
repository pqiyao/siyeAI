package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppChatPreset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppChatPresetMapper {

    AppChatPreset findById(@Param("id") long id);

    AppChatPreset findEnabledPublicById(@Param("id") long id);

    List<AppChatPreset> listPublicEnabled();

    List<AppChatPreset> listAdmin(
            @Param("keyword") String keyword,
            @Param("apiType") String apiType,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdmin(
            @Param("keyword") String keyword,
            @Param("apiType") String apiType,
            @Param("enabled") Boolean enabled
    );

    void upsertPlatformPreset(AppChatPreset preset);

    int updateStatus(@Param("id") long id, @Param("enabled") boolean enabled);

    int updateSortOrder(@Param("id") long id, @Param("sortOrder") int sortOrder);

    int deleteById(@Param("id") long id);
}
