<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="动态详情" mode="dark" @back="goBack">
			<view v-if="post && !isMine && showPostMessageEntry" slot="right" class="nav-action" @tap="openChat">
				<text class="cuIcon-messagefill nav-ico"></text>
				<text>私信</text>
			</view>
		</tavern-nav-bar>

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view v-if="loading && !post" class="state-card">正在加载动态...</view>
			<view v-else-if="errorMsg && !post" class="state-card">
				<text class="state-title">{{ errorMsg }}</text>
				<view class="state-btn" @tap="loadDetail">重试</view>
			</view>

			<view v-if="post" class="post-card">
				<view class="post-head">
					<image class="avatar" :src="avatarUrl(post)" mode="aspectFill"></image>
					<view class="author">
						<text class="name">{{ post.nickname || '用户' + post.userId }}</text>
						<text class="time">{{ formatTime(post.createdAt) }}</text>
					</view>
					<view
						v-if="!isMine && showRelationAction"
						class="follow-btn"
						:class="{ active: relationDone, incoming: friendFeatureEnabled && incomingFriendRequestPending }"
						@tap="addFriend"
					>{{ relationButtonText }}</view>
				</view>
				<view v-if="!isMine && (showRelationAction || showPostMessageEntry)" class="relation-panel">
					<view class="relation-copy">
						<text class="relation-title">{{ relationTitle }}</text>
						<text class="relation-desc">{{ relationDesc }}</text>
					</view>
					<view class="relation-actions">
						<view v-if="showRelationAction" class="relation-btn" @tap="addFriend">{{ relationButtonText }}</view>
						<view v-if="showPostMessageEntry" class="relation-btn primary" @tap="openChat">发消息</view>
					</view>
				</view>
				<text v-if="post.content" class="content">{{ post.content }}</text>
				<view v-if="post.mediaList && post.mediaList.length" class="media-grid" :class="'media-grid--' + Math.min(post.mediaList.length, 3)">
					<image
						v-for="(media, index) in post.mediaList"
						:key="media.id || index"
						class="media"
						:src="mediaUrl(media)"
						mode="aspectFill"
						@tap="previewMedia(index)"
					></image>
				</view>
				<view class="post-foot">
					<view v-if="socialFeatureConfig.likeEnabled" class="foot-item" :class="{ on: post.isLiked }" @tap="toggleLike">
						<text class="cuIcon-appreciatefill foot-ico"></text>
						<text>{{ post.isLiked ? '已赞' : '点赞' }}</text>
						<text>{{ post.likeCount || 0 }}</text>
					</view>
					<view v-if="socialFeatureConfig.commentEnabled" class="foot-item">
						<text class="cuIcon-commentfill foot-ico"></text>
						<text>评论</text>
						<text>{{ post.commentCount || 0 }}</text>
					</view>
					<view v-if="!isMine && showPostMessageEntry" class="foot-item" @tap="openChat">
						<text class="cuIcon-messagefill foot-ico"></text>
						<text>发消息</text>
					</view>
				</view>
			</view>

			<view v-if="post" class="comment-card">
				<view class="comment-title">评论</view>
				<view v-if="socialFeatureConfig.commentEnabled" class="comment-input-row">
					<input
						v-model="commentText"
						class="comment-input"
						placeholder="写下你的评论"
						placeholder-class="placeholder"
						confirm-type="send"
						@confirm="sendComment"
					/>
					<view class="send-btn" :class="{ disabled: submittingComment }" @tap="sendComment">
						<text class="cuIcon-send"></text>
					</view>
				</view>
				<view v-if="!comments.length" class="empty-comment">还没有评论</view>
				<view v-for="comment in comments" :key="comment.commentId" class="comment-item">
					<view class="comment-head">
						<image class="comment-avatar" :src="avatarUrl(comment)" mode="aspectFill"></image>
						<view class="comment-main">
							<view class="comment-top">
								<text class="comment-name">{{ comment.nickname || '用户' + comment.userId }}</text>
								<text class="comment-time">{{ formatTime(comment.createdAt) }}</text>
							</view>
							<text class="comment-content">{{ comment.content }}</text>
							<view v-if="socialFeatureConfig.commentEnabled" class="reply-action" @tap="startReply(comment)">回复</view>
							<view v-if="comment.replies && comment.replies.length" class="reply-list">
								<view v-for="reply in comment.replies" :key="reply.replyId" class="reply-item">
									<text class="reply-name">{{ reply.fromNickname || '用户' + reply.fromUserId }}</text>
									<text v-if="reply.toNickname" class="reply-to"> 回复 {{ reply.toNickname }}</text>
									<text class="reply-content">：{{ reply.content }}</text>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			postId: '',
			post: null,
			loading: false,
			errorMsg: '',
			commentText: '',
			replyTarget: null,
			submittingComment: false,
			friendRequestPending: false,
			incomingFriendRequestPending: false,
			incomingFriendRequestId: null,
			socialFeatureConfig: tavernApi.getSocialFeatureConfig()
		};
	},
	computed: {
		comments() {
			return this.post && Array.isArray(this.post.comments) ? this.post.comments : [];
		},
		isMine() {
			const userId = tavernApi.getStoredUserId(tavernApi.getStoredUser());
			return userId && this.post && String(this.post.userId) === String(userId);
		},
		showFollowAction() {
			const config = this.socialFeatureConfig || {};
			return config.followEnabled !== false;
		},
		friendFeatureEnabled() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true;
		},
		showFriendAction() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true && config.friendRequestEnabled === true;
		},
		showRelationAction() {
			return this.showFriendAction || (this.friendFeatureEnabled && this.incomingFriendRequestPending) || this.showFollowAction;
		},
		relationDone() {
			return !!(this.post && (this.post.isFriend || this.friendRequestPending || (this.friendFeatureEnabled && this.incomingFriendRequestPending) || this.post.isFollowed));
		},
		relationButtonText() {
			if (this.post && this.post.isFriend) return '已是好友';
			if (this.friendFeatureEnabled && this.incomingFriendRequestPending) return '同意好友';
			if (this.friendRequestPending) return '已申请';
			if (!this.showFriendAction && this.post && this.post.isFollowed) return '已关注';
			return this.showFriendAction ? '加好友' : '关注';
		},
		relationTitle() {
			if (this.post && this.post.isFriend) return '已经是好友';
			if (this.friendFeatureEnabled && this.incomingFriendRequestPending) return 'TA 想加你为好友';
			if (this.friendRequestPending) return '好友申请已发送';
			if (!this.showFriendAction && this.post && this.post.isFollowed) return '已经关注 TA';
			return this.showFriendAction ? '想认识 TA？' : '喜欢 TA 的动态？';
		},
		relationDesc() {
			if (this.post && this.post.isFriend) return '可以从真人聊天页继续找到 TA，也可以直接发消息。';
			if (this.friendFeatureEnabled && this.incomingFriendRequestPending) return '同意后双方会进入好友列表，可以更方便地私聊。';
			if (this.friendRequestPending) return '等对方通过后，就会出现在好友列表里。';
			if (!this.showFriendAction && this.post && this.post.isFollowed) return '可以从关注动态里继续看到 TA。';
			return this.showFriendAction ? '先发送好友申请，通过后可以更稳定地私聊。' : '先关注 TA，之后可以继续看到 TA 的动态。';
		},
		showPostMessageEntry() {
			const config = this.socialFeatureConfig || {};
			return config.postMessageEntryVisible !== false && config.chatEnabled !== false && config.chatEntryVisible !== false;
		}
	},
	onLoad(options) {
		this.postId = options && options.id ? String(options.id) : '';
		this.loadSocialFeatureConfig(true).finally(() => this.loadDetail());
	},
	onShow() {
		this.loadSocialFeatureConfig(true);
	},
	onPullDownRefresh() {
		this.loadDetail().finally(() => uni.stopPullDownRefresh());
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/social/feed' }) });
		},
		loadDetail() {
			if (!this.postId) {
				this.errorMsg = '动态不存在';
				return Promise.resolve();
			}
			this.loading = true;
			this.errorMsg = '';
			return tavernApi.fetchCommunityPost(this.postId)
				.then((post) => {
					this.post = post || null;
					this.friendRequestPending = !!(post && post.friendRequestPending);
					this.incomingFriendRequestPending = !!(post && post.incomingFriendRequestPending);
					this.incomingFriendRequestId = post && post.incomingFriendRequestId ? post.incomingFriendRequestId : null;
				})
				.catch((error) => {
					this.errorMsg = (error && error.message) || '动态加载失败';
					uni.showToast({ title: this.errorMsg, icon: 'none' });
				})
				.finally(() => {
					this.loading = false;
				});
		},
		loadSocialFeatureConfig(force) {
			return tavernApi.fetchSocialFeatureConfig(!!force)
				.then((config) => {
					this.socialFeatureConfig = config || tavernApi.getSocialFeatureConfig();
					return this.socialFeatureConfig;
				})
				.catch(() => {
					this.socialFeatureConfig = tavernApi.getSocialFeatureConfig();
					return this.socialFeatureConfig;
				});
		},
		ensureLogin() {
			if (tavernApi.hasLoggedInUser()) return true;
			uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/detail?id=' + encodeURIComponent(this.postId)) });
			return false;
		},
		toggleLike() {
			if (this.socialFeatureConfig.likeEnabled === false) {
				uni.showToast({ title: '当前已关闭点赞功能', icon: 'none' });
				return;
			}
			if (!this.post || !this.ensureLogin()) return;
			const action = this.post.isLiked ? tavernApi.unlikeCommunityPost : tavernApi.likeCommunityPost;
			action(this.post.postId).then((updated) => {
				this.post = Object.assign({}, this.post, updated || {});
				this.markFeedRefresh();
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		addFriend() {
			if (!this.post || !this.ensureLogin()) return;
			if (this.post.isFriend) {
				uni.navigateTo({ url: '/pages/social/chatList?mode=friends' });
				return;
			}
			if (this.friendFeatureEnabled && this.incomingFriendRequestPending && this.incomingFriendRequestId) {
				tavernApi.acceptCommunityFriendRequest(this.incomingFriendRequestId).then(() => {
					this.post.isFriend = true;
					this.friendRequestPending = false;
					this.incomingFriendRequestPending = false;
					this.incomingFriendRequestId = null;
					this.markFeedRefresh();
					uni.showToast({ title: '已成为好友', icon: 'none' });
				}).catch((error) => {
					uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
				});
				return;
			}
			if (!this.showFriendAction) {
				this.followUser();
				return;
			}
			if (this.friendRequestPending) {
				uni.showToast({ title: '好友申请已发送', icon: 'none' });
				return;
			}
			tavernApi.requestCommunityFriend(this.post.userId).then((res) => {
				this.post.isFriend = !!(res && res.isFriend);
				this.friendRequestPending = !!(res && res.pending);
				this.incomingFriendRequestPending = false;
				this.incomingFriendRequestId = null;
				this.markFeedRefresh();
				uni.showToast({ title: this.post.isFriend ? '已成为好友' : '好友申请已发送', icon: 'none' });
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		followUser() {
			if (this.socialFeatureConfig.followEnabled === false) {
				uni.showToast({ title: '当前已关闭关注功能', icon: 'none' });
				return;
			}
			if (!this.post || !this.ensureLogin()) return;
			if (this.post.isFollowed) {
				uni.showToast({ title: '已关注', icon: 'none' });
				return;
			}
			tavernApi.followCommunityUser(this.post.userId).then((res) => {
				this.post.isFollowed = !!(res && res.followed);
				this.markFeedRefresh();
				uni.showToast({ title: '已关注', icon: 'none' });
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		openChat() {
			if (!this.showPostMessageEntry) {
				uni.showToast({ title: '真人聊天暂未开放', icon: 'none' });
				return;
			}
			if (!this.post || this.isMine) return;
			const peerId = this.post.userId;
			const peerName = this.post.nickname || ('用户' + peerId);
			const targetUrl = '/pages/social/chatPage?peerId=' + encodeURIComponent(peerId) + '&name=' + encodeURIComponent(peerName);
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl(targetUrl) });
				return;
			}
			uni.navigateTo({ url: targetUrl });
		},
		startReply(comment) {
			if (this.socialFeatureConfig.commentEnabled === false) {
				uni.showToast({ title: '当前已关闭评论功能', icon: 'none' });
				return;
			}
			if (!this.ensureLogin()) return;
			this.replyTarget = comment;
			this.commentText = '回复 ' + (comment.nickname || '用户' + comment.userId) + '：';
		},
		sendComment() {
			if (this.socialFeatureConfig.commentEnabled === false) {
				uni.showToast({ title: '当前已关闭评论功能', icon: 'none' });
				return;
			}
			if (!this.post || this.submittingComment || !this.ensureLogin()) return;
			const raw = String(this.commentText || '').trim();
			if (!raw) {
				uni.showToast({ title: '评论不能为空', icon: 'none' });
				return;
			}
			this.submittingComment = true;
			const target = this.replyTarget;
			const payloadText = target ? raw.replace(/^回复\s+[^：:]+[：:]\s*/, '') : raw;
			const request = target
				? tavernApi.addCommunityReply(target.commentId, { content: payloadText, toUserId: target.userId })
				: tavernApi.addCommunityComment(this.post.postId, raw);
			request.then((updated) => {
				this.post = Object.assign({}, this.post, updated || {});
				this.commentText = '';
				this.replyTarget = null;
				this.markFeedRefresh();
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '发送失败', icon: 'none' });
			}).finally(() => {
				this.submittingComment = false;
			});
		},
		markFeedRefresh() {
			try {
				uni.setStorageSync('social_feed_refresh_needed', '1');
			} catch (e) {}
		},
		mediaUrl(media) {
			const raw = media && (media.mediaUrl || media.mediaKey || media.url);
			return tavernApi.resolveJgAssetUrl(raw || '');
		},
		avatarUrl(source) {
			return tavernApi.resolveJgAssetUrl(source && source.avatar ? source.avatar : '/static/logo.png');
		},
		previewMedia(index) {
			const list = this.post && this.post.mediaList ? this.post.mediaList : [];
			const urls = list.map(this.mediaUrl).filter(Boolean);
			if (!urls.length) return;
			uni.previewImage({ urls, current: urls[index] || urls[0] });
		},
		formatTime(value) {
			if (!value) return '';
			const d = new Date(String(value).replace(/-/g, '/'));
			if (isNaN(d.getTime())) return String(value);
			const diff = Date.now() - d.getTime();
			if (diff < 60000) return '刚刚';
			if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
			if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
			return (d.getMonth() + 1) + '-' + d.getDate();
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	height: 100vh;
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	background: #ecf8fb;
}

.app-page-bg {
	position: fixed;
	inset: 0;
	width: 100%;
	height: 100%;
	opacity: 0.5;
	z-index: 0;
}

.nav-action {
	padding: 12rpx 18rpx;
	border-radius: 8rpx;
	background: rgba(58, 128, 160, 0.12);
	color: #2d6f89;
	font-size: 24rpx;
	font-weight: 700;
}

.scroll {
	position: relative;
	z-index: 1;
	flex: 1;
	min-height: 0;
	padding: 24rpx;
	box-sizing: border-box;
}

.state-card,
.post-card,
.comment-card {
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.78);
	border: 1rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
}

.state-card {
	padding: 52rpx 34rpx;
	text-align: center;
	color: #6f7b88;
	font-size: 26rpx;
}

.state-title {
	display: block;
	color: #26394d;
	font-size: 30rpx;
	font-weight: 700;
}

.state-btn {
	margin: 28rpx auto 0;
	width: 220rpx;
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #4f93a3;
	color: #fff;
	font-size: 26rpx;
	font-weight: 700;
}

.post-card,
.comment-card {
	padding: 24rpx;
}

.comment-card {
	margin-top: 20rpx;
}

.post-head,
.comment-head {
	display: flex;
	align-items: flex-start;
	gap: 18rpx;
}

.avatar {
	width: 82rpx;
	height: 82rpx;
	border-radius: 50%;
	background: rgba(79, 147, 163, 0.12);
}

.author {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.name {
	color: #26394d;
	font-size: 30rpx;
	font-weight: 700;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.time {
	color: #7891a4;
	font-size: 22rpx;
}

.follow-btn {
	padding: 12rpx 20rpx;
	border-radius: 8rpx;
	background: #e85d75;
	color: #fff;
	font-size: 24rpx;
	font-weight: 700;
}

.follow-btn.active {
	background: #edf5f8;
	color: #2d6f89;
}

.relation-panel {
	margin-top: 22rpx;
	padding: 20rpx;
	border-radius: 8rpx;
	display: flex;
	gap: 18rpx;
	align-items: center;
	background: #f4f8fb;
	border: 1rpx solid #e4eef3;
}

.relation-copy {
	flex: 1;
	min-width: 0;
}

.relation-title,
.relation-desc {
	display: block;
}

.relation-title {
	color: #213547;
	font-size: 27rpx;
	font-weight: 800;
}

.relation-desc {
	margin-top: 6rpx;
	color: #6c7d89;
	font-size: 23rpx;
	line-height: 1.4;
}

.relation-actions {
	display: flex;
	gap: 10rpx;
	flex-shrink: 0;
}

.relation-btn {
	min-width: 92rpx;
	height: 58rpx;
	padding: 0 14rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #ffffff;
	color: #2d6f89;
	border: 1rpx solid #d7e6ed;
	font-size: 23rpx;
	font-weight: 700;
	box-sizing: border-box;
}

.relation-btn.primary {
	background: #2d6f89;
	border-color: #2d6f89;
	color: #ffffff;
}

.content {
	display: block;
	margin-top: 22rpx;
	color: #2c405a;
	font-size: 30rpx;
	line-height: 1.65;
	white-space: pre-wrap;
	word-break: break-word;
}

.media-grid {
	margin-top: 20rpx;
	display: grid;
	gap: 10rpx;
}

.media-grid--1 {
	grid-template-columns: 1fr;
}

.media-grid--2,
.media-grid--3 {
	grid-template-columns: repeat(2, minmax(0, 1fr));
}

.media {
	width: 100%;
	height: 220rpx;
	border-radius: 8rpx;
	background: rgba(79, 147, 163, 0.1);
}

.media-grid--1 .media {
	height: 440rpx;
}

.post-foot {
	margin-top: 22rpx;
	display: flex;
	gap: 14rpx;
}

.foot-item {
	min-width: 0;
	flex: 1;
	height: 66rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	background: rgba(79, 147, 163, 0.08);
	color: #5f7688;
	font-size: 24rpx;
}

.foot-item.on {
	background: rgba(181, 138, 146, 0.16);
	color: #a65f72;
}

.comment-title {
	color: #26394d;
	font-size: 30rpx;
	font-weight: 700;
}

.comment-input-row {
	margin-top: 18rpx;
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.comment-input {
	flex: 1;
	min-width: 0;
	height: 68rpx;
	border-radius: 8rpx;
	padding: 0 20rpx;
	box-sizing: border-box;
	background: rgba(79, 147, 163, 0.08);
	color: #2c405a;
	font-size: 26rpx;
}

.placeholder {
	color: #95a6b4;
}

.send-btn {
	width: 104rpx;
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #4f93a3;
	color: #fff;
	font-size: 24rpx;
	font-weight: 700;
}

.send-btn.disabled {
	opacity: 0.55;
}

.empty-comment {
	margin-top: 30rpx;
	text-align: center;
	color: #7891a4;
	font-size: 24rpx;
}

.comment-item {
	margin-top: 30rpx;
}

.comment-avatar {
	width: 58rpx;
	height: 58rpx;
	border-radius: 50%;
	background: rgba(79, 147, 163, 0.12);
}

.comment-main {
	flex: 1;
	min-width: 0;
}

.comment-top {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 14rpx;
}

.comment-name {
	color: #26394d;
	font-size: 25rpx;
	font-weight: 700;
}

.comment-time {
	color: #95a6b4;
	font-size: 21rpx;
}

.comment-content {
	display: block;
	margin-top: 8rpx;
	color: #2c405a;
	font-size: 26rpx;
	line-height: 1.55;
	word-break: break-word;
}

.reply-action {
	margin-top: 10rpx;
	color: #4f93a3;
	font-size: 23rpx;
}

.reply-list {
	margin-top: 12rpx;
	padding: 14rpx 16rpx;
	border-radius: 8rpx;
	background: rgba(79, 147, 163, 0.08);
}

.reply-item {
	font-size: 24rpx;
	line-height: 1.5;
	color: #4d6476;
}

.reply-name {
	color: #2f6f92;
	font-weight: 700;
}

.reply-to {
	color: #7891a4;
}

.bottom-space {
	height: 140rpx;
}

.page {
	background:
		linear-gradient(180deg, #eef7f1 0%, #f7f7f2 42%, #f4f1ea 100%);
}

.app-page-bg {
	opacity: 0.06;
	filter: grayscale(1);
}

.nav-action {
	min-width: 104rpx;
	height: 54rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	background: rgba(255, 255, 255, 0.78);
	color: #2e6b4d;
	border: 1rpx solid rgba(46, 107, 77, 0.12);
	font-weight: 900;
	box-sizing: border-box;
}

.nav-ico,
.foot-ico {
	font-size: 24rpx;
	line-height: 1;
}

.state-card,
.post-card,
.comment-card {
	background: rgba(255, 255, 255, 0.94);
	border-color: rgba(46, 107, 77, 0.08);
	box-shadow: 0 14rpx 30rpx rgba(34, 52, 42, 0.08);
}

.state-card {
	color: #65736a;
}

.state-title,
.name,
.relation-title,
.comment-title,
.comment-name {
	color: #1f2933;
	font-weight: 900;
}

.state-btn,
.send-btn {
	background: #2e6b4d;
	font-weight: 900;
}

.post-card {
	overflow: hidden;
}

.avatar,
.comment-avatar {
	background: #e6efe8;
	border: 3rpx solid #ffffff;
	box-shadow: 0 8rpx 16rpx rgba(34, 52, 42, 0.1);
	box-sizing: border-box;
}

.time,
.relation-desc,
.empty-comment,
.comment-time {
	color: #7b877f;
}

.follow-btn {
	background: #e36f5f;
	font-weight: 900;
	box-shadow: 0 12rpx 22rpx rgba(227, 111, 95, 0.16);
}

.follow-btn.active {
	background: rgba(46, 107, 77, 0.09);
	color: #2e6b4d;
	box-shadow: none;
}

.follow-btn.incoming {
	background: #2e6b4d;
	color: #fff;
}

.relation-panel {
	background:
		linear-gradient(135deg, rgba(238, 247, 241, 0.96) 0%, rgba(255, 244, 235, 0.94) 100%);
	border-color: rgba(46, 107, 77, 0.1);
}

.relation-btn {
	color: #2e6b4d;
	border-color: rgba(46, 107, 77, 0.16);
	font-weight: 900;
}

.relation-btn.primary {
	background: #2e6b4d;
	border-color: #2e6b4d;
}

.content,
.comment-content {
	color: #263238;
}

.media-grid {
	gap: 8rpx;
	overflow: hidden;
	border-radius: 8rpx;
}

.media {
	border-radius: 0;
	background: rgba(46, 107, 77, 0.08);
}

.post-foot {
	gap: 10rpx;
}

.foot-item {
	gap: 6rpx;
	background: #f1f5f0;
	color: #65736a;
	font-weight: 800;
}

.foot-item.on {
	background: rgba(227, 111, 95, 0.12);
	color: #b7554a;
}

.comment-input {
	background: #f4f6f2;
	color: #263238;
	border: 1rpx solid rgba(46, 107, 77, 0.08);
}

.send-btn {
	width: 72rpx;
	font-size: 34rpx;
	background: #e36f5f;
	box-shadow: 0 12rpx 22rpx rgba(227, 111, 95, 0.18);
}

.reply-action,
.reply-name {
	color: #2e6b4d;
}

.reply-list {
	background: rgba(46, 107, 77, 0.08);
}

.reply-item {
	color: #4f6257;
}

.placeholder {
	color: #8a958e;
}

/* Detail view: keep the post readable, remove the stacked boxes. */
.scroll {
	padding: 20rpx 30rpx 0;
}

.post-card,
.comment-card {
	padding: 24rpx 0;
	border-radius: 0;
	background: transparent;
	border: 0;
	box-shadow: none;
}

.comment-card {
	margin-top: 0;
	border-top: 1rpx solid rgba(46, 107, 77, 0.1);
}

.relation-panel {
	display: none;
}

.follow-btn {
	height: 48rpx;
	padding: 0 16rpx;
	border-radius: 999rpx;
	box-shadow: none;
}

.content {
	margin-top: 20rpx;
	font-size: 30rpx;
	line-height: 1.68;
}

.media-grid {
	margin-top: 16rpx;
	border-radius: 20rpx;
}

.post-foot {
	margin-top: 18rpx;
	gap: 34rpx;
}

.foot-item {
	flex: 0 0 auto;
	height: 42rpx;
	padding: 0;
	background: transparent;
	gap: 8rpx;
}

.foot-item text:nth-child(2) {
	display: none;
}

.comment-input {
	border-radius: 999rpx;
	background: rgba(46, 107, 77, 0.06);
	border: 0;
}

.send-btn {
	border-radius: 50%;
	box-shadow: none;
}

.reply-list {
	padding: 10rpx 0 0 18rpx;
	border-radius: 0;
	background: transparent;
	border-left: 4rpx solid rgba(46, 107, 77, 0.12);
}
</style>
