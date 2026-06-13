<template>
		<view class="content">
			<text>222</text>
			 <l-svga ref="svga"></l-svga>
		</view>
</template>

<script>
	export default {
		data() {
			return {
				
			}
		},
		mounted() {
			this.$nextTick(()=>{
				this.$refs.svga.render(async (parser, player) => {
					const videoItem = await parser.load("/static/9572670e5a2d4877972219578255c926.svga");
					// const videoItem = await parser.load("https://moqu-1319976617.cos.ap-guangzhou.myqcloud.com/mo-qu/giftUrl/user-upload/2023-12-05/9572670e5a2d4877972219578255c926.svga");
					// const videoItem1 = await parser.load("https://moqu-1319976617.cos.ap-guangzhou.myqcloud.com/mo-qu/giftUrl/user-upload/2023-12-04/df8bca3068204a4abd56c11e3ad4f08b.svga");
					// const videoItem2 = await parser.load("https://moqu-1319976617.cos.ap-guangzhou.myqcloud.com/mo-qu/giftUrl/user-upload/2023-12-04/685fb23555794cd784108940351fe61c.svga");
					await player.setVideoItem(videoItem);
				    // 设置当前动画的循环次数，0代表无限循环 默认 0
				    player.loops = 0
					// player.setImage('http://wzilu.oss-cn-shenzhen.aliyuncs.com/admin/1657594273000.png', 'gift1');			
					// player.setImage('https://wzilu.oss-cn-shenzhen.aliyuncs.com/svga/acoustics_1.png', 'gift4');				
				    // 开始播放动画，reverse = true 时则反向播放。
				    player.startAnimation()
				    // 监听动画完成
				    player.onFinished(() => {
				        console.log('动画结束')
				    })
				})
			})
		},
		onLoad(option) {
			
		
		},
		methods: {
			
		},
	}
</script>

<style lang="scss">
	.content{
		height: 100vh;
		background: #333;
	}

</style>
