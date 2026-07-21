<template>
	<view>
		<view class="bottomBox">
			<slot></slot>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				
			}
		},
		methods: {
			
		}
	}
</script>

<style scoped lang="scss">
.bottomBox{
	position: fixed;
	bottom: 0rpx;
	left: 0rpx;
	width: 750rpx;
}
</style>
