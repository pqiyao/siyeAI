<template>
	<view class="page" :class="localeFontClass">
		<image class="page-glow page-glow--top" src="/static/user/vipbgs.png" mode="widthFix"></image>
		<image class="page-glow page-glow--bottom" src="/static/user/moneybg.png" mode="widthFix"></image>

		<view class="hero-card">
			<image class="hero-bg" src="/static/user/vipbgs.png" mode="aspectFill"></image>
			<image class="hero-side-art" src="/static/user/vipbg.png" mode="aspectFit"></image>
			<view class="hero-mask"></view>
			<view class="hero-topline">
				<text class="hero-kicker">{{ uiText.spaceTitle }}</text>
				<view class="member-chip" @tap="goMyVip">
					<image class="member-chip-icon" src="/static/user/guan.png" mode="aspectFit"></image>
					<text class="member-chip-text">{{ vipLabel }}</text>
				</view>
			</view>

			<view class="profile-row" @tap="onProfileTap">
				<image class="avatar" :src="displayAvatar" mode="aspectFill"></image>
				<view class="profile-copy">
					<view class="name-row">
						<text class="nickname">{{ displayName }}</text>
						<view v-if="telegramReady" class="sync-badge">{{ uiText.statusReady }}</view>
					</view>
					<text class="account-line">{{ accountModeText }}</text>
					<text class="account-id">{{ uiText.uidPrefix }} {{ accountIdText }}</text>
				</view>
				<view class="edit-btn">{{ uiText.editProfile }}</view>
			</view>

			<view class="hero-metrics">
				<view class="metric-pill">
					<text class="metric-value">{{ tavernStats.fav }}</text>
					<text class="metric-label">{{ uiText.favorites }}</text>
				</view>
				<view class="metric-pill">
					<text class="metric-value">{{ tavernStats.chats }}</text>
					<text class="metric-label">{{ uiText.activeChats }}</text>
				</view>
				<view class="metric-pill">
					<text class="metric-value">{{ chatQuotaDisplay }}</text>
					<text class="metric-label">{{ uiText.todayChatQuota }}</text>
				</view>
			</view>
			<view class="hero-status-bar">
				<view class="hero-status-chip">
					<image class="hero-status-icon" src="/static/user/ri.png" mode="aspectFit"></image>
					<text class="hero-status-text">{{ todayChatStatusText }}</text>
				</view>
				<view class="hero-status-chip hero-status-chip--accent">
					<image class="hero-status-icon" src="/static/user/guan.png" mode="aspectFit"></image>
					<text class="hero-status-text">{{ vipLabel }}</text>
				</view>
			</view>
		</view>

		<view class="wallet-card">
			<image class="wallet-bg" src="/static/user/qianbg.png" mode="aspectFill"></image>
			<image class="wallet-art" src="/static/user/qianright.png" mode="aspectFit"></image>
			<view class="wallet-mask"></view>
			<view class="wallet-copy">
				<text class="wallet-kicker">{{ uiText.walletTitle }}</text>
				<text class="wallet-value">{{ scoreDisplay }} {{ uiText.diamondUnit }}</text>
				<text class="wallet-sub">{{ walletSubtitleText }}</text>
			</view>
			<view class="wallet-actions">
				<view class="wallet-soft-btn" @tap="goMyVip">{{ uiText.viewBenefits }}</view>
				<view class="wallet-main-btn" @tap="goMyMoney">{{ uiText.rechargeNow }}</view>
			</view>
		</view>

		<view class="quota-grid">
			<view class="quota-card">
				<image class="quota-card-bg" src="/static/user/zsbg.png" mode="aspectFill"></image>
				<view class="quota-card-mask"></view>
				<text class="quota-card-label">{{ uiText.todayChatCard }}</text>
				<text class="quota-card-value">{{ chatQuotaDisplay }}</text>
				<text class="quota-card-note">{{ uiText.chatQuotaNote }}</text>
			</view>
			<view class="quota-card quota-card--violet">
				<image class="quota-card-bg" src="/static/user/vipbg.png" mode="aspectFill"></image>
				<view class="quota-card-mask"></view>
				<text class="quota-card-label">{{ uiText.todayImageCard }}</text>
				<text class="quota-card-value">{{ imageQuotaDisplay }}</text>
				<text class="quota-card-note">{{ uiText.imageQuotaNote }}</text>
			</view>
		</view>

		<view class="benefit-card" @tap="goMyVip">
			<image class="benefit-bg" src="/static/user/zsbg.png" mode="aspectFill"></image>
			<view class="benefit-mask"></view>
			<image class="benefit-crown" src="/static/user/guan.png" mode="aspectFit"></image>
			<view class="benefit-copy">
				<text class="benefit-title">{{ uiText.benefitsTitle }}</text>
				<text class="benefit-desc">{{ uiText.benefitsDesc }}</text>
			</view>
			<view class="benefit-link">{{ uiText.goView }}</view>
		</view>

		<view class="section-card">
			<image class="section-bg-art section-bg-art--warm" src="/static/user/zsbg.png" mode="aspectFill"></image>
			<view class="section-card-mask"></view>
			<view class="section-head">
				<text class="section-title">{{ uiText.shortcutsTitle }}</text>
				<text class="section-note">{{ uiText.shortcutsNote }}</text>
			</view>
			<view class="shortcut-grid">
				<view
					v-for="item in shortcutList"
					:key="item.key"
					class="shortcut-item"
					@tap="onActionTap(item.key)"
				>
					<view class="shortcut-icon-shell">
						<image class="shortcut-icon" :src="item.iconPath" mode="aspectFit"></image>
					</view>
					<text class="shortcut-name">{{ item.name }}</text>
					<text class="shortcut-desc">{{ item.desc }}</text>
				</view>
			</view>
		</view>

		<view class="section-card">
			<image class="section-bg-art section-bg-art--cool" src="/static/user/moneybg.png" mode="aspectFill"></image>
			<view class="section-card-mask"></view>
			<view class="section-head">
				<text class="section-title">{{ uiText.serviceTitle }}</text>
			</view>
			<view class="service-list">
				<view
					v-for="item in serviceList"
					:key="item.key"
					class="service-item"
					@tap="onActionTap(item.key)"
				>
					<view class="service-left">
						<view class="service-icon-shell">
							<image class="service-icon" :src="item.iconPath" mode="aspectFit"></image>
						</view>
						<view class="service-copy">
							<text class="service-name">{{ item.name }}</text>
							<text class="service-desc">{{ item.desc }}</text>
						</view>
					</view>
					<text class="service-arrow">›</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { applyTavernTabBarLabels, syncTavernTabBar } from '@/common/tavernTabBar.js';

const tavernApi = require('@/common/tavernApi.js');
const { getTavernUiText, formatLocaleText, getLanguageCode } = require('@/common/tavernUiI18n.js');

const SERVICE_SUPPORT_COPY = {
	'zh-cn': {
		supportName: '联系客服',
		supportDesc: '支付、账号和 Bug 问题都在这里',
		ticketsName: '我的工单',
		ticketsDesc: '查看客服回复和处理进度'
	},
	'zh-hk': {
		supportName: '聯絡客服',
		supportDesc: '支付、帳號與 Bug 問題都在這裡',
		ticketsName: '我的工單',
		ticketsDesc: '查看客服回覆與處理進度'
	},
	en: {
		supportName: 'Contact Support',
		supportDesc: 'Payment, account, and bug help',
		ticketsName: 'My Tickets',
		ticketsDesc: 'View replies and ticket progress'
	},
	ko: {
		supportName: '고객센터',
		supportDesc: '결제, 계정, 버그 문제를 여기서 처리합니다',
		ticketsName: '내 티켓',
		ticketsDesc: '답변과 처리 진행 상황 확인'
	},
	ja: {
		supportName: 'サポート',
		supportDesc: '決済、アカウント、Bug 問題はこちらへ',
		ticketsName: 'マイチケット',
		ticketsDesc: '返信と進捗を確認'
	}
};

export default {
	data() {
		return {
			storeProfile: {},
			tavernStats: { fav: 0, chats: 0, chars: 0 },
			storeProfileAccessSignature: ''
		};
	},
	computed: {
		uiText() {
			return getTavernUiText('user');
		},
		hasToken() {
			const u = uni.getStorageSync('user');
			return !!(u && u.token);
		},
		displayAvatar() {
			const avatar = this.storeProfile.avatar;
			if (!avatar) {
				return '/static/logo.png';
			}
			if (String(avatar).indexOf('http') === 0) {
				return avatar;
			}
			if (typeof this.$getimgsrc === 'function') {
				return this.$getimgsrc(avatar);
			}
			return avatar;
		},
		displayName() {
			return this.storeProfile.nickname || this.uiText.guestLabel;
		},
		accountIdText() {
			return this.storeProfile.userId || '--';
		},
		vipLabel() {
			return this.storeProfile.vipName || this.uiText.guestLabel;
		},
		accountModeText() {
			return this.telegramReady ? this.uiText.accountModeReady : this.uiText.accountModeBridge;
		},
		todayChatStatusText() {
			return formatLocaleText(this.uiText.todayChatStatus, { count: this.chatQuotaDisplay });
		},
		scoreDisplay() {
			return Number(this.storeProfile.score || 0);
		},
		goldDisplay() {
			return Number(this.storeProfile.goldCoin || 0);
		},
		chatQuotaDisplay() {
			return Number(this.storeProfile.dailyChatQuota || 0);
		},
		imageQuotaDisplay() {
			return Number(this.storeProfile.dailyImageQuota || 0);
		},
		walletSubtitleText() {
			return formatLocaleText(this.uiText.walletSubtitle, { gold: this.goldDisplay });
		},
		shortcutList() {
			return [
				{
					key: 'favorites',
					name: this.uiText.shortcutFavoritesName,
					desc: this.uiText.shortcutFavoritesDesc,
					iconPath: '/static/user/u4.png'
				},
				{
					key: 'tavern',
					name: this.uiText.shortcutTavernName,
					desc: this.uiText.shortcutTavernDesc,
					iconPath: '/static/user/u0.png'
				},
				{
					key: 'wallet',
					name: this.uiText.shortcutWalletName,
					desc: this.uiText.shortcutWalletDesc,
					iconPath: '/static/user/qian.png'
				},
				{
					key: 'vip',
					name: this.uiText.shortcutVipName,
					desc: this.uiText.shortcutVipDesc,
					iconPath: '/static/user/guan.png'
				}
			];
		},
		serviceList() {
			getTavernUiText('language');
			const serviceCopy = SERVICE_SUPPORT_COPY[getLanguageCode()] || SERVICE_SUPPORT_COPY.en;
			return [
				{
					key: 'profile',
					name: this.uiText.serviceProfileName,
					desc: this.uiText.serviceProfileDesc,
					iconPath: '/static/user/v0.png'
				},
				{
					key: 'support',
					name: serviceCopy.supportName,
					desc: serviceCopy.supportDesc,
					iconPath: '/static/user/v5.png'
				},
				{
					key: 'tickets',
					name: serviceCopy.ticketsName,
					desc: serviceCopy.ticketsDesc,
					iconPath: '/static/user/v10.png'
				},
				{
					key: 'language',
					name: this.uiText.serviceLanguageName,
					desc: this.uiText.serviceLanguageDesc,
					iconPath: '/static/user/v5.png'
				},
				{
					key: 'settings',
					name: this.uiText.serviceSettingsName,
					desc: this.uiText.serviceSettingsDesc,
					iconPath: '/static/user/v10.png'
				}
			];
		},
		telegramReady() {
			return !!this.storeProfile.telegramReady;
		}
	},
	onShow() {
		applyTavernTabBarLabels(this.allText, this);
		syncTavernTabBar(this, 'pages/user/user', this.allText);
		this.loadPage();
	},
	methods: {
		loadPage() {
			const clientUid = tavernApi.getClientUid();
			tavernApi
				.fetchStoreOverview(clientUid)
				.then((res) => {
					const nextProfile = (res && res.profile) || {};
					const nextAccessSignature = tavernApi.getProfileAccessSignature(nextProfile);
					if (this.storeProfileAccessSignature && this.storeProfileAccessSignature !== nextAccessSignature) {
						tavernApi.markCharacterAccessRefreshNeeded('user-center');
					}
					this.storeProfileAccessSignature = nextAccessSignature;
					this.storeProfile = nextProfile;
				})
				.catch((e) => {
					this.storeProfile = {};
					console.warn('store overview failed:', e && e.message ? e.message : e);
				});
			tavernApi
				.fetchMeStats(clientUid)
				.then((s) => {
					if (s && typeof s === 'object') {
						this.tavernStats = {
							fav: Number(s.fav) || 0,
							chats: Number(s.chats) || 0,
							chars: Number(s.chars) || 0
						};
					}
				})
				.catch((e) => {
					this.tavernStats = { fav: 0, chats: 0, chars: 0 };
					console.warn('tavern stats failed:', e && e.message ? e.message : e);
				});
		},
		onProfileTap() {
			if (this.hasToken) {
				this.util.urlTo('/pages/user/editziliao');
				return;
			}
			uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/user/user') });
		},
		goMyFavorites() {
			this.util.urlTo('/pages/user/myfavorites');
		},
		goMyTavernChars() {
			uni.switchTab({ url: '/pages/tavern/tavern' });
		},
		goMyMoney() {
			this.util.urlTo('/pages/user/mymoney');
		},
		goMyVip() {
			this.util.urlTo('/pages/user/myvip');
		},
		goSettings() {
			this.util.urlTo('/pages/user/set');
		},
		onActionTap(key) {
			if (key === 'profile') {
				this.onProfileTap();
			} else if (key === 'favorites') {
				this.goMyFavorites();
			} else if (key === 'tavern') {
				this.goMyTavernChars();
			} else if (key === 'wallet') {
				this.goMyMoney();
			} else if (key === 'vip') {
				this.goMyVip();
			} else if (key === 'language') {
				this.util.urlTo('/pages/user/language');
			} else if (key === 'support') {
				this.util.urlTo('/pages/user/supportCreate');
			} else if (key === 'tickets') {
				this.util.urlTo('/pages/user/supportTickets');
			} else if (key === 'settings') {
				this.goSettings();
			}
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	min-height: 100vh;
	padding: 112rpx 24rpx calc(42rpx + #{$tavern-tabbar-spacer} + env(safe-area-inset-bottom));
	box-sizing: border-box;
	background:
		radial-gradient(circle at top left, rgba(255, 255, 255, 0.06), transparent 24%),
		$tavern-page-bg-top;
	overflow: hidden;
}

.page-glow {
	position: absolute;
	pointer-events: none;
	opacity: 0.95;
}

.page-glow--top {
	top: -40rpx;
	left: -60rpx;
	width: 840rpx;
}

.page-glow--bottom {
	right: -120rpx;
	bottom: 140rpx;
	width: 620rpx;
	opacity: 0.3;
}

.hero-card,
.wallet-card,
.benefit-card,
.section-card,
.quota-card {
	position: relative;
	overflow: hidden;
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow:
		0 18rpx 44rpx rgba(4, 8, 18, 0.34),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.04);
}

.hero-card {
	padding: 28rpx;
	border-radius: 34rpx;
	background: rgba(19, 23, 36, 0.92);
}

.hero-bg,
.hero-side-art,
.wallet-bg,
.benefit-bg,
.quota-card-bg {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

.hero-side-art {
	inset: auto;
	right: -24rpx;
	bottom: -18rpx;
	width: 280rpx;
	height: 240rpx;
	z-index: 1;
	opacity: 0.62;
}

.hero-mask,
.wallet-mask,
.benefit-mask,
.quota-card-mask {
	position: absolute;
	inset: 0;
}

.hero-mask {
	background:
		linear-gradient(135deg, rgba(18, 22, 35, 0.78) 0%, rgba(41, 18, 54, 0.7) 100%),
		radial-gradient(circle at top right, rgba(255, 255, 255, 0.16), transparent 30%);
}

.hero-topline,
.profile-row,
.hero-metrics,
.wallet-copy,
.wallet-actions,
.benefit-copy,
.benefit-link,
.section-head,
.shortcut-grid,
.service-list {
	position: relative;
	z-index: 2;
}

.hero-topline {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.hero-kicker {
	font-size: 24rpx;
	font-weight: 700;
	letter-spacing: 2rpx;
	color: rgba(255, 255, 255, 0.74);
}

.member-chip {
	display: inline-flex;
	align-items: center;
	gap: 10rpx;
	padding: 10rpx 18rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.14);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	backdrop-filter: blur(10px);
	-webkit-backdrop-filter: blur(10px);
}

.member-chip-icon {
	width: 28rpx;
	height: 28rpx;
}

.member-chip-text {
	font-size: 22rpx;
	font-weight: 700;
	color: #fff;
}

.profile-row {
	display: flex;
	align-items: center;
	margin-top: 28rpx;
}

.avatar {
	width: 144rpx;
	height: 144rpx;
	border-radius: 50%;
	border: 4rpx solid rgba(255, 255, 255, 0.16);
	flex-shrink: 0;
	background: rgba(255, 255, 255, 0.08);
	box-shadow:
		0 16rpx 30rpx rgba(5, 8, 18, 0.26),
		0 0 0 10rpx rgba(255, 255, 255, 0.04);
}

.profile-copy {
	flex: 1;
	min-width: 0;
	margin-left: 20rpx;
}

.name-row {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12rpx;
}

.nickname {
	font-size: 40rpx;
	font-weight: 800;
	color: #fff;
}

.sync-badge {
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 700;
	color: #e9d5ff;
	background: rgba(124, 58, 237, 0.18);
	border: 1rpx solid rgba(196, 181, 253, 0.24);
}

.account-line,
.account-id {
	display: block;
	margin-top: 10rpx;
	font-size: 24rpx;
	line-height: 1.55;
	color: rgba(241, 245, 249, 0.8);
}

.edit-btn {
	flex-shrink: 0;
	min-width: 148rpx;
	height: 76rpx;
	padding: 0 20rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, rgba(124, 58, 237, 0.28) 0%, rgba(236, 72, 153, 0.2) 100%);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	box-shadow: 0 12rpx 24rpx rgba(76, 29, 149, 0.16);
}

.hero-metrics {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 14rpx;
	margin-top: 24rpx;
}

.metric-pill {
	padding: 18rpx 16rpx;
	border-radius: 22rpx;
	background: rgba(9, 13, 21, 0.32);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	backdrop-filter: blur(8px);
	-webkit-backdrop-filter: blur(8px);
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.05);
}

.metric-value {
	display: block;
	font-size: 30rpx;
	font-weight: 800;
	color: #fff;
}

.metric-label {
	display: block;
	margin-top: 8rpx;
	font-size: 21rpx;
	line-height: 1.45;
	color: rgba(241, 245, 249, 0.72);
}

.hero-status-bar {
	position: relative;
	z-index: 2;
	display: flex;
	gap: 14rpx;
	margin-top: 18rpx;
}

.hero-status-chip {
	flex: 1;
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 16rpx 18rpx;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.08);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	backdrop-filter: blur(10px);
	-webkit-backdrop-filter: blur(10px);
}

.hero-status-chip--accent {
	background: rgba(168, 85, 247, 0.16);
	border-color: rgba(216, 180, 254, 0.2);
}

.hero-status-icon {
	width: 34rpx;
	height: 34rpx;
	flex-shrink: 0;
}

.hero-status-text {
	font-size: 22rpx;
	font-weight: 700;
	color: #f8fafc;
	line-height: 1.45;
}

.wallet-card {
	margin-top: 20rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	min-height: 260rpx;
	background: rgba(24, 24, 30, 0.88);
	box-shadow:
		0 22rpx 48rpx rgba(6, 10, 20, 0.28),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.05);
}

.wallet-mask {
	background:
		linear-gradient(135deg, rgba(23, 28, 43, 0.82) 0%, rgba(46, 19, 37, 0.62) 100%),
		radial-gradient(circle at top left, rgba(255, 255, 255, 0.12), transparent 24%);
}

.wallet-art {
	position: absolute;
	right: -8rpx;
	bottom: 12rpx;
	width: 280rpx;
	height: 180rpx;
	z-index: 1;
	opacity: 0.92;
}

.wallet-copy {
	max-width: 390rpx;
}

.wallet-kicker {
	display: block;
	font-size: 24rpx;
	font-weight: 700;
	color: rgba(255, 255, 255, 0.72);
}

.wallet-value {
	display: block;
	margin-top: 16rpx;
	font-size: 46rpx;
	font-weight: 800;
	line-height: 1.08;
	color: #fff;
}

.wallet-sub {
	display: block;
	margin-top: 12rpx;
	font-size: 23rpx;
	line-height: 1.6;
	color: rgba(241, 245, 249, 0.82);
}

.wallet-actions {
	display: flex;
	gap: 14rpx;
	margin-top: 28rpx;
}

.wallet-soft-btn,
.wallet-main-btn {
	height: 78rpx;
	padding: 0 28rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
}

.wallet-soft-btn {
	color: #f5d0fe;
	background: rgba(124, 58, 237, 0.16);
	border: 1rpx solid rgba(216, 180, 254, 0.26);
}

.wallet-main-btn {
	color: #fff;
	background: linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%);
	box-shadow: 0 14rpx 28rpx rgba(219, 39, 119, 0.22);
}

.quota-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 16rpx;
	margin-top: 20rpx;
}

.quota-card {
	padding: 24rpx;
	border-radius: 30rpx;
	background: rgba(23, 27, 38, 0.92);
	min-height: 208rpx;
}

.quota-card--violet .quota-card-mask {
	background:
		linear-gradient(135deg, rgba(19, 22, 35, 0.8) 0%, rgba(58, 20, 73, 0.66) 100%);
}

.quota-card-mask {
	background:
		linear-gradient(135deg, rgba(13, 18, 31, 0.76) 0%, rgba(26, 27, 48, 0.58) 100%);
}

.quota-card-label,
.quota-card-value,
.quota-card-note {
	position: relative;
	z-index: 1;
	display: block;
}

.quota-card-label {
	font-size: 23rpx;
	font-weight: 700;
	color: rgba(255, 255, 255, 0.72);
}

.quota-card-value {
	margin-top: 16rpx;
	font-size: 42rpx;
	font-weight: 800;
	color: #fff;
}

.quota-card-note {
	margin-top: 12rpx;
	font-size: 22rpx;
	line-height: 1.55;
	color: rgba(226, 232, 240, 0.76);
}

.benefit-card {
	margin-top: 20rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	min-height: 208rpx;
	background: rgba(24, 28, 39, 0.92);
}

.benefit-mask {
	background:
		linear-gradient(135deg, rgba(18, 23, 36, 0.78) 0%, rgba(45, 25, 52, 0.58) 100%);
}

.benefit-crown {
	position: absolute;
	right: 26rpx;
	top: 24rpx;
	width: 92rpx;
	height: 92rpx;
	z-index: 2;
}

.benefit-copy {
	position: relative;
	z-index: 2;
	max-width: 470rpx;
}

.benefit-title {
	display: block;
	font-size: 32rpx;
	font-weight: 800;
	color: #fff;
}

.benefit-desc {
	display: block;
	margin-top: 14rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: rgba(241, 245, 249, 0.84);
}

.benefit-link {
	position: relative;
	z-index: 2;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	margin-top: 22rpx;
	min-width: 180rpx;
	height: 76rpx;
	padding: 0 24rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.14);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
}

.section-card {
	margin-top: 20rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	background: rgba(18, 21, 32, 0.9);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
}

.section-bg-art {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
	opacity: 0.18;
}

.section-bg-art--warm {
	opacity: 0.16;
	transform: scale(1.08);
}

.section-bg-art--cool {
	opacity: 0.12;
	transform: scale(1.04);
}

.section-card-mask {
	position: absolute;
	inset: 0;
	background:
		linear-gradient(135deg, rgba(15, 18, 29, 0.9) 0%, rgba(18, 21, 32, 0.82) 46%, rgba(34, 17, 44, 0.78) 100%),
		radial-gradient(circle at top right, rgba(255, 255, 255, 0.08), transparent 26%);
}

.section-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 14rpx;
}

.section-title {
	font-size: 30rpx;
	font-weight: 800;
	color: #f8fafc;
}

.section-note {
	max-width: 320rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: #8f97ae;
	text-align: right;
}

.shortcut-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 16rpx;
	margin-top: 22rpx;
}

.shortcut-item {
	padding: 24rpx 22rpx;
	border-radius: 24rpx;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.07) 0%, rgba(255, 255, 255, 0.03) 100%),
		rgba(255, 255, 255, 0.04);
	border: 1rpx solid rgba(255, 255, 255, 0.1);
	box-shadow:
		inset 0 1rpx 0 rgba(255, 255, 255, 0.07),
		0 14rpx 28rpx rgba(6, 10, 20, 0.14);
	backdrop-filter: blur(8px);
	-webkit-backdrop-filter: blur(8px);
}

.shortcut-icon-shell {
	width: 86rpx;
	height: 86rpx;
	border-radius: 26rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0.11) 100%),
		rgba(17, 21, 32, 0.52);
	border: 1rpx solid rgba(255, 255, 255, 0.18);
	box-shadow:
		inset 0 1rpx 0 rgba(255, 255, 255, 0.12),
		0 14rpx 26rpx rgba(4, 8, 18, 0.18);
}

.shortcut-icon {
	width: 54rpx;
	height: 54rpx;
	opacity: 0.98;
	filter: brightness(1.72) contrast(1.18) saturate(1.18) drop-shadow(0 6rpx 12rpx rgba(236, 72, 153, 0.14));
}

.shortcut-name {
	display: block;
	margin-top: 16rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #f8fafc;
}

.shortcut-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.55;
	color: #8f97ae;
}

.service-list {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
	margin-top: 22rpx;
}

.service-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	padding: 20rpx;
	border-radius: 24rpx;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.07) 0%, rgba(255, 255, 255, 0.03) 100%),
		rgba(255, 255, 255, 0.04);
	border: 1rpx solid rgba(255, 255, 255, 0.1);
	box-shadow:
		inset 0 1rpx 0 rgba(255, 255, 255, 0.07),
		0 14rpx 28rpx rgba(6, 10, 20, 0.14);
	backdrop-filter: blur(8px);
	-webkit-backdrop-filter: blur(8px);
}

.service-left {
	display: flex;
	align-items: center;
	gap: 16rpx;
	flex: 1;
	min-width: 0;
}

.service-icon-shell {
	width: 82rpx;
	height: 82rpx;
	border-radius: 24rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0.11) 100%),
		rgba(18, 22, 34, 0.5);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	box-shadow:
		inset 0 1rpx 0 rgba(255, 255, 255, 0.1),
		0 14rpx 26rpx rgba(6, 10, 20, 0.16);
	flex-shrink: 0;
}

.service-icon {
	width: 50rpx;
	height: 50rpx;
	flex-shrink: 0;
	opacity: 0.98;
	filter: brightness(1.72) contrast(1.18) saturate(1.14) drop-shadow(0 6rpx 12rpx rgba(168, 85, 247, 0.16));
}

.service-copy {
	flex: 1;
	min-width: 0;
}

.service-name {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: #f8fafc;
}

.service-desc {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	line-height: 1.55;
	color: #8f97ae;
}

.service-arrow {
	flex-shrink: 0;
	min-width: 56rpx;
	height: 56rpx;
	border-radius: 18rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	line-height: 1;
	color: #d8b4fe;
	background: rgba(168, 85, 247, 0.14);
	border: 1rpx solid rgba(192, 132, 252, 0.18);
}
</style>

<style lang="scss">
page {
	background: $tavern-page-bg-top;
}
</style>
