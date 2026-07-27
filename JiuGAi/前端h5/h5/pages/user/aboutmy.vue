<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="关于四叶酒馆" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="brand-card">
				<image src="/static/logo.png" mode="aspectFill" class="logo"></image>
				<text class="name">四叶酒馆</text>
				<text class="ver">版本 V {{ versionName }}</text>
				<text class="desc">AI 角色扮演纯娱乐分享项目，当前非商用，不支持充值或在线支付。内容由模型生成，请理性体验并遵守内容合规要求。</text>
			</view>

			<view class="contact-card">
				<text class="contact-title">官方 QQ 群</text>
				<text class="contact-no">{{ contact.qqGroup }}</text>
				<image class="qr" :src="contact.qrImage" mode="widthFix"></image>
				<text class="contact-tip">体验协助、问题反馈、数据处理或模型 token 自愿资助都可以通过官方群联系。</text>
			</view>

			<view class="friend-card">
				<view class="friend-head">
					<text class="friend-title">友情链接</text>
					<text class="friend-subtitle">语音创作与开源社区</text>
				</view>
				<view v-for="item in friendLinks" :key="item.url" class="friend-row" @tap="openExternalLink(item.url)">
					<view class="friend-mark"><u-icon name="volume-up" color="#247494" size="28"></u-icon></view>
					<view class="friend-copy">
						<text class="friend-name">{{ item.name }}</text>
						<text class="friend-desc">{{ item.description }}</text>
					</view>
					<u-icon name="arrow-right" color="#8da5b2" size="26"></u-icon>
				</view>
				<text class="friend-note">第三方网站与本应用相互独立，使用音色或模型前请确认对应许可、署名和商用规则。</text>
			</view>

			<view class="link-row" @tap="openTerms">用户协议</view>
			<view class="link-row" @tap="openPrivacy">隐私政策</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const compliance = require('@/common/tavernCompliance.js');
const api = require('@/common/api.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			contact: compliance.getOfficialContact(),
			versionName: api.versionName || '1.0.0',
			friendLinks: [
				{ name: 'AivisHub', description: 'AivisSpeech 音色模型社区', url: 'https://hub.aivis-project.com/' },
				{ name: 'VOICEVOX', description: '开源日语语音合成项目', url: 'https://voicevox.hiroshiba.jp/' },
				{ name: 'COEIROINK', description: '面向创作的日语语音合成', url: 'https://coeiroink.com/' }
			]
		};
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		openTerms() {
			uni.navigateTo({ url: '/pages/user/tiaokuan/tiaokuan' });
		},
		openPrivacy() {
			uni.navigateTo({ url: '/pages/user/yinshi/yinshi' });
		},
		copyExternalLink(url) {
			uni.setClipboardData({
				data: url,
				success: () => uni.showToast({ title: '链接已复制，请在浏览器打开', icon: 'none' }),
				fail: () => uni.showToast({ title: '无法打开链接', icon: 'none' })
			});
		},
		openExternalLink(url) {
			const link = String(url || '').trim();
			if (!/^https:\/\//i.test(link)) return;
			/* #ifdef APP-PLUS */
			try {
				if (typeof plus !== 'undefined' && plus.runtime && typeof plus.runtime.openURL === 'function') {
					plus.runtime.openURL(link, () => this.copyExternalLink(link));
					return;
				}
			} catch (e) {
				this.copyExternalLink(link);
				return;
			}
			/* #endif */
			/* #ifdef H5 */
			if (typeof window !== 'undefined' && typeof window.open === 'function') {
				const opened = window.open(link, '_blank', 'noopener,noreferrer');
				if (opened) opened.opener = null;
				else this.copyExternalLink(link);
				return;
			}
			/* #endif */
			this.copyExternalLink(link);
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
		radial-gradient(circle at 18% 8%, rgba(99, 102, 241, 0.2), transparent 30%),
		linear-gradient(180deg, #10111c 0%, #151827 52%, #111827 100%);
}

.scroll {
	flex: 1;
	height: 0;
	padding: 28rpx 28rpx 0;
	box-sizing: border-box;
}

.brand-card,
.contact-card,
.friend-card,
.link-row {
	border-radius: 32rpx;
	background: rgba(255, 255, 255, 0.08);
	border: 1rpx solid rgba(255, 255, 255, 0.12);
	box-shadow: 0 22rpx 62rpx rgba(0, 0, 0, 0.18);
	backdrop-filter: blur(18rpx);
}

.brand-card,
.contact-card,
.friend-card {
	padding: 36rpx 30rpx;
	text-align: center;
}

.logo {
	width: 150rpx;
	height: 150rpx;
	border-radius: 36rpx;
}

.name {
	display: block;
	margin-top: 24rpx;
	font-size: 40rpx;
	font-weight: 800;
	color: #fff;
}

.ver {
	display: block;
	margin-top: 10rpx;
	font-size: 25rpx;
	color: rgba(226, 232, 240, 0.62);
}

.desc {
	display: block;
	margin-top: 18rpx;
	font-size: 25rpx;
	line-height: 1.75;
	color: rgba(226, 232, 240, 0.82);
}

.contact-card {
	margin-top: 20rpx;
}

.friend-card {
	margin-top: 20rpx;
	padding: 28rpx 30rpx 22rpx;
	text-align: left;
}

.friend-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 18rpx;
	padding-bottom: 14rpx;
}

.friend-title {
	font-size: 30rpx;
	font-weight: 800;
	color: #244b66;
}

.friend-subtitle {
	font-size: 21rpx;
	color: #8297a5;
}

.friend-row {
	min-height: 102rpx;
	display: flex;
	align-items: center;
	gap: 18rpx;
	border-top: 1rpx solid rgba(88, 154, 181, 0.13);
}

.friend-mark {
	width: 58rpx;
	height: 58rpx;
	flex: 0 0 58rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 50%;
	background: #e9f5f8;
}

.friend-copy {
	min-width: 0;
	flex: 1;
}

.friend-name,
.friend-desc {
	display: block;
}

.friend-name {
	font-size: 26rpx;
	font-weight: 700;
	color: #2a536b;
}

.friend-desc {
	margin-top: 5rpx;
	font-size: 21rpx;
	color: #7b909d;
}

.friend-note {
	display: block;
	padding-top: 12rpx;
	font-size: 20rpx;
	line-height: 1.55;
	color: #8b9da8;
}

.contact-title {
	display: block;
	font-size: 31rpx;
	font-weight: 800;
	color: #fff;
}

.contact-no {
	display: block;
	margin-top: 10rpx;
	font-size: 30rpx;
	font-weight: 700;
	color: #bfdbfe;
}

.qr {
	display: block;
	width: 430rpx;
	margin: 24rpx auto 0;
	border-radius: 28rpx;
}

.contact-tip {
	display: block;
	margin-top: 18rpx;
	font-size: 24rpx;
	line-height: 1.65;
	color: rgba(226, 232, 240, 0.72);
}

.link-row {
	margin-top: 18rpx;
	padding: 26rpx 30rpx;
	font-size: 28rpx;
	font-weight: 700;
	color: #e0e7ff;
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

.brand-card,
.contact-card,
.friend-card,
.link-row {
	background: rgba(255, 255, 255, 0.88);
	border-color: rgba(255, 255, 255, 0.92);
	box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
}

.logo {
	border-radius: 50%;
	border: 5rpx solid rgba(255, 255, 255, 0.96);
	box-shadow: 0 14rpx 30rpx rgba(67, 112, 142, 0.14);
}

.name,
.contact-title,
.link-row {
	color: #244b66;
}

.ver,
.desc,
.contact-tip {
	color: #687f92;
}

.contact-no {
	color: #247494;
}
</style>
