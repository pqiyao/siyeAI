package com.example.sillyspringboot.compat.h5.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppCharacterSocialMapper {
    boolean isFavorite(@Param("userId") long userId, @Param("characterId") long characterId);

    void insertFavorite(@Param("userId") long userId, @Param("characterId") long characterId);

    void deleteFavorite(@Param("userId") long userId, @Param("characterId") long characterId);

    void deleteFavoritesBatch(@Param("userId") long userId, @Param("characterIds") List<Long> characterIds);

    String findVote(@Param("userId") long userId, @Param("characterId") long characterId);

    void upsertVote(@Param("userId") long userId, @Param("characterId") long characterId, @Param("vote") String vote);

    void deleteVote(@Param("userId") long userId, @Param("characterId") long characterId);

    long countFavoritesByCharacter(@Param("characterId") long characterId);

    long countVoteByCharacter(@Param("characterId") long characterId, @Param("vote") String vote);

    List<Map<String, Object>> listInteractionCounts(@Param("characterIds") List<Long> characterIds);

    List<Long> listFavoriteCharacterIdsForUser(
            @Param("userId") long userId,
            @Param("characterIds") List<Long> characterIds);

    List<Map<String, Object>> listVotesForUser(
            @Param("userId") long userId,
            @Param("characterIds") List<Long> characterIds);

    long countVisibleFavoritesByUser(@Param("userId") long userId);

    List<Long> listFavoriteCharacterIds(@Param("userId") long userId, @Param("limit") int limit);

    /** 收藏角色按「最近有会话活动」排序（无会话则按收藏时间）。 */
    List<Long> listFavoriteCharacterIdsByRecentChat(@Param("userId") long userId, @Param("limit") int limit);
}

