<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar mode="dark" :show-back="false">
			<template #center>
				<view class="title-wrap">
					<text class="app-title">{{ t.应用标题 }}</text>
				</view>
			</template>
			<template #right>
				<view class="nav-actions">
					<view class="icon-btn" @tap="goInboxAds">
						<text class="ad-mark">✧</text>
						<view v-if="adUnread > 0" class="nav-dot"></view>
					</view>
					<view class="icon-btn" @tap="onMore">
						<text class="dot-more">⋯</text>
					</view>
				</view>
			</template>
		</tavern-nav-bar>

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
			<view class="card card-system card-system--ad" @tap="goInboxAds">
				<view class="thumb-wrap">
					<image
						class="thumb-img"
						:src="adEntryCover"
						mode="aspectFill"
						lazy-load
					></image>
				</view>
				<view class="card-main">
					<view v-if="adUnread > 0" class="card-unread-badge">{{ adUnread > 99 ? '99+' : adUnread }}</view>
					<view class="card-body">
						<text class="card-title">{{ t.活动推荐 || '活动推荐' }}</text>
						<text class="card-author">{{ t.作者 }}：官方</text>
						<text class="card-desc">{{ adEntryDesc }}</text>
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

			<view class="scroll-pad"></view>
		</scroll-view>
	</view>
</template>

<script>
	import { applyTavernTabBarLabels, syncTavernTabBar } from '@/common/tavernTabBar.js';
	const tavernInboxBadge = require('@/common/tavernInboxBadge.js');
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	const tavernApi = require('@/common/tavernApi.js');
	const tavernErrors = require('@/common/tavernErrors.js');
	const tavernNoticeState = require('@/common/tavernNoticeState.js');

	function clearSessionLocalArtifacts(session) {
		const clientUid = tavernApi.getClientUid();
		const candidates = [];
		if (session && session.id != null && session.id !== '') {
			candidates.push(session.id);
		}
		if (session && session.characterId != null && session.characterId !== '') {
			candidates.push('char_' + session.characterId);
		}
		candidates.forEach((candidate) => {
			tavernApi.cleanupLocalConversationArtifacts({
				clientUid,
				conversationId: candidate
			});
		});
	}

	export default {
		components: { TavernNavBar },
		data() {
			return {
				sessions: [],
				jgInboxLoading: false,
				jgInboxError: '',
				noticeUnread: 0,
				adUnread: 0,
				adPreview: null,
				unreadIdentitySignature: '',
				noticeUnreadRequestVersion: 0,
				adUnreadRequestVersion: 0,
				moreBusy: false
			};
		},
		computed: {
			t() {
				return this.allText.酒馆页 || {};
			},
			adEntryCover() {
				const url = this.adPreview && this.adPreview.imageUrl;
				if (url) {
					return tavernApi.resolveJgAssetUrl(url) || '/static/chat/c0.png';
				}
				return '/static/chat/c0.png';
			},
			adEntryDesc() {
				if (this.adPreview && this.adPreview.title) {
					return String(this.adPreview.title);
				}
				return this.t.活动推荐副标题 || '官方活动与精选推荐，随时可看';
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
			this.refreshAdUnread();
			this.loadAdPreview();
		},
		onUnload() {
			this.noticeUnreadRequestVersion += 1;
			this.adUnreadRequestVersion += 1;
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
			syncCombinedBadge() {
				tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi, {
					noticeUnread: this.noticeUnread,
					adUnread: this.adUnread
				});
			},
			getUnreadIdentitySignature() {
				try {
					if (typeof tavernApi.getViewerIdentitySignature === 'function') {
						return String(tavernApi.getViewerIdentitySignature() || '');
					}
					return 'client:' + String(tavernApi.getClientUid() || '');
				} catch (e) {
					return 'unknown';
				}
			},
			ensureUnreadIdentity() {
				const currentIdentity = this.getUnreadIdentitySignature();
				if (
					this.unreadIdentitySignature &&
					this.unreadIdentitySignature !== currentIdentity
				) {
					this.noticeUnreadRequestVersion += 1;
					this.adUnreadRequestVersion += 1;
					this.noticeUnread = 0;
					this.adUnread = 0;
					this.syncCombinedBadge();
				}
				this.unreadIdentitySignature = currentIdentity;
				return currentIdentity;
			},
			isUnreadRequestCurrent(part, identitySignature, requestVersion) {
				if (this.getUnreadIdentitySignature() !== identitySignature) return false;
				if (this.unreadIdentitySignature !== identitySignature) return false;
				return part === 'notice'
					? this.noticeUnreadRequestVersion === requestVersion
					: this.adUnreadRequestVersion === requestVersion;
			},
			refreshNoticeUnread() {
				const identitySignature = this.ensureUnreadIdentity();
				const requestVersion = ++this.noticeUnreadRequestVersion;
				if (!tavernApi.jgEnabled()) {
					this.noticeUnread = 0;
					this.syncCombinedBadge();
					return;
				}
				tavernNoticeState
					.fetchUnreadState(tavernApi, 30)
					.then(({ unreadCount }) => {
						if (!this.isUnreadRequestCurrent('notice', identitySignature, requestVersion)) return;
						this.noticeUnread = Number(unreadCount) || 0;
						this.syncCombinedBadge();
					})
					.catch(() => {
						if (!this.isUnreadRequestCurrent('notice', identitySignature, requestVersion)) return;
						this.syncCombinedBadge();
					});
			},
			refreshAdUnread() {
				const identitySignature = this.ensureUnreadIdentity();
				const requestVersion = ++this.adUnreadRequestVersion;
				if (!tavernApi.jgEnabled() || typeof tavernApi.fetchInboxAdsUnread !== 'function') {
					this.adUnread = 0;
					this.syncCombinedBadge();
					return;
				}
				tavernApi
					.fetchInboxAdsUnread(tavernApi.getClientUid())
					.then(({ unreadCount }) => {
						if (!this.isUnreadRequestCurrent('ad', identitySignature, requestVersion)) return;
						this.adUnread = Number(unreadCount) || 0;
						this.syncCombinedBadge();
					})
					.catch(() => {
						if (!this.isUnreadRequestCurrent('ad', identitySignature, requestVersion)) return;
						this.syncCombinedBadge();
					});
			},
			loadAdPreview() {
				if (!tavernApi.jgEnabled() || typeof tavernApi.fetchInboxAds !== 'function') {
					this.adPreview = null;
					return;
				}
				tavernApi
					.fetchInboxAds(1)
					.then((list) => {
						const first = Array.isArray(list) && list.length ? list[0] : null;
						this.adPreview = first
							? {
									id: first.id,
									title: first.title || '',
									imageUrl: first.imageUrl || ''
								}
							: null;
					})
					.catch(() => {
						this.adPreview = null;
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
				const u = s.coverThumbUrl || s.avatarThumbUrl || s.coverUrl || s.avatarUrl;
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
								clearSessionLocalArtifacts(s);
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
			goInboxAds() {
				uni.navigateTo({ url: '/pages/tavern/inboxAds' });
			},
			goHelper() {
				uni.navigateTo({ url: '/pages/user/supportTickets' });
			},
			onMore() {
				if (this.moreBusy) return;
				uni.showActionSheet({
					itemList: [
						this.t.全部标为已读 || '全部标为已读',
						this.t.管理会话 || '管理会话'
					],
					success: (res) => {
						if (res.tapIndex === 0) {
							this.clearAllRead();
						} else if (res.tapIndex === 1) {
							this.goSessionManage();
						}
					}
				});
			},
			clearAllRead() {
				if (this.moreBusy) return;
				if (!tavernApi.jgEnabled()) {
					uni.showToast({
						title: this.t.发现后端未配置 || '后端接口未开启',
						icon: 'none'
					});
					return;
				}
				this.moreBusy = true;
				uni.showLoading({
					title: this.t.处理中 || '处理中…',
					mask: true
				});
				const noticePromise = tavernNoticeState
					.markAllAsRead(tavernApi)
					.then((state) => ({ ok: true, state: state }))
					.catch((error) => ({ ok: false, error: error }));
				const adPromise =
					typeof tavernApi.markInboxAdsReadAll === 'function'
						? tavernApi
								.markInboxAdsReadAll(tavernApi.getClientUid())
								.then((state) => ({ ok: true, state: state }))
								.catch((error) => ({ ok: false, error: error }))
						: Promise.resolve({ ok: true, state: { unreadCount: 0 } });
				Promise.all([noticePromise, adPromise])
					.then(([noticeResult, adResult]) => {
						if (noticeResult.ok) {
							this.noticeUnread = Number(noticeResult.state && noticeResult.state.unreadCount) || 0;
						}
						if (adResult.ok) {
							this.adUnread = Number(adResult.state && adResult.state.unreadCount) || 0;
						}
						this.syncCombinedBadge();
						if (noticeResult.ok && adResult.ok) {
							uni.showToast({
								title: this.t.已全部标为已读 || '已全部标为已读',
								icon: 'success'
							});
							return;
						}
						const firstError = !noticeResult.ok ? noticeResult.error : adResult.error;
						uni.showToast({
							title: tavernErrors.getTavernErrorMessage(
								firstError,
								this.t.标为已读失败 || '操作失败'
							),
							icon: 'none'
						});
					})
					.finally(() => {
						try {
							uni.hideLoading();
						} catch (e) {}
						this.moreBusy = false;
					});
			},
			goSessionManage() {
				uni.navigateTo({ url: '/pages/tavern/sessionManage' });
			}
		}
	};
</script>

<style scoped lang="scss">
	$page-bg: $tavern-surface-dark;
	$card-bg: $tavern-card-dark;
	$text: #244b66;
	$muted: $tavern-muted-on-dark;
	$desc: #687f92;
	$link: #247494;

	.page {
		position: relative;
		height: 100vh;
		display: flex;
		flex-direction: column;
		background: $page-bg;
		padding-bottom: env(safe-area-inset-bottom);
		box-sizing: border-box;
		overflow: hidden;
	}

	.icon-btn {
		min-width: 72rpx;
		min-height: 64rpx;
		padding: 0 8rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		box-sizing: border-box;
		position: relative;
	}

	.nav-actions {
		display: flex;
		align-items: center;
		gap: 4rpx;
	}

	.ad-mark {
		font-size: 34rpx;
		line-height: 1;
		color: #355468;
		font-weight: 500;
		opacity: 0.88;
	}

	.nav-dot {
		position: absolute;
		top: 12rpx;
		right: 10rpx;
		width: 12rpx;
		height: 12rpx;
		border-radius: 50%;
		background: #ef4444;
		border: 2rpx solid rgba(255, 255, 255, 0.92);
	}

	.dot-more {
		font-size: 44rpx;
		color: $text;
		line-height: 1;
		font-weight: bold;
		letter-spacing: 2rpx;
	}

	.card-system--ad {
		opacity: 1;
		border-color: rgba(36, 116, 148, 0.22);
		box-shadow: 0 16rpx 36rpx rgba(36, 116, 148, 0.12);
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

	.section-head {
		padding: 16rpx 28rpx 16rpx;
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
		box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
		border: 1rpx solid rgba(255, 255, 255, 0.9);
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
		background:
			radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
			radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
			linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
		height: 100%;
	}
</style>
