<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="summary-card">
				<text class="summary-title">{{ product.name || copy.productFallback }}</text>
				<text class="summary-price">楼{{ product.priceYuan || '--' }}</text>
				<text class="summary-subtitle">{{ product.subtitle || copy.summaryFallback }}</text>
			</view>

			<view class="channel-card">
				<text class="section-title">{{ copy.channelTitle }}</text>
				<view
					v-for="item in channels"
					:key="item.code"
					class="channel-item"
					:class="{ active: selectedChannel === item.code }"
					@tap="selectChannel(item.code)"
				>
					<view class="channel-main">
						<view class="channel-head">
							<text class="channel-name">{{ item.name }}</text>
							<text v-if="item.ready" class="channel-badge">{{ copy.channelReady }}</text>
						</view>
						<text class="channel-desc">{{ item.desc || copy.channelDescFallback }}</text>
					</view>
					<text class="channel-check">{{ selectedChannel === item.code ? copy.selected : copy.select }}</text>
				</view>
			</view>

			<view class="order-card">
				<text class="section-title">{{ copy.orderTitle }}</text>
				<view class="order-row">
					<text class="order-label">{{ copy.orderNo }}</text>
					<text class="order-value">{{ order.orderNo || '--' }}</text>
				</view>
				<view class="order-row">
					<text class="order-label">{{ copy.orderStatus }}</text>
					<text class="order-value">{{ order.statusLabel || copy.pending }}</text>
				</view>
				<view class="order-row">
					<text class="order-label">{{ copy.orderBenefit }}</text>
					<text class="order-value">{{ benefitText }}</text>
				</view>
			</view>

			<view class="profile-card">
				<text class="section-title">{{ copy.profileTitle }}</text>
				<view class="order-row">
					<text class="order-label">{{ copy.profileDiamond }}</text>
					<text class="order-value">{{ Number(profile.score || 0) }}</text>
				</view>
				<view class="order-row">
					<text class="order-label">{{ copy.profileCoin }}</text>
					<text class="order-value">{{ Number(profile.goldCoin || 0) }}</text>
				</view>
				<view class="order-row">
					<text class="order-label">{{ copy.profileVip }}</text>
					<text class="order-value">{{ profile.vipName || copy.defaultVip }}</text>
				</view>
			</view>

			<view v-if="paymentMessage" class="message-card">{{ paymentMessage }}</view>

			<view class="action-stack">
				<view class="primary-btn" @tap="payNow">{{ payButtonText }}</view>
				<view class="ghost-btn" @tap="rebuildOrder">{{ copy.rebuildOrder }}</view>
				<view class="ghost-btn ghost-btn--soft" @tap="openSupport">{{ supportButtonText }}</view>
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
const { getLanguageCode, getTavernUiText } = require('@/common/tavernUiI18n.js');
const { getProjectNoticeCopy } = require('@/common/tavernProjectNotice.js');

const COPY = {
	'zh-cn': {
		title: '支付确认',
		productFallback: '会员权益订单',
		summaryFallback: '确认下单后即可进入支付流程，支付成功后会自动发放权益。',
		channelTitle: '支付方式',
		channelReady: '可用',
		channelDescFallback: '选择后将按该渠道创建支付订单。',
		selected: '已选中',
		select: '选择',
		orderTitle: '订单信息',
		orderNo: '订单号',
		orderStatus: '状态',
		orderBenefit: '到账权益',
		profileTitle: '到账后将更新',
		profileDiamond: '当前钻石',
		profileCoin: '当前金币',
		profileVip: '当前 VIP',
		defaultVip: '普通用户',
		pending: '待创建',
		rebuildOrder: '重新创建订单',
		creating: '正在创建订单...',
		paying: '处理中...',
		openTelegram: '打开 Telegram 支付',
		mockPay: '确认模拟支付',
		payNow: '立即支付',
		benefitFallback: '订单生成后显示',
		vipEmpty: '无 VIP 时长',
		loadError: '加载支付页失败',
		productNotFound: '商品不存在',
		createOrderError: '创建订单失败',
		payError: '支付失败',
		mockPaySuccess: '模拟支付成功',
		tgPaid: '支付成功，权益将自动到账',
		tgStatusPrefix: '支付状态：',
		tgCopied: '支付链接已复制，请在 Telegram 中打开',
		tgOpenTip: '请在 Telegram 中打开支付链接'
	},
	'zh-hk': {
		title: '支付確認',
		productFallback: '會員權益訂單',
		summaryFallback: '確認下單後即可進入支付流程，支付成功後會自動發放權益。',
		channelTitle: '支付方式',
		channelReady: '可用',
		channelDescFallback: '選擇後將按該渠道建立支付訂單。',
		selected: '已選中',
		select: '選擇',
		orderTitle: '訂單信息',
		orderNo: '訂單號',
		orderStatus: '狀態',
		orderBenefit: '到帳權益',
		profileTitle: '到帳後將更新',
		profileDiamond: '目前鑽石',
		profileCoin: '目前金幣',
		profileVip: '目前 VIP',
		defaultVip: '普通用戶',
		pending: '待建立',
		rebuildOrder: '重新建立訂單',
		creating: '正在建立訂單...',
		paying: '處理中...',
		openTelegram: '打開 Telegram 支付',
		mockPay: '確認模擬支付',
		payNow: '立即支付',
		benefitFallback: '訂單建立後顯示',
		vipEmpty: '無 VIP 時長',
		loadError: '載入支付頁失敗',
		productNotFound: '商品不存在',
		createOrderError: '建立訂單失敗',
		payError: '支付失敗',
		mockPaySuccess: '模擬支付成功',
		tgPaid: '支付成功，權益將自動到帳',
		tgStatusPrefix: '支付狀態：',
		tgCopied: '支付連結已複製，請在 Telegram 中打開',
		tgOpenTip: '請在 Telegram 中打開支付連結'
	},
	en: {
		title: 'Payment',
		productFallback: 'Membership Order',
		summaryFallback: 'Confirm the order to enter payment. Benefits will be granted automatically after success.',
		channelTitle: 'Payment Channel',
		channelReady: 'Ready',
		channelDescFallback: 'The order will be created with this payment channel.',
		selected: 'Selected',
		select: 'Choose',
		orderTitle: 'Order Info',
		orderNo: 'Order No.',
		orderStatus: 'Status',
		orderBenefit: 'Benefits',
		profileTitle: 'After Payment',
		profileDiamond: 'Diamonds',
		profileCoin: 'Coins',
		profileVip: 'VIP',
		defaultVip: 'Standard User',
		pending: 'Pending',
		rebuildOrder: 'Rebuild Order',
		creating: 'Creating order...',
		paying: 'Processing...',
		openTelegram: 'Open Telegram Payment',
		mockPay: 'Confirm Mock Payment',
		payNow: 'Pay Now',
		benefitFallback: 'Shown after order creation',
		vipEmpty: 'No VIP duration',
		loadError: 'Failed to load payment page',
		productNotFound: 'Product not found',
		createOrderError: 'Failed to create order',
		payError: 'Payment failed',
		mockPaySuccess: 'Mock payment completed',
		tgPaid: 'Payment succeeded. Benefits will be applied shortly.',
		tgStatusPrefix: 'Payment status: ',
		tgCopied: 'Payment link copied. Open it in Telegram.',
		tgOpenTip: 'Open this payment link in Telegram.'
	},
	ko: {
		title: '결제 확인',
		productFallback: '멤버십 주문',
		summaryFallback: '주문을 확인하면 결제 단계로 이동합니다. 결제 성공 후 혜택이 자동 반영됩니다.',
		channelTitle: '결제 수단',
		channelReady: '사용 가능',
		channelDescFallback: '선택한 채널로 주문이 생성됩니다.',
		selected: '선택됨',
		select: '선택',
		orderTitle: '주문 정보',
		orderNo: '주문번호',
		orderStatus: '상태',
		orderBenefit: '지급 혜택',
		profileTitle: '결제 후 반영',
		profileDiamond: '현재 다이아',
		profileCoin: '현재 코인',
		profileVip: '현재 VIP',
		defaultVip: '일반 사용자',
		pending: '대기 중',
		rebuildOrder: '주문 다시 만들기',
		creating: '주문 생성 중...',
		paying: '처리 중...',
		openTelegram: 'Telegram 결제 열기',
		mockPay: '모의 결제 확인',
		payNow: '지금 결제',
		benefitFallback: '주문 생성 후 표시',
		vipEmpty: 'VIP 기간 없음',
		loadError: '결제 페이지를 불러오지 못했습니다',
		productNotFound: '상품을 찾을 수 없습니다',
		createOrderError: '주문 생성 실패',
		payError: '결제 실패',
		mockPaySuccess: '모의 결제가 완료되었습니다',
		tgPaid: '결제가 완료되었습니다. 혜택이 곧 반영됩니다.',
		tgStatusPrefix: '결제 상태: ',
		tgCopied: '결제 링크를 복사했습니다. Telegram에서 열어 주세요.',
		tgOpenTip: 'Telegram에서 결제 링크를 열어 주세요.'
	},
	ja: {
		title: '支払い確認',
		productFallback: '会員特典注文',
		summaryFallback: '注文を確定すると支払いフローへ進みます。支払い成功後に特典が自動反映されます。',
		channelTitle: '支払い方法',
		channelReady: '利用可能',
		channelDescFallback: '選択したチャネルで注文を作成します。',
		selected: '選択済み',
		select: '選択',
		orderTitle: '注文情報',
		orderNo: '注文番号',
		orderStatus: '状態',
		orderBenefit: '反映特典',
		profileTitle: '反映後の状態',
		profileDiamond: '現在のダイヤ',
		profileCoin: '現在のコイン',
		profileVip: '現在の VIP',
		defaultVip: '一般ユーザー',
		pending: '未作成',
		rebuildOrder: '注文を作り直す',
		creating: '注文を作成中...',
		paying: '処理中...',
		openTelegram: 'Telegram 支払いを開く',
		mockPay: 'モック支払いを確定',
		payNow: '今すぐ支払う',
		benefitFallback: '注文作成後に表示されます',
		vipEmpty: 'VIP 期間なし',
		loadError: '支払いページの読み込みに失敗しました',
		productNotFound: '商品が見つかりません',
		createOrderError: '注文の作成に失敗しました',
		payError: '支払いに失敗しました',
		mockPaySuccess: 'モック支払いが完了しました',
		tgPaid: '支払いが完了しました。特典が自動反映されます。',
		tgStatusPrefix: '支払い状況: ',
		tgCopied: '支払いリンクをコピーしました。Telegram で開いてください。',
		tgOpenTip: 'Telegram で支払いリンクを開いてください。'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			productCode: '',
			product: {},
			profile: {},
			order: {},
			channels: [],
			selectedChannel: '',
			paying: false,
			creating: false,
			lastPayment: null,
			profileAccessSignature: ''
		};
	},
	computed: {
		copy() {
			getTavernUiText('language');
			return COPY[getLanguageCode()] || COPY['zh-cn'];
		},
		projectNotice() {
			return getProjectNoticeCopy(getLanguageCode());
		},
		benefitText() {
			if (!this.order || !this.order.productName) return this.copy.benefitFallback;
			const vipText = Number(this.order.vipDays || 0) > 0 ? `+${Number(this.order.vipDays || 0)} VIP` : this.copy.vipEmpty;
			return `+${Number(this.order.scoreAmount || 0)} / +${Number(this.order.goldCoinAmount || 0)} / ${vipText}`;
		},
		paymentMessage() {
			return (this.lastPayment && this.lastPayment.message) || '';
		},
		payButtonText() {
			if (this.creating) return this.copy.creating;
			if (this.paying) return this.copy.paying;
			const action = this.lastPayment && this.lastPayment.action;
			if (action === 'open_invoice') return this.copy.openTelegram;
			if (action === 'open_external_url') return this.copy.payNow;
			if (action === 'mock_pay') return this.copy.mockPay;
			return this.copy.payNow;
		},
		supportButtonText() {
			getTavernUiText('language');
			const code = getLanguageCode();
			const map = {
				'zh-cn': '支付遇到问题',
				'zh-hk': '支付遇到問題',
				en: 'Need payment help?',
				ko: '결제에 문제가 있나요?',
				ja: '決済で困っていますか？'
			};
			return map[code] || map.en;
		}
	},
	onLoad(options) {
		this.productCode = options && options.productCode ? String(options.productCode) : '';
	},
	onShow() {
		this.initProjectShell();
	},
	methods: {
		initProjectShell() {
			this.channels = [];
			this.order = {};
			this.lastPayment = null;
		},
		openProjectContact() {
			uni.navigateTo({ url: '/pages/user/aboutmy' });
		},
		requireH5Login() {
			if (tavernApi.hasLoggedInUser()) {
				return true;
			}
			const redirect = '/pages/user/pay?productCode=' + encodeURIComponent(this.productCode || '');
			uni.showModal({
				title: '请先登录',
				content: '充值和开通会员需要账号，用于保存订单和权益。',
				confirmText: '去登录',
				cancelText: '稍后',
				success: (res) => {
					if (res.confirm) {
						uni.navigateTo({ url: tavernApi.buildLoginUrl(redirect) });
					}
				}
			});
			return false;
		},
		loadPage() {
			const clientUid = tavernApi.getClientUid();
			tavernApi
				.fetchStoreOverview(clientUid)
				.then((res) => {
					const nextProfile = (res && res.profile) || {};
					const nextAccessSignature = tavernApi.getProfileAccessSignature(nextProfile);
					if (this.profileAccessSignature && this.profileAccessSignature !== nextAccessSignature) {
						tavernApi.markCharacterAccessRefreshNeeded('store-overview');
					}
					this.profileAccessSignature = nextAccessSignature;
					this.profile = nextProfile;
					this.channels = Array.isArray(res && res.channels) ? res.channels : [];
					const coin = (res && res.products && res.products.coin) || [];
					const vip = (res && res.products && res.products.vip) || [];
					const allProducts = coin.concat(vip);
					this.product = allProducts.find((item) => item.code === this.productCode) || {};
					if (!this.product.code) {
						uni.showToast({ title: this.copy.productNotFound, icon: 'none' });
						return;
					}
					this.selectedChannel = this.pickDefaultChannel();
					if (tavernApi.hasLoggedInUser() && !this.order.orderNo) {
						this.createOrder();
					}
				})
				.catch((e) => {
					uni.showToast({ title: (e && e.message) || this.copy.loadError, icon: 'none' });
				});
		},
		pickDefaultChannel() {
			if (this.channels.find((item) => item.code === this.selectedChannel)) {
				return this.selectedChannel;
			}
			const ready = this.channels.find((item) => item.ready && item.enabled !== false);
			if (ready) return ready.code;
			const enabled = this.channels.find((item) => item.enabled !== false);
			if (enabled) return enabled.code;
			return this.channels.length ? this.channels[0].code : 'mock_wechat';
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		selectChannel(code) {
			if (!code || this.selectedChannel === code) return;
			this.selectedChannel = code;
			this.order = {};
			this.lastPayment = null;
			if (!tavernApi.hasLoggedInUser()) return;
			this.createOrder();
		},
		createOrder() {
			if (!this.requireH5Login()) return;
			if (this.creating || !this.productCode || !this.selectedChannel) return;
			this.creating = true;
			tavernApi
				.postStoreOrderCreate({
					clientUid: tavernApi.getClientUid(),
					productCode: this.productCode,
					paymentChannel: this.selectedChannel
				})
				.then((res) => {
					this.order = (res && res.order) || {};
					this.lastPayment = (res && res.payment) || null;
					this.channels = (res && res.channels) || this.channels;
				})
				.catch((e) => {
					this.order = {};
					this.lastPayment = null;
					uni.showToast({ title: (e && e.message) || this.copy.createOrderError, icon: 'none' });
				})
				.finally(() => {
					this.creating = false;
				});
		},
		rebuildOrder() {
			if (!this.requireH5Login()) return;
			this.order = {};
			this.lastPayment = null;
			this.createOrder();
		},
		openSupport() {
			const subject = encodeURIComponent((this.product && this.product.name) || this.copy.productFallback);
			const orderNo = this.order && this.order.orderNo ? this.order.orderNo : '';
			const url =
				'/pages/user/supportCreate?ticketType=PAYMENT&subject=' +
				subject +
				(orderNo ? '&orderNo=' + encodeURIComponent(orderNo) : '');
			uni.navigateTo({ url });
		},
		payNow() {
			if (!this.requireH5Login()) return;
			if (this.paying) return;
			if (!this.order.orderNo) {
				this.createOrder();
				return;
			}
			this.paying = true;
			tavernApi
				.postStoreOrderPay({
					clientUid: tavernApi.getClientUid(),
					orderNo: this.order.orderNo
				})
				.then((res) => {
					this.order = (res && res.order) || this.order;
					this.lastPayment = (res && res.payment) || this.lastPayment;
					return this.dispatchPaymentAction(this.lastPayment);
				})
				.catch((e) => {
					uni.showToast({ title: (e && e.message) || this.copy.payError, icon: 'none' });
				})
				.finally(() => {
					this.paying = false;
				});
		},
		dispatchPaymentAction(payment) {
			const action = payment && payment.action;
			if (action === 'mock_pay') {
				return this.finishMockPay();
			}
			if (action === 'open_invoice' && payment && payment.invoiceLink) {
				return this.openTelegramInvoice(payment.invoiceLink);
			}
			if (action === 'open_external_url' && payment && payment.paymentUrl) {
				return this.openExternalPayment(payment.paymentUrl);
			}
			if (payment && payment.message) {
				uni.showToast({ title: payment.message, icon: 'none' });
			}
			return Promise.resolve();
		},
		finishMockPay() {
			return tavernApi
				.postStoreOrderMockPay({
					clientUid: tavernApi.getClientUid(),
					orderNo: this.order.orderNo
				})
				.then((res) => {
					this.order = (res && res.order) || this.order;
					const nextProfile = (res && res.profile) || this.profile;
					const nextAccessSignature = tavernApi.getProfileAccessSignature(nextProfile);
					if (this.profileAccessSignature !== nextAccessSignature) {
						tavernApi.markCharacterAccessRefreshNeeded('mock-pay');
					}
					this.profileAccessSignature = nextAccessSignature;
					this.profile = nextProfile;
					this.lastPayment = (res && res.payment) || this.lastPayment;
					uni.showToast({ title: this.copy.mockPaySuccess, icon: 'none' });
				});
		},
		openTelegramInvoice(invoiceLink) {
			return new Promise((resolve) => {
				/* #ifdef H5 */
				try {
					const tg = typeof window !== 'undefined' && window.Telegram ? window.Telegram.WebApp : null;
					if (tg && typeof tg.openInvoice === 'function') {
						tg.openInvoice(invoiceLink, (status) => {
							if (status === 'paid') {
								uni.showToast({ title: this.copy.tgPaid, icon: 'none' });
								this.loadPage();
							} else if (status) {
								uni.showToast({ title: this.copy.tgStatusPrefix + status, icon: 'none' });
							}
							resolve();
						});
						return;
					}
					if (typeof window !== 'undefined') {
						window.location.href = invoiceLink;
						resolve();
						return;
					}
				} catch (e) {}
				/* #endif */
				uni.setClipboardData({
					data: invoiceLink,
					success: () => {
						uni.showToast({ title: this.copy.tgCopied, icon: 'none' });
						resolve();
					},
					fail: () => {
						uni.showToast({ title: this.copy.tgOpenTip, icon: 'none' });
						resolve();
					}
				});
			});
		},
		openExternalPayment(paymentUrl) {
			return new Promise((resolve) => {
				/* #ifdef H5 */
				try {
					if (typeof window !== 'undefined') {
						window.location.href = paymentUrl;
						resolve();
						return;
					}
				} catch (e) {}
				/* #endif */
				uni.setClipboardData({
					data: paymentUrl,
					success: () => {
						uni.showToast({ title: this.copy.tgCopied, icon: 'none' });
						resolve();
					},
					fail: () => {
						uni.showToast({ title: this.copy.tgOpenTip, icon: 'none' });
						resolve();
					}
				});
			});
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background: $tavern-page-bg;
}

.scroll {
	height: calc(100vh - 88rpx);
	padding: 24rpx;
	box-sizing: border-box;
}

.summary-card,
.channel-card,
.order-card,
.profile-card,
.message-card {
	background: $tavern-card-dark;
	border-radius: 24rpx;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
	padding: 28rpx;
	margin-bottom: 20rpx;
}

.summary-card {
	background: linear-gradient(135deg, rgba(56, 189, 248, 0.18) 0%, rgba(168, 85, 247, 0.18) 100%);
}

.summary-title,
.summary-price,
.section-title,
.channel-name,
.order-value,
.message-card {
	color: $tavern-text-on-dark;
}

.summary-title {
	display: block;
	font-size: 36rpx;
	font-weight: 700;
}

.summary-price {
	display: block;
	margin-top: 14rpx;
	font-size: 42rpx;
	font-weight: 700;
}

.summary-subtitle,
.channel-desc,
.order-label {
	display: block;
	margin-top: 12rpx;
	font-size: 26rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.section-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	margin-bottom: 18rpx;
}

.channel-item,
.order-row,
.action-stack {
	display: flex;
}

.channel-item {
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	padding: 20rpx 0;
	border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
}

.channel-item:last-child {
	border-bottom: none;
}

.channel-item.active .channel-check {
	color: #f5d0fe;
	background: rgba(168, 85, 247, 0.22);
}

.channel-head {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.channel-badge {
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	color: #fff;
	background: rgba(16, 185, 129, 0.22);
	border: 1rpx solid rgba(16, 185, 129, 0.34);
}

.channel-main {
	flex: 1;
	min-width: 0;
}

.channel-name {
	font-size: 28rpx;
	font-weight: 700;
}

.channel-check {
	padding: 8rpx 16rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: $tavern-muted-on-dark;
	background: rgba(148, 163, 184, 0.16);
}

.order-row {
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	padding: 12rpx 0;
}

.order-value {
	font-size: 26rpx;
	text-align: right;
}

.message-card {
	font-size: 25rpx;
	line-height: 1.7;
	background: rgba(168, 85, 247, 0.12);
	border-color: rgba(196, 181, 253, 0.2);
}

.action-stack {
	flex-direction: column;
	gap: 14rpx;
}

.primary-btn,
.ghost-btn {
	height: 84rpx;
	line-height: 84rpx;
	text-align: center;
	border-radius: 999rpx;
	font-size: 28rpx;
	font-weight: 600;
}

.primary-btn {
	color: #fff;
	background: $tavern-accent-gradient;
}

.ghost-btn {
	color: #c4b5fd;
	background: rgba(91, 33, 182, 0.16);
	border: 1rpx solid rgba(196, 181, 253, 0.26);
}

.ghost-btn--soft {
	color: #fce7f3;
	background: rgba(236, 72, 153, 0.14);
	border-color: rgba(244, 114, 182, 0.24);
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

/* Light clover tavern pay refresh. */
.summary-card,
.channel-card,
.order-card,
.profile-card,
.message-card {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.summary-card {
	background: linear-gradient(135deg, rgba(220, 247, 251, 0.74), rgba(255, 235, 243, 0.56));
}

.summary-title,
.summary-price,
.section-title,
.channel-name,
.order-value,
.message-card {
	color: #244b66;
}

.summary-subtitle,
.channel-desc,
.order-label {
	color: #687f92;
}

.channel-item,
.ghost-btn,
.message-card {
	background: rgba(255, 255, 255, 0.38);
	border-color: rgba(255, 255, 255, 0.46);
}

.channel-item.active .channel-check,
.primary-btn,
.project-notice-btn--primary {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
	color: #fff;
}

.channel-check,
.ghost-btn,
.ghost-btn--soft {
	color: #247494;
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

<style lang="scss">
page {
	background: $tavern-page-bg;
}
</style>
