import {
	toLoadPath
} from './utils'
export class Player {
	imageBus = {}
	textBus = {}
	resetBus = []
	setVideoItemMap = new Map()
	constructor(component) {
		this.component = component
	}
	set loops(v) {
		this.component.rLoops = v
	}
	set clearsAfterStop(v) {
		this.component.rClearsAfterStop = v
	}
	set fillMode(v) {
		this.component.rFillMode = v
	}
	clear() {
		this.component.rClear = true
		this.reset('rClear')
	}
	pauseAnimation() {
		this.component.rPauseAnimation = true
		this.reset('rPauseAnimation')
	}
	reset(name) {
		if (!name) return
		if (name && !this.resetBus.includes(name)) {
			this.resetBus.push(name)
		}
		clearTimeout(this.resetTimer)
		this.resetTimer = setTimeout(() => {
			while (this.resetBus.length) {
				const name = this.resetBus.shift()
				this.component[name] = false
			}
		}, 30)
	}
	setVideoItem(videoItem) {
		const _this = this.component
		
		_this.rOnFinished = false
		const uid = `${new Date().getTime()}`;
		_this.rVideoItem = `${videoItem}|${uid}`
		
		return new Promise((resolve, reject) => {
			this.setVideoItemMap.set(uid, {
				resolve,
				reject,
				videoItem
			})
			// const shopWatch = _this.$watch('rSetVideoItem', (v) => {
			// 	if (v) {
			// 		resolve(true)
			// 		this.reset('rSetVideoItem')
			// 		shopWatch()
			// 	}
			// })
		})
	}
	startAnimation(reverse) {
		this.component.rStartAnimation = {
			reverse
		}
		this.reset('rStartAnimation')
	}
	stopAnimation(clear) {
		this.component.rStopAnimation = {
			clear
		}
		this.reset('rStopAnimation')
	}
	startAnimationWithRange(range, reverse) {
		this.component.rStartAnimationWithRange = {
			range,
			reverse
		}
		this.reset('rStartAnimation')
	}
	onFinished(cb) {
		const shopWatch = this.component.$watch('rOnFinished', v => {
			if (v) {
				cb()
				shopWatch()
			}
		})
	}
	onFrame(cb) {
		const shopWatch = this.component.$watch('rOnFrame', v => {
			cb(v)
			shopWatch()
		})
	}
	onPercentage(cb) {
		const shopWatch = this.component.$watch('rOnPercentage', v => {
			cb(v)
			shopWatch()
		})
	}
	stepToFrame(frame, andPlay = false) {
		console.log('未实现')
	}
	stepToPercentage(percentage, andPlay = false) {
		console.log('未实现')
	}
	setContentMode(contentMode) {
		this.component.rSetContentMode = contentMode
		this.reset('rSetContentMode')
	}
	setText(text, key) {
		clearTimeout(this.textTimer)
		if (!this.textBus[key]) {
			this.textBus[key] = text
		}
		this.textTimer = setTimeout(() => {
			this.component.rSetText = this.textBus
			this.textBus = {}
			this.reset('rSetText')
		}, 5)
	}
	setImage(src, key) {
		clearTimeout(this.imageTimer)
		if (!this.imageBus[key]) {
			this.imageBus[key] = src
		}
		this.imageTimer = setTimeout(() => {
			this.component.rSetImage = this.imageBus
			this.imageBus = {}
			this.reset('rSetImage')
		}, 5)
	}
}


export class Parser {
	map = new Map()
	constructor(component) {
		this.component = component
	}
	async load(src) {
		let url = ''
		url = await toLoadPath(src)
		return new Promise((resolve, reject)=>{
			const uid = `${new Date().getTime()}`; 
			const svgaURL = `${uid}@${url}`
			this.map.set(uid, {
				uid,
				src,
				url,
				svgaURL,
				videoItem: '',
				status: false,
				resolve,
				reject
			})
			const stop = this.component.$watch('rLoadSrc', (v)=>{
				const item = this.map.get(uid)
				if(!v && item){
					this.component.rLoadSrc = item.svgaURL
					stop&&stop()
				}
			},{immediate: true})
			
		})
	}
}


// const parser = new Parser('id', this)

// const videoItem = await parser.load("https://wzilu.oss-cn-shenzhen.aliyuncs.com/svga/svga_home_box_float_new.svga");