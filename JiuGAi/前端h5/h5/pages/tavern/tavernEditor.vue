<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="pageTitle" mode="dark" @back="goBack"></tavern-nav-bar>

		<view v-if="loading" class="state-box">
			<text class="state-txt">{{ texts.loading }}</text>
		</view>

		<scroll-view v-else scroll-y class="scroll" :show-scrollbar="false" enable-back-to-top>
			<view class="hero-card">
				<text class="hero-title">{{ editorHeroTitle }}</text>
				<text class="hero-tip hero-tip--muted">{{ texts.editorGuide }}</text>

				<view class="upload-row">
					<view class="upload-card upload-card--avatar" @tap="pickImage('avatarUrl')">
						<image v-if="avatarPreview" class="upload-img" :src="avatarPreview" mode="aspectFill"></image>
						<view v-else class="upload-empty">
							<text class="upload-empty-ico">+</text>
							<text class="upload-empty-txt">{{ texts.uploadAvatar }}</text>
						</view>
						<view class="upload-mask">
							<text class="upload-mask-main">{{ uploadMaskTitle('avatarUrl', texts.avatar) }}</text>
							<text v-if="uploadMaskSubtext('avatarUrl')" class="upload-mask-sub">{{ uploadMaskSubtext('avatarUrl') }}</text>
						</view>
					</view>

					<view class="upload-card upload-card--cover" @tap="pickImage('coverUrl')">
						<image v-if="coverPreview" class="upload-img" :src="coverPreview" mode="aspectFill"></image>
						<view v-else class="upload-empty">
							<text class="upload-empty-ico">+</text>
							<text class="upload-empty-txt">{{ texts.uploadCover }}</text>
						</view>
						<view class="upload-mask">
							<text class="upload-mask-main">{{ uploadMaskTitle('coverUrl', texts.cover) }}</text>
							<text v-if="uploadMaskSubtext('coverUrl')" class="upload-mask-sub">{{ uploadMaskSubtext('coverUrl') }}</text>
						</view>
					</view>
				</view>
			</view>

			<view class="tab-row">
				<view
					v-for="tab in editorTabs"
					:key="tab.key"
					class="tab-pill"
					:class="{ 'tab-pill--on': activeTab === tab.key, 'tab-pill--soft': tab.key === 'prompt' }"
					@tap="activeTab = tab.key"
				>
					{{ tab.label }}
				</view>
			</view>

			<view v-if="activeTab === 'base'" class="panel">
				<view class="field-block">
					<text class="field-label">创作类型</text>
					<view class="type-switch">
						<view class="type-option" :class="{ 'type-option--on': form.cardType === 'SINGLE' }" @tap="setCardType('SINGLE')">
							<u-icon name="account" :color="form.cardType === 'SINGLE' ? '#163f52' : '#708692'" size="30"></u-icon>
							<view><text class="type-title">单角色</text><text class="type-sub">一对一故事</text></view>
						</view>
						<view class="type-option" :class="{ 'type-option--on': form.cardType === 'ENSEMBLE' }" @tap="setCardType('ENSEMBLE')">
							<u-icon name="account-fill" :color="form.cardType === 'ENSEMBLE' ? '#163f52' : '#708692'" size="30"></u-icon>
							<view><text class="type-title">多角色</text><text class="type-sub">2–8 名成员</text></view>
						</view>
					</view>
				</view>
				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.name }}</text>
						<text class="field-required">{{ texts.required }}</text>
					</view>
					<input
						class="field-input"
						v-model="form.name"
						maxlength="64"
						:disabled="saving"
						:placeholder="texts.namePh"
					/>
				</view>

				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.tagline }}</text>
						<text class="field-optional">{{ texts.optional }}</text>
					</view>
					<input
						class="field-input"
						v-model="form.tagline"
						maxlength="128"
						:disabled="saving"
						:placeholder="texts.taglinePh"
					/>
				</view>

				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.bio }}</text>
						<text class="field-optional">{{ texts.optional }}</text>
					</view>
					<text class="field-hint">{{ texts.bioHint }}</text>
					<textarea
						class="field-area field-area--large"
						v-model="form.bio"
						maxlength="6000"
						auto-height
						:disabled="saving"
						:placeholder="texts.bioPh"
					></textarea>
				</view>
			</view>

			<view v-else-if="activeTab === 'members'" class="panel">
				<character-members-editor
					v-model="form.members"
					:card-type="form.cardType"
					:character-id="id"
					:voice-feature-enabled="featureConfigReady && featureConfig.voiceFeatureEnabled !== false"
					:image-generation-enabled="featureConfigReady && featureConfig.imageGenerationEnabled !== false"
					@pick-avatar="pickMemberImage"
					@pick-reference="pickMemberReference"
				></character-members-editor>
			</view>

			<view v-else-if="activeTab === 'world'" class="panel">
				<view class="field-block world-scenario">
					<view class="field-label-row"><text class="field-label">{{ texts.scenario }}</text><text class="field-optional">{{ texts.optional }}</text></view>
					<text class="field-hint">{{ texts.scenarioHint }}</text>
					<textarea class="field-area" v-model="form.scenario" maxlength="12000" auto-height :disabled="saving" :placeholder="texts.scenarioPh"></textarea>
				</view>
				<character-worldbook-editor v-model="form.lorebookEntries" :members="form.members"></character-worldbook-editor>
			</view>

			<view v-else-if="activeTab === 'openings'" class="panel">
				<character-openings-editor v-model="form.openings" :members="form.members"></character-openings-editor>
			</view>

			<view v-else class="panel">
				<text class="panel-note">{{ texts.advancedNote }}</text>
				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.systemPrompt }}</text>
						<text class="field-optional">{{ texts.optional }}</text>
					</view>
					<text class="field-hint">{{ texts.systemHint }}</text>
					<textarea
						class="field-area"
						v-model="form.systemPrompt"
						maxlength="6000"
						auto-height
						:disabled="saving"
						:placeholder="texts.systemPh"
					></textarea>
				</view>

				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.postHistory }}</text>
						<text class="field-optional">{{ texts.optional }}</text>
					</view>
					<text class="field-hint">{{ texts.postHint }}</text>
					<textarea
						class="field-area"
						v-model="form.postHistoryInstructions"
						maxlength="4000"
						auto-height
						:disabled="saving"
						:placeholder="texts.postPh"
					></textarea>
				</view>

				<view class="field-block">
					<view class="field-label-row">
						<text class="field-label">{{ texts.mesExample }}</text>
						<text class="field-optional">{{ texts.optional }}</text>
					</view>
					<text class="field-hint">{{ texts.exampleHint }}</text>
					<textarea
						class="field-area field-area--large"
						v-model="form.mesExample"
						maxlength="8000"
						auto-height
						:disabled="saving"
						:placeholder="texts.examplePh"
					></textarea>
				</view>
			</view>

			<view class="bottom-bar">
				<view
					v-if="id"
					class="danger-btn"
					:class="{ 'danger-btn--disabled': saving || deleting }"
					@tap="confirmDelete"
				>
					{{ deleting ? texts.deleting : texts.deleteCard }}
				</view>
				<view class="submit-btn" :class="{ 'submit-btn--disabled': saving }" @tap="submit">
					{{ saving ? texts.saving : texts.saveCard }}
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
import CharacterMembersEditor from '@/components/tavern/character-members-editor.vue';
import CharacterOpeningsEditor from '@/components/tavern/character-openings-editor.vue';
import CharacterWorldbookEditor from '@/components/tavern/character-worldbook-editor.vue';

function makeClientKey(prefix) {
	return prefix + '_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
}

function emptyMember(primary) {
	return { clientKey: makeClientKey('member'), name: '', tagline: '', persona: '', avatarUrl: '', voiceConfigJson: '', imageReferenceUrl: '', primaryMember: primary !== false };
}

function emptyOpening(memberKey) {
	return { clientKey: makeClientKey('opening'), title: '开场 1', summary: '', scenarioOverride: '', defaultOpening: true, segments: [{ clientKey: makeClientKey('segment'), speakerClientKey: memberKey || '', speakerType: 'CHARACTER', content: '' }] };
}

const TEXTS = Object.freeze({
	save: '\u4fdd\u5b58',
	saving: '\u4fdd\u5b58\u4e2d...',
	loading: '\u52a0\u8f7d\u4e2d...',
	pageCreate: '\u521b\u5efa\u89d2\u8272\u5361',
	pageEdit: '\u7f16\u8f91\u89d2\u8272\u5361',
	editorTitleCreate: '\u521b\u5efa\u4f60\u7684\u89d2\u8272',
	editorTitleEdit: '\u7f16\u8f91\u4f60\u7684\u89d2\u8272',
	editorGuide: '\u5148\u8d77\u540d\u5b57\u3001\u52a0\u5934\u50cf\uff0c\u518d\u5199\u5f00\u573a\u767d\u5c31\u80fd\u5f00\u59cb\u804a',
	uploadAvatar: '\u4e0a\u4f20\u5934\u50cf',
	uploadCover: '\u4e0a\u4f20\u5c01\u9762',
	uploading: '\u4e0a\u4f20\u4e2d...',
	avatar: '\u89d2\u8272\u5934\u50cf',
	cover: '\u89d2\u8272\u5c01\u9762',
	baseTab: '\u57fa\u7840',
	storyTab: '\u4eba\u8bbe\u4e0e\u5267\u60c5',
	promptTab: '\u8fdb\u9636\uff08\u9009\u586b\uff09',
	required: '\u5fc5\u586b',
	optional: '\u9009\u586b',
	suggested: '\u5efa\u8bae',
	advancedNote: '\u8fd9\u4e9b\u4e00\u822c\u4e0d\u7528\u586b\u3002\u60f3\u66f4\u7cbe\u7ec6\u63a7\u5236\u89d2\u8272\u8868\u73b0\u65f6\u518d\u6765\u3002',
	name: '\u89d2\u8272\u540d\u79f0',
	namePh: '\u7ed9\u89d2\u8272\u8d77\u4e2a\u540d\u5b57',
	tagline: '\u4e00\u53e5\u8bdd\u4ecb\u7ecd',
	taglinePh: '\u7528\u4e00\u53e5\u8bdd\u6982\u62ec\u8fd9\u4e2a\u89d2\u8272\uff08\u53ef\u7559\u7a7a\uff09',
	bio: '\u89d2\u8272\u4ecb\u7ecd',
	bioHint: '\u5199\u66f4\u8be6\u7ec6\u7684\u4ecb\u7ecd\uff0c\u4f1a\u51fa\u73b0\u5728\u89d2\u8272\u8be6\u60c5\u91cc',
	bioPh: '\u4ed6/\u5979\u662f\u8c01\uff0c\u6709\u4ec0\u4e48\u6545\u4e8b\uff1f',
	persona: '\u6027\u683c\u4e0e\u4eba\u8bbe',
	personaHint: '\u4ed6/\u5979\u600e\u4e48\u8bf4\u8bdd\u3001\u6709\u4ec0\u4e48\u4e60\u60ef\u4e0e\u6027\u683c',
	personaPh: '\u4f8b\u5982\uff1a\u6e29\u67d4\u3001\u7231\u5f00\u73a9\u7b11\uff0c\u8bf4\u8bdd\u504f\u53e3\u8bed\u5316',
	scenario: '\u6545\u4e8b\u80cc\u666f',
	scenarioHint: '\u4f60\u4eec\u73b0\u5728\u5904\u5728\u4ec0\u4e48\u60c5\u5883\u91cc',
	scenarioPh: '\u4f8b\u5982\uff1a\u5728\u4e00\u5bb6\u96e8\u5929\u7684\u5496\u5561\u9986\u91cc\u5076\u9047',
	firstMessage: '\u5f00\u573a\u767d',
	firstHint: '\u8fdb\u5165\u804a\u5929\u65f6\uff0c\u89d2\u8272\u8bf4\u7684\u7b2c\u4e00\u53e5\u8bdd',
	firstPh: '\u5199\u4e00\u53e5\u5f00\u573a\u767d\uff0c\u8ba9\u5bf9\u8bdd\u81ea\u7136\u5f00\u59cb',
	altGreeting: '\u5907\u7528\u5f00\u573a\u767d',
	altHint: '\u53ef\u9009\u3002\u6709\u591a\u6761\u65f6\uff0c\u4f1a\u968f\u673a\u9009\u4e00\u6761\u4f5c\u4e3a\u5f00\u573a',
	addGreeting: '\u6dfb\u52a0\u5f00\u573a',
	greetPh: '\u518d\u5199\u4e00\u6761\u5907\u7528\u5f00\u573a\u767d',
	delete: '\u5220\u9664',
	systemPrompt: '\u9ad8\u7ea7\u8bbe\u5b9a',
	systemHint: '\u4e00\u822c\u4e0d\u7528\u586b\u3002\u586b\u4e86\u4f1a\u8986\u76d6\u9ed8\u8ba4\u89c4\u5219',
	systemPh: '\u53ef\u9009\uff0c\u7528\u6765\u66f4\u5f3a\u5236\u5730\u7ea6\u675f\u89d2\u8272\u8868\u73b0',
	postHistory: '\u5bf9\u8bdd\u8865\u5145\u8bf4\u660e',
	postHint: '\u53ef\u9009\u3002\u63d0\u9192\u89d2\u8272\u5728\u5bf9\u8bdd\u4e2d\u8981\u6ce8\u610f\u4ec0\u4e48',
	postPh: '\u4f8b\u5982\uff1a\u4fdd\u6301\u6e29\u67d4\uff0c\u4e0d\u8981\u7a81\u7136\u6539\u53d8\u4eba\u8bbe',
	mesExample: '\u5bf9\u8bdd\u793a\u4f8b',
	exampleHint: '\u53ef\u9009\u3002\u5199\u51e0\u53e5\u793a\u4f8b\u5bf9\u8bdd\uff0c\u8ba9\u8bed\u6c14\u66f4\u7a33',
	examplePh: '\u53ef\u6309\u300c\u7528\u6237\uff1a...\u300d\u300c\u89d2\u8272\uff1a...\u300d\u7684\u683c\u5f0f\u5199',
	saveCard: '\u4fdd\u5b58\u89d2\u8272\u5361',
	backendOff: '\u6682\u65f6\u65e0\u6cd5\u4fdd\u5b58\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5',
	loadFailed: '\u52a0\u8f7d\u5931\u8d25',
	imageUploadSuccess: '\u56fe\u7247\u4e0a\u4f20\u6210\u529f',
	imageUploadFail: '\u56fe\u7247\u4e0a\u4f20\u5931\u8d25',
	imageOnly: '\u8bf7\u9009\u62e9\u56fe\u7247\u6587\u4ef6',
	imageTooLarge: '\u56fe\u7247\u8fc7\u5927\uff0c\u5355\u6587\u4ef6\u6700\u591a 28MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5',
	saveSuccess: '\u4fdd\u5b58\u6210\u529f',
	saveFail: '\u4fdd\u5b58\u5931\u8d25',
	nameRequired: '\u8bf7\u8f93\u5165\u89d2\u8272\u540d\u79f0',
	deleteCard: '\u5220\u9664\u89d2\u8272\u5361',
	deleting: '\u5220\u9664\u4e2d...',
	deleteTitle: '\u5220\u9664\u89d2\u8272\u5361',
	deleteContent: '\u5220\u9664\u540e\uff0c\u8be5\u89d2\u8272\u7684\u804a\u5929\u8bb0\u5f55\u4e5f\u4f1a\u4e00\u8d77\u6e05\u9664\uff0c\u4e14\u4e0d\u53ef\u6062\u590d\u3002\u786e\u5b9a\u5220\u9664\u5417\uff1f',
	deleteSuccess: '\u5220\u9664\u6210\u529f',
	deleteFail: '\u5220\u9664\u5931\u8d25'
});

function emptyForm() {
	return {
		id: '',
		name: '',
		tagline: '',
		bio: '',
		persona: '',
		scenario: '',
		firstMessage: '',
		alternateGreetings: [''],
		mesExample: '',
		systemPrompt: '',
		postHistoryInstructions: '',
		avatarUrl: '',
		coverUrl: ''
		, cardType: 'SINGLE'
		, members: []
		, openings: []
		, lorebookEntries: []
	};
}

export default {
	components: {
		TavernNavBar,
		CharacterMembersEditor,
		CharacterOpeningsEditor,
		CharacterWorldbookEditor
	},
	data() {
		return {
			id: '',
			loading: false,
			saving: false,
			deleting: false,
			uploadField: '',
			uploadProgress: 0,
			localPreviewUrls: {
				avatarUrl: '',
				coverUrl: ''
			},
			localPreviewOwned: {
				avatarUrl: false,
				coverUrl: false
			},
			activeTab: 'base',
			featureConfigReady: false,
			tabs: [
				{ key: 'base', label: '基础' },
				{ key: 'members', label: '成员' },
				{ key: 'world', label: '世界' },
				{ key: 'openings', label: '开场' },
				{ key: 'prompt', label: '进阶' }
			],
			form: emptyForm(),
			featureConfig: {
				loginEnabled: require('@/common/tavernApi.js').isLoginEnabled(),
				registerEnabled: require('@/common/tavernApi.js').isRegisterEnabled(),
				userCharacterCreationEnabled: require('@/common/tavernApi.js').isUserCharacterCreationEnabled(),
				voiceFeatureEnabled: require('@/common/tavernApi.js').isVoiceFeatureEnabled(),
				imageGenerationEnabled: require('@/common/tavernApi.js').isImageGenerationEnabled()
			}
		};
	},
	computed: {
		texts() {
			return TEXTS;
		},
		pageTitle() {
			return this.id ? this.texts.pageEdit : this.texts.pageCreate;
		},
		editorHeroTitle() {
			return this.id ? this.texts.editorTitleEdit : this.texts.editorTitleCreate;
		},
		avatarPreview() {
			return this.localPreviewUrls.avatarUrl || this.previewUrl(this.form.avatarUrl);
		},
		coverPreview() {
			return this.localPreviewUrls.coverUrl || this.previewUrl(this.form.coverUrl) || this.localPreviewUrls.avatarUrl || this.previewUrl(this.form.avatarUrl);
		},
		editorTabs() {
			return this.tabs.map(tab => tab.key === 'members'
				? Object.assign({}, tab, { label: this.form.cardType === 'ENSEMBLE' ? '成员' : '人设' })
				: tab);
		}
	},
	onLoad(query) {
		this.id = query && query.id ? String(query.id) : '';
		this.syncFeatureConfig(!!this.id);
		if (this.id) {
			this.loadEditor();
		} else {
			this.form = this.normalizeStudioForm(emptyForm());
		}
	},
	onShow() {
		if (this.featureConfigReady) this.syncFeatureConfig(true);
	},
	onUnload() {
		this.clearLocalPreviews();
	},
	methods: {
		normalizeStudioForm(source) {
			const next = Object.assign(emptyForm(), source || {});
			next.cardType = String(next.cardType || next.card_type || 'SINGLE').toUpperCase() === 'ENSEMBLE' ? 'ENSEMBLE' : 'SINGLE';
			let members = Array.isArray(next.members) ? next.members : [];
			if (!members.length) {
				const member = emptyMember(true);
				member.name = next.name || '';
				member.tagline = next.tagline || '';
				member.persona = next.persona || '';
				member.avatarUrl = next.avatarUrl || '';
				members = [member];
			}
			next.members = members.map((item, index) => Object.assign(emptyMember(index === 0), item || {}, {
				clientKey: String((item && item.clientKey) || makeClientKey('member')),
				primaryMember: index === 0,
				settingsOpen: false
			}));
			if (next.cardType === 'ENSEMBLE' && next.members.length < 2) next.members.push(emptyMember(false));

			let openings = Array.isArray(next.openings) ? next.openings : [];
			if (!openings.length) {
				const legacy = [next.firstMessage].concat(Array.isArray(next.alternateGreetings) ? next.alternateGreetings : []).filter(item => String(item || '').trim());
				openings = legacy.map((content, index) => {
					const opening = emptyOpening(next.members[0].clientKey);
					opening.title = '开场 ' + (index + 1);
					opening.defaultOpening = index === 0;
					opening.segments[0].content = content;
					return opening;
				});
			}
			if (!openings.length) openings = [emptyOpening(next.members[0].clientKey)];
			next.openings = openings.map((item, index) => {
				const opening = Object.assign(emptyOpening(next.members[0].clientKey), item || {});
				opening.clientKey = String(opening.clientKey || makeClientKey('opening'));
				opening.defaultOpening = index === 0 ? opening.defaultOpening !== false : !!opening.defaultOpening;
				opening.segments = (Array.isArray(opening.segments) && opening.segments.length ? opening.segments : [emptyOpening(next.members[0].clientKey).segments[0]])
					.map(segment => Object.assign({ clientKey: makeClientKey('segment'), speakerClientKey: next.members[0].clientKey, speakerType: 'CHARACTER', content: '' }, segment || {}));
				return opening;
			});
			if (!next.openings.some(item => item.defaultOpening)) next.openings[0].defaultOpening = true;
			next.lorebookEntries = (Array.isArray(next.lorebookEntries) ? next.lorebookEntries : []).map((item) => {
				const entry = Object.assign({
					clientKey: makeClientKey('lore'), title: '', memberClientKey: '', keywords: [], secondaryKeywords: [],
					matchMode: 'ANY', content: '', priority: 100, constantInjection: false, scanDepth: 8,
					injectionPosition: 'BEFORE_CHARACTER', enabled: true, advancedOpen: false
				}, item || {});
				entry.clientKey = String(entry.clientKey || makeClientKey('lore'));
				entry.keywords = Array.isArray(entry.keywords) ? entry.keywords : [];
				entry.secondaryKeywords = Array.isArray(entry.secondaryKeywords) ? entry.secondaryKeywords : [];
				entry.matchMode = entry.matchMode === 'ALL' ? 'ALL' : 'ANY';
				entry.advancedOpen = false;
				return entry;
			});
			next.alternateGreetings = this.normalizeGreetings(next.alternateGreetings);
			return next;
		},
		setCardType(type) {
			const nextType = type === 'ENSEMBLE' ? 'ENSEMBLE' : 'SINGLE';
			if (nextType === this.form.cardType) return;
			(this.form.members || []).forEach((member, index) => {
				if (index === 0 && member && member.ttsUserVoiceBindingLoaded) {
					this.$set(member, 'ttsUserVoiceBindingDirty', true);
				}
			});
			if (nextType === 'SINGLE' && this.form.members.length > 1) {
				uni.showModal({ title: '切换为单角色', content: '将保留第一名角色，其他成员会从这张卡中移除。', confirmText: '继续', success: ({ confirm }) => {
					if (!confirm) return;
					this.form.cardType = 'SINGLE';
					this.form.members = this.form.members.slice(0, 1);
					this.repairOpeningSpeakers();
				}});
				return;
			}
			this.form.cardType = nextType;
			if (nextType === 'ENSEMBLE' && this.form.members.length < 2) this.form.members.push(emptyMember(false));
		},
		repairOpeningSpeakers() {
			const keys = this.form.members.map(item => item.clientKey);
			(this.form.openings || []).forEach(opening => (opening.segments || []).forEach(segment => {
				if (segment.speakerType !== 'NARRATOR' && keys.indexOf(segment.speakerClientKey) < 0) segment.speakerClientKey = keys[0] || '';
			}));
		},
		syncFeatureConfig(forceRefresh) {
			const tavernApi = require('@/common/tavernApi.js');
			tavernApi
				.fetchAppRuntimeConfig(!!forceRefresh)
				.then((config) => {
					this.featureConfig = config || this.featureConfig;
					if (!this.id && this.featureConfig.userCharacterCreationEnabled === false) {
						uni.showToast({ title: '\u5f53\u524d\u5df2\u6682\u505c\u7528\u6237\u7aef\u521b\u5efa\u89d2\u8272\u5361', icon: 'none', duration: 2600 });
						setTimeout(() => {
							this.goBack();
						}, 260);
					}
				})
				.catch(() => {})
				.finally(() => {
					this.featureConfigReady = true;
				});
		},
		ensureCreationEnabled() {
			if (this.featureConfig.userCharacterCreationEnabled !== false) {
				return true;
			}
			uni.showToast({ title: '\u5f53\u524d\u5df2\u6682\u505c\u7528\u6237\u7aef\u521b\u5efa\u89d2\u8272\u5361', icon: 'none', duration: 2600 });
			return false;
		},
		isBrowserFileObject(file) {
			return !!file
				&& typeof file === 'object'
				&& typeof file.name === 'string'
				&& typeof file.size === 'number'
				&& typeof file.slice === 'function';
		},
		revokeLocalPreview(field) {
			if (!field) {
				return;
			}
			if (this.localPreviewOwned[field] && this.localPreviewUrls[field] && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
				try {
					URL.revokeObjectURL(this.localPreviewUrls[field]);
				} catch (e) {}
			}
			this.localPreviewUrls[field] = '';
			this.localPreviewOwned[field] = false;
		},
		clearLocalPreviews() {
			this.revokeLocalPreview('avatarUrl');
			this.revokeLocalPreview('coverUrl');
		},
		setLocalPreview(field, file) {
			if (!field || !file) {
				return;
			}
			this.revokeLocalPreview(field);
			if (file.path) {
				this.localPreviewUrls[field] = String(file.path);
				this.localPreviewOwned[field] = false;
				return;
			}
			if (this.isBrowserFileObject(file) && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
				try {
					this.localPreviewUrls[field] = URL.createObjectURL(file);
					this.localPreviewOwned[field] = true;
				} catch (e) {}
			}
		},
		uploadMaskTitle(field, idleText) {
			if (this.uploadField !== field) {
				return idleText;
			}
			return this.texts.uploading;
		},
		uploadMaskSubtext(field) {
			if (this.uploadField !== field) {
				return '';
			}
			if (this.uploadProgress > 0) {
				return this.uploadProgress + '%';
			}
			return '';
		},
		previewUrl(raw) {
			if (!raw || String(raw).trim() === '') {
				return '';
			}
			const tavernApi = require('@/common/tavernApi.js');
			return tavernApi.resolveJgAssetUrl(raw) || '';
		},
		normalizeGreetings(list) {
			if (!Array.isArray(list) || !list.length) {
				return [''];
			}
			const rows = list
				.map((item) => String(item == null ? '' : item).replace(/\r\n/g, '\n'))
				.filter((item, idx, arr) => {
					if (item.trim() !== '') {
						return true;
					}
					return arr.length === 1 && idx === 0;
				});
			return rows.length ? rows : [''];
		},
		loadEditor() {
			const tavernApi = require('@/common/tavernApi.js');
			if (!this.id) {
				return;
			}
			this.loading = true;
			tavernApi
				.fetchMyCharacterEditor(this.id, tavernApi.getClientUid())
				.then((data) => {
					this.form = this.normalizeStudioForm(data || {});
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.loadFailed),
						icon: 'none',
						duration: 2800
					});
					setTimeout(() => {
						this.goBack();
					}, 300);
				})
				.finally(() => {
					this.loading = false;
				});
		},
		addGreeting() {
			this.form.alternateGreetings.push('');
		},
		removeGreeting(idx) {
			if (this.form.alternateGreetings.length <= 1) {
				this.form.alternateGreetings = [''];
				return;
			}
			this.form.alternateGreetings.splice(idx, 1);
		},
		pickMemberImage(index) {
			if (this.saving || this.loading || this.uploadField || !this.form.members[index]) return;
			uni.chooseImage({ count: 1, sizeType: ['compressed'], success: (res) => {
				const file = this.normalizePickedImage(res);
				if (file) this.uploadMemberImage(index, file, 'avatarUrl');
			}});
		},
		pickMemberReference(index) {
			if (!this.featureConfigReady || this.featureConfig.imageGenerationEnabled === false) return;
			if (this.saving || this.loading || this.uploadField || !this.form.members[index]) return;
			uni.chooseImage({ count: 1, sizeType: ['compressed'], success: (res) => {
				const file = this.normalizePickedImage(res);
				if (file) this.uploadMemberImage(index, file, 'imageReferenceUrl');
			}});
		},
		uploadMemberImage(index, file, targetField) {
			if (!this.isImageUploadFile(file) || this.isImageFileTooLarge(file)) {
				uni.showToast({ title: this.isImageFileTooLarge(file) ? this.texts.imageTooLarge : this.texts.imageOnly, icon: 'none' });
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			const tavernErrors = require('@/common/tavernErrors.js');
			const field = targetField === 'imageReferenceUrl' ? 'imageReferenceUrl' : 'avatarUrl';
			this.uploadField = 'member_' + index + '_' + field;
			this.uploadProgress = 0;
			const uploadSource = file && file.path ? file.path : file;
			uni.showLoading({ title: this.texts.uploading, mask: true });
			tavernApi.uploadMyCharacterImage(uploadSource, tavernApi.getClientUid(), percent => { this.uploadProgress = percent; })
				.then(data => {
					const url = data && data.url ? String(data.url) : '';
					if (!url) throw new Error(this.texts.imageUploadFail);
					this.$set(this.form.members[index], field, url);
					if (field === 'avatarUrl' && !this.form.members[index].imageReferenceUrl) this.$set(this.form.members[index], 'imageReferenceUrl', url);
					if (field === 'avatarUrl' && index === 0 && !this.form.avatarUrl) this.form.avatarUrl = url;
				})
				.catch(e => uni.showToast({ title: tavernErrors.getTavernErrorMessage(e, this.texts.imageUploadFail), icon: 'none' }))
				.finally(() => { this.uploadField = ''; this.uploadProgress = 0; uni.hideLoading(); });
		},
		pickImage(field) {
			if (this.saving || this.loading || this.uploadField) {
				return;
			}
			if (!this.ensureCreationEnabled()) {
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				uni.showToast({ title: this.texts.backendOff, icon: 'none' });
				return;
			}
			uni.chooseImage({
				count: 1,
				sizeType: ['compressed'],
				success: (res) => {
					const file = this.normalizePickedImage(res);
					if (!file) {
						return;
					}
					this.uploadCharacterImage(field, file);
				}
			});
		},
		normalizePickedImage(res) {
			const tempFiles = res && Array.isArray(res.tempFiles) ? res.tempFiles : [];
			if (tempFiles.length && tempFiles[0]) {
				return tempFiles[0];
			}
			const tempPaths = res && Array.isArray(res.tempFilePaths) ? res.tempFilePaths : [];
			if (tempPaths.length && tempPaths[0]) {
				return {
					path: tempPaths[0],
					name: String(tempPaths[0]).split('/').pop()
				};
			}
			return null;
		},
		isImageUploadFile(file) {
			const fileName = String((file && (file.name || file.path)) || '').toLowerCase();
			const mime = String((file && file.type) || '').toLowerCase();
			if (mime.indexOf('image/') === 0) {
				return true;
			}
			return /\.(png|jpe?g|webp|gif|bmp|avif|heic|heif)($|\?)/.test(fileName);
		},
		isImageFileTooLarge(file) {
			const tavernApi = require('@/common/tavernApi.js');
			const limit = tavernApi.getUploadMaxFileBytes ? tavernApi.getUploadMaxFileBytes() : 50 * 1024 * 1024;
			const size = Number((file && file.size) || 0);
			return size > 0 && size > limit;
		},
		uploadCharacterImage(field, file) {
			if (!file) {
				return;
			}
			if (!this.isImageUploadFile(file)) {
				uni.showToast({ title: this.texts.imageOnly, icon: 'none', duration: 2600 });
				return;
			}
			if (this.isImageFileTooLarge(file)) {
				uni.showToast({ title: this.texts.imageTooLarge, icon: 'none', duration: 2600 });
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			const tavernErrors = require('@/common/tavernErrors.js');
			this.setLocalPreview(field, file);
			this.uploadField = field;
			this.uploadProgress = 0;
			uni.showLoading({ title: this.texts.uploading, mask: true });
			const uploadSource = file && file.path ? file.path : file;
			tavernApi
				.uploadMyCharacterImage(uploadSource, tavernApi.getClientUid(), (percent) => {
					this.uploadProgress = percent;
				})
				.then((data) => {
					const url = data && data.url ? String(data.url) : '';
					if (!url) {
						throw new Error(this.texts.imageUploadFail);
					}
					this.form[field] = url;
					if (field === 'avatarUrl' && !this.form.coverUrl) {
						this.form.coverUrl = url;
					}
					uni.showToast({ title: this.texts.imageUploadSuccess, icon: 'none' });
				})
				.catch((e) => {
					if (e && String(e.message || '') === 'cancelled') {
						return;
					}
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.imageUploadFail),
						icon: 'none',
						duration: 2800
					});
				})
				.finally(() => {
					this.uploadField = '';
					this.uploadProgress = 0;
					uni.hideLoading();
				});
		},
		buildPayload() {
			const tavernApi = require('@/common/tavernApi.js');
			const singleCard = this.form.cardType !== 'ENSEMBLE';
			const members = (this.form.members || []).map((item, index) => {
				const member = Object.assign({}, item, {
					name: String(singleCard && index === 0 ? this.form.name : (item.name || '')).trim(),
					tagline: String(singleCard && index === 0 ? this.form.tagline : (item.tagline || '')).trim(),
					avatarUrl: String(singleCard && index === 0 ? this.form.avatarUrl : (item.avatarUrl || '')).trim(),
					primaryMember: index === 0
				});
				const rawVoiceConfig = String(member.voiceConfigJson || '').trim();
				if (rawVoiceConfig) {
					try {
						const voiceConfig = JSON.parse(rawVoiceConfig);
						if (voiceConfig && typeof voiceConfig === 'object' && !Array.isArray(voiceConfig)) delete voiceConfig.ttsModelName;
						member.voiceConfigJson = voiceConfig && typeof voiceConfig === 'object' && !Array.isArray(voiceConfig)
							? JSON.stringify(voiceConfig)
							: '';
					} catch (e) {
						member.voiceConfigJson = '';
					}
				} else {
					member.voiceConfigJson = '';
				}
				delete member.settingsOpen;
				delete member.ttsUserVoiceId;
				delete member.ttsUserVoiceBindingLoaded;
				delete member.ttsUserVoiceBindingDirty;
				return member;
			});
			const sourceOpenings = this.form.openings || [];
			const requestedDefaultIndex = sourceOpenings.findIndex(item => item && item.defaultOpening);
			const defaultOpeningIndex = requestedDefaultIndex >= 0 ? requestedDefaultIndex : 0;
			const openings = sourceOpenings.map((item, index) => Object.assign({}, item, {
				defaultOpening: index === defaultOpeningIndex,
				segments: (item.segments || []).map(segment => Object.assign({}, segment, { content: String(segment.content || '') }))
			}));
			const legacyOpeningOrder = openings.length
				? [openings[defaultOpeningIndex]].concat(openings.filter((item, index) => index !== defaultOpeningIndex))
				: [];
			const legacyGreetings = legacyOpeningOrder.map(opening => this.renderLegacyOpening(opening, members)).filter(Boolean);
			const primaryMember = members[0] || {};
			return {
				id: this.form.id || undefined,
				clientUid: tavernApi.getClientUid(),
				name: String(this.form.name || '').trim(),
				tagline: String(this.form.tagline || '').trim(),
				bio: String(this.form.bio || ''),
				persona: String(primaryMember.persona || this.form.persona || ''),
				scenario: String(this.form.scenario || ''),
				firstMessage: legacyGreetings[0] || '',
				alternateGreetings: legacyGreetings.slice(1),
				mesExample: String(this.form.mesExample || ''),
				systemPrompt: String(this.form.systemPrompt || ''),
				postHistoryInstructions: String(this.form.postHistoryInstructions || ''),
				avatarUrl: String(this.form.avatarUrl || '').trim(),
				coverUrl: String(this.form.coverUrl || '').trim(),
				cardType: this.form.cardType === 'ENSEMBLE' ? 'ENSEMBLE' : 'SINGLE',
				members,
				openings,
				lorebookEntries: (this.form.lorebookEntries || []).map(item => {
					const entry = Object.assign({}, item);
					delete entry.advancedOpen;
					entry.matchMode = entry.matchMode === 'ALL' ? 'ALL' : 'ANY';
					const scanDepth = Math.floor(Number(entry.scanDepth));
					const priority = Math.floor(Number(entry.priority));
					entry.scanDepth = Number.isFinite(scanDepth) ? Math.max(1, Math.min(100, scanDepth)) : 8;
					entry.priority = Number.isFinite(priority) ? Math.max(0, Math.min(1000, priority)) : 100;
					entry.injectionPosition = ['BEFORE_CHARACTER', 'AFTER_CHARACTER', 'BEFORE_HISTORY'].indexOf(entry.injectionPosition) >= 0
						? entry.injectionPosition : 'BEFORE_CHARACTER';
					return entry;
				})
			};
		},
		renderLegacyOpening(opening, members) {
			const ensemble = this.form.cardType === 'ENSEMBLE';
			return (opening && Array.isArray(opening.segments) ? opening.segments : [])
				.map(segment => {
					const content = String(segment && segment.content || '').trim();
					if (!content) return '';
					if (!ensemble && segment.speakerType !== 'NARRATOR') return content;
					if (segment.speakerType === 'NARRATOR') return '【旁白】' + content;
					const member = members.find(item => item.clientKey === segment.speakerClientKey);
					return '【' + ((member && member.name) || '角色') + '】' + content;
				})
				.filter(Boolean)
				.join('\n\n');
		},
		collectVoiceBindingChanges() {
			return (this.form.members || []).map((member, index) => ({
				index,
				voiceId: Math.max(0, Math.floor(Number(member && member.ttsUserVoiceId) || 0)),
				dirty: !!(member && member.ttsUserVoiceBindingDirty)
			})).filter(item => item.dirty);
		},
		syncPrivateVoiceBindings(savedForm, changes) {
			if (!Array.isArray(changes) || !changes.length) return Promise.resolve();
			const tavernApi = require('@/common/tavernApi.js');
			const characterId = Math.max(0, Math.floor(Number(savedForm && savedForm.id) || Number(this.id) || 0));
			const members = Array.isArray(savedForm && savedForm.members) ? savedForm.members : [];
			if (!characterId) return Promise.reject(new Error('角色已保存，但没有返回可用的角色 ID'));
			return Promise.all(changes.map(change => {
				const member = members[change.index] || {};
				const memberId = Math.max(0, Math.floor(Number(member.id) || 0));
				const ensemble = String(savedForm && savedForm.cardType || this.form.cardType).toUpperCase() === 'ENSEMBLE';
				if (ensemble && !memberId) return Promise.reject(new Error('角色已保存，但没有返回成员 ID'));
				return tavernApi.putUserTtsVoiceBinding(tavernApi.getClientUid(), {
					scopeType: ensemble ? 'MEMBER' : 'CHARACTER',
					characterId,
					memberId: ensemble ? memberId : 0,
					voiceId: change.voiceId > 0 ? change.voiceId : null
				});
			}));
		},
		submit() {
			if (this.loading || this.saving || this.deleting) {
				return;
			}
			if (!this.ensureCreationEnabled()) {
				return;
			}
			const payload = this.buildPayload();
			if (!payload.name) {
				this.activeTab = 'base';
				uni.showToast({ title: this.texts.nameRequired, icon: 'none' });
				return;
			}
			const minimumMembers = payload.cardType === 'ENSEMBLE' ? 2 : 1;
			if (payload.members.length < minimumMembers || payload.members.some(item => !item.name)) {
				this.activeTab = 'members';
				uni.showToast({ title: payload.cardType === 'ENSEMBLE' ? '请至少填写两名角色的名称' : '请填写角色名称', icon: 'none' });
				return;
			}
			if (!payload.openings.some(item => (item.segments || []).some(segment => String(segment.content || '').trim()))) {
				this.activeTab = 'openings';
				uni.showToast({ title: '请至少填写一个开场场景', icon: 'none' });
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			const voiceBindingChanges = this.collectVoiceBindingChanges();
			this.saving = true;
			tavernApi
				.saveMyCharacter(payload)
				.then((data) => {
					const savedForm = data || {};
					this.form = this.normalizeStudioForm(savedForm);
					this.id = savedForm && savedForm.id != null ? String(savedForm.id) : (this.form.id ? String(this.form.id) : this.id);
					return this.syncPrivateVoiceBindings(savedForm, voiceBindingChanges).catch(error => {
						error.characterSaved = true;
						throw error;
					});
				})
				.then(() => {
					uni.showToast({ title: this.texts.saveSuccess, icon: 'none' });
					setTimeout(() => {
						this.goBack();
					}, 300);
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					uni.showToast({
						title: e && e.characterSaved
							? '角色卡已保存，但音色绑定失败：' + String(e.message || '请重试')
							: tavernErrors.getTavernErrorMessage(e, this.texts.saveFail),
						icon: 'none',
						duration: 2800
					});
				})
				.finally(() => {
					this.saving = false;
				});
		},
		confirmDelete() {
			if (!this.id || this.deleting || this.saving) {
				return;
			}
			uni.showModal({
				title: this.texts.deleteTitle,
				content: this.texts.deleteContent,
				confirmColor: '#ef4444',
				success: (res) => {
					if (res && res.confirm) {
						this.deleteCurrent();
					}
				}
			});
		},
		deleteCurrent() {
			const tavernApi = require('@/common/tavernApi.js');
			const tavernErrors = require('@/common/tavernErrors.js');
			if (!this.id) {
				return;
			}
			this.deleting = true;
			tavernApi
				.deleteMyCharacter({
					id: Number(this.id),
					clientUid: tavernApi.getClientUid()
				})
				.then(() => {
					tavernApi.cleanupLocalCharacterArtifacts({
						clientUid: tavernApi.getClientUid(),
						characterId: Number(this.id),
						conversationId: this.form && (this.form.conversationId != null ? this.form.conversationId : this.form.conversation_id)
					});
					uni.showToast({ title: this.texts.deleteSuccess, icon: 'none' });
					setTimeout(() => {
						uni.reLaunch({ url: '/pages/tavern/tavern' });
					}, 300);
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.deleteFail),
						icon: 'none',
						duration: 2800
					});
				})
				.finally(() => {
					this.deleting = false;
				});
		},
		goBack() {
			const pages = getCurrentPages();
			if (pages && pages.length > 1) {
				uni.navigateBack();
				return;
			}
			uni.reLaunch({ url: '/pages/tavern/tavern' });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	position: relative;
	height: 100vh;
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	background: $tavern-page-bg;
	padding-bottom: env(safe-area-inset-bottom);
	box-sizing: border-box;
	overflow: hidden;
}

.save-btn {
	padding: 0 16rpx;
	font-size: 28rpx;
	font-weight: 700;
	color: $tavern-accent-violet;
}

.save-btn--disabled {
	opacity: 0.45;
}

.state-box {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40rpx;
}

.state-txt {
	font-size: 28rpx;
	color: $tavern-muted-on-dark;
}

.scroll {
	flex: 1;
	height: 0;
	padding: 20rpx 22rpx calc(32rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.hero-card,
.panel {
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	border-radius: $tavern-radius-lg;
	box-shadow: $tavern-card-shadow;
}

.hero-card {
	padding: 20rpx;
}

.hero-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.hero-tip {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: #ddd6fe;
}

.hero-tip--muted {
	color: $tavern-muted-on-dark;
}

.upload-row {
	display: flex;
	gap: 14rpx;
	margin-top: 18rpx;
}

.upload-tip {
	display: block;
	margin-top: 14rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: $tavern-muted-on-dark;
}

.upload-card {
	position: relative;
	overflow: hidden;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx dashed rgba(255, 255, 255, 0.14);
}

.upload-card--avatar {
	width: 190rpx;
	height: 240rpx;
}

.upload-card--cover {
	flex: 1;
	height: 240rpx;
}

.upload-img {
	width: 100%;
	height: 100%;
	display: block;
}

.upload-empty {
	height: 100%;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
}

.upload-empty-ico {
	font-size: 48rpx;
	line-height: 1;
	color: $tavern-text-on-dark;
}

.upload-empty-txt {
	font-size: 24rpx;
	color: $tavern-muted-on-dark;
}

.upload-mask {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 12rpx 14rpx;
	color: #fff;
	display: flex;
	flex-direction: column;
	gap: 4rpx;
	background: linear-gradient(180deg, rgba(15, 23, 42, 0), rgba(15, 23, 42, 0.88));
}

.upload-mask-main {
	font-size: 22rpx;
	font-weight: 600;
}

.upload-mask-sub {
	font-size: 20rpx;
	color: rgba(255, 255, 255, 0.72);
}

.tab-row {
	display: flex;
	gap: 12rpx;
	margin: 18rpx 0;
}

.tab-pill {
	flex: 1;
	min-height: 66rpx;
	padding: 0 10rpx;
	line-height: 66rpx;
	text-align: center;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: $tavern-muted-on-dark;
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx solid rgba(255, 255, 255, 0.06);
	box-sizing: border-box;
}

.tab-pill--on {
	color: #fff;
	background: $tavern-accent-gradient;
	border-color: transparent;
	font-weight: 700;
}

.tab-pill--soft:not(.tab-pill--on) {
	opacity: 0.88;
}

.panel {
	padding: 20rpx;
}

.panel-note {
	display: block;
	margin-bottom: 18rpx;
	padding: 14rpx 16rpx;
	border-radius: 14rpx;
	font-size: 22rpx;
	line-height: 1.45;
	color: #4d6678;
	background: rgba(220, 238, 250, 0.72);
	border: 1rpx solid rgba(148, 183, 210, 0.28);
}

.field-block + .field-block {
	margin-top: 20rpx;
}

.field-row {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 12rpx;
}

.field-label-row {
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
}

.field-label {
	font-size: 26rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.field-required,
.field-optional,
.field-suggest {
	font-size: 20rpx;
	font-weight: 600;
	line-height: 1;
	padding: 6rpx 10rpx;
	border-radius: 999rpx;
}

.field-required {
	color: #b91c1c;
	background: rgba(254, 226, 226, 0.9);
}

.field-optional {
	color: #247494;
	background: rgba(220, 238, 250, 0.95);
}

.field-suggest {
	color: #a16207;
	background: rgba(254, 243, 199, 0.95);
}

.field-hint {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: $tavern-muted-on-dark;
}

.field-input,
.field-area {
	width: 100%;
	box-sizing: border-box;
	margin-top: 14rpx;
	padding: 18rpx 20rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx solid rgba(255, 255, 255, 0.07);
	font-size: 26rpx;
	line-height: 1.55;
	color: $tavern-text-on-dark;
}

.field-input {
	min-height: 84rpx;
}

.field-area {
	min-height: 170rpx;
}

.field-area--greet {
	flex: 1;
	min-height: 120rpx;
	margin-top: 0;
}

.field-area--large {
	min-height: 220rpx;
}

.field-link {
	font-size: 24rpx;
	color: $tavern-accent-violet;
}

.field-link--danger {
	margin-top: 18rpx;
	color: #fda4af;
}

.greet-row {
	display: flex;
	align-items: flex-start;
	gap: 12rpx;
	margin-top: 14rpx;
}

.bottom-bar {
	padding: 24rpx 0 8rpx;
}

.bottom-note {
	display: block;
	margin-bottom: 16rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: $tavern-muted-on-dark;
}

.danger-btn {
	height: 76rpx;
	line-height: 76rpx;
	margin-bottom: 14rpx;
	text-align: center;
	border-radius: 18rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #fecaca;
	background: rgba(127, 29, 29, 0.22);
	border: 1rpx solid rgba(248, 113, 113, 0.24);
}

.danger-btn--disabled {
	opacity: 0.55;
	pointer-events: none;
}

.submit-btn {
	height: 84rpx;
	line-height: 84rpx;
	text-align: center;
	border-radius: 20rpx;
	font-size: 28rpx;
	font-weight: 700;
	color: #fff;
	background: $tavern-accent-gradient;
}

.submit-btn--disabled {
	opacity: 0.55;
}

/* Light clover tavern editor refresh. */
.hero-card,
.panel {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.hero-title,
.field-label,
.upload-empty-ico {
	color: #244b66;
}

.hero-tip,
.upload-tip,
.upload-empty-txt,
.field-hint,
.bottom-note {
	color: #687f92;
}

.upload-card,
.tab-pill,
.field-input,
.field-area {
	background: rgba(255, 255, 255, 0.5);
	border-color: rgba(255, 255, 255, 0.56);
	color: #16384d;
}

.upload-mask {
	background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(36, 75, 102, 0.58) 100%);
}

.tab-pill,
.field-link,
.save-btn {
	color: #1f6686;
}

.tab-pill--on,
.submit-btn {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
	color: #fff;
}

.danger-btn {
	background: rgba(244, 166, 196, 0.18);
	color: #9f4464;
	border-color: rgba(244, 166, 196, 0.28);
}

/* Character Studio v2: restrained creation workspace. */
.tab-row {
	gap: 0;
	padding: 6rpx;
	border-radius: 8rpx;
	background: rgba(225, 238, 244, 0.72);
	border: 1rpx solid rgba(72, 111, 132, 0.12);
}

.tab-pill {
	min-width: 0;
	min-height: 60rpx;
	line-height: 60rpx;
	border: 0;
	border-radius: 6rpx;
	background: transparent;
	color: #607b89;
}

.tab-pill--on,
.tab-pill--on.tab-pill--soft {
	background: rgba(255, 255, 255, 0.9);
	color: #173f52;
	box-shadow: 0 4rpx 12rpx rgba(45, 89, 111, 0.1);
}

.panel,
.hero-card {
	border-radius: 8rpx;
}

.type-switch {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0;
	margin-top: 14rpx;
	padding: 5rpx;
	border-radius: 8rpx;
	background: rgba(225, 238, 244, 0.75);
	border: 1rpx solid rgba(72, 111, 132, 0.14);
}

.type-option {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	min-height: 76rpx;
	border-radius: 6rpx;
	color: #708692;
}

.type-option--on {
	background: rgba(255, 255, 255, 0.92);
	color: #173f52;
	box-shadow: 0 4rpx 12rpx rgba(45, 89, 111, 0.1);
}

.type-title,
.type-sub {
	display: block;
}

.type-title {
	font-size: 25rpx;
	font-weight: 700;
}

.type-sub {
	margin-top: 2rpx;
	font-size: 19rpx;
	color: #718895;
}

.world-scenario {
	padding-bottom: 22rpx;
	margin-bottom: 8rpx;
	border-bottom: 1rpx solid rgba(72, 111, 132, 0.16);
}
</style>
