<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="联系我们" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="contact-card">
				<image class="logo" src="/static/logo.png" mode="aspectFill"></image>
				<text class="title">四叶酒馆官方群</text>
				<text class="group-no">QQ群：{{ contact.qqGroup }}</text>
				<text class="desc">
					用于体验协助、问题反馈、账号数据处理、内容合规反馈和模型 token 自愿资助沟通。
				</text>
				<image class="qr" :src="contact.qrImage" mode="widthFix"></image>
				<text class="tip">长按或截图识别二维码加入群聊。</text>
			</view>

			<view class="notice">
				<text class="notice-title">温馨说明</text>
				<text class="notice-body">
					本项目为纯娱乐分享项目，当前非商用，不支持充值、在线支付或付费开通。若你自愿资助，相关费用会全部用于补充模型 token 和维持体验服务。
				</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const compliance = require('@/common/tavernCompliance.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			contact: compliance.getOfficialContact()
		};
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	height: 100vh;
	display: flex;
	flex-direction: column;
	background:
		radial-gradient(circle at 18% 8%, rgba(129, 140, 248, 0.2), transparent 34%),
		radial-gradient(circle at 86% 80%, rgba(34, 211, 238, 0.13), transparent 30%),
		linear-gradient(180deg, #10111c 0%, #151827 52%, #111827 100%);
}

.scroll {
	flex: 1;
	height: 0;
	padding: 28rpx 28rpx 0;
	box-sizing: border-box;
}

.contact-card,
.notice {
	border-radius: 34rpx;
	background: rgba(255, 255, 255, 0.08);
	border: 1rpx solid rgba(255, 255, 255, 0.12);
	box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.22);
	backdrop-filter: blur(20rpx);
}

.contact-card {
	padding: 40rpx 30rpx 34rpx;
	text-align: center;
}

.logo {
	width: 132rpx;
	height: 132rpx;
	border-radius: 32rpx;
	box-shadow: 0 18rpx 54rpx rgba(0, 0, 0, 0.28);
}

.title {
	display: block;
	margin-top: 22rpx;
	font-size: 38rpx;
	font-weight: 800;
	color: #fff;
}

.group-no {
	display: block;
	margin-top: 10rpx;
	font-size: 30rpx;
	font-weight: 700;
	color: #bfdbfe;
}

.desc {
	display: block;
	margin-top: 18rpx;
	font-size: 25rpx;
	line-height: 1.75;
	color: rgba(226, 232, 240, 0.82);
}

.qr {
	display: block;
	width: 460rpx;
	margin: 30rpx auto 0;
	border-radius: 30rpx;
	box-shadow: 0 18rpx 50rpx rgba(0, 0, 0, 0.22);
}

.tip {
	display: block;
	margin-top: 20rpx;
	font-size: 24rpx;
	color: rgba(226, 232, 240, 0.68);
}

.notice {
	margin-top: 20rpx;
	padding: 28rpx;
}

.notice-title {
	display: block;
	font-size: 29rpx;
	font-weight: 800;
	color: #fef3c7;
}

.notice-body {
	display: block;
	margin-top: 12rpx;
	font-size: 25rpx;
	line-height: 1.75;
	color: rgba(254, 243, 199, 0.86);
}

.bottom-space {
	height: calc(40rpx + env(safe-area-inset-bottom));
}

/* Light clover tavern refresh. */
.page {
	background:
		radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
		radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
}

.contact-card,
.notice {
	background: rgba(255, 255, 255, 0.88);
	border-color: rgba(255, 255, 255, 0.92);
	box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
}

.logo,
.qr {
	box-shadow: 0 14rpx 30rpx rgba(67, 112, 142, 0.14);
}

.logo {
	border-radius: 50%;
	border: 5rpx solid rgba(255, 255, 255, 0.96);
}

.title,
.notice-title {
	color: #244b66;
}

.group-no {
	color: #247494;
}

.desc,
.tip,
.notice-body {
	color: #687f92;
}
</style>
