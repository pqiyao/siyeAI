<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="pageTitle" mode="dark" @back="goBack">
			<template #right>
				<text class="save-btn" :class="{ 'save-btn--disabled': saving || loading }" @tap="submit">
					{{ saving ? texts.saving : texts.save }}
				</text>
			</template>
		</tavern-nav-bar>

		<view v-if="loading" class="state-box">
			<text class="state-txt">{{ texts.loading }}</text>
		</view>

		<scroll-view v-else scroll-y class="scroll" :show-scrollbar="false" enable-back-to-top>
			<view class="hero-card">
				<text class="hero-title">{{ texts.editorTitle }}</text>
				<text class="hero-tip">{{ texts.editorTip1 }}</text>
				<text class="hero-tip hero-tip--muted">{{ texts.editorTip2 }}</text>

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
				<text class="upload-tip">{{ texts.uploadTip }}</text>
			</view>

			<view class="tab-row">
				<view
					v-for="tab in tabs"
					:key="tab.key"
					class="tab-pill"
					:class="{ 'tab-pill--on': activeTab === tab.key }"
					@tap="activeTab = tab.key"
				>
					{{ tab.label }}
				</view>
			</view>

			<view v-if="activeTab === 'base'" class="panel">
				<view class="field-block">
					<text class="field-label">{{ texts.name }}</text>
					<input
						class="field-input"
						v-model="form.name"
						maxlength="64"
						:disabled="saving"
						:placeholder="texts.namePh"
					/>
				</view>

				<view class="field-block">
					<text class="field-label">{{ texts.tagline }}</text>
					<input
						class="field-input"
						v-model="form.tagline"
						maxlength="128"
						:disabled="saving"
						:placeholder="texts.taglinePh"
					/>
				</view>

				<view class="field-block">
					<text class="field-label">bio</text>
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

			<view v-else-if="activeTab === 'story'" class="panel">
				<view class="field-block">
					<text class="field-label">persona</text>
					<text class="field-hint">{{ texts.personaHint }}</text>
					<textarea
						class="field-area"
						v-model="form.persona"
						maxlength="6000"
						auto-height
						:disabled="saving"
						:placeholder="texts.personaPh"
					></textarea>
				</view>

				<view class="field-block">
					<text class="field-label">scenario</text>
					<text class="field-hint">{{ texts.scenarioHint }}</text>
					<textarea
						class="field-area"
						v-model="form.scenario"
						maxlength="6000"
						auto-height
						:disabled="saving"
						:placeholder="texts.scenarioPh"
					></textarea>
				</view>

				<view class="field-block">
					<text class="field-label">{{ texts.firstMessage }}</text>
					<text class="field-hint">{{ texts.firstHint }}</text>
					<textarea
						class="field-area"
						v-model="form.firstMessage"
						maxlength="6000"
						auto-height
						:disabled="saving"
						:placeholder="texts.firstPh"
					></textarea>
				</view>

				<view class="field-block">
					<view class="field-row">
						<view>
							<text class="field-label">{{ texts.altGreeting }}</text>
							<text class="field-hint">{{ texts.altHint }}</text>
						</view>
						<text class="field-link" @tap="addGreeting">{{ texts.addGreeting }}</text>
					</view>
					<view v-for="(line, idx) in form.alternateGreetings" :key="'g-' + idx" class="greet-row">
						<textarea
							class="field-area field-area--greet"
							v-model="form.alternateGreetings[idx]"
							maxlength="2000"
							auto-height
							:disabled="saving"
							:placeholder="texts.greetPh"
						></textarea>
						<text
							v-if="form.alternateGreetings.length > 1"
							class="field-link field-link--danger"
							@tap="removeGreeting(idx)"
						>{{ texts.delete }}</text>
					</view>
				</view>
			</view>

			<view v-else class="panel">
				<view class="field-block">
					<text class="field-label">system_prompt</text>
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
					<text class="field-label">post_history_instructions</text>
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
					<text class="field-label">mes_example</text>
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
				<view class="bottom-note">{{ texts.bottomNote }}</view>
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

const TEXTS = Object.freeze({
	save: '\u4fdd\u5b58',
	saving: '\u4fdd\u5b58\u4e2d...',
	loading: '\u52a0\u8f7d\u4e2d...',
	pageCreate: '\u521b\u5efa\u89d2\u8272\u5361',
	pageEdit: '\u7f16\u8f91\u89d2\u8272\u5361',
	editorTitle: '\u89d2\u8272\u5361\u5185\u5bb9\u7f16\u8f91',
	editorTip1: '\u5b57\u6bb5\u4e0e\u540e\u53f0\u7ba1\u7406\u7aef\u4fdd\u6301\u540c\u4e00\u5957\u6838\u5fc3\u5185\u5bb9\uff0c\u53ef\u5148\u5728\u6211\u7684\u89d2\u8272\u9875\u5bfc\u5165 ST PNG \u518d\u7ee7\u7eed\u7f16\u8f91',
	editorTip2: '\u7528\u6237\u521b\u5efa\u7684\u89d2\u8272\u5361\u53ea\u4f1a\u51fa\u73b0\u5728\u9152\u9986\u9875\uff0c\u4e0d\u4f1a\u8fdb\u5165\u53d1\u73b0\u9875',
	uploadAvatar: '\u4e0a\u4f20\u5934\u50cf',
	uploadCover: '\u4e0a\u4f20\u5c01\u9762',
	uploading: '\u4e0a\u4f20\u4e2d...',
	uploadTip: '\u666e\u901a\u56fe\u7247\u4f1a\u4f18\u5148\u4f7f\u7528\u624b\u673a\u7cfb\u7edf\u538b\u7f29\u56fe\u6765\u63d0\u9ad8\u4e0a\u4f20\u901f\u5ea6\uff0c\u9875\u9762\u4f1a\u5148\u672c\u5730\u9884\u89c8\u518d\u4e0a\u4f20\uff0cST PNG \u89d2\u8272\u5361\u5bfc\u5165\u4ecd\u4fdd\u6301\u539f\u59cb\u6587\u4ef6',
	avatar: '\u89d2\u8272\u5934\u50cf',
	cover: '\u89d2\u8272\u5c01\u9762',
	baseTab: '\u57fa\u7840\u4e0e\u5c55\u793a',
	storyTab: '\u89d2\u8272\u4e0e\u5267\u60c5',
	promptTab: '\u63d0\u793a\u8bcd\u4e0e\u793a\u4f8b',
	name: '\u89d2\u8272\u540d\u79f0',
	namePh: '\u8bf7\u8f93\u5165\u89d2\u8272\u540d\u79f0',
	tagline: '\u4e00\u53e5\u8bdd\u8bbe\u5b9a',
	taglinePh: '\u5217\u8868\u77ed\u63cf\u8ff0\uff0c\u53ef\u7559\u7a7a\u81ea\u52a8\u4ece bio \u751f\u6210',
	bioHint: '\u5bf9\u5e94\u540e\u53f0 description / \u53d1\u73b0\u9875\u8be6\u60c5\u4ecb\u7ecd\u957f\u6587',
	bioPh: '\u89d2\u8272\u63cf\u8ff0 bio',
	personaHint: '\u5bf9\u5e94\u540e\u53f0 personality\uff0c\u5199\u5165\u89d2\u8272\u4eba\u8bbe\u4e0e\u7cfb\u7edf\u4e0a\u4e0b\u6587',
	personaPh: '\u6027\u683c\u4eba\u8bbe persona',
	scenarioHint: '\u5bf9\u5e94\u540e\u53f0 scenario\uff0c\u586b\u5199\u5f53\u524d\u4e16\u754c\u6216\u5267\u60c5\u80cc\u666f',
	scenarioPh: '\u60c5\u666f scenario',
	firstMessage: '\u7b2c\u4e00\u6761\u6d88\u606f',
	firstHint: '\u5bf9\u5e94\u540e\u53f0 first_mes\uff0c\u8fdb\u5165\u804a\u5929\u65f6\u53ef\u4f5c\u4e3a\u89d2\u8272\u5f00\u573a\u767d',
	firstPh: 'ST: first_mes',
	altGreeting: '\u5176\u4ed6\u5f00\u573a',
	altHint: '\u5bf9\u5e94\u540e\u53f0 alternate_greetings\uff0c\u591a\u6761\u65f6\u540e\u7aef\u4f1a\u53c2\u4e0e\u968f\u673a\u5f00\u573a',
	addGreeting: '\u6dfb\u52a0\u5f00\u573a',
	greetPh: '\u4e00\u6761\u5907\u7528\u5f00\u573a\u767d',
	delete: '\u5220\u9664',
	systemHint: '\u4e0d\u586b\u5219\u7531\u540e\u7aef\u6309\u89d2\u8272\u5b57\u6bb5\u62fc\u88c5\uff0c\u586b\u5199\u540e\u4f1a\u8986\u76d6\u9ed8\u8ba4 system \u63d0\u793a',
	systemPh: '\u7cfb\u7edf\u63d0\u793a system_prompt',
	postHint: '\u5bf9\u5e94\u540e\u53f0 post-history \u8ffd\u52a0\u8bf4\u660e',
	postPh: 'post-history \u8bf4\u660e',
	exampleHint: '\u53ef\u586b\u5199\u793a\u4f8b\u5bf9\u8bdd\uff0c\u5e2e\u52a9\u6a21\u578b\u7a33\u5b9a\u89d2\u8272\u8bed\u6c14\u4e0e\u98ce\u683c',
	examplePh: '\u5bf9\u8bdd\u793a\u4f8b mes_example',
	bottomNote: '\u4fdd\u5b58\u540e\u53ef\u76f4\u63a5\u5728\u9152\u9986\u9875\u67e5\u770b\u548c\u8fdb\u5165\u804a\u5929\uff0c\u53d1\u73b0\u9875\u4e0d\u4f1a\u5c55\u793a\u8fd9\u7c7b\u79c1\u6709\u89d2\u8272\u5361',
	saveCard: '\u4fdd\u5b58\u89d2\u8272\u5361',
	backendOff: '\u540e\u7aef\u63a5\u53e3\u672a\u5f00\u542f',
	loadFailed: '\u52a0\u8f7d\u5931\u8d25',
	imageUploadSuccess: '\u56fe\u7247\u4e0a\u4f20\u6210\u529f',
	imageUploadFail: '\u56fe\u7247\u4e0a\u4f20\u5931\u8d25',
	imageOnly: '\u8bf7\u9009\u62e9\u56fe\u7247\u6587\u4ef6',
	imageTooLarge: '\u56fe\u7247\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 10MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5',
	saveSuccess: '\u4fdd\u5b58\u6210\u529f',
	saveFail: '\u4fdd\u5b58\u5931\u8d25',
	nameRequired: '\u8bf7\u8f93\u5165\u89d2\u8272\u540d\u79f0',
	deleteCard: '\u5220\u9664\u89d2\u8272\u5361',
	deleting: '\u5220\u9664\u4e2d...',
	deleteTitle: '\u5220\u9664\u89d2\u8272\u5361',
	deleteContent: '\u5220\u9664\u540e\u5c06\u4e00\u8d77\u6e05\u7406\u8fd9\u4e2a\u89d2\u8272\u7684\u804a\u5929\u8bb0\u5f55\u3001\u957f\u671f\u8bb0\u5fc6\u548c\u4e92\u52a8\u6570\u636e\uff0c\u786e\u5b9a\u7ee7\u7eed\u5417\uff1f',
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
	};
}

export default {
	components: {
		TavernNavBar
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
			tabs: [
				{ key: 'base', label: TEXTS.baseTab },
				{ key: 'story', label: TEXTS.storyTab },
				{ key: 'prompt', label: TEXTS.promptTab }
			],
			form: emptyForm(),
			featureConfig: {
				loginEnabled: require('@/common/tavernApi.js').isLoginEnabled(),
				registerEnabled: require('@/common/tavernApi.js').isRegisterEnabled(),
				userCharacterCreationEnabled: require('@/common/tavernApi.js').isUserCharacterCreationEnabled()
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
		avatarPreview() {
			return this.localPreviewUrls.avatarUrl || this.previewUrl(this.form.avatarUrl);
		},
		coverPreview() {
			return this.localPreviewUrls.coverUrl || this.previewUrl(this.form.coverUrl) || this.localPreviewUrls.avatarUrl || this.previewUrl(this.form.avatarUrl);
		}
	},
	onLoad(query) {
		this.id = query && query.id ? String(query.id) : '';
		this.syncFeatureConfig(!!this.id);
		if (this.id) {
			this.loadEditor();
		}
	},
	onUnload() {
		this.clearLocalPreviews();
	},
	methods: {
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
				.catch(() => {});
		},
		ensureCreationEnabled() {
			if (this.id || this.featureConfig.userCharacterCreationEnabled !== false) {
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
					const next = Object.assign(emptyForm(), data || {});
					next.alternateGreetings = this.normalizeGreetings(next.alternateGreetings);
					this.form = next;
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
			return {
				id: this.form.id || undefined,
				clientUid: tavernApi.getClientUid(),
				name: String(this.form.name || '').trim(),
				tagline: String(this.form.tagline || '').trim(),
				bio: String(this.form.bio || ''),
				persona: String(this.form.persona || ''),
				scenario: String(this.form.scenario || ''),
				firstMessage: String(this.form.firstMessage || ''),
				alternateGreetings: (this.form.alternateGreetings || [])
					.map((item) => String(item == null ? '' : item).trim())
					.filter(Boolean),
				mesExample: String(this.form.mesExample || ''),
				systemPrompt: String(this.form.systemPrompt || ''),
				postHistoryInstructions: String(this.form.postHistoryInstructions || ''),
				avatarUrl: String(this.form.avatarUrl || '').trim(),
				coverUrl: String(this.form.coverUrl || '').trim()
			};
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
			const tavernApi = require('@/common/tavernApi.js');
			this.saving = true;
			tavernApi
				.saveMyCharacter(payload)
				.then((data) => {
					const next = Object.assign(emptyForm(), data || {});
					next.alternateGreetings = this.normalizeGreetings(next.alternateGreetings);
					this.form = next;
					this.id = next.id ? String(next.id) : this.id;
					uni.showToast({ title: this.texts.saveSuccess, icon: 'none' });
					setTimeout(() => {
						this.goBack();
					}, 300);
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.saveFail),
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
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	background: $tavern-page-bg;
	padding-bottom: env(safe-area-inset-bottom);
	box-sizing: border-box;
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
	height: 66rpx;
	line-height: 66rpx;
	text-align: center;
	border-radius: 999rpx;
	font-size: 24rpx;
	color: $tavern-muted-on-dark;
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx solid rgba(255, 255, 255, 0.06);
}

.tab-pill--on {
	color: #fff;
	background: $tavern-accent-gradient;
	border-color: transparent;
}

.panel {
	padding: 20rpx;
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

.field-label {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
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
</style>

