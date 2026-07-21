<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="隐私政策" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="hero">
				<text class="eyebrow">四叶酒馆 · 隐私政策</text>
				<text class="title">我们只收集提供体验所需的信息</text>
				<text class="subtitle">本项目为纯娱乐分享、非商用、不支持充值。请在使用前了解账号、匿名设备、角色卡、聊天与上传内容的处理方式。</text>
			</view>

			<view class="section" v-for="item in sections" :key="item.title">
				<text class="section-title">{{ item.title }}</text>
				<text class="section-body">{{ item.body }}</text>
			</view>

			<view class="contact-card">
				<text class="contact-title">隐私与数据处理联系</text>
				<text class="contact-desc">官方 QQ 群：{{ contact.qqGroup }}</text>
				<image class="qr" :src="contact.qrImage" mode="widthFix"></image>
				<text class="contact-tip">如需删除账号、处理个人数据或反馈内容合规问题，可以通过官方群联系作者。</text>
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
			sections: compliance.getPrivacySections(),
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
		radial-gradient(circle at 80% 6%, rgba(14, 165, 233, 0.18), transparent 32%),
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
	color: #67e8f9;
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
	color: #a5f3fc;
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

/* Light clover tavern refresh. */
.page {
	background:
		radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
		radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
}

.hero,
.section,
.contact-card {
	background: rgba(255, 255, 255, 0.88);
	border-color: rgba(255, 255, 255, 0.92);
	box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
}

.eyebrow,
.contact-desc {
	color: #247494;
}

.title,
.section-title,
.contact-title {
	color: #244b66;
}

.subtitle,
.section-body,
.contact-tip {
	color: #687f92;
}
</style>
