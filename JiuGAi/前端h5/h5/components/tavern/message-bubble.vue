<template>
	<view class="chat-message-bubble" :class="bubbleClass" :style="bubbleStyle">
		<slot></slot>
	</view>
</template>

<script>
	export default {
		name: 'MessageBubble',
		props: {
			bubbleClass: {
				type: [String, Array, Object],
				default: ''
			},
			bubbleStyle: {
				type: [String, Object],
				default: ''
			}
		}
	};
</script>

<style lang="scss">
.chat-message-bubble {
	position: relative;
	display: block;
	flex: 0 1 auto;
	min-width: 0;
	box-sizing: border-box;
	overflow: visible;
}

.chat-message-bubble--split-host .message-content,
.chat-message-split-list,
.chat-message-native,
.chat-message-segment,
.chat-message-segment-line {
	width: 100%;
	min-width: 0;
	box-sizing: border-box;
}

.chat-message-split-list,
.chat-message-native {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
}

.chat-message-split-part {
	position: relative;
	margin-bottom: 8rpx;
	word-break: break-word;
	overflow-wrap: break-word;
}

.chat-message-split-part:last-child {
	margin-bottom: 0;
}

.chat-message-markdown,
.chat-message-user-text,
.chat-message-segment-text {
	display: block;
	max-width: 100%;
	min-width: 0;
	box-sizing: border-box;
	word-break: break-word;
	overflow-wrap: break-word;
}

.chat-message-user-text,
.chat-message-segment-text {
	white-space: pre-wrap;
}

.chat-message-markdown .st-chat-render {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.chat-message-markdown .st-chat-seg,
.chat-message-markdown .st-chat-seg-body-h5 {
	min-width: 0;
	box-sizing: border-box;
}

.chat-message-markdown p {
	margin: 0 0 0.72em;
	color: inherit;
}

.chat-message-markdown p:last-child,
.chat-message-markdown .st-chat-seg:last-child {
	margin-bottom: 0;
}

.chat-message-markdown ol,
.chat-message-markdown ul {
	margin: 0.5em 0;
	padding-left: 1.35em;
}

.chat-message-markdown li {
	margin: 0.22em 0;
}

.chat-message-markdown blockquote {
	margin: 0.58em 0;
	padding: 0.28em 0.72em;
	border-left: 2rpx solid var(--chat-content-accent, #d9adb9);
	color: inherit;
}

.chat-message-markdown code {
	padding: 2rpx 8rpx;
	border-radius: 8rpx;
	background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
	color: inherit;
}

.chat-message-markdown a {
	color: var(--chat-content-accent, #d9adb9);
}

.chat-message-markdown pre {
	max-width: 100%;
	margin: 0.62em 0;
	padding: 14rpx 16rpx;
	border-radius: 12rpx;
	background: var(--chat-content-surface-strong, rgba(3, 8, 14, 0.28));
	box-sizing: border-box;
	overflow-x: auto;
}

.chat-message-markdown pre code {
	padding: 0;
	background: transparent;
}

.chat-message-markdown table {
	display: block;
	max-width: 100%;
	overflow-x: auto;
	border-collapse: collapse;
}

.chat-message-markdown th,
.chat-message-markdown td {
	padding: 8rpx 12rpx;
	border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
}

.chat-message-native {
	gap: 8rpx;
}

.chat-message-segment-label,
.chat-message-segment-label-text {
	box-sizing: border-box;
}
</style>
