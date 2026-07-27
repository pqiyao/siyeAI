<template>
	<view
		class="wrap"
		:class="[localeFontClass, {
			focused: inputFocus,
			'wrap--with-bg': hasChatBackground,
			'wrap--app-plus': isAppPlus,
			'wrap--appearance-custom': chatBubbleAppearanceEnabled,
			'wrap--segment-labels': chatAppearanceSegmentLabelsEnabled,
			'wrap--read-novel': chatAppearanceReadMode === 'novel',
			'wrap--read-speech-only': chatAppearanceReadMode === 'speechOnly',
			'wrap--read-hide-thought': chatAppearanceReadMode === 'hideThought',
			'wrap--read-soft-action': chatAppearanceReadMode === 'softAction'
		}]"
		:style="wrapStyle"
	>
		<image class="chat-default-bg" :src="defaultChatBackgroundUrl" mode="aspectFill"></image>
		<image v-if="hasCustomChatBackground" class="chat-role-bg" :src="chatBackgroundUrl" mode="aspectFill"></image>
		<view v-if="chatBubbleAppearanceEnabled && normalizedChatAppearanceConfig.backdropStrength > 0" class="chat-readable-overlay" :style="chatReadableOverlayStyle"></view>
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
					<view class="nav-appearance-config" title="聊天显示" @tap="goAppearanceSetting">
						<u-icon name="setting" size="34" color="#ffffff"></u-icon>
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
			<view
				v-if="jgOn && jgChatLoadState === 'ready' && chatModelCatalog.enabled"
				class="chat-model-bar"
				:class="{ 'chat-model-bar--disabled': sending || chatModelCatalog.loading }"
				@tap="openChatModelPicker"
			>
				<view class="chat-model-bar__source">{{ currentChatModelSourceLabel }}</view>
				<text class="chat-model-bar__name">{{ currentChatModelName }}</text>
				<text v-if="currentChatModelPrice" class="chat-model-bar__price">{{ currentChatModelPrice }}</text>
				<u-icon name="arrow-down" size="22" color="#dbe7ef"></u-icon>
			</view>
			<view v-if="jgOn" class="tool-bar">
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onRegen">{{ chatUi.regen }}</text>
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onContinue">{{ chatUi.continue }}</text>
				<text class="tool-i" @tap="onRestart">{{ chatUi.restart }}</text>
				<text class="tool-i" @tap="onMem">{{ chatUi.memory }}</text>
				<text class="tool-i" :class="{ 'tool-i--active': branchPanel.visible }" @tap="openBranchPanel">{{ tx('branch_panel_title', '分支') }}</text>
				<text
					v-if="canShowReplyHelpTrigger()"
					class="tool-i tool-i--reply"
					:class="{ 'tool-i--active': replySuggest.visible }"
					@tap="toggleReplySuggestions"
				>{{ tx('reply_help_title', 'AI帮答') }}</text>
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
					class="chat-message-row"
					:class="[
						m.role === 'user' ? 'chat-message-row--user' : 'chat-message-row--assistant',
						{
							'chat-message-row--read-empty': !messageVisibleInReadMode(m)
						}
					]"
				>
					<image
						v-if="m.role !== 'user'"
						class="chat-message-avatar chat-message-avatar--character"
						:src="m.speakerAvatarUrl || charAvatar"
						mode="aspectFill"
						lazy-load
						@tap.stop="openCharImagePreview"
						@longpress.stop="applyCharacterNameToDraft"
					></image>
					<message-bubble
						:bubble-style="messageBubbleInlineStyle(m)"
						:bubble-class="messageBubbleClass(m)"
					>
						<text v-if="m.role !== 'user' && m.speakerName" class="chat-message-speaker">{{ m.speakerName }}</text>
						<message-content
							:image-urls="m.imageUrls"
							:local-kind="m.localKind"
							:local-prompt="m.localPrompt"
							:quote-meta="messageQuoteMeta(m)"
							:is-user="m.role === 'user'"
							:has-text="!!String(m.text || '').trim()"
							@preview-image="previewChatMessageImages(m, $event)"
						>
						<!-- #ifdef H5 -->
						<view
							v-if="shouldRenderSplitBubbles(m)"
							class="chat-message-split-list"
						>
							<view
								v-for="(chunk, chunkIndex) in assistantBubbleChunks(m)"
								:key="String(m.id) + '-split-' + chunkIndex"
								class="chat-message-split-part"
								:style="assistantSplitBubbleStyle(m, chunkIndex)"
							>
								<view
									class="chat-message-markdown"
									:style="assistantMessageTextStyle(m)"
									v-html="mdHtml(chunk)"
									@tap="onMarkdownTap"
									@touchstart="startMessageActionPress(m, $event)"
									@touchmove="moveMessageActionPress($event)"
									@touchend="endMessageActionPress"
									@touchcancel="endMessageActionPress"
								></view>
							</view>
						</view>
						<view
							v-else-if="isAssistantMessage(m)"
							class="chat-message-markdown"
							:style="assistantMessageTextStyle(m)"
							v-html="mdHtml(m.text)"
							@tap="onMarkdownTap"
							@touchstart="startMessageActionPress(m, $event)"
							@touchmove="moveMessageActionPress($event)"
							@touchend="endMessageActionPress"
							@touchcancel="endMessageActionPress"
						></view>
						<template v-else>
							<voice-message-card
								v-if="shouldShowUserVoiceCard(m)"
								:message-id="m.id"
								:card-class="userVoiceCardClass(m)"
								:duration-label="userVoiceDurationLabel(m)"
								:transcript-text="userVoiceTranscriptText(m)"
								:can-edit="canEditUserMessage(m)"
								:edit-label="chatUi.edit"
								@toggle="toggleUserVoice(m)"
								@edit="openEditUserMessage(m)"
								@press-start="startMessageActionPress(m, $event)"
								@press-move="moveMessageActionPress($event)"
								@press-end="endMessageActionPress"
								@press-cancel="endMessageActionPress"
							></voice-message-card>
							<template v-else>
								<text
									class="chat-message-user-text"
									:style="userMessageTextStyle(m)"
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
							v-if="shouldRenderSplitBubbles(m)"
							class="chat-message-split-list"
						>
							<view
								v-for="(chunk, chunkIndex) in assistantBubbleChunks(m)"
								:key="String(m.id) + '-split-' + chunkIndex"
								class="chat-message-split-part"
								:style="assistantSplitBubbleStyle(m, chunkIndex)"
								@touchstart="startMessageActionPress(m, $event)"
								@touchmove="moveMessageActionPress($event)"
								@touchend="endMessageActionPress"
								@touchcancel="endMessageActionPress"
							>
								<view :class="nativeMessageContentClass()">
									<view
										v-for="(seg, si) in mdSegments(chunk)"
										:key="si"
										:class="nativeSegmentClass(seg)"
										:style="nativeSegmentWrapStyle(seg)"
									>
										<view :class="nativeSegmentLineClass()" :style="nativeSegmentLineStyle()">
											<view
												v-if="chatAppearanceSegmentLabelsEnabled"
												:class="nativeSegmentLabelClass(seg)"
												:style="nativeSegmentLabelStyle(seg)"
											>
												<text :class="nativeSegmentLabelTextClass()" :style="nativeSegmentLabelTextStyle(seg)">{{ nativeSegmentLabelText(seg) }}</text>
											</view>
											<text :class="nativeSegmentTextClass(seg)" :style="nativeSegmentTextStyle(seg)">{{ seg.text }}</text>
										</view>
									</view>
								</view>
							</view>
						</view>
						<view
							v-else-if="isAssistantMessage(m)"
							:class="nativeMessageContentClass()"
							@touchstart="startMessageActionPress(m, $event)"
							@touchmove="moveMessageActionPress($event)"
							@touchend="endMessageActionPress"
							@touchcancel="endMessageActionPress"
						>
							<view
								v-for="(seg, si) in mdSegments(m.text)"
								:key="si"
								:class="nativeSegmentClass(seg)"
								:style="nativeSegmentWrapStyle(seg)"
							>
								<view :class="nativeSegmentLineClass()" :style="nativeSegmentLineStyle()">
									<view
										v-if="chatAppearanceSegmentLabelsEnabled"
										:class="nativeSegmentLabelClass(seg)"
										:style="nativeSegmentLabelStyle(seg)"
									>
										<text :class="nativeSegmentLabelTextClass()" :style="nativeSegmentLabelTextStyle(seg)">{{ nativeSegmentLabelText(seg) }}</text>
									</view>
									<text
										v-if="seg.type === 'speech'"
										:class="nativeSegmentTextClass(seg)"
										:style="nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else-if="seg.type === 'action'"
										:class="nativeSegmentTextClass(seg)"
										:style="nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else-if="seg.type === 'thought'"
										:class="nativeSegmentTextClass(seg)"
										:style="nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
									<text
										v-else
										:class="nativeSegmentTextClass(seg)"
										:style="nativeSegmentTextStyle(seg)"
									>{{ seg.text }}</text>
								</view>
							</view>
						</view>
						<view v-else>
							<voice-message-card
								v-if="shouldShowUserVoiceCard(m)"
								:message-id="m.id"
								:card-class="userVoiceCardClass(m)"
								:duration-label="userVoiceDurationLabel(m)"
								:transcript-text="userVoiceTranscriptText(m)"
								:can-edit="canEditUserMessage(m)"
								:edit-label="chatUi.edit"
								@toggle="toggleUserVoice(m)"
								@edit="openEditUserMessage(m)"
								@press-start="startMessageActionPress(m, $event)"
								@press-move="moveMessageActionPress($event)"
								@press-end="endMessageActionPress"
								@press-cancel="endMessageActionPress"
							></voice-message-card>
							<template v-else>
								<text
									:class="nativeUserMessageTextClass()"
									:style="userMessageTextStyle(m)"
									@touchstart="startMessageActionPress(m, $event)"
									@touchmove="moveMessageActionPress($event)"
									@touchend="endMessageActionPress"
									@touchcancel="endMessageActionPress"
								>{{ m.text }}</text>
								<text v-if="canEditUserMessage(m)" class="user-edit-tag" @tap.stop="openEditUserMessage(m)">{{ chatUi.edit }}</text>
							</template>
						</view>
						<!-- #endif -->
						<message-actions
							v-if="messageActionsVisible(m)"
							:message-id="m.id"
							:is-app-plus="isAppPlus"
							:streaming="isStreamingAssistantRow(m)"
							:streaming-status-text="streamingAssistantStatusText(m)"
							:show-assistant-voice="shouldShowAssistantVoicePill(m)"
							:assistant-voice-label="assistantVoiceLabel(m)"
							:assistant-voice-pill-class="assistantVoicePillClass(m)"
							:show-swipe-controls="isAssistantMessage(m) && !m.openingMessage && m.swipes && m.swipes.length > 1"
							:swipe-label="swipeLabel(m)"
							:recovery="recoveryForMessage(m)"
							:recovery-primary-label="recoveryPrimaryLabel()"
							:recovery-regen-label="tx('regen', '重新生成')"
							:recovery-copy-label="tx('copy', '复制')"
							:can-copy-recovery="!!String(m.text || '').trim()"
							@assistant-voice-toggle="toggleAssistantVoice(m)"
							@swipe-previous="swipeCharMessage(m, -1)"
							@swipe-next="swipeCharMessage(m, 1)"
							@recovery-primary="runGenerationRecoveryPrimary"
							@recovery-regen="runGenerationRecoveryRegen"
							@recovery-copy="copyGenerationRecoveryText(m)"
							@recovery-close="clearGenerationRecovery"
						></message-actions>
						</message-content>
					</message-bubble>
					<image
						v-if="m.role === 'user'"
						class="chat-message-avatar"
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
					:class="{ 'message-action-item--disabled': messageActionSheet.deleting || messageActionSheet.forking }"
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
		<view v-if="branchPanel.visible" class="branch-mask" @tap="closeBranchPanel">
			<view class="branch-sheet" @tap.stop>
				<view class="branch-head">
					<view class="branch-head-copy">
						<text class="branch-title">故事管理</text>
						<text class="branch-sub">{{ branchPanel.loading ? tx('loading', '加载中...') : (branchPanel.mode === 'sessions' ? '管理这个角色的独立故事' : (branchPanel.mode === 'openings' ? '选择故事的开场' : '切换当前剧情路线')) }}</text>
					</view>
					<view
						class="branch-close"
						:title="tx('close', '关闭')"
						:aria-label="tx('close', '关闭')"
						@tap="closeBranchPanel"
					>
						<u-icon name="close" color="#64748b" size="30"></u-icon>
					</view>
				</view>
				<view class="branch-tabs">
					<view class="branch-tab" :class="{ 'branch-tab--active': branchPanel.mode === 'sessions' }" @tap="branchPanel.mode = 'sessions'">会话</view>
					<view class="branch-tab" :class="{ 'branch-tab--active': branchPanel.mode === 'openings' }" @tap="branchPanel.mode = 'openings'">开场</view>
					<view class="branch-tab" :class="{ 'branch-tab--active': branchPanel.mode === 'branches' }" @tap="branchPanel.mode = 'branches'">剧情分支</view>
				</view>
				<view v-if="branchPanel.mode === 'sessions'" class="branch-create" @tap="createStorySession">
					<u-icon name="plus" color="#245b73" size="28"></u-icon><text>新建故事</text>
				</view>
				<scroll-view class="branch-scroll" scroll-y :show-scrollbar="false">
					<view v-if="branchPanel.loading" class="branch-empty">
						<text class="branch-empty-title">{{ tx('loading', '加载中...') }}</text>
					</view>
					<view v-else-if="branchPanel.error" class="branch-empty">
						<text class="branch-empty-title">{{ tx('branch_load_failed', '分支加载失败') }}</text>
						<text class="branch-empty-sub" @tap.stop="loadBranchPanel">{{ branchPanel.error }}</text>
					</view>
					<view v-else-if="!branchPanelRows.length" class="branch-empty">
						<text class="branch-empty-title">{{ branchPanel.mode === 'sessions' ? '暂无独立会话' : (branchPanel.mode === 'openings' ? '暂无开场场景' : '暂无剧情分支') }}</text>
						<text class="branch-empty-sub">{{ branchPanel.mode === 'sessions' ? '点击上方新建故事' : (branchPanel.mode === 'openings' ? '可以在角色卡编辑器中添加开场' : '长按任意消息即可从那里创建新路线') }}</text>
					</view>
					<view
						v-for="row in branchPanelRows"
						:key="'branch_row_' + row.id"
						class="branch-row"
						:class="{
							'branch-row--active': row.active,
							'branch-row--switching': branchPanel.switchingBranchId === row.id || branchPanel.deletingBranchId === row.id
						}"
						:style="row.kind === 'branch' && row.depth ? { paddingLeft: (24 + row.depth * 24) + 'rpx' } : null"
						@tap.stop="switchBranch(row)"
					>
						<view class="branch-row-head">
							<view class="branch-row-title-wrap">
								<view class="branch-row-title-line">
									<u-icon v-if="row.kind === 'branch' && row.depth" name="arrow-right" color="#94a3b8" size="22"></u-icon>
									<text class="branch-row-title">{{ row.title }}</text>
								</view>
								<text class="branch-row-preview">{{ row.preview || tx('branch_no_preview', '暂无消息') }}</text>
							</view>
							<view class="branch-row-side">
								<text class="branch-row-current">{{ row.active ? tx('branch_current', '当前') : (row.created ? tx('branch_continue', '继续') : tx('branch_start', '开始')) }}</text>
								<view v-if="row.kind === 'branch' || row.kind === 'session'" class="branch-row-actions">
									<u-icon name="edit-pen" color="#64748b" size="28" @tap.stop="openBranchRename(row)"></u-icon>
									<u-icon v-if="row.kind === 'branch' && !row.defaultBranch" name="trash" color="#b45353" size="28" @tap.stop="deleteManagedBranch(row)"></u-icon>
									<u-icon v-if="row.kind === 'session' && !row.active" name="trash" color="#b45353" size="28" @tap.stop="deleteStorySession(row)"></u-icon>
								</view>
							</view>
						</view>
						<view class="branch-row-meta">
							<text v-if="row.created" class="branch-row-meta-text">{{ tx('branch_message_count', '{n} 条消息').replace('{n}', String(row.messageCount || 0)) }}</text>
							<text v-else class="branch-row-meta-text">{{ tx('branch_new_story', '新剧情') }}</text>
						</view>
					</view>
				</scroll-view>
			</view>
			<view v-if="branchPanel.editorVisible" class="branch-editor-mask" @tap.stop="closeBranchRename">
				<view class="branch-editor" @tap.stop>
					<text class="branch-editor-title">重命名</text>
					<input
						class="branch-editor-input"
						v-model="branchPanel.editorTitle"
						maxlength="80"
						:disabled="branchPanel.editing"
						placeholder="输入名称"
						confirm-type="done"
						@confirm="saveBranchRename"
					/>
					<view class="branch-editor-actions">
						<text class="branch-editor-button" @tap="closeBranchRename">取消</text>
						<text class="branch-editor-button branch-editor-button--primary" @tap="saveBranchRename">保存</text>
					</view>
				</view>
			</view>
		</view>
		<view v-if="memoryPanel.visible" class="memory-mask" @tap="closeMemoryPanel">
			<view class="memory-sheet" @tap.stop>
				<view class="memory-head">
					<view class="memory-head-copy">
						<text class="memory-title">{{ tx('memory_panel_title', '长期记忆') }}</text>
						<text class="memory-sub">{{ memoryStatusLabel(memoryPanel.detail && memoryPanel.detail.syncStatus) }}</text>
					</view>
					<view
						class="memory-close"
						:title="tx('close', '关闭')"
						:aria-label="tx('close', '关闭')"
						@tap="closeMemoryPanel"
					>
						<u-icon name="close" color="#64748b" size="30"></u-icon>
					</view>
				</view>
				<view class="memory-stats">
					<view class="memory-stat">
						<text class="memory-stat-label">{{ tx('memory_enabled', '启用') }}</text>
						<text class="memory-stat-value">{{ memoryPanel.detail && memoryPanel.detail.enabledEntryCount != null ? memoryPanel.detail.enabledEntryCount : 0 }}</text>
					</view>
					<view class="memory-stat">
						<text class="memory-stat-label">{{ tx('memory_total', '总数') }}</text>
						<text class="memory-stat-value">{{ memoryPanelTotalCount }}</text>
					</view>
					<view class="memory-stat">
						<text class="memory-stat-label">{{ tx('memory_facts', '事实') }}</text>
						<text class="memory-stat-value">{{ memoryPanel.detail && memoryPanel.detail.factsCount != null ? memoryPanel.detail.factsCount : 0 }}</text>
					</view>
				</view>
				<text v-if="memoryPanel.detail && memoryPanel.detail.summaryPreview" class="memory-summary">
					{{ memoryPanel.detail.summaryPreview }}
				</text>
				<view class="memory-actions">
					<text class="memory-guide">{{ memoryPanelGuideText() }}</text>
					<view class="memory-action-row">
						<view
							class="memory-icon-button memory-icon-button--add"
							:class="{ 'memory-icon-button--disabled': memoryPanelBusy }"
							:title="tx('memory_add', '新增记忆')"
							:aria-label="tx('memory_add', '新增记忆')"
							@tap="openMemoryEditor()"
						>
							<u-icon name="plus" color="#245b73" size="30"></u-icon>
						</view>
						<view
							v-if="memoryPanel.detail && String(memoryPanel.detail.syncStatus || '').toUpperCase() === 'FAILED'"
							class="memory-icon-button memory-icon-button--secondary"
							:class="{ 'memory-icon-button--disabled': memoryPanelBusy }"
							:title="tx('memory_sync_retry', '重试同步')"
							:aria-label="tx('memory_sync_retry', '重试同步')"
							@tap="retryMemorySync"
						>
							<u-icon name="server-fill" color="#35556a" size="30" :class="{ 'memory-icon--spin': memoryPanel.syncing }"></u-icon>
						</view>
						<view
							class="memory-icon-button memory-icon-button--primary"
							:class="{ 'memory-icon-button--disabled': memoryPanelBusy }"
							:title="tx('memory_refresh', '重新整理')"
							:aria-label="tx('memory_refresh', '重新整理')"
							@tap="refreshMemoryPanel"
						>
							<u-icon name="reload" color="#ffffff" size="30" :class="{ 'memory-icon--spin': memoryPanel.refreshing }"></u-icon>
						</view>
					</view>
				</view>
				<text v-if="memoryPanel.error" class="memory-error">{{ memoryPanel.error }}</text>
				<text v-else-if="memoryPanel.detail && memoryPanel.detail.syncError" class="memory-error">
					{{ tx('memory_sync_error_prefix', '记忆同步异常：') }}{{ memoryPanel.detail.syncError }}
				</text>
				<view class="memory-filters">
					<view
						v-for="filter in memoryPanelFilterOptions"
						:key="'memory_filter_' + filter.key"
						class="memory-filter"
						:class="{ 'memory-filter--active': memoryPanel.filter === filter.key }"
						:title="filter.label"
						:aria-label="filter.label"
						@tap="setMemoryPanelFilter(filter.key)"
					>
						<u-icon :name="filter.icon" :color="memoryPanel.filter === filter.key ? '#ffffff' : '#5f7182'" size="25"></u-icon>
						<text class="memory-filter-label">{{ filter.label }}</text>
						<text class="memory-filter-count">{{ filter.count }}</text>
					</view>
				</view>
				<scroll-view
					class="memory-scroll"
					scroll-y
					:show-scrollbar="false"
					:lower-threshold="80"
					@scrolltolower="loadMoreMemoryPanel"
				>
					<view v-if="memoryPanel.loading && !memoryPanel.entries.length" class="memory-empty">
						<text class="memory-empty-title">{{ tx('loading', '加载中...') }}</text>
					</view>
					<view v-else-if="!memoryPanel.entries.length" class="memory-empty">
						<text class="memory-empty-title">{{ memoryPanelFilterEmptyTitle }}</text>
						<text class="memory-empty-sub">{{ memoryPanelFilterEmptyText }}</text>
					</view>
					<view v-if="memoryPanel.entries.length" class="memory-list">
						<view
							v-for="entry in memoryPanel.entries"
							:key="'memory_entry_' + memoryPanelEntryIdentity(entry)"
							class="memory-entry"
							:class="{
								'memory-entry--disabled': !entry.enabled && !entry.archived,
								'memory-entry--archived': entry.archived
							}"
						>
							<view class="memory-entry-head">
								<view class="memory-entry-title-wrap">
									<text class="memory-entry-type">{{ memoryTypeLabel(entry.memoryType) }}</text>
									<text class="memory-entry-title">{{ entry.title || tx('memory_untitled', '记忆要点') }}</text>
								</view>
								<text
									class="memory-entry-status"
									:class="{
										'memory-entry-status--disabled': !entry.enabled && !entry.archived,
										'memory-entry-status--archived': entry.archived
									}"
								>
									{{ entry.archived ? tx('memory_archived', '已归档') : (entry.enabled ? tx('enabled', '启用') : (entry.manualDisabled ? tx('memory_user_disabled', '用户停用') : tx('disabled', '已停用'))) }}
								</text>
							</view>
							<text class="memory-entry-content">{{ entry.content }}</text>
							<text v-if="entry.archived" class="memory-entry-archive-reason">{{ memoryArchiveReasonText(entry) }}</text>
							<view v-if="entry.keywords.length || entry.secondaryKeywords.length" class="memory-keywords">
								<text
									v-for="(keyword, idx) in entry.keywords"
									:key="'memory_kw_' + entry.id + '_' + idx"
									class="memory-keyword"
								>{{ keyword }}</text>
								<text
									v-for="(keyword, idx) in entry.secondaryKeywords"
									:key="'memory_skw_' + entry.id + '_' + idx"
									class="memory-keyword memory-keyword--secondary"
								>{{ keyword }}</text>
							</view>
							<view class="memory-entry-foot">
								<text v-if="memoryEntryMetaText(entry)" class="memory-entry-meta">{{ memoryEntryMetaText(entry) }}</text>
										<view v-if="entry.id" class="memory-entry-buttons">
											<view
												class="memory-entry-icon memory-entry-icon--edit"
												:class="{ 'memory-entry-icon--busy': memoryPanelBusy }"
												:title="tx('edit', '编辑')"
												:aria-label="tx('edit', '编辑')"
												@tap.stop="openMemoryEditor(entry)"
											>
												<u-icon name="edit-pen" color="#245b73" size="27"></u-icon>
											</view>
									<view
										class="memory-entry-icon"
										:class="{
											'memory-entry-icon--disable': entry.enabled,
											'memory-entry-icon--enable': !entry.enabled,
											'memory-entry-icon--busy': memoryPanel.updatingEntryId === String(entry.id || '')
										}"
										:title="entry.enabled ? tx('disable', '停用') : tx('enable', '重新启用')"
										:aria-label="entry.enabled ? tx('disable', '停用') : tx('enable', '重新启用')"
										@tap.stop="toggleMemoryPanelEntry(entry)"
									>
										<u-icon
											:name="memoryPanel.updatingEntryId === String(entry.id || '') ? 'reload' : (entry.enabled ? 'eye-off' : 'checkmark-circle')"
											:color="entry.enabled ? '#b42318' : '#177245'"
											size="27"
											:class="{ 'memory-icon--spin': memoryPanel.updatingEntryId === String(entry.id || '') }"
										></u-icon>
									</view>
									<view
										class="memory-entry-icon memory-entry-icon--delete"
										:class="{ 'memory-entry-icon--busy': memoryPanel.deletingEntryId === String(entry.id || '') }"
										:title="tx('delete', '删除')"
										:aria-label="tx('delete', '删除')"
										@tap.stop="deleteMemoryPanelEntry(entry)"
									>
										<u-icon
											:name="memoryPanel.deletingEntryId === String(entry.id || '') ? 'reload' : 'trash'"
											color="#9f2d28"
											size="27"
											:class="{ 'memory-icon--spin': memoryPanel.deletingEntryId === String(entry.id || '') }"
										></u-icon>
									</view>
								</view>
							</view>
						</view>
						<view v-if="memoryPanel.loadingMore" class="memory-page-state">
							<u-icon name="reload" color="#5f7182" size="24" class="memory-icon--spin"></u-icon>
							<text>{{ tx('memory_loading_more', '正在加载更多记忆') }}</text>
						</view>
						<view v-else-if="memoryPanel.loadMoreError" class="memory-page-state memory-page-state--error" @tap="loadMoreMemoryPanel">
							<u-icon name="reload" color="#b42318" size="24"></u-icon>
							<text>{{ memoryPanel.loadMoreError }}</text>
						</view>
						<view v-else-if="memoryPanel.entries.length && !memoryPanel.hasMore" class="memory-page-state">
							<text>{{ tx('memory_loaded_all', '已加载当前分类的全部记忆') }}</text>
						</view>
					</view>
				</scroll-view>
				<view v-if="memoryPanel.editorVisible" class="memory-editor-mask" @tap="closeMemoryEditor">
					<scroll-view class="memory-editor" scroll-y :show-scrollbar="false" @tap.stop>
						<view class="memory-editor-head">
							<text class="memory-editor-title">{{ memoryPanel.editorEntryId ? '编辑记忆' : '新增记忆' }}</text>
							<view class="memory-close" @tap="closeMemoryEditor"><u-icon name="close" color="#64748b" size="28"></u-icon></view>
						</view>
						<text class="memory-field-label">类型</text>
						<picker :range="memoryEditorTypeOptions" range-key="label" :value="memoryEditorTypeIndex" @change="changeMemoryEditorType">
							<view class="memory-picker">{{ memoryTypeLabel(memoryPanel.editor.memoryType) }}<u-icon name="arrow-down" color="#64748b" size="22"></u-icon></view>
						</picker>
						<text class="memory-field-label">标题（可选）</text>
						<input v-model="memoryPanel.editor.title" class="memory-editor-input" maxlength="120" placeholder="例如：与角色的约定" />
						<text class="memory-field-label">记忆内容</text>
						<textarea v-model="memoryPanel.editor.content" class="memory-editor-textarea" maxlength="1200" placeholder="写下需要角色长期记住的事实" />
						<text class="memory-field-label">触发关键词</text>
						<input v-model="memoryPanel.editor.keywordsText" class="memory-editor-input" maxlength="240" placeholder="用逗号分隔，例如：戒指，婚约" />
						<text class="memory-field-label">辅助关键词（可选）</text>
						<input v-model="memoryPanel.editor.secondaryKeywordsText" class="memory-editor-input" maxlength="240" placeholder="组合触发时使用" />
						<view class="memory-priority-row">
							<view><text class="memory-field-label memory-field-label--inline">优先级</text><text class="memory-field-help">数值越高越优先，40–200</text></view>
							<input v-model="memoryPanel.editor.priority" class="memory-priority-input" type="number" maxlength="3" />
						</view>
						<view class="memory-switch-row">
							<view><text class="memory-switch-title">保护记忆</text><text class="memory-field-help">容量整理时不自动淘汰</text></view>
							<switch :checked="memoryPanel.editor.manualPinned" color="#4f93a3" @change="memoryPanel.editor.manualPinned = $event.detail.value" />
						</view>
						<view class="memory-switch-row" :class="{ 'memory-switch-row--disabled': !memoryEditorCanConstant }">
							<view><text class="memory-switch-title">固定注入</text><text class="memory-field-help">每轮都参与回复，仅身份、关系、设定、边界可用</text></view>
							<switch :checked="memoryPanel.editor.constantInjection" :disabled="!memoryEditorCanConstant" color="#4f93a3" @change="memoryPanel.editor.constantInjection = $event.detail.value" />
						</view>
						<text v-if="memoryPanel.editorError" class="memory-error">{{ memoryPanel.editorError }}</text>
						<view class="memory-editor-actions">
							<text class="memory-editor-button" @tap="closeMemoryEditor">取消</text>
							<text class="memory-editor-button memory-editor-button--primary" :class="{ 'memory-icon-button--disabled': memoryPanel.savingEntry }" @tap="saveMemoryEditor">{{ memoryPanel.savingEntry ? '保存中' : '保存' }}</text>
						</view>
					</scroll-view>
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
							v-if="canManageCharacterUserVoices()"
							class="character-voice-global-action"
							@tap="goCharacterUserVoices"
						>
							{{ tx('manage_private_voices', '绑定我的音色') }}
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
				<view v-if="characterVoiceGlobalState.mode === 'custom'" class="character-voice-field" :class="{ 'character-voice-field--disabled': !characterVoicePanel.enabled }">
					<text class="character-voice-label">{{ tx('character_voice_model', '角色级 TTS 模型覆盖') }}</text>
					<input
						class="character-voice-input"
						v-model="characterVoicePanel.ttsModelName"
						:disabled="!characterVoicePanel.enabled || characterVoicePanel.saving"
						:placeholder="tx('character_voice_model_placeholder', '留空则跟随全局 TTS 模型')"
						confirm-type="done"
					/>
				</view>
				<view v-if="characterVoiceGlobalState.mode === 'custom'" class="character-voice-field" :class="{ 'character-voice-field--disabled': !characterVoicePanel.enabled }">
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
					<text v-if="item.error" class="composer-image-retry" @tap.stop="retryComposerImage(item)">
						{{ tx('retry', '重试') }}
					</text>
				</view>
				<text class="composer-image-remove" @tap.stop="removeComposerImage(item.id)">×</text>
			</view>
			<text class="composer-image-hint">{{ tx('chat_image_compressed_hint', '已压缩处理 · 最多 4 张') }}</text>
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
		<view v-if="chatModelPicker.visible" class="chat-model-picker-mask" @tap="closeChatModelPicker" @touchmove.stop.prevent>
			<view class="chat-model-picker" @tap.stop>
				<view class="chat-model-picker__handle"></view>
				<view class="chat-model-picker__head">
					<view>
						<text class="chat-model-picker__title">选择聊天模型</text>
						<text class="chat-model-picker__subtitle">本次选择只改变模型，不改变角色卡、世界书和记忆</text>
					</view>
					<text class="chat-model-picker__close" @tap="closeChatModelPicker">×</text>
				</view>
				<view class="chat-model-picker__tabs">
					<view
						class="chat-model-picker__tab"
						:class="{ 'chat-model-picker__tab--active': chatModelPicker.tab === 'SYSTEM' }"
						@tap="setChatModelPickerTab('SYSTEM')"
					>平台模型 {{ chatModelCatalog.platformModels.length }}</view>
					<view
						class="chat-model-picker__tab"
						:class="{ 'chat-model-picker__tab--active': chatModelPicker.tab === 'BYOK' }"
						@tap="setChatModelPickerTab('BYOK')"
					>我的 API {{ chatModelCatalog.byokModels.length }}</view>
				</view>
				<view class="chat-model-picker__search">
					<u-icon name="search" size="28" color="#6b7f8d"></u-icon>
					<input v-model="chatModelPicker.search" maxlength="80" placeholder="搜索模型名称或特点" />
					<text v-if="chatModelPicker.search" @tap="chatModelPicker.search = ''">清除</text>
				</view>
				<scroll-view class="chat-model-picker__list" scroll-y :show-scrollbar="false">
					<view
						v-for="item in visibleChatModelItems"
						:key="item._source + '_' + item._ref"
						class="chat-model-option"
						:class="{
							'chat-model-option--active': isCurrentChatModel(item),
							'chat-model-option--disabled': item.available === false,
							'chat-model-option--busy': chatModelPicker.selectingRef === item._ref
						}"
						@tap="selectChatModelItem(item)"
					>
						<view class="chat-model-option__top">
							<view class="chat-model-option__identity">
								<text class="chat-model-option__name">{{ item.displayName || item.modelName }}</text>
								<text v-if="item.badge" class="chat-model-option__badge">{{ item.badge }}</text>
								<text v-if="isCurrentChatModel(item)" class="chat-model-option__selected">当前</text>
							</view>
							<text class="chat-model-option__price">{{ chatModelItemPrice(item) }}</text>
						</view>
						<text v-if="item.shortDescription" class="chat-model-option__desc">{{ item.shortDescription }}</text>
						<text v-else-if="item._source === 'BYOK'" class="chat-model-option__desc chat-model-option__desc--mono">{{ item.modelName }}</text>
						<view v-if="item._source === 'SYSTEM'" class="chat-model-option__meta">
							<text>{{ chatModelLevelText('质量', item.qualityLevel) }}</text>
							<text>{{ chatModelLevelText('速度', item.speedLevel) }}</text>
							<text v-if="item.contextLabel">{{ item.contextLabel }}</text>
							<text v-for="tag in (item.tags || []).slice(0, 3)" :key="item._ref + '_' + tag">{{ tag }}</text>
						</view>
						<text v-if="item.available === false" class="chat-model-option__reason">{{ item.unavailableReason || '暂不可用' }}</text>
					</view>
					<view v-if="!visibleChatModelItems.length" class="chat-model-picker__empty">
						<text>{{ chatModelPicker.search ? '没有找到匹配模型' : (chatModelPicker.tab === 'BYOK' ? '还没有保存自定义聊天模型' : '平台暂未发布可选模型') }}</text>
						<text v-if="chatModelPicker.tab === 'BYOK' && !chatModelPicker.search" class="chat-model-picker__settings" @tap="goAiSettings">前往 AI 设置</text>
					</view>
				</scroll-view>
				<view v-if="chatModelPicker.tab === 'SYSTEM'" class="chat-model-picker__wallet">
					<text>余额</text>
					<text>{{ chatModelCatalog.wallet.diamonds || 0 }} 钻石</text>
					<text>{{ chatModelCatalog.wallet.gold || 0 }} 金币</text>
				</view>
			</view>
		</view>
		<chat-composer
			v-model="draft"
			:attachment-menu-visible="attachmentMenuVisible"
			:show-voice-action="voiceFeatureEnabledGlobal !== false"
			:voice-action-active="voiceRecording || voiceTranscribing || pendingVoiceStartTimer"
			:voice-action-label="tx('voice_input', '语音输入')"
			:show-image-generation-action="imageGenerationEnabledGlobal !== false && characterImageGlobalState.imageEnabledGlobal !== false"
			:image-generation-active="characterImagePanel.visible"
			:image-generation-action-label="tx('chat_image_generate', '聊天生图')"
			:camera-action-label="tx('camera', '相机')"
			:album-action-label="tx('album', '相册')"
			:at-chat-bottom="atChatBottom"
			:quote="composerQuote"
			:draft-restored-notice-visible="draftRestoredNoticeVisible"
			:draft-restored-text="tx('draft_restored', '已恢复上次未发送内容')"
			:clear-text="tx('clear', '清空')"
			:placeholder="tx('input_message', '输入消息...')"
			:scroll-bottom-text="chatScrollBottomText"
			:cursor-spacing="isAppPlus ? 96 : 18"
			:disabled="sending || jgIdentityReloading || jgChatLoadState !== 'ready'"
			:attachment-voice-icon="attachmentVoiceIcon"
			:attachment-image-icon="attachmentImageIcon"
			:attachment-camera-icon="attachmentCameraIcon"
			:attachment-album-icon="attachmentAlbumIcon"
			:input-expression-icon="inputExpressionIcon"
			:input-plus-icon="inputPlusIcon"
			:send-up-icon="sendUpIcon"
			@toggle-voice-input="toggleVoiceInput"
			@open-character-image-panel="openCharacterImagePanel"
			@pick-camera="pickChatImages('camera')"
			@pick-album="pickChatImages('album')"
			@clear-restored-draft="clearRestoredDraft"
			@dismiss-draft-restored-notice="dismissDraftRestoredNotice"
			@clear-composer-quote="clearComposerQuote"
			@open-expression-panel="openExpressionPanel"
			@open-attachment-menu="openChatAttachmentMenu"
			@scroll-bottom="scrollChatToBottom({ immediate: true })"
			@primary-action="onPrimaryAction"
			@focus="onInputFocus"
			@blur="onInputBlur"
			@confirm="send"
		></chat-composer>
		</template>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="inputFocus ? 150 : 92" :compact="inputFocus" />
		<!-- #endif -->
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
	import MessageBubble from '@/components/tavern/message-bubble.vue';
	import MessageContent from '@/components/tavern/message-content.vue';
	import MessageActions from '@/components/tavern/message-actions.vue';
	import VoiceMessageCard from '@/components/tavern/voice-message-card.vue';
	import ChatComposer from '@/components/tavern/chat-composer.vue';
	const { getTavernUiText, formatLocaleText } = require('@/common/tavernUiI18n.js');
	const companionStore = require('@/common/companionStore.js');
	const chatAppearance = require('@/common/chatAppearance.js');
	const structuredContent = require('@/common/chatStructuredContent.js');
	const viewerIdentity = require('@/common/viewerIdentity.js');
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
	const LOCAL_CHAT_IMAGE_CACHE_PREFIX = 'tavern_local_chat_images_';
	const LOCAL_CHAT_IMAGE_CACHE_VERSION = 1;
	const LOCAL_CHAT_IMAGE_PENDING_KEEP_MS = 10 * 60 * 1000;
	const LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH = 1200 * 1024;
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
	const LOCAL_CHARACTER_VOICE_CONFIG_VERSION = 2;
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
	const ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH = 500;
	const ASSISTANT_VOICE_SEGMENT_HARD_MAX = 900;
	const ASSISTANT_VOICE_SEGMENT_SOFT_MIN = 260;
	const ASSISTANT_VOICE_SEGMENT_SHORT_LENGTH = 48;
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
			ttsProviderSource: '',
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
			userVoiceCreationEnabled: false,
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
			canFork: false,
			deleting: false,
			forking: false,
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

	function createBranchPanelState() {
		return {
			visible: false,
			loading: false,
			error: '',
			mode: 'sessions',
			sessions: [],
			activeConversationId: '',
			openings: [],
			branches: [],
			switchingBranchId: '',
			forkingMessageId: '',
			deletingBranchId: '',
			editorVisible: false,
			editorKind: '',
			editorId: '',
			editorTitle: '',
			editing: false
		};
	}

	const MEMORY_PANEL_PAGE_SIZE = 40;
	let memoryPanelRequestSeed = 0;
	let memoryPanelListRequestSeed = 0;

	function createMemoryPanelState() {
		return {
			visible: false,
			clientUid: '',
			characterId: '',
			conversationId: '',
			activeBranchId: '',
			loading: false,
			refreshing: false,
			syncing: false,
			updatingEntryId: '',
			deletingEntryId: '',
			savingEntry: false,
			editorVisible: false,
			editorEntryId: '',
			editorError: '',
			editor: {
				memoryType: 'event',
				title: '',
				content: '',
				keywordsText: '',
				secondaryKeywordsText: '',
				priority: '120',
				manualPinned: true,
				constantInjection: false
			},
			filter: 'all',
			page: 0,
			pageSize: MEMORY_PANEL_PAGE_SIZE,
			totalEntries: 0,
			hasMore: false,
			loadingMore: false,
			loadMoreError: '',
			requestToken: ++memoryPanelRequestSeed,
			listRequestToken: ++memoryPanelListRequestSeed,
			error: '',
			detail: null,
			entries: []
		};
	}

	export default {
		components: { TavernNavBar, MessageBubble, MessageContent, MessageActions, VoiceMessageCard, ChatComposer },
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
				sendingStartedAt: 0,
				jgOn: false,
				userAvatar: '',
				streamAbortController: null,
				streamingAssistantMessageId: '',
				streamingAssistantMode: '',
				stopRefreshTimer: null,
				stopSyncVersion: 0,
				followBottom: true,
				atChatBottom: true,
				chatUnreadCount: 0,
				chatUnreadMessageKeyMap: {},
				chatUserTouching: false,
				lastChatScrollTop: 0,
				chatAutoScrollAt: 0,
				chatProgrammaticScroll: false,
				chatProgrammaticScrollTimer: null,
				chatFollowScrollTimer: null,
				chatScrollWithAnimation: true,
				chatViewportReady: false,
				chatAnimationTimer: null,
				chatRevealTimer: null,
				editOverlay: createEditOverlayState(),
				messageActionSheet: createMessageActionSheetState(),
				messagePressState: createMessagePressState(),
				branchPanel: createBranchPanelState(),
				memoryPanel: createMemoryPanelState(),
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
				jgActiveBranchId: '',
				jgMemory: null,
				jgTavernMeta: null,
				memoryRefreshing: false,
				memoryRefreshStatusToken: 0,
				messageHistoryHasMore: false,
				messageHistoryLoading: false,
				messageHistoryNextBeforeId: '',
				messageHistoryLoadAt: 0,
				jgLoadRetryTimer: null,
				jgLoadAutoRetried: false,
				jgLoadRequestToken: 0,
				jgViewerIdentitySignature: '',
				jgIdentityReloading: false,
				jgRuntimeRequestVersion: 0,
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
				imageGenerationEnabledGlobal: true,
				visionAccessState: {
					loaded: false,
					loading: false,
					mode: 'system',
					canUse: false,
					denyReason: '',
					visionScoreCost: 0,
					visionGoldCost: 0
				},
				rechargeEntryVisible: true,
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
				assistantVoiceRestorePendingMap: {},
				localChatImageHydratePending: false,
				assistantVoiceAutoEnabled: false,
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
				isAppPlus: false,
				chatAppearanceConfig: chatAppearance.loadConfig(),
				chatAppearanceChangeHandler: null,
				chatAppearanceRequestGuard: null,
				chatAppearanceRequestVersion: 0,
				chatAppearanceDisposed: false,
				chatModelRequestVersion: 0,
				chatModelCatalog: {
					enabled: false,
					loading: false,
					platformModels: [],
					byokModels: [],
					current: null,
					wallet: { diamonds: 0, gold: 0 },
					message: ''
				},
				chatModelPicker: {
					visible: false,
					tab: 'SYSTEM',
					search: '',
					selectingRef: ''
				}
			};
		},
		computed: {
			chatUi() {
				return getTavernUiText('chat');
			},
			currentChatModel() {
				return this.chatModelCatalog && this.chatModelCatalog.current ? this.chatModelCatalog.current : {};
			},
			currentChatModelName() {
				return this.currentChatModel.displayName || '选择聊天模型';
			},
			currentChatModelPrice() {
				return this.currentChatModel.priceText || '';
			},
			currentChatModelSourceLabel() {
				return this.currentChatModel.source === 'BYOK' ? '我的 API' : '平台';
			},
			visibleChatModelItems() {
				const source = this.chatModelPicker.tab === 'BYOK'
					? this.chatModelCatalog.byokModels
					: this.chatModelCatalog.platformModels;
				const keyword = String(this.chatModelPicker.search || '').trim().toLowerCase();
				return (Array.isArray(source) ? source : []).map((item) => Object.assign({}, item, {
					_source: this.chatModelPicker.tab,
					_ref: this.chatModelPicker.tab === 'BYOK' ? String(item.id || '') : String(item.offeringCode || '')
				})).filter((item) => {
					if (!keyword) return true;
					return [item.displayName, item.modelName, item.shortDescription, (item.tags || []).join(' ')]
						.join(' ').toLowerCase().indexOf(keyword) >= 0;
				});
			},
			memoryPanelBusy() {
				const panel = this.memoryPanel || {};
				return !!(panel.loading || panel.loadingMore || panel.refreshing || panel.syncing || panel.updatingEntryId || panel.deletingEntryId || panel.savingEntry);
			},
			memoryEditorTypeOptions() {
				return ['identity', 'relationship', 'preference', 'promise', 'event', 'setting', 'boundary']
					.map((value) => ({ value, label: this.memoryTypeLabel(value) }));
			},
			memoryEditorTypeIndex() {
				const type = String(this.memoryPanel && this.memoryPanel.editor && this.memoryPanel.editor.memoryType || 'event');
				const index = this.memoryEditorTypeOptions.findIndex((item) => item.value === type);
				return index < 0 ? 4 : index;
			},
			memoryEditorCanConstant() {
				const type = String(this.memoryPanel && this.memoryPanel.editor && this.memoryPanel.editor.memoryType || '');
				return ['identity', 'relationship', 'setting', 'boundary'].includes(type);
			},
			memoryPanelTotalCount() {
				const detail = this.memoryPanel && this.memoryPanel.detail || {};
				return Math.max(0, Number(detail.entryCount || 0)) + Math.max(0, Number(detail.archivedEntryCount || 0));
			},
			memoryPanelFilterOptions() {
				const panel = this.memoryPanel || {};
				const detail = panel.detail || {};
				const archived = Math.max(0, Number(detail.archivedEntryCount || 0));
				const enabled = Math.max(0, Number(detail.enabledEntryCount || 0));
				const disabled = Math.max(0, Number(detail.disabledEntryCount || 0));
				const active = detail.entryCount != null
					? Math.max(0, Number(detail.entryCount || 0))
					: enabled + disabled;
				return [
					{ key: 'all', icon: 'list', label: this.tx('memory_filter_all', '全部'), count: active + archived },
					{ key: 'enabled', icon: 'checkmark-circle', label: this.tx('memory_filter_enabled', '启用'), count: enabled },
					{ key: 'disabled', icon: 'pause-circle', label: this.tx('memory_filter_disabled', '停用'), count: disabled },
					{ key: 'archived', icon: 'bookmark', label: this.tx('memory_filter_archived', '归档'), count: archived }
				];
			},
			memoryPanelFilterEmptyTitle() {
				if (this.memoryPanel && this.memoryPanel.error) {
					return this.tx('memory_load_failed_title', '暂时无法读取记忆');
				}
				const filter = String(this.memoryPanel && this.memoryPanel.filter || 'all');
				if (filter === 'enabled') return this.tx('memory_empty_enabled', '暂无启用记忆');
				if (filter === 'disabled') return this.tx('memory_empty_disabled', '暂无停用记忆');
				if (filter === 'archived') return this.tx('memory_empty_archived', '暂无归档记忆');
				return this.tx('memory_empty_title', '暂无长期记忆');
			},
			memoryPanelFilterEmptyText() {
				if (this.memoryPanel && this.memoryPanel.error) {
					return this.tx('memory_load_failed_sub', '请稍后重试，或关闭面板后重新打开');
				}
				const filter = String(this.memoryPanel && this.memoryPanel.filter || 'all');
				if (filter === 'enabled') return this.tx('memory_empty_enabled_sub', '可重新启用停用条目，或继续聊天后重新整理');
				if (filter === 'disabled') return this.tx('memory_empty_disabled_sub', '当前没有被用户或系统停用的条目');
				if (filter === 'archived') return this.tx('memory_empty_archived_sub', '低价值记忆达到容量上限后会保留在这里');
				return this.tx('memory_empty_sub', '聊天足够后可重新整理生成');
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
			branchPanelRows() {
				const mode = this.branchPanel && ['sessions', 'branches'].includes(this.branchPanel.mode) ? this.branchPanel.mode : 'openings';
				const items = Array.isArray(this.branchPanel && this.branchPanel[mode]) ? this.branchPanel[mode] : [];
				const rows = items
					.map((item) => this.normalizeBranchPanelRow(item, mode === 'branches' ? 'branch' : (mode === 'sessions' ? 'session' : 'opening')))
					.filter((item) => item && item.id);
				return mode === 'branches' ? this.sortBranchPanelTree(rows) : rows;
			},
			showStopStream() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					return this.sending && tavernApi.jgStreamEnabled();
				} catch (e) {
					return false;
				}
			},
			chatScrollBottomText() {
				const count = Number(this.chatUnreadCount || 0);
				if (count > 0) {
					const displayCount = count > 99 ? '99+' : String(count);
					return this.tx('chat_new_messages', '{count} 条新消息').replace('{count}', displayCount);
				}
				return this.tx('chat_bottom', '回到底部');
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
				const style = {};
				if (url) {
					style['--chat-bg-image'] = "url('" + String(url).replace(/'/g, '%27') + "')";
				}
				Object.assign(style, chatAppearance.buildCssVars(this.chatAppearanceConfig));
				return style;
			},
			chatReadableOverlayStyle() {
				const strength = Math.max(0, Math.min(55, Number(this.normalizedChatAppearanceConfig.backdropStrength || 0)));
				return { backgroundColor: 'rgba(0,0,0,' + (strength / 100).toFixed(3) + ')' };
			},
			normalizedChatAppearanceConfig() {
				return chatAppearance.normalizeConfig(this.chatAppearanceConfig);
			},
			chatBubbleAppearanceEnabled() {
				return chatAppearance.isBubbleCustomized(this.chatAppearanceConfig);
			},
			chatAppearanceEnabled() {
				return this.chatBubbleAppearanceEnabled;
			},
			chatAppearanceReadMode() {
				return this.normalizedChatAppearanceConfig.readMode || 'original';
			},
			chatAppearanceSegmentLabelsEnabled() {
				return this.normalizedChatAppearanceConfig.showSegmentLabels === true;
			},
			chatReplySplitMode() {
				return this.normalizedChatAppearanceConfig.replySplitMode || 'none';
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
			this.chatAppearanceDisposed = false;
			this.chatAppearanceRequestGuard = chatAppearance.createRequestGuard();
			const tavernApi = require('@/common/tavernApi.js');
			this.jgViewerIdentitySignature = tavernApi.getViewerIdentitySignature();
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
			this.applyImageGenerationFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
			this.applyRechargeEntryConfig(tavernApi.getRuntimeFeatureConfig());
			this.chatAppearanceChangeHandler = () => {
				this.refreshChatAppearanceConfig();
			};
			if (uni && typeof uni.$on === 'function') {
				uni.$on('tavern-chat-appearance-changed', this.chatAppearanceChangeHandler);
			}
			this.refreshChatAppearanceConfig();
			this.refreshUserAvatar();
			this.refreshLocalExpressionLibrary();
			this.refreshAssistantVoiceAutoPreference();
			this.refreshCharacterVoiceConfig();
			this.refreshCharacterImageConfig();
			this.refreshCharacterImageGlobalSummary(false, false);
			this.refreshVoiceFeatureGlobalState(false);
			this.refreshVisionAccessState(false);
			this.loadJgSession({ identitySignature: this.jgViewerIdentitySignature });
		},
		onShow() {
			companionStore.emitLayout({ avoidBottom: this.inputFocus ? 150 : 92, compact: this.inputFocus === true });
			if (this.jgOn) {
				const tavernApi = require('@/common/tavernApi.js');
				const identityChanged = this.handleJgIdentityChangeOnShow(tavernApi);
				this.applyVoiceFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
				this.applyImageGenerationFeatureGlobalConfig(tavernApi.getRuntimeFeatureConfig());
				this.applyRechargeEntryConfig(tavernApi.getRuntimeFeatureConfig());
				this.refreshChatAppearanceConfig();
				this.syncChatAppearanceFromCloud();
				this.refreshUserAvatar();
				this.refreshLocalExpressionLibrary();
				this.refreshAssistantVoiceAutoPreference();
				this.refreshCharacterVoiceConfig();
				this.refreshCharacterImageConfig();
				this.refreshCharacterImageGlobalSummary(true, false);
				this.refreshVoiceFeatureGlobalState(true);
				this.refreshVisionAccessState(true);
				if (this.jgChatLoadState === 'ready') {
					this.refreshChatModelCatalog(true);
				}
				if (!identityChanged) {
					this.maybeRecoverJgSessionOnShow();
				}
			}
		},
		onHide() {
			companionStore.emitLayout({ avoidBottom: 92, compact: false });
			this.flushDraftSave();
		},
		onUnload() {
			companionStore.emitLayout({ avoidBottom: 92, compact: false });
			if (this.sending || this.streamAbortController) {
				this.stopGeneration({ silent: true, skipSync: true });
			}
			this.chatAppearanceDisposed = true;
			this.chatAppearanceRequestVersion += 1;
			if (this.chatAppearanceRequestGuard && typeof this.chatAppearanceRequestGuard.cancel === 'function') {
				this.chatAppearanceRequestGuard.cancel();
			}
			this.chatAppearanceRequestGuard = null;
			if (this.chatAppearanceChangeHandler && uni && typeof uni.$off === 'function') {
				uni.$off('tavern-chat-appearance-changed', this.chatAppearanceChangeHandler);
			}
			this.chatAppearanceChangeHandler = null;
			this.flushDraftSave();
			this.clearJgLoadRetryTimer();
			this.clearDraftSaveTimer();
			this.clearStopSyncTimer();
			this.clearChatUiTimers();
			this.clearMessageActionPressState();
			this.disposeVoiceRecorder(true);
			this.disposeUserVoicePlayer();
			this.disposeAssistantVoicePlayer();
			this.chatModelRequestVersion += 1;
			this.chatModelPicker.visible = false;
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
			compactPanelText(value, maxLength) {
				const limit = Math.max(12, Number(maxLength || 80));
				const text = String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
				if (text.length <= limit) return text;
				return text.slice(0, limit).trim() + '...';
			},
			refreshChatAppearanceConfig() {
				try {
					this.chatAppearanceConfig = chatAppearance.loadConfig(this.cid);
				} catch (e) {
					this.chatAppearanceConfig = chatAppearance.normalizeConfig(null);
				}
			},
			syncChatAppearanceFromCloud() {
				if (this.chatAppearanceDisposed) return;
				if (!this.chatAppearanceRequestGuard || this.chatAppearanceRequestGuard.active === false) {
					this.chatAppearanceRequestGuard = chatAppearance.createRequestGuard();
				}
				const requestVersion = ++this.chatAppearanceRequestVersion;
				const characterId = String(this.cid || '');
				const tavernApi = require('@/common/tavernApi.js');
				const ownerSignature = tavernApi.getViewerStateSignature();
				chatAppearance.syncFromCloud(characterId || null, { guard: this.chatAppearanceRequestGuard }).then((config) => {
					if (this.chatAppearanceDisposed || requestVersion !== this.chatAppearanceRequestVersion) return;
					if (String(this.cid || '') !== characterId) return;
					if (tavernApi.getViewerStateSignature() !== ownerSignature) return;
					this.chatAppearanceConfig = chatAppearance.normalizeConfig(config);
				});
			},
			buildChatAppearancePayloadFields() {
				const config = chatAppearance.normalizeConfig(this.chatAppearanceConfig);
				if (!config.replySplitMode || config.replySplitMode === 'none') {
					return {};
				}
				return {
					replySplitMode: config.replySplitMode
				};
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
				return tavernErrors.resolveCommercialPrompt(e, {
					rechargeEntryVisible: this.rechargeEntryVisible !== false
				});
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
				const state = this.visionAccessState || {};
				if (!state.loaded) {
					this.refreshVisionAccessState(true);
					uni.showToast({
						title: this.tx('chat_image_state_loading', '正在检查识图配置，请稍后再试'),
						icon: 'none'
					});
					return false;
				}
				if (!state.canUse) {
					uni.showToast({
						title: state.denyReason || this.tx('chat_image_unavailable', '当前识图功能不可用'),
						icon: 'none',
						duration: 3200
					});
					return false;
				}
				return true;
			},
			resetChatModelCatalog() {
				this.chatModelRequestVersion += 1;
				this.chatModelCatalog = {
					enabled: false,
					loading: false,
					platformModels: [],
					byokModels: [],
					current: null,
					wallet: { diamonds: 0, gold: 0 },
					message: ''
				};
				this.chatModelPicker.visible = false;
				this.chatModelPicker.search = '';
				this.chatModelPicker.selectingRef = '';
			},
			refreshChatModelCatalog(silent) {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser || !tavernApi.hasLoggedInUser()) {
					this.resetChatModelCatalog();
					return Promise.resolve(null);
				}
				const requestVersion = ++this.chatModelRequestVersion;
				this.chatModelCatalog.loading = true;
				const conversationId = Number(this.jgConversationId || 0);
				return tavernApi.getTavernChatModels(tavernApi.getClientUid(), conversationId > 0 ? conversationId : null)
					.then((data) => {
						if (requestVersion !== this.chatModelRequestVersion) return null;
						const next = data && typeof data === 'object' ? data : {};
						this.chatModelCatalog = {
							enabled: next.enabled === true,
							loading: false,
							platformModels: Array.isArray(next.platformModels) ? next.platformModels : [],
							byokModels: Array.isArray(next.byokModels) ? next.byokModels : [],
							current: next.current && typeof next.current === 'object' && next.current.source ? next.current : null,
							wallet: next.wallet && typeof next.wallet === 'object' ? next.wallet : { diamonds: 0, gold: 0 },
							message: String(next.message || '')
						};
						return this.chatModelCatalog;
					})
					.catch((error) => {
						if (requestVersion !== this.chatModelRequestVersion) return null;
						this.chatModelCatalog.loading = false;
						if (!silent) this.showErrorToast(this.jgErrMsg(error, '模型列表加载失败'));
						return null;
					});
			},
			openChatModelPicker() {
				if (!this.chatModelCatalog.enabled) return;
				if (this.sending) {
					uni.showToast({ title: '生成结束后再切换模型', icon: 'none' });
					return;
				}
				this.closeChatAttachmentMenu();
				this.closeExpressionPanel();
				this.inputFocus = false;
				try { uni.hideKeyboard(); } catch (e) {}
				this.chatModelPicker.search = '';
				this.chatModelPicker.tab = this.currentChatModel.source === 'BYOK' ? 'BYOK' : 'SYSTEM';
				this.chatModelPicker.visible = true;
				this.refreshChatModelCatalog(true);
			},
			closeChatModelPicker() {
				if (this.chatModelPicker.selectingRef) return;
				this.chatModelPicker.visible = false;
				this.chatModelPicker.search = '';
			},
			setChatModelPickerTab(tab) {
				if (this.chatModelPicker.selectingRef) return;
				this.chatModelPicker.tab = tab === 'BYOK' ? 'BYOK' : 'SYSTEM';
				this.chatModelPicker.search = '';
			},
			isCurrentChatModel(item) {
				return !!item && this.currentChatModel.source === item._source
					&& String(this.currentChatModel.ref || '') === String(item._ref || '');
			},
			chatModelItemPrice(item) {
				if (!item) return '';
				if (item.available === false) return '不可用';
				return item._source === 'BYOK' ? '自己的额度' : String(item.priceText || '');
			},
			chatModelLevelText(label, value) {
				const level = Math.max(1, Math.min(5, Number(value || 3)));
				return String(label || '') + ' ' + level + '/5';
			},
			selectChatModelItem(item) {
				if (!item || this.sending || this.chatModelPicker.selectingRef) return;
				if (item.available === false) {
					uni.showToast({ title: item.unavailableReason || '当前模型暂不可用', icon: 'none' });
					return;
				}
				if (this.isCurrentChatModel(item)) {
					this.closeChatModelPicker();
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				const ref = String(item._ref || '').trim();
				if (!ref) return;
				this.chatModelRequestVersion += 1;
				this.chatModelCatalog.loading = false;
				this.chatModelPicker.selectingRef = ref;
				tavernApi.selectTavernChatModel({
					clientUid: tavernApi.getClientUid(),
					conversationId: Number(this.jgConversationId || 0) || null,
					source: item._source,
					ref
				}).then((current) => {
					this.chatModelCatalog.current = current && current.source ? current : this.chatModelCatalog.current;
					this.chatModelPicker.visible = false;
					this.chatModelPicker.search = '';
					uni.showToast({ title: '已切换为 ' + (current && current.displayName ? current.displayName : (item.displayName || item.modelName)), icon: 'none' });
				}).catch((error) => {
					this.showErrorToast(this.jgErrMsg(error, '模型切换失败'));
					this.refreshChatModelCatalog(true);
				}).finally(() => {
					this.chatModelPicker.selectingRef = '';
				});
			},
			createChatGenerationRequestId(prefix, seed) {
				const safePrefix = String(prefix || 'chat').replace(/[^A-Za-z0-9_.:-]/g, '').slice(0, 18) || 'chat';
				const safeSeed = String(seed || '').replace(/[^A-Za-z0-9_.:-]/g, '').slice(-28);
				return safePrefix + '_' + Date.now() + '_' + (safeSeed || Math.random().toString(36).slice(2, 10));
			},
			buildChatModelPayloadFields(generationRequestId) {
				const fields = { generationRequestId };
				if (this.chatModelCatalog.loading) return fields;
				if (!this.chatModelCatalog.enabled || !this.currentChatModel.source || !this.currentChatModel.ref) return fields;
				fields.chatModelSource = this.currentChatModel.source;
				fields.chatModelRef = String(this.currentChatModel.ref);
				if (this.currentChatModel.source === 'SYSTEM' && this.currentChatModel.selectionVersion != null) {
					fields.chatModelSelectionVersion = Number(this.currentChatModel.selectionVersion);
				}
				return fields;
			},
			looksLikeVisionModel(modelName) {
				return /(vision|visual|multimodal|qwen[^/]*vl|llava|pixtral|gpt-4o|gpt-4\.1|gemini|claude|grok[^/]*vision)/i.test(String(modelName || ''));
			},
			refreshVisionAccessState(force) {
				const state = this.visionAccessState || {};
				if (state.loading) return Promise.resolve(state);
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser()) {
					this.visionAccessState = Object.assign({}, state, { loaded: true, loading: false, canUse: false, denyReason: '识图功能需要先登录账号' });
					return Promise.resolve(this.visionAccessState);
				}
				this.visionAccessState = Object.assign({}, state, { loading: true });
				return tavernApi.getTavernUserAiProvider(tavernApi.getClientUid()).then((data) => {
					const next = data || {};
					const mode = next.mode === 'custom' ? 'custom' : 'system';
					let canUse = false;
					let denyReason = '';
					if (mode === 'custom') {
						const visionReady = !!String(next.visionModelName || '').trim() || this.looksLikeVisionModel(next.modelName);
						canUse = next.canUse === true && next.apiKeyConfigured === true && visionReady;
						if (!canUse) denyReason = next.denyReason || '请先在 AI 设置中配置自己的 API Key 和视觉模型';
					} else {
						canUse = next.visionOfficialEnabled === true && next.visionOfficialReady === true;
						if (!canUse) denyReason = next.visionOfficialEnabled === true ? '官方识图暂时没有可用供应商' : '官方识图功能当前未开放';
					}
					this.visionAccessState = {
						loaded: true,
						loading: false,
						mode,
						canUse,
						denyReason,
						visionScoreCost: Math.max(0, Number(next.visionScoreCost || 0)),
						visionGoldCost: Math.max(0, Number(next.visionGoldCost || 0))
					};
					return this.visionAccessState;
				}).catch(() => {
					this.visionAccessState = Object.assign({}, state, {
						loaded: false,
						loading: false,
						canUse: false,
						denyReason: '识图配置加载失败，请稍后重试'
					});
					return this.visionAccessState;
				});
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
						this.applyImageGenerationFeatureGlobalConfig(config);
						this.applyRechargeEntryConfig(config);
						return this.voiceFeatureEnabledGlobal;
					}).catch(() => this.voiceFeatureEnabledGlobal);
				} catch (e) {
					return Promise.resolve(this.voiceFeatureEnabledGlobal);
				}
			},
			applyImageGenerationFeatureGlobalConfig(config) {
				const enabled = !(config && config.imageGenerationEnabled === false);
				this.imageGenerationEnabledGlobal = enabled;
				if (!enabled) {
					this.characterImagePanel = Object.assign(createCharacterImagePanelState(), { visible: false });
				}
				return enabled;
			},
			applyRechargeEntryConfig(config) {
				const visible = !(config && config.rechargeEntryVisible === false);
				this.rechargeEntryVisible = visible;
				if (
					!visible &&
					this.commercialPrompt &&
					this.commercialPrompt.visible &&
					this.commercialPrompt.kind !== 'chat_quota'
				) {
					this.commercialPrompt = Object.assign({}, this.commercialPrompt, {
						message: '充值与会员购买入口暂未开放，请稍后再试或联系客服。',
						primaryText: '联系客服',
						primaryUrl: '/pages/user/lianxiwomen/lianxiwomen',
						secondaryText: '',
						secondaryUrl: ''
					});
				}
				return visible;
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
				if (msg.indexOf('模型价格或配置已更新') >= 0) {
					this.refreshChatModelCatalog(true);
				}
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
				const showRechargeEntry = this.rechargeEntryVisible !== false;
				const purchaseUnavailableMessage = '充值与会员购买入口暂未开放，请稍后再试或联系客服。';
				this.commercialPrompt = {
					visible: true,
					kind: data.kind || '',
					title: isChatQuota ? '今日聊天次数已用完' : (data.title || this.tx('membership_title', '会员权益提示')),
					message: isChatQuota
						? '今天的官方模型聊天次数已经用完。你可以配置自己的 API Key 继续聊天，或联系作者获取协助。'
						: (showRechargeEntry
							? (data.message || rawMessage || this.tx('membership_message', '当前操作需要更高权益，请先开通会员或充值。'))
							: purchaseUnavailableMessage),
					primaryText: isChatQuota ? '配置 API Key' : (showRechargeEntry ? (data.primaryText || this.chatUi.openVip) : '联系客服'),
					primaryUrl: isChatQuota ? '/pages/user/aiSettings' : (showRechargeEntry ? (data.primaryUrl || '/pages/user/myvip') : '/pages/user/lianxiwomen/lianxiwomen'),
					secondaryText: isChatQuota
						? '联系作者'
						: (showRechargeEntry ? (data.secondaryText || this.chatUi.recharge) : ''),
					secondaryUrl: isChatQuota
						? '/pages/user/lianxiwomen/lianxiwomen'
						: (showRechargeEntry ? (data.secondaryUrl || '/pages/user/pay') : '')
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
				const canFork = this.canForkChatBranchFromMessage(message);
				if (!text && !canDelete && !canFork) return;
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
			resolveMessageActionMenuPosition(message, event, hasText, canDelete, canFork) {
				const point = this.extractMessageActionPoint(event);
				const viewport = this.getMessageActionViewport();
				const pxPerRpx = viewport.width / 750;
				const margin = 12;
				const offset = 10;
				const menuWidth = Math.max(118, Math.round(236 * pxPerRpx));
				const rowCount = (hasText ? 2 : 0) + (canFork ? 1 : 0) + (canDelete ? 1 : 0);
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
			canForkChatBranchFromMessage(message) {
				if (!this.jgOn || !message || this.sending) return false;
				const id = this.normalizeDbMessageId(message.id);
				if (!id || id.indexOf('db_') !== 0) return false;
				return message.role === 'user' || message.role === 'char';
			},
			openMessageActionSheet(message, event) {
				if (!message) return;
				this.clearMessageActionPressState();
				const text = String(message.text || '').trim();
				const canDelete = this.canBranchDeleteChatMessage(message);
				const canFork = this.canForkChatBranchFromMessage(message);
				if (!text && !canDelete && !canFork) return;
				const position = this.resolveMessageActionMenuPosition(message, event, !!text, canDelete, canFork);
				this.messageActionSheet = {
					visible: true,
					messageId: this.normalizeDbMessageId(message.id),
					role: message.role || '',
					text,
					canDelete,
					canFork,
					deleting: false,
					forking: false,
					leftPx: position.leftPx,
					topPx: position.topPx,
					imageUrls: Array.isArray(message.imageUrls) ? message.imageUrls.slice() : [],
					voiceUrl: this.normalizeVoiceMessageUrl(message.voiceUrl),
					voiceDurationMs: this.normalizeVoiceDurationMs(message.voiceDurationMs)
				};
			},
			closeMessageActionSheet(force) {
				if (
					this.messageActionSheet &&
					(this.messageActionSheet.deleting || this.messageActionSheet.forking) &&
					force !== true
				) return;
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
				if (this.messageActionSheet.deleting || this.messageActionSheet.forking) return;
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
				var studioMembers = [];
				var conversationId = '';
				var activeBranchId = '';
				var page = null;
				if (Array.isArray(pack)) {
					rows = pack;
				} else if (pack && typeof pack === 'object') {
					rows = Array.isArray(pack.messages) ? pack.messages : [];
					mem = pack.memory != null ? pack.memory : null;
					meta = pack.tavernMeta != null ? pack.tavernMeta : null;
					studioMembers = Array.isArray(pack.studioMembers) ? pack.studioMembers : [];
					conversationId = pack.conversationId != null ? pack.conversationId : '';
					activeBranchId = pack.activeBranchId != null ? pack.activeBranchId : '';
					page = pack.page && typeof pack.page === 'object' ? pack.page : null;
				}
				return {
					rows,
					mem,
					meta,
					studioMembers,
					conversationId,
					activeBranchId,
					page
				};
			},
			normalizeCharacterStudioMembers(source) {
				return (Array.isArray(source) ? source : []).map((item) => {
					const id = Math.max(0, Math.floor(Number(item && item.id) || 0));
					const name = this.normalizeCharacterVoiceText(item && item.name, 64);
					if (!id || !name) return null;
					return {
						id,
						name,
						avatarUrl: this.normalizeCharacterVoiceText(item && item.avatarUrl, 512)
					};
				}).filter(Boolean);
			},
			applyCharacterStudioMembers(source) {
				if (!this.char || typeof this.char !== 'object') return;
				this.$set(this.char, 'studioMembers', this.normalizeCharacterStudioMembers(source));
			},
			characterStudioMembers() {
				return this.normalizeCharacterStudioMembers(this.char && this.char.studioMembers);
			},
			findCharacterStudioMemberByName(name) {
				const expected = this.normalizeCharacterVoiceText(name, 64).toLocaleLowerCase();
				if (!expected) return null;
				return this.characterStudioMembers().find((member) => member.name.toLocaleLowerCase() === expected) || null;
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
				const trackIncomingUnread = opts.trackIncomingUnread === true && !opts.prependHistory;
				const previousChatMessageKeyMap = trackIncomingUnread
					? this.collectChatMessageKeyMap(this.messages)
					: {};
				let incomingRows = [];
				this.syncLocalChatConversationId(envelope.conversationId);
				this.applyCharacterStudioMembers(envelope.studioMembers);
				this.jgActiveBranchId = envelope.activeBranchId == null ? '' : String(envelope.activeBranchId);
				if (opts.prependHistory) {
					const currentRows = Array.isArray(this.messages) ? this.messages.slice() : [];
					this.messages = this.mergeLocalChatImagesIntoRows(
						this.mergePrependedHistoryRows(normalizedRows, currentRows)
					);
				} else {
					this.messages = this.mergeLocalChatImagesIntoRows(normalizedRows);
				}
				if (trackIncomingUnread) {
					incomingRows = (Array.isArray(this.messages) ? this.messages : []).filter((row) => {
						return this.shouldCountUnreadChatRow(row) && !this.chatUnreadRowExistsInMap(row, previousChatMessageKeyMap);
					});
				}
				if (this.streamingAssistantMessageId) {
					const exists = this.messages.some(
						(row) => this.normalizeDbMessageId(row && row.id) === this.streamingAssistantMessageId
					);
					if (!exists) {
						this.finishAssistantStreaming(this.streamingAssistantMessageId);
					}
				}
				this.recoverStaleSendingState();
				this.syncUserVoiceEntries();
				this.jgMemory = envelope.mem;
				this.jgTavernMeta = envelope.meta;
				this.syncAssistantVoiceEntries();
				this.syncMessageHistoryPageState(envelope.page);
				if (opts.invalidateReplySuggestions !== false) {
					this.invalidateReplySuggestions();
				}
				if (trackIncomingUnread && incomingRows.length) {
					this.handleIncomingChatRows(incomingRows);
				}
				return incomingRows;
			},
			currentJgViewerIdentitySignature(tavernApi) {
				const api = tavernApi || require('@/common/tavernApi.js');
				return String(api.getViewerIdentitySignature() || '').trim();
			},
			isJgViewerIdentityCurrent(identitySignature) {
				const expected = String(identitySignature || this.jgViewerIdentitySignature || '').trim();
				return !!expected && expected === this.currentJgViewerIdentitySignature();
			},
			isJgRuntimeRequestCurrent(version, identitySignature) {
				return (
					Number(version) === Number(this.jgRuntimeRequestVersion) &&
					String(identitySignature || '') === String(this.jgViewerIdentitySignature || '') &&
					this.isJgViewerIdentityCurrent(identitySignature)
				);
			},
			ensureJgIdentityReadyForAction() {
				const currentIdentity = this.currentJgViewerIdentitySignature();
				if (!this.jgViewerIdentitySignature) {
					this.jgViewerIdentitySignature = currentIdentity;
				}
				if (currentIdentity !== this.jgViewerIdentitySignature) {
					this.reloadJgSessionForIdentity(currentIdentity);
					return false;
				}
				return !this.jgIdentityReloading && this.jgChatLoadState === 'ready';
			},
			handleJgIdentityChangeOnShow(tavernApi) {
				const currentIdentity = this.currentJgViewerIdentitySignature(tavernApi);
				if (!this.jgViewerIdentitySignature) {
					this.jgViewerIdentitySignature = currentIdentity;
					return false;
				}
				if (!viewerIdentity.shouldReloadViewerIdentity(this.jgViewerIdentitySignature, currentIdentity)) {
					return false;
				}
				this.reloadJgSessionForIdentity(currentIdentity);
				return true;
			},
			reloadJgSessionForIdentity(identitySignature) {
				const nextIdentity = String(identitySignature || this.currentJgViewerIdentitySignature()).trim();
				if (!nextIdentity || (nextIdentity === this.jgViewerIdentitySignature && this.jgIdentityReloading)) {
					return false;
				}
				this.jgIdentityReloading = true;
				this.jgViewerIdentitySignature = nextIdentity;
				this.jgRuntimeRequestVersion += 1;
				this.jgLoadRequestToken = Date.now() + Math.random();
				this.memoryRefreshStatusToken += 1;
				this.clearJgLoadRetryTimer();
				this.clearDraftSaveTimer();
				this.clearStopSyncTimer();
				this.clearChatUiTimers();
				this.clearMessageActionPressState();
				if (this.streamAbortController) {
					const controller = this.streamAbortController;
					this.streamAbortController = null;
					try {
						controller.abort();
					} catch (e) {}
				}
				this.disposeVoiceRecorder(true);
				this.disposeUserVoicePlayer();
				this.disposeAssistantVoicePlayer();
				this.resetConversationVoiceRuntimeState();
				this.finishSendingState();
				this.streamingAssistantMessageId = '';
				this.streamingAssistantMode = '';
				this.char = null;
				this.messages = [];
				this.draft = '';
				this.draftHydrated = false;
				this.draftRestoredNoticeVisible = false;
				this.composerImages = [];
				this.composerQuote = createComposerQuoteState();
				this.editOverlay = createEditOverlayState();
				this.messageActionSheet = createMessageActionSheetState();
				this.branchPanel = createBranchPanelState();
				this.memoryPanel = createMemoryPanelState();
				this.invalidateReplySuggestions();
				this.clearGenerationRecovery();
				this.closeCommercialPrompt();
				this.attachmentMenuVisible = false;
				this.expressionPanelVisible = false;
				this.expressionLibrary = [];
				this.expressionUploadBusy = false;
				this.inputFocus = false;
				this.charImagePreviewVisible = false;
				this.characterVoiceConfig = createDefaultCharacterVoiceConfig();
				this.characterVoicePanel = createCharacterVoicePanelState();
				this.characterImageConfig = createDefaultCharacterImageConfig();
				this.characterImagePanel = createCharacterImagePanelState();
				this.resetCharacterImageReferencePreparedCache();
				this.jgConversationId = '';
				this.jgActiveBranchId = '';
				this.jgMemory = null;
				this.jgTavernMeta = null;
				this.resetChatModelCatalog();
				this.memoryRefreshing = false;
				this.messageHistoryHasMore = false;
				this.messageHistoryLoading = false;
				this.messageHistoryNextBeforeId = '';
				this.messageHistoryLoadAt = 0;
				this.chatViewportReady = false;
				this.resetChatUnreadState();
				this.jgChatLoadState = 'loading';
				this.jgChatErrorMsg = '';
				this.loadJgSession({
					identityReload: true,
					identitySignature: nextIdentity
				});
				return true;
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
				const loadIdentitySignature = String(
					opts.identitySignature || this.jgViewerIdentitySignature || tavernApi.getViewerIdentitySignature() || ''
				).trim();
				if (!this.jgViewerIdentitySignature) {
					this.jgViewerIdentitySignature = loadIdentitySignature;
				}
				if (
					!loadIdentitySignature ||
					loadIdentitySignature !== this.jgViewerIdentitySignature ||
					!this.isJgViewerIdentityCurrent(loadIdentitySignature)
				) {
					return;
				}
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
				this.resetChatUnreadState();
				this.lastChatScrollTop = 0;
				this.scrollTo = '';
				tavernApi
					.fetchCharacter(this.cid)
					.then((c) => {
						if (this.jgLoadRequestToken !== requestToken || !this.isJgViewerIdentityCurrent(loadIdentitySignature)) {
							return Promise.reject({ __stale: true });
						}
						if (!c) {
							throw new Error(this.tx('character_missing', '角色不存在或已下架'));
						}
						this.char = c;
						this.resetCharacterImageReferencePreparedCache();
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
						if (this.jgLoadRequestToken !== requestToken || !this.isJgViewerIdentityCurrent(loadIdentitySignature)) {
							return;
						}
						this.applyMessagesEnvelope(pack);
						this.hydrateStoredDraft();
						this.followBottom = true;
						this.atChatBottom = true;
						this.jgChatLoadState = 'ready';
						this.jgIdentityReloading = false;
						this.jgLoadAutoRetried = false;
						this.refreshChatModelCatalog(true);
						this.scrollChatToBottom({ immediate: true, reveal: true });
					})
					.catch((e) => {
						if ((e && e.__stale) || this.jgLoadRequestToken !== requestToken) return;
						if (e && e.message === 'vip') {
							this.jgIdentityReloading = false;
							return;
						}
						if (opts.autoRetry !== false && this.scheduleJgLoadAutoRetry(e)) {
							return;
						}
						this.jgChatLoadState = 'error';
						this.jgIdentityReloading = false;
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
				const mediaKeys = Array.isArray(entry.mediaKeys)
					? entry.mediaKeys.map((item) => String(item || '').trim()).filter(Boolean)
					: [];
				return {
					messageId: messageId || (assistantMessageId ? 'local_' + assistantMessageId : ''),
					assistantMessageId: assistantMessageId && assistantMessageId.startsWith('db_') ? assistantMessageId : '',
					text,
					signature: role === 'user' ? this.buildLocalChatUserSignature(text) : '',
					imageUrls,
					mediaKeys,
					role,
					kind,
					prompt,
					aspectRatio,
					createdAt,
					updatedAt
				};
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
					const entries = source
						.map((item) => this.normalizeLocalChatImageEntry(item))
						.filter(Boolean)
						.sort((a, b) => a.createdAt - b.createdAt);
					if (
						(raw && typeof raw === 'object' && raw.version !== LOCAL_CHAT_IMAGE_CACHE_VERSION) ||
						entries.length !== source.length
					) {
						this.writeLocalChatImageEntries(entries, conversationId);
					}
					return entries;
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
					uni.setStorageSync(key, {
						version: LOCAL_CHAT_IMAGE_CACHE_VERSION,
						updatedAt: Date.now(),
						entries: normalized.map((item) => ({
							messageId: item.messageId,
							assistantMessageId: item.assistantMessageId,
							text: item.text,
							imageUrls: item.imageUrls,
							mediaKeys: item.mediaKeys,
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
				const indexedMedia = !!(entry && Array.isArray(entry.mediaKeys) && entry.mediaKeys.length);
				return this.normalizeChatRow({
					id: entry && entry.messageId ? entry.messageId : fallbackId,
					role: entry && entry.role === 'char' ? 'char' : 'user',
					text: entry && entry.text ? entry.text : '',
					imageUrls: indexedMedia ? [] : entry && entry.imageUrls ? entry.imageUrls : [],
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
				return (Array.isArray(entries) ? entries : []).filter((entry) => {
					if (!entry || !entry.imageUrls || !entry.imageUrls.length) return false;
					if (entry._used) return true;
					if (entry.messageId && rowIds[entry.messageId]) return true;
					if (entry.assistantMessageId && rowIds[entry.assistantMessageId]) return true;
					if (entry.role === 'char') return true;
					return String(entry.messageId || '').indexOf('u_') === 0 && Date.now() - entry.updatedAt <= LOCAL_CHAT_IMAGE_PENDING_KEEP_MS;
				});
			},
			hydrateLocalChatImageMedia(entries) {
				if (this.localChatImageHydratePending) return;
				const targets = (Array.isArray(entries) ? entries : []).filter(
					(item) => item && Array.isArray(item.mediaKeys) && item.mediaKeys.length
				);
				if (!targets.length) return;
				this.localChatImageHydratePending = true;
				const localMediaStore = require('@/common/localMediaStore.js');
				Promise.all(targets.map((entry) => Promise.all(
					entry.mediaKeys.map((key) => localMediaStore.get(key).catch(() => null))
				).then((stored) => {
					const available = stored.map((item, index) => ({
						item,
						key: entry.mediaKeys[index]
					})).filter((row) => row.item && String(row.item.url || '').trim());
					return {
						entry,
						keys: available.map((row) => row.key),
						urls: available.map((row) => String(row.item.url || '').trim())
					};
				})
				)).then((resolved) => {
					let changed = false;
					const removedMessageIds = {};
					resolved.forEach((item) => {
						if (!item.urls.length) {
							if (item.entry.messageId) removedMessageIds[item.entry.messageId] = true;
							item.entry.imageUrls = [];
							item.entry.mediaKeys = [];
							changed = true;
							return;
						}
						item.entry.imageUrls = item.urls;
						item.entry.mediaKeys = item.keys;
						item.entry.updatedAt = Date.now();
						changed = true;
					});
					if (!changed) return;
					for (let i = entries.length - 1; i >= 0; i -= 1) {
						if (entries[i] && removedMessageIds[entries[i].messageId]) entries.splice(i, 1);
					}
					this.writeLocalChatImageEntries(entries);
					const byMessage = {};
					entries.forEach((entry) => {
						if (entry && entry.messageId && entry.imageUrls && entry.imageUrls.length) {
							byMessage[entry.messageId] = entry.imageUrls.slice();
						}
					});
					this.messages = (Array.isArray(this.messages) ? this.messages : []).map((row) => {
						if (row && byMessage[row.id]) return Object.assign({}, row, { imageUrls: byMessage[row.id] });
						if (row && removedMessageIds[row.id]) {
							if (row.localOnly && row.localKind === 'image_generation') return null;
							return Object.assign({}, row, { imageUrls: [] });
						}
						return row;
					}).filter(Boolean);
				}).catch(() => {}).finally(() => {
					this.localChatImageHydratePending = false;
				});
			},
			mergeLocalChatImagesIntoRows(rows) {
				const entries = this.readLocalChatImageEntries();
				const safeRows = Array.isArray(rows) ? rows : [];
				if (!entries.length) {
					return safeRows;
				}
				this.hydrateLocalChatImageMedia(entries);
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
					if (
						matchedEntry &&
						(!Array.isArray(matchedEntry.mediaKeys) || !matchedEntry.mediaKeys.length) &&
						matchedEntry.imageUrls &&
						matchedEntry.imageUrls.length
					) {
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
					&& !!this.isAppPlus;
			},
			toggleAssistantVoiceAuto() {
				if (!this.isVoiceFeatureEnabledGlobal()) return;
				const next = !this.assistantVoiceAutoEnabled;
				this.assistantVoiceAutoEnabled = next;
				this.writeAssistantVoiceAutoPreference(next);
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
					ttsProviderSource: this.normalizeCharacterVoiceText(source.ttsProviderSource, 80).toLowerCase(),
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
					userVoiceCreationEnabled: source.userVoiceCreationEnabled === true,
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
				this.characterVoicePanel.ttsProviderSource = this.normalizeCharacterVoiceText(state.providerSource, 80).toLowerCase();
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
				const hasProviderScopedOverride = !!(
					next.ttsModelName || next.ttsVoiceName || next.ttsVoiceTemplateCode
				);
				if (hasProviderScopedOverride && String(this.characterVoiceGlobalState.mode || '').trim() === 'custom') {
					const providerSource = this.normalizeCharacterVoiceText(
						this.characterVoiceGlobalState.providerSource,
						80
					).toLowerCase();
					if (!providerSource) {
						this.showErrorToast(this.tx('character_voice_provider_missing', '当前 TTS 供应商尚未加载，请刷新后重试'));
						return;
					}
					next.ttsProviderSource = providerSource;
				} else if (!hasProviderScopedOverride) {
					next.ttsProviderSource = '';
				}
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
			buildCharacterVoiceTtsPayload(text, requestId, segmentIndex, segmentCount, messageId, speakerMemberId) {
				const payload = {
					clientUid: require('@/common/tavernApi.js').getClientUid(),
					content: text,
					characterId: Number(this.char && this.char.id) || Number(this.cid) || 0
				};
				const row = this.findMessageRowById(messageId);
				const resolvedSpeakerMemberId = Math.max(0, Math.floor(Number(speakerMemberId) || 0)) ||
					(row && Math.max(0, Math.floor(Number(row.speakerMemberId) || 0)));
				if (resolvedSpeakerMemberId > 0) payload.speakerMemberId = resolvedSpeakerMemberId;
				if (requestId) {
					payload.ttsRequestId = requestId;
					payload.ttsSegmentIndex = segmentIndex;
					payload.ttsSegmentCount = segmentCount;
				}
				const config = this.currentCharacterVoiceConfig();
				if (config.ttsProviderSource && config.ttsModelName) {
					payload.ttsModelName = config.ttsModelName;
				}
				if (config.ttsProviderSource && config.ttsVoiceTemplateCode) {
					payload.ttsVoiceTemplateCode = config.ttsVoiceTemplateCode;
				} else if (config.ttsProviderSource && config.ttsVoiceName) {
					payload.ttsVoiceName = config.ttsVoiceName;
				}
				if (config.ttsProviderSource && (payload.ttsModelName || payload.ttsVoiceName || payload.ttsVoiceTemplateCode)) {
					payload.ttsProviderSource = config.ttsProviderSource;
				}
				return payload;
			},
			localMediaSignature(value) {
				const source = String(value || '');
				let hash = 2166136261;
				for (let i = 0; i < source.length; i += 1) {
					hash ^= source.charCodeAt(i);
					hash = Math.imul(hash, 16777619);
				}
				return (hash >>> 0).toString(16);
			},
			goCharacterUserVoices(memberId) {
				if (!this.canManageCharacterUserVoices()) return;
				const characterId = Number(this.char && this.char.id) || Number(this.cid) || 0;
				if (!characterId) return;
				const safeMemberId = Math.max(0, Math.floor(Number(memberId) || 0));
				let url = '/pages/user/myVoices?characterId=' + encodeURIComponent(String(characterId));
				if (safeMemberId > 0) url += '&memberId=' + encodeURIComponent(String(safeMemberId));
				uni.navigateTo({ url });
			},
			canManageCharacterUserVoices() {
				const state = this.characterVoiceGlobalState || {};
				return state.userVoiceCreationEnabled === true
					&& String(state.mode || '').trim() === 'custom'
					&& String(state.providerSource || '').trim().toLowerCase() === 'siliconflow';
			},
			assistantVoiceSegmentSignature(segment) {
				const item = segment && typeof segment === 'object' ? segment : { text: segment, speakerMemberId: 0 };
				return this.localMediaSignature(
					String(Math.max(0, Math.floor(Number(item.speakerMemberId) || 0))) + '\n' + String(item.text || '')
				);
			},
			assistantVoiceLocalMediaKey(messageId, segmentIndex) {
				const ownerKey = this.resolveLocalExpressionViewerKey();
				const conversationId = this.resolveLocalChatConversationId();
				return ['tts', ownerKey, conversationId, this.normalizeDbMessageId(messageId), segmentIndex].join(':');
			},
			persistAssistantVoiceSegment(messageId, segmentIndex, segment, taskId, audioDataUrl) {
				const safeMessageId = this.normalizeDbMessageId(messageId);
				const ownerKey = this.resolveLocalExpressionViewerKey();
				const conversationId = this.resolveLocalChatConversationId();
				if (!safeMessageId || !ownerKey || !conversationId) return Promise.resolve(audioDataUrl);
				const localMediaStore = require('@/common/localMediaStore.js');
				return localMediaStore.putDataUrl({
					key: this.assistantVoiceLocalMediaKey(safeMessageId, segmentIndex),
					ownerKey,
					conversationId,
					messageId: safeMessageId,
					kind: 'assistant_tts',
					taskId: String(taskId || '').trim(),
					segmentIndex,
					signature: this.assistantVoiceSegmentSignature(segment)
				}, audioDataUrl).then((stored) => stored && stored.url ? stored.url : audioDataUrl);
			},
			restoreAssistantVoiceEntry(row) {
				const messageId = this.assistantVoiceMessageId(row);
				if (!messageId || !messageId.startsWith('db_') || this.assistantVoiceRestorePendingMap[messageId]) {
					return Promise.resolve(null);
				}
				const voiceSegments = this.assistantVoiceSegmentsForRow(row);
				const speechText = voiceSegments.map((item) => item.text).join('\n').trim();
				const sentenceTexts = voiceSegments.map((item) => item.text);
				if (!speechText || !voiceSegments.length) return Promise.resolve(null);
				this.$set(this.assistantVoiceRestorePendingMap, messageId, true);
				const localMediaStore = require('@/common/localMediaStore.js');
				return localMediaStore.list({
					ownerKey: this.resolveLocalExpressionViewerKey(),
					conversationId: this.resolveLocalChatConversationId(),
					messageId,
					kind: 'assistant_tts'
				}).then((stored) => {
					const audioSegments = new Array(sentenceTexts.length).fill('');
					(Array.isArray(stored) ? stored : []).forEach((item) => {
						const index = Math.max(0, Number(item.segmentIndex) || 0);
						if (index >= sentenceTexts.length) return;
						if (String(item.signature || '') !== this.assistantVoiceSegmentSignature(voiceSegments[index])) return;
						audioSegments[index] = String(item.url || '').trim();
					});
					if (!audioSegments.some((item) => item)) return null;
					const storedTaskId = (Array.isArray(stored) ? stored : [])
						.map((item) => String(item && item.taskId || '').trim())
						.find((item) => item) || ('tts_' + messageId);
					return this.setAssistantVoiceEntry(messageId, {
						speechText,
						preparedSentenceKey: this.assistantVoiceSentenceKey(voiceSegments),
						sentenceTexts,
						sentenceSpeakerMemberIds: voiceSegments.map((item) => item.speakerMemberId),
						sentenceAudioUrls: audioSegments,
						audioDataUrl: audioSegments[0] || '',
						state: audioSegments[0] ? 'ready' : 'idle',
						taskId: storedTaskId,
						requestKey: '',
						error: '',
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
				}).catch(() => null).finally(() => {
					this.$delete(this.assistantVoiceRestorePendingMap, messageId);
				});
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
				if (this.imageGenerationEnabledGlobal === false) {
					return;
				}
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
			resolveCharacterCardImageReferenceUrl() {
				if (!this.char) {
					return '';
				}
				const tavernApi = require('@/common/tavernApi.js');
				const candidates = [
					this.char.avatar_thumb,
					this.char.avatar,
					this.char.cover_thumb,
					this.char.cover,
					this.char.cover_detail,
					this.charAvatar,
					this.charPreviewImage
				];
				for (let i = 0; i < candidates.length; i += 1) {
					const resolved = tavernApi.resolveJgAssetUrl(candidates[i]);
					if (resolved && String(resolved).trim()) {
						return String(resolved).trim();
					}
				}
				return '';
			},
			resolveCharacterImageReferenceUrl(globalState) {
				const sourceMode = this.normalizeCharacterImageReferenceSourceMode(
					globalState && globalState.imageReferenceSourceMode
				);
				if (sourceMode === 'latest_generated_first') {
					const latest = this.readLatestCharacterImageReference();
					if (latest) {
						return latest;
					}
				}
				return this.resolveCharacterCardImageReferenceUrl();
			},
			resetCharacterImageReferencePreparedCache() {
				this.characterImageReferencePreparedSource = '';
				this.characterImageReferencePreparedUrl = '';
				this.characterImageReferencePreparedAt = 0;
				this.characterImageReferencePreparePromise = null;
			},
			readCharacterImageReferencePreparedCache(referenceSource) {
				const safeSource = this.normalizeCharacterImageText(referenceSource, 255);
				const safeUrl = this.normalizeCharacterImageText(this.characterImageReferencePreparedUrl, 1024 * 1024);
				if (
					safeSource &&
					safeUrl &&
					this.normalizeCharacterImageText(this.characterImageReferencePreparedSource, 255) === safeSource
				) {
					return safeUrl;
				}
				return '';
			},
			saveCharacterImageReferencePreparedCache(referenceSource, preparedUrl) {
				const safeSource = this.normalizeCharacterImageText(referenceSource, 255);
				const safeUrl = String(preparedUrl || '').trim();
				if (!safeSource || safeUrl.indexOf('data:image/') !== 0) {
					this.resetCharacterImageReferencePreparedCache();
					return '';
				}
				this.characterImageReferencePreparedSource = safeSource;
				this.characterImageReferencePreparedUrl = safeUrl;
				this.characterImageReferencePreparedAt = Date.now();
				return safeUrl;
			},
			warmCharacterImageReferencePayload(force) {
				return this.prepareCharacterImageReferencePayload(force === true).catch(() => '');
			},
			prepareCharacterImageReferencePayload(force, globalState) {
				const referenceSource = this.resolveCharacterImageReferenceUrl(globalState);
				if (!referenceSource) {
					this.resetCharacterImageReferencePreparedCache();
					return Promise.resolve('');
				}
				const safeSource = String(referenceSource || '').trim();
				if (!force) {
					const cached = this.readCharacterImageReferencePreparedCache(safeSource);
					if (cached) {
						return Promise.resolve(cached);
					}
					if (this.characterImageReferencePreparePromise) {
						return this.characterImageReferencePreparePromise;
					}
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					if (!tavernApi || typeof tavernApi.prepareLocalChatImage !== 'function') {
						return Promise.resolve(safeSource);
					}
					const task = tavernApi.prepareLocalChatImage(safeSource)
						.then((prepared) => {
							const preparedUrl = prepared && prepared.url ? String(prepared.url).trim() : '';
							if (preparedUrl.indexOf('data:image/') === 0) {
								return this.saveCharacterImageReferencePreparedCache(safeSource, preparedUrl);
							}
							return preparedUrl || safeSource;
						})
						.catch(() => this.readCharacterImageReferencePreparedCache(safeSource) || safeSource)
						.finally(() => {
							if (this.characterImageReferencePreparePromise === task) {
								this.characterImageReferencePreparePromise = null;
							}
						});
					this.characterImageReferencePreparePromise = task;
					return task;
				} catch (e) {
					return Promise.resolve(this.readCharacterImageReferencePreparedCache(safeSource) || safeSource);
				}
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
			resolveCharacterImageVisualContext() {
				const source = this.char && typeof this.char === 'object' ? this.char : {};
				const rawFields = [
					source.description,
					source.personality,
					source.scenario,
					source.system_prompt,
					source.systemPrompt,
					source.first_mes,
					source.firstMes,
					source.mes_example,
					source.mesExample
				];
				const seen = {};
				const parts = rawFields
					.map((item) => this.normalizeCharacterImageText(item, 180))
					.filter((item) => {
						if (!item || seen[item]) return false;
						seen[item] = true;
						return true;
					})
					.slice(0, 5);
				return this.normalizeCharacterImageText(parts.join(' | '), 720);
			},
			resolveCharacterImageRecentSceneHint(userPrompt) {
				const safePrompt = this.normalizeCharacterImageText(userPrompt, 300);
				const rows = Array.isArray(this.messages) ? this.messages : [];
				if (!rows.length) return '';
				const snippets = [];
				for (let i = rows.length - 1; i >= 0 && snippets.length < 4; i -= 1) {
					const row = rows[i];
					if (!row || row.localKind === 'image_generation') continue;
					const text = this.normalizeCharacterImageText(row.text || row.content || '', 90);
					if (!text || text === safePrompt) continue;
					const role = row.role === 'user' ? '用户' : '角色';
					snippets.push(role + '：' + text);
				}
				return this.normalizeCharacterImageText(snippets.reverse().join(' / '), 360);
			},
			resolveCharacterImagePromptProfile(modelName, providerSource) {
				const text = String((providerSource || '') + ' ' + (modelName || '')).toLowerCase();
				if (/(flux|kontext|gpt-image|dall|imagen|recraft|seedream)/.test(text)) {
					return '使用自然语言描述，强调主体、场景、镜头、光线和质感，避免堆砌过多权重符号。';
				}
				if (/(sdxl|stable[-_ ]?diffusion|img2img|image2image|image-?edit|inpaint|controlnet|kolors|janus)/.test(text)) {
					return '使用清晰的视觉标签和短句，主体、外貌、服装、动作、背景、光线分层描述。';
				}
				if (/(siliconflow|wanx|openrouter)/.test(text)) {
					return '使用中英混合的紧凑视觉提示词，先写画面主体，再写风格、构图和质量要求。';
				}
				return '使用具体、可绘制的视觉语言，减少抽象情绪词，避免让模型猜。';
			},
			buildCharacterImageNegativePrompt(consistencyMode, useReferenceImage) {
				const mode = this.normalizeCharacterImageConsistencyMode(consistencyMode);
				const negatives = [
					'low quality',
					'blurry',
					'bad anatomy',
					'deformed hands',
					'extra fingers',
					'missing fingers',
					'extra limbs',
					'duplicate face',
					'cross-eye',
					'wrong facial features',
					'watermark',
					'logo',
					'text artifacts',
					'jpeg artifacts',
					'overexposed',
					'underexposed'
				];
				if (mode !== 'free') {
					negatives.push('inconsistent identity', 'different hairstyle', 'different hair color');
				}
				if (useReferenceImage) {
					negatives.push('copying reference pose exactly', 'copying reference background exactly');
				}
				return negatives.join(', ');
			},
			buildCharacterImageGenerationPrompt(userPrompt) {
				const safePrompt = this.normalizeCharacterImageText(userPrompt, 300);
				return safePrompt;
			},
			isCharacterImageModelUsable(modelName) {
				const text = String(modelName || '').trim().toLowerCase();
				if (!text) return false;
				return /(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|paint-by-example|kontext|flux|sdxl|stable[-_]?diffusion|dall[-_]?e|gpt-image|kolors|wanx|imagen|recraft|seedream|janus|text-to-image|text2image|t2i|image-generation|imagegeneration|generative-image)/.test(text);
			},
			isCharacterImageReferenceEditModel(modelName) {
				const text = String(modelName || '').trim().toLowerCase();
				if (!text) return false;
				return /(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|paint-by-example|kontext)/.test(text);
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
			currentEnsembleSpeakerMemberId() {
				const rows = Array.isArray(this.messages) ? this.messages : [];
				for (let i = rows.length - 1; i >= 0; i -= 1) {
					const row = rows[i];
					if (row && row.role === 'char' && Number(row.speakerMemberId) > 0) {
						return Number(row.speakerMemberId);
					}
				}
				return 0;
			},
			generateCharacterImage() {
				if (!this.characterImagePanel || this.characterImagePanel.generating) return;
				if (this.imageGenerationEnabledGlobal === false) {
					this.characterImagePanel = Object.assign(createCharacterImagePanelState(), { visible: false });
					return;
				}
				if (this.characterImageGlobalState && this.characterImageGlobalState.imageEnabledGlobal === false) {
					this.characterImagePanel = Object.assign(createCharacterImagePanelState(), { visible: false });
					return;
				}
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
					const customMode = String(state.mode || '').trim() === 'custom';
					const resolvedModelName = customMode
						? this.normalizeCharacterImageText(state.imageModelName, 255)
						: '';
					if (customMode) {
						if (!state.apiKeyConfigured) {
							throw new Error(this.tx('character_image_need_key', '先去 AI 设置页填写生图 API Key'));
						}
						if (!resolvedModelName) {
							throw new Error(this.tx('character_image_need_model', '先去 AI 设置页选择生图模型'));
						}
						if (!this.isCharacterImageModelUsable(resolvedModelName)) {
							throw new Error(this.tx('character_image_need_image_model', '当前选择的模型不是生图模型，请先去 AI 设置页点“获取列表”，再从返回列表里选文生图模型'));
						}
					}
					const aspectRatio = this.normalizeCharacterImageAspectRatio(this.currentCharacterImageConfig().aspectRatio);
					const consistencyMode = this.normalizeCharacterImageConsistencyMode(
						state.imageCharacterConsistencyMode
					);
					const referenceSourceMode = this.normalizeCharacterImageReferenceSourceMode(
						state.imageReferenceSourceMode
					);
					const characterId = Number(this.char && this.char.id) || Number(this.cid) || 0;
					const referenceTask = consistencyMode === 'free'
						? Promise.resolve('')
						: this.prepareCharacterImageReferencePayload(false, state);
					return referenceTask.then((referenceImageUrl) => {
						const payload = {
							clientUid: tavernApi.getClientUid(),
							imageRequestId: 'image_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10),
							prompt: this.buildCharacterImageGenerationPrompt(displayPrompt),
							userPrompt: displayPrompt,
							recentSceneHint: this.resolveCharacterImageRecentSceneHint(displayPrompt),
							count: 1,
							aspectRatio,
							...(customMode ? {
								modelName: resolvedModelName,
								providerSource: this.normalizeCharacterImageText(state.providerSource, 80)
							} : {}),
							characterId,
							characterName: this.normalizeCharacterImageText(this.char && this.char.name, 120),
							speakerMemberId: this.currentEnsembleSpeakerMemberId(),
							referenceImageUrl: referenceImageUrl || '',
							referenceMode: consistencyMode,
							referenceSourceMode,
							referencePolicy: consistencyMode === 'strong'
								? 'reference_only'
								: consistencyMode === 'balanced' ? 'balanced' : 'prompt_first'
						};
						return tavernApi.postImageGenerate(payload).then((data) => ({
							data,
							aspectRatio,
							fallbackWarning: this.normalizeCharacterImageText(data && data.warning, 200)
						}));
					});
				}).then(({ data, aspectRatio, fallbackWarning }) => {
					const images = Array.isArray(data && data.images) ? data.images : [];
					const first = images[0] && typeof images[0] === 'object' ? images[0] : null;
					const imageUrl = first && first.url ? String(first.url).trim() : '';
					if (!imageUrl) {
						throw new Error(this.tx('character_image_failed', '生图失败，请稍后再试'));
					}
					const localMessageId = 'img_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
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
					}).then((persisted) => {
						const persistedUrl = persisted && persisted.url ? String(persisted.url).trim() : imageUrl;
						const localMediaStore = require('@/common/localMediaStore.js');
						const mediaKey = ['image', this.resolveLocalExpressionViewerKey(), this.resolveLocalChatConversationId(), localMessageId, 0].join(':');
						const meta = {
							key: mediaKey,
							ownerKey: this.resolveLocalExpressionViewerKey(),
							conversationId: this.resolveLocalChatConversationId(),
							messageId: localMessageId,
							kind: 'generated_image',
							segmentIndex: 0,
							signature: this.localMediaSignature(displayPrompt)
						};
						const saveTask = this.isLocalInlineImageUrl(persistedUrl)
							? localMediaStore.putDataUrl(meta, persistedUrl)
							: localMediaStore.registerLocalUrl(meta, persistedUrl, Math.floor(imageUrl.length * 0.75), 'image/png');
						return saveTask.then((stored) => ({
							aspectRatio,
							messageId: localMessageId,
							imageUrl: stored && stored.url ? String(stored.url).trim() : persistedUrl,
							mediaKeys: [mediaKey],
							warning: this.normalizeCharacterImageText((data && data.warning) || fallbackWarning, 120)
						})).catch((error) => {
							if (!this.isAppPlus && imageUrl.length <= LOCAL_CHAT_IMAGE_DATA_URL_MAX_LENGTH) {
								return {
									aspectRatio,
									messageId: localMessageId,
									imageUrl,
									mediaKeys: [],
									warning: this.normalizeCharacterImageText((data && data.warning) || fallbackWarning, 120)
								};
							}
							throw error;
						});
					});
				}).then(({ imageUrl, aspectRatio, warning, messageId, mediaKeys }) => {
					const entry = this.upsertLocalChatImageEntry({
						messageId,
						assistantMessageId: this.resolveCharacterImageAnchorMessageId(),
						role: 'char',
						kind: 'image_generation',
						prompt: displayPrompt,
						aspectRatio,
						text: '',
						imageUrls: [imageUrl],
						mediaKeys,
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
				const recentPenaltyMap = this.buildRecentAssistantExpressionPenaltyMap(
					opts.recentLimit,
					opts.excludeMessageId
				);
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
				const sourceText = this.normalizeAssistantExpressionKeyword(payload.text);
				if (!sourceText) {
					return {
						entry: null,
						text: payload.text,
						via: ''
					};
				}
				let best = null;
				let bestScore = -Infinity;
				library.forEach((item) => {
					if (!item || !item.imageUrl) return;
					const keyword = this.normalizeAssistantExpressionKeyword(item.content || item.label);
					if (!keyword) return;
					if (this.isWeakAssistantExpressionHint(item.content || item.label)) return;
					const hitIndex = sourceText.indexOf(keyword);
					if (hitIndex < 0) return;
					const repeatPenalty = Math.max(0, Math.floor(Number(recentPenaltyMap[keyword]) || 0));
					const usageBoost = Math.min(Math.max(0, Math.floor(Number(item.useCount) || 0)), 18) * 20;
					const exactBoost = sourceText === keyword ? 900 : 0;
					const startBoost = hitIndex === 0 ? 180 : 0;
					const score =
						keyword.length * 1200 +
						exactBoost +
						startBoost -
						Math.min(hitIndex, 500) * 4 +
						usageBoost -
						repeatPenalty;
					if (score > bestScore) {
						best = item;
						bestScore = score;
					}
				});
				return {
					entry: best,
					text: payload.text,
					via: best ? 'content' : ''
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
			retryComposerImage(item) {
				if (!item || !item.id || !item.uploadFile || item.uploading) return;
				this.updateComposerImage(item.id, {
					uploading: true,
					progress: 0,
					error: ''
				});
				this.uploadComposerImage(item.id, item.uploadFile);
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
					branchId: m.branchId == null ? '' : String(m.branchId),
					role: m.role,
					text,
					openingMessage: m.openingMessage === true,
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
					voiceDurationMs: this.normalizeVoiceDurationMs(m.voiceDurationMs),
					speakerMemberId: Math.max(0, Math.floor(Number(m.speakerMemberId) || 0)),
					speakerName: this.normalizeCharacterVoiceText(m.speakerName, 64),
					speakerAvatarUrl: this.normalizeCharacterVoiceText(m.speakerAvatarUrl, 512)
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
				if (this.chatProgrammaticScrollTimer) {
					clearTimeout(this.chatProgrammaticScrollTimer);
					this.chatProgrammaticScrollTimer = null;
				}
				this.chatProgrammaticScroll = false;
				this.clearPendingFollowScroll();
				this.clearPendingVoiceStart();
				this.clearVoiceRecordTimer();
			},
			clearPendingFollowScroll() {
				if (this.chatFollowScrollTimer) {
					clearTimeout(this.chatFollowScrollTimer);
					this.chatFollowScrollTimer = null;
				}
			},
			resetChatUnreadState() {
				this.chatUnreadCount = 0;
				this.chatUnreadMessageKeyMap = {};
			},
			chatUnreadMessageKeys(row) {
				if (!row) return [];
				const id = this.normalizeDbMessageId(row.id);
				const keys = [];
				if (id) keys.push('id:' + id);
				const role = row.role ? String(row.role) : 'msg';
				const kind = row.messageKind ? String(row.messageKind) : '';
				const continueFrom = this.normalizeDbMessageId(row.continueFromMessageId);
				const text = String(row.text || '').slice(0, 240);
				const imageCount = Array.isArray(row.imageUrls) ? row.imageUrls.length : 0;
				const voiceUrl = this.normalizeVoiceMessageUrl(row.voiceUrl);
				const signature = [role, kind, continueFrom, text, imageCount, voiceUrl].join(':');
				if (kind || continueFrom || text || imageCount > 0 || voiceUrl) {
					keys.push('sig:' + signature);
				}
				return keys;
			},
			chatUnreadRowExistsInMap(row, map) {
				const source = map && typeof map === 'object' ? map : {};
				return this.chatUnreadMessageKeys(row).some((key) => !!source[key]);
			},
			collectChatMessageKeyMap(rows) {
				const map = {};
				(Array.isArray(rows) ? rows : []).forEach((row) => {
					this.chatUnreadMessageKeys(row).forEach((key) => {
						if (key) map[key] = true;
					});
				});
				return map;
			},
			shouldCountUnreadChatRow(row) {
				return !!row && row.role !== 'user';
			},
			rememberChatUnreadRows(rows) {
				const source = Array.isArray(rows) ? rows : rows ? [rows] : [];
				if (!source.length || this.atChatBottom || this.followBottom) return;
				const map = Object.assign({}, this.chatUnreadMessageKeyMap || {});
				let changed = false;
				source.forEach((row) => {
					if (!this.shouldCountUnreadChatRow(row)) return;
					this.chatUnreadMessageKeys(row).forEach((key) => {
						if (!key || map[key]) return;
						map[key] = true;
						changed = true;
					});
				});
				if (changed) {
					this.chatUnreadMessageKeyMap = map;
				}
			},
			markChatUnreadRows(rows) {
				const source = Array.isArray(rows) ? rows : rows ? [rows] : [];
				if (!source.length) return 0;
				if (this.atChatBottom || this.followBottom) {
					this.resetChatUnreadState();
					return 0;
				}
				const map = Object.assign({}, this.chatUnreadMessageKeyMap || {});
				let added = 0;
				source.forEach((row) => {
					if (!this.shouldCountUnreadChatRow(row)) return;
					const keys = this.chatUnreadMessageKeys(row);
					if (!keys.length || this.chatUnreadRowExistsInMap(row, map)) return;
					keys.forEach((key) => {
						map[key] = true;
					});
					added += 1;
				});
				if (added > 0) {
					this.chatUnreadMessageKeyMap = map;
					this.chatUnreadCount = Math.min(999, Number(this.chatUnreadCount || 0) + added);
				}
				return added;
			},
			handleIncomingChatRows(rows, options) {
				const opts = options && typeof options === 'object' ? options : {};
				if (this.atChatBottom || this.followBottom) {
					this.followScrollNextTick();
					return;
				}
				if (opts.alreadyCounted) {
					this.rememberChatUnreadRows(rows);
					return;
				}
				this.markChatUnreadRows(rows);
			},
			scrollChatToBottom(options) {
				const opts = options || {};
				const immediate = opts.immediate !== false;
				const reveal = !!opts.reveal;
				this.clearPendingFollowScroll();
				if (this.chatAnimationTimer) {
					clearTimeout(this.chatAnimationTimer);
					this.chatAnimationTimer = null;
				}
				if (this.chatRevealTimer) {
					clearTimeout(this.chatRevealTimer);
					this.chatRevealTimer = null;
				}
				this.followBottom = true;
				this.atChatBottom = true;
				this.resetChatUnreadState();
				this.lastChatScrollTop = Number.MAX_SAFE_INTEGER;
				this.markChatAutoScroll();
				this.markChatProgrammaticScroll();
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
				if (this.chatFollowScrollTimer) return;
				this.chatFollowScrollTimer = setTimeout(() => {
					this.chatFollowScrollTimer = null;
					if (!this.followBottom || this.chatUserTouching) return;
					this.scrollChatToBottom({ immediate: true });
				}, 80);
			},
			showTypingHintRow() {
				return !!this.sending && !this.streamingAssistantMessageId;
			},
			createStreamAbortController() {
				if (typeof AbortController === 'function') {
					return new AbortController();
				}
				const listeners = [];
				const signal = {
					aborted: false,
					addEventListener(eventName, handler) {
						if (eventName === 'abort' && typeof handler === 'function') {
							listeners.push(handler);
						}
					},
					removeEventListener(eventName, handler) {
						if (eventName !== 'abort') return;
						const index = listeners.indexOf(handler);
						if (index >= 0) {
							listeners.splice(index, 1);
						}
					}
				};
				return {
					signal,
					abort() {
						if (signal.aborted) return;
						signal.aborted = true;
						listeners.slice().forEach((handler) => {
							try {
								handler();
							} catch (e) {}
						});
					}
				};
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
			finalizeAssistantStreamRequest(controller) {
				if (!controller || this.streamAbortController !== controller) return;
				this.finishAssistantStreaming();
				this.streamAbortController = null;
				this.finishSendingState();
			},
			isStreamingAssistantRow(row) {
				if (!row || row.role !== 'char') return false;
				const messageId = this.normalizeDbMessageId(row.id);
				return !!messageId && messageId === this.streamingAssistantMessageId;
			},
			messageActionsVisible(row) {
				return (
					this.isStreamingAssistantRow(row) ||
					this.shouldShowAssistantVoicePill(row) ||
					(this.isAssistantMessage(row) && row.swipes && row.swipes.length > 1) ||
					!!this.recoveryForMessage(row)
				);
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
			markChatProgrammaticScroll() {
				this.chatProgrammaticScroll = true;
				if (this.chatProgrammaticScrollTimer) {
					clearTimeout(this.chatProgrammaticScrollTimer);
				}
				this.chatProgrammaticScrollTimer = setTimeout(() => {
					this.chatProgrammaticScroll = false;
					this.chatProgrammaticScrollTimer = null;
				}, 240);
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
				const likelyUserScrollUp = this.chatUserTouching && (movedUp || fallbackManualMove);
				if (likelyUserScrollUp || (isManualWindow && movedUp && !this.chatProgrammaticScroll)) {
					this.followBottom = false;
					this.atChatBottom = false;
					this.clearPendingFollowScroll();
					this.closeExpressionPanel();
				}
				if (hasTop && Number.isFinite(height) && Number.isFinite(clientHeight)) {
					const distanceToBottom = height - clientHeight - top;
					if (distanceToBottom <= 64) {
						this.followBottom = true;
						this.atChatBottom = true;
						this.resetChatUnreadState();
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
				this.resetChatUnreadState();
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
				this.recoverStaleSendingState();
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
			beginSendingState() {
				this.sendingStartedAt = Date.now();
				this.sending = true;
			},
			finishSendingState() {
				this.sending = false;
				this.sendingStartedAt = 0;
			},
			recoverStaleSendingState() {
				if (!this.sending) {
					this.sendingStartedAt = 0;
					return false;
				}
				if (this.streamingAssistantMessageId) {
					return false;
				}
				const startedAt = Number(this.sendingStartedAt || 0);
				const staleMs = 180000;
				if (!startedAt) {
					this.sendingStartedAt = Date.now();
					return false;
				}
				if (Date.now() - startedAt > staleMs) {
					this.finishSendingState();
					this.streamAbortController = null;
					return true;
				}
				return false;
			},
			clearStopSyncTimer() {
				this.stopSyncVersion = Number(this.stopSyncVersion || 0) + 1;
				if (this.stopRefreshTimer) {
					clearTimeout(this.stopRefreshTimer);
					this.stopRefreshTimer = null;
				}
			},
			queueStopSync(delay) {
				if (!this.jgOn || !this.char) return;
				this.clearStopSyncTimer();
				const syncVersion = this.stopSyncVersion;
				const firstDelay = typeof delay === 'number' ? delay : 700;
				const retryDelays = [firstDelay, 1500, 3000];
				let retryIndex = 0;
				const scheduleNext = () => {
					if (syncVersion !== this.stopSyncVersion || retryIndex >= retryDelays.length) return;
					const retryDelay = retryDelays[retryIndex++];
					this.stopRefreshTimer = setTimeout(() => {
						this.stopRefreshTimer = null;
						Promise.resolve()
							.then(() => this.refreshJgMessages({ trackIncomingUnread: true }))
							.catch(() => {})
							.finally(scheduleNext);
					}, retryDelay);
				};
				scheduleNext();
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
				if (!opts.skipSync) {
					this.queueStopSync(700);
				}
				const streamController = this.streamAbortController;
				const streamingMessageId = this.streamingAssistantMessageId;
				if (streamController) {
					this.streamAbortController = null;
					try {
						streamController.abort();
					} catch (err) {}
					this.finishAssistantStreaming(streamingMessageId);
					this.finishSendingState();
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
								if (!opts.skipSync) {
									this.queueStopSync(900);
								}
							})
							.catch(function () {});
					}
				} catch (e) {}
			},
			openBranchPanel() {
				if (!this.jgOn || this.jgChatLoadState !== 'ready') {
					this.showErrorToast(this.tx('chat_not_ready', '聊天还没有准备好'));
					return;
				}
				this.closeMemoryPanel();
				this.branchPanel = Object.assign({}, this.branchPanel, {
					visible: true,
					error: '',
					switchingBranchId: ''
				});
				this.loadBranchPanel();
				this.loadStorySessions();
			},
			closeBranchPanel() {
				this.branchPanel = createBranchPanelState();
			},
			branchPanelPayload() {
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				return {
					characterId: cid,
					clientUid: tavernApi.getClientUid()
				};
			},
			normalizeBranchPanelRow(item, kind) {
				const source = item && typeof item === 'object' ? item : {};
				if (kind === 'session') {
					const conversationId = source.id == null ? '' : String(source.id);
					return {
						id: 'session_' + conversationId,
						conversationId,
						kind: 'session',
						title: String(source.displayTitle || source.nickname || '未命名故事'),
						active: conversationId === String(this.branchPanel.activeConversationId || this.jgConversationId || ''),
						created: true,
						messageCount: 0,
						preview: this.compactPanelText(source.snippet || source.lastMessage || '', 180),
						updatedAt: source.updatedAt || ''
					};
				}
				if (kind === 'branch') {
					const branchId = source.id == null ? '' : String(source.id);
					return {
						id: branchId,
						branchId,
						variantIndex: -1,
						kind: 'branch',
						title: String(source.title || (source.defaultBranch ? '主线' : '剧情分支')),
						active: source.active === true || branchId === String(this.jgActiveBranchId || ''),
						created: true,
						messageCount: Number(source.messageCount || 0),
						preview: this.compactPanelText(source.lastMessagePreview || '', 180),
						updatedAt: source.updatedAt || '',
						parentBranchId: source.parentBranchId == null ? '' : String(source.parentBranchId),
						defaultBranch: source.defaultBranch === true,
						depth: 0
					};
				}
				const variantIndex = Number(source.variantIndex);
				const safeVariantIndex = isFinite(variantIndex) && variantIndex >= 0 ? Math.floor(variantIndex) : -1;
				const branchId = source.branchId == null ? '' : String(source.branchId);
				const id = safeVariantIndex >= 0 ? 'opening_' + safeVariantIndex : branchId;
				const title = String(source.title || this
					.tx('branch_opening_item', '开场 {n}')
					.replace('{n}', String(safeVariantIndex >= 0 ? safeVariantIndex + 1 : '')));
				return {
					id,
					branchId,
					variantIndex: safeVariantIndex,
					kind: 'opening',
					title,
					active: source.active === true || (branchId && branchId === String(this.jgActiveBranchId || '')),
					created: source.created === true || !!branchId,
					messageCount: Number(source.messageCount || 0),
					preview: this.compactPanelText(source.preview || '', 180),
					updatedAt: source.updatedAt || ''
				};
			},
			sortBranchPanelTree(rows) {
				const source = Array.isArray(rows) ? rows : [];
				const byParent = {};
				source.forEach(row => {
					const parentId = row.parentBranchId && source.some(item => item.id === row.parentBranchId)
						? row.parentBranchId
						: '';
					if (!byParent[parentId]) byParent[parentId] = [];
					byParent[parentId].push(row);
				});
				const result = [];
				const visited = {};
				const visit = (parentId, depth) => {
					(byParent[parentId] || []).forEach(row => {
						if (visited[row.id]) return;
						visited[row.id] = true;
						row.depth = Math.min(depth, 4);
						result.push(row);
						visit(row.id, depth + 1);
					});
				};
				visit('', 0);
				source.forEach(row => { if (!visited[row.id]) { row.depth = 0; result.push(row); } });
				return result;
			},
			openBranchRename(row) {
				if (!row || (row.kind !== 'branch' && row.kind !== 'session')) return;
				this.$set(this.branchPanel, 'editorVisible', true);
				this.$set(this.branchPanel, 'editorKind', row.kind);
				this.$set(this.branchPanel, 'editorId', row.kind === 'session' ? String(row.conversationId || '') : String(row.branchId || ''));
				this.$set(this.branchPanel, 'editorTitle', String(row.title || ''));
			},
			closeBranchRename() {
				if (!this.branchPanel || this.branchPanel.editing) return;
				this.$set(this.branchPanel, 'editorVisible', false);
				this.$set(this.branchPanel, 'editorKind', '');
				this.$set(this.branchPanel, 'editorId', '');
				this.$set(this.branchPanel, 'editorTitle', '');
			},
			saveBranchRename() {
				if (!this.branchPanel || this.branchPanel.editing) return;
				const title = String(this.branchPanel.editorTitle || '').trim();
				if (!title) { this.showErrorToast('请输入名称'); return; }
				const tavernApi = require('@/common/tavernApi.js');
				const kind = this.branchPanel.editorKind;
				const id = Number(this.branchPanel.editorId);
				if (!id) return;
				this.$set(this.branchPanel, 'editing', true);
				const request = kind === 'session'
					? tavernApi.postTavernSessionRename(Object.assign({}, this.branchPanelPayload(), { conversationId: id, title }))
					: tavernApi.postTavernBranchRename(Object.assign({}, this.branchPanelPayload(), { branchId: id, title }));
				return request.then(data => {
					if (kind === 'branch') this.applyBranchEnvelope(data);
					this.$set(this.branchPanel, 'editorVisible', false);
					return kind === 'session' ? this.loadStorySessions() : true;
				}).then(() => {
					uni.showToast({ title: '名称已更新', icon: 'none' });
				}).catch(e => this.showErrorToast(this.jgErrMsg(e, '重命名失败')))
					.finally(() => { if (this.branchPanel) this.$set(this.branchPanel, 'editing', false); });
			},
			deleteManagedBranch(row) {
				if (!row || row.kind !== 'branch' || row.defaultBranch || this.sending) return;
				uni.showModal({
					title: '删除剧情分支',
					content: '将隐藏这个分支，子分支会保留并接到上一级。确定删除吗？',
					confirmText: '删除',
					confirmColor: '#b45353',
					success: result => {
						if (!result.confirm) return;
						const tavernApi = require('@/common/tavernApi.js');
						const wasActive = row.active === true;
						this.$set(this.branchPanel, 'deletingBranchId', String(row.id));
						tavernApi.postTavernBranchDelete(Object.assign({}, this.branchPanelPayload(), { branchId: Number(row.branchId) }))
							.then(data => {
								this.applyBranchEnvelope(data);
								if (!wasActive) return true;
								this.resetMessageHistoryForBranch();
								this.refreshChatModelCatalog(true);
								return this.refreshJgMessages({ invalidateReplySuggestions: true });
							})
							.then(() => uni.showToast({ title: '分支已删除', icon: 'none' }))
							.catch(e => this.showErrorToast(this.jgErrMsg(e, '删除分支失败')))
							.finally(() => { if (this.branchPanel) this.$set(this.branchPanel, 'deletingBranchId', ''); });
					}
				});
			},
			deleteStorySession(row) {
				if (!row || row.kind !== 'session' || row.active || this.sending || this.branchPanel.deletingBranchId) return;
				uni.showModal({
					title: '删除独立故事',
					content: '这个故事的消息和记忆会被清空，无法恢复。确定删除吗？',
					confirmText: '删除',
					confirmColor: '#b45353',
					success: result => {
						if (!result.confirm) return;
						const tavernApi = require('@/common/tavernApi.js');
						this.$set(this.branchPanel, 'deletingBranchId', String(row.id));
						tavernApi.postTavernSessionDeleteOne(Object.assign({}, this.branchPanelPayload(), {
							conversationId: Number(row.conversationId)
						}))
							.then(() => this.loadStorySessions())
							.then(() => uni.showToast({ title: '故事已删除', icon: 'none' }))
							.catch(e => this.showErrorToast(this.jgErrMsg(e, '删除故事失败')))
							.finally(() => { if (this.branchPanel) this.$set(this.branchPanel, 'deletingBranchId', ''); });
					}
				});
			},
			applyBranchEnvelope(data) {
				const source = data && typeof data === 'object' ? data : {};
				const openings = Array.isArray(source.openings) ? source.openings : [];
				const branches = Array.isArray(source.branches) ? source.branches : [];
				this.jgActiveBranchId = source.activeBranchId == null ? this.jgActiveBranchId : String(source.activeBranchId);
				if (this.branchPanel && this.branchPanel.visible) {
					this.$set(this.branchPanel, 'openings', openings);
					this.$set(this.branchPanel, 'branches', branches);
					this.$set(this.branchPanel, 'error', '');
				}
				return openings;
			},
			loadBranchPanel() {
				if (!this.jgOn || !this.char) return Promise.resolve(false);
				const tavernApi = require('@/common/tavernApi.js');
				if (this.branchPanel && this.branchPanel.visible) {
					this.$set(this.branchPanel, 'loading', true);
					this.$set(this.branchPanel, 'error', '');
				}
				return tavernApi
					.postTavernBranchList(this.branchPanelPayload())
					.then((data) => {
						this.applyBranchEnvelope(data);
						return true;
					})
					.catch((e) => {
						const msg = this.jgErrMsg(e, this.tx('branch_load_failed_retry', '加载失败，点这里重试'));
						if (this.branchPanel && this.branchPanel.visible) {
							this.$set(this.branchPanel, 'error', msg);
						}
						return false;
					})
					.finally(() => {
						if (this.branchPanel && this.branchPanel.visible) {
							this.$set(this.branchPanel, 'loading', false);
						}
					});
			},
			loadStorySessions() {
				if (!this.jgOn || !this.char) return Promise.resolve(false);
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				return tavernApi.fetchTavernCharacterSessions(cid, tavernApi.getClientUid())
					.then(data => {
						if (!this.branchPanel || !this.branchPanel.visible) return false;
						this.$set(this.branchPanel, 'sessions', Array.isArray(data && data.sessions) ? data.sessions : []);
						this.$set(this.branchPanel, 'activeConversationId', data && data.activeConversationId != null ? String(data.activeConversationId) : '');
						return true;
					})
					.catch(() => false);
			},
			createStorySession() {
				if (this.sending || this.branchPanel.loading) return;
				const tavernApi = require('@/common/tavernApi.js');
				this.$set(this.branchPanel, 'loading', true);
				tavernApi.postTavernSessionCreate(Object.assign({}, this.branchPanelPayload(), { title: '新故事' }))
					.then(data => {
						this.jgConversationId = data && data.conversationId != null ? String(data.conversationId) : this.jgConversationId;
						this.jgActiveBranchId = data && data.activeBranchId != null ? String(data.activeBranchId) : '';
						this.resetMessageHistoryForBranch();
						this.refreshChatModelCatalog(true);
						return this.refreshJgMessages({ invalidateReplySuggestions: true });
					})
					.then(() => Promise.all([this.loadStorySessions(), this.loadBranchPanel()]))
					.then(() => { this.$set(this.branchPanel, 'mode', 'openings'); uni.showToast({ title: '新故事已创建，请选择开场', icon: 'none' }); })
					.catch(e => this.showErrorToast(this.jgErrMsg(e, '新建故事失败')))
					.finally(() => { if (this.branchPanel && this.branchPanel.visible) this.$set(this.branchPanel, 'loading', false); });
			},
			resetMessageHistoryForBranch() {
				this.jgLoadRequestToken = Date.now() + Math.random();
				this.messageHistoryHasMore = false;
				this.messageHistoryLoading = false;
				this.messageHistoryNextBeforeId = '';
				this.messageHistoryLoadAt = Date.now();
				this.chatUnreadCount = 0;
				this.chatUnreadMessageKeyMap = {};
			},
			switchBranch(row) {
				if (!row || row.active || this.sending || this.branchPanel.deletingBranchId) {
					if (row && row.active) {
						this.closeBranchPanel();
					}
					return Promise.resolve(false);
				}
				if (row.kind === 'session') {
					const tavernApi = require('@/common/tavernApi.js');
					this.$set(this.branchPanel, 'switchingBranchId', String(row.id || ''));
					return tavernApi.postTavernSessionActivate(Object.assign({}, this.branchPanelPayload(), { conversationId: Number(row.conversationId) }))
						.then(data => {
							this.jgConversationId = data && data.conversationId != null ? String(data.conversationId) : String(row.conversationId);
							this.jgActiveBranchId = data && data.activeBranchId != null ? String(data.activeBranchId) : '';
							this.resetMessageHistoryForBranch();
							this.refreshChatModelCatalog(true);
							return this.refreshJgMessages({ invalidateReplySuggestions: true });
						})
						.then(() => { this.closeBranchPanel(); this.scrollChatToBottom({ immediate: true }); return true; })
						.catch(e => { this.showErrorToast(this.jgErrMsg(e, '切换会话失败')); return false; });
				}
				const variantIndex = Number(row.variantIndex);
				if (row.kind !== 'branch' && (!isFinite(variantIndex) || variantIndex < 0)) return Promise.resolve(false);
				const tavernApi = require('@/common/tavernApi.js');
				this.$set(this.branchPanel, 'switchingBranchId', String(row.id || ''));
				const request = row.kind === 'branch'
					? tavernApi.postTavernBranchSwitch(Object.assign({}, this.branchPanelPayload(), { branchId: Number(row.branchId) }))
					: tavernApi.postTavernOpeningBranchSelect(Object.assign({}, this.branchPanelPayload(), { variantIndex }));
				return request
					.then((data) => {
						this.applyBranchEnvelope(data);
						this.resetMessageHistoryForBranch();
						this.refreshChatModelCatalog(true);
						return this.refreshJgMessages({ invalidateReplySuggestions: true });
					})
					.then(() => {
						this.closeBranchPanel();
						this.followBottom = true;
						this.atChatBottom = true;
						this.scrollChatToBottom({ immediate: true });
						return true;
					})
					.catch((e) => {
						this.showErrorToast(this.jgErrMsg(e, this.tx('branch_switch_failed', '分支切换失败')));
						return false;
					})
					.finally(() => {
						if (this.branchPanel && this.branchPanel.visible) {
							this.$set(this.branchPanel, 'switchingBranchId', '');
						}
					});
			},
			forkBranchFromMessage(messageId, options) {
				if (this.sending) {
					this.showErrorToast(this.tx('branch_fork_wait', '等这轮回复完成后再开分支'));
					return Promise.resolve(false);
				}
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return Promise.resolve(false);
				const opts = options && typeof options === 'object' ? options : {};
				const tavernApi = require('@/common/tavernApi.js');
				const payload = Object.assign({}, this.branchPanelPayload(), {
					messageId: safeId
				});
				if (opts.variantIndex != null) {
					payload.variantIndex = opts.variantIndex;
				}
				if (opts.title) {
					payload.title = opts.title;
				}
				if (this.branchPanel && this.branchPanel.visible) {
					this.$set(this.branchPanel, 'forkingMessageId', safeId);
				}
				return tavernApi
					.postTavernBranchFork(payload)
					.then((data) => {
						this.applyBranchEnvelope(data);
						this.resetMessageHistoryForBranch();
						this.refreshChatModelCatalog(true);
						return this.refreshJgMessages({ invalidateReplySuggestions: true });
					})
					.then(() => {
						this.closeBranchPanel();
						this.followBottom = true;
						this.atChatBottom = true;
						this.scrollChatToBottom({ immediate: true });
						uni.showToast({ title: this.tx('branch_fork_success', '已开新分支'), icon: 'none' });
						return true;
					})
					.catch((e) => {
						this.showErrorToast(this.jgErrMsg(e, this.tx('branch_fork_failed', '开分支失败')));
						return false;
					})
					.finally(() => {
						if (this.branchPanel && this.branchPanel.visible) {
							this.$set(this.branchPanel, 'forkingMessageId', '');
						}
					});
			},
			forkMessageActionBranch() {
				if (!this.messageActionSheet || this.messageActionSheet.forking) return;
				const messageId = this.normalizeDbMessageId(this.messageActionSheet.messageId);
				const message = this.findMessageById(messageId);
				if (!message || !this.canForkChatBranchFromMessage(message)) {
					this.showErrorToast(this.tx('branch_fork_unavailable', '这条消息暂时不能开分支'));
					return;
				}
				this.$set(this.messageActionSheet, 'forking', true);
				const variantIndex = message.role === 'char' && typeof message.swipeIndex === 'number'
					? message.swipeIndex
					: null;
				this.forkBranchFromMessage(messageId, { variantIndex })
					.finally(() => {
						if (this.messageActionSheet && this.messageActionSheet.messageId === messageId) {
							this.$set(this.messageActionSheet, 'forking', false);
						}
						this.closeMessageActionSheet(true);
					});
			},
			findMessageByPanelId(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return null;
				return (this.messages || []).find((row) => this.normalizeDbMessageId(row && row.id) === safeId) || null;
			},
			jumpToBranchMessage(messageId) {
				const safeId = this.normalizeDbMessageId(messageId);
				if (!safeId) return;
				this.restoreChatViewportAtMessage(safeId);
			},
			memoryPanelPayload(overrides) {
				const panel = this.memoryPanel || {};
				const options = overrides && typeof overrides === 'object' ? overrides : {};
				const filter = ['all', 'enabled', 'disabled', 'archived'].includes(String(options.filter || panel.filter || 'all'))
					? String(options.filter || panel.filter || 'all')
					: 'all';
				const requestedPage = Number(options.page != null ? options.page : panel.page || 1);
				const requestedPageSize = Number(options.pageSize != null ? options.pageSize : panel.pageSize || MEMORY_PANEL_PAGE_SIZE);
				return {
					clientUid: String(panel.clientUid || ''),
					characterId: Number(panel.characterId) || 0,
					conversationId: Number(panel.conversationId) || 0,
					branchId: Number(panel.activeBranchId) || 0,
					entryFilter: filter,
					filter,
					page: isFinite(requestedPage) && requestedPage > 0 ? Math.floor(requestedPage) : 1,
					pageSize: isFinite(requestedPageSize) && requestedPageSize > 0 ? Math.floor(requestedPageSize) : MEMORY_PANEL_PAGE_SIZE
				};
			},
			normalizeMemoryPanelEntry(raw) {
				const source = raw && typeof raw === 'object' ? raw : {};
				const keywords = Array.isArray(source.keywords) ? source.keywords : [];
				const secondaryKeywords = Array.isArray(source.secondaryKeywords) ? source.secondaryKeywords : [];
				const archiveReason = String(source.archiveReason || source.archivedReason || source.retiredReason || '');
				const archivedAt = source.archivedAt || source.retiredAt || '';
				const archived = source.archived === true
					|| source.retired === true
					|| !!archiveReason
					|| !!archivedAt
					|| String(source.status || '').toUpperCase() === 'ARCHIVED';
				return {
					id: source.id,
					entryKey: String(source.entryKey || ''),
					memoryType: String(source.memoryType || 'event'),
					title: String(source.title || ''),
					content: String(source.content || ''),
					keywords: keywords.map((item) => String(item || '')).filter((item) => item),
					secondaryKeywords: secondaryKeywords.map((item) => String(item || '')).filter((item) => item),
					priority: Number(source.priority || 0),
					position: String(source.position || ''),
					constantInjection: source.constantInjection === true,
					selective: source.selective === true,
					enabled: source.enabled === true,
					manualDisabled: source.manualDisabled === true,
					manualPinned: source.manualPinned === true,
					archived,
					archiveReason,
					archivedAt,
					confidence: source.confidence == null ? null : Number(source.confidence),
					sourceMessageFromId: source.sourceMessageFromId,
					sourceMessageToId: source.sourceMessageToId
				};
			},
			memoryPanelEntryIdentity(entry) {
				const source = entry && typeof entry === 'object' ? entry : {};
				if (source.id != null && String(source.id)) return 'id:' + String(source.id);
				if (source.entryKey) return 'key:' + String(source.entryKey);
				return 'content:' + String(source.memoryType || '') + ':' + String(source.content || source.title || '').trim().toLowerCase();
			},
			mergeMemoryPanelEntries(currentEntries, incomingEntries) {
				const merged = [];
				const indexes = Object.create(null);
				const append = (entry) => {
					if (!entry) return;
					const identity = this.memoryPanelEntryIdentity(entry);
					if (Object.prototype.hasOwnProperty.call(indexes, identity)) {
						const index = indexes[identity];
						merged.splice(index, 1, Object.assign({}, merged[index], entry));
						return;
					}
					indexes[identity] = merged.length;
					merged.push(entry);
				};
				(Array.isArray(currentEntries) ? currentEntries : []).forEach(append);
				(Array.isArray(incomingEntries) ? incomingEntries : []).forEach(append);
				return merged;
			},
			filterLegacyMemoryEntries(entries, filter) {
				const safeEntries = Array.isArray(entries) ? entries : [];
				if (filter === 'archived') return safeEntries.filter((entry) => entry && entry.archived);
				if (filter === 'enabled') return safeEntries.filter((entry) => entry && !entry.archived && entry.enabled);
				if (filter === 'disabled') return safeEntries.filter((entry) => entry && !entry.archived && !entry.enabled);
				return safeEntries;
			},
			applyMemoryPanelMetadata(detail) {
				const source = detail && typeof detail === 'object' ? detail : {};
				const previous = this.memoryPanel && this.memoryPanel.detail && typeof this.memoryPanel.detail === 'object'
					? this.memoryPanel.detail
					: {};
				const metadata = Object.assign({}, previous, source);
				delete metadata.entries;
				this.$set(this.memoryPanel, 'detail', metadata);
				this.jgMemory = Object.assign({}, this.jgMemory || {}, {
					summaryPreview: metadata.summaryPreview || '',
					factsCount: metadata.factsCount || 0,
					entryCount: metadata.entryCount || 0,
					enabledEntryCount: metadata.enabledEntryCount || 0,
					memoryWorldName: metadata.memoryWorldName || '',
					syncStatus: metadata.syncStatus || '',
					syncError: metadata.syncError || '',
					updatedAt: metadata.updatedAt || ''
				});
			},
			applyMemoryPanelDetail(detail, options) {
				const source = detail && typeof detail === 'object' ? detail : {};
				const opts = options && typeof options === 'object' ? options : {};
				const filter = ['all', 'enabled', 'disabled', 'archived'].includes(String(opts.filter || ''))
					? String(opts.filter)
					: String(this.memoryPanel && this.memoryPanel.filter || 'all');
				const normalizedEntries = Array.isArray(source.entries)
					? source.entries.map((item) => this.normalizeMemoryPanelEntry(item)).filter((item) => item.content || item.title)
					: [];
				const hasPagination = source.entryPage != null
					|| source.entryPageSize != null
					|| source.entryTotal != null
					|| typeof source.entryHasMore === 'boolean'
					|| source.page != null
					|| source.pageSize != null
					|| source.totalEntries != null
					|| typeof source.hasMore === 'boolean';
				const pageEntries = hasPagination
					? normalizedEntries
					: this.filterLegacyMemoryEntries(normalizedEntries, filter);
				const entries = opts.append
					? this.mergeMemoryPanelEntries(this.memoryPanel.entries, pageEntries)
					: pageEntries;
				const requestedPage = Number(opts.page || 1);
				const responsePage = Number(source.entryPage != null ? source.entryPage : source.page);
				const responsePageSize = Number(source.entryPageSize != null ? source.entryPageSize : source.pageSize);
				const responseTotal = Number(source.entryTotal != null ? source.entryTotal : source.totalEntries);
				const page = hasPagination && isFinite(responsePage) && responsePage > 0
					? Math.floor(responsePage)
					: (hasPagination ? Math.max(1, Math.floor(requestedPage || 1)) : 1);
				const pageSize = hasPagination && isFinite(responsePageSize) && responsePageSize > 0
					? Math.floor(responsePageSize)
					: Number(this.memoryPanel.pageSize || MEMORY_PANEL_PAGE_SIZE);
				const totalEntries = hasPagination && isFinite(responseTotal) && responseTotal >= 0
					? Math.floor(responseTotal)
					: entries.length;
				const responseHasMore = typeof source.entryHasMore === 'boolean' ? source.entryHasMore : source.hasMore;
				const hasMore = hasPagination
					? (typeof responseHasMore === 'boolean' ? responseHasMore : page * pageSize < totalEntries)
					: false;
				const metadata = Object.assign({}, source);
				if (!hasPagination) {
					const archived = normalizedEntries.filter((entry) => entry.archived).length;
					const activeEntries = normalizedEntries.filter((entry) => !entry.archived);
					const enabled = activeEntries.filter((entry) => entry.enabled).length;
					if (metadata.entryCount == null) metadata.entryCount = activeEntries.length;
					if (metadata.enabledEntryCount == null) metadata.enabledEntryCount = enabled;
					if (metadata.disabledEntryCount == null) metadata.disabledEntryCount = activeEntries.filter((entry) => !entry.enabled).length;
					if (metadata.archivedEntryCount == null) metadata.archivedEntryCount = archived;
				}
				this.applyMemoryPanelMetadata(metadata);
				this.$set(this.memoryPanel, 'entries', entries);
				this.$set(this.memoryPanel, 'page', page);
				this.$set(this.memoryPanel, 'pageSize', pageSize);
				this.$set(this.memoryPanel, 'totalEntries', totalEntries);
				this.$set(this.memoryPanel, 'hasMore', hasMore);
			},
			openMemoryPanel() {
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing) {
					return;
				}
				if (!this.jgOn || this.jgChatLoadState !== 'ready' || !this.char) {
					this.showErrorToast(this.tx('chat_not_ready', '聊天还没有准备好'));
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				const clientUid = String(tavernApi.getClientUid() || '').trim();
				const characterId = Number(this.char && this.char.id) || Number(this.cid);
				const conversationId = Number(this.jgConversationId);
				const activeBranchId = Number(this.jgActiveBranchId);
				if (!clientUid || !characterId || !conversationId || !activeBranchId) {
					this.showErrorToast(this.tx('chat_not_ready', '聊天还没有准备好'));
					return;
				}
				this.closeBranchPanel();
				this.memoryPanel = Object.assign(createMemoryPanelState(), {
					visible: true,
					clientUid,
					characterId,
					conversationId,
					activeBranchId,
					error: ''
				});
				this.loadMemoryPanel();
			},
			closeMemoryPanel() {
				this.memoryPanel = createMemoryPanelState();
			},
			isMemoryPanelRequestCurrent(requestToken) {
				return !!(
					this.memoryPanel
					&& this.memoryPanel.visible
					&& this.memoryPanel.requestToken === requestToken
				);
			},
			isMemoryPanelListRequestCurrent(requestToken, listRequestToken, filter) {
				return !!(
					this.isMemoryPanelRequestCurrent(requestToken)
					&& this.memoryPanel.listRequestToken === listRequestToken
					&& String(this.memoryPanel.filter || 'all') === String(filter || 'all')
				);
			},
			loadMemoryPanel(options) {
				if (!this.memoryPanel || !this.memoryPanel.visible) return Promise.resolve(false);
				const opts = options && typeof options === 'object' ? options : {};
				const append = opts.append === true;
				if (append && (this.memoryPanel.loading || this.memoryPanel.loadingMore || !this.memoryPanel.hasMore)) {
					return Promise.resolve(false);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const requestToken = this.memoryPanel.requestToken;
				const filter = String(this.memoryPanel.filter || 'all');
				const targetPage = append ? Math.max(1, Number(this.memoryPanel.page || 0) + 1) : 1;
				const listRequestToken = ++memoryPanelListRequestSeed;
				this.$set(this.memoryPanel, 'listRequestToken', listRequestToken);
				if (append) {
					this.$set(this.memoryPanel, 'loadingMore', true);
					this.$set(this.memoryPanel, 'loadMoreError', '');
				} else {
					this.$set(this.memoryPanel, 'loading', true);
					this.$set(this.memoryPanel, 'error', '');
					this.$set(this.memoryPanel, 'loadMoreError', '');
					this.$set(this.memoryPanel, 'entries', []);
					this.$set(this.memoryPanel, 'page', 0);
					this.$set(this.memoryPanel, 'totalEntries', 0);
					this.$set(this.memoryPanel, 'hasMore', false);
				}
				return tavernApi.postTavernMemoryEntries(this.memoryPanelPayload({
					filter,
					page: targetPage,
					pageSize: this.memoryPanel.pageSize || MEMORY_PANEL_PAGE_SIZE
				}))
					.then((detail) => {
						if (!this.isMemoryPanelListRequestCurrent(requestToken, listRequestToken, filter)) return false;
						this.applyMemoryPanelDetail(detail || {}, { append, page: targetPage, filter });
						return true;
					})
					.catch((e) => {
						if (!this.isMemoryPanelListRequestCurrent(requestToken, listRequestToken, filter)) return false;
						if (append) {
							const message = this.jgErrMsg(e, this.tx('memory_load_more_failed', '更多记忆加载失败，轻触此处重试'));
							this.$set(this.memoryPanel, 'loadMoreError', message);
						} else {
							const message = this.jgErrMsg(e, this.tx('memory_load_failed', '记忆读取失败'));
							this.$set(this.memoryPanel, 'error', message);
						}
						return false;
					})
					.finally(() => {
						if (this.isMemoryPanelListRequestCurrent(requestToken, listRequestToken, filter)) {
							this.$set(this.memoryPanel, append ? 'loadingMore' : 'loading', false);
						}
					});
			},
			loadMoreMemoryPanel() {
				if (!this.memoryPanel || !this.memoryPanel.visible || this.memoryPanelBusy || !this.memoryPanel.hasMore) {
					return Promise.resolve(false);
				}
				return this.loadMemoryPanel({ append: true });
			},
			setMemoryPanelFilter(filter) {
				if (!this.memoryPanel || !this.memoryPanel.visible) return Promise.resolve(false);
				if (this.memoryPanel.refreshing || this.memoryPanel.syncing || this.memoryPanel.updatingEntryId || this.memoryPanel.deletingEntryId) {
					return Promise.resolve(false);
				}
				const safeFilter = ['all', 'enabled', 'disabled', 'archived'].includes(String(filter || '')) ? String(filter) : 'all';
				if (safeFilter === this.memoryPanel.filter && this.memoryPanel.page > 0) return Promise.resolve(true);
				this.$set(this.memoryPanel, 'filter', safeFilter);
				return this.loadMemoryPanel({ append: false });
			},
			openMemoryEditor(entry) {
				if (!this.memoryPanel || !this.memoryPanel.visible || this.memoryPanelBusy) return;
				const source = entry && typeof entry === 'object' ? entry : {};
				if (source.archived) {
					uni.showToast({ title: '归档记忆不能编辑', icon: 'none' });
					return;
				}
				const type = ['identity', 'relationship', 'preference', 'promise', 'event', 'setting', 'boundary']
					.includes(String(source.memoryType || '')) ? String(source.memoryType) : 'event';
				this.$set(this.memoryPanel, 'editorEntryId', source.id ? String(source.id) : '');
				this.$set(this.memoryPanel, 'editorError', '');
				this.$set(this.memoryPanel, 'editor', {
					memoryType: type,
					title: String(source.title || ''),
					content: String(source.content || ''),
					keywordsText: (Array.isArray(source.keywords) ? source.keywords : []).join('，'),
					secondaryKeywordsText: (Array.isArray(source.secondaryKeywords) ? source.secondaryKeywords : []).join('，'),
					priority: String(Number(source.priority) || this.defaultMemoryPriority(type)),
					manualPinned: source.id ? source.manualPinned === true : true,
					constantInjection: source.constantInjection === true && ['identity', 'relationship', 'setting', 'boundary'].includes(type)
				});
				this.$set(this.memoryPanel, 'editorVisible', true);
			},
			closeMemoryEditor() {
				if (!this.memoryPanel || this.memoryPanel.savingEntry) return;
				this.$set(this.memoryPanel, 'editorVisible', false);
				this.$set(this.memoryPanel, 'editorError', '');
			},
			changeMemoryEditorType(event) {
				const index = Number(event && event.detail && event.detail.value);
				const option = this.memoryEditorTypeOptions[index] || this.memoryEditorTypeOptions[4];
				this.$set(this.memoryPanel.editor, 'memoryType', option.value);
				this.$set(this.memoryPanel.editor, 'priority', String(this.defaultMemoryPriority(option.value)));
				if (!['identity', 'relationship', 'setting', 'boundary'].includes(option.value)) {
					this.$set(this.memoryPanel.editor, 'constantInjection', false);
				}
			},
			defaultMemoryPriority(type) {
				if (['identity', 'relationship', 'boundary'].includes(type)) return 200;
				if (['preference', 'promise', 'setting'].includes(type)) return 160;
				return 120;
			},
			parseMemoryKeywords(value) {
				const unique = [];
				String(value || '').split(/[，,、;；\n]+/).map((item) => item.trim()).filter(Boolean).forEach((item) => {
					if (!unique.includes(item)) unique.push(item);
				});
				return unique.slice(0, 12);
			},
			saveMemoryEditor() {
				if (!this.memoryPanel || !this.memoryPanel.editorVisible || this.memoryPanel.savingEntry) return;
				const editor = this.memoryPanel.editor || {};
				const content = String(editor.content || '').trim();
				const priority = Number(editor.priority);
				const keywords = this.parseMemoryKeywords(editor.keywordsText);
				if (!content) {
					this.$set(this.memoryPanel, 'editorError', '请填写记忆内容');
					return;
				}
				if (!isFinite(priority) || priority < 40 || priority > 200 || Math.floor(priority) !== priority) {
					this.$set(this.memoryPanel, 'editorError', '优先级必须是 40–200 的整数');
					return;
				}
				if (!editor.constantInjection && !keywords.length) {
					this.$set(this.memoryPanel, 'editorError', '普通记忆至少需要一个有效关键词');
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				const requestToken = this.memoryPanel.requestToken;
				const payload = Object.assign({}, this.memoryPanelPayload(), {
					memoryType: String(editor.memoryType || 'event'),
					title: String(editor.title || '').trim(),
					content,
					keywords,
					secondaryKeywords: this.parseMemoryKeywords(editor.secondaryKeywordsText),
					priority,
					manualPinned: editor.manualPinned === true,
					constantInjection: editor.constantInjection === true
				});
				if (this.memoryPanel.editorEntryId) payload.entryId = Number(this.memoryPanel.editorEntryId);
				this.$set(this.memoryPanel, 'savingEntry', true);
				this.$set(this.memoryPanel, 'editorError', '');
				tavernApi.postTavernMemorySaveEntry(payload)
					.then((detail) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
						this.applyMemoryPanelMetadata(detail || {});
						this.$set(this.memoryPanel, 'editorVisible', false);
						return this.loadMemoryPanel({ append: false }).then(() => {
							uni.showToast({ title: '记忆已保存', icon: 'none' });
							return true;
						});
					})
					.catch((error) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
						this.$set(this.memoryPanel, 'editorError', this.jgErrMsg(error, '记忆保存失败'));
					})
					.finally(() => {
						if (this.isMemoryPanelRequestCurrent(requestToken)) this.$set(this.memoryPanel, 'savingEntry', false);
					});
			},
			refreshMemoryPanel() {
				if (!this.memoryPanel || this.memoryPanelBusy) return;
				const tavernApi = require('@/common/tavernApi.js');
				const requestToken = this.memoryPanel.requestToken;
				this.$set(this.memoryPanel, 'refreshing', true);
				this.$set(this.memoryPanel, 'error', '');
				this.memoryRefreshing = true;
				this.memoryRefreshStatusToken = requestToken;
				tavernApi.postTavernMemoryRefresh(this.memoryPanelPayload())
					.then((memory) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
						if (memory && typeof memory === 'object') {
							this.jgMemory = Object.assign({}, this.jgMemory || {}, memory);
						}
						return this.loadMemoryPanel();
					})
					.then((loaded) => {
						if (loaded && this.isMemoryPanelRequestCurrent(requestToken)) {
							uni.showToast({ title: this.tx('memory_refresh_success_lorebook', '已整理长期记忆'), icon: 'none' });
						}
					})
					.catch((e) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
						const message = this.jgErrMsg(e, this.tx('memory_refresh_failed', '记忆刷新失败'));
						this.$set(this.memoryPanel, 'error', message);
						uni.showToast({ title: message, icon: 'none', duration: 3200 });
					})
					.finally(() => {
						if (this.memoryRefreshStatusToken === requestToken) {
							this.memoryRefreshing = false;
							this.memoryRefreshStatusToken = 0;
						}
						if (this.isMemoryPanelRequestCurrent(requestToken)) {
							this.$set(this.memoryPanel, 'refreshing', false);
						}
					});
			},
			toggleMemoryPanelEntry(entry) {
				if (!entry || !entry.id || this.memoryPanelBusy) return;
				const nextEnabled = !entry.enabled;
				uni.showModal({
					title: nextEnabled ? this.tx('memory_enable_title', '重新启用这条记忆？') : this.tx('memory_disable_title', '停用这条记忆？'),
					content: nextEnabled
						? this.tx('memory_enable_desc', '启用后，这条记忆会重新参与当前剧情分支的后续回复。')
						: this.tx('memory_disable_desc', '停用后，它不会继续影响当前剧情分支的后续回复，历史聊天不会改变。'),
					confirmText: nextEnabled ? this.tx('enable', '启用') : this.tx('disable', '停用'),
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res || !res.confirm) return;
						const tavernApi = require('@/common/tavernApi.js');
						const requestToken = this.memoryPanel.requestToken;
						this.$set(this.memoryPanel, 'updatingEntryId', String(entry.id || ''));
						tavernApi.postTavernMemorySetEntryEnabled(Object.assign({}, this.memoryPanelPayload(), {
							entryId: entry.id,
							enabled: nextEnabled
						}))
							.then((detail) => {
								if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
								this.applyMemoryPanelMetadata(detail || {});
								return this.loadMemoryPanel({ append: false }).then(() => {
									if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
									uni.showToast({
										title: nextEnabled ? this.tx('memory_enabled_done', '已重新启用') : this.tx('memory_disabled', '已停用'),
										icon: 'none'
									});
									return true;
								});
							})
							.catch((e) => {
								if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
								uni.showToast({
									title: this.jgErrMsg(e, nextEnabled ? this.tx('memory_enable_failed', '启用失败') : this.tx('memory_disable_failed', '停用失败')),
									icon: 'none',
									duration: 3200
								});
							})
							.finally(() => {
								if (this.isMemoryPanelRequestCurrent(requestToken)) {
									this.$set(this.memoryPanel, 'updatingEntryId', '');
								}
							});
					}
				});
			},
			deleteMemoryPanelEntry(entry) {
				if (!entry || !entry.id || this.memoryPanelBusy) return;
				uni.showModal({
					title: this.tx('memory_delete_title', '删除这条记忆？'),
					content: this.tx('memory_delete_desc', '删除后，这条记忆会从当前剧情分支移除并停止参与后续回复。历史聊天不会改变，删除操作不能在面板中恢复。'),
					confirmText: this.tx('delete', '删除'),
					confirmColor: '#b42318',
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res || !res.confirm) return;
						const tavernApi = require('@/common/tavernApi.js');
						const requestToken = this.memoryPanel.requestToken;
						this.$set(this.memoryPanel, 'deletingEntryId', String(entry.id || ''));
						tavernApi.postTavernMemoryDeleteEntry(Object.assign({}, this.memoryPanelPayload(), {
							entryId: entry.id
						}))
							.then((detail) => {
								if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
								this.applyMemoryPanelMetadata(detail || {});
								const status = String(detail && detail.syncStatus || '').toUpperCase();
								return this.loadMemoryPanel({ append: false }).then(() => {
									if (!this.isMemoryPanelRequestCurrent(requestToken)) return false;
									uni.showToast({
										title: status === 'FAILED'
											? this.tx('memory_deleted_sync_failed', '已删除，同步待重试')
											: this.tx('memory_deleted', '已删除'),
										icon: 'none',
										duration: status === 'FAILED' ? 3200 : 1800
									});
									return true;
								});
							})
							.catch((e) => {
								if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
								uni.showToast({
									title: this.jgErrMsg(e, this.tx('memory_delete_failed', '删除失败')),
									icon: 'none',
									duration: 3200
								});
							})
							.finally(() => {
								if (this.isMemoryPanelRequestCurrent(requestToken)) {
									this.$set(this.memoryPanel, 'deletingEntryId', '');
								}
							});
					}
				});
			},
			retryMemorySync() {
				if (!this.memoryPanel || this.memoryPanelBusy) return;
				const tavernApi = require('@/common/tavernApi.js');
				const requestToken = this.memoryPanel.requestToken;
				this.$set(this.memoryPanel, 'syncing', true);
				this.$set(this.memoryPanel, 'error', '');
				tavernApi.postTavernMemorySync(this.memoryPanelPayload())
					.then((detail) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
						this.applyMemoryPanelMetadata(detail || {});
						const status = String(detail && detail.syncStatus || '').toUpperCase();
						if (status === 'SUCCESS' || status === 'SKIPPED') {
							uni.showToast({ title: this.tx('memory_sync_retry_success', '记忆同步已恢复'), icon: 'none' });
						} else {
							uni.showToast({ title: this.tx('memory_sync_retry_failed', '同步仍未成功，请稍后再试'), icon: 'none', duration: 3200 });
						}
					})
					.catch((e) => {
						if (!this.isMemoryPanelRequestCurrent(requestToken)) return;
						const message = this.jgErrMsg(e, this.tx('memory_sync_retry_failed', '记忆同步失败'));
						this.$set(this.memoryPanel, 'error', message);
						uni.showToast({ title: message, icon: 'none', duration: 3200 });
					})
					.finally(() => {
						if (this.isMemoryPanelRequestCurrent(requestToken)) {
							this.$set(this.memoryPanel, 'syncing', false);
						}
					});
			},
			memoryPanelGuideText() {
				const visibleCount = (this.messages || []).filter((item) => item && !item.hidden).length;
				if (visibleCount < 6) {
					return this.tx('memory_guide_short', '再聊几轮后即可整理（至少 6 条消息）');
				}
				const cooldown = Number(this.memoryPanel && this.memoryPanel.detail && this.memoryPanel.detail.manualRefreshCooldownSeconds || 0);
				if (cooldown > 0) {
					return this.tx('memory_guide_ready_cooldown', '仅影响当前剧情分支，整理后需等待 {n} 秒').replace('{n}', String(cooldown));
				}
				return this.tx('memory_guide_ready', '仅影响当前剧情分支，历史消息不会被改写');
			},
			memoryTypeLabel(type) {
				const key = String(type || '').trim();
				const labels = {
					identity: '身份',
					relationship: '关系',
					preference: '偏好',
					promise: '约定',
					event: '事件',
					setting: '设定',
					boundary: '边界'
				};
				return labels[key] || '记忆';
			},
			memoryStatusLabel(status) {
				const key = String(status || '').trim().toUpperCase();
				if (this.memoryPanel && this.memoryPanel.refreshing) {
					return this.tx('memory_refreshing', '记忆整理中');
				}
				if (this.memoryPanel && this.memoryPanel.loading) {
					return this.tx('memory_loading', '读取中');
				}
				if (key === 'SUCCESS') return this.tx('memory_sync_status_success', '同步成功');
				if (key === 'PENDING') return this.tx('memory_sync_status_pending', '等待同步');
				if (key === 'FAILED') return this.tx('memory_sync_status_failed', '同步失败');
				if (key === 'SKIPPED') return this.tx('memory_sync_status_skipped', '暂无启用记忆');
				if (key === 'STOPPED') return this.tx('memory_sync_status_stopped', '已停止注入');
				return this.tx('memory_sync_status_empty', '未生成');
			},
			memoryEntryMetaText(entry) {
				const parts = [];
				if (entry.manualPinned) parts.push(this.tx('memory_manual_pinned', '手动保留'));
				if (entry.constantInjection) parts.push(this.tx('memory_constant', '固定'));
				if (entry.selective) parts.push(this.tx('memory_selective', '关键词'));
				if (entry.priority) parts.push(this.tx('memory_priority', '优先级') + ' ' + entry.priority);
				if (entry.confidence != null && isFinite(entry.confidence)) {
					parts.push(Math.round(entry.confidence * 100) + '%');
				}
				return parts.join(' · ');
			},
			memoryArchiveReasonText(entry) {
				const reason = String(entry && entry.archiveReason || '').trim();
				const key = reason.toUpperCase();
				const labels = {
					CAPACITY: this.tx('memory_archive_reason_capacity', '达到当前剧情分支的记忆容量上限'),
					LOW_VALUE: this.tx('memory_archive_reason_low_value', '长期未使用且价值较低'),
					DUPLICATE: this.tx('memory_archive_reason_duplicate', '已由更完整的同类记忆替代'),
					SUPERSEDED: this.tx('memory_archive_reason_superseded', '已由新的事实或状态替代'),
					REPLACED: this.tx('memory_archive_reason_replaced', '已由新的事实替代'),
					HISTORY_CHANGED: this.tx('memory_archive_reason_history', '相关历史消息已发生变化')
				};
				const text = labels[key] || reason || this.tx('memory_archive_reason_default', '为控制记忆容量而自动归档');
				return this.tx('memory_archive_reason_prefix', '归档原因：') + text;
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
				if (this.sending || !this.jgOn || !m || m.role !== 'char') return Promise.resolve(false);
				if (m.openingMessage) {
					this.openBranchPanel();
					return Promise.resolve(false);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				return tavernApi
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
							openingMessage: d.openingMessage === true || m.openingMessage === true,
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
						return true;
					})
					.catch((e) => {
						uni.showToast({ title: this.jgErrMsg(e, this.tx('swipe_failed', '切换失败')), icon: 'none' });
						return false;
					});
			},
			refreshJgMessages(options) {
				const opts = options && typeof options === 'object' ? options : {};
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const requestToken = this.jgLoadRequestToken;
				return tavernApi.fetchTavernMessages(cid, tavernApi.getClientUid(), {
					limit: TAVERN_MESSAGES_INITIAL_LIMIT
				}).then((pack) => {
					if (this.jgLoadRequestToken !== requestToken) {
						return [];
					}
					return this.applyMessagesEnvelope(pack, opts);
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
			goAppearanceSetting() {
				uni.navigateTo({ url: '/pages/user/chatAppearanceSetting?characterId=' + encodeURIComponent(String(this.cid || '')) });
			},
			isAssistantMessage(message) {
				if (!message) {
					return false;
				}
				const role = String(message.role || '').toLowerCase();
				return role !== 'user' && role !== 'me' && role !== 'human';
			},
			assistantDisplayText(text) {
				return String(text == null ? '' : text);
			},
			assistantBubbleChunks(message) {
				if (!message || !this.isAssistantMessage(message)) return [];
				const chunks = chatAppearance.splitReplyBubbleTexts(message.text, this.chatReplySplitMode);
				if (this.chatAppearanceReadMode !== 'speechOnly') {
					return chunks;
				}
				return chunks.filter((chunk) => this.textHasSpeechSegment(chunk) || this.textHasRichSegment(chunk));
			},
			textHasSpeechSegment(text) {
				const source = String(text || '').trim();
				if (!source) return false;
				try {
					const { extractChatSpeechSegments, splitChatSegments } = require('@/common/chatMarkdown.js');
					const segments = typeof extractChatSpeechSegments === 'function'
						? extractChatSpeechSegments(source)
						: (typeof splitChatSegments === 'function' ? splitChatSegments(source) : []);
					return segments.some((item) => item && item.type === 'speech' && String(item.text || '').trim());
				} catch (e) {
					return /[“"「『][^”"」』]{2,}[”"」』]/.test(source);
				}
			},
			textHasRichSegment(text) {
				try {
					const { splitChatSegments } = require('@/common/chatMarkdown.js');
					const segments = typeof splitChatSegments === 'function' ? splitChatSegments(text) : [];
					return segments.some((item) => item && item.type === 'rich' && String(item.text || '').trim());
				} catch (e) {
					return false;
				}
			},
			messageVisibleInReadMode(message) {
				if (!message || !this.isAssistantMessage(message) || this.chatAppearanceReadMode !== 'speechOnly') {
					return true;
				}
				if (Array.isArray(message.imageUrls) && message.imageUrls.length) {
					return true;
				}
				const text = this.assistantDisplayText(message.text);
				return this.textHasSpeechSegment(text) || this.textHasRichSegment(text);
			},
			shouldRenderSplitBubbles(message) {
				if (this.chatReplySplitMode !== 'bubble' || !message || !this.isAssistantMessage(message)) {
					return false;
				}
				if (Array.isArray(message.imageUrls) && message.imageUrls.length) {
					return false;
				}
				return this.assistantBubbleChunks(message).length > 1;
			},
			assistantSplitHostStyle() {
				const config = chatAppearance.normalizeConfig(this.chatAppearanceConfig);
				return chatAppearance.buildSplitHostStyleObject(config);
			},
			assistantSplitBubbleStyle(message, index) {
				return chatAppearance.buildSplitBubbleStyleObject(this.chatAppearanceConfig);
			},
			mdHtml(text) {
				const { renderChatMarkdown } = require('@/common/chatMarkdown.js');
				const config = this.normalizedChatAppearanceConfig;
				return renderChatMarkdown(this.assistantDisplayText(text), {
					readMode: this.chatAppearanceReadMode,
					showSegmentLabels: this.chatAppearanceSegmentLabelsEnabled,
					replySplitMode: this.chatReplySplitMode,
					segmentColors: chatAppearance.buildSegmentColors(config),
					segmentWeights: chatAppearance.buildSegmentWeights(config),
					thoughtItalic: config.thoughtItalic
				});
			},
			mdSegments(text) {
				const displayText = this.assistantDisplayText(text);
				let list = [];
				try {
					const { splitChatSegments } = require('@/common/chatMarkdown.js');
					list = typeof splitChatSegments === 'function' ? splitChatSegments(displayText) : [];
				} catch (e) {
					list = [];
				}
				const source = list.length ? this.expandNativeStructuredSegments(list) : this.splitNativeFallbackSegments(displayText);
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
				return this.applyNativeReadModeSegments(normalized);
			},
			expandNativeStructuredSegments(list) {
				return (Array.isArray(list) ? list : []).reduce((acc, item) => {
					if (!item) return acc;
					if (item.type === 'rich-content') {
						try {
							const { splitChatSegments, structuredTextForSemantics } = require('@/common/chatMarkdown.js');
							const plainText = typeof structuredTextForSemantics === 'function'
								? structuredTextForSemantics(item.text)
								: structuredContent.stripStructuredMarkupToText(item.text);
							if (!plainText) return acc;
							const nested = typeof splitChatSegments === 'function' ? splitChatSegments(plainText) : [];
							acc.push.apply(acc, nested.length ? nested : [{ type: 'narration', text: plainText }]);
						} catch (e) {
							const plainText = structuredContent.stripStructuredMarkupToText(item.text);
							if (!plainText) return acc;
							acc.push({ type: 'narration', text: plainText });
						}
						return acc;
					}
					if (item.type === 'rich-pending') {
						const pendingText = structuredContent.stripStructuredMarkupToText(item.text);
						if (pendingText) acc.push({ type: 'narration', text: pendingText });
						return acc;
					}
					acc.push(item);
					return acc;
				}, []);
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
				const re = /(\*[^*\n]{2,}\*|（[^）\n]{2,}）|\([^)\n]{2,}\)|“[^”\n]+”|"[^"\n]+"|「[^」\n]+」|『[^』\n]+』)/g;
				let cursor = 0;
				let match = null;
				while ((match = re.exec(text))) {
					const before = text.slice(cursor, match.index).trim();
					if (before) {
						result.push({ type: 'narration', text: before });
					}
					const token = match[0];
					let type = 'speech';
					if (token.charAt(0) === '*') {
						type = 'action';
					} else if (token.charAt(0) === '（' || token.charAt(0) === '(') {
						type = /心里|心想|想着|想道|脑海|内心|念头|暗想|默念/.test(token) ? 'thought' : 'action';
					}
					result.push({
						type,
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
				if (sourceType === 'rich-content' || sourceType === 'rich-pending') return 'narration';
				return ['speech', 'action', 'thought', 'narration', 'rich'].indexOf(sourceType) >= 0
					? sourceType
					: 'narration';
			},
			normalizeNativeSegmentText(item, normalizedType) {
				const type = normalizedType || (item && item.type ? item.type : 'narration');
				let text = String((item && item.text) || '').trim();
				if (type === 'rich' || type === 'rich-content' || type === 'rich-pending') {
					return structuredContent.stripStructuredMarkupToText(text);
				}
				if (type === 'action' && text.length > 1 && text.charAt(0) === '*' && text.charAt(text.length - 1) === '*') {
					text = text.slice(1, -1).trim();
				}
				return text;
			},
			ensureNativeSegmentContrast(list) {
				return Array.isArray(list) ? list.filter((item) => item && item.text) : [];
			},
			applyNativeReadModeSegments(list) {
				const source = Array.isArray(list) ? list.filter((item) => item && item.text) : [];
				if (this.chatAppearanceReadMode === 'speechOnly') {
					return source.filter((item) => item.type === 'speech' || item.type === 'rich');
				}
				if (this.chatAppearanceReadMode === 'hideThought') {
					return source.filter((item) => item.type !== 'thought');
				}
				return source;
			},
			nativeMessageContentClass() {
				return 'chat-message-native';
			},
			nativeSegmentClass(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				return ['chat-message-segment', 'chat-message-segment--' + type];
			},
			nativeSegmentLineClass() {
				return 'chat-message-segment-line';
			},
			nativeSegmentLabelClass(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				return ['chat-message-segment-label', 'chat-message-segment-label--' + type];
			},
			nativeSegmentLabelTextClass() {
				return 'chat-message-segment-label-text';
			},
			nativeUserMessageTextClass() {
				return 'chat-message-user-text';
			},
			nativeSegmentWrapStyle(segment) {
				if (!segment) {
					return {};
				}
				const type = segment.type || 'narration';
				if (this.chatAppearanceReadMode === 'speechOnly' && type !== 'speech' && type !== 'rich') {
					return { display: 'none' };
				}
				if (this.chatAppearanceReadMode === 'hideThought' && type === 'thought') {
					return { display: 'none' };
				}
				const rich = type === 'rich';
				return {
					display: 'block',
					width: '100%',
					minWidth: '0',
					margin: '0 0 8rpx',
					padding: rich ? '10rpx 0 0' : '0',
					background: 'transparent',
					borderTop: rich ? '1rpx solid rgba(255,255,255,0.18)' : '0',
					borderLeft: '0',
					borderRadius: '0',
					boxShadow: 'none',
					boxSizing: 'border-box',
					opacity: chatAppearance.buildSegmentTextStyleObject(type, this.normalizedChatAppearanceConfig).opacity
				};
			},
			nativeSegmentLabelText(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				return chatAppearance.segmentLabel(type);
			},
			nativeSegmentLineStyle() {
				return {
					display: this.chatAppearanceSegmentLabelsEnabled ? 'flex' : 'block',
					flexDirection: 'row',
					alignItems: 'flex-start',
					width: '100%',
					minWidth: '0',
					boxSizing: 'border-box'
				};
			},
			nativeSegmentLabelStyle(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				const colors = chatAppearance.buildSegmentAccentSurface(type, this.normalizedChatAppearanceConfig);
				return {
					display: 'flex',
					flexShrink: '0',
					alignItems: 'center',
					justifyContent: 'center',
					margin: '3rpx 10rpx 0 0',
					padding: '0 10rpx',
					minWidth: '58rpx',
					height: '34rpx',
					borderRadius: '999rpx',
					border: '1rpx solid ' + colors.border,
					background: colors.background,
					boxSizing: 'border-box'
				};
			},
			nativeSegmentLabelTextStyle(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				const colors = chatAppearance.buildSegmentAccentSurface(type, this.normalizedChatAppearanceConfig);
				return {
					display: 'block',
					color: colors.text,
					WebkitTextFillColor: colors.text,
					fontSize: '21rpx',
					lineHeight: '32rpx',
					fontWeight: colors.fontWeight,
					textAlign: 'center',
					whiteSpace: 'nowrap'
				};
			},
			nativeSegmentTextClass(segment) {
				const type = segment && segment.type ? String(segment.type) : 'narration';
				return ['chat-message-segment-text', 'chat-message-segment-text--' + type];
			},
			messageBubbleClass(message) {
				const hasImage = !!(message && message.imageUrls && message.imageUrls.length);
				const hasText = !!String((message && message.text) || '').trim();
				const isUser = message && message.role === 'user';
				return {
					'chat-message-bubble--assistant': !isUser,
					'chat-message-bubble--user': isUser,
					'chat-message-bubble--split-host': this.shouldRenderSplitBubbles(message),
					'chat-message-bubble--streaming': this.isStreamingAssistantRow(message),
					'chat-message-bubble--has-image': hasImage,
					'chat-message-bubble--image-only': hasImage && !hasText,
					'chat-message-bubble--text-only': !hasImage && hasText
				};
			},
			messageBubbleInlineStyle(message) {
				if (!message) {
					return '';
				}
				if (this.shouldRenderSplitBubbles(message)) {
					return this.assistantSplitHostStyle();
				}
				const hasImage = Array.isArray(message.imageUrls) && message.imageUrls.length;
				const hasText = String(message.text || '').trim();
				if (hasImage && !hasText) {
					return chatAppearance.buildImageBubbleStyleObject(this.chatAppearanceConfig);
				}
				return chatAppearance.buildBubbleStyleObject(message, this.chatAppearanceConfig);
			},
			userMessageTextStyle(message) {
				if (!message || message.role !== 'user') {
					return {};
				}
				return chatAppearance.buildMessageTextStyleObject('user', this.chatAppearanceConfig);
			},
			assistantMessageTextStyle(message) {
				if (!message || message.role === 'user') {
					return {};
				}
				return chatAppearance.buildMessageTextStyleObject('assistant', this.chatAppearanceConfig);
			},
			nativeSegmentTextStyle(segment) {
				if (!segment) {
					return {};
				}
				const type = segment.type || 'narration';
				const appearanceConfig = this.normalizedChatAppearanceConfig;
				const segmentStyle = chatAppearance.buildSegmentTextStyleObject(type, appearanceConfig);
				const messageTextStyle = chatAppearance.buildMessageTextStyleObject('assistant', appearanceConfig);
				let fontSize = messageTextStyle['font-size'];
				let lineHeight = messageTextStyle['line-height'];
				return {
					display: 'block',
					flex: this.chatAppearanceSegmentLabelsEnabled ? '1 1 auto' : 'none',
					width: this.chatAppearanceSegmentLabelsEnabled ? 'auto' : '100%',
					maxWidth: '100%',
					minWidth: '0',
					color: segmentStyle.color,
					WebkitTextFillColor: segmentStyle.color,
					fontSize: fontSize,
					lineHeight: lineHeight,
					fontStyle: segmentStyle.fontStyle,
					fontWeight: segmentStyle.fontWeight,
					letterSpacing: '0',
					textShadow: 'none',
					wordBreak: 'break-word',
					overflowWrap: 'break-word',
					whiteSpace: 'pre-wrap'
				};
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
				const { extractChatSpeechSegments, splitChatSegments } = require('@/common/chatMarkdown.js');
				const list = typeof extractChatSpeechSegments === 'function'
					? extractChatSpeechSegments(text)
					: (typeof splitChatSegments === 'function' ? splitChatSegments(text) : []);
				if (!Array.isArray(list) || !list.length) return '';
				return list
					.filter((item) => item && item.type === 'speech')
					.map((item) => this.stripAssistantSpeechWrapping(item.text))
					.filter((item) => item)
					.join('\n')
					.trim();
			},
			extractAssistantSpeakerBlocks(row) {
				const text = String(row && row.text || '').replace(/\r\n?/g, '\n');
				const defaultSpeakerMemberId = Math.max(0, Math.floor(Number(row && row.speakerMemberId) || 0));
				const markers = /【([^】\n]{1,64})】/g;
				const blocks = [];
				let cursor = 0;
				let activeMemberId = defaultSpeakerMemberId;
				let match = null;
				const append = (content, speakerMemberId) => {
					if (!content) return;
					const previous = blocks.length ? blocks[blocks.length - 1] : null;
					if (previous && previous.speakerMemberId === speakerMemberId) {
						previous.content += content;
						return;
					}
					blocks.push({ content, speakerMemberId });
				};
				while ((match = markers.exec(text))) {
					append(text.slice(cursor, match.index), activeMemberId);
					const markerName = this.normalizeCharacterVoiceText(match[1], 64);
					const member = this.findCharacterStudioMemberByName(markerName);
					if (member) {
						activeMemberId = member.id;
					} else if (/^(旁白|narrator)$/i.test(markerName)) {
						activeMemberId = 0;
					} else {
						append(match[0], activeMemberId);
					}
					cursor = markers.lastIndex;
				}
				append(text.slice(cursor), activeMemberId);
				return blocks;
			},
			assistantVoiceSegmentsForRow(row, options) {
				const opts = options || {};
				const segments = [];
				this.extractAssistantSpeakerBlocks(row).forEach((block) => {
					const speechText = this.extractAssistantSpeechText(block.content);
					this.splitAssistantSpeechIntoSentences(speechText, opts).forEach((text) => {
						if (text) segments.push({
							text,
							speakerMemberId: Math.max(0, Math.floor(Number(block.speakerMemberId) || 0))
						});
					});
				});
				if (segments.length) return segments;
				const fallbackText = this.extractAssistantSpeechText(row && row.text);
				return this.splitAssistantSpeechIntoSentences(fallbackText, opts).map((text) => ({
					text,
					speakerMemberId: Math.max(0, Math.floor(Number(row && row.speakerMemberId) || 0))
				}));
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
						buffer.length >= ASSISTANT_VOICE_SEGMENT_HARD_MAX
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
					if (
						prev.length + next.length + 1 <= ASSISTANT_VOICE_SEGMENT_TARGET_LENGTH ||
						(next.length <= ASSISTANT_VOICE_SEGMENT_SHORT_LENGTH && prev.length + next.length + 1 <= ASSISTANT_VOICE_SEGMENT_HARD_MAX)
					) {
						merged.splice(lastIndex, 1, prev + ' ' + next);
						return;
					}
					merged.push(next);
				});
				return merged;
			},
			assistantVoiceSentenceKey(sentenceTexts) {
				return Array.isArray(sentenceTexts) ? sentenceTexts.map((item) => {
					if (item && typeof item === 'object') {
						return String(Math.max(0, Math.floor(Number(item.speakerMemberId) || 0))) + '\u001f' + String(item.text || '').trim();
					}
					return '0\u001f' + String(item || '').trim();
				}).filter((item) => item !== '0\u001f').join('\n@@\n') : '';
			},
			countAssistantVoiceSentencePrefix(currentTexts, nextTexts) {
				const left = Array.isArray(currentTexts) ? currentTexts : [];
				const right = Array.isArray(nextTexts) ? nextTexts : [];
				const total = Math.min(left.length, right.length);
				let count = 0;
				for (let i = 0; i < total; i += 1) {
					if (this.assistantVoiceSentenceKey([left[i]]) !== this.assistantVoiceSentenceKey([right[i]])) {
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
			assistantVoiceMissingSegmentIndexes(entry) {
				const segments = this.assistantVoiceAudioSegments(entry);
				const missing = [];
				segments.forEach((item, index) => {
					if (!this.isAssistantVoiceAudioUrl(item)) missing.push(index);
				});
				return missing;
			},
			assistantVoiceIsComplete(entry) {
				const total = Array.isArray(entry && entry.sentenceTexts) ? entry.sentenceTexts.length : 0;
				return total > 0 && this.assistantVoiceMissingSegmentIndexes(entry).length === 0;
			},
			isAssistantVoiceAudioUrl(value) {
				const url = String(value || '').trim();
				return !!url && (
					url.indexOf('data:audio/') === 0 ||
					url.indexOf('blob:') === 0 ||
					url.indexOf('_doc/') === 0 ||
					url.indexOf('file://') === 0 ||
					url.indexOf('content://') === 0
				);
			},
			firstAssistantVoiceAudio(entry) {
				const list = this.assistantVoiceAudioSegments(entry);
				const first = list.length ? String(list[0] || '').trim() : '';
				return this.isAssistantVoiceAudioUrl(first) ? first : '';
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
				const fullSentenceKey = this.assistantVoiceSentenceKey(this.assistantVoiceSegmentsForRow(row));
				const streamingSentenceKey = this.assistantVoiceSentenceKey(
					this.assistantVoiceSegmentsForRow(row, { includeTrailingPartial: false })
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
				try {
					const localMediaStore = require('@/common/localMediaStore.js');
					localMediaStore.moveMessage(
						this.resolveLocalExpressionViewerKey(),
						this.resolveLocalChatConversationId(),
						fromId,
						nextId
					).catch(() => {});
				} catch (e) {}
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
				const rows = Array.isArray(this.messages) ? this.messages : [];
				rows.forEach((row) => {
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
				if (this.isVoiceFeatureEnabledGlobal() && this.isCharacterVoiceEnabled()) {
					rows.forEach((row) => {
						const messageId = this.assistantVoiceMessageId(row);
						if (row && row.role === 'char' && messageId && !next[messageId]) {
							this.restoreAssistantVoiceEntry(row);
						}
					});
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
				if (state === 'partial') return this.tx('assistant_voice_complete', '补全语音');
				if (state === 'error') return this.tx('assistant_voice_retry', '重试语音');
				return this.tx('assistant_voice_play', '播放台词');
			},
			assistantVoicePillClass(row) {
				const entry = this.getAssistantVoiceEntry(row);
				const state = entry && entry.state ? entry.state : 'idle';
				return {
					'assistant-voice-pill--loading': state === 'loading',
					'assistant-voice-pill--playing': state === 'playing',
					'assistant-voice-pill--partial': state === 'partial',
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
					this.assistantVoicePlayer.onError(() => {
						const messageId = this.assistantVoicePlayingMessageId;
						this.assistantVoicePlayingMessageId = '';
						if (messageId && this.assistantVoiceStateMap[messageId]) {
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
						state: entry && entry.requestKey
							? 'loading'
							: this.assistantVoiceHasPlayableAudio(entry)
								? (this.assistantVoiceIsComplete(entry) ? 'ready' : 'partial')
								: 'idle',
						playingIndex: -1,
						waitingForSegmentIndex: -1,
						autoPlayPending: false
					});
				}
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
				if (!this.isAssistantVoiceAudioUrl(audioDataUrl)) {
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
				if (this.isAssistantVoiceAudioUrl(nextAudio)) {
					this.playAssistantVoiceByMessageId(safeId, nextIndex, nextAudio);
					return;
				}
				if (!entry.requestKey) {
					this.assistantVoicePlayingMessageId = '';
					this.setAssistantVoiceEntry(safeId, {
						state: this.assistantVoiceHasPlayableAudio(entry) ? 'partial' : 'idle',
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
				const sentenceSpeakerMemberIds = Array.isArray(entry.sentenceSpeakerMemberIds)
					? entry.sentenceSpeakerMemberIds.slice() : [];
				const opts = options || {};
				let firstAudioDataUrl = this.firstAssistantVoiceAudio(entry);
				const run = (index) => {
					if (index >= sentenceTexts.length) {
						const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
						if (latest && latest.requestKey === requestKey) {
							this.setAssistantVoiceEntry(safeId, {
								requestKey: '',
								state: this.assistantVoicePlayingMessageId === safeId ? 'playing' : this.assistantVoiceHasPlayableAudio(latest) ? 'ready' : 'idle',
								autoPlayPending: false,
								missingIndexes: [],
								failedIndex: -1,
								isComplete: true,
								retryable: false
							});
						}
						return Promise.resolve(firstAudioDataUrl || null);
					}
					const latestBeforeRequest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
					const existingSegments = this.assistantVoiceAudioSegments(latestBeforeRequest);
					const existingAudio = String(existingSegments[index] || '').trim();
					if (this.isAssistantVoiceAudioUrl(existingAudio)) {
						if (!firstAudioDataUrl) {
							firstAudioDataUrl = existingAudio;
						}
						return run(index + 1);
					}
					return tavernApi
						.postTavernSpeech(this.buildCharacterVoiceTtsPayload(
							sentenceTexts[index],
							requestKey,
							index,
							sentenceTexts.length,
							safeId,
							sentenceSpeakerMemberIds[index]
						))
						.then((data) => {
							const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
							if (!latest || latest.requestKey !== requestKey) {
								return firstAudioDataUrl || null;
							}
							const audioDataUrl = data && data.audioDataUrl ? String(data.audioDataUrl).trim() : '';
							if (!audioDataUrl || audioDataUrl.indexOf('data:audio/') !== 0) {
								throw new Error(this.tx('assistant_voice_failed', '语音生成失败'));
							}
							return this.persistAssistantVoiceSegment(safeId, index, {
								text: sentenceTexts[index],
								speakerMemberId: sentenceSpeakerMemberIds[index]
							}, requestKey, audioDataUrl)
								.catch(() => audioDataUrl)
								.then((playableAudioUrl) => {
									const freshEntry = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
									if (!freshEntry || freshEntry.requestKey !== requestKey) {
										return firstAudioDataUrl || null;
									}
									const audioSegments = this.assistantVoiceAudioSegments(freshEntry);
									audioSegments[index] = playableAudioUrl;
									if (!firstAudioDataUrl) firstAudioDataUrl = playableAudioUrl;
									this.setAssistantVoiceEntry(safeId, {
										audioDataUrl: audioSegments[0] || playableAudioUrl,
										sentenceAudioUrls: audioSegments,
										state: this.assistantVoicePlayingMessageId === safeId ? 'playing' : 'ready',
										error: ''
									});
									const fresh = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
									if (fresh && fresh.autoPlayPending && index === 0) {
										const latestRow = this.findMessageRowById(safeId);
										if (latestRow) this.playAssistantVoice(latestRow);
									} else if (fresh && fresh.waitingForSegmentIndex === index && this.assistantVoicePlayingMessageId === safeId) {
										this.playAssistantVoiceByMessageId(safeId, index, playableAudioUrl);
									}
									return run(index + 1);
								});
						})
						.catch((error) => {
							const latest = this.assistantVoiceStateMap && this.assistantVoiceStateMap[safeId];
							if (latest && latest.requestKey === requestKey) {
								this.setAssistantVoiceEntry(safeId, {
									requestKey: '',
									state: firstAudioDataUrl ? 'partial' : 'error',
									autoPlayPending: false,
									waitingForSegmentIndex: -1,
									missingIndexes: this.assistantVoiceMissingSegmentIndexes(latest),
									failedIndex: index,
									isComplete: false,
									retryable: true,
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
				const voiceSegments = this.assistantVoiceSegmentsForRow(row, {
					includeTrailingPartial: opts.includeTrailingPartial !== false
				});
				const sentenceTexts = voiceSegments.map((item) => item.text);
				if (!voiceSegments.length) {
					this.clearAssistantVoiceEntry(messageId);
					return Promise.resolve(null);
				}
				const preparedSentenceKey = this.assistantVoiceSentenceKey(voiceSegments);
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
				const previousSpeakerMemberIds = Array.isArray(current && current.sentenceSpeakerMemberIds)
					? current.sentenceSpeakerMemberIds : [];
				const previousAudioSegments = this.assistantVoiceAudioSegments(current);
				const reusableCount = this.countAssistantVoiceSentencePrefix(
					previousTexts.map((text, index) => ({ text, speakerMemberId: previousSpeakerMemberIds[index] })),
					voiceSegments
				);
				const nextAudioSegments = new Array(sentenceTexts.length).fill('');
				for (let i = 0; i < reusableCount; i += 1) {
					nextAudioSegments[i] = previousAudioSegments[i] || '';
				}
				const requestKey = String(current && (current.taskId || current.requestKey) || '').trim() || ('tts_' + messageId);
				this.setAssistantVoiceEntry(messageId, {
					speechText,
					preparedSentenceKey: preparedSentenceKey,
					sentenceTexts,
					sentenceSpeakerMemberIds: voiceSegments.map((item) => item.speakerMemberId),
					sentenceAudioUrls: nextAudioSegments,
					state: reusableCount < sentenceTexts.length ? 'loading' : this.assistantVoiceHasPlayableAudio({ sentenceTexts, sentenceAudioUrls: nextAudioSegments }) ? 'ready' : 'idle',
					audioDataUrl: nextAudioSegments[0] || '',
					error: '',
					taskId: requestKey,
					requestKey: reusableCount < sentenceTexts.length ? requestKey : '',
					autoPlayPending: !!opts.autoplay,
					missingIndexes: nextAudioSegments.map((item, index) => this.isAssistantVoiceAudioUrl(item) ? -1 : index).filter((index) => index >= 0),
					failedIndex: -1,
					isComplete: reusableCount >= sentenceTexts.length,
					retryable: false,
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
			resumeAssistantVoiceSegments(row, options) {
				const messageId = this.assistantVoiceMessageId(row);
				const entry = this.getAssistantVoiceEntry(row);
				if (!messageId || !entry) return Promise.resolve(null);
				const missingIndexes = this.assistantVoiceMissingSegmentIndexes(entry);
				if (!missingIndexes.length) {
					this.setAssistantVoiceEntry(messageId, { state: 'ready', isComplete: true, retryable: false });
					this.playAssistantVoice(row);
					return Promise.resolve(this.firstAssistantVoiceAudio(entry));
				}
				if (entry.requestKey) {
					this.playAssistantVoice(row);
					return Promise.resolve(null);
				}
				const requestKey = String(entry.taskId || ('tts_' + messageId)).trim();
				this.setAssistantVoiceEntry(messageId, {
					requestKey,
					state: 'loading',
					missingIndexes,
					failedIndex: -1,
					retryable: false,
					error: ''
				});
				const firstAudio = this.firstAssistantVoiceAudio(entry);
				if (firstAudio && options && options.autoplay) {
					this.playAssistantVoiceByMessageId(messageId, 0, firstAudio);
				}
				return this.preloadAssistantVoiceSegments(messageId, requestKey, {
					toastOnError: !firstAudio
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
				if (entry && (entry.state === 'partial' || this.assistantVoiceMissingSegmentIndexes(entry).length > 0) && !entry.requestKey) {
					this.resumeAssistantVoiceSegments(row, { autoplay: true });
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
				if (!this.ensureJgIdentityReadyForAction()) return;
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const runtimeRequestVersion = this.jgRuntimeRequestVersion;
				const runtimeIdentitySignature = this.jgViewerIdentitySignature;
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
					this.buildChatModelPayloadFields(this.createChatGenerationRequestId('regen', anchor.targetAssistantMessageId)),
					this.buildAssistantExpressionPayloadFields(),
					this.buildChatAppearancePayloadFields()
				);
				this.beginSendingState();

				if (tavernApi.jgStreamEnabled()) {
					const backup = String(last.text || '');
					let started = false;
					this.beginAssistantStreaming(last.id, 'regenerate');
					this.notifyCompanionReplying('regenerate');
					const streamController = this.createStreamAbortController();
					this.streamAbortController = streamController;
					this.followBottom = true;
					tavernApi
						.postTavernRegenerateStream(
							payload,
							{
								onDelta: (piece) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
									if (!started) {
										started = true;
										this.$set(last, 'text', piece);
									} else {
										const next = (last.text || '') + piece;
										this.$set(last, 'text', next);
									}
									this.handleIncomingChatRows(last);
								},
								onDone: (data) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
									if (cancelled) {
										this.showGenerationStopToast('stopped', '已停止');
										this.queueStopSync(700);
									}
									this.finishAssistantStreaming(last.id);
									if (!cancelled) {
										this.prepareAssistantVoiceForRow(last, {
											autoplay: this.shouldAutoPlayAssistantVoice(),
											force: true,
											toastOnError: false,
											allowStreaming: true,
											includeTrailingPartial: true
										}).catch(() => {});
									}
									this.notifyCompanionReply(last.text);
									this.applyAssistantExpressionForRow(last);
									this.handleIncomingChatRows(last, { alreadyCounted: started });
								},
								onAbort: () => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
									if (!started) {
										this.$set(last, 'text', backup);
									}
									this.queueStopSync(700);
									this.finishAssistantStreaming(last.id);
									this.showGenerationStopToast('stopped', '已停止');
								},
								onError: (e) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
							{ signal: streamController.signal }
						)
						.finally(() => {
							this.finalizeAssistantStreamRequest(streamController);
						});
					return;
				}

				if (this.isAppPlus) {
					this.beginAssistantStreaming(last.id, 'regenerate');
					this.followBottom = true;
					this.followScrollNextTick();
				}
				tavernApi
					.postTavernRegenerate(payload)
					.then((d) => {
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
						if (d && d.content != null) {
							this.$set(last, 'text', String(d.content));
						}
						this.applyAssistantExpressionForRow(last);
						this.notifyCompanionReply(last.text);
						this.handleIncomingChatRows(last);
						return this.refreshJgMessages();
					})
					.catch((e) => {
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
						if (this.isAppPlus) {
							this.finishAssistantStreaming(last.id);
						}
						this.finishSendingState();
					});
			},
			onContinue() {
				if (!this.ensureJgIdentityReadyForAction()) return;
				if (this.sending || this.voiceRecording || this.voiceStopping || this.voiceTranscribing || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const runtimeRequestVersion = this.jgRuntimeRequestVersion;
				const runtimeIdentitySignature = this.jgViewerIdentitySignature;
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
					this.buildChatModelPayloadFields(this.createChatGenerationRequestId('continue', anchor.targetAssistantMessageId)),
					this.buildAssistantExpressionPayloadFields(),
					this.buildChatAppearancePayloadFields()
				);
				this.beginSendingState();

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
					let streamed = false;
					this.beginAssistantStreaming(rid, 'continue');
					this.notifyCompanionReplying('continue');
					const streamController = this.createStreamAbortController();
					this.streamAbortController = streamController;
					this.followBottom = true;
					tavernApi
						.postTavernContinueStream(
							payload,
							{
								onDelta: (piece) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
									acc += piece;
									const row = this.messages.find((item) => item && item.id === rid);
									if (row) {
										this.$set(row, 'text', acc);
										streamed = true;
										this.handleIncomingChatRows(row);
									}
								},
								onDone: (data) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
									const row = this.messages.find((item) => item && item.id === rid);
									if (!row) {
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
									this.prepareAssistantVoiceForRow(row, {
										autoplay: this.shouldAutoPlayAssistantVoice(),
										force: true,
										toastOnError: false,
										allowStreaming: true,
										includeTrailingPartial: true
									}).catch(() => {});
									this.notifyCompanionReply(row.text);
									this.applyAssistantExpressionForRow(row);
									// 商用一致性：流式续写完成后也刷新一次消息列表，避免本地临时文本被状态刷新覆盖
									this.refreshJgMessages().catch(() => {});
									this.handleIncomingChatRows(row, { alreadyCounted: streamed });
								},
								onAbort: () => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
									const row = this.messages.find((item) => item && item.id === rid);
									if (row && !String(row.text || '').trim()) {
										this.messages = this.messages.filter((item) => item && item.id !== rid);
									}
									this.queueStopSync(700);
									this.finishAssistantStreaming(rid);
									this.showGenerationStopToast('stopped', '已停止');
								},
								onError: (e) => {
									if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
							{ signal: streamController.signal }
						)
						.finally(() => {
							this.finalizeAssistantStreamRequest(streamController);
						});
					return;
				}

				const appPendingContinueId = this.isAppPlus ? 'cont_pending_' + Date.now() : '';
				let appPendingContinueRow = null;
				if (appPendingContinueId) {
					this.messages = this.messages.concat({
						id: appPendingContinueId,
						role: 'char',
						text: '',
						messageKind: 'CONTINUATION',
						continueFromMessageId: anchor.targetAssistantMessageId,
						swipes: [''],
						swipeIndex: 0
					});
					appPendingContinueRow = this.messages[this.messages.length - 1];
					this.beginAssistantStreaming(appPendingContinueId, 'continue');
					this.followBottom = true;
					this.followScrollNextTick();
				}
				tavernApi
					.postTavernContinue(payload)
					.then((d) => {
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
						const aid = d && d.messageId ? this.normalizeDbMessageId(d.messageId) : '';
						const rid = aid || appPendingContinueId || 'cont_' + Date.now();
						const raw = d && d.content;
						const reply =
							raw != null
								? String(raw).trim()
								: this.tx('empty_ai', '模型未返回内容');
						const sw =
							Array.isArray(d && d.swipes) && d.swipes.length
								? d.swipes.map((x) => String(x))
								: [reply];
						const si = typeof (d && d.swipeIndex) === 'number' ? d.swipeIndex : 0;
						if (appPendingContinueRow) {
							if (aid) {
								this.moveAssistantStreamingMessageId(appPendingContinueId, aid);
								this.$set(appPendingContinueRow, 'id', aid);
							}
							this.$set(appPendingContinueRow, 'text', reply);
							this.$set(appPendingContinueRow, 'messageKind', d && d.messageKind ? String(d.messageKind) : 'CONTINUATION');
							this.$set(appPendingContinueRow, 'continueFromMessageId', this.normalizeDbMessageId(d && d.continueFromMessageId ? d.continueFromMessageId : anchor.targetAssistantMessageId));
							this.$set(appPendingContinueRow, 'swipes', sw);
							this.$set(appPendingContinueRow, 'swipeIndex', si);
						} else if (d && d.content != null) {
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
						const appendedRow = appPendingContinueRow || this.messages[this.messages.length - 1];
						this.applyAssistantExpressionForRow(appendedRow);
						this.notifyCompanionReply(appendedRow.text);
						this.handleIncomingChatRows(appendedRow);
						return this.refreshJgMessages();
					})
					.catch((e) => {
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
						if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
						if (appPendingContinueId) {
							this.finishAssistantStreaming(appPendingContinueRow && appPendingContinueRow.id ? appPendingContinueRow.id : appPendingContinueId);
						}
						this.finishSendingState();
					});
			},
			onRestart() {
				if (!this.ensureJgIdentityReadyForAction()) return;
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
						if (!this.ensureJgIdentityReadyForAction()) return;
						const runtimeRequestVersion = this.jgRuntimeRequestVersion;
						const runtimeIdentitySignature = this.jgViewerIdentitySignature;
						const requestClientUid = tavernApi.getClientUid();
						const localConversationId = this.resolveLocalChatConversationId();
						this.beginSendingState();
						tavernApi
							.postTavernSessionRestart({
								characterId: cid,
								clientUid: requestClientUid
							})
							.then(() => {
								if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
								tavernApi.cleanupLocalConversationArtifacts({
									clientUid: requestClientUid,
									conversationId: localConversationId
								});
								this.resetConversationVoiceRuntimeState();
								return this.refreshJgMessages();
							})
							.then(() => {
								if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
								this.scrollChatToBottom({ immediate: true });
								uni.showToast({
									title: this.tx('restart_success', '已清空，可重新对话'),
									icon: 'none'
								});
							})
							.catch((e) => {
								if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
								uni.showToast({
									title: this.jgErrMsg(e, this.tx('restart_failed', '操作失败')),
									icon: 'none',
									duration: 3200
								});
							})
							.finally(() => {
								if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
								this.finishSendingState();
							});
					}
				});
			},
			onMem() {
				if (this.voiceRecording || this.voiceStopping || this.voiceTranscribing) return;
				this.openMemoryPanel();
			},
			submitOutgoingMessage(rawText, rawImageUrls, options) {
				const opts = options || {};
				if (!this.ensureJgIdentityReadyForAction()) return false;
				const runtimeRequestVersion = this.jgRuntimeRequestVersion;
				const runtimeIdentitySignature = this.jgViewerIdentitySignature;
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
				const generationRequestId = this.createChatGenerationRequestId('send', uid);
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
				this.beginSendingState();
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
							visionRequestId: imageUrls.length ? 'vision_' + uid : '',
							attachmentMode,
							attachmentHint,
							voiceUrl: userVoiceMeta.voiceUrl,
							voiceDurationMs: userVoiceMeta.durationMs,
							temperature: 0.85
						},
						this.buildChatModelPayloadFields(generationRequestId),
						this.buildAssistantExpressionPayloadFields(),
						this.buildChatAppearancePayloadFields()
					);

					if (tavernApi.jgStreamEnabled()) {
						const rid = 'r_' + Date.now();
						let replyStreamed = false;
						this.beginAssistantStreaming(rid, 'generate');
						this.notifyCompanionReplying('generate');
						const streamController = this.createStreamAbortController();
						this.streamAbortController = streamController;
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
										if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid) {
											const next = (last.text || '') + piece;
											this.$set(last, 'text', next);
											replyStreamed = true;
											this.handleIncomingChatRows(last);
										}
									},
									onDone: (data) => {
										if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
										const row = this.messages[this.messages.length - 1];
										if (!row || row.id !== rid) {
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
									this.prepareAssistantVoiceForRow(row, {
										autoplay: this.shouldAutoPlayAssistantVoice(),
										force: true,
										toastOnError: false,
										allowStreaming: true,
										includeTrailingPartial: true
									}).catch(() => {});
										this.applyAssistantExpressionForRow(row);
										this.handleIncomingChatRows(row, { alreadyCounted: replyStreamed });
									},
									onAbort: () => {
										if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid && !String(last.text || '').trim()) {
											this.messages = this.messages.filter((x) => x.id !== rid);
										}
										this.queueStopSync(700);
										this.finishAssistantStreaming(rid);
										this.showGenerationStopToast('stopped', '已停止');
									},
									onError: (e) => {
										if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
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
								{ signal: streamController.signal }
							)
							.finally(() => {
								this.finalizeAssistantStreamRequest(streamController);
							});
						return true;
					}

					const appPendingReplyId = this.isAppPlus ? 'r_pending_' + Date.now() : '';
					let appPendingReplyRow = null;
					if (appPendingReplyId) {
						this.beginAssistantStreaming(appPendingReplyId, 'generate');
						this.messages = this.messages.concat({
							id: appPendingReplyId,
							role: 'char',
							text: '',
							swipes: [''],
							swipeIndex: 0
						});
						appPendingReplyRow = this.messages[this.messages.length - 1];
						this.followBottom = true;
						this.followScrollNextTick();
					}
					tavernApi
						.postTavernChat(payload)
						.then((data) => {
							if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
							this.patchLastOptimisticUserId(data && data.userMessageId, data && data.messageId);
							const aid =
								data && data.messageId ? this.normalizeDbMessageId(data.messageId) : '';
							const rid = aid || appPendingReplyId || 'r_' + Date.now();
							const raw = data && data.content;
							const reply =
								raw != null
									? String(raw).trim()
									: this.tx('empty_ai', '模型未返回内容');
							const sw =
								Array.isArray(data && data.swipes) && data.swipes.length
									? data.swipes.map((x) => String(x))
									: [reply];
							const si = typeof (data && data.swipeIndex) === 'number' ? data.swipeIndex : 0;
							if (appPendingReplyRow) {
								if (aid) {
									this.moveAssistantStreamingMessageId(appPendingReplyId, aid);
									this.$set(appPendingReplyRow, 'id', aid);
								}
								this.$set(appPendingReplyRow, 'text', reply);
								this.$set(appPendingReplyRow, 'swipes', sw);
								this.$set(appPendingReplyRow, 'swipeIndex', si);
							} else {
								this.messages = this.messages.concat({
									id: rid,
									role: 'char',
									text: reply,
									swipes: sw,
									swipeIndex: si
								});
							}
							const appendedRow = appPendingReplyRow || this.messages[this.messages.length - 1];
							this.applyAssistantExpressionForRow(appendedRow);
							this.notifyCompanionReply(appendedRow.text);
							this.handleIncomingChatRows(appendedRow);
						})
						.catch((e) => {
							if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
							const rid = appPendingReplyId || 'r_' + Date.now();
							const result = this.handleCommercialError(e, this.tx('chat_failed', '对话失败'), {
								skipToastWhenPrompted: true
							});
							const msg = result.message;
							this.notifyCompanionError(msg);
							if (!result.prompted) {
								this.showErrorToast(msg);
							}
							let recoveryRow = appPendingReplyRow;
							if (!recoveryRow) {
								this.messages = this.messages.concat({
									id: rid,
									role: 'char',
									text: ''
								});
								recoveryRow = this.messages[this.messages.length - 1];
							}
							this.markGenerationRecovery(rid, {
								message: msg,
								partialText: '',
								canRegen: false,
								retryText: text
							});
							this.handleIncomingChatRows(recoveryRow);
						})
						.finally(() => {
							if (!this.isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)) return;
							if (appPendingReplyId) {
								this.finishAssistantStreaming(appPendingReplyRow && appPendingReplyRow.id ? appPendingReplyRow.id : appPendingReplyId);
							}
							this.finishSendingState();
						});
					return true;
				}

				uni.showToast({ title: this.tx('backend_disabled', 'Backend unavailable'), icon: 'none' });
				this.notifyCompanionError(this.tx('backend_disabled', 'Backend unavailable'));
				this.finishSendingState();
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

.wrap > .chat-readable-overlay {
	position: absolute;
	inset: 0;
	z-index: 0;
	pointer-events: none;
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

	.nav-appearance-config {
		width: 64rpx;
		height: 64rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 18rpx;
		background: rgba(14, 23, 36, 0.24);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
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

	.chat-model-bar {
		flex: 0 0 64rpx;
		height: 64rpx;
		min-height: 64rpx;
		display: flex;
		align-items: center;
		gap: 12rpx;
		padding: 0 24rpx;
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.1);
		background: rgba(18, 22, 31, 0.9);
		color: #eef5f8;
		backdrop-filter: blur(12px);
	}

	.chat-model-bar--disabled {
		opacity: 0.7;
		pointer-events: none;
	}

	.chat-model-bar__source {
		flex: 0 0 auto;
		padding: 5rpx 10rpx;
		border: 1rpx solid rgba(250, 176, 91, 0.5);
		border-radius: 6rpx;
		color: #ffc47c;
		font-size: 20rpx;
		line-height: 1;
	}

	.chat-model-bar__name {
		min-width: 0;
		flex: 1;
		overflow: hidden;
		font-size: 24rpx;
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chat-model-bar__price {
		flex: 0 1 auto;
		max-width: 42%;
		overflow: hidden;
		color: #a8c4d2;
		font-size: 21rpx;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chat-model-picker-mask {
		position: fixed !important;
		inset: 0;
		z-index: 3100 !important;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		background: rgba(7, 11, 17, 0.66);
	}

	.chat-model-picker {
		width: 100%;
		max-width: 820rpx;
		max-height: 82vh;
		display: flex;
		flex-direction: column;
		padding: 10rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
		border-top: 1rpx solid rgba(255, 255, 255, 0.12);
		border-radius: 8rpx 8rpx 0 0;
		background: #f4f7f6;
		box-shadow: 0 -20rpx 60rpx rgba(0, 0, 0, 0.28);
		color: #20313a;
	}

	.chat-model-picker__handle {
		width: 64rpx;
		height: 7rpx;
		margin: 2rpx auto 16rpx;
		border-radius: 4rpx;
		background: #b9c6cc;
	}

	.chat-model-picker__head,
	.chat-model-option__top,
	.chat-model-option__identity,
	.chat-model-picker__wallet {
		display: flex;
		align-items: center;
	}

	.chat-model-picker__head {
		justify-content: space-between;
		gap: 24rpx;
	}

	.chat-model-picker__head > view {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 5rpx;
	}

	.chat-model-picker__title {
		font-size: 32rpx;
		font-weight: 700;
	}

	.chat-model-picker__subtitle {
		color: #6b7f8d;
		font-size: 21rpx;
		line-height: 1.45;
	}

	.chat-model-picker__close {
		flex: 0 0 56rpx;
		width: 56rpx;
		height: 56rpx;
		color: #526773;
		font-size: 44rpx;
		line-height: 52rpx;
		text-align: center;
	}

	.chat-model-picker__tabs {
		display: grid;
		grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
		gap: 8rpx;
		margin-top: 22rpx;
		padding: 6rpx;
		border-radius: 8rpx;
		background: #e4eae9;
	}

	.chat-model-picker__tab {
		height: 60rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 6rpx;
		color: #627783;
		font-size: 24rpx;
	}

	.chat-model-picker__tab--active {
		background: #ffffff;
		box-shadow: 0 3rpx 12rpx rgba(28, 48, 58, 0.1);
		color: #173847;
		font-weight: 700;
	}

	.chat-model-picker__search {
		height: 70rpx;
		display: flex;
		align-items: center;
		gap: 12rpx;
		margin: 18rpx 0 14rpx;
		padding: 0 20rpx;
		border: 1rpx solid #cfdbdd;
		border-radius: 8rpx;
		background: #ffffff;
	}

	.chat-model-picker__search input {
		min-width: 0;
		flex: 1;
		font-size: 24rpx;
	}

	.chat-model-picker__search > text {
		color: #d36c4c;
		font-size: 22rpx;
	}

	.chat-model-picker__list {
		min-height: 260rpx;
		flex: 1;
	}

	.chat-model-option {
		margin-bottom: 12rpx;
		padding: 20rpx;
		border: 1rpx solid #d4dfe0;
		border-radius: 8rpx;
		background: #ffffff;
	}

	.chat-model-option--active {
		border-color: #e68a58;
		box-shadow: inset 5rpx 0 0 #e68a58;
	}

	.chat-model-option--disabled {
		background: #edf1f0;
		opacity: 0.68;
	}

	.chat-model-option--busy {
		pointer-events: none;
		opacity: 0.6;
	}

	.chat-model-option__top {
		justify-content: space-between;
		gap: 16rpx;
	}

	.chat-model-option__identity {
		min-width: 0;
		flex: 1;
		gap: 9rpx;
	}

	.chat-model-option__name {
		min-width: 0;
		overflow: hidden;
		font-size: 27rpx;
		font-weight: 700;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chat-model-option__badge,
	.chat-model-option__selected {
		flex: 0 0 auto;
		padding: 4rpx 9rpx;
		border-radius: 5rpx;
		font-size: 19rpx;
	}

	.chat-model-option__badge {
		background: #e8efee;
		color: #49636f;
	}

	.chat-model-option__selected {
		background: #fff0e7;
		color: #c45f36;
	}

	.chat-model-option__price {
		flex: 0 0 auto;
		color: #c45f36;
		font-size: 22rpx;
		font-weight: 700;
	}

	.chat-model-option__desc {
		display: block;
		margin-top: 10rpx;
		color: #5e727c;
		font-size: 22rpx;
		line-height: 1.5;
	}

	.chat-model-option__desc--mono {
		word-break: break-all;
	}

	.chat-model-option__meta {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		margin-top: 13rpx;
	}

	.chat-model-option__meta > text {
		padding: 4rpx 9rpx;
		border: 1rpx solid #d9e2e2;
		border-radius: 5rpx;
		color: #637680;
		font-size: 19rpx;
	}

	.chat-model-option__reason {
		display: block;
		margin-top: 10rpx;
		color: #a74b45;
		font-size: 21rpx;
	}

	.chat-model-picker__empty {
		min-height: 260rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 20rpx;
		color: #71848d;
		font-size: 24rpx;
	}

	.chat-model-picker__settings {
		color: #c45f36;
		font-weight: 700;
	}

	.chat-model-picker__wallet {
		flex: 0 0 62rpx;
		gap: 20rpx;
		margin-top: 8rpx;
		padding: 0 4rpx;
		border-top: 1rpx solid #dbe3e3;
		color: #60747e;
		font-size: 21rpx;
	}

	.chat-model-picker__wallet > text:first-child {
		margin-right: auto;
		font-weight: 700;
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

	.tool-i--active {
		color: #ffffff;
		background: rgba(236, 72, 153, 0.32);
	}

	.tool-i--reply {
		color: #ffffff;
		background: linear-gradient(135deg, rgba(59, 154, 184, 0.86) 0%, rgba(127, 214, 221, 0.74) 100%);
		box-shadow: 0 8rpx 18rpx rgba(59, 154, 184, 0.18);
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

	.branch-mask,
	.memory-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2330;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 32rpx 20rpx calc(env(safe-area-inset-bottom) + 24rpx);
		background: rgba(10, 18, 24, 0.32);
		box-sizing: border-box;
	}

	.memory-mask {
		z-index: 2340;
	}

	.branch-sheet,
	.memory-sheet {
		width: 100%;
		max-width: 760rpx;
		max-height: calc(82vh - env(safe-area-inset-bottom));
		min-height: 420rpx;
		padding: 26rpx;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		border-radius: 32rpx;
		background: rgba(255, 255, 255, 0.95);
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		box-shadow: 0 20rpx 52rpx rgba(15, 23, 42, 0.16);
		box-sizing: border-box;
	}

	.branch-sheet {
		height: calc(82vh - env(safe-area-inset-bottom));
	}

	.memory-sheet {
		height: calc(82vh - env(safe-area-inset-bottom));
	}

	.branch-head,
	.memory-head {
		flex-shrink: 0;
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 20rpx;
	}

	.branch-head-copy,
	.memory-head-copy {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.branch-title,
	.memory-title {
		font-size: 32rpx;
		line-height: 1.25;
		font-weight: 800;
		color: #17344b;
	}

	.branch-sub,
	.memory-sub {
		font-size: 23rpx;
		line-height: 1.45;
		color: #64748b;
	}

	.branch-close,
	.memory-close {
		flex-shrink: 0;
		width: 58rpx;
		height: 58rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		color: #64748b;
		background: rgba(241, 245, 249, 0.86);
	}

	.chat-message-speaker {
		display: block;
		margin: 0 0 10rpx;
		font-size: 22rpx;
		line-height: 1.35;
		font-weight: 750;
		color: #2b6177;
	}

	.branch-tabs {
		flex-shrink: 0;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		margin-top: 20rpx;
		padding: 5rpx;
		border-radius: 10rpx;
		background: #edf3f6;
		border: 1rpx solid rgba(87, 119, 136, 0.14);
	}

	.branch-tab {
		height: 62rpx;
		line-height: 62rpx;
		text-align: center;
		border-radius: 7rpx;
		font-size: 24rpx;
		font-weight: 650;
		color: #647b88;
	}

	.branch-tab--active {
		color: #173f52;
		background: #fff;
		box-shadow: 0 4rpx 12rpx rgba(42, 84, 104, 0.1);
	}

	.branch-create {
		flex-shrink: 0;
		height: 68rpx;
		margin-top: 14rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		border-bottom: 1rpx solid rgba(87, 119, 136, 0.18);
		font-size: 24rpx;
		font-weight: 700;
		color: #245b73;
	}

	.branch-scroll,
	.memory-scroll {
		flex: 1;
		min-height: 0;
		margin-top: 20rpx;
	}

	.branch-scroll {
		height: 0;
		width: 100%;
		overflow: hidden;
	}

	.memory-scroll {
		height: 0;
		width: 100%;
		overflow: hidden;
	}

	.memory-list {
		padding-bottom: 8rpx;
		box-sizing: border-box;
	}

	.branch-empty,
	.memory-empty {
		min-height: 220rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 10rpx;
		padding: 24rpx;
		box-sizing: border-box;
	}

	.branch-empty-title,
	.memory-empty-title {
		font-size: 28rpx;
		font-weight: 700;
		color: #334155;
	}

	.branch-empty-sub,
	.memory-empty-sub {
		font-size: 23rpx;
		line-height: 1.5;
		color: #7a8796;
		text-align: center;
	}

	.branch-row,
	.memory-entry {
		padding: 22rpx;
		border-radius: 24rpx;
		background: rgba(248, 250, 252, 0.92);
		border: 1rpx solid rgba(203, 213, 225, 0.52);
		box-sizing: border-box;
	}

	.branch-row--active {
		background: rgba(235, 252, 255, 0.94);
		border-color: rgba(79, 147, 163, 0.46);
		box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.12);
	}

	.branch-row--switching {
		opacity: 0.62;
		pointer-events: none;
	}

	.branch-row + .branch-row,
	.memory-entry + .memory-entry {
		margin-top: 18rpx;
	}

	.branch-row-head,
	.memory-entry-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 18rpx;
	}

	.branch-row-title-wrap,
	.memory-entry-title-wrap {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.branch-row-title,
	.memory-entry-title {
		font-size: 27rpx;
		line-height: 1.35;
		font-weight: 750;
		color: #172033;
	}

	.branch-row-title-line,
	.branch-row-side,
	.branch-row-actions {
		display: flex;
		align-items: center;
	}

	.branch-row-title-line {
		min-width: 0;
		gap: 6rpx;
	}

	.branch-row-side {
		flex-shrink: 0;
		gap: 12rpx;
	}

	.branch-row-actions {
		gap: 10rpx;
		padding: 2rpx 0;
	}

	.branch-row-preview,
	.memory-entry-content {
		display: block;
		font-size: 24rpx;
		line-height: 1.55;
		color: #475569;
		word-break: break-word;
	}

	.branch-row-current,
	.memory-entry-status {
		flex-shrink: 0;
		padding: 7rpx 14rpx;
		border-radius: 999rpx;
		font-size: 21rpx;
		line-height: 1.25;
		font-weight: 700;
		color: #1f6686;
		background: rgba(220, 247, 251, 0.84);
	}

	.branch-row-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 10rpx;
		margin-top: 16rpx;
	}

	.branch-row-meta-text {
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
		font-size: 21rpx;
		line-height: 1.25;
		font-weight: 650;
		color: #64748b;
		background: rgba(241, 245, 249, 0.9);
	}

	.branch-editor-mask {
		position: absolute;
		inset: 0;
		z-index: 3;
		display: flex;
		align-items: flex-end;
		background: rgba(15, 23, 42, 0.34);
	}

	.branch-editor {
		width: 100%;
		padding: 30rpx;
		box-sizing: border-box;
		border-radius: 22rpx 22rpx 0 0;
		background: #ffffff;
	}

	.branch-editor-title {
		display: block;
		font-size: 29rpx;
		font-weight: 750;
		color: #172033;
	}

	.branch-editor-input {
		height: 78rpx;
		margin-top: 22rpx;
		padding: 0 20rpx;
		box-sizing: border-box;
		border: 1rpx solid #cbd5e1;
		border-radius: 8rpx;
		font-size: 27rpx;
		color: #172033;
		background: #ffffff;
	}

	.branch-editor-actions {
		display: flex;
		justify-content: flex-end;
		gap: 18rpx;
		margin-top: 26rpx;
	}

	.branch-editor-button {
		min-width: 112rpx;
		height: 66rpx;
		line-height: 66rpx;
		text-align: center;
		border-radius: 8rpx;
		font-size: 25rpx;
		font-weight: 700;
		color: #475569;
		background: #f1f5f9;
	}

	.branch-editor-button--primary {
		color: #ffffff;
		background: #24647d;
	}

	.memory-stats {
		flex-shrink: 0;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 12rpx;
		margin-top: 20rpx;
	}

	.memory-stat {
		padding: 16rpx;
		border-radius: 20rpx;
		background: rgba(248, 250, 252, 0.9);
		border: 1rpx solid rgba(203, 213, 225, 0.46);
		box-sizing: border-box;
	}

	.memory-stat-label,
	.memory-stat-value {
		display: block;
		text-align: center;
	}

	.memory-stat-label {
		font-size: 21rpx;
		line-height: 1.25;
		color: #64748b;
	}

	.memory-stat-value {
		margin-top: 8rpx;
		font-size: 32rpx;
		line-height: 1.15;
		font-weight: 850;
		color: #17344b;
	}

	.memory-summary {
		flex-shrink: 0;
		display: block;
		margin-top: 16rpx;
		padding: 16rpx 18rpx;
		border-radius: 20rpx;
		background: rgba(235, 252, 255, 0.72);
		font-size: 24rpx;
		line-height: 1.55;
		color: #35556a;
		word-break: break-word;
	}

	.memory-actions {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-top: 16rpx;
	}

	.memory-guide {
		min-width: 0;
		font-size: 21rpx;
		line-height: 1.45;
		color: #64748b;
	}

	.memory-action-row {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 10rpx;
	}

	.memory-icon-button {
		width: 64rpx;
		height: 64rpx;
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		box-sizing: border-box;
	}

	.memory-icon-button--primary {
		background: #4f93a3;
		box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.16);
	}

	.memory-icon-button--secondary {
		background: rgba(226, 232, 240, 0.92);
		border: 1rpx solid rgba(148, 163, 184, 0.3);
	}

	.memory-icon-button--add {
		background: rgba(220, 247, 251, 0.9);
		border: 1rpx solid rgba(79, 147, 163, 0.28);
	}

	.memory-icon-button--disabled {
		opacity: 0.58;
		pointer-events: none;
	}

	.memory-icon--spin {
		animation: memory-icon-spin 0.9s linear infinite;
	}

	@keyframes memory-icon-spin {
		from { transform: rotate(0deg); }
		to { transform: rotate(360deg); }
	}

	.memory-error {
		flex-shrink: 0;
		display: block;
		margin-top: 12rpx;
		padding: 12rpx 16rpx;
		border-radius: 18rpx;
		font-size: 23rpx;
		line-height: 1.45;
		color: #b42318;
		background: rgba(254, 226, 226, 0.88);
	}

	.memory-filters {
		flex-shrink: 0;
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 6rpx;
		margin-top: 16rpx;
		padding: 6rpx;
		border-radius: 16rpx;
		background: rgba(226, 232, 240, 0.7);
		box-sizing: border-box;
	}

	.memory-filter {
		min-width: 0;
		height: 58rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 7rpx;
		padding: 0 10rpx;
		border-radius: 12rpx;
		color: #5f7182;
		box-sizing: border-box;
	}

	.memory-filter--active {
		color: #ffffff;
		background: #4f93a3;
		box-shadow: 0 6rpx 14rpx rgba(48, 103, 117, 0.16);
	}

	.memory-filter-label,
	.memory-filter-count {
		font-size: 21rpx;
		line-height: 1;
		font-weight: 700;
	}

	.memory-filter-count {
		min-width: 28rpx;
		height: 28rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 5rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.52);
		box-sizing: border-box;
	}

	.memory-entry--disabled {
		opacity: 0.62;
	}

	.memory-entry--archived {
		background: rgba(248, 250, 252, 0.68);
		border-style: dashed;
		border-color: rgba(148, 163, 184, 0.52);
	}

	.memory-entry-type {
		align-self: flex-start;
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		line-height: 1.2;
		font-weight: 700;
		color: #226b8b;
		background: rgba(220, 247, 251, 0.72);
	}

	.memory-entry-status--disabled {
		color: #64748b;
		background: rgba(226, 232, 240, 0.92);
	}

	.memory-entry-status--archived {
		color: #5d6472;
		background: rgba(226, 232, 240, 0.78);
	}

	.memory-entry-content {
		margin-top: 14rpx;
	}

	.memory-entry-archive-reason {
		display: block;
		margin-top: 12rpx;
		padding: 10rpx 12rpx;
		border-radius: 12rpx;
		font-size: 21rpx;
		line-height: 1.4;
		color: #667085;
		background: rgba(226, 232, 240, 0.62);
	}

	.memory-keywords {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		margin-top: 14rpx;
	}

	.memory-keyword {
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		line-height: 1.2;
		color: #36526a;
		background: rgba(226, 232, 240, 0.82);
	}

	.memory-keyword--secondary {
		color: #667085;
		background: rgba(241, 245, 249, 0.9);
	}

	.memory-entry-foot {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-top: 16rpx;
	}

	.memory-entry-meta {
		min-width: 0;
		font-size: 21rpx;
		line-height: 1.35;
		color: #7a8796;
	}

	.memory-entry-buttons {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.memory-entry-icon {
		width: 56rpx;
		height: 56rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		box-sizing: border-box;
	}

	.memory-entry-icon--disable {
		background: rgba(254, 226, 226, 0.88);
	}

	.memory-entry-icon--enable {
		background: rgba(220, 252, 231, 0.9);
	}

	.memory-entry-icon--delete {
		background: rgba(254, 242, 242, 0.92);
		border: 1rpx solid rgba(185, 28, 28, 0.12);
	}

	.memory-entry-icon--edit {
		background: rgba(220, 247, 251, 0.86);
		border: 1rpx solid rgba(79, 147, 163, 0.18);
	}

	.memory-entry-icon--busy {
		opacity: 0.58;
		pointer-events: none;
	}

	.memory-page-state {
		min-height: 64rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		padding: 12rpx 16rpx 4rpx;
		font-size: 21rpx;
		line-height: 1.35;
		color: #7a8796;
		text-align: center;
		box-sizing: border-box;
	}

	.memory-page-state--error {
		color: #b42318;
	}

	.memory-editor-mask {
		position: absolute;
		inset: 0;
		z-index: 6;
		display: flex;
		align-items: flex-end;
		background: rgba(15, 23, 42, 0.38);
	}

	.memory-editor {
		width: 100%;
		max-height: 90%;
		padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
		border-radius: 16rpx 16rpx 0 0;
		background: #ffffff;
		box-sizing: border-box;
	}

	.memory-editor-head,
	.memory-priority-row,
	.memory-switch-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 20rpx;
	}

	.memory-editor-title {
		font-size: 30rpx;
		font-weight: 800;
		color: #172033;
	}

	.memory-field-label {
		display: block;
		margin: 22rpx 0 10rpx;
		font-size: 23rpx;
		font-weight: 700;
		color: #334155;
	}

	.memory-field-label--inline {
		margin: 0 0 5rpx;
	}

	.memory-picker,
	.memory-editor-input,
	.memory-editor-textarea,
	.memory-priority-input {
		border: 1rpx solid #cbd5e1;
		border-radius: 8rpx;
		background: #ffffff;
		font-size: 25rpx;
		color: #172033;
		box-sizing: border-box;
	}

	.memory-picker {
		height: 72rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 18rpx;
	}

	.memory-editor-input {
		height: 72rpx;
		padding: 0 18rpx;
	}

	.memory-editor-textarea {
		width: 100%;
		height: 190rpx;
		padding: 16rpx 18rpx;
		line-height: 1.5;
	}

	.memory-priority-row,
	.memory-switch-row {
		margin-top: 22rpx;
		padding: 18rpx 0;
		border-top: 1rpx solid rgba(203, 213, 225, 0.68);
	}

	.memory-priority-input {
		width: 116rpx;
		height: 66rpx;
		padding: 0 14rpx;
		text-align: center;
	}

	.memory-switch-title,
	.memory-field-help {
		display: block;
	}

	.memory-switch-title {
		font-size: 24rpx;
		font-weight: 700;
		color: #334155;
	}

	.memory-field-help {
		margin-top: 5rpx;
		font-size: 20rpx;
		line-height: 1.4;
		color: #64748b;
	}

	.memory-switch-row--disabled {
		opacity: 0.52;
	}

	.memory-editor-actions {
		display: flex;
		justify-content: flex-end;
		gap: 16rpx;
		margin-top: 26rpx;
	}

	.memory-editor-button {
		min-width: 112rpx;
		height: 66rpx;
		line-height: 66rpx;
		text-align: center;
		border-radius: 8rpx;
		font-size: 24rpx;
		font-weight: 700;
		color: #475569;
		background: #f1f5f9;
	}

	.memory-editor-button--primary {
		color: #ffffff;
		background: #24647d;
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
		gap: 12rpx;
		overflow-x: auto;
		padding: 2rpx 2rpx 4rpx;
		scroll-snap-type: x proximity;
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
		flex: 0 0 360rpx;
		min-height: 132rpx;
		padding: 18rpx 18rpx;
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.06);
		border: 1rpx solid rgba(255, 255, 255, 0.06);
		box-sizing: border-box;
		scroll-snap-align: start;
		transition: transform 0.16s ease, background 0.16s ease;
	}

	.reply-help-card:active {
		transform: scale(0.985);
		background: rgba(255, 255, 255, 0.1);
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
		font-size: 25rpx;
		line-height: 1.52;
		color: #f8fafc;
		display: -webkit-box;
		overflow: hidden;
		-webkit-line-clamp: 4;
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

	.attach-fab-backdrop {
		position: fixed;
		inset: 0;
		z-index: 4;
		background: transparent;
	}

	.composer-image-strip {
		width: 100%;
		max-width: 100%;
		min-width: 0;
		box-sizing: border-box;
		overflow-x: hidden;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 12rpx;
		padding: 0 18rpx 12rpx;
	}

	.composer-image-card {
		flex: 0 0 138rpx;
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
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		padding: 12rpx;
		background: rgba(8, 12, 22, 0.46);
	}

	.composer-image-mask-text {
		color: #fff;
		font-size: 22rpx;
		line-height: 1.35;
		text-align: center;
	}

	.composer-image-retry {
		padding: 5rpx 12rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		line-height: 1.2;
		font-weight: 700;
		color: #fff;
		background: rgba(255, 255, 255, 0.22);
	}

	.composer-image-hint {
		flex: 1 1 220rpx;
		min-width: 0;
		max-width: 100%;
		padding: 8rpx 14rpx;
		border-radius: 999rpx;
		font-size: 21rpx;
		line-height: 1.2;
		color: rgba(255, 255, 255, 0.82);
		background: rgba(8, 12, 22, 0.18);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		box-sizing: border-box;
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

	.stop-stream {
		font-size: 24rpx;
		color: #f87171;
		padding: 6rpx 20rpx;
		border-radius: 999rpx;
		border: 1rpx solid rgba(248, 113, 113, 0.45);
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
	.reply-help-panel {
		background: rgba(255, 255, 255, 0.3) !important;
		border-color: rgba(255, 255, 255, 0.28) !important;
		box-shadow: 0 12rpx 28rpx rgba(67, 112, 142, 0.08);
		backdrop-filter: blur(22rpx) saturate(135%);
		-webkit-backdrop-filter: blur(22rpx) saturate(135%);
	}

	.tool-i,
	.reply-help-head-btn,
	.reply-help-trigger {
		color: #1c5975;
		background: rgba(220, 247, 251, 0.72);
	}

	.reply-help-trigger,
	.edit-btn--primary,
	.commercial-btn--primary {
		color: #fff;
		background: #4f93a3;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.18);
	}

	.reply-help-text,
	.edit-title,
	.commercial-title {
		color: #17344b !important;
		text-shadow: none !important;
	}

	.edit-ta {
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
	.typing-hint {
		color: #4e6678;
	}

	.chat-fill-retry {
		background: #4f93a3;
	}

	.chat-fill-back,
	.nav-link {
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

	/* Transparent dark glass keeps chat readable on bright character backgrounds. */
	.wrap--with-bg::after {
		background: rgba(255, 255, 255, 0.02) !important;
	}

	.tool-bar,
	.memory-bar,
	.reply-help-panel,
	.ai-disclaimer {
		flex-shrink: 0;
	}

	.user-edit-tag {
		color: rgba(222, 235, 244, 0.78) !important;
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

	.reply-help-trigger,
	.edit-btn--primary,
	.commercial-btn--primary {
		align-self: flex-end;
		flex-shrink: 0;
		margin-left: 0;
		margin-right: 0;
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
	.wrap--app-plus .ai-disclaimer {
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
	.wrap--app-plus .user-edit-tag {
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

	.wrap--app-plus .branch-mask,
	.wrap--app-plus .memory-mask,
	.wrap--app-plus .character-voice-mask,
	.wrap--app-plus .image-quick-mask {
		padding: 18rpx 16rpx calc(env(safe-area-inset-bottom) + 12rpx) !important;
	}

	.wrap--app-plus .branch-sheet,
	.wrap--app-plus .memory-sheet,
	.wrap--app-plus .character-voice-sheet {
		border-radius: 32rpx;
	}

	.wrap--app-plus .image-quick-card {
		max-height: calc(100vh - 148rpx - env(safe-area-inset-bottom)) !important;
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

	.wrap--app-plus .reply-help-trigger {
		height: 72rpx;
		line-height: 72rpx;
		align-self: center;
		border-radius: 28rpx;
		color: #fff !important;
		background: linear-gradient(135deg, #2f86a8 0%, #70cedb 64%, #e5a9c3 100%) !important;
		box-shadow: 0 10rpx 22rpx rgba(47, 134, 168, 0.2);
	}

	.wrap--app-plus .reply-help-trigger {
		padding: 0 24rpx;
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

	/* #endif */

	.tool-i--reply,
	.wrap--app-plus .tool-i--reply {
		color: #ffffff !important;
		background: linear-gradient(135deg, rgba(59, 154, 184, 0.92) 0%, rgba(127, 214, 221, 0.78) 100%) !important;
		border: 1rpx solid rgba(184, 241, 248, 0.42);
		box-shadow: 0 8rpx 18rpx rgba(38, 110, 130, 0.2);
	}

	.tool-i--reply.tool-i--active,
	.wrap--app-plus .tool-i--reply.tool-i--active {
		background: linear-gradient(135deg, rgba(236, 72, 153, 0.82) 0%, rgba(59, 154, 184, 0.86) 100%) !important;
	}

	.reply-help-list,
	.wrap--app-plus .reply-help-list {
		gap: 12rpx;
		padding: 2rpx 2rpx 4rpx;
		scroll-snap-type: x proximity;
	}

	.reply-help-card,
	.wrap--app-plus .reply-help-card {
		flex-basis: 360rpx;
		min-height: 132rpx;
		padding: 18rpx;
		scroll-snap-align: start;
	}

	.reply-help-text,
	.wrap--app-plus .reply-help-text {
		-webkit-line-clamp: 4;
		line-height: 1.52;
	}
</style>



<style>
page {
		background-color: transparent;
		background-image: url('/static/login.png');
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
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


	.voice-status-card {
		margin: 0 18rpx 12rpx auto;
		max-width: 72%;
		min-width: 0;
		box-sizing: border-box;
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

	.expression-panel {
		margin: 0 18rpx 10rpx;
		width: calc(100% - 36rpx);
		max-width: calc(100% - 36rpx);
		box-sizing: border-box;
		overflow-x: hidden;
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
		min-width: 0;
		margin-bottom: 18rpx;
	}

	.expression-panel-actions {
		display: flex;
		align-items: center;
		gap: 10rpx;
		flex-shrink: 0;
	}

	.expression-panel-title-wrap {
		min-width: 0;
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

	/* #ifdef APP-PLUS */
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
	.wrap--app-plus .ai-disclaimer {
		backdrop-filter: none !important;
		-webkit-backdrop-filter: none !important;
	}

	.wrap--app-plus.focused .chat-scroll {
		padding-bottom: 56rpx !important;
	}

	.wrap--app-plus .message-action-mask {
		z-index: 2000 !important;
	}

	.wrap--app-plus .message-action-menu {
		z-index: 2001 !important;
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
