<template>
	<!--本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 2  544，营业执照号： 9 14406  0 5MA C      0Y 3J 7 0  Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。-->
	<view class="fui-spin__wrap" :style="{width:width?width+'rpx':'auto',height:height?height+'rpx':'auto'}"
		ref="fui_spin_ani">
		<slot></slot>
	</view>
</template>

<script>
	// #ifdef APP-NVUE
	const animation = uni.requireNativePlugin('animation');
	// #endif
	export default {
		name: "fui-spin",
		props: {
			width: {
				type: [Number, String],
				default: 0
			},
			height: {
				type: [Number, String],
				default: 0
			}
		},
		data() {
			return {
				// #ifdef APP-NVUE
				deg: 0,
				stop: false
				// #endif
			};
		},
		// #ifdef APP-NVUE
		mounted() {
			this.$nextTick(() => {
				setTimeout(() => {
					this.deg += 360;
					this._animation()
				}, 50)
			})
		},
		// #endif
		// #ifdef APP-NVUE
		// #ifndef VUE3
		beforeDestroy() {
			this.deg = 0;
			this.stop = true;
		},
		// #endif
		// #ifdef VUE3
		beforeUnmount() {
			this.deg = 0;
			this.stop = true;
		},
		// #endif
		// #endif
		methods: {
			// #ifdef APP-NVUE
			_animation() {
				if (!this.$refs['fui_spin_ani'] || this.stop) return;
				animation.transition(
					this.$refs['fui_spin_ani'].ref, {
						styles: {
							transform: `rotate(${this.deg}deg)`
						},
						duration: 800, //ms
						timingFunction: 'linear',
						iterationCount: 'infinite',
						needLayout: false,
						delay: 0 //ms
					}, () => {
						this.deg += 360;
						this._animation()
					}
				);
			}
			// #endif
		}
	}
</script>

<style scoped>
	.fui-spin__wrap {
		/* #ifndef APP-NVUE */
		display: inline-flex;
		/* #endif */
		align-items: center;
		justify-content: center;
		flex-direction: row;

		/* #ifndef APP-NVUE */
		animation: spin 0.85s linear infinite;
		/* #endif */
	}


	/* #ifndef APP-NVUE */
	@-webkit-keyframes spin {
		0% {
			transform: rotate(0deg);
		}

		100% {
			transform: rotate(360deg);
		}
	}

	@keyframes spin {
		0% {
			transform: rotate(0deg);
		}

		100% {
			transform: rotate(360deg);
		}
	}

	/* #endif */
</style>