const AUTH_STORAGE_KEYS = [
	'user',
	'token',
	'unreadTotal',
	'tavern_viewer_membership_snapshot',
	'tavern_client_uid'
];
const CHARACTER_ACCESS_REFRESH_FLAG_KEY = 'tavern_character_access_refresh_needed';
const LOGIN_PAGE = '/pages/login/login';
const REDIRECT_LOCK_MS = 2500;

let redirectLocked = false;
let redirectUnlockTimer = null;

function readStoredUser() {
	try {
		const user = uni.getStorageSync('user');
		return user && typeof user === 'object' ? user : {};
	} catch (e) {
		return {};
	}
}

function getStoredToken() {
	const user = readStoredUser();
	const userToken = user.token == null ? '' : String(user.token).trim();
	if (userToken) return userToken;
	try {
		return String(uni.getStorageSync('token') || '').trim();
	} catch (e) {
		return '';
	}
}

function hasStoredAuth() {
	return !!getStoredToken();
}

function normalizePageUrl(value) {
	const url = String(value || '').trim();
	if (!url || url.indexOf('/pages/') !== 0 || url.indexOf(LOGIN_PAGE) === 0) return '';
	return url;
}

function currentPageUrl() {
	try {
		const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
		const page = pages && pages.length ? pages[pages.length - 1] : null;
		if (!page) return '';
		const route = String(page.route || (page.$page && page.$page.route) || '').replace(/^\/+/, '');
		if (!route) return '';
		const options = page.options && typeof page.options === 'object' ? page.options : {};
		const query = Object.keys(options)
			.filter((key) => options[key] != null && String(options[key]) !== '')
			.map((key) => encodeURIComponent(key) + '=' + encodeURIComponent(String(options[key])))
			.join('&');
		return normalizePageUrl('/' + route + (query ? '?' + query : ''));
	} catch (e) {
		return '';
	}
}

function resolveAppVm() {
	try {
		const app = typeof getApp === 'function' ? getApp() : null;
		return app && app.$vm ? app.$vm : app;
	} catch (e) {
		return null;
	}
}

function clearAuthStorage() {
	AUTH_STORAGE_KEYS.forEach((key) => {
		try {
			uni.removeStorageSync(key);
		} catch (e) {}
	});
	try {
		uni.setStorageSync(CHARACTER_ACCESS_REFRESH_FLAG_KEY, 'auth-expired');
	} catch (e) {}
}

function clearAuthState() {
	const vm = resolveAppVm();
	let committed = false;
	try {
		if (vm && vm.$store && typeof vm.$store.commit === 'function') {
			vm.$store.commit('clearAuth');
			committed = true;
		}
	} catch (e) {}
	if (!committed) clearAuthStorage();
	else {
		try {
			uni.setStorageSync(CHARACTER_ACCESS_REFRESH_FLAG_KEY, 'auth-expired');
		} catch (e) {}
	}
	try {
		if (vm && vm.$socket && typeof vm.$socket.safeClose === 'function') {
			vm.$socket.safeClose();
		}
	} catch (e) {}
	try {
		if (uni && typeof uni.$emit === 'function') uni.$emit('auth-session-expired');
	} catch (e) {}
}

function languageCode() {
	try {
		const value = uni.getStorageSync('languageType');
		if (typeof value === 'string' && value) return value;
		return ['zh-hk', 'zh-cn', 'en', 'ko', 'ja'][Number(value)] || 'zh-cn';
	} catch (e) {
		return 'zh-cn';
	}
}

function loginMessage(expired) {
	const code = languageCode();
	const messages = expired
		? {
			'zh-cn': '登录已过期，请重新登录',
			'zh-hk': '登入已過期，請重新登入',
			en: 'Your session has expired. Please sign in again.',
			ko: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
			ja: 'ログインの有効期限が切れました。もう一度ログインしてください。'
		}
		: {
			'zh-cn': '请先登录',
			'zh-hk': '請先登入',
			en: 'Please sign in first.',
			ko: '먼저 로그인해 주세요.',
			ja: '先にログインしてください。'
		};
	return messages[code] || messages['zh-cn'];
}

function buildLoginUrl(redirectUrl) {
	const target = normalizePageUrl(redirectUrl) || currentPageUrl();
	return LOGIN_PAGE + (target ? '?redirect=' + encodeURIComponent(target) : '');
}

function lockRedirect() {
	if (redirectLocked) return false;
	redirectLocked = true;
	if (redirectUnlockTimer) clearTimeout(redirectUnlockTimer);
	redirectUnlockTimer = setTimeout(() => {
		redirectLocked = false;
		redirectUnlockTimer = null;
	}, REDIRECT_LOCK_MS);
	return true;
}

function redirectToLogin(options) {
	const opts = options && typeof options === 'object' ? options : {};
	const expired = opts.expired === true;
	if (!lockRedirect()) return false;
	const url = buildLoginUrl(opts.redirectUrl);
	clearAuthState();
	try {
		uni.hideLoading();
		uni.showToast({ title: loginMessage(expired), icon: 'none', duration: 1800 });
	} catch (e) {}
	uni.reLaunch({
		url,
		fail: () => {
			uni.redirectTo({ url });
		}
	});
	return true;
}

function handleAuthExpired(options) {
	if (!hasStoredAuth()) return false;
	return redirectToLogin(Object.assign({}, options || {}, { expired: true }));
}

function requireAuth(redirectUrl) {
	if (hasStoredAuth()) return true;
	redirectToLogin({ expired: false, redirectUrl });
	return false;
}

function isLegacyAuthExpiredCode(code) {
	const value = Number(code);
	return value === 4003 || value === 10001;
}

module.exports = {
	getStoredToken,
	hasStoredAuth,
	buildLoginUrl,
	redirectToLogin,
	handleAuthExpired,
	requireAuth,
	isLegacyAuthExpiredCode
};
