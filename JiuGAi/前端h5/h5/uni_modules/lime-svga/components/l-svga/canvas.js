import {base64ToPath} from './utils'
class Image {
	constructor() {
		this.currentSrc = null
		this.naturalHeight = 0
		this.naturalWidth = 0
		this.width = 0
		this.height = 0
		this.tagName = 'IMG'
	}
	set src(src) {
		if(/^data:/.test(src)) {
			base64ToPath(src).then(res => {
				this.currentSrc = res
				getImageInfo(res)
			})
		} else {
			this.currentSrc = src
			getImageInfo(src)
		}
		const getImageInfo = (src) => {
			uni.getImageInfo({
				src,
				success: (res) => {
					this.naturalWidth = this.width = res.width
					this.naturalHeight = this.height = res.height
					this.onload()
				},
				fail: (err) => {
					this.onerror(err)
				}
			})
		}
	}
	get src() {
		return this.currentSrc
	}
}
export class Canvas {
	constructor(canvasId, size, ctx, pixelRatio) {
		this.canvasId
		this.size = size
		this.ctx = ctx
		this.pixelRatio = pixelRatio
	}
	requestAnimationFrame(cb) {
		return setTimeout(() => {
				cb()
		}, 1000/30)
	}
	getContext() {
		const {ctx} = this
		ctx.uniDrawImage = ctx.drawImage
		ctx.drawImage = (...a) => {
			a[0] = a[0].src
			ctx.uniDrawImage(...a)
		}
		return ctx
	}
	createImage() {
		return new Image()
	}
	set width(w) {
		this.size.width = w
	}
	set height(h) {
		this.size.height = h
	}
	get width() {
		return this.size.width || 0
	}
	get height() {
		return this.size.height || 0
	}
}