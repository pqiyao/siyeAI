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
				<view class="cell" @tap="openChatAppearance">
					<text class="cell-txt">聊天显示与气泡</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view v-if="showUserVoiceEntry" class="cell" @tap="openUserVoices">
					<text class="cell-txt">{{ pageCopy.userVoices }}</text>
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
				<view v-if="androidApp" class="cell" @tap="checkUpdate">
					<text class="cell-txt">{{ checkingUpdate ? pageCopy.checkingUpdate : pageCopy.checkUpdate }}</text>
					<view class="cell-side"><text class="cell-version">V{{ installedVersion }}</text><u-icon name="reload" color="#6f9eaa" size="25"></u-icon></view>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/aboutmy')">
					<text class="cell-txt">{{ pageCopy.about }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" @tap="util.urlTo('/pages/user/tiaokuan/tiaokuan')">
					<text class="cell-txt">{{ pageCopy.terms }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell" :class="{ 'cell--last': !hasLogin }" @tap="util.urlTo('/pages/user/yinshi/yinshi')">
					<text class="cell-txt">{{ pageCopy.privacy }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view v-if="hasLogin" class="cell cell--last" @tap="outlogin">
					<text class="cell-txt cell-txt--danger">{{ pageCopy.logout }}</text>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
			</view>

		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');
const appUpdate = require('@/common/appUpdate.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '更多设置',
		persona: '酒馆 · 我的人设',
		userVoices: '自建音色',
		support: '联系客服',
		tickets: '我的工单',
		security: '账号与安全',
		about: '关于我们',
		terms: '使用条款',
		privacy: '隐私协议',
		checkUpdate: '检查更新',
		checkingUpdate: '正在检查更新',
		logout: '退出登录'
	},
	'zh-hk': {
		title: '更多設定',
		persona: '酒館 · 我的人設',
		userVoices: '自建音色',
		support: '聯絡客服',
		tickets: '我的工單',
		security: '帳號與安全',
		about: '關於我們',
		terms: '使用條款',
		privacy: '隱私協議',
		checkUpdate: '檢查更新',
		checkingUpdate: '正在檢查更新',
		logout: '登出'
	},
	en: {
		title: 'More Settings',
		persona: 'Tavern · My Persona',
		userVoices: 'Custom Voices',
		support: 'Contact Support',
		tickets: 'My Tickets',
		security: 'Account & Security',
		about: 'About Us',
		terms: 'Terms of Use',
		privacy: 'Privacy Policy',
		checkUpdate: 'Check for Updates',
		checkingUpdate: 'Checking for Updates',
		logout: 'Log Out'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			voiceFeatureEnabled: true,
			androidApp: appUpdate.isAndroidApp(),
			installedVersion: '1.3.6',
			checkingUpdate: false
		};
	},
	onShow() {
		this.loadRuntimeSettings();
		this.loadInstalledVersion();
	},
	computed: {
		pageCopy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		hasLogin() {
			const stateUser = this.$store && this.$store.state ? this.$store.state.user : null;
			let storedUser = {};
			try {
				storedUser = uni.getStorageSync('user') || {};
			} catch (e) {
				console.error('[settings] load stored user failed', e);
			}
			const user = stateUser && typeof stateUser === 'object' && stateUser.token ? stateUser : storedUser;
			return !!(user && user.token);
		},
		showUserVoiceEntry() {
			return this.hasLogin && this.voiceFeatureEnabled !== false;
		}
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		openChatAppearance() {
			const url = '/pages/user/chatAppearanceSetting';
			uni.navigateTo({
				url,
				fail: (error) => {
					console.error('[chat-appearance] navigateTo failed', error);
					uni.redirectTo({
						url,
						fail: (fallbackError) => {
							console.error('[chat-appearance] redirectTo failed', fallbackError);
							uni.showToast({ title: '页面打开失败，请更新后重试', icon: 'none' });
						}
					});
				}
			});
		},
		openUserVoices() {
			this.util.urlTo('/pages/user/myVoices');
		},
		loadRuntimeSettings() {
			tavernApi.fetchAppRuntimeConfig(true).then((config) => {
				this.voiceFeatureEnabled = !(config && config.voiceFeatureEnabled === false);
			}).catch((error) => {
				console.error('[settings] load runtime settings failed', error);
			});
		},
		loadInstalledVersion() {
			if (!this.androidApp) return;
			appUpdate.readInstalledInfo().then((info) => {
				this.installedVersion = info.versionName || this.installedVersion;
			}).catch(() => {});
		},
		checkUpdate() {
			if (!this.androidApp || this.checkingUpdate) return;
			this.checkingUpdate = true;
			appUpdate.checkNow().catch(() => {}).finally(() => { this.checkingUpdate = false; });
		},
		outlogin() {
			if (!this.hasLogin) {
				uni.switchTab({ url: '/pages/index/index', fail: () => uni.reLaunch({ url: '/pages/index/index' }) });
				return;
			}
			this.$store.commit('clearAuth');
			tavernApi.markCharacterAccessRefreshNeeded('logout');
			if (this.$socket && typeof this.$socket.safeClose === 'function') {
				this.$socket.safeClose();
			}
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

.cell-side {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.cell-version {
	font-size: 22rpx;
	color: #718a96;
}

</style>
