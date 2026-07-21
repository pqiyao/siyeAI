<template>
	<!--本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 2 5 44，营业执照号：9  144 060   5MA C0 Y  3 J  7    0Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。-->
	<view :style="{ height: statusBarHeight,zIndex:isFixed?zIndex:1,background:background }" class="fui-status__bar"
		:class="{'fui-status__bar-fixed':isFixed}">
		<slot />
	</view>
</template>

<script>
	var statusBarHeight = uni.getSystemInfoSync().statusBarHeight + 'px'
	export default {
		name: "fui-status-bar",
		emits: ['init'],
		props: {
			//背景色
			background: {
				type: String,
				default: 'transparent'
			},
			//是否固定在顶部
			isFixed: {
				type: Boolean,
				default: false
			},
			//z-index值，isFixed为true时生效
			zIndex: {
				type: Number,
				default: 99
			}
		},
		data() {
			return {
				statusBarHeight
			};
		},
		created() {
			this.$emit('init', {
				statusBarHeight: statusBarHeight
			})
		}
	}
</script>

<style scoped>
	.fui-status__bar {
		/* #ifndef APP-NVUE */
		width: 100%;
		/* #endif */
		height: 20px;
		overflow: hidden;
	}

	.fui-status__bar-fixed {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
	}
</style>