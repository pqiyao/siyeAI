// 入口：uni-app + Vue2；uView 全局组件；Vuex / util / websocket 挂 prototype
// #ifndef VUE3
import Vue from 'vue'
import App from './App'
import uView from 'uview-ui'
Vue.use(uView)
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
import socket from 'utils/websocket.js'
Vue.prototype.$socket = socket
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
