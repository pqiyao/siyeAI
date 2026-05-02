<script>
	import Vue from 'vue'
	import { refreshI18nViews } from '@/common/i18nRefresh.js'
	import { applyLocaleToDocument } from '@/common/localeHtml.js'
	import zhcn from '@/common/text/zh-cn.json'
	import en from '@/common/text/en.json'
	import zhhk from '@/common/text/zh-hk.json' 
	import ko from '@/common/text/ko.json'
	import ja from '@/common/text/ja.json'
	const tavernCompliance = require('@/common/tavernCompliance.js')
	export default {
		onLaunch: function() {
			console.log('App Launch')
			Vue.prototype.texts = [zhhk,zhcn,en,ko,ja];
			Vue.prototype.languageChange=function(){
				const raw = uni.getStorageSync('languageType');
				const codeMap = { 'zh-hk': 0, 'zh-cn': 1, 'en': 2, 'ko': 3, 'ja': 4 };
				let idx = 1;
				if (raw !== undefined && raw !== null && raw !== '') {
					idx = typeof raw === 'string' && codeMap[raw] !== undefined ? codeMap[raw] : Number(raw);
				}
				if (isNaN(idx) || idx < 0 || idx >= this.texts.length) idx = 1;
				const base = this.texts[idx];
				const merged = { ...base };
				// 角色广场页：无模块时整段回退；有模块时与 zh-cn/en 浅合并，补全新增 key
				const tavernFallback = idx >= 2 ? en.酒馆页 : zhcn.酒馆页;
				merged.酒馆页 = { ...tavernFallback, ...(merged.酒馆页 || {}) };
				const rechargeFallback = idx >= 2 ? en.充值页 : zhcn.充值页;
				merged.充值页 = { ...rechargeFallback, ...(merged.充值页 || {}) };
				const mineFallback = idx >= 2 ? en.我的页 : zhcn.我的页;
				merged.我的页 = { ...mineFallback, ...(merged.我的页 || {}) };
				Vue.prototype.allText = merged;
				try {
					if (this.$store && this.$store.commit) {
						this.$store.commit('setLocale', idx);
					}
				} catch (e) {}
				applyLocaleToDocument(idx);
				this.$forceUpdate();
				refreshI18nViews(merged);
			}
			this.languageChange();
			this.getxieyi()
			setTimeout(() => {
				tavernCompliance.ensureAgeConfirmed();
			}, 420);
		},
		onShow: function() {
			console.log('App Show')
			if (!this._canCallRemoteApi()) {
				return;
			}
			this.util.request('index/getCountryForIp', {}, 'POST').then((res) => {
				console.log(res.data);
				if (res.code == '1' && res.data) {
					this.checkLanguageRecommendation(res.data);
				}
			});
		},
		onHide: function() {
			console.log('App Hide')
		},
		methods: {
			/** H5 只要是 http(s) 正常站点访问，就允许调用当前站点 API；仅 file:// 等离线场景跳过 */
			_canCallRemoteApi() {
				// #ifdef H5
				try {
					const loc = (typeof window !== 'undefined' && window.location) || null;
					if (!loc) return false;
					const protocol = String(loc.protocol || '').toLowerCase();
					if (protocol && protocol !== 'http:' && protocol !== 'https:') return false;
					return !!String(loc.hostname || '').trim();
				} catch (e) {
					return false;
				}
				// #endif
				return true;
			},
			sanitizeHtml(html){
				if(!html) return ''
				if(typeof document !== 'undefined'){
					const container = document.createElement('div')
					container.innerHTML = html
					const walk = (node)=>{
						const children = Array.from(node.childNodes)
						for(const child of children){
							if(child.nodeType === 1){
								const tag = child.tagName.toLowerCase()
								if(tag === 'script' || tag === 'style'){
									child.remove()
									continue
								}
								for(const attr of child.getAttributeNames()){
									child.removeAttribute(attr)
								}
								walk(child)
							}else if(child.nodeType === 8){
								child.remove()
							}
						}
					}
					walk(container)
					return container.innerHTML
				}
				let s = html
				s = s.replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi,'')
				s = s.replace(/<style[\s\S]*?>[\s\S]*?<\/style>/gi,'')
				s = s.replace(/\s[\w:.-]+\s*=\s*("[\s\S]*?"|'[\s\S]*?')/g,'')
				return s
			},
			getxieyi() {
				if (!this._canCallRemoteApi()) {
					this.$store.commit('setxieyi', '');
					return;
				}
				this.util.request('index/article_info', { id: 1 }, 'POST').then((res) => {
					const safe = this.sanitizeHtml(res.data.content);
					this.$store.commit('setxieyi', safe);
				});
			},
			// 根据IP自动设置语言
			checkLanguageRecommendation(ipLanguage) {
				// 检查用户是否已手动选择过语言
				const userManuallySelected = uni.getStorageSync('userManuallySelectedLanguage');
				if(userManuallySelected) {
					return; // 用户已手动选择，不再自动设置
				}
				
				// 语言代码映射到语言索引
				const languageMap = {
					'zh-hk': 0, // 繁体中文
					'zh-cn': 1, // 简体中文
					'en': 2,    // English
					'ko': 3,    // 한국어
					'ja': 4     // 日本語
				};
				
				// 获取推荐的语言索引
				const recommendedIndex = languageMap[ipLanguage];
				if(recommendedIndex === undefined) {
					return; // 不支持的语言代码
				}
				
				// 获取当前语言设置
				const currentRawLanguage = uni.getStorageSync('languageType');
				let currentLanguage =
					typeof currentRawLanguage === 'string' && languageMap[currentRawLanguage] !== undefined
						? languageMap[currentRawLanguage]
						: (currentRawLanguage === undefined || currentRawLanguage === null || currentRawLanguage === ''
							? 1
							: Number(currentRawLanguage));
				if (isNaN(currentLanguage)) currentLanguage = 1;
				
				// 如果推荐语言与当前语言不同，直接设置
				if(recommendedIndex !== currentLanguage) {
					this.switchLanguage(recommendedIndex, false); // false表示不是用户手动选择
				}
			},
			
			// 切换语言
			switchLanguage(languageIndex, isManualSelection = true) {
				uni.setStorageSync('languageType', languageIndex);
				this.languageChange();
				
				// 如果是用户手动选择，记录标记
				if(isManualSelection) {
					uni.setStorageSync('userManuallySelectedLanguage', true);
				}
				
				// 如果用户已登录，同步到服务器
				const user = uni.getStorageSync('user');
				if(user && user.token) {
				const langCode = languageIndex === 0 ? 'zh-hk' : 
									 languageIndex === 1 ? 'zh-cn' : 
									 languageIndex === 2 ? 'en' : 
									 languageIndex === 3 ? 'ko' : 
									 'ja';
					
					this.util.request('user/updLang', {
						token: user.token,
						clang: langCode
					}).then(res => {
						console.log('语言切换成功');
					}).catch(err => {
						console.log('语言切换失败:', err);
					});
				}
				
				// 只有用户手动选择时才显示成功提示
				if(isManualSelection) {
					uni.showToast({
						title: this.allText.我的页.语言切换成功,
						icon: 'success'
					});
				}
			}
		}
	}
</script>

<style>
	/*每个页面公共css */
	@import "colorui/main.css";
	@import "colorui/icon.css";
	/*每个页面公共css */
	uni-toast {
	    z-index: 99999 !important; /* 根据实际情况进行调整 */
	}
	/* #ifdef H5 */
	html,
	body,
	#app,
	uni-app,
	uni-page,
	uni-page-wrapper,
	uni-page-body {
		background-color: #12121b !important;
	}
	html,
	body,
	#app,
	uni-app {
		min-height: 100%;
	}
	uni-page,
	uni-page-wrapper,
	uni-page-body {
		min-height: 100vh;
	}
	uni-tabbar {
		border-top: 1px solid rgba(255, 255, 255, 0.06) !important;
		box-shadow: 0 -6px 20px rgba(0, 0, 0, 0.25);
	}
	/* 语言切换：西文用拉丁栈，中日韩用中文栈（含韩日字形回退） */
	html.locale-latin uni-page-body {
		font-family: 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif !important;
	}
	html.locale-cjk uni-page-body {
		font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Malgun Gothic', 'Apple SD Gothic Neo',
			'Noto Sans SC', sans-serif !important;
	}
	/* #endif */

	/* App / 小程序：根节点带 localeFontClass 时切换字体（与 H5 html 类一致） */
	.locale-font-latin {
		font-family: 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif;
	}
	.locale-font-cjk {
		font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Malgun Gothic', 'Apple SD Gothic Neo',
			'Noto Sans SC', sans-serif;
	}
</style>
