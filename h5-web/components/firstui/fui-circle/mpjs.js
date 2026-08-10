// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 25  44，营业执照号： 914 40  6 05M    A C   0Y3 J   70Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
// #ifdef MP

export default {
	data() {
		return {
			context: null,
			start: 0
		}
	},
	watch: {
		percent(val) {
			setTimeout(() => {
				this.init();
			}, 50)
		},
		w(val) {
			setTimeout(() => {
				this.init();
			}, 50)
		},
		strokeWidth(val) {
			setTimeout(() => {
				this.init();
			}, 50)
		}
	},
	mounted() {
		this.$nextTick(() => {
			setTimeout(() => {
				this.init();
			}, 50)
		})
	},
	methods: {
		//初始化绘制
		init(init) {
			let start = this.activeMode === 'backwards' ? 0 : this.start;
			start = start > this.percent ? 0 : start;
			this.drawCircle(start);
		},
		//默认背景
		drawDefaultCircle(ctx) {
			let sw = Number(this.strokeWidth);
			// #ifdef MP-ALIPAY
			sw = sw * 4
			// #endif
			ctx.setLineWidth(sw);
			ctx.setStrokeStyle(this.background);
			let eAngle = Math.PI * 2 + Number(this.sAngle) * Math.PI;
			this.drawArc(ctx, eAngle, true);
		},
		drawCircle(start) {
			let ctx = this.context;
			if (!ctx) {
				ctx = uni.createCanvasContext(this.circleId, this);
				this.context = ctx;
			}
			//绘制默认背景
			this.defaultShow && this.drawDefaultCircle(ctx)
			let sw = Number(this.strokeWidth)
			// #ifdef MP-ALIPAY
			sw = sw * 4
			// #endif
			ctx.setLineWidth(sw);
			let gradient = this.foreground || this.primaryColor;
			// #ifndef MP-KUAISHOU
			if (this.gradient) {
				gradient = ctx.createLinearGradient(0, 0, Number(this.w), 0);
				gradient.addColorStop(0, this.gradient);
				gradient.addColorStop(1, this.foreground || this.primaryColor);
			}
			// #endif
			ctx.setStrokeStyle(gradient);
			let percentage = Number(this.percent)
			if (percentage > 0) {
				start += Number(this.speed);
				start = start > percentage ? percentage : start;
			}
			if (this.show) {
				let si = Number(this.size)
				// #ifdef MP-ALIPAY
				si = si * 4
				// #endif
				ctx.setFontSize(si);
				ctx.setFillStyle(this.color || this.primaryColor);
				ctx.setTextAlign('center');
				ctx.setTextBaseline('middle');
				let percent = `${this.counterclockwise ? 100 - start : start}%`;
				let radius = this.w / 2
				// #ifdef MP-ALIPAY
				radius = radius * 4
				// #endif
				ctx.fillText(percent, radius, radius);
			}
			if (percentage == 0 || (this.counterclockwise && start == 100)) {
				ctx.draw();
			} else {
				let eAngle = ((2 * Math.PI) / 100) * start + Number(this.sAngle) * Math.PI;
				this.drawArc(ctx, eAngle);
			}
			let time = 1000 / 60
			// #ifdef MP-ALIPAY
			time = time / 2
			// #endif
			setTimeout(() => {
				this.start = start;
				if (start == percentage) {
					this.$emit('end', {
						canvasId: this.circleId,
						percent: start
					});
				} else {
					this.drawCircle(start);
				}
				this.$emit('change', {
					percent: start
				});
			}, time);
		},
		//创建弧线
		drawArc(ctx, eAngle, def) {
			ctx.setLineCap(this.lineCap);
			ctx.beginPath();
			let radius = this.w / 2;
			let sw = Number(this.strokeWidth);
			// #ifdef MP-ALIPAY
			radius = radius * 4
			sw = sw * 4
			// #endif
			ctx.arc(radius, radius, radius - sw, Number(this.sAngle) * Math.PI, eAngle, this.counterclockwise);
			ctx.stroke();
			!def && ctx.draw();
		}
	}
}

// #endif

// #ifndef MP
export default {}
// #endif