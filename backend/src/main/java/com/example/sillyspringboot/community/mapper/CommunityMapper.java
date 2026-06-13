package com.example.sillyspringboot.community.mapper;

import com.example.sillyspringboot.community.entity.CommunityComment;
import com.example.sillyspringboot.community.entity.CommunityCommentReply;
import com.example.sillyspringboot.community.entity.CommunityPost;
import com.example.sillyspringboot.community.entity.CommunityPostMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommunityMapper {

    int insertPost(CommunityPost post);

    int insertPostMedia(CommunityPostMedia media);

    int countActiveBlockBetween(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    Map<String, Object> findUserCard(@Param("userId") long userId);

    Map<String, Object> findPostCard(@Param("postId") long postId, @Param("viewerId") Long viewerId);

    Map<String, Object> findPostOwner(@Param("postId") long postId);

    long countFeed(@Param("feed") String feed, @Param("viewerId") Long viewerId, @Param("userId") Long userId);

    List<Map<String, Object>> listFeed(
            @Param("feed") String feed,
            @Param("viewerId") Long viewerId,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<Map<String, Object>> listMediaByPostIds(@Param("postIds") List<Long> postIds);

    int incrementPostView(@Param("postId") long postId);

    int countLike(@Param("postId") long postId, @Param("userId") long userId);

    int insertLike(@Param("postId") long postId, @Param("userId") long userId, @Param("toUserId") long toUserId);

    int deleteLike(@Param("postId") long postId, @Param("userId") long userId);

    int incrementLikeCount(@Param("postId") long postId);

    int decrementLikeCount(@Param("postId") long postId);

    int insertComment(CommunityComment comment);

    Map<String, Object> findComment(@Param("commentId") long commentId);

    List<Map<String, Object>> listComments(
            @Param("postId") long postId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int insertReply(CommunityCommentReply reply);

    List<Map<String, Object>> listReplies(
            @Param("commentId") long commentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int incrementPostCommentCount(@Param("postId") long postId);

    int incrementCommentReplyCount(@Param("commentId") long commentId);

    int countFollow(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

    int insertFollow(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

    int deleteFollow(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

    Map<String, Object> findFriendRequest(@Param("requesterUserId") long requesterUserId, @Param("targetUserId") long targetUserId);

    Map<String, Object> findPendingFriendRequest(@Param("requesterUserId") long requesterUserId, @Param("targetUserId") long targetUserId);

    int upsertFriendRequest(
            @Param("requesterUserId") long requesterUserId,
            @Param("targetUserId") long targetUserId,
            @Param("requestMessage") String requestMessage
    );

    int updateFriendRequestStatus(
            @Param("requestId") long requestId,
            @Param("status") String status,
            @Param("handledByUserId") long handledByUserId
    );

    int updatePendingFriendRequestStatusByPair(
            @Param("requesterUserId") long requesterUserId,
            @Param("targetUserId") long targetUserId,
            @Param("status") String status,
            @Param("handledByUserId") long handledByUserId
    );

    Map<String, Object> findFriendRequestById(@Param("requestId") long requestId);

    int upsertFriend(
            @Param("userId") long userId,
            @Param("friendUserId") long friendUserId,
            @Param("requestId") Long requestId
    );

    int removeFriend(@Param("userId") long userId, @Param("friendUserId") long friendUserId);

    int countFriendPair(@Param("userId") long userId, @Param("friendUserId") long friendUserId);

    int upsertBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId, @Param("reason") String reason);

    int removeBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    long countFriendRequests(
            @Param("userId") long userId,
            @Param("box") String box,
            @Param("status") String status
    );

    List<Map<String, Object>> listFriendRequests(
            @Param("userId") long userId,
            @Param("box") String box,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countFriends(@Param("userId") long userId, @Param("relation") String relation);

    List<Map<String, Object>> listFriends(
            @Param("userId") long userId,
            @Param("relation") String relation,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminFriendRequests(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId
    );

    List<Map<String, Object>> listAdminFriendRequests(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminFriends(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId
    );

    List<Map<String, Object>> listAdminFriends(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminPosts(@Param("keyword") String keyword, @Param("status") String status, @Param("userId") Long userId);

    List<Map<String, Object>> listAdminPosts(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findAdminPost(@Param("postId") long postId);

    int updatePostStatus(@Param("postId") long postId, @Param("status") String status);

    int reviewPost(
            @Param("postId") long postId,
            @Param("status") String status,
            @Param("reviewNote") String reviewNote,
            @Param("reviewedBy") String reviewedBy
    );

    int softDeletePost(@Param("postId") long postId);

    long countAdminComments(
            @Param("keyword") String keyword,
            @Param("postId") Long postId,
            @Param("userId") Long userId
    );

    List<Map<String, Object>> listAdminComments(
            @Param("keyword") String keyword,
            @Param("postId") Long postId,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findAdminComment(@Param("commentId") long commentId);

    List<Map<String, Object>> listAdminReplies(@Param("commentId") long commentId);

    int softDeleteComment(@Param("commentId") long commentId);

    int softDeleteRepliesByComment(@Param("commentId") long commentId);

    int decrementPostCommentCountBy(@Param("postId") long postId, @Param("delta") long delta);
}
