<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="聊天设定" mode="dark" @back="goBack">
			<template #right>
				<text class="nav-save" :class="{ 'nav-save--disabled': saving }" @tap="save">{{ saving ? '保存中' : '保存' }}</text>
			</template>
		</tavern-nav-bar>

		<scroll-view class="body-scroll" scroll-y>
			<view class="body">
				<view class="identity-summary">
					<view class="identity-mark"><u-icon name="account-fill" color="#ffffff" size="32"></u-icon></view>
					<view class="identity-copy">
						<text class="identity-kicker">当前聊天身份</text>
						<text class="identity-name">{{ effectiveIdentityName }}</text>
						<text v-if="characterName" class="identity-context">与 {{ characterName }} 的对话</text>
					</view>
					<view class="identity-state"><view class="identity-state-dot"></view><text>使用中</text></view>
				</view>

				<view class="section section--identity">
					<view class="section-heading">
						<view class="section-icon"><u-icon name="account-fill" color="#287080" size="26"></u-icon></view>
						<view class="section-heading-copy"><text class="section-title">聊天身份</text><text class="section-caption">称呼与当前会话身份</text></view>
					</view>
					<view class="field-grid">
						<view class="field">
							<text class="lab">账号显示名</text>
							<input class="inp" v-model="displayName" maxlength="64" placeholder="账号资料显示名称" placeholder-class="ph" />
						</view>
						<view class="field">
							<text class="lab">默认聊天称呼</text>
							<input class="inp" v-model="stDisplayName" maxlength="64" placeholder="角色默认如何称呼你" placeholder-class="ph" />
						</view>
					</view>
					<view class="field field--last" v-if="cid">
						<view class="field-head"><text class="lab lab--inline">当前会话覆盖名</text><text class="scope-tag">仅本会话</text></view>
						<view class="inline-input">
							<input class="inp inline-input-main" v-model="stDisplayNameOverride" maxlength="64" placeholder="留空则使用默认称呼" placeholder-class="ph" />
							<view v-if="stDisplayNameOverride" class="clear-icon" title="清除覆盖名" @tap="stDisplayNameOverride = ''"><u-icon name="close" color="#637786" size="22"></u-icon></view>
						</view>
					</view>
				</view>

				<view class="section section--persona">
					<view class="section-head">
						<view class="section-heading section-heading--compact">
							<view class="section-icon"><u-icon name="edit-pen" color="#287080" size="26"></u-icon></view>
							<view class="section-heading-copy"><text class="section-title section-title--inline">我的人设</text><text class="section-caption">身份、性格与对话习惯</text></view>
						</view>
						<text class="counter">{{ persona.length }}/2000</text>
					</view>
					<textarea class="area" v-model="persona" maxlength="2000" placeholder="身份、性格、习惯、关系和说话方式" placeholder-class="ph" />
					<view class="section-actions">
						<view class="text-action" :class="{ 'text-action--disabled': !persona || saving }" @tap="confirmClearPersona"><u-icon name="trash" color="#8b3f3b" size="22"></u-icon><text>清空人设</text></view>
					</view>
				</view>

				<view v-if="conversationId && presetFeatureConfigReady && showPresetSection" class="section preset-section">
					<view class="section-head">
						<view class="section-heading section-heading--compact">
							<view class="section-icon"><u-icon name="file-text" color="#287080" size="26"></u-icon></view>
							<view class="section-heading-copy"><text class="section-title section-title--inline">生成预设</text><text class="section-sub">{{ selectedPresetName }}</text></view>
						</view>
						<view v-if="presetSaving || presetLoading" class="loading-mark"><u-icon name="reload" color="#3f8f9f" size="23" class="spin"></u-icon></view>
					</view>
					<view v-if="showSystemPresets && showUserPresets" class="segment">
						<view class="segment-item" :class="{ 'segment-item--active': presetTab === 'official' }" @tap="presetTab = 'official'">官方预设</view>
						<view class="segment-item" :class="{ 'segment-item--active': presetTab === 'mine' }" @tap="presetTab = 'mine'">我的预设 <text v-if="myPresets.length">{{ myPresets.length }}</text></view>
					</view>

					<view v-if="showSystemPresets && presetTab === 'official'" class="preset-pane">
						<picker mode="selector" :range="officialPickerOptions" range-key="name" :value="officialPickerIndex" @change="handleOfficialPickerChange">
							<view class="selector-row">
								<view class="selector-copy"><text class="selector-label">会话使用</text><text class="selector-value">{{ officialPickerName }}</text></view>
								<u-icon name="arrow-down" color="#5f7280" size="24"></u-icon>
							</view>
						</picker>
						<text v-if="selectedOfficialSummary" class="preset-summary">{{ selectedOfficialSummary }}</text>
						<view v-if="showUserPresets" class="copy-button" :class="{ 'copy-button--disabled': !selectedOfficial || presetSaving }" @tap="copyOfficialPreset">
							<u-icon name="file-text" color="#236f82" size="24"></u-icon><text>复制生成参数到我的预设</text>
						</view>
					</view>

					<view v-else-if="showUserPresets" class="preset-pane">
						<view v-if="!myPresets.length" class="preset-empty"><text>暂无我的预设</text></view>
						<view v-for="item in myPresets" :key="'my_preset_' + item.id" class="preset-row" :class="{ 'preset-row--selected': Number(currentPresetId) === Number(item.id), 'preset-row--disabled': !item.enabled }" @tap="selectMyPreset(item)">
							<view class="preset-row-main"><text class="preset-row-name">{{ item.name }}</text><text class="preset-row-meta">{{ presetSummaryText(item) }}</text></view>
							<view class="preset-row-actions">
								<view class="icon-button" title="编辑预设" @tap.stop="openPresetEditor(item)"><u-icon name="edit-pen" color="#236f82" size="24"></u-icon></view>
								<view class="icon-button icon-button--danger" title="删除预设" @tap.stop="confirmDeletePreset(item)"><u-icon name="trash" color="#9f2d28" size="24"></u-icon></view>
							</view>
						</view>
					</view>
				</view>

				<view v-if="backgroundPreview" class="background-strip" @tap="previewBackground">
					<image class="background-thumb" :src="backgroundPreview" mode="aspectFill" />
					<view class="background-copy"><text class="background-title">当前聊天背景</text><text class="background-name">{{ characterName || '角色背景' }}</text></view>
					<u-icon name="arrow-right" color="#637786" size="24"></u-icon>
				</view>
			</view>
		</scroll-view>

		<view v-if="showUserPresets && presetEditor.visible" class="editor-mask" @tap="closePresetEditor">
			<view class="editor-sheet" @tap.stop>
				<view class="editor-head"><text class="editor-title">编辑我的预设</text><view class="icon-button" @tap="closePresetEditor"><u-icon name="close" color="#637786" size="26"></u-icon></view></view>
				<text class="lab">名称</text>
				<input class="inp" v-model="presetEditor.name" maxlength="40" placeholder="预设名称" placeholder-class="ph" />
				<view class="param-grid">
					<view class="param-field"><text class="lab">随机度</text><input class="param-input" v-model="presetEditor.temperature" type="digit" /></view>
					<view class="param-field"><text class="lab">Top P</text><input class="param-input" v-model="presetEditor.topP" type="digit" /></view>
					<view class="param-field"><text class="lab">回复长度</text><input class="param-input" v-model="presetEditor.maxTokens" type="number" /></view>
					<view class="param-field"><text class="lab">上下文长度</text><input class="param-input" v-model="presetEditor.maxContext" type="number" /></view>
				</view>
				<view class="enable-row"><view><text class="enable-title">启用预设</text><text class="enable-sub">关闭后绑定此预设的会话恢复系统默认</text></view><switch :checked="presetEditor.enabled" color="#3f8f9f" @change="presetEditor.enabled = $event.detail.value" /></view>
				<text v-if="presetEditor.error" class="editor-error">{{ presetEditor.error }}</text>
				<view class="editor-actions"><view class="editor-button editor-button--ghost" @tap="closePresetEditor">取消</view><view class="editor-button" :class="{ 'editor-button--disabled': presetEditor.saving }" @tap="savePresetEditor">{{ presetEditor.saving ? '保存中' : '保存' }}</view></view>
			</view>
		</view>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	function emptyPresetEditor() {
		return { visible: false, id: null, name: '', temperature: '1', topP: '1', maxTokens: '512', maxContext: '8192', enabled: true, saving: false, error: '' };
	}

	export default {
		components: { TavernNavBar },
		data() {
			return {
				cid: '', conversationId: null, displayName: '', stDisplayName: '', stDisplayNameOverride: '', effectiveStDisplayName: '', persona: '', saving: false,
				characterName: '', backgroundPreview: '', presetLoading: false, presetSaving: false, currentPresetId: null, platformPresets: [], myPresets: [],
				presetTab: 'official', officialDraftId: null, presetEditor: emptyPresetEditor(), presetFeatureConfigReady: false,
				systemChatPresetEntryVisible: true, userChatPresetEntryVisible: true
			};
		},
		onLoad(query) {
			this.cid = query && query.id ? String(query.id) : '';
			this.loadPresetFeatureConfig().finally(() => this.loadProfile());
			this.loadCharacterPreview();
		},
		computed: {
			effectiveIdentityName() { return (this.stDisplayNameOverride || this.stDisplayName || this.displayName || '未设置称呼').trim(); },
			showSystemPresets() { return this.systemChatPresetEntryVisible !== false; },
			showUserPresets() { return this.userChatPresetEntryVisible !== false; },
			showPresetSection() { return this.showSystemPresets || this.showUserPresets; },
			allPresets() { return (this.platformPresets || []).concat(this.myPresets || []); },
			selectedPreset() { return this.allPresets.find((item) => Number(item.id) === Number(this.currentPresetId)) || null; },
			selectedPresetName() {
				if (!this.selectedPreset) return '系统默认';
				if (this.selectedPreset.scope === 'PUBLIC' && !this.showSystemPresets) return '系统预设已隐藏';
				if (this.selectedPreset.scope === 'PRIVATE' && !this.showUserPresets) return '我的预设已隐藏';
				return this.selectedPreset.name;
			},
			officialPickerOptions() { return [{ id: null, name: '系统默认' }].concat(this.platformPresets || []); },
			officialPickerIndex() {
				const id = Number(this.officialDraftId || 0);
				const index = this.officialPickerOptions.findIndex((item) => Number(item.id || 0) === id);
				return index < 0 ? 0 : index;
			},
			officialPickerName() { return this.officialPickerOptions[this.officialPickerIndex].name; },
			selectedOfficial() { return (this.platformPresets || []).find((item) => Number(item.id) === Number(this.officialDraftId)) || null; },
			selectedOfficialSummary() { return this.selectedOfficial ? this.presetSummaryText(this.selectedOfficial) : '使用系统当前默认生成参数'; }
		},
		methods: {
			goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/tavern/tavern' }) }); },
			loadPresetFeatureConfig() {
				const api = require('@/common/tavernApi.js');
				return api.fetchAppRuntimeConfig(true).then((config) => {
					this.systemChatPresetEntryVisible = !config || config.systemChatPresetEntryVisible !== false;
					this.userChatPresetEntryVisible = !config || config.userChatPresetEntryVisible !== false;
					if (!this.systemChatPresetEntryVisible && this.userChatPresetEntryVisible) this.presetTab = 'mine';
					if (this.systemChatPresetEntryVisible && !this.userChatPresetEntryVisible) this.presetTab = 'official';
				}).finally(() => { this.presetFeatureConfigReady = true; });
			},
			loadProfile() {
				const api = require('@/common/tavernApi.js');
				if (!api.jgEnabled()) return uni.showToast({ title: '请先配置酒馆后端', icon: 'none' });
				api.getTavernProfile(api.getClientUid(), this.cid || undefined).then((data) => {
					if (!data) return;
					this.displayName = data.display_name || ''; this.stDisplayName = data.st_display_name || ''; this.stDisplayNameOverride = data.st_display_name_override || '';
					this.effectiveStDisplayName = data.effective_st_display_name || ''; this.persona = data.persona || ''; this.conversationId = data.conversation_id || null;
					if (this.showPresetSection) this.loadChatPresets();
				}).catch((error) => this.showError(error, '加载聊天设定失败'));
			},
			loadCharacterPreview() {
				if (!this.cid) return;
				const api = require('@/common/tavernApi.js');
				api.fetchCharacter(this.cid).then((card) => {
					if (!card) return; this.characterName = card.nickname || card.name || '';
					const raw = card.chat_background_url || card.chatBackgroundUrl || ''; this.backgroundPreview = raw ? api.resolveJgAssetUrl(raw) : '';
				}).catch(() => {});
			},
			previewBackground() { if (this.backgroundPreview) uni.previewImage({ current: this.backgroundPreview, urls: [this.backgroundPreview] }); },
			loadChatPresets() {
				if (!this.showPresetSection || !this.conversationId || this.presetLoading) return Promise.resolve(false);
				const api = require('@/common/tavernApi.js'); this.presetLoading = true;
				return api.fetchTavernChatPresets(api.getClientUid(), this.conversationId).then((data) => {
					this.currentPresetId = data && data.currentPresetId ? Number(data.currentPresetId) : null;
					this.platformPresets = data && Array.isArray(data.platformPresets) ? data.platformPresets : [];
					this.myPresets = data && Array.isArray(data.myPresets) ? data.myPresets : [];
					const currentOfficial = this.platformPresets.find((item) => Number(item.id) === Number(this.currentPresetId));
					if (currentOfficial || this.officialDraftId == null) this.officialDraftId = currentOfficial ? Number(currentOfficial.id) : null;
					return true;
				}).catch((error) => { this.showError(error, '加载聊天预设失败'); return false; }).finally(() => { this.presetLoading = false; });
			},
			handleOfficialPickerChange(event) {
				if (!this.showSystemPresets || this.presetSaving) return;
				const option = this.officialPickerOptions[Number(event && event.detail && event.detail.value) || 0]; if (!option) return;
				const previousOfficialId = this.officialDraftId;
				this.officialDraftId = option.id == null ? null : Number(option.id);
				this.saveConversationPreset(this.officialDraftId).then((saved) => {
					if (!saved) this.officialDraftId = previousOfficialId;
				});
			},
			saveConversationPreset(presetId) {
				if (!this.conversationId || this.presetSaving) return Promise.resolve(false);
				const api = require('@/common/tavernApi.js'); this.presetSaving = true;
				return api.putTavernConversationPreset(api.getClientUid(), this.conversationId, presetId).then(() => {
					this.currentPresetId = presetId || null; uni.showToast({ title: '聊天预设已切换', icon: 'none' }); return true;
				}).catch((error) => { this.showError(error, '切换聊天预设失败'); return false; }).finally(() => { this.presetSaving = false; });
			},
			copyOfficialPreset() {
				if (!this.showSystemPresets || !this.showUserPresets || !this.selectedOfficial || this.presetSaving) return;
				const api = require('@/common/tavernApi.js'); this.presetSaving = true;
				api.postTavernChatPresetCopy(api.getClientUid(), this.selectedOfficial.id, this.selectedOfficial.name + ' 副本').then((row) => {
					return this.loadChatPresets().then(() => { this.presetTab = 'mine'; const item = this.myPresets.find((entry) => Number(entry.id) === Number(row && row.id)); if (item) this.openPresetEditor(item); });
				}).catch((error) => this.showError(error, '复制预设失败')).finally(() => { this.presetSaving = false; });
			},
			selectMyPreset(item) {
				if (!this.showUserPresets || !item || !item.enabled || this.presetSaving) return;
				const previousOfficialId = this.officialDraftId;
				this.officialDraftId = null;
				this.saveConversationPreset(Number(item.id)).then((saved) => {
					if (!saved) this.officialDraftId = previousOfficialId;
				});
			},
			presetSummaryText(item) {
				const s = item && item.summary || {}; const parts = [];
				if (s.temperature !== '' && s.temperature != null) parts.push('随机度 ' + s.temperature);
				if (s.topP !== '' && s.topP != null) parts.push('Top P ' + s.topP);
				if (s.maxTokens) parts.push('回复 ' + s.maxTokens);
				if (s.maxContext) parts.push('上下文 ' + s.maxContext);
				return parts.join(' · ') || '使用系统默认参数';
			},
			openPresetEditor(item) {
				if (!this.showUserPresets) return;
				const form = require('@/common/chatPresetForm.js');
				const s = item && item.summary || {};
				this.presetEditor = { visible: true, id: Number(item.id), name: item.name || '', temperature: form.fieldText(s.temperature, 1), topP: form.fieldText(s.topP, 1), maxTokens: form.fieldText(s.maxTokens, 512), maxContext: form.fieldText(s.maxContext, 8192), enabled: item.enabled !== false, saving: false, error: '' };
			},
			closePresetEditor() { if (!this.presetEditor.saving) this.presetEditor = emptyPresetEditor(); },
			savePresetEditor() {
				const e = this.presetEditor; if (!this.showUserPresets || !e.visible || e.saving) return;
				const form = require('@/common/chatPresetForm.js');
				const temperature = form.requiredNumber(e.temperature); const topP = form.requiredNumber(e.topP); const maxTokens = form.requiredNumber(e.maxTokens); const maxContext = form.requiredNumber(e.maxContext);
				const payload = { name: String(e.name || '').trim(), temperature: temperature.value, topP: topP.value, maxTokens: maxTokens.value, maxContext: maxContext.value, enabled: e.enabled === true };
				if (!payload.name) return this.$set(this.presetEditor, 'error', '请输入预设名称');
				if (!temperature.valid) return this.$set(this.presetEditor, 'error', '请输入随机度');
				if (payload.temperature < 0 || payload.temperature > 2) return this.$set(this.presetEditor, 'error', '随机度范围为 0–2');
				if (!topP.valid) return this.$set(this.presetEditor, 'error', '请输入 Top P');
				if (payload.topP < 0.01 || payload.topP > 1) return this.$set(this.presetEditor, 'error', 'Top P 范围为 0.01–1');
				if (!maxTokens.valid) return this.$set(this.presetEditor, 'error', '请输入回复长度');
				if (!Number.isInteger(payload.maxTokens) || payload.maxTokens < 64 || payload.maxTokens > 8192) return this.$set(this.presetEditor, 'error', '回复长度范围为 64–8192');
				if (!maxContext.valid) return this.$set(this.presetEditor, 'error', '请输入上下文长度');
				if (!Number.isInteger(payload.maxContext) || payload.maxContext < 2048 || payload.maxContext > 131072 || payload.maxContext < payload.maxTokens + 512) return this.$set(this.presetEditor, 'error', '上下文长度无效');
				const api = require('@/common/tavernApi.js'); this.$set(this.presetEditor, 'saving', true); this.$set(this.presetEditor, 'error', '');
				api.putTavernPrivateChatPreset(api.getClientUid(), e.id, payload).then(() => this.loadChatPresets()).then(() => { this.presetEditor = emptyPresetEditor(); uni.showToast({ title: '预设已保存', icon: 'none' }); }).catch((error) => this.$set(this.presetEditor, 'error', this.errorText(error, '保存预设失败'))).finally(() => { if (this.presetEditor.visible) this.$set(this.presetEditor, 'saving', false); });
			},
			confirmDeletePreset(item) {
				if (!this.showUserPresets) return;
				uni.showModal({ title: '删除我的预设？', content: '使用此预设的会话将恢复系统默认。', confirmText: '删除', confirmColor: '#9f2d28', success: (result) => { if (result.confirm) this.deletePreset(item); } });
			},
			deletePreset(item) {
				if (!this.showUserPresets) return;
				const api = require('@/common/tavernApi.js'); this.presetSaving = true;
				api.deleteTavernPrivateChatPreset(api.getClientUid(), item.id).then(() => this.loadChatPresets()).then(() => uni.showToast({ title: '预设已删除', icon: 'none' })).catch((error) => this.showError(error, '删除预设失败')).finally(() => { this.presetSaving = false; });
			},
			confirmClearPersona() { if (!this.persona || this.saving) return; uni.showModal({ title: '清空我的人设？', content: '保存后，所有会话将不再使用这段人设。', success: (result) => { if (result.confirm) this.persona = ''; } }); },
			save() {
				const api = require('@/common/tavernApi.js'); if (!api.jgEnabled() || this.saving) return; this.saving = true;
				const payload = { display_name: (this.displayName || '').trim(), st_display_name: (this.stDisplayName || '').trim(), persona: (this.persona || '').trim() };
				if (this.cid) payload.st_display_name_override = (this.stDisplayNameOverride || '').trim();
				api.putTavernProfile(api.getClientUid(), payload, this.cid || undefined).then(() => { this.effectiveStDisplayName = this.effectiveIdentityName; uni.showToast({ title: '聊天设定已保存', icon: 'none' }); }).catch((error) => this.showError(error, '保存聊天设定失败')).finally(() => { this.saving = false; });
			},
			errorText(error, fallback) { return require('@/common/tavernErrors.js').getTavernErrorMessage(error, fallback); },
			showError(error, fallback) { uni.showToast({ title: this.errorText(error, fallback), icon: 'none', duration: 2800 }); }
		}
	};
</script>

<style scoped lang="scss">
	.page { position: relative; height: 100vh; display: flex; flex-direction: column; overflow: hidden; color: #26394d; background: transparent; }
	.body-scroll { position: relative; z-index: 1; flex: 1; min-height: 0; }
	.body { width: 100%; max-width: 880rpx; margin: 0 auto; padding: 24rpx 24rpx calc(56rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
	.nav-save { min-width: 92rpx; padding: 18rpx 8rpx; text-align: right; color: #3f7f91; font-size: 25rpx; font-weight: 750; text-shadow: 0 1rpx 0 rgba(255,255,255,.7); }
	.nav-save--disabled { opacity: .5; pointer-events: none; }
	.identity-summary { position: relative; min-height: 144rpx; padding: 25rpx 26rpx; display: flex; align-items: center; gap: 20rpx; overflow: hidden; background: linear-gradient(135deg, rgba(255,255,255,.84) 0%, rgba(239,249,252,.72) 55%, rgba(251,239,248,.68) 100%); border: 1rpx solid rgba(255,255,255,.8); border-radius: 28rpx; box-sizing: border-box; box-shadow: 0 22rpx 52rpx rgba(67,112,142,.14), inset 0 1rpx 0 rgba(255,255,255,.9); backdrop-filter: blur(24rpx); -webkit-backdrop-filter: blur(24rpx); }
	.identity-summary::after { content: ''; position: absolute; left: 26rpx; right: 26rpx; bottom: 0; height: 3rpx; border-radius: 999rpx; background: linear-gradient(90deg, rgba(79,147,163,.72), rgba(120,148,170,.45), rgba(181,138,146,.48)); }
	.identity-copy { min-width: 0; display: flex; flex-direction: column; gap: 5rpx; }
	.identity-kicker, .identity-name, .identity-context, .section-title, .section-caption, .section-sub, .lab, .counter, .selector-label, .selector-value, .preset-summary, .preset-row-name, .preset-row-meta, .background-title, .background-name, .enable-title, .enable-sub, .editor-error { display: block; }
	.identity-kicker { color: #628090; font-size: 20rpx; font-weight: 600; }
	.identity-name { color: #244b66; font-size: 34rpx; line-height: 1.25; font-weight: 800; word-break: break-word; }
	.identity-context { color: #657f91; font-size: 22rpx; }
	.identity-mark { position: relative; width: 72rpx; height: 72rpx; flex: 0 0 auto; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: linear-gradient(145deg, #4f93a3, #78a8b2); border: 3rpx solid rgba(255,255,255,.78); box-shadow: 0 12rpx 26rpx rgba(48,103,117,.22), inset 0 1rpx 0 rgba(255,255,255,.25); }
	.identity-state { margin-left: auto; flex: 0 0 auto; display: flex; align-items: center; gap: 8rpx; padding: 8rpx 14rpx; color: #537684; background: rgba(255,255,255,.6); border: 1rpx solid rgba(79,147,163,.13); border-radius: 999rpx; font-size: 19rpx; box-shadow: 0 6rpx 16rpx rgba(67,112,142,.07); }
	.identity-state-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #55b698; box-shadow: 0 0 0 5rpx rgba(85,182,152,.12); }
	.section { position: relative; margin-top: 22rpx; padding: 30rpx 28rpx; overflow: hidden; background: rgba(255,255,255,.66); border: 1rpx solid rgba(255,255,255,.72); border-radius: 24rpx; box-shadow: 0 20rpx 48rpx rgba(67,112,142,.11), inset 0 1rpx 0 rgba(255,255,255,.84); backdrop-filter: blur(22rpx); -webkit-backdrop-filter: blur(22rpx); }
	.section::before { content: ''; position: absolute; top: 0; left: 28rpx; width: 74rpx; height: 4rpx; border-radius: 0 0 999rpx 999rpx; background: linear-gradient(90deg, #4f93a3, #9bbec5); opacity: .72; }
	.section--persona::before { background: linear-gradient(90deg, #7894aa, #b7a9c7); }
	.preset-section::before { background: linear-gradient(90deg, #b58a92, #d6adb9); }
	.section-head, .field-head, .section-actions, .editor-head, .enable-row, .editor-actions { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
	.section-heading { display: flex; align-items: center; gap: 14rpx; margin-bottom: 24rpx; }
	.section-heading--compact { margin-bottom: 0; }
	.section-heading-copy { min-width: 0; }
	.section-icon { width: 56rpx; height: 56rpx; flex: 0 0 auto; display: flex; align-items: center; justify-content: center; border-radius: 16rpx; background: linear-gradient(145deg, rgba(255,255,255,.92), rgba(226,243,247,.78)); border: 1rpx solid rgba(79,147,163,.12); box-shadow: 0 9rpx 22rpx rgba(67,112,142,.1); }
	.section-title { margin-bottom: 20rpx; font-size: 30rpx; line-height: 1.2; font-weight: 800; color: #244b66; }
	.section-title--inline { margin-bottom: 0; }
	.section-caption, .section-sub { margin-top: 6rpx; font-size: 21rpx; color: #718797; }
	.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18rpx; }
	.field { min-width: 0; margin-bottom: 20rpx; }
	.field--last { margin: 4rpx 0 0; padding-top: 24rpx; border-top: 1rpx solid rgba(79,147,163,.13); }
	.lab { margin-bottom: 11rpx; font-size: 23rpx; font-weight: 750; color: #315b72; }
	.lab--inline { margin-bottom: 0; }
	.scope-tag { padding: 6rpx 13rpx; font-size: 19rpx; color: #53788a; background: rgba(232,244,247,.82); border: 1rpx solid rgba(79,147,163,.12); border-radius: 999rpx; }
	.inp, .area, .param-input { width: 100%; border: 1rpx solid rgba(78,99,115,.13); border-radius: 18rpx; background: rgba(255,255,255,.7); color: #26394d; box-sizing: border-box; box-shadow: inset 0 2rpx 8rpx rgba(72,103,119,.035), 0 8rpx 20rpx rgba(67,112,142,.045); transition: border-color .18s ease, background .18s ease, box-shadow .18s ease, transform .18s ease; }
	.inp:focus, .area:focus, .param-input:focus { border-color: rgba(79,147,163,.5); background: rgba(255,255,255,.9); box-shadow: 0 0 0 6rpx rgba(79,147,163,.09), 0 12rpx 28rpx rgba(67,112,142,.08); }
	.inp { height: 78rpx; padding: 0 20rpx; font-size: 25rpx; }
	.area { height: 236rpx; min-height: 236rpx; padding: 20rpx; font-size: 25rpx; line-height: 1.65; }
	.inline-input { position: relative; }
	.inline-input-main { padding-right: 64rpx; }
	.clear-icon { position: absolute; right: 8rpx; top: 8rpx; width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; }
	.counter { flex: 0 0 auto; padding: 7rpx 13rpx; border-radius: 999rpx; background: rgba(255,255,255,.66); border: 1rpx solid rgba(120,148,170,.13); font-size: 19rpx; color: #6b8090; box-shadow: 0 6rpx 16rpx rgba(67,112,142,.05); }
	.section-actions { justify-content: flex-end; margin-top: 12rpx; }
	.text-action { display: flex; align-items: center; gap: 7rpx; min-height: 50rpx; padding: 0 15rpx; color: #ad5e71; background: rgba(255,246,248,.72); border-radius: 999rpx; font-size: 21rpx; }
	.text-action--disabled, .copy-button--disabled, .editor-button--disabled { opacity: .42; pointer-events: none; }
	.segment { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 24rpx; padding: 7rpx; border-radius: 20rpx; background: rgba(231,241,244,.68); border: 1rpx solid rgba(79,147,163,.09); box-shadow: inset 0 2rpx 8rpx rgba(72,103,119,.04); }
	.segment-item { height: 64rpx; display: flex; align-items: center; justify-content: center; gap: 6rpx; border-radius: 15rpx; color: #667f8f; font-size: 23rpx; font-weight: 700; transition: transform .18s ease, box-shadow .18s ease; }
	.segment-item--active { color: #2d7488; background: rgba(255,255,255,.9); box-shadow: 0 9rpx 22rpx rgba(67,112,142,.12), inset 0 1rpx 0 rgba(255,255,255,.9); }
	.preset-pane { margin-top: 18rpx; }
	.selector-row { height: 82rpx; padding: 0 20rpx; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; border: 1rpx solid rgba(78,99,115,.13); border-radius: 18rpx; background: rgba(255,255,255,.7); box-sizing: border-box; box-shadow: 0 8rpx 20rpx rgba(67,112,142,.05); }
	.selector-copy { min-width: 0; }
	.selector-label { color: #6b7f8b; font-size: 19rpx; }
	.selector-value { margin-top: 2rpx; color: #244b66; font-size: 25rpx; font-weight: 750; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.preset-summary { margin-top: 12rpx; font-size: 21rpx; line-height: 1.5; color: #5a707c; }
	.copy-button { min-height: 70rpx; margin-top: 17rpx; padding: 0 20rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; border: 1rpx solid rgba(79,147,163,.16); border-radius: 18rpx; color: #fff; background: linear-gradient(135deg, #4f93a3, #75a8b2); box-shadow: 0 13rpx 28rpx rgba(48,103,117,.17); font-size: 23rpx; font-weight: 700; }
	.copy-button--disabled { color: #87a1ad; background: rgba(242,248,249,.72); border-color: rgba(79,147,163,.1); box-shadow: inset 0 2rpx 8rpx rgba(72,103,119,.03); }
	.preset-empty { padding: 44rpx 20rpx; text-align: center; color: #718590; font-size: 23rpx; }
	.preset-row { min-height: 90rpx; margin-bottom: 10rpx; padding: 14rpx 10rpx 14rpx 17rpx; display: flex; align-items: center; gap: 12rpx; border: 1rpx solid rgba(79,147,163,.1); border-radius: 18rpx; background: rgba(255,255,255,.52); box-sizing: border-box; }
	.preset-row--selected { background: rgba(234,248,250,.78); border-color: rgba(79,147,163,.28); box-shadow: 0 8rpx 20rpx rgba(67,112,142,.07); }
	.preset-row--disabled { opacity: .56; }
	.preset-row-main { min-width: 0; flex: 1; }
	.preset-row-name { color: #203846; font-size: 24rpx; font-weight: 750; }
	.preset-row-meta { margin-top: 5rpx; color: #667d89; font-size: 20rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.preset-row-actions { flex: 0 0 auto; display: flex; gap: 4rpx; }
	.icon-button { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(235,246,248,.88); border: 1rpx solid rgba(79,147,163,.1); box-shadow: 0 7rpx 16rpx rgba(67,112,142,.07); }
	.icon-button--danger { background: rgba(255,239,243,.9); border-color: rgba(181,94,113,.1); }
	.loading-mark { width: 42rpx; height: 42rpx; display: flex; align-items: center; justify-content: center; }
	.spin { animation: spin .9s linear infinite; }
	@keyframes spin { to { transform: rotate(360deg); } }
	.background-strip { min-height: 96rpx; margin-top: 22rpx; padding: 13rpx 18rpx 13rpx 13rpx; display: flex; align-items: center; gap: 15rpx; background: rgba(255,255,255,.64); border: 1rpx solid rgba(255,255,255,.72); border-radius: 22rpx; box-sizing: border-box; box-shadow: 0 18rpx 40rpx rgba(67,112,142,.1); backdrop-filter: blur(20rpx); -webkit-backdrop-filter: blur(20rpx); }
	.background-thumb { width: 80rpx; height: 66rpx; flex: 0 0 auto; border-radius: 16rpx; box-shadow: 0 7rpx 18rpx rgba(45,74,92,.14); }
	.background-copy { min-width: 0; flex: 1; }
	.background-title { color: #203846; font-size: 23rpx; font-weight: 700; }
	.background-name { margin-top: 3rpx; color: #667d89; font-size: 20rpx; }
	.editor-mask { position: fixed; inset: 0; z-index: 2400; display: flex; align-items: flex-end; justify-content: center; padding: 20rpx 18rpx calc(18rpx + env(safe-area-inset-bottom)); background: rgba(35,55,70,.38); backdrop-filter: blur(8rpx); -webkit-backdrop-filter: blur(8rpx); box-sizing: border-box; }
	.editor-sheet { width: 100%; max-width: 760rpx; padding: 30rpx 28rpx; border-radius: 32rpx 32rpx 24rpx 24rpx; background: rgba(255,255,255,.94); border: 1rpx solid rgba(255,255,255,.9); box-sizing: border-box; box-shadow: 0 28rpx 70rpx rgba(38,57,77,.22), inset 0 1rpx 0 #fff; }
	.editor-head { margin-bottom: 22rpx; }
	.editor-title { font-size: 30rpx; font-weight: 800; color: #244b66; }
	.param-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin-top: 18rpx; }
	.param-field { min-width: 0; }
	.param-input { height: 72rpx; padding: 0 17rpx; font-size: 24rpx; }
	.enable-row { margin-top: 22rpx; padding: 18rpx 0; border-top: 1rpx solid #e2e8f0; border-bottom: 1rpx solid #e2e8f0; }
	.enable-title { color: #203846; font-size: 24rpx; font-weight: 700; }
	.enable-sub { margin-top: 4rpx; color: #6b7f8b; font-size: 19rpx; }
	.editor-error { margin-top: 14rpx; color: #a8322d; font-size: 22rpx; }
	.editor-actions { justify-content: flex-end; margin-top: 24rpx; }
	.editor-button { min-width: 132rpx; height: 70rpx; display: flex; align-items: center; justify-content: center; border-radius: 999rpx; background: linear-gradient(135deg, #4f93a3, #75a8b2); color: #fff; box-shadow: 0 12rpx 26rpx rgba(48,103,117,.18); font-size: 23rpx; font-weight: 750; }
	.editor-button--ghost { color: #597180; background: rgba(236,242,244,.9); box-shadow: none; }
	.segment-item:active, .copy-button:active, .icon-button:active, .editor-button:active { transform: scale(.97); }
	@media (max-width: 520px) { .field-grid { grid-template-columns: 1fr; gap: 0; } .identity-summary { min-height: 140rpx; } .identity-state { display: none; } }
</style>

<style>
	.ph { color: #87959d; }
</style>
