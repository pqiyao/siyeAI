<template>
	<view class="wrap" :class="{ focused: inputFocus, 'wrap--with-bg': hasChatBackground, 'wrap--app-plus': isAppPlus }" :style="wrapStyle">
		<image class="chat-default-bg" :src="defaultChatBackgroundUrl" mode="aspectFill"></image>
		<image v-if="hasCustomChatBackground" class="chat-role-bg" :src="chatBackgroundUrl" mode="aspectFill"></image>
		<tavern-nav-bar :title="title" mode="dark" @back="goBack">
			<template #right>
				<view v-if="jgOn" class="nav-right-tools">
					<view
						v-if="voiceFeatureEnabledGlobal !== false"
						class="nav-voice-toggle"
						:class="{ 'nav-voice-toggle--off': !assistantVoiceAutoEnabled }"
						@tap="toggleAssistantVoiceAuto"
					>
						<image
							class="nav-voice-toggle-icon"
							:src="assistantVoiceAutoEnabled ? assistantVoiceOnIcon : assistantVoiceOffIcon"
							mode="aspectFit"
						></image>
					</view>
					<view
						v-if="voiceFeatureEnabledGlobal !== false"
						class="nav-voice-config"
						:class="{ 'nav-voice-config--active': isCharacterVoiceConfigCustomized() }"
						@tap="openCharacterVoicePanel"
					>
						<image class="nav-voice-config-icon" :src="characterVoiceIcon" mode="aspectFit"></image>
					</view>
					<text class="nav-link" @tap="goPersona">{{ chatUi.settings }}</text>
				</view>
			</template>
		</tavern-nav-bar>

		<view v-if="jgOn && jgChatLoadState === 'loading'" class="chat-fill">
			<text class="chat-fill-txt">{{ tx('chat_loading', '加载中...') }}</text>
		</view>

		<view v-else-if="jgOn && jgChatLoadState === 'error'" class="chat-fill chat-fill--err">
			<text class="chat-fill-txt">{{ jgChatErrorMsg || tx('chat_load_failed', '加载失败') }}</text>
			<view class="chat-fill-retry" @tap="retryJgChatLoad">{{ tx('retry', '点击重试') }}</view>
			<view class="chat-fill-back" @tap="goBack">{{ tx('back', '返回') }}</view>
		</view>

		<template v-else>
			<view v-if="jgOn" class="tool-bar">
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onRegen">{{ chatUi.regen }}</text>
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onContinue">{{ chatUi.continue }}</text>
				<text class="tool-i" @tap="onRestart">{{ chatUi.restart }}</text>
				<text class="tool-i" @tap="onMem">{{ chatUi.memory }}</text>
			</view>
			<view v-if="jgOn && jgChatLoadState === 'ready' && memoryBarText" class="memory-bar" @tap="onMem">
				<text class="memory-bar-txt">{{ memoryBarText }}</text>
			</view>
		<scroll-view
			class="chat-scroll"
			:class="{ 'chat-scroll--preparing': !chatViewportReady }"
			scroll-y
			:scroll-into-view="scrollTo"
			:scroll-with-animation="chatScrollWithAnimation"
			@scroll="onChatScroll"
			@scrolltoupper="onChatScrollToUpper"
			@scrolltolower="onChatScrollToLower"
			@touchstart="onChatTouchStart"
			@touchmove="onChatTouchMove"
			@touchend="onChatTouchEnd"
			@touchcancel="onChatTouchEnd"
			:upper-threshold="60"
			:lower-threshold="80"
		>
			<view
				class="chat-scroll-content"
				@touchstart="onChatTouchStart"
				@touchmove="onChatTouchMove"
				@touchend="onChatTouchEnd"
				@touchcancel="onChatTouchEnd"
			>
				<view v-if="showHistoryLoadBanner()" class="chat-history-banner" :class="{ 'chat-history-banner--loading': messageHistoryLoading }">
					<text class="chat-history-banner__text">{{ historyLoadBannerText() }}</text>
				</view>
				<view
					v-for="m in messages"
					:key="m.id"
					:id="'m-' + m.id"
					class="msg-row"
					:class="[m.role === 'user' ? 'me' : 'them', { 'msg-row--app-plus': isAppPlus }]"
				>
					<image
						v-if="m.role !== 'user'"
						class="av av--char"
						:src="charAvatar"
						mode="aspectFill"
						lazy-load
						@tap.stop="openCharImagePreview"
						@longpress.stop="applyCharacterNameToDraft"
					></image>
					<view
						class="bubble"
						:class="{
							'bubble--char': m.role !== 'user',
							'bubble--me': m.role === 'user',
							'bubble--them': m.role !== 'user',
							'bubble--app-plus': isAppPlus,
							'bubble--streaming': isStreamingAssistantRow(m),
							'bubble--has-image': m.imageUrls && m.imageUrls.length,
							'bubble--image-only': m.imageUrls && m.imageUrls.length && !String(m.text || '').trim(),
							'bubble--text-only': (!m.imageUrls || !m.imageUrls.length) && String(m.text || '').trim()
						}"
					>
						<view v-if="m.imageUrls && m.imageUrls.length" class="msg-image-list">
							<image
								v-for="(img, imgIndex) in m.imageUrls"
								:key="imgIndex"
								class="msg-image"
								:src="img"
								mode="aspectFill"
								lazy-load
								@tap.stop="previewChatMessageImages(m, imgIndex)"
							></image>
						</view>
						<view v-if="m.localKind === 'image_generation' && m.localPrompt" class="local-image-prompt-row">
							<text class="local-image-prompt-text">{{ m.localPrompt }}</text>
						</view>
						<view v-if="messageQuoteMeta(m)" class="msg-quote-preview" :class="{ 'msg-quote-preview--me': m.role === 'user' }">
							<text class="msg-quote-preview-speaker">{{ messageQuoteMeta(m).speaker }}</text>
							<text class="msg-quote-preview-text">{{ messageQuoteMeta(m).text }}</text>
						</view>
						<!-- #ifdef H5 -->
						<view
							v-if="isAssistantMessage(m)"
							class="md-inner"
							v-html="mdHtml(m.text)"
							@tap="onMarkdownTap"
							@touchstart="startMessageActionPress(m, $event)"
							@touchmove="moveMessageActionPress($event)"
							@touchend="endMessageActionPress"
							@touchcancel="endMessageActionPress"
						></view>
						<template v-else>
							<view v-if="shouldShowUserVoiceCard(m)" class="user-voice-row">
								<view
									class="user-voice-card"
									:class="userVoiceCardClass(m)"
									@tap.stop="toggleUserVoice(m)"
								>
									<view class="user-voice-wave">
										<text v-for="n in 4" :key="'user_voice_bar_' + m.id + '_' + n" class="user-voice-bar"></text>
									</view>
									<text class="user-voice-duration">{{ userVoiceDurationLabel(m) }}</text>
								</view>
								<view v-if="userVoiceTranscriptText(m)" class="user-voice-transcript-wrap">
									<text
										class="user-voice-transcript"
										@touchstart="startMessageActionPress(m, $event)"
										@touchmove="moveMessageActionPress($event)"
										@touchend="endMessageActionPress"
										@touchcancel="endMessageActionPress"
									>{{ userVoiceTranscriptText(m) }}</text>
									<text
										v-if="canEditUserMessage(m)"
										class="user-edit-tag user-edit-tag--voice"
										@tap.stop="openEditUserMessage(m)"
									>{{ chatUi.edit }}</text>
								</view>
							</view>
							<template v-else>
								<text
									class="txt"
									@touchstart="startMessageActionPress(m, $event)"
									@touchmove="moveMessageActionPress($event)"
									@touchend="endMessageActionPress"
									@touchcancel="endMessageActionPress"
								>{{ m.text }}</text>
								<text v-if="canEditUserMessage(m)" class="user-edit-tag" @tap.stop="openEditUserMessage(m)">{{ chatUi.edit }}</text>
							</template>
						</template>
						<!-- #endif -->
						<!-- #ifndef H5 -->
						<view
							v-if="isAssistantMessage(m)"
							class="md-inner md-inner--native"
							@touchstart="startMessageActionPress(m, $event)"
							@touchmove="moveMessageActionPress($event)"
							@touchend="endMessageActionPress"
							@touchcancel="endMessageActionPress"
						>
							<view
								v-for="(seg, si) in mdSegments(m.text)"
								:key="si"
								class="st-chat-seg-native"
								:class="'st-chat-seg-native--' + seg.type"
								:style="nativeSegmentWrapStyle(seg)"
							>
								<text class="st-chat-seg-text" :style="nativeSegmentTextStyle(seg)">{{ seg.text }}</text>
							</view>
						</view>
						<view v-else>
							<view v-if="shouldShowUserVoiceCard(m)" class="user-voice-row">
								<view
									class="user-voice-card"
									:class="userVoiceCardClass(m)"
									@tap.stop="toggleUserVoice(m)"
								>
									<view class="user-voice-wave">
										<text v-for="n in 4" :key="'user_voice_native_bar_' + m.id + '_' + n" class="user-voice-bar"></text>
									</view>
									<text class="user-voice-duration">{{ userVoiceDurationLabel(m) }}</text>
							</view>
							<view v-if="userVoiceTranscriptText(m)" class="user-voice-transcript-wrap">
								<text
									class="user-voice-transcript"
									@touchstart="startMessageActionPress(m, $event)"
									@touchmove="moveMessageActionPress($event)"
									@touchend="endMessageActionPress"
									@touchcancel="endMessageActionPress"
								>{{ userVoiceTranscriptText(m) }}</text>
								<text
									v-if="canEditUserMessage(m)"
									class="user-edit-tag user-edit-tag--voice"
										@tap.stop="openEditUserMessage(m)"
									>{{ chatUi.edit }}</text>
								</view>
							</view>
							<template v-else>
								<text
									class="txt"
									@touchstart="startMessageActionPress(m, $event)"
									@touchmove="moveMessageActionPress($event)"
									@touchend="endMessageActionPress"
									@touchcancel="endMessageActionPress"
								>{{ m.text }}</text>
								<text v-if="canEditUserMessage(m)" class="user-edit-tag" @tap.stop="openEditUserMessage(m)">{{ chatUi.edit }}</text>
							</template>
						</view>
						<!-- #endif -->
						<view v-if="isStreamingAssistantRow(m)" class="stream-inline" :class="{ 'stream-inline--app-plus': isAppPlus }">
							<view class="stream-inline-wave">
								<text v-for="n in 3" :key="'stream_inline_' + m.id + '_' + n" class="stream-inline-bar"></text>
							</view>
							<text class="stream-inline-text">{{ streamingAssistantStatusText(m) }}</text>
						</view>
						<view v-if="shouldShowAssistantVoicePill(m)" class="assistant-voice-row">
							<view
								class="assistant-voice-pill"
								:class="assistantVoicePillClass(m)"
								@tap.stop="toggleAssistantVoice(m)"
							>
								<view class="assistant-voice-pill-dot"></view>
								<text class="assistant-voice-pill-text">{{ assistantVoiceLabel(m) }}</text>
							</view>
						</view>
						<view v-if="isAssistantMessage(m) && m.swipes && m.swipes.length > 1" class="swipe-row">
							<text class="swipe-btn" @tap.stop="swipeCharMessage(m, -1)">&lt;</text>
							<text class="swipe-num">{{ swipeLabel(m) }}</text>
							<text class="swipe-btn" @tap.stop="swipeCharMessage(m, 1)">&gt;</text>
						</view>
						<view v-if="recoveryForMessage(m)" class="generation-recovery">
							<view class="generation-recovery-copy">
								<text class="generation-recovery-title">{{ recoveryForMessage(m).title }}</text>
								<text class="generation-recovery-message">{{ recoveryForMessage(m).message }}</text>
							</view>
							<view class="generation-recovery-actions">
								<text class="generation-recovery-btn generation-recovery-btn--primary" @tap.stop="runGenerationRecoveryPrimary">
									{{ recoveryPrimaryLabel() }}
								</text>
								<text
									v-if="recoveryForMessage(m).canRegen"
									class="generation-recovery-btn"
									@tap.stop="runGenerationRecoveryRegen"
								>
									{{ tx('regen', '重新生成') }}
								</text>
								<text
									v-if="String(m.text || '').trim()"
									class="generation-recovery-btn"
									@tap.stop="copyGenerationRecoveryText(m)"
								>
									{{ tx('copy', '复制') }}
								</text>
								<text class="generation-recovery-close" @tap.stop="clearGenerationRecovery">×</text>
							</view>
						</view>
					</view>
					<image
						v-if="m.role === 'user'"
						class="av"
						:src="resolvedUserAvatar"
						mode="aspectFill"
						lazy-load
						@error="handleUserAvatarError"
					></image>
				</view>
				<view id="bottom-anchor" style="height: 24rpx;"></view>
			</view>
		</scroll-view>

		<view v-if="showTypingHintRow()" class="typing-row" :class="{ 'typing-row--app-plus': isAppPlus }">
			<text class="typing-hint">{{ tx('ai_thinking', '思考中...') }}</text>
			<text v-if="showStopStream" class="stop-stream" @tap="stopGeneration">{{ chatUi.stop }}</text>
		</view>
		<view v-if="shouldShowReplyHelpPanel()" class="reply-help-panel">
			<view class="reply-help-head">
				<text class="reply-help-title">{{ tx('reply_help_title', 'AI帮答') }}</text>
				<view class="reply-help-head-actions">
					<text
						class="reply-help-head-btn"
						:class="{ 'reply-help-head-btn--disabled': replySuggest.loading }"
						@tap="refreshReplySuggestions(true)"
					>
						{{ tx('reply_help_refresh', '换一批') }}
					</text>
					<text class="reply-help-head-btn" @tap="closeReplySuggestions">{{ tx('collapse', '收起') }}</text>
				</view>
			</view>
			<text v-if="replySuggest.loading" class="reply-help-state">
				{{ tx('reply_help_loading', '现在正在让四叶生成帮答建议') }}
			</text>
			<text v-else-if="replySuggest.error" class="reply-help-state reply-help-state--error">
				{{ replySuggest.error }}
			</text>
			<view v-else class="reply-help-list">
				<view
					v-for="(item, idx) in replySuggest.items"
					:key="idx"
					class="reply-help-card"
					@tap="applyReplySuggestion(item)"
				>
					<text class="reply-help-index">{{ idx + 1 }}</text>
					<text class="reply-help-text">{{ item }}</text>
				</view>
			</view>
		</view>
		<view v-if="editOverlay.visible" class="edit-mask" @tap="closeEditUser">
			<view class="edit-panel" @tap.stop>
				<text class="edit-title">{{ chatUi.editTitle }}</text>
				<text class="edit-sub">{{ chatUi.editSub }}</text>
				<textarea class="edit-ta" v-model="editOverlay.draft" :disabled="editOverlay.saving" auto-height />
				<view class="edit-actions">
					<text class="edit-btn edit-btn--muted" @tap="closeEditUser">{{ tx('cancel', '取消') }}</text>
					<text class="edit-btn edit-btn--primary" @tap="submitEditUser">{{ tx('save', '保存') }}</text>
				</view>
			</view>
		</view>
		<view v-if="messageActionSheet.visible" class="message-action-mask" @tap="closeMessageActionSheet">
			<view
				class="message-action-menu"
				:style="{ left: messageActionSheet.leftPx + 'px', top: messageActionSheet.topPx + 'px' }"
				@tap.stop
			>
				<view
					v-if="messageActionSheet.text"
					class="message-action-item"
					:class="{ 'message-action-item--disabled': messageActionSheet.deleting }"
					@tap="copyMessageActionText"
				>
					<text class="message-action-item-label">{{ tx('copy', '复制') }}</text>
				</view>
				<view
					v-if="messageActionSheet.text"
					class="message-action-item"
					:class="{ 'message-action-item--disabled': messageActionSheet.deleting }"
					@tap="quoteMessageActionText"
				>
					<text class="message-action-item-label">{{ tx('quote', '引用') }}</text>
				</view>
				<view
					v-if="messageActionSheet.canDelete"
					class="message-action-item message-action-item--danger"
					:class="{ 'message-action-item--disabled': messageActionSheet.deleting }"
					@tap="confirmDeleteMessageAction"
				>
					<text class="message-action-item-label">{{ messageActionSheet.deleting ? tx('deleting', '删除中...') : tx('delete', '删除') }}</text>
				</view>
				<view
					v-if="!messageActionSheet.text && !messageActionSheet.canDelete"
					class="message-action-item message-action-item--disabled"
				>
					<text class="message-action-item-label">{{ tx('message_action_empty', '暂无可操作项') }}</text>
				</view>
			</view>
		</view>
		<view v-if="commercialPrompt.visible" class="commercial-mask" @tap="closeCommercialPrompt">
			<view class="commercial-card" @tap.stop>
				<text class="commercial-title">{{ commercialPrompt.title }}</text>
				<text class="commercial-sub">{{ commercialPrompt.message }}</text>
				<view class="commercial-actions">
					<text
						v-if="commercialPrompt.kind !== 'chat_quota'"
						class="commercial-btn commercial-btn--ghost"
						@tap="closeCommercialPrompt"
					>{{ chatUi.later }}</text>
					<text
						v-if="commercialPrompt.secondaryUrl"
						class="commercial-btn commercial-btn--muted"
						@tap="goCommercial(commercialPrompt.secondaryUrl)"
					>
						{{ commercialPrompt.secondaryText || chatUi.recharge }}
					</text>
					<text class="commercial-btn commercial-btn--primary" @tap="goCommercial(commercialPrompt.primaryUrl)">
						{{ commercialPrompt.primaryText || chatUi.openVip }}
					</text>
				</view>
			</view>
		</view>
		<view v-if="charImagePreviewVisible" class="char-image-mask" @tap="closeCharImagePreview">
			<view class="char-image-shell" @tap.stop>
				<text class="char-image-close" @tap="closeCharImagePreview">×</text>
				<image class="char-image-full" :src="charPreviewImage" mode="aspectFit" lazy-load></image>
			</view>
		</view>
		<view v-if="voiceFeatureEnabledGlobal !== false && characterVoicePanel.visible" class="character-voice-mask" @tap="closeCharacterVoicePanel">
			<view class="character-voice-sheet" :style="characterVoiceSheetInlineStyle()" @tap.stop>
				<view class="character-voice-sheet-top">
					<view class="character-voice-sheet-head">
						<image class="character-voice-avatar" :src="charAvatar" mode="aspectFill" lazy-load></image>
						<view class="character-voice-head-copy">
							<text class="character-voice-title">{{ tx('character_voice_title', '角色语音') }}</text>
							<text class="character-voice-sub">{{ tx('character_voice_sub', '这里只覆盖当前角色的 TTS 模型、音色和自动播放') }}</text>
						</view>
					</view>
					<text class="character-voice-close" @tap="closeCharacterVoicePanel">×</text>
				</view>
				<scroll-view class="character-voice-scroll" :style="characterVoiceScrollInlineStyle()" scroll-y :show-scrollbar="false">
					<view class="character-voice-scroll-body">
				<view class="character-voice-global-card">
					<view class="character-voice-global-head">
						<view class="character-voice-global-copy">
							<text class="character-voice-global-title">{{ tx('character_voice_global_title', '当前全局 TTS') }}</text>
							<text class="character-voice-global-sub">{{ characterVoiceGlobalModeText() }}</text>
						</view>
					</view>
					<text v-if="characterVoiceGlobalState.loading" class="character-voice-global-empty">
						{{ tx('character_voice_global_loading', '正在读取当前全局 TTS 配置...') }}
					</text>
					<text v-else-if="characterVoiceGlobalState.error" class="character-voice-global-empty character-voice-global-empty--error">
						{{ characterVoiceGlobalState.error }}
					</text>
					<view v-else class="character-voice-global-pills">
						<text class="character-voice-global-pill">{{ characterVoiceGlobalProviderText() }}</text>
						<text class="character-voice-global-pill">{{ characterVoiceGlobalTtsText() }}</text>
						<text class="character-voice-global-pill">{{ characterVoiceGlobalVoiceText() }}</text>
					</view>
					<view class="character-voice-global-actions">
						<text class="character-voice-global-action" @tap="goAiSettings">
							{{ tx('go_ai_settings', '去 AI 设置') }}
						</text>
						<text
							v-if="!characterVoiceGlobalState.loading && !characterVoiceGlobalState.error"
							class="character-voice-global-action character-voice-global-action--primary"
							@tap="applyCharacterVoiceGlobalDefaults"
						>
							{{ tx('character_voice_apply_global', '把全局 TTS / 音色带入当前角色') }}
						</text>
					</view>
				</view>
				<view class="character-voice-field">
					<text class="character-voice-label">{{ tx('character_voice_enabled', '启用角色语音') }}</text>
					<view class="character-voice-switch-row">
						<text
							class="character-voice-switch"
							:class="{ 'character-voice-switch--active': characterVoicePanel.enabled }"
							@tap="setCharacterVoicePanelEnabled(true)"
						>{{ tx('on', '开启') }}</text>
						<text
							class="character-voice-switch"
							:class="{ 'character-voice-switch--active': !characterVoicePanel.enabled }"
							@tap="setCharacterVoicePanelEnabled(false)"
						>{{ tx('off', '关闭') }}</text>
					</view>
				</view>
				<view class="character-voice-field" :class="{ 'character-voice-field--disabled': !characterVoicePanel.enabled }">
					<text class="character-voice-label">{{ tx('character_voice_auto_play', '自动播放') }}</text>
					<view class="character-voice-switch-row">
						<text
							class="character-voice-switch"
							:class="{ 'character-voice-switch--active': characterVoicePanel.autoPlayEnabled }"
							@tap="setCharacterVoicePanelAutoPlay(true)"
						>{{ tx('character_voice_auto', '自动') }}</text>
						<text
							class="character-voice-switch"
							:class="{ 'character-voice-switch--active': !characterVoicePanel.autoPlayEnabled }"
							@tap="setCharacterVoicePanelAutoPlay(false)"
						>{{ tx('character_voice_manual', '手动') }}</text>
					</view>
				</view>
				<view class="character-voice-field" :class="{ 'character-voice-field--disabled': !characterVoicePanel.enabled }">
					<text class="character-voice-label">{{ tx('character_voice_model', 'TTS 模型') }}</text>
					<input
						class="character-voice-input"
						v-model="characterVoicePanel.ttsModelName"
						:disabled="!characterVoicePanel.enabled || characterVoicePanel.saving"
						:placeholder="tx('character_voice_model_placeholder', '留空则跟随全局 TTS 模型')"
						confirm-type="done"
					/>
				</view>
				<view class="character-voice-field" :class="{ 'character-voice-field--disabled': !characterVoicePanel.enabled }">
					<view class="character-voice-label-row">
						<text class="character-voice-label">{{ tx('character_voice_voice', '音色') }}</text>
						<text
							v-if="characterVoicePanelVoiceTemplates.length"
							class="character-voice-meta"
						>
							{{ characterVoicePanelVoiceTemplates.length }}
						</text>
						<text v-else-if="characterVoicePanelVoicePresets.length" class="character-voice-meta">
							{{ characterVoicePanelVoicePresets.length }}
						</text>
					</view>
					<text class="character-voice-template-intro">{{ characterVoiceTemplateIntroText() }}</text>
					<scroll-view
						v-if="characterVoicePanelVoiceTemplates.length"
						class="character-voice-template-scroll"
						scroll-y
						:show-scrollbar="false"
					>
						<view class="character-voice-template-list">
							<view
								v-for="item in characterVoicePanelVoiceTemplates"
								:key="'character_voice_template_' + item.code"
								class="character-voice-template-card"
								:class="{ 'character-voice-template-card--active': characterVoicePanel.ttsVoiceTemplateCode === item.code }"
								@tap="selectCharacterVoiceTemplate(item)"
							>
								<image
									v-if="characterVoiceTemplateAssetUrl(item.coverImageUrl)"
									class="character-voice-template-card__cover"
									:src="characterVoiceTemplateAssetUrl(item.coverImageUrl)"
									mode="aspectFill"
								/>
								<view v-else class="character-voice-template-card__cover character-voice-template-card__cover--placeholder">音</view>
								<view class="character-voice-template-card__body">
									<view class="character-voice-template-card__head">
										<text class="character-voice-template-card__title">{{ item.displayName || item.code }}</text>
										<text
											class="character-voice-template-card__badge"
											:class="'character-voice-template-card__badge--' + (item.statusCode || 'pending')"
										>{{ item.ready ? tx('ready', '已就绪') : (item.statusText || tx('character_voice_template_pending', '首次使用自动生成')) }}</text>
									</view>
									<text v-if="item.recommendedModelName" class="character-voice-template-card__meta">{{ item.recommendedModelName }}</text>
									<text v-if="item.description" class="character-voice-template-card__desc">{{ item.description }}</text>
								</view>
								<text v-if="characterVoicePanel.ttsVoiceTemplateCode === item.code" class="character-voice-template-card__check">已选</text>
							</view>
						</view>
					</scroll-view>
					<view v-if="characterVoicePanel.ttsVoiceTemplateCode" class="character-voice-template-active">
						<view class="character-voice-template-active__copy">
							<text class="character-voice-template-active__title">{{ characterVoiceSelectedTemplateTitleText() }}</text>
							<text class="character-voice-template-active__desc">{{ characterVoiceSelectedTemplateStatusText() }}</text>
						</view>
						<text class="character-voice-template-active__switch" @tap.stop="clearCharacterVoiceTemplateSelection">改为手填 ID</text>
					</view>
					<view v-else class="character-voice-manual-box">
						<text class="character-voice-manual-label">高级音色 ID</text>
						<input
							class="character-voice-input"
							v-model="characterVoicePanel.ttsVoiceName"
							:disabled="!characterVoicePanel.enabled || characterVoicePanel.saving"
							:placeholder="characterVoiceVoicePlaceholder()"
							confirm-type="done"
						/>
						<scroll-view v-if="characterVoicePanelVoicePresets.length" class="character-voice-chip-scroll" scroll-y :show-scrollbar="false">
							<view class="character-voice-chip-row">
								<text
									v-for="voice in characterVoicePanelVoicePresets"
									:key="'voice_preset_' + voice"
									class="character-voice-chip"
									:class="{ 'character-voice-chip--active': normalizeCharacterVoiceText(characterVoicePanel.ttsVoiceName).toLowerCase() === String(voice).toLowerCase() }"
									@tap="selectCharacterVoicePreset(voice)"
								>{{ voice }}</text>
							</view>
						</scroll-view>
					</view>
					<text class="character-voice-hint">{{ characterVoiceVoiceHintText() }}</text>
				</view>
					</view>
				</scroll-view>
				<view class="character-voice-actions">
					<text class="character-voice-btn character-voice-btn--ghost" @tap="resetCharacterVoicePanelToDefault">
						{{ tx('character_voice_reset', '恢复默认') }}
					</text>
					<text class="character-voice-btn character-voice-btn--primary" @tap="saveCharacterVoicePanel">
						{{ tx('save', '保存') }}
					</text>
				</view>
			</view>
		</view>
		<view v-if="jgOn && jgChatLoadState === 'ready'" class="ai-disclaimer">
			<text class="ai-disclaimer-txt">内容由 AI 生成</text>
		</view>
		<view v-if="attachmentMenuVisible" class="attach-fab-backdrop" @tap="closeChatAttachmentMenu"></view>
		<view v-if="composerImages.length" class="composer-image-strip">
			<view
				v-for="item in composerImages"
				:key="item.id"
				class="composer-image-card"
			>
				<image
					class="composer-image"
					:src="item.previewUrl"
					mode="aspectFill"
					lazy-load
					@tap.stop="previewPendingChatImage(item)"
				></image>
				<view v-if="item.uploading || item.error" class="composer-image-mask">
					<text class="composer-image-mask-text">
						{{ item.error || (item.progress > 0 ? item.progress + '%' : tx('uploading', '上传中')) }}
					</text>
				</view>
				<text class="composer-image-remove" @tap.stop="removeComposerImage(item.id)">×</text>
			</view>
		</view>
		<view v-if="voiceFeatureEnabledGlobal !== false && (voiceRecording || voiceStopping || voiceTranscribing)" class="voice-status-card" :class="{ 'voice-status-card--recording': voiceRecording }">
			<view class="voice-status-main">
				<view class="voice-status-wave">
					<text v-for="n in 4" :key="'voice_status_bar_' + n" class="voice-status-bar"></text>
				</view>
				<view class="voice-status-copy">
					<text class="voice-status-title">
						{{ voiceStatusTitleText() }}
					</text>
					<text class="voice-status-sub">
						{{ voiceStatusSubText() }}
					</text>
				</view>
			</view>
			<text v-if="voiceRecording && !voiceStopping" class="voice-status-action" @tap="stopVoiceRecording">{{ tx('voice_send', '发送') }}</text>
		</view>
		<view v-if="expressionPanelVisible" class="expression-panel">
			<view class="expression-panel-head">
				<view class="expression-panel-title-wrap">
					<text class="expression-panel-title">{{ tx('expression_panel_title', '表情') }}</text>
					<text class="expression-panel-count">{{ expressionLibrary.length }}</text>
				</view>
				<view class="expression-panel-actions">
					<view
						class="expression-upload-chip"
						:class="isCharacterAiExpressionEnabled() ? 'expression-upload-chip--active' : 'expression-upload-chip--muted'"
						@tap="toggleCharacterAiExpressionEnabled()"
					>
						{{ isCharacterAiExpressionEnabled() ? tx('character_ai_expression_on', 'AI 表情开') : tx('character_ai_expression_off', 'AI 表情关') }}
					</view>
					<view
						class="expression-upload-chip"
						:class="{ 'expression-upload-chip--disabled': expressionUploadBusy }"
						@tap="pickLocalExpression('album')"
					>
						{{ expressionUploadBusy ? tx('expression_uploading', '处理中...') : tx('expression_add_album', '添加表情') }}
					</view>
					<text class="expression-panel-close" @tap="closeExpressionPanel">×</text>
				</view>
			</view>
			<view v-if="!expressionLibrary.length" class="expression-empty">
				<view
					class="expression-empty-card"
					:class="{ 'expression-empty-card--disabled': expressionUploadBusy }"
					@tap="pickLocalExpression('album')"
				>
					<view class="expression-empty-badge-wrap">
						<text class="expression-empty-badge">+</text>
						<text class="expression-empty-dot expression-empty-dot--one"></text>
						<text class="expression-empty-dot expression-empty-dot--two"></text>
					</view>
					<text class="expression-empty-label">
						{{ expressionUploadBusy ? tx('expression_uploading', '处理中...') : tx('expression_add_album', '添加表情') }}
					</text>
				</view>
			</view>
			<view v-else class="expression-body">
				<view v-if="recentExpressionLibrary.length" class="expression-recent-section">
					<view class="expression-section-head">
						<text class="expression-section-title">{{ tx('expression_recent_title', '最近使用') }}</text>
						<text class="expression-section-sub">{{ recentExpressionLibrary.length }}</text>
					</view>
					<scroll-view class="expression-recent-scroll" scroll-x show-scrollbar="false">
						<view class="expression-recent-row">
							<view
								v-for="item in recentExpressionLibrary"
								:key="'recent_' + item.id"
								class="expression-recent-card"
								@tap="sendLocalExpression(item)"
							>
								<image class="expression-recent-image" :src="item.imageUrl" mode="aspectFill" lazy-load></image>
								<text class="expression-recent-label">{{ item.label }}</text>
							</view>
						</view>
					</scroll-view>
				</view>
				<view class="expression-section-head expression-section-head--grid">
					<text class="expression-section-title">{{ tx('expression_all_title', '全部表情') }}</text>
					<text class="expression-section-sub">{{ expressionLibrary.length }}</text>
				</view>
				<view class="expression-grid">
					<view
						v-for="item in expressionLibrary"
						:key="item.id"
						class="expression-card"
						@tap="sendLocalExpression(item)"
					>
						<image class="expression-card-image" :src="item.imageUrl" mode="aspectFill" lazy-load></image>
						<view class="expression-card-actions">
							<text class="expression-card-action expression-card-action--rename" @tap.stop="renameLocalExpression(item)">改名</text>
							<text class="expression-card-action expression-card-action--remove" @tap.stop="removeLocalExpression(item)">×</text>
						</view>
						<text class="expression-card-label">{{ item.label }}</text>
					</view>
				</view>
			</view>
		</view>
		<view v-if="expressionEditor.visible" class="expression-editor-mask" @tap="closeExpressionEditor">
			<view class="expression-editor-panel" @tap.stop>
				<view class="expression-editor-top">
					<text class="expression-editor-title">
						{{ expressionEditor.id ? tx('expression_editor_rename_title', '重命名表情') : tx('expression_editor_title', '给表情取名') }}
					</text>
					<text class="expression-editor-close" @tap="closeExpressionEditor">×</text>
				</view>
				<image v-if="expressionEditor.imageUrl" class="expression-editor-preview" :src="expressionEditor.imageUrl" mode="aspectFit" lazy-load></image>
				<view class="expression-editor-meta">
					<text class="expression-editor-count">{{ expressionEditor.draft ? expressionEditor.draft.length : 0 }}/20</text>
				</view>
				<input
					class="expression-editor-input"
					v-model="expressionEditor.draft"
					:maxlength="20"
					:disabled="expressionEditor.saving"
					:placeholder="tx('expression_editor_placeholder', '输入名字')"
					confirm-type="done"
					@confirm="submitExpressionEditor"
				/>
				<view class="expression-editor-actions">
					<text class="expression-editor-btn expression-editor-btn--ghost" @tap="closeExpressionEditor">{{ tx('cancel', '取消') }}</text>
					<text class="expression-editor-btn expression-editor-btn--primary" @tap="submitExpressionEditor">{{ tx('save', '保存') }}</text>
				</view>
			</view>
		</view>
		<view v-if="characterImagePanel.visible" class="image-quick-mask" @tap="closeCharacterImagePanel">
			<view class="image-quick-shell" @tap.stop>
				<view class="image-quick-card">
					<view class="image-quick-card-inner">
						<view class="image-quick-head">
							<view class="image-quick-identity">
								<text class="image-quick-title">{{ tx('character_image_title', '图片生成') }}</text>
							</view>
							<text class="image-quick-close" @tap="closeCharacterImagePanel">×</text>
						</view>
						<textarea
							class="image-quick-input"
							v-model="characterImagePanel.prompt"
							:disabled="characterImagePanel.generating"
							:maxlength="300"
							auto-height
							:show-confirm-bar="false"
							:placeholder="tx('character_image_prompt_placeholder', '描述你想生成的图片')"
						/>
						<view class="image-quick-actions">
							<text class="image-quick-link" @tap="goAiSettings">{{ tx('go_ai_settings', 'AI 设置') }}</text>
							<text
								class="image-quick-btn"
								:class="{ 'image-quick-btn--disabled': characterImagePanel.generating }"
								@tap="generateCharacterImage"
							>
								{{ characterImagePanel.generating ? tx('character_image_generating', '生图中...') : tx('character_image_generate', '开始生图') }}
							</text>
						</view>
					</view>
				</view>
			</view>
		</view>
		<view class="input-bar">
			<view v-if="attachmentMenuVisible" class="attach-fab-menu" @tap.stop>
				<view
					v-if="voiceFeatureEnabledGlobal !== false"
					class="attach-fab-item"
					:class="{ 'attach-fab-item--active': voiceRecording || voiceTranscribing || pendingVoiceStartTimer }"
					@tap.stop="toggleVoiceInput"
				>
					<view class="attach-fab-badge">
						<image class="attach-fab-icon" :src="attachmentVoiceIcon" mode="aspectFit"></image>
					</view>
				</view>
				<view
					v-if="characterImageGlobalState.imageEnabledGlobal !== false"
					class="attach-fab-item"
					:class="{ 'attach-fab-item--active': characterImagePanel.visible }"
					@tap.stop="openCharacterImagePanel"
				>
					<view class="attach-fab-badge">
						<image class="attach-fab-icon" :src="attachmentImageIcon" mode="aspectFit"></image>
					</view>
				</view>
				<view class="attach-fab-item" @tap.stop="pickChatImages('camera')">
					<view class="attach-fab-badge">
						<image class="attach-fab-icon" :src="attachmentCameraIcon" mode="aspectFit"></image>
					</view>
				</view>
				<view class="attach-fab-item" @tap.stop="pickChatImages('album')">
					<view class="attach-fab-badge">
						<image class="attach-fab-icon" :src="attachmentAlbumIcon" mode="aspectFit"></image>
					</view>
				</view>
			</view>
			<view v-if="atChatBottom" class="input-pill" :class="{ 'input-pill--with-quote': composerQuote.visible }">
				<view v-if="draftRestoredNoticeVisible" class="draft-restore-bar">
					<text class="draft-restore-text">{{ tx('draft_restored', '已恢复上次未发送内容') }}</text>
					<text class="draft-restore-action" @tap.stop="clearRestoredDraft">{{ tx('clear', '清空') }}</text>
					<text class="draft-restore-close" @tap.stop="dismissDraftRestoredNotice">×</text>
				</view>
				<view v-if="composerQuote.visible" class="composer-quote-bar">
					<view class="composer-quote-copy">
						<text class="composer-quote-speaker">{{ composerQuote.speaker }}</text>
						<text class="composer-quote-text">{{ composerQuote.text }}</text>
					</view>
					<text class="composer-quote-close" @tap="clearComposerQuote">×</text>
				</view>
				<view class="input-main">
					<textarea
						class="inp"
						placeholder-class="inp-ph"
						v-model="draft"
						:placeholder="tx('input_message', '输入消息...')"
						confirm-type="send"
						auto-height
						:maxlength="-1"
						:show-confirm-bar="false"
						:cursor-spacing="isAppPlus ? 96 : 18"
						:adjust-position="true"
						:disabled="sending"
						@focus="onInputFocus"
						@blur="onInputBlur"
						@confirm="send"
					></textarea>
					<view class="input-actions">
						<view class="expression-trigger" @tap="openExpressionPanel">
							<image class="input-action-icon" :src="inputExpressionIcon" mode="aspectFit"></image>
						</view>
						<view class="attach-btn" :class="{ 'attach-btn--active': attachmentMenuVisible }" @tap.stop="openChatAttachmentMenu">
							<image class="input-action-icon" :src="inputPlusIcon" mode="aspectFit"></image>
						</view>
					</view>
				</view>
			</view>
			<view v-else class="scroll-bottom-pill" @tap="scrollChatToBottom({ immediate: true })">
				<text class="scroll-bottom-pill-text">{{ tx('chat_bottom', '回到底部') }}</text>
			</view>
			<view
				class="send send--icon"
				:class="{
					senddisabled: sending
				}"
				v-if="atChatBottom"
				@tap="onPrimaryAction"
			>
				<image class="send-icon" :src="sendUpIcon" mode="aspectFit"></image>
			</view>
		</view>
		</template>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="inputFocus ? 150 : 92" :compact="inputFocus" />
		<!-- #endif -->
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
	const { getTavernUiText, formatLocaleText } = require('@/common/tavernUiI18n.js');
	const companionStore = require('@/common/companionStore.js');
	const DEFAULT_CHAT_BACKGROUND_URL = '/static/login.png';

	function buildInlineSvgDataUrl(svg) {
		return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
	}

	const ATTACH_CAMERA_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<path d="M16 20h8l4-6h8l4 6h8a6 6 0 0 1 6 6v20a6 6 0 0 1-6 6H16a6 6 0 0 1-6-6V26a6 6 0 0 1 6-6Z" stroke="#23213A" stroke-width="3.6" stroke-linejoin="round"/>
			<circle cx="32" cy="36" r="10" stroke="#23213A" stroke-width="3.6"/>
			<path d="M13 28h5" stroke="#23213A" stroke-width="3.6" stroke-linecap="round"/>
		</svg>
	`);
	const ATTACH_ALBUM_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<rect x="9" y="13" width="46" height="38" rx="8" stroke="#23213A" stroke-width="3.6"/>
			<path d="m17 43 11-12 8 8 7-6 8 10" stroke="#23213A" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round"/>
			<circle cx="45" cy="24" r="4" fill="#F7A32D"/>
		</svg>
	`);
	const ATTACH_VOICE_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<path d="M32 11c5.2 0 9.5 4.3 9.5 9.5v12.4c0 5.3-4.3 9.5-9.5 9.5s-9.5-4.2-9.5-9.5V20.5C22.5 15.3 26.8 11 32 11Z" stroke="#23213A" stroke-width="3.6"/>
			<path d="M18 30.5c0 8 6.2 14.5 14 14.5s14-6.5 14-14.5" stroke="#23213A" stroke-width="3.6" stroke-linecap="round"/>
			<path d="M32 45v8" stroke="#23213A" stroke-width="3.6" stroke-linecap="round"/>
			<path d="M25 53h14" stroke="#23213A" stroke-width="3.6" stroke-linecap="round"/>
		</svg>
	`);
	const ATTACH_IMAGE_GENERATE_ICON = '/static/chat/image-generate.png';
	const EXPRESSION_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<circle cx="32" cy="32" r="21" stroke="#1F2937" stroke-width="3.6"/>
			<circle cx="24" cy="28" r="2.8" fill="#1F2937"/>
			<circle cx="40" cy="28" r="2.8" fill="#1F2937"/>
			<path d="M22.5 39c2.2 2.8 5.4 4.5 9.5 4.5s7.3-1.7 9.5-4.5" stroke="#1F2937" stroke-width="3.6" stroke-linecap="round"/>
		</svg>
	`);
	const INPUT_PLUS_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<path d="M32 18v28M18 32h28" stroke="#1F2937" stroke-width="4.2" stroke-linecap="round"/>
		</svg>
	`);
	const SEND_UP_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<path d="M32 45V20" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round"/>
			<path d="m22 30 10-10 10 10" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round"/>
		</svg>
	`);
	const SEND_DOWN_ICON = buildInlineSvgDataUrl(`
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
			<path d="M32 19v25" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round"/>
			<path d="m22 34 10 10 10-10" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round"/>
		</svg>
	`);
	const CHARACTER_VOICE_ICON = '/static/chat/voice-config.svg';
	const ASSISTANT_VOICE_ON_ICON = '/static/chat/voice-play.png';
	const ASSISTANT_VOICE_OFF_ICON = '/static/chat/voice-mute.png';
	const ASSISTANT_VOICE_SILENT_WAV_DATA_URL = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=';
	const LOCAL_CHAT_IMAGE_CACHE_PREFIX = 'tavern_local_chat_images_';
	const LOCAL_CHAT_IMAGE_CACHE_VERSION = 1;
	const LOCAL_CHAT_IMAGE_CACHE_LIMIT = 80;
	const LOCAL_CHAT_IMAGE_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
	const LOCAL_CHAT_IMAGE_PENDING_KEEP_MS = 10 * 60 * 1000;
	const LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH = 1200 * 1024;
	const LOCAL_CHAT_IMAGE_CACHE_MAX_TOTAL_LENGTH = 6 * 1024 * 1024;
	const LOCAL_USER_VOICE_CACHE_PREFIX = 'tavern_local_user_voice_';
	const LOCAL_USER_VOICE_CACHE_VERSION = 1;
	const LOCAL_USER_VOICE_CACHE_LIMIT = 24;
	const LOCAL_USER_VOICE_CACHE_TTL_MS = 14 * 24 * 60 * 60 * 1000;
	const LOCAL_USER_VOICE_CACHE_MAX_TOTAL_LENGTH = 4 * 1024 * 1024;
	const LOCAL_USER_VOICE_DATA_URL_MAX_LENGTH = 3 * 1024 * 1024;
	const LOCAL_EXPRESSION_LIBRARY_PREFIX = 'tavern_local_expressions_';
	const LOCAL_EXPRESSION_LIBRARY_VERSION = 1;
	const LOCAL_EXPRESSION_LIBRARY_LIMIT = 40;
	const LOCAL_EXPRESSION_LABEL_MAX = 20;
	const LOCAL_EXPRESSION_HINT_LIMIT = 12;
	const LOCAL_EXPRESSION_PICK_MAX_BYTES = 2 * 1024 * 1024;
	const LOCAL_EXPRESSION_DATA_URL_MAX_LENGTH = 900 * 1024;
	const LOCAL_ASSISTANT_VOICE_PREF_PREFIX = 'tavern_assistant_voice_pref_';
	const LOCAL_CHARACTER_VOICE_CONFIG_PREFIX = 'tavern_character_voice_cfg_';
	const LOCAL_CHARACTER_VOICE_CONFIG_VERSION = 1;
	const LOCAL_CHARACTER_IMAGE_CONFIG_PREFIX = 'tavern_character_image_cfg_';
	const LOCAL_CHARACTER_IMAGE_CONFIG_VERSION = 1;
	const LOCAL_CHARACTER_IMAGE_REFERENCE_PREFIX = 'tavern_character_image_ref_';
	const LOCAL_CHAT_DRAFT_PREFIX = 'tavern_chat_draft_';
	const LOCAL_CHAT_DRAFT_VERSION = 1;
	const LOCAL_CHAT_DRAFT_SAVE_DELAY_MS = 360;
	const LOCAL_CHAT_DRAFT_MAX_LENGTH = 5000;
	const MESSAGE_ACTION_LONG_PRESS_MS = 560;
	const MESSAGE_ACTION_MOVE_THRESHOLD_PX = 10;
	const MIN_VOICE_RECORD_DURATION_MS = 800;
	const VOICE_STOP_CALLBACK_TIMEOUT_MS = 10000;
	const ASSISTANT_VOICE_SEGMENT_MAX = 6;
	const ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH = 42;
	const ASSISTANT_VOICE_SEGMENT_SOFT_MIN = 18;
	const ASSISTANT_VOICE_SEGMENT_SHORT_LENGTH = 8;
	const ASSISTANT_EXPRESSION_MARKER_REGEX = /\[\[\s*expr\s*:\s*([^[\]]+?)\s*\]\]/gi;
	const LOCAL_EXPRESSION_RECENT_AVOID_LIMIT = 4;
	const OPENAI_TTS_VOICE_PRESETS = Object.freeze(['alloy', 'nova', 'shimmer', 'echo', 'fable', 'onyx']);
	const SILICONFLOW_TTS_VOICE_PRESETS = Object.freeze(['alex', 'benjamin', 'charles', 'david', 'anna', 'bella', 'claire', 'diana']);
	const CHARACTER_IMAGE_ASPECT_OPTIONS = Object.freeze([
		{ value: 'portrait', label: '3:4' },
		{ value: 'square', label: '1:1' },
		{ value: 'landscape', label: '4:3' },
		{ value: 'wide', label: '16:9' }
	]);
	const CHARACTER_VOICE_PROVIDER_LABELS = Object.freeze({
		siliconflow: 'SiliconFlow',
		deepseek: 'DeepSeek',
		openrouter: 'OpenRouter',
		openai: 'OpenAI',
		groq: 'Groq',
		mistralai: 'Mistral',
		moonshot: 'Moonshot',
		xai: 'xAI',
		fireworks: 'Fireworks',
		custom: '自定义'
	});
	const WEAK_ASSISTANT_EXPRESSION_KEYWORDS = {
		'嗯': true,
		'啊': true,
		'哦': true,
		'哈': true,
		'呀': true,
		'哇': true,
		'欸': true,
		'诶': true,
		'呜': true,
		'哼': true,
		'好': true,
		'行': true,
		'是': true,
		'不': true,
		'额': true,
		'嗷': true,
		'呀哈': true,
		'嘿': true,
		'嘿嘿': true,
		'哈哈': true,
		'呵呵': true,
		'嘻嘻': true,
		'呃': true,
		'唉': true,
		'哎': true,
		'啦': true,
		'喔': true,
		'好呀': true,
		'好的': true,
		'可以': true,
		'收到': true,
		'在吗': true,
		'晚安': true,
		'早安': true,
		'谢谢': true,
		'爱你': true,
		'抱抱': true,
		'亲亲': true,
		'ok': true,
		'okay': true
	};

	function createDefaultCharacterVoiceConfig() {
		return {
			enabled: false,
			autoPlayEnabled: false,
			allowAiExpression: false,
			ttsModelName: '',
			ttsVoiceName: '',
			ttsVoiceTemplateCode: '',
			updatedAt: 0
		};
	}

	function normalizeCharacterVoiceTemplateItem(source) {
		const raw = source && typeof source === 'object' ? source : {};
		return {
			code: String(raw.code || '').trim(),
			displayName: String(raw.displayName || '').trim(),
			description: String(raw.description || '').trim(),
			providerSource: String(raw.providerSource || '').trim(),
			recommendedModelName: String(raw.recommendedModelName || '').trim(),
			coverImageUrl: String(raw.coverImageUrl || '').trim(),
			referenceAudioUrl: String(raw.referenceAudioUrl || '').trim(),
			sampleScript: String(raw.sampleScript || '').trim(),
			ready: raw.ready === true,
			selected: raw.selected === true,
			statusCode: String(raw.statusCode || '').trim(),
			statusText: String(raw.statusText || '').trim(),
			lastError: String(raw.lastError || '').trim()
		};
	}

	function createCharacterVoicePanelState() {
		return Object.assign(
			{
				visible: false,
				saving: false
			},
			createDefaultCharacterVoiceConfig()
		);
	}

	function createCharacterVoiceGlobalState() {
		return {
			loading: false,
			loaded: false,
			error: '',
			loadedAt: 0,
			enabledGlobal: false,
			canUse: false,
			denyReason: '',
			mode: 'system',
			providerSource: '',
			modelName: '',
			sttModelName: '',
			ttsModelName: '',
			ttsVoiceName: '',
			ttsVoiceTemplateCode: '',
			ttsVoiceTemplateLabel: '',
			apiKeyConfigured: false,
			apiKeyMask: '',
			customUrl: '',
			ttsUseSeparateConfig: false,
			providerOptions: [],
			ttsVoiceTemplates: []
		};
	}

	function createDefaultCharacterImageConfig() {
		return {
			enabled: true,
			styleHint: '',
			aspectRatio: 'portrait',
			updatedAt: 0
		};
	}

	function createCharacterImagePanelState() {
		return {
			visible: false,
			generating: false,
			prompt: ''
		};
	}

	function createCharacterImageGlobalState() {
		return {
			loading: false,
			loaded: false,
			error: '',
			loadedAt: 0,
			enabledGlobal: false,
			canUse: false,
			denyReason: '',
			mode: 'system',
			providerSource: '',
			imageModelName: '',
			apiKeyConfigured: false,
			apiKeyMask: '',
			customUrl: '',
			imageUseSeparateConfig: false,
			imageEnabledGlobal: true,
			imageCanUse: false,
			imageDenyReason: '',
			imageCharacterConsistencyMode: 'free',
			imageReferenceSourceMode: 'latest_generated_first',
			providerOptions: []
		};
	}

	const MESSAGE_QUOTE_OPEN_PREFIX = '[引用:';
	const MESSAGE_QUOTE_CLOSE_MARKER = '[/引用]';
	const MESSAGE_QUOTE_EXCERPT_MAX = 160;
	const TAVERN_MESSAGES_INITIAL_LIMIT = 400;
	const TAVERN_MESSAGES_HISTORY_LIMIT = 200;

	function createComposerQuoteState() {
		return {
			visible: false,
			messageId: '',
			role: '',
			speaker: '',
			text: ''
		};
	}

	function createEditOverlayState() {
		return {
			visible: false,
			messageId: '',
			draft: '',
			saving: false,
			imageUrls: [],
			quoteMeta: createComposerQuoteState(),
			voiceUrl: '',
			voiceDurationMs: null
		};
	}

	function createMessageActionSheetState() {
		return {
			visible: false,
			messageId: '',
			role: '',
			text: '',
			canDelete: false,
			deleting: false,
			leftPx: 12,
			topPx: 12,
			imageUrls: [],
			voiceUrl: '',
			voiceDurationMs: null
		};
	}

	function createMessagePressState() {
		return {
			timer: null,
			fired: false,
			messageId: '',
			startX: 0,
			startY: 0
		};
	}

	export default {
		components: { TavernNavBar },
		data() {
			return {
				cid: '',
				char: null,
				messages: [],
				draft: '',
				composerQuote: createComposerQuoteState(),
				scrollTo: '',
				inputFocus: false,
				sending: false,
				jgOn: false,
				userAvatar: '',
				streamAbortController: null,
				streamingAssistantMessageId: '',
				streamingAssistantMode: '',
				stopRefreshTimer: null,
				followBottom: true,
				atChatBottom: true,
				chatUserTouching: false,
				lastChatScrollTop: 0,
				chatAutoScrollAt: 0,
				chatScrollWithAnimation: true,
				chatViewportReady: false,
				chatAnimationTimer: null,
				chatRevealTimer: null,
				editOverlay: createEditOverlayState(),
				messageActionSheet: createMessageActionSheetState(),
				messagePressState: createMessagePressState(),
				commercialPrompt: {
					visible: false,
					kind: '',
					title: '',
					message: '',
					primaryText: '',
					primaryUrl: '',
					secondaryText: '',
					secondaryUrl: ''
				},
				charImagePreviewVisible: false,
				jgChatLoadState: 'idle',
				jgChatErrorMsg: '',
				jgConversationId: '',
				jgMemory: null,
				jgTavernMeta: null,
				memoryRefreshing: false,
				messageHistoryHasMore: false,
				messageHistoryLoading: false,
				messageHistoryNextBeforeId: '',
				messageHistoryLoadAt: 0,
				jgLoadRetryTimer: null,
				jgLoadAutoRetried: false,
				jgLoadRequestToken: 0,
				replySuggest: {
					visible: false,
					loading: false,
					error: '',
					items: [],
					contextKey: ''
				},
				draftSaveTimer: null,
				draftRestoredNoticeVisible: false,
				draftHydrated: false,
				generationRecovery: {
					visible: false,
					messageId: '',
					mode: 'retry',
					title: '',
					message: '',
					canContinue: false,
					canRegen: false,
					retryText: ''
				},
				expressionLibrary: [],
				expressionEditor: {
					visible: false,
					id: '',
					draft: '',
					imageUrl: '',
					saving: false
				},
				expressionUploadBusy: false,
				composerImages: [],
				attachmentMenuVisible: false,
				expressionPanelVisible: false,
				voiceRecorderManager: null,
				voiceRecorderReady: false,
				voiceBrowserRecorder: null,
				voiceBrowserStream: null,
				voiceBrowserMimeType: '',
				voiceRecording: false,
				voiceStopping: false,
				voiceTranscribing: false,
				voiceRecordStartedAt: 0,
				voiceRecordDurationMs: 0,
				voiceRecordTimer: null,
				voiceDiscardNextStop: false,
				voiceStopPendingTimer: null,
				voiceStopPendingAt: 0,
				pendingVoiceStartTimer: null,
				pendingVoiceStartAt: 0,
				silentGenerationInterruptUntil: 0,
				userVoicePlayer: null,
				userVoicePlayerReady: false,
				userVoicePlayingMessageId: '',
				userVoiceStateMap: {},
				voiceFeatureEnabledGlobal: true,
				voiceInputAiState: {
					loadedAt: 0,
					mode: 'system',
					canUse: true,
					denyReason: '',
					apiKeyConfigured: false,
					sttModelName: ''
				},
				assistantVoicePlayer: null,
				assistantVoicePlayerReady: false,
				assistantVoicePlayingMessageId: '',
				assistantVoiceStateMap: {},
				assistantVoiceAutoEnabled: false,
				assistantVoiceBrowserUnlocked: false,
				assistantVoiceBrowserUnlocking: false,
				assistantVoiceAutoplayHintShown: false,
				characterVoiceConfig: createDefaultCharacterVoiceConfig(),
				characterVoicePanel: createCharacterVoicePanelState(),
				characterVoiceGlobalState: createCharacterVoiceGlobalState(),
				characterImageConfig: createDefaultCharacterImageConfig(),
				characterImagePanel: createCharacterImagePanelState(),
				characterImageGlobalState: createCharacterImageGlobalState(),
				characterImageReferencePreparedSource: '',
				characterImageReferencePreparedUrl: '',
				characterImageReferencePreparedAt: 0,
				characterImageReferencePreparePromise: null,
				attachmentCameraIcon: ATTACH_CAMERA_ICON,
				attachmentAlbumIcon: ATTACH_ALBUM_ICON,
				attachmentVoiceIcon: ATTACH_VOICE_ICON,
				attachmentImageIcon: ATTACH_IMAGE_GENERATE_ICON,
				assistantVoiceOnIcon: ASSISTANT_VOICE_ON_ICON,
				assistantVoiceOffIcon: ASSISTANT_VOICE_OFF_ICON,
				characterVoiceIcon: CHARACTER_VOICE_ICON,
				inputExpressionIcon: EXPRESSION_ICON,
				inputPlusIcon: INPUT_PLUS_ICON,
				sendUpIcon: SEND_UP_ICON,
				sendDownIcon: SEND_DOWN_ICON,
				isAppPlus: false
			};
		},
		computed: {
			chatUi() {
				return getTavernUiText('chat');
			},
			memoryBarText() {
				if (!this.jgOn || this.jgChatLoadState !== 'ready') {
					return '';
				}
				if (this.memoryRefreshing) {
					return this.tx('memory_refreshing', '记忆整理中');
				}
				const meta = this.jgTavernMeta || {};
				const every = meta.memoryAutoEveryMessages;
				const minM = meta.memoryAutoMinMinutesBetween;
				const divider = this.tx('memory_divider', ' · ');
				var rule = '';
				if (every != null && minM != null) {
					rule = this
						.tx('memory_rule', '约每 {every} 条、间隔至少 {min} 分钟自动整理')
						.replace('{every}', String(every))
						.replace('{min}', String(minM));
				}
				const mem = this.jgMemory;
				const enabledCountRaw = mem && mem.enabledEntryCount != null ? Number(mem.enabledEntryCount) : 0;
				const enabledCount = isNaN(enabledCountRaw) ? 0 : enabledCountRaw;
				const syncStatus = String((mem && mem.syncStatus) || '').trim().toUpperCase();
				if (syncStatus === 'FAILED') {
					return this.tx('memory_sync_failed_retry', '记忆同步失败 · 点击重试');
				}
				if (syncStatus === 'PENDING') {
					return this.tx('memory_refreshing', '记忆整理中');
				}
				if (!mem || enabledCount <= 0) {
					var base = this.tx('memory_empty_longterm', '尚未生成长期记忆');
					return rule ? base + divider + rule : base;
				}
				var prev = (mem.summaryPreview || '').trim();
				if (prev.length > 40) {
					prev = prev.slice(0, 40) + '...';
				}
				var line = this
					.tx('memory_sync_success', '记忆已整理 · {n} 条要点 · 同步成功')
					.replace('{n}', String(enabledCount));
				if (prev) {
					line += divider + prev;
				}
				return line;
			},
			showStopStream() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					return this.sending && tavernApi.jgStreamEnabled();
				} catch (e) {
					return false;
				}
			},
			t() {
				return (this.allText && this.allText['酒馆页']) || {};
			},
			title() {
				return this.char ? this.char.nickname : 'Chat';
			},
			charAvatar() {
				if (!this.char) return '/static/logo.png';
				const tavernApi = require('@/common/tavernApi.js');
				const u = this.char.avatar_thumb || this.char.cover_thumb || this.char.avatar || this.char.cover;
				if (!u || String(u).trim() === '') return '/static/logo.png';
				return tavernApi.resolveJgAssetUrl(u) || '/static/logo.png';
			},
			charPreviewImage() {
				if (!this.char) return this.charAvatar;
				const tavernApi = require('@/common/tavernApi.js');
				const u = this.char.cover_detail || this.char.cover || this.char.avatar || this.char.cover_thumb || this.char.avatar_thumb;
				if (!u || String(u).trim() === '') return this.charAvatar;
				return tavernApi.resolveJgAssetUrl(u) || this.charAvatar;
			},
			resolvedUserAvatar() {
				const u = this.userAvatar;
				if (u != null && String(u).trim() !== '') return u;
				return '/static/logo.png';
			},
			chatBackgroundUrl() {
				if (!this.char) return DEFAULT_CHAT_BACKGROUND_URL;
				const tavernApi = require('@/common/tavernApi.js');
				const raw = this.char.chat_background_url || this.char.chatBackgroundUrl || '';
				return tavernApi.resolveJgAssetUrl(raw) || DEFAULT_CHAT_BACKGROUND_URL;
			},
			recentExpressionLibrary() {
				return (Array.isArray(this.expressionLibrary) ? this.expressionLibrary : [])
					.filter((item) => item && item.imageUrl && (Number(item.lastUsedAt) > 0 || Number(item.useCount) > 0))
					.slice()
					.sort((a, b) => {
						const lastUsedDiff = Number(b && b.lastUsedAt ? b.lastUsedAt : 0) - Number(a && a.lastUsedAt ? a.lastUsedAt : 0);
						if (lastUsedDiff) return lastUsedDiff;
						return Number(b && b.useCount ? b.useCount : 0) - Number(a && a.useCount ? a.useCount : 0);
					})
					.slice(0, 8);
			},
			hasChatBackground() {
				return !!this.chatBackgroundUrl;
			},
			hasCustomChatBackground() {
				return !!this.chatBackgroundUrl && this.chatBackgroundUrl !== DEFAULT_CHAT_BACKGROUND_URL;
			},
			wrapStyle() {
				const url = this.chatBackgroundUrl;
				if (!url) return {};
				return {
					'--chat-bg-image': "url('" + String(url).replace(/'/g, '%27') + "')"
				};
			},
			defaultChatBackgroundUrl() {
				return DEFAULT_CHAT_BACKGROUND_URL;
			},
			characterVoicePanelVoicePresets() {
				const modelName = this.normalizeCharacterVoiceText(
					this.characterVoicePanel && this.characterVoicePanel.ttsModelName
				);
				if (this.supportsCharacterVoiceOpenAiPresets(modelName)) {
					return OPENAI_TTS_VOICE_PRESETS.slice();
				}
				if (this.supportsCharacterVoiceSiliconFlowPresets(modelName)) {
					return SILICONFLOW_TTS_VOICE_PRESETS.slice();
				}
				return [];
			},
			characterVoicePanelVoiceTemplates() {
				const state = this.characterVoiceGlobalState || {};
				const source = Array.isArray(state.ttsVoiceTemplates) ? state.ttsVoiceTemplates : [];
				return source.map((item) => normalizeCharacterVoiceTemplateItem(item)).filter((item) => item.code);
			},
			selectedCharacterVoicePanelVoiceTemplate() {
				const currentCode = this.normalizeCharacterVoiceText(
					this.characterVoicePanel && this.characterVoicePanel.ttsVoiceTemplateCode,
					64
				);
				if (!currentCode) {
					return null;
				}
				return this.characterVoicePanelVoiceTemplates.find((item) => item.code === currentCode) || null;
			}
		},
		watch: {
			draft(value) {
				if (!this.draftHydrated) {
					return;
				}
				this.scheduleDraftSave(value);
				if (String(value || '').trim()) {
					this.closeReplySuggestions();
				}
			}
		},
			onLoad(q) {
			// #ifdef APP-PLUS
			this.isAppPlus = true;
			// #endif
			this.cid = (q && q.id) || '';
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				this.jgOn = true;
				this.char = null;
				this.jgChatLoadState = 'error';
				this.jgChatErrorMsg = this.tx('backend_disabled', '后端接口未开启');
				return;
			}
			this.jgOn = true;
			this.char = null;
			this.jgChatLoadState = 'loading';
			this.jgChatErrorMsg = '';
			this.applyVoiceFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
			this.refreshUserAvatar();
			this.refreshLocalExpressionLibrary();
			this.refreshAssistantVoiceAutoPreference();
			this.refreshCharacterVoiceConfig();
			this.refreshCharacterImageConfig();
			this.refreshCharacterImageGlobalSummary(false, false);
			this.refreshVoiceFeatureGlobalState(false);
			this.initAssistantVoiceBrowserUnlockTracking();
			this.loadJgSession();
		},
		onShow() {
			companionStore.emitLayout({ avoidBottom: this.inputFocus ? 150 : 92, compact: this.inputFocus === true });
			if (this.jgOn) {
				const tavernApi = require('@/common/tavernApi.js');
				this.applyVoiceFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
				this.refreshUserAvatar();
				this.refreshLocalExpressionLibrary();
				this.refreshAssistantVoiceAutoPreference();
				this.refreshCharacterVoiceConfig();
				this.refreshCharacterImageConfig();
				this.refreshCharacterImageGlobalSummary(false, false);
				this.refreshVoiceFeatureGlobalState(false);
				this.initAssistantVoiceBrowserUnlockTracking();
				this.maybeRecoverJgSessionOnShow();
			}
		},
		onHide() {
			companionStore.emitLayout({ avoidBottom: 92, compact: false });
			this.flushDraftSave();
		},
		onUnload() {
			companionStore.emitLayout({ avoidBottom: 92, compact: false });
			this.flushDraftSave();
			this.clearJgLoadRetryTimer();
			this.clearDraftSaveTimer();
			this.clearStopSyncTimer();
			this.clearChatUiTimers();
			this.clearMessageActionPressState();
			this.disposeAssistantVoiceBrowserUnlockTracking();
			this.disposeVoiceRecorder(true);
			this.disposeUserVoicePlayer();
			this.disposeAssistantVoicePlayer();
		},
		methods: {
			tx(key, fallback) {
				const extra = this.chatUi || {};
				const extraValue = key ? extra[key] : '';
				if (extraValue != null && String(extraValue).trim() !== '') return extraValue;
				const dict = this.t || {};
				const v = key ? dict[key] : '';
				if (v != null && String(v).trim() !== '') return v;
				return fallback || '';
			},
			jgErrMsg(e, fallback) {
				const tavernErrors = require('@/common/tavernErrors.js');
				return tavernErrors.getTavernErrorMessage(e, fallback);
			},
			localChatViewerKey() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					return String(tavernApi.getClientUid() || '').trim();
				} catch (e) {
					return '';
				}
			},
			chatDraftStorageKey() {
				const viewer = this.localChatViewerKey();
				const cid = this.cid == null ? '' : String(this.cid).trim();
				return viewer && cid ? LOCAL_CHAT_DRAFT_PREFIX + viewer + '_' + cid : '';
			},
			clearDraftSaveTimer() {
				if (this.draftSaveTimer) {
					clearTimeout(this.draftSaveTimer);
					this.draftSaveTimer = null;
				}
			},
			normalizeDraftText(value) {
				const text = value == null ? '' : String(value);
				return text.length > LOCAL_CHAT_DRAFT_MAX_LENGTH ? text.slice(0, LOCAL_CHAT_DRAFT_MAX_LENGTH) : text;
			},
			readStoredDraft() {
				const key = this.chatDraftStorageKey();
				if (!key) return '';
				try {
					const raw = uni.getStorageSync(key);
					if (raw && typeof raw === 'object') {
						return this.normalizeDraftText(raw.text || '');
					}
					return this.normalizeDraftText(raw || '');
				} catch (e) {
					return '';
				}
			},
			writeStoredDraft(value) {
				const key = this.chatDraftStorageKey();
				if (!key) return;
				const text = this.normalizeDraftText(value);
				try {
					if (text.trim()) {
						uni.setStorageSync(key, {
							version: LOCAL_CHAT_DRAFT_VERSION,
							text,
							updatedAt: Date.now()
						});
					} else {
						uni.removeStorageSync(key);
					}
				} catch (e) {}
			},
			scheduleDraftSave(value) {
				this.clearDraftSaveTimer();
				this.draftSaveTimer = setTimeout(() => {
					this.draftSaveTimer = null;
					this.writeStoredDraft(value);
				}, LOCAL_CHAT_DRAFT_SAVE_DELAY_MS);
			},
			flushDraftSave() {
				this.clearDraftSaveTimer();
				if (this.draftHydrated) {
					this.writeStoredDraft(this.draft);
				}
			},
			hydrateStoredDraft() {
				if (this.draftHydrated) return;
				const restored = this.readStoredDraft();
				this.draftHydrated = true;
				if (!String(this.draft || '').trim() && restored.trim()) {
					this.draft = restored;
					this.draftRestoredNoticeVisible = true;
				}
			},
			clearStoredDraft() {
				this.clearDraftSaveTimer();
				this.writeStoredDraft('');
			},
			clearRestoredDraft() {
				this.draft = '';
				this.draftRestoredNoticeVisible = false;
				this.clearStoredDraft();
			},
			dismissDraftRestoredNotice() {
				this.draftRestoredNoticeVisible = false;
			},
			resolveCommercialPrompt(e) {
				const tavernErrors = require('@/common/tavernErrors.js');
				return tavernErrors.resolveCommercialPrompt(e);
			},
			showErrorToast(message) {
				const text = String(message || '').trim();
				if (!text) return;
				uni.showToast({
					title: text.length > 120 ? text.slice(0, 120) + '...' : text,
					icon: 'none',
					duration: 3500
				});
			},
			formatVoiceRecordDuration(ms) {
				const totalSeconds = Math.max(0, Math.floor(Number(ms || 0) / 1000));
				const minutes = Math.floor(totalSeconds / 60);
				const seconds = totalSeconds % 60;
				const minuteText = minutes < 10 ? '0' + minutes : String(minutes);
				const secondText = seconds < 10 ? '0' + seconds : String(seconds);
				return minuteText + ':' + secondText;
			},
			voiceStatusTitleText() {
				if (this.voiceRecording) {
					return this.tx('voice_recording_title', '语音消息');
				}
				if (this.voiceStopping) {
					return this.tx('voice_stopping_title', '结束录音中');
				}
				return this.tx('voice_sending_title', '发送语音');
			},
			voiceStatusSubText() {
				if (this.voiceRecording) {
					return this.formatVoiceRecordDuration(this.voiceRecordDurationMs);
				}
				if (this.voiceStopping) {
					return this.tx('voice_stopping_sub', '正在准备发送，请稍等一下');
				}
				return this.tx('voice_sending_sub', '马上送达');
			},
			normalizeVoiceInputAiState(raw) {
				const source = raw && typeof raw === 'object' ? raw : {};
				return {
					loadedAt: Date.now(),
					mode: String(source.mode || '').trim() === 'custom' ? 'custom' : 'system',
					canUse: source.canUse !== false,
					denyReason: this.normalizeCharacterVoiceText(source.denyReason, 200),
					apiKeyConfigured: !!source.apiKeyConfigured,
					sttModelName: this.normalizeCharacterVoiceText(source.sttModelName, 255)
				};
			},
			normalizeVoiceDurationMs(ms) {
				const value = Number(ms || 0);
				return isFinite(value) && value > 0 ? Math.round(value) : 0;
			},
			normalizeVoiceMessageUrl(url) {
				return url == null ? '' : String(url).trim();
			},
			resolveVoiceMessageAudioUrl(url) {
				const safeUrl = this.normalizeVoiceMessageUrl(url);
				if (!safeUrl) return '';
				try {
					const tavernApi = require('@/common/tavernApi.js');
					if (tavernApi && typeof tavernApi.resolveJgAssetUrl === 'function') {
						return tavernApi.resolveJgAssetUrl(safeUrl) || safeUrl;
					}
				} catch (e) {}
				return safeUrl;
			},
			buildVoiceMessageLocalUrl(uploadSource) {
				if (typeof uploadSource === 'string') {
					return String(uploadSource).trim();
				}
				if (
					this.isBrowserVoiceUploadSource(uploadSource) &&
					typeof URL !== 'undefined' &&
					typeof URL.createObjectURL === 'function'
				) {
					try {
						return URL.createObjectURL(uploadSource);
					} catch (e) {}
				}
				return '';
			},
			revokeVoiceMessageLocalUrl(url) {
				const safeUrl = String(url || '').trim();
				if (!safeUrl || safeUrl.indexOf('blob:') !== 0 || typeof URL === 'undefined' || typeof URL.revokeObjectURL !== 'function') {
					return;
				}
				try {
					URL.revokeObjectURL(safeUrl);
				} catch (e) {}
			},
			buildUserVoiceEntryFromRow(row, existingEntry) {
				if (!row || row.role !== 'user') return null;
				const current = existingEntry && typeof existingEntry === 'object' ? existingEntry : {};
				const messageId = this.normalizeDbMessageId(row && row.id);
				const voiceUrl = this.normalizeVoiceMessageUrl((row && row.voiceUrl) || current.voiceUrl);
				const resolvedAudioUrl = voiceUrl ? this.resolveVoiceMessageAudioUrl(voiceUrl) : '';
				const cachedLocalEntry = !resolvedAudioUrl ? this.findLocalUserVoiceEntry(messageId) : null;
				const cachedLocalAudioUrl = this.normalizeVoiceMessageUrl(cachedLocalEntry && cachedLocalEntry.audioUrl);
				const currentAudioUrl = this.normalizeVoiceMessageUrl(current.audioUrl);
				const audioUrl = cachedLocalAudioUrl || resolvedAudioUrl || currentAudioUrl;
				if (!messageId || !audioUrl) return null;
				const isPlaying = this.userVoicePlayingMessageId === messageId;
				return Object.assign({}, current, {
					audioUrl: audioUrl,
					voiceUrl: voiceUrl,
					durationMs: this.normalizeVoiceDurationMs(
						row && row.voiceDurationMs != null
							? row.voiceDurationMs
							: current.durationMs != null
								? current.durationMs
								: cachedLocalEntry && cachedLocalEntry.durationMs
					),
					state: isPlaying ? 'playing' : 'ready',
					error: isPlaying ? String(current.error || '') : ''
				});
			},
			normalizeOutgoingUserVoiceMeta(meta) {
				const source = meta && typeof meta === 'object' ? meta : {};
				const voiceUrl = this.normalizeVoiceMessageUrl(source.voiceUrl);
				const audioUrl = this.normalizeVoiceMessageUrl(source.audioUrl) || this.resolveVoiceMessageAudioUrl(voiceUrl);
				return {
					audioUrl: audioUrl,
					voiceUrl: voiceUrl,
					durationMs: this.normalizeVoiceDurationMs(source.durationMs)
				};
			},
			getUserVoiceEntry(row) {
				const messageId = this.normalizeDbMessageId(row && row.id);
				if (!messageId) return null;
				return this.userVoiceStateMap && this.userVoiceStateMap[messageId] ? this.userVoiceStateMap[messageId] : null;
			},
			setUserVoiceEntry(messageId, patch) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				const current = this.userVoiceStateMap && this.userVoiceStateMap[safeId] ? this.userVoiceStateMap[safeId] : {};
				const next = Object.assign({}, current, patch || {});
				this.$set(this.userVoiceStateMap, safeId, next);
				if (safeId.startsWith('db_') && this.isPersistableLocalUserVoiceAudioUrl(next.audioUrl)) {
					this.upsertLocalUserVoiceEntry({
						messageId: safeId,
						audioUrl: next.audioUrl,
						durationMs: next.durationMs
					});
				}
				return next;
			},
			clearUserVoiceEntry(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId || !this.userVoiceStateMap || !this.userVoiceStateMap[safeId]) return;
				const entry = this.userVoiceStateMap[safeId];
				if (entry && entry.audioUrl) {
					this.revokeVoiceMessageLocalUrl(entry.audioUrl);
				}
				if (this.userVoicePlayingMessageId === safeId) {
					this.userVoicePlayingMessageId = '';
				}
				this.$delete(this.userVoiceStateMap, safeId);
			},
			updateUserVoiceEntryId(fromMessageId, toMessageId) {
				const fromId = this.normalizeDbMessageId(fromMessageId);
				const nextId = this.normalizeDbMessageId(toMessageId);
				if (!fromId || !nextId || fromId === nextId || !this.userVoiceStateMap || !this.userVoiceStateMap[fromId]) {
					return;
				}
				const entry = this.userVoiceStateMap[fromId];
				this.$set(this.userVoiceStateMap, nextId, Object.assign({}, entry));
				this.$delete(this.userVoiceStateMap, fromId);
				if (this.userVoicePlayingMessageId === fromId) {
					this.userVoicePlayingMessageId = nextId;
				}
				this.updateLocalUserVoiceEntryId(fromId, nextId);
				if (this.isPersistableLocalUserVoiceAudioUrl(entry && entry.audioUrl)) {
					this.upsertLocalUserVoiceEntry({
						messageId: nextId,
						audioUrl: entry.audioUrl,
						durationMs: entry.durationMs
					});
				}
			},
			localUserVoiceStorageKey(conversationId, viewerKey) {
				const safeConversationId = this.resolveLocalChatConversationId(conversationId);
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				return safeViewerKey && safeConversationId ? LOCAL_USER_VOICE_CACHE_PREFIX + safeViewerKey + '_' + safeConversationId : '';
			},
			isPersistableLocalUserVoiceAudioUrl(url) {
				const safeUrl = this.normalizeVoiceMessageUrl(url);
				if (!safeUrl || safeUrl.indexOf('blob:') === 0) return false;
				if (/^https?:\/\//i.test(safeUrl)) return false;
				if (safeUrl.indexOf('/uploads/h5/') === 0) return false;
				if (safeUrl.indexOf('data:') === 0) {
					return safeUrl.length <= LOCAL_USER_VOICE_DATA_URL_MAX_LENGTH;
				}
				return true;
			},
			isManagedLocalUserVoiceFileUrl(url) {
				const safeUrl = this.normalizeVoiceMessageUrl(url);
				return this.isPersistableLocalUserVoiceAudioUrl(safeUrl) && safeUrl.indexOf('data:') !== 0;
			},
			releaseManagedLocalUserVoiceAudioUrl(url) {
				const safeUrl = this.normalizeVoiceMessageUrl(url);
				if (!this.isManagedLocalUserVoiceFileUrl(safeUrl)) return;
				if (typeof uni === 'undefined' || typeof uni.removeSavedFile !== 'function') return;
				try {
					uni.removeSavedFile({
						filePath: safeUrl
					});
				} catch (e) {}
			},
			normalizeLocalUserVoiceEntry(entry) {
				if (!entry || typeof entry !== 'object') return null;
				const messageId = this.normalizeDbMessageId(entry.messageId);
				const audioUrl = this.normalizeVoiceMessageUrl(entry.audioUrl);
				if (!messageId || !messageId.startsWith('db_') || !this.isPersistableLocalUserVoiceAudioUrl(audioUrl)) {
					return null;
				}
				const now = Date.now();
				const createdAtRaw = Number(entry.createdAt);
				const updatedAtRaw = Number(entry.updatedAt != null ? entry.updatedAt : createdAtRaw);
				return {
					messageId,
					audioUrl,
					durationMs: this.normalizeVoiceDurationMs(entry.durationMs),
					createdAt: isFinite(createdAtRaw) && createdAtRaw > 0 ? createdAtRaw : now,
					updatedAt: isFinite(updatedAtRaw) && updatedAtRaw > 0 ? updatedAtRaw : now
				};
			},
			calcLocalUserVoiceEntrySize(entry) {
				return entry && entry.audioUrl ? String(entry.audioUrl).length : 0;
			},
			capLocalUserVoiceEntriesByStorageBudget(entries) {
				const source = (Array.isArray(entries) ? entries : []).slice().sort((a, b) => a.createdAt - b.createdAt);
				const kept = [];
				let totalSize = 0;
				for (let i = source.length - 1; i >= 0; i -= 1) {
					const entry = source[i];
					if (!entry) continue;
					const entrySize = this.calcLocalUserVoiceEntrySize(entry);
					if (!kept.length || totalSize + entrySize <= LOCAL_USER_VOICE_CACHE_MAX_TOTAL_LENGTH) {
						kept.unshift(entry);
						totalSize += entrySize;
					}
				}
				return kept;
			},
			readLocalUserVoiceEntries(conversationId) {
				const key = this.localUserVoiceStorageKey(conversationId);
				if (!key) return [];
				try {
					const raw = uni.getStorageSync(key);
					const source =
						raw && typeof raw === 'object' && Array.isArray(raw.entries)
							? raw.entries
							: Array.isArray(raw)
								? raw
								: [];
					const now = Date.now();
					const entries = source
						.map((item) => this.normalizeLocalUserVoiceEntry(item))
						.filter((item) => item && now - item.updatedAt <= LOCAL_USER_VOICE_CACHE_TTL_MS)
						.sort((a, b) => a.createdAt - b.createdAt);
					const cappedEntries = this.capLocalUserVoiceEntriesByStorageBudget(entries);
					if (
						(raw && typeof raw === 'object' && raw.version !== LOCAL_USER_VOICE_CACHE_VERSION) ||
						entries.length !== source.length ||
						cappedEntries.length !== entries.length
					) {
						this.writeLocalUserVoiceEntries(cappedEntries, conversationId);
					}
					return cappedEntries;
				} catch (e) {
					return [];
				}
			},
			writeLocalUserVoiceEntries(entries, conversationId) {
				const key = this.localUserVoiceStorageKey(conversationId);
				if (!key) return;
				const normalized = (Array.isArray(entries) ? entries : [])
					.map((item) => this.normalizeLocalUserVoiceEntry(item))
					.filter(Boolean)
					.sort((a, b) => a.createdAt - b.createdAt);
				try {
					if (!normalized.length) {
						uni.removeStorageSync(key);
						return;
					}
					const countCapped = normalized.slice(Math.max(0, normalized.length - LOCAL_USER_VOICE_CACHE_LIMIT));
					const capped = this.capLocalUserVoiceEntriesByStorageBudget(countCapped);
					uni.setStorageSync(key, {
						version: LOCAL_USER_VOICE_CACHE_VERSION,
						updatedAt: Date.now(),
						entries: capped.map((item) => ({
							messageId: item.messageId,
							audioUrl: item.audioUrl,
							durationMs: item.durationMs,
							createdAt: item.createdAt,
							updatedAt: item.updatedAt
						}))
					});
				} catch (e) {}
			},
			findLocalUserVoiceEntry(messageId, conversationId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				const entries = this.readLocalUserVoiceEntries(conversationId);
				return entries.find((item) => item && item.messageId === safeId) || null;
			},
			upsertLocalUserVoiceEntry(entry, conversationId) {
				const normalized = this.normalizeLocalUserVoiceEntry(entry);
				if (!normalized) return null;
				const entries = this.readLocalUserVoiceEntries(conversationId);
				const next = Object.assign({}, normalized, {
					updatedAt: Date.now()
				});
				const index = entries.findIndex((item) => item && item.messageId === next.messageId);
				if (index >= 0) {
					next.createdAt = entries[index].createdAt || next.createdAt;
					entries.splice(index, 1, Object.assign({}, entries[index], next));
				} else {
					entries.push(next);
				}
				this.writeLocalUserVoiceEntries(entries, conversationId);
				return next;
			},
			updateLocalUserVoiceEntryId(fromMessageId, toMessageId, conversationId) {
				const fromId = this.normalizeDbMessageId(fromMessageId);
				const nextId = this.normalizeDbMessageId(toMessageId);
				if (!fromId || !nextId || fromId === nextId) return;
				const entries = this.readLocalUserVoiceEntries(conversationId);
				const index = entries.findIndex((item) => item && item.messageId === fromId);
				if (index < 0) return;
				entries[index] = Object.assign({}, entries[index], {
					messageId: nextId,
					updatedAt: Date.now()
				});
				this.writeLocalUserVoiceEntries(entries, conversationId);
			},
			migrateLocalUserVoiceCache(fromConversationId, toConversationId) {
				const fromKey = this.localUserVoiceStorageKey(fromConversationId);
				const toKey = this.localUserVoiceStorageKey(toConversationId);
				if (!fromKey || !toKey || fromKey === toKey) return;
				try {
					const current = uni.getStorageSync(toKey);
					const hasCurrent =
						Array.isArray(current) ||
						(current && typeof current === 'object' && Array.isArray(current.entries));
					if (hasCurrent) {
						uni.removeStorageSync(fromKey);
						return;
					}
					const source = uni.getStorageSync(fromKey);
					const hasSource =
						Array.isArray(source) ||
						(source && typeof source === 'object' && Array.isArray(source.entries));
					if (!hasSource) return;
					uni.setStorageSync(toKey, source);
					uni.removeStorageSync(fromKey);
				} catch (e) {}
			},
			shouldShowUserVoiceCard(row) {
				if (!this.isVoiceFeatureEnabledGlobal()) return false;
				return !!(row && row.role === 'user' && this.getUserVoiceEntry(row));
			},
			userVoiceDurationLabel(row) {
				const entry = this.getUserVoiceEntry(row);
				const durationMs = entry ? this.normalizeVoiceDurationMs(entry.durationMs) : 0;
				if (durationMs > 0) {
					return this.formatVoiceRecordDuration(durationMs);
				}
				return this.tx('voice_message_label', '语音消息');
			},
			userVoiceTranscriptText(row) {
				const text = String(row && row.text != null ? row.text : '')
					.replace(/\s+/g, ' ')
					.trim();
				return text;
			},
			userVoiceCardClass(row) {
				const entry = this.getUserVoiceEntry(row);
				const state = entry && entry.state ? entry.state : 'ready';
				return {
					'user-voice-card--playing': state === 'playing',
					'user-voice-card--error': state === 'error'
				};
			},
			createUserVoiceH5Player() {
				const audio = new Audio();
				audio.preload = 'auto';
				const endedHandlers = [];
				const stopHandlers = [];
				const errorHandlers = [];
				const emitHandlers = (list, payload) => {
					list.slice().forEach((fn) => {
						try {
							fn(payload);
						} catch (e) {}
					});
				};
				audio.addEventListener('ended', () => emitHandlers(endedHandlers));
				audio.addEventListener('error', (err) => emitHandlers(errorHandlers, err));
				return {
					autoplay: false,
					get src() {
						return audio.src;
					},
					set src(value) {
						audio.src = value || '';
					},
					onEnded(fn) {
						if (typeof fn === 'function') endedHandlers.push(fn);
					},
					onStop(fn) {
						if (typeof fn === 'function') stopHandlers.push(fn);
					},
					onError(fn) {
						if (typeof fn === 'function') errorHandlers.push(fn);
					},
					play() {
						const result = audio.play();
						if (result && typeof result.catch === 'function') {
							result.catch((err) => emitHandlers(errorHandlers, err));
						}
						return result;
					},
					stop() {
						try {
							audio.pause();
						} catch (e) {}
						try {
							audio.currentTime = 0;
						} catch (e) {}
						emitHandlers(stopHandlers);
					},
					destroy() {
						try {
							audio.pause();
						} catch (e) {}
						try {
							audio.removeAttribute('src');
							audio.load();
						} catch (e) {}
					}
				};
			},
			getUserVoicePlayer() {
				if (!this.userVoicePlayer) {
					if (!this.isAppPlus && typeof Audio === 'function') {
						this.userVoicePlayer = this.createUserVoiceH5Player();
					} else if (typeof uni !== 'undefined' && typeof uni.createInnerAudioContext === 'function') {
						this.userVoicePlayer = uni.createInnerAudioContext();
					}
				}
				if (this.userVoicePlayer && !this.userVoicePlayerReady) {
					this.userVoicePlayer.autoplay = false;
					this.userVoicePlayer.onEnded(() => {
						const messageId = this.userVoicePlayingMessageId;
						this.userVoicePlayingMessageId = '';
						if (messageId && this.userVoiceStateMap[messageId]) {
							this.setUserVoiceEntry(messageId, { state: 'ready' });
						}
					});
					this.userVoicePlayer.onStop(() => {
						const messageId = this.userVoicePlayingMessageId;
						this.userVoicePlayingMessageId = '';
						if (messageId && this.userVoiceStateMap[messageId]) {
							this.setUserVoiceEntry(messageId, { state: 'ready' });
						}
					});
					this.userVoicePlayer.onError(() => {
						const messageId = this.userVoicePlayingMessageId;
						this.userVoicePlayingMessageId = '';
						if (messageId && this.userVoiceStateMap[messageId]) {
							this.setUserVoiceEntry(messageId, {
								state: 'error',
								error: this.tx('voice_play_failed', '语音播放失败')
							});
						}
					});
					this.userVoicePlayerReady = true;
				}
				return this.userVoicePlayer;
			},
			stopUserVoicePlayback() {
				const messageId = this.userVoicePlayingMessageId;
				try {
					const player = this.getUserVoicePlayer();
					if (player) {
						player.stop();
					}
				} catch (e) {}
				this.userVoicePlayingMessageId = '';
				if (messageId && this.userVoiceStateMap[messageId]) {
					this.setUserVoiceEntry(messageId, { state: 'ready' });
				}
			},
			playUserVoiceByMessageId(messageId, sourceUrl) {
				const safeId = this.normalizeDbMessageId(messageId);
				const audioUrl = String(sourceUrl || '').trim();
				if (!safeId || !audioUrl) return;
				try {
					const player = this.getUserVoicePlayer();
					if (!player) {
						this.showErrorToast(this.tx('voice_play_failed', '语音播放失败'));
						return;
					}
					if (this.assistantVoicePlayingMessageId) {
						this.stopAssistantVoicePlayback();
					}
					if (this.userVoicePlayingMessageId && this.userVoicePlayingMessageId !== safeId) {
						this.stopUserVoicePlayback();
					}
					this.userVoicePlayingMessageId = safeId;
					this.setUserVoiceEntry(safeId, { state: 'playing' });
					player.src = audioUrl;
					player.play();
				} catch (e) {
					this.userVoicePlayingMessageId = '';
					this.setUserVoiceEntry(safeId, { state: 'error', error: this.tx('voice_play_failed', '语音播放失败') });
					this.showErrorToast(this.tx('voice_play_failed', '语音播放失败'));
				}
			},
			toggleUserVoice(row) {
				const messageId = this.normalizeDbMessageId(row && row.id);
				const entry = this.getUserVoiceEntry(row);
				if (!messageId || !entry || !entry.audioUrl) return;
				if (this.userVoicePlayingMessageId === messageId) {
					this.stopUserVoicePlayback();
					return;
				}
				this.playUserVoiceByMessageId(messageId, entry.audioUrl);
			},
			disposeUserVoicePlayer() {
				if (this.userVoicePlayer) {
					try {
						if (typeof this.userVoicePlayer.destroy === 'function') {
							this.userVoicePlayer.destroy();
						} else if (typeof this.userVoicePlayer.stop === 'function') {
							this.userVoicePlayer.stop();
						}
					} catch (e) {}
				}
				this.userVoicePlayer = null;
				this.userVoicePlayerReady = false;
				this.userVoicePlayingMessageId = '';
				Object.keys(this.userVoiceStateMap || {}).forEach((messageId) => {
					const entry = this.userVoiceStateMap[messageId];
					if (entry && entry.audioUrl) {
						this.revokeVoiceMessageLocalUrl(entry.audioUrl);
					}
				});
				this.userVoiceStateMap = {};
			},
			onInputFocus() {
				this.inputFocus = true;
				companionStore.emitLayout({ avoidBottom: 150, compact: true });
				this.closeMessageActionSheet();
				this.closeChatAttachmentMenu();
				this.closeExpressionPanel();
				this.$nextTick(() => {
					if (!this.atChatBottom) return;
					this.scrollChatToBottom({ immediate: true });
					setTimeout(() => {
						if (this.inputFocus && this.atChatBottom) {
							this.scrollChatToBottom({ immediate: true });
						}
					}, 180);
				});
			},
			onInputBlur() {
				this.inputFocus = false;
				companionStore.emitLayout({ avoidBottom: 92, compact: false });
				this.flushDraftSave();
			},
			closeChatAttachmentMenu() {
				this.attachmentMenuVisible = false;
			},
			closeExpressionPanel() {
				this.expressionPanelVisible = false;
				if (this.expressionEditor.visible && !this.expressionEditor.saving) {
					this.closeExpressionEditor();
				}
			},
			ensureCanUseChatImages() {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser()) {
					uni.showToast({
						title: this.tx('chat_image_need_login', '识图功能需要先登录账号'),
						icon: 'none'
					});
					return false;
				}
				if (typeof tavernApi.isUserByokEnabled === 'function' && !tavernApi.isUserByokEnabled()) {
					uni.showToast({
						title: this.tx('chat_image_need_byok', '当前未开启自定义 API 识图功能'),
						icon: 'none'
					});
					return false;
				}
				return true;
			},
			isVoiceFeatureEnabledGlobal() {
				return this.voiceFeatureEnabledGlobal !== false;
			},
			applyVoiceFeatureGlobalConfig(config) {
				const enabled = !(config && config.voiceFeatureEnabled === false);
				this.voiceFeatureEnabledGlobal = enabled;
				if (!enabled) {
					this.handleVoiceFeatureDisabledRuntime();
				}
				return enabled;
			},
			refreshVoiceFeatureGlobalState(force) {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					this.applyVoiceFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
					return Promise.resolve(tavernApi.fetchAppRuntimeConfig(force)).then((config) => {
						this.applyVoiceFeatureGlobalConfig(config);
						return this.voiceFeatureEnabledGlobal;
					}).catch(() => this.voiceFeatureEnabledGlobal);
				} catch (e) {
					return Promise.resolve(this.voiceFeatureEnabledGlobal);
				}
			},
			refreshVoiceInputAiState(force) {
				const current = this.voiceInputAiState || this.normalizeVoiceInputAiState(null);
				if (!force && Number(current.loadedAt || 0) > 0 && Date.now() - Number(current.loadedAt || 0) < 15000) {
					return Promise.resolve(current);
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					const clientUid =
						tavernApi && typeof tavernApi.getClientUid === 'function'
							? String(tavernApi.getClientUid() || '').trim()
							: '';
					if (!clientUid || !tavernApi || typeof tavernApi.getTavernUserAiProvider !== 'function') {
						return Promise.reject(new Error(this.tx('voice_ai_state_failed', '语音配置读取失败，请稍后再试')));
					}
					return tavernApi.getTavernUserAiProvider(clientUid).then((data) => {
						const next = this.normalizeVoiceInputAiState(data);
						this.voiceInputAiState = next;
						return next;
					});
				} catch (e) {
					return Promise.reject(e);
				}
			},
			ensureVoiceInputAiReady() {
				return this.refreshVoiceInputAiState(false)
					.then((state) => {
						if (!state || state.canUse === false) {
							this.showErrorToast(
								(state && state.denyReason) || this.tx('voice_need_ai_ready', '当前账号暂不可用语音输入')
							);
							return false;
						}
						if (String((state && state.mode) || '').trim() !== 'custom') {
							this.showErrorToast(
								this.tx('voice_need_custom_mode', '先去 AI 设置页切到“我的 API Key”，再使用语音输入')
							);
							return false;
						}
						if (!state.apiKeyConfigured) {
							this.showErrorToast(
								this.tx('voice_need_api_key', '先去 AI 设置页填写可用 API Key，再使用语音输入')
							);
							return false;
						}
						if (!this.normalizeCharacterVoiceText(state.sttModelName, 255)) {
							this.showErrorToast(
								this.tx('voice_need_stt_model', '先在 AI 设置页配置语音识别模型，再使用语音输入')
							);
							return false;
						}
						return true;
					})
					.catch((err) => {
						this.showErrorToast(this.jgErrMsg(err, this.tx('voice_ai_state_failed', '语音配置读取失败，请稍后再试')));
						return false;
					});
			},
			handleVoiceFeatureDisabledRuntime() {
				this.clearPendingVoiceStart();
				if (this.voiceRecording || this.voiceStopping || this.voiceTranscribing) {
					this.disposeVoiceRecorder(true);
				}
				if (this.userVoicePlayingMessageId) {
					this.stopUserVoicePlayback();
				}
				if (this.assistantVoicePlayingMessageId || Object.keys(this.assistantVoiceStateMap || {}).length) {
					this.interruptAssistantVoiceRound({ stopUserVoice: false });
				}
				if (this.characterVoicePanel && this.characterVoicePanel.visible) {
					this.characterVoicePanel = createCharacterVoicePanelState();
				}
				this.assistantVoiceAutoEnabled = false;
				this.assistantVoiceBrowserUnlocked = false;
				this.assistantVoiceBrowserUnlocking = false;
			},
			ensureCanUseVoiceInput() {
				if (!this.isVoiceFeatureEnabledGlobal()) {
					uni.showToast({
						title: this.tx('voice_feature_disabled', '当前已关闭语音功能'),
						icon: 'none'
					});
					return false;
				}
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser()) {
					uni.showToast({
						title: this.tx('voice_need_login', '语音输入需要先登录账号'),
						icon: 'none'
					});
					return false;
				}
				if (typeof tavernApi.isUserByokEnabled === 'function' && !tavernApi.isUserByokEnabled()) {
					uni.showToast({
						title: this.tx('voice_need_byok', '当前未开启自定义 API 语音功能'),
						icon: 'none'
					});
					return false;
				}
				if (this.canUseBrowserVoiceInput()) {
					return true;
				}
				if (typeof uni === 'undefined' || typeof uni.getRecorderManager !== 'function') {
					uni.showToast({
						title: this.tx('voice_not_supported', '当前环境暂不支持录音'),
						icon: 'none'
					});
					return false;
				}
				return true;
			},
			canUseBrowserVoiceInput() {
				if (this.isAppPlus) return false;
				return (
					typeof window !== 'undefined' &&
					typeof navigator !== 'undefined' &&
					navigator.mediaDevices &&
					typeof navigator.mediaDevices.getUserMedia === 'function' &&
					typeof MediaRecorder !== 'undefined'
				);
			},
			requestAppAndroidPermission(permissionId) {
				if (!this.isAppPlus) {
					return Promise.resolve(1);
				}
				try {
					if (typeof plus === 'undefined' || !plus.os) {
						return Promise.resolve(1);
					}
					const osName = String((plus.os && plus.os.name) || '').trim().toLowerCase();
					if (osName !== 'android') {
						return Promise.resolve(1);
					}
					if (!plus.android || typeof plus.android.requestPermissions !== 'function') {
						return Promise.resolve(1);
					}
				} catch (e) {
					return Promise.resolve(1);
				}
				const safePermissionId = String(permissionId || '').trim();
				if (!safePermissionId) {
					return Promise.resolve(1);
				}
				return new Promise((resolve) => {
					try {
						plus.android.requestPermissions(
							[safePermissionId],
							(resultObj) => {
								const granted = Array.isArray(resultObj && resultObj.granted)
									? resultObj.granted.some((item) => String(item || '').trim() === safePermissionId)
									: false;
								if (granted) {
									resolve(1);
									return;
								}
								const deniedAlways = Array.isArray(resultObj && resultObj.deniedAlways)
									? resultObj.deniedAlways.some((item) => String(item || '').trim() === safePermissionId)
									: false;
								if (deniedAlways) {
									resolve(-1);
									return;
								}
								resolve(0);
							},
							() => resolve(0)
						);
					} catch (err) {
						resolve(0);
					}
				});
			},
			ensureAppMicrophonePermission() {
				return this.requestAppAndroidPermission('android.permission.RECORD_AUDIO').then((result) => {
					if (result === 1) {
						return true;
					}
					if (result === -1) {
						this.showErrorToast(
							this.tx('voice_permission_settings', '麦克风权限被永久拒绝，请到系统设置里开启')
						);
						return false;
					}
					this.showErrorToast(this.tx('voice_permission_required', '请先允许 APP 使用麦克风'));
					return false;
				});
			},
			isBrowserVoiceUploadSource(source) {
				return !!(
					source &&
					typeof source === 'object' &&
					typeof source.size === 'number' &&
					typeof source.type === 'string'
				);
			},
			getVoiceRecorderManager() {
				if (!this.voiceRecorderManager && typeof uni !== 'undefined' && typeof uni.getRecorderManager === 'function') {
					this.voiceRecorderManager = uni.getRecorderManager();
				}
				if (this.voiceRecorderManager && !this.voiceRecorderReady) {
					this.voiceRecorderManager.onStop((res) => {
						this.handleVoiceRecorderStop(res);
					});
					this.voiceRecorderManager.onError((err) => {
						this.handleVoiceRecorderError(err);
					});
					this.voiceRecorderReady = true;
				}
				return this.voiceRecorderManager;
			},
			clearVoiceRecordTimer() {
				if (this.voiceRecordTimer) {
					clearInterval(this.voiceRecordTimer);
					this.voiceRecordTimer = null;
				}
			},
			clearVoiceStopPending() {
				if (this.voiceStopPendingTimer) {
					clearTimeout(this.voiceStopPendingTimer);
					this.voiceStopPendingTimer = null;
				}
				this.voiceStopPendingAt = 0;
			},
			armVoiceStopPending(silent) {
				this.clearVoiceStopPending();
				this.voiceStopPendingAt = Date.now();
				const silentTimeout = silent === true;
				this.voiceStopPendingTimer = setTimeout(() => {
					this.voiceStopPendingTimer = null;
					this.voiceStopPendingAt = 0;
					this.voiceDiscardNextStop = true;
					this.voiceTranscribing = false;
					this.resetVoiceRecordingState();
					if (!silentTimeout) {
						this.showErrorToast(this.tx('voice_stop_timeout', '录音结束超时，请重试'));
					}
				}, VOICE_STOP_CALLBACK_TIMEOUT_MS);
			},
			freezeVoiceRecordingForStop() {
				const currentDuration = this.normalizeVoiceDurationMs(
					Math.max(
						Number(this.voiceRecordDurationMs || 0),
						Number(this.voiceRecordStartedAt || 0) > 0 ? Date.now() - Number(this.voiceRecordStartedAt || 0) : 0
					)
				);
				this.clearVoiceRecordTimer();
				this.voiceRecordDurationMs = currentDuration;
				this.voiceRecording = false;
				this.voiceStopping = true;
				return currentDuration;
			},
			startVoiceRecordTimer() {
				this.clearVoiceRecordTimer();
				this.voiceRecordDurationMs = 0;
				this.voiceRecordStartedAt = Date.now();
				this.voiceRecordTimer = setInterval(() => {
					this.voiceRecordDurationMs = Math.max(0, Date.now() - Number(this.voiceRecordStartedAt || 0));
				}, 250);
			},
			resetVoiceRecordingState() {
				this.clearVoiceStopPending();
				this.clearVoiceRecordTimer();
				this.voiceRecording = false;
				this.voiceStopping = false;
				this.voiceRecordStartedAt = 0;
				this.voiceRecordDurationMs = 0;
			},
			stopBrowserVoiceStream(stream) {
				const target = stream || this.voiceBrowserStream;
				if (!target || typeof target.getTracks !== 'function') return;
				try {
					target.getTracks().forEach((track) => {
						try {
							track.stop();
						} catch (e) {}
					});
				} catch (e) {}
			},
			pickBrowserVoiceMimeType() {
				if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
					return '';
				}
				let candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];
				try {
					const ua = typeof navigator !== 'undefined' ? String(navigator.userAgent || '').toLowerCase() : '';
					const isFirefox = ua.indexOf('firefox') >= 0;
					const isSafari = ua.indexOf('safari') >= 0 && ua.indexOf('chrome') < 0 && ua.indexOf('chromium') < 0;
					if (isFirefox) {
						candidates = ['audio/ogg;codecs=opus', 'audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
					} else if (isSafari) {
						candidates = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus'];
					}
				} catch (e) {}
				for (let i = 0; i < candidates.length; i += 1) {
					if (MediaRecorder.isTypeSupported(candidates[i])) {
						return candidates[i];
					}
				}
				return '';
			},
			buildBrowserVoiceFile(blob, mimeType) {
				const safeMimeType = String(mimeType || (blob && blob.type) || 'audio/webm').trim() || 'audio/webm';
				let ext = 'webm';
				if (safeMimeType.indexOf('ogg') >= 0) {
					ext = 'ogg';
				} else if (safeMimeType.indexOf('mp4') >= 0 || safeMimeType.indexOf('m4a') >= 0) {
					ext = 'm4a';
				}
				try {
					return new File([blob], 'voice.' + ext, {
						type: safeMimeType,
						lastModified: Date.now()
					});
				} catch (e) {
					blob.name = 'voice.' + ext;
					return blob;
				}
			},
			readBrowserVoiceArrayBuffer(blobLike) {
				if (!blobLike) {
					return Promise.reject(new Error('empty_blob'));
				}
				try {
					if (typeof blobLike.arrayBuffer === 'function') {
						return blobLike.arrayBuffer();
					}
				} catch (e) {}
				return new Promise((resolve, reject) => {
					try {
						const reader = new FileReader();
						reader.onload = (event) => {
							resolve(event && event.target ? event.target.result : reader.result);
						};
						reader.onerror = () => reject(new Error('read_blob_failed'));
						reader.readAsArrayBuffer(blobLike);
					} catch (err) {
						reject(err);
					}
				});
			},
			readBrowserVoiceDataUrl(blobLike) {
				if (!blobLike) {
					return Promise.reject(new Error('empty_blob'));
				}
				return new Promise((resolve, reject) => {
					try {
						const reader = new FileReader();
						reader.onload = (event) => {
							resolve(event && event.target ? event.target.result : reader.result);
						};
						reader.onerror = () => reject(new Error('read_blob_failed'));
						reader.readAsDataURL(blobLike);
					} catch (err) {
						reject(err);
					}
				});
			},
			persistVoiceUploadSourceLocally(uploadSource) {
				const fallbackUrl = this.buildVoiceMessageLocalUrl(uploadSource);
				if (typeof uploadSource === 'string') {
					const tempFilePath = String(uploadSource || '').trim();
					if (!tempFilePath) {
						return Promise.resolve(fallbackUrl);
					}
					if (typeof uni === 'undefined' || typeof uni.saveFile !== 'function') {
						return Promise.resolve(tempFilePath);
					}
					return new Promise((resolve) => {
						try {
							uni.saveFile({
								tempFilePath,
								success: (res) => {
									const savedFilePath = String((res && res.savedFilePath) || tempFilePath).trim();
									resolve(savedFilePath || tempFilePath);
								},
								fail: () => resolve(tempFilePath)
							});
						} catch (e) {
							resolve(tempFilePath);
						}
					});
				}
				if (this.isBrowserVoiceUploadSource(uploadSource)) {
					return this.readBrowserVoiceDataUrl(uploadSource)
						.then((dataUrl) => {
							const safeDataUrl = this.normalizeVoiceMessageUrl(dataUrl);
							if (!safeDataUrl || safeDataUrl.length > LOCAL_USER_VOICE_DATA_URL_MAX_LENGTH) {
								return fallbackUrl;
							}
							return safeDataUrl;
						})
						.catch(() => fallbackUrl);
				}
				return Promise.resolve(fallbackUrl);
			},
			createBrowserAudioContext() {
				try {
					if (typeof window !== 'undefined') {
						const Ctor = window.AudioContext || window.webkitAudioContext;
						if (Ctor) {
							return new Ctor();
						}
					}
				} catch (e) {}
				return null;
			},
			mixAudioBufferToMono(audioBuffer) {
				const channelCount = Math.max(1, Number(audioBuffer && audioBuffer.numberOfChannels) || 1);
				const frameCount = Math.max(0, Number(audioBuffer && audioBuffer.length) || 0);
				const mono = new Float32Array(frameCount);
				if (!audioBuffer || !frameCount) return mono;
				for (let channelIndex = 0; channelIndex < channelCount; channelIndex += 1) {
					const channelData = audioBuffer.getChannelData(channelIndex);
					for (let i = 0; i < frameCount; i += 1) {
						mono[i] += channelData[i] / channelCount;
					}
				}
				return mono;
			},
			buildWavArrayBufferFromMono(samples, sampleRate) {
				const frameCount = samples ? samples.length : 0;
				const buffer = new ArrayBuffer(44 + frameCount * 2);
				const view = new DataView(buffer);
				const writeAscii = (offset, text) => {
					for (let i = 0; i < text.length; i += 1) {
						view.setUint8(offset + i, text.charCodeAt(i));
					}
				};
				writeAscii(0, 'RIFF');
				view.setUint32(4, 36 + frameCount * 2, true);
				writeAscii(8, 'WAVE');
				writeAscii(12, 'fmt ');
				view.setUint32(16, 16, true);
				view.setUint16(20, 1, true);
				view.setUint16(22, 1, true);
				view.setUint32(24, sampleRate, true);
				view.setUint32(28, sampleRate * 2, true);
				view.setUint16(32, 2, true);
				view.setUint16(34, 16, true);
				writeAscii(36, 'data');
				view.setUint32(40, frameCount * 2, true);
				let offset = 44;
				for (let i = 0; i < frameCount; i += 1) {
					const sample = Math.max(-1, Math.min(1, samples[i] || 0));
					view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
					offset += 2;
				}
				return buffer;
			},
			convertBrowserVoiceToWavFile(source) {
				if (!this.isBrowserVoiceUploadSource(source)) {
					return Promise.resolve(source);
				}
				const mimeType = String((source && source.type) || '').toLowerCase();
				if (mimeType.indexOf('audio/wav') === 0 || mimeType.indexOf('audio/mpeg') === 0) {
					return Promise.resolve(source);
				}
				const audioContext = this.createBrowserAudioContext();
				if (!audioContext) {
					return Promise.resolve(source);
				}
				return this.readBrowserVoiceArrayBuffer(source)
					.then((arrayBuffer) => audioContext.decodeAudioData(arrayBuffer.slice(0)))
					.then((audioBuffer) => {
						const sampleRate = Math.max(8000, Math.floor(Number(audioBuffer && audioBuffer.sampleRate) || 16000));
						const mono = this.mixAudioBufferToMono(audioBuffer);
						const wavArrayBuffer = this.buildWavArrayBufferFromMono(mono, sampleRate);
						const wavBlob = new Blob([wavArrayBuffer], { type: 'audio/wav' });
						try {
							return new File([wavBlob], 'voice.wav', {
								type: 'audio/wav',
								lastModified: Date.now()
							});
						} catch (e) {
							wavBlob.name = 'voice.wav';
							return wavBlob;
						}
					})
					.catch(() => source)
					.finally(() => {
						try {
							if (typeof audioContext.close === 'function') {
								audioContext.close();
							}
						} catch (e) {}
					});
			},
			transcribeVoiceUploadSource(uploadSource, meta) {
				const tavernApi = require('@/common/tavernApi.js');
				const voiceMeta = meta && typeof meta === 'object' ? meta : {};
				const localAudioUrl = String(voiceMeta.audioUrl || '').trim();
				this.voiceTranscribing = true;
				return this.persistVoiceUploadSourceLocally(uploadSource)
					.then((persistedLocalAudioUrl) => {
						const playbackLocalAudioUrl = this.normalizeVoiceMessageUrl(persistedLocalAudioUrl) || localAudioUrl;
						return this.convertBrowserVoiceToWavFile(uploadSource)
							.then((normalizedSource) => tavernApi.transcribeTavernAudio(normalizedSource, tavernApi.getClientUid()))
							.then((data) => {
								const text = data && data.text != null ? String(data.text).trim() : '';
								if (!text) {
									throw new Error(this.tx('voice_empty', '没有识别到可发送的内容'));
								}
								const persistedVoiceUrl = this.normalizeVoiceMessageUrl(data && data.audioUrl);
								const serverAudioUrl = persistedVoiceUrl ? this.resolveVoiceMessageAudioUrl(persistedVoiceUrl) : '';
								const playbackAudioUrl = playbackLocalAudioUrl || serverAudioUrl || localAudioUrl;
								const sent = this.submitOutgoingMessage(text, [], {
									clearDraft: false,
									clearComposerImages: false,
									allowWhenNotAtBottom: true,
									userVoiceMeta: {
										durationMs: this.normalizeVoiceDurationMs(voiceMeta.durationMs),
										audioUrl: playbackAudioUrl || localAudioUrl,
										voiceUrl: persistedVoiceUrl
									}
								});
								if (localAudioUrl && (!sent || localAudioUrl !== playbackAudioUrl)) {
									this.revokeVoiceMessageLocalUrl(localAudioUrl);
								}
								if (!sent) {
									if (playbackLocalAudioUrl && playbackLocalAudioUrl !== localAudioUrl) {
										this.releaseManagedLocalUserVoiceAudioUrl(playbackLocalAudioUrl);
									}
									this.draft = text;
									uni.showToast({
										title: this.tx('voice_fill_draft', '语音内容已放到输入框'),
										icon: 'none'
									});
								}
								return data;
							})
							.catch((err) => {
								if (playbackLocalAudioUrl && playbackLocalAudioUrl !== localAudioUrl) {
									this.releaseManagedLocalUserVoiceAudioUrl(playbackLocalAudioUrl);
								}
								if (localAudioUrl) {
									this.revokeVoiceMessageLocalUrl(localAudioUrl);
								}
								this.showErrorToast(this.jgErrMsg(err, this.tx('voice_transcribe_failed', '语音识别失败')));
								return null;
							})
							.finally(() => {
								this.voiceTranscribing = false;
							});
					});
			},
			handleVoiceRecorderError(err) {
				this.voiceDiscardNextStop = false;
				this.clearVoiceStopPending();
				this.voiceTranscribing = false;
				this.resetVoiceRecordingState();
				this.voiceBrowserRecorder = null;
				this.stopBrowserVoiceStream();
				this.voiceBrowserStream = null;
				this.showErrorToast(this.jgErrMsg(err, this.tx('voice_failed', '录音失败，请稍后再试')));
			},
			handleVoiceRecorderStop(res) {
				this.clearVoiceStopPending();
				const shouldDiscard = this.voiceDiscardNextStop === true;
				this.voiceDiscardNextStop = false;
				const durationMs = this.normalizeVoiceDurationMs(this.voiceRecordDurationMs);
				this.resetVoiceRecordingState();
				if (shouldDiscard) {
					return;
				}
				if (durationMs > 0 && durationMs < MIN_VOICE_RECORD_DURATION_MS) {
					this.showErrorToast(this.tx('voice_too_short', '录音太短了，再说长一点试试'));
					return;
				}
				const uploadSource = res && Object.prototype.hasOwnProperty.call(res, 'tempFilePath') ? res.tempFilePath : '';
				if (
					(!uploadSource && !this.isBrowserVoiceUploadSource(uploadSource)) ||
					(typeof uploadSource === 'string' && !String(uploadSource).trim())
				) {
					this.showErrorToast(this.tx('voice_failed', '录音失败，请稍后再试'));
					return;
				}
				this.transcribeVoiceUploadSource(
					typeof uploadSource === 'string' ? String(uploadSource).trim() : uploadSource,
					{
						durationMs: durationMs,
						audioUrl: this.buildVoiceMessageLocalUrl(uploadSource)
					}
				);
			},
			async startBrowserVoiceRecording() {
				if (!this.canUseBrowserVoiceInput()) {
					this.showErrorToast(this.tx('voice_not_supported', '当前环境暂不支持录音'));
					return;
				}
				const stream = await navigator.mediaDevices.getUserMedia({
					audio: {
						channelCount: 1,
						echoCancellation: true,
						noiseSuppression: true,
						autoGainControl: true
					}
				});
				const mimeType = this.pickBrowserVoiceMimeType();
				const options = mimeType ? { mimeType: mimeType } : undefined;
				const recorder = options ? new MediaRecorder(stream, options) : new MediaRecorder(stream);
				const chunks = [];
				recorder.ondataavailable = (event) => {
					if (event && event.data && event.data.size > 0) {
						chunks.push(event.data);
					}
				};
				recorder.onerror = (event) => {
					const reason = event && event.error ? event.error : event;
					this.handleVoiceRecorderError(reason);
				};
				recorder.onstop = () => {
					const shouldDiscard = this.voiceDiscardNextStop === true;
					this.voiceBrowserRecorder = null;
					this.stopBrowserVoiceStream(stream);
					if (this.voiceBrowserStream === stream) {
						this.voiceBrowserStream = null;
					}
					const finalMimeType = recorder.mimeType || mimeType || 'audio/webm';
					const blob = new Blob(chunks, { type: finalMimeType });
					if (shouldDiscard) {
						this.voiceDiscardNextStop = false;
						this.resetVoiceRecordingState();
						return;
					}
					this.handleVoiceRecorderStop({
						tempFilePath: this.buildBrowserVoiceFile(blob, finalMimeType)
					});
				};
				this.voiceBrowserRecorder = recorder;
				this.voiceBrowserStream = stream;
				this.voiceBrowserMimeType = mimeType || '';
				this.voiceDiscardNextStop = false;
				this.voiceStopping = false;
				recorder.start(250);
				this.voiceRecording = true;
				this.startVoiceRecordTimer();
			},
			startVoiceRecording() {
				if (!this.ensureCanUseVoiceInput()) return;
				if (this.voiceRecording || this.voiceStopping || this.voiceTranscribing || this.sending) return;
				this.ensureVoiceInputAiReady().then((ready) => {
					if (!ready) return;
					this.ensureAppMicrophonePermission().then((granted) => {
						if (!granted) return;
						if (this.voiceRecording || this.voiceStopping || this.voiceTranscribing || this.sending) return;
						this.clearPendingVoiceStart();
						this.clearVoiceStopPending();
						this.voiceStopping = false;
						this.interruptAssistantVoiceRound();
						this.closeChatAttachmentMenu();
						this.closeExpressionPanel();
						this.closeReplySuggestions();
						this.inputFocus = false;
						try {
							uni.hideKeyboard();
						} catch (e) {}
						if (this.canUseBrowserVoiceInput()) {
							this.startBrowserVoiceRecording().catch((err) => {
								this.handleVoiceRecorderError(err);
							});
							return;
						}
						const recorder = this.getVoiceRecorderManager();
						if (!recorder) {
							this.showErrorToast(this.tx('voice_not_supported', '当前环境暂不支持录音'));
							return;
						}
						this.voiceDiscardNextStop = false;
						try {
							recorder.start({
								duration: 60000,
								sampleRate: 16000,
								numberOfChannels: 1,
								encodeBitRate: 96000,
								format: 'mp3'
							});
							this.voiceRecording = true;
							this.voiceStopping = false;
							this.startVoiceRecordTimer();
						} catch (err) {
							this.handleVoiceRecorderError(err);
						}
					});
				});
			},
			stopVoiceRecording(discard) {
				if (!this.voiceRecording || this.voiceStopping) return;
				if (this.voiceBrowserRecorder) {
					this.voiceDiscardNextStop = discard === true;
					this.freezeVoiceRecordingForStop();
					try {
						if (this.voiceBrowserRecorder.state !== 'inactive') {
							this.voiceBrowserRecorder.stop();
						}
					} catch (err) {
						this.handleVoiceRecorderError(err);
					}
					return;
				}
				const recorder = this.getVoiceRecorderManager();
				if (!recorder) {
					this.resetVoiceRecordingState();
					return;
				}
				this.voiceDiscardNextStop = discard === true;
				this.freezeVoiceRecordingForStop();
				try {
					this.armVoiceStopPending(discard === true);
					recorder.stop();
				} catch (err) {
					this.handleVoiceRecorderError(err);
				}
			},
			toggleVoiceInput() {
				if (!this.isVoiceFeatureEnabledGlobal()) return;
				if (this.voiceTranscribing) {
					uni.showToast({
						title: this.tx('voice_sending_title', '发送语音'),
						icon: 'none'
					});
					return;
				}
				if (this.voiceStopping) {
					uni.showToast({
						title: this.tx('voice_stopping_title', '结束录音中'),
						icon: 'none'
					});
					return;
				}
				if (this.pendingVoiceStartTimer) {
					this.clearPendingVoiceStart();
					return;
				}
				if (this.voiceRecording) {
					this.stopVoiceRecording(false);
					return;
				}
				if (this.sending || this.streamAbortController) {
					this.stopGeneration({ silent: true });
					this.startVoiceRecordingAfterStop();
					return;
				}
				this.startVoiceRecording();
			},
			disposeVoiceRecorder(discard) {
				this.clearVoiceStopPending();
				this.voiceDiscardNextStop = discard === true;
				if (this.voiceBrowserRecorder) {
					try {
						if (this.voiceBrowserRecorder.state !== 'inactive') {
							this.voiceBrowserRecorder.stop();
						}
					} catch (e) {}
					this.voiceBrowserRecorder = null;
				}
				this.stopBrowserVoiceStream();
				this.voiceBrowserStream = null;
				if (this.voiceRecording || this.voiceStopping) {
					try {
						const recorder = this.getVoiceRecorderManager();
						if (recorder) {
							recorder.stop();
						}
					} catch (e) {}
				}
				this.voiceTranscribing = false;
				this.resetVoiceRecordingState();
			},
			openExpressionPanel() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char || !this.atChatBottom) return;
				this.closeChatAttachmentMenu();
				this.closeReplySuggestions();
				this.refreshLocalExpressionLibrary();
				this.expressionPanelVisible = !this.expressionPanelVisible;
				if (this.expressionPanelVisible) {
					this.inputFocus = false;
					try {
						uni.hideKeyboard();
					} catch (e) {}
				}
			},
			closeExpressionEditor() {
				if (this.expressionEditor.saving) return;
				this.expressionEditor = {
					visible: false,
					id: '',
					draft: '',
					imageUrl: '',
					saving: false
				};
			},
			openExpressionEditor(imageUrl, entry) {
				this.expressionEditor = {
					visible: true,
					id: entry && entry.id ? String(entry.id) : '',
					draft: entry && entry.label ? String(entry.label) : '',
					imageUrl: imageUrl || (entry && entry.imageUrl ? String(entry.imageUrl) : ''),
					saving: false
				};
				this.inputFocus = false;
				try {
					uni.hideKeyboard();
				} catch (e) {}
			},
			pickLocalExpression(sourceType) {
				if (this.expressionUploadBusy) return;
				if (sourceType === 'camera' && !this.ensureAppCameraReady()) return;
				const maxBytes = LOCAL_EXPRESSION_PICK_MAX_BYTES;
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					sourceType: [sourceType === 'camera' ? 'camera' : 'album'],
					success: (res) => {
						const picked = this.extractPickedChatImages(res, maxBytes);
						if (!picked.length) return;
						this.createLocalExpressionDraftFromPick(picked[0]);
					},
					fail: (err) => {
						this.handleChooseImageFailure(err, sourceType);
					}
				});
			},
			createLocalExpressionDraftFromPick(item) {
				if (!item || !item.uploadFile) return;
				const tavernApi = require('@/common/tavernApi.js');
				this.expressionUploadBusy = true;
				tavernApi
					.prepareLocalChatImage(item.uploadFile)
					.then((data) => {
						const imageUrl = data && data.url ? String(data.url).trim() : '';
						if (!imageUrl || imageUrl.indexOf('data:image/') !== 0) {
							throw new Error(this.tx('expression_failed', '表情加载失败'));
						}
						if (imageUrl.length > LOCAL_EXPRESSION_DATA_URL_MAX_LENGTH) {
							throw new Error(this.tx('expression_too_large', '表情图片太大，建议裁剪后再试'));
						}
						this.openExpressionEditor(imageUrl, null);
					})
					.catch((e) => {
						this.showErrorToast(this.jgErrMsg(e, this.tx('expression_failed', '表情加载失败')));
					})
					.finally(() => {
						this.expressionUploadBusy = false;
					});
			},
			submitExpressionEditor() {
				const label = String(this.expressionEditor.draft || '').replace(/\s+/g, ' ').trim();
				const imageUrl = String(this.expressionEditor.imageUrl || '').trim();
				if (!imageUrl || imageUrl.indexOf('data:image/') !== 0) {
					this.showErrorToast(this.tx('expression_failed', '表情加载失败'));
					return;
				}
				if (!label) {
					this.showErrorToast(this.tx('expression_need_name', '先给这张表情起个名字'));
					return;
				}
				this.expressionEditor.saving = true;
				const now = Date.now();
				const current = this.expressionLibrary.find((item) => item && item.id === this.expressionEditor.id) || null;
				const saved = this.upsertLocalExpressionEntry({
					id: current && current.id ? current.id : 'expr_' + now + '_' + Math.random().toString(36).slice(2, 8),
					label: label.slice(0, LOCAL_EXPRESSION_LABEL_MAX),
					content: label.slice(0, LOCAL_EXPRESSION_LABEL_MAX),
					imageUrl,
					createdAt: current && current.createdAt ? current.createdAt : now,
					updatedAt: now,
					lastUsedAt: current && current.lastUsedAt ? current.lastUsedAt : 0,
					useCount: current && current.useCount ? current.useCount : 0
				});
				this.expressionEditor.saving = false;
				if (!saved) {
					this.showErrorToast(this.tx('expression_save_failed', '表情保存失败，请换更小的图片试试'));
					return;
				}
				const successText = current
					? this.tx('expression_rename_success', '表情名字已更新')
					: this.tx('expression_save_success', '表情已保存');
				this.closeExpressionEditor();
				uni.showToast({
					title: successText,
					icon: 'none'
				});
			},
			sendLocalExpression(item) {
				const imageUrl = item && item.imageUrl ? String(item.imageUrl).trim() : '';
				const attachmentHint = this.normalizeLocalExpressionHint(item && (item.content || item.label));
				if (!imageUrl) {
					uni.showToast({
						title: this.tx('expression_failed', '表情加载失败'),
						icon: 'none'
					});
					return;
				}
				if (!this.ensureCanUseChatImages()) return;
				const sent = this.submitOutgoingMessage('', [imageUrl], {
					clearDraft: false,
					clearComposerImages: false,
					checkUploading: false,
					attachmentMode: 'expression',
					attachmentHint
				});
				if (sent && item && item.id) {
					this.touchLocalExpressionUsage(item.id);
				}
			},
			renameLocalExpression(item) {
				if (!item || !item.id || !item.imageUrl) return;
				this.openExpressionEditor(item.imageUrl, item);
			},
			removeLocalExpression(item) {
				if (!item || !item.id) return;
				uni.showModal({
					title: this.tx('expression_delete_title', '删除表情'),
					content: this.tx('expression_delete_desc', '删除后只能重新上传，确定继续吗？'),
					confirmText: this.tx('confirm', '确定'),
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res.confirm) return;
						if (!this.deleteLocalExpressionEntry(item.id)) {
							this.showErrorToast(this.tx('expression_delete_failed', '删除失败，请重试'));
							return;
						}
						uni.showToast({
							title: this.tx('expression_delete_success', '已删除'),
							icon: 'none'
						});
					}
				});
			},
			handleCommercialError(e, fallback, options) {
				const msg = this.jgErrMsg(e, fallback);
				const prompt = this.resolveCommercialPrompt(e);
				if (prompt) {
					this.openCommercialPrompt(prompt, msg);
					if (!(options && options.skipToastWhenPrompted)) {
						this.showErrorToast(msg);
					}
					return { message: msg, prompted: true };
				}
				if (!options || options.toast !== false) {
					this.showErrorToast(msg);
				}
				return { message: msg, prompted: false };
			},
			openCommercialPrompt(prompt, rawMessage) {
				const data = prompt || {};
				const isChatQuota = data.kind === 'chat_quota';
				this.commercialPrompt = {
					visible: true,
					kind: data.kind || '',
					title: isChatQuota ? '今日聊天次数已用完' : (data.title || this.tx('membership_title', '会员权益提示')),
					message: isChatQuota
						? '今天的官方模型聊天次数已经用完。你可以配置自己的 API Key 继续聊天，或联系作者获取协助。'
						: (data.message || rawMessage || this.tx('membership_message', '当前操作需要更高权益，请先开通会员或充值。')),
					primaryText: isChatQuota ? '配置 API Key' : (data.primaryText || this.chatUi.openVip),
					primaryUrl: isChatQuota ? '/pages/user/aiSettings' : (data.primaryUrl || '/pages/user/myvip'),
					secondaryText: isChatQuota ? '联系作者' : (data.secondaryText || this.chatUi.recharge),
					secondaryUrl: isChatQuota ? '/pages/user/lianxiwomen/lianxiwomen' : (data.secondaryUrl || '/pages/user/pay')
				};
			},
			closeCommercialPrompt() {
				this.commercialPrompt = {
					visible: false,
					kind: '',
					title: '',
					message: '',
					primaryText: '',
					primaryUrl: '',
					secondaryText: '',
					secondaryUrl: ''
				};
			},
			currentCharacterDisplayName() {
				return this.normalizeCharacterImageText(this.char && (this.char.nickname || this.char.name), 60);
			},
			appendTextToDraft(text, options) {
				const addition = String(text || '').trim();
				if (!addition) return '';
				const opts = options && typeof options === 'object' ? options : {};
				const base = opts.replace ? '' : String(this.draft || '');
				let next = base;
				if (next && !/[\s\n]$/.test(next)) {
					next += opts.separator != null ? String(opts.separator) : '\n';
				}
				next += addition;
				this.draft = next;
				this.scrollChatToBottom({ immediate: true });
				return next;
			},
			applyCharacterNameToDraft() {
				const name = this.currentCharacterDisplayName();
				if (!name) return;
				this.closeMessageActionSheet();
				if (typeof uni !== 'undefined' && typeof uni.setClipboardData === 'function') {
					try {
						uni.setClipboardData({
							data: name,
							showToast: false
						});
					} catch (e) {}
				}
				this.appendTextToDraft(name, { separator: ' ' });
				uni.showToast({
					title: this.tx('char_name_fill_success', '角色名已放到输入框'),
					icon: 'none'
				});
			},
			normalizeQuoteExcerpt(text, maxLength) {
				const limit = Number(maxLength) > 0 ? Number(maxLength) : MESSAGE_QUOTE_EXCERPT_MAX;
				return String(text || '')
					.replace(/\r\n/g, '\n')
					.replace(/\n+/g, ' ')
					.replace(/\s+/g, ' ')
					.trim()
					.slice(0, limit);
			},
			normalizeComposerQuoteMeta(raw) {
				const source = raw && typeof raw === 'object' ? raw : {};
				const speaker = this.normalizeCharacterImageText(source.speaker, 40);
				const text = this.normalizeQuoteExcerpt(source.text, MESSAGE_QUOTE_EXCERPT_MAX);
				if (!speaker || !text) return createComposerQuoteState();
				return {
					visible: true,
					messageId: this.normalizeDbMessageId(source.messageId),
					role: source.role === 'char' ? 'char' : source.role === 'user' ? 'user' : '',
					speaker,
					text
				};
			},
			messageQuoteSpeakerName(message) {
				if (message && message.role === 'user') {
					return '我';
				}
				return this.currentCharacterDisplayName() || '角色';
			},
			messageQuoteMeta(message) {
				if (!message || !message.quote) return null;
				const quote = this.normalizeComposerQuoteMeta(message.quote);
				return quote.visible ? quote : null;
			},
			buildComposerQuoteMetaFromMessage(message) {
				if (!message) return createComposerQuoteState();
				return this.normalizeComposerQuoteMeta({
					messageId: message.messageId || message.id,
					role: message.role,
					speaker: this.messageQuoteSpeakerName(message),
					text: message.text
				});
			},
			clearComposerQuote() {
				this.composerQuote = createComposerQuoteState();
			},
			serializeQuotedMessageText(bodyText, quoteMeta) {
				const quote = this.normalizeComposerQuoteMeta(quoteMeta);
				const body = String(bodyText || '').trim();
				if (!quote.visible) return body;
				const lines = [
					MESSAGE_QUOTE_OPEN_PREFIX + quote.speaker + ']',
					quote.text,
					MESSAGE_QUOTE_CLOSE_MARKER
				];
				if (body) {
					lines.push(body);
				}
				return lines.join('\n').trim();
			},
			extractQuotedMessagePayload(rawText, role) {
				const normalizedText = String(rawText || '').replace(/\r\n/g, '\n').trim();
				if (role !== 'user' || normalizedText.indexOf(MESSAGE_QUOTE_OPEN_PREFIX) !== 0) {
					return {
						text: normalizedText,
						quote: null
					};
				}
				const headerEnd = normalizedText.indexOf(']\n');
				if (headerEnd < 0) {
					return {
						text: normalizedText,
						quote: null
					};
				}
				const closeToken = '\n' + MESSAGE_QUOTE_CLOSE_MARKER;
				const closeIndex = normalizedText.indexOf(closeToken, headerEnd + 2);
				if (closeIndex < 0) {
					return {
						text: normalizedText,
						quote: null
					};
				}
				const speaker = normalizedText.slice(MESSAGE_QUOTE_OPEN_PREFIX.length, headerEnd).trim();
				const quoteText = normalizedText.slice(headerEnd + 2, closeIndex).trim();
				let body = normalizedText.slice(closeIndex + closeToken.length);
				if (body.indexOf('\n') === 0) {
					body = body.slice(1);
				}
				body = body.trim();
				const quote = this.normalizeComposerQuoteMeta({
					role,
					speaker,
					text: quoteText
				});
				if (!quote.visible) {
					return {
						text: normalizedText,
						quote: null
					};
				}
				return {
					text: body,
					quote
				};
			},
			resolveCharacterVoiceSheetMetrics() {
				const viewport = this.getMessageActionViewport();
				const pxPerRpx = viewport.width / 750;
				const safeAreaBottom = Number(viewport.safeAreaBottom) || 0;
				const maskTop = Math.round(32 * pxPerRpx);
				const maskBottom = Math.round(24 * pxPerRpx) + safeAreaBottom;
				const sheetInner = Math.round(60 * pxPerRpx);
				const headerHeight = Math.round(104 * pxPerRpx);
				const actionHeight = Math.round(110 * pxPerRpx);
				const gap = Math.round(24 * pxPerRpx);
				const sheetHeight = Math.max(420, Math.round(viewport.height - maskTop - maskBottom));
				const scrollHeight = Math.max(220, Math.round(sheetHeight - sheetInner - headerHeight - actionHeight - gap));
				return {
					sheetHeight,
					scrollHeight
				};
			},
			characterVoiceSheetInlineStyle() {
				const metrics = this.resolveCharacterVoiceSheetMetrics();
				return {
					height: metrics.sheetHeight + 'px',
					maxHeight: metrics.sheetHeight + 'px'
				};
			},
			characterVoiceScrollInlineStyle() {
				const metrics = this.resolveCharacterVoiceSheetMetrics();
				return {
					height: metrics.scrollHeight + 'px',
					maxHeight: metrics.scrollHeight + 'px'
				};
			},
			extractMessageActionPoint(event) {
				const source = event || {};
				const detail = source.detail || {};
				const point =
					(source.changedTouches && source.changedTouches[0]) ||
					(source.touches && source.touches[0]) ||
					(detail.changedTouches && detail.changedTouches[0]) ||
					(detail.touches && detail.touches[0]) ||
					detail ||
					source;
				if (!point) return null;
				const x = Number(
					point.clientX != null
						? point.clientX
						: point.pageX != null
							? point.pageX
							: point.x
				);
				const y = Number(
					point.clientY != null
						? point.clientY
						: point.pageY != null
							? point.pageY
							: point.y
				);
				if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
				return { x, y };
			},
			createMessageActionSyntheticEvent(point) {
				if (!point || !Number.isFinite(point.x) || !Number.isFinite(point.y)) {
					return {};
				}
				return {
					detail: {
						x: point.x,
						y: point.y
					},
					changedTouches: [
						{
							clientX: point.x,
							clientY: point.y,
							pageX: point.x,
							pageY: point.y,
							x: point.x,
							y: point.y
						}
					]
				};
			},
			clearMessageActionPressState() {
				if (this.messagePressState && this.messagePressState.timer) {
					clearTimeout(this.messagePressState.timer);
				}
				this.messagePressState = createMessagePressState();
			},
			startMessageActionPress(message, event) {
				if (!message) return;
				const text = String(message.text || '').trim();
				const canDelete = this.canBranchDeleteChatMessage(message);
				if (!text && !canDelete) return;
				const point = this.extractMessageActionPoint(event) || { x: 0, y: 0 };
				this.clearMessageActionPressState();
				const messageId = this.normalizeDbMessageId(message.id || message.messageId);
				const timer = setTimeout(() => {
					const pressState = this.messagePressState || {};
					if (pressState.messageId !== messageId || pressState.fired) {
						return;
					}
					this.messagePressState = Object.assign(createMessagePressState(), {
						fired: true,
						messageId,
						startX: pressState.startX,
						startY: pressState.startY
					});
					this.openMessageActionSheet(message, this.createMessageActionSyntheticEvent(point));
				}, MESSAGE_ACTION_LONG_PRESS_MS);
				this.messagePressState = {
					timer,
					fired: false,
					messageId,
					startX: point.x,
					startY: point.y
				};
			},
			moveMessageActionPress(event) {
				const pressState = this.messagePressState || {};
				if (!pressState.timer || pressState.fired) return;
				const point = this.extractMessageActionPoint(event);
				if (!point) return;
				const deltaX = Math.abs(Number(point.x) - Number(pressState.startX || 0));
				const deltaY = Math.abs(Number(point.y) - Number(pressState.startY || 0));
				if (deltaX > MESSAGE_ACTION_MOVE_THRESHOLD_PX || deltaY > MESSAGE_ACTION_MOVE_THRESHOLD_PX) {
					this.clearMessageActionPressState();
				}
			},
			endMessageActionPress() {
				this.clearMessageActionPressState();
			},
			getMessageActionViewport() {
				let width = 375;
				let height = 667;
				let safeAreaBottom = 0;
				try {
					if (typeof uni !== 'undefined' && typeof uni.getWindowInfo === 'function') {
						const info = uni.getWindowInfo() || {};
						width = Number(info.windowWidth) || width;
						height = Number(info.windowHeight) || height;
						safeAreaBottom = Number(info.safeAreaInsets && info.safeAreaInsets.bottom) || safeAreaBottom;
					} else if (typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function') {
						const info = uni.getSystemInfoSync() || {};
						width = Number(info.windowWidth) || width;
						height = Number(info.windowHeight) || height;
						safeAreaBottom = Number(info.safeAreaInsets && info.safeAreaInsets.bottom) || safeAreaBottom;
					} else if (typeof window !== 'undefined') {
						width = Number(window.innerWidth) || width;
						height = Number(window.innerHeight) || height;
					}
				} catch (e) {}
				return { width, height, safeAreaBottom };
			},
			resolveMessageActionMenuPosition(message, event, hasText, canDelete) {
				const point = this.extractMessageActionPoint(event);
				const viewport = this.getMessageActionViewport();
				const pxPerRpx = viewport.width / 750;
				const margin = 12;
				const offset = 10;
				const menuWidth = Math.max(118, Math.round(236 * pxPerRpx));
				const rowCount = (hasText ? 2 : 0) + (canDelete ? 1 : 0);
				const rowHeight = Math.max(45, Math.round(90 * pxPerRpx));
				const menuHeight = Math.max(rowHeight, rowCount * rowHeight + 2);
				let left = message && message.role === 'user'
					? viewport.width - menuWidth - 18
					: 18;
				let top = Math.max(margin, Math.round((viewport.height - menuHeight) / 2));
				if (point) {
					left = point.x + offset;
					top = point.y - 18;
					if (message && message.role === 'user') {
						left = point.x - menuWidth - offset;
					}
				}
				if (left + menuWidth > viewport.width - margin) {
					left = viewport.width - menuWidth - margin;
				}
				if (left < margin) {
					left = margin;
				}
				if (top + menuHeight > viewport.height - margin) {
					top = viewport.height - menuHeight - margin;
				}
				if (top < margin) {
					top = margin;
				}
				return {
					leftPx: Math.round(left),
					topPx: Math.round(top)
				};
			},
			canBranchDeleteChatMessage(message) {
				if (!this.jgOn || !message) return false;
				const id = this.normalizeDbMessageId(message.id);
				if (!id || id.indexOf('db_') !== 0) return false;
				return message.role === 'user' || message.role === 'char';
			},
			openMessageActionSheet(message, event) {
				if (!message) return;
				this.clearMessageActionPressState();
				const text = String(message.text || '').trim();
				const canDelete = this.canBranchDeleteChatMessage(message);
				if (!text && !canDelete) return;
				const position = this.resolveMessageActionMenuPosition(message, event, !!text, canDelete);
				this.messageActionSheet = {
					visible: true,
					messageId: this.normalizeDbMessageId(message.id),
					role: message.role || '',
					text,
					canDelete,
					deleting: false,
					leftPx: position.leftPx,
					topPx: position.topPx,
					imageUrls: Array.isArray(message.imageUrls) ? message.imageUrls.slice() : [],
					voiceUrl: this.normalizeVoiceMessageUrl(message.voiceUrl),
					voiceDurationMs: this.normalizeVoiceDurationMs(message.voiceDurationMs)
				};
			},
			closeMessageActionSheet(force) {
				if (this.messageActionSheet && this.messageActionSheet.deleting && force !== true) return;
				this.clearMessageActionPressState();
				this.messageActionSheet = createMessageActionSheetState();
			},
			copyMessageActionText() {
				if (this.messageActionSheet.deleting) return;
				const text = String(this.messageActionSheet.text || '').trim();
				if (!text) return;
				uni.setClipboardData({
					data: text,
					success: () => {
						this.closeMessageActionSheet();
						uni.showToast({
							title: this.tx('message_copy_success', '消息已复制'),
							icon: 'none'
						});
					},
					fail: () => {
						this.showErrorToast(this.tx('message_copy_failed', '复制失败，请重试'));
					}
				});
			},
			quoteMessageActionText() {
				if (this.messageActionSheet.deleting) return;
				const quote = this.buildComposerQuoteMetaFromMessage(this.messageActionSheet);
				if (!quote.visible) return;
				this.closeMessageActionSheet();
				this.composerQuote = quote;
				this.scrollChatToBottom({ immediate: true });
				uni.showToast({
					title: this.tx('message_quote_success', '已添加引用'),
					icon: 'none'
				});
			},
			deleteMessageBranch(messageId, options) {
				const id = this.normalizeDbMessageId(messageId);
				if (!id) return Promise.resolve(false);
				const opts = options && typeof options === 'object' ? options : {};
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				if (!cid) return Promise.resolve(false);
				if (this.messageActionSheet && this.messageActionSheet.visible && this.messageActionSheet.messageId === id) {
					this.$set(this.messageActionSheet, 'deleting', true);
				}
				return tavernApi
					.postTavernDeleteMessageBranch({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						messageId: id
					})
					.then(() => this.refreshJgMessages())
					.then(() => {
						this.scrollChatToBottom({ immediate: true });
						if (typeof opts.onDone === 'function') {
							opts.onDone();
						}
						return true;
					})
					.catch((e) => {
						if (opts.toastOnError !== false) {
							this.showErrorToast(this.jgErrMsg(e, this.tx('message_delete_failed', '删除失败，请重试')));
						}
						return false;
					})
					.finally(() => {
						if (this.messageActionSheet && this.messageActionSheet.messageId === id) {
							this.$set(this.messageActionSheet, 'deleting', false);
						}
					});
			},
			confirmDeleteMessageAction() {
				if (this.messageActionSheet.deleting) return;
				if (this.sending) {
					this.showErrorToast(this.tx('message_delete_wait', '等这轮回复完成后再删除'));
					return;
				}
				if (!this.messageActionSheet.canDelete || !this.messageActionSheet.messageId) {
					this.showErrorToast(this.tx('message_delete_unavailable', '这条消息暂时不能删除'));
					return;
				}
				uni.showModal({
					title: this.tx('message_delete_title', '删除这条消息？'),
					content: this.tx('message_delete_desc', '删除后会回到这条消息之前的时间线，这条消息以及后面的内容都会一起移除。'),
					confirmText: this.tx('delete', '删除'),
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res || !res.confirm) return;
						const deletingMessageId = this.messageActionSheet.messageId;
						this.closeMessageActionSheet(true);
						this.deleteMessageBranch(deletingMessageId, {
							onDone: () => {
								uni.showToast({
									title: this.tx('message_delete_success', '已回到删除前的时间线'),
									icon: 'none'
								});
							}
						});
					}
				});
			},
			openCharImagePreview() {
				if (!this.charPreviewImage) return;
				this.charImagePreviewVisible = true;
			},
			closeCharImagePreview() {
				this.charImagePreviewVisible = false;
			},
			goCommercial(url) {
				const target = String(url || '').trim();
				this.closeCommercialPrompt();
				if (!target) return;
				uni.navigateTo({
					url: target,
					fail: () => {
						uni.switchTab({ url: '/pages/user/user' });
					}
				});
			},
			goAiSettings() {
				uni.navigateTo({
					url: '/pages/user/aiSettings',
					fail: () => {
						uni.switchTab({ url: '/pages/user/user' });
					}
				});
			},
			formatMemoryTime(iso) {
				if (!iso) {
					return '';
				}
				try {
					const d = new Date(iso);
					if (isNaN(d.getTime())) {
						return String(iso).slice(0, 16);
					}
					const now = Date.now();
					const diff = now - d.getTime();
					if (diff < 60000) {
						return this.tx('memory_just_now', '刚刚');
					}
					if (diff < 3600000) {
						return formatLocaleText(this.tx('memory_minutes_ago', '{count}分钟前'), {
							count: Math.floor(diff / 60000)
						});
					}
					if (diff < 86400000) {
						return formatLocaleText(this.tx('memory_hours_ago', '{count}小时前'), {
							count: Math.floor(diff / 3600000)
						});
					}
					const mm = d.getMinutes();
					const mp = mm < 10 ? '0' + mm : '' + mm;
					return d.getMonth() + 1 + '/' + d.getDate() + ' ' + d.getHours() + ':' + mp;
				} catch (err) {
					return String(iso).slice(0, 16);
				}
			},
			normalizeMessagesEnvelope(pack) {
				var rows = [];
				var mem = null;
				var meta = null;
				var conversationId = '';
				var page = null;
				if (Array.isArray(pack)) {
					rows = pack;
				} else if (pack && typeof pack === 'object') {
					rows = Array.isArray(pack.messages) ? pack.messages : [];
					mem = pack.memory != null ? pack.memory : null;
					meta = pack.tavernMeta != null ? pack.tavernMeta : null;
					conversationId = pack.conversationId != null ? pack.conversationId : '';
					page = pack.page && typeof pack.page === 'object' ? pack.page : null;
				}
				return {
					rows,
					mem,
					meta,
					conversationId,
					page
				};
			},
			syncMessageHistoryPageState(page) {
				var nextPage = page && typeof page === 'object' ? page : {};
				this.messageHistoryHasMore = !!nextPage.hasMore;
				this.messageHistoryNextBeforeId = this.normalizeDbMessageId(nextPage.nextBeforeMessageId);
			},
			mergePrependedHistoryRows(olderRows, currentRows) {
				var seen = {};
				var merged = [];
				(olderRows || []).concat(currentRows || []).forEach((row, index) => {
					if (!row) return;
					var key = this.normalizeDbMessageId(row.id);
					if (!key) {
						key = 'row_' + index + '_' + String(row.role || '') + '_' + String(row.text || '');
					}
					if (seen[key]) return;
					seen[key] = true;
					merged.push(row);
				});
				return merged;
			},
			restoreChatViewportAtMessage(messageId) {
				var safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return;
				if (this.chatAnimationTimer) {
					clearTimeout(this.chatAnimationTimer);
					this.chatAnimationTimer = null;
				}
				this.followBottom = false;
				this.atChatBottom = false;
				this.markChatAutoScroll();
				this.chatScrollWithAnimation = false;
				this.scrollTo = '';
				this.$nextTick(() => {
					this.markChatAutoScroll();
					this.scrollTo = 'm-' + safeId;
					this.chatAnimationTimer = setTimeout(() => {
						this.chatScrollWithAnimation = true;
						this.chatAnimationTimer = null;
					}, 48);
				});
			},
			applyMessagesEnvelope(pack, options) {
				const opts = options && typeof options === 'object' ? options : {};
				const envelope = this.normalizeMessagesEnvelope(pack);
				const normalizedRows = envelope.rows.map((row) => this.normalizeChatRow(row));
				this.syncLocalChatConversationId(envelope.conversationId);
				if (opts.prependHistory) {
					const currentRows = Array.isArray(this.messages) ? this.messages.slice() : [];
					this.messages = this.mergeLocalChatImagesIntoRows(
						this.mergePrependedHistoryRows(normalizedRows, currentRows)
					);
				} else {
					this.messages = this.mergeLocalChatImagesIntoRows(normalizedRows);
				}
				if (this.streamingAssistantMessageId) {
					const exists = this.messages.some(
						(row) => this.normalizeDbMessageId(row && row.id) === this.streamingAssistantMessageId
					);
					if (!exists) {
						this.finishAssistantStreaming(this.streamingAssistantMessageId);
					}
				}
				this.syncUserVoiceEntries();
				this.jgMemory = envelope.mem;
				this.jgTavernMeta = envelope.meta;
				this.syncAssistantVoiceEntries();
				this.syncMessageHistoryPageState(envelope.page);
				if (opts.invalidateReplySuggestions !== false) {
					this.invalidateReplySuggestions();
				}
			},
			clearJgLoadRetryTimer() {
				if (this.jgLoadRetryTimer) {
					clearTimeout(this.jgLoadRetryTimer);
					this.jgLoadRetryTimer = null;
				}
			},
			scheduleJgLoadAutoRetry(error) {
				if (this.jgLoadAutoRetried || !this.jgOn || !this.cid) {
					return false;
				}
				if (error && error.__stale) {
					return false;
				}
				this.jgLoadAutoRetried = true;
				this.clearJgLoadRetryTimer();
				this.jgChatLoadState = 'loading';
				this.jgChatErrorMsg = '';
				this.jgLoadRetryTimer = setTimeout(() => {
					this.jgLoadRetryTimer = null;
					if (!this.jgOn || !this.cid || this.jgChatLoadState === 'ready') {
						return;
					}
					this.loadJgSession({
						autoRetry: false,
						keepAutoRetryState: true
					});
				}, 420);
				return true;
			},
			maybeRecoverJgSessionOnShow() {
				if (!this.jgOn || !this.cid) return;
				if (this.jgChatLoadState === 'loading' || this.jgLoadRetryTimer) return;
				if (this.jgChatLoadState === 'error' || this.jgChatLoadState === 'idle') {
					this.loadJgSession({
						autoRetry: false,
						keepAutoRetryState: true
					});
				}
			},
			loadJgSession(options) {
				const opts = options && typeof options === 'object' ? options : {};
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) return;
				const requestToken = Date.now() + Math.random();
				this.jgLoadRequestToken = requestToken;
				if (!opts.keepAutoRetryState) {
					this.jgLoadAutoRetried = false;
				}
				this.clearJgLoadRetryTimer();
				this.clearChatUiTimers();
				this.resetConversationVoiceRuntimeState();
				this.jgChatLoadState = 'loading';
				this.jgChatErrorMsg = '';
				this.jgMemory = null;
				this.jgTavernMeta = null;
				this.messageHistoryHasMore = false;
				this.messageHistoryLoading = false;
				this.messageHistoryNextBeforeId = '';
				this.messageHistoryLoadAt = 0;
				this.chatViewportReady = false;
				this.chatScrollWithAnimation = false;
				this.followBottom = true;
				this.atChatBottom = true;
				this.lastChatScrollTop = 0;
				this.scrollTo = '';
				tavernApi
					.fetchCharacter(this.cid)
					.then((c) => {
						if (this.jgLoadRequestToken !== requestToken) {
							return Promise.reject({ __stale: true });
						}
						if (!c) {
							throw new Error(this.tx('character_missing', '角色不存在或已下架'));
						}
						this.char = c;
						this.refreshCharacterVoiceConfig(c && c.id);
						this.refreshCharacterImageConfig(c && c.id);
						this.syncLocalChatConversationId(c && (c.conversationId != null ? c.conversationId : c.conversation_id));
						this.applyVipGate();
						if (!this.char || this.char.unlocked === false) {
							return Promise.reject(new Error('vip'));
						}
						return tavernApi.fetchTavernMessages(this.cid, tavernApi.getClientUid(), {
							limit: TAVERN_MESSAGES_INITIAL_LIMIT
						});
					})
					.then((pack) => {
						if (this.jgLoadRequestToken !== requestToken) {
							return;
						}
						this.applyMessagesEnvelope(pack);
						this.hydrateStoredDraft();
						this.followBottom = true;
						this.atChatBottom = true;
						this.jgChatLoadState = 'ready';
						this.jgLoadAutoRetried = false;
						this.scrollChatToBottom({ immediate: true, reveal: true });
					})
					.catch((e) => {
						if ((e && e.__stale) || this.jgLoadRequestToken !== requestToken) return;
						if (e && e.message === 'vip') return;
						if (opts.autoRetry !== false && this.scheduleJgLoadAutoRetry(e)) {
							return;
						}
						this.jgChatLoadState = 'error';
						this.jgChatErrorMsg = this.jgErrMsg(
							e,
							this.tx('chat_load_failed', '网络异常，请重试')
						);
					});
			},
			retryJgChatLoad() {
				this.loadJgSession();
			},
			showHistoryLoadBanner() {
				return !!this.jgOn && this.jgChatLoadState === 'ready' && (this.messageHistoryLoading || this.messageHistoryHasMore);
			},
			historyLoadBannerText() {
				if (this.messageHistoryLoading) {
					return this.tx('chat_history_loading_more', '正在加载更早消息...');
				}
				if (this.messageHistoryHasMore) {
					return this.tx('chat_history_load_more', '上滑加载更早消息');
				}
				return '';
			},
			maybeLoadOlderMessages(reason) {
				if (!this.jgOn || this.jgChatLoadState !== 'ready' || !this.messageHistoryHasMore) {
					return;
				}
				if (this.messageHistoryLoading) {
					return;
				}
				if (Date.now() - Number(this.messageHistoryLoadAt || 0) < 260) {
					return;
				}
				this.loadOlderMessages(reason);
			},
			loadOlderMessages(reason, attempt) {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled() || !this.cid || !this.messageHistoryHasMore || this.messageHistoryLoading) {
					return Promise.resolve(false);
				}
				const anchorMessageId = this.messages && this.messages.length ? this.normalizeDbMessageId(this.messages[0].id) : '';
				const beforeMessageId = this.normalizeDbMessageId(this.messageHistoryNextBeforeId);
				if (!beforeMessageId) {
					this.messageHistoryHasMore = false;
					return Promise.resolve(false);
				}
				const requestToken = this.jgLoadRequestToken;
				const previousCount = Array.isArray(this.messages) ? this.messages.length : 0;
				const safeAttempt = Number.isFinite(Number(attempt)) ? Number(attempt) : 0;
				this.messageHistoryLoading = true;
				this.messageHistoryLoadAt = Date.now();
				return tavernApi
					.fetchTavernMessages(this.cid, tavernApi.getClientUid(), {
						beforeMessageId,
						limit: TAVERN_MESSAGES_HISTORY_LIMIT
					})
					.then((pack) => {
						if (this.jgLoadRequestToken !== requestToken) {
							return false;
						}
						this.applyMessagesEnvelope(pack, {
							prependHistory: true,
							invalidateReplySuggestions: false
						});
						if (anchorMessageId) {
							this.restoreChatViewportAtMessage(anchorMessageId);
						}
						const nextCount = Array.isArray(this.messages) ? this.messages.length : 0;
						if (nextCount <= previousCount && this.messageHistoryHasMore && safeAttempt < 2) {
							this.messageHistoryLoading = false;
							return this.loadOlderMessages(reason, safeAttempt + 1);
						}
						return nextCount > previousCount;
					})
					.catch((e) => {
						if (this.jgLoadRequestToken !== requestToken) {
							return false;
						}
						this.showErrorToast(this.jgErrMsg(e, this.tx('chat_history_load_failed', '加载更早消息失败')));
						return false;
					})
					.finally(() => {
						if (this.jgLoadRequestToken === requestToken) {
							this.messageHistoryLoading = false;
							this.messageHistoryLoadAt = Date.now();
						}
					});
			},
			refreshUserAvatar() {
				const fallback = '/static/logo.png';
				try {
					const direct = this.normalizeUserAvatarSource(this.pickUserAvatarCandidate(uni.getStorageSync('user')));
					if (direct) {
						this.userAvatar = direct;
						return;
					}
				} catch (e) {}
				try {
					const storeUser =
						this.$store && this.$store.state && this.$store.state.user
							? this.$store.state.user
							: null;
					const fromStore = this.normalizeUserAvatarSource(this.pickUserAvatarCandidate(storeUser));
					if (fromStore) {
						this.userAvatar = fromStore;
						return;
					}
				} catch (e) {}
				if (!String(this.userAvatar || '').trim()) {
					this.userAvatar = fallback;
				}
			},
			pickUserAvatarCandidate(source) {
				if (!source || typeof source !== 'object') return '';
				const candidates = [
					source.avatar,
					source.avatarUrl,
					source.avatar_url,
					source.headimg,
					source.headImg,
					source.headImage,
					source.portrait,
					source.profile && source.profile.avatar,
					source.profile && source.profile.avatarUrl
				];
				for (let i = 0; i < candidates.length; i += 1) {
					const raw = candidates[i];
					if (raw == null) continue;
					const value = String(raw).trim();
					if (value) return value;
				}
				return '';
			},
			normalizeUserAvatarSource(raw) {
				const value = raw == null ? '' : String(raw).trim();
				if (!value) return '';
				if (
					value.indexOf('http://') === 0 ||
					value.indexOf('https://') === 0 ||
					value.indexOf('/') === 0 ||
					value.indexOf('data:') === 0 ||
					value.indexOf('file://') === 0 ||
					value.indexOf('blob:') === 0
				) {
					return value;
				}
				if (typeof this.$getimgsrc === 'function') {
					const resolved = this.$getimgsrc(value);
					if (resolved != null && String(resolved).trim() !== '') {
						return String(resolved).trim();
					}
				}
				return value;
			},
			handleUserAvatarError() {
				this.userAvatar = '/static/logo.png';
			},
			resolveLocalChatConversationId(candidate) {
				const direct = candidate == null ? '' : String(candidate).trim();
				if (direct) return direct;
				const current = this.jgConversationId == null ? '' : String(this.jgConversationId).trim();
				if (current) return current;
				const charConversationId = this.char
					? this.char.conversationId != null && this.char.conversationId !== ''
						? this.char.conversationId
						: this.char.conversation_id
					: '';
				const charId = charConversationId == null ? '' : String(charConversationId).trim();
				if (charId) return charId;
				const fallback = this.cid == null ? '' : String(this.cid).trim();
				return fallback ? 'char_' + fallback : '';
			},
			syncLocalChatConversationId(candidate) {
				const current = this.jgConversationId == null ? '' : String(this.jgConversationId).trim();
				const next = this.resolveLocalChatConversationId(candidate);
				if (!next) return '';
				if (current && current !== next) {
					this.migrateLocalUserVoiceCache(current, next);
					this.migrateLocalChatImageCache(current, next);
				}
				this.jgConversationId = next;
				return next;
			},
			legacyLocalChatImageStorageKey(conversationId) {
				const safeConversationId = this.resolveLocalChatConversationId(conversationId);
				return safeConversationId ? LOCAL_CHAT_IMAGE_CACHE_PREFIX + safeConversationId : '';
			},
			localChatImageStorageKey(conversationId, viewerKey) {
				const safeConversationId = this.resolveLocalChatConversationId(conversationId);
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				return safeViewerKey && safeConversationId ? LOCAL_CHAT_IMAGE_CACHE_PREFIX + safeViewerKey + '_' + safeConversationId : '';
			},
			clearLegacyLocalChatImageEntries(conversationId) {
				const legacyKey = this.legacyLocalChatImageStorageKey(conversationId);
				const nextKey = this.localChatImageStorageKey(conversationId);
				if (!legacyKey || legacyKey === nextKey) return;
				try {
					uni.removeStorageSync(legacyKey);
				} catch (e) {}
			},
			buildLocalChatUserSignature(text) {
				return String(text || '')
					.replace(/\s+/g, ' ')
					.trim();
			},
			normalizeLocalChatImageEntry(entry) {
				if (!entry || typeof entry !== 'object') return null;
				const messageId = this.normalizeDbMessageId(entry.messageId);
				const assistantMessageId = this.normalizeDbMessageId(entry.assistantMessageId);
				const imageUrls = this.normalizeChatImageUrls(entry.imageUrls);
				if (!imageUrls.length) return null;
				if (
					imageUrls.some(
						(item) =>
							this.isLocalInlineImageUrl(item) &&
							String(item == null ? '' : item).trim().length > LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH
					)
				) {
					return null;
				}
				const text = String(entry.text || '');
				const role = String(entry.role || '').trim() === 'char' ? 'char' : 'user';
				const kind = this.normalizeCharacterImageText(entry.kind, 40);
				const prompt = role === 'char' ? this.normalizeCharacterImageText(entry.prompt, 300) : '';
				const aspectRatio = role === 'char' ? this.normalizeCharacterImageAspectRatio(entry.aspectRatio) : 'portrait';
				const createdAtRaw = Number(entry.createdAt);
				const updatedAtRaw = Number(entry.updatedAt != null ? entry.updatedAt : createdAtRaw);
				const now = Date.now();
				const createdAt = isFinite(createdAtRaw) && createdAtRaw > 0 ? createdAtRaw : now;
				const updatedAt = isFinite(updatedAtRaw) && updatedAtRaw > 0 ? updatedAtRaw : createdAt;
				return {
					messageId: messageId || (assistantMessageId ? 'local_' + assistantMessageId : ''),
					assistantMessageId: assistantMessageId && assistantMessageId.startsWith('db_') ? assistantMessageId : '',
					text,
					signature: role === 'user' ? this.buildLocalChatUserSignature(text) : '',
					imageUrls,
					role,
					kind,
					prompt,
					aspectRatio,
					createdAt,
					updatedAt
				};
			},
			calcLocalChatImageEntrySize(entry) {
				if (!entry || !Array.isArray(entry.imageUrls)) return 0;
				return entry.imageUrls.reduce((total, item) => total + String(item == null ? '' : item).trim().length, 0);
			},
			capLocalChatImageEntriesByStorageBudget(entries) {
				const source = (Array.isArray(entries) ? entries : []).slice().sort((a, b) => a.createdAt - b.createdAt);
				const kept = [];
				let totalSize = 0;
				for (let i = source.length - 1; i >= 0; i--) {
					const entry = source[i];
					if (!entry) continue;
					const entrySize = this.calcLocalChatImageEntrySize(entry);
					if (!kept.length || totalSize + entrySize <= LOCAL_CHAT_IMAGE_CACHE_MAX_TOTAL_LENGTH) {
						kept.unshift(entry);
						totalSize += entrySize;
					}
				}
				return kept;
			},
			readLocalChatImageEntries(conversationId) {
				const key = this.localChatImageStorageKey(conversationId);
				this.clearLegacyLocalChatImageEntries(conversationId);
				if (!key) return [];
				try {
					const raw = uni.getStorageSync(key);
					const source =
						raw && typeof raw === 'object' && Array.isArray(raw.entries)
							? raw.entries
							: Array.isArray(raw)
								? raw
								: [];
					const now = Date.now();
					const entries = source
						.map((item) => this.normalizeLocalChatImageEntry(item))
						.filter((item) => item && now - item.updatedAt <= LOCAL_CHAT_IMAGE_CACHE_TTL_MS)
						.sort((a, b) => a.createdAt - b.createdAt);
					const cappedEntries = this.capLocalChatImageEntriesByStorageBudget(entries);
					if (
						(raw && typeof raw === 'object' && raw.version !== LOCAL_CHAT_IMAGE_CACHE_VERSION) ||
						entries.length !== source.length ||
						cappedEntries.length !== entries.length
					) {
						this.writeLocalChatImageEntries(cappedEntries, conversationId);
					}
					return cappedEntries;
				} catch (e) {
					return [];
				}
			},
			writeLocalChatImageEntries(entries, conversationId) {
				const key = this.localChatImageStorageKey(conversationId);
				this.clearLegacyLocalChatImageEntries(conversationId);
				if (!key) return;
				const normalized = (Array.isArray(entries) ? entries : [])
					.map((item) => this.normalizeLocalChatImageEntry(item))
					.filter(Boolean)
					.sort((a, b) => a.createdAt - b.createdAt);
				try {
					if (!normalized.length) {
						uni.removeStorageSync(key);
						return;
					}
					const countCapped = normalized.slice(Math.max(0, normalized.length - LOCAL_CHAT_IMAGE_CACHE_LIMIT));
					const capped = this.capLocalChatImageEntriesByStorageBudget(countCapped);
					uni.setStorageSync(key, {
						version: LOCAL_CHAT_IMAGE_CACHE_VERSION,
						updatedAt: Date.now(),
						entries: capped.map((item) => ({
							messageId: item.messageId,
							assistantMessageId: item.assistantMessageId,
							text: item.text,
							imageUrls: item.imageUrls,
							role: item.role,
							kind: item.kind,
							prompt: item.prompt,
							aspectRatio: item.aspectRatio,
							createdAt: item.createdAt,
							updatedAt: item.updatedAt
						}))
					});
				} catch (e) {}
			},
			migrateLocalChatImageCache(fromConversationId, toConversationId) {
				const fromKey = this.localChatImageStorageKey(fromConversationId);
				const toKey = this.localChatImageStorageKey(toConversationId);
				if (!fromKey || !toKey || fromKey === toKey) return;
				try {
					const current = uni.getStorageSync(toKey);
					const hasCurrent =
						Array.isArray(current) ||
						(current && typeof current === 'object' && Array.isArray(current.entries));
					if (hasCurrent) {
						uni.removeStorageSync(fromKey);
						return;
					}
					const source = uni.getStorageSync(fromKey);
					const hasSource =
						Array.isArray(source) ||
						(source && typeof source === 'object' && Array.isArray(source.entries));
					if (!hasSource) return;
					uni.setStorageSync(toKey, source);
					uni.removeStorageSync(fromKey);
				} catch (e) {}
			},
			upsertLocalChatImageEntry(entry, conversationId) {
				const normalized = this.normalizeLocalChatImageEntry(entry);
				if (!normalized) return null;
				const entries = this.readLocalChatImageEntries(conversationId);
				const next = Object.assign({}, normalized, {
					updatedAt: Date.now()
				});
				let index = entries.findIndex((item) => item.messageId === next.messageId);
				if (index < 0 && next.role === 'user' && next.assistantMessageId) {
					index = entries.findIndex(
						(item) => item.assistantMessageId === next.assistantMessageId && item.signature === next.signature
					);
				}
				if (index >= 0) {
					next.createdAt = entries[index].createdAt || next.createdAt;
					entries.splice(index, 1, Object.assign({}, entries[index], next));
				} else {
					entries.push(next);
				}
				this.writeLocalChatImageEntries(entries, conversationId);
				return next;
			},
			updateLocalChatImageEntryIds(fromMessageId, toMessageId, assistantMessageId, conversationId) {
				const fromId = this.normalizeDbMessageId(fromMessageId);
				const nextId = this.normalizeDbMessageId(toMessageId) || fromId;
				const nextAssistantId = this.normalizeDbMessageId(assistantMessageId);
				const entries = this.readLocalChatImageEntries(conversationId);
				let index = entries.findIndex((item) => item.messageId === fromId);
				if (index < 0 && nextId) {
					index = entries.findIndex((item) => item.messageId === nextId);
				}
				if (index < 0) return '';
				const current = Object.assign({}, entries[index], {
					messageId: nextId || entries[index].messageId,
					assistantMessageId: nextAssistantId && nextAssistantId.startsWith('db_') ? nextAssistantId : entries[index].assistantMessageId,
					updatedAt: Date.now()
				});
				entries.splice(index, 1);
				const mergedIndex = entries.findIndex((item) => item.messageId === current.messageId);
				if (mergedIndex >= 0) {
					const merged = Object.assign({}, entries[mergedIndex], current, {
						createdAt: Math.min(entries[mergedIndex].createdAt || current.createdAt, current.createdAt)
					});
					if (!merged.assistantMessageId) {
						merged.assistantMessageId = entries[mergedIndex].assistantMessageId || '';
					}
					if (!merged.text) {
						merged.text = entries[mergedIndex].text || '';
						merged.signature = this.buildLocalChatUserSignature(merged.text);
					}
					if (!merged.imageUrls || !merged.imageUrls.length) {
						merged.imageUrls = entries[mergedIndex].imageUrls || [];
					}
					entries.splice(mergedIndex, 1, merged);
				} else {
					entries.push(current);
				}
				this.writeLocalChatImageEntries(entries, conversationId);
				return current.messageId;
			},
			findUniqueLocalChatImageEntryBySignature(entries, signature) {
				if (!signature) return null;
				const matches = (Array.isArray(entries) ? entries : []).filter(
					(item) => item && !item._used && item.signature === signature
				);
				return matches.length === 1 ? matches[0] : null;
			},
			buildInjectedLocalChatRow(entry) {
				const fallbackId =
					entry && entry.assistantMessageId
						? 'local_user_before_' + entry.assistantMessageId
						: 'local_user_' + Date.now();
				return this.normalizeChatRow({
					id: entry && entry.messageId ? entry.messageId : fallbackId,
					role: entry && entry.role === 'char' ? 'char' : 'user',
					text: entry && entry.text ? entry.text : '',
					imageUrls: entry && entry.imageUrls ? entry.imageUrls : [],
					localKind: entry && entry.kind ? entry.kind : '',
					localPrompt: entry && entry.prompt ? entry.prompt : '',
					localOnly: true
				});
			},
			appendRuntimeLocalChatImageEntry(entry) {
				const normalized = this.normalizeLocalChatImageEntry(entry);
				if (!normalized) return null;
				const row = this.buildInjectedLocalChatRow(normalized);
				const safeRows = Array.isArray(this.messages) ? this.messages.slice() : [];
				const anchorId = normalized.assistantMessageId;
				const anchorIndex = anchorId ? safeRows.findIndex((item) => item && item.id === anchorId) : -1;
				if (anchorIndex >= 0) {
					const insertAt = normalized.role === 'char' ? anchorIndex + 1 : anchorIndex;
					safeRows.splice(insertAt, 0, row);
				} else {
					safeRows.push(row);
				}
				this.messages = safeRows;
				return row;
			},
			pruneLocalChatImageEntries(entries, rows) {
				const safeRows = Array.isArray(rows) ? rows : [];
				const rowIds = {};
				safeRows.forEach((row) => {
					if (row && row.id) {
						rowIds[row.id] = true;
					}
				});
				const now = Date.now();
				return (Array.isArray(entries) ? entries : []).filter((entry) => {
					if (!entry || !entry.imageUrls || !entry.imageUrls.length) return false;
					if (entry._used) return true;
					if (entry.messageId && rowIds[entry.messageId]) return true;
					if (entry.assistantMessageId && rowIds[entry.assistantMessageId]) return true;
					if (entry.role === 'char') {
						return now - entry.updatedAt <= LOCAL_CHAT_IMAGE_CACHE_TTL_MS;
					}
					return String(entry.messageId || '').indexOf('u_') === 0 && now - entry.updatedAt <= LOCAL_CHAT_IMAGE_PENDING_KEEP_MS;
				});
			},
			mergeLocalChatImagesIntoRows(rows) {
				const entries = this.readLocalChatImageEntries();
				const safeRows = Array.isArray(rows) ? rows : [];
				if (!entries.length) {
					return safeRows;
				}
				const nextRows = [];
				safeRows.forEach((row) => {
					if (!row) return;
					entries.forEach((entry) => {
						if (
							!entry ||
							entry._used ||
							entry.role !== 'user' ||
							!entry.assistantMessageId ||
							entry.assistantMessageId !== row.id
						) {
							return;
						}
						nextRows.push(this.buildInjectedLocalChatRow(entry));
						entry._used = true;
					});
					let mergedRow = row;
					let matchedEntry = null;
					if (row.id) {
						matchedEntry = entries.find((item) => item && !item._used && item.messageId === row.id) || null;
					}
					if (!matchedEntry && row.role === 'user') {
						matchedEntry = this.findUniqueLocalChatImageEntryBySignature(
							entries,
							this.buildLocalChatUserSignature(row.text)
						);
						if (!matchedEntry && !this.buildLocalChatUserSignature(row.text)) {
							const blankMatches = entries.filter(
								(item) => item && !item._used && !item.signature && !item.assistantMessageId
							);
							matchedEntry = blankMatches.length === 1 ? blankMatches[0] : null;
						}
						if (matchedEntry && row.id) {
							matchedEntry.messageId = row.id;
							matchedEntry.updatedAt = Date.now();
						}
					}
					if (matchedEntry && matchedEntry.imageUrls && matchedEntry.imageUrls.length) {
						mergedRow = Object.assign({}, row, {
							imageUrls:
								row.imageUrls && row.imageUrls.length ? row.imageUrls.slice() : matchedEntry.imageUrls.slice()
						});
						matchedEntry._used = true;
					}
					nextRows.push(mergedRow);
					entries.forEach((entry) => {
						if (
							!entry ||
							entry._used ||
							entry.role !== 'char' ||
							!entry.assistantMessageId ||
							entry.assistantMessageId !== row.id
						) {
							return;
						}
						nextRows.push(this.buildInjectedLocalChatRow(entry));
						entry._used = true;
					});
				});
				entries.forEach((entry) => {
					if (!entry || entry._used || entry.role !== 'char') {
						return;
					}
					nextRows.push(this.buildInjectedLocalChatRow(entry));
					entry._used = true;
				});
				this.writeLocalChatImageEntries(this.pruneLocalChatImageEntries(entries, nextRows));
				return nextRows;
			},
			clearLocalChatImageEntries(conversationId) {
				const key = this.localChatImageStorageKey(conversationId);
				this.clearLegacyLocalChatImageEntries(conversationId);
				if (!key) return;
				try {
					uni.removeStorageSync(key);
				} catch (e) {}
			},
			localAssistantVoicePreferenceKey(viewerKey) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				return safeViewerKey ? LOCAL_ASSISTANT_VOICE_PREF_PREFIX + safeViewerKey : '';
			},
			readAssistantVoiceAutoPreference(viewerKey) {
				const key = this.localAssistantVoicePreferenceKey(viewerKey);
				if (!key) return false;
				try {
					const raw = uni.getStorageSync(key);
					if (raw && typeof raw === 'object' && raw.enabled != null) {
						return raw.enabled === true;
					}
					if (typeof raw === 'boolean') {
						return raw === true;
					}
				} catch (e) {}
				return false;
			},
			writeAssistantVoiceAutoPreference(enabled, viewerKey) {
				const key = this.localAssistantVoicePreferenceKey(viewerKey);
				if (!key) return false;
				try {
					uni.setStorageSync(key, {
						enabled: enabled !== false,
						updatedAt: Date.now()
					});
					return true;
				} catch (e) {
					return false;
				}
			},
			refreshAssistantVoiceAutoPreference() {
				if (!this.isVoiceFeatureEnabledGlobal()) {
					this.assistantVoiceAutoEnabled = false;
					return false;
				}
				this.assistantVoiceAutoEnabled = this.readAssistantVoiceAutoPreference() !== false;
				return this.assistantVoiceAutoEnabled;
			},
			canUseBrowserAssistantVoiceAutoplay() {
				return !this.isAppPlus
					&& typeof window !== 'undefined'
					&& typeof document !== 'undefined'
					&& typeof Audio === 'function';
			},
			initAssistantVoiceBrowserUnlockTracking() {
				if (!this.canUseBrowserAssistantVoiceAutoplay()) {
					return;
				}
				if (this._assistantVoiceBrowserUnlockHandler) {
					return;
				}
				const handler = () => {
					this.tryUnlockAssistantVoiceBrowserPlayback({
						resumePending: true,
						silent: true
					});
				};
				this._assistantVoiceBrowserUnlockHandler = handler;
				['pointerdown', 'touchend', 'click', 'keydown'].forEach((eventName) => {
					try {
						window.addEventListener(eventName, handler, {
							capture: true,
							passive: true
						});
					} catch (e) {
						try {
							window.addEventListener(eventName, handler, true);
						} catch (err) {}
					}
				});
			},
			disposeAssistantVoiceBrowserUnlockTracking() {
				const handler = this._assistantVoiceBrowserUnlockHandler;
				if (!handler || typeof window === 'undefined') {
					this._assistantVoiceBrowserUnlockHandler = null;
					return;
				}
				['pointerdown', 'touchend', 'click', 'keydown'].forEach((eventName) => {
					try {
						window.removeEventListener(eventName, handler, true);
					} catch (e) {}
				});
				this._assistantVoiceBrowserUnlockHandler = null;
			},
			tryUnlockAssistantVoiceBrowserPlayback(options) {
				const opts = options && typeof options === 'object' ? options : {};
				if (!this.canUseBrowserAssistantVoiceAutoplay()) {
					return Promise.resolve(true);
				}
				if (this.assistantVoiceBrowserUnlocked) {
					if (opts.resumePending !== false) {
						this.resumePendingAssistantVoiceAutoplay();
					}
					return Promise.resolve(true);
				}
				if (this.assistantVoiceBrowserUnlocking) {
					return Promise.resolve(false);
				}
				const player = this.getAssistantVoicePlayer();
				if (!player || typeof player.prime !== 'function') {
					return Promise.resolve(false);
				}
				this.assistantVoiceBrowserUnlocking = true;
				return Promise.resolve(player.prime())
					.then(() => {
						this.assistantVoiceBrowserUnlocking = false;
						this.assistantVoiceBrowserUnlocked = true;
						this.assistantVoiceAutoplayHintShown = false;
						if (opts.resumePending !== false) {
							this.resumePendingAssistantVoiceAutoplay();
						}
						return true;
					})
					.catch(() => {
						this.assistantVoiceBrowserUnlocking = false;
						return false;
					});
			},
			shouldAutoPrepareAssistantVoice() {
				return this.isVoiceFeatureEnabledGlobal()
					&& !!this.assistantVoiceAutoEnabled
					&& this.isCharacterVoiceEnabled()
					&& this.isCharacterVoiceAutoPlayEnabled();
			},
			shouldAutoPlayAssistantVoice() {
				return this.isVoiceFeatureEnabledGlobal()
					&& !!this.assistantVoiceAutoEnabled
					&& this.isCharacterVoiceEnabled()
					&& this.isCharacterVoiceAutoPlayEnabled()
					&& (this.isAppPlus || this.assistantVoiceBrowserUnlocked === true);
			},
			toggleAssistantVoiceAuto() {
				if (!this.isVoiceFeatureEnabledGlobal()) return;
				const next = !this.assistantVoiceAutoEnabled;
				this.assistantVoiceAutoEnabled = next;
				this.writeAssistantVoiceAutoPreference(next);
				if (next) {
					this.tryUnlockAssistantVoiceBrowserPlayback({
						resumePending: true,
						silent: true
					}).catch(() => {});
				}
				if (!next) {
					this.stopAssistantVoicePlayback();
					Object.keys(this.assistantVoiceStateMap || {}).forEach((messageId) => {
						const entry = this.assistantVoiceStateMap[messageId];
						if (!entry || typeof entry !== 'object') return;
						this.setAssistantVoiceEntry(messageId, {
							autoPlayPending: false,
							waitingForSegmentIndex: -1,
							state: this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : entry.requestKey ? 'loading' : 'idle'
						});
					});
				}
				uni.showToast({
					title: next
						? this.tx('assistant_voice_auto_on', '已开启自动语音')
						: this.tx('assistant_voice_auto_off', '已关闭自动语音'),
					icon: 'none'
				});
			},
			resolveCharacterVoiceCharacterId(characterId) {
				const direct = characterId != null ? String(characterId).trim() : '';
				if (direct) return direct;
				const current = this.char && this.char.id != null ? String(this.char.id).trim() : '';
				if (current) return current;
				const fallback = this.cid != null ? String(this.cid).trim() : '';
				return fallback || '';
			},
			localCharacterVoiceConfigKey(viewerKey, characterId) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				const safeCharacterId = this.resolveCharacterVoiceCharacterId(characterId);
				if (!safeViewerKey || !safeCharacterId) return '';
				return LOCAL_CHARACTER_VOICE_CONFIG_PREFIX + safeViewerKey + '_' + safeCharacterId;
			},
			normalizeCharacterVoiceText(value, maxLength) {
				const limit = Math.max(0, Math.floor(Number(maxLength) || 255));
				const text = String(value == null ? '' : value)
					.replace(/\s+/g, ' ')
					.trim();
				return limit > 0 ? text.slice(0, limit) : text;
			},
			normalizeCharacterVoiceConfig(config) {
				const source = config && typeof config === 'object' ? config : {};
				return {
					enabled: source.enabled === true,
					autoPlayEnabled: source.autoPlayEnabled === true,
					allowAiExpression: source.allowAiExpression === true,
					ttsModelName: this.normalizeCharacterVoiceText(source.ttsModelName, 255),
					ttsVoiceName: this.normalizeCharacterVoiceText(source.ttsVoiceName, 255),
					ttsVoiceTemplateCode: this.normalizeCharacterVoiceText(source.ttsVoiceTemplateCode, 64),
					updatedAt: Math.max(0, Math.floor(Number(source.updatedAt) || 0))
				};
			},
			normalizeCharacterVoiceGlobalState(raw) {
				const source = raw && typeof raw === 'object' ? raw : {};
				const providerOptions = Array.isArray(source.providerOptions)
					? source.providerOptions.map((item) => {
						const next = item && typeof item === 'object' ? item : {};
						return {
							value: this.normalizeCharacterVoiceText(next.value, 80),
							label: this.normalizeCharacterVoiceText(next.label || next.name || next.value, 80)
						};
					}).filter((item) => item.value)
					: [];
				const ttsVoiceTemplates = Array.isArray(source.ttsVoiceTemplates)
					? source.ttsVoiceTemplates.map((item) => normalizeCharacterVoiceTemplateItem(item)).filter((item) => item.code)
					: [];
				return {
					loading: false,
					loaded: true,
					error: '',
					loadedAt: Date.now(),
					enabledGlobal: source.enabledGlobal !== false,
					canUse: source.canUse !== false,
					denyReason: this.normalizeCharacterVoiceText(source.denyReason, 200),
					mode: String(source.mode || '').trim() === 'custom' ? 'custom' : 'system',
					providerSource: this.normalizeCharacterVoiceText(source.effectiveTtsProviderSource || source.providerSource, 80),
					modelName: this.normalizeCharacterVoiceText(source.modelName, 255),
					sttModelName: this.normalizeCharacterVoiceText(source.sttModelName, 255),
					ttsModelName: this.normalizeCharacterVoiceText(source.ttsModelName, 255),
					ttsVoiceName: this.normalizeCharacterVoiceText(source.ttsVoiceName, 255),
					ttsVoiceTemplateCode: this.normalizeCharacterVoiceText(source.ttsVoiceTemplateCode, 64),
					ttsVoiceTemplateLabel: this.normalizeCharacterVoiceText(source.ttsVoiceTemplateLabel, 120),
					apiKeyConfigured: !!(source.effectiveTtsApiKeyConfigured || source.apiKeyConfigured),
					apiKeyMask: this.normalizeCharacterVoiceText(source.effectiveTtsApiKeyMask || source.apiKeyMask, 120),
					customUrl: this.normalizeCharacterVoiceText(source.effectiveTtsCustomUrl || source.customUrl, 255),
					ttsUseSeparateConfig: source.ttsUseSeparateConfig === true,
					providerOptions,
					ttsVoiceTemplates
				};
			},
			readCharacterVoiceConfig(characterId, viewerKey) {
				const key = this.localCharacterVoiceConfigKey(viewerKey, characterId);
				if (!key) return createDefaultCharacterVoiceConfig();
				try {
					const raw = uni.getStorageSync(key);
					const source =
						raw && typeof raw === 'object' && raw.config && typeof raw.config === 'object'
							? raw.config
							: raw && typeof raw === 'object'
								? raw
								: null;
					if (!source) {
						return createDefaultCharacterVoiceConfig();
					}
					const normalized = this.normalizeCharacterVoiceConfig(source);
					if (!raw || raw.version !== LOCAL_CHARACTER_VOICE_CONFIG_VERSION) {
						this.writeCharacterVoiceConfig(normalized, characterId, viewerKey);
					}
					return normalized;
				} catch (e) {
					return createDefaultCharacterVoiceConfig();
				}
			},
			writeCharacterVoiceConfig(config, characterId, viewerKey) {
				const key = this.localCharacterVoiceConfigKey(viewerKey, characterId);
				if (!key) return false;
				const normalized = this.normalizeCharacterVoiceConfig(
					Object.assign({}, config || {}, {
						updatedAt: Date.now()
					})
				);
				const isDefault =
					normalized.enabled !== true &&
					normalized.autoPlayEnabled !== true &&
					normalized.allowAiExpression !== true &&
					!normalized.ttsModelName &&
					!normalized.ttsVoiceName &&
					!normalized.ttsVoiceTemplateCode;
				try {
					if (isDefault) {
						uni.removeStorageSync(key);
						return true;
					}
					uni.setStorageSync(key, {
						version: LOCAL_CHARACTER_VOICE_CONFIG_VERSION,
						config: normalized
					});
					return true;
				} catch (e) {
					return false;
				}
			},
			refreshCharacterVoiceConfig(characterId) {
				const next = this.readCharacterVoiceConfig(characterId);
				this.characterVoiceConfig = next;
				if (next.enabled === false) {
					this.stopAssistantVoicePlayback();
				}
				return next;
			},
			currentCharacterVoiceConfig() {
				return this.normalizeCharacterVoiceConfig(this.characterVoiceConfig);
			},
			isCharacterVoiceEnabled() {
				return this.isVoiceFeatureEnabledGlobal() && this.currentCharacterVoiceConfig().enabled === true;
			},
			isCharacterVoiceAutoPlayEnabled() {
				return this.currentCharacterVoiceConfig().autoPlayEnabled === true;
			},
			isCharacterAiExpressionEnabled() {
				return this.currentCharacterVoiceConfig().allowAiExpression === true;
			},
			isCharacterVoiceConfigCustomized() {
				const config = this.currentCharacterVoiceConfig();
				return (
					config.enabled === true ||
					config.autoPlayEnabled === true ||
					config.allowAiExpression === true ||
					!!config.ttsModelName ||
					!!config.ttsVoiceName ||
					!!config.ttsVoiceTemplateCode
				);
			},
			toggleCharacterAiExpressionEnabled() {
				const current = this.currentCharacterVoiceConfig();
				const next = Object.assign({}, current, {
					allowAiExpression: current.allowAiExpression !== true
				});
				const ok = this.writeCharacterVoiceConfig(next);
				if (!ok) {
					this.showErrorToast(this.tx('character_ai_expression_save_failed', '角色表情设置保存失败，请重试'));
					return;
				}
				this.characterVoiceConfig = next;
				uni.showToast({
					title: next.allowAiExpression
						? this.tx('character_ai_expression_enabled_toast', '当前角色已允许 AI 发表情')
						: this.tx('character_ai_expression_disabled_toast', '当前角色已关闭 AI 发表情'),
					icon: 'none'
				});
			},
			supportsCharacterVoiceOpenAiPresets(modelName) {
				const text = String(modelName || '').trim().toLowerCase();
				return !!text && /(gpt-4o-mini-tts|tts-1|tts-1-hd|\/tts|openai\/.*tts)/.test(text);
			},
			supportsCharacterVoiceSiliconFlowPresets(modelName) {
				const text = String(modelName || '').trim().toLowerCase();
				return !!text && /(cosyvoice|fish-speech|gpt-sovits)/.test(text);
			},
			isCharacterVoiceKnownOpenAiPreset(voiceName) {
				const text = String(voiceName || '').trim().toLowerCase();
				return !!text && OPENAI_TTS_VOICE_PRESETS.indexOf(text) >= 0;
			},
			resolveCharacterVoiceGlobalProviderLabel() {
				const state = this.characterVoiceGlobalState || {};
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('official_api', '官方 API');
				}
				const providerSource = this.normalizeCharacterVoiceText(state.providerSource, 80);
				const options = Array.isArray(state.providerOptions) ? state.providerOptions : [];
				const matched = options.find((item) => String((item && item.value) || '').trim() === providerSource);
				if (matched && matched.label) {
					return matched.label;
				}
				return CHARACTER_VOICE_PROVIDER_LABELS[providerSource] || providerSource || this.tx('not_configured', '未配置');
			},
			characterVoiceGlobalModeText() {
				const state = this.characterVoiceGlobalState || {};
				if (state.loading) {
					return this.tx('character_voice_global_mode_loading', '正在读取全局 TTS');
				}
				if (state.error) {
					return this.tx('character_voice_global_mode_error', '全局 TTS 读取失败');
				}
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('character_voice_global_mode_system', '当前仍使用官方 API');
				}
				if (state.ttsUseSeparateConfig === true) {
					return this.tx('character_voice_global_mode_custom_tts_split', '全局 TTS 已独立配置');
				}
				return this.tx('character_voice_global_mode_custom', '全局 TTS 跟随主平台');
			},
			characterVoiceGlobalProviderText() {
				return this.tx('platform', '平台') + ' · ' + this.resolveCharacterVoiceGlobalProviderLabel();
			},
			characterVoiceGlobalKeyText() {
				const state = this.characterVoiceGlobalState || {};
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('character_voice_global_key_system', '官方托管');
				}
				if (!state.apiKeyConfigured) {
					return this.tx('character_voice_global_key_missing', '未保存');
				}
				const mask = this.normalizeCharacterVoiceText(state.apiKeyMask, 120);
				return mask || this.tx('character_voice_global_key_saved', '已保存');
			},
			characterVoiceGlobalSttText() {
				const state = this.characterVoiceGlobalState || {};
				return this.normalizeCharacterVoiceText(state.sttModelName, 255) || this.tx('follow_global_none', '未配置');
			},
			characterVoiceGlobalTtsText() {
				const state = this.characterVoiceGlobalState || {};
				return this.tx('tts_model_short', '模型') + ' · ' + (
					this.normalizeCharacterVoiceText(state.ttsModelName, 255) || this.tx('follow_global_none', '未配置')
				);
			},
			characterVoiceGlobalVoiceText() {
				const state = this.characterVoiceGlobalState || {};
				const templateCode = this.normalizeCharacterVoiceText(state.ttsVoiceTemplateCode, 64);
				const templateLabel =
					this.normalizeCharacterVoiceText(state.ttsVoiceTemplateLabel, 120) ||
					(
						(Array.isArray(state.ttsVoiceTemplates) ? state.ttsVoiceTemplates : [])
							.map((item) => normalizeCharacterVoiceTemplateItem(item))
							.find((item) => item.code === templateCode) || {}
					).displayName ||
					templateCode;
				if (templateCode) {
					return this.tx('voice_short', '音色') + ' · 模板 · ' + templateLabel;
				}
				return this.tx('voice_short', '音色') + ' · ' + (
					this.normalizeCharacterVoiceText(state.ttsVoiceName, 255) || this.tx('follow_global_none', '未配置')
				);
			},
			characterVoiceGlobalTipText() {
				const state = this.characterVoiceGlobalState || {};
				if (state.loading) {
					return this.tx('character_voice_global_loading_tip', '这里会显示当前真正生效的全局 TTS 平台、模型和音色。');
				}
				if (state.error) {
					return this.tx('character_voice_global_error_tip', '这里读不到全局配置时，去 AI 设置页仍然可以正常改平台、模型和 API Key。');
				}
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('character_voice_global_system_tip', '现在还是官方 API。想自己选平台、填 API Key、单独配 TTS，需要先去 AI 设置页切到“我的 API Key”。');
				}
				if (!state.apiKeyConfigured) {
					return this.tx('character_voice_global_key_tip', '当前生效的 TTS 平台还没保存 Key。先去 AI 设置页填好，再回来给角色单独挑声线。');
				}
				if (!this.normalizeCharacterVoiceText(state.ttsModelName, 255)) {
					return this.tx('character_voice_global_tts_tip', '当前全局还没配 TTS 模型。先在 AI 设置里把平台、TTS 模型和音色配好，再回来做角色覆盖。');
				}
				return this.tx('character_voice_global_ok_tip', '这里不存平台和 API Key，只给当前角色覆盖 TTS 模型、音色和自动播放。大多数角色直接跟随全局就够了。');
			},
			refreshCharacterVoiceGlobalSummary(force, showToast) {
				const current = this.characterVoiceGlobalState || createCharacterVoiceGlobalState();
				if (current.loading) {
					return Promise.resolve(current);
				}
				const now = Date.now();
				if (!force && current.loaded && now - Number(current.loadedAt || 0) < 15000) {
					return Promise.resolve(current);
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					const clientUid = tavernApi && typeof tavernApi.getClientUid === 'function' ? String(tavernApi.getClientUid() || '').trim() : '';
					if (!clientUid) {
						throw new Error(this.tx('login_required', '请先登录'));
					}
					this.characterVoiceGlobalState = Object.assign(createCharacterVoiceGlobalState(), current, {
						loading: true,
						error: ''
					});
					return tavernApi.getTavernUserAiProvider(clientUid).then((data) => {
						const next = this.normalizeCharacterVoiceGlobalState(data);
						this.characterVoiceGlobalState = next;
						return next;
					}).catch((err) => {
						const next = Object.assign(createCharacterVoiceGlobalState(), current, {
							loading: false,
							loaded: false,
							error: this.jgErrMsg(err, this.tx('character_voice_global_failed', '全局语音配置读取失败'))
						});
						this.characterVoiceGlobalState = next;
						if (showToast) {
							this.showErrorToast(next.error);
						}
						return next;
					});
				} catch (err) {
					const next = Object.assign(createCharacterVoiceGlobalState(), current, {
						loading: false,
						loaded: false,
						error: this.jgErrMsg(err, this.tx('character_voice_global_failed', '全局语音配置读取失败'))
					});
					this.characterVoiceGlobalState = next;
					if (showToast) {
						this.showErrorToast(next.error);
					}
					return Promise.resolve(next);
				}
			},
			applyCharacterVoiceGlobalDefaults() {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				const state = this.characterVoiceGlobalState || {};
				if (state.loading) return;
				const ttsModelName = this.normalizeCharacterVoiceText(state.ttsModelName, 255);
				const ttsVoiceName = this.normalizeCharacterVoiceText(state.ttsVoiceName, 255);
				const ttsVoiceTemplateCode = this.normalizeCharacterVoiceText(state.ttsVoiceTemplateCode, 64);
				if (!ttsModelName && !ttsVoiceName && !ttsVoiceTemplateCode) {
					this.showErrorToast(this.tx('character_voice_no_global_tts', '当前全局还没有可带入的 TTS / 音色'));
					return;
				}
				this.characterVoicePanel.ttsModelName = ttsModelName;
				this.characterVoicePanel.ttsVoiceTemplateCode = ttsVoiceTemplateCode;
				this.characterVoicePanel.ttsVoiceName = ttsVoiceTemplateCode ? '' : ttsVoiceName;
				uni.showToast({
					title: this.tx('character_voice_applied_global', '已带入全局语音配置'),
					icon: 'none'
				});
			},
			openCharacterVoicePanel() {
				if (!this.isVoiceFeatureEnabledGlobal()) return;
				const config = this.currentCharacterVoiceConfig();
				this.characterVoicePanel = Object.assign(
					createCharacterVoicePanelState(),
					config,
					{
						visible: true,
						saving: false
					}
				);
				this.inputFocus = false;
				this.refreshCharacterVoiceGlobalSummary(false, false);
				try {
					uni.hideKeyboard();
				} catch (e) {}
			},
			closeCharacterVoicePanel() {
				if (this.characterVoicePanel && this.characterVoicePanel.saving) return;
				this.characterVoicePanel = createCharacterVoicePanelState();
			},
			setCharacterVoicePanelEnabled(enabled) {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				this.characterVoicePanel.enabled = enabled !== false;
			},
			setCharacterVoicePanelAutoPlay(enabled) {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				this.characterVoicePanel.autoPlayEnabled = enabled !== false;
			},
			selectCharacterVoicePreset(voiceName) {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving || this.characterVoicePanel.enabled === false) return;
				this.characterVoicePanel.ttsVoiceTemplateCode = '';
				this.characterVoicePanel.ttsVoiceName = String(voiceName || '').trim();
			},
			characterVoiceTemplateAssetUrl(url) {
				const safeUrl = this.normalizeCharacterVoiceText(url, 255);
				if (!safeUrl) {
					return '';
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					if (tavernApi && typeof tavernApi.resolveJgAssetUrl === 'function') {
						return tavernApi.resolveJgAssetUrl(safeUrl) || safeUrl;
					}
				} catch (e) {}
				return safeUrl;
			},
			selectCharacterVoiceTemplate(item) {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving || this.characterVoicePanel.enabled === false) return;
				if (!item || !item.code) return;
				this.characterVoicePanel.ttsVoiceTemplateCode = String(item.code || '').trim();
				this.characterVoicePanel.ttsVoiceName = '';
				if (
					!this.normalizeCharacterVoiceText(this.characterVoicePanel.ttsModelName, 255) &&
					this.normalizeCharacterVoiceText(item.recommendedModelName, 255)
				) {
					this.characterVoicePanel.ttsModelName = this.normalizeCharacterVoiceText(item.recommendedModelName, 255);
				}
			},
			clearCharacterVoiceTemplateSelection() {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				this.characterVoicePanel.ttsVoiceTemplateCode = '';
			},
			characterVoiceTemplateIntroText() {
				if (this.normalizeCharacterVoiceText(this.characterVoicePanel && this.characterVoicePanel.ttsVoiceTemplateCode, 64)) {
					return this.tx('character_voice_template_intro_active', '当前角色正在使用模板音色，真正发声时会优先按你的 API Key 自动生成并复用专属 voice。');
				}
				return this.tx('character_voice_template_intro', '推荐直接选模板音色。首次使用时，会用你当前生效的 TTS API Key 自动生成属于你的专属音色。');
			},
			characterVoiceSelectedTemplateTitleText() {
				const template = this.selectedCharacterVoicePanelVoiceTemplate;
				if (!template) {
					return this.tx('character_voice_template_current', '当前模板音色');
				}
				return this.tx('character_voice_template_current', '当前模板音色') + ' · ' + (template.displayName || template.code);
			},
			characterVoiceSelectedTemplateStatusText() {
				const template = this.selectedCharacterVoicePanelVoiceTemplate;
				if (!template) {
					return this.tx('character_voice_template_pending', '首次使用自动生成');
				}
				if (template.ready) {
					return template.statusText || this.tx('character_voice_template_ready', '当前账号已准备好专属音色');
				}
				return template.statusText || this.tx('character_voice_template_pending', '首次使用自动生成');
			},
			characterVoiceVoicePlaceholder() {
				const modelName = this.normalizeCharacterVoiceText(
					this.characterVoicePanel && this.characterVoicePanel.ttsModelName
				);
				if (this.supportsCharacterVoiceOpenAiPresets(modelName)) {
					return this.tx('character_voice_voice_openai', '留空则使用默认音色 alloy');
				}
				if (this.supportsCharacterVoiceSiliconFlowPresets(modelName)) {
					return this.tx('character_voice_voice_siliconflow', '留空则用默认音色 alex，也可填写模型自己的音色 ID');
				}
				return this.tx('character_voice_voice_placeholder', '留空则跟随全局音色，或填写模型自己的音色 ID');
			},
			characterVoiceVoiceHintText() {
				const template = this.selectedCharacterVoicePanelVoiceTemplate;
				if (template) {
					return template.ready
						? (template.statusText || this.tx('character_voice_template_hint_ready', '这个角色会优先使用你已经生成好的模板音色，手填 voice ID 已临时收起。'))
						: (template.statusText || this.tx('character_voice_template_hint_pending', '首次播放到这个角色时，系统会先为你生成一份专属 voice，成功后会自动复用。'));
				}
				const modelName = this.normalizeCharacterVoiceText(
					this.characterVoicePanel && this.characterVoicePanel.ttsModelName
				);
				const voiceName = this.normalizeCharacterVoiceText(
					this.characterVoicePanel && this.characterVoicePanel.ttsVoiceName,
					255
				);
				if (!modelName) {
					return this.tx('character_voice_hint_follow_global', '模型和音色都留空时，会跟随 AI 设置页里的全局语音配置。');
				}
				if (this.supportsCharacterVoiceOpenAiPresets(modelName)) {
					return this.tx('character_voice_hint_openai', '这类模型适合 alloy / nova / shimmer / echo / fable / onyx。');
				}
				if (this.supportsCharacterVoiceSiliconFlowPresets(modelName)) {
					if (this.isCharacterVoiceKnownOpenAiPreset(voiceName)) {
						return this.tx('character_voice_hint_siliconflow_wrong', '当前模型别再用 alloy / nova，建议换 alex / bella 这类系统音色。');
					}
					return this.tx('character_voice_hint_siliconflow', '这类模型通常更自然，推荐 alex / benjamin / charles / david / anna / bella / claire / diana。');
				}
				if (this.isCharacterVoiceKnownOpenAiPreset(voiceName)) {
					return this.tx('character_voice_hint_generic_wrong', '当前模型未识别为 OpenAI 标准 TTS，alloy / nova 这类音色可能不可用。');
				}
				return this.tx('character_voice_hint_generic', '想让声音更像真人，优先试 CosyVoice2、Fish-Speech 或 GPT-SoVITS。');
			},
			resetCharacterVoicePanelToDefault() {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				this.characterVoicePanel = Object.assign(createCharacterVoicePanelState(), {
					visible: true,
					saving: false
				});
			},
			saveCharacterVoicePanel() {
				if (!this.characterVoicePanel || this.characterVoicePanel.saving) return;
				const next = this.normalizeCharacterVoiceConfig(this.characterVoicePanel);
				this.characterVoicePanel.saving = true;
				const ok = this.writeCharacterVoiceConfig(next);
				this.characterVoicePanel.saving = false;
				if (!ok) {
					this.showErrorToast(this.tx('character_voice_save_failed', '角色语音保存失败，请重试'));
					return;
				}
				this.characterVoiceConfig = next;
				if (next.enabled === false) {
					this.stopAssistantVoicePlayback();
				}
				this.closeCharacterVoicePanel();
				uni.showToast({
					title: this.tx('character_voice_saved', '角色语音已保存'),
					icon: 'none'
				});
			},
			buildCharacterVoiceTtsPayload(text) {
				const payload = {
					clientUid: require('@/common/tavernApi.js').getClientUid(),
					content: text
				};
				const config = this.currentCharacterVoiceConfig();
				if (config.ttsModelName) {
					payload.ttsModelName = config.ttsModelName;
				}
				if (config.ttsVoiceTemplateCode) {
					payload.ttsVoiceTemplateCode = config.ttsVoiceTemplateCode;
				} else if (config.ttsVoiceName) {
					payload.ttsVoiceName = config.ttsVoiceName;
				}
				return payload;
			},
			normalizeCharacterImageText(value, maxLength) {
				const limit = Math.max(0, Math.floor(Number(maxLength) || 255));
				const text = String(value == null ? '' : value)
					.replace(/\s+/g, ' ')
					.trim();
				return limit > 0 ? text.slice(0, limit) : text;
			},
			normalizeCharacterImageAspectRatio(value) {
				const safe = String(value == null ? '' : value).trim().toLowerCase();
				if (safe === 'square' || safe === 'landscape' || safe === 'wide') {
					return safe;
				}
				return 'portrait';
			},
			characterImageAspectOptions() {
				return CHARACTER_IMAGE_ASPECT_OPTIONS.slice();
			},
			localCharacterImageConfigKey(viewerKey, characterId) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				const safeCharacterId = this.resolveCharacterVoiceCharacterId(characterId);
				if (!safeViewerKey || !safeCharacterId) return '';
				return LOCAL_CHARACTER_IMAGE_CONFIG_PREFIX + safeViewerKey + '_' + safeCharacterId;
			},
			localCharacterImageReferenceKey(viewerKey, characterId) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				const safeCharacterId = this.resolveCharacterVoiceCharacterId(characterId);
				if (!safeViewerKey || !safeCharacterId) return '';
				return LOCAL_CHARACTER_IMAGE_REFERENCE_PREFIX + safeViewerKey + '_' + safeCharacterId;
			},
			normalizeCharacterImageConsistencyMode(value) {
				const safe = String(value || '').trim().toLowerCase();
				if (safe === 'free') return 'free';
				if (safe === 'strong' || safe === 'reference_only') return 'strong';
				return 'balanced';
			},
			normalizeCharacterImageReferenceSourceMode(value) {
				const safe = String(value || '').trim().toLowerCase();
				if (safe === 'avatar_only') return 'avatar_only';
				return 'latest_generated_first';
			},
			resolveCharacterImageReferencePolicy(value) {
				const mode = this.normalizeCharacterImageConsistencyMode(value);
				if (mode === 'free') return 'prompt_first';
				if (mode === 'strong') return 'reference_only';
				return 'balanced';
			},
			normalizeCharacterImageConfig(config) {
				const source = config && typeof config === 'object' ? config : {};
				return {
					enabled: source.enabled !== false,
					styleHint: this.normalizeCharacterImageText(source.styleHint, 120),
					aspectRatio: this.normalizeCharacterImageAspectRatio(source.aspectRatio),
					updatedAt: Math.max(0, Math.floor(Number(source.updatedAt) || 0))
				};
			},
			readLatestCharacterImageReference(characterId, viewerKey) {
				const key = this.localCharacterImageReferenceKey(viewerKey, characterId);
				if (!key) return '';
				try {
					const raw = uni.getStorageSync(key);
					const source = raw && typeof raw === 'object' ? raw : {};
					const url = this.normalizeCharacterImageText(source.url || raw, 1024 * 1024);
					return url || '';
				} catch (e) {
					return '';
				}
			},
			writeLatestCharacterImageReference(url, characterId, viewerKey) {
				const key = this.localCharacterImageReferenceKey(viewerKey, characterId);
				const safeUrl = this.normalizeCharacterImageText(url, 1024 * 1024);
				if (!key || !safeUrl) return '';
				try {
					uni.setStorageSync(key, {
						url: safeUrl,
						updatedAt: Date.now()
					});
					return safeUrl;
				} catch (e) {
					return '';
				}
			},
			readCharacterImageConfig(characterId, viewerKey) {
				const key = this.localCharacterImageConfigKey(viewerKey, characterId);
				if (!key) return createDefaultCharacterImageConfig();
				try {
					const raw = uni.getStorageSync(key);
					const source =
						raw && typeof raw === 'object' && raw.config && typeof raw.config === 'object'
							? raw.config
							: raw && typeof raw === 'object'
								? raw
								: null;
					if (!source) {
						return createDefaultCharacterImageConfig();
					}
					const normalized = this.normalizeCharacterImageConfig(source);
					if (!raw || raw.version !== LOCAL_CHARACTER_IMAGE_CONFIG_VERSION) {
						this.writeCharacterImageConfig(normalized, characterId, viewerKey);
					}
					return normalized;
				} catch (e) {
					return createDefaultCharacterImageConfig();
				}
			},
			writeCharacterImageConfig(config, characterId, viewerKey) {
				const key = this.localCharacterImageConfigKey(viewerKey, characterId);
				if (!key) return false;
				const normalized = this.normalizeCharacterImageConfig(
					Object.assign({}, config || {}, {
						updatedAt: Date.now()
					})
				);
				const isDefault =
					normalized.enabled !== false &&
					!normalized.styleHint &&
					this.normalizeCharacterImageAspectRatio(normalized.aspectRatio) === 'portrait';
				try {
					if (isDefault) {
						uni.removeStorageSync(key);
						return true;
					}
					uni.setStorageSync(key, {
						version: LOCAL_CHARACTER_IMAGE_CONFIG_VERSION,
						config: normalized
					});
					return true;
				} catch (e) {
					return false;
				}
			},
			refreshCharacterImageConfig(characterId) {
				const next = this.readCharacterImageConfig(characterId);
				this.characterImageConfig = next;
				return next;
			},
			currentCharacterImageConfig() {
				return this.normalizeCharacterImageConfig(this.characterImageConfig);
			},
			normalizeCharacterImageGlobalState(raw) {
				const source = raw && typeof raw === 'object' ? raw : {};
				const providerOptions = Array.isArray(source.providerOptions)
					? source.providerOptions.map((item) => {
						const next = item && typeof item === 'object' ? item : {};
						return {
							value: this.normalizeCharacterImageText(next.value, 80),
							label: this.normalizeCharacterImageText(next.label || next.name || next.value, 80)
						};
					}).filter((item) => item.value)
					: [];
				return {
					loading: false,
					loaded: true,
					error: '',
					loadedAt: Date.now(),
					enabledGlobal: source.enabledGlobal !== false,
					canUse: source.canUse !== false,
					denyReason: this.normalizeCharacterImageText(source.denyReason, 200),
					mode: String(source.mode || '').trim() === 'custom' ? 'custom' : 'system',
					providerSource: this.normalizeCharacterImageText(
						source.effectiveImageProviderSource || source.imageProviderSource || source.providerSource,
						80
					),
					imageModelName: this.normalizeCharacterImageText(source.imageModelName, 255),
					apiKeyConfigured: !!(source.effectiveImageApiKeyConfigured || source.apiKeyConfigured),
					apiKeyMask: this.normalizeCharacterImageText(
						source.effectiveImageApiKeyMask || source.imageApiKeyMask || source.apiKeyMask,
						120
					),
					customUrl: this.normalizeCharacterImageText(
						source.effectiveImageCustomUrl || source.imageCustomUrl || source.customUrl,
						255
					),
					imageUseSeparateConfig: source.imageUseSeparateConfig === true,
					imageEnabledGlobal: source.imageEnabledGlobal !== false,
					imageCanUse: source.imageCanUse === true,
					imageDenyReason: this.normalizeCharacterImageText(source.imageDenyReason || source.denyReason, 200),
					imageCharacterConsistencyMode: this.normalizeCharacterImageConsistencyMode(
						source.imageCharacterConsistencyMode
					),
					imageReferenceSourceMode: this.normalizeCharacterImageReferenceSourceMode(
						source.imageReferenceSourceMode
					),
					providerOptions
				};
			},
			resolveCharacterImageGlobalProviderLabel() {
				const state = this.characterImageGlobalState || {};
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('official_api', '官方 API');
				}
				const providerSource = this.normalizeCharacterImageText(state.providerSource, 80);
				const options = Array.isArray(state.providerOptions) ? state.providerOptions : [];
				const matched = options.find((item) => String((item && item.value) || '').trim() === providerSource);
				if (matched && matched.label) {
					return matched.label;
				}
				return CHARACTER_VOICE_PROVIDER_LABELS[providerSource] || providerSource || this.tx('not_configured', '未配置');
			},
			characterImageGlobalModeText() {
				const state = this.characterImageGlobalState || {};
				if (state.loading) {
					return this.tx('character_image_global_mode_loading', '正在读取全局生图');
				}
				if (state.error) {
					return this.tx('character_image_global_mode_error', '全局生图读取失败');
				}
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('character_image_global_mode_system', '当前仍使用官方 API');
				}
				if (state.imageUseSeparateConfig === true) {
					return this.tx('character_image_global_mode_custom_split', '全局生图已独立配置');
				}
				return this.tx('character_image_global_mode_custom', '全局生图跟随主平台');
			},
			characterImageGlobalProviderText() {
				return this.tx('platform', '平台') + ' · ' + this.resolveCharacterImageGlobalProviderLabel();
			},
			characterImageGlobalModelText() {
				const state = this.characterImageGlobalState || {};
				return this.tx('image_model_short', '模型') + ' · ' + (
					this.normalizeCharacterImageText(state.imageModelName, 255) || this.tx('follow_global_none', '未配置')
				);
			},
			characterImageGlobalKeyText() {
				const state = this.characterImageGlobalState || {};
				if (String(state.mode || '').trim() !== 'custom') {
					return this.tx('character_image_global_key_system', '官方托管');
				}
				if (!state.apiKeyConfigured) {
					return this.tx('character_image_global_key_missing', '未保存 Key');
				}
				const mask = this.normalizeCharacterImageText(state.apiKeyMask, 120);
				return this.tx('api_key_short', 'Key') + ' · ' + (mask || this.tx('saved', '已保存'));
			},
			characterImageStyleHintText() {
				const config = this.normalizeCharacterImageConfig(this.characterImagePanel);
				if (!config.enabled) {
					return this.tx('character_image_disabled_hint', '关闭后，这个角色不会再从聊天页进入本地生图。');
				}
				return this.tx('character_image_style_hint', '这里是当前角色的本地偏好，只会影响生图提示词和默认画幅，不会改全局平台或 API Key。');
			},
			refreshCharacterImageGlobalSummary(force, showToast) {
				const current = this.characterImageGlobalState || createCharacterImageGlobalState();
				if (current.loading) {
					return Promise.resolve(current);
				}
				const now = Date.now();
				if (!force && current.loaded && now - Number(current.loadedAt || 0) < 15000) {
					return Promise.resolve(current);
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					const clientUid = tavernApi && typeof tavernApi.getClientUid === 'function' ? String(tavernApi.getClientUid() || '').trim() : '';
					if (!clientUid) {
						throw new Error(this.tx('login_required', '请先登录'));
					}
					this.characterImageGlobalState = Object.assign(createCharacterImageGlobalState(), current, {
						loading: true,
						error: ''
					});
					return tavernApi.getTavernUserAiProvider(clientUid).then((data) => {
						const next = this.normalizeCharacterImageGlobalState(data);
						this.characterImageGlobalState = next;
						return next;
					}).catch((err) => {
						const next = Object.assign(createCharacterImageGlobalState(), current, {
							loading: false,
							loaded: false,
							error: this.jgErrMsg(err, this.tx('character_image_global_failed', '全局生图配置读取失败'))
						});
						this.characterImageGlobalState = next;
						if (showToast) {
							this.showErrorToast(next.error);
						}
						return next;
					});
				} catch (err) {
					const next = Object.assign(createCharacterImageGlobalState(), current, {
						loading: false,
						loaded: false,
						error: this.jgErrMsg(err, this.tx('character_image_global_failed', '全局生图配置读取失败'))
					});
					this.characterImageGlobalState = next;
					if (showToast) {
						this.showErrorToast(next.error);
					}
					return Promise.resolve(next);
				}
			},
			openCharacterImagePanel() {
				if (this.characterImageGlobalState && this.characterImageGlobalState.imageEnabledGlobal === false) {
					return;
				}
				this.closeChatAttachmentMenu();
				this.characterImagePanel = Object.assign(createCharacterImagePanelState(), {
					visible: true,
					generating: false,
					prompt: ''
				});
				this.inputFocus = false;
				this.refreshCharacterImageGlobalSummary(false, false);
				try {
					uni.hideKeyboard();
				} catch (e) {}
			},
			closeCharacterImagePanel() {
				if (this.characterImagePanel && this.characterImagePanel.generating) return;
				this.characterImagePanel = createCharacterImagePanelState();
			},
			setCharacterImagePanelEnabled(enabled) {
				if (!this.characterImagePanel || this.characterImagePanel.generating) return;
				this.characterImagePanel.enabled = enabled !== false;
			},
			setCharacterImagePanelAspectRatio(value) {
				if (!this.characterImagePanel || this.characterImagePanel.generating || this.characterImagePanel.enabled === false) return;
				this.characterImagePanel.aspectRatio = this.normalizeCharacterImageAspectRatio(value);
			},
			resetCharacterImagePanelToDefault() {
				if (!this.characterImagePanel || this.characterImagePanel.generating) return;
				this.characterImagePanel = Object.assign(createCharacterImagePanelState(), {
					visible: true,
					generating: false,
					prompt: ''
				});
			},
			saveCharacterImagePanelConfig(showToast) {
				if (!this.characterImagePanel) return false;
				const next = this.normalizeCharacterImageConfig(this.characterImagePanel);
				const ok = this.writeCharacterImageConfig(next);
				if (!ok) {
					this.showErrorToast(this.tx('character_image_save_failed', '角色生图偏好保存失败，请重试'));
					return false;
				}
				this.characterImageConfig = next;
				if (showToast) {
					uni.showToast({
						title: this.tx('character_image_saved', '角色生图偏好已保存'),
						icon: 'none'
					});
				}
				return true;
			},
			saveCharacterImagePanelOnly() {
				if (!this.characterImagePanel || this.characterImagePanel.generating) return;
				this.saveCharacterImagePanelConfig(true);
			},
			buildCharacterImagePrompt() {
				return this.normalizeCharacterImageText(this.characterImagePanel && this.characterImagePanel.prompt, 300);
			},
			resolveCharacterImageTagHints() {
				const raw = [];
				if (this.char) {
					if (Array.isArray(this.char.label_array)) {
						raw.push.apply(raw, this.char.label_array);
					}
					if (Array.isArray(this.char.labelArray)) {
						raw.push.apply(raw, this.char.labelArray);
					}
					const tagsJson = this.char.tags_json || this.char.tagsJson;
					if (typeof tagsJson === 'string' && tagsJson.trim()) {
						try {
							const parsed = JSON.parse(tagsJson);
							if (Array.isArray(parsed)) {
								raw.push.apply(raw, parsed);
							}
						} catch (e) {}
					}
				}
				const seen = {};
				return raw
					.map((item) => this.normalizeCharacterImageText(item, 24))
					.filter((item) => {
						if (!item || seen[item]) return false;
						seen[item] = true;
						return true;
					})
					.slice(0, 6);
			},
			buildCharacterImageGenerationPrompt(userPrompt) {
				const safePrompt = this.normalizeCharacterImageText(userPrompt, 300);
				return safePrompt;
			},
			isCharacterImageModelUsable(modelName) {
				return !!String(modelName || '').trim();
			},
			resolveCharacterImageAnchorMessageId() {
				const rows = Array.isArray(this.messages) ? this.messages : [];
				for (let i = rows.length - 1; i >= 0; i -= 1) {
					const row = rows[i];
					if (!row || row.role === 'user') continue;
					const id = this.normalizeDbMessageId(row.id);
					if (id && id.indexOf('db_') === 0) {
						return id;
					}
				}
				return '';
			},
			generateCharacterImage() {
				if (!this.characterImagePanel || this.characterImagePanel.generating) return;
				if (this.sending) {
					this.showErrorToast(this.tx('character_image_wait_reply', '等这轮回复完成后再生图'));
					return;
				}
				const displayPrompt = this.buildCharacterImagePrompt();
				if (!displayPrompt) {
					this.showErrorToast(this.tx('character_image_need_prompt', '先描述一下想生成什么图片'));
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				this.characterImagePanel.generating = true;
				Promise.resolve(this.refreshCharacterImageGlobalSummary(false, false)).then((globalState) => {
					const state = globalState || {};
					if (state.error) {
						throw new Error(state.error);
					}
					if (state.imageCanUse === false) {
						throw new Error(
							this.normalizeCharacterImageText(state.imageDenyReason, 200) ||
							this.tx('character_image_unavailable', '当前账号暂不可用聊天内生图')
						);
					}
					if (String(state.mode || '').trim() !== 'custom') {
						throw new Error(this.tx('character_image_need_custom_mode', '先去 AI 设置页切到“我的 API Key”再使用聊天内生图'));
					}
					if (!state.apiKeyConfigured) {
						throw new Error(this.tx('character_image_need_key', '先去 AI 设置页填写生图 API Key'));
					}
					const resolvedModelName = this.normalizeCharacterImageText(state.imageModelName, 255);
					if (!resolvedModelName) {
						throw new Error(this.tx('character_image_need_model', '先去 AI 设置页选择生图模型'));
					}
					if (!this.isCharacterImageModelUsable(resolvedModelName)) {
						throw new Error(this.tx('character_image_need_image_model', '当前选择的模型不是生图模型，请先去 AI 设置页点“获取列表”，再从返回列表里选文生图模型'));
					}
					const aspectRatio = this.normalizeCharacterImageAspectRatio(this.currentCharacterImageConfig().aspectRatio);
					const payload = {
						clientUid: tavernApi.getClientUid(),
						prompt: this.buildCharacterImageGenerationPrompt(displayPrompt),
						userPrompt: displayPrompt,
						count: 1,
						aspectRatio,
						modelName: resolvedModelName,
						providerSource: this.normalizeCharacterImageText(state.providerSource, 80),
						characterId: '',
						characterName: '',
						referenceImageUrl: '',
						referenceMode: '',
						referencePolicy: 'prompt_first'
					};
					return tavernApi.postImageGenerate(payload).then((data) => ({
						data,
						aspectRatio,
						fallbackWarning: ''
					}));
				}).then(({ data, aspectRatio, fallbackWarning }) => {
					const images = Array.isArray(data && data.images) ? data.images : [];
					const first = images[0] && typeof images[0] === 'object' ? images[0] : null;
					const imageUrl = first && first.url ? String(first.url).trim() : '';
					if (!imageUrl) {
						throw new Error(this.tx('character_image_failed', '生图失败，请稍后再试'));
					}
					return Promise.resolve(
						tavernApi.persistGeneratedChatImage(imageUrl, {
							fileNamePrefix: 'tavern_image_' + Date.now()
						})
					).catch((error) => {
						if (this.isAppPlus) {
							throw new Error(
								this.tx('character_image_save_failed', '生图成功了，但保存到本地失败，请重试')
							);
						}
						if (this.isLocalInlineImageUrl(imageUrl) && imageUrl.length > LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH) {
							throw new Error(
								this.tx('character_image_h5_local_failed', '当前浏览器暂不支持本地保存这张大图，请在 APP 内使用')
							);
						}
						return {
							url: imageUrl,
							persisted: false
						};
					}).then((persisted) => ({
						aspectRatio,
						imageUrl: persisted && persisted.url ? String(persisted.url).trim() : imageUrl,
						warning: this.normalizeCharacterImageText(
							(data && data.warning) || fallbackWarning,
							120
						)
					}));
				}).then(({ imageUrl, aspectRatio, warning }) => {
					const entry = this.upsertLocalChatImageEntry({
						messageId: 'img_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
						assistantMessageId: this.resolveCharacterImageAnchorMessageId(),
						role: 'char',
						kind: 'image_generation',
						prompt: displayPrompt,
						aspectRatio,
						text: '',
						imageUrls: [imageUrl],
						createdAt: Date.now(),
						updatedAt: Date.now()
					});
					if (!entry) {
						throw new Error(this.tx('character_image_cache_failed', '生图成功了，但本地缓存失败，请重试'));
					}
					this.appendRuntimeLocalChatImageEntry(entry);
					this.characterImagePanel.generating = false;
					this.closeCharacterImagePanel();
					this.scrollChatToBottom({ immediate: false });
					uni.showToast({
						title: warning || this.tx('character_image_done', '图片已插入聊天'),
						icon: 'none'
					});
				}).catch((err) => {
					this.characterImagePanel.generating = false;
					const handled = this.handleCommercialError(err, this.tx('character_image_failed', '生图失败，请稍后再试'), {
						skipToastWhenPrompted: true,
						toast: false
					});
					if (!(handled && handled.prompted)) {
						this.showErrorToast(this.jgErrMsg(err, this.tx('character_image_failed', '生图失败，请稍后再试')));
					}
				});
			},
			resolveLocalExpressionViewerKey() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					const viewerKey =
						tavernApi && typeof tavernApi.getClientUid === 'function' ? String(tavernApi.getClientUid() || '').trim() : '';
					if (viewerKey) {
						return viewerKey;
					}
				} catch (e) {}
				return 'guest_local';
			},
			resolveLocalExpressionCharacterId(characterId) {
				return this.resolveCharacterVoiceCharacterId(characterId);
			},
			localExpressionStorageKey(viewerKey, characterId) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				const safeCharacterId = this.resolveLocalExpressionCharacterId(characterId);
				return safeViewerKey && safeCharacterId ? LOCAL_EXPRESSION_LIBRARY_PREFIX + safeViewerKey + '_' + safeCharacterId : '';
			},
			legacyLocalExpressionStorageKey(viewerKey) {
				const safeViewerKey = viewerKey == null ? this.resolveLocalExpressionViewerKey() : String(viewerKey).trim();
				return safeViewerKey ? LOCAL_EXPRESSION_LIBRARY_PREFIX + safeViewerKey : '';
			},
			sortLocalExpressionEntries(entries) {
				return (Array.isArray(entries) ? entries : []).slice().sort((a, b) => {
					const lastUsedDiff = Number(b && b.lastUsedAt ? b.lastUsedAt : 0) - Number(a && a.lastUsedAt ? a.lastUsedAt : 0);
					if (lastUsedDiff) return lastUsedDiff;
					return Number(b && b.updatedAt ? b.updatedAt : 0) - Number(a && a.updatedAt ? a.updatedAt : 0);
				});
			},
			normalizeLocalExpressionEntry(entry) {
				if (!entry || typeof entry !== 'object') return null;
				const id = entry.id == null ? '' : String(entry.id).trim();
				const imageUrl = entry.imageUrl == null ? '' : String(entry.imageUrl).trim();
				const label = String(entry.label != null ? entry.label : entry.content || '').replace(/\s+/g, ' ').trim();
				if (!id || !imageUrl || imageUrl.indexOf('data:image/') !== 0 || !label) {
					return null;
				}
				if (imageUrl.length > LOCAL_EXPRESSION_DATA_URL_MAX_LENGTH) {
					return null;
				}
				const now = Date.now();
				const createdAtRaw = Number(entry.createdAt);
				const updatedAtRaw = Number(entry.updatedAt != null ? entry.updatedAt : createdAtRaw);
				const lastUsedAtRaw = Number(entry.lastUsedAt);
				return {
					id,
					label: label.slice(0, LOCAL_EXPRESSION_LABEL_MAX),
					content: label.slice(0, LOCAL_EXPRESSION_LABEL_MAX),
					imageUrl,
					createdAt: isFinite(createdAtRaw) && createdAtRaw > 0 ? createdAtRaw : now,
					updatedAt: isFinite(updatedAtRaw) && updatedAtRaw > 0 ? updatedAtRaw : now,
					lastUsedAt: isFinite(lastUsedAtRaw) && lastUsedAtRaw > 0 ? lastUsedAtRaw : 0,
					useCount: Math.max(0, Math.floor(Number(entry.useCount) || 0))
				};
			},
			readLocalExpressionEntries(viewerKey, characterId) {
				const key = this.localExpressionStorageKey(viewerKey, characterId);
				if (!key) return [];
				try {
					const raw = uni.getStorageSync(key);
					const source =
						raw && typeof raw === 'object' && Array.isArray(raw.entries)
							? raw.entries
							: Array.isArray(raw)
								? raw
								: [];
					const entries = this.sortLocalExpressionEntries(
						source.map((item) => this.normalizeLocalExpressionEntry(item)).filter(Boolean)
					);
					if (
						(raw && typeof raw === 'object' && raw.version !== LOCAL_EXPRESSION_LIBRARY_VERSION) ||
						entries.length !== source.length
					) {
						this.writeLocalExpressionEntries(entries, viewerKey, characterId);
					}
					if (entries.length || source.length) {
						return entries;
					}
					const legacyKey = this.legacyLocalExpressionStorageKey(viewerKey);
					if (!legacyKey || legacyKey === key) {
						return entries;
					}
					const legacyRaw = uni.getStorageSync(legacyKey);
					const legacySource =
						legacyRaw && typeof legacyRaw === 'object' && Array.isArray(legacyRaw.entries)
							? legacyRaw.entries
							: Array.isArray(legacyRaw)
								? legacyRaw
								: [];
					const legacyEntries = this.sortLocalExpressionEntries(
						legacySource.map((item) => this.normalizeLocalExpressionEntry(item)).filter(Boolean)
					);
					if (!legacyEntries.length) {
						return [];
					}
					this.writeLocalExpressionEntries(legacyEntries, viewerKey, characterId);
					try {
						uni.removeStorageSync(legacyKey);
					} catch (e) {}
					return legacyEntries;
				} catch (e) {
					return [];
				}
			},
			writeLocalExpressionEntries(entries, viewerKey, characterId) {
				const key = this.localExpressionStorageKey(viewerKey, characterId);
				if (!key) return false;
				const normalized = this.sortLocalExpressionEntries(
					(Array.isArray(entries) ? entries : []).map((item) => this.normalizeLocalExpressionEntry(item)).filter(Boolean)
				).slice(0, LOCAL_EXPRESSION_LIBRARY_LIMIT);
				try {
					if (!normalized.length) {
						uni.removeStorageSync(key);
						return true;
					}
					uni.setStorageSync(key, {
						version: LOCAL_EXPRESSION_LIBRARY_VERSION,
						updatedAt: Date.now(),
						entries: normalized.map((item) => ({
							id: item.id,
							label: item.label,
							content: item.content,
							imageUrl: item.imageUrl,
							createdAt: item.createdAt,
							updatedAt: item.updatedAt,
							lastUsedAt: item.lastUsedAt,
							useCount: item.useCount
						}))
					});
					return true;
				} catch (e) {
					return false;
				}
			},
			refreshLocalExpressionLibrary(characterId) {
				this.expressionLibrary = this.readLocalExpressionEntries(null, characterId);
				return this.expressionLibrary;
			},
			upsertLocalExpressionEntry(entry) {
				const normalized = this.normalizeLocalExpressionEntry(entry);
				if (!normalized) return null;
				const entries = this.readLocalExpressionEntries();
				const index = entries.findIndex((item) => item.id === normalized.id);
				if (index >= 0) {
					entries.splice(index, 1, Object.assign({}, entries[index], normalized, {
						createdAt: entries[index].createdAt || normalized.createdAt
					}));
				} else {
					entries.push(normalized);
				}
				if (!this.writeLocalExpressionEntries(entries)) {
					return null;
				}
				this.refreshLocalExpressionLibrary();
				return normalized;
			},
			deleteLocalExpressionEntry(id) {
				const safeId = id == null ? '' : String(id).trim();
				if (!safeId) return false;
				const entries = this.readLocalExpressionEntries().filter((item) => item && item.id !== safeId);
				const ok = this.writeLocalExpressionEntries(entries);
				if (ok) {
					this.refreshLocalExpressionLibrary();
				}
				return ok;
			},
			touchLocalExpressionUsage(id) {
				const safeId = id == null ? '' : String(id).trim();
				if (!safeId) return;
				const entries = this.readLocalExpressionEntries();
				const index = entries.findIndex((item) => item && item.id === safeId);
				if (index < 0) return;
				const current = Object.assign({}, entries[index], {
					lastUsedAt: Date.now(),
					updatedAt: Date.now(),
					useCount: Math.max(0, Math.floor(Number(entries[index].useCount) || 0)) + 1
				});
				entries.splice(index, 1, current);
				if (this.writeLocalExpressionEntries(entries)) {
					this.refreshLocalExpressionLibrary();
				}
			},
			normalizeLocalExpressionHint(text) {
				return String(text == null ? '' : text)
					.replace(/\s+/g, ' ')
					.trim()
					.slice(0, LOCAL_EXPRESSION_LABEL_MAX);
			},
			scoreLocalExpressionHint(item) {
				if (!item) return -Infinity;
				const hint = this.normalizeLocalExpressionHint(item.content || item.label);
				const keyword = this.normalizeAssistantExpressionKeyword(hint);
				if (!keyword) return -Infinity;
				const useCount = Math.max(0, Math.floor(Number(item.useCount) || 0));
				const lastUsedAt = Math.max(0, Math.floor(Number(item.lastUsedAt) || 0));
				return keyword.length * 1000 + Math.min(useCount, 24) * 40 + Math.min(lastUsedAt, 9999999999999) / 1000000000;
			},
			collectLocalExpressionHints(limit) {
				const max = Math.max(
					0,
					Math.min(Math.floor(Number(limit) || LOCAL_EXPRESSION_HINT_LIMIT), LOCAL_EXPRESSION_HINT_LIMIT)
				);
				if (!max) return [];
				const library =
					Array.isArray(this.expressionLibrary) && this.expressionLibrary.length
						? this.expressionLibrary
						: this.readLocalExpressionEntries();
				const seen = {};
				const strongHints = [];
				const weakHints = [];
				library.forEach((item) => {
					const hint = this.normalizeLocalExpressionHint(item && (item.content || item.label));
					if (!hint) return;
					const key = this.normalizeAssistantExpressionKeyword(hint);
					if (!key || seen[key]) return;
					seen[key] = true;
					const normalizedItem = Object.assign({}, item, { content: hint, label: hint });
					if (this.isWeakAssistantExpressionHint(hint)) {
						weakHints.push({ hint, score: this.scoreLocalExpressionHint(normalizedItem) });
						return;
					}
					strongHints.push({ hint, score: this.scoreLocalExpressionHint(normalizedItem) });
				});
				strongHints.sort((a, b) => b.score - a.score);
				weakHints.sort((a, b) => b.score - a.score);
				const hints = (strongHints.length ? strongHints.concat(weakHints) : weakHints).map((item) => item.hint);
				return hints.slice(0, max);
			},
			buildAssistantExpressionPayloadFields() {
				if (!this.isCharacterAiExpressionEnabled()) {
					return {};
				}
				const expressionHints = this.collectLocalExpressionHints();
				const avoidExpressionHints = this.collectRecentAssistantExpressionHints();
				const payload = {};
				if (expressionHints.length) {
					payload.expressionHints = expressionHints;
				}
				if (avoidExpressionHints.length) {
					payload.avoidExpressionHints = avoidExpressionHints;
				}
				return payload;
			},
			normalizeAssistantExpressionHint(text) {
				return String(text == null ? '' : text)
					.replace(/\s+/g, ' ')
					.trim()
					.slice(0, LOCAL_EXPRESSION_LABEL_MAX);
			},
			isWeakAssistantExpressionHint(text) {
				const hint = this.normalizeAssistantExpressionHint(text);
				const keyword = this.normalizeAssistantExpressionKeyword(hint);
				if (!keyword) return true;
				if (WEAK_ASSISTANT_EXPRESSION_KEYWORDS[keyword]) return true;
				if (/^[0-9]+$/.test(keyword)) return true;
				if (/^[\W_]+$/i.test(keyword)) return true;
				if (/^(哈哈哈+|呵呵呵+|嘿嘿嘿+|嘻嘻嘻+|嗯嗯+|啊啊+|哦哦+)$/.test(keyword)) return true;
				if (/^(好+|行+|是+|不+|哇+|呀+|啦+|喔+|呜+)$/.test(keyword)) return true;
				const pureAscii = /^[a-z0-9_-]+$/i.test(hint);
				if (pureAscii) {
					return keyword.length < 3;
				}
				return keyword.length < 2;
			},
			normalizeAssistantExpressionKeyword(text) {
				return String(text == null ? '' : text)
					.toLowerCase()
					.replace(/\s+/g, '')
					.trim();
			},
			extractAssistantExpressionPayload(text) {
				const rawText = String(text == null ? '' : text);
				const keywords = [];
				const cleanedText = rawText
					.replace(ASSISTANT_EXPRESSION_MARKER_REGEX, (all, keyword) => {
						const safeKeyword = this.normalizeAssistantExpressionHint(keyword);
						if (safeKeyword) {
							keywords.push(safeKeyword);
						}
						return '';
					})
					.replace(/[ \t]+\n/g, '\n')
					.replace(/\n{3,}/g, '\n\n')
					.trim();
				return {
					text: cleanedText,
					keywords
				};
			},
			findLocalExpressionByKeyword(keyword, library) {
				const targetKeyword = this.normalizeAssistantExpressionKeyword(keyword);
				if (!targetKeyword) return null;
				const source =
					Array.isArray(library) && library.length ? library : this.readLocalExpressionEntries();
				let best = null;
				let bestScore = -1;
				source.forEach((item) => {
					if (!item || !item.imageUrl) return;
					const candidateKeyword = this.normalizeAssistantExpressionKeyword(item.content || item.label);
					if (!candidateKeyword || candidateKeyword !== targetKeyword) return;
					const score =
						Math.min(Math.max(0, Math.floor(Number(item.useCount) || 0)), 999) * 1000 +
						Math.max(0, Math.floor(Number(item.lastUsedAt) || 0));
					if (score > bestScore) {
						best = item;
						bestScore = score;
					}
				});
				return best;
			},
			collectRecentAssistantExpressionStats(limit, excludeMessageId) {
				const max = Math.max(
					0,
					Math.min(Math.floor(Number(limit) || LOCAL_EXPRESSION_RECENT_AVOID_LIMIT), LOCAL_EXPRESSION_HINT_LIMIT)
				);
				if (!max) return [];
				const library =
					Array.isArray(this.expressionLibrary) && this.expressionLibrary.length
						? this.expressionLibrary
						: this.readLocalExpressionEntries();
				const safeExcludeId = this.normalizeDbMessageId(excludeMessageId);
				const statsMap = {};
				const ordered = [];
				for (let i = (this.messages && this.messages.length ? this.messages.length : 0) - 1; i >= 0; i--) {
					const row = this.messages[i];
					if (!row || row.role !== 'char') continue;
					const rowId = this.normalizeDbMessageId(row.id);
					if (safeExcludeId && rowId === safeExcludeId) continue;
					const urls = Array.isArray(row.imageUrls) ? row.imageUrls : [];
					if (!urls.length) continue;
					const matched = this.findLocalExpressionByImageUrl(urls[0], library);
					if (!matched) continue;
					const hint = this.normalizeLocalExpressionHint(matched.content || matched.label);
					const key = this.normalizeAssistantExpressionKeyword(hint);
					if (!hint || !key) continue;
					if (!statsMap[key]) {
						if (ordered.length >= max) {
							continue;
						}
						statsMap[key] = {
							hint,
							key,
							count: 0,
							lastUsedAt: Math.max(0, Math.floor(Number(matched.lastUsedAt) || 0)),
							useCount: Math.max(0, Math.floor(Number(matched.useCount) || 0))
						};
						ordered.push(statsMap[key]);
					}
					statsMap[key].count += 1;
				}
				return ordered;
			},
			findLocalExpressionByImageUrl(imageUrl, library) {
				const targetUrl = String(imageUrl == null ? '' : imageUrl).trim();
				if (!targetUrl) return null;
				const source =
					Array.isArray(library) && library.length ? library : this.readLocalExpressionEntries();
				let best = null;
				let bestScore = -1;
				source.forEach((item) => {
					if (!item || !item.imageUrl || item.imageUrl !== targetUrl) return;
					const score =
						Math.min(Math.max(0, Math.floor(Number(item.useCount) || 0)), 999) * 1000 +
						Math.max(0, Math.floor(Number(item.lastUsedAt) || 0));
					if (score > bestScore) {
						best = item;
						bestScore = score;
					}
				});
				return best;
			},
			collectRecentAssistantExpressionHints(limit, excludeMessageId) {
				return this.collectRecentAssistantExpressionStats(limit, excludeMessageId).map((item) => item.hint);
			},
			buildRecentAssistantExpressionPenaltyMap(limit, excludeMessageId) {
				const stats = this.collectRecentAssistantExpressionStats(limit, excludeMessageId);
				const penalties = {};
				stats.forEach((item, index) => {
					if (!item || !item.key) return;
					const rankPenalty = Math.max(0, (stats.length - index) * 1200);
					const repeatPenalty = Math.max(0, item.count - 1) * 1800;
					penalties[item.key] = rankPenalty + repeatPenalty;
				});
				return penalties;
			},
			pickAssistantExpressionForText(text, options) {
				const opts = options || {};
				const library =
					Array.isArray(this.expressionLibrary) && this.expressionLibrary.length
						? this.expressionLibrary
						: this.readLocalExpressionEntries();
				const payload = this.extractAssistantExpressionPayload(text);
				for (let i = 0; i < payload.keywords.length; i++) {
					const matchedByMarker = this.findLocalExpressionByKeyword(payload.keywords[i], library);
					if (matchedByMarker) {
						return {
							entry: matchedByMarker,
							text: payload.text,
							via: 'marker'
						};
					}
				}
				if (payload.keywords.length) {
					return {
						entry: null,
						text: payload.text,
						via: 'marker-miss'
					};
				}
				return {
					entry: null,
					text: payload.text,
					via: ''
				};
			},
			isLocalInlineImageUrl(url) {
				return String(url == null ? '' : url).trim().indexOf('data:image/') === 0;
			},
			deleteLocalChatImageEntryByMessageId(messageId, conversationId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return false;
				const entries = this.readLocalChatImageEntries(conversationId);
				const next = entries.filter((item) => item && item.messageId !== safeId);
				if (next.length === entries.length) return false;
				this.writeLocalChatImageEntries(next, conversationId);
				return true;
			},
			applyAssistantExpressionForRow(row, options) {
				if (!row || row.role !== 'char') return null;
				const opts = options || {};
				const messageId = this.normalizeDbMessageId(row.id);
				if (!messageId || !messageId.startsWith('db_')) return null;
				const expressionAllowed = this.isCharacterAiExpressionEnabled();
				const enqueueAssistantVoice = () => {
					if (opts.voice === false) return;
					const shouldPrepare = opts.preloadVoice != null ? !!opts.preloadVoice : this.shouldAutoPrepareAssistantVoice();
					if (!shouldPrepare) return;
					this.prepareAssistantVoiceForRow(row, {
						autoplay: opts.autoplayVoice != null ? !!opts.autoplayVoice : this.shouldAutoPlayAssistantVoice(),
						force: opts.forceVoice === true,
						toastOnError: !!opts.toastVoiceError
					});
				};
				const matchedResult = expressionAllowed
					? this.pickAssistantExpressionForText(row.text, {
						excludeMessageId: messageId,
						recentLimit: opts.recentLimit
					})
					: {
						entry: null,
						text: this.extractAssistantExpressionPayload(row.text).text,
						via: ''
					};
				const matched = matchedResult && matchedResult.entry ? matchedResult.entry : null;
				const cleanText =
					matchedResult && typeof matchedResult.text === 'string' ? matchedResult.text : String(row.text || '');
				if (cleanText !== String(row.text || '')) {
					this.$set(row, 'text', cleanText);
				}
				if (Array.isArray(row.swipes) && row.swipes.length) {
					const nextSwipes = row.swipes.map((item) => this.extractAssistantExpressionPayload(item).text);
					this.$set(row, 'swipes', nextSwipes);
				}
				if (!matched || !matched.imageUrl) {
					this.deleteLocalChatImageEntryByMessageId(messageId);
					if (
						Array.isArray(row.imageUrls) &&
						row.imageUrls.length &&
						row.imageUrls.every((item) => this.isLocalInlineImageUrl(item))
					) {
						this.$set(row, 'imageUrls', []);
					}
					enqueueAssistantVoice();
					return null;
				}
				this.upsertLocalChatImageEntry({
					messageId,
					assistantMessageId: '',
					text: '',
					imageUrls: [matched.imageUrl],
					createdAt: Date.now()
				});
				this.$set(row, 'imageUrls', [matched.imageUrl]);
				if (opts.touchUsage !== false) {
					this.touchLocalExpressionUsage(matched.id);
				}
				enqueueAssistantVoice();
				return matched;
			},
			normalizeChatImageUrls(list) {
				if (!Array.isArray(list) || !list.length) return [];
				const tavernApi = require('@/common/tavernApi.js');
				return list
					.map((item) => tavernApi.resolveJgAssetUrl(item))
					.filter((item) => item && String(item).trim() !== '');
			},
			previewChatMessageImages(message, index) {
				const urls = this.normalizeChatImageUrls(message && message.imageUrls);
				if (!urls.length) return;
				uni.previewImage({
					urls,
					current: urls[Math.max(0, Math.min(Number(index) || 0, urls.length - 1))]
				});
			},
			previewPendingChatImage(item) {
				if (!item || !item.previewUrl) return;
				uni.previewImage({
					urls: this.composerImages.map((entry) => entry.previewUrl).filter(Boolean),
					current: item.previewUrl
				});
			},
			openChatAttachmentMenu() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				if (this.attachmentMenuVisible) {
					this.closeChatAttachmentMenu();
					return;
				}
				this.closeCharacterImagePanel();
				this.closeReplySuggestions();
				this.closeExpressionPanel();
				this.attachmentMenuVisible = true;
			},
			pickChatImages(sourceType) {
				if (!this.ensureCanUseChatImages()) return;
				if (sourceType === 'camera' && !this.ensureAppCameraReady()) return;
				this.closeChatAttachmentMenu();
				const maxCount = Math.max(0, 4 - this.composerImages.length);
				if (maxCount <= 0) {
					uni.showToast({
						title: this.tx('chat_image_limit', '一次最多添加 4 张图片'),
						icon: 'none'
					});
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
			const maxBytes = Number(tavernApi.getUploadMaxFileBytes ? tavernApi.getUploadMaxFileBytes() : 28 * 1024 * 1024);
				uni.chooseImage({
					count: maxCount,
					sizeType: ['compressed'],
					sourceType: [sourceType === 'camera' ? 'camera' : 'album'],
					success: (res) => {
						const picked = this.extractPickedChatImages(res, maxBytes);
						if (!picked.length) {
							return;
						}
						this.appendComposerImages(picked);
					},
					fail: (err) => {
						this.handleChooseImageFailure(err, sourceType);
					}
				});
			},
			ensureAppCameraReady() {
				if (!this.isAppPlus) return true;
				try {
					if (typeof plus !== 'undefined' && plus.camera && typeof plus.camera.getCamera === 'function') {
						return true;
					}
				} catch (e) {}
				uni.showToast({
					title: this.tx('camera_module_missing', '当前安装包未包含相机模块，请重新打包 APP'),
					icon: 'none',
					duration: 3200
				});
				return false;
			},
			handleChooseImageFailure(err, sourceType) {
				const rawMessage =
					(err && (err.errMsg || err.message)) ||
					this.jgErrMsg(err, '') ||
					'';
				const message = String(rawMessage || '').trim();
				const lower = message.toLowerCase();
				if (!message) return;
				if (lower.indexOf('cancel') >= 0 || lower.indexOf('canceled') >= 0 || message.indexOf('取消') >= 0) {
					return;
				}
				if (sourceType === 'camera' && (lower.indexOf('camera') >= 0 || message.indexOf('模块') >= 0)) {
					this.showErrorToast(this.tx('camera_module_missing', '当前安装包未包含相机模块，请重新打包 APP'));
					return;
				}
				this.showErrorToast(
					sourceType === 'camera'
						? this.tx('camera_pick_failed', '打开相机失败，请检查相机权限或重新安装 APP')
						: this.tx('album_pick_failed', '选择图片失败，请稍后再试')
				);
			},
			extractPickedChatImages(result, maxBytes) {
				const tempPaths = Array.isArray(result && result.tempFilePaths) ? result.tempFilePaths : [];
				const tempFiles = Array.isArray(result && result.tempFiles) ? result.tempFiles : [];
				const list = [];
				for (let i = 0; i < tempPaths.length; i++) {
					const tempFile = tempFiles[i] || {};
					const size = Number(tempFile.size || 0);
					if (size > 0 && maxBytes > 0 && size > maxBytes) {
						uni.showToast({
							title: this.tx('chat_image_too_large', '图片过大，请压缩后再试'),
							icon: 'none'
						});
						continue;
					}
					const localPath = tempPaths[i];
					const uploadFile = tempFile.file || localPath;
					if (!localPath || !uploadFile) {
						continue;
					}
					list.push({
						id: 'ci_' + Date.now() + '_' + i + '_' + Math.random().toString(36).slice(2, 8),
						previewUrl: localPath,
						uploadFile
					});
				}
				return list;
			},
			appendComposerImages(list) {
				if (!Array.isArray(list) || !list.length) return;
				const next = this.composerImages.slice();
				list.forEach((entry) => {
					if (!entry || next.length >= 4) {
						return;
					}
					next.push({
						id: entry.id,
						previewUrl: entry.previewUrl,
						uploadFile: entry.uploadFile,
						uploadedUrl: '',
						progress: 0,
						uploading: true,
						error: ''
					});
				});
				this.composerImages = next;
				list.forEach((entry) => {
					if (entry) {
						this.uploadComposerImage(entry.id, entry.uploadFile);
					}
				});
			},
			updateComposerImage(id, patch) {
				const index = this.composerImages.findIndex((item) => item && item.id === id);
				if (index < 0) return;
				this.$set(this.composerImages, index, Object.assign({}, this.composerImages[index], patch || {}));
			},
			uploadComposerImage(id, uploadFile) {
				const tavernApi = require('@/common/tavernApi.js');
				tavernApi
					.prepareLocalChatImage(uploadFile, (progress) => {
						this.updateComposerImage(id, {
							progress: Number(progress) || 0
						});
					})
					.then((data) => {
						const uploadedUrl = data && data.url ? String(data.url).trim() : '';
						if (!uploadedUrl) {
							throw new Error(this.tx('chat_image_upload_failed', '图片上传失败'));
						}
						if (
							this.isLocalInlineImageUrl(uploadedUrl) &&
							uploadedUrl.length > LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH
						) {
							throw new Error(this.tx('chat_image_too_large_cache', '图片过大，建议裁剪后再试'));
						}
						this.updateComposerImage(id, {
							uploadedUrl,
							progress: 100,
							uploading: false,
							error: ''
						});
					})
					.catch((error) => {
						this.updateComposerImage(id, {
							uploading: false,
							error: this.jgErrMsg(error, this.tx('chat_image_upload_failed', '图片上传失败'))
						});
					});
			},
			removeComposerImage(id) {
				this.composerImages = this.composerImages.filter((item) => item && item.id !== id);
			},
			pendingChatImageUrls() {
				return this.composerImages
					.map((item) => (item && item.uploadedUrl ? String(item.uploadedUrl).trim() : ''))
					.filter((item) => item);
			},
			hasUploadingComposerImages() {
				return this.composerImages.some((item) => item && item.uploading);
			},
			normalizeChatRow(m) {
				if (!m) return m;
				const quotePayload = this.extractQuotedMessagePayload(m.text, m.role);
				const swipes =
					Array.isArray(m.swipes) && m.swipes.length
						? m.swipes.map((s) => this.extractAssistantExpressionPayload(String(s)).text)
						: quotePayload.text != null && String(quotePayload.text) !== ''
							? [this.extractAssistantExpressionPayload(String(quotePayload.text)).text]
							: [];
				let si = typeof m.swipeIndex === 'number' ? m.swipeIndex : 0;
				if (swipes.length && si >= swipes.length) si = swipes.length - 1;
				const text = swipes.length
					? String(swipes[si] != null ? swipes[si] : '')
					: this.extractAssistantExpressionPayload(String(quotePayload.text || '')).text;
				const imageUrls = this.normalizeChatImageUrls(m.imageUrls || m.images || []);
				return {
					id: this.normalizeDbMessageId(m.id),
					role: m.role,
					text,
					quote: quotePayload.quote,
					messageKind: String(m.messageKind || 'NORMAL').trim().toUpperCase() === 'CONTINUATION' ? 'CONTINUATION' : 'NORMAL',
					continueFromMessageId: this.normalizeDbMessageId(m.continueFromMessageId),
					swipes,
					swipeIndex: si,
					imageUrls,
					localKind: this.normalizeCharacterImageText(m.localKind, 40),
					localPrompt: this.normalizeCharacterImageText(m.localPrompt || m.prompt, 300),
					localOnly: m.localOnly === true,
					voiceUrl: this.normalizeVoiceMessageUrl(m.voiceUrl),
					voiceDurationMs: this.normalizeVoiceDurationMs(m.voiceDurationMs)
				};
			},
			clearGenerationRecovery() {
				this.generationRecovery = {
					visible: false,
					messageId: '',
					mode: 'retry',
					title: '',
					message: '',
					canContinue: false,
					canRegen: false,
					retryText: ''
				};
			},
			markGenerationRecovery(messageId, options) {
				const safeId = this.normalizeDbMessageId(messageId);
				const opts = options && typeof options === 'object' ? options : {};
				if (!safeId) {
					this.clearGenerationRecovery();
					return;
				}
				const hasText = !!String(opts.partialText || '').trim();
				this.generationRecovery = {
					visible: true,
					messageId: safeId,
					mode: hasText ? 'continue' : 'retry',
					title: hasText
						? this.tx('generation_recovery_partial_title', '回复中断了')
						: this.tx('generation_recovery_empty_title', '生成失败了'),
					message:
						opts.message ||
						(hasText
							? this.tx('generation_recovery_partial_desc', '已保留目前生成的内容，可以继续接上。')
							: this.tx('generation_recovery_empty_desc', '这轮没有拿到回复，可以重新试一次。')),
					canContinue: hasText,
					canRegen: !!opts.canRegen,
					retryText: this.normalizeDraftText(opts.retryText || '')
				};
			},
			recoveryForMessage(message) {
				if (!message || !this.generationRecovery || !this.generationRecovery.visible) {
					return null;
				}
				const messageId = this.normalizeDbMessageId(message.id);
				return messageId && messageId === this.generationRecovery.messageId ? this.generationRecovery : null;
			},
			recoveryPrimaryLabel() {
				const recoveryId = this.normalizeDbMessageId(this.generationRecovery && this.generationRecovery.messageId);
				return this.generationRecovery && this.generationRecovery.canContinue && recoveryId.startsWith('db_')
					? this.tx('continue', '继续生成')
					: this.tx('retry', '重试');
			},
			runGenerationRecoveryPrimary() {
				if (!this.generationRecovery || !this.generationRecovery.visible) return;
				const recoveryId = this.normalizeDbMessageId(this.generationRecovery.messageId);
				if (this.generationRecovery.canContinue && recoveryId.startsWith('db_')) {
					this.onContinue();
				} else {
					this.retryGenerationFromRecovery();
				}
			},
			runGenerationRecoveryRegen() {
				if (!this.generationRecovery || !this.generationRecovery.visible) return;
				const recoveryId = this.normalizeDbMessageId(this.generationRecovery.messageId);
				if (!recoveryId.startsWith('db_')) {
					this.retryGenerationFromRecovery();
					return;
				}
				this.onRegen();
			},
			findMessageById(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				return (this.messages || []).find((row) => this.normalizeDbMessageId(row && row.id) === safeId) || null;
			},
			copyGenerationRecoveryText(message) {
				const text = String(message && message.text || '').trim();
				if (!text) return;
				uni.setClipboardData({
					data: text,
					success: () => {
						uni.showToast({ title: this.tx('copy_success', '已复制'), icon: 'none' });
					},
					fail: () => {
						this.showErrorToast(this.tx('message_copy_failed', '复制失败，请重试'));
					}
				});
			},
			retryGenerationFromRecovery() {
				if (!this.generationRecovery || !this.generationRecovery.visible) return false;
				const retryText = this.normalizeDraftText(this.generationRecovery.retryText || '');
				if (!retryText.trim()) {
					const recoveryRow = this.findMessageById(this.generationRecovery.messageId);
					if (recoveryRow && String(recoveryRow.text || '').trim()) {
						this.onContinue();
						return true;
					}
					this.onRegen();
					return true;
				}
				const recoveryId = this.generationRecovery.messageId;
				const rows = Array.isArray(this.messages) ? this.messages.slice() : [];
				const recoveryIndex = rows.findIndex((row) => this.normalizeDbMessageId(row && row.id) === recoveryId);
				if (recoveryIndex > 0) {
					const prev = rows[recoveryIndex - 1];
					const prevId = this.normalizeDbMessageId(prev && prev.id);
					if (
						prev &&
						prev.role === 'user' &&
						!prevId.startsWith('db_') &&
						String(prev.text || '').trim() === retryText.trim()
					) {
						rows.splice(recoveryIndex - 1, 2);
					} else {
						rows.splice(recoveryIndex, 1);
					}
				} else if (recoveryIndex === 0) {
					rows.splice(0, 1);
				}
				this.messages = rows;
				this.clearGenerationRecovery();
				return this.submitOutgoingMessage(retryText, [], {
					clearDraft: false,
					clearComposerImages: false,
					clearQuote: false,
					quoteMeta: createComposerQuoteState(),
					checkUploading: false,
					allowWhenNotAtBottom: true,
					skipDraftClear: true
				});
			},
			invalidateReplySuggestions() {
				this.replySuggest = {
					visible: false,
					loading: false,
					error: '',
					items: [],
					contextKey: ''
				};
			},
			closeReplySuggestions() {
				this.replySuggest.visible = false;
				this.replySuggest.loading = false;
				this.replySuggest.error = '';
			},
			currentReplySuggestionKey() {
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					return '';
				}
				const last = this.messages && this.messages.length ? this.messages[this.messages.length - 1] : null;
				const lastText = last && last.text ? String(last.text) : '';
				return [String(this.cid || ''), String(anchor.targetAssistantMessageId || ''), lastText].join('|');
			},
			canOpenReplySuggestions() {
				if (this.sending || !this.jgOn || !this.char || this.jgChatLoadState !== 'ready') {
					return false;
				}
				return this.assistantTailActionState().ok;
			},
			canShowReplyHelpTrigger() {
				return this.jgOn && this.char && this.jgChatLoadState === 'ready' && this.assistantTailActionState().ok;
			},
			shouldShowReplyHelpPanel() {
				return !!this.replySuggest.visible && this.canShowReplyHelpTrigger();
			},
			toggleReplySuggestions() {
				this.closeChatAttachmentMenu();
				this.closeExpressionPanel();
				if (!this.canOpenReplySuggestions()) {
					const anchor = this.assistantTailActionState();
					if (anchor.reason === 'sending') {
						uni.showToast({ title: this.tx('reply_help_wait', '等这轮回复完成后再使用帮答'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('reply_help_need_ai', '需要先有一条可用的 AI 回复'), icon: 'none' });
					}
					return;
				}
				if (this.replySuggest.visible) {
					this.closeReplySuggestions();
					return;
				}
				this.draftRestoredNoticeVisible = false;
				this.replySuggest.visible = true;
				this.refreshReplySuggestions(false);
			},
			refreshReplySuggestions(force) {
				if (!this.canOpenReplySuggestions()) {
					return Promise.resolve([]);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const key = this.currentReplySuggestionKey();
				if (!force && this.replySuggest.contextKey === key && this.replySuggest.items.length) {
					this.replySuggest.visible = true;
					return Promise.resolve(this.replySuggest.items);
				}
				this.replySuggest.visible = true;
				this.replySuggest.loading = true;
				this.replySuggest.error = '';
				return tavernApi
					.fetchTavernReplySuggestions({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						content: String(this.draft || '').trim()
					})
					.then((items) => {
						const list = Array.isArray(items)
							? items
									.map((item) => String(item == null ? '' : item).trim())
									.filter((item) => item !== '')
							: [];
						if (!list.length) {
							throw new Error(this.tx('reply_help_empty', '这次没有拿到可用建议，换一批再试试'));
						}
						this.replySuggest.items = list;
						this.replySuggest.contextKey = key;
						this.replySuggest.error = '';
						return list;
					})
					.catch((e) => {
						this.replySuggest.items = [];
						this.replySuggest.error = this.jgErrMsg(
							e,
							this.tx('reply_help_failed', 'AI帮答暂时不可用，请稍后再试')
						);
						return [];
					})
					.finally(() => {
						this.replySuggest.loading = false;
					});
			},
			applyReplySuggestion(text) {
				const value = String(text || '').trim();
				if (!value) return;
				this.draft = value;
				this.scheduleDraftSave(value);
				this.inputFocus = true;
				this.replySuggest.visible = false;
			},
			clearChatUiTimers() {
				if (this.chatAnimationTimer) {
					clearTimeout(this.chatAnimationTimer);
					this.chatAnimationTimer = null;
				}
				if (this.chatRevealTimer) {
					clearTimeout(this.chatRevealTimer);
					this.chatRevealTimer = null;
				}
				this.clearPendingVoiceStart();
				this.clearVoiceRecordTimer();
			},
			scrollChatToBottom(options) {
				const opts = options || {};
				const immediate = opts.immediate !== false;
				const reveal = !!opts.reveal;
				this.clearChatUiTimers();
				this.followBottom = true;
				this.atChatBottom = true;
				this.lastChatScrollTop = Number.MAX_SAFE_INTEGER;
				this.markChatAutoScroll();
				this.chatScrollWithAnimation = !immediate;
				this.scrollTo = '';
				this.$nextTick(() => {
					this.markChatAutoScroll();
					this.scrollTo = 'bottom-anchor';
					if (immediate) {
						this.chatAnimationTimer = setTimeout(() => {
							this.chatScrollWithAnimation = true;
							this.chatAnimationTimer = null;
						}, 48);
					}
					if (reveal) {
						this.chatRevealTimer = setTimeout(() => {
							this.chatViewportReady = true;
							this.chatRevealTimer = null;
						}, immediate ? 56 : 220);
					}
				});
			},
			onPrimaryAction() {
				if (!this.atChatBottom) {
					this.scrollChatToBottom({ immediate: true });
					return;
				}
				this.send();
			},
			followScrollNextTick() {
				if (!this.followBottom) return;
				this.scrollChatToBottom({ immediate: true });
			},
			showTypingHintRow() {
				return !!this.sending && !this.streamingAssistantMessageId;
			},
			beginAssistantStreaming(messageId, mode) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return '';
				this.streamingAssistantMessageId = safeId;
				this.streamingAssistantMode = String(mode || 'generate').trim() || 'generate';
				return safeId;
			},
			moveAssistantStreamingMessageId(fromMessageId, toMessageId) {
				const fromId = this.normalizeDbMessageId(fromMessageId);
				const nextId = this.normalizeDbMessageId(toMessageId);
				if (!fromId || !nextId || fromId === nextId) return nextId || fromId;
				if (this.streamingAssistantMessageId === fromId) {
					this.streamingAssistantMessageId = nextId;
				}
				return nextId;
			},
			finishAssistantStreaming(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId || this.streamingAssistantMessageId === safeId) {
					this.streamingAssistantMessageId = '';
					this.streamingAssistantMode = '';
				}
			},
			isStreamingAssistantRow(row) {
				if (!row || row.role !== 'char') return false;
				const messageId = this.normalizeDbMessageId(row.id);
				return !!messageId && messageId === this.streamingAssistantMessageId;
			},
			streamingAssistantStatusText(row) {
				const mode = String(this.streamingAssistantMode || 'generate').trim();
				const hasText = !!String(row && row.text || '').trim();
				if (mode === 'continue') {
					return hasText ? this.tx('streaming_continue', '正在续写') : this.tx('streaming_waiting', '正在接续');
				}
				if (mode === 'regenerate') {
					return hasText ? this.tx('streaming_regenerate', '正在重生') : this.tx('streaming_waiting', '正在接续');
				}
				return hasText ? this.tx('streaming_reply', '正在回复') : this.tx('streaming_waiting', '正在接入');
			},
			notifyCompanionThinking() {
				companionStore.emitThinking(this.tx('streaming_waiting', '正在接入'));
			},
			notifyCompanionReplying(mode) {
				const safeMode = String(mode || 'generate').trim();
				if (safeMode === 'continue') {
					companionStore.emitReplying(this.tx('streaming_continue', '正在续写'));
					return;
				}
				if (safeMode === 'regenerate') {
					companionStore.emitReplying(this.tx('streaming_regenerate', '正在重生'));
					return;
				}
				companionStore.emitReplying(this.tx('streaming_reply', '正在回复'));
			},
			notifyCompanionReply(text) {
				const speechText = this.extractAssistantSpeechText(text);
				if (speechText) {
					companionStore.emitReply(speechText);
				}
			},
			notifyCompanionError(message) {
				companionStore.emitError(message || this.tx('chat_failed', '对话失败'));
			},
			markChatAutoScroll() {
				this.chatAutoScrollAt = Date.now();
			},
			onChatTouchStart() {
				this.chatUserTouching = true;
			},
			onChatTouchMove(event) {
				this.chatUserTouching = true;
				this.moveMessageActionPress(event);
			},
			onChatTouchEnd() {
				setTimeout(() => {
					this.chatUserTouching = false;
				}, 120);
			},
			onChatScroll(e) {
				const d = (e && e.detail) || {};
				const top = Number(d.scrollTop);
				const height = Number(d.scrollHeight);
				const clientHeight = Number(d.clientHeight != null ? d.clientHeight : d.height);
				const hasTop = Number.isFinite(top);
				const movedUp = hasTop ? top < this.lastChatScrollTop - 3 : false;
				const fallbackManualMove = !hasTop && typeof d.deltaY === 'number' && Math.abs(d.deltaY) > 2;
				const isManualWindow = Date.now() - this.chatAutoScrollAt > 180;
				if (isManualWindow && (movedUp || (this.chatUserTouching && fallbackManualMove))) {
					this.followBottom = false;
					this.atChatBottom = false;
					this.closeExpressionPanel();
				}
				if (hasTop && Number.isFinite(height) && Number.isFinite(clientHeight)) {
					const distanceToBottom = height - clientHeight - top;
					if (distanceToBottom <= 64) {
						this.followBottom = true;
						this.atChatBottom = true;
					}
				}
				if (hasTop) {
					this.lastChatScrollTop = top;
					if (top <= 24) {
						this.maybeLoadOlderMessages('scroll-top');
					}
				}
			},
			onChatScrollToUpper() {
				this.maybeLoadOlderMessages('upper-threshold');
			},
			onChatScrollToLower() {
				this.followBottom = true;
				this.atChatBottom = true;
			},
			normalizeDbMessageId(id) {
				if (id == null) return '';
				const s = String(id).trim();
				if (s.startsWith('db_')) return s;
				if (/^\d+$/.test(s)) return 'db_' + s;
				return s;
			},
			lastAssistantTargetPayload() {
				const n = this.messages.length;
				if (n === 0) return { ok: false, reason: 'empty' };
				const last = this.messages[n - 1];
				if (!last || last.role !== 'char') {
					return { ok: false, reason: 'not_char' };
				}
				if (!String(last.text || '').trim()) {
					return { ok: false, reason: 'empty_char' };
				}
				const nid = this.normalizeDbMessageId(last.id);
				if (!nid.startsWith('db_')) {
					return { ok: false, reason: 'pending_sync' };
				}
				return { ok: true, targetAssistantMessageId: nid };
			},
			assistantTailActionState() {
				if (this.sending) {
					return { ok: false, reason: 'sending' };
				}
				if (!this.jgOn || !this.char) {
					return { ok: false, reason: 'unready' };
				}
				const anchor = this.lastAssistantTargetPayload();
				if (!anchor.ok) {
					return anchor;
				}
				return anchor;
			},
			assistantTailActionHint() {
				const state = this.assistantTailActionState();
				if (state.ok) {
					return '';
				}
				if (state.reason === 'sending') {
					return this.tx('tail_sending', '当前回复还在生成中，等这一轮结束后才能续写或重生。');
				}
				if (state.reason === 'empty') {
					return this.tx('tail_empty', '续写和重生都需要先有一条 AI 回复，空白会话不能直接使用。');
				}
				if (state.reason === 'pending_sync') {
					return this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。');
				}
				if (state.reason === 'empty_char') {
					return this.tx('tail_empty_char', '最后一条 AI 回复内容为空，暂时不能续写或重生。');
				}
				if (state.reason === 'not_char') {
					return this.tx('tail_not_char', '续写和重生只作用于当前会话最后一条 AI 回复。');
				}
				return '';
			},
			clearStopSyncTimer() {
				if (this.stopRefreshTimer) {
					clearTimeout(this.stopRefreshTimer);
					this.stopRefreshTimer = null;
				}
			},
			queueStopSync(delay) {
				if (!this.jgOn || !this.char) return;
				this.clearStopSyncTimer();
				this.stopRefreshTimer = setTimeout(() => {
					this.stopRefreshTimer = null;
					this.refreshJgMessages()
						.then(() => {
							this.followScrollNextTick();
						})
						.catch(() => {});
				}, typeof delay === 'number' ? delay : 700);
			},
			clearPendingVoiceStart() {
				if (this.pendingVoiceStartTimer) {
					clearTimeout(this.pendingVoiceStartTimer);
					this.pendingVoiceStartTimer = null;
				}
				this.pendingVoiceStartAt = 0;
			},
			markSilentGenerationInterrupt(durationMs) {
				const ttl = Number(durationMs || 0);
				this.silentGenerationInterruptUntil = Date.now() + (ttl > 0 ? ttl : 4200);
			},
			isSilentGenerationInterruptActive() {
				return Number(this.silentGenerationInterruptUntil || 0) > Date.now();
			},
			showGenerationStopToast(key, fallback) {
				if (this.isSilentGenerationInterruptActive()) {
					return false;
				}
				uni.showToast({
					title: this.tx(key, fallback),
					icon: 'none'
				});
				return true;
			},
			interruptAssistantVoiceRound(options) {
				const opts = options && typeof options === 'object' ? options : {};
				if (opts.stopUserVoice !== false && this.userVoicePlayingMessageId) {
					this.stopUserVoicePlayback();
				}
				if (this.assistantVoicePlayingMessageId) {
					this.stopAssistantVoicePlayback();
				}
				const next = {};
				Object.keys(this.assistantVoiceStateMap || {}).forEach((messageId) => {
					const entry = this.assistantVoiceStateMap[messageId];
					if (!entry || typeof entry !== 'object') return;
					next[messageId] = Object.assign({}, entry, {
						requestKey: '',
						autoPlayPending: false,
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						state: this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : 'idle'
					});
				});
				this.assistantVoicePlayingMessageId = '';
				this.assistantVoiceStateMap = next;
			},
			resetConversationVoiceRuntimeState() {
				this.clearPendingVoiceStart();
				this.silentGenerationInterruptUntil = 0;
				if (this.userVoicePlayingMessageId) {
					this.stopUserVoicePlayback();
				}
				if (this.assistantVoicePlayingMessageId || Object.keys(this.assistantVoiceStateMap || {}).length) {
					this.interruptAssistantVoiceRound({ stopUserVoice: false });
				}
				Object.keys(this.userVoiceStateMap || {}).forEach((messageId) => {
					this.clearUserVoiceEntry(messageId);
				});
				this.userVoiceStateMap = {};
				this.userVoicePlayingMessageId = '';
				this.assistantVoiceStateMap = {};
				this.assistantVoicePlayingMessageId = '';
			},
			startVoiceRecordingAfterStop() {
				this.clearPendingVoiceStart();
				this.pendingVoiceStartAt = Date.now();
				const tryStart = () => {
					if (!this.isVoiceFeatureEnabledGlobal()) {
						this.clearPendingVoiceStart();
						return;
					}
					if (this.voiceRecording || this.voiceStopping || this.voiceTranscribing) {
						this.clearPendingVoiceStart();
						return;
					}
					if (this.sending || this.streamAbortController) {
						if (Date.now() - Number(this.pendingVoiceStartAt || 0) >= 3200) {
							this.clearPendingVoiceStart();
							this.showErrorToast(this.tx('voice_wait_stop_failed', '当前回复还没停下来，请再试一次'));
							return;
						}
						this.pendingVoiceStartTimer = setTimeout(tryStart, 120);
						return;
					}
					this.clearPendingVoiceStart();
					this.startVoiceRecording();
				};
				tryStart();
			},
			patchLastOptimisticUserId(userMessageId, assistantMessageId) {
				const uid = this.normalizeDbMessageId(userMessageId);
				const aid = this.normalizeDbMessageId(assistantMessageId);
				for (let i = this.messages.length - 1; i >= 0; i--) {
					const row = this.messages[i];
					if (row && row.role === 'user' && String(row.id).indexOf('u_') === 0) {
						const fromId = String(row.id);
						const nextId = uid.startsWith('db_') ? uid : fromId;
						if (nextId !== fromId) {
							this.$set(row, 'id', nextId);
						}
						this.updateUserVoiceEntryId(fromId, nextId);
						this.updateLocalChatImageEntryIds(fromId, nextId, aid);
						return nextId;
					}
				}
				if (uid.startsWith('db_')) {
					this.updateUserVoiceEntryId(uid, uid);
					this.updateLocalChatImageEntryIds(uid, uid, aid);
					return uid;
				}
				return '';
			},
			stopGeneration(options) {
				const opts = options && typeof options === 'object' ? options : {};
				if (opts.silent) {
					this.markSilentGenerationInterrupt(opts.silentDurationMs);
				}
				this.interruptAssistantVoiceRound({ stopUserVoice: false });
				this.queueStopSync(700);
				if (this.streamAbortController) {
					try {
						this.streamAbortController.abort();
					} catch (err) {}
					this.streamAbortController = null;
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					if (tavernApi.jgEnabled() && this.char && this.cid) {
						const cid = Number(this.char && this.char.id) || Number(this.cid);
						tavernApi
							.postTavernChatStop({
								characterId: cid,
								clientUid: tavernApi.getClientUid()
							})
							.finally(() => {
								this.queueStopSync(900);
							})
							.catch(function () {});
					}
				} catch (e) {}
			},
			swipeLabel(m) {
				const s = (m && m.swipes) || [];
				const i = typeof m.swipeIndex === 'number' ? m.swipeIndex : 0;
				const safe = s.length ? Math.min(Math.max(0, i), s.length - 1) : 0;
				return s.length ? safe + 1 + '/' + s.length : '';
			},
			mergeContinuationText(prefix, suffix) {
				const base = String(prefix || '');
				const ext = String(suffix || '');
				if (!ext) return base;
				if (!base) return ext.replace(/^\s+/, '');
				const last = base.slice(-1);
				const first = ext.charAt(0);
				if (/\s/.test(last) || /\s/.test(first)) return base + ext;
				if (/[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/.test(last) || /[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/.test(first)) {
					return base + ext;
				}
				if (',.;:!?)]}"\''.indexOf(first) >= 0) {
					return base + ext;
				}
				if (/[A-Za-z0-9]$/.test(last) && /^[A-Za-z0-9]/.test(first)) {
					return base + ' ' + ext.replace(/^\s+/, '');
				}
				return base + ext;
			},
			swipeCharMessage(m, delta) {
				if (this.sending || !this.jgOn || !m || m.role !== 'char') return;
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				tavernApi
					.postTavernSwipeSelect({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						messageId: this.normalizeDbMessageId(m.id),
						delta: delta
					})
					.then((d) => {
						if (!d) return;
						const row = this.normalizeChatRow({
							id: d.id || m.id,
							role: d.role || 'char',
							text: d.text,
							messageKind: d.messageKind || m.messageKind,
							continueFromMessageId: d.continueFromMessageId || m.continueFromMessageId,
							swipes: d.swipes,
							swipeIndex: d.swipeIndex
						});
						this.applyAssistantExpressionForRow(row);
						const idx = this.messages.indexOf(m);
						if (idx >= 0) {
							this.$set(this.messages, idx, row);
						}
					})
					.catch((e) => {
						uni.showToast({ title: this.jgErrMsg(e, this.tx('swipe_failed', '切换失败')), icon: 'none' });
					});
			},
			refreshJgMessages() {
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				return tavernApi.fetchTavernMessages(cid, tavernApi.getClientUid(), {
					limit: TAVERN_MESSAGES_INITIAL_LIMIT
				}).then((pack) => {
					this.applyMessagesEnvelope(pack);
				});
			},
			canEditUserMessage(m) {
				if (!this.jgOn || !m || m.role !== 'user') return false;
				const id = this.normalizeDbMessageId(m.id);
				return id.indexOf('db_') === 0;
			},
			openEditUserMessage(m) {
				if (this.sending || !this.canEditUserMessage(m)) return;
				this.closeMessageActionSheet();
				this.editOverlay = {
					visible: true,
					messageId: this.normalizeDbMessageId(m.id),
					draft: String(m.text || ''),
					saving: false,
					imageUrls: Array.isArray(m.imageUrls) ? m.imageUrls.slice() : [],
					quoteMeta: this.normalizeComposerQuoteMeta(m.quote),
					voiceUrl: this.normalizeVoiceMessageUrl(m.voiceUrl),
					voiceDurationMs: this.normalizeVoiceDurationMs(m.voiceDurationMs)
				};
			},
			closeEditUser(force) {
				if (this.editOverlay.saving && force !== true) return;
				this.editOverlay = createEditOverlayState();
			},
			submitEditUser() {
				if (this.editOverlay.saving) return;
				const messageId = this.normalizeDbMessageId(this.editOverlay.messageId);
				const text = String(this.editOverlay.draft || '').trim();
				const imageUrls = Array.isArray(this.editOverlay.imageUrls) ? this.editOverlay.imageUrls.slice() : [];
				const quoteMeta = this.normalizeComposerQuoteMeta(this.editOverlay.quoteMeta);
				const voiceUrl = this.normalizeVoiceMessageUrl(this.editOverlay.voiceUrl);
				const voiceDurationMs = this.normalizeVoiceDurationMs(this.editOverlay.voiceDurationMs);
				if (!text && !imageUrls.length && !voiceUrl) {
					uni.showToast({ title: this.tx('save_empty', '内容不能为空'), icon: 'none' });
					return;
				}
				this.editOverlay.saving = true;
				this.deleteMessageBranch(messageId, {
					toastOnError: false
				})
					.then((ok) => {
						if (!ok) {
							this.showErrorToast(this.tx('save_failed', '保存失败'));
							return;
						}
						this.closeEditUser(true);
						const sent = this.submitOutgoingMessage(text, imageUrls, {
							allowWhenNotAtBottom: true,
							checkUploading: false,
							quoteMeta,
							userVoiceMeta: {
								audioUrl: voiceUrl ? this.resolveVoiceMessageAudioUrl(voiceUrl) : '',
								voiceUrl,
								durationMs: voiceDurationMs
							}
						});
						if (!sent) {
							this.draft = text;
							this.showErrorToast(this.tx('edit_resend_failed', '已回档，但重新发送失败，请手动发送一次'));
							return;
						}
						uni.showToast({
							title: this.tx('edit_resend_success', '已更新，正在重新生成'),
							icon: 'none'
						});
					})
					.finally(() => {
						if (this.editOverlay && this.editOverlay.visible) {
							this.$set(this.editOverlay, 'saving', false);
						}
					});
			},
			onMarkdownTap(e) {
				/* #ifdef H5 */
				try {
					let el = e.target;
					for (let i = 0; i < 12 && el; i++) {
						const tag = el.tagName ? String(el.tagName).toUpperCase() : '';
						if (tag === 'PRE') {
							const txt = (el.textContent || el.innerText || '').trim();
							if (txt) {
								uni.setClipboardData({ data: txt });
								uni.showToast({ title: this.tx('copy_code_success', '代码已复制'), icon: 'none' });
							}
							return;
						}
						el = el.parentElement;
					}
				} catch (err) {}
				/* #endif */
			},
			applyVipGate() {
				if (this.char && !this.char.unlocked) {
					this.jgChatLoadState = 'error';
					this.jgChatErrorMsg = this.tx('need_vip', '当前角色仅会员可用');
					this.openCommercialPrompt(
						{
							title: this.tx('vip_gate_title', '当前角色需要会员权限'),
							message: this.tx('vip_gate_message', '这个角色已设置为会员专属。开通会员后即可进入聊天、续写和重生。'),
							primaryText: this.chatUi.openVip,
							primaryUrl: '/pages/user/myvip',
							secondaryText: this.chatUi.recharge,
							secondaryUrl: '/pages/user/pay'
						},
						this.jgChatErrorMsg
					);
				}
			},
			goBack() {
				this.util.safeNavigateBack('/pages/tavern/tavernInbox');
			},
			goPersona() {
				const query = this.cid ? '?id=' + encodeURIComponent(this.cid) : '';
				uni.navigateTo({ url: '/pages/tavern/chatPersona' + query });
			},
			isAssistantMessage(message) {
				if (!message) {
					return false;
				}
				const role = String(message.role || '').toLowerCase();
				return role !== 'user' && role !== 'me' && role !== 'human';
			},
			mdHtml(text) {
				const { renderChatMarkdown } = require('@/common/chatMarkdown.js');
				return renderChatMarkdown(text);
			},
			mdSegments(text) {
				const { splitChatSegments } = require('@/common/chatMarkdown.js');
				const list = typeof splitChatSegments === 'function' ? splitChatSegments(text) : [];
				const source = this.normalizeNativeSegmentSource(list, text);
				if (!source.length) {
					return [];
				}
				const normalized = source.map((item) => {
					const type = this.normalizeNativeSegmentType(item);
					return {
						type,
						text: this.normalizeNativeSegmentText(item, type)
					};
				}).filter((item) => item.text);
				return this.ensureNativeSegmentContrast(normalized);
			},
			normalizeNativeSegmentSource(list, text) {
				if (!Array.isArray(list) || !list.length) {
					return this.splitNativeFallbackSegments(text);
				}
				if (list.length === 1 && (!list[0].type || list[0].type === 'narration')) {
					return this.splitNativeFallbackSegments(list[0].text || text);
				}
				return list.reduce((acc, item) => {
					if (!item || item.type !== 'narration') {
						acc.push(item);
						return acc;
					}
					const expanded = this.splitNativeFallbackSegments(item.text);
					if (expanded.length) {
						acc.push.apply(acc, expanded);
					}
					return acc;
				}, []);
			},
			splitNativeFallbackSegments(text) {
				const raw = String(text || '').replace(/\r\n?/g, '\n').trim();
				if (!raw) {
					return [];
				}
				const lines = raw
					.split(/\n+/)
					.map((line) => line.trim())
					.filter(Boolean);
				if (!lines.length) {
					return [{ type: 'narration', text: raw }];
				}
				return lines.reduce((acc, line) => {
					acc.push.apply(acc, this.splitNativeInlineSegments(line));
					return acc;
				}, []);
			},
			splitNativeInlineSegments(line) {
				const text = String(line || '').trim();
				if (!text) {
					return [];
				}
				const result = [];
				const re = /(\*[^*\n]{2,}\*|“[^”\n]{2,}”|"[^"\n]{2,}"|「[^」\n]{2,}」|『[^』\n]{2,}』)/g;
				let cursor = 0;
				let match = null;
				while ((match = re.exec(text))) {
					const before = text.slice(cursor, match.index).trim();
					if (before) {
						result.push({ type: 'narration', text: before });
					}
					const token = match[0];
					result.push({
						type: token.charAt(0) === '*' ? 'action' : 'speech',
						text: token
					});
					cursor = match.index + token.length;
				}
				const after = text.slice(cursor).trim();
				if (after) {
					result.push({ type: 'narration', text: after });
				}
				return result.length ? result : [{ type: 'narration', text }];
			},
			normalizeNativeSegmentType(item) {
				const sourceType = item && item.type ? String(item.type) : 'narration';
				if (sourceType !== 'narration') {
					return sourceType;
				}
				const text = String((item && item.text) || '').trim();
				if (/^[^：:\n]{1,20}[：:]\s*\S/.test(text)) {
					return 'speech';
				}
				if (/^[“"「『].+[”"」』]$/.test(text) || /[“"「『][^”"」』]{2,}[”"」』]/.test(text)) {
					return 'speech';
				}
				if (/^\*[^*]{2,}\*$/.test(text) || /^（.+）$/.test(text) || /^\(.+\)$/.test(text)) {
					return /心里|心想|想着|想道|内心|念头|暗想|默念/.test(text) ? 'thought' : 'action';
				}
				return 'narration';
			},
			normalizeNativeSegmentText(item, normalizedType) {
				const type = normalizedType || (item && item.type ? item.type : 'narration');
				let text = String((item && item.text) || '').trim();
				if (type === 'action' && text.length > 1 && text.charAt(0) === '*' && text.charAt(text.length - 1) === '*') {
					text = text.slice(1, -1).trim();
				}
				return text;
			},
			ensureNativeSegmentContrast(list) {
				if (!Array.isArray(list) || !list.length) {
					return [];
				}
				const hasStyledSegment = list.some((item) => item && item.type && item.type !== 'narration');
				if (hasStyledSegment) {
					return list;
				}
				if (list.length === 1) {
					const only = list[0];
					return [{
						type: 'speech',
						text: only.text
					}];
				}
				return list.map((item, index) => ({
					type: index === list.length - 1 ? 'speech' : 'narration',
					text: item.text
				}));
			},
			nativeSegmentWrapStyle(segment) {
				if (!this.isAppPlus || !segment) {
					return '';
				}
				return 'margin:0 0 6rpx;padding:0;background:transparent;border-left:0;border-radius:0;box-shadow:none;box-sizing:border-box;';
			},
			nativeSegmentTextStyle(segment) {
				if (!this.isAppPlus || !segment) {
					return '';
				}
				const type = segment.type || 'narration';
				let color = '#f3f4f6';
				let weight = '560';
				if (type === 'speech') {
					color = '#f1abc6';
					weight = '600';
				} else if (type === 'action') {
					color = '#b7dec6';
				} else if (type === 'thought') {
					color = '#cbc2e2';
				}
				return `color:${color};font-size:29rpx;line-height:1.72;font-style:normal;font-weight:${weight};letter-spacing:0;text-shadow:none;`;
			},
			assistantVoiceMessageId(row) {
				return this.normalizeDbMessageId(row && row.id);
			},
			stripAssistantSpeechWrapping(text) {
				const value = String(text || '').trim();
				if (value.length < 2) return value;
				const pairs = [
					['"', '"'],
					['“', '”'],
					['「', '」'],
					['『', '』']
				];
				for (let i = 0; i < pairs.length; i += 1) {
					const pair = pairs[i];
					if (value.charAt(0) === pair[0] && value.charAt(value.length - 1) === pair[1]) {
						return value.slice(1, -1).trim();
					}
				}
				return value;
			},
			extractAssistantSpeechText(text) {
				const { splitChatSegments } = require('@/common/chatMarkdown.js');
				const list = typeof splitChatSegments === 'function' ? splitChatSegments(text) : [];
				if (!Array.isArray(list) || !list.length) return '';
				return list
					.filter((item) => item && item.type === 'speech')
					.map((item) => this.stripAssistantSpeechWrapping(item.text))
					.filter((item) => item)
					.join('\n')
					.trim();
			},
			splitLongAssistantVoiceSentence(sentence) {
				const value = String(sentence || '').replace(/\s+/g, ' ').trim();
				if (!value) return [];
				if (value.length <= ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH) {
					return [value];
				}
				const parts = [];
				let buffer = '';
				const softBreakMap = {
					'，': true,
					',': true,
					'、': true,
					'：': true,
					':': true,
					'~': true,
					'～': true
				};
				const pushBuffer = () => {
					const next = buffer.replace(/\s+/g, ' ').trim();
					if (next) {
						parts.push(next);
					}
					buffer = '';
				};
				for (let i = 0; i < value.length; i += 1) {
					const ch = value.charAt(i);
					buffer += ch;
					if (
						(buffer.length >= ASSISTANT_VOICE_SEGMENT_SOFT_MIN && softBreakMap[ch]) ||
						buffer.length >= ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH
					) {
						pushBuffer();
					}
				}
				pushBuffer();
				return parts.length ? parts : [value];
			},
			splitAssistantSpeechIntoSentences(text, options) {
				const value = String(text || '').replace(/\r\n?/g, '\n').trim();
				if (!value) return [];
				const opts = options || {};
				const includeTrailingPartial = opts.includeTrailingPartial !== false;
				const strongBreakMap = {
					'。': true,
					'！': true,
					'!': true,
					'？': true,
					'?': true,
					'；': true,
					';': true,
					'…': true
				};
				const closingMap = {
					'”': true,
					'"': true,
					'」': true,
					'』': true
				};
				const rough = [];
				let buffer = '';
				const pushBuffer = () => {
					const next = buffer.replace(/\s+/g, ' ').trim();
					if (next) {
						rough.push(next);
					}
					buffer = '';
				};
				for (let i = 0; i < value.length; i += 1) {
					const ch = value.charAt(i);
					if (ch === '\n') {
						pushBuffer();
						continue;
					}
					buffer += ch;
					const prev = buffer.length > 1 ? buffer.charAt(buffer.length - 2) : '';
					if (strongBreakMap[ch] || (closingMap[ch] && strongBreakMap[prev])) {
						pushBuffer();
					}
				}
				if (includeTrailingPartial) {
					pushBuffer();
				}
				const expanded = [];
				rough.forEach((item) => {
					this.splitLongAssistantVoiceSentence(item).forEach((part) => {
						if (part) {
							expanded.push(part);
						}
					});
				});
				const merged = [];
				expanded.forEach((item) => {
					const next = String(item || '').trim();
					if (!next) return;
					if (!merged.length) {
						merged.push(next);
						return;
					}
					const lastIndex = merged.length - 1;
					const prev = merged[lastIndex];
					if (next.length <= ASSISTANT_VOICE_SEGMENT_SHORT_LENGTH && prev.length < ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH) {
						merged.splice(lastIndex, 1, prev + next);
						return;
					}
					merged.push(next);
				});
				if (merged.length > ASSISTANT_VOICE_SEGMENT_MAX) {
					const capped = merged.slice(0, ASSISTANT_VOICE_SEGMENT_MAX - 1);
					capped.push(merged.slice(ASSISTANT_VOICE_SEGMENT_MAX - 1).join(' '));
					return capped;
				}
				return merged;
			},
			assistantVoiceSentenceKey(sentenceTexts) {
				return Array.isArray(sentenceTexts) ? sentenceTexts.map((item) => String(item || '').trim()).filter((item) => item).join('\n@@\n') : '';
			},
			countAssistantVoiceSentencePrefix(currentTexts, nextTexts) {
				const left = Array.isArray(currentTexts) ? currentTexts : [];
				const right = Array.isArray(nextTexts) ? nextTexts : [];
				const total = Math.min(left.length, right.length);
				let count = 0;
				for (let i = 0; i < total; i += 1) {
					if (String(left[i] || '') !== String(right[i] || '')) {
						break;
					}
					count += 1;
				}
				return count;
			},
			findMessageRowById(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				return (Array.isArray(this.messages) ? this.messages : []).find(
					(item) => this.normalizeDbMessageId(item && item.id) === safeId
				) || null;
			},
			assistantVoiceAudioSegments(entry) {
				const total = Array.isArray(entry && entry.sentenceTexts) ? entry.sentenceTexts.length : 0;
				const source = Array.isArray(entry && entry.sentenceAudioUrls) ? entry.sentenceAudioUrls.slice(0, total) : [];
				while (source.length < total) {
					source.push('');
				}
				return source;
			},
			firstAssistantVoiceAudio(entry) {
				const list = this.assistantVoiceAudioSegments(entry);
				const first = list.length ? String(list[0] || '').trim() : '';
				return first && first.indexOf('data:audio/') === 0 ? first : '';
			},
			assistantVoiceHasPlayableAudio(entry) {
				return !!this.firstAssistantVoiceAudio(entry);
			},
			getAssistantVoiceEntry(row) {
				const messageId = this.assistantVoiceMessageId(row);
				if (!messageId) return null;
				const entry = this.assistantVoiceStateMap && this.assistantVoiceStateMap[messageId];
				if (!entry || typeof entry !== 'object') return null;
				const speechText = this.extractAssistantSpeechText(row && row.text);
				if (!speechText) {
					return null;
				}
				if (speechText === String(entry.speechText || '')) {
					return entry;
				}
				const fullSentenceKey = this.assistantVoiceSentenceKey(this.splitAssistantSpeechIntoSentences(speechText));
				const streamingSentenceKey = this.assistantVoiceSentenceKey(
					this.splitAssistantSpeechIntoSentences(speechText, { includeTrailingPartial: false })
				);
				const entrySentenceKey = String(entry.preparedSentenceKey || '');
				if (!entrySentenceKey || (entrySentenceKey !== fullSentenceKey && entrySentenceKey !== streamingSentenceKey)) {
					return null;
				}
				return entry;
			},
			setAssistantVoiceEntry(messageId, patch) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				const current = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId]
					? this.assistantVoiceStateMap[safeId]
					: {};
				const next = Object.assign({}, current, patch || {});
				this.$set(this.assistantVoiceStateMap, safeId, next);
				return next;
			},
			updateAssistantVoiceEntryId(fromMessageId, toMessageId) {
				const fromId = this.normalizeDbMessageId(fromMessageId);
				const nextId = this.normalizeDbMessageId(toMessageId);
				if (!fromId || !nextId || fromId === nextId || !this.assistantVoiceStateMap || !this.assistantVoiceStateMap[fromId]) {
					return;
				}
				const entry = this.assistantVoiceStateMap[fromId];
				this.$set(this.assistantVoiceStateMap, nextId, Object.assign({}, entry));
				this.$delete(this.assistantVoiceStateMap, fromId);
				if (this.assistantVoicePlayingMessageId === fromId) {
					this.assistantVoicePlayingMessageId = nextId;
				}
			},
			clearAssistantVoiceEntry(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId || !this.assistantVoiceStateMap || !this.assistantVoiceStateMap[safeId]) return;
				if (this.assistantVoicePlayingMessageId === safeId) {
					this.stopAssistantVoicePlayback();
				}
				this.$delete(this.assistantVoiceStateMap, safeId);
			},
			syncAssistantVoiceEntries() {
				const next = {};
				(Array.isArray(this.messages) ? this.messages : []).forEach((row) => {
					const messageId = this.assistantVoiceMessageId(row);
					if (!messageId) return;
					const entry = this.assistantVoiceStateMap && this.assistantVoiceStateMap[messageId];
					const speechText = this.extractAssistantSpeechText(row && row.text);
					if (entry && speechText && speechText === String(entry.speechText || '')) {
						next[messageId] = entry;
					}
				});
				if (this.assistantVoicePlayingMessageId && !next[this.assistantVoicePlayingMessageId]) {
					this.stopAssistantVoicePlayback();
				}
				this.assistantVoiceStateMap = next;
				if (this.assistantVoicePlayingMessageId && !next[this.assistantVoicePlayingMessageId]) {
					this.assistantVoicePlayingMessageId = '';
				}
			},
			syncUserVoiceEntries() {
				const next = {};
				(Array.isArray(this.messages) ? this.messages : []).forEach((row) => {
					const messageId = this.normalizeDbMessageId(row && row.id);
					if (!messageId) return;
					const entry = this.userVoiceStateMap && this.userVoiceStateMap[messageId];
					const restored = this.buildUserVoiceEntryFromRow(row, entry);
					if (restored && restored.audioUrl) {
						if (
							entry &&
							entry.audioUrl &&
							entry.audioUrl !== restored.audioUrl &&
							this.userVoicePlayingMessageId !== messageId
						) {
							this.revokeVoiceMessageLocalUrl(entry.audioUrl);
						}
						next[messageId] = restored;
					}
				});
				Object.keys(this.userVoiceStateMap || {}).forEach((messageId) => {
					if (!next[messageId]) {
						const entry = this.userVoiceStateMap[messageId];
						if (entry && entry.audioUrl) {
							this.revokeVoiceMessageLocalUrl(entry.audioUrl);
						}
					}
				});
				if (this.userVoicePlayingMessageId && !next[this.userVoicePlayingMessageId]) {
					this.stopUserVoicePlayback();
				}
				this.userVoiceStateMap = next;
				if (this.userVoicePlayingMessageId && !next[this.userVoicePlayingMessageId]) {
					this.userVoicePlayingMessageId = '';
				}
			},
			shouldShowAssistantVoicePill(row) {
				if (!this.isVoiceFeatureEnabledGlobal()) return false;
				if (!row || row.role !== 'char') return false;
				if (!this.isCharacterVoiceEnabled()) return false;
				if (this.getAssistantVoiceEntry(row)) return true;
				return !!this.extractAssistantSpeechText(row.text);
			},
			assistantVoiceLabel(row) {
				const entry = this.getAssistantVoiceEntry(row);
				const state = entry && entry.state ? entry.state : 'idle';
				if (state === 'loading') return this.tx('assistant_voice_loading', '语音生成中');
				if (state === 'playing') return this.tx('assistant_voice_stop', '停止语音');
				if (state === 'error') return this.tx('assistant_voice_retry', '重试语音');
				return this.tx('assistant_voice_play', '播放台词');
			},
			assistantVoicePillClass(row) {
				const entry = this.getAssistantVoiceEntry(row);
				const state = entry && entry.state ? entry.state : 'idle';
				return {
					'assistant-voice-pill--loading': state === 'loading',
					'assistant-voice-pill--playing': state === 'playing',
					'assistant-voice-pill--error': state === 'error'
				};
			},
			getAssistantVoicePlayer() {
				if (!this.assistantVoicePlayer) {
					if (!this.isAppPlus && typeof Audio === 'function') {
						this.assistantVoicePlayer = this.createAssistantVoiceH5Player();
					} else if (typeof uni !== 'undefined' && typeof uni.createInnerAudioContext === 'function') {
						this.assistantVoicePlayer = uni.createInnerAudioContext();
					}
				}
				if (this.assistantVoicePlayer && !this.assistantVoicePlayerReady) {
					this.assistantVoicePlayer.autoplay = false;
					this.assistantVoicePlayer.onEnded(() => {
						const messageId = this.assistantVoicePlayingMessageId;
						if (!messageId) return;
						this.continueAssistantVoicePlayback(messageId);
					});
					this.assistantVoicePlayer.onStop(() => {
						const messageId = this.assistantVoicePlayingMessageId;
						this.assistantVoicePlayingMessageId = '';
						if (messageId && this.assistantVoiceStateMap[messageId]) {
							const entry = this.assistantVoiceStateMap[messageId];
							this.setAssistantVoiceEntry(messageId, {
								state: entry && entry.requestKey ? 'loading' : this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : 'idle',
								playingIndex: -1,
								waitingForSegmentIndex: -1,
								autoPlayPending: false
							});
						}
					});
					this.assistantVoicePlayer.onError((err) => {
						const messageId = this.assistantVoicePlayingMessageId;
						this.assistantVoicePlayingMessageId = '';
						if (messageId && this.assistantVoiceStateMap[messageId]) {
							const entry = this.assistantVoiceStateMap[messageId];
							if (this.isAssistantVoiceAutoplayBlockedError(err)) {
								this.handleAssistantVoiceAutoplayBlocked(messageId, entry);
								return;
							}
							this.setAssistantVoiceEntry(messageId, {
								state: 'error',
								error: this.tx('assistant_voice_play_failed', '语音播放失败')
							});
						}
					});
					this.assistantVoicePlayerReady = true;
				}
				return this.assistantVoicePlayer;
			},
			createAssistantVoiceH5Player() {
				const audio = new Audio();
				audio.preload = 'auto';
				audio.playsInline = true;
				audio.muted = false;
				try {
					audio.setAttribute('playsinline', 'true');
					audio.setAttribute('webkit-playsinline', 'true');
					audio.setAttribute('x5-playsinline', 'true');
				} catch (e) {}
				const endedHandlers = [];
				const stopHandlers = [];
				const errorHandlers = [];
				const emitHandlers = (list, payload) => {
					list.slice().forEach((fn) => {
						try {
							fn(payload);
						} catch (e) {}
					});
				};
				audio.addEventListener('ended', () => emitHandlers(endedHandlers));
				audio.addEventListener('error', (err) => emitHandlers(errorHandlers, err));
				return {
					autoplay: false,
					get src() {
						return audio.src;
					},
					set src(value) {
						audio.src = value || '';
					},
					onEnded(fn) {
						if (typeof fn === 'function') endedHandlers.push(fn);
					},
					onStop(fn) {
						if (typeof fn === 'function') stopHandlers.push(fn);
					},
					onError(fn) {
						if (typeof fn === 'function') errorHandlers.push(fn);
					},
					play() {
						const result = audio.play();
						if (result && typeof result.catch === 'function') {
							result.catch((err) => {
								emitHandlers(errorHandlers, err);
							});
						}
						return result;
					},
					stop() {
						try {
							audio.pause();
						} catch (e) {}
						try {
							audio.currentTime = 0;
						} catch (e) {}
						emitHandlers(stopHandlers);
					},
					destroy() {
						try {
							audio.pause();
						} catch (e) {}
						try {
							audio.removeAttribute('src');
							audio.load();
						} catch (e) {}
					},
					prime() {
						const probe = new Audio();
						try {
							probe.preload = 'auto';
							probe.muted = true;
							probe.playsInline = true;
							probe.setAttribute('playsinline', 'true');
							probe.setAttribute('webkit-playsinline', 'true');
							probe.setAttribute('x5-playsinline', 'true');
							probe.src = ASSISTANT_VOICE_SILENT_WAV_DATA_URL;
							const result = probe.play();
							if (result && typeof result.then === 'function') {
								return result.then(() => {
									try {
										probe.pause();
										probe.currentTime = 0;
									} catch (e) {}
									try {
										probe.removeAttribute('src');
										probe.load();
									} catch (e) {}
									return true;
								});
							}
						} catch (e) {}
						try {
							probe.pause();
						} catch (e) {}
						try {
							probe.removeAttribute('src');
							probe.load();
						} catch (e) {}
						return Promise.resolve(true);
					}
				};
			},
			stopAssistantVoicePlayback() {
				const messageId = this.assistantVoicePlayingMessageId;
				try {
					const player = this.getAssistantVoicePlayer();
					if (player) {
						player.stop();
					}
				} catch (e) {}
				this.assistantVoicePlayingMessageId = '';
				if (messageId && this.assistantVoiceStateMap[messageId]) {
					const entry = this.assistantVoiceStateMap[messageId];
					this.setAssistantVoiceEntry(messageId, {
						state: entry && entry.requestKey ? 'loading' : this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : 'idle',
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
				}
			},
			isAssistantVoiceAutoplayBlockedError(err) {
				const name = String((err && err.name) || '').trim().toLowerCase();
				const message = String((err && err.message) || err || '').trim().toLowerCase();
				return name === 'notallowederror'
					|| message.includes('notallowederror')
					|| message.includes("play() failed because the user didn't interact")
					|| message.includes("user didn't interact")
					|| message.includes('autoplay');
			},
			handleAssistantVoiceAutoplayBlocked(messageId, entry) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId || !entry || typeof entry !== 'object') {
					return;
				}
				this.assistantVoiceBrowserUnlocked = false;
				this.assistantVoiceBrowserUnlocking = false;
				const waitingIndex = entry.waitingForSegmentIndex != null && entry.waitingForSegmentIndex >= 0
					? entry.waitingForSegmentIndex
					: entry.playingIndex != null && entry.playingIndex >= 0
						? entry.playingIndex
						: 0;
				const resumeIndex = Math.max(0, Number(waitingIndex));
				this.assistantVoicePlayingMessageId = '';
				this.setAssistantVoiceEntry(safeId, {
					state: entry.requestKey ? 'loading' : this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : 'idle',
					playingIndex: -1,
					waitingForSegmentIndex: resumeIndex,
					autoPlayPending: true,
					error: ''
				});
				this.initAssistantVoiceBrowserUnlockTracking();
				if (!this.assistantVoiceAutoplayHintShown) {
					this.assistantVoiceAutoplayHintShown = true;
					uni.showToast({
						title: this.tx('assistant_voice_browser_unlock', '点一下页面即可允许浏览器自动播报'),
						icon: 'none'
					});
				}
			},
			resumePendingAssistantVoiceAutoplay() {
				if (!this.canUseBrowserAssistantVoiceAutoplay() || !this.assistantVoiceAutoEnabled) {
					return false;
				}
				if (this.assistantVoicePlayingMessageId) {
					return false;
				}
				const rows = Array.isArray(this.messages) ? this.messages.slice().reverse() : [];
				for (let i = 0; i < rows.length; i += 1) {
					const row = rows[i];
					if (!row || row.role !== 'char') continue;
					const messageId = this.assistantVoiceMessageId(row);
					const entry = messageId && this.assistantVoiceStateMap ? this.assistantVoiceStateMap[messageId] : null;
					if (!entry || !entry.autoPlayPending) continue;
					const segmentIndex = Math.max(0, Number(entry.waitingForSegmentIndex || 0));
					const audioSegments = this.assistantVoiceAudioSegments(entry);
					const audioDataUrl = String(audioSegments[segmentIndex] || '').trim();
					if (audioDataUrl && audioDataUrl.indexOf('data:audio/') === 0) {
						return this.playAssistantVoiceByMessageId(messageId, segmentIndex, audioDataUrl);
					}
					const firstAudio = this.firstAssistantVoiceAudio(entry);
					if (firstAudio) {
						return this.playAssistantVoiceByMessageId(messageId, 0, firstAudio);
					}
				}
				return false;
			},
			disposeAssistantVoicePlayer() {
				this.stopAssistantVoicePlayback();
				if (this.assistantVoicePlayer && typeof this.assistantVoicePlayer.destroy === 'function') {
					try {
						this.assistantVoicePlayer.destroy();
					} catch (e) {}
				}
				this.assistantVoicePlayer = null;
				this.assistantVoicePlayerReady = false;
			},
			playAssistantVoiceByMessageId(messageId, segmentIndex, sourceUrl) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return false;
				const player = this.getAssistantVoicePlayer();
				if (!player) {
					this.showErrorToast(this.tx('assistant_voice_not_supported', '当前环境暂不支持语音播放'));
					return false;
				}
				const entry = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
				if (!entry || typeof entry !== 'object') return false;
				const audioSegments = this.assistantVoiceAudioSegments(entry);
				const audioDataUrl = String(sourceUrl || audioSegments[segmentIndex] || '').trim();
				if (!audioDataUrl || audioDataUrl.indexOf('data:audio/') !== 0) {
					if (entry.requestKey) {
						this.assistantVoicePlayingMessageId = safeId;
						this.setAssistantVoiceEntry(safeId, {
							state: 'playing',
							waitingForSegmentIndex: segmentIndex,
							playingIndex: Math.max(-1, Number(entry.playingIndex != null ? entry.playingIndex : segmentIndex - 1)),
							autoPlayPending: false
						});
					}
					return false;
				}
				if (this.assistantVoicePlayingMessageId && this.assistantVoicePlayingMessageId !== safeId) {
					this.stopAssistantVoicePlayback();
				}
				this.assistantVoicePlayingMessageId = safeId;
				this.setAssistantVoiceEntry(safeId, {
					state: 'playing',
					error: '',
					audioDataUrl: audioSegments[0] || audioDataUrl,
					playingIndex: segmentIndex,
					waitingForSegmentIndex: -1,
					autoPlayPending: false
				});
				try {
					const sentenceTexts = Array.isArray(entry.sentenceTexts) ? entry.sentenceTexts : [];
					const spokenText = String(sentenceTexts[segmentIndex] || entry.speechText || '').trim();
					if (spokenText) {
						companionStore.emitReply(spokenText);
					}
					player.src = audioDataUrl;
					player.play();
					return true;
				} catch (e) {
					this.assistantVoicePlayingMessageId = '';
					this.setAssistantVoiceEntry(safeId, {
						state: 'error',
						error: this.tx('assistant_voice_play_failed', '语音播放失败'),
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
					this.showErrorToast(this.tx('assistant_voice_play_failed', '语音播放失败'));
					return false;
				}
			},
			continueAssistantVoicePlayback(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				const entry = safeId && this.assistantVoiceStateMap ? this.assistantVoiceStateMap[safeId] : null;
				if (!safeId || !entry || typeof entry !== 'object') {
					this.assistantVoicePlayingMessageId = '';
					return;
				}
				const sentenceTexts = Array.isArray(entry.sentenceTexts) ? entry.sentenceTexts : [];
				const audioSegments = this.assistantVoiceAudioSegments(entry);
				const nextIndex = Math.max(0, Number(entry.playingIndex || 0) + 1);
				if (nextIndex >= sentenceTexts.length) {
					this.assistantVoicePlayingMessageId = '';
					this.setAssistantVoiceEntry(safeId, {
						state: entry.requestKey ? 'loading' : 'ready',
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
					return;
				}
				const nextAudio = String(audioSegments[nextIndex] || '').trim();
				if (nextAudio && nextAudio.indexOf('data:audio/') === 0) {
					this.playAssistantVoiceByMessageId(safeId, nextIndex, nextAudio);
					return;
				}
				if (!entry.requestKey) {
					this.assistantVoicePlayingMessageId = '';
					this.setAssistantVoiceEntry(safeId, {
						state: this.assistantVoiceHasPlayableAudio(entry) ? 'ready' : 'idle',
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
					return;
				}
				this.assistantVoicePlayingMessageId = safeId;
				this.setAssistantVoiceEntry(safeId, {
					state: 'playing',
					waitingForSegmentIndex: nextIndex,
					autoPlayPending: false
				});
			},
			playAssistantVoice(row) {
				if (!this.isVoiceFeatureEnabledGlobal() || !this.isCharacterVoiceEnabled()) return;
				this.tryUnlockAssistantVoiceBrowserPlayback({
					resumePending: false,
					silent: true
				}).catch(() => {});
				const messageId = this.assistantVoiceMessageId(row);
				const entry = this.getAssistantVoiceEntry(row);
				if (!messageId || !entry) return;
				const firstAudio = this.firstAssistantVoiceAudio(entry);
				if (!firstAudio) {
					if (entry.requestKey) {
						this.assistantVoicePlayingMessageId = messageId;
						this.setAssistantVoiceEntry(messageId, {
							state: 'loading',
							autoPlayPending: true,
							waitingForSegmentIndex: 0
						});
						return;
					}
					this.prepareAssistantVoiceForRow(row, {
						autoplay: true,
						toastOnError: true
					});
					return;
				}
				this.playAssistantVoiceByMessageId(messageId, 0, firstAudio);
			},
			preloadAssistantVoiceSegments(messageId, requestKey, options) {
				const safeId = this.normalizeDbMessageId(messageId);
				const entry = safeId && this.assistantVoiceStateMap ? this.assistantVoiceStateMap[safeId] : null;
				if (!safeId || !entry || entry.requestKey !== requestKey) {
					return Promise.resolve(null);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const sentenceTexts = Array.isArray(entry.sentenceTexts) ? entry.sentenceTexts.slice() : [];
				const opts = options || {};
				let firstAudioDataUrl = this.firstAssistantVoiceAudio(entry);
				const run = (index) => {
					if (index >= sentenceTexts.length) {
						const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
						if (latest && latest.requestKey === requestKey) {
							this.setAssistantVoiceEntry(safeId, {
								requestKey: '',
								state: this.assistantVoicePlayingMessageId === safeId ? 'playing' : this.assistantVoiceHasPlayableAudio(latest) ? 'ready' : 'idle',
								autoPlayPending: false
							});
						}
						return Promise.resolve(firstAudioDataUrl || null);
					}
					const latestBeforeRequest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
					const existingSegments = this.assistantVoiceAudioSegments(latestBeforeRequest);
					const existingAudio = String(existingSegments[index] || '').trim();
					if (existingAudio && existingAudio.indexOf('data:audio/') === 0) {
						if (!firstAudioDataUrl) {
							firstAudioDataUrl = existingAudio;
						}
						return run(index + 1);
					}
					return tavernApi
						.postTavernSpeech(this.buildCharacterVoiceTtsPayload(sentenceTexts[index]))
						.then((data) => {
							const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
							if (!latest || latest.requestKey !== requestKey) {
								return firstAudioDataUrl || null;
							}
							const audioDataUrl = data && data.audioDataUrl ? String(data.audioDataUrl).trim() : '';
							if (!audioDataUrl || audioDataUrl.indexOf('data:audio/') !== 0) {
								throw new Error(this.tx('assistant_voice_failed', '语音生成失败'));
							}
							const audioSegments = this.assistantVoiceAudioSegments(latest);
							audioSegments[index] = audioDataUrl;
							if (!firstAudioDataUrl) {
								firstAudioDataUrl = audioDataUrl;
							}
							this.setAssistantVoiceEntry(safeId, {
								audioDataUrl: audioSegments[0] || audioDataUrl,
								sentenceAudioUrls: audioSegments,
								state: this.assistantVoicePlayingMessageId === safeId ? 'playing' : 'ready',
								error: ''
							});
							const fresh = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
							if (fresh && fresh.autoPlayPending && index === 0) {
								const latestRow = this.findMessageRowById(safeId);
								if (latestRow) {
									this.playAssistantVoice(latestRow);
								}
							} else if (fresh && fresh.waitingForSegmentIndex === index && this.assistantVoicePlayingMessageId === safeId) {
								this.playAssistantVoiceByMessageId(safeId, index, audioDataUrl);
							}
							return run(index + 1);
						})
						.catch((error) => {
							const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
							if (latest && latest.requestKey === requestKey) {
								this.setAssistantVoiceEntry(safeId, {
									requestKey: '',
									state: firstAudioDataUrl ? 'ready' : 'error',
									autoPlayPending: false,
									waitingForSegmentIndex: -1,
									error: this.jgErrMsg(error, this.tx('assistant_voice_failed', '语音生成失败'))
								});
								if (!firstAudioDataUrl) {
									this.assistantVoicePlayingMessageId = '';
								}
							}
							if (opts.toastOnError && !firstAudioDataUrl) {
								this.showErrorToast(this.jgErrMsg(error, this.tx('assistant_voice_failed', '语音生成失败')));
							}
							return firstAudioDataUrl || null;
						});
				};
				return run(0);
			},
			prepareAssistantVoiceForRow(row, options) {
				if (!this.isVoiceFeatureEnabledGlobal()) return Promise.resolve(null);
				if (!row || row.role !== 'char') return Promise.resolve(null);
				if (!this.isCharacterVoiceEnabled()) {
					const messageId = this.assistantVoiceMessageId(row);
					if (messageId) {
						this.clearAssistantVoiceEntry(messageId);
					}
					return Promise.resolve(null);
				}
				const messageId = this.assistantVoiceMessageId(row);
				const speechText = this.extractAssistantSpeechText(row.text);
				const opts = options || {};
				if (!messageId || (!messageId.startsWith('db_') && opts.allowStreaming !== true)) {
					return Promise.resolve(null);
				}
				if (!speechText) {
					this.clearAssistantVoiceEntry(messageId);
					return Promise.resolve(null);
				}
				const sentenceTexts = this.splitAssistantSpeechIntoSentences(speechText, {
					includeTrailingPartial: opts.includeTrailingPartial !== false
				});
				if (!sentenceTexts.length) {
					this.clearAssistantVoiceEntry(messageId);
					return Promise.resolve(null);
				}
				const preparedSentenceKey = this.assistantVoiceSentenceKey(sentenceTexts);
				const current = this.assistantVoiceStateMap && this.assistantVoiceStateMap[messageId];
				if (current && opts.force !== true) {
					if (current.preparedSentenceKey === preparedSentenceKey) {
						if (current.speechText !== speechText) {
							this.setAssistantVoiceEntry(messageId, {
								speechText: speechText
							});
						}
						if (this.assistantVoiceHasPlayableAudio(current)) {
							if (opts.autoplay && this.assistantVoicePlayingMessageId !== messageId && current.state !== 'playing') {
								this.playAssistantVoice(row);
							}
							return Promise.resolve(this.firstAssistantVoiceAudio(current));
						}
						if (current.requestKey) {
							if (opts.autoplay) {
								this.setAssistantVoiceEntry(messageId, {
									autoPlayPending: true,
									waitingForSegmentIndex: 0
								});
							}
							return Promise.resolve(null);
						}
					}
					if (this.assistantVoiceHasPlayableAudio(current)) {
						// Keep matching prefix audio when streaming appends new complete sentences.
					}
				}
				const previousTexts = Array.isArray(current && current.sentenceTexts) ? current.sentenceTexts : [];
				const previousAudioSegments = this.assistantVoiceAudioSegments(current);
				const reusableCount = opts.force === true ? 0 : this.countAssistantVoiceSentencePrefix(previousTexts, sentenceTexts);
				const nextAudioSegments = new Array(sentenceTexts.length).fill('');
				for (let i = 0; i < reusableCount; i += 1) {
					nextAudioSegments[i] = previousAudioSegments[i] || '';
				}
				const requestKey = 'tts_' + messageId + '_' + Date.now();
				this.setAssistantVoiceEntry(messageId, {
					speechText,
					preparedSentenceKey: preparedSentenceKey,
					sentenceTexts,
					sentenceAudioUrls: nextAudioSegments,
					state: reusableCount < sentenceTexts.length ? 'loading' : this.assistantVoiceHasPlayableAudio({ sentenceTexts, sentenceAudioUrls: nextAudioSegments }) ? 'ready' : 'idle',
					audioDataUrl: nextAudioSegments[0] || '',
					error: '',
					requestKey: reusableCount < sentenceTexts.length ? requestKey : '',
					autoPlayPending: !!opts.autoplay,
					playingIndex: -1,
					waitingForSegmentIndex: -1
				});
				if (reusableCount >= sentenceTexts.length) {
					if (opts.autoplay) {
						this.playAssistantVoice(row);
					}
					return Promise.resolve(nextAudioSegments[0] || null);
				}
				return this.preloadAssistantVoiceSegments(messageId, requestKey, {
					toastOnError: !!opts.toastOnError
				});
			},
			toggleAssistantVoice(row) {
				if (!this.isVoiceFeatureEnabledGlobal()) return;
				const messageId = this.assistantVoiceMessageId(row);
				const entry = this.getAssistantVoiceEntry(row);
				if (!messageId || !this.shouldShowAssistantVoicePill(row) || !this.isCharacterVoiceEnabled()) return;
				if (entry && entry.state === 'playing') {
					this.stopAssistantVoicePlayback();
					return;
				}
				if (entry && entry.audioDataUrl) {
					this.playAssistantVoice(row);
					return;
				}
				this.prepareAssistantVoiceForRow(row, {
					autoplay: true,
					force: !!(entry && entry.state === 'error'),
					toastOnError: true
				});
			},
			prepareStreamingAssistantVoice(row) {
				if (!row || row.role !== 'char') return Promise.resolve(null);
				const hasExistingEntry = !!this.getAssistantVoiceEntry(row);
				if (!this.shouldAutoPrepareAssistantVoice() && !hasExistingEntry) {
					return Promise.resolve(null);
				}
				return this.prepareAssistantVoiceForRow(row, {
					autoplay: this.shouldAutoPlayAssistantVoice(),
					toastOnError: false,
					allowStreaming: true,
					includeTrailingPartial: false
				});
			},
			onRegen() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const n = this.messages.length;
				if (n === 0 || this.messages[n - 1].role !== 'char') {
					uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					return;
				}
				const last = this.messages[n - 1];
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					if (anchor.reason === 'pending_sync') {
						uni.showToast({ title: this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。'), icon: 'none' });
					} else if (anchor.reason === 'empty_char') {
						uni.showToast({ title: this.tx('tail_regen_empty', '最后一条 AI 回复为空，暂时不能重新生成'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					}
					return;
				}
				this.clearGenerationRecovery();
				this.silentGenerationInterruptUntil = 0;
				this.clearPendingVoiceStart();
				this.interruptAssistantVoiceRound();
				const payload = Object.assign(
					{
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						targetAssistantMessageId: anchor.targetAssistantMessageId
					},
					this.buildAssistantExpressionPayloadFields()
				);
				this.sending = true;

				if (tavernApi.jgStreamEnabled()) {
					const backup = String(last.text || '');
					let started = false;
					this.beginAssistantStreaming(last.id, 'regenerate');
					this.notifyCompanionReplying('regenerate');
					this.streamAbortController = new AbortController();
					this.followBottom = true;
					tavernApi
						.postTavernRegenerateStream(
							payload,
							{
								onDelta: (piece) => {
									if (!started) {
										started = true;
										this.$set(last, 'text', piece);
									} else {
										const next = (last.text || '') + piece;
										this.$set(last, 'text', next);
									}
									this.prepareStreamingAssistantVoice(last).catch(() => {});
									this.followScrollNextTick();
								},
								onDone: (data) => {
									const cancelled = data && data.cancelled;
									const c = data && data.content != null ? String(data.content).trim() : '';
									if (cancelled && !c) {
										this.$set(last, 'text', backup);
									} else if (data && data.content != null) {
										this.$set(last, 'text', String(data.content).trim());
									}
									if (Array.isArray(data && data.swipes)) {
										this.$set(
											last,
											'swipes',
											data.swipes.map((x) => String(x))
										);
										if (typeof data.swipeIndex === 'number') {
											this.$set(last, 'swipeIndex', data.swipeIndex);
										}
									}
									if (cancelled && c) {
										this.showGenerationStopToast('stopped_keep', '已停止，已保留本次生成的内容');
										this.queueStopSync(700);
									}
									this.finishAssistantStreaming(last.id);
									this.notifyCompanionReply(last.text);
									this.applyAssistantExpressionForRow(last);
									this.followScrollNextTick();
								},
								onAbort: () => {
									if (!started) {
										this.$set(last, 'text', backup);
									}
									this.queueStopSync(700);
									this.finishAssistantStreaming(last.id);
									this.showGenerationStopToast('stopped', '已停止');
								},
								onError: (e) => {
									const result = this.handleCommercialError(e, this.tx('regen_failed', '重新生成失败'), {
										skipToastWhenPrompted: true
									});
									this.notifyCompanionError(result.message);
									if (!result.prompted) {
										this.showErrorToast(result.message);
									}
									this.$set(last, 'text', backup);
									this.markGenerationRecovery(last.id, {
										message: result.message,
										partialText: backup,
										canRegen: true
									});
									this.finishAssistantStreaming(last.id);
								}
							},
							{ signal: this.streamAbortController.signal }
						)
						.finally(() => {
							this.finishAssistantStreaming(last.id);
							this.streamAbortController = null;
							this.sending = false;
						});
					return;
				}

				tavernApi
					.postTavernRegenerate(payload)
					.then((d) => {
						if (d && d.content != null) {
							this.$set(last, 'text', String(d.content));
						}
						this.applyAssistantExpressionForRow(last);
						this.notifyCompanionReply(last.text);
						return this.refreshJgMessages();
					})
					.then(() => {
						this.scrollChatToBottom({ immediate: true });
					})
					.catch((e) => {
						const result = this.handleCommercialError(e, this.tx('regen_failed', '重新生成失败'), {
							skipToastWhenPrompted: true
						});
						this.notifyCompanionError(result.message);
						if (!result.prompted) {
							this.showErrorToast(result.message);
						}
						this.markGenerationRecovery(last.id, {
							message: result.message,
							partialText: last && last.text,
							canRegen: true
						});
					})
					.finally(() => {
						this.sending = false;
					});
			},
			onContinue() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const n = this.messages.length;
				if (n === 0 || this.messages[n - 1].role !== 'char') {
					uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					return;
				}
				const last = this.messages[n - 1];
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					if (anchor.reason === 'pending_sync') {
						uni.showToast({ title: this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。'), icon: 'none' });
					} else if (anchor.reason === 'empty_char') {
						uni.showToast({ title: this.tx('tail_empty_char', '最后一条 AI 回复内容为空，暂时不能续写或重生。'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					}
					return;
				}
				this.clearGenerationRecovery();
				this.silentGenerationInterruptUntil = 0;
				this.clearPendingVoiceStart();
				this.interruptAssistantVoiceRound();
				const payload = Object.assign(
					{
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						targetAssistantMessageId: anchor.targetAssistantMessageId
					},
					this.buildAssistantExpressionPayloadFields()
				);
				this.sending = true;

				if (tavernApi.jgStreamEnabled()) {
					const rid = 'cont_' + Date.now();
					this.messages = this.messages.concat({
						id: rid,
						role: 'char',
						text: '',
						messageKind: 'CONTINUATION',
						continueFromMessageId: anchor.targetAssistantMessageId,
						swipes: [''],
						swipeIndex: 0
					});
					let acc = '';
					this.beginAssistantStreaming(rid, 'continue');
					this.notifyCompanionReplying('continue');
					this.streamAbortController = new AbortController();
					this.followBottom = true;
					tavernApi
						.postTavernContinueStream(
							payload,
							{
								onDelta: (piece) => {
									acc += piece;
									const row = this.messages.find((item) => item && item.id === rid);
									if (row) {
										this.$set(row, 'text', acc);
										this.prepareStreamingAssistantVoice(row).catch(() => {});
									}
									this.followScrollNextTick();
								},
								onDone: (data) => {
									const row = this.messages.find((item) => item && item.id === rid);
									if (!row) {
										this.followScrollNextTick();
										return;
									}
									if (data && data.content != null) {
										this.$set(row, 'text', String(data.content).trim());
									}
									if (data && data.messageId) {
										this.updateAssistantVoiceEntryId(rid, data.messageId);
										this.moveAssistantStreamingMessageId(rid, data.messageId);
										this.$set(row, 'id', this.normalizeDbMessageId(data.messageId));
									}
									this.$set(row, 'messageKind', data && data.messageKind ? String(data.messageKind) : 'CONTINUATION');
									this.$set(row, 'continueFromMessageId', this.normalizeDbMessageId(data && data.continueFromMessageId ? data.continueFromMessageId : anchor.targetAssistantMessageId));
									if (Array.isArray(data && data.swipes)) {
										this.$set(
											row,
											'swipes',
											data.swipes.map((x) => String(x))
										);
										if (typeof data.swipeIndex === 'number') {
											this.$set(row, 'swipeIndex', data.swipeIndex);
										}
									}
									if (data && data.cancelled && acc) {
										this.showGenerationStopToast('stopped_keep', '已停止，已保留本次生成的内容');
										this.queueStopSync(700);
									}
									this.finishAssistantStreaming(row.id);
									this.notifyCompanionReply(row.text);
									this.applyAssistantExpressionForRow(row);
									// 商用一致性：流式续写完成后也刷新一次消息列表，避免本地临时文本被状态刷新覆盖
									this.refreshJgMessages().catch(() => {});
									this.followScrollNextTick();
								},
								onAbort: () => {
									const row = this.messages.find((item) => item && item.id === rid);
									if (row && !String(row.text || '').trim()) {
										this.messages = this.messages.filter((item) => item && item.id !== rid);
									}
									this.queueStopSync(700);
									this.finishAssistantStreaming(rid);
									this.showGenerationStopToast('stopped', '已停止');
								},
								onError: (e) => {
									const result = this.handleCommercialError(e, this.tx('continue_failed', '续写失败'), {
										skipToastWhenPrompted: true
									});
									this.notifyCompanionError(result.message);
									if (!result.prompted) {
										this.showErrorToast(result.message);
									}
									const row = this.messages.find((item) => item && item.id === rid);
									if (row) {
										this.$set(row, 'text', acc);
									}
									this.markGenerationRecovery(rid, {
										message: result.message,
										partialText: acc,
										canRegen: true
									});
									this.finishAssistantStreaming(rid);
								}
							},
							{ signal: this.streamAbortController.signal }
						)
						.finally(() => {
							this.finishAssistantStreaming(rid);
							this.streamAbortController = null;
							this.sending = false;
						});
					return;
				}

				tavernApi
					.postTavernContinue(payload)
					.then((d) => {
						const aid = d && d.messageId ? this.normalizeDbMessageId(d.messageId) : '';
						const rid = aid || 'cont_' + Date.now();
						const raw = d && d.content;
						const reply =
							raw != null && String(raw).trim() !== ''
								? String(raw).trim()
								: this.tx('empty_ai', '模型未返回内容');
						const sw =
							Array.isArray(d && d.swipes) && d.swipes.length
								? d.swipes.map((x) => String(x))
								: [reply];
						const si = typeof (d && d.swipeIndex) === 'number' ? d.swipeIndex : 0;
						if (d && d.content != null) {
							this.messages = this.messages.concat({
								id: rid,
								role: 'char',
								text: reply,
								messageKind: d && d.messageKind ? String(d.messageKind) : 'CONTINUATION',
								continueFromMessageId: this.normalizeDbMessageId(d && d.continueFromMessageId ? d.continueFromMessageId : anchor.targetAssistantMessageId),
								swipes: sw,
								swipeIndex: si
							});
						}
						const appendedRow = this.messages[this.messages.length - 1];
						this.applyAssistantExpressionForRow(appendedRow);
						this.notifyCompanionReply(appendedRow.text);
						return this.refreshJgMessages();
					})
					.then(() => {
						this.scrollChatToBottom({ immediate: true });
					})
					.catch((e) => {
						const result = this.handleCommercialError(e, this.tx('continue_failed', '续写失败'), {
							skipToastWhenPrompted: true
						});
						this.notifyCompanionError(result.message);
						if (!result.prompted) {
							this.showErrorToast(result.message);
						}
						this.markGenerationRecovery(last.id, {
							message: result.message,
							partialText: last && last.text,
							canRegen: true
						});
					})
					.finally(() => {
						this.sending = false;
					});
			},
			onRestart() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				uni.showModal({
					title: this.tx('restart_title', '重新开始聊天'),
					content: this.tx('restart_desc', '将清空与当前角色的本轮会话记录，但不会删除角色。确定继续吗？'),
					confirmText: this.tx('confirm', '确定'),
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res.confirm) return;
						this.sending = true;
						tavernApi
							.postTavernSessionRestart({
								characterId: cid,
								clientUid: tavernApi.getClientUid()
							})
							.then(() => {
								tavernApi.cleanupLocalConversationArtifacts({
									clientUid: tavernApi.getClientUid(),
									conversationId: this.localChatConversationId
								});
								this.resetConversationVoiceRuntimeState();
								return this.refreshJgMessages();
							})
							.then(() => {
								this.scrollChatToBottom({ immediate: true });
								uni.showToast({
									title: this.tx('restart_success', '已清空，可重新对话'),
									icon: 'none'
								});
							})
							.catch((e) => {
								uni.showToast({
									title: this.jgErrMsg(e, this.tx('restart_failed', '操作失败')),
									icon: 'none',
									duration: 3200
								});
							})
							.finally(() => {
								this.sending = false;
							});
					}
				});
			},
			onMem() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				this.sending = true;
				this.memoryRefreshing = true;
				var refreshResult = null;
				tavernApi
					.postTavernMemoryRefresh({ characterId: cid, clientUid: tavernApi.getClientUid() })
					.then((memory) => {
						if (memory && typeof memory === 'object') {
							refreshResult = memory;
							this.jgMemory = Object.assign({}, this.jgMemory || {}, memory);
						}
						return this.refreshJgMessages();
					})
					.then(() => {
						const syncStatus = String((refreshResult && refreshResult.syncStatus) || '').trim().toUpperCase();
						uni.showToast({
							title:
								syncStatus === 'FAILED'
									? this.tx('memory_sync_failed_retry', '记忆同步失败 · 点击重试')
									: this.tx('memory_refresh_success_lorebook', '已整理长期记忆'),
							icon: 'none'
						});
					})
					.catch((e) => {
						uni.showToast({
							title: this.jgErrMsg(e, this.tx('memory_refresh_failed', '记忆刷新失败')),
							icon: 'none',
							duration: 3200
						});
					})
					.finally(() => {
						this.sending = false;
						this.memoryRefreshing = false;
					});
			},
			submitOutgoingMessage(rawText, rawImageUrls, options) {
				const opts = options || {};
				const allowWhenNotAtBottom = opts && opts.allowWhenNotAtBottom === true;
				if (!this.atChatBottom && !allowWhenNotAtBottom) {
					this.scrollChatToBottom({ immediate: true });
					return false;
				}
				const text = (rawText == null ? '' : String(rawText)).trim();
				const imageUrls = Array.isArray(rawImageUrls)
					? rawImageUrls.map((item) => (item == null ? '' : String(item).trim())).filter((item) => item)
					: [];
				const explicitQuote = Object.prototype.hasOwnProperty.call(opts, 'quoteMeta');
				const quoteMeta = this.normalizeComposerQuoteMeta(explicitQuote ? opts.quoteMeta : this.composerQuote);
				const payloadText = this.serializeQuotedMessageText(text, quoteMeta);
				const attachmentMode = opts && opts.attachmentMode === 'expression' ? 'expression' : imageUrls.length ? 'photo' : '';
				const attachmentHint =
					attachmentMode === 'expression' ? this.normalizeLocalExpressionHint(opts && opts.attachmentHint) : '';
				if ((!text && !imageUrls.length && !quoteMeta.visible) || !this.char || this.sending) return false;
				if (opts.checkUploading !== false && this.hasUploadingComposerImages()) {
					uni.showToast({
						title: this.tx('chat_image_uploading', '图片还在上传中，请稍等'),
						icon: 'none'
					});
					return false;
				}
				const userVoiceMeta = this.normalizeOutgoingUserVoiceMeta(opts.userVoiceMeta);
				this.silentGenerationInterruptUntil = 0;
				this.clearGenerationRecovery();
				this.clearPendingVoiceStart();
				this.interruptAssistantVoiceRound();
				this.closeReplySuggestions();
				this.closeChatAttachmentMenu();
				this.closeExpressionPanel();
				const uid = 'u_' + Date.now();
				const optimisticImages = imageUrls.slice();
				this.messages = this.messages.concat({
					id: uid,
					role: 'user',
					text,
					quote: quoteMeta.visible ? quoteMeta : null,
					imageUrls: optimisticImages,
					voiceUrl: userVoiceMeta.voiceUrl,
					voiceDurationMs: userVoiceMeta.durationMs
				});
				if (userVoiceMeta.audioUrl) {
					this.setUserVoiceEntry(uid, {
						audioUrl: userVoiceMeta.audioUrl,
						voiceUrl: userVoiceMeta.voiceUrl,
						durationMs: userVoiceMeta.durationMs,
						state: 'ready',
						error: ''
					});
				}
				if (optimisticImages.length) {
					this.upsertLocalChatImageEntry({
						messageId: uid,
						assistantMessageId: '',
						text,
						imageUrls: optimisticImages,
						createdAt: Date.now()
					});
				}
				if (opts.clearDraft) {
					this.draft = '';
					this.clearStoredDraft();
					this.draftRestoredNoticeVisible = false;
				}
				if (opts.clearComposerImages) {
					this.composerImages = [];
				}
				if (opts.clearQuote !== false) {
					this.composerQuote = createComposerQuoteState();
				}
				this.sending = true;
				this.followBottom = true;
				this.atChatBottom = true;
				this.scrollChatToBottom({ immediate: true });
				this.notifyCompanionThinking();

				const tavernApi = require('@/common/tavernApi.js');
				if (tavernApi.jgEnabled()) {
					const cid = Number(this.char && this.char.id) || Number(this.cid);
					const clientUid = tavernApi.getClientUid();
					const payload = Object.assign(
						{
							characterId: cid,
							clientUid,
							content: payloadText,
							imageUrls,
							attachmentMode,
							attachmentHint,
							voiceUrl: userVoiceMeta.voiceUrl,
							voiceDurationMs: userVoiceMeta.durationMs,
							temperature: 0.85
						},
						this.buildAssistantExpressionPayloadFields()
					);

					if (tavernApi.jgStreamEnabled()) {
						const rid = 'r_' + Date.now();
						this.beginAssistantStreaming(rid, 'generate');
						this.notifyCompanionReplying('generate');
						this.streamAbortController = new AbortController();
						this.messages = this.messages.concat({
							id: rid,
							role: 'char',
							text: '',
							swipes: [''],
							swipeIndex: 0
						});
						tavernApi
							.postTavernChatStream(
								payload,
								{
									onDelta: (piece) => {
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid) {
											const next = (last.text || '') + piece;
											this.$set(last, 'text', next);
											this.prepareStreamingAssistantVoice(last).catch(() => {});
										}
										this.followScrollNextTick();
									},
									onDone: (data) => {
										const row = this.messages[this.messages.length - 1];
										if (!row || row.id !== rid) {
											this.followScrollNextTick();
											return;
										}
										if (data && data.content != null) {
											this.$set(row, 'text', String(data.content).trim());
										}
										if (data && data.messageId) {
											this.updateAssistantVoiceEntryId(rid, data.messageId);
											this.moveAssistantStreamingMessageId(rid, data.messageId);
											this.$set(row, 'id', this.normalizeDbMessageId(data.messageId));
										}
										this.patchLastOptimisticUserId(data && data.userMessageId, data && data.messageId);
										if (Array.isArray(data && data.swipes)) {
											this.$set(
												row,
												'swipes',
												data.swipes.map((x) => String(x))
											);
										if (typeof data.swipeIndex === 'number') {
											this.$set(row, 'swipeIndex', data.swipeIndex);
										}
									}
										if (data && data.cancelled) {
											this.queueStopSync(700);
											this.showGenerationStopToast('stopped', '已停止');
										}
										this.finishAssistantStreaming(row.id);
										this.notifyCompanionReply(row.text);
										if (!String(row.id || '').startsWith('db_')) {
											this.prepareAssistantVoiceForRow(row, {
												autoplay: this.shouldAutoPlayAssistantVoice(),
												toastOnError: false,
												allowStreaming: true,
												includeTrailingPartial: true
											}).catch(() => {});
										}
										this.applyAssistantExpressionForRow(row);
										this.followScrollNextTick();
									},
									onAbort: () => {
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid && !String(last.text || '').trim()) {
											this.messages = this.messages.filter((x) => x.id !== rid);
										}
										this.queueStopSync(700);
										this.finishAssistantStreaming(rid);
										this.showGenerationStopToast('stopped', '已停止');
									},
									onError: (e) => {
										const result = this.handleCommercialError(e, this.tx('chat_failed', '对话失败'), {
											skipToastWhenPrompted: true
										});
										const msg = result.message;
										this.notifyCompanionError(msg);
										if (!result.prompted) {
											this.showErrorToast(msg);
										}
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid) {
											const partial = String(last.text || '').trim();
											if (!partial) {
												this.$set(last, 'text', '');
											}
											this.markGenerationRecovery(rid, {
												message: msg,
												partialText: partial,
												canRegen: false,
												retryText: text
											});
										}
										this.finishAssistantStreaming(rid);
									}
								},
								{ signal: this.streamAbortController.signal }
							)
							.finally(() => {
								this.finishAssistantStreaming(rid);
								this.streamAbortController = null;
								this.sending = false;
							});
						return true;
					}

					tavernApi
						.postTavernChat(payload)
						.then((data) => {
							this.patchLastOptimisticUserId(data && data.userMessageId, data && data.messageId);
							const aid =
								data && data.messageId ? this.normalizeDbMessageId(data.messageId) : '';
							const rid = aid || 'r_' + Date.now();
							const raw = data && data.content;
							const reply =
								raw != null && String(raw).trim() !== ''
									? String(raw).trim()
									: this.tx('empty_ai', '模型未返回内容');
							const sw =
								Array.isArray(data && data.swipes) && data.swipes.length
									? data.swipes.map((x) => String(x))
									: [reply];
							const si = typeof (data && data.swipeIndex) === 'number' ? data.swipeIndex : 0;
							this.messages = this.messages.concat({
								id: rid,
								role: 'char',
								text: reply,
								swipes: sw,
								swipeIndex: si
							});
							const appendedRow = this.messages[this.messages.length - 1];
							this.applyAssistantExpressionForRow(appendedRow);
							this.notifyCompanionReply(appendedRow.text);
							this.scrollChatToBottom({ immediate: true });
						})
						.catch((e) => {
							const rid = 'r_' + Date.now();
							const result = this.handleCommercialError(e, this.tx('chat_failed', '对话失败'), {
								skipToastWhenPrompted: true
							});
							const msg = result.message;
							this.notifyCompanionError(msg);
							if (!result.prompted) {
								this.showErrorToast(msg);
							}
							this.messages = this.messages.concat({
								id: rid,
								role: 'char',
								text: ''
							});
							this.markGenerationRecovery(rid, {
								message: msg,
								partialText: '',
								canRegen: false,
								retryText: text
							});
							this.scrollChatToBottom({ immediate: true });
						})
						.finally(() => {
							this.sending = false;
						});
					return true;
				}

				uni.showToast({ title: this.tx('backend_disabled', 'Backend unavailable'), icon: 'none' });
				this.notifyCompanionError(this.tx('backend_disabled', 'Backend unavailable'));
				this.sending = false;
				return false;
			},
			send() {
				return this.submitOutgoingMessage(this.draft, this.pendingChatImageUrls(), {
					clearDraft: true,
					clearComposerImages: true
				});
			}
		}
	};
</script>

<style scoped lang="scss">
	$bg: #12122b;
	$card: #1a1a38;
	$text: #f1f5f9;

	.wrap {
		position: relative;
		height: 100vh;
		min-height: 100vh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		isolation: isolate;
		background-color: transparent;
		background-image: url('/static/login.png');
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
	}

	/* #ifdef H5 */
	.wrap {
		height: 100dvh;
		min-height: 100dvh;
	}
	/* #endif */

	.wrap::before,
	.wrap::after {
		content: '';
		position: absolute;
		inset: 0;
		z-index: 0;
		pointer-events: none;
	}

.chat-default-bg,
.chat-role-bg {
	position: absolute;
	inset: 0;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	z-index: 0;
	width: 100%;
	height: 100%;
	display: block;
	pointer-events: none;
}

.chat-role-bg {
	z-index: 0;
}

	.wrap--with-bg::before {
		background-image: var(--chat-bg-image);
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
		filter: none;
		transform: none;
		will-change: transform;
	}

	.wrap--with-bg::after {
		background: rgba(255, 255, 255, 0.02);
	}

.wrap > * {
	position: relative;
	z-index: 1;
}

.wrap > .chat-default-bg,
.wrap > .chat-role-bg {
	position: absolute;
	z-index: 0;
}

	.chat-fill {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 48rpx 40rpx;
		gap: 28rpx;
		min-height: 400rpx;
	}

	.chat-fill-txt {
		font-size: 28rpx;
		color: #94a3b8;
		text-align: center;
		line-height: 1.55;
		padding: 0 24rpx;
	}

	.chat-fill--err .chat-fill-txt {
		color: #fca5a5;
	}

	.chat-fill-retry {
		padding: 16rpx 44rpx;
		background: linear-gradient(90deg, #6366f1, #8b5cf6);
		color: #fff;
		border-radius: 999rpx;
		font-size: 28rpx;
		font-weight: 600;
	}

	.chat-fill-back {
		font-size: 26rpx;
		color: #a78bfa;
		padding: 12rpx;
	}

	.nav-link {
		font-size: 28rpx;
		color: #a78bfa;
		padding: 0 16rpx;
	}

	.nav-right-tools {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.nav-voice-toggle {
		width: 68rpx;
		height: 68rpx;
		border-radius: 22rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(14, 23, 36, 0.24);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08),
			0 10rpx 22rpx rgba(8, 18, 30, 0.1);
	}

	.nav-voice-toggle--off {
		opacity: 0.76;
	}

	.nav-voice-toggle-icon {
		width: 34rpx;
		height: 34rpx;
	}

	.nav-voice-config {
		width: 68rpx;
		height: 68rpx;
		border-radius: 22rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(12, 21, 34, 0.18);
		border: 1rpx solid rgba(255, 255, 255, 0.1);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.06),
			0 10rpx 22rpx rgba(8, 18, 30, 0.08);
	}

	.nav-voice-config--active {
		background: linear-gradient(135deg, rgba(56, 189, 248, 0.3), rgba(244, 114, 182, 0.22));
		border-color: rgba(125, 211, 252, 0.4);
	}

	.nav-voice-config-icon {
		width: 34rpx;
		height: 34rpx;
	}

	.tool-bar {
		flex-shrink: 0;
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		padding: 12rpx 24rpx;
		background: rgba(10, 14, 28, 0.18);
		backdrop-filter: blur(12rpx);
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
	}

	.tool-i {
		font-size: 24rpx;
		color: #e2e8f0;
		padding: 8rpx 20rpx;
		border-radius: 999rpx;
		background: rgba(124, 58, 237, 0.2);
	}

	.tool-i--disabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.tool-hint {
		flex-shrink: 0;
		padding: 0 24rpx 10rpx;
		background: rgba(10, 14, 28, 0.12);
		backdrop-filter: blur(8rpx);
	}

	.tool-hint-txt {
		font-size: 20rpx;
		color: #64748b;
		line-height: 1.4;
	}

	.memory-bar {
		flex-shrink: 0;
		padding: 10rpx 24rpx 14rpx;
		background: rgba(10, 14, 28, 0.14);
		backdrop-filter: blur(10rpx);
		border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
	}

	.memory-bar-txt {
		font-size: 22rpx;
		color: #94a3b8;
		line-height: 1.45;
		display: block;
	}

	.chat-scroll {
		flex: 1;
		height: 0;
		min-height: 0;
		padding: 20rpx 24rpx;
		box-sizing: border-box;
		opacity: 1;
		transition: opacity 0.12s ease;
	}

	.chat-scroll--preparing {
		opacity: 0;
		pointer-events: none;
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

	.msg-row {
		display: flex;
		align-items: flex-end;
		gap: 10rpx;
		margin-bottom: 28rpx;

		&.them {
			justify-content: flex-start;
		}

		&.me {
			justify-content: flex-end;
		}
	}

	.av {
		width: 72rpx;
		height: 72rpx;
		border-radius: 50%;
		flex-shrink: 0;
		overflow: hidden;
		border: 2rpx solid rgba(255, 255, 255, 0.18);
		box-shadow: 0 10rpx 22rpx rgba(15, 23, 42, 0.14);
		background: rgba(255, 255, 255, 0.12);
		/* #ifdef H5 */
		object-fit: cover;
		/* #endif */
	}

	.av--char {
		/* #ifdef H5 */
		cursor: zoom-in;
		transition: transform 0.22s ease, box-shadow 0.22s ease;
		/* #endif */
	}

	/* #ifdef H5 */
	.av--char:hover {
		transform: translateY(-2rpx) scale(1.03);
		box-shadow: 0 12rpx 20rpx rgba(12, 18, 34, 0.26);
	}
	/* #endif */

	.bubble {
		position: relative;
		max-width: 70%;
		margin: 0 8rpx;
		padding: 22rpx 24rpx 20rpx;
		border-radius: 28rpx 28rpx 28rpx 14rpx;
		background: linear-gradient(180deg, rgba(10, 12, 18, 0.52) 0%, rgba(10, 12, 18, 0.34) 100%);
		backdrop-filter: blur(12rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow:
			0 14rpx 30rpx rgba(15, 23, 42, 0.16),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
		overflow: visible;
	}

	.bubble::before {
		content: '';
		position: absolute;
		left: 14rpx;
		right: 14rpx;
		top: 2rpx;
		height: 16rpx;
		border-radius: 999rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.18) 0%, rgba(255, 255, 255, 0) 100%);
		opacity: 0.72;
		pointer-events: none;
	}

	.bubble::after {
		content: '';
		position: absolute;
		bottom: 14rpx;
		width: 18rpx;
		height: 18rpx;
		transform: rotate(45deg);
		pointer-events: none;
	}

	.msg-image-list {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-bottom: 14rpx;
	}

	.msg-image {
		width: 188rpx;
		height: 188rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.14);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
		box-shadow: 0 8rpx 18rpx rgba(15, 23, 42, 0.12);
	}

	.msg-row.me .bubble {
		background: linear-gradient(135deg, rgba(124, 58, 237, 0.44) 0%, rgba(219, 39, 119, 0.3) 100%);
		backdrop-filter: blur(18rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
		border-radius: 28rpx 28rpx 14rpx 28rpx;
	}

	.msg-row.them .bubble::after {
		left: -8rpx;
		background: inherit;
		border-left: 1rpx solid rgba(255, 255, 255, 0.1);
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.1);
	}

	.msg-row.me .bubble::after {
		right: -8rpx;
		background: inherit;
		border-right: 1rpx solid rgba(255, 255, 255, 0.12);
		border-top: 1rpx solid rgba(255, 255, 255, 0.12);
	}

	.msg-row.them .bubble {
		max-width: 78%;
		padding: 20rpx 22rpx 18rpx;
	}

.bubble--has-image {
	padding-top: 18rpx;
}

.local-image-prompt-row {
	margin: 10rpx 4rpx 2rpx;
}

.local-image-prompt-text {
	display: block;
	font-size: 24rpx;
	line-height: 1.55;
	color: rgba(255, 255, 255, 0.92);
	word-break: break-word;
}

.bubble--image-only {
	padding: 10rpx;
	background: rgba(255, 255, 255, 0.08);
}

	.bubble--image-only::before {
		opacity: 0.46;
		left: 10rpx;
		right: 10rpx;
	}

	.bubble--image-only .msg-image-list {
		margin-bottom: 0;
	}

	.bubble--image-only .msg-image {
		width: 210rpx;
		height: 210rpx;
	}

	.txt {
		font-size: 28rpx;
		color: $text;
		line-height: 1.58;
		letter-spacing: 0.15rpx;
		word-break: break-word;
	}

	.msg-quote-preview {
		margin-bottom: 16rpx;
		padding: 14rpx 16rpx;
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.12);
		border-left: 6rpx solid rgba(255, 255, 255, 0.34);
	}

	.msg-row.me .msg-quote-preview {
		background: rgba(255, 255, 255, 0.78);
		border-left-color: rgba(52, 143, 184, 0.42);
	}

	.msg-quote-preview-speaker {
		display: block;
		font-size: 21rpx;
		font-weight: 700;
		line-height: 1.4;
		color: rgba(255, 255, 255, 0.76);
	}

	.msg-row.me .msg-quote-preview-speaker {
		color: #34617b;
	}

	.msg-quote-preview-text {
		display: block;
		margin-top: 6rpx;
		font-size: 23rpx;
		line-height: 1.55;
		color: rgba(255, 255, 255, 0.92);
		word-break: break-word;
	}

	.msg-row.me .msg-quote-preview-text {
		color: #1f3f56;
	}

	.generation-recovery {
		margin-top: 16rpx;
		padding: 16rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.12);
		border: 1rpx solid rgba(255, 255, 255, 0.16);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
	}

	.generation-recovery-copy {
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.generation-recovery-title {
		font-size: 23rpx;
		font-weight: 700;
		color: rgba(255, 255, 255, 0.95);
	}

	.generation-recovery-message {
		font-size: 22rpx;
		line-height: 1.5;
		color: rgba(255, 255, 255, 0.76);
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
		font-weight: 700;
		color: #1f5f76;
		background: rgba(255, 255, 255, 0.86);
	}

	.generation-recovery-btn--primary {
		color: #fff;
		background: linear-gradient(135deg, #348fb8 0%, #76d2dd 100%);
	}

	.generation-recovery-close {
		width: 48rpx;
		height: 48rpx;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.8);
		background: rgba(15, 23, 42, 0.2);
	}

	.md-inner {
		font-size: 30rpx;
		color: rgba(255, 255, 255, 0.94);
		line-height: 1.76;
		word-break: break-word;
		text-shadow: 0 1rpx 7rpx rgba(0, 0, 0, 0.18);
	}

	.md-inner--native {
		display: flex;
		flex-direction: column;
		gap: 10rpx;
	}

	.st-chat-seg-native {
		display: block;
	}

	.st-chat-seg-text {
		font-size: 29rpx;
		line-height: 1.78;
		word-break: break-word;
		white-space: pre-wrap;
	}

	.st-chat-seg-native--speech .st-chat-seg-text {
		color: #c05781;
		font-weight: 700;
		letter-spacing: 0.08rpx;
	}

	.st-chat-seg-native--thought .st-chat-seg-text {
		color: #4e6678;
		font-style: italic;
		font-size: 28rpx;
	}

	.st-chat-seg-native--thought {
		padding-left: 12rpx;
		border-left: 4rpx solid rgba(52, 143, 184, 0.34);
	}

	.st-chat-seg-native--action .st-chat-seg-text {
		color: #3d5c70;
		font-style: italic;
		font-size: 28rpx;
	}

	.st-chat-seg-native--narration .st-chat-seg-text {
		color: #17344b;
		font-weight: 400;
	}

	.md-inner >>> .st-chat-render {
		display: flex;
		flex-direction: column;
		gap: 10rpx;
	}

	.md-inner >>> .st-chat-seg {
		display: block;
	}

	.md-inner >>> .st-chat-seg > *:first-child {
		margin-top: 0;
	}

	.md-inner >>> .st-chat-seg > *:last-child {
		margin-bottom: 0;
	}

	.md-inner >>> .st-chat-seg--speech {
		color: #ffc2d3;
		font-weight: 700;
		font-size: 29rpx;
		line-height: 1.72;
		letter-spacing: 0.08rpx;
		text-shadow: 0 1rpx 10rpx rgba(0, 0, 0, 0.24);
	}

	.md-inner >>> .st-chat-seg--speech p {
		color: inherit;
		display: block;
	}

	.md-inner >>> .st-chat-seg--thought,
	.md-inner >>> .st-chat-seg--action {
		color: rgba(255, 255, 255, 0.82);
		font-weight: 400;
		font-size: 28rpx;
		line-height: 1.84;
	}

	.md-inner >>> .st-chat-seg--thought p,
	.md-inner >>> .st-chat-seg--action p {
		color: inherit;
	}

	.md-inner >>> .st-chat-seg--thought {
		font-style: italic;
		color: rgba(240, 228, 255, 0.94);
		padding-left: 12rpx;
		border-left: 4rpx solid rgba(240, 228, 255, 0.18);
	}

	.md-inner >>> .st-chat-seg--action {
		font-style: italic;
		letter-spacing: 0.12rpx;
		padding-left: 12rpx;
	}

	.md-inner >>> .st-chat-seg--narration {
		color: rgba(255, 255, 255, 0.98);
		font-weight: 400;
		font-size: 29rpx;
		line-height: 1.82;
	}

	.md-inner >>> p {
		margin: 0.14em 0;
	}

	.md-inner >>> p:first-child {
		margin-top: 0;
	}

	.md-inner >>> p:last-child {
		margin-bottom: 0;
	}

	.md-inner >>> code {
		background: rgba(255, 255, 255, 0.12);
		padding: 2rpx 8rpx;
		border-radius: 6rpx;
		font-size: 26rpx;
		color: rgba(255, 255, 255, 0.96);
	}

	.md-inner >>> pre {
		background: rgba(0, 0, 0, 0.24);
		padding: 16rpx;
		border-radius: 12rpx;
		overflow-x: auto;
		margin: 0.4em 0;
		/* #ifdef H5 */
		cursor: pointer;
		/* #endif */
	}

	.user-edit-tag {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.55);
	}

	.msg-row.me .user-edit-tag {
		text-align: right;
	}

	.edit-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.55);
		z-index: 2000;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 32rpx;
		padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}

	.commercial-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2100;
		background: rgba(5, 8, 24, 0.72);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 32rpx;
		box-sizing: border-box;
	}

	.message-action-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2050;
		background: rgba(6, 10, 24, 0.04);
	}

	.char-image-mask {
		position: fixed;
		inset: 0;
		z-index: 2300;
		background: rgba(3, 6, 16, 0.94);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 36rpx;
		box-sizing: border-box;
	}

	.char-image-shell {
		position: relative;
		width: 100%;
		max-width: 760rpx;
		max-height: calc(100vh - 72rpx);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.char-image-close {
		position: absolute;
		top: -12rpx;
		right: -4rpx;
		z-index: 2;
		width: 72rpx;
		height: 72rpx;
		line-height: 72rpx;
		text-align: center;
		font-size: 48rpx;
		color: rgba(255, 255, 255, 0.92);
	}

	.char-image-full {
		width: 100%;
		height: calc(100vh - 120rpx);
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.03);
	}

	.character-voice-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2350;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 32rpx 20rpx calc(env(safe-area-inset-bottom) + 24rpx);
		background: rgba(10, 18, 24, 0.26);
		box-sizing: border-box;
	}

	.character-voice-sheet {
		width: 100%;
		max-width: 760rpx;
		padding: 26rpx;
		display: flex;
		flex-direction: column;
		min-height: 0;
		overflow: hidden;
		border-radius: 32rpx;
		background: rgba(255, 255, 255, 0.94);
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		box-shadow: 0 20rpx 52rpx rgba(15, 23, 42, 0.16);
		box-sizing: border-box;
	}

	.character-voice-sheet-top {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 20rpx;
		flex-shrink: 0;
	}

	.character-voice-scroll {
		flex: 1;
		min-height: 0;
		margin-top: 4rpx;
	}

	.character-voice-scroll-body {
		padding-right: 4rpx;
		padding-bottom: 8rpx;
		box-sizing: border-box;
	}

	.character-voice-sheet-head {
		display: flex;
		align-items: center;
		gap: 18rpx;
		min-width: 0;
	}

	.character-voice-avatar {
		width: 84rpx;
		height: 84rpx;
		border-radius: 26rpx;
		background: rgba(255, 255, 255, 0.72);
		border: 3rpx solid rgba(255, 255, 255, 0.9);
		box-shadow: 0 10rpx 24rpx rgba(36, 70, 88, 0.1);
		flex-shrink: 0;
	}

	.character-voice-head-copy {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.character-voice-title {
		display: block;
		font-size: 32rpx;
		font-weight: 800;
		color: #203846;
	}

	.character-voice-sub {
		display: block;
		font-size: 24rpx;
		line-height: 1.65;
		color: #5f7280;
	}

	.character-voice-entry {
		display: inline-flex;
		align-items: center;
		margin-top: 4rpx;
		padding: 10rpx 16rpx;
		border-radius: 999rpx;
		background: rgba(63, 143, 159, 0.12);
		color: #236f82;
		font-size: 22rpx;
		font-weight: 600;
		align-self: flex-start;
	}

	.character-voice-close {
		width: 58rpx;
		height: 58rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.76);
		color: #203846;
		font-size: 30rpx;
		line-height: 1;
		flex-shrink: 0;
	}

	.character-voice-global-card {
		margin-top: 20rpx;
		padding: 22rpx 24rpx;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.76);
		border: 1rpx solid rgba(79, 147, 163, 0.14);
	}

	.character-voice-global-head {
		display: flex;
		align-items: flex-start;
	}

	.character-voice-global-copy {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.character-voice-global-title {
		display: block;
		font-size: 26rpx;
		font-weight: 700;
		color: #203846;
	}

	.character-voice-global-sub {
		display: block;
		font-size: 22rpx;
		line-height: 1.65;
		color: #5f7280;
	}

	.character-voice-global-empty {
		display: block;
		margin-top: 18rpx;
		font-size: 22rpx;
		line-height: 1.7;
		color: #5f7280;
	}

	.character-voice-global-empty--error {
		color: #c2410c;
	}

	.character-voice-global-pills {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 18rpx;
	}

	.character-voice-global-pill {
		max-width: 100%;
		padding: 10rpx 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.78);
		border: 1rpx solid rgba(79, 147, 163, 0.12);
		color: #203846;
		font-size: 22rpx;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.character-voice-global-actions {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 16rpx;
	}

	.character-voice-global-action {
		display: inline-flex;
		align-items: center;
		padding: 12rpx 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.78);
		color: #53656f;
		font-size: 22rpx;
		font-weight: 600;
		flex-shrink: 0;
	}

	.character-voice-global-action--primary {
		background: rgba(63, 143, 159, 0.12);
		color: #236f82;
	}

	.character-voice-field {
		margin-top: 22rpx;
		padding: 24rpx;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.76);
		border: 1rpx solid rgba(79, 147, 163, 0.12);
	}

	.character-voice-field--disabled {
		opacity: 0.56;
	}

	.character-voice-label-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
	}

	.character-voice-label {
		display: block;
		font-size: 26rpx;
		font-weight: 700;
		color: #203846;
	}

	.character-voice-meta {
		font-size: 22rpx;
		color: #5f7280;
	}

	.character-voice-switch-row {
		display: flex;
		gap: 12rpx;
		margin-top: 16rpx;
	}

	.character-voice-switch {
		min-width: 132rpx;
		height: 66rpx;
		padding: 0 24rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.76);
		color: #53656f;
		font-size: 24rpx;
		font-weight: 600;
	}

	.character-voice-switch--active {
		background: #3f8f9f;
		color: #fff;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.2);
	}

	.character-voice-input {
		width: 100%;
		height: 78rpx;
		margin-top: 16rpx;
		padding: 0 22rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.78);
		border: 1rpx solid rgba(79, 147, 163, 0.14);
		box-sizing: border-box;
		font-size: 26rpx;
		color: #203846;
	}

	.character-voice-template-intro {
		display: block;
		margin-top: 14rpx;
		font-size: 22rpx;
		line-height: 1.7;
		color: #5f7280;
	}

	.character-voice-template-scroll {
		margin-top: 18rpx;
		max-height: 420rpx;
		padding-right: 4rpx;
		box-sizing: border-box;
	}

	.character-voice-template-list {
		display: flex;
		flex-direction: column;
		gap: 12rpx;
	}

	.character-voice-template-card {
		display: flex;
		align-items: center;
		gap: 14rpx;
		min-height: 112rpx;
		padding: 14rpx;
		overflow: hidden;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.82);
		border: 1rpx solid rgba(79, 147, 163, 0.12);
		box-shadow: 0 12rpx 24rpx rgba(36, 70, 88, 0.08);
		box-sizing: border-box;
	}

	.character-voice-template-card--active {
		border-color: rgba(79, 147, 163, 0.66);
		box-shadow:
			0 0 0 3rpx rgba(79, 147, 163, 0.1),
			0 14rpx 28rpx rgba(36, 70, 88, 0.1);
	}

	.character-voice-template-card__cover {
		display: flex;
		width: 76rpx;
		height: 76rpx;
		flex-shrink: 0;
		border-radius: 18rpx;
		overflow: hidden;
		background: rgba(237, 245, 244, 0.96);
		opacity: 1;
		filter: none;
	}

	.character-voice-template-card__cover--placeholder {
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 22rpx;
		font-weight: 700;
		color: #53656f;
	}

	.character-voice-template-card__body {
		min-width: 0;
		flex: 1;
	}

	.character-voice-template-card__head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 14rpx;
	}

	.character-voice-template-card__title {
		flex: 1;
		min-width: 0;
		font-size: 25rpx;
		font-weight: 700;
		color: #203846;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.character-voice-template-card__badge {
		flex-shrink: 0;
		padding: 6rpx 14rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.72);
		font-size: 20rpx;
		font-weight: 700;
		color: #53656f;
		max-width: 220rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.character-voice-template-card__badge--ready {
		background: rgba(187, 247, 208, 0.9);
		color: #166534;
	}

	.character-voice-template-card__badge--failed {
		background: rgba(254, 205, 211, 0.9);
		color: #be123c;
	}

	.character-voice-template-card__badge--requires_api_key,
	.character-voice-template-card__badge--requires_provider,
	.character-voice-template-card__badge--requires_model {
		background: rgba(254, 240, 138, 0.9);
		color: #854d0e;
	}

	.character-voice-template-card__meta {
		display: block;
		margin-top: 6rpx;
		font-size: 21rpx;
		color: #236f82;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.character-voice-template-card__desc {
		display: -webkit-box;
		margin-top: 4rpx;
		font-size: 22rpx;
		line-height: 1.45;
		color: #5f7280;
		overflow: hidden;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
	}

	.character-voice-template-card__check {
		flex-shrink: 0;
		padding: 7rpx 12rpx;
		border-radius: 999rpx;
		background: #4f93a3;
		color: #ffffff;
		font-size: 19rpx;
		font-weight: 800;
		line-height: 1.2;
	}

	.character-voice-template-active {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 18rpx;
		margin-top: 18rpx;
		padding: 20rpx 22rpx;
		border-radius: 24rpx;
		background: rgba(63, 143, 159, 0.1);
		border: 1rpx solid rgba(79, 147, 163, 0.14);
	}

	.character-voice-template-active__copy {
		flex: 1;
		min-width: 0;
	}

	.character-voice-template-active__title {
		display: block;
		font-size: 24rpx;
		font-weight: 700;
		color: #203846;
	}

	.character-voice-template-active__desc {
		display: block;
		margin-top: 8rpx;
		font-size: 21rpx;
		line-height: 1.6;
		color: #5f7280;
	}

	.character-voice-template-active__switch {
		flex-shrink: 0;
		padding: 0 18rpx;
		height: 58rpx;
		line-height: 58rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.78);
		font-size: 22rpx;
		font-weight: 700;
		color: #236f82;
	}

	.character-voice-manual-box {
		margin-top: 18rpx;
		padding: 20rpx;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.72);
		border: 1rpx solid rgba(79, 147, 163, 0.12);
	}

	.character-voice-manual-label {
		display: block;
		font-size: 22rpx;
		font-weight: 700;
		letter-spacing: 1rpx;
		color: #53656f;
	}

	.character-voice-chip-scroll {
		max-height: 172rpx;
		margin-top: 16rpx;
	}

	.character-voice-chip-scroll .character-voice-chip-row {
		margin-top: 0;
	}

	.character-voice-chip-row {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 16rpx;
	}

	.character-voice-chip {
		padding: 10rpx 22rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.76);
		color: #53656f;
		font-size: 22rpx;
	}

	.character-voice-chip--active {
		background: #3f8f9f;
		color: #fff;
		box-shadow: 0 10rpx 20rpx rgba(48, 103, 117, 0.18);
	}

	.character-voice-hint {
		display: block;
		margin-top: 14rpx;
		font-size: 22rpx;
		line-height: 1.7;
		color: #5f7280;
	}

	.character-voice-actions {
		display: flex;
		gap: 14rpx;
		flex-shrink: 0;
		margin-top: 18rpx;
		padding-top: 18rpx;
		border-top: 1rpx solid rgba(79, 147, 163, 0.12);
	}

	.character-voice-btn {
		flex: 1;
		height: 78rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		font-size: 26rpx;
		font-weight: 700;
	}

	.character-voice-btn--ghost {
		background: rgba(255, 255, 255, 0.76);
		color: #53656f;
	}

	.character-voice-btn--primary {
		background: #3f8f9f;
		color: #fff;
		box-shadow: 0 16rpx 28rpx rgba(48, 103, 117, 0.2);
	}

	.character-image-mask {
		@extend .character-voice-mask;
	}

	.character-image-sheet {
		@extend .character-voice-sheet;
	}

	.character-image-sheet-top {
		@extend .character-voice-sheet-top;
	}

	.character-image-sheet-head {
		@extend .character-voice-sheet-head;
	}

	.character-image-avatar {
		@extend .character-voice-avatar;
	}

	.character-image-head-copy {
		@extend .character-voice-head-copy;
	}

	.character-image-title {
		@extend .character-voice-title;
	}

	.character-image-sub {
		@extend .character-voice-sub;
	}

	.character-image-close {
		@extend .character-voice-close;
	}

	.character-image-global-card {
		@extend .character-voice-global-card;
	}

	.character-image-global-copy {
		@extend .character-voice-global-copy;
	}

	.character-image-global-title {
		@extend .character-voice-global-title;
	}

	.character-image-global-sub {
		@extend .character-voice-global-sub;
	}

	.character-image-global-empty {
		@extend .character-voice-global-empty;
	}

	.character-image-global-empty--error {
		@extend .character-voice-global-empty--error;
	}

	.character-image-global-pills {
		@extend .character-voice-global-pills;
	}

	.character-image-global-pill {
		@extend .character-voice-global-pill;
	}

	.character-image-global-actions {
		@extend .character-voice-global-actions;
	}

	.character-image-global-action {
		@extend .character-voice-global-action;
	}

	.character-image-field {
		@extend .character-voice-field;
	}

	.character-image-field--disabled {
		@extend .character-voice-field--disabled;
	}

	.character-image-label {
		@extend .character-voice-label;
	}

	.character-image-switch-row {
		@extend .character-voice-switch-row;
	}

	.character-image-switch {
		@extend .character-voice-switch;
	}

	.character-image-switch--active {
		@extend .character-voice-switch--active;
	}

	.character-image-input {
		@extend .character-voice-input;
	}

	.character-image-chip-row {
		@extend .character-voice-chip-row;
		margin-top: 16rpx;
	}

	.character-image-chip {
		@extend .character-voice-chip;
	}

	.character-image-chip--active {
		@extend .character-voice-chip--active;
	}

	.character-image-hint {
		@extend .character-voice-hint;
	}

	.character-image-textarea {
		width: 100%;
		min-height: 180rpx;
		margin-top: 16rpx;
		padding: 20rpx 22rpx;
		border-radius: 24rpx;
		background: rgba(248, 250, 252, 0.94);
		border: 1rpx solid rgba(191, 219, 254, 0.44);
		box-sizing: border-box;
		font-size: 26rpx;
		line-height: 1.65;
		color: #1f2937;
	}

	.character-image-actions {
		@extend .character-voice-actions;
	}

	.character-image-btn {
		@extend .character-voice-btn;
	}

	.character-image-btn--ghost {
		@extend .character-voice-btn--ghost;
	}

	.character-image-btn--muted {
		background: rgba(224, 231, 255, 0.88);
		color: #4c5f8d;
	}

	.character-image-btn--primary {
		@extend .character-voice-btn--primary;
	}

	.character-image-btn--disabled {
		opacity: 0.6;
		pointer-events: none;
	}

	.commercial-card {
		width: 100%;
		max-width: 620rpx;
		padding: 34rpx 30rpx 28rpx;
		border-radius: 26rpx;
		background: linear-gradient(180deg, rgba(24, 24, 48, 0.98) 0%, rgba(20, 20, 40, 0.98) 100%);
		border: 1rpx solid rgba(216, 180, 254, 0.22);
		box-shadow: 0 24rpx 72rpx rgba(15, 23, 42, 0.42);
	}

	.commercial-title {
		display: block;
		font-size: 34rpx;
		font-weight: 700;
		color: #f8fafc;
	}

	.commercial-sub {
		display: block;
		margin-top: 18rpx;
		font-size: 26rpx;
		line-height: 1.7;
		color: #cbd5e1;
	}

	.commercial-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 28rpx;
	}

	.commercial-btn {
		flex: 1;
		height: 76rpx;
		line-height: 76rpx;
		text-align: center;
		border-radius: 999rpx;
		font-size: 26rpx;
		font-weight: 600;
	}

	.commercial-btn--ghost {
		color: #cbd5e1;
		background: rgba(148, 163, 184, 0.12);
	}

	.commercial-btn--muted {
		color: #f8fafc;
		background: rgba(91, 33, 182, 0.34);
	}

	.commercial-btn--primary {
		color: #fff;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
	}

	.edit-panel {
		width: 100%;
		max-height: 70vh;
		background: #1a1a38;
		border-radius: 20rpx;
		padding: 28rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-sizing: border-box;
	}

	.edit-title {
		display: block;
		font-size: 32rpx;
		color: #f1f5f9;
		margin-bottom: 12rpx;
	}

	.edit-sub {
		display: block;
		font-size: 24rpx;
		color: #94a3b8;
		line-height: 1.4;
		margin-bottom: 20rpx;
	}

	.edit-ta {
		width: 100%;
		min-height: 200rpx;
		padding: 20rpx;
		font-size: 28rpx;
		color: #f1f5f9;
		background: rgba(0, 0, 0, 0.25);
		border-radius: 12rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-sizing: border-box;
		margin-bottom: 24rpx;
	}

	.edit-actions {
		display: flex;
		justify-content: flex-end;
		gap: 24rpx;
	}

	.edit-btn {
		font-size: 28rpx;
		padding: 16rpx 28rpx;
		border-radius: 12rpx;
	}

	.message-action-menu {
		position: absolute;
		min-width: 236rpx;
		overflow: hidden;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.98);
		border: 1rpx solid rgba(226, 232, 240, 0.92);
		box-shadow: 0 18rpx 48rpx rgba(15, 23, 42, 0.2);
		box-sizing: border-box;
	}

	.message-action-item {
		min-height: 90rpx;
		padding: 0 28rpx;
		display: flex;
		align-items: center;
		justify-content: flex-start;
		background: transparent;
		box-sizing: border-box;
	}

	.message-action-item + .message-action-item {
		border-top: 1rpx solid rgba(226, 232, 240, 0.92);
	}

	.message-action-item--danger .message-action-item-label {
		color: #d14343;
	}

	.message-action-item--disabled {
		opacity: 0.58;
		pointer-events: none;
	}

	.message-action-item-label {
		font-size: 30rpx;
		font-weight: 500;
		color: #10233d;
	}

	.edit-btn--muted {
		color: #94a3b8;
	}

	.edit-btn--primary {
		color: #fff;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
	}

	.md-inner >>> a {
		color: #d8b4fe;
	}

	.reply-help-panel {
		flex-shrink: 0;
		margin: 0 18rpx 10rpx;
		padding: 18rpx 18rpx 12rpx;
		border-radius: 24rpx;
		background: rgba(10, 14, 28, 0.22);
		backdrop-filter: blur(16rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow: 0 14rpx 32rpx rgba(15, 23, 42, 0.16);
	}

	.reply-help-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-bottom: 14rpx;
	}

	.reply-help-title {
		font-size: 24rpx;
		font-weight: 700;
		color: #f8fafc;
	}

	.reply-help-head-actions {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.reply-help-head-btn {
		padding: 8rpx 18rpx;
		border-radius: 999rpx;
		font-size: 22rpx;
		color: #cbd5e1;
		background: rgba(124, 58, 237, 0.16);
	}

	.reply-help-head-btn--disabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.reply-help-state {
		display: block;
		padding: 8rpx 6rpx 12rpx;
		font-size: 24rpx;
		line-height: 1.5;
		color: #94a3b8;
	}

	.reply-help-state--error {
		color: #fda4af;
	}

	.reply-help-list {
		display: flex;
		flex-direction: row;
		gap: 10rpx;
		overflow-x: auto;
		padding-bottom: 2rpx;
		/* #ifdef H5 */
		scrollbar-width: none;
		/* #endif */
	}

	/* #ifdef H5 */
	.reply-help-list::-webkit-scrollbar {
		display: none;
	}
	/* #endif */

	.reply-help-card {
		display: flex;
		align-items: flex-start;
		gap: 12rpx;
		flex: 0 0 286rpx;
		min-height: 112rpx;
		padding: 16rpx 15rpx;
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.06);
		border: 1rpx solid rgba(255, 255, 255, 0.06);
		box-sizing: border-box;
	}

	.reply-help-index {
		flex-shrink: 0;
		width: 34rpx;
		height: 34rpx;
		line-height: 34rpx;
		text-align: center;
		border-radius: 10rpx;
		font-size: 22rpx;
		font-weight: 700;
		color: #ec4899;
		background: rgba(255, 255, 255, 0.88);
	}

	.reply-help-text {
		flex: 1;
		font-size: 24rpx;
		line-height: 1.5;
		color: #f8fafc;
		display: -webkit-box;
		overflow: hidden;
		-webkit-line-clamp: 3;
		-webkit-box-orient: vertical;
		word-break: break-word;
	}

	.ai-disclaimer {
		flex-shrink: 0;
		padding: 0 24rpx 8rpx;
		text-align: center;
		pointer-events: none;
	}

	.ai-disclaimer-txt {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		padding: 7rpx 18rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		line-height: 1.2;
		letter-spacing: 1rpx;
		color: rgba(248, 252, 255, 0.82);
		background: rgba(8, 13, 22, 0.3);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
		backdrop-filter: blur(14rpx) saturate(116%);
		-webkit-backdrop-filter: blur(14rpx) saturate(116%);
	}

	.image-quick-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2280;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 24rpx 18rpx calc(env(safe-area-inset-bottom) + 18rpx);
		background: rgba(5, 10, 20, 0.16);
		box-sizing: border-box;
	}

	.image-quick-shell {
		width: 100%;
		max-width: 760rpx;
		flex-shrink: 0;
	}

	.image-quick-card {
		width: 100%;
		max-height: calc(100vh - 180rpx - env(safe-area-inset-bottom));
		overflow: hidden;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.9);
		border: 1rpx solid rgba(255, 255, 255, 0.74);
		box-shadow: 0 16rpx 36rpx rgba(31, 61, 92, 0.14);
		backdrop-filter: blur(18rpx) saturate(120%);
		-webkit-backdrop-filter: blur(18rpx) saturate(120%);
		box-sizing: border-box;
	}

	.image-quick-card-inner {
		padding: 18rpx 20rpx 20rpx;
		box-sizing: border-box;
	}

	.image-quick-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12rpx;
	}

	.image-quick-identity {
		display: flex;
		align-items: center;
		gap: 12rpx;
		min-width: 0;
	}

	.image-quick-avatar {
		width: 52rpx;
		height: 52rpx;
		border-radius: 16rpx;
		background: rgba(236, 242, 246, 0.88);
	}

	.image-quick-title {
		font-size: 26rpx;
		font-weight: 700;
		color: #18384d;
	}

	.image-quick-close {
		flex-shrink: 0;
		padding: 0 8rpx;
		font-size: 34rpx;
		line-height: 1;
		color: rgba(24, 56, 77, 0.6);
	}

	.image-quick-input {
		width: 100%;
		min-height: 88rpx;
		max-height: 320rpx;
		margin-top: 16rpx;
		padding: 18rpx 20rpx;
		border-radius: 22rpx;
		background: rgba(242, 247, 250, 0.92);
		font-size: 26rpx;
		line-height: 1.65;
		color: #1f2937;
		box-sizing: border-box;
	}

	.image-quick-actions {
		margin-top: 16rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 16rpx;
	}

	.image-quick-link {
		font-size: 24rpx;
		font-weight: 600;
		color: #3a7491;
	}

	.image-quick-btn {
		min-width: 180rpx;
		height: 64rpx;
		line-height: 64rpx;
		padding: 0 24rpx;
		border-radius: 999rpx;
		text-align: center;
		font-size: 24rpx;
		font-weight: 700;
		color: #fff;
		background: #4f93a3;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.2);
	}

	.image-quick-btn--disabled {
		opacity: 0.62;
	}

	.input-bar {
		flex-shrink: 0;
		position: relative;
		z-index: 6;
		display: flex;
		align-items: flex-end;
		gap: 14rpx;
		padding: 14rpx 18rpx calc(14rpx + env(safe-area-inset-bottom));
		background: rgba(9, 13, 24, 0.16);
		backdrop-filter: blur(14rpx);
		border-top: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.attach-fab-backdrop {
		position: fixed;
		inset: 0;
		z-index: 4;
		background: transparent;
	}

	.attach-fab-menu {
		position: absolute;
		left: 18rpx;
		bottom: calc(100% + 14rpx);
		z-index: 7;
		display: flex;
		flex-direction: column;
		gap: 14rpx;
	}

	.attach-fab-item {
		display: flex;
		align-items: center;
		gap: 12rpx;
		animation: attach-fab-pop 0.18s ease-out;
	}

	.attach-fab-badge {
		flex-shrink: 0;
		width: 72rpx;
		height: 72rpx;
		line-height: 72rpx;
		border-radius: 50%;
		text-align: center;
		font-size: 26rpx;
		font-weight: 700;
		color: #fff;
		background: #4f93a3;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.attach-fab-label {
		height: 64rpx;
		line-height: 64rpx;
		padding: 0 22rpx;
		border-radius: 999rpx;
		font-size: 24rpx;
		font-weight: 600;
		color: #25546d;
		background: rgba(255, 255, 255, 0.9);
		border: 1rpx solid rgba(255, 255, 255, 0.7);
		box-shadow: 0 12rpx 28rpx rgba(67, 112, 142, 0.14);
		backdrop-filter: blur(20rpx) saturate(130%);
		-webkit-backdrop-filter: blur(20rpx) saturate(130%);
	}

	.composer-image-strip {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		padding: 0 18rpx 12rpx;
	}

	.composer-image-card {
		position: relative;
		width: 138rpx;
		height: 138rpx;
		border-radius: 22rpx;
		overflow: hidden;
		background: rgba(255, 255, 255, 0.18);
		border: 1rpx solid rgba(255, 255, 255, 0.16);
		box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.12);
	}

	.composer-image {
		width: 100%;
		height: 100%;
	}

	.composer-image-mask {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 12rpx;
		background: rgba(8, 12, 22, 0.46);
	}

	.composer-image-mask-text {
		color: #fff;
		font-size: 22rpx;
		line-height: 1.35;
		text-align: center;
	}

	.composer-image-remove {
		position: absolute;
		top: 8rpx;
		right: 8rpx;
		width: 38rpx;
		height: 38rpx;
		line-height: 38rpx;
		border-radius: 50%;
		text-align: center;
		font-size: 28rpx;
		color: #fff;
		background: rgba(8, 12, 22, 0.58);
	}

	.attach-btn {
		flex-shrink: 0;
		width: 72rpx;
		height: 72rpx;
		line-height: 72rpx;
		border-radius: 50%;
		text-align: center;
		font-size: 42rpx;
		font-weight: 500;
		color: #f8fafc;
		background: linear-gradient(135deg, rgba(124, 58, 237, 0.72) 0%, rgba(236, 72, 153, 0.66) 100%);
		box-shadow: 0 10rpx 24rpx rgba(124, 58, 237, 0.18);
		transition: transform 0.18s ease, box-shadow 0.18s ease;
	}

	.attach-btn--active {
		transform: rotate(45deg) scale(0.96);
		box-shadow: 0 14rpx 30rpx rgba(52, 143, 184, 0.22);
	}

	.reply-help-trigger {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 22rpx;
		border-radius: 36rpx;
		font-size: 24rpx;
		font-weight: 600;
		color: #f8fafc;
		background: linear-gradient(135deg, rgba(124, 58, 237, 0.72) 0%, rgba(236, 72, 153, 0.66) 100%);
		box-shadow: 0 10rpx 24rpx rgba(124, 58, 237, 0.22);
		white-space: nowrap;
		transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
	}

	.reply-help-trigger--active {
		transform: translateY(-2rpx) scale(0.98);
		box-shadow: 0 14rpx 30rpx rgba(52, 143, 184, 0.24);
	}

	.reply-help-trigger-text {
		line-height: 1;
	}

	.reply-help-trigger--disabled {
		opacity: 0.4;
		pointer-events: none;
	}

	.inp {
		flex: 1;
		min-height: 72rpx;
		max-height: 176rpx;
		padding: 16rpx 24rpx;
		box-sizing: border-box;
		background: rgba(9, 13, 24, 0.18);
		backdrop-filter: blur(16rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.1);
		border-radius: 28rpx;
		font-size: 27rpx;
		line-height: 40rpx;
		color: $text;
		overflow-y: auto;
	}

	.send {
		padding: 0 28rpx;
		height: 72rpx;
		line-height: 72rpx;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
		color: #fff;
		font-size: 28rpx;
		border-radius: 36rpx;
		min-width: 116rpx;
		text-align: center;
		font-weight: 600;
	}

	.send.senddisabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.send.send--bottom {
		background: linear-gradient(135deg, #f18ab2 0%, #f6a3c8 58%, #ffc1d9 100%) !important;
		box-shadow: 0 12rpx 24rpx rgba(241, 138, 178, 0.22) !important;
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

	.typing-row {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 24rpx;
		padding: 8rpx 24rpx 0;
	}

	.typing-hint {
		font-size: 24rpx;
		color: #94a3b8;
	}

	.stream-inline {
		display: inline-flex;
		align-items: center;
		gap: 12rpx;
		margin-top: 14rpx;
		padding: 10rpx 16rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.08);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
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
		background: rgba(158, 238, 244, 0.92);
		transform-origin: center bottom;
		animation: voice-status-wave 1s ease-in-out infinite;
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
		letter-spacing: 0.2rpx;
		color: rgba(234, 246, 255, 0.88);
	}

	.stop-stream {
		font-size: 24rpx;
		color: #f87171;
		padding: 6rpx 20rpx;
		border-radius: 999rpx;
		border: 1rpx solid rgba(248, 113, 113, 0.45);
	}

	.swipe-row {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 16rpx;
		margin-top: 14rpx;
		padding-top: 14rpx;
		border-top: 1rpx solid rgba(255, 255, 255, 0.09);
	}

	.swipe-btn {
		font-size: 36rpx;
		color: #c4b5fd;
		padding: 0 12rpx;
		line-height: 1;
	}

	.swipe-num {
		font-size: 22rpx;
		color: #94a3b8;
	}

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
		background: linear-gradient(135deg, rgba(14, 28, 43, 0.22) 0%, rgba(21, 44, 62, 0.14) 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.14);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.1),
			0 10rpx 24rpx rgba(8, 18, 30, 0.1);
	}

	.assistant-voice-pill-dot {
		flex-shrink: 0;
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		background: #7fd6dd;
		box-shadow: 0 0 0 8rpx rgba(127, 214, 221, 0.14);
	}

	.bubble .assistant-voice-pill .assistant-voice-pill-text {
		font-size: 22rpx;
		font-weight: 650;
		letter-spacing: 0.18rpx;
		color: #f8fbff !important;
	}

	.assistant-voice-pill--loading .assistant-voice-pill-dot {
		background: #f7acc5;
		box-shadow: 0 0 0 8rpx rgba(247, 172, 197, 0.12);
		animation: assistant-voice-pulse 1.1s ease-in-out infinite;
	}

	.assistant-voice-pill--playing {
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.34) 0%, rgba(118, 210, 221, 0.28) 100%);
		border-color: rgba(156, 224, 238, 0.22);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.14),
			0 12rpx 28rpx rgba(52, 143, 184, 0.16);
	}

	.assistant-voice-pill--playing .assistant-voice-pill-dot {
		background: #ecfbff;
		box-shadow: 0 0 0 8rpx rgba(236, 251, 255, 0.16);
	}

	.assistant-voice-pill--error {
		background: linear-gradient(135deg, rgba(113, 34, 46, 0.22) 0%, rgba(83, 20, 34, 0.16) 100%);
		border-color: rgba(255, 173, 186, 0.24);
	}

	.assistant-voice-pill--error .assistant-voice-pill-dot {
		background: #ffb6c5;
		box-shadow: 0 0 0 8rpx rgba(255, 182, 197, 0.14);
	}

	/* Light clover tavern chat refresh. Keeps segmented message rendering and chat background logic intact. */
	.wrap {
		background-color: transparent;
		background-image: url('/static/login.png');
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
	}

	.wrap--with-bg::after {
		background: rgba(255, 255, 255, 0.02);
	}

	.tool-bar,
	.tool-hint,
	.memory-bar,
	.reply-help-panel,
	.input-bar {
		background: rgba(255, 255, 255, 0.3) !important;
		border-color: rgba(255, 255, 255, 0.28) !important;
		box-shadow: 0 12rpx 28rpx rgba(67, 112, 142, 0.08);
		backdrop-filter: blur(22rpx) saturate(135%);
		-webkit-backdrop-filter: blur(22rpx) saturate(135%);
	}

	.tool-i,
	.reply-help-head-btn,
	.attach-btn,
	.reply-help-trigger {
		color: #1c5975;
		background: rgba(220, 247, 251, 0.72);
	}

	.attach-btn,
	.reply-help-trigger,
	.send,
	.edit-btn--primary,
	.commercial-btn--primary {
		color: #fff;
		background: #4f93a3;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.send.send--bottom {
		background: #6d8a92 !important;
		box-shadow: 0 12rpx 24rpx rgba(70, 86, 92, 0.18) !important;
	}

	.bubble,
	.msg-row.them .bubble {
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.28) 0%, rgba(244, 249, 255, 0.18) 100%) !important;
		border-color: rgba(255, 255, 255, 0.24) !important;
		box-shadow: 0 14rpx 30rpx rgba(67, 112, 142, 0.08);
		backdrop-filter: blur(26rpx) saturate(140%);
		-webkit-backdrop-filter: blur(26rpx) saturate(140%);
	}

	.msg-row.me .bubble {
		background: rgba(238, 249, 249, 0.4) !important;
		border-color: rgba(255, 255, 255, 0.28) !important;
	}

	.bubble .assistant-voice-pill {
		background: linear-gradient(135deg, rgba(255, 255, 255, 0.76) 0%, rgba(234, 247, 252, 0.68) 100%);
		border-color: rgba(164, 220, 235, 0.58);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.92),
			0 10rpx 24rpx rgba(67, 112, 142, 0.08);
	}

	.bubble .assistant-voice-pill .assistant-voice-pill-text {
		color: #245c78 !important;
	}

	.bubble .assistant-voice-pill-dot {
		background: #68bdd1;
		box-shadow: 0 0 0 8rpx rgba(104, 189, 209, 0.12);
	}

	.bubble .assistant-voice-pill--playing {
		background: #4f93a3;
		border-color: rgba(255, 255, 255, 0.56);
	}

	.bubble .assistant-voice-pill--playing .assistant-voice-pill-text {
		color: #ffffff !important;
	}

	.bubble .assistant-voice-pill--playing .assistant-voice-pill-dot {
		background: rgba(255, 255, 255, 0.96);
		box-shadow: 0 0 0 8rpx rgba(255, 255, 255, 0.18);
	}

	.bubble .assistant-voice-pill--error {
		background: linear-gradient(135deg, rgba(255, 239, 243, 0.94) 0%, rgba(255, 227, 234, 0.88) 100%);
		border-color: rgba(244, 166, 196, 0.46);
	}

	.bubble .assistant-voice-pill--error .assistant-voice-pill-text {
		color: #b24b6f !important;
	}

	.txt,
	.md-inner,
	.md-inner >>> .st-chat-render,
	.md-inner >>> p,
	.md-inner >>> li,
	.md-inner >>> span,
	.md-inner >>> strong,
	.md-inner >>> em,
	.md-inner >>> .st-chat-seg--thought,
	.md-inner >>> .st-chat-seg--action,
	.md-inner >>> .st-chat-seg--narration,
	.reply-help-text,
	.edit-title,
	.commercial-title {
		color: #17344b !important;
		text-shadow: none !important;
	}

	.bubble .txt,
	.bubble .md-inner,
	.bubble .md-inner *,
	.bubble text {
		color: #17344b !important;
		text-shadow: none !important;
	}

	.md-inner >>> .st-chat-seg--speech {
		color: #c05781 !important;
		text-shadow: none !important;
	}

	.md-inner >>> .st-chat-seg--thought {
		color: #4e6678 !important;
		border-left-color: rgba(52, 143, 184, 0.34);
	}

	.md-inner >>> .st-chat-seg--action {
		color: #3d5c70 !important;
	}

	.md-inner >>> .st-chat-seg--narration {
		color: #17344b !important;
	}

	.md-inner >>> code,
	.md-inner >>> pre,
	.edit-ta,
	.inp {
		color: #17344b !important;
		background: rgba(255, 255, 255, 0.38);
		border-color: rgba(88, 189, 210, 0.22);
	}

	.chat-fill-txt,
	.tool-hint-txt,
	.memory-bar-txt,
	.user-edit-tag,
	.edit-sub,
	.commercial-sub,
	.reply-help-state,
	.typing-hint,
	.swipe-num {
		color: #4e6678;
	}

	.chat-fill-retry {
		background: #4f93a3;
	}

	.chat-fill-back,
	.nav-link,
	.swipe-btn,
	.md-inner >>> a {
		color: #1f6686 !important;
	}

	.edit-mask,
	.commercial-mask,
	.message-action-mask {
		background: rgba(67, 112, 142, 0.08);
	}

	.char-image-mask {
		background: rgba(22, 48, 64, 0.78);
	}

	.commercial-card,
	.edit-panel,
	.reply-help-card {
		background: rgba(255, 255, 255, 0.42);
		border-color: rgba(255, 255, 255, 0.32);
		box-shadow: 0 22rpx 54rpx rgba(67, 112, 142, 0.12);
		backdrop-filter: blur(26rpx) saturate(140%);
		-webkit-backdrop-filter: blur(26rpx) saturate(140%);
	}

	.message-action-menu {
		background: rgba(255, 255, 255, 0.84);
		border-color: rgba(255, 255, 255, 0.42);
		box-shadow: 0 18rpx 46rpx rgba(67, 112, 142, 0.18);
		backdrop-filter: blur(26rpx) saturate(140%);
		-webkit-backdrop-filter: blur(26rpx) saturate(140%);
	}

	.commercial-btn--ghost,
	.commercial-btn--muted {
		color: #247494;
		background: rgba(220, 247, 251, 0.88);
	}

	.message-action-item + .message-action-item {
		border-top-color: rgba(255, 255, 255, 0.52);
	}

	.message-action-item-label {
		color: #1f6686;
	}

	.message-action-item--danger .message-action-item-label {
		color: #d6617d;
	}

	.reply-help-index {
		color: #fff;
		background: #4f93a3;
	}

	.inp {
		background: rgba(255, 255, 255, 0.32);
	}

	/* Transparent dark glass keeps chat readable on bright character backgrounds. */
	.wrap--with-bg::after {
		background: rgba(255, 255, 255, 0.02) !important;
	}

	.tool-bar,
	.memory-bar,
	.reply-help-panel,
	.ai-disclaimer,
	.input-bar {
		flex-shrink: 0;
	}

	.bubble,
	.msg-row.them .bubble {
		background: linear-gradient(180deg, rgba(7, 12, 20, 0.46) 0%, rgba(9, 15, 24, 0.34) 100%) !important;
		border-color: rgba(255, 255, 255, 0.14) !important;
		border-radius: 24rpx 24rpx 24rpx 10rpx;
		box-shadow: 0 14rpx 30rpx rgba(4, 12, 22, 0.16) !important;
		backdrop-filter: blur(16rpx) saturate(112%);
		-webkit-backdrop-filter: blur(16rpx) saturate(112%);
	}

	.msg-row.me .bubble {
		background: linear-gradient(135deg, rgba(10, 45, 56, 0.44) 0%, rgba(8, 18, 29, 0.32) 100%) !important;
		border-color: rgba(130, 219, 232, 0.18) !important;
		border-radius: 24rpx 24rpx 10rpx 24rpx;
	}

	.bubble .assistant-voice-pill {
		background: linear-gradient(135deg, rgba(19, 34, 48, 0.62) 0%, rgba(15, 28, 40, 0.46) 100%);
		border-color: rgba(255, 255, 255, 0.14);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08),
			0 10rpx 26rpx rgba(4, 12, 22, 0.16);
	}

	.bubble .assistant-voice-pill .assistant-voice-pill-text {
		color: rgba(242, 250, 255, 0.96) !important;
	}

	.bubble .assistant-voice-pill-dot {
		background: #8de8f0;
		box-shadow: 0 0 0 8rpx rgba(141, 232, 240, 0.12);
	}

	.bubble .assistant-voice-pill--playing {
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.44) 0%, rgba(118, 210, 221, 0.32) 100%);
		border-color: rgba(156, 224, 238, 0.26);
	}

	.bubble .assistant-voice-pill--error {
		background: linear-gradient(135deg, rgba(94, 26, 40, 0.46) 0%, rgba(74, 19, 31, 0.34) 100%);
		border-color: rgba(255, 182, 197, 0.18);
	}

	.bubble .txt,
	.bubble .md-inner,
	.bubble .md-inner *,
	.bubble text,
	.bubble .reply-help-text {
		color: rgba(250, 253, 255, 0.98) !important;
		text-shadow: none !important;
	}

	.md-inner >>> p,
	.md-inner >>> li,
	.md-inner >>> span,
	.md-inner >>> strong,
	.md-inner >>> em {
		color: rgba(250, 253, 255, 0.98) !important;
	}

	.md-inner >>> .st-chat-seg--speech,
	.md-inner >>> .st-chat-seg--speech p {
		color: #eefcff !important;
		font-weight: 650;
	}

	.md-inner >>> .st-chat-seg--thought,
	.md-inner >>> .st-chat-seg--thought p {
		color: rgba(224, 237, 247, 0.9) !important;
		border-left-color: rgba(156, 224, 238, 0.3) !important;
	}

	.md-inner >>> .st-chat-seg--action,
	.md-inner >>> .st-chat-seg--action p {
		color: rgba(236, 244, 250, 0.94) !important;
	}

	.md-inner >>> .st-chat-seg--narration,
	.md-inner >>> .st-chat-seg--narration p {
		color: rgba(250, 253, 255, 0.98) !important;
	}

	.md-inner >>> code {
		color: #f8fbff !important;
		background: rgba(255, 255, 255, 0.12) !important;
	}

	.md-inner >>> pre {
		color: #f8fbff !important;
		background: rgba(3, 8, 14, 0.32) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.12);
	}

	.md-inner >>> a,
	.swipe-btn {
		color: #98e8ff !important;
	}

	.user-edit-tag,
	.swipe-num {
		color: rgba(222, 235, 244, 0.78) !important;
	}

	.swipe-row {
		border-top-color: rgba(255, 255, 255, 0.14) !important;
	}

	/* Keep AI-helper consistent with the chat bubbles without making a heavy dark block. */
	.reply-help-panel {
		background: linear-gradient(180deg, rgba(8, 13, 22, 0.42) 0%, rgba(10, 16, 26, 0.32) 100%) !important;
		border-color: rgba(255, 255, 255, 0.14) !important;
		box-shadow: 0 14rpx 34rpx rgba(4, 12, 22, 0.16) !important;
	}

	.reply-help-title {
		color: rgba(250, 253, 255, 0.98) !important;
		letter-spacing: 0.2rpx;
	}

	.reply-help-head-btn {
		color: #dff9ff !important;
		background: rgba(130, 219, 232, 0.18) !important;
		border: 1rpx solid rgba(156, 224, 238, 0.24);
	}

	.reply-help-state {
		color: rgba(224, 237, 247, 0.86) !important;
	}

	.reply-help-state--error {
		color: #ffd3dc !important;
	}

	.reply-help-card {
		background: linear-gradient(180deg, rgba(5, 10, 18, 0.42) 0%, rgba(8, 13, 22, 0.34) 100%) !important;
		border-color: rgba(255, 255, 255, 0.12) !important;
	}

	.reply-help-text {
		color: rgba(250, 253, 255, 0.98) !important;
		font-weight: 500;
	}

	.reply-help-index {
		color: #fff !important;
		background: linear-gradient(135deg, #3b9ab8 0%, #7fd6dd 100%) !important;
		box-shadow: 0 6rpx 16rpx rgba(59, 154, 184, 0.22);
	}

	.bubble,
	.msg-row.them .bubble {
		background: linear-gradient(180deg, rgba(7, 12, 20, 0.46) 0%, rgba(9, 15, 24, 0.34) 100%) !important;
		border-color: rgba(255, 255, 255, 0.14) !important;
	}

	.msg-row.me .bubble {
		background: linear-gradient(135deg, rgba(10, 45, 56, 0.44) 0%, rgba(8, 18, 29, 0.32) 100%) !important;
		border-color: rgba(130, 219, 232, 0.18) !important;
	}

	.bubble .txt,
	.bubble .md-inner,
	.bubble .md-inner *,
	.bubble text {
		color: rgba(250, 253, 255, 0.98) !important;
	}

	.md-inner >>> .st-chat-seg--speech,
	.md-inner >>> .st-chat-seg--speech p {
		color: #eefcff !important;
		font-weight: 650;
	}

	.md-inner >>> .st-chat-seg--thought,
	.md-inner >>> .st-chat-seg--thought p {
		color: rgba(224, 237, 247, 0.9) !important;
	}

	.md-inner >>> .st-chat-seg--action,
	.md-inner >>> .st-chat-seg--action p {
		color: rgba(236, 244, 250, 0.94) !important;
	}

	.md-inner >>> .st-chat-seg--narration,
	.md-inner >>> .st-chat-seg--narration p {
		color: rgba(250, 253, 255, 0.98) !important;
	}

	.nav-link {
		color: #2f7ea3 !important;
		background: rgba(231, 247, 255, 0.72);
		padding: 8rpx 18rpx;
		border-radius: 999rpx;
		border: 1rpx solid rgba(189, 227, 245, 0.82);
	}

	.nav-voice-toggle {
		background: rgba(231, 247, 255, 0.72);
		border-color: rgba(189, 227, 245, 0.82);
		box-shadow: 0 8rpx 18rpx rgba(66, 103, 132, 0.06);
	}

	.nav-voice-toggle--off {
		background: rgba(255, 255, 255, 0.62);
		border-color: rgba(214, 229, 238, 0.88);
	}

	/* Composer polish: clean floating controls, no heavy gray strip. */
	.input-bar {
		align-items: flex-end !important;
		gap: 12rpx;
		padding: 12rpx 18rpx calc(14rpx + env(safe-area-inset-bottom)) !important;
		background:
			linear-gradient(180deg, rgba(8, 14, 24, 0) 0%, rgba(8, 14, 24, 0.12) 100%) !important;
		border-top: 0 !important;
		box-shadow: none !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.inp {
		width: 0;
		min-height: 76rpx;
		max-height: 184rpx;
		padding: 18rpx 26rpx !important;
		box-sizing: border-box;
		line-height: 40rpx;
		border-radius: 30rpx;
		white-space: pre-wrap;
		word-break: break-word;
		resize: none;
		overflow-y: auto;
		color: #223245 !important;
		background: rgba(255, 255, 255, 0.9) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.78) !important;
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.92),
			0 10rpx 24rpx rgba(9, 18, 30, 0.16);
	}

	.attach-btn,
	.reply-help-trigger,
	.send {
		align-self: flex-end;
		flex-shrink: 0;
		margin-left: 0;
		margin-right: 0;
	}

	.attach-btn {
		width: 76rpx;
		height: 76rpx;
		line-height: 76rpx;
		border-radius: 50%;
		font-size: 44rpx;
		color: #fff !important;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.reply-help-trigger {
		height: 76rpx;
		line-height: 76rpx;
		padding: 0 24rpx;
		border-radius: 30rpx;
		color: #fff !important;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.send {
		height: 76rpx;
		line-height: 76rpx;
		padding: 0 30rpx;
		border-radius: 30rpx;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.send.send--bottom {
		background: #6d8a92 !important;
		box-shadow: 0 12rpx 24rpx rgba(70, 86, 92, 0.18) !important;
	}

	.bubble .st-chat-seg-native--speech .st-chat-seg-text {
		color: #c05781 !important;
		font-weight: 700 !important;
	}

	.bubble .st-chat-seg-native--thought .st-chat-seg-text {
		color: #4e6678 !important;
		font-weight: 400 !important;
	}

	.bubble .st-chat-seg-native--action .st-chat-seg-text {
		color: #3d5c70 !important;
		font-weight: 400 !important;
	}

	.bubble .st-chat-seg-native--narration .st-chat-seg-text {
		color: #17344b !important;
		font-weight: 400 !important;
	}

	/* #ifdef APP-PLUS */
	.wrap--app-plus {
		background-color: transparent;
		background-image: url('/static/login.png');
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
	}

	.wrap--app-plus.wrap--with-bg::after {
		background: rgba(255, 255, 255, 0.02) !important;
	}

	.wrap--app-plus .tool-bar,
	.wrap--app-plus .memory-bar,
	.wrap--app-plus .reply-help-panel,
	.wrap--app-plus .ai-disclaimer,
	.wrap--app-plus .input-bar {
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus .tool-bar,
	.wrap--app-plus .memory-bar {
		background: rgba(255, 255, 255, 0.36) !important;
		border-color: rgba(255, 255, 255, 0.34) !important;
		box-shadow: 0 10rpx 24rpx rgba(66, 103, 132, 0.08) !important;
	}

	.wrap--app-plus .tool-i {
		color: #1b5570 !important;
		background: rgba(255, 255, 255, 0.78) !important;
		border: 1rpx solid rgba(177, 222, 238, 0.82);
	}

	.wrap--app-plus .memory-bar-txt,
	.wrap--app-plus .chat-fill-txt,
	.wrap--app-plus .typing-hint,
	.wrap--app-plus .user-edit-tag,
	.wrap--app-plus .swipe-num {
		color: #4d6374 !important;
	}

	.wrap--app-plus .nav-link {
		height: 64rpx;
		line-height: 64rpx;
		padding: 0 20rpx;
		border-radius: 22rpx;
		font-size: 24rpx;
		font-weight: 600;
		color: #226b8b !important;
		background: rgba(255, 255, 255, 0.74);
		border: 1rpx solid rgba(188, 223, 239, 0.92);
		box-shadow: 0 8rpx 18rpx rgba(66, 103, 132, 0.06);
	}

	.wrap--app-plus .nav-right-tools {
		flex-shrink: 0;
		gap: 10rpx;
		align-items: center;
	}

	.wrap--app-plus .nav-voice-toggle {
		width: 64rpx;
		height: 64rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.74);
		border-color: rgba(188, 223, 239, 0.92);
		box-shadow: 0 8rpx 18rpx rgba(66, 103, 132, 0.06);
	}

	.wrap--app-plus .nav-voice-config {
		width: 64rpx;
		height: 64rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.74);
		border-color: rgba(188, 223, 239, 0.92);
		box-shadow: 0 8rpx 18rpx rgba(66, 103, 132, 0.06);
	}

	.wrap--app-plus .character-voice-mask,
	.wrap--app-plus .image-quick-mask {
		padding: 18rpx 16rpx calc(env(safe-area-inset-bottom) + 12rpx) !important;
	}

	.wrap--app-plus .character-voice-sheet {
		border-radius: 32rpx;
	}

	.wrap--app-plus .image-quick-card {
		max-height: calc(100vh - 148rpx - env(safe-area-inset-bottom)) !important;
	}

	.wrap--app-plus .chat-scroll {
		padding: 22rpx 18rpx 28rpx;
	}

	.wrap--app-plus .msg-row {
		align-items: flex-end;
		gap: 12rpx;
		margin-bottom: 34rpx;
	}

	.wrap--app-plus .av {
		width: 68rpx;
		height: 68rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.76);
		box-shadow: 0 8rpx 18rpx rgba(7, 18, 28, 0.12);
		background: rgba(255, 255, 255, 0.28);
	}

	.wrap--app-plus .bubble,
	.wrap--app-plus .msg-row.them .bubble {
		position: relative;
		max-width: calc(100% - 108rpx);
		margin: 0 10rpx;
		padding: 26rpx 28rpx 24rpx;
		border-radius: 34rpx 34rpx 34rpx 18rpx;
		background:
			linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 248, 252, 0.94) 100%) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.22) !important;
		box-shadow:
			0 18rpx 36rpx rgba(27, 70, 96, 0.16),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.9),
			inset 0 -1rpx 0 rgba(132, 178, 206, 0.12) !important;
	}

	.wrap--app-plus .msg-row.me .bubble {
		background:
			linear-gradient(145deg, #2389aa 0%, #45b6cc 62%, #6dd1da 100%) !important;
		border-color: rgba(231, 253, 255, 0.48) !important;
		border-radius: 34rpx 34rpx 18rpx 34rpx;
		box-shadow:
			0 18rpx 36rpx rgba(21, 121, 151, 0.28),
			inset 0 1rpx 0 rgba(238, 252, 255, 0.34),
			inset 0 -1rpx 0 rgba(17, 90, 112, 0.14) !important;
	}

	.wrap--app-plus .bubble--streaming {
		border-color: rgba(158, 238, 244, 0.22) !important;
		box-shadow:
			0 24rpx 44rpx rgba(4, 12, 22, 0.2),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.16),
			0 0 0 1rpx rgba(158, 238, 244, 0.08) !important;
	}

	.wrap--app-plus .assistant-voice-row {
		margin-top: 18rpx;
	}

	.wrap--app-plus .assistant-voice-pill {
		min-height: 56rpx;
		padding: 0 20rpx;
		border-radius: 999rpx;
		background: linear-gradient(135deg, rgba(11, 26, 40, 0.42) 0%, rgba(16, 44, 58, 0.24) 100%);
		border-color: rgba(255, 255, 255, 0.18);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.12),
			0 12rpx 28rpx rgba(4, 12, 22, 0.14);
	}

	.wrap--app-plus .assistant-voice-pill .assistant-voice-pill-text {
		font-size: 23rpx;
		color: rgba(244, 251, 255, 0.98) !important;
	}

	.wrap--app-plus .assistant-voice-pill-dot {
		width: 15rpx;
		height: 15rpx;
		background: #9eeef4;
		box-shadow: 0 0 0 8rpx rgba(158, 238, 244, 0.12);
	}

	.wrap--app-plus .assistant-voice-pill--playing {
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.5) 0%, rgba(118, 210, 221, 0.38) 100%);
		border-color: rgba(193, 242, 248, 0.3);
	}

	.wrap--app-plus .bubble::before {
		left: 16rpx;
		right: 16rpx;
		top: 4rpx;
		height: 20rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.46) 0%, rgba(255, 255, 255, 0) 100%);
		opacity: 0.76;
	}

	.wrap--app-plus .msg-row.them .bubble::after,
	.wrap--app-plus .msg-row.me .bubble::after {
		content: '';
		position: absolute;
		bottom: 18rpx;
		width: 18rpx;
		height: 18rpx;
		transform: rotate(45deg);
	}

	.wrap--app-plus .msg-row.them .bubble::after {
		left: -9rpx;
		background: rgba(240, 249, 253, 0.96);
		border-left: 1rpx solid rgba(86, 121, 145, 0.2);
		border-bottom: 1rpx solid rgba(86, 121, 145, 0.2);
		box-shadow: -6rpx 8rpx 14rpx rgba(27, 70, 96, 0.08);
	}

	.wrap--app-plus .msg-row.me .bubble::after {
		right: -9rpx;
		background: #45b6cc;
		border-right: 1rpx solid rgba(231, 253, 255, 0.42);
		border-top: 1rpx solid rgba(231, 253, 255, 0.42);
		box-shadow: 6rpx 8rpx 14rpx rgba(21, 121, 151, 0.12);
	}

.wrap--app-plus .bubble--has-image {
	padding-top: 16rpx;
}

.wrap--app-plus .local-image-prompt-row {
	margin-top: 12rpx;
}

.wrap--app-plus .local-image-prompt-text {
	font-size: 25rpx;
}

.wrap--app-plus .bubble--text-only {
	padding-bottom: 22rpx;
}

	.wrap--app-plus .bubble--image-only {
		padding: 10rpx;
		background:
			linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 248, 252, 0.92) 100%) !important;
	}

	.wrap--app-plus .msg-row.me .bubble--image-only {
		background:
			linear-gradient(145deg, #2389aa 0%, #45b6cc 100%) !important;
	}

	.wrap--app-plus .bubble--image-only::before {
		left: 10rpx;
		right: 10rpx;
		top: 3rpx;
		opacity: 0.52;
	}

	.wrap--app-plus .msg-image-list {
		gap: 14rpx;
		margin-bottom: 10rpx;
	}

	.wrap--app-plus .bubble--image-only .msg-image-list {
		margin-bottom: 0;
	}

	.wrap--app-plus .msg-image {
		border-radius: 24rpx;
		border-color: rgba(255, 255, 255, 0.18);
		box-shadow:
			0 12rpx 24rpx rgba(4, 12, 22, 0.16),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.14);
	}

	.wrap--app-plus .bubble--image-only .msg-image {
		width: 216rpx;
		height: 216rpx;
	}

	.wrap--app-plus .txt,
	.wrap--app-plus .bubble .txt,
	.wrap--app-plus .bubble .md-inner,
	.wrap--app-plus .bubble .md-inner *,
	.wrap--app-plus .bubble text {
		color: #123247 !important;
		font-size: 30rpx;
		line-height: 1.86;
		letter-spacing: 0.2rpx;
		text-shadow: none !important;
	}

	.wrap--app-plus .msg-row.me .bubble .txt,
	.wrap--app-plus .msg-row.me .bubble .md-inner,
	.wrap--app-plus .msg-row.me .bubble .md-inner *,
	.wrap--app-plus .msg-row.me .bubble text {
		color: #ffffff !important;
	}

	.wrap--app-plus .msg-row.them .bubble .txt,
	.wrap--app-plus .msg-row.them .bubble .md-inner,
	.wrap--app-plus .msg-row.them .bubble .md-inner *,
	.wrap--app-plus .msg-row.them .bubble text {
		color: #123247 !important;
	}

	.wrap--app-plus .md-inner--native {
		gap: 14rpx;
	}

	.wrap--app-plus .st-chat-seg-native {
		display: block;
		padding: 2rpx 0;
	}

	.wrap--app-plus .st-chat-seg-text {
		font-size: 30rpx;
		line-height: 1.86;
	}

	.wrap--app-plus .st-chat-seg-native--speech .st-chat-seg-text {
		color: #b94873 !important;
		font-weight: 700 !important;
	}

	.wrap--app-plus .st-chat-seg-native--thought {
		padding-left: 16rpx;
		border-left: 4rpx solid rgba(185, 96, 133, 0.26);
	}

	.wrap--app-plus .st-chat-seg-native--thought .st-chat-seg-text {
		color: #7a5871 !important;
		font-style: italic;
		font-size: 28rpx;
	}

	.wrap--app-plus .st-chat-seg-native--action {
		padding-left: 16rpx;
		border-left: 4rpx solid rgba(50, 137, 166, 0.28);
	}

	.wrap--app-plus .st-chat-seg-native--action .st-chat-seg-text {
		color: #2e6f89 !important;
		font-style: italic;
		font-size: 28rpx;
	}

	.wrap--app-plus .st-chat-seg-native--narration .st-chat-seg-text {
		color: #123247 !important;
		font-weight: 500 !important;
	}

	.wrap--app-plus .swipe-row {
		margin-top: 18rpx;
		padding-top: 16rpx;
		border-top-color: rgba(255, 255, 255, 0.12) !important;
	}

	.wrap--app-plus .swipe-btn {
		color: #9ae9ff !important;
	}

	.wrap--app-plus .reply-help-panel {
		margin: 0 16rpx 10rpx;
		padding: 18rpx 18rpx 12rpx;
		background: rgba(8, 14, 24, 0.28) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.14) !important;
		box-shadow: 0 12rpx 26rpx rgba(4, 12, 22, 0.14) !important;
	}

	.wrap--app-plus .reply-help-title {
		color: rgba(247, 251, 255, 0.98) !important;
	}

	.wrap--app-plus .typing-row {
		gap: 18rpx;
		padding: 10rpx 24rpx 0;
	}

	.wrap--app-plus .typing-hint {
		padding: 10rpx 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.18);
		color: rgba(232, 246, 255, 0.9);
	}

	.wrap--app-plus .stop-stream {
		background: rgba(93, 25, 40, 0.12);
		border-color: rgba(248, 113, 113, 0.3);
		color: #ffd4db;
	}

	.wrap--app-plus .stream-inline {
		margin-top: 16rpx;
		padding: 10rpx 18rpx;
		background: rgba(255, 255, 255, 0.08);
		border-color: rgba(255, 255, 255, 0.12);
	}

	.wrap--app-plus .stream-inline-text {
		color: rgba(234, 246, 255, 0.9);
	}

	.wrap--app-plus .reply-help-head-btn {
		color: #e4f8ff !important;
		background: rgba(125, 205, 223, 0.18) !important;
		border: 1rpx solid rgba(158, 226, 238, 0.2);
	}

	.wrap--app-plus .reply-help-state {
		color: rgba(224, 237, 247, 0.9) !important;
	}

	.wrap--app-plus .reply-help-state--error {
		color: #ffd5df !important;
	}

	.wrap--app-plus .reply-help-card {
		background: rgba(255, 255, 255, 0.08) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.1) !important;
	}

	.wrap--app-plus .reply-help-text {
		color: rgba(247, 251, 255, 0.98) !important;
		font-size: 26rpx;
		line-height: 1.68;
	}

	.wrap--app-plus .reply-help-index {
		color: #fff !important;
		background: linear-gradient(135deg, #358dac 0%, #79d0db 100%) !important;
		box-shadow: 0 6rpx 16rpx rgba(53, 141, 172, 0.22);
	}

	.wrap--app-plus .ai-disclaimer-txt {
		background: rgba(10, 16, 26, 0.24) !important;
		border-color: rgba(255, 255, 255, 0.14) !important;
		color: rgba(247, 251, 255, 0.84) !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus .input-bar {
		align-items: center !important;
		gap: 10rpx;
		padding: 10rpx 14rpx calc(12rpx + env(safe-area-inset-bottom)) !important;
		background:
			linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(10, 16, 26, 0.08) 100%) !important;
		border-top: 0 !important;
		box-shadow: none !important;
	}

	.wrap--app-plus .input-pill {
		min-height: 82rpx;
		align-items: stretch;
		gap: 10rpx;
		padding: 8rpx 10rpx 8rpx 22rpx;
		border-radius: 28rpx;
	}

	.wrap--app-plus .input-pill--with-quote {
		padding-top: 12rpx;
		padding-bottom: 12rpx;
	}

	.wrap--app-plus .input-actions {
		gap: 4rpx;
		padding-bottom: 0;
	}

	.wrap--app-plus .attach-btn,
	.wrap--app-plus .reply-help-trigger,
	.wrap--app-plus .send {
		height: 72rpx;
		line-height: 72rpx;
		align-self: center;
		border-radius: 28rpx;
		color: #fff !important;
		background: linear-gradient(135deg, #2f86a8 0%, #70cedb 64%, #e5a9c3 100%) !important;
		box-shadow: 0 10rpx 22rpx rgba(47, 134, 168, 0.2);
	}

	.wrap--app-plus .attach-btn {
		width: 72rpx;
		border-radius: 50%;
		font-size: 44rpx;
	}

	.wrap--app-plus .reply-help-trigger {
		padding: 0 24rpx;
	}

	.wrap--app-plus .send {
		width: 72rpx;
		padding: 0;
		justify-content: center;
	}

	.wrap--app-plus .send.send--bottom {
		background: linear-gradient(135deg, #ef7ea8 0%, #f59bbf 62%, #ffc3d7 100%) !important;
		box-shadow: 0 10rpx 22rpx rgba(239, 126, 168, 0.26) !important;
	}

	.wrap--app-plus .inp {
		width: 0;
		min-height: 72rpx;
		max-height: 220rpx;
		padding: 16rpx 22rpx !important;
		line-height: 40rpx;
		border-radius: 26rpx;
		background: rgba(255, 255, 255, 0.94) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.82) !important;
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.96),
			0 10rpx 24rpx rgba(9, 18, 30, 0.12);
		color: #1b2e40 !important;
		word-break: break-word;
		white-space: pre-wrap;
		resize: none;
		overflow-y: auto;
	}

	.bubble--app-plus {
		position: relative;
		max-width: calc(100% - 108rpx);
		margin: 0 10rpx;
		padding: 26rpx 28rpx 24rpx;
		border-radius: 34rpx 34rpx 34rpx 18rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 248, 252, 0.94) 100%) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.22) !important;
		box-shadow:
			0 18rpx 36rpx rgba(27, 70, 96, 0.16),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.9),
			inset 0 -1rpx 0 rgba(132, 178, 206, 0.12) !important;
	}

	.bubble--app-plus.bubble--me {
		border-radius: 34rpx 34rpx 18rpx 34rpx;
		background: linear-gradient(145deg, #2389aa 0%, #45b6cc 62%, #6dd1da 100%) !important;
		border-color: rgba(231, 253, 255, 0.48) !important;
		box-shadow:
			0 18rpx 36rpx rgba(21, 121, 151, 0.28),
			inset 0 1rpx 0 rgba(238, 252, 255, 0.34),
			inset 0 -1rpx 0 rgba(17, 90, 112, 0.14) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus::after,
	.msg-row--app-plus.me .bubble--app-plus::after {
		content: '';
		position: absolute;
		bottom: 18rpx;
		width: 18rpx;
		height: 18rpx;
		transform: rotate(45deg);
	}

	.msg-row--app-plus.them .bubble--app-plus::after {
		left: -9rpx;
		background: rgba(240, 249, 253, 0.96);
		border-left: 1rpx solid rgba(86, 121, 145, 0.2);
		border-bottom: 1rpx solid rgba(86, 121, 145, 0.2);
		box-shadow: -6rpx 8rpx 14rpx rgba(27, 70, 96, 0.08);
	}

	.msg-row--app-plus.me .bubble--app-plus::after {
		right: -9rpx;
		background: #45b6cc;
		border-right: 1rpx solid rgba(231, 253, 255, 0.42);
		border-top: 1rpx solid rgba(231, 253, 255, 0.42);
		box-shadow: 6rpx 8rpx 14rpx rgba(21, 121, 151, 0.12);
	}

	.msg-row--app-plus.them .bubble--app-plus .txt,
	.msg-row--app-plus.them .bubble--app-plus .md-inner,
	.msg-row--app-plus.them .bubble--app-plus .md-inner *,
	.msg-row--app-plus.them .bubble--app-plus text {
		color: #123247 !important;
		text-shadow: none !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .txt,
	.msg-row--app-plus.me .bubble--app-plus .md-inner,
	.msg-row--app-plus.me .bubble--app-plus .md-inner *,
	.msg-row--app-plus.me .bubble--app-plus text {
		color: #ffffff !important;
		text-shadow: none !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--speech .st-chat-seg-text {
		color: #b94873 !important;
		font-weight: 700 !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--thought .st-chat-seg-text {
		color: #7a5871 !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--action .st-chat-seg-text {
		color: #2e6f89 !important;
	}

	.msg-row--app-plus .bubble--app-plus .txt,
	.msg-row--app-plus .bubble--app-plus .md-inner,
	.msg-row--app-plus .bubble--app-plus .st-chat-seg-text {
		font-size: 30rpx !important;
		line-height: 1.82 !important;
		letter-spacing: 0 !important;
		word-break: break-word;
		white-space: pre-wrap;
	}

	.msg-row--app-plus .bubble--app-plus .msg-quote-preview {
		margin-bottom: 16rpx;
		padding: 14rpx 16rpx;
		border-radius: 18rpx;
		background: rgba(225, 242, 249, 0.76) !important;
		border-left: 6rpx solid rgba(55, 145, 176, 0.42) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .msg-quote-preview {
		background: rgba(255, 255, 255, 0.2) !important;
		border-left-color: rgba(255, 255, 255, 0.5) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .msg-quote-preview-speaker {
		color: #246178 !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .msg-quote-preview-text {
		color: #40586a !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .msg-quote-preview-speaker,
	.msg-row--app-plus.me .bubble--app-plus .msg-quote-preview-text {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.msg-row--app-plus .bubble--app-plus.bubble--has-image {
		padding-top: 18rpx;
	}

	.msg-row--app-plus .bubble--app-plus.bubble--image-only {
		padding: 10rpx;
	}

	.msg-row--app-plus .bubble--app-plus .msg-image-list {
		gap: 12rpx;
		margin-bottom: 12rpx;
	}

	.msg-row--app-plus .bubble--app-plus.bubble--image-only .msg-image-list {
		margin-bottom: 0;
	}

	.msg-row--app-plus .bubble--app-plus .msg-image {
		width: 198rpx;
		height: 198rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.62);
		border: 1rpx solid rgba(255, 255, 255, 0.72) !important;
		box-shadow:
			0 10rpx 22rpx rgba(27, 70, 96, 0.14),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.3) !important;
	}

	.msg-row--app-plus .bubble--app-plus.bubble--image-only .msg-image {
		width: 232rpx;
		height: 232rpx;
	}

	.msg-row--app-plus .bubble--app-plus .local-image-prompt-row {
		margin: 12rpx 0 2rpx;
		padding: 12rpx 14rpx;
		border-radius: 18rpx;
		background: rgba(226, 243, 249, 0.72) !important;
		border: 1rpx solid rgba(55, 145, 176, 0.18);
	}

	.msg-row--app-plus.me .bubble--app-plus .local-image-prompt-row {
		background: rgba(255, 255, 255, 0.18) !important;
		border-color: rgba(255, 255, 255, 0.2);
	}

	.msg-row--app-plus.them .bubble--app-plus .local-image-prompt-text {
		color: #40586a !important;
		font-size: 24rpx !important;
		line-height: 1.55 !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .local-image-prompt-text {
		color: rgba(255, 255, 255, 0.88) !important;
		font-size: 24rpx !important;
		line-height: 1.55 !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-row {
		align-items: flex-end;
		gap: 12rpx;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-card {
		min-width: 236rpx;
		max-width: 460rpx;
		height: 78rpx;
		padding: 0 22rpx;
		border-radius: 24rpx 24rpx 10rpx 24rpx;
		background: rgba(255, 255, 255, 0.2) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.26) !important;
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.28),
			0 8rpx 18rpx rgba(18, 96, 120, 0.12) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-card--playing {
		background: rgba(255, 255, 255, 0.26) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-card--error {
		background: rgba(255, 230, 236, 0.24) !important;
		border-color: rgba(255, 214, 224, 0.36) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-bar,
	.msg-row--app-plus.me .bubble--app-plus .user-voice-duration {
		color: rgba(255, 255, 255, 0.94) !important;
		background-color: rgba(255, 255, 255, 0.92) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-duration {
		background-color: transparent !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-transcript-wrap {
		max-width: 460rpx;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-voice-transcript {
		font-size: 25rpx !important;
		line-height: 1.65 !important;
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill {
		min-height: 54rpx;
		padding: 0 18rpx;
		border-radius: 999rpx;
		background: rgba(226, 243, 249, 0.86) !important;
		border: 1rpx solid rgba(55, 145, 176, 0.18) !important;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.7) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill-text {
		color: #246178 !important;
		font-size: 23rpx !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill-dot {
		background: #45a9bf !important;
		box-shadow: 0 0 0 8rpx rgba(69, 169, 191, 0.12) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill--playing {
		background: linear-gradient(135deg, #2f91b3 0%, #68c8d6 100%) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill--playing .assistant-voice-pill-text {
		color: #ffffff !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill--error {
		background: rgba(255, 232, 238, 0.92) !important;
		border-color: rgba(224, 110, 140, 0.24) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .assistant-voice-pill--error .assistant-voice-pill-text {
		color: #a64167 !important;
	}

	.msg-row--app-plus .bubble--app-plus .stream-inline--app-plus {
		margin-top: 16rpx;
		padding: 10rpx 16rpx;
		border-radius: 999rpx;
		background: rgba(226, 243, 249, 0.78) !important;
		border: 1rpx solid rgba(55, 145, 176, 0.16) !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .stream-inline--app-plus {
		background: rgba(255, 255, 255, 0.18) !important;
		border-color: rgba(255, 255, 255, 0.22) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .stream-inline-text {
		color: #2d647f !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .stream-inline-text {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .stream-inline-bar {
		background: #45a9bf !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .stream-inline-bar {
		background: rgba(255, 255, 255, 0.92) !important;
	}

	.typing-row--app-plus {
		gap: 16rpx;
		padding: 10rpx 22rpx 0;
	}

	.typing-row--app-plus .typing-hint {
		padding: 10rpx 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.88) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.16);
		color: #2d647f !important;
		box-shadow: 0 8rpx 18rpx rgba(27, 70, 96, 0.08);
	}

	.typing-row--app-plus .stop-stream {
		padding: 10rpx 20rpx;
		border-radius: 999rpx;
		background: rgba(255, 239, 243, 0.94) !important;
		border: 1rpx solid rgba(216, 86, 116, 0.22) !important;
		color: #b64d6f !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .swipe-row {
		margin-top: 18rpx;
		padding-top: 14rpx;
		border-top: 1rpx solid rgba(86, 121, 145, 0.14) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .swipe-btn {
		min-width: 44rpx;
		height: 44rpx;
		line-height: 44rpx;
		text-align: center;
		border-radius: 999rpx;
		background: rgba(226, 243, 249, 0.82);
		color: #2d647f !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .swipe-num {
		color: #5a7182 !important;
	}

	.msg-row--app-plus.me .bubble--app-plus .user-edit-tag {
		color: rgba(255, 255, 255, 0.72) !important;
	}

	.msg-row--app-plus.them .bubble--app-plus .user-edit-tag {
		color: #6a7d8c !important;
	}
	/* #endif */
</style>

<style>
	.wrap .msg-row.them .bubble {
		background: rgba(38, 40, 46, 0.5) !important;
	}

	.wrap .msg-row.me .bubble {
		background: rgba(48, 50, 56, 0.54) !important;
	}

	.wrap .bubble .txt,
	.wrap .bubble .md-inner,
	.wrap .bubble .md-inner *,
	.wrap .bubble .md-inner p,
	.wrap .bubble .md-inner span,
	.wrap .bubble .md-inner strong,
	.wrap .bubble .md-inner em,
	.wrap .bubble .md-inner div,
	.wrap .bubble .md-inner li,
	.wrap .bubble .md-inner blockquote,
	.wrap .bubble .md-inner .st-chat-render,
	.wrap .bubble .md-inner .st-chat-seg,
	.wrap .bubble .md-inner .st-chat-seg *,
	.wrap .bubble .st-chat-seg-text,
	.wrap .bubble text {
		color: #ffffff !important;
		font-style: normal !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.46) !important;
	}

	.wrap .bubble .md-inner .st-chat-seg,
	.wrap .bubble .md-inner .st-chat-seg--speech,
	.wrap .bubble .md-inner .st-chat-seg--narration,
	.wrap .bubble .md-inner .st-chat-seg--action,
	.wrap .bubble .md-inner .st-chat-seg--thought {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		box-shadow: none !important;
	}

	.wrap .bubble .msg-quote-preview {
		background: rgba(255, 255, 255, 0.1) !important;
		border-left-color: rgba(255, 255, 255, 0.24) !important;
	}

	.wrap .bubble .msg-quote-preview-speaker,
	.wrap .bubble .msg-quote-preview-text {
		color: #ffffff !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.4) !important;
	}

	/* #ifdef APP-PLUS */
	.wrap--app-plus .msg-row--app-plus .bubble--app-plus {
		background: rgba(38, 40, 46, 0.54) !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus {
		background: rgba(48, 50, 56, 0.58) !important;
	}

	.wrap--app-plus .bubble--app-plus .txt,
	.wrap--app-plus .bubble--app-plus .md-inner,
	.wrap--app-plus .bubble--app-plus .md-inner *,
	.wrap--app-plus .bubble--app-plus .st-chat-seg-text,
	.wrap--app-plus .bubble--app-plus text {
		color: #ffffff !important;
		font-style: normal !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.46) !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		box-shadow: none !important;
	}
	/* #endif */
</style>

<style>
page {
		background-color: transparent;
		background-image: url('/static/login.png');
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
	}

	.inp-ph {
		color: #64748b;
	}

	/* #ifdef APP-PLUS */
	.inp-ph {
		color: #7f8d9a !important;
	}
	/* #endif */
</style>

<style>
	.input-bar {
		position: relative;
		z-index: 6;
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

	.input-pill {
		flex: 1;
		min-height: 88rpx;
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

	.input-pill--with-quote {
		border-radius: 34rpx;
		padding-top: 12rpx;
		padding-bottom: 12rpx;
	}

	.draft-restore-bar {
		display: flex;
		align-items: center;
		gap: 12rpx;
		min-height: 46rpx;
		padding: 8rpx 12rpx 8rpx 16rpx;
		border-radius: 22rpx;
		background: rgba(236, 247, 252, 0.92);
		border: 1rpx solid rgba(79, 147, 163, 0.14);
		box-sizing: border-box;
	}

	.draft-restore-text {
		flex: 1;
		min-width: 0;
		font-size: 22rpx;
		line-height: 1.35;
		color: #426273;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.draft-restore-action {
		flex-shrink: 0;
		font-size: 22rpx;
		font-weight: 700;
		color: #2f7f96;
	}

	.draft-restore-close {
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

	.input-main {
		display: flex;
		align-items: flex-end;
		gap: 8rpx;
		min-width: 0;
	}

	.composer-quote-bar {
		display: flex;
		align-items: flex-start;
		gap: 14rpx;
		padding: 12rpx 16rpx;
		border-radius: 22rpx;
		background: rgba(240, 244, 247, 0.96);
		border-left: 6rpx solid rgba(52, 143, 184, 0.42);
	}

	.composer-quote-copy {
		flex: 1;
		min-width: 0;
	}

	.composer-quote-speaker {
		display: block;
		font-size: 21rpx;
		font-weight: 700;
		line-height: 1.4;
		color: #2d647f;
	}

	.composer-quote-text {
		display: block;
		margin-top: 4rpx;
		font-size: 22rpx;
		line-height: 1.5;
		color: #526277;
		word-break: break-word;
	}

	.composer-quote-close {
		flex-shrink: 0;
		width: 34rpx;
		height: 34rpx;
		line-height: 34rpx;
		text-align: center;
		font-size: 28rpx;
		color: #7390a2;
	}

	.input-actions {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		gap: 6rpx;
		padding-bottom: 4rpx;
	}

	.voice-status-card {
		margin: 0 18rpx 12rpx auto;
		max-width: 72%;
		padding: 18rpx 22rpx 18rpx 20rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 20rpx;
		border-radius: 30rpx 30rpx 10rpx 30rpx;
		background:
			radial-gradient(circle at 12% 18%, rgba(119, 214, 222, 0.2), transparent 28%),
			linear-gradient(135deg, rgba(103, 196, 220, 0.96) 0%, rgba(77, 178, 214, 0.98) 100%);
		border: 2rpx solid rgba(82, 173, 198, 0.18);
		box-shadow: 0 18rpx 34rpx rgba(50, 118, 146, 0.18);
	}

	.voice-status-card--recording {
		background:
			radial-gradient(circle at 14% 20%, rgba(255, 203, 86, 0.18), transparent 28%),
			linear-gradient(135deg, rgba(104, 198, 222, 0.98) 0%, rgba(244, 154, 181, 0.98) 100%);
	}

	.voice-status-main {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 14rpx;
	}

	.voice-status-wave {
		flex-shrink: 0;
		width: 88rpx;
		height: 52rpx;
		padding: 0 10rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.18);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.22);
	}

	.voice-status-bar {
		width: 8rpx;
		height: 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.9);
		transform-origin: center bottom;
		animation: voice-status-wave 1.15s ease-in-out infinite;
	}

	.voice-status-bar:nth-child(2) {
		height: 28rpx;
		animation-delay: 0.12s;
	}

	.voice-status-bar:nth-child(3) {
		height: 22rpx;
		animation-delay: 0.24s;
	}

	.voice-status-bar:nth-child(4) {
		height: 32rpx;
		animation-delay: 0.36s;
	}

	.voice-status-copy {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.voice-status-title {
		font-size: 25rpx;
		font-weight: 700;
		color: #fff;
	}

	.voice-status-sub {
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.82);
	}

	.voice-status-action {
		flex-shrink: 0;
		height: 64rpx;
		padding: 0 24rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.16);
		color: #fff;
		font-size: 23rpx;
		font-weight: 700;
		border: 2rpx solid rgba(255, 255, 255, 0.16);
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
		background: linear-gradient(135deg, rgba(255, 255, 255, 0.26) 0%, rgba(255, 255, 255, 0.14) 100%);
		border: 2rpx solid rgba(255, 255, 255, 0.2);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.22);
	}

	.user-voice-card--playing {
		background: linear-gradient(135deg, rgba(255, 255, 255, 0.32) 0%, rgba(255, 255, 255, 0.2) 100%);
	}

	.user-voice-card--error {
		background: linear-gradient(135deg, rgba(255, 255, 255, 0.18) 0%, rgba(255, 216, 220, 0.22) 100%);
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
		color: rgba(255, 255, 255, 0.92);
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
		background: rgba(255, 255, 255, 0.92);
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
		color: rgba(255, 255, 255, 0.92);
	}

	.user-edit-tag--voice {
		margin-top: 2rpx;
		flex-shrink: 0;
	}

	.user-voice-card--playing .user-voice-bar {
		animation: voice-status-wave 1.2s ease-in-out infinite;
	}

	.expression-panel {
		margin: 0 18rpx 10rpx;
		padding: 20rpx;
		border-radius: 34rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(244, 247, 251, 0.95) 100%);
		border: 2rpx solid rgba(148, 163, 184, 0.12);
		box-shadow: 0 22rpx 44rpx rgba(15, 23, 42, 0.14);
		backdrop-filter: blur(14rpx);
	}

	.expression-panel-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-bottom: 18rpx;
	}

	.expression-panel-actions {
		display: flex;
		align-items: center;
		gap: 10rpx;
		flex-shrink: 0;
	}

	.expression-panel-title-wrap {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.expression-panel-title {
		font-size: 26rpx;
		font-weight: 700;
		color: #1f2937;
	}

	.expression-panel-count {
		min-width: 42rpx;
		height: 42rpx;
		padding: 0 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		background: rgba(15, 23, 42, 0.88);
		color: #fff;
		font-size: 22rpx;
		font-weight: 600;
		box-sizing: border-box;
	}

	.expression-upload-chip {
		min-width: 148rpx;
		height: 62rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 20rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.88);
		border: 2rpx solid rgba(148, 163, 184, 0.16);
		color: #1f2937;
		font-size: 23rpx;
		font-weight: 600;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.55);
	}

	.expression-upload-chip--active {
		background: linear-gradient(135deg, rgba(94, 200, 240, 0.96), rgba(122, 162, 255, 0.94));
		border-color: transparent;
		color: #fff;
		box-shadow: 0 12rpx 24rpx rgba(94, 200, 240, 0.24);
	}

	.expression-upload-chip--muted {
		background: rgba(15, 23, 42, 0.05);
		color: #526277;
	}

	.expression-upload-chip--disabled {
		opacity: 0.55;
	}

	.expression-panel-close {
		width: 54rpx;
		height: 54rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		background: rgba(15, 23, 42, 0.06);
		color: #0f172a;
		font-size: 28rpx;
		line-height: 1;
	}

	.expression-empty {
		display: block;
	}

	.expression-empty-card {
		width: 100%;
		min-height: 224rpx;
		padding: 30rpx 24rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16rpx;
		border-radius: 30rpx;
		background:
			radial-gradient(circle at 18% 20%, rgba(118, 210, 221, 0.22), transparent 24%),
			radial-gradient(circle at 82% 78%, rgba(244, 166, 196, 0.18), transparent 20%),
			linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(237, 242, 247, 0.98) 100%);
		border: 2rpx solid rgba(148, 163, 184, 0.16);
		box-shadow:
			inset 0 1rpx 0 rgba(255, 255, 255, 0.72),
			0 18rpx 34rpx rgba(148, 163, 184, 0.12);
		box-sizing: border-box;
	}

	.expression-empty-card--disabled {
		opacity: 0.55;
	}

	.expression-empty-badge-wrap {
		position: relative;
		width: 100rpx;
		height: 100rpx;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.expression-empty-badge {
		width: 78rpx;
		height: 78rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 26rpx;
		background: rgba(15, 23, 42, 0.06);
		color: #111827;
		font-size: 38rpx;
		font-weight: 500;
		line-height: 1;
	}

	.expression-empty-dot {
		position: absolute;
		display: block;
		border-radius: 999rpx;
		background: rgba(52, 143, 184, 0.18);
	}

	.expression-empty-dot--one {
		top: 10rpx;
		right: 6rpx;
		width: 18rpx;
		height: 18rpx;
	}

	.expression-empty-dot--two {
		left: 4rpx;
		bottom: 14rpx;
		width: 12rpx;
		height: 12rpx;
		background: rgba(244, 166, 196, 0.22);
	}

	.expression-empty-label {
		font-size: 24rpx;
		font-weight: 600;
		color: #334155;
	}

	.expression-body {
		display: flex;
		flex-direction: column;
		gap: 18rpx;
	}

	.expression-recent-section {
		padding: 18rpx 18rpx 16rpx;
		border-radius: 28rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.82) 0%, rgba(240, 247, 251, 0.9) 100%);
		border: 2rpx solid rgba(148, 163, 184, 0.1);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.7);
	}

	.expression-section-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12rpx;
		margin-bottom: 14rpx;
	}

	.expression-section-head--grid {
		margin-bottom: 0;
		padding: 0 4rpx;
	}

	.expression-section-title {
		font-size: 24rpx;
		font-weight: 700;
		color: #1f2937;
	}

	.expression-section-sub {
		min-width: 40rpx;
		height: 40rpx;
		padding: 0 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		background: rgba(15, 23, 42, 0.08);
		color: #475569;
		font-size: 20rpx;
		font-weight: 600;
		box-sizing: border-box;
	}

	.expression-recent-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.expression-recent-row {
		display: inline-flex;
		align-items: stretch;
		gap: 14rpx;
		padding-right: 4rpx;
	}

	.expression-recent-card {
		width: 128rpx;
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 10rpx;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.9);
		border: 2rpx solid rgba(148, 163, 184, 0.12);
		box-shadow: 0 10rpx 22rpx rgba(15, 23, 42, 0.06);
		box-sizing: border-box;
	}

	.expression-recent-image {
		width: 108rpx;
		height: 108rpx;
		border-radius: 20rpx;
		background: #f3f4f6;
	}

	.expression-recent-label {
		width: 100%;
		margin-top: 10rpx;
		font-size: 21rpx;
		font-weight: 600;
		line-height: 1.2;
		color: #334155;
		text-align: center;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.expression-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 14rpx;
	}

	.expression-card {
		width: calc((100% - 42rpx) / 4);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12rpx;
		position: relative;
		padding: 8rpx 8rpx 12rpx;
		border-radius: 30rpx;
		background: rgba(255, 255, 255, 0.74);
		border: 2rpx solid rgba(148, 163, 184, 0.12);
		box-sizing: border-box;
		box-shadow: 0 12rpx 24rpx rgba(15, 23, 42, 0.05);
	}

	.expression-card-image {
		width: 100%;
		height: 134rpx;
		border-radius: 24rpx;
		background: #f3f4f6;
	}

	.expression-card-actions {
		position: absolute;
		top: 8rpx;
		right: 8rpx;
		display: flex;
		align-items: center;
		gap: 8rpx;
	}

	.expression-card-action {
		height: 34rpx;
		min-width: 34rpx;
		padding: 0 10rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		font-size: 20rpx;
		font-weight: 700;
		line-height: 1;
		box-sizing: border-box;
	}

	.expression-card-action--rename {
		background: rgba(255, 255, 255, 0.92);
		color: #1f4f68;
	}

	.expression-card-action--remove {
		background: rgba(15, 23, 42, 0.68);
		color: #fff;
	}

	.expression-card-label {
		font-size: 22rpx;
		line-height: 1.2;
		color: #374151;
		max-width: 100%;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.expression-editor-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2200;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 32rpx;
		background: rgba(15, 23, 42, 0.42);
		box-sizing: border-box;
	}

	.expression-editor-panel {
		width: 100%;
		max-width: 640rpx;
		padding: 30rpx;
		border-radius: 36rpx;
		background: rgba(255, 255, 255, 0.9);
		border: 1rpx solid rgba(255, 255, 255, 0.56);
		box-shadow: 0 24rpx 56rpx rgba(15, 23, 42, 0.22);
		box-sizing: border-box;
	}

	.expression-editor-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
	}

	.expression-editor-title {
		display: block;
		font-size: 30rpx;
		font-weight: 700;
		color: #1f2937;
	}

	.expression-editor-close {
		width: 56rpx;
		height: 56rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		background: rgba(15, 23, 42, 0.06);
		color: #0f172a;
		font-size: 28rpx;
		line-height: 1;
	}

	.expression-editor-preview {
		width: 100%;
		height: 320rpx;
		margin-top: 20rpx;
		border-radius: 30rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.84) 0%, rgba(233, 238, 244, 0.96) 100%);
		border: 2rpx solid rgba(148, 163, 184, 0.14);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
	}

	.expression-editor-meta {
		display: flex;
		justify-content: flex-end;
		margin-top: 12rpx;
	}

	.expression-editor-count {
		font-size: 22rpx;
		color: #64748b;
	}

	.expression-editor-input {
		width: 100%;
		height: 82rpx;
		margin-top: 12rpx;
		padding: 0 22rpx;
		border-radius: 26rpx;
		background: rgba(255, 255, 255, 0.92);
		border: 2rpx solid rgba(148, 163, 184, 0.16);
		color: #1f2937;
		font-size: 28rpx;
		box-sizing: border-box;
	}

	.expression-editor-actions {
		display: flex;
		gap: 14rpx;
		margin-top: 20rpx;
	}

	.expression-editor-btn {
		flex: 1;
		height: 72rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 24rpx;
		border-radius: 999rpx;
		font-size: 24rpx;
		font-weight: 600;
	}

	.expression-editor-btn--ghost {
		background: rgba(241, 245, 249, 0.94);
		color: #475569;
	}

	.expression-editor-btn--primary {
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.94) 0%, rgba(118, 210, 221, 0.92) 100%);
		color: #fff;
	}

	.scroll-bottom-pill {
		flex: 1;
		height: 88rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 26rpx;
		border-radius: 999rpx;
		background: linear-gradient(135deg, rgba(52, 143, 184, 0.92) 0%, rgba(118, 210, 221, 0.9) 62%, rgba(244, 166, 196, 0.92) 100%) !important;
		box-shadow: 0 12rpx 24rpx rgba(52, 143, 184, 0.16);
	}

	.scroll-bottom-pill-text {
		color: #fff;
		font-size: 28rpx;
		font-weight: 600;
		letter-spacing: 1rpx;
	}

	.inp {
		flex: 1;
		min-height: 72rpx !important;
		max-height: 196rpx !important;
		padding: 14rpx 8rpx 14rpx 0 !important;
		background: transparent !important;
		border: 0 !important;
		box-shadow: none !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
		color: #1f2937 !important;
		font-size: 28rpx;
		line-height: 40rpx;
	}

	.expression-trigger,
	.attach-btn {
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
	}

	.input-action-icon {
		width: 42rpx;
		height: 42rpx;
	}

	.attach-btn--active {
		transform: rotate(45deg);
	}

	.send.send--icon {
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

	.send-icon {
		width: 42rpx;
		height: 42rpx;
	}

	.attach-fab-menu {
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
		gap: 18rpx;
	}

	.attach-fab-item {
		display: flex;
	}

	.attach-fab-item--active .attach-fab-badge {
		background: linear-gradient(135deg, #ef86af 0%, #f5bdd1 100%);
		border-color: rgba(239, 134, 175, 0.12);
		box-shadow: 0 14rpx 26rpx rgba(239, 134, 175, 0.2);
	}

	.attach-fab-badge {
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

	.attach-fab-icon {
		width: 44rpx;
		height: 44rpx;
	}

	.attach-fab-label {
		display: none !important;
	}

	/* #ifdef APP-PLUS */
	.wrap--app-plus.focused .chat-scroll {
		padding-bottom: 42rpx !important;
	}

	.wrap--app-plus .input-bar {
		align-items: flex-end !important;
		gap: 10rpx !important;
		padding: 10rpx 14rpx calc(10rpx + env(safe-area-inset-bottom)) !important;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(232, 244, 250, 0.72) 100%) !important;
	}

	.wrap--app-plus .input-pill {
		min-height: 82rpx !important;
		max-height: 300rpx;
		padding: 8rpx 10rpx 8rpx 20rpx !important;
		border-radius: 30rpx !important;
		background: rgba(255, 255, 255, 0.96) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.18) !important;
		box-shadow: 0 10rpx 24rpx rgba(27, 70, 96, 0.1) !important;
	}

	.wrap--app-plus .input-pill--with-quote {
		border-radius: 28rpx !important;
		padding-top: 12rpx !important;
		padding-bottom: 12rpx !important;
	}

	.wrap--app-plus .draft-restore-bar {
		min-height: 44rpx;
		padding: 8rpx 12rpx !important;
		border-radius: 20rpx !important;
		background: rgba(236, 247, 252, 0.94) !important;
		border-color: rgba(55, 145, 176, 0.16) !important;
	}

	.wrap--app-plus .draft-restore-text {
		font-size: 21rpx !important;
		color: #426273 !important;
	}

	.wrap--app-plus .draft-restore-action {
		font-size: 21rpx !important;
		color: #2f7f96 !important;
	}

	.wrap--app-plus .draft-restore-close {
		background: rgba(255, 255, 255, 0.86) !important;
		color: #6d7f8b !important;
	}

	.wrap--app-plus .input-main {
		align-items: flex-end !important;
		gap: 8rpx !important;
		min-height: 66rpx;
	}

	.wrap--app-plus .inp {
		min-height: 66rpx !important;
		max-height: 208rpx !important;
		padding: 13rpx 8rpx 13rpx 0 !important;
		font-size: 28rpx !important;
		line-height: 40rpx !important;
		color: #1b2e40 !important;
		word-break: break-word;
		white-space: pre-wrap;
		overflow-y: auto;
	}

	.wrap--app-plus .input-actions {
		align-self: flex-end;
		height: 66rpx;
		gap: 4rpx !important;
		padding-bottom: 0 !important;
	}

	.wrap--app-plus .expression-trigger,
	.wrap--app-plus .attach-btn {
		width: 66rpx !important;
		height: 66rpx !important;
		min-width: 66rpx !important;
		border-radius: 50% !important;
		background: rgba(236, 247, 252, 0.88) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.12) !important;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.82) !important;
	}

	.wrap--app-plus .input-action-icon {
		width: 40rpx;
		height: 40rpx;
	}

	.wrap--app-plus .send.send--icon {
		width: 82rpx !important;
		height: 82rpx !important;
		min-width: 82rpx !important;
		align-self: flex-end !important;
		border-radius: 50% !important;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.2) !important;
	}

	.wrap--app-plus .send-icon {
		width: 40rpx;
		height: 40rpx;
	}

	.wrap--app-plus .composer-quote-bar {
		max-height: 138rpx;
		padding: 12rpx 14rpx !important;
		border-radius: 20rpx !important;
		background: rgba(236, 247, 252, 0.92) !important;
		border-left: 6rpx solid rgba(55, 145, 176, 0.42);
		overflow: hidden;
	}

	.wrap--app-plus .composer-quote-text {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.wrap--app-plus .composer-quote-close {
		width: 42rpx;
		height: 42rpx;
		line-height: 42rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.76);
		color: #5b7284;
	}

	.wrap--app-plus .scroll-bottom-pill {
		height: 82rpx;
		border-radius: 999rpx;
		background: #4f93a3 !important;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.18) !important;
	}

	.wrap--app-plus .attach-fab-menu {
		right: 16rpx;
		bottom: calc(env(safe-area-inset-bottom) + 110rpx);
		max-width: calc(100vw - 126rpx);
		gap: 14rpx;
	}

	.wrap--app-plus .attach-fab-badge {
		width: 82rpx;
		height: 82rpx;
		background: rgba(255, 255, 255, 0.96);
		border-color: rgba(86, 121, 145, 0.14);
		box-shadow: 0 12rpx 24rpx rgba(27, 70, 96, 0.12);
	}

	.wrap--app-plus .message-action-mask {
		background: rgba(6, 10, 24, 0.08);
	}

	.wrap--app-plus .message-action-menu {
		min-width: 236rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.96) !important;
		border: 1rpx solid rgba(86, 121, 145, 0.16) !important;
		box-shadow: 0 18rpx 42rpx rgba(27, 70, 96, 0.2) !important;
	}

	.wrap--app-plus .message-action-item {
		min-height: 88rpx;
		padding: 0 28rpx;
	}

	.wrap--app-plus .message-action-item-label {
		font-size: 27rpx;
		color: #1f6686 !important;
	}

	.wrap--app-plus .message-action-item--danger .message-action-item-label {
		color: #c65373 !important;
	}

	.wrap--app-plus {
		background-color: transparent !important;
		background-image: url('/static/login.png') !important;
		background-size: cover !important;
		background-position: center center !important;
		background-repeat: no-repeat !important;
	}

	.wrap--app-plus::before {
		display: none !important;
		background-image: none !important;
	}

	.wrap--app-plus::after {
		background: rgba(255, 255, 255, 0.02) !important;
	}

	.wrap--app-plus .chat-default-bg,
	.wrap--app-plus .chat-role-bg {
		position: absolute !important;
		inset: 0 !important;
		width: 100% !important;
		height: 100% !important;
		z-index: 0 !important;
		opacity: 1 !important;
	}

	.wrap--app-plus .tool-bar,
	.wrap--app-plus .memory-bar,
	.wrap--app-plus .reply-help-panel,
	.wrap--app-plus .ai-disclaimer,
	.wrap--app-plus .input-bar,
	.wrap--app-plus .bubble,
	.wrap--app-plus .msg-row.me .bubble,
	.wrap--app-plus .msg-row.them .bubble {
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus .msg-row {
		padding-left: 22rpx !important;
		padding-right: 22rpx !important;
		gap: 14rpx !important;
		margin-bottom: 30rpx !important;
	}

	.wrap--app-plus .bubble,
	.wrap--app-plus .bubble--app-plus {
		max-width: calc(100% - 116rpx) !important;
		min-width: 0 !important;
		padding: 22rpx 26rpx !important;
		border-radius: 32rpx 32rpx 32rpx 16rpx !important;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.88) 0%, rgba(240, 250, 252, 0.82) 100%) !important;
		border: 1rpx solid rgba(85, 120, 138, 0.18) !important;
		box-shadow:
			0 18rpx 34rpx rgba(30, 56, 76, 0.16),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.9),
			inset 0 -1rpx 0 rgba(96, 156, 178, 0.1) !important;
		box-sizing: border-box !important;
		overflow: visible !important;
	}

	.wrap--app-plus .msg-row.me .bubble,
	.wrap--app-plus .bubble--app-plus.bubble--me {
		border-radius: 32rpx 32rpx 16rpx 32rpx !important;
		background: linear-gradient(135deg, #3bb984 0%, #36a39d 56%, #2d8da6 100%) !important;
		border-color: rgba(235, 255, 248, 0.42) !important;
		box-shadow:
			0 18rpx 34rpx rgba(32, 125, 116, 0.24),
			inset 0 1rpx 0 rgba(245, 255, 252, 0.32),
			inset 0 -1rpx 0 rgba(18, 94, 98, 0.16) !important;
	}

	.wrap--app-plus .bubble::before,
	.wrap--app-plus .bubble--app-plus::before {
		display: none !important;
	}

	.wrap--app-plus .bubble::after,
	.wrap--app-plus .bubble--app-plus::after {
		width: 18rpx !important;
		height: 18rpx !important;
		top: 24rpx !important;
		border: none !important;
		box-shadow: none !important;
	}

	.wrap--app-plus .msg-row.them .bubble::after,
	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus::after {
		left: -8rpx !important;
		background: rgba(246, 251, 252, 0.88) !important;
		transform: rotate(45deg) !important;
	}

	.wrap--app-plus .msg-row.me .bubble::after,
	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus::after {
		right: -8rpx !important;
		background: #36a39d !important;
		transform: rotate(45deg) !important;
	}

	.wrap--app-plus .bubble .txt,
	.wrap--app-plus .bubble .md-inner,
	.wrap--app-plus .bubble .md-inner *,
	.wrap--app-plus .bubble text,
	.wrap--app-plus .bubble--app-plus .txt,
	.wrap--app-plus .bubble--app-plus .md-inner,
	.wrap--app-plus .bubble--app-plus .md-inner *,
	.wrap--app-plus .bubble--app-plus text {
		font-size: 30rpx !important;
		line-height: 1.78 !important;
		color: #25394a !important;
		word-break: break-word !important;
		overflow-wrap: break-word !important;
		text-shadow: none !important;
	}

	.wrap--app-plus .msg-row.me .bubble .txt,
	.wrap--app-plus .msg-row.me .bubble .md-inner,
	.wrap--app-plus .msg-row.me .bubble .md-inner *,
	.wrap--app-plus .msg-row.me .bubble text,
	.wrap--app-plus .bubble--app-plus.bubble--me .txt,
	.wrap--app-plus .bubble--app-plus.bubble--me .md-inner,
	.wrap--app-plus .bubble--app-plus.bubble--me .md-inner *,
	.wrap--app-plus .bubble--app-plus.bubble--me text {
		color: #ffffff !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .md-inner--native {
		gap: 12rpx !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native {
		border-radius: 18rpx !important;
		box-sizing: border-box !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--speech {
		padding: 10rpx 14rpx !important;
		background: rgba(255, 235, 246, 0.72) !important;
		border-left: 6rpx solid rgba(226, 82, 145, 0.78) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--speech .st-chat-seg-text {
		color: #cf3278 !important;
		font-size: 31rpx !important;
		line-height: 1.76 !important;
		font-weight: 800 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--narration .st-chat-seg-text {
		color: #25394a !important;
		font-weight: 500 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--action {
		padding: 8rpx 12rpx 8rpx 16rpx !important;
		background: rgba(60, 180, 156, 0.11) !important;
		border-left: 5rpx solid rgba(55, 171, 148, 0.56) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--action .st-chat-seg-text {
		color: #2c5f58 !important;
		font-size: 29rpx !important;
		line-height: 1.78 !important;
		font-style: italic;
		font-weight: 500 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--thought {
		padding: 8rpx 12rpx 8rpx 16rpx !important;
		background: rgba(141, 112, 190, 0.1) !important;
		border-left: 5rpx solid rgba(141, 112, 190, 0.42) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .st-chat-seg-native--thought .st-chat-seg-text {
		color: #615579 !important;
		font-size: 29rpx !important;
		line-height: 1.78 !important;
		font-style: italic;
		font-weight: 500 !important;
	}

	.wrap--app-plus .bubble--image-only {
		padding: 8rpx !important;
		background: transparent !important;
		border-color: transparent !important;
		box-shadow: none !important;
	}

	.wrap--app-plus .msg-image {
		max-width: 430rpx !important;
		max-height: 520rpx !important;
		border-radius: 22rpx !important;
	}

	.wrap--app-plus .input-bar {
		position: relative !important;
		z-index: 5 !important;
		padding: 10rpx 14rpx calc(12rpx + env(safe-area-inset-bottom)) !important;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(246, 252, 252, 0.18) 100%) !important;
		box-shadow: 0 -10rpx 24rpx rgba(38, 57, 77, 0.05) !important;
	}

	.wrap--app-plus .input-pill {
		flex: 1 1 auto !important;
		min-width: 0 !important;
		min-height: 82rpx !important;
		max-height: 300rpx !important;
		background: rgba(255, 255, 255, 0.72) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.42) !important;
		box-shadow: 0 10rpx 26rpx rgba(24, 48, 68, 0.12) !important;
	}

	.wrap--app-plus .inp {
		width: 100% !important;
		min-height: 66rpx !important;
		max-height: 208rpx !important;
		color: #26394d !important;
		background: transparent !important;
	}

	.wrap--app-plus.focused .chat-scroll {
		padding-bottom: 56rpx !important;
	}

	.wrap--app-plus .attach-fab-menu {
		z-index: 9 !important;
	}

	.wrap--app-plus .message-action-mask {
		z-index: 2000 !important;
	}

	.wrap--app-plus .message-action-menu {
		z-index: 2001 !important;
	}

	.generation-recovery {
		background: rgba(236, 247, 252, 0.92) !important;
		border-color: rgba(79, 147, 163, 0.18) !important;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.58) !important;
	}

	.generation-recovery-title {
		color: #1f5268 !important;
	}

	.generation-recovery-message {
		color: #536878 !important;
	}

	.generation-recovery-close {
		color: #667b88 !important;
		background: rgba(255, 255, 255, 0.72) !important;
	}
	/* #endif */

	@keyframes voice-status-pulse {
		0% {
			transform: scale(0.92);
			opacity: 0.82;
		}
		50% {
			transform: scale(1.08);
			opacity: 1;
		}
		100% {
			transform: scale(0.92);
			opacity: 0.82;
		}
	}

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
</style>

<style scoped lang="scss">
	.bubble::before,
	.bubble--app-plus::before {
		display: none !important;
	}

	.msg-row.them .bubble {
		max-width: 76%;
		padding: 16rpx 20rpx !important;
		border-radius: 22rpx;
		background: rgba(38, 40, 46, 0.42) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.16) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.12),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08) !important;
		backdrop-filter: blur(8rpx) saturate(106%);
		-webkit-backdrop-filter: blur(8rpx) saturate(106%);
	}

	.msg-row.me .bubble {
		max-width: 70%;
		padding: 16rpx 20rpx !important;
		border-radius: 22rpx;
		background: rgba(48, 50, 56, 0.46) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.18) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.12),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08) !important;
		backdrop-filter: blur(6rpx) saturate(106%);
		-webkit-backdrop-filter: blur(6rpx) saturate(106%);
	}

	.msg-row.them .bubble::after,
	.msg-row.me .bubble::after {
		display: none !important;
	}

	.bubble .txt,
	.bubble .md-inner,
	.bubble .md-inner * {
		font-size: 28rpx;
		line-height: 1.66;
		letter-spacing: 0;
		color: #ffffff !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
		word-break: break-word;
		overflow-wrap: break-word;
	}

	.msg-row.them .bubble .txt,
	.msg-row.them .bubble .md-inner,
	.msg-row.them .bubble .md-inner * {
		color: #ffffff !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.msg-row.me .bubble .txt,
	.msg-row.me .bubble .md-inner,
	.msg-row.me .bubble .md-inner * {
		color: #ffffff !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.bubble--image-only {
		padding: 8rpx !important;
		background: transparent !important;
		border-color: transparent !important;
		box-shadow: none !important;
	}

	.bubble--image-only::before,
	.bubble--image-only::after {
		display: none !important;
	}

	.md-inner >>> .st-chat-render {
		gap: 6rpx;
	}

	.md-inner >>> .st-chat-seg {
		margin: 0 0 6rpx !important;
		letter-spacing: 0 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
		box-shadow: none !important;
	}

	.md-inner >>> .st-chat-seg > *:first-child {
		margin-top: 0 !important;
	}

	.md-inner >>> .st-chat-seg > *:last-child {
		margin-bottom: 0 !important;
	}

	.md-inner >>> .st-chat-seg--speech,
	.md-inner >>> .st-chat-seg--speech p,
	.md-inner >>> .st-chat-seg--speech * {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		color: #ffffff !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.md-inner >>> .st-chat-seg--narration,
	.md-inner >>> .st-chat-seg--narration p,
	.md-inner >>> .st-chat-seg--narration * {
		background: transparent !important;
		border-left: 0 !important;
		color: #ffffff !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.md-inner >>> .st-chat-seg--action {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		border-radius: 0 !important;
	}

	.md-inner >>> .st-chat-seg--action p,
	.md-inner >>> .st-chat-seg--action * {
		color: #ffffff !important;
		font-style: normal !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.md-inner >>> .st-chat-seg--thought {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		border-radius: 0 !important;
	}

	.md-inner >>> .st-chat-seg--thought p,
	.md-inner >>> .st-chat-seg--thought * {
		color: #ffffff !important;
		font-style: normal !important;
		font-weight: 500 !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.bubble .msg-quote-preview {
		margin-bottom: 10rpx;
		padding: 10rpx 12rpx;
		border-radius: 14rpx;
		background: rgba(255, 255, 255, 0.1) !important;
		border-left-color: rgba(255, 255, 255, 0.26) !important;
	}

	.bubble .msg-quote-preview-speaker {
		color: #ffffff !important;
	}

	.bubble .msg-quote-preview-text {
		color: rgba(255, 255, 255, 0.86) !important;
	}

	.msg-row.me .bubble .msg-quote-preview {
		background: rgba(255, 255, 255, 0.14) !important;
		border-left-color: rgba(255, 255, 255, 0.38) !important;
	}

	.msg-row.me .bubble .msg-quote-preview-speaker {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.msg-row.me .bubble .msg-quote-preview-text {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	/* #ifdef APP-PLUS */
	.wrap--app-plus .msg-row--app-plus {
		padding-left: 14rpx !important;
		padding-right: 14rpx !important;
		gap: 10rpx !important;
		margin-bottom: 22rpx !important;
	}

	.wrap--app-plus .msg-row--app-plus .bubble--app-plus {
		max-width: calc(100% - 104rpx) !important;
		min-width: 0 !important;
		padding: 16rpx 20rpx !important;
		border-radius: 22rpx !important;
		background: rgba(38, 40, 46, 0.46) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.16) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.12),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08) !important;
		box-sizing: border-box !important;
		overflow: visible !important;
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus {
		max-width: calc(100% - 120rpx) !important;
		border-radius: 22rpx !important;
		background: rgba(48, 50, 56, 0.5) !important;
		border-color: rgba(255, 255, 255, 0.18) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.12),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.08) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus::after,
	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus::after {
		display: none !important;
	}

	.wrap--app-plus .msg-row--app-plus .bubble--app-plus .txt,
	.wrap--app-plus .msg-row--app-plus .bubble--app-plus .md-inner,
	.wrap--app-plus .msg-row--app-plus .bubble--app-plus .st-chat-seg-text {
		font-size: 28rpx !important;
		line-height: 1.66 !important;
		letter-spacing: 0 !important;
		color: #ffffff !important;
		word-break: break-word !important;
		overflow-wrap: break-word !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .txt,
	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .md-inner,
	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus text {
		color: #ffffff !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus .txt,
	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus .md-inner,
	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus text {
		color: #ffffff !important;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.48) !important;
	}

	.wrap--app-plus .msg-row--app-plus .bubble--app-plus .msg-quote-preview {
		margin-bottom: 10rpx;
		padding: 10rpx 12rpx;
		border-radius: 14rpx;
		background: rgba(255, 255, 255, 0.1) !important;
		border-left-color: rgba(255, 255, 255, 0.26) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .msg-quote-preview-speaker {
		color: #ffffff !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .msg-quote-preview-text {
		color: rgba(255, 255, 255, 0.86) !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus .msg-quote-preview {
		background: rgba(255, 255, 255, 0.14) !important;
		border-left-color: rgba(255, 255, 255, 0.38) !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus .msg-quote-preview-speaker {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus .msg-quote-preview-text {
		color: rgba(255, 255, 255, 0.9) !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .md-inner--native {
		gap: 6rpx !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native {
		margin: 0 0 6rpx !important;
		border-radius: 0 !important;
		box-sizing: border-box !important;
		background: transparent !important;
		box-shadow: none !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--speech {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--speech .st-chat-seg-text {
		color: #ffffff !important;
		font-size: 28rpx !important;
		line-height: 1.66 !important;
		font-weight: 500 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--narration .st-chat-seg-text {
		color: #ffffff !important;
		font-weight: 500 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--action {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--action .st-chat-seg-text {
		color: #ffffff !important;
		font-size: 28rpx !important;
		line-height: 1.66 !important;
		font-style: normal;
		font-weight: 500 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--thought {
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
	}

	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus .st-chat-seg-native--thought .st-chat-seg-text {
		color: #ffffff !important;
		font-size: 28rpx !important;
		line-height: 1.66 !important;
		font-style: normal;
		font-weight: 500 !important;
	}
	/* #endif */
</style>



<style>
	.wrap .msg-row.them .bubble {
		position: relative !important;
		max-width: 78% !important;
		padding: 15rpx 20rpx !important;
		border-radius: 20rpx !important;
		background: linear-gradient(180deg, rgba(32, 34, 42, 0.62) 0%, rgba(24, 26, 34, 0.54) 100%) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.17) !important;
		border-left: 4rpx solid rgba(255, 193, 220, 0.42) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.14),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.12) !important;
		backdrop-filter: blur(8rpx) saturate(108%) !important;
		-webkit-backdrop-filter: blur(8rpx) saturate(108%) !important;
	}

	.wrap .msg-row.me .bubble {
		position: relative !important;
		max-width: 72% !important;
		padding: 15rpx 20rpx !important;
		border-radius: 20rpx !important;
		background: linear-gradient(180deg, rgba(38, 65, 72, 0.62) 0%, rgba(30, 53, 60, 0.54) 100%) !important;
		border: 1rpx solid rgba(220, 252, 255, 0.2) !important;
		border-right: 4rpx solid rgba(200, 245, 223, 0.42) !important;
		box-shadow:
			0 6rpx 16rpx rgba(0, 0, 0, 0.13),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.14) !important;
		backdrop-filter: blur(8rpx) saturate(108%) !important;
		-webkit-backdrop-filter: blur(8rpx) saturate(108%) !important;
	}

	.wrap .bubble--image-only {
		padding: 8rpx !important;
		background: transparent !important;
		border-color: transparent !important;
		box-shadow: none !important;
	}

	.wrap .bubble .txt,
	.wrap .bubble .md-inner,
	.wrap .bubble .st-chat-seg-text,
	.wrap .bubble text {
		color: #f2f4f7 !important;
		font-style: normal !important;
		font-weight: 560 !important;
		text-shadow: none !important;
	}

	.wrap .bubble .st-chat-seg,
	.wrap .bubble .st-chat-seg *,
	.wrap .bubble .st-chat-seg p {
		color: #f2f4f7 !important;
		font-style: normal !important;
		font-weight: 560 !important;
		text-shadow: none !important;
	}

	.wrap .bubble .st-chat-seg--speech,
	.wrap .bubble .st-chat-seg--speech *,
	.wrap .bubble .st-chat-seg--speech p {
		color: #f4b8cf !important;
		font-weight: 600 !important;
	}

	.wrap .bubble .st-chat-seg--action,
	.wrap .bubble .st-chat-seg--action *,
	.wrap .bubble .st-chat-seg--action p {
		color: #bfe8d2 !important;
	}

	.wrap .bubble .st-chat-seg--thought,
	.wrap .bubble .st-chat-seg--thought *,
	.wrap .bubble .st-chat-seg--thought p {
		color: #d4caef !important;
	}

	.wrap .bubble .st-chat-seg--narration,
	.wrap .bubble .st-chat-seg--narration *,
	.wrap .bubble .st-chat-seg--narration p {
		color: #f2f4f7 !important;
	}

	.wrap .bubble .st-chat-seg,
	.wrap .bubble .st-chat-seg--speech,
	.wrap .bubble .st-chat-seg--narration,
	.wrap .bubble .st-chat-seg--action,
	.wrap .bubble .st-chat-seg--thought {
		background: transparent !important;
		border-left: 0 !important;
		box-shadow: none !important;
	}

	/* #ifdef APP-PLUS */
	.wrap--app-plus .msg-row--app-plus.them .bubble--app-plus {
		position: relative !important;
		max-width: calc(100% - 110rpx) !important;
		padding: 14rpx 18rpx !important;
		border-radius: 19rpx !important;
		background: rgba(23, 25, 33, 0.54) !important;
		border: 1rpx solid rgba(255, 255, 255, 0.14) !important;
		border-left: 4rpx solid rgba(241, 171, 198, 0.5) !important;
		box-shadow:
			0 5rpx 12rpx rgba(0, 0, 0, 0.1),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.1) !important;
	}

	.wrap--app-plus .msg-row--app-plus.me .bubble--app-plus {
		position: relative !important;
		max-width: calc(100% - 122rpx) !important;
		padding: 14rpx 18rpx !important;
		border-radius: 19rpx !important;
		background: rgba(29, 51, 57, 0.54) !important;
		border: 1rpx solid rgba(220, 252, 255, 0.16) !important;
		border-right: 4rpx solid rgba(183, 222, 198, 0.48) !important;
		box-shadow:
			0 5rpx 12rpx rgba(0, 0, 0, 0.1),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.1) !important;
	}

	.wrap--app-plus .bubble--app-plus .txt,
	.wrap--app-plus .bubble--app-plus .md-inner,
	.wrap--app-plus .bubble--app-plus .st-chat-seg-text,
	.wrap--app-plus .bubble--app-plus text {
		color: #f3f4f6 !important;
		font-size: 29rpx !important;
		line-height: 1.72 !important;
		font-style: normal !important;
		font-weight: 560 !important;
		letter-spacing: 0 !important;
		text-shadow: none !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native {
		margin: 0 0 7rpx !important;
		padding: 0 !important;
		background: transparent !important;
		border-left: 0 !important;
		box-shadow: none !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native--speech .st-chat-seg-text {
		color: #f1abc6 !important;
		font-weight: 600 !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native--action .st-chat-seg-text {
		color: #b7dec6 !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native--thought .st-chat-seg-text {
		color: #cbc2e2 !important;
	}

	.wrap--app-plus .bubble--app-plus .st-chat-seg-native--narration .st-chat-seg-text {
		color: #f3f4f6 !important;
	}
	/* #endif */
</style>



