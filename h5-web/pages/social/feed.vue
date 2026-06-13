<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar title="社区" mode="dark" @back="goBack">
			<view slot="right" class="nav-actions">
				<view v-if="showFriendEntry" class="nav-action nav-action--ghost" @tap="openFriends">
					<text class="cuIcon-friend nav-ico"></text>
					<text>好友</text>
				</view>
				<view v-if="showChatEntry" class="nav-action nav-action--ghost" @tap="openChatList">
					<text class="cuIcon-message nav-ico"></text>
					<text>私信</text>
				</view>
				<view v-if="canCreatePost" class="nav-action" @tap="openCreate">
					<text class="cuIcon-add nav-ico"></text>
					<text>发布</text>
				</view>
			</view>
		</tavern-nav-bar>

		<view class="community-hero">
			<view class="hero-copy">
				<text class="hero-kicker">SIYE SOCIAL</text>
				<text class="hero-title">社区广场</text>
				<text class="hero-desc">动态、同好、私信都在这里。</text>
				<view class="hero-meta">
					<view class="hero-meta-item">推荐</view>
					<view class="hero-meta-item">关注</view>
					<view class="hero-meta-item">真人私信</view>
				</view>
			</view>
			<view class="hero-actions">
				<view v-if="showFriendEntry" class="hero-action" @tap="openFriends">
					<text class="cuIcon-friend"></text>
					<text>好友</text>
				</view>
				<view v-if="canCreatePost" class="hero-action primary" @tap="openCreate">
					<text class="cuIcon-add"></text>
					<text>发布</text>
				</view>
			</view>
		</view>

		<view class="tabs">
			<view class="tab" :class="{ active: feed === 'recommend' }" @tap="switchFeed('recommend')">推荐</view>
			<view class="tab" :class="{ active: feed === 'following' }" @tap="switchFeed('following')">关注</view>
		</view>

		<scroll-view
			scroll-y
			class="scroll"
			:show-scrollbar="false"
			:lower-threshold="220"
			@scrolltolower="loadMore"
		>
			<view v-if="loading && !posts.length" class="state-card">正在加载社区动态...</view>
			<view v-else-if="errorMsg && !posts.length" class="state-card">
				<text class="state-title">{{ errorMsg }}</text>
				<view class="state-btn" @tap="reload">重试</view>
			</view>
			<view v-else-if="!posts.length" class="state-card">
				<text class="state-title">{{ feed === 'following' ? '还没有关注动态' : '社区还没有动态' }}</text>
				<text class="state-desc">{{ feed === 'following' ? '去推荐流发现感兴趣的人。' : '发一条文字或图片动态，先把这里点亮。' }}</text>
				<view class="state-btn" @tap="openCreate">发布动态</view>
			</view>

			<view v-for="post in posts" :key="post.postId" class="post-card" @tap="openDetail(post)">
				<view class="post-head">
					<image class="avatar" :src="avatarUrl(post)" mode="aspectFill"></image>
					<view class="author">
						<text class="name">{{ post.nickname || '用户' + post.userId }}</text>
						<text class="time">{{ formatTime(post.createdAt) }}</text>
					</view>
					<view
						v-if="showPostRelationAction(post)"
						class="relation-chip"
						:class="postRelationClass(post)"
						@tap.stop="handleRelationTap(post)"
					>{{ postRelationText(post) }}</view>
				</view>
				<text v-if="post.content" class="content">{{ post.content }}</text>
				<view v-if="post.mediaList && post.mediaList.length" class="media-grid" :class="'media-grid--' + Math.min(post.mediaList.length, 3)">
					<image
						v-for="(media, index) in post.mediaList"
						:key="media.id || index"
						class="media"
						:src="mediaUrl(media)"
						mode="aspectFill"
						@tap.stop="previewMedia(post.mediaList, index)"
					></image>
				</view>
				<view class="post-foot">
					<view v-if="socialFeatureConfig.likeEnabled" class="foot-item" :class="{ on: post.isLiked }" @tap.stop="toggleLike(post)">
						<text class="cuIcon-appreciatefill foot-ico"></text>
						<text>{{ post.isLiked ? '已赞' : '点赞' }}</text>
						<text>{{ post.likeCount || 0 }}</text>
					</view>
					<view v-if="socialFeatureConfig.commentEnabled" class="foot-item">
						<text class="cuIcon-commentfill foot-ico"></text>
						<text>评论</text>
						<text>{{ post.commentCount || 0 }}</text>
					</view>
					<view class="foot-item">
						<text class="cuIcon-attentionfill foot-ico"></text>
						<text>浏览</text>
						<text>{{ post.viewCount || 0 }}</text>
					</view>
					<view v-if="showPostMessageAction(post)" class="foot-item foot-item--primary" @tap.stop="openChatToPostAuthor(post)">
						<text class="cuIcon-messagefill foot-ico"></text>
						<text>私信</text>
					</view>
				</view>
			</view>

			<view v-if="posts.length" class="list-tail">
				<text v-if="loading">正在加载更多...</text>
				<text v-else-if="hasMore">继续下滑加载更多</text>
				<text v-else>已经到底了</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="104" />
		<!-- #endif -->
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			feed: 'recommend',
			pageNum: 1,
			pageSize: 10,
			total: 0,
			posts: [],
			loading: false,
			errorMsg: '',
			socialFeatureConfig: tavernApi.getSocialFeatureConfig()
		};
	},
	computed: {
		hasMore() {
			return this.posts.length < this.total;
		},
		canCreatePost() {
			const config = this.socialFeatureConfig || {};
			return config.communityEnabled !== false && config.postCreateEnabled !== false && config.postPublishMode !== 'closed';
		},
		showChatEntry() {
			const config = this.socialFeatureConfig || {};
			return config.chatEnabled !== false && config.chatEntryVisible !== false;
		},
		showFriendEntry() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true && config.friendEntryVisible === true;
		},
		friendFeatureEnabled() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true;
		},
		showFollowAction() {
			const config = this.socialFeatureConfig || {};
			return config.followEnabled !== false;
		},
		showFriendAction() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true && config.friendRequestEnabled === true;
		},
		showPostMessageEntry() {
			const config = this.socialFeatureConfig || {};
			return config.postMessageEntryVisible !== false && config.chatEnabled !== false && config.chatEntryVisible !== false;
		},
		currentUserId() {
			return tavernApi.getStoredUserId(tavernApi.getStoredUser());
		}
	},
	onLoad(options) {
		if (options && options.feed === 'following') {
			this.feed = 'following';
		}
		this.loadSocialFeatureConfig(true).finally(() => this.reload());
	},
	onShow() {
		this.loadSocialFeatureConfig(true);
		if (this.consumeRefreshFlag()) {
			this.reload();
		}
	},
	onPullDownRefresh() {
		this.reload().finally(() => uni.stopPullDownRefresh());
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) });
		},
		consumeRefreshFlag() {
			try {
				const value = uni.getStorageSync('social_feed_refresh_needed');
				if (value) {
					uni.removeStorageSync('social_feed_refresh_needed');
					return true;
				}
			} catch (e) {}
			return false;
		},
		switchFeed(next) {
			if (this.feed === next) return;
			if (next === 'following' && !tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/feed?feed=following') });
				return;
			}
			this.feed = next;
			this.reload();
		},
		reload() {
			this.pageNum = 1;
			this.total = 0;
			return this.fetchPosts(true);
		},
		loadMore() {
			if (this.loading || !this.hasMore) return;
			this.pageNum += 1;
			this.fetchPosts(false);
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
		fetchPosts(reset) {
			this.loading = true;
			this.errorMsg = '';
			return tavernApi.fetchCommunityPosts({
				feed: this.feed,
				pageNum: this.pageNum,
				pageSize: this.pageSize
			}).then((data) => {
				const rows = Array.isArray(data && data.rows) ? data.rows : [];
				this.total = Number(data && data.total) || rows.length;
				this.posts = reset ? rows : this.posts.concat(rows);
			}).catch((error) => {
				if (this.pageNum > 1) this.pageNum -= 1;
				this.errorMsg = (error && error.message) || '社区加载失败';
				if (this.errorMsg.indexOf('请先登录') !== -1 || error.statusCode === 401 || error.statusCode === 403) {
					uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/feed?feed=' + this.feed) });
				} else {
					uni.showToast({ title: this.errorMsg, icon: 'none' });
				}
			}).finally(() => {
				this.loading = false;
			});
		},
		openCreate() {
			if (!this.canCreatePost) {
				uni.showToast({ title: '当前已关闭动态发布', icon: 'none' });
				return;
			}
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/postCreate') });
				return;
			}
			uni.navigateTo({ url: '/pages/social/postCreate' });
		},
		openChatList() {
			if (!this.showChatEntry) {
				uni.showToast({ title: '真人聊天暂未开放', icon: 'none' });
				return;
			}
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/chatList') });
				return;
			}
			uni.navigateTo({ url: '/pages/social/chatList' });
		},
		openFriends() {
			if (!this.showFriendEntry) {
				uni.showToast({ title: '好友入口暂未开放', icon: 'none' });
				return;
			}
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/chatList?mode=friends') });
				return;
			}
			uni.navigateTo({ url: '/pages/social/chatList?mode=friends' });
		},
		openDetail(post) {
			uni.navigateTo({ url: '/pages/social/detail?id=' + encodeURIComponent(post.postId) });
		},
		isOwnPost(post) {
			return !!(this.currentUserId && post && String(post.userId) === String(this.currentUserId));
		},
		showPostRelationAction(post) {
			return !!(post && !this.isOwnPost(post) && (this.showFriendAction || (this.friendFeatureEnabled && post.incomingFriendRequestPending) || this.showFollowAction));
		},
		showPostMessageAction(post) {
			return !!(post && !this.isOwnPost(post) && this.showPostMessageEntry);
		},
		postRelationText(post) {
			if (!post) return '';
			if (post.isFriend) return '好友';
			if (this.friendFeatureEnabled && post.incomingFriendRequestPending) return '同意';
			if (post.friendRequestPending) return '已申请';
			if (!this.showFriendAction && post.isFollowed) return '已关注';
			return this.showFriendAction ? '加好友' : '关注';
		},
		postRelationClass(post) {
			return {
				'relation-chip--done': !!(post && (post.isFriend || (!this.showFriendAction && post.isFollowed))),
				'relation-chip--pending': !!(post && post.friendRequestPending),
				'relation-chip--incoming': !!(post && this.friendFeatureEnabled && post.incomingFriendRequestPending),
				'relation-chip--action': !!(post && !post.isFriend && !post.friendRequestPending && !(this.friendFeatureEnabled && post.incomingFriendRequestPending) && (this.showFriendAction || !post.isFollowed))
			};
		},
		handleRelationTap(post) {
			if (!post || this.isOwnPost(post)) return;
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/detail?id=' + post.postId) });
				return;
			}
			if (this.showFriendAction || (this.friendFeatureEnabled && post.incomingFriendRequestPending)) {
				if (post.isFriend) {
					this.openChatToPostAuthor(post);
					return;
				}
				if (post.friendRequestPending) {
					uni.showToast({ title: '好友申请已发送', icon: 'none' });
					return;
				}
				if (post.incomingFriendRequestPending && post.incomingFriendRequestId) {
					tavernApi.acceptCommunityFriendRequest(post.incomingFriendRequestId).then(() => {
						post.isFriend = true;
						post.friendRequestPending = false;
						post.incomingFriendRequestPending = false;
						post.incomingFriendRequestId = null;
						this.markFeedRefresh();
						uni.showToast({ title: '已成为好友', icon: 'none' });
					}).catch((error) => {
						uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
					});
					return;
				}
				if (!this.showFriendAction) {
					uni.showToast({ title: '好友申请暂未开放', icon: 'none' });
					return;
				}
				tavernApi.requestCommunityFriend(post.userId).then((res) => {
					post.isFriend = !!(res && res.isFriend);
					post.friendRequestPending = !!(res && res.pending);
					post.incomingFriendRequestPending = false;
					post.incomingFriendRequestId = null;
					this.markFeedRefresh();
					uni.showToast({ title: post.isFriend ? '已成为好友' : '好友申请已发送', icon: 'none' });
				}).catch((error) => {
					uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
				});
				return;
			}
			if (post.isFollowed) {
				uni.showToast({ title: '已关注', icon: 'none' });
				return;
			}
			if (this.socialFeatureConfig.followEnabled === false) {
				uni.showToast({ title: '当前已关闭关注功能', icon: 'none' });
				return;
			}
			tavernApi.followCommunityUser(post.userId).then((res) => {
				post.isFollowed = !!(res && res.followed);
				this.markFeedRefresh();
				uni.showToast({ title: '已关注', icon: 'none' });
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		openChatToPostAuthor(post) {
			if (!post || this.isOwnPost(post)) return;
			if (!this.showPostMessageEntry) {
				uni.showToast({ title: '真人聊天暂未开放', icon: 'none' });
				return;
			}
			const peerId = post.userId;
			const peerName = post.nickname || ('用户' + peerId);
			const targetUrl = '/pages/social/chatPage?peerId=' + encodeURIComponent(peerId) + '&name=' + encodeURIComponent(peerName);
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl(targetUrl) });
				return;
			}
			uni.navigateTo({ url: targetUrl });
		},
		markFeedRefresh() {
			try {
				uni.setStorageSync('social_feed_refresh_needed', '1');
			} catch (e) {}
		},
		toggleLike(post) {
			if (this.socialFeatureConfig.likeEnabled === false) {
				uni.showToast({ title: '当前已关闭点赞功能', icon: 'none' });
				return;
			}
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/detail?id=' + post.postId) });
				return;
			}
			const action = post.isLiked ? tavernApi.unlikeCommunityPost : tavernApi.likeCommunityPost;
			action(post.postId).then((updated) => {
				Object.assign(post, updated || {});
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		mediaUrl(media) {
			const raw = media && (media.mediaUrl || media.mediaKey || media.url);
			return tavernApi.resolveJgAssetUrl(raw || '');
		},
		avatarUrl(post) {
			return tavernApi.resolveJgAssetUrl(post && post.avatar ? post.avatar : '/static/logo.png');
		},
		previewMedia(list, index) {
			const urls = (list || []).map(this.mediaUrl).filter(Boolean);
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
	background:
		linear-gradient(180deg, #eef7f1 0%, #f7f7f2 38%, #f4f1ea 100%);
}

.nav-actions {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.nav-action {
	min-width: 88rpx;
	height: 54rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 6rpx;
	background: #2e6b4d;
	color: #fff;
	font-size: 23rpx;
	font-weight: 800;
	box-shadow: 0 10rpx 20rpx rgba(46, 107, 77, 0.16);
}

.nav-action--ghost {
	background: rgba(255, 255, 255, 0.78);
	color: #2e6b4d;
	box-shadow: none;
	border: 1rpx solid rgba(46, 107, 77, 0.12);
}

.nav-ico {
	font-size: 24rpx;
	line-height: 1;
}

.community-hero {
	position: relative;
	z-index: 1;
	margin: 18rpx 24rpx 0;
	padding: 28rpx;
	border-radius: 8rpx;
	background:
		linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 247, 241, 0.96) 58%, rgba(255, 244, 235, 0.94) 100%);
	border: 1rpx solid rgba(46, 107, 77, 0.1);
	box-shadow: 0 18rpx 38rpx rgba(34, 52, 42, 0.1);
	overflow: hidden;
}

.community-hero::before {
	content: '';
	position: absolute;
	left: 0;
	top: 0;
	bottom: 0;
	width: 8rpx;
	background: linear-gradient(180deg, #2e6b4d 0%, #e36f5f 100%);
}

.hero-copy {
	position: relative;
	z-index: 1;
}

.hero-kicker,
.hero-title,
.hero-desc {
	display: block;
}

.hero-kicker {
	color: #2e6b4d;
	font-size: 20rpx;
	font-weight: 900;
	letter-spacing: 0;
}

.hero-title {
	margin-top: 8rpx;
	color: #1f2933;
	font-size: 44rpx;
	font-weight: 900;
	line-height: 1.18;
}

.hero-desc {
	margin-top: 10rpx;
	color: #607066;
	font-size: 25rpx;
	line-height: 1.45;
}

.hero-meta {
	margin-top: 20rpx;
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
}

.hero-meta-item {
	height: 44rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	background: rgba(46, 107, 77, 0.08);
	color: #315b44;
	font-size: 22rpx;
	font-weight: 800;
}

.hero-actions {
	margin-top: 24rpx;
	display: flex;
	gap: 12rpx;
}

.hero-action {
	flex: 1;
	height: 72rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	background: #ffffff;
	color: #2e6b4d;
	border: 1rpx solid rgba(46, 107, 77, 0.12);
	font-size: 26rpx;
	font-weight: 900;
}

.hero-action.primary {
	background: #e36f5f;
	border-color: #e36f5f;
	color: #fff;
	box-shadow: 0 14rpx 26rpx rgba(227, 111, 95, 0.18);
}

.tabs {
	position: relative;
	z-index: 1;
	display: flex;
	gap: 12rpx;
	padding: 18rpx 24rpx 8rpx;
}

.tab {
	flex: 1;
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.74);
	border: 1rpx solid rgba(46, 107, 77, 0.1);
	color: #65736a;
	font-size: 26rpx;
	font-weight: 900;
}

.tab.active {
	background: #1f2933;
	border-color: #1f2933;
	color: #fff;
}

.scroll {
	position: relative;
	z-index: 1;
	flex: 1;
	min-height: 0;
	box-sizing: border-box;
	padding: 10rpx 24rpx 0;
}

.state-card,
.post-card {
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.94);
	border: 1rpx solid rgba(46, 107, 77, 0.08);
	box-shadow: 0 14rpx 30rpx rgba(34, 52, 42, 0.08);
}

.state-card {
	margin: 20rpx 0;
	padding: 52rpx 34rpx;
	text-align: center;
	color: #65736a;
	font-size: 26rpx;
}

.state-title,
.state-desc {
	display: block;
	line-height: 1.5;
}

.state-title {
	color: #1f2933;
	font-size: 30rpx;
	font-weight: 900;
}

.state-desc {
	margin-top: 10rpx;
	color: #65736a;
}

.state-btn {
	margin: 28rpx auto 0;
	width: 220rpx;
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #2e6b4d;
	color: #fff;
	font-size: 26rpx;
	font-weight: 900;
}

.post-card {
	margin: 18rpx 0;
	padding: 24rpx;
	overflow: hidden;
}

.post-head {
	display: flex;
	align-items: center;
	gap: 18rpx;
}

.avatar {
	width: 76rpx;
	height: 76rpx;
	border-radius: 50%;
	background: #e6efe8;
	border: 3rpx solid #fff;
	box-shadow: 0 8rpx 16rpx rgba(34, 52, 42, 0.1);
}

.author {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.name {
	color: #1f2933;
	font-size: 28rpx;
	font-weight: 900;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.time {
	color: #7b877f;
	font-size: 22rpx;
}

.relation-chip {
	flex-shrink: 0;
	min-width: 86rpx;
	height: 44rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
	font-weight: 900;
	line-height: 1;
}

.relation-chip--done {
	background: rgba(46, 107, 77, 0.1);
	color: #2e6b4d;
}

.relation-chip--pending {
	background: rgba(114, 86, 58, 0.1);
	color: #72563a;
}

.relation-chip--incoming {
	background: rgba(46, 107, 77, 0.12);
	color: #2e6b4d;
}

.relation-chip--action {
	background: rgba(227, 111, 95, 0.1);
	color: #b7554a;
}

.content {
	display: block;
	margin-top: 20rpx;
	color: #263238;
	font-size: 30rpx;
	line-height: 1.65;
	white-space: pre-wrap;
	word-break: break-word;
}

.media-grid {
	margin-top: 20rpx;
	display: grid;
	gap: 8rpx;
	overflow: hidden;
	border-radius: 8rpx;
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
	border-radius: 0;
	background: rgba(46, 107, 77, 0.08);
}

.media-grid--1 .media {
	height: 420rpx;
}

.post-foot {
	margin-top: 22rpx;
	display: flex;
	gap: 10rpx;
}

.foot-item {
	min-width: 0;
	flex: 1;
	height: 64rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 6rpx;
	background: #f1f5f0;
	color: #65736a;
	font-size: 23rpx;
	font-weight: 800;
}

.foot-item.on {
	background: rgba(227, 111, 95, 0.12);
	color: #b7554a;
}

.foot-item--primary {
	background: #2e6b4d;
	color: #ffffff;
	flex: 1.35;
}

.foot-ico {
	font-size: 24rpx;
	line-height: 1;
}

.list-tail {
	padding: 24rpx;
	text-align: center;
	color: #7b877f;
	font-size: 24rpx;
}

.bottom-space {
	height: 150rpx;
}

/* Softer social feed: reduce framed blocks and hard button shapes. */
.page {
	background: linear-gradient(180deg, #f7fbf8 0%, #fffaf3 100%);
}

.nav-action {
	min-width: 54rpx;
	width: 54rpx;
	height: 54rpx;
	padding: 0;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.72);
	border-color: rgba(46, 107, 77, 0.08);
	box-shadow: none;
	color: #2e6b4d;
}

.nav-action text:not(.nav-ico) {
	display: none;
}

.nav-action:last-child {
	background: #2e6b4d;
	color: #fff;
}

.community-hero {
	margin: 0;
	padding: 34rpx 30rpx 28rpx;
	border-radius: 0;
	background: linear-gradient(135deg, rgba(239, 249, 242, 0.96) 0%, rgba(255, 247, 237, 0.96) 100%);
	border: 0;
	box-shadow: none;
}

.community-hero::before {
	display: none;
}

.hero-title {
	font-size: 46rpx;
}

.hero-meta-item {
	height: auto;
	padding: 0;
	background: transparent;
	color: #647367;
	font-size: 23rpx;
}

.hero-meta-item::before {
	content: '#';
	color: #2e6b4d;
	margin-right: 2rpx;
}

.hero-action {
	height: 64rpx;
	background: transparent;
	border: 0;
	justify-content: flex-start;
	color: #2e6b4d;
}

.hero-action.primary {
	justify-content: center;
	border-radius: 999rpx;
	background: #2e6b4d;
	box-shadow: none;
}

.tabs {
	padding: 20rpx 30rpx 0;
	gap: 36rpx;
}

.tab {
	flex: 0 0 auto;
	height: 54rpx;
	padding: 0;
	background: transparent;
	border: 0;
	border-radius: 0;
	color: #7b877f;
}

.tab.active {
	position: relative;
	background: transparent;
	color: #1f2933;
}

.tab.active::after {
	content: '';
	position: absolute;
	left: 12rpx;
	right: 12rpx;
	bottom: 0;
	height: 5rpx;
	border-radius: 999rpx;
	background: #2e6b4d;
}

.scroll {
	padding: 10rpx 30rpx 0;
}

.post-card {
	margin: 0;
	padding: 30rpx 0;
	border-radius: 0;
	background: transparent;
	border: 0;
	border-bottom: 1rpx solid rgba(46, 107, 77, 0.1);
	box-shadow: none;
}

.state-card {
	background: transparent;
	border: 0;
	box-shadow: none;
}

.avatar {
	box-shadow: none;
}

.media-grid {
	border-radius: 18rpx;
}

.media-grid--1 .media {
	height: 520rpx;
}

.post-foot {
	justify-content: space-between;
	gap: 0;
}

.foot-item {
	flex: 0 0 auto;
	height: 46rpx;
	padding: 0;
	background: transparent;
	border-radius: 0;
	color: #68766c;
}

.foot-item--primary {
	color: #2e6b4d;
	background: transparent;
}

/* Trim the new-page chrome back to a plain content stream. */
.community-hero {
	display: none;
}

.tabs {
	padding: 18rpx 30rpx 2rpx;
	border-bottom: 1rpx solid rgba(46, 107, 77, 0.08);
}

.scroll {
	padding-top: 0;
}

.post-card {
	padding: 28rpx 0 26rpx;
}

.content {
	margin-top: 18rpx;
	font-size: 29rpx;
	line-height: 1.62;
}

.media-grid {
	margin-top: 16rpx;
	border-radius: 20rpx;
}

.post-foot {
	margin-top: 18rpx;
	justify-content: flex-start;
	gap: 34rpx;
}

.foot-item {
	flex: 0 0 auto;
	height: 40rpx;
	gap: 8rpx;
	font-size: 23rpx;
}

.foot-item text:nth-child(2) {
	display: none;
}

.foot-item--primary {
	margin-left: auto;
}
</style>
