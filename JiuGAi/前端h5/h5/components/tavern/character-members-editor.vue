<template>
	<view class="studio-section">
		<view class="section-head">
			<view>
				<text class="section-title">{{ ensemble ? '角色成员' : '角色人设' }}</text>
				<text class="section-sub">{{ sectionSubText }}</text>
			</view>
			<view v-if="ensemble && members.length < 8" class="icon-action" @tap="addMember">
				<u-icon name="plus" color="#245b73" size="30"></u-icon>
			</view>
		</view>

		<view v-for="(member, index) in members" :key="member.clientKey" class="member-row" :class="{ 'member-row--single': !ensemble }">
			<view v-if="ensemble" class="member-head">
				<view class="member-avatar" @tap="pickAvatar(index)">
					<image v-if="member.avatarUrl" :src="resolveUrl(member.avatarUrl)" mode="aspectFill"></image>
					<u-icon v-else name="camera" color="#607b89" size="36"></u-icon>
				</view>
				<view class="member-head-copy">
					<text class="member-order">{{ index === 0 ? '主角色' : '成员 ' + (index + 1) }}</text>
					<text class="member-order-tip">{{ index === 0 ? '负责代表这张角色卡' : '共同参与剧情' }}</text>
				</view>
				<view class="member-row-actions">
					<u-icon v-if="index > 0" name="arrow-up" color="#607b89" size="27" @tap.stop="moveMember(index, -1)"></u-icon>
					<u-icon v-if="index < members.length - 1" name="arrow-down" color="#607b89" size="27" @tap.stop="moveMember(index, 1)"></u-icon>
					<u-icon v-if="members.length > 2" name="trash" color="#9b5c67" size="28" @tap.stop="removeMember(index)"></u-icon>
				</view>
			</view>

			<view v-if="ensemble" class="identity-fields">
				<view class="identity-field">
					<text class="field-label">角色名称</text>
					<input v-model="member.name" maxlength="64" class="identity-input identity-input--name" placeholder="输入角色名称" />
				</view>
				<view class="identity-field">
					<text class="field-label">身份或一句话介绍</text>
					<input v-model="member.tagline" maxlength="255" class="identity-input" placeholder="例如：冷静可靠的调查员" />
				</view>
			</view>

			<view class="persona-head">
				<text class="field-label">{{ ensemble ? '成员人设' : '性格与人设' }}</text>
				<text class="persona-count">人设字数 {{ (member.persona || '').length }}/12000</text>
			</view>
			<textarea
				v-model="member.persona"
				maxlength="12000"
				auto-height
				class="member-persona"
				:placeholder="ensemble ? '性格、说话方式、经历、习惯，以及与其他成员的关系' : '写清楚角色的性格、说话方式、经历、习惯和行为边界'"
			></textarea>

			<view v-if="imageGenerationEnabled || voiceFeatureEnabled" class="member-tools">
				<view v-if="imageGenerationEnabled" class="member-reference" @tap="pickReference(index)">
					<image v-if="member.imageReferenceUrl || member.avatarUrl" :src="resolveUrl(member.imageReferenceUrl || member.avatarUrl)" mode="aspectFill"></image>
					<u-icon v-else name="photo" color="#607b89" size="26"></u-icon>
					<text>{{ member.imageReferenceUrl ? '已设置生图参考' : '生图跟随角色头像' }}</text>
				</view>
				<view v-if="voiceFeatureEnabled" class="member-advanced-action" @tap="toggleMemberSettings(member)">
					<text>{{ voiceOverrideEnabled(member) ? '专属音色' : '音色设置' }}</text>
					<u-icon :name="member.settingsOpen ? 'arrow-up' : 'arrow-down'" color="#607b89" size="20"></u-icon>
				</view>
			</view>

			<view v-if="voiceFeatureEnabled && member.settingsOpen" class="member-settings">
				<view class="voice-head">
					<view class="voice-head-copy">
						<text class="voice-title">{{ ensemble ? '成员专属音色' : '角色专属音色' }}</text>
						<text class="voice-state">{{ memberVoiceSummary(member) }}</text>
					</view>
					<view class="catalog-action" :class="{ 'catalog-action--disabled': voiceCatalogLoading }" @tap="loadVoiceCatalog(true)">
						<u-icon name="reload" color="#2d657a" size="22"></u-icon>
						<text>{{ voiceCatalogLoading ? '读取中' : '刷新音色' }}</text>
					</view>
				</view>

				<text class="member-settings-tip">TTS 模型由 AI 设置统一管理，这里只选择角色使用的音色。</text>
				<text v-if="voiceCatalogModelName" class="model-summary">当前模型：{{ voiceCatalogModelName }}</text>
				<text v-if="voiceCatalogMessage" class="catalog-message" :class="{ 'catalog-message--error': voiceCatalogError }">{{ voiceCatalogMessage }}</text>

				<view class="settings-field">
					<text class="settings-label">默认</text>
					<view class="voice-choice" :class="{ 'voice-choice--on': !voiceOverrideEnabled(member) }" @tap="selectFollowGlobal(member)">
						<view class="voice-choice-copy">
							<text class="voice-choice-name">跟随全局音色</text>
							<text class="voice-choice-tip">使用 AI 设置或平台默认音色</text>
						</view>
						<u-icon v-if="!voiceOverrideEnabled(member)" name="checkbox-mark" color="#277268" size="25"></u-icon>
					</view>
				</view>

				<view v-if="voiceCatalogPrivateVoices.length" class="settings-field">
					<view class="settings-label-row">
						<text class="settings-label">我的自建音色</text>
						<text class="voice-count">{{ voiceCatalogPrivateVoices.length }}</text>
					</view>
					<scroll-view
						class="voice-catalog-scroll"
						:class="{ 'voice-catalog-scroll--private': voiceCatalogPrivateVoices.length > 4 }"
						scroll-y
						:show-scrollbar="false"
					>
						<view class="private-voice-list">
							<view
								v-for="voice in voiceCatalogPrivateVoices"
								:key="'private_voice_' + voice.id"
								class="voice-choice"
								:class="{
									'voice-choice--on': privateVoiceSelected(member, voice),
									'voice-choice--disabled': !voiceCatalogCustomMode || !voice.available
								}"
								@tap="selectPrivateVoice(member, voice)"
							>
								<view class="voice-choice-copy">
									<text class="voice-choice-name">{{ voice.displayName || ('音色 ' + voice.id) }}</text>
									<text class="voice-choice-tip">{{ privateVoiceStatusText(voice) }}</text>
								</view>
								<u-icon v-if="privateVoiceSelected(member, voice)" name="checkbox-mark" color="#277268" size="25"></u-icon>
							</view>
						</view>
					</scroll-view>
				</view>

				<view v-if="voicePresets(member).length" class="settings-field">
					<view class="settings-label-row">
						<text class="settings-label">平台常用音色</text>
						<text class="voice-count">{{ voicePresets(member).length }}</text>
					</view>
					<scroll-view
						class="voice-catalog-scroll"
						:class="{ 'voice-catalog-scroll--presets': voicePresets(member).length > 6 }"
						scroll-y
						:show-scrollbar="false"
					>
						<view class="option-list">
							<view
								v-for="voice in voicePresets(member)"
								:key="'member_voice_' + voice"
								class="option-chip"
								:class="{ 'option-chip--on': voiceValue(member, 'ttsVoiceName').toLowerCase() === voice.toLowerCase() }"
								@tap="selectVoiceName(member, voice)"
							>{{ voice }}</view>
						</view>
					</scroll-view>
				</view>

				<view v-if="selectableVoiceTemplates.length" class="settings-field settings-field--templates">
					<view class="settings-label-row">
						<text class="settings-label">平台提供音色</text>
						<text class="voice-count">{{ selectableVoiceTemplates.length }}</text>
					</view>
					<scroll-view
						class="voice-catalog-scroll"
						:class="{ 'voice-catalog-scroll--templates': selectableVoiceTemplates.length > 4 }"
						scroll-y
						:show-scrollbar="false"
					>
						<view class="template-list">
							<view
								v-for="item in selectableVoiceTemplates"
								:key="'member_template_' + item.code"
								class="template-option"
								:class="{
									'template-option--on': voiceValue(member, 'ttsVoiceTemplateCode') === item.code,
									'template-option--disabled': !templateVoiceSelectable(item)
								}"
								@tap="selectVoiceTemplate(member, item)"
							>
								<text class="template-name">{{ item.displayName || item.code }}</text>
								<text class="template-model">{{ item.statusText || '首次使用时自动准备' }}</text>
							</view>
						</view>
					</scroll-view>
				</view>

				<text
					v-if="voiceCatalogLoaded && !voiceCatalogLoading && !hasVoiceCatalogEntries"
					class="catalog-empty"
				>音色接口暂未返回可选项，当前继续跟随全局音色。</text>

				<view v-if="voiceCatalogCustomMode" class="settings-field settings-field--advanced">
					<text class="settings-label">其他音色 ID（高级）</text>
					<input :value="voiceValue(member, 'ttsVoiceName')" maxlength="255" class="settings-input" placeholder="仅在列表没有目标音色时手动填写" @input="updateManualVoice(member, $event.detail.value)" />
				</view>
			</view>
		</view>
	</view>
</template>

<script>
const OPENAI_TTS_VOICES = Object.freeze(['alloy', 'nova', 'shimmer', 'echo', 'fable', 'onyx']);
const SILICONFLOW_TTS_VOICES = Object.freeze(['alex', 'benjamin', 'charles', 'david', 'anna', 'bella', 'claire', 'diana']);

export default {
	name: 'CharacterMembersEditor',
	props: {
		value: { type: Array, default: () => [] },
		cardType: { type: String, default: 'SINGLE' },
		characterId: { type: [Number, String], default: 0 },
		voiceFeatureEnabled: { type: Boolean, default: true },
		imageGenerationEnabled: { type: Boolean, default: true }
	},
	data() {
		return {
			voiceCatalogLoading: false,
			voiceCatalogLoaded: false,
			voiceCatalogError: '',
			voiceCatalogMessage: '',
			voiceCatalogModelName: '',
			voiceCatalogProviderSource: '',
			voiceCatalogCustomMode: false,
			voiceCatalogPresets: [],
			voiceCatalogTemplates: [],
			voiceCatalogPrivateVoices: []
		};
	},
	computed: {
		members() { return this.value; },
		ensemble() { return this.cardType === 'ENSEMBLE'; },
		sectionSubText() {
			const capabilities = this.voiceFeatureEnabled && this.imageGenerationEnabled
				? '人设、形象和音色'
				: this.voiceFeatureEnabled
					? '人设和音色'
					: this.imageGenerationEnabled ? '人设和形象' : '人设';
			if (this.ensemble) {
				return '每名成员可以拥有独立' + capabilities;
			}
			return '完善这个角色的' + capabilities.replace('人设', '性格');
		},
		selectableVoiceTemplates() {
			return this.voiceCatalogTemplates.filter(item => item && item.code);
		},
		hasVoiceCatalogEntries() {
			return this.voiceCatalogPrivateVoices.length > 0
				|| this.voiceCatalogPresets.length > 0
				|| this.selectableVoiceTemplates.length > 0;
		}
	},
	methods: {
		resolveUrl(value) {
			return require('@/common/tavernApi.js').resolveJgAssetUrl(value) || value || '';
		},
		newMember() {
			return {
				clientKey: 'member_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7),
				name: '', tagline: '', persona: '', avatarUrl: '', voiceConfigJson: '',
				imageReferenceUrl: '', primaryMember: false
			};
		},
		addMember() {
			this.members.push(this.newMember());
			this.$emit('input', this.members);
		},
		removeMember(index) {
			this.members.splice(index, 1);
			this.$emit('input', this.members);
		},
		moveMember(index, offset) {
			const target = index + offset;
			if (target < 0 || target >= this.members.length) return;
			const moved = this.members.splice(index, 1)[0];
			this.members.splice(target, 0, moved);
			this.members.forEach((member, memberIndex) => { member.primaryMember = memberIndex === 0; });
			this.$emit('input', this.members);
		},
		pickAvatar(index) { this.$emit('pick-avatar', index); },
		pickReference(index) {
			if (!this.imageGenerationEnabled) return;
			this.$emit('pick-reference', index);
		},
		toggleMemberSettings(member) {
			if (!this.voiceFeatureEnabled) return;
			const nextOpen = !member.settingsOpen;
			this.$set(member, 'settingsOpen', nextOpen);
			this.$emit('input', this.members);
			if (nextOpen && !this.voiceCatalogLoading) {
				if (!this.voiceCatalogLoaded) this.loadVoiceCatalog(false);
				else this.loadMemberBindings(require('@/common/tavernApi.js'), require('@/common/tavernApi.js').getClientUid()).catch(() => {});
			}
		},
		readVoice(member) {
			try {
				const value = JSON.parse(String(member && member.voiceConfigJson || '').trim() || '{}');
				return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
			} catch (e) {
				return {};
			}
		},
		writeVoice(member, value) {
			const next = Object.assign({}, value && typeof value === 'object' ? value : {});
			delete next.ttsModelName;
			if (this.voiceCatalogCustomMode && (next.ttsVoiceName || next.ttsVoiceTemplateCode)) {
				next.ttsProviderSource = String(this.voiceCatalogProviderSource || '').trim().toLowerCase();
			} else {
				delete next.ttsProviderSource;
			}
			this.$set(member, 'voiceConfigJson', Object.keys(next).length ? JSON.stringify(next) : '');
			this.$emit('input', this.members);
		},
		voiceValue(member, key) {
			return String(this.readVoice(member)[key] || '');
		},
		voiceOverrideEnabled(member) {
			return this.privateVoiceId(member) > 0
				|| !!this.voiceValue(member, 'ttsVoiceName').trim()
				|| !!this.voiceValue(member, 'ttsVoiceTemplateCode').trim();
		},
		privateVoiceId(member) {
			return Math.max(0, Math.floor(Number(member && member.ttsUserVoiceId) || 0));
		},
		privateVoiceSelected(member, voice) {
			return this.privateVoiceId(member) > 0 && this.privateVoiceId(member) === Number(voice && voice.id);
		},
		privateVoiceById(id) {
			const target = Math.max(0, Math.floor(Number(id) || 0));
			return this.voiceCatalogPrivateVoices.find(item => Number(item && item.id) === target) || null;
		},
		memberVoiceSummary(member) {
			const privateId = this.privateVoiceId(member);
			if (privateId > 0) {
				const voice = this.privateVoiceById(privateId);
				return voice ? '我的音色：' + (voice.displayName || privateId) : '已绑定我的自建音色';
			}
			const templateCode = this.voiceValue(member, 'ttsVoiceTemplateCode').trim();
			if (templateCode) {
				const template = this.voiceCatalogTemplates.find(item => String(item && item.code) === templateCode);
				return '平台音色：' + String(template && template.displayName || templateCode);
			}
			const voiceName = this.voiceValue(member, 'ttsVoiceName').trim();
			return voiceName ? '平台音色：' + voiceName : '当前跟随全局音色设置';
		},
		markPrivateBinding(member, voiceId) {
			this.$set(member, 'ttsUserVoiceId', Math.max(0, Math.floor(Number(voiceId) || 0)));
			this.$set(member, 'ttsUserVoiceBindingLoaded', true);
			this.$set(member, 'ttsUserVoiceBindingDirty', true);
			this.$emit('input', this.members);
		},
		preparePublicVoice(member) {
			this.markPrivateBinding(member, 0);
		},
		selectVoiceName(member, voice) {
			this.preparePublicVoice(member);
			this.writeVoice(member, { ttsVoiceName: String(voice || '').trim() });
		},
		selectVoiceTemplate(member, item) {
			if (!this.templateVoiceSelectable(item)) return;
			this.preparePublicVoice(member);
			this.writeVoice(member, { ttsVoiceTemplateCode: String(item.code) });
		},
		selectPrivateVoice(member, voice) {
			if (!this.voiceCatalogCustomMode || !voice || !voice.available) return;
			this.markPrivateBinding(member, voice.id);
			this.writeVoice(member, {});
		},
		privateVoiceStatusText(voice) {
			if (!this.voiceCatalogCustomMode) return '切换到自己的 TTS API 后可用';
			return voice && voice.available ? '使用你的 TTS API' : String(voice && voice.statusText || '当前不可用');
		},
		templateVoiceSelectable(item) {
			const status = String(item && item.statusCode || '').trim().toLowerCase();
			return !!(item && item.code) && status.indexOf('requires_') !== 0 && status !== 'failed';
		},
		selectFollowGlobal(member) {
			this.markPrivateBinding(member, 0);
			this.writeVoice(member, {});
		},
		updateManualVoice(member, value) {
			this.preparePublicVoice(member);
			const text = String(value || '').trim();
			this.writeVoice(member, text ? { ttsVoiceName: text } : {});
		},
		voicePresets(member) {
			if (this.voiceCatalogPresets.length) return this.voiceCatalogPresets.slice();
			const model = String(this.voiceCatalogModelName || '').toLowerCase();
			if (/(gpt-4o-mini-tts|tts-1|tts-1-hd|\/tts|openai\/.*tts)/.test(model)) return OPENAI_TTS_VOICES.slice();
			if (/(cosyvoice|fish-speech|gpt-sovits)/.test(model)) return SILICONFLOW_TTS_VOICES.slice();
			return [];
		},
		providerLabel(state) {
			const value = String(state && (state.effectiveTtsProviderSource || state.ttsProviderSource || state.providerSource) || '').trim();
			const options = Array.isArray(state && state.providerOptions) ? state.providerOptions : [];
			const matched = options.find(item => item && item.value === value);
			return String(matched && matched.label || value || '已配置平台');
		},
		loadMemberBindings(tavernApi, clientUid) {
			const characterId = Math.max(0, Math.floor(Number(this.characterId) || 0));
			if (!characterId) return Promise.resolve([]);
			const targets = this.ensemble ? this.members : this.members.slice(0, 1);
			return Promise.all(targets.map(member => {
				const memberId = Math.max(0, Math.floor(Number(member && member.id) || 0));
				const scope = this.ensemble
					? { scopeType: 'MEMBER', characterId, memberId }
					: { scopeType: 'CHARACTER', characterId, memberId: 0 };
				if (this.ensemble && !memberId) return Promise.resolve(null);
				return tavernApi.getUserTtsVoiceBinding(clientUid, scope).then(binding => {
					if (member.ttsUserVoiceBindingDirty) return binding;
					this.$set(member, 'ttsUserVoiceId', Math.max(0, Math.floor(Number(binding && binding.voiceId) || 0)));
					this.$set(member, 'ttsUserVoiceBindingLoaded', true);
					this.$set(member, 'ttsUserVoiceBindingDirty', false);
					return binding;
				});
			}));
		},
		loadVoiceCatalog(showToast) {
			if (!this.voiceFeatureEnabled) return Promise.resolve([]);
			if (this.voiceCatalogLoading) return Promise.resolve([]);
			const tavernApi = require('@/common/tavernApi.js');
			this.voiceCatalogLoading = true;
			this.voiceCatalogError = '';
			this.voiceCatalogMessage = '正在读取可用音色...';
			const clientUid = tavernApi.getClientUid();
			const settle = promise => Promise.resolve(promise)
				.then(data => ({ data, error: null }))
				.catch(error => ({ data: null, error }));
			const errors = [];
			const summaries = [];
			return Promise.all([
				settle(tavernApi.getTavernUserAiProvider(clientUid)),
				settle(tavernApi.getUserTtsVoices(clientUid))
			])
				.then(results => {
					const providerResult = results[0] || {};
					const voiceResult = results[1] || {};
					if (providerResult.error) {
						errors.push(String(providerResult.error.message || '官方音色读取失败'));
					} else {
						const providerState = providerResult.data && typeof providerResult.data === 'object' ? providerResult.data : {};
						this.voiceCatalogCustomMode = providerState.mode === 'custom';
						this.voiceCatalogProviderSource = String(
							providerState.effectiveTtsProviderSource || providerState.ttsProviderSource || providerState.providerSource || ''
						).trim().toLowerCase();
						this.voiceCatalogModelName = String(providerState.ttsModelName || '').trim();
						this.voiceCatalogPresets = (Array.isArray(providerState.ttsVoicePresets) ? providerState.ttsVoicePresets : [])
							.map(voice => String(voice || '').trim().toLowerCase())
							.filter((voice, index, list) => voice && list.indexOf(voice) === index);
						this.voiceCatalogTemplates = (Array.isArray(providerState.ttsVoiceTemplates) ? providerState.ttsVoiceTemplates : [])
							.filter(item => item && item.code);
						const officialCount = this.voiceCatalogPresets.length + this.voiceCatalogTemplates.length;
						summaries.push((this.voiceCatalogCustomMode ? this.providerLabel(providerState) : '官方平台') + '音色 ' + officialCount + ' 个');
					}
					if (voiceResult.error) {
						errors.push(String(voiceResult.error.message || '我的音色读取失败'));
					} else {
						const voiceState = voiceResult.data && typeof voiceResult.data === 'object' ? voiceResult.data : {};
						this.voiceCatalogPrivateVoices = (Array.isArray(voiceState.voices) ? voiceState.voices : [])
							.filter(item => item && Number(item.id) > 0);
						const availablePrivateCount = this.voiceCatalogPrivateVoices.filter(item => item.available).length;
						summaries.push('我的可用音色 ' + availablePrivateCount + ' 个');
					}
					return settle(this.loadMemberBindings(tavernApi, clientUid));
				})
				.then(bindingResult => {
					if (bindingResult && bindingResult.error) {
						errors.push(String(bindingResult.error.message || '成员音色绑定读取失败'));
					}
					this.voiceCatalogError = errors.join('；');
					this.voiceCatalogMessage = summaries.concat(errors).join('；') || '音色列表为空';
					if (showToast) uni.showToast({ title: this.voiceCatalogMessage, icon: 'none', duration: errors.length ? 3000 : 1800 });
					return this.voiceCatalogPrivateVoices;
				})
				.catch(error => {
					this.voiceCatalogError = String(error && error.message || '读取音色失败');
					this.voiceCatalogMessage = this.voiceCatalogError;
					if (showToast) uni.showToast({ title: this.voiceCatalogMessage, icon: 'none', duration: 3000 });
					return [];
				})
				.finally(() => {
					this.voiceCatalogLoaded = true;
					this.voiceCatalogLoading = false;
				});
		}
	}
};
</script>

<style scoped lang="scss">
.studio-section { color: #17394b; }
.section-head, .member-head, .persona-head, .member-tools, .voice-head { display: flex; align-items: center; justify-content: space-between; }
.section-head { margin-bottom: 20rpx; }
.section-title { display: block; font-size: 30rpx; font-weight: 750; }
.section-sub { display: block; margin-top: 6rpx; font-size: 22rpx; color: #607b89; }
.icon-action { width: 64rpx; height: 64rpx; border-radius: 22rpx; display: flex; align-items: center; justify-content: center; background: rgba(228, 245, 249, .82); border: 1rpx solid rgba(79, 147, 163, .14); box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, .1); }
.member-row { padding: 24rpx; border: 1rpx solid rgba(255, 255, 255, .8); border-radius: 28rpx; background: rgba(255, 255, 255, .48); box-shadow: 0 16rpx 34rpx rgba(52, 94, 118, .08); }
.member-row + .member-row { margin-top: 18rpx; }
.member-row:first-of-type { margin-top: 0; }
.member-row--single { padding: 4rpx 0 0; border: 0; border-radius: 0; background: transparent; box-shadow: none; }
.member-head { gap: 16rpx; }
.member-avatar { width: 112rpx; height: 112rpx; flex: 0 0 112rpx; border-radius: 28rpx; overflow: hidden; display: flex; align-items: center; justify-content: center; background: rgba(225, 238, 244, 0.78); border: 2rpx solid rgba(255, 255, 255, .88); box-shadow: 0 12rpx 26rpx rgba(52, 94, 118, .12); }
.member-avatar image { width: 100%; height: 100%; }
.member-head-copy { flex: 1; min-width: 0; }
.member-order { display: block; font-size: 27rpx; font-weight: 700; color: #17394b; }
.member-order-tip { display: block; margin-top: 8rpx; font-size: 21rpx; color: #708692; }
.member-row-actions { flex: 0 0 112rpx; display: flex; align-items: center; justify-content: flex-end; gap: 20rpx; }
.identity-fields { margin-top: 22rpx; display: grid; grid-template-columns: 1fr; gap: 16rpx; }
.identity-field { min-width: 0; }
.field-label, .settings-label { display: block; margin-bottom: 10rpx; font-size: 22rpx; font-weight: 650; color: #446475; }
.identity-input { width: 100%; height: 78rpx; padding: 0 20rpx; box-sizing: border-box; border: 1rpx solid rgba(79,147,163,.16); border-radius: 20rpx; background: rgba(255,255,255,.72); color: #23485b; font-size: 25rpx; box-shadow: inset 0 1rpx 0 rgba(255,255,255,.88); }
.identity-input--name { font-size: 27rpx; font-weight: 650; color: #17394b; }
.persona-head { margin-top: 24rpx; align-items: flex-end; gap: 16rpx; }
.persona-head .field-label { margin-bottom: 0; }
.persona-count { flex-shrink: 0; font-size: 20rpx; color: #718792; }
.member-persona { width: 100%; min-height: 230rpx; box-sizing: border-box; margin-top: 12rpx; padding: 22rpx; border-radius: 22rpx; background: rgba(255,255,255,.72); border: 1rpx solid rgba(79,147,163,.16); color: #17394b; font-size: 25rpx; line-height: 1.68; box-shadow: inset 0 1rpx 0 rgba(255,255,255,.88); }
.member-tools { gap: 16rpx; margin-top: 16rpx; }
.member-reference, .member-advanced-action { display: inline-flex; align-items: center; gap: 8rpx; min-height: 54rpx; font-size: 22rpx; color: #426676; }
.member-reference image { width: 42rpx; height: 42rpx; border-radius: 14rpx; }
.member-reference { min-width: 0; }
.member-advanced-action { margin-left: auto; white-space: nowrap; }
.member-settings { margin-top: 18rpx; padding: 22rpx; border: 1rpx solid rgba(79,147,163,.12); border-radius: 24rpx; background: rgba(232,247,250,.5); }
.voice-head { gap: 18rpx; align-items: flex-start; }
.voice-head-copy { flex: 1; min-width: 0; }
.voice-title { display: block; font-size: 25rpx; font-weight: 700; color: #244d60; }
.voice-state { display: block; margin-top: 6rpx; font-size: 20rpx; color: #718792; line-height: 1.45; }
.catalog-action { flex-shrink: 0; min-height: 48rpx; display: inline-flex; align-items: center; gap: 7rpx; font-size: 21rpx; color: #2d657a; }
.catalog-action--disabled { opacity: .55; }
.member-settings-tip, .catalog-message, .model-summary { display: block; margin-top: 12rpx; font-size: 20rpx; line-height: 1.5; color: #657f8c; }
.model-summary { color: #426676; }
.catalog-message { color: #2d657a; }
.catalog-message--error { color: #9b4f5a; }
.catalog-empty { display: block; margin-top: 18rpx; padding: 16rpx 18rpx; border-radius: 18rpx; background: rgba(255,255,255,.5); color: #718792; font-size: 20rpx; line-height: 1.5; }
.settings-field { margin-top: 20rpx; }
.settings-field--templates { margin-top: 22rpx; }
.settings-field--advanced { padding-top: 4rpx; }
.settings-input { width: 100%; height: 74rpx; padding: 0 18rpx; box-sizing: border-box; border: 1rpx solid rgba(79,147,163,.16); border-radius: 18rpx; background: rgba(255,255,255,.7); color: #244d60; font-size: 24rpx; }
.settings-label-row { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin-bottom: 10rpx; }
.settings-label-row .settings-label { margin-bottom: 0; }
.voice-count { min-width: 38rpx; height: 32rpx; padding: 0 10rpx; box-sizing: border-box; border-radius: 999rpx; background: rgba(255,255,255,.7); color: #62808e; font-size: 18rpx; line-height: 32rpx; text-align: center; box-shadow: inset 0 1rpx 0 rgba(255,255,255,.88); }
.voice-catalog-scroll { width: 100%; }
.voice-catalog-scroll--private { height: 310rpx; }
.voice-catalog-scroll--presets { height: 230rpx; }
.voice-catalog-scroll--templates { height: 290rpx; }
.option-list { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10rpx; padding: 2rpx; box-sizing: border-box; }
.settings-label--full { width: 100%; margin-bottom: 0; }
.option-chip { width: 100%; min-width: 0; height: 60rpx; padding: 0 16rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; border: 1rpx solid rgba(79,147,163,.18); border-radius: 18rpx; color: #4c6d7c; background: rgba(255,255,255,.58); font-size: 21rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; box-shadow: 0 5rpx 12rpx rgba(69,105,119,.05), inset 0 1rpx 0 rgba(255,255,255,.82); }
.option-chip--on { border-color: #39788e; color: #17495d; background: rgba(222,239,244,.88); font-weight: 650; }
.private-voice-list { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10rpx; padding: 2rpx; box-sizing: border-box; }
.voice-choice { min-height: 76rpx; padding: 14rpx 18rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: space-between; gap: 14rpx; border: 1rpx solid rgba(79,147,163,.16); border-radius: 20rpx; background: rgba(255,255,255,.58); }
.voice-choice--on { border-color: #39788e; background: rgba(222,239,244,.88); }
.voice-choice--disabled { opacity: .5; }
.voice-choice-copy { flex: 1; min-width: 0; }
.voice-choice-name { display: block; font-size: 23rpx; font-weight: 650; color: #284f61; overflow-wrap: anywhere; }
.voice-choice-tip { display: block; margin-top: 5rpx; font-size: 19rpx; line-height: 1.4; color: #718792; }
.template-list { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10rpx; padding: 2rpx; box-sizing: border-box; }
.template-option { min-width: 0; padding: 16rpx 18rpx; border: 1rpx solid rgba(79,147,163,.16); border-radius: 20rpx; background: rgba(255,255,255,.58); }
.template-option--on { border-color: #39788e; background: rgba(222,239,244,.88); }
.template-option--disabled { opacity: .5; }
.template-name { display: block; font-size: 22rpx; font-weight: 650; color: #284f61; overflow-wrap: anywhere; }
.template-model { display: block; margin-top: 6rpx; font-size: 19rpx; color: #718792; overflow-wrap: anywhere; }
.clear-voice { margin-top: 20rpx; min-height: 52rpx; display: inline-flex; align-items: center; gap: 7rpx; color: #657b86; font-size: 21rpx; }
</style>
