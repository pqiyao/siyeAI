<template>
	<view class="page" :class="localeFontClass">
		<view class="head-strip" :style="{ paddingTop: statusBarH + 12 + 'px' }">
			<view class="head-row">
				<view>
					<text class="head-title">{{ texts.mineTitle }}</text>
					<text class="head-sub">{{ texts.privateOnlyIntro }}</text>
				</view>
				<view class="head-actions">
					<view class="sort-chip" @tap="openSort">
						<text class="sort-txt">{{ sortLabel }}</text>
						<text class="sort-arrow">v</text>
					</view>
					<view
						class="import-chip"
						:class="{ 'chip--disabled': importing || !featureConfig.userCharacterCreationEnabled }"
						@tap="openImportPng"
					>
						<text class="import-txt">{{ importing ? importProgressLabel : texts.importCard }}</text>
					</view>
					<view class="create-chip" :class="{ 'chip--disabled': !featureConfig.userCharacterCreationEnabled }" @tap="openCreate">
						<text class="create-plus">+</text>
						<text class="create-txt">{{ texts.createCard }}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="summary-card">
			<view class="summary-main">
				<text class="summary-num">{{ list.length }}</text>
				<text class="summary-label">{{ texts.createdCount }}</text>
			</view>
			<text class="summary-tip">{{ texts.summaryTip }}</text>
			<text v-if="!featureConfig.userCharacterCreationEnabled" class="summary-lock-tip">{{ texts.creationPaused }}</text>
		</view>

		<view v-if="loading && !visibleList.length" class="mine-skeleton">
			<view v-for="n in skeletonList" :key="'mine_skeleton_' + n" class="mine-skeleton-card">
				<view class="mine-skeleton-visual"></view>
				<view class="mine-skeleton-line mine-skeleton-line--title"></view>
				<view class="mine-skeleton-line"></view>
				<view class="mine-skeleton-actions">
					<view class="mine-skeleton-btn"></view>
					<view class="mine-skeleton-btn mine-skeleton-btn--ghost"></view>
				</view>
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
							<view class="card-float-top">
								<text class="hdl">{{ displayHandle(item) }}</text>
								<view class="like-badge">
									<text class="heart">{{ texts.heart }}</text>
									<text>{{ formatLikes(item.like_count) }}</text>
								</view>
							</view>
						<view class="card-float-tags">
							<text class="float-tag float-tag--private">{{ texts.onlyMe }}</text>
							<text v-if="reviewStatusText(item)" class="float-tag" :class="reviewStatusClass(item)">{{ reviewStatusText(item) }}</text>
						</view>
					</view>
						<view class="card-meta">
							<view class="meta-title-row">
								<text class="meta-title">{{ item.nickname || '-' }}</text>
							</view>
							<text class="meta-desc">{{ cardSubtitle(item) }}</text>
							<text v-if="item.review_reason" class="meta-review">{{ item.review_reason }}</text>
						</view>
						<view class="card-actions">
							<view
								class="action-btn action-btn--ghost action-btn--danger"
								:class="{ 'action-btn--disabled': deletingId === item.id }"
								@tap.stop="confirmDelete(item)"
							>
								{{ deletingId === item.id ? texts.deleting : texts.deleteCard }}
							</view>
							<view class="action-btn" @tap.stop="openEditor(item)">
								{{ texts.editContent }}
							</view>
						</view>
					</view>
				</view>
			</view>
			<view v-if="hasMoreCards" class="list-more">
				<text class="list-more-text">{{ listProgressText(visibleList.length, list.length) }}</text>
			</view>
			<view class="pad"></view>
		</scroll-view>
	</view>
</template>

<script>
import { applyTavernTabBarLabels, syncTavernTabBar } from '@/common/tavernTabBar.js';
const tavernListPerf = require('@/common/tavernListPerf.js');

const MINE_INITIAL_VISIBLE = 8;
const MINE_BATCH_VISIBLE = 8;
const MINE_SKELETON_COUNT = 4;

const TEXTS = Object.freeze({
	mineTitle: '\u6211\u7684\u89d2\u8272',
	privateOnlyIntro: '\u4ec5\u81ea\u5df1\u53ef\u89c1\uff0c\u4e0d\u4f1a\u51fa\u73b0\u5728\u53d1\u73b0\u9875',
	createCard: '\u65b0\u5efa\u89d2\u8272\u5361',
	importCard: '\u5bfc\u5165PNG',
	importing: '\u5bfc\u5165\u4e2d...',
	importPngOnly: '\u8bf7\u9009\u62e9 .png \u683c\u5f0f\u7684 ST \u89d2\u8272\u5361',
	importTooLarge: '\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 10MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5',
	createdCount: '\u5df2\u521b\u5efa\u89d2\u8272\u5361',
	summaryTip: '\u652f\u6301\u5934\u50cf\u3001\u5c01\u9762\u3001\u5267\u60c5\u3001\u5f00\u573a\u767d\u3001\u63d0\u793a\u8bcd\u7b49\u5b8c\u6574\u5185\u5bb9\u7f16\u8f91',
	pendingReview: '\u5f85\u5ba1\u6838',
	rejectedReview: '\u5df2\u9a73\u56de',
	approvedReview: '\u5df2\u901a\u8fc7',
	loading: '\u52a0\u8f7d\u4e2d...',
	retry: '\u70b9\u51fb\u91cd\u8bd5',
	empty: '\u8fd8\u6ca1\u6709\u521b\u5efa\u89d2\u8272\u5361',
	emptyTip: '\u521b\u5efa\u540e\u7684\u89d2\u8272\u5361\u4f1a\u5728\u8fd9\u91cc\u72ec\u7acb\u7ba1\u7406\uff0c\u4e0d\u4f1a\u8fdb\u5165\u53d1\u73b0\u9875',
	createNow: '\u7acb\u5373\u521b\u5efa',
	sortName: '\u6309\u540d\u79f0',
	sortRecent: '\u6309\u6700\u8fd1\u521b\u5efa',
	onlyMe: '\u4ec5\u81ea\u5df1\u53ef\u89c1',
	editContent: '\u7f16\u8f91\u5185\u5bb9',
	deleteCard: '\u5220\u9664\u89d2\u8272',
	deleting: '\u5220\u9664\u4e2d...',
	deleteTitle: '\u5220\u9664\u89d2\u8272\u5361',
	deleteContent: '\u5220\u9664\u540e\u5c06\u4e00\u8d77\u6e05\u7406\u8fd9\u4e2a\u89d2\u8272\u7684\u804a\u5929\u8bb0\u5f55\u3001\u957f\u671f\u8bb0\u5fc6\u548c\u4e92\u52a8\u6570\u636e\uff0c\u786e\u5b9a\u7ee7\u7eed\u5417\uff1f',
	deleteSuccess: '\u5220\u9664\u6210\u529f',
	deleteFailed: '\u5220\u9664\u5931\u8d25',
	anon: '\u533f\u540d',
	noIntro: '\u6682\u65e0\u7b80\u4ecb',
	backendOff: '\u540e\u7aef\u63a5\u53e3\u672a\u5f00\u542f',
	loadFailed: '\u52a0\u8f7d\u5931\u8d25',
	importSuccess: '\u5bfc\u5165\u6210\u529f',
	importFailed: '\u5bfc\u5165\u5931\u8d25',
	creationPaused: '\u5f53\u524d\u5df2\u6682\u505c\u7528\u6237\u7aef\u65b0\u5efa\u548c\u5bfc\u5165\u89d2\u8272\u5361\uff0c\u4f46\u5df2\u521b\u5efa\u7684\u89d2\u8272\u4ecd\u53ef\u7ee7\u7eed\u7ba1\u7406\u3002',
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
		this.syncFeatureConfig(true);
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
		ensureCreationEnabled() {
			if (this.featureConfig.userCharacterCreationEnabled) {
				return true;
			}
			uni.showToast({ title: this.texts.creationPaused, icon: 'none', duration: 2600 });
			return false;
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
			const raw = item && (item.cover || item.avatar) ? item.cover || item.avatar : '';
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
			if (!this.ensureCreationEnabled()) {
				return;
			}
			uni.navigateTo({ url: '/pages/tavern/tavernEditor' });
		},
		openImportPng() {
			if (this.loading || this.importing || this.deletingId) {
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
			if (tavernApi.canUseBrowserFilePicker && tavernApi.canUseBrowserFilePicker()) {
				tavernApi
					.pickBrowserPngFile()
					.then((file) => {
						this.importMyPngFile(file);
					})
					.catch((e) => {
						if (e && String(e.message || '') === 'cancelled') {
							return;
						}
					});
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
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	background: $tavern-page-bg;
	padding-bottom: calc(24rpx + #{$tavern-tabbar-spacer} + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.head-strip {
	padding: 0 24rpx 12rpx;
}

.head-row {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18rpx;
}

.head-title {
	display: block;
	font-size: 36rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.head-sub {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: $tavern-muted-on-dark;
}

.head-actions {
	display: flex;
	align-items: center;
	gap: 12rpx;
	flex-shrink: 0;
}

.sort-chip,
.import-chip,
.create-chip {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	min-height: 64rpx;
	padding: 0 18rpx;
	border-radius: 999rpx;
	border: 1rpx solid $tavern-border-on-dark;
}

.sort-chip {
	background: rgba(255, 255, 255, 0.05);
}

.import-chip {
	background: rgba(255, 255, 255, 0.08);
}

.create-chip {
	background: $tavern-accent-gradient;
	border-color: transparent;
}

.sort-txt,
.import-txt,
.create-txt {
	font-size: 22rpx;
}

.sort-txt,
.sort-arrow,
.import-txt {
	color: $tavern-muted-on-dark;
}

.create-txt,
.create-plus {
	color: #fff;
	font-weight: 700;
}

.create-plus {
	font-size: 28rpx;
	line-height: 1;
}

.chip--disabled {
	opacity: 0.55;
}

.summary-card {
	margin: 0 20rpx 16rpx;
	padding: 18rpx 20rpx;
	border-radius: $tavern-radius-lg;
	background: linear-gradient(135deg, rgba(139, 92, 246, 0.16), rgba(15, 23, 42, 0.92));
	border: 1rpx solid rgba(196, 181, 253, 0.18);
	box-shadow: $tavern-card-shadow;
}

.summary-main {
	display: flex;
	align-items: baseline;
	gap: 12rpx;
}

.summary-num {
	font-size: 42rpx;
	font-weight: 700;
	color: #fff;
}

.summary-label {
	font-size: 24rpx;
	color: #ddd6fe;
}

.summary-tip {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: rgba(255, 255, 255, 0.75);
}

.summary-lock-tip {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #fde68a;
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
	margin: 0 20rpx;
	padding-top: 8rpx;
}

.mine-skeleton-card {
	width: 336rpx;
	margin: 16rpx auto 0;
	padding-bottom: 18rpx;
	position: relative;
	overflow: hidden;
	border-radius: 28rpx;
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
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
	width: 336rpx;
	height: 430rpx;
	border-radius: 28rpx 28rpx 0 0;
}

.mine-skeleton-line {
	width: 292rpx;
	height: 22rpx;
	margin: 18rpx 22rpx 0;
}

.mine-skeleton-line--title {
	width: 188rpx;
	height: 28rpx;
}

.mine-skeleton-actions {
	display: flex;
	gap: 12rpx;
	margin: 22rpx 18rpx 0;
}

.mine-skeleton-btn {
	flex: 1;
	height: 64rpx;
	border-radius: 16rpx;
}

.mine-skeleton-btn--ghost {
	background: rgba(255, 255, 255, 0.05);
}

.page-scroll {
	flex: 1;
	height: 0;
}

.grid2-wrap {
	display: flex;
	flex-wrap: wrap;
	margin: 0 20rpx;
}

.grid2-item {
	width: 50%;
	margin-top: 16rpx;
}

.card-disc {
	width: 336rpx;
	overflow: hidden;
	border-radius: 28rpx;
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.card-visual {
	position: relative;
	height: 430rpx;
	background: rgba(255, 255, 255, 0.04);
}

.card-bg {
	width: 100%;
	height: 100%;
	display: block;
}

.card-float-top {
	position: absolute;
	left: 16rpx;
	right: 16rpx;
	top: 16rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10rpx;
}

.hdl,
.like-badge {
	max-width: 140rpx;
	padding: 8rpx 12rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	color: #fff;
	background: rgba(15, 23, 42, 0.55);
	backdrop-filter: blur(8rpx);
}

.hdl {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.like-badge {
	display: flex;
	align-items: center;
	gap: 6rpx;
	justify-content: center;
}

.heart {
	line-height: 1;
}

.card-float-tags {
	position: absolute;
	left: 16rpx;
	right: 16rpx;
	bottom: 16rpx;
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10rpx;
}

.float-tag {
	padding: 10rpx 14rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	line-height: 1;
	color: #fff;
	background: rgba(15, 23, 42, 0.55);
}

.float-tag--private {
	background: rgba(139, 92, 246, 0.76);
}

.float-tag--pending {
	background: rgba(59, 130, 246, 0.76);
}

.float-tag--reject {
	background: rgba(239, 68, 68, 0.82);
}

.float-tag--approved {
	background: rgba(34, 197, 94, 0.76);
}

.card-meta {
	padding: 18rpx 18rpx 0;
}

.meta-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.meta-title {
	flex: 1;
	min-width: 0;
	font-size: 28rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.meta-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: $tavern-muted-on-dark;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
	min-height: 66rpx;
}

.meta-review {
	display: block;
	margin-top: 10rpx;
	font-size: 20rpx;
	line-height: 1.5;
	color: #fca5a5;
}

.card-actions {
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 18rpx;
}

.action-btn {
	flex: 1;
	height: 64rpx;
	line-height: 64rpx;
	text-align: center;
	border-radius: 16rpx;
	font-size: 22rpx;
	font-weight: 700;
	color: #fff;
	background: $tavern-accent-gradient;
}

.action-btn--ghost {
	color: $tavern-text-on-dark;
	background: rgba(255, 255, 255, 0.06);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.action-btn--danger {
	color: #fecaca;
	border-color: rgba(248, 113, 113, 0.22);
	background: rgba(127, 29, 29, 0.22);
}

.action-btn--disabled {
	opacity: 0.58;
	pointer-events: none;
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
	height: calc(24rpx + env(safe-area-inset-bottom));
}

@keyframes mine-skeleton-shimmer {
	100% {
		transform: translateX(100%);
	}
}
</style>
