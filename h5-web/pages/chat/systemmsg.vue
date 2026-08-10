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
				<view class="empty-icon">
					<u-icon name="email-fill" color="#4f93a3" size="42"></u-icon>
				</view>
				<text class="empty-txt">{{ loadFailed ? loadErrorText : emptyText }}</text>
				<view v-if="loadFailed" class="empty-retry" @tap="load">{{ uiText.retry }}</view>
			</view>
			<view v-for="item in list" :key="item.id + '_' + item.createdAt" class="card">
				<view class="card-accent"></view>
				<view class="card-hd">
					<view class="tag" :class="{ 'tag--review': item.tagType === 'review' }">
						<u-icon :name="item.tagType === 'review' ? 'checkmark-circle-fill' : 'volume-fill'" :color="item.tagType === 'review' ? '#b7791f' : '#4f93a3'" size="21"></u-icon>
						<text>{{ item.tagText }}</text>
					</view>
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
			loading: false,
			viewerIdentitySignature: '',
			loadRequestVersion: 0
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
	onUnload() {
		this.loadRequestVersion += 1;
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
		getViewerIdentitySignature() {
			try {
				if (typeof tavernApi.getViewerIdentitySignature === 'function') {
					return String(tavernApi.getViewerIdentitySignature() || '');
				}
				return 'client:' + String(tavernApi.getClientUid() || '');
			} catch (e) {
				return 'unknown';
			}
		},
		prepareViewerIdentity() {
			const currentIdentity = this.getViewerIdentitySignature();
			if (this.viewerIdentitySignature && this.viewerIdentitySignature !== currentIdentity) {
				this.loadRequestVersion += 1;
				this.list = [];
				const tavernInboxBadge = require('@/common/tavernInboxBadge.js');
				tavernInboxBadge
					.refreshCombinedInboxBadge(this, tavernApi, { noticeUnread: 0, adUnread: 0 })
					.catch(() => {});
			}
			this.viewerIdentitySignature = currentIdentity;
			return currentIdentity;
		},
		isLoadRequestCurrent(identitySignature, requestVersion) {
			return (
				this.loadRequestVersion === requestVersion &&
				this.viewerIdentitySignature === identitySignature &&
				this.getViewerIdentitySignature() === identitySignature
			);
		},
		load() {
			const identitySignature = this.prepareViewerIdentity();
			const requestVersion = ++this.loadRequestVersion;
			this.loading = true;
			this.loadFailed = false;
			this.loadErrorText = '';
			if (!tavernApi.jgEnabled()) {
				this.list = [];
				this.loading = false;
				const tavernInboxBadge = require('@/common/tavernInboxBadge.js');
				tavernInboxBadge.refreshCombinedInboxBadge(this, null, {
					noticeUnread: 0,
					adUnread: 0
				});
				return;
			}
			Promise.all([tavernApi.fetchAppNotices(), tavernApi.fetchUserMessages(tavernApi.getClientUid(), 30)])
				.then(([notices, messages]) => {
					if (!this.isLoadRequestCurrent(identitySignature, requestVersion)) return;
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
					const tavernInboxBadge = require('@/common/tavernInboxBadge.js');
					return tavernNoticeState
						.markAllAsRead(tavernApi)
						.then((state) => {
							if (!this.isLoadRequestCurrent(identitySignature, requestVersion)) return;
							const count = Math.max(0, Number(state && state.unreadCount) || 0);
							return tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi, {
								noticeUnread: count
							});
						})
						.catch(() => {
							if (!this.isLoadRequestCurrent(identitySignature, requestVersion)) return;
							return tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi);
						});
				})
				.catch((e) => {
					if (!this.isLoadRequestCurrent(identitySignature, requestVersion)) return;
					this.list = [];
					this.loadFailed = true;
					this.loadErrorText = tavernErrors.getTavernErrorMessage(e, this.uiText.loadFailed);
				})
				.finally(() => {
					if (this.isLoadRequestCurrent(identitySignature, requestVersion)) {
						this.loading = false;
					}
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
		padding: 24rpx 26rpx 0;
		box-sizing: border-box;
	}

	.empty {
		margin: 24rpx 0;
		padding: 108rpx 32rpx;
		text-align: center;
		border-radius: 32rpx;
		background: rgba(255, 255, 255, 0.54);
		border: 1rpx solid rgba(255, 255, 255, 0.76);
		box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.1);
		backdrop-filter: blur(18rpx);
		-webkit-backdrop-filter: blur(18rpx);
	}

	.empty--loading {
		display: flex;
		flex-direction: column;
		gap: 22rpx;
	}

	.loading-line {
		height: 28rpx;
		border-radius: 999rpx;
		background: linear-gradient(90deg, rgba(255, 255, 255, 0.42), rgba(126, 174, 194, 0.2), rgba(255, 255, 255, 0.42));
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
		display: block;
		margin-top: 20rpx;
		font-size: 28rpx;
		color: $muted;
		line-height: 1.5;
	}

	.empty-icon {
		width: 92rpx;
		height: 92rpx;
		margin: 0 auto;
		border-radius: 30rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(228, 246, 250, 0.9);
		border: 1rpx solid rgba(79, 147, 163, 0.16);
		box-shadow: 0 16rpx 34rpx rgba(79, 147, 163, 0.12);
	}

	.empty-retry {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 180rpx;
		height: 72rpx;
		margin-top: 28rpx;
		padding: 0 28rpx;
		border-radius: 999rpx;
		font-size: 26rpx;
		color: #fff;
		background: #4f93a3;
		box-shadow: 0 14rpx 28rpx rgba(79, 147, 163, 0.2);
		font-weight: 700;
	}

	.card {
		position: relative;
		overflow: hidden;
		background: linear-gradient(145deg, rgba(255, 255, 255, 0.9) 0%, rgba(247, 252, 254, 0.76) 100%);
		border-radius: 30rpx;
		padding: 30rpx 30rpx 32rpx;
		margin-bottom: 22rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.92);
		box-shadow: 0 20rpx 48rpx rgba(67, 112, 142, 0.12), inset 0 1rpx 0 rgba(255, 255, 255, 0.86);
		backdrop-filter: blur(18rpx);
		-webkit-backdrop-filter: blur(18rpx);
	}

	.card-accent {
		position: absolute;
		left: 0;
		top: 28rpx;
		bottom: 28rpx;
		width: 5rpx;
		border-radius: 0 999rpx 999rpx 0;
		background: linear-gradient(180deg, #4f93a3 0%, #8ecbd3 68%, #cf88a7 100%);
	}

	.card-hd {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}

	.tag {
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
		padding: 8rpx 14rpx;
		border-radius: 999rpx;
		font-size: 21rpx;
		color: #4f7f8e;
		font-weight: 700;
		background: rgba(226, 245, 249, 0.86);
	}

	.tag--review {
		color: #99651a;
		background: rgba(255, 244, 214, 0.9);
	}

	.time {
		font-size: 21rpx;
		color: $muted;
	}

	.card-title {
		display: block;
		font-size: 31rpx;
		font-weight: 800;
		color: $text;
		margin-bottom: 12rpx;
		line-height: 1.4;
	}

	.card-body {
		display: block;
		font-size: 26rpx;
		color: $muted;
		line-height: 1.72;
		white-space: pre-wrap;
	}

	@media (hover: hover) and (pointer: fine) {
		.card {
			transition: transform 180ms ease, box-shadow 180ms ease;
		}

		.card:hover {
			transform: translateY(-3rpx);
			box-shadow: 0 26rpx 58rpx rgba(67, 112, 142, 0.16);
		}
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
