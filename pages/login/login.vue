<template>
	<view class="auth-page" :class="localeFontClass">
		<image class="page-glow page-glow--top" src="/static/user/vipbgs.png" mode="widthFix"></image>
		<image class="page-glow page-glow--bottom" src="/static/user/moneybg.png" mode="widthFix"></image>

		<view class="page-head">
			<view class="lang-chip" @tap="util.urlTo('/pages/user/language')">{{ copy.language }}</view>
		</view>

		<view class="hero">
			<image class="brand-logo" src="/static/logo.png" mode="aspectFill"></image>
			<text class="brand-name">AI Character Chat</text>
			<text class="brand-subtitle">{{ copy.subtitle }}</text>
		</view>

		<view class="auth-card">
			<view v-if="!featureConfig.loginEnabled" class="feature-notice">
				<text class="feature-notice__title">{{ copy.loginPausedTitle || '当前暂停账号登录' }}</text>
				<text class="feature-notice__desc">{{ copy.loginPausedDesc || '现在先开放纯体验模式，登录功能暂时关闭。想继续体验可以先游客浏览。' }}</text>
			</view>

			<view class="field">
				<text class="field-label">{{ copy.accountLabel }}</text>
				<input
					v-model="account"
					class="field-input"
					type="text"
					:placeholder="copy.accountPlaceholder"
					placeholder-style="color: rgba(148, 163, 184, 0.88);"
					maxlength="32"
					:disabled="submitting"
					confirm-type="next"
				/>
			</view>

			<view class="field">
				<view class="field-head">
					<text class="field-label">{{ copy.passwordLabel }}</text>
					<text class="field-toggle" @tap="inputshow = !inputshow">{{ inputshow ? copy.hide : copy.show }}</text>
				</view>
				<input
					v-model="password"
					class="field-input"
					:type="inputshow ? 'text' : 'password'"
					:placeholder="copy.passwordPlaceholder"
					placeholder-style="color: rgba(148, 163, 184, 0.88);"
					maxlength="64"
					:disabled="submitting"
					confirm-type="done"
					@confirm="login"
				/>
			</view>

			<view class="agreement-row" :class="{ 'agreement-row--warn': !agreePrivacy }" @tap="agreePrivacy = !agreePrivacy">
				<image class="agreement-icon" :src="agreePrivacy ? '/static/user/wx.png' : '/static/user/xz.png'" mode="aspectFit"></image>
				<text class="agreement-text">
					我已满18周岁，并已阅读同意
					<text class="agreement-link" @tap.stop="util.urlTo('/pages/user/tiaokuan/tiaokuan')">《用户协议》</text>
					和
					<text class="agreement-link" @tap.stop="util.urlTo('/pages/user/yinshi/yinshi')">《隐私政策》</text>
				</text>
			</view>
			<view v-if="!agreePrivacy" class="agreement-tip">
				<text class="agreement-tip__title">{{ agreementRequiredHintText }}</text>
				<text class="agreement-tip__desc">{{ agreementRequiredDescText }}</text>
			</view>

			<fui-button
				background="#4F46E5"
				disabledBackground="#A5B4FC"
				:disabled="!agreePrivacy || submitting || !featureConfig.loginEnabled"
				radius="48rpx"
				@tap="login"
			>{{ submitting ? copy.loginLoading : copy.login }}</fui-button>

			<view class="switch-row">
				<text class="switch-text">{{ copy.noAccount }}</text>
				<text class="switch-link" :class="{ 'switch-link--disabled': !featureConfig.registerEnabled }" @tap="goRegister">{{ copy.register }}</text>
			</view>
		</view>

		<u-popup v-model="show" mode="center" width="624rpx" height="334rpx" border-radius="28">
			<view class="popup-box">
				<text class="popup-title">{{ content }}</text>
				<view class="popup-btn" @tap="show = false">{{ copy.confirm }}</view>
			</view>
		</u-popup>
	</view>
</template>

<script>
import fuiButton from "@/components/firstui/fui-button/fui-button.vue"
const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		language: '语言',
		subtitle: '游客先体验，充值时再认领你的账号。',
		accountLabel: '账号',
		accountPlaceholder: '请输入账号',
		passwordLabel: '密码',
		passwordPlaceholder: '请输入密码',
		show: '显示',
		hide: '隐藏',
		agreePrefix: '我已阅读并同意',
		agreeLink: '隐私协议',
		login: '登录',
		noAccount: '还没有账号？',
		register: '注册新账号',
		confirm: '确定',
		loginLoading: '登录中...',
		accountRequired: '请输入账号',
		passwordRequired: '请输入密码'
	},
	'zh-hk': {
		language: '語言',
		subtitle: '旅客可先體驗，需要充值時再認領你的帳號。',
		accountLabel: '帳號',
		accountPlaceholder: '請輸入帳號',
		passwordLabel: '密碼',
		passwordPlaceholder: '請輸入密碼',
		show: '顯示',
		hide: '隱藏',
		agreePrefix: '我已閱讀並同意',
		agreeLink: '私隱協議',
		login: '登入',
		noAccount: '還沒有帳號？',
		register: '註冊新帳號',
		confirm: '確定',
		loginLoading: '登入中...',
		accountRequired: '請輸入帳號',
		passwordRequired: '請輸入密碼'
	},
	en: {
		language: 'Lang',
		subtitle: 'Browse as a guest first, then claim your account when you need payment.',
		accountLabel: 'Account',
		accountPlaceholder: 'Enter your account',
		passwordLabel: 'Password',
		passwordPlaceholder: 'Enter your password',
		show: 'Show',
		hide: 'Hide',
		agreePrefix: 'I have read and agree to the',
		agreeLink: 'Privacy Policy',
		login: 'Log In',
		noAccount: 'No account yet?',
		register: 'Create one',
		confirm: 'OK',
		loginLoading: 'Signing in...',
		accountRequired: 'Please enter your account',
		passwordRequired: 'Please enter your password',
		agreementRequiredHint: 'Consent required',
		agreementRequiredDesc: 'Tick the 18+ and policy checkbox first, then the login button will become available.',
		loginPausedTitle: 'Login unavailable',
		loginPausedDesc: 'Account login is temporarily closed. You can still browse as a guest first.',
		loginPausedToast: 'Login is temporarily unavailable',
		registerPausedToast: 'Registration is temporarily unavailable'
	}
};

export default {
	components: { fuiButton },
	data() {
		return {
			agreePrivacy: false,
			inputshow: false,
			show: false,
			password: '',
			account: '',
			content: '',
			redirectUrl: '',
			submitting: false,
			featureConfig: {
				loginEnabled: tavernApi.isLoginEnabled(),
				registerEnabled: tavernApi.isRegisterEnabled(),
				userCharacterCreationEnabled: tavernApi.isUserCharacterCreationEnabled()
			}
		}
	},
	computed: {
		copy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		agreementRequiredHintText() {
			return this.copy.agreementRequiredHint || '\u672a\u52fe\u9009\u534f\u8bae';
		},
		agreementRequiredDescText() {
			return this.copy.agreementRequiredDesc || '\u5148\u52fe\u9009\u201c\u6211\u5df2\u6ee118\u5468\u5c81\u5e76\u540c\u610f\u7528\u6237\u534f\u8bae\u4e0e\u9690\u79c1\u653f\u7b56\u201d\uff0c\u767b\u5f55\u6309\u94ae\u624d\u4f1a\u53ef\u70b9\u51fb';
		}
	},
	onLoad(options) {
		this.redirectUrl = options && options.redirect ? decodeURIComponent(options.redirect) : '';
		this.syncFeatureConfig(false);
	},
	onShow() {
		this.syncFeatureConfig(true);
	},
	methods: {
		syncFeatureConfig(forceRefresh) {
			tavernApi
				.fetchAppRuntimeConfig(!!forceRefresh)
				.then((config) => {
					this.featureConfig = config || this.featureConfig;
				})
				.catch(() => {});
		},
		isTabPage(url) {
			const path = String(url || '').split('?')[0];
			return [
				'/pages/index/index',
				'/pages/tavern/tavern',
				'/pages/tavern/tavernInbox',
				'/pages/user/user'
			].indexOf(path) !== -1;
		},
		navigateAfterAuth(url) {
			const target = String(url || '').trim();
			if (!target) return false;
			if (this.isTabPage(target)) {
				uni.switchTab({ url: String(target).split('?')[0] });
				return true;
			}
			uni.redirectTo({
				url: target,
				fail: () => {
					uni.reLaunch({ url: target });
				}
			});
			return true;
		},
		goRegister() {
			if (!this.featureConfig.registerEnabled) {
				this.util.showToast(this.copy.registerPausedToast || this.copy.loginPausedToast || '当前暂未开放账号注册');
				return;
			}
			let url = '/pages/login/reg';
			if (this.redirectUrl) {
				url += '?redirect=' + encodeURIComponent(this.redirectUrl);
			}
			this.util.urlTo(url);
		},
		afterLogin(userinfo) {
			uni.setStorageSync('user', userinfo);
			tavernApi.markCharacterAccessRefreshNeeded('login');
			this.$store.commit('setuser',userinfo)
			this.$store.commit('settoken',userinfo.token)
			if (this.redirectUrl && this.navigateAfterAuth(this.redirectUrl)) {
				return
			}
			if (userinfo.need_edit == 0 && userinfo.status == 'normal') {
				this.util.urlTo('/pages/perfect/perfect')
			} else if (userinfo.need_edit == 1 && userinfo.status == 'normal') {
				uni.reLaunch({
					url: '/pages/index/index'
				})
			}
		},
		login() {
			if (this.submitting) return false
			if (!this.featureConfig.loginEnabled) {
				this.util.showToast(this.copy.loginPausedToast || '当前暂未开放账号登录')
				return false
			}
			if (!this.agreePrivacy) {
				this.util.showToast('请先确认已满18周岁，并同意用户协议与隐私政策')
				return false
			}
			if (!this.account) {
				this.util.showToast(this.copy.accountRequired)
				return false
			}
			if (!this.password) {
				this.util.showToast(this.copy.passwordRequired)
				return false
			}
			uni.showLoading({
				title: this.copy.loginLoading
			})
			this.submitting = true
			this.util.request('index/emslogin', {
				account: this.account,
				password: this.password,
				client_uid: tavernApi.getClientUid()
			}, 'POST').then(res => {
				if (res.code == 1) {
					this.afterLogin(res.data.userinfo)
				} else if (res.code == 4002) {
					this.content = res.msg
					this.show = true
				}
			}).catch(() => {
			}).finally(() => {
				this.submitting = false
				uni.hideLoading()
			})
		}
	}
}
</script>

<style scoped lang="scss">
.auth-page {
	position: relative;
	min-height: 100vh;
	padding: calc(56rpx + env(safe-area-inset-top)) 36rpx 64rpx;
	box-sizing: border-box;
	background:
		radial-gradient(circle at top left, rgba(99, 102, 241, 0.22), transparent 34%),
		radial-gradient(circle at bottom right, rgba(236, 72, 153, 0.16), transparent 28%),
		linear-gradient(180deg, #0f172a 0%, #111827 38%, #1e1b4b 100%);
	overflow: hidden;
}

.page-glow {
	position: absolute;
	pointer-events: none;
	opacity: 0.48;
}

.page-glow--top {
	top: -40rpx;
	left: -120rpx;
	width: 760rpx;
}

.page-glow--bottom {
	right: -160rpx;
	bottom: -40rpx;
	width: 620rpx;
	opacity: 0.36;
}

.page-head {
	display: flex;
	justify-content: flex-end;
	position: relative;
	z-index: 1;
}

.lang-chip {
	padding: 14rpx 24rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.08);
	border: 1rpx solid rgba(255, 255, 255, 0.12);
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.9);
	backdrop-filter: blur(18rpx);
}

.hero {
	position: relative;
	z-index: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 72rpx 0 44rpx;
}

.brand-logo {
	width: 164rpx;
	height: 164rpx;
	border-radius: 40rpx;
	box-shadow: 0 24rpx 60rpx rgba(15, 23, 42, 0.45);
}

.brand-name {
	margin-top: 28rpx;
	font-size: 48rpx;
	font-weight: 700;
	color: #ffffff;
}

.brand-subtitle {
	margin-top: 16rpx;
	max-width: 560rpx;
	text-align: center;
	font-size: 26rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.82);
}

.auth-card {
	position: relative;
	z-index: 1;
	padding: 36rpx 30rpx 30rpx;
	border-radius: 32rpx;
	background: rgba(15, 23, 42, 0.74);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	box-shadow: 0 26rpx 80rpx rgba(15, 23, 42, 0.34);
	backdrop-filter: blur(20rpx);
}

.feature-notice {
	margin-bottom: 24rpx;
	padding: 22rpx 24rpx;
	border-radius: 24rpx;
	background: rgba(245, 158, 11, 0.14);
	border: 1rpx solid rgba(251, 191, 36, 0.28);
}

.feature-notice__title {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: #fde68a;
}

.feature-notice__desc {
	display: block;
	margin-top: 10rpx;
	font-size: 24rpx;
	line-height: 1.6;
	color: rgba(254, 240, 138, 0.9);
}

.field {
	margin-bottom: 22rpx;
	padding: 24rpx 24rpx 18rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.04);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
}

.field-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.field-label,
.field-toggle {
	font-size: 24rpx;
	color: rgba(196, 181, 253, 0.92);
}

.field-input {
	width: 100%;
	height: 76rpx;
	margin-top: 10rpx;
	font-size: 30rpx;
	color: #f8fafc;
	background: transparent;
	caret-color: #ffffff;
	-webkit-text-fill-color: #f8fafc;
}

/* #ifdef H5 */
.field-input:-webkit-autofill,
.field-input:-webkit-autofill:hover,
.field-input:-webkit-autofill:focus {
	-webkit-text-fill-color: #f8fafc;
	transition: background-color 99999s ease-in-out 0s;
	box-shadow: 0 0 0 1000px transparent inset;
}
/* #endif */

.agreement-row {
	display: flex;
	align-items: center;
	gap: 14rpx;
	margin: 10rpx 0 12rpx;
	padding: 12rpx 14rpx;
	border-radius: 20rpx;
	border: 1rpx solid transparent;
	background: rgba(15, 23, 42, 0.18);
}

.agreement-row--warn {
	border-color: rgba(251, 191, 36, 0.34);
	background: rgba(245, 158, 11, 0.08);
}

.agreement-icon {
	width: 36rpx;
	height: 36rpx;
	flex-shrink: 0;
}

.agreement-text {
	font-size: 24rpx;
	line-height: 1.6;
	color: rgba(226, 232, 240, 0.78);
}

.agreement-link {
	color: #c4b5fd;
}

.agreement-tip {
	margin: 0 0 24rpx;
	padding: 18rpx 20rpx;
	border-radius: 20rpx;
	background: rgba(245, 158, 11, 0.12);
	border: 1rpx solid rgba(251, 191, 36, 0.22);
}

.agreement-tip__title {
	display: block;
	font-size: 24rpx;
	font-weight: 700;
	color: #fde68a;
}

.agreement-tip__desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: rgba(254, 240, 138, 0.92);
}

.switch-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	margin-top: 26rpx;
	font-size: 24rpx;
}

.switch-text {
	color: rgba(226, 232, 240, 0.68);
}

.switch-link {
	color: #a5b4fc;
	font-weight: 600;
}

.switch-link--disabled {
	opacity: 0.45;
}

.popup-box {
	padding: 48rpx 40rpx 36rpx;
}

.popup-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	line-height: 1.6;
	text-align: center;
	color: #1f2937;
}

.popup-btn {
	margin-top: 42rpx;
	height: 88rpx;
	line-height: 88rpx;
	text-align: center;
	border-radius: 44rpx;
	font-size: 28rpx;
	font-weight: 600;
	color: #fff;
	background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
}
</style>
