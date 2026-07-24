<template>
	<view v-if="hasActions" class="message-actions">
		<view v-if="streaming" class="stream-inline" :class="{ 'stream-inline--app-plus': isAppPlus }">
			<view class="stream-inline-wave">
				<text v-for="n in 3" :key="'stream_inline_' + messageId + '_' + n" class="stream-inline-bar"></text>
			</view>
			<text class="stream-inline-text">{{ streamingStatusText }}</text>
		</view>
		<assistant-voice-pill
			v-if="showAssistantVoice"
			:label="assistantVoiceLabel"
			:pill-class="assistantVoicePillClass"
			@toggle="emitAssistantVoiceToggle"
		></assistant-voice-pill>
		<message-swipe-controls
			v-if="showSwipeControls"
			:label="swipeLabel"
			@previous="emitSwipePrevious"
			@next="emitSwipeNext"
		></message-swipe-controls>
		<generation-recovery-card
			v-if="recovery"
			:recovery="recovery"
			:primary-label="recoveryPrimaryLabel"
			:regen-label="recoveryRegenLabel"
			:copy-label="recoveryCopyLabel"
			:can-copy="canCopyRecovery"
			@primary="emitRecoveryPrimary"
			@regen="emitRecoveryRegen"
			@copy="emitRecoveryCopy"
			@close="emitRecoveryClose"
		></generation-recovery-card>
	</view>
</template>

<script>
	import AssistantVoicePill from '@/components/tavern/assistant-voice-pill.vue';
	import MessageSwipeControls from '@/components/tavern/message-swipe-controls.vue';
	import GenerationRecoveryCard from '@/components/tavern/generation-recovery-card.vue';

	export default {
		name: 'MessageActions',
		components: { AssistantVoicePill, MessageSwipeControls, GenerationRecoveryCard },
		props: {
			messageId: {
				type: [String, Number],
				default: ''
			},
			isAppPlus: {
				type: Boolean,
				default: false
			},
			streaming: {
				type: Boolean,
				default: false
			},
			streamingStatusText: {
				type: String,
				default: ''
			},
			showAssistantVoice: {
				type: Boolean,
				default: false
			},
			assistantVoiceLabel: {
				type: String,
				default: ''
			},
			assistantVoicePillClass: {
				type: [String, Array, Object],
				default: ''
			},
			showSwipeControls: {
				type: Boolean,
				default: false
			},
			swipeLabel: {
				type: String,
				default: ''
			},
			recovery: {
				type: Object,
				default: null
			},
			recoveryPrimaryLabel: {
				type: String,
				default: ''
			},
			recoveryRegenLabel: {
				type: String,
				default: ''
			},
			recoveryCopyLabel: {
				type: String,
				default: ''
			},
			canCopyRecovery: {
				type: Boolean,
				default: false
			}
		},
		computed: {
			hasActions() {
				return this.streaming || this.showAssistantVoice || this.showSwipeControls || !!this.recovery;
			}
		},
		methods: {
			emitAssistantVoiceToggle() {
				this.$emit('assistant-voice-toggle');
			},
			emitSwipePrevious() {
				this.$emit('swipe-previous');
			},
			emitSwipeNext() {
				this.$emit('swipe-next');
			},
			emitRecoveryPrimary() {
				this.$emit('recovery-primary');
			},
			emitRecoveryRegen() {
				this.$emit('recovery-regen');
			},
			emitRecoveryCopy() {
				this.$emit('recovery-copy');
			},
			emitRecoveryClose() {
				this.$emit('recovery-close');
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: controls inherit the shared chat message surface. */
	@keyframes message-actions-stream-wave {
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

	.message-actions {
		width: 100%;
		min-width: 0;
		box-sizing: border-box;
	}

	.stream-inline {
		display: inline-flex;
		align-items: center;
		gap: 12rpx;
		margin-top: 14rpx;
		padding: 10rpx 16rpx;
		border-radius: 999rpx;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
	}

	.stream-inline-wave {
		display: inline-flex;
		align-items: flex-end;
		gap: 5rpx;
		height: 24rpx;
	}

	.stream-inline-bar {
		width: 6rpx;
		border-radius: 999rpx;
		background: var(--chat-content-accent, #d9adb9);
		transform-origin: center bottom;
		animation: message-actions-stream-wave 1s ease-in-out infinite;
	}

	.stream-inline-bar:nth-child(1) {
		height: 12rpx;
	}

	.stream-inline-bar:nth-child(2) {
		height: 20rpx;
		animation-delay: 0.12s;
	}

	.stream-inline-bar:nth-child(3) {
		height: 14rpx;
		animation-delay: 0.22s;
	}

	.stream-inline-text {
		font-size: 22rpx;
		font-weight: 600;
		letter-spacing: 0;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
	}

</style>
