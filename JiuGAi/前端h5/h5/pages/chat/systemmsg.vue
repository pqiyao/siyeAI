<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="pageTitle" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view v-if="loading" class="empty empty--loading">
				<view class="loading-line"></view>
				<view class="loading-line"></view>
				<view class="loading-line"></view>
			</view>
			<view v-else-if="!list.length" class="empty">
				<text class="empty-txt">{{ loadFailed ? loadErrorText : emptyText }}</text>
				<view v-if="loadFailed" class="empty-retry" @tap="load">{{ uiText.retry }}</view>
			</view>
			<view v-for="item in list" :key="item.id + '_' + item.createdAt" class="card">
				<view class="card-hd">
					<text class="tag" :class="{ 'tag--review': item.tagType === 'review' }">{{ item.tagText }}</text>
					<text class="time">{{ item.createtime_attr }}</text>
				</view>
				<text class="card-title">{{ item.title }}</text>
				<text class="card-body">{{ item.content }}</text>
			</view>
			<view class="pad"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const tavernErrors = require('@/common/tavernErrors.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const SYSTEM_MSG_TEXT = {
	'zh-cn': {
		title: '系统消息',
		empty: '暂无系统消息',
		retry: '点击重试',
		loadFailed: '加载失败',
		systemTag: '系统消息',
		reviewTag: '审核通知',
		inboxTag: '站内消息'
	},
	'zh-hk': {
		title: '系統消息',
		empty: '暫無系統消息',
		retry: '點擊重試',
		loadFailed: '載入失敗',
		systemTag: '系統消息',
		reviewTag: '審核通知',
		inboxTag: '站內消息'
	},
	en: {
		title: 'System Messages',
		empty: 'No system messages yet',
		retry: 'Tap to retry',
		loadFailed: 'Failed to load',
		systemTag: 'System',
		reviewTag: 'Review',
		inboxTag: 'Inbox'
	},
	ko: {
		title: '시스템 메시지',
		empty: '시스템 메시지가 없습니다',
		retry: '다시 시도',
		loadFailed: '불러오기에 실패했습니다',
		systemTag: '시스템',
		reviewTag: '심사 알림',
		inboxTag: '받은 메시지'
	},
	ja: {
		title: 'システムメッセージ',
		empty: 'システムメッセージはまだありません',
		retry: '再試行',
		loadFailed: '読み込みに失敗しました',
		systemTag: 'システム',
		reviewTag: '審査通知',
		inboxTag: '受信メッセージ'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			list: [],
			loadFailed: false,
			loadErrorText: '',
			loading: false
		};
	},
	computed: {
		uiText() {
			const code = getLanguageCode();
			return SYSTEM_MSG_TEXT[code] || SYSTEM_MSG_TEXT['zh-cn'];
		},
		pageTitle() {
			return this.uiText.title;
		},
		emptyText() {
			return this.uiText.empty;
		}
	},
	onShow() {
		this.load();
	},
	methods: {
		goBack() {
			this.util.safeNavigateBack('/pages/tavern/tavernInbox');
		},
		formatTime(value) {
			if (!value) return '';
			const d = new Date(value);
			if (isNaN(d.getTime())) return '';
			const pad = (n) => String(n).padStart(2, '0');
			return (
				d.getFullYear() +
				'-' +
				pad(d.getMonth() + 1) +
				'-' +
				pad(d.getDate()) +
				' ' +
				pad(d.getHours()) +
				':' +
				pad(d.getMinutes())
			);
		},
		load() {
			this.loading = true;
			this.loadFailed = false;
			this.loadErrorText = '';
			if (!tavernApi.jgEnabled()) {
				this.list = [];
				this.loading = false;
				return;
			}
			Promise.all([tavernApi.fetchAppNotices(), tavernApi.fetchUserMessages(tavernApi.getClientUid(), 30)])
				.then(([notices, messages]) => {
					const systemRows = (Array.isArray(notices) ? notices : []).map((item) => ({
						...item,
						tagType: 'system',
						tagText: this.uiText.systemTag,
						createtime_attr: this.formatTime(item.createdAt)
					}));
					const reviewRows = (Array.isArray(messages) ? messages : []).map((item) => ({
						...item,
						tagType: item.messageType === 'CHARACTER_REVIEW' ? 'review' : 'system',
						tagText:
							item.messageType === 'CHARACTER_REVIEW' ? this.uiText.reviewTag : this.uiText.inboxTag,
						createtime_attr: this.formatTime(item.createdAt)
					}));
					this.list = systemRows.concat(reviewRows).sort((a, b) => {
						const ta = new Date(a.createdAt || 0).getTime();
						const tb = new Date(b.createdAt || 0).getTime();
						return tb - ta;
					});
					const tavernNoticeState = require('@/common/tavernNoticeState.js');
					const { syncTavernInboxBadge } = require('@/common/tavernTabBar.js');
					tavernNoticeState
						.markAllAsRead(tavernApi)
						.then((state) => {
							const count = Math.max(0, Number(state && state.unreadCount) || 0);
							try {
								this.$store.commit('setUnreadTotal', count);
							} catch (e) {}
							syncTavernInboxBadge(this, count);
						})
						.catch(() => {
							try {
								this.$store.commit('setUnreadTotal', 0);
							} catch (e) {}
							syncTavernInboxBadge(this, 0);
						});
				})
				.catch((e) => {
					this.list = [];
					this.loadFailed = true;
					this.loadErrorText = tavernErrors.getTavernErrorMessage(e, this.uiText.loadFailed);
				})
				.finally(() => {
					this.loading = false;
				});
		}
	}
};
</script>

<style scoped lang="scss">
	$page:
		radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
		radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
	$card: rgba(255, 255, 255, 0.88);
	$text: #244b66;
	$muted: #687f92;

	.page {
		height: 100vh;
		display: flex;
		flex-direction: column;
		background: $page;
	}

	.scroll {
		flex: 1;
		height: 0;
		padding: 20rpx 28rpx 0;
		box-sizing: border-box;
	}

	.empty {
		padding: 120rpx 32rpx;
		text-align: center;
	}

	.empty--loading {
		display: flex;
		flex-direction: column;
		gap: 22rpx;
	}

	.loading-line {
		height: 26rpx;
		border-radius: 999rpx;
		background: linear-gradient(90deg, rgba(255, 255, 255, 0.04), rgba(148, 163, 184, 0.16), rgba(255, 255, 255, 0.04));
		position: relative;
		overflow: hidden;
	}

	.loading-line::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.24) 50%, transparent 100%);
		transform: translateX(-100%);
		animation: system-msg-shimmer 1.2s infinite;
	}

	.loading-line:nth-child(2) {
		width: 78%;
		margin: 0 auto;
	}

	.loading-line:nth-child(3) {
		width: 56%;
		margin: 0 auto;
	}

	.empty-txt {
		font-size: 28rpx;
		color: $muted;
		line-height: 1.5;
	}

	.empty-retry {
		margin-top: 28rpx;
		font-size: 28rpx;
		color: #247494;
		font-weight: 600;
	}

	.card {
		background: $card;
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 24rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.9);
		box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
	}

	.card-hd {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}

	.tag {
		font-size: 22rpx;
		color: #247494;
		font-weight: 600;
	}

	.tag--review {
		color: #f59e0b;
	}

	.time {
		font-size: 22rpx;
		color: $muted;
	}

	.card-title {
		display: block;
		font-size: 30rpx;
		font-weight: bold;
		color: $text;
		margin-bottom: 12rpx;
		line-height: 1.4;
	}

	.card-body {
		display: block;
		font-size: 26rpx;
		color: $muted;
		line-height: 1.55;
	}

	.pad {
		height: calc(48rpx + env(safe-area-inset-bottom));
	}

	@keyframes system-msg-shimmer {
		100% {
			transform: translateX(100%);
		}
	}
</style>

<style>
	page {
		background:
			radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
			radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
			linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
		height: 100%;
	}
</style>
