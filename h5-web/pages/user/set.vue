<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="pageCopy.title" mode="dark" @back="goBack" />
		<view class="body">
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
		persona: '酒馆 · 我的人设',
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
		persona: '酒館 · 我的人設',
		support: '聯絡客服',
		tickets: '我的工單',
		security: '帳號與安全',
		about: '關於我們',
		terms: '使用條款',
		privacy: '隱私協議',
		logout: '登出'
	},
	en: {
		title: 'More Settings',
		persona: 'Tavern · My Persona',
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

.sheet {
	background: rgba(255, 255, 255, 0.56);
	border-radius: 16rpx;
	overflow: hidden;
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.1);
	border: 1rpx solid rgba(255, 255, 255, 0.5);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.cell {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 29rpx;
	min-height: 107rpx;
	border-bottom: 1rpx solid rgba(88, 189, 210, 0.12);
}

.cell--last {
	border-bottom: none;
}

.cell-txt {
	font-size: 30rpx;
	color: #244b66;
}

.cell-txt--danger {
	color: #cf6b84;
}

</style>
