<template>
	<view class="auth-page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<image class="page-glow page-glow--top" src="/static/user/vipbgs.png" mode="widthFix"></image>
		<image class="page-glow page-glow--bottom" src="/static/user/moneybg.png" mode="widthFix"></image>

		<view class="hero">
			<image class="brand-logo" src="/static/logo.png" mode="aspectFill"></image>
			<text class="brand-name">四叶酒馆</text>
			<text class="brand-subtitle">{{ copy.subtitle }}</text>
		</view>

		<view class="auth-card">
			<view v-if="!featureConfig.registerEnabled" class="feature-notice">
				<text class="feature-notice__title">{{ copy.registerPausedTitle || '当前暂停账号注册' }}</text>
				<text class="feature-notice__desc">{{ copy.registerPausedDesc || '现在先开放纯体验模式，平台账号注册暂时关闭。想继续体验可以先游客浏览。' }}</text>
			</view>

			<view class="field">
				<text class="field-label">{{ copy.accountLabel }}</text>
				<input
					v-model="username"
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
					v-model="pwd"
					class="field-input"
					:type="inputshow ? 'text' : 'password'"
					:placeholder="copy.passwordPlaceholder"
					placeholder-style="color: rgba(148, 163, 184, 0.88);"
					maxlength="64"
					:disabled="submitting"
					confirm-type="next"
				/>
			</view>

			<view class="field">
				<text class="field-label">{{ copy.confirmLabel }}</text>
				<input
					v-model="pwd2"
					class="field-input"
					:type="inputshow ? 'text' : 'password'"
					:placeholder="copy.confirmPlaceholder"
					placeholder-style="color: rgba(148, 163, 184, 0.88);"
					maxlength="64"
					:disabled="submitting"
					confirm-type="done"
					@confirm="reg"
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

			<view class="submit-wrap" :class="{ 'submit-wrap--pending': !agreePrivacy }">
				<fui-button
					background="#348FB8"
					disabledBackground="#B7DCE7"
					radius="48rpx"
					:disabled="submitting || !featureConfig.registerEnabled"
					@tap="reg"
				>{{ submitting ? copy.registering || copy.register : copy.register }}</fui-button>
			</view>

			<view class="switch-row">
				<text class="switch-text">{{ copy.hasAccount }}</text>
				<text class="switch-link" :class="{ 'switch-link--disabled': !featureConfig.loginEnabled }" @tap="goLogin">{{ copy.login }}</text>
			</view>
		</view>
	</view>
</template>

<script>
import fuiButton from "@/components/firstui/fui-button/fui-button.vue"
const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		subtitle: '保留你当前设备上的聊天与偏好，注册后即可认领成正式账号。',
		accountLabel: '账号',
		accountPlaceholder: '请设置账号',
		passwordLabel: '密码',
		passwordPlaceholder: '请设置密码',
		confirmLabel: '确认密码',
		confirmPlaceholder: '请再次输入密码',
		show: '显示',
		hide: '隐藏',
		agreePrefix: '我已阅读并同意',
		agreeLink: '隐私协议',
		register: '立即注册',
		hasAccount: '已经有账号？',
		login: '去登录',
		passwordMismatch: '两次输入的密码不一致',
		needAgreement: '请先同意隐私协议',
		accountRequired: '请输入账号',
		passwordRequired: '请输入密码'
	},
	'zh-hk': {
		subtitle: '保留目前裝置上的聊天與偏好，註冊後即可認領成正式帳號。',
		accountLabel: '帳號',
		accountPlaceholder: '請設定帳號',
		passwordLabel: '密碼',
		passwordPlaceholder: '請設定密碼',
		confirmLabel: '確認密碼',
		confirmPlaceholder: '請再次輸入密碼',
		show: '顯示',
		hide: '隱藏',
		agreePrefix: '我已閱讀並同意',
		agreeLink: '私隱協議',
		register: '立即註冊',
		hasAccount: '已經有帳號？',
		login: '去登入',
		passwordMismatch: '兩次輸入的密碼不一致',
		needAgreement: '請先同意私隱協議',
		accountRequired: '請輸入帳號',
		passwordRequired: '請輸入密碼'
	},
	en: {
		subtitle: 'Claim your current guest session as a real account without losing chats and preferences.',
		accountLabel: 'Account',
		accountPlaceholder: 'Choose an account name',
		passwordLabel: 'Password',
		passwordPlaceholder: 'Create a password',
		confirmLabel: 'Confirm Password',
		confirmPlaceholder: 'Enter the password again',
		show: 'Show',
		hide: 'Hide',
		agreePrefix: 'I have read and agree to the',
		agreeLink: 'Privacy Policy',
		register: 'Create Account',
		hasAccount: 'Already have an account?',
		login: 'Log In',
		passwordMismatch: 'Passwords do not match',
		needAgreement: 'Please accept the privacy policy first',
		accountRequired: 'Please enter an account',
		passwordRequired: 'Please enter a password'
	}
};

export default {
	components: { fuiButton },
	data() {
		return {
			inputshow: false,
			show: false,
			background: {
				backgroundColor: ''
			},
			code_url: '',
			code: '',
			pwd: '',
			pwd2:'',
			email: '',
			username: '',
			code_id:'',
			codeNun: 60,
			agreePrivacy: false,
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
			return this.copy.agreementRequiredDesc || '\u5148\u52fe\u9009\u201c\u6211\u5df2\u6ee118\u5468\u5c81\u5e76\u540c\u610f\u7528\u6237\u534f\u8bae\u4e0e\u9690\u79c1\u653f\u7b56\u201d\uff0c\u6ce8\u518c\u6309\u94ae\u624d\u4f1a\u53ef\u70b9\u51fb';
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
		showAgreementDialog() {
			uni.showModal({
				title: this.agreementRequiredHintText || '未勾选协议',
				content:
					this.agreementRequiredDescText ||
					'请先勾选“我已满18周岁并同意用户协议与隐私政策”，再继续注册。',
				confirmText: '我知道了',
				showCancel: false
			});
		},
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
		goLogin() {
			if (!this.featureConfig.loginEnabled) {
				this.util.showToast(this.copy.loginPausedToast || '当前暂未开放账号登录');
				return;
			}
			let url = '/pages/login/login';
			if (this.redirectUrl) {
				url += '?redirect=' + encodeURIComponent(this.redirectUrl);
			}
			uni.navigateTo({ url });
		},
		afterRegister(userinfo) {
			uni.setStorageSync('user', userinfo);
			tavernApi.markCharacterAccessRefreshNeeded('register');
			this.$store.commit('setuser',userinfo)
			this.$store.commit('settoken',userinfo.token)
			if (this.redirectUrl && this.navigateAfterAuth(this.redirectUrl)) {
				return
			}
			this.util.urlTo('/pages/perfect/perfect')
		},
		reg() {
			if (this.submitting) return
			if (!this.featureConfig.registerEnabled) {
				this.util.showToast(this.copy.registerPausedToast || '当前暂未开放账号注册')
				return
			}
			if(this.pwd != this.pwd2){
				this.util.showToast(this.copy.passwordMismatch)
				return
			}
			if (!this.agreePrivacy) {
				this.showAgreementDialog()
				return false
			}
			if (!this.username) {
				this.util.showToast(this.copy.accountRequired)
				return false
			}
			if (!this.pwd) {
				this.util.showToast(this.copy.passwordRequired)
				return false
			}
			uni.showLoading({
				title: this.copy.registering || this.copy.register
			})
			this.submitting = true
			this.util.request('index/emsregister',{
				password:this.pwd,
				username:this.username,
				client_uid: tavernApi.getClientUid(),
				type:0
			},'POST').then(res=>{
				this.afterRegister(res.data.userinfo)
			}).catch(() => {
			}).finally(() => {
				this.submitting = false
				uni.hideLoading()
			})
		}
	},
}
</script>

<style scoped lang="scss">
.auth-page {
	position: relative;
	min-height: 100vh;
	padding: calc(72rpx + env(safe-area-inset-top)) 36rpx 64rpx;
	box-sizing: border-box;
	background:
		radial-gradient(circle at top left, rgba(79, 70, 229, 0.24), transparent 34%),
		radial-gradient(circle at bottom right, rgba(236, 72, 153, 0.14), transparent 28%),
		linear-gradient(180deg, #0f172a 0%, #111827 40%, #1f1b4b 100%);
	overflow: hidden;
}

.page-glow {
	position: absolute;
	pointer-events: none;
	opacity: 0.42;
}

.page-glow--top {
	top: -48rpx;
	left: -120rpx;
	width: 760rpx;
}

.page-glow--bottom {
	right: -150rpx;
	bottom: -10rpx;
	width: 620rpx;
	opacity: 0.3;
}

.hero {
	position: relative;
	z-index: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 40rpx 0 34rpx;
}

.brand-logo {
	width: 150rpx;
	height: 150rpx;
	border-radius: 38rpx;
	box-shadow: 0 24rpx 64rpx rgba(15, 23, 42, 0.4);
}

.brand-name {
	margin-top: 24rpx;
	font-size: 48rpx;
	font-weight: 700;
	color: #fff;
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

/* Light clover tavern auth refresh. */
.auth-page {
	background:
		radial-gradient(circle at top left, rgba(200, 229, 250, 0.88), transparent 34%),
		radial-gradient(circle at bottom right, rgba(248, 226, 244, 0.76), transparent 28%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
}

.page-glow {
	opacity: 0.28;
}

.brand-logo {
	border-radius: 50%;
	border: 6rpx solid rgba(255, 255, 255, 0.8);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.16);
}

.brand-name {
	color: #244b66;
}

.brand-subtitle {
	color: #687f92;
}

.auth-card,
.field,
.agreement-row {
	backdrop-filter: blur(22rpx);
}

.auth-card {
	background: rgba(255, 255, 255, 0.54);
	border-color: rgba(255, 255, 255, 0.46);
	box-shadow: 0 26rpx 72rpx rgba(67, 112, 142, 0.12);
}

.feature-notice {
	background: rgba(255, 246, 220, 0.8);
	border-color: rgba(245, 190, 86, 0.28);
}

.feature-notice__title {
	color: #9a6b18;
}

.feature-notice__desc {
	color: #9a7b45;
}

.field {
	background: rgba(255, 255, 255, 0.34);
	border-color: rgba(255, 255, 255, 0.46);
}

.field-label,
.field-toggle,
.agreement-link,
.switch-link {
	color: #247494;
}

.field-input {
	color: #16384d;
	caret-color: #247494;
	-webkit-text-fill-color: #16384d;
}

/* #ifdef H5 */
.field-input:-webkit-autofill,
.field-input:-webkit-autofill:hover,
.field-input:-webkit-autofill:focus {
	-webkit-text-fill-color: #16384d;
}
/* #endif */

.agreement-row {
	background: rgba(255, 255, 255, 0.3);
}

.agreement-row--warn,
.agreement-tip {
	background: rgba(255, 246, 220, 0.74);
	border-color: rgba(245, 190, 86, 0.28);
}

.agreement-text,
.switch-text {
	color: #687f92;
}

.agreement-tip__title {
	color: #9a6b18;
}

.agreement-tip__desc {
	color: #9a7b45;
}

/* Readability pass: soften visual noise and keep agreement state obvious. */
.auth-page {
	background: transparent;
}

.page-glow {
	opacity: 0.2;
}

.auth-card {
	background: rgba(255, 255, 255, 0.72);
	border-color: rgba(201, 225, 239, 0.92);
	box-shadow: 0 22rpx 58rpx rgba(64, 106, 132, 0.14);
}

.field {
	background: rgba(255, 255, 255, 0.78);
	border-color: rgba(191, 219, 236, 0.94);
}

.field-label,
.field-toggle,
.agreement-link,
.switch-link,
.brand-name {
	color: #2a617f;
}

.field-input,
.agreement-text,
.switch-text,
.brand-subtitle {
	color: #4f6a7d;
}

.agreement-row {
	background: rgba(244, 251, 255, 0.9);
	border-color: rgba(191, 222, 241, 0.9);
}

.agreement-row--warn,
.agreement-tip {
	background: rgba(255, 247, 227, 0.92);
	border-color: rgba(236, 188, 93, 0.34);
}

.submit-wrap--pending {
	filter: saturate(0.82);
}
</style>
