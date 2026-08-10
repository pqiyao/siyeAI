<template>
	<view class="auth-page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<u-navbar
			:title="copy.title"
			:background="{ backgroundColor: 'transparent' }"
			:immersive="true"
			title-color="#fff"
			back-icon-color="#fff"
		></u-navbar>

		<view class="hero">
			<image class="brand-logo" src="/static/logo.png" mode="aspectFill"></image>
			<text class="hero-title">{{ copy.title }}</text>
			<text class="hero-subtitle">{{ copy.subtitle }}</text>
		</view>

		<view class="auth-card">
			<view class="field">
				<text class="field-label">{{ copy.emailLabel }}</text>
				<input
					v-model.trim="email"
					class="field-input"
					type="text"
					maxlength="128"
					:disabled="submitting || sendingCode"
					:placeholder="copy.emailPlaceholder"
				/>
			</view>

			<view class="field">
				<text class="field-label">{{ copy.codeLabel }}</text>
				<view class="code-row">
					<input
						v-model.trim="captcha"
						class="field-input field-input--code"
						type="number"
						maxlength="8"
						:disabled="submitting"
						:placeholder="copy.codePlaceholder"
					/>
					<view
						class="code-button"
						:class="{ 'code-button--disabled': sendingCode || countdown > 0 }"
						@tap="getCode"
					>{{ countdown > 0 ? countdown + 's' : (sendingCode ? copy.sending : copy.sendCode) }}</view>
				</view>
			</view>

			<view class="field">
				<view class="field-head">
					<text class="field-label">{{ copy.passwordLabel }}</text>
					<text class="field-toggle" @tap="inputshow = !inputshow">{{ inputshow ? copy.hide : copy.show }}</text>
				</view>
				<input
					v-model="newpassword"
					class="field-input"
					:type="inputshow ? 'text' : 'password'"
					maxlength="64"
					:disabled="submitting"
					:placeholder="copy.passwordPlaceholder"
					@confirm="resetPassword"
				/>
			</view>

			<text class="account-hint">{{ copy.accountHint }}</text>
			<view class="submit-wrap">
				<fui-button background="#348FB8" radius="48rpx" :disabled="submitting" @tap="resetPassword">
					{{ submitting ? copy.resetting : copy.reset }}
				</fui-button>
			</view>
		</view>
	</view>
</template>

<script>
import fuiButton from '@/components/firstui/fui-button/fui-button.vue';
const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '重置密码', subtitle: '通过注册账号使用的邮箱验证身份。', emailLabel: '账号邮箱', emailPlaceholder: '请输入注册时使用的邮箱',
		codeLabel: '邮箱验证码', codePlaceholder: '请输入 8 位验证码', sendCode: '获取验证码', sending: '发送中...', passwordLabel: '新密码',
		passwordPlaceholder: '请输入 6-64 位新密码', show: '显示', hide: '隐藏', accountHint: '只有使用邮箱地址注册的账号支持自助重置；普通用户名账号请联系管理员核验处理。',
		reset: '立即重置密码', resetting: '重置中...', emailInvalid: '请输入有效的账号邮箱', codeRequired: '请输入 8 位邮箱验证码',
		passwordInvalid: '新密码长度需为 6-64 位', requestFirst: '请先获取邮箱验证码', sent: '如果该邮箱已注册，验证码将发送到邮箱，请注意查收。',
		unavailableTitle: '暂时无法发送邮件', unavailable: '当前服务器未配置密码重置邮件。请联系管理员核验账号并重置密码。',
		sendFailed: '验证码发送失败，请稍后重试', resetSuccess: '密码已重置，请使用新密码登录', resetFailed: '验证码无效或已过期，请重新获取'
	},
	'zh-hk': {
		title: '重設密碼', subtitle: '透過註冊帳號所使用的電郵驗證身份。', emailLabel: '帳號電郵', emailPlaceholder: '請輸入註冊時使用的電郵',
		codeLabel: '電郵驗證碼', codePlaceholder: '請輸入 8 位驗證碼', sendCode: '取得驗證碼', sending: '傳送中...', passwordLabel: '新密碼',
		passwordPlaceholder: '請輸入 6-64 位新密碼', show: '顯示', hide: '隱藏', accountHint: '只有以電郵地址註冊的帳號支援自助重設；一般用戶名稱帳號請聯絡管理員核驗。',
		reset: '立即重設密碼', resetting: '重設中...', emailInvalid: '請輸入有效的帳號電郵', codeRequired: '請輸入 8 位電郵驗證碼',
		passwordInvalid: '新密碼長度須為 6-64 位', requestFirst: '請先取得電郵驗證碼', sent: '如該電郵已註冊，驗證碼將傳送到郵箱，請注意查收。',
		unavailableTitle: '暫時無法傳送郵件', unavailable: '目前伺服器尚未設定密碼重設郵件，請聯絡管理員核驗帳號。',
		sendFailed: '驗證碼傳送失敗，請稍後再試', resetSuccess: '密碼已重設，請使用新密碼登入', resetFailed: '驗證碼無效或已過期，請重新取得'
	},
	en: {
		title: 'Reset Password', subtitle: 'Verify your identity through the email used as your account.', emailLabel: 'Account email', emailPlaceholder: 'Enter the email used to register',
		codeLabel: 'Email code', codePlaceholder: 'Enter the 8-digit code', sendCode: 'Send code', sending: 'Sending...', passwordLabel: 'New password',
		passwordPlaceholder: 'Enter a new password (6-64 characters)', show: 'Show', hide: 'Hide', accountHint: 'Self-service reset is available only for accounts registered with an email address. Contact an administrator for username-only accounts.',
		reset: 'Reset Password', resetting: 'Resetting...', emailInvalid: 'Enter a valid account email', codeRequired: 'Enter the 8-digit email code',
		passwordInvalid: 'The new password must be 6-64 characters', requestFirst: 'Request an email code first', sent: 'If this email is registered, a code will arrive shortly.',
		unavailableTitle: 'Email unavailable', unavailable: 'Password reset email is not configured on this server. Contact an administrator to verify your account.',
		sendFailed: 'Could not send the code. Try again later.', resetSuccess: 'Password reset. Sign in with your new password.', resetFailed: 'The code is invalid or expired. Request a new one.'
	},
	ko: {
		title: '비밀번호 재설정', subtitle: '가입 계정에 사용한 이메일로 본인을 확인합니다.', emailLabel: '계정 이메일', emailPlaceholder: '가입할 때 사용한 이메일을 입력하세요',
		codeLabel: '이메일 인증 코드', codePlaceholder: '8자리 코드를 입력하세요', sendCode: '코드 받기', sending: '전송 중...', passwordLabel: '새 비밀번호',
		passwordPlaceholder: '6-64자의 새 비밀번호를 입력하세요', show: '표시', hide: '숨기기', accountHint: '이메일 주소로 가입한 계정만 직접 재설정할 수 있습니다. 사용자 이름 계정은 관리자에게 문의하세요.',
		reset: '비밀번호 재설정', resetting: '재설정 중...', emailInvalid: '올바른 계정 이메일을 입력하세요', codeRequired: '8자리 이메일 코드를 입력하세요',
		passwordInvalid: '새 비밀번호는 6-64자여야 합니다', requestFirst: '먼저 이메일 코드를 요청하세요', sent: '등록된 이메일이라면 곧 인증 코드가 도착합니다.',
		unavailableTitle: '이메일을 보낼 수 없음', unavailable: '서버에 비밀번호 재설정 이메일이 구성되지 않았습니다. 관리자에게 계정 확인을 요청하세요.',
		sendFailed: '코드를 보내지 못했습니다. 잠시 후 다시 시도하세요.', resetSuccess: '비밀번호가 재설정되었습니다. 새 비밀번호로 로그인하세요.', resetFailed: '코드가 잘못되었거나 만료되었습니다. 새 코드를 요청하세요.'
	},
	ja: {
		title: 'パスワード再設定', subtitle: '登録アカウントに使用したメールで本人確認します。', emailLabel: 'アカウントのメール', emailPlaceholder: '登録時のメールアドレスを入力',
		codeLabel: 'メール認証コード', codePlaceholder: '8桁のコードを入力', sendCode: 'コードを取得', sending: '送信中...', passwordLabel: '新しいパスワード',
		passwordPlaceholder: '6-64文字の新しいパスワード', show: '表示', hide: '非表示', accountHint: 'メールアドレスで登録したアカウントのみ自身で再設定できます。ユーザー名のみのアカウントは管理者にお問い合わせください。',
		reset: 'パスワードを再設定', resetting: '再設定中...', emailInvalid: '有効なアカウントメールを入力してください', codeRequired: '8桁のメールコードを入力してください',
		passwordInvalid: '新しいパスワードは6-64文字で入力してください', requestFirst: '先にメールコードを取得してください', sent: '登録済みのメールであれば、まもなくコードが届きます。',
		unavailableTitle: 'メールを送信できません', unavailable: 'このサーバーにはパスワード再設定メールが設定されていません。管理者にアカウント確認を依頼してください。',
		sendFailed: 'コードを送信できませんでした。しばらくしてから再試行してください。', resetSuccess: 'パスワードを再設定しました。新しいパスワードでログインしてください。', resetFailed: 'コードが無効または期限切れです。新しいコードを取得してください。'
	}
};

export default {
	components: { fuiButton },
	data() {
		return {
			inputshow: false,
			email: '',
			captcha: '',
			newpassword: '',
			requestId: '',
			countdown: 0,
			countdownTimer: null,
			sendingCode: false,
			submitting: false,
			navigateTimer: null
		};
	},
	computed: {
		copy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		}
	},
	onUnload() {
		this.clearTimers();
	},
	methods: {
		clearTimers() {
			if (this.countdownTimer) clearInterval(this.countdownTimer);
			if (this.navigateTimer) clearTimeout(this.navigateTimer);
			this.countdownTimer = null;
			this.navigateTimer = null;
		},
		validEmail() {
			return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(this.email || '').trim());
		},
		startCountdown(seconds) {
			if (this.countdownTimer) clearInterval(this.countdownTimer);
			this.countdown = Math.max(1, Math.min(300, Number(seconds) || 60));
			this.countdownTimer = setInterval(() => {
				this.countdown -= 1;
				if (this.countdown <= 0) {
					clearInterval(this.countdownTimer);
					this.countdownTimer = null;
					this.countdown = 0;
				}
			}, 1000);
		},
		getCode() {
			if (this.sendingCode || this.countdown > 0) return;
			if (!this.validEmail()) {
				this.util.showToast(this.copy.emailInvalid);
				return;
			}
			this.sendingCode = true;
			tavernApi.requestH5PasswordReset(this.email).then((result) => {
				if (!result || result.deliveryAvailable !== true) {
					uni.showModal({ title: this.copy.unavailableTitle, content: this.copy.unavailable, showCancel: false });
					return;
				}
				this.requestId = String(result.requestId || '');
				this.startCountdown(result.retryAfterSeconds || 60);
				this.util.showToast(this.copy.sent);
			}).catch(() => {
				this.util.showToast(this.copy.sendFailed);
			}).finally(() => {
				this.sendingCode = false;
			});
		},
		resetPassword() {
			if (this.submitting) return;
			if (!this.validEmail()) {
				this.util.showToast(this.copy.emailInvalid);
				return;
			}
			if (!this.requestId) {
				this.util.showToast(this.copy.requestFirst);
				return;
			}
			if (!/^\d{8}$/.test(String(this.captcha || '').trim())) {
				this.util.showToast(this.copy.codeRequired);
				return;
			}
			if (this.newpassword.length < 6 || this.newpassword.length > 64) {
				this.util.showToast(this.copy.passwordInvalid);
				return;
			}
			this.submitting = true;
			tavernApi.confirmH5PasswordReset({
				requestId: this.requestId,
				email: this.email,
				code: this.captcha,
				newPassword: this.newpassword
			}).then(() => {
				this.util.showToast(this.copy.resetSuccess);
				this.navigateTimer = setTimeout(() => uni.navigateBack(), 700);
			}).catch(() => {
				this.util.showToast(this.copy.resetFailed);
			}).finally(() => {
				this.submitting = false;
			});
		}
	}
};
</script>

<style scoped lang="scss">
.auth-page {
	position: relative;
	min-height: 100vh;
	padding: calc(126rpx + env(safe-area-inset-top)) 36rpx 64rpx;
	box-sizing: border-box;
	overflow: hidden;
	color: #f8fafc;
}

.hero {
	position: relative;
	z-index: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 24rpx 0 38rpx;
}

.brand-logo { width: 138rpx; height: 138rpx; border-radius: 36rpx; box-shadow: 0 24rpx 60rpx rgba(15, 23, 42, 0.42); }
.hero-title { margin-top: 22rpx; font-size: 42rpx; font-weight: 700; }
.hero-subtitle { margin-top: 12rpx; max-width: 570rpx; text-align: center; font-size: 25rpx; line-height: 1.6; color: rgba(226, 232, 240, 0.84); }

.auth-card {
	position: relative;
	z-index: 1;
	padding: 32rpx 28rpx 30rpx;
	border-radius: 32rpx;
	background: rgba(15, 23, 42, 0.76);
	border: 1rpx solid rgba(148, 163, 184, 0.18);
	box-shadow: 0 26rpx 80rpx rgba(15, 23, 42, 0.34);
	backdrop-filter: blur(20rpx);
}

.field { margin-bottom: 22rpx; padding: 22rpx 24rpx 16rpx; border-radius: 24rpx; background: rgba(255, 255, 255, 0.05); border: 1rpx solid rgba(148, 163, 184, 0.16); }
.field-head, .code-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.field-label, .field-toggle { font-size: 24rpx; color: rgba(196, 181, 253, 0.94); }
.field-toggle { padding: 4rpx 0 4rpx 18rpx; }
.field-input { width: 100%; height: 72rpx; margin-top: 8rpx; font-size: 29rpx; color: #f8fafc; -webkit-text-fill-color: #f8fafc; }
.field-input--code { flex: 1; min-width: 0; }
.code-button { flex: 0 0 auto; min-width: 160rpx; padding: 16rpx 18rpx; border-radius: 18rpx; text-align: center; font-size: 23rpx; color: #e0f2fe; background: rgba(52, 143, 184, 0.3); border: 1rpx solid rgba(125, 211, 252, 0.24); }
.code-button--disabled { opacity: 0.52; }
.account-hint { display: block; font-size: 22rpx; line-height: 1.6; color: rgba(203, 213, 225, 0.78); }
.submit-wrap { margin-top: 28rpx; }
</style>
