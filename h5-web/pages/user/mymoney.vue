<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="hero-card">
				<view class="hero-tag"><u-icon name="red-packet-fill" color="#4f93a3" size="22"></u-icon><text>{{ copy.walletTag }}</text></view>
				<text class="hero-title">{{ copy.heroTitle }}</text>
				<text class="hero-subtitle">{{ copy.heroSubtitle }}</text>
			</view>

			<view class="balance-card">
				<view class="balance-item balance-item--diamond">
					<view class="balance-head"><view class="balance-icon"><u-icon name="integral-fill" color="#4f93a3" size="25"></u-icon></view><text class="balance-label">{{ copy.diamondBalance }}</text></view>
					<text class="balance-value">{{ Number(profile.score || 0) }}</text>
				</view>
				<view class="balance-item balance-item--coin">
					<view class="balance-head"><view class="balance-icon"><u-icon name="rmb-circle-fill" color="#ad7a24" size="25"></u-icon></view><text class="balance-label">{{ copy.coinBalance }}</text></view>
					<text class="balance-value">{{ Number(profile.goldCoin || 0) }}</text>
				</view>
			</view>
			<view class="quota-hint">
				<text class="quota-hint-text">{{ copy.quotaHint }}</text>
			</view>

			<view v-if="rechargeEntryReady && rechargeEntryVisible" class="section-card">
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
							<view
								v-if="rechargeEntryReady && rechargeEntryVisible"
								class="buy-btn"
								@tap="goPay(item.code)"
							><text>{{ copy.buyNow }}</text><u-icon name="arrow-right" color="#ffffff" size="20"></u-icon></view>
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
					<view class="order-head-actions">
						<view v-if="hasRemovableOrders" class="order-tool-btn" role="button" aria-label="清理未支付订单" title="清理未支付订单" @tap="toggleAllRemovableOrders">
							<u-icon name="checkbox-mark" color="#b45309" size="22"></u-icon>
						</view>
						<view class="order-tool-btn" role="button" :aria-label="copy.refresh" :title="copy.refresh" @tap="loadPage">
							<u-icon name="reload" color="#4f7f8e" size="22"></u-icon>
						</view>
					</view>
				</view>
				<scroll-view v-if="orderList.length" scroll-y class="order-list-scroll" :class="{ 'order-list-scroll--limited': orderList.length > 4 }" :show-scrollbar="false">
					<view class="order-list">
					<view
						v-for="item in orderList"
						:key="item.orderNo"
						class="order-item"
					>
						<view class="order-main">
							<text class="order-name">{{ item.productName }}</text>
							<text class="order-meta">{{ item.orderNo }}</text>
							<text class="order-meta">{{ formatDate(item.createdAt) }}</text>
						</view>
						<view class="order-side">
							<text class="order-price">¥{{ item.amountYuan }}</text>
							<text class="order-status" :class="item.status === 'PAID' ? 'paid' : 'pending'">{{ item.statusLabel }}</text>
							<view
								v-if="String(item.status || '').toUpperCase() !== 'PAID'"
								class="order-tool-btn order-remove-btn"
								role="button"
								aria-label="删除未支付订单"
								title="删除未支付订单"
								@tap.stop="confirmRemoveOrders([item.orderNo])"
							>
								<u-icon name="trash" color="#b45309" size="22"></u-icon>
							</view>
						</view>
					</view>
					</view>
				</scroll-view>
				<view v-else class="empty-box">
					<text>{{ copy.emptyOrders }}</text>
				</view>
			</view>

			<view v-if="rechargeEntryReady && rechargeEntryVisible" class="note-card">
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
		heroTitle: '把钻石和金币装进你的酒馆口袋',
		heroSubtitle: '充值后可用于后续功能解锁、订单抵扣和运营活动。游客也能浏览，真正付款时再登录即可。',
		diamondBalance: '钻石余额',
		coinBalance: '金币余额',
		quotaHint: '免费每日额度用完后，聊天 / 生图 / 语音将优先消耗钻石（或金币，视站点配置）。',
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
		heroTitle: '把鑽石和金幣放進你的酒館口袋',
		heroSubtitle: '充值後可用於後續功能解鎖、訂單抵扣與活動使用。旅客可先瀏覽，付款時再登入即可。',
		diamondBalance: '鑽石餘額',
		coinBalance: '金幣餘額',
		quotaHint: '免費每日額度用完後，聊天 / 生圖 / 語音會優先消耗鑽石（或金幣，視站點設定）。',
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
		heroTitle: 'Top up diamonds and coins for your tavern account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		quotaHint: 'When free daily quota runs out, chat / image / voice will spend diamonds (or coins, depending on site settings).',
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
		heroTitle: 'Top up diamonds and coins for your tavern account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		quotaHint: 'When free daily quota runs out, chat / image / voice will spend diamonds (or coins, depending on site settings).',
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
		heroTitle: 'Top up diamonds and coins for your tavern account',
		heroSubtitle: 'Use your balance for future unlocks, orders, and events. Guests can browse first and log in only when paying.',
		diamondBalance: 'Diamond Balance',
		coinBalance: 'Coin Balance',
		quotaHint: 'When free daily quota runs out, chat / image / voice will spend diamonds (or coins, depending on site settings).',
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
			orderList: [],
			selectedOrderNos: [],
			rechargeEntryVisible: tavernApi.isRechargeEntryVisible(),
			rechargeEntryReady: typeof tavernApi.hasRuntimeFeatureConfigSnapshot === 'function' && tavernApi.hasRuntimeFeatureConfigSnapshot()
		};
	},
	computed: {
		copy() {
			return COPY[getLanguageCode()] || COPY.en;
		},
		hasRemovableOrders() {
			return this.orderList.some((item) => String(item.status || '').toUpperCase() !== 'PAID');
		}
	},
	onShow() {
		this.loadPage();
		this.syncRechargeEntryVisibility(true);
	},
	methods: {
		toggleAllRemovableOrders() {
			const removable = this.orderList
				.filter((item) => String(item.status || '').toUpperCase() !== 'PAID')
				.map((item) => item.orderNo)
				.filter(Boolean);
			this.selectedOrderNos = this.selectedOrderNos.length === removable.length ? [] : removable;
			if (this.selectedOrderNos.length) this.confirmRemoveOrders(this.selectedOrderNos);
		},
		confirmRemoveOrders(orderNos) {
			const targets = Array.isArray(orderNos) ? orderNos.filter(Boolean) : [];
			if (!targets.length) return;
			uni.showModal({
				title: '删除未支付订单',
				content: '删除后仅从钱包列表隐藏，不会影响支付平台已经发出的回调。',
				confirmText: '删除',
				cancelText: '取消',
				success: (res) => {
					if (!res.confirm) return;
					const clientUid = tavernApi.getClientUid();
					Promise.all(targets.map((orderNo) => tavernApi.postStoreOrderRemove({ clientUid, orderNo }))).then(() => {
						this.orderList = this.orderList.filter((order) => !targets.includes(order.orderNo));
						this.selectedOrderNos = [];
					}).catch((error) => {
						uni.showToast({ title: error && error.message ? error.message : '订单删除失败', icon: 'none' });
					});
				}
			});
		},
		syncRechargeEntryVisibility(forceRefresh) {
			this.rechargeEntryVisible = tavernApi.isRechargeEntryVisible();
			if (typeof tavernApi.hasRuntimeFeatureConfigSnapshot === 'function' && tavernApi.hasRuntimeFeatureConfigSnapshot()) {
				this.rechargeEntryReady = true;
			}
			return tavernApi
				.fetchAppRuntimeConfig(forceRefresh === true)
				.then((config) => {
					this.rechargeEntryVisible = !(config && config.rechargeEntryVisible === false);
					this.rechargeEntryReady = true;
					return this.rechargeEntryVisible;
				})
				.catch(() => {
					this.rechargeEntryReady = true;
					return this.rechargeEntryVisible;
				});
		},
		ensureLoginForPayment(productCode) {
			if (!this.rechargeEntryReady || !this.rechargeEntryVisible) return;
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
			if (!this.rechargeEntryReady || !this.rechargeEntryVisible) return;
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

.quota-hint {
	margin: -8rpx 0 20rpx;
	padding: 0 8rpx;
}

.quota-hint-text {
	display: block;
	font-size: 22rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
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

/* Wallet recharge: light glass visual system. */
.page {
	position: relative;
	background: transparent;
	overflow: hidden;
	color: #203846;
}

.scroll {
	height: calc(100vh - 88rpx);
	padding: 28rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
}

.hero-card,
.balance-card,
.section-card,
.note-card {
	margin-bottom: 22rpx;
	padding: 30rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.84);
	border-radius: 32rpx;
	background: linear-gradient(145deg, rgba(255, 255, 255, 0.76) 0%, rgba(245, 251, 253, 0.56) 100%);
	box-shadow: 0 22rpx 52rpx rgba(44, 83, 103, 0.12), inset 0 1rpx 0 rgba(255, 255, 255, 0.9);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
	box-sizing: border-box;
}

.hero-card {
	padding: 34rpx 32rpx;
	border-radius: 36rpx;
	background: linear-gradient(135deg, rgba(255, 255, 255, 0.82) 0%, rgba(226, 245, 249, 0.64) 62%, rgba(255, 238, 246, 0.52) 100%);
}

.hero-tag {
	display: inline-flex;
	align-items: center;
	gap: 8rpx;
	padding: 9rpx 15rpx;
	border: 1rpx solid rgba(79, 147, 163, 0.16);
	border-radius: 999rpx;
	font-size: 21rpx;
	font-weight: 700;
	color: #4f7f8e;
	background: rgba(255, 255, 255, 0.54);
}

.hero-title,
.section-title,
.balance-value,
.product-name,
.product-price,
.order-name,
.order-price,
.note-title {
	color: #203846;
}

.hero-title {
	margin-top: 18rpx;
	font-size: 36rpx;
	line-height: 1.38;
	font-weight: 800;
}

.hero-subtitle,
.section-subtitle,
.balance-label,
.product-desc,
.product-bonus,
.order-meta,
.note-text,
.empty-box text {
	color: #647b8b;
}

.hero-subtitle {
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.75;
}

.balance-card {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 16rpx;
	padding: 18rpx;
	background: rgba(255, 255, 255, 0.5);
}

.balance-item {
	min-width: 0;
	padding: 24rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.84);
	border-radius: 26rpx;
	background: rgba(255, 255, 255, 0.62);
	box-shadow: 0 14rpx 32rpx rgba(44, 83, 103, 0.08), inset 0 1rpx 0 rgba(255, 255, 255, 0.88);
	box-sizing: border-box;
}

.balance-item--diamond {
	background: linear-gradient(145deg, rgba(236, 249, 252, 0.86) 0%, rgba(255, 255, 255, 0.62) 100%);
}

.balance-item--coin {
	background: linear-gradient(145deg, rgba(255, 249, 231, 0.82) 0%, rgba(255, 255, 255, 0.62) 100%);
}

.balance-head {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.balance-icon {
	width: 46rpx;
	height: 46rpx;
	border-radius: 15rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.66);
}

.balance-label {
	display: block;
	min-width: 0;
	font-size: 22rpx;
	line-height: 1.35;
}

.balance-value {
	margin-top: 14rpx;
	font-size: 38rpx;
	line-height: 1.2;
	font-weight: 800;
}

.quota-hint {
	margin: -2rpx 0 24rpx;
	padding: 0 8rpx;
}

.quota-hint-text {
	font-size: 22rpx;
	line-height: 1.75;
	color: #607786;
}

.section-card {
	padding: 30rpx;
}

.section-head {
	align-items: flex-start;
}

.section-title {
	font-size: 30rpx;
	line-height: 1.3;
	font-weight: 800;
}

.section-subtitle {
	margin-top: 8rpx;
	font-size: 23rpx;
	line-height: 1.55;
}

.product-list,
.order-list {
	margin-top: 18rpx;
}

.product-item,
.order-item {
	gap: 20rpx;
	padding: 24rpx 0;
	border-bottom: 1rpx solid rgba(79, 147, 163, 0.12);
}

.product-item:first-child,
.order-item:first-child {
	padding-top: 8rpx;
}

.product-item:last-child,
.order-item:last-child {
	padding-bottom: 4rpx;
	border-bottom: 0;
}

.product-head {
	flex-wrap: wrap;
	gap: 10rpx;
}

.product-name {
	font-size: 29rpx;
	line-height: 1.35;
	font-weight: 800;
}

.tag-label {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	padding: 6rpx 12rpx;
	border-radius: 999rpx;
	font-size: 19rpx;
	font-weight: 700;
	color: #a24f72;
	background: rgba(255, 235, 244, 0.9);
	border: 1rpx solid rgba(182, 95, 131, 0.14);
}

.product-desc,
.product-bonus {
	margin-top: 8rpx;
	font-size: 23rpx;
	line-height: 1.55;
}

.product-bonus {
	color: #4f7f8e;
	font-weight: 600;
}

.product-side,
.order-side {
	gap: 12rpx;
}

.product-price,
.order-price {
	font-size: 32rpx;
	line-height: 1.2;
	font-weight: 800;
}

.buy-btn {
	min-width: 142rpx;
	height: 66rpx;
	padding: 0 22rpx;
	gap: 7rpx;
	border-radius: 999rpx;
	font-size: 23rpx;
	font-weight: 800;
	color: #fff;
	background: linear-gradient(135deg, #4f93a3 0%, #72bdc8 100%);
	box-shadow: 0 14rpx 28rpx rgba(79, 147, 163, 0.22);
}

.refresh-btn {
	flex-shrink: 0;
	gap: 7rpx;
	padding: 9rpx 16rpx;
	font-size: 21rpx;
	font-weight: 700;
	color: #4f7f8e;
	background: rgba(255, 255, 255, 0.58);
	border: 1rpx solid rgba(79, 147, 163, 0.15);
}

.order-name {
	font-size: 27rpx;
	line-height: 1.4;
	font-weight: 800;
}

.order-meta {
	margin-top: 7rpx;
	font-size: 21rpx;
	line-height: 1.5;
	word-break: break-all;
}

.order-status {
	font-size: 20rpx;
	font-weight: 700;
	padding: 7rpx 13rpx;
}

.order-status.paid {
	color: #28705f;
	background: rgba(218, 243, 235, 0.9);
}

.order-status.pending {
	color: #9a6b18;
	background: rgba(255, 245, 213, 0.94);
}

.note-card {
	padding: 24rpx 26rpx;
	background: linear-gradient(145deg, rgba(236, 249, 252, 0.68) 0%, rgba(255, 241, 247, 0.58) 100%);
}

.note-title {
	font-size: 25rpx;
	font-weight: 800;
}

.note-text {
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.7;
}

.empty-box {
	padding: 34rpx 18rpx 18rpx;
	text-align: center;
}

@media (max-width: 420px) {
	.hero-title {
		font-size: 33rpx;
	}

	.balance-item {
		padding: 20rpx;
	}

	.product-item,
	.order-item {
		gap: 14rpx;
	}

	.buy-btn {
		min-width: 126rpx;
		padding: 0 18rpx;
	}
}

@media (hover: hover) and (pointer: fine) {
	.hero-card,
	.balance-item,
	.section-card,
	.buy-btn,
	.refresh-btn {
		transition: transform 180ms ease, box-shadow 180ms ease;
	}

	.balance-item:hover,
	.section-card:hover,
	.buy-btn:hover,
	.refresh-btn:hover {
		transform: translateY(-2rpx);
	}
}

.order-head-actions {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	flex-wrap: wrap;
	gap: 10rpx;
}

.order-tool-btn {
	width: 54rpx;
	height: 54rpx;
	flex: 0 0 54rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1rpx solid rgba(79, 147, 163, 0.15);
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.64);
	box-shadow: 0 8rpx 18rpx rgba(67, 112, 142, 0.08), inset 0 1rpx 0 rgba(255, 255, 255, 0.86);
	transition: transform 160ms ease, background 160ms ease, box-shadow 160ms ease;
}

.order-list-scroll {
	width: 100%;
}

.order-list-scroll--limited {
	height: 520rpx;
}

.order-item {
	padding-left: 8rpx;
	padding-right: 8rpx;
	border-radius: 18rpx;
	transition: background 160ms ease, opacity 160ms ease;
}

@media (hover: hover) and (pointer: fine) {
	.order-tool-btn:hover {
		transform: translateY(-2rpx);
	}
}

.order-tool-btn:active {
	transform: scale(0.96);
}

.order-remove-btn {
	border-color: rgba(180, 83, 9, 0.18);
	background: rgba(255, 247, 237, 0.82);
}
</style>
