import Vue from 'vue'
import Vuex from 'vuex'
Vue.use(Vuex)

const AUTH_STORAGE_KEYS = ['user', 'token', 'unreadTotal', 'tavern_viewer_membership_snapshot', 'tavern_client_uid'];

function clearAuthStorage() {
	AUTH_STORAGE_KEYS.forEach((key) => {
		try {
			uni.removeStorageSync(key);
		} catch (e) {}
	});
}

const store = new Vuex.Store({
	state: {
		token: uni.getStorageSync('token') || '',
		user: uni.getStorageSync('user') || {},
		unreadTotal: uni.getStorageSync('unreadTotal') || '',
		xieyi: uni.getStorageSync('xieyi') || '',
		/** 语言切换时自增，让全局 mixin 的 allText 计算属性重新求值（prototype 本身非响应式） */
		localeTick: 0,
		/** 与 languageType 一致：0 繁 1 简 2 英 … */
		localeIndex: 1
	},
	mutations: {
		setLocale(state, idx) {
			const n = typeof idx === 'number' && !isNaN(idx) ? idx : 1;
			state.localeIndex = n;
			state.localeTick += 1;
		},
		setxieyi(state, xieyi) {
			state.xieyi = xieyi
			uni.setStorageSync('xieyi', xieyi)
		},
		setUnreadTotal(state, unreadTotal) {
			state.unreadTotal = unreadTotal
			uni.setStorageSync('unreadTotal', unreadTotal)
		},
		setuser(state, user) {
			state.user = user
			uni.setStorageSync('user', user)
		},
		settoken(state, token) {
			state.token = token
			uni.setStorageSync('token', token)
		},
		clearAuth(state) {
			state.token = ''
			state.user = {}
			state.unreadTotal = ''
			clearAuthStorage()
		},
		userout(state) {
			state.token = ''
			state.user = {}
			state.unreadTotal = ''
			clearAuthStorage()
			uni.reLaunch({
				url: '/pages/login/login'
			})
		}
	},
	actions: {
		userout({ commit }) {
			commit('userout')
		}
	}
})
export default store
