// 入口：uni-app + Vue2；uView 全局组件；Vuex / util / websocket 挂 prototype
// #ifndef VUE3
import Vue from 'vue'
import App from './App'
import uView from 'uview-ui'
import Live2DCompanion from '@/components/Live2DCompanion/index.vue'
const companionStore = require('@/common/companionStore.js')
Vue.use(uView)
Vue.component('live2d-companion', Live2DCompanion)
import store from './store'
Vue.prototype.$store = store
Vue.mixin({
	computed: {
		allText() {
			void this.$store.state.localeTick;
			return Vue.prototype.allText || {};
		},
		/** 英/德/法用拉丁栈，其余用中文栈（含日韩回退）；页面根节点可加 :class="localeFontClass" */
		localeFontClass() {
			const i = this.$store.state.localeIndex;
			const latin = [2];
			return latin.indexOf(i) !== -1 ? 'locale-font-latin' : 'locale-font-cjk';
		}
	}
})
let cachedSocketModule = null
function resolveSocketModule() {
	if (cachedSocketModule) return cachedSocketModule
	const mod = require('@/utils/websocket.js')
	cachedSocketModule = mod && mod.default ? mod.default : mod
	return cachedSocketModule
}
Object.defineProperty(Vue.prototype, '$socket', {
	configurable: true,
	enumerable: false,
	get() {
		return resolveSocketModule()
	}
})
import util from '@/common/util.js'
Vue.prototype.util = util
var api = require('common/api.js')
Vue.prototype.$getimgsrc = function (url) {
	let urlk = url
	let str = RegExp('http')
	let newUrl
	if (str.test(urlk)) {
		newUrl = urlk
	} else {
		const base = String(api.img_url || '').replace(/\/+$/, '')
		const path = String(urlk || '').replace(/^\/+/, '')
		newUrl = base + '/' + path
	}
	return newUrl
}
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
	...App
})
app.$mount()
// #ifdef H5
try {
	var mountGlobalLive2DCompanion = function () {
		if (typeof window === 'undefined' || typeof document === 'undefined') return
		if (document.getElementById('global-live2d-companion-host')) return
		var hostEl = document.createElement('div')
		hostEl.id = 'global-live2d-companion-host'
		document.body.appendChild(hostEl)
		var hiddenRoutes = {
			'pages/login/login': true,
			'pages/login/reg': true,
			'pages/user/pay': true,
			'pages/user/gamePlay': true
		}
		var routeLayout = function (route) {
			if (route === 'pages/tavern/tavernChat') {
				return { avoidBottom: 92, compact: false }
			}
			if (route === 'pages/user/live2dSetting') {
				return { avoidBottom: 32, compact: false }
			}
			if (route === 'pages/user/user') {
				return { avoidBottom: 104, compact: false }
			}
			return { avoidBottom: 92, compact: false }
		}
		new Vue({
			data: function () {
				return {
					active: false,
					compact: false,
					avoidBottom: 92,
					route: '',
					overrideLayout: {},
					config: companionStore.getConfig(),
					timer: null
				}
			},
			created: function () {
				var self = this
				uni.$on(companionStore.EVENTS.configChanged, function (config) {
					self.config = Object.assign({}, config || companionStore.getConfig())
					self.refreshLayout()
				})
				uni.$on(companionStore.EVENTS.layoutChanged, function (layout) {
					self.overrideLayout = Object.assign({}, self.overrideLayout, layout || {})
					self.refreshLayout()
				})
			},
			mounted: function () {
				var self = this
				this.refreshLayout()
				this.timer = setInterval(function () {
					self.refreshLayout()
				}, 350)
			},
			beforeDestroy: function () {
				if (this.timer) clearInterval(this.timer)
				this.timer = null
			},
			methods: {
				currentRoute: function () {
					try {
						var pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
						var page = pages && pages.length ? pages[pages.length - 1] : null
						return page && page.route ? String(page.route) : ''
					} catch (e) {
						return ''
					}
				},
				isChatInputFocused: function () {
					if (this.route !== 'pages/tavern/tavernChat') return false
					try {
						var el = document.activeElement
						if (!el) return false
						var tag = String(el.tagName || '').toLowerCase()
						var cls = String(el.className || '')
						return (tag === 'textarea' || tag === 'input') && /inp|uni-textarea/.test(cls)
					} catch (e) {
						return false
					}
				},
				refreshLayout: function () {
					var nextRoute = this.currentRoute()
					if (nextRoute && nextRoute !== this.route) {
						this.route = nextRoute
						this.overrideLayout = {}
					}
					var base = routeLayout(this.route)
					var merged = Object.assign({}, base, this.overrideLayout || {})
					if (this.isChatInputFocused()) {
						merged.compact = true
						merged.avoidBottom = 150
					}
					var hidden = hiddenRoutes[this.route] === true || merged.hidden === true
					this.config = companionStore.getConfig()
					this.active = this.config.enabled === true && !hidden
					this.compact = merged.compact === true
					this.avoidBottom = Number(merged.avoidBottom || base.avoidBottom || 92)
				}
			},
			render: function (h) {
				return h(Live2DCompanion, {
					props: {
						active: this.active,
						compact: this.compact,
						avoidBottom: this.avoidBottom
					}
				})
			}
		}).$mount(hostEl)
	}
	if (document.body) {
		mountGlobalLive2DCompanion()
	} else {
		document.addEventListener('DOMContentLoaded', mountGlobalLive2DCompanion, { once: true })
	}
	// #ifdef H5
	var flushBootSplash = function () {
		if (typeof window === 'undefined') return
		var requestHide =
			typeof window.__requestBootSplashHide === 'function'
				? window.__requestBootSplashHide
				: window.__hideBootSplash
		if (typeof requestHide !== 'function') return
		requestHide()
	}
	if (typeof Vue !== 'undefined' && typeof Vue.nextTick === 'function') {
		Vue.nextTick(function () {
			flushBootSplash()
		})
	} else {
		flushBootSplash()
	}
	// #endif
} catch (e) {}
// #endif
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
import App from './App'
export function createApp() {
	const app = createSSRApp(App)
	return { app }
}
// #endif
