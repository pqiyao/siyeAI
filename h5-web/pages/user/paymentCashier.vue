<template>
	<view class="cashier-page">
		<web-view v-if="paymentUrl" :src="paymentUrl"></web-view>
		<view v-else class="cashier-error">
			<text class="cashier-error__title">支付页面无法打开</text>
			<text class="cashier-error__desc">支付链接已过期，请返回后重新发起支付。</text>
			<view class="cashier-error__button" @tap="goBack">返回订单</view>
		</view>
	</view>
</template>

<script>
const tavernApi = require('@/common/tavernApi.js');
const paymentCashierSession = require('@/common/paymentCashierSession.js');

const TERMINAL_FAILURE_STATUSES = ['CLOSED', 'FAILED', 'CANCELLED', 'EXPIRED'];

export default {
	data() {
		return {
			orderNo: '',
			paymentUrl: '',
			pollTimer: null,
			polling: false,
			pollAttempts: 0,
			pollErrors: 0,
			schemeBridgeTimer: null,
			pageVisible: false,
			settled: false
		};
	},
	onLoad(options) {
		this.orderNo = options && options.orderNo ? String(options.orderNo).trim() : '';
		const session = paymentCashierSession.load(this.orderNo);
		this.paymentUrl = session ? session.paymentUrl : '';
	},
	onReady() {
		/* #ifdef APP-PLUS */
		this.schemeBridgeTimer = setTimeout(() => this.bindExternalPaymentSchemes(), 500);
		/* #endif */
	},
	onShow() {
		this.pageVisible = true;
		if (this.orderNo && this.paymentUrl && !this.settled) this.scheduleOrderCheck(400);
	},
	onHide() {
		this.pageVisible = false;
		this.stopOrderChecks();
	},
	onUnload() {
		this.pageVisible = false;
		this.stopOrderChecks();
		if (this.schemeBridgeTimer) clearTimeout(this.schemeBridgeTimer);
		this.schemeBridgeTimer = null;
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.redirectTo({ url: '/pages/user/pay' }) });
		},
		stopOrderChecks() {
			if (this.pollTimer) clearTimeout(this.pollTimer);
			this.pollTimer = null;
		},
		bindExternalPaymentSchemes() {
			/* #ifdef APP-PLUS */
			try {
				const hostWebview = this.$scope && this.$scope.$getAppWebview ? this.$scope.$getAppWebview() : null;
				const children = hostWebview && typeof hostWebview.children === 'function' ? hostWebview.children() : [];
				const paymentWebview = children && children.length ? children[0] : null;
				if (!paymentWebview || typeof paymentWebview.overrideUrlLoading !== 'function') return;
				paymentWebview.overrideUrlLoading(
					{ mode: 'reject', match: '^(alipays|alipay|weixin)://.*' },
					(event) => {
						const targetUrl = event && event.url ? String(event.url) : '';
						if (!targetUrl) return;
						plus.runtime.openURL(targetUrl, () => {
							uni.showToast({ title: '未能打开支付应用', icon: 'none' });
						});
					}
				);
			} catch (e) {}
			/* #endif */
		},
		scheduleOrderCheck(delay) {
			this.stopOrderChecks();
			if (!this.pageVisible || this.settled || !this.orderNo) return;
			this.pollTimer = setTimeout(() => this.checkOrderStatus(), Math.max(0, Number(delay) || 0));
		},
		checkOrderStatus() {
			if (this.polling || this.settled || !this.pageVisible) return;
			this.polling = true;
			this.pollAttempts += 1;
			tavernApi
				.fetchStoreOrders(tavernApi.getClientUid(), 50)
				.then((orders) => {
					this.pollErrors = 0;
					const list = Array.isArray(orders) ? orders : [];
					const order = list.find((item) => item && String(item.orderNo || '') === this.orderNo);
					if (!order) return;
					const status = String(order.status || '').trim().toUpperCase();
					if (status === 'PAID') {
						this.handlePaid();
						return;
					}
					if (TERMINAL_FAILURE_STATUSES.includes(status)) this.handleTerminalFailure(order.statusLabel);
				})
				.catch(() => {
					this.pollErrors += 1;
				})
				.finally(() => {
					this.polling = false;
					if (this.settled || !this.pageVisible) return;
					const normalDelay = this.pollAttempts < 24 ? 2500 : 5000;
					this.scheduleOrderCheck(this.pollErrors >= 3 ? 8000 : normalDelay);
				});
		},
		handlePaid() {
			if (this.settled) return;
			this.settled = true;
			this.stopOrderChecks();
			paymentCashierSession.clear(this.orderNo);
			tavernApi.markCharacterAccessRefreshNeeded('app-payment-paid');
			uni.showToast({ title: '支付成功，权益已到账', icon: 'success', duration: 1400 });
			setTimeout(() => this.goBack(), 900);
		},
		handleTerminalFailure(statusLabel) {
			if (this.settled) return;
			this.settled = true;
			this.stopOrderChecks();
			paymentCashierSession.clear(this.orderNo);
			uni.showModal({
				title: '支付未完成',
				content: statusLabel ? `订单状态：${statusLabel}` : '当前订单已结束，请返回后重新下单。',
				showCancel: false,
				success: () => this.goBack()
			});
		}
	}
};
</script>

<style scoped>
.cashier-page {
	min-height: 100vh;
	background: #f5f7fa;
}

.cashier-error {
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 48rpx;
	box-sizing: border-box;
}

.cashier-error__title {
	font-size: 34rpx;
	font-weight: 700;
	color: #172033;
}

.cashier-error__desc {
	margin-top: 18rpx;
	font-size: 26rpx;
	line-height: 1.7;
	text-align: center;
	color: #667085;
}

.cashier-error__button {
	margin-top: 36rpx;
	padding: 20rpx 36rpx;
	border-radius: 8rpx;
	background: #247494;
	color: #ffffff;
	font-size: 28rpx;
}
</style>
