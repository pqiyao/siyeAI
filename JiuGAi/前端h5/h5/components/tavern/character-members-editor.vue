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
					<text class="settings-label">我的自建音色</text>
					<view class="private-voice-list">
						<view
							v-for="voice in voiceCatalogPrivateVoices"
							:key="'private_voice_' + voice.id"
							class="voice-choice"
							:class="{ 'voice-choice--on': privateVoiceSelected(member, voice), 'voice-choice--disabled': !voice.available }"
							@tap="selectPrivateVoice(member, voice)"
						>
							<view class="voice-choice-copy">
								<text class="voice-choice-name">{{ voice.displayName || ('音色 ' + voice.id) }}</text>
								<text class="voice-choice-tip">{{ voice.available ? '使用你的硅基流动 API' : (voice.statusText || '当前不可用') }}</text>
							</view>
							<u-icon v-if="privateVoiceSelected(member, voice)" name="checkbox-mark" color="#277268" size="25"></u-icon>
						</view>
					</view>
				</view>

				<view v-if="voicePresets(member).length" class="option-list">
					<text class="settings-label settings-label--full">平台常用音色</text>
					<view
						v-for="voice in voicePresets(member)"
						:key="'member_voice_' + voice"
						class="option-chip"
						:class="{ 'option-chip--on': voiceValue(member, 'ttsVoiceName').toLowerCase() === voice.toLowerCase() }"
						@tap="selectVoiceName(member, voice)"
					>{{ voice }}</view>
				</view>

				<view v-if="selectableVoiceTemplates.length" class="settings-field settings-field--templates">
					<text class="settings-label">平台提供音色</text>
					<view class="template-list">
						<view
							v-for="item in selectableVoiceTemplates"
							:key="'member_template_' + item.code"
							class="template-option"
							:class="{ 'template-option--on': voiceValue(member, 'ttsVoiceTemplateCode') === item.code }"
							@tap="selectVoiceTemplate(member, item)"
						>
							<text class="template-name">{{ item.displayName || item.code }}</text>
							<text class="template-model">{{ item.statusText || '首次使用时自动准备' }}</text>
						</view>
					</view>
				</view>

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
			return this.voiceCatalogTemplates.filter(item => {
				const status = String(item && item.statusCode || '').trim().toLowerCase();
				return status.indexOf('requires_') !== 0;
			});
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
			if (next.ttsVoiceName || next.ttsVoiceTemplateCode) {
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
			if (!item || !item.code) return;
			this.preparePublicVoice(member);
			this.writeVoice(member, { ttsVoiceTemplateCode: String(item.code) });
		},
		selectPrivateVoice(member, voice) {
			if (!voice || !voice.available) return;
			this.markPrivateBinding(member, voice.id);
			this.writeVoice(member, {});
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
			if (!this.voiceCatalogCustomMode) return [];
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
			return Promise.all([
				tavernApi.getTavernUserAiProvider(clientUid),
				tavernApi.getUserTtsVoices(clientUid).catch(() => ({ voices: [] }))
			])
				.then(results => {
					const providerState = results[0] && typeof results[0] === 'object' ? results[0] : {};
					const voiceState = results[1] && typeof results[1] === 'object' ? results[1] : {};
					this.voiceCatalogCustomMode = providerState.mode === 'custom';
					this.voiceCatalogProviderSource = String(
						providerState.effectiveTtsProviderSource || providerState.ttsProviderSource || providerState.providerSource || ''
					).trim().toLowerCase();
					this.voiceCatalogModelName = String(providerState.ttsModelName || '').trim();
					this.voiceCatalogTemplates = (Array.isArray(providerState.ttsVoiceTemplates) ? providerState.ttsVoiceTemplates : [])
						.filter(item => item && item.code);
					this.voiceCatalogPrivateVoices = (Array.isArray(voiceState.voices) ? voiceState.voices : [])
						.filter(item => item && Number(item.id) > 0);
					const availablePrivateCount = this.voiceCatalogPrivateVoices.filter(item => item.available).length;
					const label = this.providerLabel(providerState);
					this.voiceCatalogMessage = this.voiceCatalogCustomMode
						? '已读取 ' + label + ' 音色；我的可用音色 ' + availablePrivateCount + ' 个'
						: '当前使用系统语音，角色会跟随平台统一模型和默认音色。';
					return this.loadMemberBindings(tavernApi, clientUid);
				})
				.then(() => {
					if (showToast) uni.showToast({ title: this.voiceCatalogMessage, icon: 'none' });
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
.icon-action { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; }
.member-row { padding: 24rpx 0 28rpx; border-top: 1rpx solid rgba(72, 111, 132, 0.16); }
.member-row:first-of-type, .member-row--single { border-top: 0; padding-top: 4rpx; }
.member-head { gap: 16rpx; }
.member-avatar { width: 112rpx; height: 112rpx; flex: 0 0 112rpx; border-radius: 10rpx; overflow: hidden; display: flex; align-items: center; justify-content: center; background: rgba(225, 238, 244, 0.78); border: 1rpx solid rgba(72, 111, 132, 0.22); }
.member-avatar image { width: 100%; height: 100%; }
.member-head-copy { flex: 1; min-width: 0; }
.member-order { display: block; font-size: 27rpx; font-weight: 700; color: #17394b; }
.member-order-tip { display: block; margin-top: 8rpx; font-size: 21rpx; color: #708692; }
.member-row-actions { flex: 0 0 112rpx; display: flex; align-items: center; justify-content: flex-end; gap: 20rpx; }
.identity-fields { margin-top: 22rpx; display: grid; grid-template-columns: 1fr; gap: 16rpx; }
.identity-field { min-width: 0; }
.field-label, .settings-label { display: block; margin-bottom: 10rpx; font-size: 22rpx; font-weight: 650; color: #446475; }
.identity-input { width: 100%; height: 76rpx; padding: 0 18rpx; box-sizing: border-box; border: 1rpx solid rgba(72,111,132,.2); border-radius: 6rpx; background: rgba(255,255,255,.68); color: #23485b; font-size: 25rpx; }
.identity-input--name { font-size: 27rpx; font-weight: 650; color: #17394b; }
.persona-head { margin-top: 24rpx; align-items: flex-end; gap: 16rpx; }
.persona-head .field-label { margin-bottom: 0; }
.persona-count { flex-shrink: 0; font-size: 20rpx; color: #718792; }
.member-persona { width: 100%; min-height: 230rpx; box-sizing: border-box; margin-top: 12rpx; padding: 20rpx; border-radius: 6rpx; background: rgba(255,255,255,.68); border: 1rpx solid rgba(72,111,132,.2); color: #17394b; font-size: 25rpx; line-height: 1.65; }
.member-tools { gap: 16rpx; margin-top: 16rpx; }
.member-reference, .member-advanced-action { display: inline-flex; align-items: center; gap: 8rpx; min-height: 54rpx; font-size: 22rpx; color: #426676; }
.member-reference image { width: 42rpx; height: 42rpx; border-radius: 5rpx; }
.member-reference { min-width: 0; }
.member-advanced-action { margin-left: auto; white-space: nowrap; }
.member-settings { margin-top: 14rpx; padding: 20rpx 0 0; border-top: 1rpx solid rgba(72,111,132,.16); }
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
.settings-field { margin-top: 20rpx; }
.settings-field--templates { margin-top: 22rpx; }
.settings-field--advanced { padding-top: 4rpx; }
.settings-input { width: 100%; height: 72rpx; padding: 0 16rpx; box-sizing: border-box; border: 1rpx solid rgba(72,111,132,.2); border-radius: 6rpx; background: rgba(255,255,255,.64); color: #244d60; font-size: 24rpx; }
.option-list { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 12rpx; }
.settings-label--full { width: 100%; margin-bottom: 0; }
.option-chip { max-width: 100%; min-height: 50rpx; padding: 0 16rpx; box-sizing: border-box; display: flex; align-items: center; border: 1rpx solid rgba(72,111,132,.22); border-radius: 5rpx; color: #4c6d7c; background: rgba(255,255,255,.45); font-size: 21rpx; overflow-wrap: anywhere; }
.option-chip--on { border-color: #39788e; color: #17495d; background: rgba(222,239,244,.88); font-weight: 650; }
.private-voice-list { display: grid; grid-template-columns: 1fr; gap: 10rpx; }
.voice-choice { min-height: 72rpx; padding: 12rpx 16rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: space-between; gap: 14rpx; border: 1rpx solid rgba(72,111,132,.2); border-radius: 6rpx; background: rgba(255,255,255,.48); }
.voice-choice--on { border-color: #39788e; background: rgba(222,239,244,.88); }
.voice-choice--disabled { opacity: .5; }
.voice-choice-copy { flex: 1; min-width: 0; }
.voice-choice-name { display: block; font-size: 23rpx; font-weight: 650; color: #284f61; overflow-wrap: anywhere; }
.voice-choice-tip { display: block; margin-top: 5rpx; font-size: 19rpx; line-height: 1.4; color: #718792; }
.template-list { display: grid; grid-template-columns: 1fr 1fr; gap: 10rpx; }
.template-option { min-width: 0; padding: 14rpx 16rpx; border: 1rpx solid rgba(72,111,132,.2); border-radius: 6rpx; background: rgba(255,255,255,.48); }
.template-option--on { border-color: #39788e; background: rgba(222,239,244,.88); }
.template-name { display: block; font-size: 22rpx; font-weight: 650; color: #284f61; overflow-wrap: anywhere; }
.template-model { display: block; margin-top: 6rpx; font-size: 19rpx; color: #718792; overflow-wrap: anywhere; }
.clear-voice { margin-top: 20rpx; min-height: 52rpx; display: inline-flex; align-items: center; gap: 7rpx; color: #657b86; font-size: 21rpx; }
</style>
