<template>
	<view class="user-voice-row">
		<view class="user-voice-card" :class="cardClass" @tap.stop="emitToggle">
			<view class="user-voice-wave">
				<text v-for="n in 4" :key="'user_voice_bar_' + messageId + '_' + n" class="user-voice-bar"></text>
			</view>
			<text class="user-voice-duration">{{ durationLabel }}</text>
		</view>
		<view v-if="transcriptText" class="user-voice-transcript-wrap">
			<text
				class="user-voice-transcript"
				@touchstart="emitPressStart"
				@touchmove="emitPressMove"
				@touchend="emitPressEnd"
				@touchcancel="emitPressCancel"
			>{{ transcriptText }}</text>
			<text
				v-if="canEdit"
				class="user-edit-tag user-edit-tag--voice"
				@tap.stop="emitEdit"
			>{{ editLabel }}</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'VoiceMessageCard',
		props: {
			messageId: {
				type: [String, Number],
				default: ''
			},
			cardClass: {
				type: [String, Array, Object],
				default: ''
			},
			durationLabel: {
				type: String,
				default: ''
			},
			transcriptText: {
				type: String,
				default: ''
			},
			canEdit: {
				type: Boolean,
				default: false
			},
			editLabel: {
				type: String,
				default: ''
			}
		},
		methods: {
			emitToggle() {
				this.$emit('toggle');
			},
			emitEdit() {
				this.$emit('edit');
			},
			emitPressStart(event) {
				this.$emit('press-start', event);
			},
			emitPressMove(event) {
				this.$emit('press-move', event);
			},
			emitPressEnd(event) {
				this.$emit('press-end', event);
			},
			emitPressCancel(event) {
				this.$emit('press-cancel', event);
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: the card inherits the shared user-message surface. */
	@keyframes voice-status-wave {
		0%,
		100% {
			transform: scaleY(0.56);
			opacity: 0.7;
		}
		50% {
			transform: scaleY(1.06);
			opacity: 1;
		}
	}

	.user-voice-row {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		justify-content: flex-end;
		gap: 10rpx;
	}

	.user-voice-card {
		min-width: 228rpx;
		max-width: 460rpx;
		height: 84rpx;
		padding: 0 24rpx;
		display: inline-flex;
		align-items: center;
		justify-content: space-between;
		gap: 20rpx;
		border-radius: 26rpx 26rpx 12rpx 26rpx;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
		box-shadow: none;
	}

	.user-voice-card--playing {
		background: var(--chat-content-surface-strong, rgba(255, 255, 255, 0.09));
		border-color: var(--chat-content-accent, #d9adb9);
	}

	.user-voice-card--error {
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border-color: var(--chat-content-danger, #dfa3ab);
	}

	.user-voice-transcript-wrap {
		max-width: 460rpx;
		display: flex;
		align-items: flex-start;
		justify-content: flex-end;
		gap: 12rpx;
	}

	.user-voice-transcript {
		flex: 1;
		min-width: 0;
		font-size: 24rpx;
		line-height: 1.65;
		color: var(--chat-content-primary, #f0eef0);
		text-align: left;
		white-space: normal;
		word-break: break-word;
	}

	.user-voice-wave {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 8rpx;
	}

	.user-voice-bar {
		width: 8rpx;
		height: 24rpx;
		border-radius: 999rpx;
		background: var(--chat-content-primary, #f0eef0);
		transform-origin: center bottom;
	}

	.user-voice-bar:nth-child(2) {
		height: 34rpx;
		animation-delay: 0.12s;
	}

	.user-voice-bar:nth-child(3) {
		height: 20rpx;
		animation-delay: 0.24s;
	}

	.user-voice-bar:nth-child(4) {
		height: 30rpx;
		animation-delay: 0.36s;
	}

	.user-voice-duration {
		flex-shrink: 0;
		font-size: 24rpx;
		font-weight: 700;
		color: var(--chat-content-primary, #f0eef0);
	}

	.user-voice-row .user-edit-tag {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
		text-align: right;
	}

	.user-edit-tag--voice {
		margin-top: 2rpx;
		flex-shrink: 0;
	}

	.user-voice-card--playing .user-voice-bar {
		animation: voice-status-wave 1.2s ease-in-out infinite;
	}

</style>
