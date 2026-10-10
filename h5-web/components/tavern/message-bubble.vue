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
	isolation: isolate;
}

/* Optical shell: the inline renderer owns the fill, while these layers add
 * the liquid-glass bend and chromatic rim without covering the text. */
.chat-message-bubble.liquid-glass {
	transform: translateZ(0);
	-webkit-transform: translateZ(0);
	background-clip: padding-box;
	font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", sans-serif;
	font-weight: 500;
	color: #172a33;
	backdrop-filter: blur(3px) saturate(118%);
	-webkit-backdrop-filter: blur(3px) saturate(118%);
}

.chat-message-bubble.liquid-glass .chat-message-markdown,
.chat-message-bubble.liquid-glass .chat-message-user-text,
.chat-message-bubble.liquid-glass .chat-message-segment-text {
	font-family: inherit;
	font-weight: 500;
	color: #172a33;
	-webkit-text-fill-color: #172a33;
}

.chat-message-bubble.liquid-glass .chat-message-markdown .st-chat-seg,
.chat-message-bubble.liquid-glass .chat-message-markdown .st-chat-seg * {
	color: #172a33;
	-webkit-text-fill-color: #172a33;
}

/* Shared liquid edge: the fill still comes from the appearance config, while
 * this optical rim gives every bubble a soft inner bend without blurring text. */
.chat-message-bubble.liquid-glass::after {
	content: '';
	position: absolute;
	inset: 1rpx;
	border-radius: inherit;
	pointer-events: none;
	background: linear-gradient(120deg, rgba(255,255,255,.58), transparent 24%, transparent 68%, rgba(83,170,181,.22));
	mix-blend-mode: screen;
	opacity: .9;
	box-shadow: inset 0 -1rpx 0 rgba(31, 100, 115, .24), inset 0 1rpx 0 rgba(255,255,255,.8);
	background-size: 220% 220%;
	animation: liquid-sheen 8s ease-in-out infinite;
}

.chat-message-bubble.liquid-glass::before {
	content: '';
	position: absolute;
	inset: 0;
	border-radius: inherit;
	pointer-events: none;
	background: radial-gradient(ellipse at 18% -8%, rgba(255,255,255,.62), transparent 36%), linear-gradient(155deg, transparent 0%, rgba(255,255,255,.13) 46%, transparent 72%);
	mix-blend-mode: screen;
	opacity: .92;
	box-shadow: inset 1rpx 0 0 rgba(255,255,255,.34), inset -1rpx 0 0 rgba(85,177,185,.18);
	animation: liquid-breathe 6s ease-in-out infinite;
}

.chat-message-bubble.liquid-glass.chat-message-bubble--assistant::after {
	background: linear-gradient(120deg, rgba(255,255,255,.62), transparent 26%, transparent 67%, rgba(100,190,204,.26));
}

.chat-message-bubble.liquid-glass.chat-message-bubble--user::after {
	background: linear-gradient(120deg, rgba(255,255,255,.62), transparent 24%, transparent 68%, rgba(236,160,201,.24));
}

@keyframes liquid-sheen {
	0%, 100% { background-position: 0% 0%; opacity: .82; }
	50% { background-position: 100% 100%; opacity: 1; }
}

@keyframes liquid-breathe {
	0%, 100% { transform: scale(1); opacity: .82; }
	50% { transform: scale(1.008); opacity: 1; }
}

.chat-message-bubble.liquid-glass > * {
	position: relative;
	z-index: 2;
}

/* A light optical keyline around glyphs keeps semantic colors legible when a
 * bright illustration sits directly behind the glass. */
.chat-message-bubble.liquid-glass .chat-message-markdown,
.chat-message-bubble.liquid-glass .chat-message-user-text,
.chat-message-bubble.liquid-glass .chat-message-segment-text,
.chat-message-bubble.liquid-glass .chat-message-speaker {
	text-shadow: 0 1rpx 2rpx rgba(255, 255, 255, .72), 0 0 1rpx rgba(26, 79, 91, .18);
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
	align-self: flex-start;
	width: auto;
	max-width: 100%;
	margin-bottom: 14rpx;
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
	gap: 10rpx;
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
	gap: 10rpx;
}

.chat-message-segment-label,
.chat-message-segment-label-text {
	box-sizing: border-box;
}
</style>
