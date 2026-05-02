<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar mode="dark" :show-back="false" @back="onClose">
			<template #left>
				<view class="icon-btn" @tap="onClose">
					<text class="icon-x">×</text>
				</view>
			</template>
			<template #center>
				<view class="title-wrap">
					<text class="app-title">{{ t.应用标题 }}</text>
					<text class="title-caret">▼</text>
				</view>
			</template>
			<template #right>
				<view class="icon-btn" @tap="onMore">
					<text class="dot-more">⋯</text>
				</view>
			</template>
		</tavern-nav-bar>

		<view class="sub-bar">
			<view class="brand">
				<view class="brand-mark">
					<text class="heart">♥</text>
					<view class="crescent"></view>
				</view>
				<text class="brand-text">AI Chat</text>
			</view>
			<view class="promo-pill" @tap="openPromo">
				<text class="promo-ico">💵</text>
				<text class="promo-txt">{{ t.促销多重 }}</text>
			</view>
			<view class="sub-icons">
				<text class="sub-ic" @tap="toastMail">✉</text>
				<text class="sub-ic lang" @tap="toastLang">A/EN</text>
			</view>
		</view>

		<view class="section-head">
			<text class="section-title">{{ t.最近聊天 }}</text>
		</view>

		<view v-if="jgInboxLoading" class="inbox-banner inbox-banner--load">
			<text class="inbox-banner-txt">{{ t.详情加载中 || '加载中…' }}</text>
		</view>
		<view v-else-if="jgInboxError" class="inbox-banner inbox-banner--err">
			<text class="inbox-banner-txt">{{ jgInboxError }}</text>
			<text class="inbox-banner-retry" @tap="loadInboxSessions">{{ t.发现点击重试 || '点击重试' }}</text>
		</view>

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="card" v-for="s in sessions" :key="s.id" @tap="openSession(s)">
				<view class="thumb-wrap">
					<image
						class="thumb-img"
						:src="sessionCover(s)"
						mode="aspectFill"
						lazy-load
					></image>
				</view>
				<view class="card-main">
					<view v-if="s.privatePhoto" class="tag-private">{{ t.发私照 }}</view>
					<view class="card-body">
						<text class="card-title" :class="{ 'title-with-badge': s.privatePhoto }">{{ displayTitle(s) }}</text>
						<text class="card-author">{{ t.作者 }}：{{ sessionAuthor(s) }}</text>
						<text class="card-desc">{{ sessionSnippet(s) }}</text>
					</view>
					<view class="card-actions" @tap.stop>
						<view class="action-links">
							<text class="link" @tap.stop="deleteRecord(s)">{{ t.删除记录 }}</text>
						</view>
						<view v-if="s.unread > 0" class="unread-dot">{{ s.unread > 99 ? '99+' : s.unread }}</view>
					</view>
				</view>
			</view>

			<view class="card card-system" @tap="goSystem">
				<view class="thumb-wrap">
					<image class="thumb-img" src="/static/chat/c0.png" mode="aspectFill" lazy-load></image>
				</view>
				<view class="card-main">
					<view v-if="noticeUnread > 0" class="card-unread-badge">{{ noticeUnread > 99 ? '99+' : noticeUnread }}</view>
					<view class="card-body">
						<text class="card-title">{{ t.系统公告 }}</text>
						<text class="card-author">{{ t.作者 }}：官方</text>
						<text class="card-desc">{{ t.公告内容 }}</text>
					</view>
				</view>
			</view>

			<view class="card card-system" @tap="goHelper">
				<view class="thumb-wrap">
					<image class="thumb-img" src="/static/chat/c1.png" mode="aspectFill" lazy-load></image>
				</view>
				<view class="card-main">
					<view class="card-body">
						<text class="card-title">{{ t.酒馆小助手 }}</text>
						<text class="card-author">{{ t.作者 }}：{{ t.官方客服 || '官方客服' }}</text>
						<text class="card-desc">{{ t.助手副标题 }}</text>
					</view>
				</view>
			</view>

			<view class="scroll-pad"></view>
		</scroll-view>
	</view>
</template>

<script>
	import { applyTavernTabBarLabels, syncTavernTabBar, syncTavernInboxBadge } from '@/common/tavernTabBar.js';
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	const tavernApi = require('@/common/tavernApi.js');
	const tavernErrors = require('@/common/tavernErrors.js');
	const tavernNoticeState = require('@/common/tavernNoticeState.js');

	export default {
		components: { TavernNavBar },
		data() {
			return {
				sessions: [],
				jgInboxLoading: false,
				jgInboxError: '',
				noticeUnread: 0
			};
		},
		computed: {
			t() {
				return this.allText.酒馆页 || {};
			}
		},
		onLoad() {
			const sys = uni.getSystemInfoSync();
			this.statusBarH = sys.statusBarHeight || 20;
		},
		onShow() {
			this.setTabText();
			this.loadInboxSessions();
			this.refreshNoticeUnread();
		},
		methods: {
			loadInboxSessions() {
				if (!tavernApi.jgEnabled()) {
					this.jgInboxLoading = false;
					this.sessions = [];
					this.jgInboxError = this.t.发现后端未配置 || '后端接口未开启';
					return;
				}
				this.jgInboxLoading = true;
				this.jgInboxError = '';
				tavernApi
					.fetchTavernSessions(tavernApi.getClientUid())
					.then((list) => {
						this.sessions = Array.isArray(list) ? list : [];
						this.jgInboxError = '';
					})
					.catch((e) => {
						this.sessions = [];
						this.jgInboxError = tavernErrors.getTavernErrorMessage(
							e,
							(this.allText.酒馆页 && this.allText.酒馆页.收件箱加载失败) || ''
						);
					})
					.finally(() => {
						this.jgInboxLoading = false;
					});
			},
			setTabText() {
				applyTavernTabBarLabels(this.allText, this);
				syncTavernTabBar(this, 'pages/tavern/tavernInbox', this.allText);
			},
			refreshNoticeUnread() {
				if (!tavernApi.jgEnabled()) {
					this.noticeUnread = 0;
					syncTavernInboxBadge(this, 0);
					return;
				}
				tavernNoticeState
					.fetchUnreadState(tavernApi, 30)
					.then(({ unreadCount }) => {
						const count = Number(unreadCount) || 0;
						this.noticeUnread = count;
						try {
							this.$store.commit('setUnreadTotal', count);
						} catch (e) {}
						syncTavernInboxBadge(this, count);
					})
					.catch(() => {
						const fallback = Number((this.$store && this.$store.state && this.$store.state.unreadTotal) || 0);
						this.noticeUnread = fallback;
						syncTavernInboxBadge(this, fallback);
					});
			},
			displayTitle(s) {
				return s.displayTitle || s.nickname || this.t.收件箱默认标题 || '会话';
			},
			sessionAuthor(s) {
				return s.authorOverride || s.creatorName || this.t.匿名作者 || '匿名';
			},
			sessionSnippet(s) {
				return s.snippet || s.lastMessage || '';
			},
			sessionCover(s) {
				const u = s.coverUrl || s.avatarUrl;
				if (!u || String(u).trim() === '') return '/static/logo.png';
				return tavernApi.resolveJgAssetUrl(u) || '/static/logo.png';
			},
			openSession(s) {
				if (s.characterId == null || s.characterId === '') return;
				uni.navigateTo({ url: '/pages/tavern/tavernChat?id=' + s.characterId });
			},
			deleteRecord(s) {
				const cid = s.characterId;
				if (cid == null || cid === '') {
					uni.showToast({ title: this.t.删除失败无角色 || '无法删除', icon: 'none' });
					return;
				}
				uni.showModal({
					title: this.t.删除会话标题 || '',
					content: this.t.删除会话确认,
					confirmText: this.t.删除会话确定 || '删除',
					cancelText: this.t.关闭 || '取消',
					success: (res) => {
						if (!res.confirm) return;
						tavernApi
							.postTavernSessionDelete({
								characterId: Number(cid),
								clientUid: tavernApi.getClientUid()
							})
							.then(() => {
								this.sessions = this.sessions.filter((x) => x.id !== s.id);
								uni.showToast({ title: this.t.记录已删除成功, icon: 'none' });
							})
							.catch((e) => {
								uni.showToast({
									title: tavernErrors.getTavernErrorMessage(
										e,
										this.t.删除会话失败 || '删除失败'
									),
									icon: 'none'
								});
							});
					}
				});
			},
			goSystem() {
				uni.navigateTo({ url: '/pages/chat/systemmsg' });
			},
			goHelper() {
				uni.navigateTo({ url: '/pages/user/supportTickets' });
			},
			onClose() {
				this.util.safeNavigateBack('/pages/index/index');
			},
			onMore() {
				uni.showToast({ title: this.t.更多菜单, icon: 'none' });
			},
			openPromo() {
				const api = require('@/common/api.js');
				const ext = api.inboxPromoExternalUrl != null ? String(api.inboxPromoExternalUrl).trim() : '';
				if (ext && /^https?:\/\//i.test(ext)) {
					/* #ifdef H5 */
					if (typeof window !== 'undefined' && window.open) {
						window.open(ext, '_blank');
						return;
					}
					/* #endif */
				}
				const p = api.inboxPromoInternalPath || '/pages/chat/systemmsg';
				uni.navigateTo({ url: p });
			},
			toastMail() {
				this.goSystem();
			},
			toastLang() {
				uni.navigateTo({ url: '/pages/user/language' });
			}
		}
	};
</script>

<style scoped lang="scss">
	$page-bg: $tavern-surface-dark;
	$card-bg: $tavern-card-dark;
	$text: #ffffff;
	$muted: $tavern-muted-on-dark;
	$desc: #a8b0c4;
	$link: #6eb5ff;
	$pink: #f472b6;
	$violet: #a78bfa;

	.page {
		height: 100vh;
		display: flex;
		flex-direction: column;
		background: $page-bg;
		padding-bottom: env(safe-area-inset-bottom);
		box-sizing: border-box;
	}

	.icon-btn {
		min-width: 88rpx;
		min-height: 72rpx;
		padding: 0 8rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		box-sizing: border-box;
	}

	.icon-x {
		font-size: 48rpx;
		color: $text;
		line-height: 1;
		font-weight: 300;
	}

	.dot-more {
		font-size: 44rpx;
		color: $text;
		line-height: 1;
		font-weight: bold;
		letter-spacing: 2rpx;
	}

	.title-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 10rpx;
		padding: 0 8rpx;
	}

	.app-title {
		font-size: 28rpx;
		font-weight: 700;
		color: $text;
		max-width: 400rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		letter-spacing: 0.5rpx;
	}

	.title-caret {
		font-size: 16rpx;
		color: $muted;
		opacity: 0.85;
	}

	.sub-bar {
		display: flex;
		align-items: center;
		padding: 16rpx 28rpx 20rpx;
		gap: 14rpx;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: 12rpx;
		flex-shrink: 0;
	}

	.brand-mark {
		width: 48rpx;
		height: 48rpx;
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.heart {
		font-size: 22rpx;
		color: #fda4af;
		line-height: 1;
		z-index: 1;
	}

	.crescent {
		position: absolute;
		width: 36rpx;
		height: 36rpx;
		border-radius: 50%;
		background: linear-gradient(145deg, $violet 0%, $pink 100%);
		opacity: 0.95;
		box-shadow: 0 0 12rpx rgba($pink, 0.35);
	}

	.brand-text {
		font-size: 32rpx;
		font-weight: 800;
		color: $text;
		letter-spacing: 1rpx;
	}

	.promo-pill {
		flex: 1;
		min-width: 0;
		height: 60rpx;
		padding: 0 18rpx;
		border-radius: 30rpx;
		background: linear-gradient(92deg, #6d28d9 0%, #a21caf 45%, #db2777 100%);
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 10rpx;
		box-shadow: 0 4rpx 20rpx rgba(124, 58, 237, 0.35);
	}

	.promo-ico {
		font-size: 26rpx;
		line-height: 1;
		flex-shrink: 0;
	}

	.promo-txt {
		font-size: 24rpx;
		color: #fff;
		font-weight: 700;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.sub-icons {
		display: flex;
		align-items: center;
		gap: 32rpx;
		flex-shrink: 0;
		padding-left: 4rpx;
	}

	.sub-ic {
		font-size: 34rpx;
		color: rgba(255, 255, 255, 0.88);
		line-height: 1;
	}

	.sub-ic.lang {
		font-size: 24rpx;
		font-weight: 700;
		letter-spacing: -0.5rpx;
	}

	.section-head {
		padding: 4rpx 28rpx 20rpx;
	}

	.section-title {
		font-size: 26rpx;
		color: $muted;
		font-weight: 500;
		letter-spacing: 1rpx;
	}

	.inbox-banner {
		margin: 0 28rpx 16rpx;
		padding: 20rpx 22rpx;
		border-radius: 16rpx;
		display: flex;
		flex-direction: row;
		align-items: center;
		flex-wrap: wrap;
		gap: 16rpx;
		box-sizing: border-box;
	}

	.inbox-banner--load {
		background: rgba(124, 58, 237, 0.15);
		border: 1rpx solid rgba(167, 139, 250, 0.35);
	}

	.inbox-banner--err {
		background: rgba(185, 28, 28, 0.18);
		border: 1rpx solid rgba(248, 113, 113, 0.35);
	}

	.inbox-banner-txt {
		flex: 1;
		min-width: 0;
		font-size: 24rpx;
		color: $desc;
		line-height: 1.45;
	}

	.inbox-banner--err .inbox-banner-txt {
		color: #fecaca;
	}

	.inbox-banner-retry {
		font-size: 24rpx;
		color: $link;
		font-weight: 600;
		flex-shrink: 0;
	}

	.scroll {
		flex: 1;
		height: 0;
		width: 100%;
		box-sizing: border-box;
		padding-top: 4rpx;
	}

	.card {
		display: flex;
		flex-direction: row;
		align-items: stretch;
		margin: 0 28rpx 24rpx;
		background: $card-bg;
		border-radius: 20rpx;
		overflow: hidden;
		box-shadow: 0 8rpx 28rpx rgba(0, 0, 0, 0.35);
		border: 1rpx solid rgba(255, 255, 255, 0.05);
	}

	.card-system {
		opacity: 0.92;
	}

	.card-unread-badge {
		position: absolute;
		top: 20rpx;
		right: 20rpx;
		z-index: 2;
		min-width: 32rpx;
		height: 32rpx;
		padding: 0 8rpx;
		border-radius: 999rpx;
		background: linear-gradient(135deg, #fb7185 0%, #ef4444 100%);
		color: #fff;
		font-size: 20rpx;
		font-weight: 700;
		line-height: 32rpx;
		text-align: center;
		box-sizing: border-box;
		box-shadow: 0 8rpx 18rpx rgba(239, 68, 68, 0.28);
	}

	.thumb-wrap {
		flex: 0 0 31%;
		width: 31%;
		min-width: 208rpx;
		max-width: 260rpx;
		align-self: stretch;
		min-height: 280rpx;
		position: relative;
		overflow: hidden;
		background: #0c0c18;
	}

	.thumb-img {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
		display: block;
		/* #ifdef H5 */
		object-fit: cover;
		object-position: center 22%;
		/* #endif */
		/* #ifdef APP-PLUS */
		object-fit: cover;
		object-position: center 22%;
		/* #endif */
	}

	.card-main {
		flex: 1;
		min-width: 0;
		padding: 22rpx 22rpx 18rpx 20rpx;
		display: flex;
		flex-direction: column;
		position: relative;
	}

	.tag-private {
		position: absolute;
		top: 14rpx;
		right: 14rpx;
		z-index: 2;
		font-size: 20rpx;
		color: #fff;
		background: linear-gradient(180deg, #ef4444 0%, #b91c1c 100%);
		padding: 6rpx 14rpx;
		border-radius: 8rpx;
		font-weight: 600;
		line-height: 1.2;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.25);
	}

	.card-body {
		flex: 1;
		min-height: 0;
		display: flex;
		flex-direction: column;
	}

	.card-title {
		font-size: 32rpx;
		font-weight: 800;
		color: $text;
		line-height: 1.35;
		letter-spacing: 0.5rpx;
	}

	.title-with-badge {
		padding-right: 132rpx;
	}

	.card-author {
		margin-top: 12rpx;
		font-size: 22rpx;
		color: $muted;
		line-height: 1.3;
	}

	.card-desc {
		margin-top: 14rpx;
		font-size: 24rpx;
		color: $desc;
		line-height: 1.5;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 3;
		overflow: hidden;
		flex: 1;
	}

	.card-actions {
		flex-shrink: 0;
		margin-top: auto;
		padding-top: 18rpx;
		display: flex;
		flex-direction: row;
		align-items: center;
		justify-content: flex-end;
		gap: 16rpx;
	}

	.action-links {
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 36rpx;
	}

	.link {
		font-size: 24rpx;
		color: $link;
		font-weight: 500;
	}

	.unread-dot {
		min-width: 36rpx;
		height: 36rpx;
		line-height: 36rpx;
		padding: 0 10rpx;
		background: #ec4899;
		color: #fff;
		font-size: 20rpx;
		font-weight: 700;
		border-radius: 18rpx;
		text-align: center;
	}

	.scroll-pad {
		height: calc(#{$tavern-tabbar-spacer} + 40rpx + env(safe-area-inset-bottom));
	}
</style>

<style>
	page {
		background-color: #12121b;
		height: 100%;
	}
</style>
