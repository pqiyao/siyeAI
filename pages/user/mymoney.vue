<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="hero-card">
				<text class="hero-tag">{{ copy.walletTag }}</text>
				<text class="hero-title">{{ copy.heroTitle }}</text>
				<text class="hero-subtitle">{{ copy.heroSubtitle }}</text>
			</view>

			<view class="balance-card">
				<view class="balance-item">
					<text class="balance-label">{{ copy.diamondBalance }}</text>
					<text class="balance-value">{{ Number(profile.score || 0) }}</text>
				</view>
				<view class="balance-item">
					<text class="balance-label">{{ copy.coinBalance }}</text>
					<text class="balance-value">{{ Number(profile.goldCoin || 0) }}</text>
				</view>
			</view>

			<view class="section-card">
				<view class="section-head">
					<view>
						<text class="section-title">{{ copy.packageTitle }}</text>
						<text class="section-subtitle">{{ copy.packageSubtitle }}</text>
					</view>
				</view>
				<view class="product-list">
					<view v-for="item in coinProducts" :key="item.code" class="product-item">
						<view class="product-main">
							<view class="product-head">
								<text class="product-name">{{ item.name }}</text>
								<text v-if="item.tagLabel" class="tag-label">{{ item.tagLabel }}</text>
							</view>
							<text class="product-desc">{{ item.subtitle || copy.packageFallback }}</text>
							<text class="product-bonus">
								{{ Number(item.scoreAmount || 0) }} {{ copy.diamondUnit }}
								· {{ Number(item.goldCoinAmount || 0) }} {{ copy.coinUnit }}
							</text>
						</view>
						<view class="product-side">
							<text class="product-price">¥{{ item.priceYuan }}</text>
							<view class="buy-btn" @tap="goPay(item.code)">{{ copy.buyNow }}</view>
						</view>
					</view>
					<view v-if="!coinProducts.length" class="empty-box">
						<text>{{ copy.emptyProducts }}</text>
					</view>
				</view>
			</view>

			<view class="section-card">
				<view class="section-head">
					<view>
						<text class="section-title">{{ copy.orderTitle }}</text>
						<text class="section-subtitle">{{ copy.orderSubtitle }}</text>
					</view>
					<view class="refresh-btn" @tap="loadPage">{{ copy.refresh }}</view>
				</view>
				<view v-if="orderList.length" class="order-list">
					<view v-for="item in orderList" :key="item.orderNo" class="order-item">
						<view class="order-main">
							<text class="order-name">{{ item.productName }}</text>
							<text class="order-meta">{{ item.orderNo }}</text>
							<text class="order-meta">{{ formatDate(item.createdAt) }}</text>
						</view>
						<view class="order-side">
							<text class="order-price">¥{{ item.amountYuan }}</text>
							<text class="order-status" :class="item.status === 'PAID' ? 'paid' : 'pending'">{{ item.statusLabel }}</text>
						</view>
					</view>
				</view>
				<view v-else class="empty-box">
					<text>{{ copy.emptyOrders }}</text>
				</view>
			</view>

			<view class="note-card">
				<text class="note-title">{{ copy.noteTitle }}</text>
				<text class="note-text">{{ copy.noteBody }}</text>
			</view>
			<u-gap height="48"></u-gap>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '钱包充值',
		walletTag: 'WALLET',
		heroTitle: '管理你的钻石和金币余额',
		heroSubtitle: '充值后可用于后续功能解锁、订单抵扣和运营活动。游客也能浏览，真正付款时再登录即可。',
		diamondBalance: '钻石余额',
		coinBalance: '金币余额',
		packageTitle: '充值套餐',
		packageSubtitle: '选择一个更适合现在节奏的档位',
		packageFallback: '购买后会自动补充账户余额',
		diamondUnit: '钻石',
		coinUnit: '金币',
		buyNow: '去支付',
		emptyProducts: '暂时还没有可购买的充值套餐。',
		orderTitle: '最近订单',
		orderSubtitle: '这里只展示你最近的充值记录',
		refresh: '刷新',
		emptyOrders: '还没有充值订单，先挑一个套餐试试吧。',
		noteTitle: '到账说明',
		noteBody: '支付成功后余额会自动刷新；若遇到延迟到账，可直接从“联系客服”提交工单处理。'
	},
	'zh-hk': {
		title: '錢包充值',
		walletTag: 'WALLET',
		heroTitle: '管理你的鑽石和金幣餘額',
		heroSubtitle: '充值後可用於後續功能解鎖、訂單抵扣與活動使用。旅客可先瀏覽，付款時再登入即可。',
		diamondBalance: '鑽石餘額',
		coinBalance: '金幣餘額',
		packageTitle: '充值方案',
		packageSubtitle: '選擇一個更適合目前節奏的檔位',
		packageFallback: '購買後會自動補充帳戶餘額',
		diamondUnit: '鑽石',
		coinUnit: '金幣',
		buyNow: '去支付',
		emptyProducts: '暫時沒有可購買的充值方案。',
		orderTitle: '最近訂單',
		orderSubtitle: '這裡會顯示你最近的充值記錄',
		refresh: '刷新',
		emptyOrders: '還沒有充值訂單，先挑一個方案試試吧。',
		noteTitle: '到帳說明',
		noteBody: '支付成功後餘額會自動刷新；若遇到延遲到帳，可從「聯絡客服」提交工單。'
	},
	en: {
		title: 'Wallet Top-Up',
		walletTag: 'WALLET',
		heroTitle: 'Top up diamonds and coins for your account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		packageTitle: 'Top-Up Plans',
		packageSubtitle: 'Pick a package that matches your current pace',
		packageFallback: 'Your balance will update automatically after purchase',
		diamondUnit: 'diamonds',
		coinUnit: 'coins',
		buyNow: 'Pay',
		emptyProducts: 'No wallet packages are available right now.',
		orderTitle: 'Recent Orders',
		orderSubtitle: 'Your latest top-up records appear here',
		refresh: 'Refresh',
		emptyOrders: 'No top-up orders yet. Try your first package.',
		noteTitle: 'Arrival Notice',
		noteBody: 'Balances refresh automatically after payment. If anything is delayed, submit a support ticket directly.'
	},
	ko: {
		title: 'Wallet Top-Up',
		walletTag: 'WALLET',
		heroTitle: 'Top up diamonds and coins for your account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		packageTitle: 'Top-Up Plans',
		packageSubtitle: 'Pick a package that matches your current pace',
		packageFallback: 'Your balance will update automatically after purchase',
		diamondUnit: 'diamonds',
		coinUnit: 'coins',
		buyNow: 'Pay',
		emptyProducts: 'No wallet packages are available right now.',
		orderTitle: 'Recent Orders',
		orderSubtitle: 'Your latest top-up records appear here',
		refresh: 'Refresh',
		emptyOrders: 'No top-up orders yet. Try your first package.',
		noteTitle: 'Arrival Notice',
		noteBody: 'Balances refresh automatically after payment. If anything is delayed, submit a support ticket directly.'
	},
	ja: {
		title: 'Wallet Top-Up',
		walletTag: 'WALLET',
		heroTitle: 'Top up diamonds and coins for your account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		packageTitle: 'Top-Up Plans',
		packageSubtitle: 'Pick a package that matches your current pace',
		packageFallback: 'Your balance will update automatically after purchase',
		diamondUnit: 'diamonds',
		coinUnit: 'coins',
		buyNow: 'Pay',
		emptyProducts: 'No wallet packages are available right now.',
		orderTitle: 'Recent Orders',
		orderSubtitle: 'Your latest top-up records appear here',
		refresh: 'Refresh',
		emptyOrders: 'No top-up orders yet. Try your first package.',
		noteTitle: 'Arrival Notice',
		noteBody: 'Balances refresh automatically after payment. If anything is delayed, submit a support ticket directly.'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			profile: {},
			coinProducts: [],
			orderList: []
		};
	},
	computed: {
		copy() {
			return COPY[getLanguageCode()] || COPY.en;
		}
	},
	onShow() {
		this.loadPage();
	},
	methods: {
		ensureLoginForPayment(productCode) {
			if (tavernApi.hasLoggedInUser()) {
				this.util.urlTo('/pages/user/pay?productCode=' + encodeURIComponent(productCode));
				return;
			}
			uni.showModal({
				title: '请先登录',
				content: '充值需要账号，用于保存订单和到账权益。',
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
					this.profile = (res && res.profile) || {};
					this.coinProducts = (res && res.products && res.products.coin) || [];
					this.orderList = (res && res.orders) || [];
				})
				.catch((e) => {
					this.profile = {};
					this.coinProducts = [];
					this.orderList = [];
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
		radial-gradient(circle at top left, rgba(99, 102, 241, 0.18), transparent 34%),
		radial-gradient(circle at bottom right, rgba(236, 72, 153, 0.14), transparent 30%),
		$tavern-page-bg;
}

.scroll {
	height: calc(100vh - 88rpx);
	padding: 24rpx;
	box-sizing: border-box;
}

.hero-card,
.balance-card,
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
	background: linear-gradient(135deg, rgba(79, 70, 229, 0.24) 0%, rgba(236, 72, 153, 0.16) 100%);
}

.hero-tag,
.tag-label {
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
.balance-value,
.product-name,
.product-price,
.order-name,
.order-price,
.note-title {
	color: $tavern-text-on-dark;
}

.hero-title {
	display: block;
	margin-top: 14rpx;
	font-size: 38rpx;
	font-weight: 700;
}

.hero-subtitle,
.section-subtitle,
.balance-label,
.product-desc,
.product-bonus,
.order-meta,
.note-text,
.empty-box text {
	display: block;
	font-size: 24rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.hero-subtitle {
	margin-top: 12rpx;
}

.balance-card {
	display: flex;
	gap: 18rpx;
}

.balance-item {
	flex: 1;
	padding: 24rpx;
	border-radius: 20rpx;
	background: rgba(15, 23, 42, 0.42);
	border: 1rpx solid rgba(148, 163, 184, 0.14);
}

.balance-value {
	display: block;
	margin-top: 12rpx;
	font-size: 36rpx;
	font-weight: 700;
}

.section-head,
.product-item,
.product-head,
.order-item {
	display: flex;
}

.section-head,
.order-item {
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.section-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
}

.section-subtitle {
	margin-top: 8rpx;
}

.refresh-btn,
.buy-btn {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	border-radius: 999rpx;
}

.refresh-btn {
	padding: 8rpx 18rpx;
	font-size: 22rpx;
	color: #c4b5fd;
	background: rgba(91, 33, 182, 0.18);
}

.product-list,
.order-list {
	margin-top: 20rpx;
}

.product-item {
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	padding: 24rpx 0;
	border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
}

.product-item:first-child {
	padding-top: 0;
}

.product-item:last-child {
	border-bottom: none;
	padding-bottom: 0;
}

.product-main,
.order-main {
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
.product-bonus {
	margin-top: 10rpx;
}

.product-side,
.order-side {
	display: flex;
	flex-direction: column;
	align-items: flex-end;
	gap: 12rpx;
}

.product-price,
.order-price {
	font-size: 34rpx;
	font-weight: 700;
}

.buy-btn {
	min-width: 140rpx;
	height: 64rpx;
	padding: 0 26rpx;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #7c3aed 0%, #ec4899 100%);
}

.order-item {
	padding: 20rpx 0;
	border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
}

.order-item:last-child {
	border-bottom: none;
	padding-bottom: 0;
}

.order-name {
	display: block;
	font-size: 28rpx;
	font-weight: 700;
}

.order-meta {
	margin-top: 8rpx;
}

.order-status {
	font-size: 22rpx;
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
}

.order-status.paid {
	color: #86efac;
	background: rgba(34, 197, 94, 0.14);
}

.order-status.pending {
	color: #fcd34d;
	background: rgba(234, 179, 8, 0.14);
}

.note-title {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
}

.note-text {
	margin-top: 12rpx;
}

.empty-box {
	padding: 18rpx 0 4rpx;
}
</style>
