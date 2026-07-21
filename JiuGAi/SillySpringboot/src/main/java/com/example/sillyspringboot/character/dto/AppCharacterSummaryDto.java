package com.example.sillyspringboot.character.dto;

import com.example.sillyspringboot.character.entity.AppCharacter;

public record AppCharacterSummaryDto(
        long characterId,
        String name,
        String stAvatarUrl,
        String description,
        String tagline,
        String bio,
        String avatarUrl,
        String coverUrl,
        String chatBackgroundUrl,
        String occupationLabel,
        String tagsJson,
        String gameplayType,
        Boolean vipOnly,
        Boolean unlockedDefault,
        Boolean clientVisible,
        Integer previewBlurVipLevel,
        int sortOrder,
        int likeCount,
        int dislikeCount,
        String creatorName,
        String creatorHandle,
        String tokenDisplay,
        String chatModesJson,
        boolean privateCard) {

    public static AppCharacterSummaryDto from(AppCharacter c) {
        if (c == null) {
            return null;
        }
        return new AppCharacterSummaryDto(
                c.getId(),
                c.getName(),
                c.getStAvatarUrl(),
                c.getDescription(),
                c.getTagline(),
                c.getBio(),
                c.getAvatarUrl(),
                c.getCoverUrl(),
                c.getChatBackgroundUrl(),
                c.getOccupationLabel(),
                c.getTagsJson(),
                c.getGameplayType(),
                c.getVipOnly(),
                c.getUnlockedDefault(),
                c.getClientVisible(),
                c.getPreviewBlurVipLevel(),
                c.getSortOrder() != null ? c.getSortOrder() : 0,
                c.getLikeCount() != null ? c.getLikeCount() : 0,
                c.getDislikeCount() != null ? c.getDislikeCount() : 0,
                c.getCreatorName(),
                c.getCreatorHandle(),
                c.getTokenDisplay(),
                c.getChatModesJson(),
                Boolean.TRUE.equals(c.getPrivateCard()));
    }
}
