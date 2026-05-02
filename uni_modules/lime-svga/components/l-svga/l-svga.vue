<template>
	<view class="lime-svga">
		<!-- #ifndef APP-VUE || H5 || APP-NVUE-->
		<canvas class="lime-svga__canvas" v-if="use2dCanvas" :id="canvasId" type="2d"></canvas>
		<canvas class="lime-svga__canvas" v-else :canvas-id="canvasId" :id="canvasId"></canvas>
		<!-- #endif -->
		<!-- #ifdef APP-VUE || H5 -->
		<view 
		class="lime-svga__canvas" 
		:id="canvasId" 
		ref="webview" 
		:rload="rLoadSrc"
		:change:rload="svga.parserLoad" 
		:rVideoItem="rVideoItem"
		:change:rVideoItem="svga.setVideoItem"
		
		:rPlayer="rPlayer"
		:change:rPlayer="svga.onPlayer"
		></view>
		
		<!-- #endif -->
		<!-- #ifdef APP-NVUE -->
		<web-view 
		class="lime-svga__canvas" 
		ref="webview" 
		@pagefinish="onPageFinish"
		@error="onError"
		@onPostMessage="onMessage"
		src="/uni_modules/lime-svga/static/index.html"></web-view>
		<!-- #endif -->
	</view>
</template>
<script>
	import {ENV_TYPE, toLoadPath} from './utils'
	// #ifndef APP-VUE || H5 || APP-NVUE
	import { Parser, Player} from './svgaplayer.weapp'
	import {Canvas} from './canvas'
	// #endif
	// #ifdef APP-VUE || H5 
	import {Player, Parser} from './svga'
	import {Canvas} from './canvas'
	// #endif
	// #ifdef APP-NVUE
	import {Player, Parser} from './svga'
	// #endif
	export default {
		props: {
			type: {
				type: String,
				default: '2d'
			},
			src: String
		},
		data() {
			return {
				use2dCanvas: true,
				nIsInit: false,
				rIsLoad: false,
				rLoadSrc: null,
				rVideoItem: null,
				rSetVideoItem: false,
				rStartAnimation: null,
				rStopAnimation: null,
				rLoops: null,
				rClear: false,
				rPauseAnimation: null,
				rSetContentMode: null,
				rClearsAfterStop: true,
				rStartAnimationWithRange: null,
				rSetText: null,
				rSetImage: null,
				rOnFinished: false,
				rOnFrame: 0,
				rOnPercentage: 0,
				urlLoads: []
			}
		},
		computed: {
			rPlayer() {
				const {rClearsAfterStop, rSetContentMode, rLoops, rStartAnimation, rStopAnimation, rClear, rPauseAnimation, rStartAnimationWithRange, rSetText, rSetImage}= this
				return {rClearsAfterStop, rSetContentMode, rLoops, rStartAnimation, rStopAnimation, rClear, rPauseAnimation, rStartAnimationWithRange, rSetText, rSetImage}
			},
			canvasId() {
				if(this._) {
					return `lime-svga${this._.uid}`
				}
				return `lime-svga${this._uid}`
			},
		},
		// #ifdef APP-NVUE
		watch: {
			rLoadSrc(v){
				if(!v) return
				// this.$refs.webview.evalJS(`parserLoad("${v.split('@')[1]}")`)
				this.$refs.webview.evalJS(`parserLoad("${v}")`)
			},
			rVideoItem(v) {
				if(!v) return
				// this.$refs.webview.evalJS(`setVideoItem("${v.split('@')[0]}")`)
				this.$refs.webview.evalJS(`setVideoItem("${v}")`)
			},
			rPlayer: {
				handler(v){
					if(!v) return
					this.$refs.webview.evalJS(`onPlayer(${JSON.stringify(v)})`)
				},
			}
		},
		// #endif
		methods: {
			// #ifdef APP-NVUE
			onPageFinish() {
				this.$refs.webview.evalJS(`init()`)
			},
			// #endif
			onMessage(e) {
				const {detail: {data: [log]} = {}} = e 
				const {data, event} = log 
				if(Array.isArray(log)) {
					console.log(...log)
					return
				}
				if(event.onPercentage) {
					this.rOnPercentage = event.onPercentage
				}
				if(event.onFrame >= 0) {
					this.rOnFrame = event.onFrame
				}
				if(event.onFinished) {
					this.rOnFinished = true
				}
				if(event.inited) {
					this.nIsInit = true
				}
				if(event.load) {
					const [uid, videoItem, err] = event.load.split('@') 
					const item = this.parser.map.get(uid);
					if(item){
						item.videoItem = videoItem
						if(err){
							item.reject(err)
						} else if(videoItem){
							item.status = true
							item.resolve(videoItem)
						}
						 // this.rIsLoad = ''
						 this.rLoadSrc = ''
						 this.parser.map.delete(uid)
					}
					// this.rIsLoad = `${event.load}@${+new Date()}`
				}
				if(event.setVideoItem) {
					const [uid, type] = event.setVideoItem.split('|') 
					const item =  this.player.setVideoItemMap.get(uid)
					if(item){
						if( type == 'true'){
							item.resolve(true)
						} else {
							item.reject(false)
						}
						this.player.setVideoItemMap.delete(uid)
					}
					// this.rSetVideoItem = true
				}
			},
			setSvga(cb){
				cb(this.parser, this.player)
			},
			getContext() {
				return new Promise((resolve) => {
					// #ifndef APP-NVUE
					const {pixelRatio} = uni.getSystemInfoSync()
					if(this.use2dCanvas) {
						uni.createSelectorQuery()
							.in(this)
							.select(`#${this.canvasId}`)
							.fields({ node: true, size: true })
							.exec(res => {
								let {node: canvas, width, height} = res[0]
								resolve({canvas, width, height ,pixelRatio})
							})
					} else {
						uni.createSelectorQuery()
							.in(this)
							.select(`#${this.canvasId}`)
							.boundingClientRect()
							.exec(res => {
								if(res) {
									const {width, height} = res[0]
									const ctx = uni.createCanvasContext(this.canvasId, this)
									const canvas = new Canvas(this.canvasId, res[0], ctx, pixelRatio)
									resolve({canvas, width, height ,pixelRatio:1})
								}
							})
					}
					// #endif
					// #ifdef APP-NVUE
					if(this.nIsInit) return resolve(this.nIsInit)
					this.$watch('nIsInit', (v) => {
						if(v) {
							resolve(v)
						}
					})
					// #endif
				})
			},
			async render(cb) {
				await this.$nextTick()
				const confing = await this.getContext()
				// #ifndef APP-VUE || H5 || APP-NVUE
				if(!this.parser) {
					this.parser = new Parser;
				}
				if(!this.player) {
					this.player = new Player;
				}
				this.player.setCanvas(confing, `#${this.canvasId}` ,this)
				if(!this.parser.load2) {
					this.parser.load2 = this.parser.load
					this.parser.load = async (src) => {
						return this.parser.load2(await toLoadPath(src)) 
					}
				}
				cb(this.parser, this.player)
				// #endif
				
				// #ifdef APP-VUE || H5 || APP-NVUE
				
				if(!this.parser){
					this.parser = new Parser(this)
				}
				// const _this = this
				// if(!this.parser) {
				// 	this.parser = {
				// 		load: async (src) => {
				// 			const url = await toLoadPath(src)
							
				// 			_this.rLoadSrc = `svga${+new Date()}@${url}`
				// 			return new Promise(resolve => {
				// 				_this.stopWatch = _this.$watch('rIsLoad', (v, o) => {
				// 					if(v != o) {
				// 						_this.stopWatch()
				// 						resolve(v)
				// 					}
				// 				})
				// 			})
				// 		}
				// 	}
				// }
				this.player = new Player(this)
				cb(this.parser, this.player)
				// #endif
			}
		},
		mounted() {
			this.$watch(() => this.src, (v) => {
				if(v) {
					this.render(async (parser, player) => {
						const videoItem = await parser.load(this.src);
						await player.setVideoItem(videoItem);
						player.startAnimation()
					})
				}
			}, {immediate: true})
			
		},
		created() {
			this.use2dCanvas = this.type === '2d' && ENV_TYPE()
		},
		beforeDestroy() {
			if(this.player) {
				this.player.stopAnimation(true)
				this.player.clear()
			}
		}
	}
</script>
<!-- #ifdef APP-VUE  || H5-->
<script module="svga" lang="renderjs">
	// #ifdef APP-VUE || H5
	import {getfileBase64} from './utils'
	// #ifdef VUE2
	import { Parser, Player } from '../../static/svgaplayer.web'  
	// #endif
	// #ifdef VUE3
	// import { Parser, Player } from 'svgaplayerweb'
	let Parser, Player;
	// #endif
	export default{
		data() {
			return {
				isInit: null,
				rparser: null,
				rplayer: null,
				clearsAfterStop: true,
				videoItem: {},
				map: {}
			}
		},
		// #ifdef VUE3
		created() {
			const script = document.createElement('script')
			script.src = 'uni_modules/lime-svga/static/svgaplayer.web.js';
			script.onload = () => {
				Parser = SVGA.Parser
				Player = SVGA.Player
				this.init()
			}
			document.head.appendChild(script)
		},
		// #endif
		mounted() {
			// #ifdef VUE2
			this.init()
			// #endif
		},
		unmounted() {
			// console.log('unmounted')
		},
		beforeUnmount() {
			// console.log('beforeUnmount')
			if(this.rplayer) {
				this.rplayer.stopAnimation(true)
				this.rplayer.clear()
			}
		},
		beforeDestroy() {
			if(this.rplayer) {
				this.rplayer.stopAnimation(true)
				this.rplayer.clear()
			}
		},
		destroyed() {
			// console.log('destroy')
		},
		methods: {
			init() {
				this.$nextTick(() => {
					const webview = this.$ownerInstance.$el.querySelector('.lime-svga__canvas')
					const canvasid = webview.id 
					const div = document.createElement('div')
					const id = `${canvasid}div`
					div.id = id
					div.style = 'height: 100%'
					if(webview.appendChild) {
						webview.appendChild(div)
					}
					if(!this.rplayer) {
						this.rplayer = new Player(`#${id}`);
						this.rplayer.onFinished(() => {
							this.emit({onFinished: true})
						})
						this.rplayer.onFrame(number => {
							this.emit({onFrame: number})
						})
						this.rplayer.onPercentage(number => {
							this.emit({onPercentage: number})
						})
					}
					if(!this.rparser) {
						this.rparser = new Parser(`#${id}`);
					}
					this.isInit = true
				})
			},
			emit(event) {
				this.$ownerInstance.callMethod('onMessage', {
					detail: {
						data: [
							{
								event
							}
						]
					}
				})
			},
			parserLoad(newValue) {
				if(newValue && !this.rparser) {
					this.$watch('isInit', () => {
						this.load(newValue)
					})
				}
				if(this.rparser && newValue) {
					this.load(newValue)
				}
			},
			load(newValue) {
				// const url = /@/.test(newValue) ? newValue.split('@')[1]: newValue
				const [uid, url] = newValue.split('@')
				if(!uid || !url) {
					this.emit({load: `${uid}@null@svga src不存在`})
				}
				if(this.map[url]) {
					// this.emit({load: this.map[url]})
					this.emit({load: `${uid}@${this.map[url]}`})
				} else {
					const load = (url) => {
						this.rparser.load(url, (videoItem) => {
							const key = `video${+new Date()}`
							this.map[url] = key
							if(!this.videoItem[key]) {
								this.videoItem[key] = videoItem
							}
							this.emit({load: `${uid}@${key}`})
						}, (err)=>{
							this.emit({load: `${uid}@null@${JSON.stringify(err)}`})
						})
					}
					// #ifdef APP-PLUS
					if(/^http|\/\//.test(url) || /^(?:_www|_doc|file:\/\/|\/storage\/)/.test(url)){
						load(url)
					} else {
						getfileBase64(url).then(load).catch((err) => {
							this.emit({load: `${uid}@null@${JSON.stringify(err)}`})
						})
					}
					// #endif
					// #ifdef H5
					load(url)
					// #endif
				}
			},
			setVideoItem(newValue) {
				if(!newValue){
					return
				}
				const [videoItem, uid] = newValue.split('|')
				// if(newValue) {
				// 	newValue = newValue.split('@')[0]
				// }
				if(videoItem && this.videoItem[videoItem] && this.rplayer) {
					this.rplayer.setVideoItem(this.videoItem[videoItem])
					this.emit({setVideoItem: `${uid}|true`})
				}
			},
			startAnimation(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.startAnimation(newValue.reverse||false)
				}
			},
			stopAnimation(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.stopAnimation()
				}
			},
			setLoops(newValue) {
				if(newValue >= 0 && this.rplayer) {
					this.rplayer.loops = newValue
				}
			},
			clear(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.clear()
				}
			},
			pauseAnimation(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.pauseAnimation()
				}
			},
			startAnimationWithRange(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.startAnimationWithRange(newValue.range, newValue.reverse)
				}
			},
			setContentMode(newValue) {
				if(newValue && this.rplayer) {
					this.rplayer.setContentMode(newValue)
				}
			},
			onPlayer(newValue) {
				if(typeof newValue.rLoops == 'number' && this.rLoops2 != newValue.rLoops) {
					this.rLoops2 = newValue.rLoops
					this.setLoops(newValue.rLoops)
				}
				if(newValue.rStartAnimation) {
					this.startAnimation(newValue.rStartAnimation)
				}
				if(newValue.rStopAnimation) {
					this.stopAnimation(newValue.rStopAnimation)
				}
				if(newValue.rClear) {
					this.clear(newValue.rClear)
				}
				if(newValue.rPauseAnimation) {
					this.pauseAnimation(newValue.rPauseAnimation)
				}
				if(newValue.rStartAnimationWithRange) {
					this.startAnimationWithRange(newValue.rStartAnimationWithRange)
				}
				if(newValue.rSetContentMode) {
					this.setContentMode(newValue.rSetContentMode)
				}
				if(newValue.rClearsAfterStop != this.clearsAfterStop && this.rplayer) {
					this.clearsAfterStop = newValue.rClearsAfterStop
					this.rplayer.clearsAfterStop = newValue.rClearsAfterStop
				}
				if(newValue.rSetText && this.setText != newValue.rSetText) {
					this.setText = newValue.rSetText
					for (let key in this.setText) {
						this.rplayer && this.rplayer.setText(this.setText[key], key)
					}
				}
				if(newValue.rSetImage && this.setImage != newValue.rSetImage) {
					this.setImage = newValue.rSetImage
					for (let key in this.setImage) {
						this.rplayer && this.rplayer.setImage(this.setImage[key], key)
					}
				}
			}
		}
	}
	// #endif
</script>
<!-- #endif -->
<style lang="stylus">
	.lime-svga,
	.lime-svga__canvas {
		// #ifndef APP-NVUE
		width: 100%;
		height: 100%;
		// #endif
		// #ifdef APP-NVUE
		flex: 1;
		// #endif
	}
</style>
