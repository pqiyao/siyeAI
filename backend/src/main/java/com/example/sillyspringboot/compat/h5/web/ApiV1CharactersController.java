package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.dto.AppCharacterSummaryDto;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5SocialService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.TagLibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/characters")
public class ApiV1CharactersController {

    private final CharacterCatalogService catalogService;
    private final AppCharacterMapper characterMapper;
    private final H5SocialService h5SocialService;
    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final StAdapter stAdapter;
    private final H5StAssetUrls stAssetUrls;
    private final TagLibraryService tagLibraryService;
    private final H5EntitlementService entitlementService;

    public ApiV1CharactersController(
            CharacterCatalogService catalogService,
            AppCharacterMapper characterMapper,
            H5SocialService h5SocialService,
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            StAdapter stAdapter,
            H5StAssetUrls stAssetUrls,
            TagLibraryService tagLibraryService,
            H5EntitlementService entitlementService
    ) {
        this.catalogService = catalogService;
        this.characterMapper = characterMapper;
        this.h5SocialService = h5SocialService;
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.stAdapter = stAdapter;
        this.stAssetUrls = stAssetUrls;
        this.tagLibraryService = tagLibraryService;
        this.entitlementService = entitlementService;
    }

    @GetMapping("")
    public ApiV1Result<List<Map<String, Object>>> list(
            @RequestParam(name = "limit", required = false, defaultValue = "24") int limit,
            @RequestParam(name = "clientUid", required = false) String clientUid,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "gameplay", required = false) String gameplay,
            @RequestParam(name = "sort", required = false, defaultValue = "default") String sort,
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset
    ) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        int safeOffset = Math.max(0, Math.min(1000, offset));
        boolean hasFilter = (q != null && !q.isBlank()) || (tag != null && !tag.isBlank()) || (gameplay != null && !gameplay.isBlank());
        int requestedWindow = safeOffset + safeLimit;
        int syncLimit = Math.max(requestedWindow * (hasFilter ? 3 : 2), hasFilter ? 144 : 96);
        if (syncLimit > 500) {
            syncLimit = 500;
        }
        List<AppCharacterSummaryDto> feed = catalogService.syncFeed(syncLimit);
        String safeQuery = normalizeQuery(q);
        String safeTag = normalizeQuery(tag);
        String safeGameplay = normalizeQuery(gameplay);

        Stream<AppCharacterSummaryDto> stream = feed.stream();
        if (!safeQuery.isEmpty()) {
            stream = stream.filter(item -> matchesSearch(item, safeQuery));
        }
        if (!safeTag.isEmpty()) {
            stream = stream.filter(item -> matchesTag(item, safeTag));
        }
        if (!safeGameplay.isEmpty()) {
            stream = stream.filter(item -> matchesGameplay(item, safeGameplay));
        }

        List<AppCharacterSummaryDto> filtered = stream.collect(Collectors.toList());
        boolean canAccessVipCharacters = safeCanAccessVipCharacters(clientUid);
        int viewerVipLevel = safeCurrentVipLevel(clientUid);
        List<Map<String, Object>> list = filtered.stream()
                .map(item -> toDiscoverCard(item, canAccessVipCharacters, viewerVipLevel))
                .collect(Collectors.toCollection(ArrayList::new));
        safeEnrichDiscoverCards(clientUid, list);
        sortDiscover(list, sort);
        if (safeOffset > 0) {
            if (safeOffset >= list.size()) {
                list = new ArrayList<>();
            } else {
                list = new ArrayList<>(list.subList(safeOffset, list.size()));
            }
        }
        if (list.size() > safeLimit) {
            list = new ArrayList<>(list.subList(0, safeLimit));
        }
        return ApiV1Result.ok(list);
    }

    @GetMapping("/tags")
    public ApiV1Result<List<Map<String, Object>>> listDiscoverTags() {
        return ApiV1Result.ok(tagLibraryService.listDiscoverOptions());
    }

    @GetMapping("/{characterId}")
    public ApiV1Result<Map<String, Object>> get(
            @PathVariable("characterId") long characterId,
            @RequestParam(name = "clientUid", required = false) String clientUid
    ) {
        AppCharacter row = characterMapper.findById(characterId);
        if (row == null || row.getDeletedAt() != null) {
            return ApiV1Result.fail("角色不存在");
        }
        Long ownerId = row.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(row.getPrivateCard())) {
            if (ownerId == null || clientUid == null || clientUid.isBlank()) {
                return ApiV1Result.fail("角色不存在");
            }
            String token = h5Auth.issueTokenForClientUid(clientUid);
            long userId = tokenService.validateAndLoadUser(token).getId();
            if (!ownerId.equals(userId)) {
                return ApiV1Result.fail("角色不存在");
            }
        } else if (Boolean.FALSE.equals(row.getClientVisible())) {
            return ApiV1Result.fail("角色不存在");
        }

        StCharacterDetail detail = null;
        if (row.getStAvatarUrl() != null && !row.getStAvatarUrl().isBlank()) {
            try {
                detail = stAdapter.getCharacter(new StCharacterGetRequest(row.getStAvatarUrl()));
            } catch (StUnavailableException ignored) {
                // 使用数据库兜底
            }
        }

        H5EntitlementService.CharacterAccess access =
                safeResolveCharacterAccess(clientUid, row.getVipOnly(), row.getUnlockedDefault());
        Map<String, Object> card = toDetailCard(row, detail, access, safeCurrentVipLevel(clientUid));
        safeEnrichDiscoverCards(clientUid, List.of(card));
        return ApiV1Result.ok(card);
    }

    private boolean safeCanAccessVipCharacters(String clientUid) {
        try {
            return entitlementService.canAccessVipCharacters(clientUid);
        } catch (Exception ignored) {
            return entitlementService.canAccessVipCharacters(null);
        }
    }

    private H5EntitlementService.CharacterAccess safeResolveCharacterAccess(
            String clientUid,
            Boolean vipOnly,
            Boolean unlockedDefault
    ) {
        try {
            return entitlementService.resolveCharacterAccess(clientUid, vipOnly, unlockedDefault);
        } catch (Exception ignored) {
            boolean baseUnlocked = !Boolean.FALSE.equals(unlockedDefault);
            if (!Boolean.TRUE.equals(vipOnly)) {
                return new H5EntitlementService.CharacterAccess(baseUnlocked, false, baseUnlocked ? "" : "当前角色暂未开放");
            }
            return new H5EntitlementService.CharacterAccess(false, true, "当前角色仅会员可用，请先开通会员");
        }
    }

    private int safeCurrentVipLevel(String clientUid) {
        try {
            return entitlementService.currentVipLevel(clientUid);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void safeEnrichDiscoverCards(String clientUid, List<Map<String, Object>> cards) {
        try {
            h5SocialService.enrichDiscoverCards(clientUid, cards);
        } catch (Exception ignored) {
            h5SocialService.enrichDiscoverCards(null, cards);
        }
    }

    private void sortDiscover(List<Map<String, Object>> list, String sort) {
        if (list == null || list.isEmpty() || sort == null) {
            return;
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        Comparator<Map<String, Object>> comparator = switch (normalized) {
            case "likes" -> Comparator.<Map<String, Object>, Long>comparing(m -> toLong(m.get("like_count"))).reversed();
            case "new" -> Comparator.<Map<String, Object>, Long>comparing(m -> toLong(m.get("id"))).reversed();
            case "old" -> Comparator.comparing(m -> toLong(m.get("id")));
            default -> Comparator.<Map<String, Object>, Integer>comparing(m -> toInt(m.get("sort_order")))
                    .thenComparing(m -> toLong(m.get("id")));
        };
        list.sort(comparator);
    }

    private boolean matchesSearch(AppCharacterSummaryDto item, String query) {
        if (query.isBlank()) {
            return true;
        }
        if (contains(item.name(), query)
                || contains(item.description(), query)
                || contains(item.tagline(), query)
                || contains(item.bio(), query)
                || contains(item.occupationLabel(), query)
                || contains(item.gameplayType(), query)
                || CharacterJsonSupport.tagsJsonMatches(item.tagsJson(), query)
                || matchesResolvedLabels(item.tagsJson(), query)) {
            return true;
        }
        return false;
    }

    private boolean matchesTag(AppCharacterSummaryDto item, String tag) {
        if (tag.isBlank()) {
            return true;
        }
        if (CharacterJsonSupport.tagsJsonMatches(item.tagsJson(), tag)) {
            return true;
        }
        if (matchesResolvedLabels(item.tagsJson(), tag)) {
            return true;
        }
        return contains(item.name(), tag)
                || contains(item.description(), tag)
                || contains(item.bio(), tag)
                || contains(item.tagline(), tag)
                || contains(item.occupationLabel(), tag)
                || contains(item.gameplayType(), tag);
    }

    private boolean matchesResolvedLabels(String tagsJson, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String safeQuery = normalizeQuery(query);
        return tagLibraryService.buildDiscoverLabelArray(tagsJson).stream()
                .map(entry -> entry.get("code"))
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(safeQuery) || value.contains(safeQuery) || safeQuery.contains(value));
    }

    private boolean matchesGameplay(AppCharacterSummaryDto item, String gameplay) {
        if (gameplay.isBlank()) {
            return true;
        }
        if (item.gameplayType() != null && !item.gameplayType().isBlank()) {
            return CharacterJsonSupport.gameplayMatches(item.gameplayType(), gameplay);
        }
        return contains(item.name(), gameplay) || contains(item.description(), gameplay);
    }

    private Map<String, Object> toDiscoverCard(
            AppCharacterSummaryDto item,
            boolean canAccessVipCharacters,
            int viewerVipLevel
    ) {
        Map<String, Object> card = new LinkedHashMap<>();
        String portrait = stAssetUrls.portraitForCharacter(item.avatarUrl(), item.coverUrl(), item.stAvatarUrl());
        String portraitAvatarThumb = stAssetUrls.portraitForCharacterThumb(
                item.avatarUrl(),
                item.coverUrl(),
                item.stAvatarUrl(),
                "avatar"
        );
        String portraitCoverThumb = stAssetUrls.portraitForCharacterThumb(
                item.avatarUrl(),
                item.coverUrl(),
                item.stAvatarUrl(),
                "card"
        );
        String rawDesc = firstNonBlank(item.tagline(), item.bio(), item.description());
        String shortDesc = shortDescriptionForDiscoverCard(rawDesc);

        card.put("id", item.characterId());
        card.put("sort_order", item.sortOrder());
        card.put("name", item.name());
        card.put("nickname", item.name());
        card.put("description", shortDesc);
        card.put("tagline", blankToDefault(item.tagline(), shortDesc));
        card.put("bio", blankToDefault(item.bio(), shortDesc));
        card.put("avatar_url", item.stAvatarUrl());
        card.put("avatar", portrait);
        card.put("cover", portrait);
        card.put("avatar_thumb", portraitAvatarThumb);
        card.put("cover_thumb", portraitCoverThumb);
        card.put("chat_background_url", stAssetUrls.resolve(firstNonBlank(item.chatBackgroundUrl(), item.coverUrl(), "")));
        card.put("creator", blankToDefault(item.creatorName(), "SillyTavern"));
        card.put("creator_handle", formatCreatorHandle(item.creatorHandle()));

        applyAccessState(
                card,
                Boolean.TRUE.equals(item.vipOnly()),
                !Boolean.FALSE.equals(item.unlockedDefault()),
                canAccessVipCharacters
        );
        card.put("vip_only", Boolean.TRUE.equals(item.vipOnly()));
        card.put("client_visible", !Boolean.FALSE.equals(item.clientVisible()));
        card.put("private_card", false);
        card.put("label_array", tagLibraryService.buildDiscoverLabelArray(item.tagsJson()));
        card.put("occupation_arr", blankToDefault(item.occupationLabel(), ""));
        card.put("token_display", blankToDefault(item.tokenDisplay(), "<2000"));
        card.put("gameplay_type", blankToDefault(item.gameplayType(), "对手戏"));
        card.put("chat_modes", CharacterJsonSupport.chatModesFromJson(item.chatModesJson(), defaultChatModes()));
        card.put("token_cost", 0);
        card.put("persona", "");
        card.put("scenario", "");
        card.put("first_message", "");
        card.put("like_count", (long) item.likeCount());
        card.put("dislike_count", (long) item.dislikeCount());
        card.put("is_favorite", false);
        card.put("user_vote", "");
        applyPreviewBlurState(card, item.previewBlurVipLevel(), viewerVipLevel);
        return card;
    }

    private Map<String, Object> toDetailCard(
            AppCharacter row,
            StCharacterDetail detail,
            H5EntitlementService.CharacterAccess access,
            int viewerVipLevel
    ) {
        Map<String, Object> card = new LinkedHashMap<>();
        String portrait = stAssetUrls.portraitForCharacter(row.getAvatarUrl(), row.getCoverUrl(), row.getStAvatarUrl());
        String portraitAvatarThumb = stAssetUrls.portraitForCharacterThumb(
                row.getAvatarUrl(),
                row.getCoverUrl(),
                row.getStAvatarUrl(),
                "avatar"
        );
        String portraitCoverThumb = stAssetUrls.portraitForCharacterThumb(
                row.getAvatarUrl(),
                row.getCoverUrl(),
                row.getStAvatarUrl(),
                "card"
        );
        String portraitDetail = stAssetUrls.portraitForCharacterThumb(
                row.getAvatarUrl(),
                row.getCoverUrl(),
                row.getStAvatarUrl(),
                "detail"
        );
        String name = row.getName();
        String bio = row.getBio() != null && !row.getBio().isBlank() ? row.getBio() : row.getDescription();
        if (bio == null) {
            bio = "";
        }
        String tagline = row.getTagline() != null && !row.getTagline().isBlank() ? row.getTagline() : bio;

        card.put("id", row.getId());
        card.put("sort_order", row.getSortOrder() != null ? row.getSortOrder() : 0);
        card.put("name", name);
        card.put("nickname", name);
        card.put("description", bio);
        card.put("avatar_url", row.getStAvatarUrl());
        card.put("avatar", portrait);
        card.put("cover", portrait);
        card.put("avatar_thumb", portraitAvatarThumb);
        card.put("cover_thumb", portraitCoverThumb);
        card.put("cover_detail", portraitDetail);
        card.put("chat_background_url", stAssetUrls.resolve(firstNonBlank(row.getChatBackgroundUrl(), row.getCoverUrl(), "")));
        card.put("tagline", tagline);
        card.put("bio", bio);
        card.put("creator", blankToDefault(row.getCreatorName(), "SillyTavern"));
        card.put("creator_handle", formatCreatorHandle(row.getCreatorHandle()));
        card.put("unlocked", access.unlocked());
        card.put("vip_only", Boolean.TRUE.equals(row.getVipOnly()));
        card.put("lock_reason", access.lockReason());
        card.put("client_visible", !Boolean.FALSE.equals(row.getClientVisible()));
        card.put("private_card", Boolean.TRUE.equals(row.getPrivateCard()));
        card.put("label_array", tagLibraryService.buildDetailLabelArrayFromJson(row.getTagsJson()));
        card.put("occupation_arr", blankToDefault(row.getOccupationLabel(), ""));
        card.put("token_display", blankToDefault(row.getTokenDisplay(), "<2000"));
        card.put("gameplay_type", blankToDefault(row.getGameplayType(), "对手戏"));
        card.put("chat_modes", CharacterJsonSupport.chatModesFromJson(row.getChatModesJson(), defaultChatModes()));
        card.put("token_cost", 0);

        if (detail != null) {
            if (detail.name() != null && !detail.name().isBlank()) {
                card.put("nickname", detail.name());
                card.put("name", detail.name());
            }
            if (detail.description() != null && !detail.description().isBlank()) {
                if (row.getBio() == null || row.getBio().isBlank()) {
                    card.put("description", detail.description());
                    card.put("bio", detail.description());
                }
                if (row.getTagline() == null || row.getTagline().isBlank()) {
                    card.put("tagline", detail.description());
                }
            }
            card.put("persona", firstNonBlank(detail.personality(), row.getPersona(), ""));
            card.put("scenario", firstNonBlank(detail.scenario(), row.getScenario(), ""));
            card.put("first_message", firstNonBlank(detail.firstMes(), row.getFirstMessage(), ""));
            if (CharacterJsonSupport.parseStringArrayJson(row.getTagsJson()).isEmpty()) {
                List<String> tags = detail.tags();
                if (tags != null && !tags.isEmpty()) {
                    List<Map<String, String>> labels = tagLibraryService.buildDetailLabelArray(tags);
                    if (!labels.isEmpty()) {
                        card.put("label_array", labels);
                    }
                }
            }
        } else {
            card.put("persona", blankToDefault(row.getPersona(), ""));
            card.put("scenario", blankToDefault(row.getScenario(), ""));
            card.put("first_message", blankToDefault(row.getFirstMessage(), ""));
        }

        if (!access.unlocked()) {
            card.put("persona", "");
            card.put("scenario", "");
            card.put("first_message", "");
        }

        card.put("like_count", row.getLikeCount() != null ? row.getLikeCount().longValue() : 0L);
        card.put("dislike_count", row.getDislikeCount() != null ? row.getDislikeCount().longValue() : 0L);
        card.put("is_favorite", false);
        card.put("user_vote", "");
        applyPreviewBlurState(card, row.getPreviewBlurVipLevel(), viewerVipLevel);
        return card;
    }

    private void applyAccessState(
            Map<String, Object> target,
            boolean vipOnly,
            boolean defaultUnlocked,
            boolean canAccessVipCharacters
    ) {
        boolean unlocked = defaultUnlocked && (!vipOnly || canAccessVipCharacters);
        target.put("unlocked", unlocked);
        if (unlocked) {
            target.put("lock_reason", "");
            return;
        }
        target.put("lock_reason", vipOnly ? "当前角色仅会员可用，请先开通会员" : "当前角色暂未开放");
    }

    private void applyPreviewBlurState(Map<String, Object> target, Integer requiredVipLevel, int viewerVipLevel) {
        int normalizedRequiredLevel = normalizePreviewBlurVipLevel(requiredVipLevel);
        boolean blurActive = normalizedRequiredLevel > 0 && viewerVipLevel < normalizedRequiredLevel;
        target.put("preview_blur_vip_level", normalizedRequiredLevel);
        target.put("preview_blur_active", blurActive);
    }

    private List<Map<String, Object>> defaultChatModes() {
        List<Map<String, Object>> modes = new ArrayList<>();
        modes.add(Map.of(
                "icon", "roleplay",
                "name", "角色扮演",
                "sub", "",
                "recommend", true
        ));
        return modes;
    }

    private static String normalizeQuery(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return c == null ? "" : c;
    }

    private static String blankToDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }

    private static String formatCreatorHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return "@share";
        }
        String trimmed = handle.trim();
        return trimmed.startsWith("@") ? trimmed : "@" + trimmed;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String shortDescriptionForDiscoverCard(String desc) {
        if (desc == null || desc.isBlank()) {
            return "";
        }
        String value = desc.trim();
        int macro = value.indexOf("{{");
        if (macro > 0) {
            value = value.substring(0, macro).trim();
        }
        int hardLimit = Math.min(value.length(), 220);
        if (value.length() > hardLimit) {
            value = value.substring(0, hardLimit).trim() + "...";
        }
        return value;
    }

    private static int normalizePreviewBlurVipLevel(Integer value) {
        int level = value == null ? 0 : value;
        if (level < 0) {
            return 0;
        }
        if (level > 2) {
            return 2;
        }
        return level;
    }
}
