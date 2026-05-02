// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID：2 54  4，营业执照号：  914 4  0 60   5M A  C  0Y3  J 70Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
// #ifndef APP-PLUS || MP-WEIXIN || H5
export default {
	data() {
		return {
			startX: 0,
			startY: 0,
			lastLeft: 0,
			lastTop: 0,
			transform: ''
		}
	},
	watch: {
		resetNum(val) {
			if (val > 0) {
				this.startX = 0;
				this.startY = 0;
				this.lastLeft = 0;
				this.lastTop = 0;
				this.transform = 'translate3d(0,0,0)'
			}
		}
	},
	methods: {
		touchstart(e) {
			if (!this.isDrag) return;
			const touch = e.touches || e.changedTouches
			this.startX = touch[0].clientX
			this.startY = touch[0].clientY
		},
		touchmove(e) {
			if (!this.isDrag) return;
			const touch = e.touches || e.changedTouches
			let pageX = touch[0].clientX,
				pageY = touch[0].clientY;

			var left = pageX - this.startX + this.lastLeft;
			left = left < -this.eLeft ? -this.eLeft : left;
			left = left > this.maxWidth ? this.maxWidth : left;
			this.startX = pageX

			var top = pageY - this.startY + this.lastTop;
			top = top < -this.eTop ? -this.eTop : top;
			top = top > this.maxHeight ? this.maxHeight : top;
			this.startY = pageY


			this.lastLeft = left
			this.lastTop = top
			this.transform = `translate3d(${left}px,${top}px,0)`
		}
	}
}

// #endif

// #ifdef APP-PLUS|| MP-WEIXIN || H5
export default {}
// #endif