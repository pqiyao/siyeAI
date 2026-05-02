<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<view class="body">
			<view class="hero-card">
				<text class="hero-title">{{ copy.heroTitle }}</text>
				<text class="hero-subtitle">{{ copy.heroSubtitle }}</text>
			</view>

			<view class="sheet">
				<view class="cell" @tap="util.urlTo('/pages/user/editpwd')">
					<view class="cell-main">
						<text class="cell-txt">{{ copy.changePassword }}</text>
						<text class="cell-sub">{{ copy.changePasswordDesc }}</text>
					</view>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
				<view class="cell cell--last" @tap="show = true">
					<view class="cell-main">
						<text class="cell-txt cell-txt--danger">{{ copy.deleteAccount }}</text>
						<text class="cell-sub">{{ copy.deleteAccountDesc }}</text>
					</view>
					<u-icon name="arrow-right" color="#94a3b8" size="28"></u-icon>
				</view>
			</view>
		</view>

		<u-modal
			v-model="show"
			:content="copy.confirmText"
			:show-cancel-button="true"
			@confirm="confirm"
		></u-modal>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '账号与安全',
		heroTitle: '把真正高风险的操作集中放在这里',
		heroSubtitle: '修改密码和注销账号都会影响后续登录、订单和客服处理，请谨慎操作。',
		changePassword: '修改密码',
		changePasswordDesc: '更新你的账号密码，建议定期更换。',
		deleteAccount: '注销账号',
		deleteAccountDesc: '永久删除当前账号及关联数据，请确认后再执行。',
		confirmText: '注销后将无法恢复当前账号、订单和关联记录，确定继续吗？'
	},
	'zh-hk': {
		title: '帳號與安全',
		heroTitle: '把真正高風險的操作集中放在這裡',
		heroSubtitle: '修改密碼與註銷帳號都會影響後續登入、訂單與客服處理，請謹慎操作。',
		changePassword: '修改密碼',
		changePasswordDesc: '更新你的帳號密碼，建議定期更換。',
		deleteAccount: '註銷帳號',
		deleteAccountDesc: '永久刪除目前帳號及關聯資料，請確認後再執行。',
		confirmText: '註銷後將無法恢復目前帳號、訂單與關聯記錄，確定繼續嗎？'
	},
	en: {
		title: 'Account & Security',
		heroTitle: 'Keep the highest-risk actions in one safe place',
		heroSubtitle: 'Changing your password or deleting the account affects future login, orders, and support handling.',
		changePassword: 'Change Password',
		changePasswordDesc: 'Update your account password and rotate it regularly.',
		deleteAccount: 'Delete Account',
		deleteAccountDesc: 'Permanently remove the current account and linked data.',
		confirmText: 'This will permanently remove the current account, orders, and related records. Continue?'
	},
	ko: {
		title: 'Account & Security',
		heroTitle: 'Keep the highest-risk actions in one safe place',
		heroSubtitle: 'Changing your password or deleting the account affects future login, orders, and support handling.',
		changePassword: 'Change Password',
		changePasswordDesc: 'Update your account password and rotate it regularly.',
		deleteAccount: 'Delete Account',
		deleteAccountDesc: 'Permanently remove the current account and linked data.',
		confirmText: 'This will permanently remove the current account, orders, and related records. Continue?'
	},
	ja: {
		title: 'Account & Security',
		heroTitle: 'Keep the highest-risk actions in one safe place',
		heroSubtitle: 'Changing your password or deleting the account affects future login, orders, and support handling.',
		changePassword: 'Change Password',
		changePasswordDesc: 'Update your account password and rotate it regularly.',
		deleteAccount: 'Delete Account',
		deleteAccountDesc: 'Permanently remove the current account and linked data.',
		confirmText: 'This will permanently remove the current account, orders, and related records. Continue?'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			show: false
		};
	},
	computed: {
		copy() {
			return COPY[getLanguageCode()] || COPY.en;
		}
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		confirm() {
			this.util
				.request('index/forever_exit', {
					token: uni.getStorageSync('user').token
				})
				.then(() => {
					uni.clearStorageSync();
					setTimeout(() => {
						uni.reLaunch({
							url: '/pages/login/login'
						});
					}, 600);
				});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background:
		radial-gradient(circle at top left, rgba(99, 102, 241, 0.14), transparent 30%),
		radial-gradient(circle at bottom right, rgba(244, 63, 94, 0.10), transparent 28%),
		$tavern-page-bg;
	display: flex;
	flex-direction: column;
}

.body {
	flex: 1;
	padding: 24rpx 24rpx 48rpx;
}

.hero-card,
.sheet {
	background: $tavern-card-dark;
	border-radius: 20rpx;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.hero-card {
	padding: 28rpx;
	margin-bottom: 24rpx;
}

.hero-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.hero-subtitle {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.sheet {
	overflow: hidden;
}

.cell {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	padding: 26rpx 28rpx;
	border-bottom: 1rpx solid $tavern-border-on-dark;
}

.cell--last {
	border-bottom: none;
}

.cell-main {
	flex: 1;
	min-width: 0;
}

.cell-txt {
	display: block;
	font-size: 30rpx;
	color: $tavern-text-on-dark;
}

.cell-sub {
	display: block;
	margin-top: 10rpx;
	font-size: 23rpx;
	line-height: 1.7;
	color: $tavern-muted-on-dark;
}

.cell-txt--danger {
	color: #fda4af;
}
</style>
