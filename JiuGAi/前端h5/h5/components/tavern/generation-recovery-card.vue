<template>
	<view class="generation-recovery">
		<view class="generation-recovery-copy">
			<text class="generation-recovery-title">{{ recoveryTitle }}</text>
			<text class="generation-recovery-message">{{ recoveryMessage }}</text>
		</view>
		<view class="generation-recovery-actions">
			<text class="generation-recovery-btn generation-recovery-btn--primary" @tap.stop="emitPrimary">
				{{ primaryLabel }}
			</text>
			<text v-if="canRegen" class="generation-recovery-btn" @tap.stop="emitRegen">
				{{ regenLabel }}
			</text>
			<text v-if="canCopy" class="generation-recovery-btn" @tap.stop="emitCopy">
				{{ copyLabel }}
			</text>
			<text class="generation-recovery-close" @tap.stop="emitClose">×</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'GenerationRecoveryCard',
		props: {
			recovery: {
				type: Object,
				default: null
			},
			primaryLabel: {
				type: String,
				default: ''
			},
			regenLabel: {
				type: String,
				default: ''
			},
			copyLabel: {
				type: String,
				default: ''
			},
			canCopy: {
				type: Boolean,
				default: false
			}
		},
		computed: {
			recoveryTitle() {
				return this.recovery && this.recovery.title ? this.recovery.title : '';
			},
			recoveryMessage() {
				return this.recovery && this.recovery.message ? this.recovery.message : '';
			},
			canRegen() {
				return !!(this.recovery && this.recovery.canRegen);
			}
		},
		methods: {
			emitPrimary() {
				this.$emit('primary');
			},
			emitRegen() {
				this.$emit('regen');
			},
			emitCopy() {
				this.$emit('copy');
			},
			emitClose() {
				this.$emit('close');
			}
		}
	};
</script>

<style scoped lang="scss">
	.generation-recovery {
		margin-top: 16rpx;
		padding: 14rpx 0 0;
		border-radius: 0;
		background: transparent;
		border: 0;
		border-top: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
		box-shadow: none;
	}

	.generation-recovery-copy {
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.generation-recovery-title {
		font-size: 23rpx;
		font-weight: 600;
		color: var(--chat-content-primary, #f0eef0);
	}

	.generation-recovery-message {
		font-size: 22rpx;
		line-height: 1.5;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
		word-break: break-word;
	}

	.generation-recovery-actions {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 10rpx;
		margin-top: 14rpx;
	}

	.generation-recovery-btn {
		height: 48rpx;
		padding: 0 18rpx;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		font-size: 21rpx;
		font-weight: 600;
		color: var(--chat-content-primary, #f0eef0);
		background: var(--chat-content-surface-strong, rgba(255, 255, 255, 0.09));
		border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
	}

	.generation-recovery-btn--primary {
		color: var(--chat-content-primary, #f0eef0);
		background: var(--chat-content-surface-strong, rgba(255, 255, 255, 0.09));
		border-color: var(--chat-content-accent, #d9adb9);
	}

	.generation-recovery-close {
		width: 48rpx;
		height: 48rpx;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		font-size: 24rpx;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
	}
</style>
