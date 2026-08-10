<template>
	<view class="assistant-voice-row">
		<view class="assistant-voice-pill" :class="pillClass" @tap.stop="emitToggle">
			<view class="assistant-voice-pill-dot"></view>
			<text class="assistant-voice-pill-text">{{ label }}</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'AssistantVoicePill',
		props: {
			label: {
				type: String,
				default: ''
			},
			pillClass: {
				type: [String, Array, Object],
				default: ''
			}
		},
		methods: {
			emitToggle() {
				this.$emit('toggle');
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: these selectors depend on the shared chat message surface. */
	@keyframes assistant-voice-pulse {
		0% {
			transform: scale(0.92);
			opacity: 0.72;
		}
		60% {
			transform: scale(1.1);
			opacity: 1;
		}
		100% {
			transform: scale(0.92);
			opacity: 0.72;
		}
	}

	.assistant-voice-row {
		display: flex;
		align-items: center;
		justify-content: flex-start;
		margin-top: 16rpx;
	}

	.assistant-voice-pill {
		display: inline-flex;
		align-items: center;
		gap: 10rpx;
		min-height: 52rpx;
		padding: 0 18rpx;
		border-radius: 999rpx;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08),
			0 10rpx 26rpx rgba(4, 12, 22, 0.16);
	}

	.assistant-voice-pill-dot {
		flex-shrink: 0;
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		background: var(--chat-content-accent, #d9adb9);
		box-shadow: 0 0 0 8rpx rgba(141, 232, 240, 0.12);
	}

	.assistant-voice-pill-text {
		font-size: 22rpx;
		font-weight: 600;
		letter-spacing: 0;
		color: var(--chat-content-primary, #f0eef0);
	}

	.assistant-voice-pill--loading .assistant-voice-pill-dot {
		background: var(--chat-content-accent, #d9adb9);
		box-shadow: 0 0 0 8rpx rgba(247, 172, 197, 0.12);
		animation: assistant-voice-pulse 1.1s ease-in-out infinite;
	}

	.assistant-voice-pill--playing {
		background: var(--chat-content-surface-strong, rgba(255, 255, 255, 0.09));
		border-color: var(--chat-content-accent, #d9adb9);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.14),
			0 12rpx 28rpx rgba(52, 143, 184, 0.16);
	}

	.assistant-voice-pill--playing .assistant-voice-pill-dot {
		background: var(--chat-content-primary, #f0eef0);
		box-shadow: 0 0 0 8rpx rgba(236, 251, 255, 0.16);
	}

	.assistant-voice-pill--error {
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border-color: var(--chat-content-danger, #dfa3ab);
	}

	.assistant-voice-pill--error .assistant-voice-pill-dot {
		background: var(--chat-content-danger, #dfa3ab);
		box-shadow: 0 0 0 8rpx rgba(255, 182, 197, 0.14);
	}

</style>
