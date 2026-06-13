package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.admin.service.CharacterContentScreeningService;
import com.example.sillyspringboot.admin.service.CharacterReviewAuditLogService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.entity.H5MyCharacter;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.compat.h5.service.H5TavernSessionService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5MyCharacterSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/characters")
public class ApiV1MyCharactersController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ApiV1MyCharactersController.class);

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final H5MyCharacterMapper mineMapper;
    private final H5UploadService uploadService;
    private final H5StAssetUrls stAssetUrls;
    private final StAdapter stAdapter;
    private final AppCharacterMapper appCharacterMapper;
    private final CharacterContentScreeningService characterContentScreeningService;
    private final CharacterReviewAuditLogService reviewAuditLogService;
    private final AppFeatureSettingsService featureSettingsService;
    private final H5VisitorTrialGuardService visitorTrialGuardService;
    private final H5EntitlementService entitlementService;
    private final H5TavernSessionService tavernSessionService;
    private final EmbeddedLorebookSyncService embeddedLorebookSyncService;
    private final SocialUploadRateLimiter rateLimiter;

    public ApiV1MyCharactersController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            H5MyCharacterMapper mineMapper,
            H5UploadService uploadService,
            H5StAssetUrls stAssetUrls,
            StAdapter stAdapter,
            AppCharacterMapper appCharacterMapper,
            CharacterContentScreeningService characterContentScreeningService,
            CharacterReviewAuditLogService reviewAuditLogService,
            AppFeatureSettingsService featureSettingsService,
            H5VisitorTrialGuardService visitorTrialGuardService,
            H5EntitlementService entitlementService,
            H5TavernSessionService tavernSessionService,
            EmbeddedLorebookSyncService embeddedLorebookSyncService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.mineMapper = mineMapper;
        this.uploadService = uploadService;
        this.stAssetUrls = stAssetUrls;
        this.stAdapter = stAdapter;
        this.appCharacterMapper = appCharacterMapper;
        this.characterContentScreeningService = characterContentScreeningService;
        this.reviewAuditLogService = reviewAuditLogService;
        this.featureSettingsService = featureSettingsService;
        this.visitorTrialGuardService = visitorTrialGuardService;
        this.entitlementService = entitlementService;
        this.tavernSessionService = tavernSessionService;
        this.embeddedLorebookSyncService = embeddedLorebookSyncService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/mine")
    public ApiV1Result<List<Map<String, Object>>> mine(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort
    ) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        String safeSort = "name".equalsIgnoreCase(sort) ? "name" : "recent";
        List<H5MyCharacter> list = mineMapper.listMine(userId, safeSort, 500);
        return ApiV1Result.ok(list.stream().map(this::toCard).toList());
    }

    @GetMapping("/mine/creation-access")
    public ApiV1Result<Map<String, Object>> creationAccess(@RequestParam("clientUid") String clientUid) {
        return ApiV1Result.ok(
                entitlementService.toMap(entitlementService.resolveCharacterCreationAccess(clientUid))
        );
    }

    @PostMapping("/create-draft")
    public ApiV1Result<Map<String, Object>> createDraft(@RequestBody Map<String, Object> body) {
        featureSettingsService.ensureUserCharacterCreationEnabled();
        String clientUid = asString(body == null ? null : body.get("clientUid"));
        String name = asString(body == null ? null : body.get("name"));
        String tagline = asString(body == null ? null : body.get("tagline"));
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }

        String safeName = name == null ? "" : name.trim();
        if (safeName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Character name is required.");
        }
        if (safeName.length() > 128) {
            safeName = safeName.substring(0, 128);
        }

        String token = h5Auth.issueTokenForClientUid(clientUid);
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        visitorTrialGuardService.guardAnonymousCharacterCreation(clientUid);
        entitlementService.requireCharacterCreationAccess(user, 1);

        String safeTagline = tagline == null ? "" : tagline.trim();
        if (safeTagline.length() > 256) {
            safeTagline = safeTagline.substring(0, 256);
        }
        if (safeTagline.isBlank()) {
            safeTagline = "My character";
        }

        H5MyCharacter row = new H5MyCharacter();
        row.setOwnerUserId(userId);
        row.setStAvatarUrl("h5draft_u" + userId + "_" + UUID.randomUUID() + ".png");
        row.setName(safeName);
        row.setTagline(safeTagline);
        row.setDescription(safeTagline);
        row.setBio("");
        row.setPrivateCard(Boolean.TRUE);
        row.setReviewStatus(CharacterReviewStatus.PENDING);
        row.setReviewReason(null);
        row.setReviewedAt(null);
        row.setReviewedBy(null);
        row.setAlternateGreetings(List.of());
        row.setVipOnly(Boolean.FALSE);
        row.setUnlockedDefault(Boolean.TRUE);
        row.setSortOrder(0);
        row.setLikeCount(0);
        row.setDislikeCount(0);
        mineMapper.insertMine(row);

        return ApiV1Result.ok(Map.of("id", row.getId()));
    }
    @GetMapping("/mine/editor/{id}")
    public ApiV1Result<Map<String, Object>> editor(
            @PathVariable("id") long id,
            @RequestParam("clientUid") String clientUid
    ) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        H5MyCharacter character = mineMapper.findEditor(id, userId);
        if (character == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Character not found.");
        }
        return ApiV1Result.ok(toEditor(character));
    }
    @PostMapping("/mine/save")
    public ApiV1Result<Map<String, Object>> save(@RequestBody H5MyCharacterSaveRequest req) {
        if (req == null || req.getClientUid() == null || req.getClientUid().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        featureSettingsService.ensureUserCharacterCreationEnabled();
        String token = h5Auth.issueTokenForClientUid(req.getClientUid());
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();

        if (req.getId() == null) {
            visitorTrialGuardService.guardAnonymousCharacterCreation(req.getClientUid());
            entitlementService.requireCharacterCreationAccess(user, 1);
            H5MyCharacter row = new H5MyCharacter();
            row.setOwnerUserId(userId);
            row.setStAvatarUrl("h5draft_u" + userId + "_" + UUID.randomUUID() + ".png");
            apply(row, req);
            if (row.getSortOrder() == null) {
                row.setSortOrder(0);
            }
            if (row.getLikeCount() == null) {
                row.setLikeCount(0);
            }
            if (row.getDislikeCount() == null) {
                row.setDislikeCount(0);
            }
            row.setStAvatarUrl(stAdapter.syncCharacterCard(toStDetail(row), row.getStAvatarUrl()));
            mineMapper.insertMine(row);
            mineMapper.softDeletePublicSyncShadowByStAvatarUrl(row.getStAvatarUrl());
            H5MyCharacter saved = mineMapper.findEditor(row.getId(), userId);
            if (saved == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save character.");
            }
            recordAutoScreen(saved.getId());
            return ApiV1Result.ok(toEditor(saved));
        }

        H5MyCharacter existed = mineMapper.findEditor(req.getId(), userId);
        if (existed == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Character not found.");
        }
        existed.setOwnerUserId(userId);
        apply(existed, req);
        existed.setStAvatarUrl(stAdapter.syncCharacterCard(toStDetail(existed), existed.getStAvatarUrl()));
        mineMapper.updateMine(existed);
        mineMapper.softDeletePublicSyncShadowByStAvatarUrl(existed.getStAvatarUrl());
        H5MyCharacter saved = mineMapper.findEditor(existed.getId(), userId);
        if (saved == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save character.");
        }
        recordAutoScreen(saved.getId());
        return ApiV1Result.ok(toEditor(saved));
    }
    @PostMapping("/mine/delete")
    @Transactional
    public ApiV1Result<Boolean> delete(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : asString(payload.get("clientUid"));
        Long id = payload == null ? null : asLong(payload.get("id"));
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "id missing");
        }
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        H5MyCharacter character = mineMapper.findEditor(id, userId);
        if (character == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Character not found.");
        }
        String stAvatarUrl = trimToEmpty(character.getStAvatarUrl());
        boolean stDeleted = deleteStCharacterQuietly(stAvatarUrl, id, userId);
        tavernSessionService.purgeUserCharacterConversations(userId, id);
        embeddedLorebookSyncService.deleteAllForCharacter(id);
        mineMapper.softDelete(id, userId);
        if (!stAvatarUrl.isBlank() && !stAvatarUrl.startsWith("__deleted_private__/")) {
            mineMapper.softDeletePublicSyncShadowByStAvatarUrl(stAvatarUrl);
        }
        if (stDeleted) {
            mineMapper.archiveStAvatarUrlAfterStDelete(id, userId);
        }
        return ApiV1Result.ok(true);
    }

    private boolean deleteStCharacterQuietly(String stAvatarUrl, long characterId, long userId) {
        String safeAvatar = trimToEmpty(stAvatarUrl);
        if (safeAvatar.isBlank() || safeAvatar.startsWith("__deleted_private__/")) {
            return false;
        }
        if (mineMapper.countActiveByStAvatarUrlExcludingId(safeAvatar, characterId) > 0) {
            return false;
        }
        try {
            return stAdapter.deleteCharacter(safeAvatar, true);
        } catch (Exception e) {
            log.warn(
                    "Failed to delete ST private character file, keep avatar tombstone active. characterId={}, userId={}, stAvatarUrl={}: {}",
                    characterId,
                    userId,
                    safeAvatar,
                    e.toString()
            );
            return false;
        }
    }
    @PostMapping(value = "/mine/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("clientUid") String clientUid,
            HttpServletRequest request
    ) {
        featureSettingsService.ensureUserCharacterCreationEnabled();
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        AppUser user = tokenService.validateAndLoadUser(h5Auth.issueTokenForClientUid(clientUid));
        rateLimiter.checkUpload(user, request, "my_character_image");
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "File is required.");
        }
        String url = uploadService.saveAndGetUrl(file);
        return ApiV1Result.ok(Map.of("url", url));
    }
    @PostMapping(value = "/mine/import-sillytavern-png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> importMinePng(
            @RequestPart("file") MultipartFile file,
            @RequestParam("clientUid") String clientUid,
            HttpServletRequest request
    ) {
        featureSettingsService.ensureUserCharacterCreationEnabled();
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        visitorTrialGuardService.guardAnonymousCharacterCreation(clientUid);
        AppUser user = tokenService.validateAndLoadUser(h5Auth.issueTokenForClientUid(clientUid));
        rateLimiter.checkUpload(user, request, "my_character_import_png");
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "File is required.");
        }
        String originalFilename = trimToEmpty(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Only ST-exported character card PNG files are supported.");
        }

        long userId = user.getId();
        entitlementService.requireCharacterCreationAccess(user, 1);
        try {
            Object raw = stAdapter.importCharacterPng(
                    file.getBytes(),
                    originalFilename,
                    new StCharacterImportRequest("png", buildPrivateImportPreservedName(userId))
            );
            String importError = extractImportError(raw);
            if (!importError.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, importError);
            }
            String stAvatarUrl = extractImportedAvatarUrl(raw);
            if (stAvatarUrl.isBlank()) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "PNG 闂傚倷娴囬褍霉閻戣棄鏋侀柟闂寸閸屻劎鎲搁弬璺ㄦ殾闁挎繂顦獮銏′繆椤栨繃顏犵紒鎰仱閺屸剝寰勬繝鍕拡闂佺顑呴ˇ鎶铰烽崒鐐粹拻濞达絿鎳撻婊呯磼鐎ｎ偄娴柟顔芥そ瀵劍淇婃繅纾濼avern 婵犵數濮烽弫鎼佸磻濞戞瑥绶為柛銉墮缁€鍫熺節闂堟稒锛旈柤鏉跨仢闇夐柨婵嗙墱濞兼劕霉濠у灝鈧繈寮婚敓鐘茬＜婵炴垶锕╅崵瀣磽娴ｆ彃浜鹃梺閫炲苯澧撮柡宀嬬秮閹垽宕妷锕€娅楅梺姹囧焺閸ㄦ娊宕伴幇顔惧崥闁绘柨鎽滅弧鈧梺鎼炲劀閸曞灚顥ら梻鍌欑劍閹爼宕曟繝姘挃闁告洦鍨奸弫鍐归悩宸剱闁绘挻娲橀幈銊ヮ潨閸℃顫梺瀹狀嚙閻偐妲愰幒鎾寸秶闁靛ě鍛毉闂備礁缍婇ˉ鎾存叏閻㈡潌鍥偋閸喎鍔呭┑鈽嗗灠閸㈠弶绂嶉悙顑跨箚妞ゆ牗鑹鹃幃鎴濃攽椤栨哎鍋㈤柡灞炬礋瀹曠厧鈹戦崼婵冨彙缂傚倷鑳舵慨瀵哥礊婵犲洤钃熼柨鐔哄Т缁€瀣⒒閸喓銆掑ù鐘欏懐纾藉ù锝堫潐閳锋劖绻涢崗鑲╂噰妞ゃ垺宀搁弫鎰緞鐎ｎ亙姹楅梻浣告贡缁垳鏁悢纰辨晩濠㈣埖鍔栭悡鐔煎箹鏉堝墽绋婚柡鍡忔櫊閺屾稑螣閻樺弶鎼愰柣顓燁殜閺屾盯鍩勯崘顏佸闂?ST 闂傚倷娴囧畷鐢稿窗閹扮増鍋￠柨鏃傚亾閺嗘粓鏌ｉ弬鎸庢喐闁绘繆娉涢埞鎴︽偐閸欏鎮欑紒缁㈠幐閸?PNG"
                );
            }
            StCharacterDetail detail = stAdapter.getCharacter(new StCharacterGetRequest(stAvatarUrl));
            if (detail == null || detail.name() == null || detail.name().isBlank()) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "PNG import failed: SillyTavern did not return a character file name. Please make sure this is a valid ST character card PNG."
                );
            }

            H5MyCharacter existing = mineMapper.findByStAvatarUrlAndOwnerAny(stAvatarUrl, userId);
            boolean isNew = existing == null;
            H5MyCharacter row = isNew ? new H5MyCharacter() : existing;
            if (!isNew && row.getDeletedAt() != null) {
                row.setDeletedAt(null);
                row.setPrivateCard(Boolean.TRUE);
                row.setVipOnly(Boolean.FALSE);
                row.setUnlockedDefault(Boolean.TRUE);
                row.setSortOrder(0);
                row.setLikeCount(0);
                row.setDislikeCount(0);
                row.setCreatorHandle("me");
                row.setTokenDisplay("<2000");
            }
            if (isNew) {
                row.setOwnerUserId(userId);
                row.setStAvatarUrl(stAvatarUrl);
                row.setPrivateCard(Boolean.TRUE);
                row.setVipOnly(Boolean.FALSE);
                row.setUnlockedDefault(Boolean.TRUE);
                row.setSortOrder(0);
                row.setLikeCount(0);
                row.setDislikeCount(0);
                row.setCreatorHandle("me");
                row.setTokenDisplay("<2000");
            }
            applyImportedDetail(row, stAvatarUrl, detail);
            row.setReviewStatus(CharacterReviewStatus.PENDING);
            row.setReviewReason(null);
            row.setReviewedAt(null);
            row.setReviewedBy(null);
            row.setDeletedAt(null);
            if (isNew) {
                mineMapper.insertMine(row);
            } else {
                mineMapper.updateMine(row);
            }
            mineMapper.softDeletePublicSyncShadowByStAvatarUrl(row.getStAvatarUrl());
            H5MyCharacter saved = mineMapper.findEditor(row.getId(), userId);
            if (saved == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to import character.");
            }
            recordAutoScreen(saved.getId());
            int importedLorebookEntries = embeddedLorebookSyncService.replaceEmbeddedLorebook(
                    saved.getId(),
                    detail.embeddedCharacterBookJson()
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("name", saved.getName());
            data.put("reviewStatus", saved.getReviewStatus());
            data.put("avatarUrl", saved.getAvatarUrl());
            data.put("stAvatarUrl", saved.getStAvatarUrl());
            data.put("importedTags", detail.tags() == null ? List.of() : detail.tags());
            data.put("importedWorldNames", detail.worldNames() == null ? List.of() : detail.worldNames());
            data.put("importedLorebookEntries", importedLorebookEntries);
            return ApiV1Result.ok(data);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, resolveImportErrorMessage(ex), ex);
        }
    }
    private Map<String, Object> toCard(H5MyCharacter c) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", c.getId());
        data.put("name", c.getName());
        data.put("description", c.getDescription());
        data.put("tagline", c.getTagline());
        data.put("bio", c.getBio());
        data.put("persona", c.getPersona());
        data.put("scenario", c.getScenario());
        data.put("first_message", c.getFirstMessage());
        data.put("mes_example", c.getMesExample());
        data.put("system_prompt", c.getSystemPrompt());
        data.put("post_history_instructions", c.getPostHistoryInstructions());
        String portrait = stAssetUrls.portraitForCharacter(c.getAvatarUrl(), c.getCoverUrl(), c.getStAvatarUrl());
        String portraitAvatarThumb = stAssetUrls.portraitForCharacterThumb(
                c.getAvatarUrl(),
                c.getCoverUrl(),
                c.getStAvatarUrl(),
                "avatar"
        );
        String portraitCoverThumb = stAssetUrls.portraitForCharacterThumb(
                c.getAvatarUrl(),
                c.getCoverUrl(),
                c.getStAvatarUrl(),
                "card"
        );
        data.put("avatar", portrait);
        data.put("cover", portrait);
        data.put("avatar_thumb", portraitAvatarThumb);
        data.put("cover_thumb", portraitCoverThumb);
        data.put("avatar_url", c.getStAvatarUrl());
        data.put("cover_url", c.getCoverUrl());
        data.put("creator_handle", "me");
        data.put("private_card", true);
        data.put("review_status", c.getReviewStatus());
        data.put("review_reason", c.getReviewReason());
        data.put("reviewed_at", c.getReviewedAt());
        data.put("reviewed_by", c.getReviewedBy());
        data.put("like_count", 0);
        data.put("dislike_count", 0);
        data.put("is_favorite", false);
        data.put("user_vote", "none");
        data.put("label_array", CharacterJsonSupport.labelArrayFromTagsJson(c.getTagsJson()));
        data.put("occupation_arr", c.getOccupationLabel() == null ? "" : c.getOccupationLabel());
        data.put("gameplay_type", c.getGameplayType() == null ? "" : c.getGameplayType());
        data.put("vip_only", Boolean.TRUE.equals(c.getVipOnly()));
        data.put("token_display", c.getTokenDisplay() == null ? "<2000" : c.getTokenDisplay());
        data.put("unlocked", !Boolean.FALSE.equals(c.getUnlockedDefault()));
        return data;
    }

    private Map<String, Object> toEditor(H5MyCharacter c) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", c.getId());
        data.put("name", c.getName());
        data.put("tagline", c.getTagline());
        data.put("bio", c.getBio());
        data.put("persona", c.getPersona());
        data.put("scenario", c.getScenario());
        data.put("firstMessage", c.getFirstMessage());
        data.put("alternateGreetings", c.getAlternateGreetings() == null ? List.of() : c.getAlternateGreetings());
        data.put("mesExample", c.getMesExample());
        data.put("systemPrompt", c.getSystemPrompt());
        data.put("postHistoryInstructions", c.getPostHistoryInstructions());
        data.put("avatarUrl", c.getAvatarUrl());
        data.put("coverUrl", c.getCoverUrl());
        data.put("tagsJson", c.getTagsJson() == null ? "[]" : c.getTagsJson());
        data.put("occupationLabel", c.getOccupationLabel());
        data.put("gameplayType", c.getGameplayType());
        data.put("vipOnly", Boolean.TRUE.equals(c.getVipOnly()));
        data.put("unlockedDefault", !Boolean.FALSE.equals(c.getUnlockedDefault()));
        data.put("creatorName", c.getCreatorName());
        data.put("creatorHandle", c.getCreatorHandle());
        data.put("tokenDisplay", c.getTokenDisplay());
        data.put("chatModesJson", c.getChatModesJson());
        data.put("sortOrder", c.getSortOrder() != null ? c.getSortOrder() : 0);
        data.put("likeCount", c.getLikeCount() != null ? c.getLikeCount() : 0);
        data.put("dislikeCount", c.getDislikeCount() != null ? c.getDislikeCount() : 0);
        data.put("reviewStatus", c.getReviewStatus());
        data.put("reviewReason", c.getReviewReason());
        data.put("reviewedAt", c.getReviewedAt());
        data.put("reviewedBy", c.getReviewedBy());
        return data;
    }

    private static void apply(H5MyCharacter row, H5MyCharacterSaveRequest req) {
        row.setName(req.getName() == null ? "" : req.getName().trim());
        String bio = req.getBio() == null ? "" : req.getBio().strip();
        String tag = req.getTagline() == null ? "" : req.getTagline().strip();
        row.setDescription(bio.isBlank() ? tag : bio);
        row.setTagline(req.getTagline());
        row.setBio(req.getBio());
        row.setPersona(req.getPersona());
        row.setScenario(req.getScenario());
        row.setFirstMessage(req.getFirstMessage());
        row.setAlternateGreetings(req.getAlternateGreetings());
        row.setMesExample(req.getMesExample());
        row.setSystemPrompt(req.getSystemPrompt());
        row.setPostHistoryInstructions(req.getPostHistoryInstructions());
        row.setAvatarUrl(req.getAvatarUrl());
        row.setCoverUrl(req.getCoverUrl());
        row.setPrivateCard(Boolean.TRUE);
        row.setReviewStatus(CharacterReviewStatus.PENDING);
        row.setReviewReason(null);
        row.setReviewedAt(null);
        row.setReviewedBy(null);
        if (req.getTagsJson() != null) {
            row.setTagsJson(req.getTagsJson());
        }
        if (req.getOccupationLabel() != null) {
            row.setOccupationLabel(req.getOccupationLabel());
        }
        if (req.getGameplayType() != null) {
            row.setGameplayType(req.getGameplayType());
        }
        if (req.getVipOnly() != null) {
            row.setVipOnly(req.getVipOnly());
        }
        if (req.getUnlockedDefault() != null) {
            row.setUnlockedDefault(req.getUnlockedDefault());
        }
        if (req.getCreatorName() != null) {
            row.setCreatorName(req.getCreatorName());
        }
        if (req.getCreatorHandle() != null) {
            row.setCreatorHandle(req.getCreatorHandle());
        }
        if (req.getTokenDisplay() != null) {
            row.setTokenDisplay(req.getTokenDisplay());
        }
        if (req.getChatModesJson() != null) {
            row.setChatModesJson(req.getChatModesJson());
        }
        if (req.getSortOrder() != null) {
            row.setSortOrder(req.getSortOrder());
        }
        if (req.getLikeCount() != null) {
            row.setLikeCount(req.getLikeCount());
        }
        if (req.getDislikeCount() != null) {
            row.setDislikeCount(req.getDislikeCount());
        }
        applyRequiredDefaults(row);
        row.setUpdatedAt(LocalDateTime.now());
    }

    private static StCharacterDetail toStDetail(H5MyCharacter row) {
        return new StCharacterDetail(
                trimToEmpty(row == null ? null : row.getName()),
                trimToEmpty(row == null ? null : row.getStAvatarUrl()),
                trimToEmpty(row == null ? null : row.getDescription()),
                trimToEmpty(row == null ? null : row.getScenario()),
                trimToEmpty(row == null ? null : row.getFirstMessage()),
                trimToEmpty(row == null ? null : row.getPersona()),
                List.of(),
                row == null || row.getAlternateGreetings() == null ? List.of() : row.getAlternateGreetings(),
                trimToEmpty(row == null ? null : row.getMesExample()),
                trimToEmpty(row == null ? null : row.getSystemPrompt()),
                trimToEmpty(row == null ? null : row.getPostHistoryInstructions()),
                "",
                trimToEmpty(row == null ? null : row.getCreatorName()),
                CharacterJsonSupport.parseStringArrayJson(row == null ? null : row.getStWorldNamesJson()),
                trimToEmpty(row == null ? null : row.getStExtraJson()),
                trimToEmpty(row == null ? null : row.getStExtraJson()));
    }

    private static void applyRequiredDefaults(H5MyCharacter row) {
        if (row.getAlternateGreetings() == null) {
            row.setAlternateGreetings(List.of());
        }
        if (row.getPrivateCard() == null) {
            row.setPrivateCard(Boolean.TRUE);
        }
        if (row.getVipOnly() == null) {
            row.setVipOnly(Boolean.FALSE);
        }
        if (row.getUnlockedDefault() == null) {
            row.setUnlockedDefault(Boolean.TRUE);
        }
        if (row.getTagsJson() == null || row.getTagsJson().isBlank()) {
            row.setTagsJson("[]");
        }
        if (row.getCreatorHandle() == null || row.getCreatorHandle().isBlank()) {
            row.setCreatorHandle("me");
        }
        if (row.getTokenDisplay() == null || row.getTokenDisplay().isBlank()) {
            row.setTokenDisplay("<2000");
        }
        if (row.getSortOrder() == null) {
            row.setSortOrder(0);
        }
        if (row.getLikeCount() == null) {
            row.setLikeCount(0);
        }
        if (row.getDislikeCount() == null) {
            row.setDislikeCount(0);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o instanceof Number number) {
            return number.longValue();
        }
        if (o instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String buildPrivateImportPreservedName(long userId) {
        return "h5_u" + userId + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void applyImportedDetail(H5MyCharacter row, String stAvatarUrl, StCharacterDetail detail) {
        String safeAvatar = trimToEmpty(stAvatarUrl);
        String assetUrl = safeAvatar.isBlank() ? "" : stAssetUrls.resolve(safeAvatar);
        String safeName = clip(firstNonBlank(detail == null ? null : detail.name(), safeAvatar, "Imported character"), 128);
        String safeDescription = clip(trimToEmpty(detail == null ? null : detail.description()), 6000);

        row.setStAvatarUrl(safeAvatar);
        row.setName(safeName);
        row.setDescription(safeDescription);
        row.setTagline(clip(buildImportedTagline(safeDescription), 256));
        row.setBio(safeDescription);
        row.setPersona(clip(trimToEmpty(detail == null ? null : detail.personality()), 6000));
        row.setScenario(clip(trimToEmpty(detail == null ? null : detail.scenario()), 6000));
        row.setFirstMessage(clip(trimToEmpty(detail == null ? null : detail.firstMes()), 6000));
        row.setAlternateGreetings(sanitizeLines(detail == null ? null : detail.alternateGreetings(), 24, 2000));
        row.setMesExample(clip(trimToEmpty(detail == null ? null : detail.mesExample()), 8000));
        row.setSystemPrompt(clip(trimToEmpty(detail == null ? null : detail.systemPrompt()), 6000));
        row.setPostHistoryInstructions(clip(trimToEmpty(detail == null ? null : detail.postHistoryInstructions()), 4000));
        row.setAvatarUrl(assetUrl);
        row.setCoverUrl(assetUrl);
        row.setTagsJson(toJsonArray(detail == null ? null : detail.tags()));
        boolean hasEmbeddedCharacterBook = detail != null && !trimToEmpty(detail.embeddedCharacterBookJson()).isBlank();
        row.setStWorldNamesJson(hasEmbeddedCharacterBook ? null : toJsonArray(detail == null ? null : detail.worldNames()));
        row.setStExtraJson(importedExtraJson(detail));
        row.setOccupationLabel("");
        row.setGameplayType("");
        row.setCreatorName(clip(firstNonBlank(detail == null ? null : detail.creator(), "me"), 120));
        row.setCreatorHandle("me");
        row.setPrivateCard(Boolean.TRUE);
        row.setVipOnly(Boolean.FALSE);
        row.setUnlockedDefault(Boolean.TRUE);
        if (row.getSortOrder() == null) {
            row.setSortOrder(0);
        }
        if (row.getLikeCount() == null) {
            row.setLikeCount(0);
        }
        if (row.getDislikeCount() == null) {
            row.setDislikeCount(0);
        }
        if (row.getTokenDisplay() == null || row.getTokenDisplay().isBlank()) {
            row.setTokenDisplay("<2000");
        }
        row.setUpdatedAt(LocalDateTime.now());
    }

    private static String importedExtraJson(StCharacterDetail detail) {
        if (detail == null) {
            return "";
        }
        String rawJson = trimToEmpty(detail.rawJson());
        if (!rawJson.isBlank()) {
            return rawJson;
        }
        return trimToEmpty(detail.embeddedCharacterBookJson());
    }

    private static String extractImportedAvatarUrl(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object fileName = map.get("file_name");
            if (fileName != null) {
                return trimToEmpty(String.valueOf(fileName));
            }
            String avatar = firstNonBlank(
                    valueAsString(map.get("avatar")),
                    valueAsString(map.get("avatar_url")),
                    valueAsString(map.get("avatarUrl"))
            );
            if (!avatar.isBlank()) {
                return avatar.trim();
            }
        }
        return "";
    }

    private static String extractImportError(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return "";
        }
        Object error = map.get("error");
        if (error instanceof Boolean b && !b) {
            return "";
        }
        String message = firstNonBlank(
                valueAsString(map.get("message")),
                valueAsString(map.get("detail")),
                valueAsString(map.get("error"))
        );
        return normalizeImportMessage(message);
    }

    private static String resolveImportErrorMessage(Throwable error) {
        Throwable cursor = error;
        String lastMessage = "";
        while (cursor != null) {
            if (cursor instanceof RestClientResponseException responseException) {
                String responseBody = responseException.getResponseBodyAsString();
                String normalized = normalizeImportMessage(responseBody);
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
            String message = normalizeImportMessage(cursor.getMessage());
            if (!message.isBlank()) {
                lastMessage = message;
            }
            cursor = cursor.getCause();
        }
        return lastMessage.isBlank() ? "PNG import failed. Please make sure this is a valid ST character card PNG." : lastMessage;
    }
    private static String normalizeImportMessage(String message) {
        String safe = trimToEmpty(message);
        if (safe.isBlank()) {
            return "";
        }
        String lower = safe.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to read character data")
                || lower.contains("unexpected end of json input")
                || lower.contains("json.parse")
                || lower.contains("invalid png")
                || lower.contains("metadata")) {
            return "This is not a valid ST character card PNG, or its metadata is missing. Please use the original PNG exported by ST.";
        }
        if (lower.contains("unsupported format")) {
            return "Only ST-exported character card PNG files are supported.";
        }
        if (lower.contains("too large")) {
            return "The uploaded PNG is too large. Please try a smaller file.";
        }
        if (lower.contains("service unavailable") || lower.contains("temporarily unavailable")) {
            return "SillyTavern is currently unavailable. Please make sure ST is running first.";
        }
        if (lower.contains("duplicate") || lower.contains("duplicata") || lower.contains("uk_character_st_avatar_url")) {
            return "The character card could not be written because the old global unique index is still active. Restart the backend so the migration can finish, then try again.";
        }
        return safe;
    }
    private static String buildImportedTagline(String description) {
        String safe = trimToEmpty(description);
        if (safe.isBlank()) {
            return "Imported from PNG";
        }
        if (safe.length() <= 56) {
            return safe;
        }
        return safe.substring(0, 56).replaceAll("\\s+$", "") + "...";
    }
    private static List<String> sanitizeLines(List<String> lines, int maxItems, int maxLength) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (String item : lines) {
            String safe = clip(trimToEmpty(item), maxLength);
            if (!safe.isBlank()) {
                out.add(safe);
            }
            if (out.size() >= Math.max(1, maxItems)) {
                break;
            }
        }
        return out;
    }

    private static String toJsonArray(List<String> values) {
        List<String> safe = sanitizeLines(values, 64, 128);
        if (safe.isEmpty()) {
            return "[]";
        }
        try {
            return JSON.writeValueAsString(safe);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safe = trimToEmpty(value);
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "";
    }

    private static String valueAsString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int maxLength) {
        String safe = trimToEmpty(value);
        if (safe.length() <= Math.max(0, maxLength)) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength));
    }

    private void recordAutoScreen(Long characterId) {
        if (characterId == null || characterId <= 0) {
            return;
        }
        AppCharacter character = appCharacterMapper.findById(characterId);
        if (character == null || !Boolean.TRUE.equals(character.getPrivateCard())) {
            return;
        }
        CharacterContentScreeningService.ScreeningResult screening = characterContentScreeningService.screen(character);
        reviewAuditLogService.recordAutoScreen(character, screening, "system_auto_screen");
    }
}
