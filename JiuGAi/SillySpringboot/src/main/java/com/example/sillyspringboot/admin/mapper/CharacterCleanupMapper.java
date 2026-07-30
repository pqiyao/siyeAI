package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.model.CharacterUploadAssetRow;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CharacterCleanupMapper {

    List<AppCharacter> lockCharacters(@Param("characterIds") List<Long> characterIds);

    List<AppCharacterMember> listMemberMedia(@Param("characterIds") List<Long> characterIds);

    int countOtherCharacterStReferences(
            @Param("stAvatarUrl") String stAvatarUrl,
            @Param("excludedCharacterIds") List<Long> excludedCharacterIds
    );

    int countOtherBindingStReferences(
            @Param("stAvatarUrl") String stAvatarUrl,
            @Param("excludedCharacterIds") List<Long> excludedCharacterIds
    );

    int countOtherLocalAssetReferences(
            @Param("assetUrl") String assetUrl,
            @Param("excludedCharacterIds") List<Long> excludedCharacterIds
    );

    CharacterUploadAssetRow findUploadAsset(@Param("assetUrl") String assetUrl);

    int deleteUploadAsset(
            @Param("assetId") long assetId,
            @Param("ownerUserId") long ownerUserId,
            @Param("relativePath") String relativePath
    );

    int markBindingsCharacterDeleted(@Param("characterIds") List<Long> characterIds);

    int archiveConversations(@Param("characterIds") List<Long> characterIds);
}
