<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>

		<view class="hero-card">
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
					<text class="metric-label">{{ todayChatQuotaTotalLabel }}</text>
				</view>
			</view>
			<view class="hero-status-bar">
				<view class="hero-status-chip">
					<image class="hero-status-icon" src="/static/user/ri.png" mode="aspectFit"></image>
					<text class="hero-status-text">{{ todayChatUsedStatusText }}</text>
				</view>
				<view class="hero-status-chip hero-status-chip--accent">
					<image class="hero-status-icon" src="/static/user/guan.png" mode="aspectFit"></image>
					<text class="hero-status-text">{{ vipLabel }}</text>
				</view>
			</view>
		</view>

		<view class="wallet-card">
			<image class="wallet-bg-art" src="/static/user/zsbg.png" mode="aspectFill"></image>
			<image class="wallet-right-art" src="/static/user/qianright.png" mode="aspectFit"></image>
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
				<text class="quota-card-label">{{ todayChatCardLabel }}</text>
				<text class="quota-card-value">{{ todayChatUsedDisplay }}</text>
				<text class="quota-card-note">{{ todayChatUsageNoteText }}</text>
			</view>
			<view class="quota-card quota-card--violet">
				<text class="quota-card-label">{{ uiText.todayImageCard }}</text>
				<text class="quota-card-value">{{ imageQuotaDisplay }}</text>
				<text class="quota-card-note">{{ uiText.imageQuotaNote }}</text>
			</view>
		</view>

		<view class="benefit-card" @tap="goMyVip">
			<image class="benefit-bg-art" src="/static/user/vipbgs.png" mode="aspectFill"></image>
			<view class="benefit-copy">
				<text class="benefit-title">{{ uiText.benefitsTitle }}</text>
				<text class="benefit-desc">{{ uiText.benefitsDesc }}</text>
			</view>
			<view class="benefit-link">{{ uiText.goView }}</view>
		</view>

		<view class="illustration-site-card" @tap="openIllustrationSite">
			<view class="illustration-site-glow"></view>
			<view class="illustration-site-icon">
				<text class="illustration-site-mark">✦</text>
			</view>
			<view class="illustration-site-copy">
				<text class="illustration-site-kicker">相关站点</text>
				<text class="illustration-site-title">四叶插画分享</text>
				<text class="illustration-site-desc">上传作品、浏览画廊、查看插画灵感</text>
			</view>
			<view class="illustration-site-action">进入</view>
		</view>

		<view class="section-card">
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
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="104" />
		<!-- #endif -->
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

const AI_SETTINGS_COPY = {
	'zh-cn': {
		name: '模型配置',
		desc: '选择官方 API，或使用自己的 API Key'
	},
	'zh-hk': {
		name: '模型配置',
		desc: '選擇官方 API，或使用自己的 API Key'
	},
	en: {
		name: 'Model Config',
		desc: 'Use official API or your own API key'
	},
	ko: {
		name: '모델 설정',
		desc: '공식 API 또는 내 API Key 사용'
	},
	ja: {
		name: 'モデル設定',
		desc: '公式 API または自分の API Key を使用'
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
		todayChatCounterLabel() {
			const map = {
				'zh-cn': '今日已聊天次数',
				'zh-hk': '今日已聊天次數',
				en: 'Today Chats',
				ko: '오늘 채팅 횟수',
				ja: '本日のチャット回数'
			};
			return this.uiText.todayChatCount || map[getLanguageCode()] || map['zh-cn'];
		},
		todayChatQuotaTotalLabel() {
			const map = {
				'zh-cn': '\u4eca\u65e5\u603b\u53ef\u804a\u5929\u6b21\u6570',
				'zh-hk': '\u4eca\u65e5\u7e3d\u53ef\u804a\u5929\u6b21\u6578',
				en: 'Today Chat Quota',
				ko: '\uc624\ub298 \ucd1d \ucc44\ud305 \uac00\ub2a5 \ud69f\uc218',
				ja: '\u672c\u65e5\u306e\u5408\u8a08\u30c1\u30e3\u30c3\u30c8\u56de\u6570'
			};
			return this.uiText.todayChatQuotaTotal || map[getLanguageCode()] || map['zh-cn'];
		},
		todayChatCardLabel() {
			const map = {
				'zh-cn': '今日已聊天次数统计',
				'zh-hk': '今日已聊天次數統計',
				en: 'Today Chat Count',
				ko: '오늘 채팅 통계',
				ja: '本日のチャット統計'
			};
			return this.uiText.todayChatCountCard || map[getLanguageCode()] || map['zh-cn'];
		},
		todayChatUsageNoteText() {
			const map = {
				'zh-cn': '今日聊天使用记录',
				'zh-hk': '今日聊天使用紀錄',
				en: 'Today chat usage',
				ko: '오늘 채팅 사용량',
				ja: '本日のチャット使用状況'
			};
			return this.uiText.chatQuotaNote || this.uiText.todayChatCountNote || map[getLanguageCode()] || map['zh-cn'];
		},
		todayChatUsedStatusText() {
			const map = {
				'zh-cn': '\u4eca\u65e5\u5df2\u804a\u5929 {used} / {total} \u6b21',
				'zh-hk': '\u4eca\u65e5\u5df2\u804a\u5929 {used} / {total} \u6b21',
				en: 'Today chats used: {used} / {total}',
				ko: '\uc624\ub298 \ucc44\ud305 \uc0ac\uc6a9: {used} / {total}',
				ja: '\u672c\u65e5\u306e\u30c1\u30e3\u30c3\u30c8\u4f7f\u7528: {used} / {total}'
			};
			const statusTpl =
				this.uiText.todayChatCountStatusWithTotal ||
				this.uiText.todayChatStatusWithTotal ||
				map[getLanguageCode()] ||
				map['zh-cn'];
			return formatLocaleText(statusTpl, {
				used: this.todayChatUsedDisplay,
				total: this.chatQuotaDisplay,
				count: this.todayChatUsedDisplay
			});
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
		todayChatUsedDisplay() {
			return Number(this.storeProfile.dailyChatUsed || 0);
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
					key: 'live2dCompanion',
					name: 'AI看板娘',
					desc: '悬浮聊天助手',
					iconPath: '/static/live2d/models/ug/icon.png'
				},
				{
					key: 'games',
					name: '小游戏',
					desc: 'HTML 轻量娱乐',
					iconPath: '/static/user/u6.png'
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
			const aiCopy = AI_SETTINGS_COPY[getLanguageCode()] || AI_SETTINGS_COPY.en;
			return [
				{
					key: 'aiSettings',
					name: aiCopy.name,
					desc: aiCopy.desc,
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
		openIllustrationSite() {
			const target = '/illustration/';
			// #ifdef H5
			window.location.href = '/illustration/';
			return;
			// #endif
			// #ifndef H5
			uni.setClipboardData({
				data: target,
				success: () => uni.showToast({ title: '插画站链接已复制', icon: 'none' })
			});
			// #endif
		},
		onActionTap(key) {
			if (key === 'aiSettings') {
				this.util.urlTo('/pages/user/aiSettings');
			} else if (key === 'profile') {
				this.onProfileTap();
			} else if (key === 'favorites') {
				this.goMyFavorites();
			} else if (key === 'tavern') {
				this.goMyTavernChars();
			} else if (key === 'live2dCompanion') {
				this.util.urlTo('/pages/user/live2dSetting');
			} else if (key === 'games') {
				this.util.urlTo('/pages/user/gameCenter');
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
	width: 100%;
	min-height: 100vh;
	padding: 112rpx 24rpx calc(42rpx + #{$tavern-tabbar-spacer} + env(safe-area-inset-bottom));
	box-sizing: border-box;
	background: transparent;
	overflow: hidden;
	overflow-x: hidden;
}

/* #ifdef H5 */
.page {
	min-height: 100dvh;
}
/* #endif */

.hero-card,
.wallet-card,
.benefit-card,
.section-card,
.quota-card,
.illustration-site-card {
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
	min-width: 0;
}

.nickname {
	display: block;
	max-width: 100%;
	font-size: 40rpx;
	font-weight: 800;
	color: #fff;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
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

.illustration-site-card {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 20rpx;
	padding: 24rpx;
	border-radius: 30rpx;
	background:
		radial-gradient(circle at 12% 0, rgba(120, 214, 218, 0.28), transparent 36%),
		linear-gradient(135deg, rgba(20, 29, 43, 0.94) 0%, rgba(30, 38, 55, 0.9) 54%, rgba(52, 42, 65, 0.92) 100%);
}

.illustration-site-glow {
	position: absolute;
	right: -52rpx;
	top: -76rpx;
	width: 230rpx;
	height: 230rpx;
	border-radius: 50%;
	background: radial-gradient(circle, rgba(255, 152, 198, 0.3), transparent 68%);
}

.illustration-site-icon {
	position: relative;
	z-index: 2;
	display: flex;
	align-items: center;
	justify-content: center;
	width: 86rpx;
	height: 86rpx;
	border-radius: 26rpx;
	background: rgba(255, 255, 255, 0.12);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	flex-shrink: 0;
}

.illustration-site-mark {
	font-size: 38rpx;
	font-weight: 900;
	color: #9deaf0;
}

.illustration-site-copy {
	position: relative;
	z-index: 2;
	flex: 1;
	min-width: 0;
}

.illustration-site-kicker,
.illustration-site-title,
.illustration-site-desc {
	display: block;
}

.illustration-site-kicker {
	font-size: 22rpx;
	font-weight: 700;
	color: rgba(157, 234, 240, 0.9);
}

.illustration-site-title {
	margin-top: 6rpx;
	font-size: 31rpx;
	line-height: 1.25;
	font-weight: 900;
	color: #fff;
}

.illustration-site-desc {
	margin-top: 8rpx;
	font-size: 23rpx;
	line-height: 1.5;
	color: rgba(226, 232, 240, 0.78);
}

.illustration-site-action {
	position: relative;
	z-index: 2;
	display: flex;
	align-items: center;
	justify-content: center;
	width: 86rpx;
	height: 60rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.16);
	border: 1rpx solid rgba(255, 255, 255, 0.18);
	font-size: 24rpx;
	font-weight: 800;
	color: #fff;
	flex-shrink: 0;
}

.section-card {
	margin-top: 20rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	background: rgba(18, 21, 32, 0.9);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
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
	filter: none;
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
	filter: none;
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

/* Final user page skin: calm glass cards over the shared image background. */
.page {
	background: transparent;
	color: #203846;
	padding: 104rpx 24rpx calc(42rpx + #{$tavern-tabbar-spacer} + env(safe-area-inset-bottom));
}

.hero-card,
.wallet-card,
.benefit-card,
.section-card,
.quota-card,
.illustration-site-card {
	position: relative;
	overflow: visible;
	border: 1rpx solid rgba(255, 255, 255, 0.38);
	background: rgba(255, 255, 255, 0.36);
	box-shadow: 0 14rpx 32rpx rgba(36, 70, 88, 0.1);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.hero-card,
.wallet-card {
	padding: 30rpx;
	border-radius: 30rpx;
	background: rgba(255, 255, 255, 0.38);
}

.hero-card::before,
.wallet-card::before {
	display: none;
}

.hero-topline,
.profile-row,
.hero-metrics,
.hero-status-bar,
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

.hero-kicker,
.wallet-kicker,
.quota-card-label {
	color: #236f82;
}

.member-chip-text,
.nickname,
.metric-value,
.hero-status-text,
.wallet-value,
.quota-card-value,
.benefit-title,
.section-title,
.shortcut-name,
.service-name {
	color: #203846;
}

.account-line,
.account-id,
.metric-label,
.wallet-sub,
.quota-card-note,
.benefit-desc,
.section-note,
.shortcut-desc,
.service-desc {
	color: #5f7280;
	font-weight: 500;
}

.hero-kicker {
	letter-spacing: 0;
}

.member-chip,
.hero-status-chip,
.metric-pill {
	background: rgba(255, 255, 255, 0.31);
	border: 1rpx solid rgba(79, 147, 163, 0.13);
	box-shadow: 0 8rpx 18rpx rgba(36, 70, 88, 0.08);
}

.member-chip {
	background: rgba(63, 143, 159, 0.1);
}

.member-chip-icon,
.hero-status-icon {
	opacity: 1;
	filter: none;
}

.profile-row {
	align-items: center;
	gap: 18rpx;
}

.profile-copy {
	margin-left: 0;
}

.nickname {
	font-size: 38rpx;
	line-height: 1.2;
	letter-spacing: 0;
}

.sync-badge {
	color: #236f82;
	background: rgba(63, 143, 159, 0.11);
	border-color: rgba(63, 143, 159, 0.16);
}

.avatar {
	width: 140rpx;
	height: 140rpx;
	border: 5rpx solid rgba(255, 255, 255, 0.92);
	background: rgba(255, 255, 255, 0.72);
	box-shadow: 0 14rpx 30rpx rgba(36, 70, 88, 0.14);
}

.edit-btn,
.wallet-main-btn,
.benefit-link {
	color: #fff;
	background: linear-gradient(135deg, #2f8796 0%, #54b5c0 100%);
	border: 1rpx solid rgba(255, 255, 255, 0.55);
	box-shadow:
		0 14rpx 28rpx rgba(47, 135, 150, 0.2),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.28);
}

.edit-btn {
	min-width: 132rpx;
	height: 72rpx;
}

.hero-metrics {
	gap: 14rpx;
}

.metric-pill {
	min-height: 88rpx;
	border-radius: 22rpx;
}

.hero-status-chip {
	background: rgba(247, 252, 252, 0.34);
}

.hero-status-chip--accent {
	background: rgba(63, 143, 159, 0.11);
	border-color: rgba(63, 143, 159, 0.15);
}

.wallet-card {
	min-height: auto;
	overflow: hidden;
}

.wallet-bg-art,
.wallet-right-art,
.benefit-bg-art {
	position: absolute;
	z-index: 1;
	pointer-events: none;
}

.wallet-bg-art {
	right: -38rpx;
	top: 14rpx;
	width: 430rpx;
	height: 140rpx;
	opacity: 0.18;
}

.wallet-right-art {
	right: 22rpx;
	bottom: 20rpx;
	width: 170rpx;
	height: 86rpx;
	opacity: 0.26;
}

.benefit-bg-art {
	right: -120rpx;
	bottom: -220rpx;
	width: 360rpx;
	height: 430rpx;
	opacity: 0.14;
}

.wallet-copy {
	max-width: none;
	padding-right: 190rpx;
	box-sizing: border-box;
}

.wallet-value {
	font-size: 48rpx;
	text-shadow: none;
}

.wallet-actions {
	gap: 14rpx;
	margin-top: 26rpx;
	justify-content: flex-start;
	padding-right: 190rpx;
	box-sizing: border-box;
}

.wallet-soft-btn,
.wallet-main-btn {
	flex: 0 0 auto;
	height: 78rpx;
	padding: 0 28rpx;
	box-sizing: border-box;
}

.wallet-soft-btn {
	color: #236f82;
	background: rgba(63, 143, 159, 0.1);
	border: 1rpx solid rgba(63, 143, 159, 0.16);
	box-shadow: none;
}

.quota-grid {
	gap: 16rpx;
}

.quota-card,
.benefit-card,
.section-card,
.illustration-site-card {
	background: rgba(255, 255, 255, 0.34);
}

.quota-card {
	padding: 24rpx;
	border-radius: 24rpx;
	min-height: 188rpx;
}

.quota-card--violet {
	background: rgba(255, 255, 255, 0.34);
	border-color: rgba(79, 147, 163, 0.13);
}

.benefit-card {
	padding: 28rpx;
	border-radius: 28rpx;
	min-height: auto;
	overflow: hidden;
}

.illustration-site-card {
	overflow: hidden;
	padding: 24rpx;
	border-radius: 28rpx;
	background:
		radial-gradient(circle at 92% 12%, rgba(248, 185, 213, 0.44), transparent 34%),
		linear-gradient(135deg, rgba(255, 255, 255, 0.76) 0%, rgba(230, 249, 252, 0.7) 50%, rgba(255, 245, 250, 0.78) 100%);
}

.illustration-site-glow {
	background: radial-gradient(circle, rgba(63, 143, 159, 0.22), transparent 68%);
}

.illustration-site-icon {
	background: rgba(63, 143, 159, 0.12);
	border-color: rgba(63, 143, 159, 0.16);
}

.illustration-site-mark {
	color: #247494;
}

.illustration-site-kicker {
	color: #247494;
}

.illustration-site-title {
	color: #244b66;
}

.illustration-site-desc {
	color: #64798b;
}

.illustration-site-action {
	background: #236f82;
	border-color: rgba(35, 111, 130, 0.12);
	color: #fff;
	box-shadow: 0 12rpx 24rpx rgba(35, 111, 130, 0.18);
}

.benefit-link {
	min-width: 168rpx;
	height: 72rpx;
}

.section-card {
	padding: 26rpx 24rpx;
	border-radius: 28rpx;
}

.section-head {
	align-items: flex-start;
}

.section-title {
	font-size: 30rpx;
}

.section-note {
	max-width: 330rpx;
	text-align: right;
}

.shortcut-grid {
	gap: 16rpx;
}

.shortcut-item,
.service-item {
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.32);
	border: 1rpx solid rgba(255, 255, 255, 0.36);
	box-shadow: 0 10rpx 22rpx rgba(36, 70, 88, 0.08);
	backdrop-filter: none;
	-webkit-backdrop-filter: none;
}

.shortcut-item {
	min-height: 212rpx;
	padding: 24rpx 18rpx;
	box-sizing: border-box;
}

.service-item {
	padding: 22rpx 18rpx;
}

.shortcut-icon-shell,
.service-icon-shell {
	background: rgba(255, 255, 255, 0.38);
	border: 1rpx solid rgba(79, 147, 163, 0.16);
	box-shadow: 0 8rpx 18rpx rgba(36, 70, 88, 0.09);
}

.shortcut-icon,
.service-icon {
	opacity: 1;
	filter: none;
}

.shortcut-name,
.service-name {
	font-weight: 800;
}

.shortcut-desc,
.service-desc {
	line-height: 1.5;
}

.service-arrow {
	color: #236f82;
	background: rgba(63, 143, 159, 0.1);
	border-color: rgba(63, 143, 159, 0.16);
}

</style>
