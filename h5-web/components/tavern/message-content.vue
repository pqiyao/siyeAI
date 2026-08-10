<template>
	<view class="message-content" :class="{ 'message-content--has-image': safeImageUrls.length, 'message-content--has-text': hasText }">
		<view v-if="safeImageUrls.length" class="msg-image-list" :class="'msg-image-list--' + imageGridMode">
			<view
				v-for="(img, imgIndex) in safeImageUrls"
				:key="imgIndex"
				class="msg-image-cell"
				:class="imageCellClass(imgIndex)"
				@tap.stop="handleImageTap(imgIndex)"
			>
				<image
					:key="imageRenderKey(img, imgIndex)"
					class="msg-image"
					:src="imageDisplaySrc(img, imgIndex)"
					mode="aspectFill"
					lazy-load
					@error="markImageFailed(imgIndex)"
					@load="clearImageFailed(imgIndex)"
				></image>
				<view v-if="imageFailed(imgIndex)" class="msg-image-error" @tap.stop="retryImage(imgIndex)">
					<text class="msg-image-error-title">{{ imageErrorText }}</text>
					<text class="msg-image-error-action">{{ imageRetryText }}</text>
				</view>
			</view>
		</view>
		<view v-if="showLocalPrompt" class="local-image-prompt-row">
			<text class="local-image-prompt-text">{{ localPrompt }}</text>
		</view>
		<view v-if="quoteMeta" class="msg-quote-preview" :class="{ 'msg-quote-preview--me': isUser }">
			<text class="msg-quote-preview-speaker">{{ quoteMeta.speaker }}</text>
			<text class="msg-quote-preview-text">{{ quoteMeta.text }}</text>
		</view>
		<slot></slot>
	</view>
</template>

<script>
	const { getLanguageCode } = require('@/common/tavernUiI18n.js');
	const IMAGE_COPY = {
		'zh-cn': { error: '图片加载失败', retry: '重试' },
		'zh-hk': { error: '圖片載入失敗', retry: '重試' },
		en: { error: 'Image failed to load', retry: 'Retry' },
		ko: { error: '이미지를 불러오지 못했습니다', retry: '다시 시도' },
		ja: { error: '画像を読み込めませんでした', retry: '再試行' }
	};

	export default {
		name: 'MessageContent',
		props: {
			imageUrls: {
				type: Array,
				default() {
					return [];
				}
			},
			localKind: {
				type: String,
				default: ''
			},
			localPrompt: {
				type: String,
				default: ''
			},
			quoteMeta: {
				type: Object,
				default: null
			},
			isUser: {
				type: Boolean,
				default: false
			},
			hasText: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				imageFailedMap: {},
				imageRetryMap: {}
			};
		},
		computed: {
			safeImageUrls() {
				return Array.isArray(this.imageUrls) ? this.imageUrls.filter(Boolean) : [];
			},
			imageGridMode() {
				const count = this.safeImageUrls.length;
				if (count <= 1) return 'single';
				if (count === 2) return 'double';
				if (count === 3) return 'triple';
				return 'grid';
			},
			imageErrorText() {
				const copy = IMAGE_COPY[getLanguageCode()] || IMAGE_COPY['zh-cn'];
				return copy.error;
			},
			imageRetryText() {
				const copy = IMAGE_COPY[getLanguageCode()] || IMAGE_COPY['zh-cn'];
				return copy.retry;
			},
			showLocalPrompt() {
				return this.localKind === 'image_generation' && !!this.localPrompt;
			}
		},
		methods: {
			imageSourceToken(src) {
				const value = String(src || '');
				if (!value) return 'empty';
				return value.length + ':' + value.slice(0, 24) + ':' + value.slice(-24);
			},
			imageStateKey(index) {
				return String(index) + '::' + this.imageSourceToken(this.safeImageUrls[index]);
			},
			imageFailed(index) {
				return this.imageFailedMap[this.imageStateKey(index)] === true;
			},
			imageRetryVersion(index) {
				return Number(this.imageRetryMap[this.imageStateKey(index)] || 0);
			},
			imageRenderKey(img, index) {
				return String(index) + '_' + this.imageSourceToken(img) + '_' + this.imageRetryVersion(index);
			},
			imageDisplaySrc(img, index) {
				const src = String(img || '');
				const retryVersion = this.imageRetryVersion(index);
				if (!src || retryVersion <= 0 || /^(data|blob|file):/i.test(src)) return src;
				return src + (src.indexOf('?') >= 0 ? '&' : '?') + '_img_retry=' + retryVersion;
			},
			imageCellClass(index) {
				const count = this.safeImageUrls.length;
				return {
					'msg-image-cell--single': count === 1,
					'msg-image-cell--double': count === 2,
					'msg-image-cell--triple-main': count === 3 && index === 0,
					'msg-image-cell--triple-side': count === 3 && index > 0,
					'msg-image-cell--grid': count >= 4
				};
			},
			handleImageTap(index) {
				if (this.imageFailed(index)) {
					this.retryImage(index);
					return;
				}
				this.emitPreviewImage(index);
			},
			markImageFailed(index) {
				this.$set(this.imageFailedMap, this.imageStateKey(index), true);
			},
			clearImageFailed(index) {
				this.$set(this.imageFailedMap, this.imageStateKey(index), false);
			},
			retryImage(index) {
				const key = this.imageStateKey(index);
				this.$set(this.imageFailedMap, key, false);
				this.$set(this.imageRetryMap, key, this.imageRetryVersion(index) + 1);
			},
			emitPreviewImage(index) {
				this.$emit('preview-image', index);
			}
		}
	};
</script>

<style lang="scss">
	/* Unscoped on purpose: content sizing follows the shared message surface. */
	.message-content {
		width: 100%;
		min-width: 0;
		box-sizing: border-box;
	}

	.msg-image-list {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-bottom: 14rpx;
	}

	.msg-image-cell {
		position: relative;
		width: 188rpx;
		height: 188rpx;
		border-radius: 22rpx;
		overflow: hidden;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.08));
		border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.12));
		box-shadow: 0 4rpx 14rpx rgba(15, 23, 42, 0.1);
		box-sizing: border-box;
	}

	.msg-image-list--single .msg-image-cell {
		width: 310rpx;
		height: 310rpx;
	}

	.msg-image-list--double .msg-image-cell {
		width: 190rpx;
		height: 190rpx;
	}

	.msg-image-cell--triple-main {
		width: 390rpx;
		height: 220rpx;
	}

	.msg-image-cell--triple-side,
	.msg-image-cell--grid {
		width: 188rpx;
		height: 188rpx;
	}

	.msg-image {
		width: 100%;
		height: 100%;
		display: block;
	}

	.msg-image-error {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		padding: 16rpx;
		background: rgba(12, 18, 28, 0.68);
		box-sizing: border-box;
	}

	.msg-image-error-title,
	.msg-image-error-action {
		display: block;
		text-align: center;
		color: #ffffff;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.28);
	}

	.msg-image-error-title {
		font-size: 22rpx;
		line-height: 1.25;
	}

	.msg-image-error-action {
		padding: 6rpx 14rpx;
		border-radius: 999rpx;
		font-size: 21rpx;
		line-height: 1.2;
		font-weight: 700;
		background: rgba(255, 255, 255, 0.2);
	}

	.local-image-prompt-row {
		margin: 12rpx 0 2rpx;
		padding: 12rpx 14rpx;
		border: 1rpx solid rgba(55, 145, 176, 0.18);
		border-radius: 18rpx;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.06));
		border-color: var(--chat-content-border, rgba(255, 255, 255, 0.14));
	}

	.local-image-prompt-text {
		display: block;
		font-size: 24rpx;
		line-height: 1.55;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
		word-break: break-word;
	}

	.chat-message-row--user .local-image-prompt-row {
		border-color: var(--chat-content-border, rgba(255, 255, 255, 0.14));
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.06));
	}

	.chat-message-row--user .local-image-prompt-text {
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
	}

	.chat-message-bubble--image-only .msg-image-list {
		margin-bottom: 0;
	}

	.chat-message-bubble--image-only .msg-image-list--single .msg-image-cell {
		width: 340rpx;
		height: 340rpx;
	}

	.chat-message-bubble--image-only .msg-image-cell--double,
	.chat-message-bubble--image-only .msg-image-cell--triple-side,
	.chat-message-bubble--image-only .msg-image-cell--grid {
		width: 210rpx;
		height: 210rpx;
	}

	.chat-message-bubble--image-only .msg-image-cell--triple-main {
		width: 432rpx;
		height: 240rpx;
	}

	.msg-quote-preview {
		margin-bottom: 10rpx;
		padding: 10rpx 12rpx;
		border-radius: 14rpx;
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border-left: 2rpx solid var(--chat-content-accent, #d9adb9);
	}

	.chat-message-row--user .msg-quote-preview {
		background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
		border-left-color: var(--chat-content-accent, #d9adb9);
	}

	.msg-quote-preview-speaker {
		display: block;
		font-size: 21rpx;
		font-weight: 600;
		line-height: 1.4;
		color: var(--chat-content-primary, #f0eef0);
	}

	.chat-message-row--user .msg-quote-preview-speaker {
		color: var(--chat-content-primary, #f0eef0);
	}

	.msg-quote-preview-text {
		display: block;
		margin-top: 6rpx;
		font-size: 23rpx;
		line-height: 1.55;
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
		word-break: break-word;
	}

	.chat-message-row--user .msg-quote-preview-text {
		color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
	}

</style>
