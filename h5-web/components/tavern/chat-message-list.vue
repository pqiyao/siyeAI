<template>
		<scroll-view
			class="chat-scroll"
			:class="{ 'chat-scroll--preparing': !controller.chatViewportReady }"
			scroll-y
			:scroll-into-view="controller.scrollTo"
			:scroll-with-animation="controller.chatScrollWithAnimation"
			@scroll="controller.onChatScroll($event)"
			@scrolltoupper="controller.onChatScrollToUpper($event)"
			@scrolltolower="controller.onChatScrollToLower($event)"
			@touchstart="controller.onChatTouchStart($event)"
			@touchmove="controller.onChatTouchMove($event)"
			@touchend="controller.onChatTouchEnd($event)"
			@touchcancel="controller.onChatTouchEnd($event)"
			:upper-threshold="60"
			:lower-threshold="80"
		>
			<view
				class="chat-scroll-content"
				@touchstart="controller.onChatTouchStart($event)"
				@touchmove="controller.onChatTouchMove($event)"
				@touchend="controller.onChatTouchEnd($event)"
				@touchcancel="controller.onChatTouchEnd($event)"
			>
				<view v-if="controller.showHistoryLoadBanner()" class="chat-history-banner" :class="{ 'chat-history-banner--loading': controller.messageHistoryLoading }">
					<text class="chat-history-banner__text">{{ controller.historyLoadBannerText() }}</text>
				</view>
				<view
					v-for="m in controller.messages"
					:key="m.id"
					:id="'m-' + m.id"
					class="chat-message-row"
					:class="[
						m.role === 'user' ? 'chat-message-row--user' : 'chat-message-row--assistant',
						{
							'chat-message-row--read-empty': !controller.messageVisibleInReadMode(m)
						}
					]"
				>
					<template v-if="controller.shouldRenderStructuredAssistant(m)">
						<view class="ensemble-message-list">
							<view
								v-for="(speakerSegment, speakerIndex) in controller.structuredAssistantSegments(m)"
								:key="String(m.id) + '-speaker-' + speakerSegment.index"
								class="ensemble-message-segment"
								:class="{ 'ensemble-message-segment--narrator': speakerSegment.type === 'NARRATOR' }"
							>
								<image
									v-if="speakerSegment.type === 'CHARACTER'"
									class="chat-message-avatar chat-message-avatar--character"
									:src="controller.structuredSegmentAvatar(speakerSegment)"
									mode="aspectFill"
									lazy-load
									@tap.stop="controller.openStructuredSegmentAvatar(speakerSegment)"
									@longpress.stop="controller.applyCharacterNameToDraft(speakerSegment.speakerName)"
								></image>
								<view v-else class="ensemble-narrator-rail"><view class="ensemble-narrator-mark"></view></view>
								<message-bubble
									:bubble-style="controller.structuredSegmentBubbleStyle(m, speakerSegment)"
									:bubble-class="controller.structuredSegmentBubbleClass(speakerSegment)"
								>
									<text class="chat-message-speaker" :class="{ 'chat-message-speaker--narrator': speakerSegment.type === 'NARRATOR' }">{{ speakerSegment.speakerName }}</text>
									<message-content
										:image-urls="speakerIndex === 0 ? m.imageUrls : []"
										:local-kind="speakerIndex === 0 ? m.localKind : ''"
										:local-prompt="speakerIndex === 0 ? m.localPrompt : ''"
										:quote-meta="speakerIndex === 0 ? controller.messageQuoteMeta(m) : null"
										:is-user="false"
										:has-text="true"
										@preview-image="controller.previewChatMessageImages(m, $event)"
									>
										<!-- #ifdef H5 -->
										<view
											class="chat-message-markdown"
											:style="controller.assistantMessageTextStyle(m)"
						v-html="controller.mdHtml(speakerSegment.content, controller.semanticForStructuredSegment(m, speakerSegment, speakerIndex))"
											@tap="controller.onMarkdownTap($event)"
											@touchstart="controller.startMessageActionPress(m, $event)"
											@touchmove="controller.moveMessageActionPress($event)"
											@touchend="controller.endMessageActionPress($event)"
											@touchcancel="controller.endMessageActionPress($event)"
										></view>
										<!-- #endif -->
										<!-- #ifndef H5 -->
										<view :class="controller.nativeMessageContentClass()">
											<view
							v-for="(nativeSegment, nativeIndex) in controller.mdSegments(speakerSegment.content, controller.semanticForStructuredSegment(m, speakerSegment, speakerIndex))"
												:key="nativeIndex"
												:class="controller.nativeSegmentClass(nativeSegment)"
												:style="controller.nativeSegmentWrapStyle(nativeSegment)"
											>
												<view :class="controller.nativeSegmentLineClass()" :style="controller.nativeSegmentLineStyle()">
													<text :class="controller.nativeSegmentTextClass(nativeSegment)" :style="controller.nativeSegmentTextStyle(nativeSegment)">{{ nativeSegment.text }}</text>
												</view>
											</view>
										</view>
										<!-- #endif -->
										<message-actions
											v-if="controller.isLastStructuredSegment(m, speakerIndex) && controller.messageActionsVisible(m)"
											:message-id="m.id"
											:is-app-plus="controller.isAppPlus"
											:streaming="false"
											:streaming-status-text="''"
											:show-assistant-voice="controller.shouldShowAssistantVoicePill(m)"
											:assistant-voice-label="controller.assistantVoiceLabel(m)"
											:assistant-voice-pill-class="controller.assistantVoicePillClass(m)"
											:show-swipe-controls="!m.openingMessage && m.swipes && m.swipes.length > 1"
											:swipe-label="controller.swipeLabel(m)"
											:recovery="controller.recoveryForMessage(m)"
											:recovery-primary-label="controller.recoveryPrimaryLabel()"
											:recovery-regen-label="controller.tx('regen', '重新生成')"
											:recovery-copy-label="controller.tx('copy', '复制')"
											:can-copy-recovery="!!String(m.text || '').trim()"
											@assistant-voice-toggle="controller.toggleAssistantVoice(m)"
											@swipe-previous="controller.swipeCharMessage(m, -1)"
											@swipe-next="controller.swipeCharMessage(m, 1)"
											@recovery-primary="controller.runGenerationRecoveryPrimary($event)"
											@recovery-regen="controller.runGenerationRecoveryRegen($event)"
											@recovery-copy="controller.copyGenerationRecoveryText(m)"
											@recovery-close="controller.clearGenerationRecovery($event)"
										></message-actions>
									</message-content>
								</message-bubble>
							</view>
						</view>
					</template>
					<template v-else>
					<image
						v-if="m.role !== 'user'"
						class="chat-message-avatar chat-message-avatar--character"
						:src="m.speakerAvatarUrl || controller.charAvatar"
						mode="aspectFill"
						lazy-load
						@tap.stop="controller.openCharImagePreview()"
						@longpress.stop="controller.applyCharacterNameToDraft()"
					></image>
					<message-bubble
						:bubble-style="controller.messageBubbleInlineStyle(m)"
						:bubble-class="controller.messageBubbleClass(m)"
					>
						<text v-if="m.role !== 'user' && m.speakerName" class="chat-message-speaker">{{ m.speakerName }}</text>
						<message-content
							:image-urls="m.imageUrls"
							:local-kind="m.localKind"
							:local-prompt="m.localPrompt"
							:quote-meta="controller.messageQuoteMeta(m)"
							:is-user="m.role === 'user'"
							:has-text="!!String(m.text || '').trim()"
							@preview-image="controller.previewChatMessageImages(m, $event)"
						>
						<!-- #ifdef H5 -->
						<view
							v-if="controller.shouldRenderSplitBubbles(m)"
							class="chat-message-split-list"
						>
							<view
								v-for="(chunk, chunkIndex) in controller.assistantBubbleChunks(m)"
								:key="String(m.id) + '-split-' + chunkIndex"
								class="chat-message-split-part"
								:style="controller.assistantSplitBubbleStyle(m, chunkIndex)"
							>
								<view
									class="chat-message-markdown"
									:style="controller.assistantMessageTextStyle(m)"
					v-html="controller.mdHtml(chunk, controller.semanticForBubbleChunk(m, chunk, chunkIndex))"
									@tap="controller.onMarkdownTap($event)"
									@touchstart="controller.startMessageActionPress(m, $event)"
									@touchmove="controller.moveMessageActionPress($event)"
									@touchend="controller.endMessageActionPress($event)"
									@touchcancel="controller.endMessageActionPress($event)"
								></view>
							</view>
						</view>
						<view
							v-else-if="controller.isAssistantMessage(m)"
							class="chat-message-markdown"
							:style="controller.assistantMessageTextStyle(m)"
				v-html="controller.mdHtml(m.text, m.semantic)"
							@tap="controller.onMarkdownTap($event)"
							@touchstart="controller.startMessageActionPress(m, $event)"
							@touchmove="controller.moveMessageActionPress($event)"
							@touchend="controller.endMessageActionPress($event)"
							@touchcancel="controller.endMessageActionPress($event)"
						></view>
						<template v-else>
							<voice-message-card
								v-if="controller.shouldShowUserVoiceCard(m)"
								:message-id="m.id"
								:card-class="controller.userVoiceCardClass(m)"
								:duration-label="controller.userVoiceDurationLabel(m)"
								:transcript-text="controller.userVoiceTranscriptText(m)"
								:can-edit="controller.canEditUserMessage(m)"
								:edit-label="controller.chatUi.edit"
								@toggle="controller.toggleUserVoice(m)"
								@edit="controller.openEditUserMessage(m)"
								@press-start="controller.startMessageActionPress(m, $event)"
								@press-move="controller.moveMessageActionPress($event)"
								@press-end="controller.endMessageActionPress($event)"
								@press-cancel="controller.endMessageActionPress($event)"
							></voice-message-card>
							<template v-else>
								<text
									class="chat-message-user-text"
									:style="controller.userMessageTextStyle(m)"
									@touchstart="controller.startMessageActionPress(m, $event)"
									@touchmove="controller.moveMessageActionPress($event)"
									@touchend="controller.endMessageActionPress($event)"
									@touchcancel="controller.endMessageActionPress($event)"
								>{{ m.text }}</text>
								<text v-if="controller.canEditUserMessage(m)" class="user-edit-tag" @tap.stop="controller.openEditUserMessage(m)">{{ controller.chatUi.edit }}</text>
							</template>
						</template>
						<!-- #endif -->
						<!-- #ifndef H5 -->
						<view
							v-if="controller.shouldRenderSplitBubbles(m)"
							class="chat-message-split-list"
						>
							<view
								v-for="(chunk, chunkIndex) in controller.assistantBubbleChunks(m)"
								:key="String(m.id) + '-split-' + chunkIndex"
								class="chat-message-split-part"
								:style="controller.assistantSplitBubbleStyle(m, chunkIndex)"
								@touchstart="controller.startMessageActionPress(m, $event)"
								@touchmove="controller.moveMessageActionPress($event)"
								@touchend="controller.endMessageActionPress($event)"
								@touchcancel="controller.endMessageActionPress($event)"
							>
								<view :class="controller.nativeMessageContentClass()">
									<view
						v-for="(seg, si) in controller.mdSegments(chunk, controller.semanticForBubbleChunk(m, chunk, chunkIndex))"
										:key="si"
										:class="controller.nativeSegmentClass(seg)"
										:style="controller.nativeSegmentWrapStyle(seg)"
									>
										<view :class="controller.nativeSegmentLineClass()" :style="controller.nativeSegmentLineStyle()">
											<view
												v-if="controller.chatAppearanceSegmentLabelsEnabled"
												:class="controller.nativeSegmentLabelClass(seg)"
												:style="controller.nativeSegmentLabelStyle(seg)"
											>
												<text :class="controller.nativeSegmentLabelTextClass()" :style="controller.nativeSegmentLabelTextStyle(seg)">{{ controller.nativeSegmentLabelText(seg) }}</text>
											</view>
											<text :class="controller.nativeSegmentTextClass(seg)" :style="controller.nativeSegmentTextStyle(seg)">{{ seg.text }}</text>
										</view>
									</view>
								</view>
							</view>
						</view>
						<view
							v-else-if="controller.isAssistantMessage(m)"
							:class="controller.nativeMessageContentClass()"
							@touchstart="controller.startMessageActionPress(m, $event)"
							@touchmove="controller.moveMessageActionPress($event)"
							@touchend="controller.endMessageActionPress($event)"
							@touchcancel="controller.endMessageActionPress($event)"
						>
							<view
				v-for="(seg, si) in controller.mdSegments(m.text, m.semantic)"
								:key="si"
								:class="controller.nativeSegmentClass(seg)"
								:style="controller.nativeSegmentWrapStyle(seg)"
							>
								<view :class="controller.nativeSegmentLineClass()" :style="controller.nativeSegmentLineStyle()">
									<view
										v-if="controller.chatAppearanceSegmentLabelsEnabled"
										:class="controller.nativeSegmentLabelClass(seg)"
										:style="controller.nativeSegmentLabelStyle(seg)"
									>
										<text :class="controller.nativeSegmentLabelTextClass()" :style="controller.nativeSegmentLabelTextStyle(seg)">{{ controller.nativeSegmentLabelText(seg) }}</text>
									</view>
									<text
										v-if="seg.type === 'speech'"
										:class="controller.nativeSegmentTextClass(seg)"
										:style="controller.nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else-if="seg.type === 'action'"
										:class="controller.nativeSegmentTextClass(seg)"
										:style="controller.nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else-if="seg.type === 'thought'"
										:class="controller.nativeSegmentTextClass(seg)"
										:style="controller.nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else
										:class="controller.nativeSegmentTextClass(seg)"
										:style="controller.nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
								</view>
							</view>
						</view>
						<view v-else>
							<voice-message-card
								v-if="controller.shouldShowUserVoiceCard(m)"
								:message-id="m.id"
								:card-class="controller.userVoiceCardClass(m)"
								:duration-label="controller.userVoiceDurationLabel(m)"
								:transcript-text="controller.userVoiceTranscriptText(m)"
								:can-edit="controller.canEditUserMessage(m)"
								:edit-label="controller.chatUi.edit"
								@toggle="controller.toggleUserVoice(m)"
								@edit="controller.openEditUserMessage(m)"
								@press-start="controller.startMessageActionPress(m, $event)"
								@press-move="controller.moveMessageActionPress($event)"
								@press-end="controller.endMessageActionPress($event)"
								@press-cancel="controller.endMessageActionPress($event)"
							></voice-message-card>
							<template v-else>
								<text
									:class="controller.nativeUserMessageTextClass()"
									:style="controller.userMessageTextStyle(m)"
									@touchstart="controller.startMessageActionPress(m, $event)"
									@touchmove="controller.moveMessageActionPress($event)"
									@touchend="controller.endMessageActionPress($event)"
									@touchcancel="controller.endMessageActionPress($event)"
								>{{ m.text }}</text>
								<text v-if="controller.canEditUserMessage(m)" class="user-edit-tag" @tap.stop="controller.openEditUserMessage(m)">{{ controller.chatUi.edit }}</text>
							</template>
						</view>
						<!-- #endif -->
						<message-actions
							v-if="controller.messageActionsVisible(m)"
							:message-id="m.id"
							:is-app-plus="controller.isAppPlus"
							:streaming="controller.isStreamingAssistantRow(m)"
							:streaming-status-text="controller.streamingAssistantStatusText(m)"
							:show-assistant-voice="controller.shouldShowAssistantVoicePill(m)"
							:assistant-voice-label="controller.assistantVoiceLabel(m)"
							:assistant-voice-pill-class="controller.assistantVoicePillClass(m)"
							:show-swipe-controls="controller.isAssistantMessage(m) && !m.openingMessage && m.swipes && m.swipes.length > 1"
							:swipe-label="controller.swipeLabel(m)"
							:recovery="controller.recoveryForMessage(m)"
							:recovery-primary-label="controller.recoveryPrimaryLabel()"
							:recovery-regen-label="controller.tx('regen', '重新生成')"
							:recovery-copy-label="controller.tx('copy', '复制')"
							:can-copy-recovery="!!String(m.text || '').trim()"
							@assistant-voice-toggle="controller.toggleAssistantVoice(m)"
							@swipe-previous="controller.swipeCharMessage(m, -1)"
							@swipe-next="controller.swipeCharMessage(m, 1)"
							@recovery-primary="controller.runGenerationRecoveryPrimary($event)"
							@recovery-regen="controller.runGenerationRecoveryRegen($event)"
							@recovery-copy="controller.copyGenerationRecoveryText(m)"
							@recovery-close="controller.clearGenerationRecovery($event)"
						></message-actions>
						</message-content>
					</message-bubble>
					<image
						v-if="m.role === 'user'"
						class="chat-message-avatar"
						:src="controller.resolvedUserAvatar"
						mode="aspectFill"
						lazy-load
						@error="controller.handleUserAvatarError($event)"
					></image>
					</template>
				</view>
				<view id="bottom-anchor" class="chat-bottom-anchor"></view>
			</view>
		</scroll-view>
</template>

<script>
import MessageBubble from '@/components/tavern/message-bubble.vue';
import MessageContent from '@/components/tavern/message-content.vue';
import MessageActions from '@/components/tavern/message-actions.vue';
import VoiceMessageCard from '@/components/tavern/voice-message-card.vue';

export default {
  name: 'ChatMessageList',
  components: { MessageBubble, MessageContent, MessageActions, VoiceMessageCard },
  props: { controller: { type: Object, required: true } }
}
</script>

<style scoped lang="scss">
	.chat-scroll {
		flex: 1;
		height: 0;
		min-height: 0;
		padding: 14rpx 24rpx 4rpx;
		box-sizing: border-box;
		opacity: 1;
		transition: opacity 0.12s ease;
	}

	.chat-scroll-content {
		width: 100%;
		max-width: 820px;
		margin: 0 auto;
		box-sizing: border-box;
	}

	.chat-scroll--preparing {
		opacity: 0;
		pointer-events: none;
	}

	.chat-message-row {
		display: flex;
		align-items: flex-end;
		gap: 10rpx;
		width: 100%;
		min-width: 0;
		margin-bottom: 22rpx;
		box-sizing: border-box;
	}

	.chat-message-row--assistant {
		justify-content: flex-start;
	}

	.ensemble-message-list {
		display: flex;
		flex: 1 1 auto;
		flex-direction: column;
		align-items: flex-start;
		gap: 18rpx;
		width: 100%;
		min-width: 0;
	}

	.ensemble-message-segment {
		display: flex;
		align-items: flex-end;
		gap: 10rpx;
		width: 100%;
		min-width: 0;
	}

	.ensemble-message-segment .ensemble-message-bubble {
		max-width: 78%;
	}

	.ensemble-message-segment--narrator {
		align-items: stretch;
	}

	.ensemble-message-segment--narrator .ensemble-message-bubble {
		max-width: 82%;
	}

	.ensemble-narrator-rail {
		display: flex;
		flex: 0 0 72rpx;
		align-items: flex-start;
		justify-content: center;
		width: 72rpx;
		min-width: 72rpx;
		padding-top: 24rpx;
		box-sizing: border-box;
	}

	.chat-message-row:nth-last-child(2) {
		margin-bottom: 6rpx;
	}

	.chat-bottom-anchor {
		height: 6rpx;
	}

	.ensemble-narrator-mark {
		width: 20rpx;
		height: 2rpx;
		border-radius: 2rpx;
		background: rgba(208, 222, 228, 0.58);
	}

	.chat-message-row--user {
		justify-content: flex-end;
	}

	.chat-message-row--read-empty {
		display: none;
	}

	.chat-message-avatar {
		display: block;
		width: 72rpx;
		min-width: 72rpx;
		max-width: 72rpx;
		height: 72rpx;
		min-height: 72rpx;
		max-height: 72rpx;
		flex: 0 0 72rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.18);
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.12);
		box-shadow: 0 10rpx 22rpx rgba(15, 23, 42, 0.14);
		box-sizing: border-box;
		overflow: hidden;
		object-fit: cover;
		line-height: 0;
	}

	.chat-message-avatar--character:hover {
		transform: translateY(-2rpx) scale(1.03);
		box-shadow: 0 12rpx 20rpx rgba(12, 18, 34, 0.26);
	}

	.chat-history-banner {
		display: flex;
		justify-content: center;
		margin-bottom: 18rpx;
	}

	.chat-history-banner__text {
		padding: 10rpx 24rpx;
		border-radius: 999rpx;
		font-size: 22rpx;
		line-height: 1.2;
		color: rgba(226, 232, 240, 0.88);
		background: rgba(15, 23, 42, 0.42);
		border: 1rpx solid rgba(148, 163, 184, 0.22);
		backdrop-filter: blur(10rpx);
		box-shadow: 0 12rpx 24rpx rgba(8, 15, 28, 0.14);
	}

	.chat-history-banner--loading .chat-history-banner__text {
		color: #f8fafc;
		background: linear-gradient(135deg, rgba(56, 189, 248, 0.24), rgba(99, 102, 241, 0.28));
		border-color: rgba(125, 211, 252, 0.32);
	}

	.user-edit-tag {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.55);
	}

	.chat-message-row--user .user-edit-tag {
		text-align: right;
	}

	.chat-message-speaker {
		display: block;
		margin: 0 0 10rpx;
		font-size: 22rpx;
		line-height: 1.35;
		font-weight: 750;
		color: #2b6177;
	}

	.chat-message-speaker--narrator {
		color: rgba(210, 224, 230, 0.76);
		font-weight: 650;
	}

	@media screen and (max-width: 480px) {
		.ensemble-message-segment .ensemble-message-bubble,
		.ensemble-message-segment--narrator .ensemble-message-bubble {
			max-width: calc(100% - 82rpx);
		}
	}

	.chat-message-markdown .st-chat-rich-block {
		display: block;
		width: 100%;
		max-width: 100%;
		min-width: 0;
		margin: 2px 0 4px;
		box-sizing: border-box;
		contain: layout paint;
		isolation: isolate;
		color: inherit;
		-webkit-text-fill-color: currentColor;
	}

	.chat-message-markdown .st-chat-rich-block > * {
		max-width: 100%;
		box-sizing: border-box;
	}

	.chat-message-markdown .st-chat-rich-block p:first-child,
	.chat-message-markdown .st-chat-rich-block div:first-child {
		margin-top: 0;
	}

	.chat-message-markdown .st-chat-rich-block p:last-child,
	.chat-message-markdown .st-chat-rich-block div:last-child {
		margin-bottom: 0;
	}

	.chat-message-markdown .st-chat-rich-block--status {
		padding: 8px 0 2px;
		border-top: 1px solid rgba(255, 255, 255, 0.18);
	}

	.chat-message-markdown .st-chat-rich-block details {
		width: 100%;
		margin: 0;
		padding: 0;
	}

	.chat-message-markdown .st-chat-rich-block summary {
		min-height: 28px;
		padding: 4px 0;
		font-weight: 700;
		line-height: 1.5;
		cursor: pointer;
	}

	.chat-message-markdown .st-chat-rich-block table {
		width: 100%;
		max-width: 100%;
		margin: 4px 0;
		border-collapse: collapse;
		table-layout: auto;
	}

	.chat-message-markdown .st-chat-rich-block th,
	.chat-message-markdown .st-chat-rich-block td {
		padding: 6px 8px;
		border-bottom: 1px solid rgba(255, 255, 255, 0.14);
		text-align: left;
		vertical-align: top;
		word-break: break-word;
	}

	.chat-message-markdown .st-chat-rich-block th {
		font-weight: 700;
	}

	.chat-message-markdown .st-chat-rich-block progress,
	.chat-message-markdown .st-chat-rich-block meter {
		display: block;
		width: 100%;
		max-width: 100%;
		height: 10px;
		margin: 6px 0;
	}

	.chat-message-markdown .st-chat-rich-pending {
		display: block;
		width: 100%;
		min-width: 0;
		box-sizing: border-box;
		white-space: pre-wrap;
		word-break: break-word;
		opacity: 0.84;
	}

</style>
