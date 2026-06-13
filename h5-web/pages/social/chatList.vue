<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar title="真人聊天" mode="dark" @back="goBack">
			<view v-if="showCommunityEntry" slot="right" class="nav-action" @tap="openFeed">
				<text class="cuIcon-community nav-ico"></text>
			</view>
		</tavern-nav-bar>

		<view class="mode-tabs">
			<view class="mode-tab" :class="{ active: mode === 'conversations' }" @tap="switchMode('conversations')">会话</view>
			<view v-if="showFriendEntry" class="mode-tab" :class="{ active: mode === 'friends' }" @tap="switchMode('friends')">好友</view>
			<view v-if="showRequestsEntry" class="mode-tab" :class="{ active: mode === 'requests' }" @tap="switchMode('requests')">申请</view>
		</view>

		<view v-if="mode === 'friends'" class="relation-tabs">
			<view class="relation-tab" :class="{ active: friendRelation === 'all' }" @tap="switchFriendRelation('all')">全部好友</view>
			<view class="relation-tab" :class="{ active: friendRelation === 'mutual' }" @tap="switchFriendRelation('mutual')">互相关注</view>
		</view>

		<scroll-view
			scroll-y
			class="scroll"
			:show-scrollbar="false"
			:lower-threshold="220"
			@scrolltolower="loadMore"
		>
			<block v-if="mode === 'conversations'">
				<view v-if="loading && !conversations.length" class="state-card">正在加载会话...</view>
				<view v-else-if="errorMsg && !conversations.length" class="state-card">
					<text class="state-title">{{ errorMsg }}</text>
					<view class="state-btn" @tap="reload">重试</view>
				</view>
				<view v-else-if="!conversations.length" class="state-card">
					<text class="state-title">还没有真人会话</text>
					<text class="state-desc">去社区发现感兴趣的人，成为好友后就能从这里继续聊天。</text>
					<view v-if="showCommunityEntry" class="state-btn" @tap="openFeed">去社区看看</view>
				</view>

				<view
					v-for="item in conversations"
					:key="item.conversationId || item.conversationKey"
					class="list-row"
					@tap="openChat(item)"
				>
					<view class="avatar-wrap">
						<image class="avatar" :src="peerAvatar(item)" mode="aspectFill"></image>
						<view v-if="socialFeatureConfig.onlineStatusVisible !== false" class="avatar-status"></view>
					</view>
					<view class="row-main">
						<view class="row-top">
							<text class="name">{{ peerName(item) }}</text>
							<text class="time">{{ formatTime(item.lastMessageAt || item.updatedAt) }}</text>
						</view>
						<text class="preview">{{ item.lastMessagePreview || '还没有消息' }}</text>
					</view>
					<view v-if="Number(item.unreadCount) > 0" class="badge">
						{{ Number(item.unreadCount) > 99 ? '99+' : Number(item.unreadCount) }}
					</view>
				</view>
			</block>

			<block v-else-if="mode === 'friends'">
				<view v-if="friendsLoading && !friends.length" class="state-card">正在加载好友...</view>
				<view v-else-if="friendsErrorMsg && !friends.length" class="state-card">
					<text class="state-title">{{ friendsErrorMsg }}</text>
					<view class="state-btn" @tap="reloadFriends">重试</view>
				</view>
				<view v-else-if="!friends.length" class="state-card">
					<text class="state-title">{{ friendRelation === 'mutual' ? '还没有互相关注好友' : '还没有好友' }}</text>
					<text class="state-desc">在动态详情里发送好友申请，对方同意后会出现在这里。</text>
					<view v-if="showCommunityEntry" class="state-btn" @tap="openFeed">发现好友</view>
				</view>

				<view
					v-for="friend in friends"
					:key="friend.userId"
					class="list-row friend-row"
					@tap="openFriendChat(friend)"
				>
					<view class="avatar-wrap">
						<image class="avatar" :src="friendAvatar(friend)" mode="aspectFill"></image>
						<view class="avatar-status avatar-status--friend"></view>
					</view>
					<view class="row-main">
						<view class="row-top">
							<text class="name">{{ friendName(friend) }}</text>
							<text class="friend-tag">{{ friend.mutual ? '互相关注' : '好友' }}</text>
						</view>
						<text class="preview">{{ friend.lastMessagePreview || friend.bio || '可以开始聊天了' }}</text>
					</view>
					<view class="chat-cta" @tap.stop="openFriendChat(friend)">
						<text class="cuIcon-messagefill"></text>
					</view>
				</view>
			</block>

			<block v-else>
				<view v-if="requestsLoading && !friendRequests.length" class="state-card">正在加载好友申请...</view>
				<view v-else-if="requestsErrorMsg && !friendRequests.length" class="state-card">
					<text class="state-title">{{ requestsErrorMsg }}</text>
					<view class="state-btn" @tap="reloadRequests">重试</view>
				</view>
				<view v-else-if="!friendRequests.length" class="state-card">
					<text class="state-title">暂无好友申请</text>
					<text class="state-desc">别人发来的好友申请会出现在这里。</text>
					<view v-if="showCommunityEntry" class="state-btn" @tap="openFeed">去社区看看</view>
				</view>

				<view
					v-for="request in friendRequests"
					:key="request.requestId"
					class="list-row friend-row"
				>
					<view class="avatar-wrap">
						<image class="avatar" :src="requestAvatar(request)" mode="aspectFill"></image>
						<view class="avatar-status avatar-status--friend"></view>
					</view>
					<view class="row-main">
						<view class="row-top">
							<text class="name">{{ requestName(request) }}</text>
							<text class="friend-tag">{{ requestStatusText(request) }}</text>
						</view>
						<text class="preview">{{ request.requestMessage || request.peerBio || '想添加你为好友' }}</text>
					</view>
					<view v-if="request.status === 'pending'" class="request-actions">
						<view class="request-btn" @tap.stop="handleFriendRequest(request, 'reject')">拒绝</view>
						<view class="request-btn primary" @tap.stop="handleFriendRequest(request, 'accept')">同意</view>
					</view>
				</view>
			</block>

			<view v-if="currentRows.length" class="list-tail">
				<text v-if="currentLoading">正在加载...</text>
				<text v-else-if="currentHasMore">继续下滑加载更多</text>
				<text v-else>已经到底了</text>
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
			mode: 'conversations',
			pageNum: 1,
			pageSize: 20,
			total: 0,
			conversations: [],
			loading: false,
			errorMsg: '',
			friendRelation: 'all',
			friendsPageNum: 1,
			friendsPageSize: 20,
			friendsTotal: 0,
			friends: [],
			friendsLoading: false,
			friendsErrorMsg: '',
			requestsPageNum: 1,
			requestsPageSize: 20,
			requestsTotal: 0,
			friendRequests: [],
			requestsLoading: false,
			requestsErrorMsg: '',
			socialFeatureConfig: tavernApi.getSocialFeatureConfig()
		};
	},
	computed: {
		hasMore() {
			return this.conversations.length < this.total;
		},
		friendsHasMore() {
			return this.friends.length < this.friendsTotal;
		},
		requestsHasMore() {
			return this.friendRequests.length < this.requestsTotal;
		},
		currentRows() {
			if (this.mode === 'friends') return this.friends;
			if (this.mode === 'requests') return this.friendRequests;
			return this.conversations;
		},
		currentLoading() {
			if (this.mode === 'friends') return this.friendsLoading;
			if (this.mode === 'requests') return this.requestsLoading;
			return this.loading;
		},
		currentHasMore() {
			if (this.mode === 'friends') return this.friendsHasMore;
			if (this.mode === 'requests') return this.requestsHasMore;
			return this.hasMore;
		},
		showCommunityEntry() {
			const config = this.socialFeatureConfig || {};
			return config.communityEnabled !== false && config.communityEntryVisible !== false;
		},
		showFriendEntry() {
			const config = this.socialFeatureConfig || {};
			return config.friendEnabled === true && config.friendEntryVisible === true;
		},
		showRequestsEntry() {
			const config = this.socialFeatureConfig || {};
			return this.showFriendEntry && config.friendRequestEnabled === true;
		},
		canOpenChat() {
			const config = this.socialFeatureConfig || {};
			return config.chatEnabled !== false && config.chatEntryVisible !== false && config.existingChatEnabled !== false;
		}
	},
	onLoad(options) {
		if (!tavernApi.hasLoggedInUser()) {
			uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/chatList') });
			return;
		}
		if (options && (options.mode === 'friends' || options.mode === 'requests')) {
			this.mode = options.mode;
		}
		this.loadSocialFeatureConfig(true).then(() => {
			if (this.mode !== 'conversations' && !this.showFriendEntry) {
				this.mode = 'conversations';
			}
			this.reloadCurrent();
		});
	},
	onShow() {
		if (!tavernApi.hasLoggedInUser()) return;
		this.loadSocialFeatureConfig(true).then(() => this.reloadCurrent());
	},
	onPullDownRefresh() {
		this.reloadCurrent().finally(() => uni.stopPullDownRefresh());
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/social/feed' }) });
		},
		switchMode(next) {
			if (this.mode === next) return;
			if ((next === 'friends' || next === 'requests') && !this.showFriendEntry) {
				uni.showToast({ title: '好友入口暂未开放', icon: 'none' });
				return;
			}
			if (next === 'requests' && !this.showRequestsEntry) {
				uni.showToast({ title: '好友申请暂未开放', icon: 'none' });
				return;
			}
			this.mode = next;
			this.reloadCurrent();
		},
		switchFriendRelation(next) {
			if (this.friendRelation === next) return;
			this.friendRelation = next;
			this.reloadFriends();
		},
		reloadCurrent() {
			if (this.mode === 'friends') return this.reloadFriends();
			if (this.mode === 'requests') return this.reloadRequests();
			return this.reload();
		},
		openFeed() {
			if (!this.showCommunityEntry) {
				uni.showToast({ title: '社区暂未开放', icon: 'none' });
				return;
			}
			uni.navigateTo({ url: '/pages/social/feed' });
		},
		reload() {
			this.pageNum = 1;
			this.total = 0;
			return this.fetchConversations(true);
		},
		reloadFriends() {
			this.friendsPageNum = 1;
			this.friendsTotal = 0;
			return this.fetchFriends(true);
		},
		reloadRequests() {
			this.requestsPageNum = 1;
			this.requestsTotal = 0;
			return this.fetchRequests(true);
		},
		loadMore() {
			if (this.mode === 'friends') {
				if (this.friendsLoading || !this.friendsHasMore) return;
				this.friendsPageNum += 1;
				this.fetchFriends(false);
				return;
			}
			if (this.mode === 'requests') {
				if (this.requestsLoading || !this.requestsHasMore) return;
				this.requestsPageNum += 1;
				this.fetchRequests(false);
				return;
			}
			if (this.loading || !this.hasMore) return;
			this.pageNum += 1;
			this.fetchConversations(false);
		},
		fetchConversations(reset) {
			this.loading = true;
			this.errorMsg = '';
			return tavernApi.fetchSocialChatConversations({
				pageNum: this.pageNum,
				pageSize: this.pageSize
			}).then((data) => {
				const rows = Array.isArray(data && data.rows) ? data.rows : [];
				this.total = Number(data && data.total) || rows.length;
				this.conversations = reset ? rows : this.conversations.concat(rows);
			}).catch((error) => {
				if (this.pageNum > 1) this.pageNum -= 1;
				this.errorMsg = (error && error.message) || '会话加载失败';
				uni.showToast({ title: this.errorMsg, icon: 'none' });
			}).finally(() => {
				this.loading = false;
			});
		},
		fetchFriends(reset) {
			this.friendsLoading = true;
			this.friendsErrorMsg = '';
			return tavernApi.fetchCommunityFriends({
				relation: this.friendRelation,
				pageNum: this.friendsPageNum,
				pageSize: this.friendsPageSize
			}).then((data) => {
				const rows = Array.isArray(data && data.rows) ? data.rows : [];
				this.friendsTotal = Number(data && data.total) || rows.length;
				this.friends = reset ? rows : this.friends.concat(rows);
			}).catch((error) => {
				if (this.friendsPageNum > 1) this.friendsPageNum -= 1;
				this.friendsErrorMsg = (error && error.message) || '好友加载失败';
				uni.showToast({ title: this.friendsErrorMsg, icon: 'none' });
			}).finally(() => {
				this.friendsLoading = false;
			});
		},
		fetchRequests(reset) {
			this.requestsLoading = true;
			this.requestsErrorMsg = '';
			return tavernApi.fetchCommunityFriendRequests({
				box: 'received',
				pageNum: this.requestsPageNum,
				pageSize: this.requestsPageSize
			}).then((data) => {
				const rows = Array.isArray(data && data.rows) ? data.rows : [];
				this.requestsTotal = Number(data && data.total) || rows.length;
				this.friendRequests = reset ? rows : this.friendRequests.concat(rows);
			}).catch((error) => {
				if (this.requestsPageNum > 1) this.requestsPageNum -= 1;
				this.requestsErrorMsg = (error && error.message) || '好友申请加载失败';
				uni.showToast({ title: this.requestsErrorMsg, icon: 'none' });
			}).finally(() => {
				this.requestsLoading = false;
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
		handleFriendRequest(request, action) {
			if (!request || !request.requestId || request.status !== 'pending') return;
			const api = action === 'accept'
				? tavernApi.acceptCommunityFriendRequest
				: tavernApi.rejectCommunityFriendRequest;
			api(request.requestId).then(() => {
				uni.showToast({ title: action === 'accept' ? '已同意' : '已拒绝', icon: 'none' });
				this.reloadRequests();
				if (action === 'accept') {
					this.reloadFriends();
				}
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
			});
		},
		openChat(item) {
			if (!this.canOpenChat) {
				uni.showToast({ title: '当前已关闭真人聊天', icon: 'none' });
				return;
			}
			const peer = item.peerUser || {};
			const peerId = item.peerUserId || peer.userId;
			if (!peerId) return;
			this.openPeerChat(peerId, this.peerName(item));
		},
		openFriendChat(friend) {
			if (!this.canOpenChat) {
				uni.showToast({ title: '当前已关闭真人聊天', icon: 'none' });
				return;
			}
			if (!friend || !friend.userId) return;
			this.openPeerChat(friend.userId, this.friendName(friend));
		},
		openPeerChat(peerId, name) {
			uni.navigateTo({
				url: '/pages/social/chatPage?peerId=' + encodeURIComponent(peerId) + '&name=' + encodeURIComponent(name || ('用户' + peerId))
			});
		},
		peerName(item) {
			const peer = item.peerUser || {};
			return peer.nickname || item.peerNickname || ('用户' + (item.peerUserId || ''));
		},
		peerAvatar(item) {
			const peer = item.peerUser || {};
			return tavernApi.resolveJgAssetUrl(peer.avatar || item.peerAvatar || '/static/logo.png');
		},
		friendName(friend) {
			return friend.nickname || ('用户' + (friend.userId || ''));
		},
		friendAvatar(friend) {
			return tavernApi.resolveJgAssetUrl(friend.avatar || '/static/logo.png');
		},
		requestName(request) {
			return request.peerNickname || ('用户' + (request.peerUserId || ''));
		},
		requestAvatar(request) {
			return tavernApi.resolveJgAssetUrl(request.peerAvatar || '/static/logo.png');
		},
		requestStatusText(request) {
			if (!request || request.status === 'pending') return '待处理';
			if (request.status === 'accepted') return '已同意';
			if (request.status === 'rejected') return '已拒绝';
			return request.status || '';
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
	background: linear-gradient(180deg, #f7fbf8 0%, #fffaf3 100%);
}

.nav-action {
	width: 54rpx;
	height: 54rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #2e6b4d;
}

.nav-ico {
	font-size: 28rpx;
	line-height: 1;
}

.mode-tabs,
.relation-tabs {
	position: relative;
	z-index: 1;
	display: flex;
	gap: 34rpx;
	padding: 18rpx 30rpx 2rpx;
	border-bottom: 1rpx solid rgba(46, 107, 77, 0.08);
}

.relation-tabs {
	padding-top: 12rpx;
	border-bottom: 0;
	gap: 28rpx;
}

.mode-tab,
.relation-tab {
	position: relative;
	height: 54rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #7b877f;
	font-size: 26rpx;
	font-weight: 900;
}

.relation-tab {
	font-size: 24rpx;
}

.mode-tab.active,
.relation-tab.active {
	color: #1f2933;
}

.mode-tab.active::after,
.relation-tab.active::after {
	content: '';
	position: absolute;
	left: 10rpx;
	right: 10rpx;
	bottom: 0;
	height: 5rpx;
	border-radius: 999rpx;
	background: #2e6b4d;
}

.scroll {
	position: relative;
	z-index: 1;
	flex: 1;
	min-height: 0;
	padding: 0 30rpx;
	box-sizing: border-box;
}

.state-card {
	padding: 54rpx 24rpx;
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
}

.state-btn {
	margin: 28rpx auto 0;
	width: 220rpx;
	height: 68rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #2e6b4d;
	color: #fff;
	font-size: 26rpx;
	font-weight: 900;
}

.list-row {
	position: relative;
	padding: 26rpx 0;
	display: flex;
	align-items: center;
	gap: 16rpx;
	border-bottom: 1rpx solid rgba(46, 107, 77, 0.1);
}

.avatar-wrap,
.avatar {
	width: 84rpx;
	height: 84rpx;
	flex-shrink: 0;
}

.avatar-wrap {
	position: relative;
}

.avatar {
	border-radius: 50%;
	background: #e6efe8;
	border: 3rpx solid #ffffff;
	box-sizing: border-box;
}

.avatar-status {
	position: absolute;
	right: 2rpx;
	bottom: 4rpx;
	width: 18rpx;
	height: 18rpx;
	border-radius: 50%;
	background: #e36f5f;
	border: 3rpx solid #ffffff;
}

.avatar-status--friend {
	background: #2e6b4d;
}

.row-main {
	flex: 1;
	min-width: 0;
}

.row-top {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.name {
	flex: 1;
	min-width: 0;
	color: #1f2933;
	font-size: 29rpx;
	font-weight: 900;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.time {
	color: #7b877f;
	font-size: 22rpx;
}

.preview {
	display: block;
	margin-top: 7rpx;
	color: #65736a;
	font-size: 24rpx;
	line-height: 1.35;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.badge {
	min-width: 38rpx;
	height: 38rpx;
	padding: 0 10rpx;
	border-radius: 999rpx;
	background: #e36f5f;
	color: #fff;
	font-size: 20rpx;
	font-weight: 900;
	line-height: 38rpx;
	text-align: center;
	box-sizing: border-box;
}

.friend-tag {
	flex-shrink: 0;
	color: #2e6b4d;
	font-size: 22rpx;
	font-weight: 900;
}

.chat-cta {
	width: 56rpx;
	height: 44rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #2e6b4d;
	font-size: 30rpx;
	flex-shrink: 0;
}

.request-actions {
	display: flex;
	gap: 10rpx;
	flex-shrink: 0;
}

.request-btn {
	height: 52rpx;
	padding: 0 16rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(101, 115, 106, 0.1);
	color: #65736a;
	font-size: 23rpx;
	font-weight: 900;
}

.request-btn.primary {
	background: #2e6b4d;
	color: #fff;
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
</style>
