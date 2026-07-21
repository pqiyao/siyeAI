package com.example.sillyspringboot.character.mapper;

import com.example.sillyspringboot.character.entity.AppCharacter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.sillyspringboot.admin.dto.OwnerPrivateCardCount;

import java.util.List;

@Mapper
public interface AppCharacterMapper {
    AppCharacter findById(@Param("id") long id);

    AppCharacter findByStAvatarUrl(@Param("stAvatarUrl") String stAvatarUrl);

    AppCharacter findSystemByStAvatarUrlAny(@Param("stAvatarUrl") String stAvatarUrl);

    AppCharacter findActivePrivateByStAvatarUrl(@Param("stAvatarUrl") String stAvatarUrl);

    AppCharacter findDeletedPrivateByStAvatarUrl(@Param("stAvatarUrl") String stAvatarUrl);

    AppCharacter findPrivateByStAvatarUrlAny(@Param("stAvatarUrl") String stAvatarUrl);

    AppCharacter findSyncShadowPublicByStAvatarUrl(@Param("stAvatarUrl") String stAvatarUrl);

    void insert(AppCharacter row);

    void insertFull(AppCharacter row);

    void updateById(AppCharacter row);

    List<AppCharacter> listLatest(@Param("limit") int limit);

    List<AppCharacter> listPublicDiscover(@Param("limit") int limit);

    List<String> listAllActiveTagsJson();

    long countAdminList(
            @Param("name") String name,
            @Param("ownerUserId") Long ownerUserId,
            @Param("systemOnly") Boolean systemOnly,
            @Param("userOnly") Boolean userOnly,
            @Param("reviewStatus") String reviewStatus);

    List<AppCharacter> listAdminPage(
            @Param("name") String name,
            @Param("ownerUserId") Long ownerUserId,
            @Param("systemOnly") Boolean systemOnly,
            @Param("userOnly") Boolean userOnly,
            @Param("reviewStatus") String reviewStatus,
            @Param("offset") int offset,
            @Param("limit") int limit);

    void softDeleteById(@Param("id") long id);

    List<OwnerPrivateCardCount> countPrivateByOwnerGrouped();

    void updateReviewById(
            @Param("id") long id,
            @Param("reviewStatus") String reviewStatus,
            @Param("reviewReason") String reviewReason,
            @Param("reviewedBy") String reviewedBy);
}
