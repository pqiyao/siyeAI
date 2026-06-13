package com.example.sillyspringboot.community.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.community.entity.CommunityComment;
import com.example.sillyspringboot.community.entity.CommunityCommentReply;
import com.example.sillyspringboot.community.entity.CommunityPost;
import com.example.sillyspringboot.community.entity.CommunityPostMedia;
import com.example.sillyspringboot.community.mapper.CommunityMapper;
import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.upload.service.AppUploadedAssetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CommunityService {

    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_HIDDEN = "hidden";
    private static final String FRIEND_REQUEST_PENDING = "pending";
    private static final String FRIEND_REQUEST_ACCEPTED = "accepted";
    private static final String FRIEND_REQUEST_REJECTED = "rejected";
    private static final int MAX_POST_IMAGES = 9;

    private final CommunityMapper mapper;
    private final AppUploadedAssetService uploadedAssetService;
    private final SocialFeatureSettingsService socialSettingsService;

    public CommunityService(
            CommunityMapper mapper,
            AppUploadedAssetService uploadedAssetService,
            SocialFeatureSettingsService socialSettingsService
    ) {
        this.mapper = mapper;
        this.uploadedAssetService = uploadedAssetService;
        this.socialSettingsService = socialSettingsService;
    }

    @Transactional
    public Map<String, Object> createPost(AppUser user, Map<String, Object> body) {
        long userId = requireUserId(user);
        String content = limit(trimToNull(str(body == null ? null : body.get("content"))), 3000);
        List<String> mediaList = normalizeMediaList(body == null ? null : body.get("mediaList"));
        if ((content == null || content.isBlank()) && mediaList.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "内容或图片至少填写一项");
        }
        socialSettingsService.ensurePostImageEnabled(!mediaList.isEmpty());
        for (String mediaKey : mediaList) {
            uploadedAssetService.requireOwnedImage(userId, mediaKey);
        }

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setContent(content);
        post.setSourceType(mediaList.isEmpty() ? "text" : "image");
        post.setStatus(STATUS_NORMAL);
        post.setOpenComments(1);
        mapper.insertPost(post);

        for (int i = 0; i < mediaList.size(); i++) {
            CommunityPostMedia media = new CommunityPostMedia();
            media.setPostId(post.getId());
            media.setUserId(userId);
            media.setMediaKey(mediaList.get(i));
            media.setStorageProvider("local");
            media.setMediaUrlSnapshot(mediaList.get(i));
            media.setMediaType("image");
            media.setSortOrder(i);
            mapper.insertPostMedia(media);
        }
        return detail(post.getId(), user, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String feed, Long userId, AppUser viewer, int pageNum, int pageSize) {
        Long viewerId = viewer == null ? null : viewer.getId();
        String safeFeed = normalizeFeed(feed);
        if ("following".equals(safeFeed) && viewerId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(50, pageSize));
        long total = mapper.countFeed(safeFeed, viewerId, userId);
        List<Map<String, Object>> rows = attachMedia(mapper.listFeed(
                safeFeed,
                viewerId,
                userId,
                (safePage - 1) * safeSize,
                safeSize
        ));
        return Map.of("total", total, "rows", rows);
    }

    @Transactional
    public Map<String, Object> detail(long postId, AppUser viewer, boolean increaseView) {
        Long viewerId = viewer == null ? null : viewer.getId();
        if (increaseView) {
            mapper.incrementPostView(postId);
        }
        Map<String, Object> post = mapper.findPostCard(postId, viewerId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        attachMedia(List.of(post));
        post.put("comments", listComments(postId, 1, 20));
        return post;
    }

    @Transactional
    public Map<String, Object> like(long postId, AppUser user) {
        long userId = requireUserId(user);
        Map<String, Object> owner = requirePostOwner(postId);
        long ownerId = longRequired(owner.get("userId"), "动态不存在");
        if (mapper.countLike(postId, userId) == 0) {
            mapper.insertLike(postId, userId, ownerId);
            mapper.incrementLikeCount(postId);
        }
        return detail(postId, user, false);
    }

    @Transactional
    public Map<String, Object> unlike(long postId, AppUser user) {
        long userId = requireUserId(user);
        requirePostOwner(postId);
        if (mapper.deleteLike(postId, userId) > 0) {
            mapper.decrementLikeCount(postId);
        }
        return detail(postId, user, false);
    }

    @Transactional
    public Map<String, Object> addComment(long postId, AppUser user, Map<String, Object> body) {
        long userId = requireUserId(user);
        requirePostOwner(postId);
        String content = limit(requiredText(body == null ? null : body.get("content"), "评论不能为空"), 1000);
        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus(STATUS_NORMAL);
        mapper.insertComment(comment);
        mapper.incrementPostCommentCount(postId);
        return detail(postId, user, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listComments(long postId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        List<Map<String, Object>> comments = mapper.listComments(postId, (safePage - 1) * safeSize, safeSize);
        for (Map<String, Object> comment : comments) {
            Long commentId = longOrNull(comment.get("commentId"));
            if (commentId != null) {
                comment.put("replies", mapper.listReplies(commentId, 0, 3));
            } else {
                comment.put("replies", List.of());
            }
        }
        return comments;
    }

    @Transactional
    public Map<String, Object> addReply(long commentId, AppUser user, Map<String, Object> body) {
        long userId = requireUserId(user);
        Map<String, Object> comment = mapper.findComment(commentId);
        if (comment == null || comment.get("deletedAt") != null || !STATUS_NORMAL.equals(str(comment.get("status")))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Long postId = longOrNull(comment.get("postId"));
        if (postId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        String content = limit(requiredText(body == null ? null : body.get("content"), "回复不能为空"), 1000);
        Long toUserId = longOrNull(body == null ? null : body.get("toUserId"));
        CommunityCommentReply reply = new CommunityCommentReply();
        reply.setCommentId(commentId);
        reply.setPostId(postId);
        reply.setFromUserId(userId);
        reply.setToUserId(toUserId);
        reply.setContent(content);
        reply.setStatus(STATUS_NORMAL);
        mapper.insertReply(reply);
        mapper.incrementCommentReplyCount(commentId);
        mapper.incrementPostCommentCount(postId);
        return detail(postId, user, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReplies(long commentId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return mapper.listReplies(commentId, (safePage - 1) * safeSize, safeSize);
    }

    @Transactional
    public Map<String, Object> follow(long targetUserId, AppUser user) {
        long userId = requireUserId(user);
        if (userId == targetUserId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能关注自己");
        }
        if (mapper.countFollow(userId, targetUserId) == 0) {
            mapper.insertFollow(userId, targetUserId);
        }
        return Map.of("followed", true);
    }

    @Transactional
    public Map<String, Object> unfollow(long targetUserId, AppUser user) {
        long userId = requireUserId(user);
        mapper.deleteFollow(userId, targetUserId);
        return Map.of("followed", false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> friends(AppUser user, String relation, int pageNum, int pageSize) {
        long userId = requireUserId(user);
        String safeRelation = normalizeFriendRelation(relation);
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(50, pageSize));
        long total = mapper.countFriends(userId, safeRelation);
        List<Map<String, Object>> rows = mapper.listFriends(
                        userId,
                        safeRelation,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(CommunityService::normalizeFriendDto)
                .toList();
        return Map.of(
                "relation", safeRelation,
                "total", total,
                "rows", rows
        );
    }

    @Transactional
    public Map<String, Object> requestFriend(AppUser user, long targetUserId, Map<String, Object> body) {
        long userId = requireUserId(user);
        if (userId == targetUserId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能添加自己为好友");
        }
        requireActiveUser(targetUserId);
        ensureNotBlocked(userId, targetUserId);
        if (mapper.countFriendPair(userId, targetUserId) > 0) {
            return friendResult(targetUserId, "friend", "already_friend");
        }
        Map<String, Object> incomingRequest = mapper.findPendingFriendRequest(targetUserId, userId);
        if (incomingRequest != null) {
            Long incomingRequestId = longOrNull(incomingRequest.get("requestId"));
            if (incomingRequestId != null) {
                mapper.updateFriendRequestStatus(incomingRequestId, FRIEND_REQUEST_ACCEPTED, userId);
            }
            mapper.updatePendingFriendRequestStatusByPair(userId, targetUserId, FRIEND_REQUEST_ACCEPTED, userId);
            createFriendPair(userId, targetUserId, incomingRequestId);
            return friendResult(targetUserId, "friend", "accepted_incoming", incomingRequestId);
        }
        String message = limit(trimToNull(str(body == null ? null : body.get("message"))), 255);
        mapper.upsertFriendRequest(userId, targetUserId, message);
        Map<String, Object> request = mapper.findFriendRequest(userId, targetUserId);
        Long requestId = longOrNull(request == null ? null : request.get("requestId"));
        if (!socialSettingsService.isFriendApprovalRequired()) {
            if (requestId != null) {
                mapper.updateFriendRequestStatus(requestId, FRIEND_REQUEST_ACCEPTED, targetUserId);
            }
            createFriendPair(userId, targetUserId, requestId);
            return friendResult(targetUserId, "friend", "accepted");
        }
        return friendResult(targetUserId, FRIEND_REQUEST_PENDING, "requested", requestId);
    }

    @Transactional
    public Map<String, Object> handleFriendRequest(AppUser user, long requestId, String action) {
        long userId = requireUserId(user);
        Map<String, Object> request = mapper.findFriendRequestById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "好友申请不存在");
        }
        long requesterUserId = longRequired(request.get("requesterUserId"), "好友申请不存在");
        long targetUserId = longRequired(request.get("targetUserId"), "好友申请不存在");
        if (targetUserId != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能处理这条好友申请");
        }
        String status = trimToNull(str(request.get("status")));
        if (!FRIEND_REQUEST_PENDING.equals(status)) {
            return friendResult(requesterUserId, status == null ? "" : status, "handled", requestId);
        }
        String safeAction = normalizeFriendRequestAction(action);
        if (FRIEND_REQUEST_ACCEPTED.equals(safeAction)) {
            ensureNotBlocked(userId, requesterUserId);
            mapper.updateFriendRequestStatus(requestId, FRIEND_REQUEST_ACCEPTED, userId);
            mapper.updatePendingFriendRequestStatusByPair(userId, requesterUserId, FRIEND_REQUEST_ACCEPTED, userId);
            createFriendPair(userId, requesterUserId, requestId);
            return friendResult(requesterUserId, "friend", "accepted", requestId);
        }
        mapper.updateFriendRequestStatus(requestId, FRIEND_REQUEST_REJECTED, userId);
        return friendResult(requesterUserId, FRIEND_REQUEST_REJECTED, "rejected", requestId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> friendRequests(AppUser user, String box, String status, int pageNum, int pageSize) {
        long userId = requireUserId(user);
        String safeBox = normalizeFriendRequestBox(box);
        String safeStatus = normalizeFriendRequestStatusOptional(status);
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(50, pageSize));
        long total = mapper.countFriendRequests(userId, safeBox, safeStatus);
        List<Map<String, Object>> rows = mapper.listFriendRequests(
                        userId,
                        safeBox,
                        safeStatus,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(row -> normalizeFriendRequestDto(row, userId))
                .toList();
        return Map.of(
                "box", safeBox,
                "status", safeStatus == null ? "all" : safeStatus,
                "total", total,
                "rows", rows
        );
    }

    @Transactional
    public Map<String, Object> removeFriend(AppUser user, long friendUserId) {
        long userId = requireUserId(user);
        if (userId == friendUserId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "好友对象不正确");
        }
        mapper.removeFriend(userId, friendUserId);
        mapper.removeFriend(friendUserId, userId);
        return friendResult(friendUserId, "removed", "removed");
    }

    @Transactional
    public Map<String, Object> blockUser(AppUser user, long blockedUserId, Map<String, Object> body) {
        long userId = requireUserId(user);
        if (userId == blockedUserId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能拉黑自己");
        }
        requireActiveUser(blockedUserId);
        String reason = limit(trimToNull(str(body == null ? null : body.get("reason"))), 255);
        mapper.upsertBlock(userId, blockedUserId, reason);
        mapper.removeFriend(userId, blockedUserId);
        mapper.removeFriend(blockedUserId, userId);
        return friendResult(blockedUserId, "blocked", "blocked");
    }

    @Transactional
    public Map<String, Object> unblockUser(AppUser user, long blockedUserId) {
        long userId = requireUserId(user);
        mapper.removeBlock(userId, blockedUserId);
        return friendResult(blockedUserId, "unblocked", "unblocked");
    }

    @Transactional(readOnly = true)
    public long countAdminPosts(String keyword, String status, Long userId) {
        return mapper.countAdminPosts(trimToNull(keyword), normalizeAdminStatusOptional(status), userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminPost(long postId) {
        Map<String, Object> row = mapper.findAdminPost(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        attachMedia(List.of(row));
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminPosts(String keyword, String status, Long userId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return attachMedia(mapper.listAdminPosts(
                trimToNull(keyword),
                normalizeAdminStatusOptional(status),
                userId,
                (safePage - 1) * safeSize,
                safeSize
        ));
    }

    @Transactional
    public Map<String, Object> updateAdminPostStatus(long postId, String status) {
        String safeStatus = normalizeAdminStatus(status);
        if (mapper.updatePostStatus(postId, safeStatus) <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("postId", postId);
        data.put("status", safeStatus);
        return data;
    }

    @Transactional
    public int removeAdminPost(long postId) {
        return mapper.softDeletePost(postId);
    }

    @Transactional(readOnly = true)
    public long countAdminComments(String keyword, Long postId, Long userId) {
        return mapper.countAdminComments(trimToNull(keyword), postId, userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminComments(String keyword, Long postId, Long userId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return mapper.listAdminComments(
                trimToNull(keyword),
                postId,
                userId,
                (safePage - 1) * safeSize,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminComment(long commentId) {
        Map<String, Object> row = mapper.findAdminComment(commentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        row = new LinkedHashMap<>(row);
        row.put("replies", mapper.listAdminReplies(commentId));
        return row;
    }

    @Transactional
    public Map<String, Object> removeAdminComment(long commentId) {
        Map<String, Object> comment = mapper.findAdminComment(commentId);
        if (comment == null || comment.get("deletedAt") != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Long postId = longOrNull(comment.get("postId"));
        long replyCount = longOrZero(comment.get("replyCount"));
        long delta = 1 + Math.max(0L, replyCount);
        int removed = mapper.softDeleteComment(commentId);
        if (removed <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        mapper.softDeleteRepliesByComment(commentId);
        if (postId != null && postId > 0) {
            mapper.decrementPostCommentCountBy(postId, delta);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commentId", commentId);
        data.put("postId", postId);
        data.put("removedReplies", replyCount);
        data.put("decrementedCommentCount", delta);
        return data;
    }

    private List<Map<String, Object>> attachMedia(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        List<Long> postIds = rows.stream()
                .map(row -> longOrNull(row.get("postId")))
                .filter(value -> value != null && value > 0)
                .distinct()
                .toList();
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (!postIds.isEmpty()) {
            for (Map<String, Object> media : mapper.listMediaByPostIds(postIds)) {
                Long postId = longOrNull(media.get("postId"));
                if (postId == null) {
                    continue;
                }
                grouped.computeIfAbsent(postId, key -> new ArrayList<>()).add(media);
            }
        }
        for (Map<String, Object> row : rows) {
            Long postId = longOrNull(row.get("postId"));
            row.put("mediaList", grouped.getOrDefault(postId, List.of()));
            row.put("isLiked", truthy(row.get("liked")));
            row.put("isFollowed", truthy(row.get("followed")));
            row.put("isFriend", truthy(row.get("friend")));
            row.put("friendRequestPending", truthy(row.get("friendRequestPending")));
            row.put("outgoingFriendRequestId", longOrNull(row.get("outgoingFriendRequestId")));
            row.put("incomingFriendRequestPending", truthy(row.get("incomingFriendRequestPending")));
            row.put("incomingFriendRequestId", longOrNull(row.get("incomingFriendRequestId")));
        }
        return rows;
    }

    private Map<String, Object> requirePostOwner(long postId) {
        Map<String, Object> owner = mapper.findPostOwner(postId);
        if (owner == null || owner.get("deletedAt") != null || !STATUS_NORMAL.equals(str(owner.get("status")))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        return owner;
    }

    private static String normalizeFeed(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "following".equals(safe) ? "following" : "recommend";
    }

    private static String normalizeFriendRelation(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("mutual".equals(safe) || "blocked".equals(safe)) {
            return safe;
        }
        return "all";
    }

    private static Map<String, Object> normalizeFriendDto(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("mutual", truthy(row.get("mutual")));
        out.put("followed", truthy(row.get("followed")));
        out.put("followedByPeer", truthy(row.get("followedByPeer")));
        out.put("blocked", truthy(row.get("blocked")));
        out.put("unreadCount", longOrZero(row.get("unreadCount")));
        out.put("isFriend", true);
        return out;
    }

    private void requireActiveUser(long userId) {
        Map<String, Object> user = mapper.findUserCard(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("disabled".equalsIgnoreCase(str(user.get("userStatus")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已停用");
        }
    }

    private void ensureNotBlocked(long userId, long peerUserId) {
        if (mapper.countActiveBlockBetween(userId, peerUserId) > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前无法添加好友");
        }
    }

    private void createFriendPair(long userId, long friendUserId, Long requestId) {
        mapper.upsertFriend(userId, friendUserId, requestId);
        mapper.upsertFriend(friendUserId, userId, requestId);
    }

    private static Map<String, Object> friendResult(long userId, String status, String action) {
        return friendResult(userId, status, action, null);
    }

    private static Map<String, Object> friendResult(long userId, String status, String action, Long requestId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("status", status);
        data.put("action", action);
        if (requestId != null) {
            data.put("requestId", requestId);
        }
        data.put("isFriend", "friend".equals(status));
        data.put("pending", FRIEND_REQUEST_PENDING.equals(status));
        return data;
    }

    private static Map<String, Object> normalizeFriendRequestDto(Map<String, Object> row, long viewerUserId) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        long requesterUserId = longOrZero(row.get("requesterUserId"));
        long targetUserId = longOrZero(row.get("targetUserId"));
        boolean incoming = targetUserId == viewerUserId;
        out.put("incoming", incoming);
        out.put("outgoing", requesterUserId == viewerUserId);
        out.put("peerUserId", incoming ? requesterUserId : targetUserId);
        out.put("peerNickname", incoming ? row.get("requesterNickname") : row.get("targetNickname"));
        out.put("peerAvatar", incoming ? row.get("requesterAvatar") : row.get("targetAvatar"));
        out.put("peerBio", incoming ? row.get("requesterBio") : row.get("targetBio"));
        out.put("peerUserStatus", incoming ? row.get("requesterUserStatus") : row.get("targetUserStatus"));
        return out;
    }

    private static String normalizeFriendRequestAction(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("accept".equals(safe) || FRIEND_REQUEST_ACCEPTED.equals(safe)) {
            return FRIEND_REQUEST_ACCEPTED;
        }
        if ("reject".equals(safe) || FRIEND_REQUEST_REJECTED.equals(safe)) {
            return FRIEND_REQUEST_REJECTED;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "好友申请操作不正确");
    }

    private static String normalizeFriendRequestBox(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "sent".equals(safe) ? "sent" : "received";
    }

    private static String normalizeFriendRequestStatusOptional(String value) {
        String safe = trimToNull(value);
        if (safe == null || "all".equalsIgnoreCase(safe)) {
            return null;
        }
        String normalized = safe.toLowerCase(Locale.ROOT);
        if (FRIEND_REQUEST_PENDING.equals(normalized)
                || FRIEND_REQUEST_ACCEPTED.equals(normalized)
                || FRIEND_REQUEST_REJECTED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "好友申请状态不正确");
    }

    private static String normalizeAdminStatusOptional(String value) {
        String safe = trimToNull(value);
        return safe == null ? null : normalizeAdminStatus(safe);
    }

    private static String normalizeAdminStatus(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (STATUS_NORMAL.equals(safe) || STATUS_HIDDEN.equals(safe)) {
            return safe;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "动态状态不正确");
    }

    private static List<String> normalizeMediaList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addMedia(out, str(item));
            }
        } else if (raw instanceof String text) {
            for (String part : text.split(",")) {
                addMedia(out, part);
            }
        }
        if (out.size() > MAX_POST_IMAGES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "最多上传 9 张图片");
        }
        return List.copyOf(out);
    }

    private static void addMedia(List<String> out, String value) {
        String media = trimToNull(value);
        if (media == null) {
            return;
        }
        if (media.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "图片地址不正确");
        }
        out.add(limit(media, 512));
    }

    private static long requireUserId(AppUser user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return user.getId();
    }

    private static String requiredText(Object value, String message) {
        String text = trimToNull(str(value));
        if (text == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return text;
    }

    private static Long longOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static long longRequired(Object value, String message) {
        Long parsed = longOrNull(value);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, message);
        }
        return parsed;
    }

    private static long longOrZero(Object value) {
        Long parsed = longOrNull(value);
        return parsed == null ? 0L : parsed;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String limit(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
