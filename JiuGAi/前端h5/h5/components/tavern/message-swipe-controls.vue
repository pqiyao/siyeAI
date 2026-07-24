<template>
	<view class="swipe-row">
		<text class="swipe-btn" @tap.stop="emitPrevious">&#8249;</text>
		<text class="swipe-num">{{ label }}</text>
		<text class="swipe-btn" @tap.stop="emitNext">&#8250;</text>
	</view>
</template>

<script>
	export default {
		name: 'MessageSwipeControls',
		props: {
			label: {
				type: String,
				default: ''
			}
		},
		methods: {
			emitPrevious() {
				this.$emit('previous');
			},
			emitNext() {
				this.$emit('next');
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: controls share one appearance in every chat runtime. */
	.swipe-row {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 16rpx;
		margin-top: 14rpx;
		padding-top: 14rpx;
		border-top: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
	}

	.swipe-btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 88rpx;
		min-height: 88rpx;
		font-size: 36rpx;
		color: var(--chat-content-accent, #d9adb9);
		padding: 0;
		line-height: 1;
	}

	.swipe-num {
		font-size: 22rpx;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
	}

</style>
