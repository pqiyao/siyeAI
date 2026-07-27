package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppChatPreset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppChatPresetMapper {

    AppChatPreset findPublicById(@Param("id") long id);

    AppChatPreset findEnabledPublicById(@Param("id") long id);

    AppChatPreset findEnabledAvailableById(@Param("id") long id, @Param("userId") long userId);

    AppChatPreset findPrivateByIdForOwner(@Param("id") long id, @Param("userId") long userId);

    List<AppChatPreset> listPublicEnabled();

    List<AppChatPreset> listPrivateByOwner(@Param("userId") long userId);

    Long lockOwnerUser(@Param("userId") long userId);

    long countPrivateByOwner(@Param("userId") long userId);

    void insertPrivate(AppChatPreset preset);

    int updatePrivate(@Param("id") long id,
                      @Param("userId") long userId,
                      @Param("name") String name,
                      @Param("description") String description,
                      @Param("bundleJson") String bundleJson,
                      @Param("enabled") boolean enabled);

    int deletePrivate(@Param("id") long id, @Param("userId") long userId);

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

    int markAllPlatformPresetsSourceUnavailable(@Param("apiType") String apiType);

    List<Long> listUnavailablePlatformPresetIds(@Param("apiType") String apiType);

    int updateStatus(@Param("id") long id, @Param("enabled") boolean enabled);

    int updateSortOrder(@Param("id") long id, @Param("sortOrder") int sortOrder);

    int deleteById(@Param("id") long id);
}
