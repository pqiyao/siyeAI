package com.example.sillyspringboot.ops.dto;

public class SocialFeatureSettings {

    private boolean communityEnabled = true;
    private boolean communityEntryVisible = true;
    private boolean guestCommunityReadEnabled = true;
    private boolean postCreateEnabled = true;
    private boolean postImageEnabled = true;
    private boolean likeEnabled = true;
    private boolean commentEnabled = true;
    private boolean followEnabled = true;
    private boolean postMessageEntryVisible = true;
    private String postPublishMode = "direct";

    private boolean chatEnabled = true;
    private boolean chatEntryVisible = true;
    private boolean newChatEnabled = true;
    private boolean existingChatEnabled = true;
    private boolean textMessageEnabled = true;
    private boolean imageMessageEnabled = true;
    private boolean messageRecallEnabled = true;
    private boolean onlineStatusVisible = true;
    private String privateChatPolicy = "all";

    private boolean friendEnabled = true;
    private boolean friendRequestEnabled = true;
    private boolean friendEntryVisible = true;
    private boolean nonFriendChatEnabled = true;
    private boolean friendRequestApprovalRequired = true;
    private boolean blockEnabled = false;

    public boolean isCommunityEnabled() {
        return communityEnabled;
    }

    public void setCommunityEnabled(boolean communityEnabled) {
        this.communityEnabled = communityEnabled;
    }

    public boolean isCommunityEntryVisible() {
        return communityEntryVisible;
    }

    public void setCommunityEntryVisible(boolean communityEntryVisible) {
        this.communityEntryVisible = communityEntryVisible;
    }

    public boolean isGuestCommunityReadEnabled() {
        return guestCommunityReadEnabled;
    }

    public void setGuestCommunityReadEnabled(boolean guestCommunityReadEnabled) {
        this.guestCommunityReadEnabled = guestCommunityReadEnabled;
    }

    public boolean isPostCreateEnabled() {
        return postCreateEnabled;
    }

    public void setPostCreateEnabled(boolean postCreateEnabled) {
        this.postCreateEnabled = postCreateEnabled;
    }

    public boolean isPostImageEnabled() {
        return postImageEnabled;
    }

    public void setPostImageEnabled(boolean postImageEnabled) {
        this.postImageEnabled = postImageEnabled;
    }

    public boolean isLikeEnabled() {
        return likeEnabled;
    }

    public void setLikeEnabled(boolean likeEnabled) {
        this.likeEnabled = likeEnabled;
    }

    public boolean isCommentEnabled() {
        return commentEnabled;
    }

    public void setCommentEnabled(boolean commentEnabled) {
        this.commentEnabled = commentEnabled;
    }

    public boolean isFollowEnabled() {
        return followEnabled;
    }

    public void setFollowEnabled(boolean followEnabled) {
        this.followEnabled = followEnabled;
    }

    public boolean isPostMessageEntryVisible() {
        return postMessageEntryVisible;
    }

    public void setPostMessageEntryVisible(boolean postMessageEntryVisible) {
        this.postMessageEntryVisible = postMessageEntryVisible;
    }

    public String getPostPublishMode() {
        return postPublishMode;
    }

    public void setPostPublishMode(String postPublishMode) {
        this.postPublishMode = postPublishMode;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }

    public boolean isChatEntryVisible() {
        return chatEntryVisible;
    }

    public void setChatEntryVisible(boolean chatEntryVisible) {
        this.chatEntryVisible = chatEntryVisible;
    }

    public boolean isNewChatEnabled() {
        return newChatEnabled;
    }

    public void setNewChatEnabled(boolean newChatEnabled) {
        this.newChatEnabled = newChatEnabled;
    }

    public boolean isExistingChatEnabled() {
        return existingChatEnabled;
    }

    public void setExistingChatEnabled(boolean existingChatEnabled) {
        this.existingChatEnabled = existingChatEnabled;
    }

    public boolean isTextMessageEnabled() {
        return textMessageEnabled;
    }

    public void setTextMessageEnabled(boolean textMessageEnabled) {
        this.textMessageEnabled = textMessageEnabled;
    }

    public boolean isImageMessageEnabled() {
        return imageMessageEnabled;
    }

    public void setImageMessageEnabled(boolean imageMessageEnabled) {
        this.imageMessageEnabled = imageMessageEnabled;
    }

    public boolean isMessageRecallEnabled() {
        return messageRecallEnabled;
    }

    public void setMessageRecallEnabled(boolean messageRecallEnabled) {
        this.messageRecallEnabled = messageRecallEnabled;
    }

    public boolean isOnlineStatusVisible() {
        return onlineStatusVisible;
    }

    public void setOnlineStatusVisible(boolean onlineStatusVisible) {
        this.onlineStatusVisible = onlineStatusVisible;
    }

    public String getPrivateChatPolicy() {
        return privateChatPolicy;
    }

    public void setPrivateChatPolicy(String privateChatPolicy) {
        this.privateChatPolicy = privateChatPolicy;
    }

    public boolean isFriendEnabled() {
        return friendEnabled;
    }

    public void setFriendEnabled(boolean friendEnabled) {
        this.friendEnabled = friendEnabled;
    }

    public boolean isFriendRequestEnabled() {
        return friendRequestEnabled;
    }

    public void setFriendRequestEnabled(boolean friendRequestEnabled) {
        this.friendRequestEnabled = friendRequestEnabled;
    }

    public boolean isFriendEntryVisible() {
        return friendEntryVisible;
    }

    public void setFriendEntryVisible(boolean friendEntryVisible) {
        this.friendEntryVisible = friendEntryVisible;
    }

    public boolean isNonFriendChatEnabled() {
        return nonFriendChatEnabled;
    }

    public void setNonFriendChatEnabled(boolean nonFriendChatEnabled) {
        this.nonFriendChatEnabled = nonFriendChatEnabled;
    }

    public boolean isFriendRequestApprovalRequired() {
        return friendRequestApprovalRequired;
    }

    public void setFriendRequestApprovalRequired(boolean friendRequestApprovalRequired) {
        this.friendRequestApprovalRequired = friendRequestApprovalRequired;
    }

    public boolean isBlockEnabled() {
        return blockEnabled;
    }

    public void setBlockEnabled(boolean blockEnabled) {
        this.blockEnabled = blockEnabled;
    }
}
