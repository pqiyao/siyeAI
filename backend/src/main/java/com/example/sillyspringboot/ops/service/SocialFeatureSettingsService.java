package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.SocialFeatureSettings;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class SocialFeatureSettingsService {

    private static final String SETTING_KEY = "social_feature_settings";
    public static final String CHAT_POLICY_ALL = "all";
    public static final String CHAT_POLICY_MUTUAL_FOLLOW = "mutual_follow";
    public static final String CHAT_POLICY_FRIEND_ONLY = "friend_only";
    public static final String CHAT_POLICY_CLOSED = "closed";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppRuntimeSettingMapper runtimeSettingMapper;

    public SocialFeatureSettingsService(AppRuntimeSettingMapper runtimeSettingMapper) {
        this.runtimeSettingMapper = runtimeSettingMapper;
    }

    @Transactional(readOnly = true)
    public SocialFeatureSettings getSettings() {
        AppRuntimeSetting raw = runtimeSettingMapper.findByKey(SETTING_KEY);
        if (raw == null || raw.getSettingValue() == null || raw.getSettingValue().isBlank()) {
            return normalize(new SocialFeatureSettings());
        }
        try {
            return normalize(objectMapper.readValue(raw.getSettingValue(), SocialFeatureSettings.class));
        } catch (Exception ignored) {
            return normalize(new SocialFeatureSettings());
        }
    }

    @Transactional
    public SocialFeatureSettings saveSettings(Map<String, Object> body) {
        SocialFeatureSettings settings = getSettings();
        if (body != null) {
            settings.setCommunityEnabled(boolVal(body.get("communityEnabled"), settings.isCommunityEnabled()));
            settings.setCommunityEntryVisible(boolVal(body.get("communityEntryVisible"), settings.isCommunityEntryVisible()));
            settings.setGuestCommunityReadEnabled(boolVal(body.get("guestCommunityReadEnabled"), settings.isGuestCommunityReadEnabled()));
            settings.setPostCreateEnabled(boolVal(body.get("postCreateEnabled"), settings.isPostCreateEnabled()));
            settings.setPostImageEnabled(boolVal(body.get("postImageEnabled"), settings.isPostImageEnabled()));
            settings.setLikeEnabled(boolVal(body.get("likeEnabled"), settings.isLikeEnabled()));
            settings.setCommentEnabled(boolVal(body.get("commentEnabled"), settings.isCommentEnabled()));
            settings.setFollowEnabled(boolVal(body.get("followEnabled"), settings.isFollowEnabled()));
            settings.setPostMessageEntryVisible(boolVal(body.get("postMessageEntryVisible"), settings.isPostMessageEntryVisible()));
            settings.setPostPublishMode(strVal(body.get("postPublishMode"), settings.getPostPublishMode()));

            settings.setChatEnabled(boolVal(body.get("chatEnabled"), settings.isChatEnabled()));
            settings.setChatEntryVisible(boolVal(body.get("chatEntryVisible"), settings.isChatEntryVisible()));
            settings.setNewChatEnabled(boolVal(body.get("newChatEnabled"), settings.isNewChatEnabled()));
            settings.setExistingChatEnabled(boolVal(body.get("existingChatEnabled"), settings.isExistingChatEnabled()));
            settings.setTextMessageEnabled(boolVal(body.get("textMessageEnabled"), settings.isTextMessageEnabled()));
            settings.setImageMessageEnabled(boolVal(body.get("imageMessageEnabled"), settings.isImageMessageEnabled()));
            settings.setMessageRecallEnabled(boolVal(body.get("messageRecallEnabled"), settings.isMessageRecallEnabled()));
            settings.setOnlineStatusVisible(boolVal(body.get("onlineStatusVisible"), settings.isOnlineStatusVisible()));
            settings.setPrivateChatPolicy(strVal(body.get("privateChatPolicy"), settings.getPrivateChatPolicy()));

            settings.setFriendEnabled(boolVal(body.get("friendEnabled"), settings.isFriendEnabled()));
            settings.setFriendRequestEnabled(boolVal(body.get("friendRequestEnabled"), settings.isFriendRequestEnabled()));
            settings.setFriendEntryVisible(boolVal(body.get("friendEntryVisible"), settings.isFriendEntryVisible()));
            settings.setNonFriendChatEnabled(boolVal(body.get("nonFriendChatEnabled"), settings.isNonFriendChatEnabled()));
            settings.setFriendRequestApprovalRequired(boolVal(body.get("friendRequestApprovalRequired"), settings.isFriendRequestApprovalRequired()));
            settings.setBlockEnabled(boolVal(body.get("blockEnabled"), settings.isBlockEnabled()));
        }
        settings = normalize(settings);
        runtimeSettingMapper.upsert(SETTING_KEY, writeJson(settings));
        return settings;
    }

    public Map<String, Object> toMap(SocialFeatureSettings settings) {
        SocialFeatureSettings safe = normalize(settings);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("communityEnabled", safe.isCommunityEnabled());
        data.put("communityEntryVisible", safe.isCommunityEntryVisible());
        data.put("guestCommunityReadEnabled", safe.isGuestCommunityReadEnabled());
        data.put("postCreateEnabled", safe.isPostCreateEnabled());
        data.put("postImageEnabled", safe.isPostImageEnabled());
        data.put("likeEnabled", safe.isLikeEnabled());
        data.put("commentEnabled", safe.isCommentEnabled());
        data.put("followEnabled", safe.isFollowEnabled());
        data.put("postMessageEntryVisible", safe.isPostMessageEntryVisible());
        data.put("postPublishMode", safe.getPostPublishMode());
        data.put("chatEnabled", safe.isChatEnabled());
        data.put("chatEntryVisible", safe.isChatEntryVisible());
        data.put("newChatEnabled", safe.isNewChatEnabled());
        data.put("existingChatEnabled", safe.isExistingChatEnabled());
        data.put("textMessageEnabled", safe.isTextMessageEnabled());
        data.put("imageMessageEnabled", safe.isImageMessageEnabled());
        data.put("messageRecallEnabled", safe.isMessageRecallEnabled());
        data.put("onlineStatusVisible", safe.isOnlineStatusVisible());
        data.put("privateChatPolicy", safe.getPrivateChatPolicy());
        data.put("friendEnabled", safe.isFriendEnabled());
        data.put("friendRequestEnabled", safe.isFriendRequestEnabled());
        data.put("friendEntryVisible", safe.isFriendEntryVisible());
        data.put("nonFriendChatEnabled", safe.isNonFriendChatEnabled());
        data.put("friendRequestApprovalRequired", safe.isFriendRequestApprovalRequired());
        data.put("blockEnabled", safe.isBlockEnabled());
        return data;
    }

    public void ensureCommunityReadable(boolean loggedIn) {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "社区暂未开放");
        }
        if (!loggedIn && !settings.isGuestCommunityReadEnabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }

    public void ensurePostCreateEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isPostCreateEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭动态发布");
        }
        if ("review".equals(settings.getPostPublishMode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "动态先审后发模式尚未接入");
        }
        if ("closed".equals(settings.getPostPublishMode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已暂停动态发布");
        }
    }

    public void ensurePostImageEnabled(boolean hasImages) {
        SocialFeatureSettings settings = getSettings();
        if (hasImages && !settings.isPostImageEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭图片动态");
        }
    }

    public void ensureLikeEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isLikeEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭点赞功能");
        }
    }

    public void ensureCommentEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isCommentEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭评论功能");
        }
    }

    public void ensureFollowEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isFollowEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭关注功能");
        }
    }

    public void ensureFriendFeatureEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isFriendEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "好友功能暂未开放");
        }
    }

    public void ensureFriendRequestEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isFriendEnabled() || !settings.isFriendRequestEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "好友申请暂未开放");
        }
    }

    public void ensureBlockEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isCommunityEnabled() || !settings.isFriendEnabled() || !settings.isBlockEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "黑名单功能暂未开放");
        }
    }

    public boolean isFriendApprovalRequired() {
        SocialFeatureSettings settings = getSettings();
        return settings.isFriendEnabled()
                && settings.isFriendRequestEnabled()
                && settings.isFriendRequestApprovalRequired();
    }

    public void ensureChatEntryEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isChatEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "真人聊天暂未开放");
        }
    }

    public void ensureChatReadable() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isChatEnabled() || !settings.isExistingChatEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭真人聊天");
        }
    }

    public void ensureCanSendMessage(String messageType, boolean existingConversation, boolean relationAllowed) {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isChatEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "真人聊天暂未开放");
        }
        if (!existingConversation && !settings.isNewChatEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭发起新私聊");
        }
        if (existingConversation && !settings.isExistingChatEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭继续已有会话");
        }
        if (CHAT_POLICY_CLOSED.equals(settings.getPrivateChatPolicy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭私聊");
        }
        if (!relationAllowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, privateChatPolicyMessage(settings));
        }
        if ("image".equals(messageType) && !settings.isImageMessageEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭图片消息");
        }
        if ("text".equals(messageType) && !settings.isTextMessageEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭文字消息");
        }
    }

    public void ensureRecallEnabled() {
        SocialFeatureSettings settings = getSettings();
        if (!settings.isChatEnabled() || !settings.isMessageRecallEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭消息撤回");
        }
    }

    public boolean requiresMutualFollow() {
        return CHAT_POLICY_MUTUAL_FOLLOW.equals(getSettings().getPrivateChatPolicy());
    }

    public boolean requiresFriendOnly() {
        return CHAT_POLICY_FRIEND_ONLY.equals(getSettings().getPrivateChatPolicy());
    }

    public boolean requiresPrivateChatRelation() {
        SocialFeatureSettings settings = getSettings();
        String policy = settings.getPrivateChatPolicy();
        return CHAT_POLICY_MUTUAL_FOLLOW.equals(policy)
                || CHAT_POLICY_FRIEND_ONLY.equals(policy)
                || (CHAT_POLICY_ALL.equals(policy) && !settings.isNonFriendChatEnabled());
    }

    private String writeJson(SocialFeatureSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize social feature settings", e);
        }
    }

    private static SocialFeatureSettings normalize(SocialFeatureSettings settings) {
        SocialFeatureSettings safe = settings == null ? new SocialFeatureSettings() : settings;
        safe.setPostPublishMode(normalizePostPublishMode(safe.getPostPublishMode()));
        safe.setPrivateChatPolicy(normalizePrivateChatPolicy(safe.getPrivateChatPolicy()));
        return safe;
    }

    private static String normalizePostPublishMode(String value) {
        String safe = normalizeKey(value, "direct");
        if ("direct".equals(safe) || "review".equals(safe) || "closed".equals(safe)) {
            return safe;
        }
        return "direct";
    }

    private static String normalizePrivateChatPolicy(String value) {
        String safe = normalizeKey(value, CHAT_POLICY_ALL);
        if (CHAT_POLICY_ALL.equals(safe)
                || CHAT_POLICY_MUTUAL_FOLLOW.equals(safe)
                || CHAT_POLICY_FRIEND_ONLY.equals(safe)
                || CHAT_POLICY_CLOSED.equals(safe)) {
            return safe;
        }
        return CHAT_POLICY_ALL;
    }

    private static String privateChatPolicyMessage(SocialFeatureSettings settings) {
        String policy = settings.getPrivateChatPolicy();
        if (CHAT_POLICY_MUTUAL_FOLLOW.equals(policy)) {
            return "互相关注后才能私聊";
        }
        if (CHAT_POLICY_FRIEND_ONLY.equals(policy)) {
            return "成为好友后才能私聊";
        }
        if (CHAT_POLICY_ALL.equals(policy) && !settings.isNonFriendChatEnabled()) {
            return "互相关注后才能私聊";
        }
        return "当前不能发起私聊";
    }

    private static String normalizeKey(String value, String fallback) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return safe.isEmpty() ? fallback : safe;
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static String strVal(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
