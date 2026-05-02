<template>
	<view class="page">
		<tavern-nav-bar title="用户协议" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="hero">
				<text class="eyebrow">AI Character Chat · 用户协议</text>
				<text class="title">使用前请确认你已满 18 周岁</text>
				<text class="subtitle">本项目为 AI 角色扮演纯娱乐分享项目，非商用，不支持充值或在线支付，请理性使用并遵守内容合规要求。</text>
			</view>

			<view class="section" v-for="item in sections" :key="item.title">
				<text class="section-title">{{ item.title }}</text>
				<text class="section-body">{{ item.body }}</text>
			</view>

			<view class="contact-card">
				<text class="contact-title">官方联系</text>
				<text class="contact-desc">QQ群：{{ contact.qqGroup }}</text>
				<image class="qr" :src="contact.qrImage" mode="widthFix"></image>
				<text class="contact-tip">如需体验协助、内容反馈或账号数据处理，可以通过官方群联系作者。</text>
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
			sections: compliance.getTermsSections(),
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
		radial-gradient(circle at 12% 8%, rgba(99, 102, 241, 0.2), transparent 30%),
		linear-gradient(180deg, #10111c 0%, #151827 52%, #111827 100%);
}

.scroll {
	flex: 1;
	height: 0;
	padding: 22rpx 26rpx 0;
	box-sizing: border-box;
}

.hero,
.section,
.contact-card {
	border-radius: 30rpx;
	background: rgba(255, 255, 255, 0.075);
	border: 1rpx solid rgba(255, 255, 255, 0.1);
	box-shadow: 0 22rpx 60rpx rgba(0, 0, 0, 0.18);
	backdrop-filter: blur(18rpx);
}

.hero {
	padding: 34rpx 30rpx;
	margin-bottom: 18rpx;
}

.eyebrow {
	font-size: 23rpx;
	color: #93c5fd;
	font-weight: 700;
}

.title {
	display: block;
	margin-top: 12rpx;
	font-size: 38rpx;
	line-height: 1.35;
	font-weight: 800;
	color: #fff;
}

.subtitle {
	display: block;
	margin-top: 14rpx;
	font-size: 25rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.82);
}

.section {
	padding: 28rpx;
	margin-bottom: 16rpx;
}

.section-title {
	display: block;
	font-size: 29rpx;
	font-weight: 800;
	color: #f8fafc;
}

.section-body {
	display: block;
	margin-top: 12rpx;
	font-size: 25rpx;
	line-height: 1.78;
	color: rgba(226, 232, 240, 0.84);
}

.contact-card {
	padding: 30rpx;
	text-align: center;
}

.contact-title {
	display: block;
	font-size: 30rpx;
	font-weight: 800;
	color: #fff;
}

.contact-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 26rpx;
	color: #c4b5fd;
}

.qr {
	display: block;
	width: 420rpx;
	margin: 24rpx auto 0;
	border-radius: 26rpx;
}

.contact-tip {
	display: block;
	margin-top: 18rpx;
	font-size: 23rpx;
	line-height: 1.65;
	color: rgba(226, 232, 240, 0.74);
}

.bottom-space {
	height: calc(40rpx + env(safe-area-inset-bottom));
}
</style>
