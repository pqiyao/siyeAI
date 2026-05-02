<template>
	<view class="page">
		<view class="card">
			<image class="logo" src="/static/logo.png" mode="aspectFill"></image>
			<text class="title">访问已暂停</text>
			<text class="desc">
				本项目为 AI 角色扮演纯娱乐分享项目，非商用，不支持充值或在线支付。继续访问前，需要确认已满 18 周岁并同意用户协议与隐私政策。
			</text>
			<view class="notice">
				<text>如果你是未成年人，或不同意“纯娱乐、非商用、不支持充值”的项目说明及相关条款，请停止访问本项目。</text>
			</view>
			<view class="btn btn--primary" @tap="confirmAge">我已满18岁并同意继续</view>
			<view class="btn btn--ghost" @tap="openTerms">查看用户协议</view>
			<view class="contact">
				<text>联系 QQ：{{ contact.qqGroup }}</text>
				<image class="qr" :src="contact.qrImage" mode="widthFix"></image>
			</view>
		</view>
	</view>
</template>

<script>
const compliance = require('@/common/tavernCompliance.js');

export default {
	data() {
		return {
			contact: compliance.getOfficialContact()
		};
	},
	methods: {
		confirmAge() {
			compliance.markAgeConfirmed();
			uni.reLaunch({ url: '/pages/index/index' });
		},
		openTerms() {
			uni.navigateTo({ url: '/pages/user/tiaokuan/tiaokuan' });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	padding: calc(70rpx + env(safe-area-inset-top)) 34rpx 48rpx;
	box-sizing: border-box;
	background:
		radial-gradient(circle at top left, rgba(99, 102, 241, 0.24), transparent 34%),
		radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.18), transparent 32%),
		linear-gradient(180deg, #0f172a 0%, #111827 48%, #1e1b4b 100%);
}

.card {
	min-height: calc(100vh - 140rpx);
	padding: 52rpx 34rpx 42rpx;
	border-radius: 38rpx;
	background: rgba(15, 23, 42, 0.78);
	border: 1rpx solid rgba(148, 163, 184, 0.18);
	box-shadow: 0 34rpx 90rpx rgba(0, 0, 0, 0.32);
	display: flex;
	flex-direction: column;
	align-items: center;
	text-align: center;
	backdrop-filter: blur(22rpx);
}

.logo {
	width: 150rpx;
	height: 150rpx;
	border-radius: 34rpx;
	box-shadow: 0 20rpx 60rpx rgba(15, 23, 42, 0.42);
}

.title {
	margin-top: 34rpx;
	font-size: 44rpx;
	font-weight: 800;
	color: #fff;
}

.desc {
	margin-top: 20rpx;
	font-size: 28rpx;
	line-height: 1.8;
	color: rgba(226, 232, 240, 0.9);
}

.notice {
	width: 100%;
	margin: 34rpx 0 28rpx;
	padding: 24rpx;
	box-sizing: border-box;
	border-radius: 26rpx;
	background: rgba(245, 158, 11, 0.14);
	border: 1rpx solid rgba(251, 191, 36, 0.28);
	font-size: 25rpx;
	line-height: 1.7;
	color: #fde68a;
}

.btn {
	width: 100%;
	height: 88rpx;
	line-height: 88rpx;
	border-radius: 999rpx;
	font-size: 28rpx;
	font-weight: 700;
	margin-top: 18rpx;
}

.btn--primary {
	color: #fff;
	background: linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%);
	box-shadow: 0 18rpx 44rpx rgba(79, 70, 229, 0.32);
}

.btn--ghost {
	color: #c4b5fd;
	background: rgba(255, 255, 255, 0.06);
	border: 1rpx solid rgba(196, 181, 253, 0.22);
}

.contact {
	margin-top: 34rpx;
	width: 100%;
	padding: 24rpx;
	box-sizing: border-box;
	border-radius: 28rpx;
	background: rgba(255, 255, 255, 0.07);
	color: rgba(226, 232, 240, 0.9);
	font-size: 26rpx;
}

.qr {
	display: block;
	width: 360rpx;
	margin: 22rpx auto 0;
	border-radius: 24rpx;
}
</style>
