<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />

		<view class="body">
			<view class="hero-card">
				<view class="hero-top">
					<text class="hero-kicker">{{ copy.kicker }}</text>
					<view v-if="apiKeyMaskText" class="hero-badge">{{ apiKeyMaskText }}</view>
				</view>
				<text class="hero-title">{{ copy.heroTitle }}</text>
				<text class="hero-subtitle">{{ heroSummaryText }}</text>
			</view>

			<view class="sheet">
				<view class="mode-row">
					<view
						class="mode-chip"
						:class="{ 'mode-chip--active': form.mode === 'system' }"
						@tap="selectSystemMode"
					>
						<text class="mode-title">{{ copy.systemMode }}</text>
						<text class="mode-desc">{{ copy.systemModeDesc }}</text>
					</view>
					<view
						class="mode-chip"
						:class="{ 'mode-chip--active': form.mode === 'custom', 'mode-chip--disabled': !viewState.canUse }"
						@tap="enableCustomMode"
					>
						<text class="mode-title">{{ copy.customMode }}</text>
						<text class="mode-desc">{{ copy.customModeDesc }}</text>
					</view>
				</view>

				<view v-if="form.mode === 'system'" class="official-card">
					<text class="official-title">{{ copy.officialTitle }}</text>
					<text class="official-desc">{{ copy.officialDesc }}</text>
				</view>

				<view v-else>
					<view class="field">
						<view class="field-head">
							<text class="field-label">{{ copy.providerSource }}</text>
							<text class="field-meta">{{ copy.openaiCompatibleOnly }}</text>
						</view>
						<picker mode="selector" :range="sourceLabelList" :value="sourcePickerIndex" @change="onSourceChange">
							<view class="picker-shell">
								<text class="picker-value">{{ selectedSourceLabel }}</text>
								<u-icon name="arrow-down" color="#5d7f95" size="24"></u-icon>
							</view>
						</picker>
						<text v-if="providerHelpText" class="field-tip">{{ providerHelpText }}</text>
					</view>

					<view v-if="form.providerSource === 'custom'" class="field">
						<text class="field-label">{{ copy.customUrl }}</text>
						<u-input
							v-model="form.customUrl"
							:border="true"
							:placeholder="copy.customUrlPlaceholder"
						/>
						<text class="field-tip">{{ copy.customUrlTip }}</text>
					</view>

					<view class="field">
						<view class="field-head">
							<text class="field-label">{{ copy.apiKey }}</text>
							<view class="field-actions">
								<text v-if="savedKeyAppliesToCurrentProvider" class="field-meta field-meta--safe">{{ copy.saved }}</text>
								<view
									v-if="savedKeyAppliesToCurrentProvider"
									class="model-load-btn model-load-btn--warn"
									:class="{ 'model-load-btn--active': form.clearStoredKey }"
									@tap="toggleClearStoredKey"
								>
									{{ clearSavedKeyText }}
								</view>
							</view>
						</view>
						<u-input
							v-model="form.apiKey"
							:border="true"
							type="password"
							:placeholder="apiKeyPlaceholderText"
						/>
						<text class="field-tip">{{ compactApiKeyTipText }}</text>
					</view>

					<view class="field">
						<view class="field-head">
							<text class="field-label">{{ copy.modelName }}</text>
							<view class="field-actions">
								<view
									class="model-load-btn model-load-btn--primary"
									:class="{ 'model-load-btn--disabled': !canLoadModels || loadingModels }"
									@tap="loadProviderModels(true)"
								>
									{{ loadingModels ? loadingModelsText : loadModelsText }}
								</view>
								<view
									class="model-load-btn model-load-btn--ghost"
									:class="{ 'model-load-btn--disabled': !canSmartFillModels }"
									@tap="smartFillModels"
								>
									{{ smartFillButtonText }}
								</view>
							</view>
						</view>
						<text v-if="mainModelHelperText" class="field-tip field-tip--safe">{{ mainModelHelperText }}</text>
						<u-input
							v-model="form.modelName"
							:border="true"
							:placeholder="modelPlaceholderText"
						/>
						<view v-if="modelPresets.length" class="preset-box">
							<view class="preset-head">
								<text class="preset-label">{{ modelListLabelText }}</text>
								<text
									v-if="modelPresets.length > presetLimit('model')"
									class="preset-toggle"
									@tap="togglePresetExpand('model')"
								>
									{{ presetToggleText('model', modelPresets, form.modelName) }}
								</text>
							</view>
							<view class="preset-list">
								<view
									v-for="model in visiblePresetItems('model', modelPresets, form.modelName)"
									:key="model"
									class="preset-chip"
									:class="{ 'preset-chip--active': form.modelName === model }"
									@tap="selectModel(model)"
								>
									{{ model }}
								</view>
							</view>
						</view>
						<view v-if="providerCapabilitySummaryItems.length" class="capability-strip">
							<view
								v-for="item in providerCapabilitySummaryItems"
								:key="'capability-' + item.key"
								class="capability-pill"
							>
								<text class="capability-pill__name">{{ item.label }}</text>
								<text class="capability-pill__count">{{ item.count }}</text>
							</view>
						</view>
						<text v-if="modelListMessage" class="field-tip">{{ modelListMessage }}</text>
					</view>

					<view class="field">
						<view class="field-head">
							<text class="field-label">{{ auxModelsTitleText }}</text>
							<text class="field-meta">{{ auxModelsMetaText }}</text>
						</view>
						<text class="field-tip">{{ compactAuxModelsTipText }}</text>
						<view class="aux-grid">
							<view class="aux-card">
								<text class="aux-card__label">{{ visionModelLabelText }}</text>
								<u-input
									v-model="form.visionModelName"
									:border="true"
									:placeholder="visionModelPlaceholderText"
								/>
								<view v-if="visionModelPresets.length" class="preset-box preset-box--tight">
									<text
										v-if="visionModelPresets.length > presetLimit('vision')"
										class="preset-toggle"
										@tap="togglePresetExpand('vision')"
									>
										{{ presetToggleText('vision', visionModelPresets, form.visionModelName) }}
									</text>
									<text class="preset-label">{{ copy.recommendedModels || '推荐模型' }}</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('vision', visionModelPresets, form.visionModelName)"
											:key="'vision-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.visionModelName === model }"
											@tap="selectCapabilityModel('visionModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
							</view>

							<view v-if="showVoiceConfig" class="aux-card">
								<view class="field-head">
									<text class="aux-card__label">{{ sttProviderSectionTitleText }}</text>
									<view class="field-actions">
										<view
											class="model-load-btn"
											:class="{ 'model-load-btn--disabled': !canLoadSttModels || loadingSttModels }"
											@tap="loadSttProviderModels(true)"
										>
											{{ loadingSttModels ? loadingModelsText : loadModelsText }}
										</view>
									</view>
								</view>
								<text class="section-caption">{{ compactSttProviderSectionDescText }}</text>
								<view class="mode-row mode-row--inner">
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': !form.sttUseSeparateConfig }"
										@tap="setSttSeparateConfig(false)"
									>
										<text class="mode-title">{{ sttFollowMainText }}</text>
										<text class="mode-desc">{{ sttFollowMainDescText }}</text>
									</view>
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': form.sttUseSeparateConfig }"
										@tap="setSttSeparateConfig(true)"
									>
										<text class="mode-title">{{ sttSeparateText }}</text>
										<text class="mode-desc">{{ sttSeparateDescText }}</text>
									</view>
								</view>
								<view v-if="form.sttUseSeparateConfig" class="tts-provider-fields">
									<view class="field field--compact">
										<view class="field-head">
											<text class="field-label">{{ sttProviderLabelText }}</text>
											<text class="field-meta">{{ copy.openaiCompatibleOnly }}</text>
										</view>
										<picker mode="selector" :range="sourceLabelList" :value="sttSourcePickerIndex" @change="onSttSourceChange">
											<view class="picker-shell">
												<text class="picker-value">{{ selectedSttSourceLabel }}</text>
												<u-icon name="arrow-down" color="#5d7f95" size="24"></u-icon>
											</view>
										</picker>
										<text v-if="sttProviderHelpText" class="field-tip">{{ sttProviderHelpText }}</text>
									</view>
									<view v-if="form.sttProviderSource === 'custom'" class="field field--compact">
										<text class="field-label">{{ copy.customUrl }}</text>
										<u-input
											v-model="form.sttCustomUrl"
											:border="true"
											:placeholder="copy.customUrlPlaceholder"
										/>
										<text class="field-tip">{{ copy.customUrlTip }}</text>
									</view>
									<view class="field field--compact field--compact-last">
										<view class="field-head">
											<text class="field-label">{{ sttApiKeyLabelText }}</text>
											<view class="field-actions">
												<text v-if="savedSttKeyAppliesToCurrentProvider" class="field-meta field-meta--safe">{{ copy.saved }}</text>
												<view
													v-if="savedSttKeyAppliesToCurrentProvider"
													class="model-load-btn model-load-btn--warn"
													:class="{ 'model-load-btn--active': form.clearStoredSttKey }"
													@tap="toggleClearStoredSttKey"
												>
													{{ clearSavedSttKeyText }}
												</view>
											</view>
										</view>
										<u-input
											v-model="form.sttApiKey"
											:border="true"
											type="password"
											:placeholder="sttApiKeyPlaceholderText"
										/>
										<text class="field-tip">{{ compactSttApiKeyTipText }}</text>
									</view>
								</view>
								<text class="aux-card__label">{{ sttModelLabelText }}</text>
								<u-input
									v-model="form.sttModelName"
									:border="true"
									:placeholder="sttModelPlaceholderText"
								/>
								<text v-if="sttModelListMessage" class="field-tip">{{ sttModelListMessage }}</text>
								<view v-if="sttModelPresets.length" class="preset-box preset-box--tight">
									<text
										v-if="sttModelPresets.length > presetLimit('stt')"
										class="preset-toggle"
										@tap="togglePresetExpand('stt')"
									>
										{{ presetToggleText('stt', sttModelPresets, form.sttModelName) }}
									</text>
									<text class="preset-label">{{ copy.recommendedModels || '推荐模型' }}</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('stt', sttModelPresets, form.sttModelName)"
											:key="'stt-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.sttModelName === model }"
											@tap="selectCapabilityModel('sttModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
								<text v-if="sttModelHintText" class="field-tip field-tip--warning">{{ sttModelHintText }}</text>
							</view>

							<view v-if="showVoiceConfig" class="aux-card aux-card--tts-config">
								<view class="field-head">
									<text class="aux-card__label">{{ ttsProviderSectionTitleText }}</text>
									<view class="field-actions">
										<view
											class="model-load-btn"
											:class="{ 'model-load-btn--disabled': !canLoadTtsModels || loadingTtsModels }"
											@tap="loadTtsProviderModels(true)"
										>
											{{ loadingTtsModels ? loadingModelsText : loadModelsText }}
										</view>
									</view>
								</view>
								<text class="section-caption">{{ compactTtsProviderSectionDescText }}</text>
								<view class="mode-row mode-row--inner">
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': !form.ttsUseSeparateConfig }"
										@tap="setTtsSeparateConfig(false)"
									>
										<text class="mode-title">{{ ttsFollowMainText }}</text>
										<text class="mode-desc">{{ ttsFollowMainDescText }}</text>
									</view>
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': form.ttsUseSeparateConfig }"
										@tap="setTtsSeparateConfig(true)"
									>
										<text class="mode-title">{{ ttsSeparateText }}</text>
										<text class="mode-desc">{{ ttsSeparateDescText }}</text>
									</view>
								</view>
								<view v-if="form.ttsUseSeparateConfig" class="tts-provider-fields">
									<view class="field field--compact">
										<view class="field-head">
											<text class="field-label">{{ ttsProviderLabelText }}</text>
											<text class="field-meta">{{ copy.openaiCompatibleOnly }}</text>
										</view>
										<picker mode="selector" :range="sourceLabelList" :value="ttsSourcePickerIndex" @change="onTtsSourceChange">
											<view class="picker-shell">
												<text class="picker-value">{{ selectedTtsSourceLabel }}</text>
												<u-icon name="arrow-down" color="#5d7f95" size="24"></u-icon>
											</view>
										</picker>
										<text v-if="ttsProviderHelpText" class="field-tip">{{ ttsProviderHelpText }}</text>
									</view>
									<view v-if="form.ttsProviderSource === 'custom'" class="field field--compact">
										<text class="field-label">{{ copy.customUrl }}</text>
										<u-input
											v-model="form.ttsCustomUrl"
											:border="true"
											:placeholder="copy.customUrlPlaceholder"
										/>
										<text class="field-tip">{{ copy.customUrlTip }}</text>
									</view>
									<view class="field field--compact field--compact-last">
										<view class="field-head">
											<text class="field-label">{{ ttsApiKeyLabelText }}</text>
											<view class="field-actions">
												<text v-if="savedTtsKeyAppliesToCurrentProvider" class="field-meta field-meta--safe">{{ copy.saved }}</text>
												<view
													v-if="savedTtsKeyAppliesToCurrentProvider"
													class="model-load-btn model-load-btn--warn"
													:class="{ 'model-load-btn--active': form.clearStoredTtsKey }"
													@tap="toggleClearStoredTtsKey"
												>
													{{ clearSavedTtsKeyText }}
												</view>
											</view>
										</view>
										<u-input
											v-model="form.ttsApiKey"
											:border="true"
											type="password"
											:placeholder="ttsApiKeyPlaceholderText"
										/>
										<text class="field-tip">{{ compactTtsApiKeyTipText }}</text>
									</view>
								</view>
							</view>

							<view v-if="showVoiceConfig" class="aux-card">
								<text class="aux-card__label">{{ ttsModelLabelText }}</text>
								<u-input
									v-model="form.ttsModelName"
									:border="true"
									:placeholder="effectiveTtsModelPlaceholderText"
								/>
								<text v-if="ttsModelListMessage" class="field-tip">{{ ttsModelListMessage }}</text>
								<view v-if="ttsModelPresets.length" class="preset-box preset-box--tight">
									<text class="preset-label">{{ copy.recommendedModels || '推荐模型' }}</text>
									<text
										v-if="ttsModelPresets.length > presetLimit('tts')"
										class="preset-toggle"
										@tap="togglePresetExpand('tts')"
									>
										{{ presetToggleText('tts', ttsModelPresets, form.ttsModelName) }}
									</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('tts', ttsModelPresets, form.ttsModelName)"
											:key="'tts-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.ttsModelName === model }"
											@tap="selectCapabilityModel('ttsModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
								<view v-if="ttsRawModelDisplayPresets.length" class="preset-box preset-box--tight">
									<text class="preset-label">{{ copy.providerReturnedModels || copy.providerModels || '返回模型' }}</text>
									<text
										v-if="ttsRawModelDisplayPresets.length > presetLimit('ttsRaw')"
										class="preset-toggle"
										@tap="togglePresetExpand('ttsRaw')"
									>
										{{ presetToggleText('ttsRaw', ttsRawModelDisplayPresets, form.ttsModelName) }}
									</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('ttsRaw', ttsRawModelDisplayPresets, form.ttsModelName)"
											:key="'tts-raw-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.ttsModelName === model }"
											@tap="selectCapabilityModel('ttsModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
								<text v-if="effectiveTtsModelHintText" class="field-tip field-tip--warning">{{ effectiveTtsModelHintText }}</text>
							</view>

							<view v-if="showVoiceConfig" class="aux-card aux-card--voice-library">
								<text class="aux-card__label">{{ ttsVoiceLabelText }}</text>
								<text class="section-caption">{{ ttsVoiceTemplateIntroText }}</text>
								<scroll-view
									v-if="ttsVoiceTemplates.length"
									class="voice-template-scroll"
									scroll-y
									:show-scrollbar="false"
								>
									<view class="voice-template-list">
										<view
											v-for="item in ttsVoiceTemplates"
											:key="'voice-template-' + item.code"
											class="voice-template-card"
											:class="{ 'voice-template-card--active': form.ttsVoiceTemplateCode === item.code }"
											@tap="selectTtsVoiceTemplate(item)"
										>
											<image
												v-if="ttsVoiceTemplateAssetUrl(item.coverImageUrl)"
												class="voice-template-card__cover"
												:src="ttsVoiceTemplateAssetUrl(item.coverImageUrl)"
												mode="aspectFill"
											/>
											<view v-else class="voice-template-card__cover voice-template-card__cover--placeholder">音</view>
											<view class="voice-template-card__body">
												<view class="voice-template-card__head">
													<text class="voice-template-card__title">{{ item.displayName || item.code }}</text>
													<text
														class="voice-template-card__badge"
														:class="'voice-template-card__badge--' + (item.statusCode || 'pending')"
													>{{ item.ready ? '\u5df2\u5c31\u7eea' : (item.statusText || '\u9996\u6b21\u4f7f\u7528\u81ea\u52a8\u751f\u6210') }}</text>
												</view>
												<text v-if="item.recommendedModelName" class="voice-template-card__meta">{{ item.recommendedModelName }}</text>
												<text v-if="item.description" class="voice-template-card__desc">{{ item.description }}</text>
											</view>
											<text v-if="form.ttsVoiceTemplateCode === item.code" class="voice-template-card__check">已选</text>
										</view>
									</view>
								</scroll-view>
								<view v-else class="voice-template-empty">
								<text class="voice-template-empty__title">{{ '\u540e\u53f0\u8fd8\u6ca1\u6709\u914d\u7f6e\u6a21\u677f\u97f3\u8272' }}</text>
								<text class="voice-template-empty__desc">{{ '\u53ef\u4ee5\u5148\u7ee7\u7eed\u4f7f\u7528\u624b\u586b\u97f3\u8272 ID\uff0c\u7b49\u540e\u53f0\u8865\u4e0a\u89d2\u8272\u6a21\u677f\u540e\u518d\u5207\u6362\u3002' }}</text>
								</view>
								<view v-if="form.ttsVoiceTemplateCode" class="voice-template-active-bar">
									<view class="voice-template-active-copy">
										<text class="voice-template-active-title">{{ selectedTtsVoiceTemplateTitleText }}</text>
										<text class="voice-template-active-desc">{{ selectedTtsVoiceTemplateStatusText }}</text>
									</view>
									<text class="voice-template-active-switch" @tap.stop="clearTtsVoiceTemplateSelection">{{ '\u6539\u4e3a\u624b\u586b ID' }}</text>
								</view>
								<view v-else class="voice-manual-box">
									<text class="voice-manual-label">手动填写音色</text>
									<u-input
										v-model="form.ttsVoiceName"
										:border="true"
										:placeholder="resolvedTtsVoicePlaceholderText"
									/>
									<view v-if="ttsVoicePresets.length" class="preset-box preset-box--tight">
										<text class="preset-label">{{ copy.recommendedModels || '推荐模型' }}</text>
										<text
											v-if="ttsVoicePresets.length > presetLimit('ttsVoice')"
											class="preset-toggle"
											@tap="togglePresetExpand('ttsVoice')"
										>
											{{ presetToggleText('ttsVoice', ttsVoicePresets, form.ttsVoiceName) }}
										</text>
										<scroll-view class="voice-preset-scroll" scroll-y :show-scrollbar="false">
											<view class="preset-list">
											<view
												v-for="voice in visiblePresetItems('ttsVoice', ttsVoicePresets, form.ttsVoiceName)"
												:key="'voice-' + voice"
												class="preset-chip"
												:class="{ 'preset-chip--active': form.ttsVoiceName === voice }"
												@tap="selectCapabilityModel('ttsVoiceName', voice)"
											>
												{{ voice }}
											</view>
											</view>
										</scroll-view>
									</view>
									<text v-if="ttsVoiceHintText" class="field-tip field-tip--warning">{{ ttsVoiceHintText }}</text>
								</view>
							</view>

							<view v-if="showImageConfig" class="aux-card aux-card--tts-config">
								<view class="field-head">
									<text class="aux-card__label">{{ imageProviderSectionTitleText }}</text>
									<view class="field-actions">
										<view
											class="model-load-btn"
											:class="{ 'model-load-btn--disabled': !canLoadImageModels || loadingImageModels }"
											@tap="loadImageProviderModels(true)"
										>
											{{ loadingImageModels ? loadingModelsText : loadModelsText }}
										</view>
									</view>
								</view>
								<text class="section-caption">{{ compactImageProviderSectionDescText }}</text>
								<view class="mode-row mode-row--inner">
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': !form.imageUseSeparateConfig }"
										@tap="setImageSeparateConfig(false)"
									>
										<text class="mode-title">{{ imageFollowMainText }}</text>
										<text class="mode-desc">{{ imageFollowMainDescText }}</text>
									</view>
									<view
										class="mode-chip mode-chip--mini"
										:class="{ 'mode-chip--active': form.imageUseSeparateConfig }"
										@tap="setImageSeparateConfig(true)"
									>
										<text class="mode-title">{{ imageSeparateText }}</text>
										<text class="mode-desc">{{ imageSeparateDescText }}</text>
									</view>
								</view>
								<view v-if="form.imageUseSeparateConfig" class="tts-provider-fields">
									<view class="field field--compact">
										<view class="field-head">
											<text class="field-label">{{ imageProviderLabelText }}</text>
											<text class="field-meta">{{ copy.openaiCompatibleOnly }}</text>
										</view>
										<picker mode="selector" :range="sourceLabelList" :value="imageSourcePickerIndex" @change="onImageSourceChange">
											<view class="picker-shell">
												<text class="picker-value">{{ selectedImageSourceLabel }}</text>
												<u-icon name="arrow-down" color="#5d7f95" size="24"></u-icon>
											</view>
										</picker>
										<text v-if="imageProviderHelpText" class="field-tip">{{ imageProviderHelpText }}</text>
									</view>
									<view v-if="form.imageProviderSource === 'custom'" class="field field--compact">
										<text class="field-label">{{ copy.customUrl }}</text>
										<u-input
											v-model="form.imageCustomUrl"
											:border="true"
											:placeholder="copy.customUrlPlaceholder"
										/>
										<text class="field-tip">{{ copy.customUrlTip }}</text>
									</view>
									<view class="field field--compact field--compact-last">
										<view class="field-head">
											<text class="field-label">{{ imageApiKeyLabelText }}</text>
											<view class="field-actions">
												<text v-if="savedImageKeyAppliesToCurrentProvider" class="field-meta field-meta--safe">{{ copy.saved }}</text>
												<view
													v-if="savedImageKeyAppliesToCurrentProvider"
													class="model-load-btn model-load-btn--warn"
													:class="{ 'model-load-btn--active': form.clearStoredImageKey }"
													@tap="toggleClearStoredImageKey"
												>
													{{ clearSavedImageKeyText }}
												</view>
											</view>
										</view>
										<u-input
											v-model="form.imageApiKey"
											:border="true"
											type="password"
											:placeholder="imageApiKeyPlaceholderText"
										/>
										<text class="field-tip">{{ compactImageApiKeyTipText }}</text>
									</view>
								</view>
							</view>

							<view v-if="showImageConfig" class="aux-card">
								<text class="aux-card__label">{{ imageModelLabelText }}</text>
								<u-input
									v-model="form.imageModelName"
									:border="true"
									:placeholder="imageModelPlaceholderText"
								/>
								<text v-if="imageModelListMessage" class="field-tip">{{ imageModelListMessage }}</text>
								<view v-if="imageModelPresets.length" class="preset-box preset-box--tight">
									<text class="preset-label">{{ copy.imageDetectedModels || '已识别的生图模型' }}</text>
									<text
										v-if="imageModelPresets.length > presetLimit('image')"
										class="preset-toggle"
										@tap="togglePresetExpand('image')"
									>
										{{ presetToggleText('image', imageModelPresets, form.imageModelName) }}
									</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('image', imageModelPresets, form.imageModelName)"
											:key="'image-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.imageModelName === model }"
											@tap="selectCapabilityModel('imageModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
								<view v-if="imageRawModelDisplayPresets.length" class="preset-box preset-box--tight">
									<text class="preset-label">{{ copy.providerReturnedModels || copy.providerModels || '返回模型' }}</text>
									<text
										v-if="imageRawModelDisplayPresets.length > presetLimit('imageRaw')"
										class="preset-toggle"
										@tap="togglePresetExpand('imageRaw')"
									>
										{{ presetToggleText('imageRaw', imageRawModelDisplayPresets, form.imageModelName) }}
									</text>
									<view class="preset-list">
										<view
											v-for="model in visiblePresetItems('imageRaw', imageRawModelDisplayPresets, form.imageModelName)"
											:key="'image-raw-' + model"
											class="preset-chip"
											:class="{ 'preset-chip--active': form.imageModelName === model }"
											@tap="selectCapabilityModel('imageModelName', model)"
										>
											{{ model }}
										</view>
									</view>
								</view>
							</view>
							<view v-if="false && showImageConfig" class="aux-card aux-card--image-consistency">
								<view class="strategy-card-head">
									<view class="strategy-title-wrap">
										<text class="aux-card__label">{{ imageConsistencySectionTitleText }}</text>
										<text class="section-caption">{{ imageConsistencySectionDescText }}</text>
									</view>
									<text class="strategy-badge">{{ imageConsistencyCurrentText }}</text>
								</view>
								<view class="strategy-switch">
									<view
										class="strategy-switch__item"
										:class="{ 'strategy-switch__item--active': form.imageCharacterConsistencyMode === 'free' }"
										@tap="setImageCharacterConsistencyMode('free')"
									>
										<text>{{ imageConsistencyFreeText }}</text>
									</view>
									<view
										class="strategy-switch__item"
										:class="{ 'strategy-switch__item--active': form.imageCharacterConsistencyMode === 'balanced' }"
										@tap="setImageCharacterConsistencyMode('balanced')"
									>
										<text>{{ imageConsistencyBalancedText }}</text>
									</view>
									<view
										class="strategy-switch__item"
										:class="{ 'strategy-switch__item--active': form.imageCharacterConsistencyMode === 'strong' }"
										@tap="setImageCharacterConsistencyMode('strong')"
									>
										<text>{{ imageConsistencyStrongText }}</text>
									</view>
								</view>
								<text class="strategy-current-desc">{{ imageConsistencyActiveDescText }}</text>
								<view class="strategy-ref-box">
									<view class="strategy-sub-head">
										<text class="field-label">{{ imageReferenceSourceLabelText }}</text>
										<text class="strategy-badge strategy-badge--soft">{{ imageReferenceSourceCurrentText }}</text>
									</view>
									<view class="strategy-switch strategy-switch--two">
										<view
											class="strategy-switch__item"
											:class="{ 'strategy-switch__item--active': form.imageReferenceSourceMode === 'latest_generated_first' }"
											@tap="setImageReferenceSourceMode('latest_generated_first')"
										>
											<text>{{ imageReferenceLatestText }}</text>
										</view>
										<view
											class="strategy-switch__item"
											:class="{ 'strategy-switch__item--active': form.imageReferenceSourceMode === 'avatar_only' }"
											@tap="setImageReferenceSourceMode('avatar_only')"
										>
											<text>{{ imageReferenceAvatarText }}</text>
										</view>
									</view>
									<text class="strategy-current-desc">{{ imageReferenceSourceActiveDescText }}</text>
									<text class="field-tip strategy-tip">{{ imageReferenceSourceTipText }}</text>
								</view>
							</view>
						</view>
					</view>

				</view>
			</view>

			<view v-if="form.mode === 'custom' && testState.message" class="status-card" :class="testState.ok ? 'status-card--success' : 'status-card--error'">
				<view class="status-head">
					<text class="status-title">{{ testStatusHeadingText }}</text>
					<text v-if="testStatusLatencyText" class="status-pill">{{ testStatusLatencyText }}</text>
				</view>
				<view v-if="testStatusItems.length" class="status-list">
					<view
						v-for="(item, index) in testStatusItems"
						:key="(item.label || 'status') + '-' + index"
						class="status-line"
					>
						<text v-if="item.label" class="status-line__label">{{ item.label }}</text>
						<text class="status-line__body">{{ item.message }}</text>
					</view>
				</view>
				<text v-else class="status-desc">{{ testState.message }}</text>
			</view>

			<view v-if="form.mode === 'custom' && denyReasonText" class="status-card status-card--warning">
				<text class="status-title">{{ copy.unavailableTitle }}</text>
				<text class="status-desc">{{ denyReasonText }}</text>
			</view>

			<view class="action-row" :class="{ 'action-row--single': form.mode !== 'custom' }">
				<view
					v-if="form.mode === 'custom'"
					class="action-button action-button--secondary"
					:class="{ 'action-button--disabled': !canRunCustomAction || testing }"
					@tap="testConnection"
				>
					{{ testing ? copy.testing : copy.testConnection }}
				</view>
				<view
					class="action-button action-button--primary"
					:class="{ 'action-button--disabled': saving }"
					@tap="save"
				>
					{{ saving ? copy.saving : copy.save }}
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const CLEAN_COPY_ZH = Object.freeze({
	title: 'AI \u8bbe\u7f6e',
	kicker: 'AI',
	heroTitle: '\u804a\u5929\u4e0e\u6a21\u578b\u914d\u7f6e',
	heroSubtitle: '\u9009\u62e9\u5e73\u53f0\u3001\u6a21\u578b\u4ee5\u53ca TTS/\u751f\u56fe\u72ec\u7acb\u914d\u7f6e\u3002',
	systemMode: '\u7cfb\u7edf\u9ed8\u8ba4',
	systemModeDesc: '\u8ddf\u968f\u5e94\u7528\u9ed8\u8ba4\u7684 AI \u914d\u7f6e\u3002',
	customMode: '\u81ea\u5b9a\u4e49',
	customModeDesc: '\u624b\u52a8\u9009\u62e9\u5e73\u53f0\u3001\u6a21\u578b\u548c\u4e13\u7528\u914d\u7f6e\u3002',
	officialTitle: '\u7531\u7cfb\u7edf\u7edf\u4e00\u7ba1\u7406',
	officialDesc: '\u5f53\u524d\u89d2\u8272\u4f1a\u76f4\u63a5\u4f7f\u7528\u5168\u5c40\u9ed8\u8ba4\u7684 AI \u8bbe\u7f6e\u3002',
	providerSource: '\u5e73\u53f0',
	openaiCompatibleOnly: 'OpenAI \u517c\u5bb9',
	modelName: '\u4e3b\u6a21\u578b',
	modelPlaceholder: '\u8f93\u5165\u6a21\u578b ID\uff0c\u4f8b\u5982 deepseek-ai/DeepSeek-V3',
	loadModels: '\u83b7\u53d6\u6a21\u578b',
	loadingModels: '\u52a0\u8f7d\u4e2d...',
	smartFill: '\u667a\u80fd\u586b\u5145',
	recommendedModels: '\u63a8\u8350\u6a21\u578b',
	providerModels: '\u5e73\u53f0\u8fd4\u56de\u5217\u8868',
	providerReturnedModels: '\u8fd4\u56de\u6a21\u578b',
	auxModelsTitle: '\u6269\u5c55\u6a21\u578b',
	auxModelsTip: '\u53ef\u4ee5\u4e3a\u89c6\u89c9\u3001\u8bc6\u97f3\u3001TTS \u548c\u751f\u56fe\u5355\u72ec\u6307\u5b9a\u6a21\u578b\u3002',
	visionModelLabel: '\u89c6\u89c9\u6a21\u578b',
	visionModelPlaceholder: '输入视觉模型 ID，例如 Qwen2.5-VL 或 Gemini Vision',
	sttModelLabel: '\u8bc6\u97f3\u6a21\u578b',
	sttModelPlaceholder: '输入识音模型 ID，例如 Whisper 或 SenseVoice',
	sttModelHintEmpty: '\u672a\u8bc6\u522b\u5230\u8bc6\u97f3\u6a21\u578b\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u3002',
	ttsProviderSectionTitle: 'TTS \u5e73\u53f0',
	ttsProviderSectionDescShared: 'TTS \u76ee\u524d\u8ddf\u968f\u4e3b\u804a\u5929\u914d\u7f6e\u3002',
	ttsProviderSectionDescSeparate: 'TTS \u53ef\u4ee5\u4f7f\u7528\u72ec\u7acb\u7684\u5e73\u53f0\u3001API Key \u548c\u63a5\u53e3\u5730\u5740\u3002',
	ttsProviderSource: 'TTS \u5e73\u53f0',
	ttsFollowMain: '\u8ddf\u968f\u4e3b\u914d\u7f6e',
	ttsFollowMainDesc: '\u590d\u7528\u4e3b\u804a\u5929\u7684\u5e73\u53f0\u548c Key\u3002',
	ttsSeparate: '\u72ec\u7acb',
	ttsSeparateDesc: '\u4f7f\u7528\u4e13\u7528 TTS \u5e73\u53f0\u548c Key\u3002',
	ttsApiKey: 'TTS API Key',
	ttsModelLabel: 'TTS \u6a21\u578b',
	ttsModelPlaceholder: '输入 TTS 模型 ID，例如 gpt-4o-mini-tts 或 fish-audio-speech',
	ttsModelHintEmpty: '\u672a\u8bc6\u522b\u5230 TTS \u6a21\u578b\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u3002',
	ttsVoiceLabel: 'TTS \u97f3\u8272',
	ttsVoicePlaceholder: '\u8f93\u5165\u97f3\u8272 ID',
	ttsVoicePlaceholderGeneric: '\u8f93\u5165\u97f3\u8272 ID',
	ttsVoicePlaceholderSiliconFlow: '\u8f93\u5165 SiliconFlow \u97f3\u8272 ID\uff0c\u4f8b\u5982 anna',
	ttsVoiceHintGeneric: '\u8bf7\u5148\u786e\u8ba4 TTS \u6a21\u578b\uff0c\u518d\u586b\u5199\u97f3\u8272 ID\u3002',
	ttsVoiceHintOpenAiOnly: 'OpenAI \u97f3\u8272\u4e00\u822c\u4f7f\u7528 alloy\u3001nova\u3001shimmer\u3001echo\u3001fable \u6216 onyx\u3002',
	ttsVoiceHintSiliconFlow: 'SiliconFlow \u97f3\u8272 ID \u901a\u5e38\u4f7f\u7528 alex\u3001anna\u3001bella \u8fd9\u7c7b\u540d\u79f0\u3002',
	ttsVoiceHintSiliconFlowWrong: '\u5f53\u524d\u97f3\u8272 ID \u770b\u8d77\u6765\u4e0d\u50cf SiliconFlow \u9884\u8bbe\u503c\uff0c\u8bf7\u518d\u68c0\u67e5\u3002',
	imageProviderSectionTitle: '\u751f\u56fe\u5e73\u53f0',
	imageProviderSectionDescShared: '\u751f\u56fe\u76ee\u524d\u8ddf\u968f\u4e3b\u804a\u5929\u914d\u7f6e\u3002',
	imageProviderSectionDescSeparate: '\u751f\u56fe\u53ef\u4ee5\u4f7f\u7528\u72ec\u7acb\u7684\u5e73\u53f0\u3001API Key \u548c\u63a5\u53e3\u5730\u5740\u3002',
	imageProviderSource: '\u751f\u56fe\u5e73\u53f0',
	imageFollowMain: '\u8ddf\u968f\u4e3b\u914d\u7f6e',
	imageFollowMainDesc: '\u590d\u7528\u4e3b\u804a\u5929\u7684\u5e73\u53f0\u548c Key\u3002',
	imageSeparate: '\u72ec\u7acb',
	imageSeparateDesc: '\u4f7f\u7528\u4e13\u7528\u751f\u56fe\u5e73\u53f0\u548c Key\u3002',
	imageApiKey: '\u751f\u56fe API Key',
	imageModelLabel: '\u751f\u56fe\u6a21\u578b',
	imageModelPlaceholder: '输入生图模型 ID，例如 FLUX、SDXL 或 image-edit 模型',
	imageModelHintEmpty: '\u672a\u8bc6\u522b\u5230\u751f\u56fe\u6a21\u578b\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u3002',
	imageDetectedModels: '\u5df2\u8bc6\u522b\u7684\u751f\u56fe\u6a21\u578b',
	apiKey: 'API Key',
	apiKeyHintPrefix: '\u5f53\u524d\u5e73\u53f0',
	apiKeyTip: '\u4ec5\u5728\u4fdd\u5b58\u65f6\u5199\u5165\u3002\u7559\u7a7a\u53ef\u4ee5\u7ee7\u7eed\u4f7f\u7528\u5df2\u4fdd\u5b58\u7684 Key\u3002',
	customUrl: '\u81ea\u5b9a\u4e49\u63a5\u53e3',
	customUrlPlaceholder: 'https://your-api.example.com/v1',
	customUrlTip: '\u4ec5\u5728\u81ea\u5b9a\u4e49\u5e73\u53f0\u65f6\u9700\u8981\u586b\u5199\u3002',
	audioModelLabel: '\u97f3\u9891\u6a21\u578b',
	audioModelPlaceholder: '输入音频模型 ID，例如 Omni、Audio、TTS 或 ASR',
	needProvider: '\u8bf7\u5148\u9009\u62e9\u5e73\u53f0',
	needModel: '\u8bf7\u5148\u586b\u5199\u6a21\u578b',
	needCustomUrl: '\u8bf7\u5148\u586b\u5199\u81ea\u5b9a\u4e49\u63a5\u53e3\u5730\u5740',
	needApiKey: '\u8bf7\u5148\u586b\u5199 API Key',
	testConnection: '\u6d4b\u8bd5\u8fde\u63a5',
	testing: '\u6d4b\u8bd5\u4e2d...',
	testSuccessTitle: '\u8fde\u63a5\u6210\u529f',
	testFailTitle: '\u8fde\u63a5\u5931\u8d25',
	save: '\u4fdd\u5b58',
	saving: '\u4fdd\u5b58\u4e2d...',
	saveSuccess: '\u4fdd\u5b58\u6210\u529f',
	untestedTitle: '\u5c1a\u672a\u91cd\u65b0\u6d4b\u8bd5',
	untestedContent: '\u4f60\u4fee\u6539\u4e86\u914d\u7f6e\uff0c\u4f46\u8fd8\u6ca1\u6709\u91cd\u65b0\u6d4b\u8bd5\u3002\u786e\u5b9a\u76f4\u63a5\u4fdd\u5b58\u5417\uff1f',
	saveAnyway: '\u4ecd\u7136\u4fdd\u5b58',
	saved: '\u5df2\u4fdd\u5b58',
	clearSavedKey: '\u6e05\u9664\u5df2\u4fdd\u5b58 Key',
	undoClearSavedKey: '\u64a4\u9500\u6e05\u9664',
	clearStoredKeyTip: '\u4fdd\u5b58\u540e\u5c06\u6e05\u9664\u5df2\u4fdd\u5b58\u7684 Key\u3002\u5982\u679c\u4f60\u518d\u6b21\u586b\u5199\u65b0 Key\uff0c\u4f1a\u4ee5\u65b0 Key \u8986\u76d6\u6b64\u64cd\u4f5c\u3002',
	unavailableTitle: '\u5f53\u524d\u8d26\u53f7\u6682\u65f6\u4e0d\u53ef\u7528',
	optional: '\u53ef\u9009'
});

const CLEAN_COPY_EN = CLEAN_COPY_ZH;

const CLEAN_COPY = Object.freeze({
	'zh-cn': CLEAN_COPY_ZH,
	'zh-hk': CLEAN_COPY_ZH,
	en: CLEAN_COPY_EN
});

const PROVIDER_COPY = Object.freeze({
	siliconflow: {
		label: 'SiliconFlow',
		helpText: '\u9ed8\u8ba4\u805a\u5408\u5e73\u53f0\uff0c\u9002\u5408\u76f4\u63a5\u5f00\u59cb\u3002',
		apiKeyHint: 'SiliconFlow API Key'
	},
	deepseek: {
		label: 'DeepSeek',
		helpText: 'DeepSeek \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'DeepSeek API Key'
	},
	openrouter: {
		label: 'OpenRouter',
		helpText: '\u805a\u5408\u591a\u5bb6\u6a21\u578b\uff0c\u9002\u5408\u9700\u8981\u66f4\u591a\u6a21\u578b\u9009\u62e9\u3002',
		apiKeyHint: 'OpenRouter API Key'
	},
	openai: {
		label: 'OpenAI',
		helpText: 'OpenAI \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'OpenAI API Key'
	},
	groq: {
		label: 'Groq',
		helpText: '\u4f4e\u5ef6\u8fdf\u7684 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'Groq API Key'
	},
	mistralai: {
		label: 'Mistral',
		helpText: 'Mistral \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'Mistral API Key'
	},
	moonshot: {
		label: 'Moonshot',
		helpText: 'Moonshot \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'Moonshot API Key'
	},
	xai: {
		label: 'xAI',
		helpText: 'xAI \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'xAI API Key'
	},
	fireworks: {
		label: 'Fireworks',
		helpText: 'Fireworks \u5b98\u65b9 OpenAI \u517c\u5bb9\u63a5\u53e3\u3002',
		apiKeyHint: 'Fireworks API Key'
	},
	custom: {
		label: '\u81ea\u5b9a\u4e49\u517c\u5bb9\u63a5\u53e3',
		helpText: '\u53ef\u4ee5\u586b\u5199\u4efb\u610f OpenAI \u517c\u5bb9 API \u5730\u5740\u3002',
		apiKeyHint: 'Bearer API Key'
	}
});

const PRESET_COLLAPSE_LIMITS = Object.freeze({
	model: 6,
	vision: 4,
	stt: 4,
	tts: 4,
	ttsVoice: 4,
	image: 4,
	imageRaw: 6
});

const OPENAI_TTS_VOICE_PRESETS = Object.freeze(['alloy', 'nova', 'shimmer', 'echo', 'fable', 'onyx']);
const SILICONFLOW_TTS_VOICE_PRESETS = Object.freeze(['alex', 'benjamin', 'charles', 'david', 'anna', 'bella', 'claire', 'diana']);
const MODEL_AUTOLOAD_DEBOUNCE_MS = 550;

const FALLBACK_PROVIDER_OPTIONS = [
	{
		value: 'siliconflow',
		label: 'SiliconFlow',
		defaultBaseUrl: 'https://api.siliconflow.cn/v1',
		customUrlRequired: false,
		defaultModel: 'deepseek-ai/DeepSeek-V3',
		modelPresets: ['deepseek-ai/DeepSeek-V3', 'deepseek-ai/DeepSeek-R1', 'Qwen/Qwen2.5-72B-Instruct'],
		helpText: 'SiliconFlow OpenAI 兼容接口，推荐用于国产大模型。',
		apiKeyHint: 'SiliconFlow API Key'
	},
	{
		value: 'deepseek',
		label: 'DeepSeek',
		defaultBaseUrl: 'https://api.deepseek.com',
		customUrlRequired: false,
		defaultModel: 'deepseek-chat',
		modelPresets: ['deepseek-chat', 'deepseek-reasoner'],
		helpText: 'DeepSeek 官方 OpenAI 兼容接口。',
		apiKeyHint: 'DeepSeek API Key'
	},
	{
		value: 'openrouter',
		label: 'OpenRouter',
		defaultBaseUrl: 'https://openrouter.ai/api/v1',
		customUrlRequired: false,
		defaultModel: 'deepseek/deepseek-chat',
		modelPresets: ['deepseek/deepseek-chat', 'deepseek/deepseek-r1', 'openai/gpt-4o-mini'],
		helpText: 'OpenRouter 聚合平台，适合需要更多模型选择。',
		apiKeyHint: 'OpenRouter API Key'
	},
	{
		value: 'openai',
		label: 'OpenAI',
		defaultBaseUrl: 'https://api.openai.com/v1',
		customUrlRequired: false,
		defaultModel: 'gpt-4o-mini',
		modelPresets: ['gpt-4o-mini', 'gpt-4o'],
		helpText: 'OpenAI 官方接口。',
		apiKeyHint: 'OpenAI API Key'
	},
	{
		value: 'groq',
		label: 'Groq',
		defaultBaseUrl: 'https://api.groq.com/openai/v1',
		customUrlRequired: false,
		defaultModel: 'llama-3.3-70b-versatile',
		modelPresets: ['llama-3.3-70b-versatile', 'llama-3.1-8b-instant'],
		helpText: 'Groq 低延迟 OpenAI 兼容接口。',
		apiKeyHint: 'Groq API Key'
	},
	{
		value: 'mistralai',
		label: 'Mistral',
		defaultBaseUrl: 'https://api.mistral.ai/v1',
		customUrlRequired: false,
		defaultModel: 'mistral-small-latest',
		modelPresets: ['mistral-small-latest', 'mistral-large-latest'],
		helpText: 'Mistral 官方 OpenAI 兼容接口。',
		apiKeyHint: 'Mistral API Key'
	},
	{
		value: 'moonshot',
		label: 'Moonshot',
		defaultBaseUrl: 'https://api.moonshot.cn/v1',
		customUrlRequired: false,
		defaultModel: 'moonshot-v1-8k',
		modelPresets: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
		helpText: 'Moonshot 官方 OpenAI 兼容接口。',
		apiKeyHint: 'Moonshot API Key'
	},
	{
		value: 'xai',
		label: 'xAI',
		defaultBaseUrl: 'https://api.x.ai/v1',
		customUrlRequired: false,
		defaultModel: 'grok-2-latest',
		modelPresets: ['grok-2-latest'],
		helpText: 'xAI 官方 OpenAI 兼容接口。',
		apiKeyHint: 'xAI API Key'
	},
	{
		value: 'fireworks',
		label: 'Fireworks',
		defaultBaseUrl: 'https://api.fireworks.ai/inference/v1',
		customUrlRequired: false,
		defaultModel: 'accounts/fireworks/models/llama-v3p1-70b-instruct',
		modelPresets: ['accounts/fireworks/models/llama-v3p1-70b-instruct'],
		helpText: 'Fireworks OpenAI 兼容接口。',
		apiKeyHint: 'Fireworks API Key'
	},
	{
		value: 'custom',
		label: '自定义兼容接口',
		defaultBaseUrl: '',
		customUrlRequired: true,
		defaultModel: '',
		modelPresets: [],
		helpText: '填写任意 OpenAI 兼容接口地址，请确认基础地址包含 /v1。',
		apiKeyHint: '兼容服务商的 Bearer API Key'
	}
];

function looksGarbledText(value) {
	const text = String(value || '');
	return /[\u95c2\u6fde\u7f01\u5a75\u940e\u95bb\u6fe0\u59a4\u9237\u951d]/.test(text);
}

function normalizeProviderOption(source) {
	const raw = source && typeof source === 'object' ? source : {};
	const value = String(raw.value || '').trim();
	const providerCopy = PROVIDER_COPY[value] || {};
	const safeLabel = looksGarbledText(raw.label) ? '' : String(raw.label || '');
	const safeHelpText = looksGarbledText(raw.helpText) ? '' : String(raw.helpText || '');
	const safeApiKeyHint = looksGarbledText(raw.apiKeyHint) ? '' : String(raw.apiKeyHint || '');
	return {
		value,
		label: safeLabel || providerCopy.label || value || '平台',
		defaultBaseUrl: String(raw.defaultBaseUrl || ''),
		customUrlRequired: raw.customUrlRequired === true,
		defaultModel: String(raw.defaultModel || ''),
		modelPresets: Array.isArray(raw.modelPresets) ? raw.modelPresets.filter(Boolean).map(String) : [],
		helpText: safeHelpText || providerCopy.helpText || '',
		apiKeyHint: safeApiKeyHint || providerCopy.apiKeyHint || ''
	};
}

function normalizeVoiceTemplateItem(source) {
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

function emptyState() {
	return {
		enabledGlobal: false,
		canUse: false,
		denyReason: '',
		mode: 'system',
		providerSource: 'siliconflow',
		modelName: '',
		visionModelName: '',
		sttModelName: '',
		sttUseSeparateConfig: false,
		sttProviderSource: '',
		sttApiKeyConfigured: false,
		sttApiKeyMask: '',
		sttCustomUrl: '',
		effectiveSttProviderSource: '',
		effectiveSttApiKeyConfigured: false,
		effectiveSttApiKeyMask: '',
		effectiveSttCustomUrl: '',
		ttsModelName: '',
		ttsVoiceName: '',
		ttsVoiceTemplateCode: '',
		ttsVoiceTemplateLabel: '',
		ttsUseSeparateConfig: false,
		ttsProviderSource: '',
		ttsApiKeyConfigured: false,
		ttsApiKeyMask: '',
		ttsCustomUrl: '',
		effectiveTtsProviderSource: '',
		effectiveTtsApiKeyConfigured: false,
		effectiveTtsApiKeyMask: '',
		effectiveTtsCustomUrl: '',
		voiceEnabledGlobal: true,
		voiceCanUse: true,
		voiceDenyReason: '',
		imageModelName: '',
		imageCharacterConsistencyMode: 'free',
		imageReferenceSourceMode: 'latest_generated_first',
		imageUseSeparateConfig: false,
		imageProviderSource: '',
		imageApiKeyConfigured: false,
		imageApiKeyMask: '',
		imageCustomUrl: '',
		effectiveImageProviderSource: '',
		effectiveImageApiKeyConfigured: false,
		effectiveImageApiKeyMask: '',
		effectiveImageCustomUrl: '',
		imageEnabledGlobal: true,
		imageCanUse: true,
		imageDenyReason: '',
		apiKeyConfigured: false,
		apiKeyMask: '',
		customUrl: '',
		currentVipLevel: 0,
		vipMinLevel: 0,
		availableSources: FALLBACK_PROVIDER_OPTIONS.map((item) => item.value),
		providerOptions: FALLBACK_PROVIDER_OPTIONS,
		ttsVoiceTemplates: []
	};
}

function uniqueStrings(list) {
	const seen = Object.create(null);
	return (Array.isArray(list) ? list : []).filter((item) => {
		const text = String(item || '').trim();
		if (!text || seen[text]) {
			return false;
		}
		seen[text] = true;
		return true;
	});
}

function pickLocaleText(localeCode, messages) {
	return messages['zh-cn'] || messages['zh-hk'] || messages.en || '';
}

export default {
	components: { TavernNavBar },
	data() {
		return {
			saving: false,
			testing: false,
			loadingModels: false,
			loadingSttModels: false,
			loadingTtsModels: false,
			loadingImageModels: false,
			loadingConfig: false,
			hasLoadedConfig: false,
			cleanFormSignature: '',
			modelListRequestSeq: 0,
			sttModelListRequestSeq: 0,
			ttsModelListRequestSeq: 0,
			imageModelListRequestSeq: 0,
			lastOkTestSignature: '',
			testState: {
				ok: false,
				message: '',
				latencyMs: 0
			},
			viewState: emptyState(),
			providerModels: [],
			providerModelItems: [],
			modelListMessage: '',
			sttProviderModels: [],
			sttProviderModelItems: [],
			sttModelListMessage: '',
			ttsProviderModels: [],
			ttsProviderModelItems: [],
			ttsModelListMessage: '',
			imageProviderModels: [],
			imageProviderModelItems: [],
			imageModelListMessage: '',
			autoLoadMainModelsTimer: null,
			autoLoadTtsModelsTimer: null,
			autoLoadImageModelsTimer: null,
			presetExpanded: {},
			form: {
				mode: 'system',
				providerSource: 'siliconflow',
				modelName: '',
				visionModelName: '',
				sttModelName: '',
				sttUseSeparateConfig: false,
				sttProviderSource: '',
				sttApiKey: '',
				sttCustomUrl: '',
				clearStoredSttKey: false,
				ttsModelName: '',
				ttsVoiceName: '',
				ttsVoiceTemplateCode: '',
				ttsUseSeparateConfig: false,
				ttsProviderSource: '',
				ttsApiKey: '',
				ttsCustomUrl: '',
				clearStoredTtsKey: false,
				imageModelName: '',
				imageCharacterConsistencyMode: 'free',
				imageReferenceSourceMode: 'latest_generated_first',
				imageUseSeparateConfig: false,
				imageProviderSource: '',
				imageApiKey: '',
				imageCustomUrl: '',
				clearStoredImageKey: false,
				apiKey: '',
				customUrl: '',
				clearStoredKey: false
			}
		};
	},
	computed: {
		localeCode() {
			return getLanguageCode();
		},
		copy() {
			return CLEAN_COPY[this.localeCode] || CLEAN_COPY['zh-cn'];
		},
		sourceOptions() {
			const fallbackOptions = FALLBACK_PROVIDER_OPTIONS
				.map(normalizeProviderOption);
			const backendOptions = Array.isArray(this.viewState.providerOptions)
				? this.viewState.providerOptions
					.map(normalizeProviderOption)
				: [];
			if (!backendOptions.length) {
				return fallbackOptions;
			}
			const fallbackMap = Object.create(null);
			fallbackOptions.forEach((item) => {
				fallbackMap[item.value] = item;
			});
			const merged = backendOptions.map((item) => {
				const fallback = fallbackMap[item.value] || normalizeProviderOption({});
				return {
					value: item.value || fallback.value,
					label: item.label || fallback.label,
					defaultBaseUrl: item.defaultBaseUrl || fallback.defaultBaseUrl,
					customUrlRequired: !!(item.customUrlRequired || fallback.customUrlRequired),
					defaultModel: item.defaultModel || fallback.defaultModel,
					modelPresets: uniqueStrings([].concat(item.modelPresets || []).concat(fallback.modelPresets || [])),
					helpText: item.helpText || fallback.helpText,
					apiKeyHint: item.apiKeyHint || fallback.apiKeyHint
				};
			});
			const existing = Object.create(null);
			merged.forEach((item) => {
				existing[item.value] = true;
			});
			fallbackOptions.forEach((item) => {
				if (!existing[item.value]) {
					merged.push(item);
				}
			});
			return merged;
		},
		sourceLabelList() {
			return this.sourceOptions.map((item) => item.label);
		},
		sourcePickerIndex() {
			const index = this.sourceOptions.findIndex((item) => item.value === this.form.providerSource);
			return index < 0 ? 0 : index;
		},
		selectedSourceLabel() {
			const current = this.sourceOptions[this.sourcePickerIndex];
			return current ? current.label : '自定义平台';
		},
		currentProviderOption() {
			return this.getProviderOption(this.form.providerSource) || this.sourceOptions[0] || normalizeProviderOption({});
		},
		ttsSourcePickerIndex() {
			const index = this.sourceOptions.findIndex((item) => item.value === this.form.ttsProviderSource);
			return index < 0 ? 0 : index;
		},
		selectedTtsSourceLabel() {
			const current = this.sourceOptions[this.ttsSourcePickerIndex];
			return current ? current.label : this.selectedSourceLabel;
		},
		sttSourcePickerIndex() {
			const index = this.sourceOptions.findIndex((item) => item.value === this.form.sttProviderSource);
			return index < 0 ? 0 : index;
		},
		selectedSttSourceLabel() {
			const current = this.sourceOptions[this.sttSourcePickerIndex];
			return current ? current.label : this.selectedSourceLabel;
		},
		currentSttProviderOption() {
			const value = String(this.form.sttProviderSource || '').trim();
			if (!value) {
				return this.currentProviderOption;
			}
			return this.getProviderOption(value) || this.currentProviderOption;
		},
		currentTtsProviderOption() {
			const value = String(this.form.ttsProviderSource || '').trim();
			if (!value) {
				return this.currentProviderOption;
			}
			return this.getProviderOption(value) || this.currentProviderOption;
		},
		imageSourcePickerIndex() {
			const index = this.sourceOptions.findIndex((item) => item.value === this.form.imageProviderSource);
			return index < 0 ? 0 : index;
		},
		selectedImageSourceLabel() {
			const current = this.sourceOptions[this.imageSourcePickerIndex];
			return current ? current.label : this.selectedSourceLabel;
		},
		testStatusItems() {
			const message = String(this.testState.message || '').trim();
			if (!message) {
				return [];
			}
			return message
				.split(/\n+/)
				.map((line) => String(line || '').trim())
				.filter(Boolean)
				.map((line) => {
					const match = line.match(/^([^:]+)\s*:\s*(.+)$/);
					if (!match) {
						const known = [
							{ scope: 'main', raw: this.normalizeConnectionScopeLabel('', 'main') },
							{ scope: 'image', raw: this.normalizeConnectionScopeLabel('', 'image') },
							{ scope: 'tts', raw: this.normalizeConnectionScopeLabel('', 'tts') },
							{ scope: 'main', raw: '主模型' },
							{ scope: 'image', raw: '生图模型' },
							{ scope: 'tts', raw: 'TTS' }
						];
						const found = known.find((item) => item.raw && line.indexOf(item.raw) === 0);
						if (!found) {
							return { label: '', message: line };
						}
						const messageText = String(line.slice(found.raw.length) || '')
						.replace(/^[\\s:：，,]+/, '')
							.trim();
						return {
							label: this.normalizeConnectionScopeLabel('', found.scope),
							message: messageText || line
						};
					}
					return {
						label: this.normalizeConnectionScopeLabel(String(match[1] || '').trim()),
						message: String(match[2] || '').trim()
					};
				});
		},
		testStatusLatencyText() {
			const latencyMs = Number(this.testState.latencyMs || 0);
			return latencyMs > 0 ? latencyMs + 'ms' : '';
		},
		testStatusHeadingText() {
			if (!this.testState.message) {
				return '';
			}
			return this.testState.ok ? this.copy.testSuccessTitle : this.copy.testFailTitle;
		},
		currentImageProviderOption() {
			const value = String(this.form.imageProviderSource || '').trim();
			if (!value) {
				return this.currentProviderOption;
			}
			return this.getProviderOption(value) || this.currentProviderOption;
		},
		showVoiceConfig() {
			return this.viewState.voiceEnabledGlobal !== false;
		},
		showImageConfig() {
			return this.viewState.imageEnabledGlobal !== false;
		},
		currentTtsProviderModelItems() {
			return this.form.ttsUseSeparateConfig ? this.ttsProviderModelItems : this.providerModelItems;
		},
		currentSttProviderModelItems() {
			return this.form.sttUseSeparateConfig ? this.sttProviderModelItems : this.providerModelItems;
		},
		currentImageProviderModelItems() {
			return this.form.imageUseSeparateConfig ? this.imageProviderModelItems : this.providerModelItems;
		},
		sttBaseModelPresets() {
			const option = this.form.sttUseSeparateConfig ? this.currentSttProviderOption : this.currentProviderOption;
			const dynamicModels = this.form.sttUseSeparateConfig ? this.sttProviderModels : this.providerModels;
			return uniqueStrings([])
				.concat(Array.isArray(dynamicModels) ? dynamicModels : [])
				.concat(Array.isArray(option.modelPresets) ? option.modelPresets : []);
		},
		ttsBaseModelPresets() {
			const option = this.form.ttsUseSeparateConfig ? this.currentTtsProviderOption : this.currentProviderOption;
			const dynamicModels = this.form.ttsUseSeparateConfig ? this.ttsProviderModels : this.providerModels;
			return uniqueStrings([])
				.concat(Array.isArray(dynamicModels) ? dynamicModels : [])
				.concat(Array.isArray(option.modelPresets) ? option.modelPresets : []);
		},
		imageBaseModelPresets() {
			const option = this.form.imageUseSeparateConfig ? this.currentImageProviderOption : this.currentProviderOption;
			const dynamicModels = this.form.imageUseSeparateConfig ? this.imageProviderModels : this.providerModels;
			return uniqueStrings([])
				.concat(Array.isArray(dynamicModels) ? dynamicModels : [])
				.concat(Array.isArray(option.modelPresets) ? option.modelPresets : []);
		},
		modelPresets() {
			const dynamicModels = Array.isArray(this.providerModels) ? this.providerModels : [];
			const presetModels = Array.isArray(this.currentProviderOption.modelPresets) ? this.currentProviderOption.modelPresets : [];
			const textCatalogMatched = this.filterModelCatalogItemsByCapability(this.providerModelItems, 'text');
			const heuristicMatched = this.filterModelListByCapability(
				uniqueStrings([].concat(dynamicModels).concat(presetModels)),
				'text'
			);
			return uniqueStrings([].concat(textCatalogMatched).concat(heuristicMatched).concat(presetModels));
		},
		providerCapabilitySummaryItems() {
			const items = [
				{ key: 'text', label: '文本', count: this.modelPresets.length },
				{ key: 'vision', label: '视觉', count: this.visionModelPresets.length },
				{ key: 'stt', label: '识音', count: this.showVoiceConfig ? this.sttModelPresets.length : 0 },
				{ key: 'tts', label: 'TTS', count: this.showVoiceConfig ? this.ttsModelPresets.length : 0 },
				{ key: 'image', label: '生图', count: this.showImageConfig ? this.imageModelPresets.length : 0 }
			];
			return items.filter((item) => item.count > 0);
		},
		mainModelHelperText() {
			if (this.loadingModels) {
				return '正在根据当前平台和 Key 获取模型列表...';
			}
			if (this.modelPresets.length) {
				return '已优先筛出可用于主聊天的文本模型。';
			}
			if (this.providerModels.length) {
				return '已拿到平台模型列表，但暂时没明确识别出文本模型，可以手动填写。';
			}
			if (this.canLoadModels) {
				return '填好 Key 后可以直接获取模型，也可以手动填写模型 ID。';
			}
			return '';
		},
		effectiveSavedKeyAvailable() {
			return this.savedKeyAppliesToCurrentProvider && !this.form.clearStoredKey;
		},
		savedSttKeyAppliesToCurrentProvider() {
			if (!this.showVoiceConfig) {
				return false;
			}
			if (!this.form.sttUseSeparateConfig || !this.viewState.sttUseSeparateConfig || !this.viewState.sttApiKeyConfigured) {
				return false;
			}
			if (String(this.form.sttProviderSource || '').trim() !== String(this.viewState.sttProviderSource || '').trim()) {
				return false;
			}
			if (this.form.sttProviderSource === 'custom') {
				return String(this.form.sttCustomUrl || '').trim() === String(this.viewState.sttCustomUrl || '').trim();
			}
			return true;
		},
		effectiveSavedSttKeyAvailable() {
			return this.savedSttKeyAppliesToCurrentProvider && !this.form.clearStoredSttKey;
		},
		savedTtsKeyAppliesToCurrentProvider() {
			if (!this.showVoiceConfig) {
				return false;
			}
			if (!this.form.ttsUseSeparateConfig || !this.viewState.ttsUseSeparateConfig || !this.viewState.ttsApiKeyConfigured) {
				return false;
			}
			if (String(this.form.ttsProviderSource || '').trim() !== String(this.viewState.ttsProviderSource || '').trim()) {
				return false;
			}
			if (this.form.ttsProviderSource === 'custom') {
				return String(this.form.ttsCustomUrl || '').trim() === String(this.viewState.ttsCustomUrl || '').trim();
			}
			return true;
		},
		effectiveSavedTtsKeyAvailable() {
			return this.savedTtsKeyAppliesToCurrentProvider && !this.form.clearStoredTtsKey;
		},
		savedImageKeyAppliesToCurrentProvider() {
			if (!this.showImageConfig) {
				return false;
			}
			if (!this.form.imageUseSeparateConfig || !this.viewState.imageUseSeparateConfig || !this.viewState.imageApiKeyConfigured) {
				return false;
			}
			if (String(this.form.imageProviderSource || '').trim() !== String(this.viewState.imageProviderSource || '').trim()) {
				return false;
			}
			if (this.form.imageProviderSource === 'custom') {
				return String(this.form.imageCustomUrl || '').trim() === String(this.viewState.imageCustomUrl || '').trim();
			}
			return true;
		},
		effectiveSavedImageKeyAvailable() {
			return this.savedImageKeyAppliesToCurrentProvider && !this.form.clearStoredImageKey;
		},
		visionModelPresets() {
			return this.filterModelsByCapability('vision');
		},
		sttModelPresets() {
			if (!this.showVoiceConfig) {
				return [];
			}
			const option = this.form.sttUseSeparateConfig ? this.currentSttProviderOption : this.currentProviderOption;
			const optionPresets = Array.isArray(option.modelPresets) ? option.modelPresets : [];
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentSttProviderModelItems, 'stt');
			const heuristicMatched = this.filterModelListByCapability(this.sttBaseModelPresets, 'stt');
			return uniqueStrings([].concat(catalogMatched).concat(heuristicMatched).concat(optionPresets.filter((model) => this.matchCapabilityModel(model, 'stt'))));
		},
		sttRawModelPresets() {
			if (!this.showVoiceConfig) {
				return [];
			}
			const dynamicModels = this.form.sttUseSeparateConfig ? this.sttProviderModels : this.providerModels;
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentSttProviderModelItems, 'stt');
			if (catalogMatched.length) {
				return catalogMatched;
			}
			return this.filterModelListByCapability(dynamicModels, 'stt');
		},
		sttRawModelDisplayPresets() {
			const raw = this.sttRawModelPresets;
			if (!raw.length) {
				return [];
			}
			if (!this.sttModelPresets.length) {
				return raw;
			}
			return raw.filter((model) => this.sttModelPresets.indexOf(model) < 0);
		},
		ttsModelPresets() {
			if (!this.showVoiceConfig) {
				return [];
			}
			const option = this.form.ttsUseSeparateConfig ? this.currentTtsProviderOption : this.currentProviderOption;
			const optionPresets = Array.isArray(option.modelPresets) ? option.modelPresets : [];
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentTtsProviderModelItems, 'tts');
			const heuristicMatched = this.filterModelListByCapability(this.ttsBaseModelPresets, 'tts');
			return uniqueStrings([].concat(catalogMatched).concat(heuristicMatched).concat(optionPresets.filter((model) => this.matchCapabilityModel(model, 'tts'))));
		},
		ttsRawModelPresets() {
			if (!this.showVoiceConfig) {
				return [];
			}
			const dynamicModels = this.form.ttsUseSeparateConfig ? this.ttsProviderModels : this.providerModels;
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentTtsProviderModelItems, 'tts');
			if (catalogMatched.length) {
				return catalogMatched;
			}
			return this.filterModelListByCapability(dynamicModels, 'tts');
		},
		ttsRawModelDisplayPresets() {
			const raw = this.ttsRawModelPresets;
			if (!raw.length) {
				return [];
			}
			if (!this.ttsModelPresets.length) {
				return raw;
			}
			return raw.filter((model) => this.ttsModelPresets.indexOf(model) < 0);
		},
		ttsVoicePresets() {
			if (!this.showVoiceConfig) {
				return [];
			}
			if (this.supportsOpenAiVoicePresets(this.form.ttsModelName)) {
				return OPENAI_TTS_VOICE_PRESETS.slice();
			}
			if (this.supportsSiliconFlowVoicePresets(this.form.ttsModelName)) {
				return SILICONFLOW_TTS_VOICE_PRESETS.slice();
			}
			return [];
		},
		ttsVoiceTemplates() {
			if (!this.showVoiceConfig) {
				return [];
			}
			const source = Array.isArray(this.viewState.ttsVoiceTemplates) ? this.viewState.ttsVoiceTemplates : [];
			return source.map((item) => normalizeVoiceTemplateItem(item)).filter((item) => item.code);
		},
		selectedTtsVoiceTemplate() {
			const currentCode = String(this.form.ttsVoiceTemplateCode || '').trim();
			if (!currentCode) {
				return null;
			}
			return this.ttsVoiceTemplates.find((item) => item.code === currentCode) || null;
		},
		imageModelPresets() {
			if (!this.showImageConfig) {
				return [];
			}
			const option = this.form.imageUseSeparateConfig ? this.currentImageProviderOption : this.currentProviderOption;
			const optionPresets = Array.isArray(option.modelPresets) ? option.modelPresets : [];
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentImageProviderModelItems, 'image');
			const heuristicMatched = this.filterModelListByCapability(this.imageBaseModelPresets, 'image');
			return uniqueStrings([].concat(catalogMatched).concat(heuristicMatched).concat(optionPresets.filter((model) => this.matchCapabilityModel(model, 'image'))));
		},
		imageRawModelPresets() {
			if (!this.showImageConfig) {
				return [];
			}
			const dynamicModels = this.form.imageUseSeparateConfig ? this.imageProviderModels : this.providerModels;
			const catalogMatched = this.filterModelCatalogItemsByCapability(this.currentImageProviderModelItems, 'image');
			if (catalogMatched.length) {
				return catalogMatched;
			}
			return this.filterModelListByCapability(dynamicModels, 'image');
		},
		imageRawModelDisplayPresets() {
			const raw = this.imageRawModelPresets;
			if (!raw.length) {
				return [];
			}
			if (!this.imageModelPresets.length) {
				return raw;
			}
			return raw.filter((model) => this.imageModelPresets.indexOf(model) < 0);
		},
		savedKeyAppliesToCurrentProvider() {
			if (!this.viewState.apiKeyConfigured) {
				return false;
			}
			if (String(this.form.providerSource || '').trim() !== String(this.viewState.providerSource || '').trim()) {
				return false;
			}
			if (this.form.providerSource === 'custom') {
				return String(this.form.customUrl || '').trim() === String(this.viewState.customUrl || '').trim();
			}
			return true;
		},
		canLoadModels() {
			if (this.form.mode !== 'custom' || !this.viewState.canUse || !String(this.form.providerSource || '').trim()) {
				return false;
			}
			if (this.form.providerSource === 'custom' && !String(this.form.customUrl || '').trim()) {
				return false;
			}
			return !!(String(this.form.apiKey || '').trim() || this.effectiveSavedKeyAvailable);
		},
		canLoadTtsModels() {
			if (!this.showVoiceConfig) {
				return false;
			}
			if (this.form.mode !== 'custom' || !this.viewState.canUse) {
				return false;
			}
			if (!this.form.ttsUseSeparateConfig) {
				return this.canLoadModels;
			}
			if (!String(this.form.ttsProviderSource || '').trim()) {
				return false;
			}
			if (this.form.ttsProviderSource === 'custom' && !String(this.form.ttsCustomUrl || '').trim()) {
				return false;
			}
			return !!(String(this.form.ttsApiKey || '').trim() || this.effectiveSavedTtsKeyAvailable);
		},
		canLoadSttModels() {
			if (!this.showVoiceConfig) {
				return false;
			}
			if (this.form.mode !== 'custom' || !this.viewState.canUse) {
				return false;
			}
			if (!this.form.sttUseSeparateConfig) {
				return this.canLoadModels;
			}
			if (!String(this.form.sttProviderSource || '').trim()) {
				return false;
			}
			if (this.form.sttProviderSource === 'custom' && !String(this.form.sttCustomUrl || '').trim()) {
				return false;
			}
			return !!(String(this.form.sttApiKey || '').trim() || this.effectiveSavedSttKeyAvailable);
		},
		canLoadImageModels() {
			if (!this.showImageConfig) {
				return false;
			}
			if (this.form.mode !== 'custom' || !this.viewState.canUse) {
				return false;
			}
			if (!this.form.imageUseSeparateConfig) {
				return this.canLoadModels;
			}
			if (!String(this.form.imageProviderSource || '').trim()) {
				return false;
			}
			if (this.form.imageProviderSource === 'custom' && !String(this.form.imageCustomUrl || '').trim()) {
				return false;
			}
			return !!(String(this.form.imageApiKey || '').trim() || this.effectiveSavedImageKeyAvailable);
		},
		canSmartFillModels() {
			const voiceAvailable = this.showVoiceConfig && (this.sttModelPresets.length || this.ttsModelPresets.length);
			const imageAvailable = this.showImageConfig && this.imageModelPresets.length;
			return !!(
				this.modelPresets.length ||
				this.visionModelPresets.length ||
				voiceAvailable ||
				imageAvailable ||
				String(this.currentProviderOption.defaultModel || '').trim()
			);
		},
		loadModelsText() {
			return this.copy.loadModels || '\u83b7\u53d6\u6a21\u578b';
		},
		ttsModelListLabelText() {
			const models = this.form.ttsUseSeparateConfig ? this.ttsProviderModels : this.providerModels;
			return models.length ? this.copy.providerModels : this.copy.recommendedModels;
		},
		smartFillButtonText() {
			return this.copy.smartFill || '智能填充';
		},
		loadingModelsText() {
			return this.copy.loadingModels || '\u83b7\u53d6\u4e2d...';
		},
		modelListLabelText() {
			return this.providerModels.length ? this.copy.providerModels : this.copy.recommendedModels;
		},
		providerHelpText() {
			return this.currentProviderOption.helpText || '';
		},
		sttProviderHelpText() {
			return this.form.sttUseSeparateConfig ? (this.currentSttProviderOption.helpText || '') : '';
		},
		ttsProviderHelpText() {
			return this.form.ttsUseSeparateConfig ? (this.currentTtsProviderOption.helpText || '') : '';
		},
		imageProviderHelpText() {
			return this.form.imageUseSeparateConfig ? (this.currentImageProviderOption.helpText || '') : '';
		},
		compactAuxModelsTipText() {
			const zhParts = ['视觉'];
			const enParts = ['vision'];
			if (this.showVoiceConfig) {
				zhParts.push('语音');
				enParts.push('voice');
			}
			if (this.showImageConfig) {
				zhParts.push('生图');
				enParts.push('image generation');
			}
			return pickLocaleText(this.localeCode, {
				'zh-cn': '这些模型仅用于' + zhParts.join('、') + '，不影响主聊天模型。',
				'zh-hk': '这些模型仅用于' + zhParts.join('、') + '，不影响主聊天模型。',
				en: 'These models are only used for ' + enParts.join(', ') + '. They do not affect the main chat model.'
			});
		},
		compactTtsProviderSectionDescText() {
			return this.form.ttsUseSeparateConfig
				? pickLocaleText(this.localeCode, {
					'zh-cn': '这只影响 TTS，不会改变主聊天模型。',
					'zh-hk': '这只影响 TTS，不会改变主聊天模型。',
					en: 'This only affects TTS and does not change the main chat model.'
				})
				: pickLocaleText(this.localeCode, {
					'zh-cn': '默认情况下，TTS 复用主平台和 Key。',
					'zh-hk': '默认情况下，TTS 复用主平台和 Key。',
					en: 'By default, TTS reuses the main provider and key.'
				});
		},
		compactSttProviderSectionDescText() {
			return this.form.sttUseSeparateConfig
				? pickLocaleText(this.localeCode, {
					'zh-cn': '这只影响 STT，不会改变主聊天模型。',
					'zh-hk': '這只影響 STT，不會改變主聊天模型。',
					en: 'This only affects STT and does not change the main chat model.'
				})
				: pickLocaleText(this.localeCode, {
					'zh-cn': '默认情况下，STT 复用主平台和 Key。',
					'zh-hk': '預設情況下，STT 共用主平台和 Key。',
					en: 'By default, STT reuses the main provider and key.'
				});
		},
		compactImageProviderSectionDescText() {
			return this.form.imageUseSeparateConfig
				? pickLocaleText(this.localeCode, {
					'zh-cn': '这只影响生图，不会改变主聊天模型。',
					'zh-hk': '这只影响生图，不会改变主聊天模型。',
					en: 'This only affects image generation and does not change the main chat model.'
				})
				: pickLocaleText(this.localeCode, {
					'zh-cn': '默认情况下，生图复用主平台和 Key。',
					'zh-hk': '默认情况下，生图复用主平台和 Key。',
					en: 'By default, image generation reuses the main provider and key.'
				});
		},
		heroSummaryText() {
			if (this.form.mode === 'system') {
				return this.copy.heroSubtitle;
			}
			if (!this.viewState.enabledGlobal) {
				return this.viewState.denyReason || this.copy.heroSubtitle;
			}
			if (!this.viewState.canUse && this.viewState.denyReason) {
				return this.viewState.denyReason;
			}
			if (this.selectedTtsVoiceTemplate) {
				return pickLocaleText(this.localeCode, {
					'zh-cn': '\u5f53\u524d\u5df2\u9009\u62e9\u97f3\u8272\u6a21\u677f\uff1a' + (this.selectedTtsVoiceTemplate.displayName || this.selectedTtsVoiceTemplate.code),
					'zh-hk': '\u76ee\u524d\u5df2\u9078\u64c7\u97f3\u8272\u6a21\u677f\uff1a' + (this.selectedTtsVoiceTemplate.displayName || this.selectedTtsVoiceTemplate.code),
					en: 'Current voice template: ' + (this.selectedTtsVoiceTemplate.displayName || this.selectedTtsVoiceTemplate.code)
				});
			}
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u9009\u62e9\u670d\u52a1\u5546\u548c\u6a21\u578b\uff0c\u6d4b\u8bd5\u901a\u8fc7\u540e\u4fdd\u5b58\u5373\u53ef\u3002',
				'zh-hk': '\u9078\u64c7\u670d\u52d9\u5546\u548c\u6a21\u578b\uff0c\u6e2c\u8a66\u901a\u904e\u5f8c\u5132\u5b58\u5373\u53ef\u3002',
				en: 'Pick a provider and model, test the connection, then save.'
			});
		},
		modelPlaceholderText() {
			return this.currentProviderOption.defaultModel || this.copy.modelPlaceholder;
		},
		auxModelsTitleText() {
			return this.copy.auxModelsTitle || '扩展模型';
		},
		auxModelsMetaText() {
			return this.copy.optional || '可选';
		},
		auxModelsTipText() {
			return this.copy.auxModelsTip || '可以为视觉、识音、TTS 和生图单独指定模型。';
		},
		visionModelLabelText() {
			return this.copy.visionModelLabel || '视觉模型';
		},
		audioModelLabelText() {
			return this.copy.audioModelLabel || '语音模型';
		},
		imageModelLabelText() {
			return this.copy.imageModelLabel || '生图模型';
		},
		visionModelPlaceholderText() {
			return this.copy.visionModelPlaceholder || '输入视觉模型 ID';
		},
		audioModelPlaceholderText() {
			return this.copy.audioModelPlaceholder || '输入音频模型 ID';
		},
		imageModelPlaceholderText() {
			return this.copy.imageModelPlaceholder || '输入生图模型 ID';
		},
		sttModelLabelText() {
			return this.copy.sttModelLabel || '识音模型';
		},
		ttsVoiceLabelText() {
			return this.copy.ttsVoiceLabel || 'TTS 音色';
		},
		ttsVoiceTemplateIntroText() {
			return this.form.ttsVoiceTemplateCode
				? pickLocaleText(this.localeCode, {
					'zh-cn': '\u5df2\u9009\u62e9\u97f3\u8272\u6a21\u677f\uff0c\u4fdd\u5b58\u540e\u4f1a\u8986\u76d6\u5f53\u524d\u89d2\u8272\u7684\u624b\u52a8\u97f3\u8272\u8bbe\u7f6e\u3002',
					'zh-hk': '\u5df2\u9078\u64c7\u97f3\u8272\u6a21\u677f\uff0c\u5132\u5b58\u5f8c\u6703\u8986\u84cb\u76ee\u524d\u89d2\u8272\u7684\u624b\u52d5\u97f3\u8272\u8a2d\u5b9a\u3002',
					en: 'A voice template is selected. Saving will override the manual voice setting for this role.'
				  })
				: pickLocaleText(this.localeCode, {
					'zh-cn': '可以直接选择模板音色，也可以继续手动填写音色 ID。',
					'zh-hk': '可以直接选择模板音色，也可以继续手动填写音色 ID。',
					en: 'Choose a template voice or keep using a manual voice ID.'
				  });
		},
		selectedTtsVoiceTemplateTitleText() {
			const template = this.selectedTtsVoiceTemplate;
			if (!template) {
				return pickLocaleText(this.localeCode, { 'zh-cn': '\u5f53\u524d\u6a21\u677f', 'zh-hk': '\u76ee\u524d\u6a21\u677f', en: 'Current Template' });
			}
			return pickLocaleText(this.localeCode, { 'zh-cn': '\u5f53\u524d\u6a21\u677f\uff1a', 'zh-hk': '\u76ee\u524d\u6a21\u677f\uff1a', en: 'Current Template: ' }) + (template.displayName || template.code);
		},
		selectedTtsVoiceTemplateStatusText() {
			const template = this.selectedTtsVoiceTemplate;
			if (!template) {
				return pickLocaleText(this.localeCode, { 'zh-cn': '\u672a\u9009\u62e9\u6a21\u677f', 'zh-hk': '\u672a\u9078\u64c7\u6a21\u677f', en: 'No template selected' });
			}
			if (template.ready) {
				return template.statusText || pickLocaleText(this.localeCode, { 'zh-cn': '\u6a21\u677f\u53ef\u7528', 'zh-hk': '\u6a21\u677f\u53ef\u7528', en: 'Template ready' });
			}
			return template.statusText || pickLocaleText(this.localeCode, { 'zh-cn': '\u6a21\u677f\u6682\u4e0d\u53ef\u7528', 'zh-hk': '\u6a21\u677f\u66ab\u4e0d\u53ef\u7528', en: 'Template not ready yet' });
		},
		sttModelPlaceholderText() {
			return this.copy.sttModelPlaceholder || '输入识音模型 ID';
		},
		ttsModelPlaceholderText() {
			return this.copy.ttsModelPlaceholder || '输入 TTS 模型 ID';
		},
		ttsVoicePlaceholderText() {
			return this.copy.ttsVoicePlaceholder || '输入音色 ID';
		},
		effectiveTtsModelPlaceholderText() {
			const option = this.form.ttsUseSeparateConfig ? this.currentTtsProviderOption : this.currentProviderOption;
			return option.defaultModel || this.copy.ttsModelPlaceholder || 'gpt-4o-mini-tts';
		},
		effectiveTtsModelHintText() {
			const dynamicModels = this.form.ttsUseSeparateConfig ? this.ttsProviderModels : this.providerModels;
			if (!dynamicModels.length || this.ttsModelPresets.length) {
				return '';
			}
			return this.copy.ttsModelHintEmpty || '未识别到 TTS 模型，可以手动填写。';
		},
		sttModelHintText() {
			if (!this.providerModels.length || this.sttModelPresets.length) {
				return '';
			}
			return this.copy.sttModelHintEmpty || '未识别到识音模型，可以手动填写。';
		},
		ttsModelHintText() {
			if (!this.providerModels.length || this.ttsModelPresets.length) {
				return '';
			}
			return this.copy.ttsModelHintEmpty || '未识别到 TTS 模型，可以手动填写。';
		},
		imageModelPlaceholderText() {
			const option = this.form.imageUseSeparateConfig ? this.currentImageProviderOption : this.currentProviderOption;
			return option.defaultModel || this.copy.imageModelPlaceholder || '输入生图模型 ID';
		},
		imageModelHintText() {
			const dynamicModels = this.form.imageUseSeparateConfig ? this.imageProviderModels : this.providerModels;
			if (!dynamicModels.length || this.imageModelPresets.length) {
				return '';
			}
			return this.copy.imageModelHintEmpty || '未识别到生图模型，可以手动填写。';
		},
		resolvedTtsVoicePlaceholderText() {
			if (this.supportsOpenAiVoicePresets(this.form.ttsModelName)) {
				return this.ttsVoicePlaceholderText;
			}
			if (this.supportsSiliconFlowVoicePresets(this.form.ttsModelName)) {
				return this.copy.ttsVoicePlaceholderSiliconFlow || '输入 SiliconFlow 音色 ID，例如 anna';
			}
			return this.copy.ttsVoicePlaceholderGeneric || '输入音色 ID';
		},
		ttsVoiceHintText() {
			if (this.form.ttsVoiceTemplateCode) {
				return '';
			}
			const modelName = String(this.form.ttsModelName || '').trim();
			if (!modelName) {
				return '';
			}
			if (this.supportsOpenAiVoicePresets(modelName)) {
				return '';
			}
			if (this.supportsSiliconFlowVoicePresets(modelName)) {
				if (this.isKnownOpenAiVoicePreset(this.form.ttsVoiceName)) {
					return this.copy.ttsVoiceHintSiliconFlowWrong || '当前音色 ID 看起来不像 SiliconFlow 预设值，请再检查。';
				}
				return this.copy.ttsVoiceHintSiliconFlow || 'SiliconFlow 音色 ID 通常使用 alex、anna、bella 这类名称。';
			}
			if (this.isKnownOpenAiVoicePreset(this.form.ttsVoiceName)) {
				return this.copy.ttsVoiceHintOpenAiOnly || 'OpenAI 音色一般使用 alloy、nova、shimmer、echo、fable 或 onyx。';
			}
			return this.copy.ttsVoiceHintGeneric || '请先确认 TTS 模型，再填写音色 ID。';
		},
		compactApiKeyTipText() {
			const hint = this.currentProviderOption.apiKeyHint;
			if (this.form.clearStoredKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			const fallback = pickLocaleText(this.localeCode, {
				'zh-cn': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				'zh-hk': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				en: 'Leave blank to keep the saved key.'
			});
			return hint ? this.copy.apiKeyHintPrefix + hint + '：' + fallback : fallback;
		},
		compactTtsApiKeyTipText() {
			const hint = this.currentTtsProviderOption.apiKeyHint;
			if (this.form.clearStoredTtsKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			const fallback = pickLocaleText(this.localeCode, {
				'zh-cn': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				'zh-hk': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				en: 'Leave blank to keep the saved key.'
			});
			return hint ? this.copy.apiKeyHintPrefix + hint + '：' + fallback : fallback;
		},
		compactSttApiKeyTipText() {
			const hint = this.currentSttProviderOption.apiKeyHint;
			if (this.form.clearStoredSttKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			const fallback = pickLocaleText(this.localeCode, {
				'zh-cn': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				'zh-hk': '留空可繼續使用已保存的 Key，填寫新 Key 會覆蓋。',
				en: 'Leave blank to keep the saved key.'
			});
			return hint ? this.copy.apiKeyHintPrefix + hint + '，' + fallback : fallback;
		},
		compactImageApiKeyTipText() {
			const hint = this.currentImageProviderOption.apiKeyHint;
			if (this.form.clearStoredImageKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			const fallback = pickLocaleText(this.localeCode, {
				'zh-cn': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				'zh-hk': '留空可继续使用已保存的 Key，填写新 Key 会覆盖。',
				en: 'Leave blank to keep the saved key.'
			});
			return hint ? this.copy.apiKeyHintPrefix + hint + '：' + fallback : fallback;
		},
		apiKeyTipText() {
			const hint = this.currentProviderOption.apiKeyHint;
			if (this.form.clearStoredKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			return hint ? this.copy.apiKeyHintPrefix + hint + '：' + this.copy.apiKeyTip : this.copy.apiKeyTip;
		},
		ttsApiKeyTipText() {
			const hint = this.currentTtsProviderOption.apiKeyHint;
			if (this.form.clearStoredTtsKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			return hint ? (this.copy.apiKeyHintPrefix + hint + '：' + this.copy.apiKeyTip) : this.copy.apiKeyTip;
		},
		imageApiKeyTipText() {
			const hint = this.currentImageProviderOption.apiKeyHint;
			if (this.form.clearStoredImageKey) {
				return this.copy.clearStoredKeyTip || '保存后将清除已保存的 Key。';
			}
			return hint ? (this.copy.apiKeyHintPrefix + hint + '：' + this.copy.apiKeyTip) : this.copy.apiKeyTip;
		},
		ttsProviderSectionTitleText() {
			return this.copy.ttsProviderSectionTitle || 'TTS 平台';
		},
		ttsProviderSectionDescText() {
			return this.form.ttsUseSeparateConfig
				? (this.copy.ttsProviderSectionDescSeparate || 'TTS 可以使用独立的平台、API Key 和接口地址。')
				: (this.copy.ttsProviderSectionDescShared || 'TTS 目前跟随主聊天配置。');
		},
		ttsFollowMainText() {
			return this.copy.ttsFollowMain || '跟随主配置';
		},
		ttsFollowMainDescText() {
			return this.copy.ttsFollowMainDesc || '复用主聊天的平台和 Key。';
		},
		ttsSeparateText() {
			return this.copy.ttsSeparate || '独立 TTS 配置';
		},
		ttsSeparateDescText() {
			return this.copy.ttsSeparateDesc || '使用专用 TTS 平台和 Key。';
		},
		ttsProviderLabelText() {
			return this.copy.ttsProviderSource || 'TTS 平台';
		},
		ttsApiKeyLabelText() {
			return this.copy.ttsApiKey || 'TTS API Key';
		},
		sttProviderSectionTitleText() {
			return this.copy.sttProviderSectionTitle || 'STT 平台';
		},
		sttFollowMainText() {
			return this.copy.sttFollowMain || '跟随主配置';
		},
		sttFollowMainDescText() {
			return this.copy.sttFollowMainDesc || '复用主聊天的平台和 Key。';
		},
		sttSeparateText() {
			return this.copy.sttSeparate || '独立 STT 配置';
		},
		sttSeparateDescText() {
			return this.copy.sttSeparateDesc || '使用专用 STT 平台和 Key。';
		},
		sttProviderLabelText() {
			return this.copy.sttProviderSource || 'STT 平台';
		},
		sttApiKeyLabelText() {
			return this.copy.sttApiKey || 'STT API Key';
		},
		imageProviderSectionTitleText() {
			return this.copy.imageProviderSectionTitle || '生图平台';
		},
		imageProviderSectionDescText() {
			return this.form.imageUseSeparateConfig
				? (this.copy.imageProviderSectionDescSeparate || '生图可以使用独立的平台、API Key 和接口地址。')
				: (this.copy.imageProviderSectionDescShared || '生图目前跟随主聊天配置。');
		},
		imageFollowMainText() {
			return this.copy.imageFollowMain || '跟随主配置';
		},
		imageFollowMainDescText() {
			return this.copy.imageFollowMainDesc || '复用主聊天的平台和 Key。';
		},
		imageSeparateText() {
			return this.copy.imageSeparate || '独立生图配置';
		},
		imageSeparateDescText() {
			return this.copy.imageSeparateDesc || '使用专用生图平台和 Key。';
		},
		imageProviderLabelText() {
			return this.copy.imageProviderSource || '生图平台';
		},
		imageApiKeyLabelText() {
			return this.copy.imageApiKey || '生图 API Key';
		},
		imageConsistencySectionTitleText() {
			return '生图策略';
		},
		imageConsistencyCurrentText() {
			const mode = this.normalizeImageCharacterConsistencyMode(this.form.imageCharacterConsistencyMode);
			if (mode === 'free') {
				return '当前：自由';
			}
			if (mode === 'strong') {
				return '当前：强一致';
			}
			return '当前：平衡';
		},
		imageConsistencySectionDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u9009\u62e9\u89d2\u8272\u4e00\u81f4\u6027\u7b56\u7565\uff0c\u5728\u81ea\u7531\u5ea6\u548c\u540c\u4e00\u89d2\u8272\u611f\u4e4b\u95f4\u53d6\u5e73\u8861\u3002',
				'zh-hk': '\u9078\u64c7\u89d2\u8272\u4e00\u81f4\u6027\u7b56\u7565\uff0c\u5728\u81ea\u7531\u5ea6\u8207\u540c\u4e00\u89d2\u8272\u611f\u4e4b\u9593\u53d6\u5e73\u8861\u3002',
				en: 'Choose how strongly image generation should preserve the same character across turns.'
			});
		},
		imageConsistencyFreeText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u81ea\u7531',
				'zh-hk': '\u81ea\u7531',
				en: 'Free'
			});
		},
		imageConsistencyFreeDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u4f18\u5148\u9075\u5faa\u5f53\u524d\u63d0\u793a\u8bcd\uff0c\u89d2\u8272\u5916\u89c2\u53d8\u5316\u66f4\u81ea\u7531\u3002',
				'zh-hk': '\u512a\u5148\u9075\u5faa\u76ee\u524d\u63d0\u793a\u8a5e\uff0c\u89d2\u8272\u5916\u89c0\u8b8a\u5316\u66f4\u81ea\u7531\u3002',
				en: 'Follow the current prompt first with the loosest character lock.'
			});
		},
		imageConsistencyBalancedText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u5e73\u8861',
				'zh-hk': '\u5e73\u8861',
				en: 'Balanced'
			});
		},
		imageConsistencyBalancedDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u5c3d\u91cf\u4fdd\u7559\u540c\u4e00\u4e2a\u89d2\u8272\uff0c\u540c\u65f6\u5141\u8bb8\u670d\u88c5\u3001\u59ff\u52bf\u548c\u573a\u666f\u81ea\u7136\u53d8\u5316\u3002',
				'zh-hk': '\u76e1\u91cf\u4fdd\u7559\u540c\u4e00\u500b\u89d2\u8272\uff0c\u540c\u6642\u5141\u8a31\u670d\u88dd\u3001\u59ff\u614b\u548c\u5834\u666f\u81ea\u7136\u8b8a\u5316\u3002',
				en: 'Keep the same character while allowing natural changes in outfit, pose, and scene.'
			});
		},
		imageConsistencyStrongText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u5f3a\u4e00\u81f4',
				'zh-hk': '\u5f37\u4e00\u81f4',
				en: 'Strong'
			});
		},
		imageConsistencyStrongDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u4f18\u5148\u9501\u5b9a\u89d2\u8272\u5916\u89c2\uff0c\u80fd\u7528\u53c2\u8003\u56fe\u65f6\u4f1a\u5c3d\u91cf\u4f7f\u7528\u53c2\u8003\u56fe\u3002',
				'zh-hk': '\u512a\u5148\u9396\u5b9a\u89d2\u8272\u5916\u89c0\uff0c\u53ef\u7528\u53c3\u8003\u5716\u6642\u6703\u76e1\u91cf\u4f7f\u7528\u53c3\u8003\u5716\u3002',
				en: 'Prioritize the same character appearance and use reference images whenever available.'
			});
		},
		imageConsistencyActiveDescText() {
			const mode = this.normalizeImageCharacterConsistencyMode(this.form.imageCharacterConsistencyMode);
			if (mode === 'free') {
				return this.imageConsistencyFreeDescText;
			}
			if (mode === 'strong') {
				return this.imageConsistencyStrongDescText;
			}
			return this.imageConsistencyBalancedDescText;
		},
		imageReferenceSourceLabelText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u53c2\u8003\u56fe\u6765\u6e90',
				'zh-hk': '\u53c3\u8003\u5716\u4f86\u6e90',
				en: 'Reference Source'
			});
		},
		imageReferenceLatestText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u6700\u8fd1\u751f\u56fe\u4f18\u5148',
				'zh-hk': '\u6700\u8fd1\u751f\u6210\u5716\u7247\u512a\u5148',
				en: 'Latest image first'
			});
		},
		imageReferenceLatestDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u4f18\u5148\u4f7f\u7528\u6700\u8fd1\u4e00\u6b21\u6ee1\u610f\u7684\u89d2\u8272\u56fe\uff0c\u5e2e\u52a9\u89d2\u8272\u5f62\u8c61\u4fdd\u6301\u4e00\u81f4\u3002',
				'zh-hk': '\u512a\u5148\u4f7f\u7528\u6700\u8fd1\u4e00\u6b21\u6eff\u610f\u7684\u89d2\u8272\u5716\uff0c\u5e6b\u52a9\u89d2\u8272\u5f62\u8c61\u4fdd\u6301\u4e00\u81f4\u3002',
				en: 'Prefer the latest approved role image to keep character identity stable.'
			});
		},
		imageReferenceAvatarText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u53ea\u7528\u89d2\u8272\u5361\u539f\u56fe',
				'zh-hk': '\u53ea\u7528\u89d2\u8272\u5361\u539f\u5716',
				en: 'Avatar only'
			});
		},
		imageReferenceAvatarDescText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u59cb\u7ec8\u4f7f\u7528\u89d2\u8272\u5361\u539f\u56fe\u505a\u53c2\u8003\uff0c\u9002\u5408\u5e0c\u671b\u89d2\u8272\u5916\u89c2\u66f4\u7a33\u5b9a\u7684\u573a\u666f\u3002',
				'zh-hk': '\u59cb\u7d42\u4f7f\u7528\u89d2\u8272\u5361\u539f\u5716\u505a\u53c3\u8003\uff0c\u9069\u5408\u5e0c\u671b\u89d2\u8272\u5916\u89c0\u66f4\u7a69\u5b9a\u7684\u5834\u666f\u3002',
				en: 'Always use the original card image when you want the strongest visual consistency.'
			});
		},
		imageReferenceSourceActiveDescText() {
			const mode = this.normalizeImageReferenceSourceMode(this.form.imageReferenceSourceMode);
			return mode === 'avatar_only' ? this.imageReferenceAvatarDescText : this.imageReferenceLatestDescText;
		},
		imageReferenceSourceTipText() {
			return pickLocaleText(this.localeCode, {
				'zh-cn': '\u5982\u679c\u5f53\u524d\u6a21\u578b\u4e0d\u652f\u6301\u53c2\u8003\u56fe\uff0c\u804a\u5929\u9875\u4f1a\u81ea\u52a8\u964d\u7ea7\u4e3a\u5f31\u4e00\u81f4\u6027\u5e76\u63d0\u793a\u4f60\u3002',
				'zh-hk': '\u5982\u679c\u76ee\u524d\u6a21\u578b\u4e0d\u652f\u63f4\u53c3\u8003\u5716\uff0c\u804a\u5929\u9801\u6703\u81ea\u52d5\u964d\u7d1a\u70ba\u5f31\u4e00\u81f4\u6027\u4e26\u63d0\u793a\u4f60\u3002',
				en: 'If the current model does not support image references, chat will fall back to weak consistency and show a notice.'
			});
		},
		imageReferenceSourceCurrentText() {
			const mode = this.normalizeImageReferenceSourceMode(this.form.imageReferenceSourceMode);
			return mode === 'avatar_only' ? '当前：角色卡原图' : '当前：最近生图优先';
		},
		heroSubtitleText() {
			if (this.form.mode === 'system') {
				return this.copy.heroSubtitle;
			}
			if (!this.viewState.enabledGlobal) {
				return this.viewState.denyReason || this.copy.heroSubtitle;
			}
			if (!this.viewState.canUse && this.viewState.denyReason) {
				return this.viewState.denyReason;
			}
			return this.copy.heroSubtitle;
		},
		apiKeyMaskText() {
			if (this.form.mode !== 'custom' || !this.savedKeyAppliesToCurrentProvider) {
				return '';
			}
			return this.form.clearStoredKey ? '将清除已保存 Key' : 'Key ' + this.viewState.apiKeyMask;
		},
		apiKeyPlaceholderText() {
			return this.effectiveSavedKeyAvailable ? this.viewState.apiKeyMask : 'sk-...';
		},
		clearSavedKeyText() {
			return this.form.clearStoredKey ? (this.copy.undoClearSavedKey || '撤销清除') : (this.copy.clearSavedKey || '清除已保存 Key');
		},
		sttApiKeyPlaceholderText() {
			return this.effectiveSavedSttKeyAvailable ? this.viewState.sttApiKeyMask : 'sk-...';
		},
		clearSavedSttKeyText() {
			return this.form.clearStoredSttKey ? (this.copy.undoClearSavedKey || '撤销清除') : (this.copy.clearSavedKey || '清除已保存 Key');
		},
		ttsApiKeyPlaceholderText() {
			return this.effectiveSavedTtsKeyAvailable ? this.viewState.ttsApiKeyMask : 'sk-...';
		},
		clearSavedTtsKeyText() {
			return this.form.clearStoredTtsKey ? (this.copy.undoClearSavedKey || '撤销清除') : (this.copy.clearSavedKey || '清除已保存 Key');
		},
		imageApiKeyPlaceholderText() {
			return this.effectiveSavedImageKeyAvailable ? this.viewState.imageApiKeyMask : 'sk-...';
		},
		clearSavedImageKeyText() {
			return this.form.clearStoredImageKey ? (this.copy.undoClearSavedKey || '撤销清除') : (this.copy.clearSavedKey || '清除已保存 Key');
		},
		denyReasonText() {
			return this.viewState.canUse ? '' : this.viewState.denyReason;
		},
		testStatusTitle() {
			if (!this.testState.message) {
				return '';
			}
			if (this.testState.ok) {
				return this.testState.latencyMs ? this.copy.testSuccessTitle + ' · ' + this.testState.latencyMs + 'ms' : this.copy.testSuccessTitle;
			}
			return this.copy.testFailTitle;
		},
		canRunCustomAction() {
			return this.form.mode === 'custom' && this.viewState.canUse;
		}
	},
	watch: {
		'form.mode': 'resetTestState',
		'form.providerSource'() {
			this.resetProviderModelState();
			this.scheduleAutoLoadModels('main');
		},
		'form.modelName': 'resetTestState',
		'form.apiKey'(value) {
			if (String(value || '').trim() && this.form.clearStoredKey) {
				this.form.clearStoredKey = false;
			}
			this.resetProviderModelState();
			this.scheduleAutoLoadModels('main');
		},
		'form.customUrl'() {
			this.resetProviderModelState();
			this.scheduleAutoLoadModels('main');
		},
		'form.sttUseSeparateConfig'(value) {
			if (value && !String(this.form.sttProviderSource || '').trim()) {
				this.form.sttProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.sttProviderSource === 'custom' && !String(this.form.sttCustomUrl || '').trim()) {
					this.form.sttCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
			this.resetSttProviderModelState();
		},
		'form.sttProviderSource'() {
			this.resetSttProviderModelState();
			this.scheduleAutoLoadModels('stt');
		},
		'form.sttApiKey'(value) {
			if (String(value || '').trim() && this.form.clearStoredSttKey) {
				this.form.clearStoredSttKey = false;
			}
			this.resetSttProviderModelState();
			this.scheduleAutoLoadModels('stt');
		},
		'form.sttCustomUrl'() {
			this.resetSttProviderModelState();
			this.scheduleAutoLoadModels('stt');
		},
		'form.ttsUseSeparateConfig'(value) {
			if (value && !String(this.form.ttsProviderSource || '').trim()) {
				this.form.ttsProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.ttsProviderSource === 'custom' && !String(this.form.ttsCustomUrl || '').trim()) {
					this.form.ttsCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
			this.resetTtsProviderModelState();
		},
		'form.ttsProviderSource'() {
			this.resetTtsProviderModelState();
			this.scheduleAutoLoadModels('tts');
		},
		'form.ttsApiKey'(value) {
			if (String(value || '').trim() && this.form.clearStoredTtsKey) {
				this.form.clearStoredTtsKey = false;
			}
			this.resetTtsProviderModelState();
			this.scheduleAutoLoadModels('tts');
		},
		'form.ttsCustomUrl'() {
			this.resetTtsProviderModelState();
			this.scheduleAutoLoadModels('tts');
		},
		'form.imageUseSeparateConfig'(value) {
			if (value && !String(this.form.imageProviderSource || '').trim()) {
				this.form.imageProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.imageProviderSource === 'custom' && !String(this.form.imageCustomUrl || '').trim()) {
					this.form.imageCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
			this.resetImageProviderModelState();
		},
		'form.imageProviderSource'() {
			this.resetImageProviderModelState();
			this.scheduleAutoLoadModels('image');
		},
		'form.imageApiKey'(value) {
			if (String(value || '').trim() && this.form.clearStoredImageKey) {
				this.form.clearStoredImageKey = false;
			}
			this.resetImageProviderModelState();
			this.scheduleAutoLoadModels('image');
		},
		'form.imageCustomUrl'() {
			this.resetImageProviderModelState();
			this.scheduleAutoLoadModels('image');
		},
		'form.imageModelName': 'resetTestState'
	},
	onLoad() {
		this.load();
	},
	onShow() {
		if (!this.hasLoadedConfig && !this.loadingConfig) {
			this.load();
		}
	},
	onBackPress() {
		if (this.hasUnsavedConfigChanges()) {
			this.confirmLeaveWithUnsavedChanges(() => this.navigateBackToUser());
			return true;
		}
		return false;
	},
	methods: {
		goBack() {
			this.confirmLeaveWithUnsavedChanges(() => this.navigateBackToUser());
		},
		navigateBackToUser() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		confirmLeaveWithUnsavedChanges(next) {
			if (!this.hasUnsavedConfigChanges()) {
				next();
				return;
			}
			uni.showModal({
				title: '配置还没保存',
				content: '离开后，本次填写的模型、地址和 API Key 会丢失。确定离开吗？',
				cancelText: '继续编辑',
				confirmText: '离开',
				success: (res) => {
					if (res && res.confirm) {
						next();
					}
				}
			});
		},
		hasUnsavedConfigChanges() {
			return this.hasLoadedConfig && this.cleanFormSignature !== this.currentFormSignature();
		},
		markConfigClean() {
			this.cleanFormSignature = this.currentFormSignature();
		},
		currentFormSignature() {
			return JSON.stringify({
				mode: this.form.mode,
				providerSource: this.form.providerSource,
				modelName: String(this.form.modelName || '').trim(),
				visionModelName: String(this.form.visionModelName || '').trim(),
				sttModelName: String(this.form.sttModelName || '').trim(),
				sttUseSeparateConfig: this.form.sttUseSeparateConfig === true,
				sttProviderSource: String(this.form.sttProviderSource || '').trim(),
				sttApiKey: String(this.form.sttApiKey || '').trim(),
				sttCustomUrl: String(this.form.sttCustomUrl || '').trim(),
				clearStoredSttKey: this.form.clearStoredSttKey === true,
				ttsModelName: String(this.form.ttsModelName || '').trim(),
				ttsVoiceName: String(this.form.ttsVoiceName || '').trim(),
				ttsVoiceTemplateCode: String(this.form.ttsVoiceTemplateCode || '').trim(),
				ttsUseSeparateConfig: this.form.ttsUseSeparateConfig === true,
				ttsProviderSource: String(this.form.ttsProviderSource || '').trim(),
				ttsApiKey: String(this.form.ttsApiKey || '').trim(),
				ttsCustomUrl: String(this.form.ttsCustomUrl || '').trim(),
				clearStoredTtsKey: this.form.clearStoredTtsKey === true,
				imageModelName: String(this.form.imageModelName || '').trim(),
				imageCharacterConsistencyMode: 'free',
				imageReferenceSourceMode: 'latest_generated_first',
				imageUseSeparateConfig: this.form.imageUseSeparateConfig === true,
				imageProviderSource: String(this.form.imageProviderSource || '').trim(),
				imageApiKey: String(this.form.imageApiKey || '').trim(),
				imageCustomUrl: String(this.form.imageCustomUrl || '').trim(),
				clearStoredImageKey: this.form.clearStoredImageKey === true,
				apiKey: String(this.form.apiKey || '').trim(),
				customUrl: String(this.form.customUrl || '').trim(),
				clearStoredKey: this.form.clearStoredKey === true
			});
		},
		applyProviderStateToForm(data) {
			const state = Object.assign(emptyState(), data || {});
			this.viewState = state;
			this.resetPresetExpandState();
			this.form.mode = state.mode || 'system';
			this.form.providerSource = this.getProviderOption(state.providerSource) ? state.providerSource : this.sourceOptions[0].value;
			this.form.modelName = state.modelName || '';
			this.form.visionModelName = state.visionModelName || '';
			this.form.sttModelName = state.sttModelName || '';
			this.form.sttUseSeparateConfig = state.sttUseSeparateConfig === true;
			this.form.sttProviderSource = state.sttProviderSource || '';
			this.form.sttApiKey = '';
			this.form.sttCustomUrl = state.sttCustomUrl || '';
			this.form.clearStoredSttKey = false;
			this.form.ttsModelName = state.ttsModelName || '';
			this.form.ttsVoiceName = state.ttsVoiceName || '';
			this.form.ttsVoiceTemplateCode = state.ttsVoiceTemplateCode || '';
			this.form.ttsUseSeparateConfig = state.ttsUseSeparateConfig === true;
			this.form.ttsProviderSource = state.ttsProviderSource || '';
			this.form.ttsApiKey = '';
			this.form.ttsCustomUrl = state.ttsCustomUrl || '';
			this.form.clearStoredTtsKey = false;
			this.form.imageUseSeparateConfig = state.imageUseSeparateConfig === true;
			this.form.imageProviderSource = state.imageProviderSource || '';
			this.form.imageApiKey = '';
			this.form.imageCustomUrl = state.imageCustomUrl || '';
			this.form.clearStoredImageKey = false;
			this.form.imageModelName = state.imageModelName || '';
			this.form.imageCharacterConsistencyMode = 'free';
			this.form.imageReferenceSourceMode = 'latest_generated_first';
			this.form.customUrl = state.customUrl || '';
			this.form.apiKey = '';
			this.form.clearStoredKey = false;
			if (!this.form.modelName) {
				this.applyProviderDefaults(this.form.providerSource, null);
			}
			this.resetTestState();
			this.providerModels = [];
			this.providerModelItems = [];
			this.modelListMessage = '';
			this.sttProviderModels = [];
			this.sttProviderModelItems = [];
			this.sttModelListMessage = '';
			this.ttsProviderModels = [];
			this.ttsProviderModelItems = [];
			this.ttsModelListMessage = '';
			this.imageProviderModels = [];
			this.imageProviderModelItems = [];
			this.imageModelListMessage = '';
			this.hasLoadedConfig = true;
			this.markConfigClean();
			this.$nextTick(() => {
				if (this.form.mode === 'custom' && this.canLoadModels) {
					this.loadProviderModels(false);
				}
				if (this.form.mode === 'custom' && this.showVoiceConfig && this.form.sttUseSeparateConfig && this.canLoadSttModels) {
					this.loadSttProviderModels(false);
				}
				if (this.form.mode === 'custom' && this.showVoiceConfig && this.form.ttsUseSeparateConfig && this.canLoadTtsModels) {
					this.loadTtsProviderModels(false);
				}
				if (this.form.mode === 'custom' && this.showImageConfig && this.form.imageUseSeparateConfig && this.canLoadImageModels) {
					this.loadImageProviderModels(false);
				}
			});
		},
		clearAutoLoadTimer(scope) {
			const key = scope === 'stt'
				? 'autoLoadSttModelsTimer'
				: (scope === 'tts'
					? 'autoLoadTtsModelsTimer'
					: (scope === 'image' ? 'autoLoadImageModelsTimer' : 'autoLoadMainModelsTimer'));
			if (this[key]) {
				clearTimeout(this[key]);
				this[key] = null;
			}
		},
		scheduleAutoLoadModels(scope) {
			const safeScope = scope === 'stt' || scope === 'tts' || scope === 'image' ? scope : 'main';
			this.clearAutoLoadTimer(safeScope);
			if (this.form.mode !== 'custom' || !this.hasLoadedConfig) {
				return;
			}
			const canLoad = safeScope === 'stt'
				? this.canLoadSttModels
				: (safeScope === 'tts'
					? this.canLoadTtsModels
					: (safeScope === 'image' ? this.canLoadImageModels : this.canLoadModels));
			if (!canLoad) {
				return;
			}
			const key = safeScope === 'stt'
				? 'autoLoadSttModelsTimer'
				: (safeScope === 'tts'
					? 'autoLoadTtsModelsTimer'
					: (safeScope === 'image' ? 'autoLoadImageModelsTimer' : 'autoLoadMainModelsTimer'));
			this[key] = setTimeout(() => {
				this[key] = null;
				if (safeScope === 'stt') {
					this.loadSttProviderModels(false);
					return;
				}
				if (safeScope === 'tts') {
					this.loadTtsProviderModels(false);
					return;
				}
				if (safeScope === 'image') {
					this.loadImageProviderModels(false);
					return;
				}
				this.loadProviderModels(false);
			}, MODEL_AUTOLOAD_DEBOUNCE_MS);
		},
		getProviderOption(value) {
			return this.sourceOptions.find((item) => item.value === value) || null;
		},
		presetLimit(section) {
			return PRESET_COLLAPSE_LIMITS[section] || 4;
		},
		isPresetExpanded(section) {
			return !!(section && this.presetExpanded && this.presetExpanded[section]);
		},
		resetPresetExpandState() {
			this.presetExpanded = {};
		},
		togglePresetExpand(section) {
			if (!section) return;
			this.$set(this.presetExpanded, section, !this.isPresetExpanded(section));
		},
		visiblePresetItems(section, list, activeValue) {
			const source = Array.isArray(list) ? list.filter(Boolean).map(String) : [];
			const limit = this.presetLimit(section);
			if (source.length <= limit || this.isPresetExpanded(section)) {
				return source;
			}
			const collapsed = source.slice(0, limit);
			const active = String(activeValue || '').trim();
			if (active && source.indexOf(active) >= 0 && collapsed.indexOf(active) < 0) {
				const next = collapsed.slice(0, Math.max(0, limit - 1));
				next.push(active);
				return uniqueStrings(next);
			}
			return collapsed;
		},
		presetToggleText(section, list, activeValue) {
			const source = Array.isArray(list) ? list : [];
			if (this.isPresetExpanded(section)) {
				return '收起';
			}
			const hiddenCount = Math.max(0, source.length - this.visiblePresetItems(section, list, activeValue).length);
			return hiddenCount > 0 ? '展开 ' + hiddenCount + ' 个' : '收起';
		},
		load() {
			if (this.loadingConfig) {
				return;
			}
			if (this.hasUnsavedConfigChanges()) {
				return;
			}
			this.loadingConfig = true;
			const clientUid = tavernApi.getClientUid();
			tavernApi.getTavernUserAiProvider(clientUid).then((data) => {
				this.applyProviderStateToForm(data);
			}).catch((err) => {
				uni.showToast({
					title: (err && err.message) || '加载失败',
					icon: 'none'
				});
			}).finally(() => {
				this.loadingConfig = false;
			});
		},
		selectSystemMode() {
			this.form.mode = 'system';
		},
		enableCustomMode() {
			if (!this.viewState.canUse) {
				uni.showToast({
					title: this.viewState.denyReason || this.copy.unavailableTitle,
					icon: 'none'
				});
				return;
			}
			this.form.mode = 'custom';
			if (!String(this.form.modelName || '').trim()) {
				this.applyProviderDefaults(this.form.providerSource, null);
			}
			this.$nextTick(() => {
				if (this.canLoadModels && !this.providerModels.length) {
					this.loadProviderModels(false);
				}
				if (this.showVoiceConfig && this.form.ttsUseSeparateConfig && this.canLoadTtsModels && !this.ttsProviderModels.length) {
					this.loadTtsProviderModels(false);
				}
				if (this.showImageConfig && this.form.imageUseSeparateConfig && this.canLoadImageModels && !this.imageProviderModels.length) {
					this.loadImageProviderModels(false);
				}
			});
		},
		onSourceChange(event) {
			if (this.form.mode !== 'custom') {
				return;
			}
			const previousOption = this.currentProviderOption;
			const index = Number(event && event.detail ? event.detail.value : 0);
			const item = this.sourceOptions[index];
			if (item) {
				this.form.providerSource = item.value;
				this.applyProviderDefaults(item.value, previousOption);
				this.$nextTick(() => {
					if (this.canLoadModels) {
						this.loadProviderModels(false);
					}
				});
			}
		},
		applyProviderDefaults(providerSource, previousOption) {
			const option = this.getProviderOption(providerSource);
			if (!option) {
				return;
			}
			this.resetPresetExpandState();
			const currentModel = String(this.form.modelName || '').trim();
			const previousPresets = previousOption && Array.isArray(previousOption.modelPresets) ? previousOption.modelPresets : [];
			const wasPreviousDefault = previousOption && currentModel && currentModel === String(previousOption.defaultModel || '').trim();
			if (!currentModel || wasPreviousDefault || previousPresets.indexOf(currentModel) >= 0) {
				this.form.modelName = option.defaultModel || (option.modelPresets && option.modelPresets[0]) || '';
			}
			if (previousOption && previousOption.value !== providerSource) {
				this.form.visionModelName = '';
				if (this.showVoiceConfig) {
					this.form.sttModelName = '';
				}
				if (this.showVoiceConfig && !this.form.ttsUseSeparateConfig) {
					this.form.ttsModelName = '';
					this.form.ttsVoiceName = '';
				}
				if (this.showImageConfig) {
					this.form.imageModelName = '';
				}
			}
			if (providerSource !== 'custom') {
				this.form.customUrl = '';
			}
		},
		selectModel(model) {
			if (this.form.mode !== 'custom') {
				return;
			}
			this.form.modelName = model;
		},
		selectCapabilityModel(fieldName, model) {
			if (this.form.mode !== 'custom' || !fieldName) {
				return;
			}
			this.form[fieldName] = model;
		},
		ttsVoiceTemplateAssetUrl(url) {
			return tavernApi.resolveJgAssetUrl(url);
		},
		selectTtsVoiceTemplate(item) {
			if (!this.showVoiceConfig || this.form.mode !== 'custom' || !item || !item.code) {
				return;
			}
			this.form.ttsVoiceTemplateCode = item.code;
			this.form.ttsVoiceName = '';
			if (!String(this.form.ttsModelName || '').trim() && String(item.recommendedModelName || '').trim()) {
				this.form.ttsModelName = String(item.recommendedModelName || '').trim();
			}
		},
		clearTtsVoiceTemplateSelection() {
			this.form.ttsVoiceTemplateCode = '';
		},
		supportsOpenAiVoicePresets(modelName) {
			const text = String(modelName || '').toLowerCase();
			if (!text) {
				return false;
			}
			return /(gpt-4o-mini-tts|tts-1|tts-1-hd|\/tts|openai\/.*tts)/.test(text);
		},
		supportsSiliconFlowVoicePresets(modelName) {
			const text = String(modelName || '').toLowerCase();
			if (!text) {
				return false;
			}
			return /(cosyvoice|fish-speech|gpt-sovits)/.test(text);
		},
		isKnownOpenAiVoicePreset(voiceName) {
			const text = String(voiceName || '').trim().toLowerCase();
			return !!text && OPENAI_TTS_VOICE_PRESETS.indexOf(text) >= 0;
		},
		matchCapabilityModel(model, capability) {
			const text = String(model || '').toLowerCase();
			if (!text) {
				return false;
			}
			const noisyKeywords = /(embedding|reranker|ranker|thinking|reasoner|instruct|chat|captioner|coder)/;
			const imageEditKeywords = /(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|edit-only|paint-by-example|kontext)/;
			if ((capability === 'stt' || capability === 'tts') && noisyKeywords.test(text)) {
				return false;
			}
			if (capability === 'text') {
				if (/(embedding|reranker|ranker|moderation|omni-moderation)/.test(text)) {
					return false;
				}
				return !this.matchCapabilityModel(text, 'vision')
					&& !this.matchCapabilityModel(text, 'stt')
					&& !this.matchCapabilityModel(text, 'tts')
					&& !this.matchCapabilityModel(text, 'image');
			}
			if (capability === 'vision') {
				if (/(embedding|reranker|ranker)/.test(text)) {
					return false;
				}
				return /(vl|vision|multimodal|image-to-text|vision-language)/.test(text);
			}
			if (capability === 'stt') {
				return /(asr|stt|transcribe|transcription|speech2text|speech-to-text|speechrecognition|speech-recognition|whisper|sensevoice|paraformer)/.test(text);
			}
			if (capability === 'tts') {
				if (/(asr|stt|transcribe|transcription|speech2text|speech-to-text|speechrecognition|speech-recognition|whisper|sensevoice|paraformer|funasr)/.test(text)) {
					return false;
				}
				return /(tts|text-to-speech|speech-synthesis|speechgeneration|speech-generation|cosyvoice|fish-speech|indextts|ttsd|voice-tts|voice_synth|voice-synth)/.test(text);
			}
			if (capability === 'image') {
				return imageEditKeywords.test(text)
					|| /(flux|sdxl|stable[-_]?diffusion|dall[-_]?e|kolors|wanx|gpt-image|imagen|recraft|seedream|janus|text-to-image|text2image|t2i|image-generation|imagegeneration|generative-image)/.test(text);
			}
			return false;
		},
		filterModelsByCapability(capability) {
			const seen = Object.create(null);
			return this.modelPresets.filter((model) => {
				const text = String(model || '').trim();
				if (!text || seen[text]) {
					return false;
				}
				if (!this.matchCapabilityModel(text, capability)) {
					return false;
				}
				seen[text] = true;
				return true;
			});
		},
		filterModelListByCapability(list, capability) {
			const source = Array.isArray(list) ? list : [];
			const seen = Object.create(null);
			return source.filter((model) => {
				const text = String(model || '').trim();
				if (!text || seen[text]) {
					return false;
				}
				if (!this.matchCapabilityModel(text, capability)) {
					return false;
				}
				seen[text] = true;
				return true;
			});
		},
		normalizeModelCatalogItems(list) {
			const source = Array.isArray(list) ? list : [];
			const seen = Object.create(null);
			return source.map((item) => {
				if (!item || typeof item !== 'object') {
					return null;
				}
				const id = String(item.id || item.name || '').trim();
				if (!id || seen[id]) {
					return null;
				}
				seen[id] = true;
				return {
					id,
					name: String(item.name || '').trim(),
					inputModalities: uniqueStrings(item.inputModalities || item.input_modalities || []),
					outputModalities: uniqueStrings(item.outputModalities || item.output_modalities || []),
					capabilityHints: uniqueStrings(item.capabilityHints || item.capability_hints || [])
				};
			}).filter(Boolean);
		},
		modelCatalogSupportsCapability(item, capability) {
			if (!item || typeof item !== 'object') {
				return false;
			}
			const id = String(item.id || item.name || '').trim();
			const hints = uniqueStrings(item.capabilityHints || item.capability_hints || []).map((value) => String(value || '').trim().toLowerCase());
			if (hints.indexOf(String(capability || '').trim().toLowerCase()) >= 0) {
				return true;
			}
			const input = uniqueStrings(item.inputModalities || item.input_modalities || []).map((value) => String(value || '').trim().toLowerCase());
			const output = uniqueStrings(item.outputModalities || item.output_modalities || []).map((value) => String(value || '').trim().toLowerCase());
			if (capability === 'text' && input.indexOf('text') >= 0 && output.indexOf('text') >= 0) {
				return true;
			}
			if (capability === 'image' && output.indexOf('image') >= 0) {
				return true;
			}
			if (capability === 'vision' && input.indexOf('image') >= 0 && output.indexOf('text') >= 0) {
				return true;
			}
			if (capability === 'stt' && (input.indexOf('audio') >= 0 || input.indexOf('speech') >= 0) && output.indexOf('text') >= 0) {
				return true;
			}
			if (capability === 'tts' && input.indexOf('text') >= 0 && (output.indexOf('audio') >= 0 || output.indexOf('speech') >= 0)) {
				return true;
			}
			return this.matchCapabilityModel(id, capability);
		},
		filterModelCatalogItemsByCapability(list, capability) {
			const source = Array.isArray(list) ? list : [];
			const seen = Object.create(null);
			return source.map((item) => {
				if (!this.modelCatalogSupportsCapability(item, capability)) {
					return '';
				}
				return String(item && (item.id || item.name) || '').trim();
			}).filter((id) => {
				if (!id || seen[id]) {
					return false;
				}
				seen[id] = true;
				return true;
			});
		},
		resolveCapabilityDefaultModel(capability, option, dynamicModels) {
			const optionPresets = option && Array.isArray(option.modelPresets) ? option.modelPresets : [];
			const candidates = this.filterModelListByCapability(
				uniqueStrings([]
					.concat(Array.isArray(dynamicModels) ? dynamicModels : [])
					.concat(optionPresets)
				),
				capability
			);
			if (candidates.length) {
				return candidates[0];
			}
			const fallback = String(option && option.defaultModel ? option.defaultModel : '').trim();
			return fallback && this.matchCapabilityModel(fallback, capability) ? fallback : '';
		},
		setTtsSeparateConfig(enabled) {
			if (!this.showVoiceConfig) {
				return;
			}
			const next = enabled === true;
			this.form.ttsUseSeparateConfig = next;
			if (next && !String(this.form.ttsProviderSource || '').trim()) {
				this.form.ttsProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.ttsProviderSource === 'custom' && !String(this.form.ttsCustomUrl || '').trim()) {
					this.form.ttsCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
		},
		setSttSeparateConfig(enabled) {
			if (!this.showVoiceConfig) {
				return;
			}
			const next = enabled === true;
			this.form.sttUseSeparateConfig = next;
			if (next && !String(this.form.sttProviderSource || '').trim()) {
				this.form.sttProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.sttProviderSource === 'custom' && !String(this.form.sttCustomUrl || '').trim()) {
					this.form.sttCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
		},
		onSttSourceChange(event) {
			if (!this.showVoiceConfig || this.form.mode !== 'custom' || !this.form.sttUseSeparateConfig) {
				return;
			}
			const previousOption = this.currentSttProviderOption;
			const index = Number(event && event.detail ? event.detail.value : 0);
			const item = this.sourceOptions[index];
			if (item) {
				this.form.sttProviderSource = item.value;
				this.applySttProviderDefaults(item.value, previousOption);
				this.$nextTick(() => {
					if (this.canLoadSttModels) {
						this.loadSttProviderModels(false);
					}
				});
			}
		},
		applySttProviderDefaults(providerSource, previousOption) {
			const option = this.getProviderOption(providerSource);
			if (!option) {
				return;
			}
			this.resetPresetExpandState();
			const currentModel = String(this.form.sttModelName || '').trim();
			const previousPresets = this.filterModelListByCapability(
				previousOption && Array.isArray(previousOption.modelPresets) ? previousOption.modelPresets : [],
				'stt'
			);
			const wasPreviousDefault = previousOption && currentModel && currentModel === String(previousOption.defaultModel || '').trim();
			const nextDefault = this.resolveCapabilityDefaultModel('stt', option, []);
			if (!currentModel || wasPreviousDefault || previousPresets.indexOf(currentModel) >= 0 || !this.matchCapabilityModel(currentModel, 'stt')) {
				this.form.sttModelName = nextDefault;
			}
			if (providerSource !== 'custom') {
				this.form.sttCustomUrl = '';
			}
		},
		onTtsSourceChange(event) {
			if (!this.showVoiceConfig || this.form.mode !== 'custom' || !this.form.ttsUseSeparateConfig) {
				return;
			}
			const previousOption = this.currentTtsProviderOption;
			const index = Number(event && event.detail ? event.detail.value : 0);
			const item = this.sourceOptions[index];
			if (item) {
				this.form.ttsProviderSource = item.value;
				this.applyTtsProviderDefaults(item.value, previousOption);
				this.$nextTick(() => {
					if (this.canLoadTtsModels) {
						this.loadTtsProviderModels(false);
					}
				});
			}
		},
		applyTtsProviderDefaults(providerSource, previousOption) {
			const option = this.getProviderOption(providerSource);
			if (!option) {
				return;
			}
			this.resetPresetExpandState();
			const currentModel = String(this.form.ttsModelName || '').trim();
			const previousPresets = previousOption && Array.isArray(previousOption.modelPresets) ? previousOption.modelPresets : [];
			const wasPreviousDefault = previousOption && currentModel && currentModel === String(previousOption.defaultModel || '').trim();
			if (!currentModel || wasPreviousDefault || previousPresets.indexOf(currentModel) >= 0) {
				this.form.ttsModelName = option.defaultModel || (option.modelPresets && option.modelPresets[0]) || '';
			}
			if (previousOption && previousOption.value !== providerSource) {
				this.form.ttsVoiceName = '';
			}
			if (providerSource !== 'custom') {
				this.form.ttsCustomUrl = '';
			}
		},
		setImageSeparateConfig(enabled) {
			if (!this.showImageConfig) {
				return;
			}
			const next = enabled === true;
			this.form.imageUseSeparateConfig = next;
			if (next && !String(this.form.imageProviderSource || '').trim()) {
				this.form.imageProviderSource = this.form.providerSource || (this.sourceOptions[0] && this.sourceOptions[0].value) || 'siliconflow';
				if (this.form.imageProviderSource === 'custom' && !String(this.form.imageCustomUrl || '').trim()) {
					this.form.imageCustomUrl = String(this.form.customUrl || '').trim();
				}
			}
		},
		normalizeImageCharacterConsistencyMode(value) {
			const safe = String(value || '').trim().toLowerCase();
			if (safe === 'free') return 'free';
			if (safe === 'strong' || safe === 'reference_only') return 'strong';
			return 'balanced';
		},
		normalizeImageReferenceSourceMode(value) {
			const safe = String(value || '').trim().toLowerCase();
			if (safe === 'avatar_only') return 'avatar_only';
			return 'latest_generated_first';
		},
		setImageCharacterConsistencyMode(mode) {
			if (!this.showImageConfig) {
				return;
			}
			this.form.imageCharacterConsistencyMode = this.normalizeImageCharacterConsistencyMode(mode);
		},
		setImageReferenceSourceMode(mode) {
			if (!this.showImageConfig) {
				return;
			}
			this.form.imageReferenceSourceMode = this.normalizeImageReferenceSourceMode(mode);
		},
		onImageSourceChange(event) {
			if (!this.showImageConfig || this.form.mode !== 'custom' || !this.form.imageUseSeparateConfig) {
				return;
			}
			const previousOption = this.currentImageProviderOption;
			const index = Number(event && event.detail ? event.detail.value : 0);
			const item = this.sourceOptions[index];
			if (item) {
				this.form.imageProviderSource = item.value;
				this.applyImageProviderDefaults(item.value, previousOption);
				this.$nextTick(() => {
					if (this.canLoadImageModels) {
						this.loadImageProviderModels(false);
					}
				});
			}
		},
		applyImageProviderDefaults(providerSource, previousOption) {
			const option = this.getProviderOption(providerSource);
			if (!option) {
				return;
			}
			this.resetPresetExpandState();
			const currentModel = String(this.form.imageModelName || '').trim();
			const previousPresets = this.filterModelListByCapability(
				previousOption && Array.isArray(previousOption.modelPresets) ? previousOption.modelPresets : [],
				'image'
			);
			const wasPreviousDefault = previousOption && currentModel && currentModel === String(previousOption.defaultModel || '').trim();
			const nextDefault = this.resolveCapabilityDefaultModel('image', option, []);
			if (!currentModel || wasPreviousDefault || previousPresets.indexOf(currentModel) >= 0 || !this.matchCapabilityModel(currentModel, 'image')) {
				this.form.imageModelName = nextDefault;
			}
			if (providerSource !== 'custom') {
				this.form.imageCustomUrl = '';
			}
		},
		smartFillModels() {
			if (this.form.mode !== 'custom') {
				return;
			}
			if (!this.providerModels.length && this.canLoadModels) {
				this.loadProviderModels(false).finally(() => {
					this.applySmartFillFromAvailableModels();
				});
				return;
			}
			this.applySmartFillFromAvailableModels();
		},
		applySmartFillFromAvailableModels() {
			if (!String(this.form.modelName || '').trim()) {
				if (this.modelPresets.length) {
					this.form.modelName = this.modelPresets[0];
				} else if (String(this.currentProviderOption.defaultModel || '').trim()) {
					this.form.modelName = String(this.currentProviderOption.defaultModel || '').trim();
				}
			}
			if (!String(this.form.visionModelName || '').trim() && this.visionModelPresets.length) {
				this.form.visionModelName = this.visionModelPresets[0];
			}
			if (this.showVoiceConfig && !String(this.form.sttModelName || '').trim() && this.sttModelPresets.length) {
				this.form.sttModelName = this.sttModelPresets[0];
			}
			if (this.showVoiceConfig && !String(this.form.ttsModelName || '').trim() && this.ttsModelPresets.length) {
				this.form.ttsModelName = this.ttsModelPresets[0];
			} else if (this.showVoiceConfig && !String(this.form.ttsModelName || '').trim()) {
				const option = this.form.ttsUseSeparateConfig ? this.currentTtsProviderOption : this.currentProviderOption;
				if (String(option.defaultModel || '').trim()) {
					this.form.ttsModelName = String(option.defaultModel || '').trim();
				} else if (this.selectedTtsVoiceTemplate && String(this.selectedTtsVoiceTemplate.recommendedModelName || '').trim()) {
					this.form.ttsModelName = String(this.selectedTtsVoiceTemplate.recommendedModelName || '').trim();
				}
			}
			if (this.showImageConfig && !String(this.form.imageModelName || '').trim() && this.imageModelPresets.length) {
				this.form.imageModelName = this.imageModelPresets[0];
			} else if (this.showImageConfig && !String(this.form.imageModelName || '').trim()) {
				const option = this.form.imageUseSeparateConfig ? this.currentImageProviderOption : this.currentProviderOption;
				const dynamicModels = this.form.imageUseSeparateConfig ? this.imageProviderModels : this.providerModels;
				const nextDefault = this.resolveCapabilityDefaultModel('image', option, dynamicModels);
				if (nextDefault) {
					this.form.imageModelName = nextDefault;
				}
			}
		},
		toggleClearStoredKey() {
			if (!this.savedKeyAppliesToCurrentProvider) {
				return;
			}
			this.form.clearStoredKey = !this.form.clearStoredKey;
		},
		toggleClearStoredSttKey() {
			if (!this.savedSttKeyAppliesToCurrentProvider) {
				return;
			}
			this.form.clearStoredSttKey = !this.form.clearStoredSttKey;
		},
		toggleClearStoredTtsKey() {
			if (!this.savedTtsKeyAppliesToCurrentProvider) {
				return;
			}
			this.form.clearStoredTtsKey = !this.form.clearStoredTtsKey;
		},
		toggleClearStoredImageKey() {
			if (!this.savedImageKeyAppliesToCurrentProvider) {
				return;
			}
			this.form.clearStoredImageKey = !this.form.clearStoredImageKey;
		},
		loadProviderModels(showToast) {
			if (this.loadingModels) {
				return Promise.resolve([]);
			}
			if (!this.canLoadModels) {
				const message = this.form.providerSource === 'custom' && !String(this.form.customUrl || '').trim()
					? this.copy.needCustomUrl
					: this.copy.needApiKey;
				this.modelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return Promise.resolve([]);
			}
			this.loadingModels = true;
			this.modelListMessage = '';
			const requestSeq = ++this.modelListRequestSeq;
			const requestProvider = this.form.providerSource;
			return tavernApi.listTavernUserAiProviderModels(tavernApi.getClientUid(), this.buildPayload()).then((data) => {
				if (requestSeq !== this.modelListRequestSeq || requestProvider !== this.form.providerSource) {
					return [];
				}
				const result = data || {};
				const models = Array.isArray(result.models)
					? result.models.filter(Boolean).map(String).filter((item, index, arr) => arr.indexOf(item) === index)
					: [];
				const modelItems = this.normalizeModelCatalogItems(result.modelItems);
				const textModels = this.filterModelCatalogItemsByCapability(modelItems, 'text');
				this.providerModels = models;
				this.providerModelItems = modelItems;
				this.modelListMessage = result.message || (models.length ? '' : '\u5e73\u53f0\u6ca1\u6709\u8fd4\u56de\u6a21\u578b\u5217\u8868\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u6a21\u578b ID');
				if (textModels.length && !String(this.form.modelName || '').trim()) {
					this.form.modelName = textModels[0];
				} else if (models.length && !String(this.form.modelName || '').trim()) {
					this.form.modelName = models[0];
				}
				if (showToast) {
					uni.showToast({
						title: result.ok === false ? this.modelListMessage : '\u5df2\u83b7\u53d6\u6a21\u578b',
						icon: result.ok === false ? 'none' : 'success'
					});
				}
				return models;
			}).catch((err) => {
				if (requestSeq !== this.modelListRequestSeq || requestProvider !== this.form.providerSource) {
					return [];
				}
				const message = (err && err.message) || '\u6a21\u578b\u5217\u8868\u62c9\u53d6\u5931\u8d25\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u6a21\u578b ID';
				this.providerModels = [];
				this.providerModelItems = [];
				this.modelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return [];
			}).finally(() => {
				if (requestSeq === this.modelListRequestSeq) {
					this.loadingModels = false;
				}
			});
		},
		loadSttProviderModels(showToast) {
			if (this.loadingSttModels) {
				return Promise.resolve([]);
			}
			if (!this.showVoiceConfig) {
				this.sttModelListMessage = '';
				return Promise.resolve([]);
			}
			if (!this.canLoadSttModels) {
				const needCustom = this.form.sttUseSeparateConfig
					&& this.form.sttProviderSource === 'custom'
					&& !String(this.form.sttCustomUrl || '').trim();
				const message = needCustom ? this.copy.needCustomUrl : this.copy.needApiKey;
				this.sttModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return Promise.resolve([]);
			}
			this.loadingSttModels = true;
			this.sttModelListMessage = '';
			const requestSeq = ++this.sttModelListRequestSeq;
			const requestProvider = String(this.form.sttProviderSource || this.form.providerSource || '').trim();
			const requestCustomUrl = this.form.sttUseSeparateConfig ? String(this.form.sttCustomUrl || '').trim() : '';
			const payload = this.form.sttUseSeparateConfig
				? {
					mode: this.form.mode,
					keyScope: 'stt',
					providerSource: requestProvider,
					customUrl: requestCustomUrl,
					apiKey: String(this.form.sttApiKey || '').trim()
				}
				: this.buildPayload();
			return tavernApi.listTavernUserAiProviderModels(tavernApi.getClientUid(), payload).then((data) => {
				if (requestSeq !== this.sttModelListRequestSeq) {
					return [];
				}
				if (this.form.sttUseSeparateConfig) {
					if (requestProvider !== String(this.form.sttProviderSource || '').trim()) {
						return [];
					}
					if (requestCustomUrl !== String(this.form.sttCustomUrl || '').trim()) {
						return [];
					}
				}
				const result = data || {};
				const models = Array.isArray(result.models)
					? result.models.filter(Boolean).map(String).filter((item, index, arr) => arr.indexOf(item) === index)
					: [];
				const modelItems = this.normalizeModelCatalogItems(result.modelItems);
				const sttModels = this.filterModelCatalogItemsByCapability(modelItems, 'stt');
				this.sttProviderModels = models;
				this.sttProviderModelItems = modelItems;
				this.sttModelListMessage = sttModels.length
					? '已识别到 ' + sttModels.length + ' 个 STT 模型'
					: (result.message || (models.length ? '平台返回了模型列表，但没有识别到 STT 模型，请手动填写或更换支持 STT 的模型' : '平台没有返回模型列表，可以手动填写模型 ID'));
				const currentSttModel = String(this.form.sttModelName || '').trim();
				if (sttModels.length && (!currentSttModel || !this.matchCapabilityModel(currentSttModel, 'stt'))) {
					this.form.sttModelName = sttModels[0];
				} else if (models.length && !currentSttModel) {
					this.form.sttModelName = models[0];
				}
				if (showToast) {
					uni.showToast({
						title: result.ok === false ? this.sttModelListMessage : (sttModels.length ? '已识别 STT 模型' : '已获取模型'),
						icon: result.ok === false ? 'none' : 'success'
					});
				}
				return models;
			}).catch((err) => {
				if (requestSeq !== this.sttModelListRequestSeq) {
					return [];
				}
				const message = (err && err.message) || '模型列表拉取失败，可以手动填写模型 ID';
				this.sttProviderModels = [];
				this.sttProviderModelItems = [];
				this.sttModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return [];
			}).finally(() => {
				if (requestSeq === this.sttModelListRequestSeq) {
					this.loadingSttModels = false;
				}
			});
		},
		loadTtsProviderModels(showToast) {
			if (this.loadingTtsModels) {
				return Promise.resolve([]);
			}
			if (!this.showVoiceConfig) {
				this.ttsModelListMessage = '';
				return Promise.resolve([]);
			}
			if (!this.canLoadTtsModels) {
				const needCustom = this.form.ttsUseSeparateConfig
					&& this.form.ttsProviderSource === 'custom'
					&& !String(this.form.ttsCustomUrl || '').trim();
				const message = needCustom ? this.copy.needCustomUrl : this.copy.needApiKey;
				this.ttsModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return Promise.resolve([]);
			}
			this.loadingTtsModels = true;
			this.ttsModelListMessage = '';
			const requestSeq = ++this.ttsModelListRequestSeq;
			const requestProvider = String(this.form.ttsProviderSource || this.form.providerSource || '').trim();
			const requestCustomUrl = this.form.ttsUseSeparateConfig ? String(this.form.ttsCustomUrl || '').trim() : '';
			const payload = this.form.ttsUseSeparateConfig
				? {
					mode: this.form.mode,
					keyScope: 'tts',
					providerSource: requestProvider,
					customUrl: requestCustomUrl,
					apiKey: String(this.form.ttsApiKey || '').trim()
				}
				: this.buildPayload();
			return tavernApi.listTavernUserAiProviderModels(tavernApi.getClientUid(), payload).then((data) => {
				if (requestSeq !== this.ttsModelListRequestSeq) {
					return [];
				}
				if (this.form.ttsUseSeparateConfig) {
					if (requestProvider !== String(this.form.ttsProviderSource || '').trim()) {
						return [];
					}
					if (requestCustomUrl !== String(this.form.ttsCustomUrl || '').trim()) {
						return [];
					}
				}
				const result = data || {};
				const models = Array.isArray(result.models)
					? result.models.filter(Boolean).map(String).filter((item, index, arr) => arr.indexOf(item) === index)
					: [];
				const modelItems = this.normalizeModelCatalogItems(result.modelItems);
				const ttsModels = this.filterModelCatalogItemsByCapability(modelItems, 'tts');
				this.ttsProviderModels = models;
				this.ttsProviderModelItems = modelItems;
				this.ttsModelListMessage = ttsModels.length
					? '已识别到 ' + ttsModels.length + ' 个 TTS 模型'
					: (result.message || (models.length ? '平台返回了模型列表，但没有识别到 TTS 模型，请手动填写或换支持 TTS 的模型' : '平台没有返回模型列表，可以手动填写模型 ID'));
				const currentTtsModel = String(this.form.ttsModelName || '').trim();
				if (ttsModels.length && (!currentTtsModel || !this.matchCapabilityModel(currentTtsModel, 'tts'))) {
					this.form.ttsModelName = ttsModels[0];
				} else if (models.length && !currentTtsModel) {
					this.form.ttsModelName = models[0];
				}
				if (showToast) {
					uni.showToast({
							title: result.ok === false ? this.ttsModelListMessage : (ttsModels.length ? '\u5df2\u8bc6\u522b TTS \u6a21\u578b' : '\u5df2\u83b7\u53d6\u6a21\u578b'),
						icon: result.ok === false ? 'none' : 'success'
					});
				}
				return models;
			}).catch((err) => {
				if (requestSeq !== this.ttsModelListRequestSeq) {
					return [];
				}
				const message = (err && err.message) || '\u6a21\u578b\u5217\u8868\u62c9\u53d6\u5931\u8d25\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u6a21\u578b ID';
				this.ttsProviderModels = [];
				this.ttsProviderModelItems = [];
				this.ttsModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return [];
			}).finally(() => {
				if (requestSeq === this.ttsModelListRequestSeq) {
					this.loadingTtsModels = false;
				}
			});
		},
		loadImageProviderModels(showToast) {
			if (this.loadingImageModels) {
				return Promise.resolve([]);
			}
			if (!this.showImageConfig) {
				this.imageModelListMessage = '';
				return Promise.resolve([]);
			}
			if (!this.canLoadImageModels) {
				const needCustom = this.form.imageUseSeparateConfig
					&& this.form.imageProviderSource === 'custom'
					&& !String(this.form.imageCustomUrl || '').trim();
				const message = needCustom ? this.copy.needCustomUrl : this.copy.needApiKey;
				this.imageModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return Promise.resolve([]);
			}
			this.loadingImageModels = true;
			this.imageModelListMessage = '';
			const requestSeq = ++this.imageModelListRequestSeq;
			const requestProvider = String(this.form.imageProviderSource || this.form.providerSource || '').trim();
			const requestCustomUrl = this.form.imageUseSeparateConfig ? String(this.form.imageCustomUrl || '').trim() : '';
			const payload = this.form.imageUseSeparateConfig
				? {
					mode: this.form.mode,
					keyScope: 'image',
					providerSource: requestProvider,
					customUrl: requestCustomUrl,
					apiKey: String(this.form.imageApiKey || '').trim()
				}
				: this.buildPayload();
			return tavernApi.listTavernUserAiProviderModels(tavernApi.getClientUid(), payload).then((data) => {
				if (requestSeq !== this.imageModelListRequestSeq) {
					return [];
				}
				if (this.form.imageUseSeparateConfig) {
					if (requestProvider !== String(this.form.imageProviderSource || '').trim()) {
						return [];
					}
					if (requestCustomUrl !== String(this.form.imageCustomUrl || '').trim()) {
						return [];
					}
				}
				const result = data || {};
				const models = Array.isArray(result.models)
					? result.models.filter(Boolean).map(String).filter((item, index, arr) => arr.indexOf(item) === index)
					: [];
				const modelItems = this.normalizeModelCatalogItems(result.modelItems);
				const imageModels = this.filterModelCatalogItemsByCapability(modelItems, 'image');
				this.imageProviderModels = models;
				this.imageProviderModelItems = modelItems;
				this.imageModelListMessage = imageModels.length
					? '\u5df2\u8bc6\u522b\u5230 ' + imageModels.length + ' \u4e2a\u751f\u56fe\u6a21\u578b'
					: (result.message || (models.length
						? '\u5e73\u53f0\u8fd4\u56de\u4e86\u6a21\u578b\u5217\u8868\uff0c\u4f46\u6ca1\u6709\u8bc6\u522b\u5230\u751f\u56fe\u6a21\u578b\uff0c\u8bf7\u624b\u52a8\u586b\u5199\u6216\u6362\u652f\u6301\u751f\u56fe\u7684\u6a21\u578b'
						: '\u5e73\u53f0\u6ca1\u6709\u8fd4\u56de\u6a21\u578b\u5217\u8868\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u6a21\u578b ID'));
				const currentImageModel = String(this.form.imageModelName || '').trim();
				if (imageModels.length && (!currentImageModel || !this.matchCapabilityModel(currentImageModel, 'image'))) {
					this.form.imageModelName = imageModels[0];
				}
				if (showToast) {
					uni.showToast({
						title: result.ok === false ? this.imageModelListMessage : (imageModels.length
							? '\u5df2\u8bc6\u522b\u751f\u56fe\u6a21\u578b'
							: this.imageModelListMessage),
						icon: result.ok === false ? 'none' : 'success'
					});
				}
				return models;
			}).catch((err) => {
				if (requestSeq !== this.imageModelListRequestSeq) {
					return [];
				}
				const message = (err && err.message) || '\u6a21\u578b\u5217\u8868\u62c9\u53d6\u5931\u8d25\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u6a21\u578b ID';
				this.imageProviderModels = [];
				this.imageProviderModelItems = [];
				this.imageModelListMessage = message;
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return [];
			}).finally(() => {
				if (requestSeq === this.imageModelListRequestSeq) {
					this.loadingImageModels = false;
				}
			});
		},
		resetTestState() {
			this.lastOkTestSignature = '';
			this.testState = {
				ok: false,
				message: '',
				latencyMs: 0
			};
		},
		resetProviderModelState() {
			this.clearAutoLoadTimer('main');
			this.resetTestState();
			this.modelListRequestSeq += 1;
			this.loadingModels = false;
			this.providerModels = [];
			this.providerModelItems = [];
			this.modelListMessage = '';
		},
		resetSttProviderModelState() {
			this.clearAutoLoadTimer('stt');
			this.sttModelListRequestSeq += 1;
			this.loadingSttModels = false;
			this.sttProviderModels = [];
			this.sttProviderModelItems = [];
			this.sttModelListMessage = '';
		},
		resetTtsProviderModelState() {
			this.clearAutoLoadTimer('tts');
			this.ttsModelListRequestSeq += 1;
			this.loadingTtsModels = false;
			this.ttsProviderModels = [];
			this.ttsProviderModelItems = [];
			this.ttsModelListMessage = '';
		},
		resetImageProviderModelState() {
			this.clearAutoLoadTimer('image');
			this.imageModelListRequestSeq += 1;
			this.loadingImageModels = false;
			this.imageProviderModels = [];
			this.imageProviderModelItems = [];
			this.imageModelListMessage = '';
		},
		currentTestSignature() {
			const keyPart = String(this.form.apiKey || '').trim()
				? 'new:' + String(this.form.apiKey || '').trim()
				: (this.effectiveSavedKeyAvailable ? 'saved:' + String(this.viewState.apiKeyMask || '') : 'missing');
			const sttProviderSource = String(this.form.sttUseSeparateConfig ? this.form.sttProviderSource : this.form.providerSource || '').trim();
			const sttCustomUrl = String(this.form.sttUseSeparateConfig ? this.form.sttCustomUrl : this.form.customUrl || '').trim();
			const sttKeyPart = this.form.sttUseSeparateConfig
				? (String(this.form.sttApiKey || '').trim()
					? 'new:' + String(this.form.sttApiKey || '').trim()
					: (this.effectiveSavedSttKeyAvailable ? 'saved:' + String(this.viewState.effectiveSttApiKeyMask || this.viewState.sttApiKeyMask || '') : 'missing'))
				: keyPart;
			const ttsProviderSource = String(this.form.ttsUseSeparateConfig ? this.form.ttsProviderSource : this.form.providerSource || '').trim();
			const ttsCustomUrl = String(this.form.ttsUseSeparateConfig ? this.form.ttsCustomUrl : this.form.customUrl || '').trim();
			const ttsKeyPart = this.form.ttsUseSeparateConfig
				? (String(this.form.ttsApiKey || '').trim()
					? 'new:' + String(this.form.ttsApiKey || '').trim()
					: (this.effectiveSavedTtsKeyAvailable ? 'saved:' + String(this.viewState.effectiveTtsApiKeyMask || this.viewState.ttsApiKeyMask || '') : 'missing'))
				: keyPart;
			const imageProviderSource = String(this.form.imageUseSeparateConfig ? this.form.imageProviderSource : this.form.providerSource || '').trim();
			const imageCustomUrl = String(this.form.imageUseSeparateConfig ? this.form.imageCustomUrl : this.form.customUrl || '').trim();
			const imageKeyPart = this.form.imageUseSeparateConfig
				? (String(this.form.imageApiKey || '').trim()
					? 'new:' + String(this.form.imageApiKey || '').trim()
					: (this.effectiveSavedImageKeyAvailable ? 'saved:' + String(this.viewState.effectiveImageApiKeyMask || this.viewState.imageApiKeyMask || '') : 'missing'))
				: keyPart;
			return [
				this.form.providerSource,
				String(this.form.modelName || '').trim(),
				this.form.providerSource === 'custom' ? String(this.form.customUrl || '').trim() : '',
				keyPart,
				this.form.sttUseSeparateConfig ? 'stt:split' : 'stt:follow',
				sttProviderSource,
				sttProviderSource === 'custom' ? sttCustomUrl : '',
				String(this.form.sttModelName || '').trim(),
				sttKeyPart,
				this.form.ttsUseSeparateConfig ? 'tts:split' : 'tts:follow',
				ttsProviderSource,
				ttsProviderSource === 'custom' ? ttsCustomUrl : '',
				String(this.form.ttsModelName || '').trim(),
				ttsKeyPart,
				this.form.imageUseSeparateConfig ? 'image:split' : 'image:follow',
				imageProviderSource,
				imageProviderSource === 'custom' ? imageCustomUrl : '',
				String(this.form.imageModelName || '').trim(),
				imageKeyPart
			].join('|');
		},
		buildPayload() {
			const trimValue = (value) => String(value || '').trim();
			const voiceVisible = this.showVoiceConfig;
			const imageVisible = this.showImageConfig;
			return {
				mode: this.form.mode,
				providerSource: this.form.providerSource,
				modelName: trimValue(this.form.modelName),
				visionModelName: trimValue(this.form.visionModelName),
				sttModelName: voiceVisible ? trimValue(this.form.sttModelName) : trimValue(this.viewState.sttModelName),
				sttUseSeparateConfig: voiceVisible ? !!this.form.sttUseSeparateConfig : this.viewState.sttUseSeparateConfig === true,
				sttProviderSource: voiceVisible ? trimValue(this.form.sttProviderSource) : trimValue(this.viewState.sttProviderSource),
				sttApiKey: voiceVisible ? trimValue(this.form.sttApiKey) : '',
				sttCustomUrl: voiceVisible ? trimValue(this.form.sttCustomUrl) : trimValue(this.viewState.sttCustomUrl),
				clearStoredSttKey: voiceVisible && !trimValue(this.form.sttApiKey) && !!this.form.clearStoredSttKey,
				ttsModelName: voiceVisible ? trimValue(this.form.ttsModelName) : trimValue(this.viewState.ttsModelName),
				ttsVoiceName: voiceVisible ? trimValue(this.form.ttsVoiceName) : trimValue(this.viewState.ttsVoiceName),
				ttsVoiceTemplateCode: voiceVisible ? trimValue(this.form.ttsVoiceTemplateCode) : trimValue(this.viewState.ttsVoiceTemplateCode),
				ttsUseSeparateConfig: voiceVisible ? !!this.form.ttsUseSeparateConfig : this.viewState.ttsUseSeparateConfig === true,
				ttsProviderSource: voiceVisible ? trimValue(this.form.ttsProviderSource) : trimValue(this.viewState.ttsProviderSource),
				ttsApiKey: voiceVisible ? trimValue(this.form.ttsApiKey) : '',
				ttsCustomUrl: voiceVisible ? trimValue(this.form.ttsCustomUrl) : trimValue(this.viewState.ttsCustomUrl),
				clearStoredTtsKey: voiceVisible && !trimValue(this.form.ttsApiKey) && !!this.form.clearStoredTtsKey,
				imageUseSeparateConfig: imageVisible ? !!this.form.imageUseSeparateConfig : this.viewState.imageUseSeparateConfig === true,
				imageProviderSource: imageVisible ? trimValue(this.form.imageProviderSource) : trimValue(this.viewState.imageProviderSource),
				imageApiKey: imageVisible ? trimValue(this.form.imageApiKey) : '',
				imageCustomUrl: imageVisible ? trimValue(this.form.imageCustomUrl) : trimValue(this.viewState.imageCustomUrl),
				clearStoredImageKey: imageVisible && !trimValue(this.form.imageApiKey) && !!this.form.clearStoredImageKey,
				imageModelName: imageVisible ? trimValue(this.form.imageModelName) : trimValue(this.viewState.imageModelName),
				imageCharacterConsistencyMode: 'free',
				imageReferenceSourceMode: 'latest_generated_first',
				apiKey: trimValue(this.form.apiKey),
				customUrl: trimValue(this.form.customUrl),
				clearStoredKey: !trimValue(this.form.apiKey) && !!this.form.clearStoredKey
			};
		},
		buildScopedTestPayload(scope) {
			const payload = this.buildPayload();
			const safeScope = String(scope || 'main').trim() || 'main';
			if (safeScope === 'stt') {
				const providerSource = String(this.form.sttUseSeparateConfig ? this.form.sttProviderSource : this.form.providerSource || '').trim();
				const customUrl = String(this.form.sttUseSeparateConfig ? this.form.sttCustomUrl : this.form.customUrl || '').trim();
				const apiKey = String(this.form.sttUseSeparateConfig ? this.form.sttApiKey : this.form.apiKey || '').trim();
				return {
					mode: this.form.mode,
					keyScope: 'stt',
					providerSource,
					customUrl,
					apiKey,
					modelName: String(this.form.sttModelName || '').trim(),
					sttUseSeparateConfig: !!this.form.sttUseSeparateConfig
				};
			}
			if (safeScope === 'tts') {
				const providerSource = String(this.form.ttsUseSeparateConfig ? this.form.ttsProviderSource : this.form.providerSource || '').trim();
				const customUrl = String(this.form.ttsUseSeparateConfig ? this.form.ttsCustomUrl : this.form.customUrl || '').trim();
				const apiKey = String(this.form.ttsUseSeparateConfig ? this.form.ttsApiKey : this.form.apiKey || '').trim();
				return {
					mode: this.form.mode,
					keyScope: 'tts',
					providerSource,
					customUrl,
					apiKey,
					modelName: String(this.form.ttsModelName || '').trim(),
					ttsUseSeparateConfig: !!this.form.ttsUseSeparateConfig
				};
			}
			if (safeScope === 'image') {
				const providerSource = String(this.form.imageUseSeparateConfig ? this.form.imageProviderSource : this.form.providerSource || '').trim();
				const customUrl = String(this.form.imageUseSeparateConfig ? this.form.imageCustomUrl : this.form.customUrl || '').trim();
				const apiKey = String(this.form.imageUseSeparateConfig ? this.form.imageApiKey : this.form.apiKey || '').trim();
				return {
					mode: this.form.mode,
					keyScope: 'image',
					providerSource,
					customUrl,
					apiKey,
					modelName: String(this.form.imageModelName || '').trim(),
					imageUseSeparateConfig: !!this.form.imageUseSeparateConfig
				};
			}
			payload.keyScope = 'main';
			return payload;
		},
		shouldRunImageConnectionTest() {
			return this.showImageConfig && this.form.mode === 'custom' && !!String(this.form.imageModelName || '').trim();
		},
		shouldRunTtsConnectionTest() {
			return this.showVoiceConfig && this.form.mode === 'custom' && !!String(this.form.ttsModelName || '').trim();
		},
		shouldRunSttConnectionTest() {
			return this.showVoiceConfig && this.form.mode === 'custom' && !!String(this.form.sttModelName || '').trim();
		},
		normalizeConnectionScopeLabel(label, scope) {
			const text = String(label || '').trim();
			const safeScope = String(scope || '').trim().toLowerCase();
			const map = {
				main: pickLocaleText(this.localeCode, { 'zh-cn': '\u804a\u5929', 'zh-hk': '\u804a\u5929', en: '\u804a\u5929' }),
				image: pickLocaleText(this.localeCode, { 'zh-cn': '\u751f\u56fe', 'zh-hk': '\u751f\u5716', en: '\u751f\u56fe' }),
				stt: 'STT',
				tts: 'TTS'
			};
			if (safeScope && map[safeScope]) {
				return map[safeScope];
			}
			if (!text) {
				return map.main;
			}
			const normalizedText = text.toLowerCase();
			if (normalizedText === 'main' || normalizedText === 'chat' || text === '\u804a\u5929' || text === '\u9471\u5a3d\u3069') {
				return map.main;
			}
			if (normalizedText === 'image' || text === '\u751f\u56fe' || text === '\u751f\u5716' || text === '\u9422\u71dd\u6d58') {
				return map.image;
			}
			if (normalizedText === 'tts') {
				return map.tts;
			}
			if (normalizedText === 'stt') {
				return map.stt;
			}
			return text;
		},
		runScopedConnectionTest(scope, label) {
			const safeLabel = this.normalizeConnectionScopeLabel(label, scope);
			return tavernApi.testTavernUserAiProvider(
				tavernApi.getClientUid(),
				this.buildScopedTestPayload(scope)
			).then((data) => {
				const result = data || {};
				return {
					label: safeLabel,
					ok: result.ok === true,
					message: result.message || (result.ok === true ? this.copy.testSuccessTitle : this.copy.testFailTitle),
					latencyMs: Number(result.latencyMs || 0)
				};
			}).catch((err) => ({
				label: safeLabel,
				ok: false,
				message: (err && err.message) || this.copy.testFailTitle,
				latencyMs: 0
			}));
		},
		buildConnectionTestResults(results) {
			const source = Array.isArray(results) ? results.filter(Boolean) : [];
			if (!source.length) {
				return {
					ok: false,
					message: this.copy.testFailTitle,
					latencyMs: 0
				};
			}
			const ok = source.every((item) => item.ok === true);
			const latencyMs = source.reduce((max, item) => Math.max(max, Number(item && item.latencyMs || 0)), 0);
			const message = source.map((item) => {
				const label = item && item.label ? String(item.label) : '';
				const body = item && item.message ? String(item.message) : (item && item.ok ? this.copy.testSuccessTitle : this.copy.testFailTitle);
				return label ? (label + '\uff1a' + body) : body;
			}).join('\\n');
			return {
				ok,
				message,
				latencyMs
			};
		},
		mergeConnectionTestResults(results) {
			const source = Array.isArray(results) ? results.filter(Boolean) : [];
			if (!source.length) {
				return {
					ok: false,
					message: this.copy.testFailTitle,
					latencyMs: 0
				};
			}
			const ok = source.every((item) => item.ok === true);
			const latencyMs = source.reduce((max, item) => Math.max(max, Number(item && item.latencyMs || 0)), 0);
			const message = source.map((item) => {
				const label = item && item.label ? String(item.label) : '';
				const body = item && item.message ? String(item.message) : (item && item.ok ? this.copy.testSuccessTitle : this.copy.testFailTitle);
				return label ? (label + '\uff1a' + body) : body;
			}).join('\\n');
			return {
				ok,
				message,
				latencyMs
			};
		},
		validateCustomForm(showToast) {
			if (this.form.mode !== 'custom') {
				return true;
			}
			let message = '';
			if (!this.viewState.canUse) {
				message = this.viewState.denyReason || this.copy.unavailableTitle;
			} else if (!String(this.form.providerSource || '').trim()) {
				message = this.copy.needProvider;
			} else if (!String(this.form.modelName || '').trim()) {
				message = this.copy.needModel;
			} else if (this.form.providerSource === 'custom' && !String(this.form.customUrl || '').trim()) {
				message = this.copy.needCustomUrl;
			} else if (!String(this.form.apiKey || '').trim() && !this.effectiveSavedKeyAvailable) {
				message = this.copy.needApiKey;
			} else if (this.showVoiceConfig && this.form.sttUseSeparateConfig && !String(this.form.sttProviderSource || '').trim()) {
				message = this.copy.needProvider || '请选择平台';
			} else if (this.showVoiceConfig && this.form.sttUseSeparateConfig && this.form.sttProviderSource === 'custom' && !String(this.form.sttCustomUrl || '').trim()) {
				message = this.copy.needCustomUrl;
			} else if (this.showVoiceConfig && this.form.sttUseSeparateConfig && !String(this.form.sttApiKey || '').trim() && !this.effectiveSavedSttKeyAvailable) {
				message = this.copy.needApiKey || '请先填写 API Key';
			} else if (this.showVoiceConfig && this.form.ttsUseSeparateConfig && !String(this.form.ttsProviderSource || '').trim()) {
				message = this.copy.needProvider || '请先选择平台';
			} else if (this.showVoiceConfig && this.form.ttsUseSeparateConfig && this.form.ttsProviderSource === 'custom' && !String(this.form.ttsCustomUrl || '').trim()) {
				message = this.copy.needCustomUrl;
			} else if (this.showVoiceConfig && this.form.ttsUseSeparateConfig && !String(this.form.ttsApiKey || '').trim() && !this.effectiveSavedTtsKeyAvailable) {
				message = this.copy.needApiKey || '请先填写 API Key';
			}
			if (!message && this.showImageConfig && this.form.imageUseSeparateConfig && !String(this.form.imageProviderSource || '').trim()) {
				message = this.copy.needProvider || '请先选择平台';
			}
			if (!message && this.showImageConfig && this.form.imageUseSeparateConfig && this.form.imageProviderSource === 'custom' && !String(this.form.imageCustomUrl || '').trim()) {
				message = this.copy.needCustomUrl;
			}
			if (!message && this.showImageConfig && this.form.imageUseSeparateConfig && !String(this.form.imageApiKey || '').trim() && !this.effectiveSavedImageKeyAvailable) {
				message = this.copy.needApiKey || '请先填写 API Key';
			}
			if (message) {
				if (showToast) {
					uni.showToast({ title: message, icon: 'none' });
				}
				return false;
			}
			return true;
		},
		testConnection() {
			if (!this.canRunCustomAction || this.testing) {
				return;
			}
			if (!this.validateCustomForm(true)) {
				return;
			}
			this.testing = true;
			const tasks = [
				this.runScopedConnectionTest('main', '聊天')
			];
			if (this.shouldRunSttConnectionTest()) {
				tasks.push(this.runScopedConnectionTest('stt', 'STT'));
			}
			if (this.shouldRunImageConnectionTest()) {
				tasks.push(this.runScopedConnectionTest('image', '生图'));
			}
			if (this.shouldRunTtsConnectionTest()) {
				tasks.push(this.runScopedConnectionTest('tts', 'TTS'));
			}
			Promise.all(tasks).then((results) => {
				this.testing = false;
				const merged = this.buildConnectionTestResults(results);
				this.testState = merged;
				if (merged.ok === true) {
					this.lastOkTestSignature = this.currentTestSignature();
				}
			}).catch((err) => {
				this.testing = false;
				this.testState = {
					ok: false,
					message: (err && err.message) || this.copy.testFailTitle,
					latencyMs: 0
				};
			});
		},
		save() {
			if (this.saving) {
				return;
			}
			if (!this.validateCustomForm(true)) {
				return;
			}
			if (this.form.mode === 'custom' && this.lastOkTestSignature !== this.currentTestSignature()) {
				uni.showModal({
					title: this.copy.untestedTitle,
					content: this.copy.untestedContent,
					confirmText: this.copy.saveAnyway,
					success: (res) => {
						if (res && res.confirm) {
							this.submitSave();
						}
					}
				});
				return;
			}
			this.submitSave();
		},
		submitSave() {
			this.saving = true;
			tavernApi.putTavernUserAiProvider(tavernApi.getClientUid(), this.buildPayload()).then((data) => {
				this.saving = false;
				this.applyProviderStateToForm(data);
				uni.showToast({
					title: this.copy.saveSuccess,
					icon: 'success'
				});
			}).catch((err) => {
				this.saving = false;
				uni.showToast({
					title: (err && err.message) || '保存失败',
					icon: 'none'
				});
			});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background: transparent;
	display: flex;
	flex-direction: column;
	color: #203846;
}

.body {
	flex: 1;
	padding: 24rpx 24rpx calc(56rpx + env(safe-area-inset-bottom));
}

.hero-card,
.sheet,
.status-card {
	background: rgba(255, 255, 255, 0.8);
	border: 1rpx solid rgba(255, 255, 255, 0.68);
	box-shadow: 0 16rpx 36rpx rgba(36, 70, 88, 0.1);
	backdrop-filter: blur(18rpx);
	-webkit-backdrop-filter: blur(18rpx);
}

.hero-card {
	padding: 28rpx;
	border-radius: 26rpx;
	margin-bottom: 24rpx;
}

.hero-top {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 16rpx;
}

.hero-kicker {
	font-size: 22rpx;
	font-weight: 700;
	color: #236f82;
}

.hero-title {
	display: block;
	font-size: 34rpx;
	line-height: 1.35;
	font-weight: 800;
	color: #203846;
}

.hero-subtitle {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: #5f7280;
}

.hero-badge {
	max-width: 220rpx;
	padding: 8rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(63, 143, 159, 0.12);
	color: #236f82;
	font-size: 21rpx;
	font-weight: 700;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.sheet {
	border-radius: 24rpx;
	padding: 24rpx;
}

.mode-row {
	display: flex;
	gap: 18rpx;
	margin-bottom: 26rpx;
}

.mode-chip {
	flex: 1;
	min-height: 108rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(79, 147, 163, 0.16);
	color: #55707e;
}

.mode-title {
	font-size: 27rpx;
	font-weight: 800;
	color: inherit;
}

.mode-desc {
	margin-top: 8rpx;
	font-size: 21rpx;
	color: inherit;
	opacity: 0.78;
}

.mode-chip--active {
	background: #3f8f9f;
	color: #ffffff;
	border-color: transparent;
	box-shadow: 0 14rpx 28rpx rgba(48, 103, 117, 0.18);
}

.mode-chip--disabled {
	opacity: 0.48;
}

.mode-row--inner {
	margin: 16rpx 0 0;
}

.mode-chip--mini {
	min-height: 96rpx;
	padding: 12rpx 10rpx;
}

.official-card {
	padding: 28rpx;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.68);
	border: 1rpx solid rgba(79, 147, 163, 0.12);
}

.official-title {
	display: block;
	font-size: 29rpx;
	font-weight: 800;
	color: #203846;
}

.official-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: #5f7280;
}

.field {
	margin-bottom: 26rpx;
}

.field:last-child {
	margin-bottom: 0;
}

.field-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 12rpx;
}

.field-label {
	display: block;
	font-size: 26rpx;
	font-weight: 800;
	color: #203846;
}

.field-meta {
	font-size: 21rpx;
	color: #738793;
}

.field-meta--safe {
	color: #237c70;
	font-weight: 700;
}

.field-actions {
	display: flex;
	align-items: center;
	gap: 12rpx;
	flex-wrap: wrap;
}

.model-load-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(63, 143, 159, 0.12);
	color: #236f82;
	font-size: 21rpx;
	font-weight: 800;
	line-height: 1.2;
	border: 1rpx solid rgba(79, 147, 163, 0.16);
}

.model-load-btn--primary {
	background: #3f8f9f;
	border-color: transparent;
	color: #ffffff;
	box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.18);
}

.model-load-btn--ghost {
	background: rgba(255, 255, 255, 0.72);
	border-color: rgba(79, 147, 163, 0.14);
	color: #236f82;
}

.model-load-btn--disabled {
	opacity: 0.48;
}

.model-load-btn--warn {
	background: rgba(255, 244, 247, 0.92);
	border-color: rgba(232, 139, 161, 0.22);
	color: #c8647c;
}

.model-load-btn--active {
	background: rgba(200, 100, 124, 0.14);
	border-color: rgba(200, 100, 124, 0.3);
	color: #b54f67;
}

.field-tip {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #667f92;
}

.section-caption {
	display: block;
	margin-top: 6rpx;
	font-size: 21rpx;
	line-height: 1.55;
	color: #7a8fa0;
}

.field-tip--warning {
	color: #b46a57;
}

.field-tip--safe {
	color: #237c70;
}

.picker-shell {
	min-height: 84rpx;
	padding: 0 24rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.86);
	border: 1rpx solid rgba(88, 189, 210, 0.22);
}

.picker-shell--disabled {
	opacity: 0.58;
}

.picker-value {
	font-size: 27rpx;
	font-weight: 700;
	color: #315d76;
}

.preset-box {
	margin-top: 14rpx;
	padding: 16rpx;
	border-radius: 18rpx;
	background: rgba(234, 249, 252, 0.68);
	border: 1rpx solid rgba(91, 190, 209, 0.16);
}

.preset-box--tight {
	margin-top: 12rpx;
	padding: 14rpx;
}

.preset-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.preset-label {
	display: block;
	margin-bottom: 12rpx;
	font-size: 22rpx;
	font-weight: 800;
	color: #3e7c94;
}

.preset-head .preset-label {
	margin-bottom: 0;
}

.preset-toggle {
	display: block;
	margin-bottom: 10rpx;
	font-size: 21rpx;
	font-weight: 800;
	color: #4aa8bf;
	text-align: right;
}

.preset-head .preset-toggle {
	margin-bottom: 0;
}

.preset-list {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
}

.preset-chip {
	max-width: 100%;
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.82);
	border: 1rpx solid rgba(84, 177, 197, 0.2);
	color: #3f667b;
	font-size: 22rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.preset-chip--active {
	background: #3f8f9f;
	color: #ffffff;
	border-color: #3f8f9f;
	box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.18);
}

.capability-strip {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.capability-pill {
	display: inline-flex;
	align-items: center;
	gap: 10rpx;
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(35, 124, 112, 0.1);
	border: 1rpx solid rgba(35, 124, 112, 0.12);
	color: #237c70;
	font-size: 21rpx;
	font-weight: 700;
	line-height: 1.2;
}

.capability-pill__name,
.capability-pill__count {
	color: inherit;
}

.aux-grid {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
	margin-top: 16rpx;
}

.aux-card {
	padding: 18rpx;
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(79, 147, 163, 0.14);
}

.aux-card--tts-config {
	background: rgba(255, 255, 255, 0.74);
	border-color: rgba(79, 147, 163, 0.16);
}

.aux-card--voice-library {
	background: rgba(255, 255, 255, 0.76);
}

.aux-card--image-consistency {
	position: relative;
	overflow: hidden;
	background: rgba(255, 255, 255, 0.76);
	border-color: rgba(79, 147, 163, 0.16);
	box-shadow: 0 12rpx 26rpx rgba(36, 70, 88, 0.08);
}

.strategy-card-head {
	position: relative;
	z-index: 1;
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 16rpx;
}

.strategy-title-wrap {
	min-width: 0;
	flex: 1;
}

.strategy-card-head .aux-card__label {
	margin-bottom: 8rpx;
}

.strategy-badge {
	flex-shrink: 0;
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(54, 154, 179, 0.12);
	border: 1rpx solid rgba(54, 154, 179, 0.16);
	color: #317b92;
	font-size: 20rpx;
	font-weight: 800;
	line-height: 1.2;
}

.strategy-badge--soft {
	background: rgba(255, 255, 255, 0.7);
	color: #527b90;
}

.strategy-switch {
	position: relative;
	z-index: 1;
	display: flex;
	gap: 8rpx;
	margin-top: 18rpx;
	padding: 8rpx;
	border-radius: 999rpx;
	background: rgba(233, 247, 251, 0.9);
	border: 1rpx solid rgba(82, 168, 193, 0.16);
	box-shadow: inset 0 2rpx 8rpx rgba(57, 115, 141, 0.08);
}

.strategy-switch__item {
	flex: 1;
	min-width: 0;
	min-height: 64rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 0 14rpx;
	color: #4f7b91;
	font-size: 23rpx;
	font-weight: 900;
	line-height: 1.2;
	text-align: center;
}

.strategy-switch__item--active {
	background: #3f8f9f;
	color: #ffffff;
	box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.18);
}

.strategy-current-desc {
	position: relative;
	z-index: 1;
	display: block;
	margin-top: 14rpx;
	padding: 14rpx 16rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(91, 165, 190, 0.12);
	color: #627c8f;
	font-size: 22rpx;
	line-height: 1.6;
}

.strategy-ref-box {
	position: relative;
	z-index: 1;
	margin-top: 20rpx;
	padding-top: 20rpx;
	border-top: 1rpx solid rgba(91, 165, 190, 0.16);
}

.strategy-sub-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.strategy-switch--two {
	margin-top: 14rpx;
}

.strategy-tip {
	margin-top: 14rpx;
}

.aux-card__label {
	display: block;
	margin-bottom: 12rpx;
	font-size: 24rpx;
	font-weight: 800;
	color: #2c5e78;
}

.tts-provider-fields {
	margin-top: 18rpx;
}

.voice-template-scroll {
	margin-top: 16rpx;
	max-height: 560rpx;
	padding-right: 4rpx;
	box-sizing: border-box;
}

.voice-template-list {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.voice-template-card {
	display: flex;
	align-items: center;
	gap: 16rpx;
	min-height: 118rpx;
	padding: 14rpx 16rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.86);
	border: 1rpx solid rgba(92, 171, 193, 0.16);
	box-shadow: 0 8rpx 20rpx rgba(69, 120, 143, 0.08);
	box-sizing: border-box;
}

.voice-template-card--active {
	background: rgba(63, 143, 159, 0.12);
	border-color: rgba(63, 143, 159, 0.36);
	box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.12);
}

.voice-template-card__cover {
	width: 78rpx;
	height: 78rpx;
	border-radius: 14rpx;
	background: rgba(235, 247, 252, 0.88);
	flex-shrink: 0;
}

.voice-template-card__cover--placeholder {
	display: flex;
	align-items: center;
	justify-content: center;
	color: #6990a3;
	font-size: 21rpx;
	font-weight: 800;
}

.voice-template-card__body {
	min-width: 0;
	flex: 1;
	display: flex;
	flex-direction: column;
}

.voice-template-card__head {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.voice-template-card__title {
	min-width: 0;
	flex: 1;
	font-size: 24rpx;
	font-weight: 800;
	color: #214d68;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.voice-template-card__badge {
	flex-shrink: 0;
	max-width: 100%;
	padding: 6rpx 12rpx;
	border-radius: 999rpx;
	font-size: 19rpx;
	line-height: 1.3;
	color: #5a6f80;
	background: rgba(234, 245, 248, 0.92);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.voice-template-card__badge--ready {
	color: #21776a;
	background: rgba(102, 214, 181, 0.16);
}

.voice-template-card__badge--failed,
.voice-template-card__badge--requires_api_key,
.voice-template-card__badge--requires_provider,
.voice-template-card__badge--requires_model,
.voice-template-card__badge--requires_byok {
	color: #b76657;
	background: rgba(255, 226, 218, 0.82);
}

.voice-template-card__meta {
	margin-top: 6rpx;
	font-size: 20rpx;
	font-weight: 700;
	color: #4a89a2;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.voice-template-card__desc {
	display: -webkit-box;
	margin-top: 4rpx;
	font-size: 21rpx;
	line-height: 1.45;
	color: #667f92;
	overflow: hidden;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.voice-template-card__check {
	flex-shrink: 0;
	padding: 7rpx 12rpx;
	border-radius: 999rpx;
	background: #3f8f9f;
	color: #ffffff;
	font-size: 19rpx;
	font-weight: 800;
	line-height: 1.2;
}

.voice-template-empty {
	margin-top: 16rpx;
	padding: 18rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.76);
	border: 1rpx dashed rgba(93, 157, 179, 0.26);
}

.voice-template-empty__title {
	display: block;
	font-size: 24rpx;
	font-weight: 800;
	color: #335f77;
}

.voice-template-empty__desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #708799;
}

.voice-template-active-bar {
	margin-top: 16rpx;
	padding: 16rpx 18rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.84);
	border: 1rpx solid rgba(76, 185, 206, 0.18);
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.voice-template-active-copy {
	min-width: 0;
	flex: 1;
}

.voice-template-active-title {
	display: block;
	font-size: 24rpx;
	font-weight: 800;
	color: #214d68;
}

.voice-template-active-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 21rpx;
	line-height: 1.55;
	color: #617b8e;
}

.voice-template-active-switch {
	flex-shrink: 0;
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(255, 244, 247, 0.92);
	color: #c7657d;
	font-size: 21rpx;
	font-weight: 800;
}

.voice-manual-box {
	margin-top: 16rpx;
}

.voice-preset-scroll {
	max-height: 184rpx;
}

.voice-manual-label {
	display: block;
	margin-bottom: 10rpx;
	font-size: 22rpx;
	font-weight: 800;
	color: #35627b;
}

.field--compact {
	margin-bottom: 18rpx;
}

.field--compact-last {
	margin-bottom: 0;
}

.status-card {
	margin-top: 22rpx;
	padding: 24rpx;
	border-radius: 22rpx;
}

.status-title {
	display: block;
	font-size: 26rpx;
	font-weight: 800;
	color: #24536e;
}

.status-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.status-pill {
	flex-shrink: 0;
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.72);
	color: #4f8ba1;
	font-size: 20rpx;
	font-weight: 800;
}

.status-list {
	margin-top: 14rpx;
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.status-line {
	padding: 14rpx 16rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.58);
	border: 1rpx solid rgba(96, 142, 171, 0.12);
}

.status-line__label {
	display: block;
	font-size: 22rpx;
	font-weight: 800;
	color: #2f647c;
}

.status-line__body {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #60798c;
}

.status-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: #60798c;
	white-space: pre-line;
}

.status-card--success {
	background: rgba(236, 255, 248, 0.76);
	border-color: rgba(91, 195, 152, 0.24);
}

.status-card--success .status-title {
	color: #2f9279;
}

.status-card--success .status-line {
	border-color: rgba(91, 195, 152, 0.14);
}

.status-card--error,
.status-card--warning {
	background: rgba(255, 246, 249, 0.78);
	border-color: rgba(232, 139, 161, 0.24);
}

.status-card--error .status-title,
.status-card--warning .status-title {
	color: #c8647c;
}

.status-card--error .status-pill,
.status-card--warning .status-pill {
	color: #c8647c;
}

.action-row {
	display: flex;
	gap: 18rpx;
	margin-top: 26rpx;
}

.action-row--single .action-button {
	flex: none;
	width: 100%;
}

.action-button {
	flex: 1;
	min-height: 88rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 27rpx;
	font-weight: 800;
}

.action-button--secondary {
	background: rgba(255, 255, 255, 0.78);
	border: 1rpx solid rgba(79, 147, 163, 0.18);
	color: #236f82;
}

.action-button--primary {
	background: #3f8f9f;
	color: #ffffff;
	box-shadow: 0 16rpx 32rpx rgba(48, 103, 117, 0.2);
}

.action-button--disabled {
	opacity: 0.55;
}
</style>
