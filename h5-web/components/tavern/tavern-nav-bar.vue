<template>
	<view class="tnb" :class="'tnb--' + mode" :style="{ paddingTop: statusBarH + 'px' }">
		<view class="tnb__inner">
			<view class="tnb__left">
				<slot name="left">
					<view v-if="showBack" class="tnb__back" @tap="onBack">
						<text class="tnb__back-icon">‹</text>
					</view>
				</slot>
			</view>
			<view class="tnb__center">
				<slot name="center">
					<text class="tnb__title">{{ title }}</text>
				</slot>
			</view>
			<view class="tnb__right">
				<slot name="right"></slot>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'TavernNavBar',
		props: {
			title: {
				type: String,
				default: ''
			},
			/** 保留 light/dark 以兼容旧页面；视觉统一为深色顶栏 */
			mode: {
				type: String,
				default: 'dark',
				validator: (v) => ['light', 'dark'].indexOf(v) !== -1
			},
			showBack: {
				type: Boolean,
				default: true
			}
		},
		data() {
			return {
				statusBarH: 20
			};
		},
		created() {
			try {
				const s = uni.getSystemInfoSync();
				this.statusBarH = s.statusBarHeight || 20;
			} catch (e) {
				this.statusBarH = 20;
			}
		},
		methods: {
			onBack() {
				this.$emit('back');
			}
		}
	};
</script>

<style scoped lang="scss">
	.tnb {
		flex-shrink: 0;
		width: 100%;
		box-sizing: border-box;
	}

	.tnb--light,
	.tnb--dark {
		background: rgba(255, 255, 255, 0.66);
		border-bottom: 1rpx solid $tavern-nav-dark-border;
		box-shadow: 0 10rpx 26rpx rgba(38, 57, 77, 0.08);
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
	}

	.tnb__inner {
		height: 88rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 12rpx 0 8rpx;
	}

	.tnb__left {
		min-width: 72rpx;
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: flex-start;
	}

	.tnb__center {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 8rpx;
	}

	.tnb__back {
		width: 72rpx;
		height: 72rpx;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.tnb__back-icon {
		font-size: 56rpx;
		font-weight: 300;
		color: $tavern-text-on-dark;
		line-height: 1;
		margin-top: -8rpx;
	}

	.tnb__title {
		width: 100%;
		text-align: center;
		font-size: 32rpx;
		font-weight: bold;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	/* #ifdef APP-PLUS */
	.tnb--light,
	.tnb--dark {
		background: rgba(255, 255, 255, 0.72) !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}
	/* #endif */

	.tnb--light .tnb__title,
	.tnb--dark .tnb__title {
		color: $tavern-text-on-dark;
	}

	.tnb__right {
		min-width: 72rpx;
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: flex-end;
	}
</style>
