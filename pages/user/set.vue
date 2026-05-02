<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="pageCopy.title" mode="dark" @back="goBack" />
		<view class="body">
			<view class="intro-card">
				<text class="intro-title">{{ pageCopy.heading }}</text>
				<text class="intro-subtitle">{{ pageCopy.subtitle }}</text>
			</view>

			<view class="sheet">
				<view class="cell" @tap="util.urlTo('/pages/tavern/chatPersona')">
					<text class="cell-txt">{{ pageCopy.persona }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/supportCreate')">
					<text class="cell-txt">{{ pageCopy.support }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/supportTickets')">
					<text class="cell-txt">{{ pageCopy.tickets }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/numanquan')">
					<text class="cell-txt">{{ pageCopy.security }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/aboutmy')">
					<text class="cell-txt">{{ pageCopy.about }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28" label="v1.0.1" label-pos="left"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/tiaokuan/tiaokuan')">
					<text class="cell-txt">{{ pageCopy.terms }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/yinshi/yinshi')">
					<text class="cell-txt">{{ pageCopy.privacy }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell cell--last" @tap="outlogin">
					<text class="cell-txt cell-txt--danger">{{ pageCopy.logout }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '更多设置',
		heading: '把和聊天真正相关的选项留在这里',
		subtitle: '去掉重复和低价值入口后，设置页会更清爽，也更像正式产品。',
		persona: '角色 · 我的人设',
		support: '联系客服',
		tickets: '我的工单',
		security: '账号与安全',
		about: '关于我们',
		terms: '使用条款',
		privacy: '隐私协议',
		logout: '退出登录'
	},
	'zh-hk': {
		title: '更多設定',
		heading: '把和聊天真正相關的選項留在這裡',
		subtitle: '移除重複與低價值入口後，設定頁會更清爽，也更像正式產品。',
		persona: '酒館 · 我的人設',
		support: '聯絡客服',
		tickets: '我的工單',
		security: '帳號與安全',
		about: '關於我們',
		terms: '使用條款',
		privacy: '隱私協議',
		logout: '退出登入'
	},
	en: {
		title: 'More Settings',
		heading: 'Keep only the options that matter to actual usage',
		subtitle: 'Removing duplicate or low-value entries makes this page feel cleaner and more product-ready.',
		persona: 'Character · My Persona',
		support: 'Contact Support',
		tickets: 'My Tickets',
		security: 'Account & Security',
		about: 'About Us',
		terms: 'Terms of Use',
		privacy: 'Privacy Policy',
		logout: 'Log Out'
	}
};

export default {
	components: { TavernNavBar },
	computed: {
		pageCopy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		}
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		outlogin() {
			uni.clearStorageSync();
			this.$store.dispatch('userout');
			this.$socket.safeClose();
			uni.reLaunch({ url: '/pages/index/index' });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background: $tavern-page-bg;
	display: flex;
	flex-direction: column;
}

.body {
	flex: 1;
	padding: 24rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
}

.intro-card {
	margin-bottom: 24rpx;
	padding: 28rpx;
	border-radius: 20rpx;
	background: rgba(15, 23, 42, 0.72);
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.intro-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.intro-subtitle {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.sheet {
	background: $tavern-card-dark;
	border-radius: 16rpx;
	overflow: hidden;
	box-shadow: $tavern-card-shadow;
	border: 1rpx solid $tavern-border-on-dark;
}

.cell {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 29rpx;
	min-height: 107rpx;
	border-bottom: 1rpx solid $tavern-border-on-dark;
}

.cell--last {
	border-bottom: none;
}

.cell-txt {
	font-size: 30rpx;
	color: $tavern-text-on-dark;
}

.cell-txt--danger {
	color: #fda4af;
}
</style>
