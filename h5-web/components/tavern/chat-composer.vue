<template>
	<view class="chat-composer input-bar">
		<view v-if="attachmentMenuVisible" class="attach-fab-menu" @tap.stop>
			<view
				v-if="showVoiceAction"
				class="attach-fab-item"
				:class="{ 'attach-fab-item--active': voiceActionActive }"
				:title="voiceActionLabel"
				:aria-label="voiceActionLabel"
				@tap.stop="emitToggleVoiceInput"
			>
				<view class="attach-fab-badge">
					<image class="attach-fab-icon" :src="attachmentVoiceIcon" mode="aspectFit"></image>
				</view>
				<text class="attach-fab-label">{{ voiceActionLabel }}</text>
			</view>
			<view
				v-if="showImageGenerationAction"
				class="attach-fab-item"
				:class="{ 'attach-fab-item--active': imageGenerationActive }"
				:title="imageGenerationActionLabel"
				:aria-label="imageGenerationActionLabel"
				@tap.stop="emitOpenCharacterImagePanel"
			>
				<view class="attach-fab-badge">
					<image class="attach-fab-icon" :src="attachmentImageIcon" mode="aspectFit"></image>
				</view>
				<text class="attach-fab-label">{{ imageGenerationActionLabel }}</text>
			</view>
			<view class="attach-fab-item" :title="cameraActionLabel" :aria-label="cameraActionLabel" @tap.stop="emitPickCamera">
				<view class="attach-fab-badge">
					<image class="attach-fab-icon" :src="attachmentCameraIcon" mode="aspectFit"></image>
				</view>
				<text class="attach-fab-label">{{ cameraActionLabel }}</text>
			</view>
			<view class="attach-fab-item" :title="albumActionLabel" :aria-label="albumActionLabel" @tap.stop="emitPickAlbum">
				<view class="attach-fab-badge">
					<image class="attach-fab-icon" :src="attachmentAlbumIcon" mode="aspectFit"></image>
				</view>
				<text class="attach-fab-label">{{ albumActionLabel }}</text>
			</view>
		</view>
		<view v-if="atChatBottom" class="composer-stack">
			<view
				v-if="showModelSelector"
				class="model-selector"
				:class="{
					'model-selector--byok': modelSource === 'BYOK',
					'model-selector--disabled': modelSelectorDisabled
				}"
				:title="modelSelectorLabel"
				:aria-label="modelSelectorLabel"
				@tap.stop="emitOpenModelPicker"
			>
				<view class="model-selector__mark">
					<text>{{ modelSource === 'BYOK' ? 'API' : 'AI' }}</text>
				</view>
				<text class="model-selector__name">{{ modelName }}</text>
				<u-icon name="arrow-down" size="20" color="#4d7280"></u-icon>
			</view>
			<view class="input-pill" :class="{ 'input-pill--with-quote': quoteVisible }">
				<view v-if="draftRestoredNoticeVisible" class="draft-restore-bar">
					<text class="draft-restore-text">{{ draftRestoredText }}</text>
					<text class="draft-restore-action" @tap.stop="emitClearRestoredDraft">{{ clearText }}</text>
					<text class="draft-restore-close" @tap.stop="emitDismissDraftRestoredNotice">{{ closeIconText }}</text>
				</view>
				<view v-if="quoteVisible" class="composer-quote-bar">
					<view class="composer-quote-copy">
						<text class="composer-quote-speaker">{{ quoteSpeaker }}</text>
						<text class="composer-quote-text">{{ quoteText }}</text>
					</view>
					<text class="composer-quote-close" @tap="emitClearComposerQuote">{{ closeIconText }}</text>
				</view>
				<view class="input-main">
					<textarea
						class="inp"
						placeholder-class="chat-composer-inp-ph"
						:value="localValue"
						:placeholder="placeholder"
						confirm-type="send"
						auto-height
						:maxlength="-1"
						:show-confirm-bar="false"
						:cursor-spacing="cursorSpacing"
						:adjust-position="true"
						:disabled="disabled"
						@input="emitInput"
						@focus="emitFocus"
						@blur="emitBlur"
						@confirm="emitConfirm"
					></textarea>
					<view class="input-actions">
						<view class="expression-trigger" @tap="emitOpenExpressionPanel">
							<image class="input-action-icon" :src="inputExpressionIcon" mode="aspectFit"></image>
						</view>
						<view class="attach-btn" :class="{ 'attach-btn--active': attachmentMenuVisible }" @tap.stop="emitOpenAttachmentMenu">
							<image class="input-action-icon" :src="inputPlusIcon" mode="aspectFit"></image>
						</view>
					</view>
				</view>
			</view>
		</view>
		<view v-else class="scroll-bottom-pill" @tap="emitScrollBottom">
			<u-icon name="arrow-down" size="28" color="#ffffff"></u-icon>
			<text class="scroll-bottom-pill-text">{{ scrollBottomText }}</text>
		</view>
		<view
			v-if="atChatBottom"
			class="send send--icon"
			:class="{ senddisabled: disabled }"
			@tap="emitPrimaryAction"
		>
			<image class="send-icon" :src="sendUpIcon" mode="aspectFit"></image>
		</view>
	</view>
</template>

<script>
	const INPUT_SYNC_DELAY_MS = 260;

	export default {
		name: 'ChatComposer',
		props: {
			value: {
				type: String,
				default: ''
			},
			attachmentMenuVisible: {
				type: Boolean,
				default: false
			},
			showVoiceAction: {
				type: Boolean,
				default: false
			},
			voiceActionActive: {
				type: Boolean,
				default: false
			},
			showImageGenerationAction: {
				type: Boolean,
				default: false
			},
			imageGenerationActive: {
				type: Boolean,
				default: false
			},
			voiceActionLabel: {
				type: String,
				default: '语音输入'
			},
			imageGenerationActionLabel: {
				type: String,
				default: '聊天生图'
			},
			cameraActionLabel: {
				type: String,
				default: '相机'
			},
			albumActionLabel: {
				type: String,
				default: '相册'
			},
			atChatBottom: {
				type: Boolean,
				default: true
			},
			quote: {
				type: Object,
				default: null
			},
			draftRestoredNoticeVisible: {
				type: Boolean,
				default: false
			},
			draftRestoredText: {
				type: String,
				default: ''
			},
			clearText: {
				type: String,
				default: ''
			},
			placeholder: {
				type: String,
				default: ''
			},
			scrollBottomText: {
				type: String,
				default: '回到底部'
			},
			cursorSpacing: {
				type: Number,
				default: 18
			},
			showModelSelector: {
				type: Boolean,
				default: false
			},
			modelName: {
				type: String,
				default: '选择聊天模型'
			},
			modelSource: {
				type: String,
				default: 'SYSTEM'
			},
			modelSelectorDisabled: {
				type: Boolean,
				default: false
			},
			modelSelectorLabel: {
				type: String,
				default: '切换聊天模型'
			},
			disabled: {
				type: Boolean,
				default: false
			},
			attachmentVoiceIcon: {
				type: String,
				default: ''
			},
			attachmentImageIcon: {
				type: String,
				default: ''
			},
			attachmentCameraIcon: {
				type: String,
				default: ''
			},
			attachmentAlbumIcon: {
				type: String,
				default: ''
			},
			inputExpressionIcon: {
				type: String,
				default: ''
			},
			inputPlusIcon: {
				type: String,
				default: ''
			},
			sendUpIcon: {
				type: String,
				default: ''
			}
		},
		data() {
			return {
				localValue: this.value == null ? '' : String(this.value),
				inputSyncTimer: null,
				pendingInputEcho: null
			};
		},
		computed: {
			quoteVisible() {
				return !!(this.quote && this.quote.visible);
			},
			quoteSpeaker() {
				return this.quote && this.quote.speaker ? this.quote.speaker : '';
			},
			quoteText() {
				return this.quote && this.quote.text ? this.quote.text : '';
			},
			closeIconText() {
				return '\u00d7';
			}
		},
		watch: {
			value(nextValue) {
				const next = nextValue == null ? '' : String(nextValue);
				if (this.pendingInputEcho !== null && next === this.pendingInputEcho) {
					this.pendingInputEcho = null;
					return;
				}
				this.pendingInputEcho = null;
				if (next === this.localValue) return;
				this.clearInputSyncTimer();
				this.localValue = next;
			}
		},
		beforeDestroy() {
			this.flushInputValue();
			this.clearInputSyncTimer();
		},
		methods: {
			clearInputSyncTimer() {
				if (!this.inputSyncTimer) return;
				clearTimeout(this.inputSyncTimer);
				this.inputSyncTimer = null;
			},
			syncInputValue() {
				const next = this.localValue == null ? '' : String(this.localValue);
				const parentValue = this.value == null ? '' : String(this.value);
				if (next === parentValue) {
					this.pendingInputEcho = null;
					return false;
				}
				this.pendingInputEcho = next;
				this.$emit('input', next);
				return true;
			},
			scheduleInputSync() {
				this.clearInputSyncTimer();
				this.inputSyncTimer = setTimeout(() => {
					this.inputSyncTimer = null;
					this.syncInputValue();
				}, INPUT_SYNC_DELAY_MS);
			},
			flushInputValue() {
				this.clearInputSyncTimer();
				return this.syncInputValue();
			},
			emitInput(event) {
				const value = event && event.detail ? event.detail.value : '';
				this.localValue = value == null ? '' : String(value);
				if (this.localValue === String(this.value == null ? '' : this.value)) {
					this.clearInputSyncTimer();
					return;
				}
				this.scheduleInputSync();
			},
			emitFocus(event) {
				this.$emit('focus', event);
			},
			emitBlur(event) {
				this.flushInputValue();
				this.$emit('blur', event);
			},
			emitConfirm(event) {
				this.flushInputValue();
				this.$emit('confirm', event);
			},
			emitToggleVoiceInput() {
				this.flushInputValue();
				this.$emit('toggle-voice-input');
			},
			emitOpenCharacterImagePanel() {
				this.flushInputValue();
				this.$emit('open-character-image-panel');
			},
			emitPickCamera() {
				this.$emit('pick-camera');
			},
			emitPickAlbum() {
				this.$emit('pick-album');
			},
			emitClearRestoredDraft() {
				this.$emit('clear-restored-draft');
			},
			emitDismissDraftRestoredNotice() {
				this.$emit('dismiss-draft-restored-notice');
			},
			emitClearComposerQuote() {
				this.$emit('clear-composer-quote');
			},
			emitOpenExpressionPanel() {
				this.flushInputValue();
				this.$emit('open-expression-panel');
			},
			emitOpenAttachmentMenu() {
				this.flushInputValue();
				this.$emit('open-attachment-menu');
			},
			emitOpenModelPicker() {
				if (this.modelSelectorDisabled) return;
				this.flushInputValue();
				this.$emit('open-model-picker');
			},
			emitScrollBottom() {
				this.flushInputValue();
				this.$emit('scroll-bottom');
			},
			emitPrimaryAction() {
				this.flushInputValue();
				this.$emit('primary-action');
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: APP polish layers depend on .wrap--app-plus ancestors. */
	.chat-composer.input-bar {
		flex-shrink: 0;
		position: relative;
		z-index: 6;
		width: 100%;
		max-width: 100%;
		min-width: 0;
		box-sizing: border-box;
		display: flex;
		align-items: flex-end !important;
		gap: 14rpx;
		padding: 12rpx 18rpx calc(12rpx + env(safe-area-inset-bottom)) !important;
		background: transparent !important;
		border-top: 0 !important;
		box-shadow: none !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.chat-composer .composer-stack {
		flex: 1 1 auto;
		min-width: 0;
		max-width: 100%;
		display: flex;
		flex-direction: column;
		align-items: stretch;
		gap: 8rpx;
	}

	.chat-composer .model-selector {
		align-self: flex-start;
		max-width: unquote("min(72%, 440rpx)");
		height: 50rpx;
		min-width: 0;
		box-sizing: border-box;
		display: flex;
		align-items: center;
		gap: 8rpx;
		margin-left: 12rpx;
		padding: 0 14rpx 0 8rpx;
		border: 1rpx solid rgba(103, 159, 174, 0.2);
		border-radius: 25rpx;
		background: rgba(247, 253, 253, 0.88);
		box-shadow: 0 6rpx 18rpx rgba(38, 77, 91, 0.1);
		backdrop-filter: blur(10rpx) saturate(120%);
		-webkit-backdrop-filter: blur(10rpx) saturate(120%);
	}

	.chat-composer .model-selector--disabled {
		opacity: 0.58;
		pointer-events: none;
	}

	.chat-composer .model-selector__mark {
		flex: 0 0 34rpx;
		width: 34rpx;
		height: 34rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 10rpx;
		color: #ffffff;
		background: #4f93a3;
	}

	.chat-composer .model-selector--byok .model-selector__mark {
		background: #d9796f;
	}

	.chat-composer .model-selector__mark text {
		font-size: 16rpx;
		font-weight: 800;
		line-height: 1;
	}

	.chat-composer .model-selector__name {
		min-width: 0;
		max-width: 100%;
		overflow: hidden;
		color: #315665;
		font-size: 22rpx;
		font-weight: 650;
		line-height: 1;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chat-composer .input-pill {
		flex: 1 1 auto;
		min-width: 0;
		max-width: 100%;
		min-height: 88rpx;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: stretch;
		gap: 10rpx;
		padding: 8rpx 12rpx 8rpx 24rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.76) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.46) !important;
		box-shadow: 0 10rpx 26rpx rgba(24, 48, 68, 0.12);
		backdrop-filter: blur(12rpx) saturate(128%);
		-webkit-backdrop-filter: blur(12rpx) saturate(128%);
	}

	.chat-composer .input-pill--with-quote {
		border-radius: 34rpx;
		padding-top: 12rpx;
		padding-bottom: 12rpx;
	}

	.chat-composer .draft-restore-bar {
		display: flex;
		align-items: center;
		gap: 12rpx;
		width: 100%;
		max-width: 100%;
		min-width: 0;
		min-height: 46rpx;
		padding: 8rpx 12rpx 8rpx 16rpx;
		border-radius: 22rpx;
		background: rgba(236, 247, 252, 0.92);
		border: 1rpx solid rgba(79, 147, 163, 0.14);
		box-sizing: border-box;
	}

	.chat-composer .draft-restore-text {
		flex: 1;
		min-width: 0;
		font-size: 22rpx;
		line-height: 1.35;
		color: #426273;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chat-composer .draft-restore-action {
		flex-shrink: 0;
		font-size: 22rpx;
		font-weight: 700;
		color: #2f7f96;
	}

	.chat-composer .draft-restore-close {
		flex-shrink: 0;
		width: 34rpx;
		height: 34rpx;
		line-height: 34rpx;
		border-radius: 50%;
		text-align: center;
		font-size: 26rpx;
		color: #6d7f8b;
		background: rgba(255, 255, 255, 0.78);
	}

	.chat-composer .input-main {
		flex: 1 1 auto;
		width: 100%;
		max-width: 100%;
		display: flex;
		align-items: flex-end;
		gap: 8rpx;
		min-width: 0;
		box-sizing: border-box;
	}

	.chat-composer .composer-quote-bar {
		display: flex;
		align-items: flex-start;
		gap: 14rpx;
		width: 100%;
		max-width: 100%;
		min-width: 0;
		padding: 12rpx 16rpx;
		border-radius: 22rpx;
		background: rgba(240, 244, 247, 0.96);
		border-left: 6rpx solid rgba(52, 143, 184, 0.42);
		box-sizing: border-box;
	}

	.chat-composer .composer-quote-copy {
		flex: 1;
		min-width: 0;
	}

	.chat-composer .composer-quote-speaker {
		display: block;
		font-size: 21rpx;
		font-weight: 700;
		line-height: 1.4;
		color: #2d647f;
	}

	.chat-composer .composer-quote-text {
		display: block;
		margin-top: 4rpx;
		font-size: 22rpx;
		line-height: 1.5;
		color: #526277;
		word-break: break-word;
	}

	.chat-composer .composer-quote-close {
		flex-shrink: 0;
		width: 34rpx;
		height: 34rpx;
		line-height: 34rpx;
		text-align: center;
		font-size: 28rpx;
		color: #7390a2;
	}

	.chat-composer .inp {
		flex: 1 1 auto;
		width: 0 !important;
		min-width: 0 !important;
		min-height: 72rpx !important;
		max-height: 196rpx !important;
		padding: 14rpx 8rpx 14rpx 0 !important;
		box-sizing: border-box;
		background: transparent !important;
		border: 0 !important;
		border-radius: 28rpx;
		box-shadow: none !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
		color: #1f2937 !important;
		font-size: 28rpx;
		line-height: 40rpx;
		overflow-y: auto;
	}

	.chat-composer .input-actions {
		flex: 0 0 auto;
		display: flex;
		align-items: center;
		gap: 6rpx;
		padding-bottom: 4rpx;
	}

	.chat-composer .expression-trigger,
	.chat-composer .attach-btn {
		display: flex !important;
		align-items: center;
		justify-content: center;
		width: 64rpx !important;
		height: 64rpx !important;
		min-width: 64rpx !important;
		padding: 0 !important;
		line-height: normal !important;
		border-radius: 50% !important;
		background: transparent !important;
		box-shadow: none !important;
		transition: transform 0.18s ease, box-shadow 0.18s ease;
	}

	.chat-composer .input-action-icon {
		width: 42rpx;
		height: 42rpx;
	}

	.chat-composer .attach-btn--active {
		transform: rotate(45deg);
	}

	.chat-composer .send.send--icon {
		flex: 0 0 88rpx !important;
		width: 88rpx !important;
		height: 88rpx !important;
		min-width: 88rpx !important;
		padding: 0 !important;
		display: flex !important;
		align-items: center;
		justify-content: center;
		border-radius: 50% !important;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.2) !important;
	}

	.chat-composer .send.senddisabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.chat-composer .send-icon {
		width: 42rpx;
		height: 42rpx;
	}

	.chat-composer .scroll-bottom-pill {
		flex: 1 1 auto;
		min-width: 0;
		max-width: 100%;
		height: 88rpx;
		box-sizing: border-box;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12rpx;
		padding: 0 26rpx;
		border-radius: 999rpx;
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.92) 0%, rgba(118, 210, 221, 0.9) 62%, rgba(244, 166, 196, 0.92) 100%) !important;
		box-shadow: 0 12rpx 24rpx rgba(52, 143, 184, 0.16);
	}

	.chat-composer .scroll-bottom-pill-text {
		color: #ffffff;
		font-size: 28rpx;
		font-weight: 600;
		line-height: 1;
		letter-spacing: 0;
	}

	.chat-composer .attach-fab-menu {
		position: fixed;
		right: 18rpx;
		left: auto;
		bottom: calc(env(safe-area-inset-bottom) + 106rpx);
		z-index: 7;
		display: flex;
		flex-direction: row;
		align-items: center;
		flex-wrap: wrap;
		justify-content: flex-end;
		max-width: calc(100vw - 132rpx);
		gap: 12rpx;
	}

	.chat-composer .attach-fab-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: flex-start;
		gap: 7rpx;
		width: 100rpx;
		min-width: 100rpx;
		animation: attach-fab-pop 0.18s ease-out;
	}

	.chat-composer .attach-fab-item--active .attach-fab-badge {
		background: linear-gradient(135deg, #ef86af 0%, #f5bdd1 100%);
		border-color: rgba(239, 134, 175, 0.12);
		box-shadow: 0 14rpx 26rpx rgba(239, 134, 175, 0.2);
	}

	.chat-composer .attach-fab-badge {
		flex-shrink: 0;
		width: 86rpx;
		height: 86rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.96);
		border: 2rpx solid rgba(31, 41, 55, 0.08);
		box-shadow: 0 14rpx 26rpx rgba(15, 23, 42, 0.12);
	}

	.chat-composer .attach-fab-icon {
		width: 44rpx;
		height: 44rpx;
	}

	.chat-composer .attach-fab-label {
		display: block !important;
		max-width: 100%;
		color: #3e5666;
		font-size: 20rpx;
		font-weight: 600;
		line-height: 1.25;
		text-align: center;
		white-space: nowrap;
		text-shadow: 0 1rpx 2rpx rgba(255, 255, 255, 0.9);
	}

	.chat-composer .attach-fab-item--active .attach-fab-label {
		color: #bd527d;
	}

	.chat-composer-inp-ph {
		color: #64748b;
	}

	@keyframes attach-fab-pop {
		from {
			opacity: 0;
			transform: translate3d(0, 12rpx, 0) scale(0.92);
		}
		to {
			opacity: 1;
			transform: translate3d(0, 0, 0) scale(1);
		}
	}

	/* #ifdef APP-PLUS */
	.chat-composer-inp-ph {
		color: #7f8d9a !important;
	}

	.wrap--app-plus .chat-composer.input-bar {
		position: relative !important;
		z-index: 5 !important;
		width: 100% !important;
		max-width: 100% !important;
		min-width: 0 !important;
		box-sizing: border-box !important;
		align-items: flex-end !important;
		gap: 10rpx !important;
		padding: 10rpx 14rpx calc(12rpx + env(safe-area-inset-bottom)) !important;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(246, 252, 252, 0.18) 100%) !important;
		border-top: 0 !important;
		box-shadow: 0 -10rpx 24rpx rgba(38, 57, 77, 0.05) !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus .chat-composer .input-pill {
		flex: 1 1 auto !important;
		min-width: 0 !important;
		max-width: 100% !important;
		min-height: 82rpx !important;
		max-height: 300rpx;
		box-sizing: border-box !important;
		padding: 8rpx 10rpx 8rpx 20rpx !important;
		border-radius: 30rpx !important;
		background: rgba(255, 255, 255, 0.72) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.42) !important;
		box-shadow: 0 10rpx 26rpx rgba(24, 48, 68, 0.12) !important;
	}

	.wrap--app-plus .chat-composer .input-pill--with-quote {
		border-radius: 28rpx !important;
		padding-top: 12rpx !important;
		padding-bottom: 12rpx !important;
	}

	.wrap--app-plus .chat-composer .draft-restore-bar {
		min-height: 44rpx;
		padding: 8rpx 12rpx !important;
		border-radius: 20rpx !important;
		background: rgba(236, 247, 252, 0.94) !important;
		border-color: rgba(55, 145, 176, 0.16) !important;
	}

	.wrap--app-plus .chat-composer .draft-restore-text {
		font-size: 21rpx !important;
		color: #426273 !important;
	}

	.wrap--app-plus .chat-composer .draft-restore-action {
		font-size: 21rpx !important;
		color: #2f7f96 !important;
	}

	.wrap--app-plus .chat-composer .draft-restore-close {
		background: rgba(255, 255, 255, 0.86) !important;
		color: #6d7f8b !important;
	}

	.wrap--app-plus .chat-composer .input-main {
		width: 100% !important;
		max-width: 100% !important;
		min-width: 0 !important;
		box-sizing: border-box !important;
		align-items: flex-end !important;
		gap: 8rpx !important;
		min-height: 66rpx;
	}

	.wrap--app-plus .chat-composer .inp {
		flex: 1 1 auto !important;
		width: 0 !important;
		min-width: 0 !important;
		min-height: 66rpx !important;
		max-height: 208rpx !important;
		padding: 13rpx 8rpx 13rpx 0 !important;
		font-size: 28rpx !important;
		line-height: 40rpx !important;
		color: #26394d !important;
		background: transparent !important;
		word-break: break-word;
		white-space: pre-wrap;
		overflow-y: auto;
	}

	.wrap--app-plus .chat-composer .input-actions {
		flex: 0 0 auto !important;
		align-self: flex-end;
		height: 66rpx;
		gap: 4rpx !important;
		padding-bottom: 0 !important;
	}

	.wrap--app-plus .chat-composer .expression-trigger,
	.wrap--app-plus .chat-composer .attach-btn {
		width: 66rpx !important;
		height: 66rpx !important;
		min-width: 66rpx !important;
		border-radius: 50% !important;
		background: rgba(236, 247, 252, 0.88) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.12) !important;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.82) !important;
	}


	.wrap--app-plus .chat-composer .input-action-icon {
		width: 40rpx;
		height: 40rpx;
	}

	.wrap--app-plus .chat-composer .send.send--icon {
		flex: 0 0 82rpx !important;
		width: 82rpx !important;
		height: 82rpx !important;
		min-width: 82rpx !important;
		align-self: flex-end !important;
		border-radius: 50% !important;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.2) !important;
	}

	.wrap--app-plus .chat-composer .send-icon {
		width: 40rpx;
		height: 40rpx;
	}

	.wrap--app-plus .chat-composer .scroll-bottom-pill {
		min-width: 0 !important;
		max-width: 100% !important;
		height: 82rpx;
		box-sizing: border-box !important;
		border-radius: 999rpx;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.18) !important;
	}

	.wrap--app-plus .chat-composer .composer-quote-bar {
		max-height: 138rpx;
		padding: 12rpx 14rpx !important;
		border-radius: 20rpx !important;
		background: rgba(236, 247, 252, 0.92) !important;
		border-left: 6rpx solid rgba(55, 145, 176, 0.42);
		overflow: hidden;
	}

	.wrap--app-plus .chat-composer .composer-quote-text {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.wrap--app-plus .chat-composer .composer-quote-close {
		width: 42rpx;
		height: 42rpx;
		line-height: 42rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.76);
		color: #5b7284;
	}

	.wrap--app-plus .chat-composer .attach-fab-menu {
		right: 18rpx;
		bottom: calc(env(safe-area-inset-bottom) + 110rpx);
		z-index: 9 !important;
		max-width: calc(100vw - 132rpx);
		gap: 12rpx;
	}

	.wrap--app-plus .chat-composer .attach-fab-item {
		width: 100rpx;
		min-width: 100rpx;
		gap: 7rpx;
	}

	.wrap--app-plus .chat-composer .attach-fab-badge {
		width: 86rpx;
		height: 86rpx;
		background: rgba(255, 255, 255, 0.96);
		border-color: rgba(86, 121, 145, 0.14);
		box-shadow: 0 12rpx 24rpx rgba(27, 70, 96, 0.12);
	}

	.wrap--app-plus .chat-composer .attach-fab-icon {
		width: 44rpx;
		height: 44rpx;
	}

	.wrap--app-plus .chat-composer .attach-fab-label {
		font-size: 20rpx;
		line-height: 1.25;
	}
	/* #endif */
</style>
