<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="ui.title" mode="dark" @back="goBack" />
		<scroll-view class="body" scroll-y :show-scrollbar="false">
			<view v-if="characterId" class="scope-switcher">
				<view class="scope-option" :class="{ 'scope-option--active': scopeMode === 'global' }" @tap="switchScope('global')">{{ ui.globalScope }}</view>
				<view class="scope-option" :class="{ 'scope-option--active': scopeMode === 'character' }" @tap="switchScope('character')">{{ ui.characterScope }}</view>
			</view>
			<view v-if="syncStatusText" class="sync-state" :class="'sync-state--' + syncState.status" @tap="handleSyncStateAction">
				<text class="sync-state-text">{{ syncStatusText }}</text>
				<text class="sync-state-action">{{ syncActionText }}</text>
			</view>
			<view class="hero-panel">
				<view class="hero-head">
					<view class="hero-heading">
						<view class="hero-icon">
							<u-icon name="eye-fill" color="#ffffff" size="28"></u-icon>
						</view>
						<text class="hero-title">{{ ui.livePreview }}</text>
					</view>
					<view class="hero-state">
						<text>{{ bubbleModeName }}</text>
						<text>{{ currentReadModeName }}</text>
						<text>{{ currentSplitModeName }}</text>
					</view>
				</view>
				<view class="preview-scene-tabs">
					<view
						v-for="item in previewScenes"
						:key="item.code"
						class="preview-scene-tab"
						:class="{ 'preview-scene-tab--active': previewScene === item.code }"
						@tap="previewScene = item.code"
					>{{ previewSceneName(item.code) }}</view>
				</view>
				<view class="preview-chat" :class="previewChatClass" :style="previewChatStyle">
					<template v-if="previewScene === 'text'">
					<view class="chat-message-row chat-message-row--assistant preview-message-row">
						<view class="preview-avatar"><u-icon name="chat-fill" color="#ffffff" size="25"></u-icon></view>
						<view class="preview-bubble-stack" :class="{ 'preview-bubble-stack--split': form.replySplitMode === 'bubble' }">
							<message-bubble
								v-for="(group, groupIndex) in previewBubbleGroups"
								:key="group.key"
								:bubble-class="previewBubbleClass(false)"
								:bubble-style="previewAssistantBubbleStyle(groupIndex, previewBubbleGroups.length)"
							>
								<message-content :has-text="true">
									<!-- #ifdef H5 -->
									<view class="preview-markdown md-inner" :style="previewAssistantTextStyle()" v-html="previewBubbleHtml(group)"></view>
									<!-- #endif -->
									<!-- #ifndef H5 -->
									<view class="preview-segments">
										<view
											v-for="(segment, segmentIndex) in group.segments"
											:key="group.key + '-' + segmentIndex"
											class="app-preview-segment"
											:class="['app-preview-segment--' + segment.type, { 'preview-seg--soft': segment.soft }]"
										>
											<view class="preview-seg-line" :class="{ 'preview-seg-line--labeled': form.showSegmentLabels }">
											<view v-if="form.showSegmentLabels" class="preview-label" :style="previewSegmentLabelStyle(segment)"><text class="preview-label-text" :style="previewSegmentLabelTextStyle(segment)">{{ segment.label }}</text></view>
												<text class="preview-text" :class="'st-chat-seg-text--' + segment.type" :style="previewSegmentTextStyle(segment)">{{ segment.text }}</text>
											</view>
										</view>
									</view>
									<!-- #endif -->
								</message-content>
							</message-bubble>
						</view>
					</view>
					<view class="chat-message-row chat-message-row--user preview-message-row preview-message-row--user">
						<message-bubble :bubble-class="previewBubbleClass(true)" :bubble-style="previewBubbleStyle(true)">
							<message-content :has-text="true">
								<text class="chat-message-user-text" :style="previewUserTextStyle()">{{ ui.previewUser }}</text>
							</message-content>
						</message-bubble>
					</view>
					</template>
					<template v-else-if="previewScene === 'media'">
						<view class="chat-message-row chat-message-row--assistant preview-message-row">
							<view class="preview-avatar"><u-icon name="chat-fill" color="#ffffff" size="25"></u-icon></view>
							<message-bubble :bubble-class="previewBubbleClass(false, true)" :bubble-style="previewImageBubbleStyle(false)">
								<image class="preview-media-image" src="/static/home/homebg.png" mode="aspectFill"></image>
							</message-bubble>
						</view>
						<view class="chat-message-row chat-message-row--user preview-message-row preview-message-row--user">
							<message-bubble :bubble-class="previewBubbleClass(true)" :bubble-style="previewBubbleStyle(true)">
								<voice-message-card message-id="preview" duration-label="0:12" :transcript-text="ui.previewUser"></voice-message-card>
							</message-bubble>
						</view>
					</template>
					<template v-else>
						<view class="chat-message-row chat-message-row--assistant preview-message-row">
							<view class="preview-avatar"><u-icon name="chat-fill" color="#ffffff" size="25"></u-icon></view>
							<message-bubble :bubble-class="previewBubbleClass(false)" :bubble-style="previewBubbleStyle(false)">
								<message-content :has-text="true">
									<!-- #ifdef H5 -->
									<view class="preview-markdown md-inner" :style="previewAssistantTextStyle()" v-html="previewStatusHtml"></view>
									<!-- #endif -->
									<!-- #ifndef H5 -->
									<view class="preview-native-status">
										<text class="preview-native-status-title">{{ ui.previewStatusTitle }}</text>
										<text class="preview-native-status-body">{{ ui.previewStatusBody }}</text>
									</view>
									<!-- #endif -->
									<message-actions :show-assistant-voice="true" :assistant-voice-label="ui.previewVoice" :show-swipe-controls="true" swipe-label="2 / 3"></message-actions>
								</message-content>
							</message-bubble>
						</view>
					</template>
				</view>
			</view>

			<view class="section section--bubble">
				<view class="section-head">
					<view class="section-heading">
						<view class="section-icon"><u-icon name="chat-fill" color="#3e8dab" size="25"></u-icon></view>
						<text class="section-title">{{ ui.bubbleAppearance }}</text>
					</view>
					<view v-if="scopeMode === 'character'" class="inherit-control" @tap="toggleInheritance('bubble')">{{ inheritBubble ? ui.inheritGlobal : ui.characterSpecific }}</view>
					<text v-else class="section-value">{{ bubbleModeName }}</text>
				</view>
				<view class="choice-grid">
					<view class="choice" :class="{ 'choice--active': !form.bubbleCustomized }" @tap="setBubbleMode(false)">
						<text class="choice-title">{{ ui.followDefault }}</text>
					</view>
					<view class="choice" :class="{ 'choice--active': form.bubbleCustomized }" @tap="setBubbleMode(true)">
						<text class="choice-title">{{ ui.customAppearance }}</text>
					</view>
				</view>
				<template v-if="form.bubbleCustomized">
					<text class="group-title">{{ ui.presetTitle }}</text>
					<view class="preset-grid">
						<view
							v-for="item in customPresets"
							:key="item.code"
							class="preset"
							:class="{ 'preset--active': form.preset === item.code }"
							@tap="selectPreset(item.code)"
						>
							<view class="preset-visual">
								<view class="preset-bubble preset-bubble--char" :style="presetVisualStyle(item, false)">
									<view class="preset-line-stack">
										<view class="preset-line preset-line--narration" :style="presetLineStyle(item, false, 'narration')"></view>
										<view class="preset-line preset-line--speech" :style="presetLineStyle(item, false, 'speech')"></view>
										<view class="preset-line preset-line--action" :style="presetLineStyle(item, false, 'action')"></view>
									</view>
								</view>
								<view class="preset-bubble preset-bubble--user" :style="presetVisualStyle(item, true)">
									<view class="preset-line preset-line--user" :style="presetLineStyle(item, true, 'user')"></view>
								</view>
							</view>
							<text class="preset-name">{{ item.name }}</text>
							<text class="preset-desc">{{ item.desc }}</text>
						</view>
					</view>

					<text class="group-title group-title--compact">{{ ui.shapeText }}</text>
					<view class="control-grid">
						<view class="control" v-for="item in numberControls" :key="item.key">
							<text class="control-label">{{ controlLabel(item.key) }}</text>
							<slider
								class="compact-slider"
								:value="form[item.key]"
								:min="item.min"
								:max="item.max"
								:step="item.step"
								activeColor="#2f7d8a"
								backgroundColor="rgba(47,125,138,0.16)"
								block-color="#ffffff"
								block-size="18"
								@changing="onSliderChanging(item.key, $event)"
								@change="onSliderChanging(item.key, $event)"
							/>
							<text class="control-value">{{ formatControlValue(item) }}</text>
						</view>
					</view>

					<text class="group-title">{{ ui.advancedVisual }}</text>
					<text class="control-label profile-label">{{ ui.typographyStyle }}</text>
					<view class="mode-grid mode-grid--advanced">
						<view v-for="item in typographyProfiles" :key="item.code" class="mode" :class="{ 'mode--active': currentTypographyProfile === item.code }" @tap="selectTypographyProfile(item.code)">
							<text class="mode-title">{{ typographyProfileName(item.code) }}</text>
						</view>
					</view>
					<text class="control-label profile-label">{{ ui.surfaceStyle }}</text>
					<view class="mode-grid mode-grid--advanced">
						<view v-for="item in surfaceModes" :key="item.code" class="mode" :class="{ 'mode--active': form.surfaceMode === item.code }" @tap="setBubbleValue('surfaceMode', item.code)">
							<text class="mode-title">{{ surfaceModeName(item.code) }}</text>
						</view>
					</view>
					<text class="control-label profile-label">{{ ui.contentToneStyle }}</text>
					<view class="mode-grid mode-grid--tone">
						<view v-for="item in contentTones" :key="item.code" class="mode" :class="{ 'mode--active': form.contentTone === item.code }" @tap="setBubbleValue('contentTone', item.code)">
							<text class="mode-title">{{ contentToneName(item.code) }}</text>
						</view>
					</view>
					<view class="switch-row" :class="{ 'switch-row--active': form.thoughtItalic }" @tap="toggleThoughtItalic">
						<text class="switch-title">{{ ui.thoughtItalic }}</text>
						<view class="switch" :class="{ 'switch--on': form.thoughtItalic }"><view class="switch-dot"></view></view>
					</view>
					<view class="control-grid control-grid--advanced">
						<view class="control" v-for="item in advancedNumberControls" :key="item.key">
							<text class="control-label">{{ controlLabel(item.key) }}</text>
							<slider class="compact-slider" :value="form[item.key]" :min="item.min" :max="item.max" :step="item.step" activeColor="#8a5263" backgroundColor="rgba(138,82,99,0.14)" block-color="#ffffff" block-size="18" @changing="onSliderChanging(item.key, $event)" @change="onSliderChanging(item.key, $event)" />
							<text class="control-value">{{ formatControlValue(item) }}</text>
						</view>
					</view>

					<text class="group-title">{{ ui.bubbleBorder }}</text>
					<view class="color-grid">
						<view class="color-field" v-for="item in surfaceColorControls" :key="item.key">
							<view class="color-head">
								<text class="control-label">{{ colorLabel(item.key) }}</text>
								<view class="color-preview" :style="{ background: form[item.key] }"></view>
							</view>
							<view class="swatch-row">
								<view
									v-for="color in item.swatches"
									:key="item.key + color"
									class="swatch"
									:class="{ 'swatch--active': normalizeColor(form[item.key]) === normalizeColor(color) }"
									:style="{ background: color }"
									@tap="setBubbleValue(item.key, color)"
								></view>
							</view>
							<input
								class="color-input"
								:class="{ 'color-input--invalid': !isValidColor(form[item.key]) }"
								:value="form[item.key]"
								maxlength="7"
								@input="onColorInput(item.key, $event)"
							/>
						</view>
					</view>

					<view class="group-title-row">
						<view>
							<text class="group-title group-title--inline">{{ ui.textColor }}</text>
							<text class="group-subtitle">{{ textColorModeText }}</text>
						</view>
						<view
							v-if="hasTextColorOverrides"
							class="icon-btn"
							hover-class="icon-btn--pressed"
							:aria-label="ui.resetTextAria"
							@tap="resetTextColors"
						>
							<fui-icon name="refresh" :size="34" color="#2f7d8a"></fui-icon>
						</view>
					</view>
					<view class="color-grid color-grid--text">
						<view
							class="color-field color-field--text"
							:class="{ 'color-field--overridden': isTextColorOverridden(item.key) }"
							v-for="item in textColorControls"
							:key="item.key"
						>
							<view class="color-head">
								<view class="text-color-label">
									<view v-if="item.type" class="type-dot" :class="'type-dot--' + item.type"></view>
									<text class="control-label">{{ colorLabel(item.key) }}</text>
								</view>
								<view class="color-preview" :style="{ background: form[item.key] }"></view>
							</view>
							<view class="swatch-row">
								<view
									v-for="color in item.swatches"
									:key="item.key + color"
									class="swatch"
									:class="{ 'swatch--active': normalizeColor(form[item.key]) === normalizeColor(color) }"
									:style="{ background: color }"
									@tap="setBubbleValue(item.key, color)"
								></view>
							</view>
							<input
								class="color-input"
								:class="{ 'color-input--invalid': !isValidColor(form[item.key]) }"
								:value="form[item.key]"
								maxlength="7"
								@input="onColorInput(item.key, $event)"
							/>
						</view>
					</view>
				</template>
			</view>

			<view class="section section--reading">
				<view class="section-head">
					<view class="section-heading">
						<view class="section-icon section-icon--pink"><u-icon name="file-text" color="#b65f83" size="25"></u-icon></view>
						<text class="section-title">{{ ui.roleplayReading }}</text>
					</view>
					<view v-if="scopeMode === 'character'" class="inherit-control" @tap="toggleInheritance('reading')">{{ inheritReading ? ui.inheritGlobal : ui.characterSpecific }}</view>
					<text v-else class="section-value">{{ currentReadModeName }}</text>
				</view>
				<view class="mode-grid mode-grid--read">
					<view v-for="item in readModes" :key="item.code" class="mode" :class="{ 'mode--active': form.readMode === item.code }" @tap="setReadingValue('readMode', item.code)">
						<text class="mode-title">{{ readModeName(item.code) }}</text>
					</view>
				</view>
				<view class="switch-row" :class="{ 'switch-row--active': form.showSegmentLabels }" @tap="toggleSegmentLabels">
					<text class="switch-title">{{ ui.segmentLabels }}</text>
					<view class="switch" :class="{ 'switch--on': form.showSegmentLabels }">
						<view class="switch-dot"></view>
					</view>
				</view>
			</view>

			<view class="section section--reply">
				<view class="section-head">
					<view class="section-heading">
						<view class="section-icon"><u-icon name="grid-fill" color="#3e8dab" size="25"></u-icon></view>
						<text class="section-title">{{ ui.aiReplyFormat }}</text>
					</view>
					<view v-if="scopeMode === 'character'" class="inherit-control" @tap="toggleInheritance('reply')">{{ inheritReplyFormat ? ui.inheritGlobal : ui.characterSpecific }}</view>
					<text v-else class="section-value">{{ currentSplitModeName }}</text>
				</view>
				<view class="mode-grid mode-grid--split">
					<view v-for="item in splitModes" :key="item.code" class="mode" :class="{ 'mode--active': form.replySplitMode === item.code }" @tap="setReplyValue(item.code)">
						<text class="mode-title">{{ splitModeName(item.code) }}</text>
					</view>
				</view>
			</view>

			<view class="actions">
				<view class="btn btn--ghost" @tap="reset">{{ ui.restoreDefault }}</view>
				<view class="btn btn--primary" @tap="save">{{ ui.saveApply }}</view>
			</view>
			<view class="save-state" :class="{ 'save-state--dirty': hasUnsavedChanges }">
				<view class="save-state-dot"></view>
				<text class="save-state-text">{{ hasUnsavedChanges ? ui.dirty : ui.applied }}</text>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
import MessageBubble from '@/components/tavern/message-bubble.vue';
import MessageContent from '@/components/tavern/message-content.vue';
import MessageActions from '@/components/tavern/message-actions.vue';
import VoiceMessageCard from '@/components/tavern/voice-message-card.vue';
const chatAppearance = require('@/common/chatAppearance.js');
const tavernApi = require('@/common/tavernApi.js');
const chatMarkdown = require('@/common/chatMarkdown.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');
const { getAppearanceCopy } = require('@/common/chatAppearanceI18n.js');

function loadAppearanceConfigSafely() {
	try {
		return chatAppearance.loadConfig();
	} catch (e) {
		console.error('[chat-appearance] load config failed', e);
		return chatAppearance.normalizeConfig(null);
	}
}

function appearanceDraftSignature(config, scopeMode, inheritance) {
	const inherited = scopeMode === 'character' && inheritance && typeof inheritance === 'object' ? inheritance : {};
	return JSON.stringify({
		config: chatAppearance.normalizeConfig(config),
		inheritBubble: inherited.inheritBubble === true,
		inheritReading: inherited.inheritReading === true,
		inheritReplyFormat: inherited.inheritReplyFormat === true
	});
}

export default {
	components: { TavernNavBar, MessageBubble, MessageContent, MessageActions, VoiceMessageCard },
	data() {
		const initialConfig = loadAppearanceConfigSafely();
		let isAppPlus = false;
		// #ifdef APP-PLUS
		isAppPlus = true;
		// #endif
		return {
			isAppPlus,
			characterId: 0,
			scopeMode: 'global',
			inheritBubble: true,
			inheritReading: true,
			inheritReplyFormat: true,
			globalConfig: initialConfig,
			detachedDrafts: { bubble: null, reading: null, replyFormat: null },
			cloudSaving: false,
			appearanceDisposed: false,
			appearanceRequestGuard: null,
			scopeRequestVersion: 0,
			saveRequestVersion: 0,
			syncState: { status: 'synced', pending: null, conflict: null, lastError: '' },
			form: initialConfig,
			savedConfigSignature: appearanceDraftSignature(initialConfig, 'global', null),
			presets: chatAppearance.PRESETS,
			previewScene: 'text',
			previewScenes: [{ code: 'text' }, { code: 'media' }, { code: 'status' }],
			readModes: [
				{ code: 'original' },
				{ code: 'novel' },
				{ code: 'speechOnly' },
				{ code: 'hideThought' },
				{ code: 'softAction' }
			],
			splitModes: [
				{ code: 'none' },
				{ code: 'bubble' }
			],
			numberControls: [
				{ key: 'fontSize', min: 24, max: 36, step: 1, unit: 'rpx' },
				{ key: 'lineHeight', min: 1.35, max: 2.1, step: 0.01, unit: '' },
				{ key: 'radius', min: 8, max: 32, step: 1, unit: 'rpx' },
				{ key: 'opacity', min: 30, max: 96, step: 1, unit: '%' },
				{ key: 'charMaxWidth', min: 62, max: 92, step: 1, unit: '%' },
				{ key: 'userMaxWidth', min: 58, max: 88, step: 1, unit: '%' },
				{ key: 'bubblePaddingY', min: 8, max: 26, step: 1, unit: 'rpx' },
				{ key: 'bubblePaddingX', min: 12, max: 34, step: 1, unit: 'rpx' },
				{ key: 'imagePadding', min: 0, max: 18, step: 1, unit: 'rpx' },
				{ key: 'backdropStrength', min: 0, max: 55, step: 1, unit: '%' }
			],
			typographyProfiles: [
				{ code: 'light' },
				{ code: 'balanced' },
				{ code: 'emphasis' },
				{ code: 'custom' }
			],
			surfaceModes: [
				{ code: 'flat' },
				{ code: 'softGradient' },
				{ code: 'legacyGlass' }
			],
			contentTones: [{ code: 'auto' }, { code: 'light' }, { code: 'dark' }],
			advancedNumberControls: [
				{ key: 'baseFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'userFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'speechFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'actionFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'thoughtFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'narrationFontWeight', min: 300, max: 700, step: 100, unit: '' },
				{ key: 'surfaceBorderOpacity', min: 0, max: 60, step: 1, unit: '%' },
				{ key: 'sideBorderWidth', min: 0, max: 6, step: 1, unit: 'rpx' },
				{ key: 'sideBorderOpacity', min: 0, max: 100, step: 1, unit: '%' },
				{ key: 'shadowStrength', min: 0, max: 100, step: 1, unit: '%' },
				{ key: 'blurRadius', min: 0, max: 16, step: 1, unit: 'rpx' }
			],
			colorControls: [
				{ key: 'charBubbleColor', swatches: ['#111318', '#20222a', '#111827', '#2f2634', '#263238'] },
				{ key: 'userBubbleColor', swatches: ['#12333a', '#264148', '#12343b', '#1f4f5f', '#36505c'] },
				{ key: 'charBorderColor', swatches: ['#efb2c8', '#ffc1dc', '#ffffff', '#f8c8dc', '#d7bde2'] },
				{ key: 'userBorderColor', swatches: ['#91ded2', '#c8f5df', '#80e6de', '#9ee4da', '#67e8f9'] },
				{ key: 'baseTextColor', swatches: ['#ffffff', '#f2f4f7', '#f8fafc', '#f5f3ff', '#e5e7eb'] },
				{ key: 'userTextColor', swatches: ['#ffffff', '#f2f4f7', '#ecfeff', '#f8feff', '#e0f2fe'] },
				{ key: 'speechColor', type: 'speech', swatches: ['#ffd8e5', '#f4b8cf', '#ffd1e4', '#fff3f8', '#ffe1ee'] },
				{ key: 'actionColor', type: 'action', swatches: ['#d4f4e9', '#bfe8d2', '#9cebd0', '#d5f6e9', '#7dd3fc'] },
				{ key: 'thoughtColor', type: 'thought', swatches: ['#e8dff8', '#d4caef', '#c6b6ff', '#e1d9ff', '#f0abfc'] },
				{ key: 'narrationColor', type: 'narration', swatches: ['#f7f7f8', '#f2f4f7', '#ffffff', '#dbeafe', '#e5e7eb'] }
			]
		};
	},
	computed: {
		ui() {
			return getAppearanceCopy(getLanguageCode());
		},
		customPresets() {
			return this.presets.filter((item) => item && !item.system).map((item) => {
				const translated = this.ui.presets[item.code];
				return Object.assign({}, item, {
					name: translated ? translated[0] : item.name,
					desc: translated ? translated[1] : item.desc
				});
			});
		},
		bubbleModeName() {
			if (!this.form.bubbleCustomized) return this.ui.followDefault;
			if (this.form.preset === chatAppearance.CUSTOM_PRESET_CODE) return this.ui.customAdjusted;
			const preset = this.customPresets.find((item) => item.code === this.form.preset);
			return (preset ? preset.name : this.ui.customAppearance) + (this.hasTextColorOverrides ? ' · ' + this.ui.textCustomized : '');
		},
		surfaceColorControls() {
			return this.colorControls.filter((item) => !chatAppearance.isTextColorKey(item.key));
		},
		textColorControls() {
			return this.colorControls.filter((item) => chatAppearance.isTextColorKey(item.key));
		},
		hasTextColorOverrides() {
			return chatAppearance.hasTextColorOverrides(this.form);
		},
		currentTypographyProfile() {
			const values = [
				Number(this.form.baseFontWeight),
				Number(this.form.userFontWeight),
				Number(this.form.speechFontWeight),
				Number(this.form.actionFontWeight),
				Number(this.form.thoughtFontWeight),
				Number(this.form.narrationFontWeight)
			];
			if (JSON.stringify(values) === JSON.stringify([400, 500, 500, 400, 400, 400])) return 'light';
			if (JSON.stringify(values) === JSON.stringify([500, 500, 600, 500, 500, 500])) return 'balanced';
			if (JSON.stringify(values) === JSON.stringify([600, 600, 700, 600, 500, 500])) return 'emphasis';
			return 'custom';
		},
		textColorModeText() {
			if (!this.hasTextColorOverrides) return this.ui.followTheme;
			const count = Object.keys(this.form.textColorOverrides || {}).length;
			return this.ui.customCount.replace('{count}', String(count));
		},
		hasUnsavedChanges() {
			return this.currentDraftSignature !== this.savedConfigSignature;
		},
		currentDraftSignature() {
			return appearanceDraftSignature(this.form, this.scopeMode, {
				inheritBubble: this.inheritBubble,
				inheritReading: this.inheritReading,
				inheritReplyFormat: this.inheritReplyFormat
			});
		},
		syncStatusText() {
			if (this.syncState.status === 'conflict') return this.ui.syncConflict;
			if (this.syncState.status === 'pending') {
				return this.syncState.lastError ? this.ui.syncOfflineSaved : this.ui.syncPending;
			}
			if (this.syncState.status === 'offline') return this.ui.syncOffline;
			return '';
		},
		syncActionText() {
			return this.syncState.status === 'conflict' ? this.ui.resolveSync : this.ui.syncRetry;
		},
		currentReadModeName() {
			const item = this.readModes.find((mode) => mode.code === this.form.readMode);
			return item ? this.readModeName(item.code) : this.ui.readModes.original;
		},
		currentSplitModeName() {
			const item = this.splitModes.find((mode) => mode.code === this.form.replySplitMode);
			return item ? this.splitModeName(item.code) : this.ui.defaultName;
		},
		previewChatStyle() {
			const base = 'linear-gradient(135deg, rgba(92,125,137,0.42), rgba(90,55,72,0.25))';
			if (!this.form.bubbleCustomized) return { background: base };
			const config = chatAppearance.normalizeConfig(this.form);
			const alpha = Math.max(0, Math.min(0.55, Number(config.backdropStrength || 0) / 100));
			return Object.assign({}, chatAppearance.buildCssVars(config), {
				background: 'linear-gradient(rgba(0,0,0,' + alpha.toFixed(3) + '),rgba(0,0,0,' + alpha.toFixed(3) + ')),' + base
			});
		},
		previewChatClass() {
			return {
				'preview-chat--app': this.isAppPlus,
				'preview-chat--system': !this.form.bubbleCustomized,
				'preview-chat--custom': this.form.bubbleCustomized,
				'wrap--appearance-custom': this.form.bubbleCustomized,
				'wrap--segment-labels': this.form.showSegmentLabels,
				'wrap--read-novel': this.form.readMode === 'novel',
				'wrap--read-speech-only': this.form.readMode === 'speechOnly',
				'wrap--read-hide-thought': this.form.readMode === 'hideThought',
				'wrap--read-soft-action': this.form.readMode === 'softAction'
			};
		},
		previewSubtitle() {
			return this.currentReadModeName + ' · ' + this.currentSplitModeName;
		},
		previewBubbleGroups() {
			const source = this.ui.previewSource;
			const chunks = chatAppearance.splitReplyBubbleTexts(source, this.form.replySplitMode);
			return chunks.map((chunk, groupIndex) => {
				const segments = chatMarkdown.splitChatSegments(chunk)
					.filter((item) => {
						if (this.form.readMode === 'speechOnly') return item.type === 'speech';
						if (this.form.readMode === 'hideThought') return item.type !== 'thought';
						return true;
					})
					.map((item) => Object.assign({}, item, {
						label: this.ui.segmentTypes[item.type] || chatAppearance.segmentLabel(item.type),
						soft: this.form.readMode === 'softAction' && (item.type === 'action' || item.type === 'narration')
					}));
				return { key: 'preview-' + groupIndex, text: chunk, segments };
			}).filter((group) => group.segments.length > 0);
		},
		previewStatusHtml() {
			const source = '<details class="status-panel" open><summary>' + this.ui.previewStatusTitle + '</summary><p>' + this.ui.previewStatusBody + '</p><progress value="72" max="100">72%</progress></details>';
			const config = chatAppearance.normalizeConfig(this.form);
			return chatMarkdown.renderChatMarkdown(source, {
				readMode: 'original',
				segmentColors: chatAppearance.buildSegmentColors(config),
				segmentWeights: chatAppearance.buildSegmentWeights(config),
				thoughtItalic: config.thoughtItalic
			});
		}
	},
	onLoad(query) {
		this.appearanceDisposed = false;
		this.appearanceRequestGuard = chatAppearance.createRequestGuard();
		this.characterId = Math.max(0, Number(query && query.characterId) || 0);
		this.scopeMode = this.characterId ? 'character' : 'global';
	},
	onShow() {
		this.loadScopeConfig();
	},
	onUnload() {
		this.appearanceDisposed = true;
		this.scopeRequestVersion += 1;
		this.saveRequestVersion += 1;
		if (this.appearanceRequestGuard && typeof this.appearanceRequestGuard.cancel === 'function') {
			this.appearanceRequestGuard.cancel();
		}
		this.appearanceRequestGuard = null;
	},
	methods: {
		loadScopeConfig() {
			const activeId = this.scopeMode === 'character' ? this.characterId : 0;
			const requestVersion = ++this.scopeRequestVersion;
			const ownerSignature = tavernApi.getViewerStateSignature();
			if (!this.appearanceRequestGuard || this.appearanceRequestGuard.active === false) {
				this.appearanceRequestGuard = chatAppearance.createRequestGuard();
			}
			this.globalConfig = chatAppearance.loadConfig();
			this.form = activeId ? chatAppearance.loadConfig(activeId) : this.globalConfig;
			this.detachedDrafts = { bubble: null, reading: null, replyFormat: null };
			const stored = activeId ? (chatAppearance.loadCharacterSections(activeId) || {}) : {};
			this.inheritBubble = !stored.bubble;
			this.inheritReading = !stored.reading;
			this.inheritReplyFormat = !stored.replyFormat;
			this.savedConfigSignature = this.currentDraftSignature;
			this.refreshSyncState(activeId);
			const requestDraftSignature = this.currentDraftSignature;
			chatAppearance.syncFromCloud(activeId || null, { guard: this.appearanceRequestGuard }).then((config) => {
				if (this.appearanceDisposed || requestVersion !== this.scopeRequestVersion) return;
				if (tavernApi.getViewerStateSignature() !== ownerSignature) return;
				if ((this.scopeMode === 'character' ? this.characterId : 0) !== activeId) return;
				if (this.currentDraftSignature !== requestDraftSignature) return;
				this.globalConfig = chatAppearance.loadConfig();
				this.form = activeId ? chatAppearance.loadConfig(activeId) : config;
				const next = activeId ? (chatAppearance.loadCharacterSections(activeId) || {}) : {};
				this.inheritBubble = !next.bubble;
				this.inheritReading = !next.reading;
				this.inheritReplyFormat = !next.replyFormat;
				this.savedConfigSignature = this.currentDraftSignature;
				this.refreshSyncState(activeId);
			});
		},
		switchScope(scope) { this.scopeMode = scope; this.loadScopeConfig(); },
		refreshSyncState(characterId) {
			this.syncState = chatAppearance.getSyncState(characterId || null);
		},
		handleSyncStateAction() {
			const activeId = this.scopeMode === 'character' ? this.characterId : 0;
			if (this.syncState.status === 'conflict') {
				uni.showActionSheet({
					itemList: [this.ui.keepLocal, this.ui.useCloud],
					success: (result) => {
						if (result && result.tapIndex === 0) this.resolveSyncConflict(activeId, 'local');
						if (result && result.tapIndex === 1) this.resolveSyncConflict(activeId, 'cloud');
					}
				});
				return;
			}
			if (!this.syncState.pending) {
				this.loadScopeConfig();
				return;
			}
			this.retryPendingSync(activeId);
		},
		retryPendingSync(activeId) {
			if (this.cloudSaving) return;
			this.scopeRequestVersion += 1;
			const requestVersion = ++this.saveRequestVersion;
			const ownerSignature = tavernApi.getViewerStateSignature();
			const scopeAtStart = this.scopeMode;
			const draftAtStart = this.currentDraftSignature;
			this.cloudSaving = true;
			chatAppearance.retryPending(activeId || null, { guard: this.appearanceRequestGuard }).then((config) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				this.refreshSyncState(activeId);
				if (this.currentDraftSignature === draftAtStart && this.syncState.status !== 'conflict') {
					this.form = config;
					this.savedConfigSignature = this.currentDraftSignature;
				}
				uni.showToast({ title: this.syncState.status === 'pending' ? this.ui.syncOfflineSaved : this.ui.saved, icon: 'none' });
			}).catch((error) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				this.refreshSyncState(activeId);
				uni.showToast({ title: error && error.preferenceConflict ? this.ui.syncConflict : this.ui.saveFailed, icon: 'none' });
			}).finally(() => {
				if (requestVersion === this.saveRequestVersion) this.cloudSaving = false;
			});
		},
		resolveSyncConflict(activeId, strategy) {
			if (this.cloudSaving) return;
			this.scopeRequestVersion += 1;
			const requestVersion = ++this.saveRequestVersion;
			const ownerSignature = tavernApi.getViewerStateSignature();
			const scopeAtStart = this.scopeMode;
			const draftAtStart = this.currentDraftSignature;
			this.cloudSaving = true;
			chatAppearance.resolveSyncConflict(activeId || null, strategy, { guard: this.appearanceRequestGuard }).then((config) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				if (this.currentDraftSignature === draftAtStart) {
					this.form = config;
					const stored = activeId ? (chatAppearance.loadCharacterSections(activeId) || {}) : {};
					this.inheritBubble = !stored.bubble;
					this.inheritReading = !stored.reading;
					this.inheritReplyFormat = !stored.replyFormat;
					this.savedConfigSignature = this.currentDraftSignature;
				}
				this.refreshSyncState(activeId);
				uni.showToast({ title: this.ui.syncResolved, icon: 'none' });
			}).catch((error) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				this.refreshSyncState(activeId);
				uni.showToast({ title: error && error.preferenceConflict ? this.ui.syncConflict : this.ui.saveFailed, icon: 'none' });
			}).finally(() => {
				if (requestVersion === this.saveRequestVersion) this.cloudSaving = false;
			});
		},
		isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart) {
			if (this.appearanceDisposed || requestVersion !== this.saveRequestVersion) return false;
			if (tavernApi.getViewerStateSignature() !== ownerSignature || this.scopeMode !== scopeAtStart) return false;
			return (this.scopeMode === 'character' ? this.characterId : 0) === activeId;
		},
		inheritanceKey(section) {
			if (section === 'bubble') return 'inheritBubble';
			if (section === 'reading') return 'inheritReading';
			return 'inheritReplyFormat';
		},
		sectionPayload(config, section) {
			const sections = chatAppearance.splitSections(config);
			return sections[section] || null;
		},
		applyDraftSection(section, payload) {
			this.form = chatAppearance.mergeSections(this.form, { [section]: payload });
		},
		ensureSectionOverride(section) {
			if (this.scopeMode !== 'character') return;
			const key = this.inheritanceKey(section);
			if (!this[key]) return;
			this[key] = false;
			this.detachedDrafts = Object.assign({}, this.detachedDrafts, {
				[section]: this.sectionPayload(this.form, section)
			});
		},
		toggleInheritance(section) {
			const normalizedSection = section === 'reply' ? 'replyFormat' : section;
			const key = this.inheritanceKey(normalizedSection);
			if (!this[key]) {
				this.detachedDrafts = Object.assign({}, this.detachedDrafts, {
					[normalizedSection]: this.sectionPayload(this.form, normalizedSection)
				});
				this[key] = true;
				this.applyDraftSection(normalizedSection, this.sectionPayload(this.globalConfig, normalizedSection));
				return;
			}
			this[key] = false;
			const restored = this.detachedDrafts[normalizedSection] || this.sectionPayload(this.globalConfig, normalizedSection);
			this.applyDraftSection(normalizedSection, restored);
		},
		controlLabel(key) {
			return this.ui.numberLabels[key] || key;
		},
		colorLabel(key) {
			return this.ui.colorLabels[key] || key;
		},
		previewSceneName(code) {
			return this.ui.previewScenes[code] || code;
		},
		typographyProfileName(code) {
			return this.ui.typographyProfiles[code] || code;
		},
		surfaceModeName(code) {
			return this.ui.surfaceModes[code] || code;
		},
		contentToneName(code) {
			return this.ui.contentTones[code] || code;
		},
		readModeName(code) {
			return this.ui.readModes[code] || code;
		},
		splitModeName(code) {
			return this.ui.splitModes[code] || code;
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/user/set' }) });
		},
		previewBubbleClass(isUser, imageOnly) {
			return {
				'chat-message-bubble--assistant': !isUser,
				'chat-message-bubble--user': isUser,
				'chat-message-bubble--text-only': !imageOnly,
				'chat-message-bubble--has-image': !!imageOnly,
				'chat-message-bubble--image-only': !!imageOnly
			};
		},
		previewBubbleStyle(isUser) {
			return chatAppearance.buildBubbleStyleObject({ role: isUser ? 'user' : 'assistant' }, this.form);
		},
		previewImageBubbleStyle() {
			return chatAppearance.buildImageBubbleStyleObject(this.form);
		},
		previewBubbleHtml(group) {
			const config = chatAppearance.normalizeConfig(this.form);
			return chatMarkdown.renderChatMarkdown(group && group.text ? group.text : '', {
				readMode: this.form.readMode,
				showSegmentLabels: this.form.showSegmentLabels,
				replySplitMode: this.form.replySplitMode,
				segmentColors: chatAppearance.buildSegmentColors(config),
				segmentWeights: chatAppearance.buildSegmentWeights(config),
				thoughtItalic: config.thoughtItalic
			});
		},
		previewAssistantBubbleStyle(index, total) {
			if (this.form.replySplitMode === 'bubble') {
				return chatAppearance.buildSplitBubbleStyleObject(this.form, index, total);
			}
			return this.previewBubbleStyle(false);
		},
		previewSegmentTextStyle(segment) {
			const type = segment && segment.type ? segment.type : 'narration';
			const config = chatAppearance.normalizeConfig(this.form);
			const segmentStyle = chatAppearance.buildSegmentTextStyleObject(type, config);
			const baseTextStyle = chatAppearance.buildMessageTextStyleObject('assistant', config);
			const fontSize = baseTextStyle['font-size'];
			const lineHeight = baseTextStyle['line-height'];
			return {
				display: 'block',
				width: '100%',
				minWidth: '0',
				color: segmentStyle.color,
				WebkitTextFillColor: segmentStyle.color,
				fontSize,
				lineHeight,
				fontWeight: segmentStyle.fontWeight,
				fontStyle: segmentStyle.fontStyle,
				letterSpacing: '0',
				textShadow: 'none',
				wordBreak: 'break-word',
				whiteSpace: 'pre-wrap'
			};
		},
		previewSegmentLabelStyle(segment) {
			const colors = chatAppearance.buildSegmentAccentSurface(segment && segment.type, this.form);
			return {
				borderColor: colors.border,
				background: colors.background
			};
		},
		previewSegmentLabelTextStyle(segment) {
			const colors = chatAppearance.buildSegmentAccentSurface(segment && segment.type, this.form);
			return {
				color: colors.text,
				WebkitTextFillColor: colors.text,
				fontWeight: colors.fontWeight
			};
		},
		previewUserTextStyle() {
			return chatAppearance.buildMessageTextStyleObject('user', this.form);
		},
		previewAssistantTextStyle() {
			return chatAppearance.buildMessageTextStyleObject('assistant', this.form);
		},
		presetVisualStyle(item, isUser) {
			const config = chatAppearance.applyPreset(this.form, item && item.code);
			const vars = chatAppearance.buildCssVars(config);
			const style = {
				background: vars[isUser ? '--chat-bubble-user-bg' : '--chat-bubble-char-bg'],
				borderColor: vars[isUser ? '--chat-bubble-user-surface-border' : '--chat-bubble-char-surface-border'],
				width: Math.max(42, Math.min(88, Number(isUser ? config.userMaxWidth : config.charMaxWidth))) + '%',
				borderRadius: chatAppearance.runtimeRpx(Math.max(4, Math.round(Number(config.radius || 20) / 2)))
			};
			if (isUser) {
				style.borderRightColor = vars['--chat-bubble-user-border'];
				style.borderRightWidth = '4rpx';
			} else {
				style.borderLeftColor = vars['--chat-bubble-char-border'];
				style.borderLeftWidth = '4rpx';
			}
			return style;
		},
		presetLineStyle(item, isUser, type) {
			const config = chatAppearance.applyPreset(this.form, item && item.code);
			const colorKeys = {
				speech: 'speechColor',
				action: 'actionColor',
				thought: 'thoughtColor',
				narration: 'narrationColor'
			};
			return {
				background: isUser ? config.userTextColor : config[colorKeys[type] || 'baseTextColor'],
				opacity: Math.max(0.48, Math.min(0.92, Number(config.opacity || 62) / 100))
			};
		},
		setBubbleMode(enabled) {
			this.ensureSectionOverride('bubble');
			this.form = chatAppearance.setBubbleCustomized(this.form, enabled === true);
		},
		setBubbleValue(key, value) {
			this.ensureSectionOverride('bubble');
			if (chatAppearance.isTextColorKey(key) && this.isValidColor(value)) {
				this.form = chatAppearance.setTextColorOverride(this.form, key, value);
				return;
			}
			const enabled = chatAppearance.setBubbleCustomized(this.form, true);
			this.form = Object.assign({}, enabled, {
				[key]: value,
				preset: chatAppearance.CUSTOM_PRESET_CODE,
				bubbleCustomized: true,
				customized: true
			});
		},
		selectPreset(code) {
			this.ensureSectionOverride('bubble');
			this.form = chatAppearance.applyPreset(this.form, code);
		},
		selectTypographyProfile(code) {
			const profiles = {
				light: [400, 500, 500, 400, 400, 400],
				balanced: [500, 500, 600, 500, 500, 500],
				emphasis: [600, 600, 700, 600, 500, 500]
			};
			if (!profiles[code]) return;
			this.ensureSectionOverride('bubble');
			const values = profiles[code];
			this.form = chatAppearance.normalizeConfig(Object.assign({}, this.form, {
				baseFontWeight: values[0],
				userFontWeight: values[1],
				speechFontWeight: values[2],
				actionFontWeight: values[3],
				thoughtFontWeight: values[4],
				narrationFontWeight: values[5],
				preset: chatAppearance.CUSTOM_PRESET_CODE,
				bubbleCustomized: true,
				customized: true
			}));
		},
		toggleThoughtItalic() {
			this.setBubbleValue('thoughtItalic', !this.form.thoughtItalic);
		},
		setReadingValue(key, value) {
			this.ensureSectionOverride('reading');
			this.form = Object.assign({}, this.form, { [key]: value });
		},
		setReplyValue(value) {
			this.ensureSectionOverride('replyFormat');
			this.form = Object.assign({}, this.form, { replySplitMode: value });
		},
		toggleSegmentLabels() {
			this.ensureSectionOverride('reading');
			this.form = Object.assign({}, this.form, { showSegmentLabels: !this.form.showSegmentLabels });
		},
		onSliderChanging(key, event) {
			const value = event && event.detail ? event.detail.value : this.form[key];
			this.setBubbleValue(key, value);
		},
		onColorInput(key, event) {
			const value = event && event.detail ? event.detail.value : '';
			if (chatAppearance.isTextColorKey(key) && !this.isValidColor(value)) {
				this.form = Object.assign({}, this.form, { [key]: value });
				return;
			}
			this.setBubbleValue(key, value);
		},
		isTextColorOverridden(key) {
			return !!(this.form.textColorOverrides && this.form.textColorOverrides[key]);
		},
		resetTextColors() {
			this.ensureSectionOverride('bubble');
			this.form = chatAppearance.clearTextColorOverrides(this.form);
			uni.showToast({ title: this.ui.textReset, icon: 'none' });
		},
		formatControlValue(item) {
			const value = Number(this.form[item.key]);
			const text = item.step < 1 ? (isFinite(value) ? value : 0).toFixed(2) : String(Math.round(isFinite(value) ? value : 0));
			return text + (item.unit || '');
		},
		normalizeColor(value) {
			return String(value || '').trim().toLowerCase();
		},
		isValidColor(value) {
			return /^#[0-9a-fA-F]{6}$/.test(String(value || '').trim());
		},
		reset() {
			try {
				if (this.scopeMode === 'character') {
					this.inheritBubble = true;
					this.inheritReading = true;
					this.inheritReplyFormat = true;
					this.form = chatAppearance.loadConfig();
				} else {
					this.form = chatAppearance.normalizeConfig(null);
				}
				uni.showToast({ title: this.ui.resetPrepared, icon: 'none' });
			} catch (e) {
				uni.showToast({ title: this.ui.resetFailed, icon: 'none' });
			}
		},
		save() {
			if (this.cloudSaving) return;
			this.cloudSaving = true;
			this.scopeRequestVersion += 1;
			const activeId = this.scopeMode === 'character' ? this.characterId : 0;
			const requestVersion = ++this.saveRequestVersion;
			const ownerSignature = tavernApi.getViewerStateSignature();
			const scopeAtStart = this.scopeMode;
			const submittedSignature = this.currentDraftSignature;
			const submittedConfig = chatAppearance.normalizeConfig(this.form);
			const submittedInheritance = {
				inheritBubble: this.inheritBubble,
				inheritReading: this.inheritReading,
				inheritReplyFormat: this.inheritReplyFormat
			};
			chatAppearance.saveCloudConfig(submittedConfig, {
				characterId: activeId,
				inheritBubble: submittedInheritance.inheritBubble,
				inheritReading: submittedInheritance.inheritReading,
				inheritReplyFormat: submittedInheritance.inheritReplyFormat,
				guard: this.appearanceRequestGuard
			}).then((confirmed) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				this.refreshSyncState(activeId);
				if (this.currentDraftSignature === submittedSignature) {
					this.form = confirmed;
					this.savedConfigSignature = this.currentDraftSignature;
				} else {
					this.savedConfigSignature = submittedSignature;
				}
				uni.$emit('tavern-chat-appearance-changed', confirmed);
				const pending = this.syncState.status === 'pending' || this.syncState.status === 'offline';
				uni.showToast({ title: pending ? this.ui.syncOfflineSaved : this.ui.saved, icon: pending ? 'none' : 'success' });
			}).catch((error) => {
				if (!this.isAppearanceRequestCurrent(requestVersion, ownerSignature, activeId, scopeAtStart)) return;
				this.refreshSyncState(activeId);
				uni.showToast({ title: error && error.preferenceConflict ? this.ui.syncConflict : this.ui.saveFailed, icon: 'none' });
			}).finally(() => {
				if (requestVersion === this.saveRequestVersion) this.cloudSaving = false;
			});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background: $tavern-page-bg;
	display: flex;
	flex-direction: column;
}

.scope-switcher { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8rpx; padding: 8rpx; margin-bottom: 20rpx; background: rgba(255,255,255,.76); border: 1rpx solid rgba(47,125,138,.14); border-radius: 16rpx; }
.scope-option { padding: 18rpx; text-align: center; color: #6b7f8d; font-size: 25rpx; font-weight: 700; border-radius: 12rpx; }
.sync-state { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin: -8rpx 0 20rpx; padding: 16rpx 18rpx; border: 1rpx solid rgba(47,125,138,.22); border-radius: 8rpx; background: rgba(239,248,249,.9); }
.sync-state--conflict { border-color: rgba(190,82,82,.28); background: rgba(255,242,242,.94); }
.sync-state-text { min-width: 0; color: #536b74; font-size: 23rpx; line-height: 1.45; }
.sync-state--conflict .sync-state-text { color: #934747; }
.sync-state-action { flex: 0 0 auto; color: #246b76; font-size: 23rpx; font-weight: 750; }
.scope-option--active { color: #174e5b; background: #fff; box-shadow: 0 6rpx 20rpx rgba(30,85,99,.12); }
.inherit-control { padding: 9rpx 15rpx; border-radius: 999rpx; background: rgba(47,125,138,.1); color: #246b76; font-size: 22rpx; font-weight: 750; }

.body {
	flex: 1;
	height: 0;
	box-sizing: border-box;
	padding: 22rpx 24rpx calc(42rpx + env(safe-area-inset-bottom));
}

/* #ifdef APP-PLUS */
.page {
	height: 100vh;
	overflow: hidden;
}

.body {
	min-height: 0;
}
/* #endif */

.preview-card,
.section {
	position: relative;
	overflow: hidden;
	border-radius: 18rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.52);
	background: rgba(255, 255, 255, 0.62);
	box-shadow: 0 20rpx 48rpx rgba(67, 112, 142, 0.1);
	backdrop-filter: blur(20rpx);
	-webkit-backdrop-filter: blur(20rpx);
}

.preview-card {
	padding: 22rpx;
}

.preview-head,
.section-head,
.control-head,
.color-head,
.switch-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.preview-title,
.section-title {
	display: block;
	color: #244b66;
	font-size: 30rpx;
	font-weight: 800;
}

.preview-subtitle,
.section-desc,
.choice-desc,
.preset-desc,
.mode-desc,
.switch-desc,
.system-note {
	display: block;
	color: #6b7f8d;
	font-size: 23rpx;
	line-height: 1.55;
}

.preview-pill {
	flex-shrink: 0;
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(79, 147, 163, 0.12);
	color: #2d7488;
	font-size: 22rpx;
	font-weight: 700;
}

.preview-chat {
	margin-top: 20rpx;
	padding: 24rpx 18rpx;
	border-radius: 18rpx;
	background:
		linear-gradient(180deg, var(--chat-bg-readable-overlay, rgba(0, 0, 0, 0)), var(--chat-bg-readable-overlay, rgba(0, 0, 0, 0))),
		linear-gradient(135deg, rgba(210, 235, 248, 0.94), rgba(255, 232, 242, 0.9));
}

.preview-message-row {
	display: flex;
	align-items: flex-end;
	margin-bottom: 20rpx;
}

.preview-message-row--user {
	justify-content: flex-end;
}

.preview-bubble-stack {
	display: flex;
	flex: 1;
	min-width: 0;
	flex-direction: column;
	align-items: flex-start;
}

.preview-bubble-stack--split .chat-message-bubble {
	margin-bottom: 14rpx;
}

.preview-bubble-stack--split .chat-message-bubble:last-child {
	margin-bottom: 0;
}

.preview-avatar {
	flex-shrink: 0;
	width: 58rpx;
	height: 58rpx;
	margin-right: 12rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.78);
	color: #426575;
	font-size: 22rpx;
	font-weight: 800;
	line-height: 58rpx;
	text-align: center;
}

.preview-segments {
	font-size: 28rpx;
	line-height: 1.66;
	color: #f2f4f7;
}

.app-preview-segment {
	display: block;
	width: 100%;
	min-width: 0;
	margin-bottom: 10rpx;
	box-sizing: border-box;
}

.app-preview-segment:last-child {
	margin-bottom: 0;
}

.preview-markdown {
	display: block;
	width: 100%;
	min-width: 0;
	color: #f2f4f7;
	font-size: 28rpx;
	line-height: 1.66;
}

.preview-markdown >>> .st-chat-render,
.preview-markdown >>> .st-chat-seg-line-h5,
.preview-markdown >>> .st-chat-seg-body-h5 {
	display: block;
	width: 100%;
	min-width: 0;
	box-sizing: border-box;
}

.preview-markdown >>> .st-chat-seg-line-h5--labeled {
	display: flex !important;
	align-items: flex-start !important;
}

.preview-markdown >>> .st-chat-seg-body-h5 {
	flex: 1;
}

.preview-markdown >>> .st-chat-seg-body-h5 p {
	margin: 0 !important;
	color: inherit !important;
	-webkit-text-fill-color: currentColor !important;
	font: inherit !important;
	line-height: inherit !important;
}

.preview-markdown >>> .st-chat-seg--soft {
	opacity: 0.58;
}

.preview-chat--custom .preview-segments,
.preview-chat--custom .preview-markdown,
.preview-chat--custom .chat-message-user-text {
	font-size: var(--chat-bubble-font-size);
	line-height: var(--chat-bubble-line-height);
	color: var(--chat-bubble-text);
}

.preview-chat--custom .preview-message-row--user .chat-message-user-text {
	color: var(--chat-bubble-user-text);
}

.preview-text,
.chat-message-user-text {
	color: #f2f4f7;
	font-size: 28rpx;
	line-height: 1.66;
	font-weight: 560;
	white-space: pre-wrap;
}

.st-chat-seg {
	margin-bottom: 8rpx;
}

.st-chat-seg--speech .preview-text {
	color: #f4b8cf;
	font-weight: 700;
}

.st-chat-seg--action .preview-text {
	color: #bfe8d2;
}

.st-chat-seg--thought .preview-text {
	color: #d4caef;
}

.st-chat-seg--narration .preview-text {
	color: #f2f4f7;
}

.preview-chat--custom .st-chat-seg--speech .preview-text {
	color: var(--chat-bubble-speech);
}

.preview-chat--custom .st-chat-seg--action .preview-text {
	color: var(--chat-bubble-action);
}

.preview-chat--custom .st-chat-seg--thought .preview-text {
	color: var(--chat-bubble-thought);
}

.preview-chat--custom .st-chat-seg--narration .preview-text {
	color: var(--chat-bubble-narration);
}

.preview-label {
	display: flex;
	flex-shrink: 0;
	align-items: center;
	justify-content: center;
	margin: 3rpx 10rpx 0 0;
	min-width: 58rpx;
	height: 34rpx;
	padding: 0 10rpx;
	border-radius: 999rpx;
	border: 1rpx solid rgba(155, 220, 255, 0.68);
	background: rgba(65, 116, 143, 0.22);
	box-sizing: border-box;
}

.st-chat-seg--speech .preview-label {
	border-color: rgba(244, 184, 207, 0.72);
	background: rgba(196, 72, 120, 0.24);
}

.st-chat-seg--action .preview-label {
	border-color: rgba(156, 235, 208, 0.68);
	background: rgba(42, 132, 105, 0.22);
}

.st-chat-seg--thought .preview-label {
	border-color: rgba(198, 182, 255, 0.72);
	background: rgba(112, 82, 166, 0.24);
}

.preview-label-text {
	display: block;
	color: rgba(255, 255, 255, 0.94) !important;
	font-size: 21rpx !important;
	font-weight: 800 !important;
	line-height: 32rpx !important;
	white-space: nowrap;
}

.preview-seg-line {
	display: block;
	width: 100%;
	min-width: 0;
	box-sizing: border-box;
}

.preview-seg-line--labeled {
	display: flex;
	flex-direction: row;
	align-items: flex-start;
}

.preview-seg-line--labeled .preview-text {
	display: block;
	flex: 1;
	min-width: 0;
}

.preset-visual {
	height: 68rpx;
	margin-bottom: 12rpx;
	padding: 8rpx 10rpx;
	border-radius: 12rpx;
	background: rgba(28, 43, 53, 0.12);
	box-sizing: border-box;
}

.preset-bubble {
	display: flex;
	align-items: center;
	height: 14rpx;
	padding: 0 5rpx;
	border: 1rpx solid transparent;
	box-shadow: 0 3rpx 8rpx rgba(15, 23, 42, 0.12);
	box-sizing: border-box;
}

.preset-bubble--char {
	width: 68%;
	height: 28rpx;
	border-radius: 10rpx 10rpx 10rpx 3rpx;
}

.preset-bubble--user {
	margin: 6rpx 0 0 auto;
}

.preset-line {
	width: 62%;
	height: 3rpx;
	border-radius: 999rpx;
}

.preset-line-stack {
	display: flex;
	flex-direction: column;
	width: 100%;
	gap: 3rpx;
}

.preset-line--narration {
	width: 72%;
}

.preset-line--speech {
	width: 88%;
}

.preset-line--action {
	width: 56%;
}

.preset-line--user {
	width: 52%;
	margin-left: auto;
}

.preview-seg--soft {
	opacity: 0.58;
}

.wrap--read-novel .preview-segments,
.wrap--read-novel .preview-text {
	font-size: 30rpx;
	line-height: 1.88;
}

.section {
	margin-top: 22rpx;
	padding: 24rpx;
}

.choice-grid,
.preset-grid,
.mode-grid,
.swatch-row {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
}

.choice-grid {
	margin-top: 18rpx;
}

.choice,
.preset,
.mode {
	box-sizing: border-box;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(79, 147, 163, 0.16);
}

.choice {
	flex: 1;
	min-width: 250rpx;
	padding: 18rpx;
}

.preset,
.mode {
	width: calc(50% - 6rpx);
	padding: 16rpx;
}

.choice--active,
.preset--active,
.mode--active {
	background: rgba(79, 147, 163, 0.96);
	border-color: rgba(255, 255, 255, 0.52);
	box-shadow: 0 12rpx 28rpx rgba(48, 103, 117, 0.14);
}

.choice-title,
.preset-name,
.mode-title {
	display: block;
	color: #244b66;
	font-size: 26rpx;
	font-weight: 800;
}

.choice--active .choice-title,
.choice--active .choice-desc,
.preset--active .preset-name,
.preset--active .preset-desc,
.mode--active .mode-title,
.mode--active .mode-desc {
	color: #ffffff;
}

.group-title {
	display: block;
	margin: 26rpx 0 14rpx;
	color: #244b66;
	font-size: 27rpx;
	font-weight: 800;
}

.system-note {
	margin-top: 18rpx;
	padding: 18rpx;
	border-radius: 16rpx;
	background: rgba(79, 147, 163, 0.1);
}

.control {
	margin-top: 20rpx;
}

.control-label,
.switch-title {
	color: #244b66;
	font-size: 27rpx;
	font-weight: 700;
}

.control-value {
	color: #4f93a3;
	font-size: 25rpx;
	font-weight: 800;
}

.color-field {
	margin-top: 22rpx;
}

.color-preview,
.swatch {
	width: 42rpx;
	height: 42rpx;
	border-radius: 50%;
	border: 2rpx solid rgba(255, 255, 255, 0.88);
	box-shadow: 0 5rpx 14rpx rgba(15, 23, 42, 0.14);
}

.swatch-row {
	margin-top: 12rpx;
}

.swatch--active {
	outline: 4rpx solid rgba(79, 147, 163, 0.24);
}

.color-input {
	margin-top: 12rpx;
	height: 62rpx;
	padding: 0 18rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.76);
	border: 1rpx solid rgba(79, 147, 163, 0.14);
	color: #244b66;
	font-size: 25rpx;
}

.color-input--invalid {
	border-color: rgba(207, 107, 132, 0.65);
	color: #cf6b84;
}

.switch-row {
	margin-top: 22rpx;
	padding-top: 20rpx;
	border-top: 1rpx solid rgba(88, 189, 210, 0.12);
}

.switch {
	flex-shrink: 0;
	width: 86rpx;
	height: 46rpx;
	padding: 4rpx;
	box-sizing: border-box;
	border-radius: 999rpx;
	background: rgba(148, 163, 184, 0.28);
	transition: all 0.18s ease;
}

.switch--on {
	background: #4f93a3;
}

.switch-dot {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	background: #ffffff;
	box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.18);
	transition: transform 0.18s ease;
}

.switch--on .switch-dot {
	transform: translateX(40rpx);
}

.actions {
	display: flex;
	gap: 16rpx;
	margin-top: 24rpx;
	padding-bottom: 12rpx;
}

.btn {
	flex: 1;
	height: 84rpx;
	border-radius: 24rpx;
	text-align: center;
	line-height: 84rpx;
	font-size: 28rpx;
	font-weight: 800;
}

.btn--ghost {
	background: rgba(255, 255, 255, 0.68);
	color: #4f6673;
	border: 1rpx solid rgba(79, 147, 163, 0.14);
}

.btn--primary {
	background: #4f93a3;
	color: #ffffff;
	box-shadow: 0 12rpx 28rpx rgba(48, 103, 117, 0.18);
}

.body {
	padding: 18rpx 22rpx calc(34rpx + env(safe-area-inset-bottom));
}

.hero-panel {
	position: relative;
	overflow: hidden;
	border-radius: 22rpx;
	padding: 22rpx;
	background:
		linear-gradient(135deg, rgba(19, 32, 43, 0.9), rgba(38, 54, 62, 0.84)),
		linear-gradient(90deg, rgba(244, 184, 207, 0.12), rgba(111, 214, 198, 0.12));
	border: 1rpx solid rgba(255, 255, 255, 0.22);
	box-shadow: 0 22rpx 48rpx rgba(28, 61, 77, 0.18);
}

.hero-head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18rpx;
}

.preview-scene-tabs {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 6rpx;
	margin-top: 18rpx;
	padding: 6rpx;
	border-radius: 12rpx;
	background: rgba(255, 255, 255, 0.08);
}

.preview-scene-tab {
	min-width: 0;
	padding: 11rpx 8rpx;
	border-radius: 9rpx;
	color: rgba(255, 255, 255, 0.66);
	font-size: 22rpx;
	font-weight: 500;
	line-height: 1.2;
	text-align: center;
}

.preview-scene-tab--active {
	background: rgba(255, 255, 255, 0.16);
	color: #ffffff;
}

.hero-kicker {
	display: block;
	color: rgba(181, 231, 223, 0.78);
	font-size: 20rpx;
	font-weight: 900;
	letter-spacing: 0;
}

.hero-title {
	display: block;
	margin-top: 4rpx;
	color: #ffffff;
	font-size: 36rpx;
	font-weight: 900;
	line-height: 1.18;
}

.hero-state {
	display: flex;
	flex-wrap: wrap;
	justify-content: flex-end;
	gap: 8rpx;
	max-width: 390rpx;
}

.hero-state text,
.section-value {
	display: inline-flex;
	align-items: center;
	min-height: 38rpx;
	padding: 0 12rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.16);
	color: rgba(255, 255, 255, 0.9);
	font-size: 21rpx;
	font-weight: 800;
	white-space: nowrap;
}

.preview-chat {
	margin-top: 20rpx;
	padding: 24rpx 16rpx 12rpx;
	border-radius: 18rpx;
	background:
		linear-gradient(180deg, var(--chat-bg-readable-overlay, rgba(0, 0, 0, 0)), var(--chat-bg-readable-overlay, rgba(0, 0, 0, 0))),
		linear-gradient(135deg, rgba(92, 125, 137, 0.42), rgba(90, 55, 72, 0.25));
	border: 1rpx solid rgba(255, 255, 255, 0.12);
}

.preview-media-image {
	display: block;
	width: 360rpx;
	height: 270rpx;
	max-width: 100%;
	border-radius: 14rpx;
	object-fit: cover;
}

.preview-native-status {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
	padding: 14rpx;
	border-radius: 12rpx;
	background: var(--chat-content-surface, rgba(255, 255, 255, 0.055));
	border: 1rpx solid var(--chat-content-border, rgba(255, 255, 255, 0.14));
}

.preview-native-status-title {
	color: var(--chat-content-primary, #f0eef0);
	font-size: 24rpx;
	font-weight: 600;
}

.preview-native-status-body {
	color: var(--chat-content-muted, rgba(224, 216, 221, 0.72));
	font-size: 22rpx;
	font-weight: 400;
	line-height: 1.55;
}

.section {
	margin-top: 18rpx;
	padding: 22rpx;
	border-radius: 22rpx;
	background: rgba(250, 253, 255, 0.82);
	border: 1rpx solid rgba(255, 255, 255, 0.7);
	box-shadow: 0 16rpx 34rpx rgba(67, 112, 142, 0.1);
}

.section-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 18rpx;
}

.section-title {
	color: #1f4557;
	font-size: 31rpx;
	font-weight: 900;
	line-height: 1.2;
}

.section-value {
	background: rgba(31, 69, 87, 0.08);
	color: #2f6d7a;
}

.choice-grid,
.mode-grid,
.preset-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
}

.choice,
.mode,
.preset {
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
	min-height: 74rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.64);
	border: 1rpx solid rgba(31, 69, 87, 0.08);
	box-shadow: none;
}

.choice {
	flex: 1;
	min-width: 250rpx;
}

.mode-grid--read .mode {
	width: calc(33.333% - 7rpx);
}

.mode-grid--split .mode {
	width: calc(50% - 5rpx);
}

.mode-grid--advanced .mode {
	width: calc(25% - 8rpx);
	min-height: 64rpx;
}

.mode-grid--tone .mode {
	width: calc(33.333% - 7rpx);
	min-height: 64rpx;
}

.preset {
	width: calc(33.333% - 7rpx);
}

.preset {
	flex-direction: column;
	align-items: stretch;
	justify-content: flex-start;
	min-height: 154rpx;
	padding: 14rpx;
}

.preset .preset-name {
	margin-top: 2rpx;
}

.preset .preset-desc {
	margin-top: 6rpx;
	font-size: 20rpx;
	line-height: 1.35;
	text-align: center;
}

.preset--active .preset-visual {
	background: rgba(255, 255, 255, 0.14);
}

.choice--active,
.mode--active,
.preset--active {
	background: #1f4557;
	border-color: rgba(255, 255, 255, 0.42);
	box-shadow: 0 14rpx 24rpx rgba(31, 69, 87, 0.18);
}

.choice-title,
.mode-title,
.preset-name {
	color: #244b66;
	font-size: 25rpx;
	font-weight: 900;
	line-height: 1.2;
	text-align: center;
}

.choice--active .choice-title,
.mode--active .mode-title,
.preset--active .preset-name {
	color: #ffffff;
}

.group-title {
	margin: 24rpx 0 12rpx;
	color: #1f4557;
	font-size: 26rpx;
	font-weight: 900;
}

.group-title--compact {
	margin-top: 20rpx;
	margin-bottom: 8rpx;
}

.profile-label {
	display: block;
	margin: 14rpx 0 8rpx;
}

.group-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin: 24rpx 0 12rpx;
}

.group-title--inline {
	display: block;
	margin: 0;
}

.group-subtitle {
	display: block;
	margin-top: 4rpx;
	color: #64808d;
	font-size: 21rpx;
	line-height: 1.4;
}

.icon-btn {
	display: flex;
	flex: 0 0 auto;
	align-items: center;
	justify-content: center;
	width: 58rpx;
	height: 58rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.7);
	border: 1rpx solid rgba(47, 125, 138, 0.16);
	box-shadow: 0 6rpx 14rpx rgba(31, 69, 87, 0.08);
}

.icon-btn--pressed {
	opacity: 0.68;
	transform: scale(0.96);
}

.control-grid,
.color-grid {
	display: flex;
	flex-wrap: wrap;
}

.control-grid {
	gap: 6rpx;
}

.color-grid {
	gap: 14rpx;
}

.control,
.color-field {
	box-sizing: border-box;
	width: 100%;
	margin-top: 0;
	padding: 16rpx 16rpx 10rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.54);
	border: 1rpx solid rgba(31, 69, 87, 0.07);
}

.color-field {
	width: calc(50% - 7rpx);
	padding-bottom: 14rpx;
}

.control {
	display: flex;
	align-items: center;
	width: 100%;
	height: 64rpx;
	min-height: 0;
	padding: 0 10rpx;
	border-radius: 12rpx;
	background: rgba(255, 255, 255, 0.46);
}

.control .control-label {
	flex: 0 0 168rpx;
	min-width: 168rpx;
	font-size: 22rpx;
	line-height: 1.2;
	white-space: nowrap;
}

.control .control-value {
	flex: 0 0 78rpx;
	min-width: 78rpx;
	font-size: 20rpx;
	line-height: 1.2;
	text-align: right;
	white-space: nowrap;
}

.compact-slider {
	flex: 1 1 auto;
	width: auto;
	min-width: 0;
	margin: 0 -4rpx !important;
}

.color-field--overridden {
	border-color: rgba(47, 125, 138, 0.28);
	box-shadow: inset 0 0 0 1rpx rgba(47, 125, 138, 0.08);
}

.text-color-label {
	display: flex;
	align-items: center;
	min-width: 0;
	gap: 9rpx;
}

.type-dot {
	flex: 0 0 auto;
	width: 10rpx;
	height: 10rpx;
	border-radius: 50%;
	box-shadow: 0 0 0 5rpx rgba(31, 69, 87, 0.05);
}

.type-dot--speech {
	background: #c85883;
}

.type-dot--action {
	background: #3b8f78;
}

.type-dot--thought {
	background: #8062ae;
}

.type-dot--narration {
	background: #5e7f91;
}

.control-head,
.color-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.control-label,
.switch-title {
	color: #244b66;
	font-size: 25rpx;
	font-weight: 800;
}

.control-value {
	color: #2f7d8a;
	font-size: 23rpx;
	font-weight: 900;
}

.swatch-row {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.swatch,
.color-preview {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	border: 2rpx solid rgba(255, 255, 255, 0.92);
}

.swatch--active {
	outline: 4rpx solid rgba(31, 69, 87, 0.16);
}

.color-input {
	margin-top: 12rpx;
	height: 56rpx;
	padding: 0 14rpx;
	border-radius: 14rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(31, 69, 87, 0.08);
	color: #244b66;
	font-size: 23rpx;
}

.switch-row {
	margin-top: 16rpx;
	padding: 18rpx 2rpx 0;
	border-top: 1rpx solid rgba(31, 69, 87, 0.08);
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.switch-row--active {
	padding: 18rpx 16rpx;
	border-radius: 18rpx;
	border: 1rpx solid rgba(31, 69, 87, 0.12);
	background: rgba(31, 69, 87, 0.06);
}

.switch {
	width: 88rpx;
	height: 48rpx;
	padding: 5rpx;
	border-radius: 999rpx;
	background: rgba(107, 127, 141, 0.24);
}

.switch--on {
	background: #1f4557;
}

.switch-dot {
	width: 38rpx;
	height: 38rpx;
}

.switch--on .switch-dot {
	transform: translateX(40rpx);
}

.actions {
	display: flex;
	gap: 14rpx;
	margin-top: 18rpx;
	padding-bottom: 8rpx;
}

.btn {
	height: 86rpx;
	border-radius: 22rpx;
	line-height: 86rpx;
	font-size: 28rpx;
	font-weight: 900;
}

.btn--ghost {
	background: rgba(255, 255, 255, 0.68);
	color: #385b68;
	border: 1rpx solid rgba(31, 69, 87, 0.1);
}

.btn--primary {
	background: linear-gradient(135deg, #1f4557, #2f7d8a);
	box-shadow: 0 16rpx 28rpx rgba(31, 69, 87, 0.22);
}

.save-state {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	min-height: 42rpx;
	padding-bottom: 18rpx;
	color: #53717d;
}

.save-state-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #4a9b75;
	box-shadow: 0 0 0 6rpx rgba(74, 155, 117, 0.12);
}

.save-state-text {
	font-size: 22rpx;
	line-height: 1.4;
}

.save-state--dirty {
	color: #a85f45;
}

.save-state--dirty .save-state-dot {
	background: #d47b58;
	box-shadow: 0 0 0 6rpx rgba(212, 123, 88, 0.14);
}

/* Home-aligned visual refresh. All configuration bindings and actions remain unchanged. */
.page {
	--look-ink: #203846;
	--look-muted: #647b8b;
	--look-accent: #4f93a3;
	--look-accent-strong: #3e8dab;
	--look-accent-soft: rgba(213, 239, 247, 0.72);
	--look-pink: #b65f83;
	--look-line: rgba(103, 157, 178, 0.2);
	color: var(--look-ink);
}

.body {
	width: 100%;
	max-width: 920rpx;
	margin: 0 auto;
	padding: 20rpx 24rpx calc(44rpx + env(safe-area-inset-bottom));
}

.scope-switcher {
	gap: 6rpx;
	padding: 6rpx;
	margin-bottom: 18rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.5);
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.28);
	box-shadow: 0 8rpx 20rpx rgba(36, 70, 88, 0.07), inset 0 1rpx 0 rgba(255, 255, 255, 0.7);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.scope-option {
	min-height: 72rpx;
	padding: 0 18rpx;
	border-radius: 18rpx;
	color: var(--look-muted);
	line-height: 72rpx;
}

.scope-option--active {
	color: #31788f;
	background: rgba(255, 255, 255, 0.84);
	box-shadow: 0 10rpx 24rpx rgba(36, 70, 88, 0.12);
}

.sync-state {
	margin: 0 0 18rpx;
	padding: 18rpx 20rpx;
	border-color: rgba(79, 147, 163, 0.2);
	border-radius: 22rpx;
	background: rgba(235, 248, 251, 0.76);
	box-shadow: 0 8rpx 20rpx rgba(36, 70, 88, 0.06);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.sync-state--conflict {
	border-color: rgba(182, 95, 131, 0.24);
	background: rgba(255, 240, 246, 0.82);
}

.sync-state-text {
	color: var(--look-muted);
}

.sync-state-action {
	color: #31788f;
}

.hero-panel,
.section {
	border: 1rpx solid rgba(255, 255, 255, 0.5);
	border-radius: 30rpx;
	background: rgba(255, 255, 255, 0.42);
	box-shadow: 0 16rpx 36rpx rgba(36, 70, 88, 0.12), inset 0 1rpx 0 rgba(255, 255, 255, 0.68);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.hero-panel {
	padding: 24rpx;
}

.hero-head {
	align-items: center;
}

.hero-heading,
.section-heading {
	display: flex;
	min-width: 0;
	align-items: center;
	gap: 14rpx;
}

.hero-icon {
	display: flex;
	flex: 0 0 58rpx;
	width: 58rpx;
	height: 58rpx;
	align-items: center;
	justify-content: center;
	border: 1rpx solid rgba(255, 255, 255, 0.48);
	border-radius: 19rpx;
	background: var(--look-accent);
	box-shadow: 0 9rpx 20rpx rgba(48, 103, 117, 0.18);
}

.hero-title {
	margin-top: 0;
	color: var(--look-ink);
	font-size: 30rpx;
	font-weight: 800;
}

.hero-state {
	max-width: 430rpx;
}

.hero-state text,
.section-value,
.inherit-control {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-height: 40rpx;
	padding: 0 13rpx;
	border: 1rpx solid rgba(79, 147, 163, 0.14);
	border-radius: 999rpx;
	background: var(--look-accent-soft);
	color: #357c91;
	font-size: 20rpx;
	font-weight: 700;
}

.preview-scene-tabs {
	gap: 6rpx;
	margin-top: 18rpx;
	padding: 6rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.48);
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.28);
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.58), 0 6rpx 16rpx rgba(36, 70, 88, 0.05);
}

.preview-scene-tab {
	min-height: 58rpx;
	padding: 0 10rpx;
	border-radius: 15rpx;
	color: #61798a;
	font-weight: 700;
	line-height: 58rpx;
}

.preview-scene-tab--active {
	background: rgba(255, 255, 255, 0.9);
	color: #31788f;
	box-shadow: 0 8rpx 18rpx rgba(36, 70, 88, 0.1);
}

.preview-chat {
	margin-top: 18rpx;
	padding: 24rpx 16rpx 12rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.42);
	border-radius: 24rpx;
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.24), 0 12rpx 28rpx rgba(36, 70, 88, 0.09);
}

.preview-avatar {
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1rpx solid rgba(255, 255, 255, 0.5);
	background: var(--look-accent);
	box-shadow: 0 7rpx 16rpx rgba(48, 103, 117, 0.18);
	line-height: normal;
}

.section {
	margin-top: 18rpx;
	padding: 24rpx;
}

.section-head {
	margin-bottom: 20rpx;
}

.section-icon {
	display: flex;
	flex: 0 0 46rpx;
	width: 46rpx;
	height: 46rpx;
	align-items: center;
	justify-content: center;
	border: 1rpx solid rgba(79, 147, 163, 0.16);
	border-radius: 16rpx;
	background: var(--look-accent-soft);
	box-shadow: 0 6rpx 14rpx rgba(36, 70, 88, 0.06);
}

.section-icon--pink {
	border-color: rgba(182, 95, 131, 0.14);
	background: rgba(255, 230, 238, 0.68);
}

.section-title {
	color: var(--look-ink);
	font-size: 28rpx;
	font-weight: 800;
}

.choice-grid {
	gap: 6rpx;
	padding: 6rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.48);
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.28);
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.6);
}

.choice {
	min-height: 68rpx;
	padding: 0 16rpx;
	border: 0;
	border-radius: 17rpx;
	background: transparent;
}

.choice--active {
	border: 0;
	background: rgba(255, 255, 255, 0.88);
	box-shadow: 0 8rpx 18rpx rgba(36, 70, 88, 0.1);
}

.choice--active .choice-title,
.choice--active .choice-desc {
	color: #31788f;
}

.mode,
.preset {
	border-color: var(--look-line);
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.48);
	box-shadow: 0 6rpx 16rpx rgba(36, 70, 88, 0.045);
}

.mode--active,
.preset--active {
	border-color: rgba(79, 147, 163, 0.4);
	background: var(--look-accent-soft);
	box-shadow: 0 9rpx 20rpx rgba(36, 70, 88, 0.07);
}

.mode--active .mode-title,
.mode--active .mode-desc,
.preset--active .preset-name {
	color: #31788f;
}

.preset--active .preset-desc {
	color: var(--look-muted);
}

.preset-visual,
.preset--active .preset-visual {
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.46);
}

.choice-title,
.mode-title,
.preset-name,
.group-title,
.control-label,
.switch-title {
	color: var(--look-ink);
}

.group-title {
	font-size: 26rpx;
	font-weight: 800;
}

.group-subtitle,
.preset-desc {
	color: var(--look-muted);
}

.control-grid {
	gap: 0;
}

.control {
	height: 74rpx;
	padding: 0 4rpx;
	border: 0;
	border-bottom: 1rpx solid var(--look-line);
	border-radius: 0;
	background: transparent;
}

.control .control-label {
	flex-basis: 168rpx;
	min-width: 168rpx;
	color: var(--look-ink);
}

.control .control-value,
.control-value {
	color: #31788f;
}

.color-grid {
	gap: 12rpx;
}

.color-field {
	width: calc(50% - 6rpx);
	padding: 18rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.5);
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.36);
	box-shadow: 0 7rpx 18rpx rgba(36, 70, 88, 0.05);
}

.color-field--overridden {
	border-color: rgba(79, 147, 163, 0.34);
	box-shadow: inset 0 0 0 1rpx rgba(79, 147, 163, 0.08), 0 7rpx 18rpx rgba(36, 70, 88, 0.05);
}

.color-input {
	height: 60rpx;
	border-color: var(--look-line);
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.62);
	color: var(--look-ink);
}

.swatch--active {
	outline-color: rgba(79, 147, 163, 0.28);
}

.switch-row {
	border-top-color: var(--look-line);
}

.switch-row--active {
	border-color: rgba(79, 147, 163, 0.2);
	border-radius: 20rpx;
	background: rgba(213, 239, 247, 0.48);
}

.switch--on {
	background: var(--look-accent);
}

.actions {
	position: sticky;
	bottom: 0;
	z-index: 6;
	gap: 12rpx;
	margin-top: 18rpx;
	padding: 12rpx 0 calc(4rpx + env(safe-area-inset-bottom));
	background: rgba(236, 247, 250, 0.78);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.btn {
	height: 84rpx;
	border-radius: 999rpx;
	line-height: 84rpx;
	font-size: 26rpx;
	font-weight: 800;
}

.btn--ghost {
	border-color: rgba(79, 147, 163, 0.22);
	background: rgba(255, 255, 255, 0.72);
	color: #456477;
}

.btn--primary {
	background: var(--look-accent);
	color: #ffffff;
	text-shadow: 0 1rpx 2rpx rgba(31, 77, 91, 0.18);
	box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.2);
}

.save-state {
	color: #53717d;
}

.save-state-dot {
	background: var(--look-accent);
	box-shadow: 0 0 0 6rpx rgba(79, 147, 163, 0.12);
}

@media (hover: hover) {
	.hero-panel,
	.section,
	.mode,
	.preset,
	.color-field,
	.btn {
		transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
	}

	.hero-panel:hover,
	.section:hover {
		transform: translateY(-2rpx);
		box-shadow: 0 20rpx 44rpx rgba(36, 70, 88, 0.14), inset 0 1rpx 0 rgba(255, 255, 255, 0.78);
	}

	.mode:hover,
	.preset:hover,
	.color-field:hover,
	.btn:hover {
		transform: translateY(-1rpx);
	}
}

@media (max-width: 420px) {
	.body {
		padding-right: 20rpx;
		padding-left: 20rpx;
	}

	.hero-head {
		align-items: flex-start;
		flex-direction: column;
	}

	.hero-state {
		max-width: none;
		justify-content: flex-start;
	}

	.section-head {
		align-items: flex-start;
		flex-wrap: wrap;
	}

	.mode-grid--advanced .mode,
	.mode-grid--tone .mode,
	.mode-grid--read .mode,
	.preset {
		width: calc(50% - 5rpx);
	}

	.color-field {
		width: 100%;
	}

	.control .control-label {
		flex-basis: 140rpx;
		min-width: 140rpx;
	}
}
</style>
