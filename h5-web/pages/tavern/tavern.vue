<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<view class="head-strip" :style="{ paddingTop: statusBarH + 6 + 'px' }">
			<view class="head-row">
				<view class="head-left">
					<view class="head-title-row">
						<text class="head-title">{{ texts.mineTitle }}</text>
						<text class="head-count-chip">{{ cardCountText }}</text>
					</view>
					<text class="head-sub">{{ texts.privateOnlyIntro }}</text>
				</view>
				<view
					class="create-chip"
					:class="{ 'chip--disabled': !featureConfig.userCharacterCreationEnabled }"
					@tap="openCreate"
				>
					<text class="create-plus">+</text>
					<text class="create-txt">{{ texts.createCard }}</text>
				</view>
			</view>

			<view class="meta-bar">
				<view class="meta-actions">
					<view class="meta-link" @tap="openSort">
						<text class="meta-link-txt">{{ sortLabel }}</text>
						<text class="sort-arrow">▾</text>
					</view>
					<view
						class="meta-link"
						:class="{ 'meta-link--disabled': importing || !featureConfig.userCharacterCreationEnabled }"
						@tap="openImportPng"
					>
						<text class="meta-link-txt">{{ importing ? importProgressLabel : texts.importCard }}</text>
					</view>
				</view>
			</view>
			<text v-if="!featureConfig.userCharacterCreationEnabled" class="lock-tip">{{ texts.creationPaused }}</text>
		</view>

		<view v-if="loading && !visibleList.length" class="mine-skeleton">
			<view v-for="n in skeletonList" :key="'mine_skeleton_' + n" class="mine-skeleton-card">
				<view class="mine-skeleton-visual"></view>
			</view>
		</view>
		<view v-else-if="errorMsg && !visibleList.length" class="state-box">
			<text class="state-txt state-txt--err">{{ errorMsg }}</text>
			<view class="state-btn" @tap="loadMine">{{ texts.retry }}</view>
		</view>
		<view v-else-if="!visibleList.length" class="state-box">
			<text class="state-txt">{{ texts.empty }}</text>
			<text class="state-tip">{{ texts.emptyTip }}</text>
			<view class="state-btn" @tap="openCreate">{{ texts.createNow }}</view>
		</view>

		<scroll-view
			v-else
			scroll-y
			class="page-scroll"
			:show-scrollbar="false"
			enable-back-to-top
			:lower-threshold="220"
			@scrolltolower="onScrollToLower"
		>
			<view class="grid2-wrap">
				<view v-for="item in visibleList" :key="item.id" class="grid2-item">
					<view class="card-disc" @tap="openDetail(item)">
						<view class="card-visual">
							<image class="card-bg" :src="coverUrl(item)" mode="aspectFill" lazy-load></image>
							<view class="card-shade" aria-hidden="true"></view>
							<view
								class="card-more"
								@tap.stop="openCardMenu(item)"
							>
								<text class="card-more-dot">···</text>
							</view>
							<view class="card-visual-copy">
								<text class="card-visual-title">{{ item.nickname || '-' }}</text>
								<text class="card-visual-desc">{{ cardSubtitle(item) }}</text>
								<text v-if="item.review_reason" class="card-visual-review">{{ item.review_reason }}</text>
								<view v-if="reviewStatusText(item)" class="card-float-tags">
									<text
										class="float-tag"
										:class="reviewStatusClass(item)"
									>{{ reviewStatusText(item) }}</text>
								</view>
								<view class="card-inline-foot">
									<text class="card-inline-handle">{{ displayHandle(item) }}</text>
									<view class="card-inline-heat">
										<text class="heart">{{ texts.heart }}</text>
										<text>{{ formatLikes(item.like_count) }}</text>
									</view>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>
			<view v-if="hasMoreCards" class="list-more" @tap="loadMoreCards">
				<text class="list-more-text">{{ listProgressText(visibleList.length, list.length) }}</text>
			</view>
			<view class="pad"></view>
		</scroll-view>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="104" />
		<!-- #endif -->
	</view>
</template>

<script>
import { applyTavernTabBarLabels, syncTavernTabBar } from '@/common/tavernTabBar.js';
const tavernListPerf = require('@/common/tavernListPerf.js');
const authSession = require('@/common/authSession.js');

const MINE_INITIAL_VISIBLE = 8;
const MINE_BATCH_VISIBLE = 8;
const MINE_SKELETON_COUNT = 4;

const TEXTS = Object.freeze({
	mineTitle: '\u6211\u7684\u89d2\u8272\u5361',
	privateOnlyIntro: '\u79c1\u4eba\u89d2\u8272\uff0c\u53ea\u6709\u4f60\u80fd\u770b\u5230',
	createCard: '\u65b0\u5efa\u89d2\u8272\u5361',
	importCard: '\u5bfc\u5165\u89d2\u8272\u5361',
	importing: '\u5bfc\u5165\u4e2d...',
	importPngOnly: '\u8bf7\u9009\u62e9 PNG \u683c\u5f0f\u7684\u89d2\u8272\u5361\u6587\u4ef6',
	importTooLarge: '\u6587\u4ef6\u8fc7\u5927\uff0c\u5355\u6587\u4ef6\u6700\u591a 28MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5',
	cardCount: '\u5171 {n} \u5f20',
	pendingReview: '\u5ba1\u6838\u4e2d',
	rejectedReview: '\u672a\u901a\u8fc7',
	approvedReview: '\u5df2\u901a\u8fc7',
	loading: '\u52a0\u8f7d\u4e2d...',
	retry: '\u70b9\u51fb\u91cd\u8bd5',
	empty: '\u8fd8\u6ca1\u6709\u89d2\u8272\u5361',
	emptyTip: '\u521b\u5efa\u4e00\u4e2a\uff0c\u5f00\u59cb\u4e13\u5c5e\u5bf9\u8bdd',
	createNow: '\u65b0\u5efa\u89d2\u8272\u5361',
	sortName: '\u6309\u540d\u79f0',
	sortRecent: '\u6700\u8fd1\u521b\u5efa',
	editContent: '\u7f16\u8f91',
	deleteCard: '\u5220\u9664',
	deleting: '\u5220\u9664\u4e2d...',
	deleteTitle: '\u5220\u9664\u89d2\u8272\u5361',
	deleteContent: '\u5220\u9664\u540e\uff0c\u8be5\u89d2\u8272\u7684\u804a\u5929\u8bb0\u5f55\u4e5f\u4f1a\u4e00\u8d77\u6e05\u9664\uff0c\u4e14\u4e0d\u53ef\u6062\u590d\u3002\u786e\u5b9a\u5220\u9664\u5417\uff1f',
	deleteSuccess: '\u5220\u9664\u6210\u529f',
	deleteFailed: '\u5220\u9664\u5931\u8d25',
	anon: '\u533f\u540d',
	noIntro: '\u6682\u65e0\u7b80\u4ecb',
	backendOff: '\u6682\u65f6\u65e0\u6cd5\u52a0\u8f7d\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5',
	loadFailed: '\u52a0\u8f7d\u5931\u8d25',
	importSuccess: '\u5bfc\u5165\u6210\u529f',
	importFailed: '\u5bfc\u5165\u5931\u8d25',
	creationPaused: '\u6682\u65f6\u65e0\u6cd5\u65b0\u5efa\u6216\u5bfc\u5165\uff0c\u5df2\u6709\u7684\u89d2\u8272\u5361\u4ecd\u53ef\u7ba1\u7406',
	creationUnavailable: '\u6682\u65f6\u65e0\u6cd5\u65b0\u5efa\u89d2\u8272\u5361',
	creationAccessFailed: '\u6682\u65f6\u65e0\u6cd5\u65b0\u5efa\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5',
	heart: '\u2665'
});

export default {
	data() {
		return {
			statusBarH: 44,
			loading: false,
			errorMsg: '',
			list: [],
			visibleCount: 0,
			sortBy: 'recent',
			requestSeq: 0,
			deletingId: '',
			importing: false,
			importProgress: 0,
			creationAccess: {
				allowed: false,
				limit: 0,
				used: 0,
				remaining: 0,
				message: ''
			},
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
		sortLabel() {
			return this.sortBy === 'name' ? this.texts.sortName : this.texts.sortRecent;
		},
		cardCountText() {
			return String(this.texts.cardCount || '\u5171 {n} \u5f20').replace('{n}', String(this.list.length));
		},
		visibleList() {
			return tavernListPerf.sliceVisibleList(this.list, this.visibleCount, MINE_INITIAL_VISIBLE);
		},
		hasMoreCards() {
			return tavernListPerf.hasMoreItems(this.list, this.visibleCount, MINE_INITIAL_VISIBLE);
		},
		skeletonList() {
			return Array.from({ length: MINE_SKELETON_COUNT }, function (_, idx) {
				return idx + 1;
			});
		},
		importProgressLabel() {
			if (!this.importing) {
				return this.texts.importCard;
			}
			if (this.importProgress > 0) {
				return this.texts.importing + ' ' + this.importProgress + '%';
			}
			return this.texts.importing;
		}
	},
	onLoad() {
		try {
			const sys = uni.getSystemInfoSync();
			this.statusBarH = sys.statusBarHeight || 44;
		} catch (e) {
			this.statusBarH = 44;
		}
	},
	onShow() {
		applyTavernTabBarLabels(this.allText, this);
		syncTavernTabBar(this, 'pages/tavern/tavern', this.allText);
		if (!authSession.requireAuth('/pages/tavern/tavern')) return;
		this.syncFeatureConfig(true);
		this.refreshCreationAccess().catch(() => {});
		this.loadMine();
	},
	methods: {
		syncFeatureConfig(forceRefresh) {
			const tavernApi = require('@/common/tavernApi.js');
			tavernApi
				.fetchAppRuntimeConfig(!!forceRefresh)
				.then((config) => {
					this.featureConfig = config || this.featureConfig;
				})
				.catch(() => {});
		},
		refreshCreationAccess() {
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled() || !tavernApi.fetchMyCharacterCreationAccess) {
				return Promise.resolve(this.creationAccess);
			}
			return tavernApi
				.fetchMyCharacterCreationAccess(tavernApi.getClientUid())
				.then((access) => {
					this.creationAccess = access || this.creationAccess;
					if (access) {
						const nextFeatureConfig = {};
						if (Object.prototype.hasOwnProperty.call(access, 'globalEnabled')) {
							nextFeatureConfig.userCharacterCreationEnabled = access.globalEnabled !== false;
						}
						if (Object.keys(nextFeatureConfig).length) {
							this.featureConfig = Object.assign({}, this.featureConfig, nextFeatureConfig);
						}
					}
					return this.creationAccess;
				});
		},
		ensureCreationEnabled() {
			if (this.featureConfig.userCharacterCreationEnabled) {
				return true;
			}
			uni.showToast({ title: this.texts.creationPaused, icon: 'none', duration: 2600 });
			return false;
		},
		ensureCreationAccessBeforeAction() {
			if (!this.ensureCreationEnabled()) {
				return Promise.resolve(false);
			}
			return this.refreshCreationAccess()
				.then((access) => {
					if (access && access.allowed === true && Number(access.remaining || 0) > 0) {
						return true;
					}
					uni.showToast({
						title: (access && access.message) || this.texts.creationUnavailable,
						icon: 'none',
						duration: 2800
					});
					return false;
				})
				.catch(() => {
					uni.showToast({ title: this.texts.creationAccessFailed, icon: 'none', duration: 2600 });
					return false;
				});
		},
		formatLikes(value) {
			const n = Number(value);
			if (!isFinite(n) || n < 0) {
				return '0';
			}
			return String(Math.floor(n));
		},
		displayHandle(item) {
			if (item && item.creator_handle) {
				return item.creator_handle;
			}
			const name = item && item.creator ? String(item.creator) : this.texts.anon;
			return name.indexOf('@') === 0 ? name : '@' + name;
		},
		cardSubtitle(item) {
			if (!item) {
				return this.texts.noIntro;
			}
			const text = this.truncatePreviewText(
				item.bio || item.persona || item.scenario || item.tagline || '',
				42
			);
			return text ? text : this.texts.noIntro;
		},
		reviewStatusText(item) {
			const status = item && item.review_status ? String(item.review_status).toUpperCase() : '';
			if (status === 'PENDING') return this.texts.pendingReview;
			if (status === 'REJECTED') return this.texts.rejectedReview;
			if (status === 'APPROVED') return this.texts.approvedReview;
			return '';
		},
		reviewStatusClass(item) {
			const status = item && item.review_status ? String(item.review_status).toUpperCase() : '';
			if (status === 'REJECTED') return 'float-tag--reject';
			if (status === 'APPROVED') return 'float-tag--approved';
			return 'float-tag--pending';
		},
		normalizePreviewText(value) {
			return value == null ? '' : String(value).replace(/\s+/g, ' ').trim();
		},
		syncVisibleCount() {
			this.visibleCount = tavernListPerf.syncVisibleCount(this.visibleCount, this.list.length, MINE_INITIAL_VISIBLE);
		},
		loadMoreCards() {
			this.visibleCount = tavernListPerf.expandVisibleCount(
				this.visibleCount,
				this.list.length,
				MINE_BATCH_VISIBLE,
				MINE_INITIAL_VISIBLE
			);
		},
		onScrollToLower() {
			if (this.loading) {
				return;
			}
			this.loadMoreCards();
		},
		listProgressText(visibleCount, totalCount) {
			const safeVisible = Math.max(0, Number(visibleCount) || 0);
			const safeTotal = Math.max(0, Number(totalCount) || 0);
			if (!safeTotal || safeVisible >= safeTotal) {
				return '';
			}
			return '已显示 ' + safeVisible + ' / ' + safeTotal + '，继续下滑自动加载更多';
		},
		truncatePreviewText(value, maxLen) {
			const text = this.normalizePreviewText(value);
			const safeMax = Math.max(Number(maxLen) || 0, 12);
			if (!text) {
				return '';
			}
			if (text.length <= safeMax) {
				return text;
			}
			return text.slice(0, safeMax).replace(/\s+$/g, '') + '...';
		},
		coverUrl(item) {
			const tavernApi = require('@/common/tavernApi.js');
			const raw = item && (item.cover_thumb || item.avatar_thumb || item.cover || item.avatar)
				? (item.cover_thumb || item.avatar_thumb || item.cover || item.avatar)
				: '';
			if (!raw || String(raw).trim() === '') {
				return '/static/logo.png';
			}
			return tavernApi.resolveJgAssetUrl(raw) || '/static/logo.png';
		},
		openSort() {
			uni.showActionSheet({
				itemList: [this.texts.sortRecent, this.texts.sortName],
				success: (res) => {
					const next = res.tapIndex === 1 ? 'name' : 'recent';
					if (next !== this.sortBy) {
						this.sortBy = next;
						this.visibleCount = 0;
						this.loadMine();
					}
				}
			});
		},
		loadMine() {
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				this.loading = false;
				this.list = [];
				this.visibleCount = 0;
				this.errorMsg = this.texts.backendOff;
				return;
			}
			const requestSeq = ++this.requestSeq;
			this.loading = true;
			this.errorMsg = '';
			tavernApi
				.fetchMyCharacters(tavernApi.getClientUid(), this.sortBy)
				.then((rows) => {
					if (requestSeq !== this.requestSeq) {
						return;
					}
					this.list = Array.isArray(rows) ? rows : [];
					this.syncVisibleCount();
				})
				.catch((e) => {
					if (requestSeq !== this.requestSeq) {
						return;
					}
					const tavernErrors = require('@/common/tavernErrors.js');
					this.list = [];
					this.visibleCount = 0;
					this.errorMsg = tavernErrors.getTavernErrorMessage(e, this.texts.loadFailed);
				})
				.finally(() => {
					if (requestSeq === this.requestSeq) {
						this.loading = false;
					}
				});
		},
		openCreate() {
			this.ensureCreationAccessBeforeAction().then((allowed) => {
				if (allowed) {
					uni.navigateTo({ url: '/pages/tavern/tavernEditor' });
				}
			});
		},
		openImportPng() {
			if (this.loading || this.importing || this.deletingId) {
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				uni.showToast({ title: this.texts.backendOff, icon: 'none' });
				return;
			}
			if (!this.ensureCreationEnabled()) {
				return;
			}
			let useBrowserFilePicker = false;
			// #ifdef H5
			useBrowserFilePicker = !!(tavernApi.canUseBrowserFilePicker && tavernApi.canUseBrowserFilePicker());
			// #endif
			if (useBrowserFilePicker) {
				tavernApi
					.pickBrowserPngFile()
					.then((file) => {
						if (!file) {
							return;
						}
						this.ensureCreationAccessBeforeAction().then((allowed) => {
							if (allowed) {
								this.importMyPngFile(file);
							}
						});
					})
					.catch((e) => {
						if (e && String(e.message || '') === 'cancelled') {
							return;
						}
					});
				return;
			}
			this.ensureCreationAccessBeforeAction().then((allowed) => {
				if (!allowed) {
					return;
				}
				uni.chooseImage({
					count: 1,
					sizeType: ['original'],
					success: (res) => {
						const file = this.normalizePickedFile(res);
						if (!file) {
							return;
						}
						this.importMyPngFile(file);
					}
				});
			});
		},
		normalizePickedFile(res) {
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
		isPngImportFile(file) {
			const fileName = String((file && (file.name || file.path)) || '').toLowerCase();
			const mime = String((file && file.type) || '').toLowerCase();
			return mime.indexOf('png') >= 0 || /\.png($|\?)/.test(fileName);
		},
		isImportFileTooLarge(file) {
			const tavernApi = require('@/common/tavernApi.js');
			const limit = tavernApi.getUploadMaxFileBytes ? tavernApi.getUploadMaxFileBytes() : 50 * 1024 * 1024;
			const size = Number((file && file.size) || 0);
			return size > 0 && size > limit;
		},
		importMyPngFile(file) {
			if (!file) {
				return;
			}
			if (!this.isPngImportFile(file)) {
				uni.showToast({ title: this.texts.importPngOnly, icon: 'none', duration: 2600 });
				return;
			}
			if (this.isImportFileTooLarge(file)) {
				uni.showToast({ title: this.texts.importTooLarge, icon: 'none', duration: 2600 });
				return;
			}
			const tavernApi = require('@/common/tavernApi.js');
			const tavernErrors = require('@/common/tavernErrors.js');
			const uploadSource = file && file.path ? file.path : file;
			this.importing = true;
			this.importProgress = 0;
			uni.showLoading({ title: this.texts.importing, mask: true });
			tavernApi
				.importMyCharacterPng(uploadSource, tavernApi.getClientUid(), (percent) => {
					this.importProgress = percent;
				})
				.then((data) => {
					const nextId = data && data.id != null && data.id !== '' ? String(data.id) : '';
					this.refreshCreationAccess().catch(() => {});
					uni.showToast({ title: this.texts.importSuccess, icon: 'none' });
					if (nextId) {
						setTimeout(() => {
							uni.navigateTo({ url: '/pages/tavern/tavernEditor?id=' + nextId });
						}, 160);
					} else {
						this.loadMine();
					}
				})
				.catch((e) => {
					if (e && String(e.message || '') === 'cancelled') {
						return;
					}
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.importFailed),
						icon: 'none',
						duration: 2800
					});
				})
				.finally(() => {
					this.importing = false;
					this.importProgress = 0;
					uni.hideLoading();
				});
		},
		openEditor(item) {
			if (!item || item.id == null || item.id === '') {
				return;
			}
			uni.navigateTo({ url: '/pages/tavern/tavernEditor?id=' + item.id });
		},
		openCardMenu(item) {
			if (!item || item.id == null || item.id === '') {
				return;
			}
			uni.showActionSheet({
				itemList: [this.texts.editContent, this.texts.deleteCard],
				success: (res) => {
					if (res.tapIndex === 0) {
						this.openEditor(item);
					} else if (res.tapIndex === 1) {
						this.confirmDelete(item);
					}
				}
			});
		},
		confirmDelete(item) {
			if (!item || item.id == null || item.id === '' || this.deletingId === item.id) {
				return;
			}
			uni.showModal({
				title: this.texts.deleteTitle,
				content: this.texts.deleteContent,
				confirmColor: '#ef4444',
				success: (res) => {
					if (res && res.confirm) {
						this.deleteCard(item);
					}
				}
			});
		},
		deleteCard(item) {
			const tavernApi = require('@/common/tavernApi.js');
			const tavernErrors = require('@/common/tavernErrors.js');
			this.deletingId = item.id;
			tavernApi
				.deleteMyCharacter({
					id: item.id,
					clientUid: tavernApi.getClientUid()
				})
				.then(() => {
					tavernApi.cleanupLocalCharacterArtifacts({
						clientUid: tavernApi.getClientUid(),
						characterId: item.id,
						conversationId: item && (item.conversationId != null ? item.conversationId : item.conversation_id)
					});
					uni.showToast({ title: this.texts.deleteSuccess, icon: 'none' });
					this.loadMine();
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.texts.deleteFailed),
						icon: 'none',
						duration: 2800
					});
				})
				.finally(() => {
					this.deletingId = '';
				});
		},
		openDetail(item) {
			if (!item || item.id == null || item.id === '') {
				return;
			}
			uni.navigateTo({ url: '/pages/tavern/charDetail?id=' + item.id });
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
	padding-bottom: 0;
	box-sizing: border-box;
}

.head-strip {
	padding: 0 24rpx 8rpx;
	flex-shrink: 0;
}

.head-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 14rpx;
}

.head-left {
	flex: 1;
	min-width: 0;
}

.head-title-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
	min-width: 0;
}

.head-title {
	display: block;
	font-size: 34rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
	line-height: 1.2;
}

.head-count-chip {
	flex-shrink: 0;
	padding: 4rpx 12rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 600;
	color: #247494;
	background: rgba(255, 255, 255, 0.55);
	border: 1rpx solid rgba(148, 183, 210, 0.28);
	line-height: 1.3;
}

.head-sub {
	display: block;
	margin-top: 4rpx;
	font-size: 20rpx;
	color: $tavern-muted-on-dark;
	line-height: 1.35;
}

.create-chip {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 6rpx;
	min-height: 64rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: $tavern-accent-gradient;
	border: none;
	flex-shrink: 0;
	box-shadow: 0 8rpx 18rpx rgba(99, 102, 241, 0.18);
}

.create-txt,
.create-plus {
	color: #fff;
	font-weight: 700;
}

.create-txt {
	font-size: 26rpx;
}

.create-plus {
	font-size: 30rpx;
	line-height: 1;
}

.chip--disabled {
	opacity: 0.55;
}

.meta-bar {
	margin-top: 10rpx;
	display: flex;
	align-items: center;
	justify-content: flex-end;
	gap: 12rpx;
}

.meta-actions {
	display: flex;
	align-items: center;
	gap: 8rpx;
	flex-shrink: 0;
}

.meta-link {
	display: flex;
	align-items: center;
	gap: 4rpx;
	min-height: 52rpx;
	padding: 0 14rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.55);
	border: 1rpx solid rgba(148, 183, 210, 0.28);
}

.meta-link--disabled {
	opacity: 0.5;
}

.meta-link-txt,
.sort-arrow {
	font-size: 22rpx;
	color: #247494;
	font-weight: 600;
}

.sort-arrow {
	font-size: 18rpx;
	opacity: 0.85;
}

.lock-tip {
	display: block;
	margin-top: 12rpx;
	padding: 14rpx 16rpx;
	border-radius: 14rpx;
	font-size: 22rpx;
	line-height: 1.45;
	color: #92400e;
	background: rgba(251, 191, 36, 0.16);
	border: 1rpx solid rgba(245, 158, 11, 0.28);
}

.state-box {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 40rpx;
	gap: 18rpx;
}

.state-txt {
	font-size: 28rpx;
	color: $tavern-muted-on-dark;
	text-align: center;
}

.state-txt--err {
	color: #fda4af;
}

.state-tip {
	font-size: 22rpx;
	line-height: 1.5;
	text-align: center;
	color: $tavern-muted-on-dark;
}

.state-btn {
	padding: 16rpx 40rpx;
	border-radius: 999rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #fff;
	background: $tavern-accent-gradient;
}

.mine-skeleton {
	display: flex;
	flex-wrap: wrap;
	margin: 0 12rpx;
	padding-top: 4rpx;
	box-sizing: border-box;
}

.mine-skeleton-card {
	width: 50%;
	box-sizing: border-box;
	padding: 0 10rpx;
	margin: 0 0 22rpx;
	position: relative;
	overflow: hidden;
}

.mine-skeleton-visual,
.mine-skeleton-line,
.mine-skeleton-btn {
	position: relative;
	overflow: hidden;
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.08);
}

.mine-skeleton-visual::after,
.mine-skeleton-line::after,
.mine-skeleton-btn::after {
	content: '';
	position: absolute;
	inset: 0;
	transform: translateX(-100%);
	background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.16), transparent);
	animation: mine-skeleton-shimmer 1.35s ease-in-out infinite;
}

.mine-skeleton-visual {
	width: 100%;
	height: 500rpx;
	border-radius: 28rpx;
}

.page-scroll {
	flex: 1;
	height: 0;
	min-height: 0;
	width: 100%;
	box-sizing: border-box;
	overflow: hidden;
}

.grid2-wrap {
	display: flex;
	flex-wrap: wrap;
	margin: 0 12rpx;
	box-sizing: border-box;
}

.grid2-item {
	width: 50%;
	box-sizing: border-box;
	padding: 0 10rpx;
	margin-top: 0;
	margin-bottom: 22rpx;
}

.card-disc {
	position: relative;
	width: 100%;
	max-width: 346rpx;
	margin: 0 auto;
	overflow: visible;
	border-radius: 0;
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
}

.card-visual {
	position: relative;
	isolation: isolate;
	width: 100%;
	height: 500rpx;
	overflow: hidden;
	border-radius: 28rpx;
	background: transparent !important;
}

.card-visual::before {
	content: '';
	position: absolute;
	inset: 0;
	border-radius: 28rpx;
	padding: 2rpx;
	background: linear-gradient(145deg, #f3f4f6 0%, #9ca3af 40%, #e5e7eb 60%, #6b7280 100%);
	-webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
	-webkit-mask-composite: xor;
	mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
	mask-composite: exclude;
	pointer-events: none;
	z-index: 6;
	opacity: 0.9;
}

.card-bg {
	position: absolute;
	left: 0;
	top: 0;
	width: 100%;
	height: 100%;
	display: block;
	z-index: 0;
	/* #ifdef H5 */
	object-fit: cover;
	object-position: center top;
	/* #endif */
}

.card-shade {
	position: absolute;
	inset: 0;
	z-index: 2;
	pointer-events: none;
	background: linear-gradient(
		180deg,
		rgba(0, 0, 0, 0.08) 0%,
		rgba(0, 0, 0, 0.02) 28%,
		rgba(0, 0, 0, 0.28) 62%,
		rgba(0, 0, 0, 0.72) 100%
	);
}

.card-more {
	position: absolute;
	top: 14rpx;
	right: 14rpx;
	z-index: 8;
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(12, 20, 28, 0.42);
	border: 1rpx solid rgba(255, 255, 255, 0.18);
	/* #ifdef H5 */
	backdrop-filter: blur(14px);
	-webkit-backdrop-filter: blur(14px);
	/* #endif */
}

.card-more:active {
	transform: scale(0.94);
	background: rgba(12, 20, 28, 0.58);
}

.card-more-dot {
	font-size: 28rpx;
	line-height: 1;
	color: rgba(255, 255, 255, 0.95);
	font-weight: 700;
	letter-spacing: 1rpx;
	margin-top: -6rpx;
}

.card-visual-copy {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 3;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
	padding: 24rpx 20rpx 20rpx;
	pointer-events: none;
}

.card-visual-title {
	color: #fff;
	font-size: 30rpx;
	font-weight: 800;
	line-height: 1.25;
	text-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.55);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.card-visual-desc {
	color: rgba(255, 255, 255, 0.84);
	font-size: 21rpx;
	line-height: 1.4;
	text-shadow: 0 1rpx 8rpx rgba(0, 0, 0, 0.45);
	overflow: hidden;
	text-overflow: ellipsis;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-visual-review {
	color: #fecaca;
	font-size: 19rpx;
	line-height: 1.4;
	text-shadow: 0 1rpx 6rpx rgba(0, 0, 0, 0.45);
	overflow: hidden;
	text-overflow: ellipsis;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-float-tags {
	position: static;
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 6rpx;
	margin-top: 4rpx;
}

.float-tag {
	padding: 3rpx 12rpx;
	border-radius: 999rpx;
	font-size: 18rpx;
	font-weight: 650;
	line-height: 1.3;
	color: rgba(255, 244, 214, 0.96);
	background: rgba(0, 0, 0, 0.28);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
}

.float-tag--pending {
	color: #dbeafe;
	background: rgba(37, 99, 235, 0.42);
	border-color: rgba(147, 197, 253, 0.28);
}

.float-tag--reject {
	color: #fee2e2;
	background: rgba(185, 28, 28, 0.46);
	border-color: rgba(252, 165, 165, 0.28);
}

.float-tag--approved {
	color: #dcfce7;
	background: rgba(22, 163, 74, 0.42);
	border-color: rgba(134, 239, 172, 0.28);
}

.card-inline-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
	margin-top: 6rpx;
	padding-top: 10rpx;
	border-top: 1rpx solid rgba(255, 255, 255, 0.12);
}

.card-inline-handle {
	flex: 1;
	min-width: 0;
	font-size: 20rpx;
	font-weight: 650;
	color: rgba(255, 255, 255, 0.78);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.card-inline-heat {
	flex-shrink: 0;
	display: inline-flex;
	align-items: center;
	gap: 6rpx;
	font-size: 20rpx;
	font-weight: 700;
	color: rgba(255, 214, 120, 0.92);
}

.heart {
	line-height: 1;
}

.list-more {
	padding: 18rpx 24rpx 0;
	text-align: center;
}

.list-more-text {
	font-size: 22rpx;
	color: $tavern-muted-on-dark;
}

.pad {
	height: calc(110rpx + env(safe-area-inset-bottom));
}

@keyframes mine-skeleton-shimmer {
	100% {
		transform: translateX(100%);
	}
}

/* Light clover tavern mine-page contrast refresh. */
.head-sub,
.meta-count,
.state-txt,
.state-tip,
.list-more-text {
	color: #4d6678;
}

.card-disc {
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
}
</style>
