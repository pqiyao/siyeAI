package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppCharacterSocialMapper;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class H5SocialService {

    private final AppTokenService tokenService;
    private final H5ClientUidAuthService h5Auth;
    private final AppCharacterSocialMapper socialMapper;
    private final AppCharacterMapper characterMapper;
    private final AppConversationMapper conversationMapper;
    private final H5MyCharacterMapper h5MyCharacterMapper;

    public H5SocialService(
            AppTokenService tokenService,
            H5ClientUidAuthService h5Auth,
            AppCharacterSocialMapper socialMapper,
            AppCharacterMapper characterMapper,
            AppConversationMapper conversationMapper,
            H5MyCharacterMapper h5MyCharacterMapper) {
        this.tokenService = tokenService;
        this.h5Auth = h5Auth;
        this.socialMapper = socialMapper;
        this.characterMapper = characterMapper;
        this.conversationMapper = conversationMapper;
        this.h5MyCharacterMapper = h5MyCharacterMapper;
    }

    public MeStats getMeStats(String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        int favCount = Math.toIntExact(Math.min(Integer.MAX_VALUE, socialMapper.countVisibleFavoritesByUser(userId)));
        int chats = conversationMapper.countActiveForUser(userId);
        int chars = h5MyCharacterMapper.countMineActive(userId);
        return new MeStats(favCount, chats, chars);
    }

    public List<AppCharacter> listFavorites(String token, int limit) {
        return listFavorites(token, limit, "favorite");
    }

    /**
     * @param sortBy {@code favorite}: 按收藏时间，{@code recent_chat}: 按最近聊天时间。
     */
    public List<AppCharacter> listFavorites(String token, int limit, String sortBy) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        int lim = Math.max(1, Math.min(200, limit));
        String safeSort = sortBy == null ? "" : sortBy.trim();
        List<Long> ids = "recent_chat".equalsIgnoreCase(safeSort)
                ? socialMapper.listFavoriteCharacterIdsByRecentChat(userId, lim)
                : socialMapper.listFavoriteCharacterIds(userId, lim);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AppCharacter> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            AppCharacter character = characterMapper.findPublicVisibleById(id);
            if (character != null) {
                out.add(character);
            }
        }
        return out;
    }

    @Transactional
    public void unfavoriteBatch(String token, List<Long> characterIds) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        if (characterIds == null || characterIds.isEmpty()) {
            return;
        }
        List<Long> ids = characterIds.stream().filter(x -> x != null && x > 0).distinct().limit(200).toList();
        if (!ids.isEmpty()) {
            socialMapper.deleteFavoritesBatch(userId, ids);
        }
    }

    /**
     * 发现列表/详情：按 H5 clientUid 回补点赞、点踩、收藏状态。
     * clientUid 相关异常只会回退为公开计数，不应拖垮主列表。
     */
    public void enrichDiscoverCards(String clientUid, List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        Long userId = null;
        if (clientUid != null && !clientUid.isBlank()) {
            try {
                String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
                userId = tokenService.validateAndLoadUser(token).getId();
            } catch (Exception ignored) {
                // 社交态失败时仅降级，保证发现页可用。
            }
        }

        List<Long> characterIds = extractCharacterIds(cards);
        if (characterIds.isEmpty()) {
            return;
        }
        Map<Long, PublicCounts> countsByCharacter = new HashMap<>();
        boolean countsLoaded = false;
        try {
            for (Map<String, Object> row : socialMapper.listInteractionCounts(characterIds)) {
                long characterId = mapLong(row, "characterId");
                if (characterId > 0) {
                    countsByCharacter.put(characterId, new PublicCounts(
                            mapLong(row, "likeCount"),
                            mapLong(row, "dislikeCount")));
                }
            }
            countsLoaded = true;
        } catch (Exception ignored) {
            // 保留卡片已有的数据库计数，社交统计异常不能阻塞首页。
        }

        Set<Long> favoriteIds = Set.of();
        Map<Long, String> votesByCharacter = Map.of();
        boolean userStateLoaded = userId == null;
        if (userId != null) {
            try {
                favoriteIds = new HashSet<>(socialMapper.listFavoriteCharacterIdsForUser(userId, characterIds));
                Map<Long, String> loadedVotes = new HashMap<>();
                for (Map<String, Object> row : socialMapper.listVotesForUser(userId, characterIds)) {
                    long characterId = mapLong(row, "characterId");
                    if (characterId > 0) {
                        Object vote = mapValue(row, "vote");
                        loadedVotes.put(characterId, vote == null ? "" : String.valueOf(vote));
                    }
                }
                votesByCharacter = loadedVotes;
                userStateLoaded = true;
            } catch (Exception ignored) {
                // 保留默认收藏/投票状态，社交统计异常不能阻塞首页。
            }
        }

        for (Map<String, Object> card : cards) {
            if (card == null) {
                continue;
            }
            long characterId = cardId(card);
            if (characterId <= 0) {
                continue;
            }
            if (countsLoaded) {
                PublicCounts counts = countsByCharacter.getOrDefault(characterId, new PublicCounts(0L, 0L));
                card.put("like_count", counts.likes());
                card.put("dislike_count", counts.dislikes());
            } else {
                card.putIfAbsent("like_count", 0L);
                card.putIfAbsent("dislike_count", 0L);
            }
            if (userId != null && userStateLoaded) {
                card.put("is_favorite", favoriteIds.contains(characterId));
                String vote = votesByCharacter.get(characterId);
                card.put("user_vote", vote == null || vote.isBlank() ? "" : vote);
            } else {
                card.putIfAbsent("is_favorite", false);
                card.putIfAbsent("user_vote", "");
            }
        }
    }

    private static List<Long> extractCharacterIds(List<Map<String, Object>> cards) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Map<String, Object> card : cards) {
            long id = cardId(card);
            if (id > 0) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private static long cardId(Map<String, Object> card) {
        Object value = card == null ? null : card.get("id");
        return value instanceof Number number ? number.longValue() : parseLong(value);
    }

    private static long mapLong(Map<String, Object> row, String key) {
        return parseLong(mapValue(row, key));
    }

    private static Object mapValue(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().replace("_", "").equalsIgnoreCase(key.replace("_", ""))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private record PublicCounts(long likes, long dislikes) {}

    @Transactional
    public InteractionResult toggleInteraction(String token, long characterId, String action) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || !ownerId.equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
            }
        } else if (characterMapper.findPublicVisibleById(characterId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }

        String safeAction = action == null ? "" : action.trim();
        if ("favorite".equalsIgnoreCase(safeAction)) {
            boolean favorite = socialMapper.isFavorite(userId, characterId);
            if (favorite) {
                socialMapper.deleteFavorite(userId, characterId);
            } else {
                try {
                    socialMapper.insertFavorite(userId, characterId);
                } catch (Exception ignored) {
                    // 并发下重复收藏视为已收藏。
                }
            }
        } else if ("like".equalsIgnoreCase(safeAction) || "dislike".equalsIgnoreCase(safeAction)) {
            String existing = socialMapper.findVote(userId, characterId);
            if (safeAction.equalsIgnoreCase(existing)) {
                socialMapper.deleteVote(userId, characterId);
            } else {
                socialMapper.upsertVote(userId, characterId, safeAction.toLowerCase());
            }
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "action 无效");
        }

        long likeCount = socialMapper.countVoteByCharacter(characterId, "like");
        long dislikeCount = socialMapper.countVoteByCharacter(characterId, "dislike");
        boolean isFavorite = socialMapper.isFavorite(userId, characterId);
        String userVote = socialMapper.findVote(userId, characterId);
        if (userVote == null || userVote.isBlank()) {
            userVote = "none";
        }
        return new InteractionResult(likeCount, dislikeCount, isFavorite, userVote);
    }

    /** 对齐 H5 user.vue：fav / chats / chars */
    public record MeStats(int favoriteCount, int activeConversationCount, int myCharacterCount) {}

    public record InteractionResult(long likeCount, long dislikeCount, boolean isFavorite, String userVote) {}
}
