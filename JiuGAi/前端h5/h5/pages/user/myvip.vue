<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="hero-card">
				<text class="hero-tag">VIP</text>
				<text class="hero-title">{{ profile.vipName || copy.defaultVip }}</text>
				<text class="hero-subtitle">{{ vipSummary }}</text>
			</view>

			<view class="section-card">
				<text class="section-title">{{ copy.benefitTitle }}</text>
				<text class="section-subtitle">{{ copy.benefitSubtitle }}</text>
				<view class="benefit-list">
					<view class="benefit-row">
						<text class="benefit-name">{{ copy.chatQuota }}</text>
						<text class="benefit-value">{{ Number(profile.dailyChatQuota || 0) }}/{{ copy.dayUnit }}</text>
					</view>
					<view class="benefit-row">
						<text class="benefit-name">{{ copy.imageQuota }}</text>
						<text class="benefit-value">{{ Number(profile.dailyImageQuota || 0) }}/{{ copy.dayUnit }}</text>
					</view>
					<view class="benefit-row">
						<text class="benefit-name">{{ copy.identityLabel }}</text>
						<text class="benefit-value">{{ profile.telegramReady ? copy.identityReady : copy.identityCompat }}</text>
					</view>
				</view>
			</view>

			<view class="section-card">
				<text class="section-title">{{ copy.productTitle }}</text>
				<text class="section-subtitle">{{ copy.productSubtitle }}</text>
				<view class="product-list">
					<view v-for="item in vipProducts" :key="item.code" class="product-item">
						<view class="product-main">
							<view class="product-head">
								<text class="product-name">{{ item.name }}</text>
								<text v-if="item.badgeLabel" class="product-badge">{{ item.badgeLabel }}</text>
							</view>
							<text class="product-desc">{{ item.subtitle || copy.productFallback }}</text>
							<text class="product-meta">
								{{ copy.bonusPrefix }} {{ Number(item.scoreAmount || 0) }} {{ copy.diamond }}
								· {{ Number(item.goldCoinAmount || 0) }} {{ copy.coin }}
							</text>
						</view>
						<view class="product-side">
							<text class="product-price">¥{{ item.priceYuan }}</text>
							<view class="buy-btn" @tap="goPay(item.code)">{{ copy.buyNow }}</view>
						</view>
					</view>
					<view v-if="!vipProducts.length" class="empty-box">
						<text>{{ copy.emptyProducts }}</text>
					</view>
				</view>
			</view>

			<view class="note-card">
				<text class="note-title">{{ copy.noteTitle }}</text>
				<text class="note-text">{{ copy.note }}</text>
			</view>
			<u-gap height="48"></u-gap>
		</scroll-view>
		<view class="project-notice-mask">
			<view class="project-notice-card">
				<text class="project-notice-tag">NOTICE</text>
				<text class="project-notice-title">{{ projectNotice.title }}</text>
				<text class="project-notice-desc">{{ projectNotice.message }}</text>
				<view class="project-notice-actions">
					<view class="project-notice-btn project-notice-btn--ghost" @tap="goBack">{{ projectNotice.backText }}</view>
					<view class="project-notice-btn project-notice-btn--primary" @tap="openProjectContact">{{ projectNotice.contactText }}</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');
const { getProjectNoticeCopy } = require('@/common/tavernProjectNotice.js');

const COPY = {
	'zh-cn': {
		title: '会员中心',
		defaultVip: '普通用户',
		benefitTitle: '当前权益',
		benefitSubtitle: '这里展示当前账号每天可用的配额和身份能力',
		chatQuota: '聊天额度',
		imageQuota: '生图额度',
		identityLabel: '身份形态',
		identityReady: '可升级为 Telegram 正式身份',
		identityCompat: '当前为 H5 兼容身份',
		productTitle: '推荐套餐',
		productSubtitle: '按你的使用频率挑一个更合适的档位',
		productFallback: '提升聊天额度与会员访问能力',
		bonusPrefix: '赠送',
		diamond: '钻石',
		coin: '金币',
		buyNow: '去开通',
		dayUnit: '天',
		noteTitle: '说明',
		note: '当前会员商品会继续复用到正式支付通道中。先看权益，再选择适合你的周卡、月卡或长期套餐。',
		vipUntil: '有效期至 {time}，你可以继续叠加会员时长。',
		vipEmpty: '当前还未开通正式 VIP，先从轻量套餐开始会更稳妥。',
		emptyProducts: '暂时没有可购买的会员套餐。'
	},
	'zh-hk': {
		title: '會員中心',
		defaultVip: '普通用戶',
		benefitTitle: '目前權益',
		benefitSubtitle: '這裡會顯示目前帳號每日可用的額度與身份能力',
		chatQuota: '聊天額度',
		imageQuota: '生圖額度',
		identityLabel: '身份形態',
		identityReady: '可升級為 Telegram 正式身份',
		identityCompat: '目前為 H5 相容身份',
		productTitle: '推薦方案',
		productSubtitle: '按你的使用頻率挑一個更合適的檔位',
		productFallback: '提升聊天額度與會員訪問能力',
		bonusPrefix: '贈送',
		diamond: '鑽石',
		coin: '金幣',
		buyNow: '去開通',
		dayUnit: '天',
		noteTitle: '說明',
		note: '目前會員商品會繼續沿用到正式支付通道。先看權益，再選擇適合你的週卡、月卡或長期套餐。',
		vipUntil: '有效期至 {time}，你可以繼續疊加會員時長。',
		vipEmpty: '目前尚未開通正式 VIP，可先從輕量方案開始。',
		emptyProducts: '暫時沒有可購買的會員方案。'
	},
	en: {
		title: 'Membership Center',
		defaultVip: 'Standard User',
		benefitTitle: 'Current Benefits',
		benefitSubtitle: 'See the daily quotas and identity capabilities available on this account.',
		chatQuota: 'Chat quota',
		imageQuota: 'Image quota',
		identityLabel: 'Identity mode',
		identityReady: 'Ready for full Telegram identity',
		identityCompat: 'Currently using H5-compatible identity',
		productTitle: 'Recommended Plans',
		productSubtitle: 'Pick a plan that matches how often you actually chat',
		productFallback: 'More chat quota and premium access',
		bonusPrefix: 'Bonus',
		diamond: 'diamonds',
		coin: 'coins',
		buyNow: 'Open',
		dayUnit: 'day',
		noteTitle: 'Note',
		note: 'The same VIP products will continue to be used when real payment providers are turned on. Review the benefits first, then choose a plan.',
		vipUntil: 'Valid until {time}. You can stack more time anytime.',
		vipEmpty: 'VIP is not active yet. Starting with a lighter plan is usually the safest choice.',
		emptyProducts: 'No membership plans are available right now.'
	},
	ko: {
		title: 'Membership Center',
		defaultVip: 'Standard User',
		benefitTitle: 'Current Benefits',
		benefitSubtitle: 'See the daily quotas and identity capabilities available on this account.',
		chatQuota: 'Chat quota',
		imageQuota: 'Image quota',
		identityLabel: 'Identity mode',
		identityReady: 'Ready for full Telegram identity',
		identityCompat: 'Currently using H5-compatible identity',
		productTitle: 'Recommended Plans',
		productSubtitle: 'Pick a plan that matches how often you actually chat',
		productFallback: 'More chat quota and premium access',
		bonusPrefix: 'Bonus',
		diamond: 'diamonds',
		coin: 'coins',
		buyNow: 'Open',
		dayUnit: 'day',
		noteTitle: 'Note',
		note: 'The same VIP products will continue to be used when real payment providers are turned on. Review the benefits first, then choose a plan.',
		vipUntil: 'Valid until {time}. You can stack more time anytime.',
		vipEmpty: 'VIP is not active yet. Starting with a lighter plan is usually the safest choice.',
		emptyProducts: 'No membership plans are available right now.'
	},
	ja: {
		title: 'Membership Center',
		defaultVip: 'Standard User',
		benefitTitle: 'Current Benefits',
		benefitSubtitle: 'See the daily quotas and identity capabilities available on this account.',
		chatQuota: 'Chat quota',
		imageQuota: 'Image quota',
		identityLabel: 'Identity mode',
		identityReady: 'Ready for full Telegram identity',
		identityCompat: 'Currently using H5-compatible identity',
		productTitle: 'Recommended Plans',
		productSubtitle: 'Pick a plan that matches how often you actually chat',
		productFallback: 'More chat quota and premium access',
		bonusPrefix: 'Bonus',
		diamond: 'diamonds',
		coin: 'coins',
		buyNow: 'Open',
		dayUnit: 'day',
		noteTitle: 'Note',
		note: 'The same VIP products will continue to be used when real payment providers are turned on. Review the benefits first, then choose a plan.',
		vipUntil: 'Valid until {time}. You can stack more time anytime.',
		vipEmpty: 'VIP is not active yet. Starting with a lighter plan is usually the safest choice.',
		emptyProducts: 'No membership plans are available right now.'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			profile: {},
			vipProducts: [],
			profileAccessSignature: ''
		};
	},
	computed: {
		copy() {
			return COPY[getLanguageCode()] || COPY.en;
		},
		projectNotice() {
			return getProjectNoticeCopy(getLanguageCode());
		},
		vipSummary() {
			if (this.profile.vipActive && this.profile.vipExpiresAt) {
				return this.copy.vipUntil.replace('{time}', this.formatDate(this.profile.vipExpiresAt));
			}
			return this.copy.vipEmpty;
		}
	},
	onShow() {
		this.initProjectShell();
	},
	methods: {
		initProjectShell() {
			this.profile = {};
			this.vipProducts = [];
		},
		openProjectContact() {
			uni.navigateTo({ url: '/pages/user/aboutmy' });
		},
		ensureLoginForPayment(productCode) {
			if (tavernApi.hasLoggedInUser()) {
				this.util.urlTo('/pages/user/pay?productCode=' + encodeURIComponent(productCode));
				return;
			}
			uni.showModal({
				title: '请先登录',
				content: '开通会员需要账号，用于保存权益和订单记录。',
				confirmText: '去登录',
				cancelText: '稍后',
				success: (res) => {
					if (res.confirm) {
						uni.navigateTo({
							url: tavernApi.buildLoginUrl('/pages/user/pay?productCode=' + encodeURIComponent(productCode))
						});
					}
				}
			});
		},
		loadPage() {
			const clientUid = tavernApi.getClientUid();
			tavernApi
				.fetchStoreOverview(clientUid)
				.then((res) => {
					const nextProfile = (res && res.profile) || {};
					const nextAccessSignature = tavernApi.getProfileAccessSignature(nextProfile);
					if (this.profileAccessSignature && this.profileAccessSignature !== nextAccessSignature) {
						tavernApi.markCharacterAccessRefreshNeeded('vip-center');
					}
					this.profileAccessSignature = nextAccessSignature;
					this.profile = nextProfile;
					const products = (res && res.products && res.products.vip) || [];
					this.vipProducts = Array.isArray(products) ? products : [];
				})
				.catch((e) => {
					this.profile = {};
					this.vipProducts = [];
					uni.showToast({ title: e && e.message ? e.message : this.copy.title, icon: 'none' });
				});
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		goPay(productCode) {
			this.ensureLoginForPayment(productCode);
		},
		formatDate(value) {
			if (!value) return '--';
			return String(value).replace('T', ' ').slice(0, 16);
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background:
		radial-gradient(circle at top left, rgba(245, 158, 11, 0.16), transparent 32%),
		radial-gradient(circle at bottom right, rgba(236, 72, 153, 0.14), transparent 28%),
		$tavern-page-bg;
}

.scroll {
	height: calc(100vh - 88rpx);
	padding: 24rpx;
	box-sizing: border-box;
}

.hero-card,
.section-card,
.note-card {
	background: $tavern-card-dark;
	border-radius: 24rpx;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
	padding: 28rpx;
	margin-bottom: 20rpx;
}

.hero-card {
	background: linear-gradient(135deg, rgba(245, 158, 11, 0.24) 0%, rgba(236, 72, 153, 0.18) 100%);
}

.hero-tag,
.product-badge {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	padding: 8rpx 16rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: #fff;
	background: rgba(255, 255, 255, 0.16);
}

.hero-title,
.section-title,
.benefit-value,
.product-name,
.product-price,
.note-title {
	color: $tavern-text-on-dark;
}

.hero-title {
	display: block;
	margin-top: 12rpx;
	font-size: 38rpx;
	font-weight: 700;
}

.hero-subtitle,
.section-subtitle,
.benefit-name,
.product-desc,
.product-meta,
.note-text,
.empty-box text {
	display: block;
	font-size: 24rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.hero-subtitle,
.section-subtitle,
.note-text {
	margin-top: 12rpx;
}

.section-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
}

.benefit-list,
.product-list {
	margin-top: 18rpx;
}

.benefit-row,
.product-item,
.product-head {
	display: flex;
}

.benefit-row,
.product-item {
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	padding: 18rpx 0;
	border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
}

.benefit-row:first-child,
.product-item:first-child {
	padding-top: 0;
}

.benefit-row:last-child,
.product-item:last-child {
	border-bottom: none;
	padding-bottom: 0;
}

.benefit-name {
	flex: 1;
	min-width: 0;
}

.benefit-value {
	font-size: 26rpx;
	font-weight: 700;
	text-align: right;
}

.product-main {
	flex: 1;
	min-width: 0;
}

.product-head {
	align-items: center;
	gap: 12rpx;
}

.product-name {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
}

.product-desc,
.product-meta {
	margin-top: 10rpx;
}

.product-side {
	display: flex;
	flex-direction: column;
	align-items: flex-end;
	gap: 12rpx;
}

.product-price {
	font-size: 34rpx;
	font-weight: 700;
}

.buy-btn {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-width: 148rpx;
	height: 64rpx;
	padding: 0 26rpx;
	border-radius: 999rpx;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #f59e0b 0%, #ec4899 100%);
}

.note-title {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
}

.empty-box {
	padding: 12rpx 0 4rpx;
}

.project-notice-mask {
	position: fixed;
	inset: 0;
	z-index: 30;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 36rpx;
	background: rgba(6, 10, 20, 0.72);
	backdrop-filter: blur(18rpx);
}

.project-notice-card {
	width: 100%;
	max-width: 680rpx;
	padding: 34rpx 30rpx 28rpx;
	border-radius: 28rpx;
	background: linear-gradient(180deg, rgba(15, 23, 42, 0.96) 0%, rgba(30, 41, 59, 0.94) 100%);
	border: 1rpx solid rgba(148, 163, 184, 0.2);
	box-shadow: 0 24rpx 72rpx rgba(15, 23, 42, 0.42);
}

.project-notice-tag {
	display: inline-flex;
	padding: 8rpx 18rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	font-weight: 700;
	letter-spacing: 1rpx;
	color: #f8fafc;
	background: rgba(99, 102, 241, 0.3);
}

.project-notice-title {
	display: block;
	margin-top: 18rpx;
	font-size: 36rpx;
	font-weight: 700;
	color: #fff;
}

.project-notice-desc {
	display: block;
	margin-top: 16rpx;
	font-size: 26rpx;
	line-height: 1.8;
	color: rgba(226, 232, 240, 0.92);
}

.project-notice-actions {
	display: flex;
	gap: 16rpx;
	margin-top: 28rpx;
}

.project-notice-btn {
	flex: 1;
	height: 78rpx;
	line-height: 78rpx;
	text-align: center;
	border-radius: 999rpx;
	font-size: 26rpx;
	font-weight: 700;
}

.project-notice-btn--ghost {
	color: #cbd5e1;
	background: rgba(148, 163, 184, 0.14);
}

.project-notice-btn--primary {
	color: #fff;
	background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}

/* Light clover tavern vip refresh. */
.hero-card,
.section-card,
.note-card {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.hero-card {
	background: linear-gradient(135deg, rgba(255, 246, 220, 0.74), rgba(255, 235, 243, 0.56));
}

.hero-title,
.section-title,
.benefit-value,
.product-name,
.product-price,
.note-title {
	color: #244b66;
}

.hero-subtitle,
.section-subtitle,
.benefit-name,
.product-desc,
.product-meta,
.note-text,
.empty-box text {
	color: #687f92;
}

.buy-btn,
.project-notice-btn--primary {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
}

.project-notice-mask {
	background: rgba(67, 112, 142, 0.22);
}

.project-notice-card {
	background: rgba(255, 255, 255, 0.58);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(24rpx);
	-webkit-backdrop-filter: blur(24rpx);
}

.project-notice-tag {
	color: #247494;
	background: rgba(220, 247, 251, 0.84);
}

.project-notice-title {
	color: #244b66;
}

.project-notice-desc,
.project-notice-btn--ghost {
	color: #687f92;
}

.project-notice-btn--ghost {
	background: rgba(255, 255, 255, 0.42);
}
</style>
