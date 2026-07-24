<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="uiText.title" mode="dark" @back="goBack" />
		<view class="body">
			<view class="search-panel">
				<view class="search-bar">
					<image class="ico" src="/static/cha.png" mode="widthFix"></image>
					<input
						class="inp"
						v-model="keyword"
						:focus="true"
						confirm-type="search"
						:placeholder="uiText.placeholder"
						@confirm="onSearch"
					/>
					<text v-if="keyword" class="clear-btn" @tap="clearKeyword">×</text>
				</view>
				<view v-if="quickTags.length" class="quick-tags">
					<text class="section-title">{{ uiText.quickTagsTitle }}</text>
					<view class="chip-row">
						<view
							v-for="tag in quickTags"
							:key="tag"
							class="chip"
							:class="{ 'chip--active': selectedTag === tag }"
							@tap="applyQuickTag(tag)"
						>{{ tag }}</view>
					</view>
				</view>
			</view>

			<view v-if="loading && !activeSourceList.length" class="state-block">
				<text class="state-title">{{ uiText.loading }}</text>
			</view>

			<view v-else-if="errorText && !activeSourceList.length" class="state-block">
				<text class="state-title">{{ errorText }}</text>
				<view class="retry-btn" @tap="retryCurrent">{{ uiText.errorRetry }}</view>
			</view>

			<template v-else-if="activeSourceList.length">
				<view class="result-head">
					<view class="result-copy">
						<text class="result-title">{{ sectionTitle }}</text>
					</view>
					<view class="search-btn" @tap="onSearch">{{ uiText.button }}</view>
				</view>

				<view class="grid2-wrap">
					<view
						v-for="item in activeList"
						:key="item.id"
						class="grid2-item"
						@tap="openDetail(item.id)"
					>
						<view
							class="card-disc"
							:class="[cardTierClass(item), { 'card-disc--hover': hoverId === item.id }]"
							@mouseenter="setHover(item.id)"
							@mouseleave="clearHover"
							@touchstart="setHover(item.id)"
							@touchend="clearHover"
							@touchcancel="clearHover"
						>
							<view class="card-grade">
								<text class="card-grade-label">{{ cardTierLabel(item) }}</text>
							</view>
							<view class="card-visual">
								<image class="card2-bg" :class="{ 'card2-bg--blur': isPreviewBlurActive(item) }" :src="coverUrl(item)" mode="aspectFill" lazy-load></image>
								<view
									v-if="isPreviewBlurActive(item)"
									class="preview-blur-surface"
									:style="blurSurfaceStyle(coverUrl(item), 'cover')"
								></view>
								<view v-if="isPreviewBlurActive(item)" class="preview-blur-layer">
									<view class="preview-blur-pill">{{ previewBlurBadgeText(item) }}</view>
									<text class="preview-blur-note">{{ previewBlurHintText(item) }}</text>
								</view>
								<view class="card-shade" aria-hidden="true"></view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ item.nickname }}</text>
									<text class="card-visual-desc">{{ cardPreview(item) || cardHeroCopy(item) }}</text>
									<view class="card-float-tags" v-if="safeLabels(item).length">
										<text
											v-for="(label, idx) in safeLabels(item)"
											:key="item.id + '_label_' + idx"
											class="float-tag"
										>{{ label.code }}</text>
									</view>
									<view class="card-inline-foot">
										<text class="card-inline-handle">{{ displayHandle(item) }}</text>
										<text
											v-if="item.unlocked === false"
											class="card-inline-unlock"
											@tap.stop="handleCardAction(item)"
										>{{ discoverUi.openVip }}</text>
										<view v-else class="card-inline-heat">
											<text class="heart sm">❤</text>
											<text>{{ formatCount(item.like_count) }}</text>
										</view>
									</view>
								</view>
							</view>
						</view>
					</view>
				</view>
				<view v-if="hasMoreResults" class="list-more" @tap="loadMoreResults">
					<text class="list-more-text">{{ listProgressText(activeList.length, activeSourceList.length) }}</text>
				</view>
			</template>

			<view v-else-if="searched" class="state-block state-block--soft">
				<text class="state-title">{{ uiText.emptyTitle }}</text>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const { getTavernUiText } = require('@/common/tavernUiI18n.js');
const tavernCharacterAccess = require('@/common/tavernCharacterAccess.js');
const tavernListPerf = require('@/common/tavernListPerf.js');
const SEARCH_RESULT_LIMIT = 24;
const SEARCH_DEFAULT_LIMIT = 12;
const SEARCH_INITIAL_VISIBLE = 8;
const SEARCH_BATCH_VISIBLE = 8;

const DEFAULT_TAGS = ['奇幻', '校园', '恋爱', '冒险', '日常', '悬疑', '科幻', '古风'];

export default {
	components: { TavernNavBar },
	data() {
		return {
			keyword: '',
			selectedTag: '',
			loading: false,
			errorText: '',
			searched: false,
			resultList: [],
			defaultList: [],
			quickTags: DEFAULT_TAGS,
			hoverId: null,
			listRequestSeq: 0,
			viewerSignature: '',
			visibleCount: 0
		};
	},
	computed: {
		uiText() {
			return getTavernUiText('search');
		},
		discoverUi() {
			return getTavernUiText('discover');
		},
		activeSourceList() {
			return this.searched ? this.resultList : this.defaultList;
		},
		activeList() {
			return tavernListPerf.sliceVisibleList(this.activeSourceList, this.visibleCount, SEARCH_INITIAL_VISIBLE);
		},
		hasMoreResults() {
			return tavernListPerf.hasMoreItems(this.activeSourceList, this.visibleCount, SEARCH_INITIAL_VISIBLE);
		},
		sectionTitle() {
			return this.searched ? this.uiText.resultTitle : this.discoverUi.discoverTitle;
		}
	},
	onLoad(query) {
		this.viewerSignature = tavernApi.getViewerStateSignature();
		const fromQuery = query && query.q ? decodeURIComponent(query.q) : '';
		const fromTag = query && query.tag ? decodeURIComponent(query.tag) : '';
		this.keyword = String(fromQuery || fromTag || '').trim();
		this.selectedTag = fromTag ? String(fromTag).trim() : '';
		this.loadQuickTags();
		if (this.selectedTag) {
			this.runSearch({ tag: this.selectedTag });
			return;
		}
		if (this.keyword) {
			this.runSearch({ q: this.keyword });
			return;
		}
			this.loadDefaultList();
	},
	onShow() {
		const currentViewerSignature = tavernApi.getViewerStateSignature();
		const shouldRefresh =
			tavernApi.consumeCharacterAccessRefreshNeeded() ||
			currentViewerSignature !== this.viewerSignature;
		if (!shouldRefresh) {
			return;
		}
		this.viewerSignature = currentViewerSignature;
		if (this.searched && this.selectedTag) {
			this.runSearch({ tag: this.selectedTag, fallbackQuery: this.selectedTag });
			return;
		}
		if (this.searched && this.keyword) {
			this.runSearch({ q: this.keyword });
			return;
		}
		this.loadDefaultList();
	},
	onUnload() {
		this.markDiscoverSearchClear();
	},
	onReachBottom() {
		this.loadMoreResults();
	},
	methods: {
		syncVisibleCount() {
			this.visibleCount = tavernListPerf.syncVisibleCount(
				this.visibleCount,
				this.activeSourceList.length,
				SEARCH_INITIAL_VISIBLE
			);
		},
		loadMoreResults() {
			if (this.loading) return;
			this.visibleCount = tavernListPerf.expandVisibleCount(
				this.visibleCount,
				this.activeSourceList.length,
				SEARCH_BATCH_VISIBLE,
				SEARCH_INITIAL_VISIBLE
			);
		},
		listProgressText(visibleCount, totalCount) {
			const safeVisible = Math.max(0, Number(visibleCount) || 0);
			const safeTotal = Math.max(0, Number(totalCount) || 0);
			if (!safeTotal || safeVisible >= safeTotal) {
				return '';
			}
			return '已显示 ' + safeVisible + ' / ' + safeTotal + '，点击继续加载';
		},
		markDiscoverSearchClear() {
			try {
				uni.removeStorageSync('tavern_discover_q');
				uni.setStorageSync('tavern_discover_clear_search', '1');
			} catch (e) {}
		},
		loadQuickTags() {
			tavernApi
				.fetchCharacterTags()
				.then((list) => {
					if (!Array.isArray(list) || !list.length) return;
					const tags = list
						.map((item) => {
							if (typeof item === 'string') return item;
							return item && (item.name || item.code || item.label)
								? String(item.name || item.code || item.label)
								: '';
						})
						.filter(Boolean)
						.slice(0, 8);
					if (tags.length) {
						this.quickTags = tags;
					}
				})
				.catch(() => {});
		},
		goBack() {
			this.markDiscoverSearchClear();
			uni.navigateBack({
				fail: () => uni.switchTab({ url: '/pages/index/index' })
			});
		},
		clearKeyword() {
			this.keyword = '';
			this.selectedTag = '';
			this.errorText = '';
			this.searched = false;
			this.resultList = [];
			this.visibleCount = 0;
			this.loadDefaultList();
		},
		applyQuickTag(tag) {
			this.selectedTag = String(tag || '').trim();
			this.keyword = this.selectedTag;
			this.runSearch({ tag: this.selectedTag, fallbackQuery: this.selectedTag });
		},
		onSearch() {
			const q = String(this.keyword || '').trim();
			if (!q) {
				uni.showToast({ title: this.uiText.noKeyword, icon: 'none' });
				return;
			}
			this.selectedTag = '';
			this.runSearch({ q });
		},
		retryCurrent() {
			if (this.searched && this.selectedTag) {
				this.runSearch({ tag: this.selectedTag, fallbackQuery: this.selectedTag });
				return;
			}
			if (this.searched && this.keyword) {
				this.runSearch({ q: this.keyword });
				return;
			}
			this.loadDefaultList();
		},
		runSearch(params) {
			const rawPayload = params || {};
			const fallbackQuery = String(rawPayload.fallbackQuery || '').trim();
			const payload = Object.assign({ limit: SEARCH_RESULT_LIMIT }, rawPayload);
			delete payload.fallbackQuery;
			const requestSeq = ++this.listRequestSeq;
			this.visibleCount = 0;
			this.loading = true;
			this.errorText = '';
			this.searched = true;
			tavernApi
				.fetchCharacterList(payload)
				.then((list) => {
					if (requestSeq !== this.listRequestSeq) {
						return;
					}
					if ((!Array.isArray(list) || !list.length) && payload.tag && fallbackQuery) {
						return tavernApi.fetchCharacterList({ q: fallbackQuery, limit: SEARCH_RESULT_LIMIT }).then((fallbackList) => {
							if (requestSeq !== this.listRequestSeq) {
								return;
							}
							this.resultList = Array.isArray(fallbackList) ? fallbackList : [];
							this.syncVisibleCount();
						});
					}
					this.resultList = Array.isArray(list) ? list : [];
					this.syncVisibleCount();
				})
				.catch((err) => {
					if (requestSeq !== this.listRequestSeq) {
						return;
					}
					this.resultList = [];
					this.syncVisibleCount();
					this.errorText = (err && err.message) || this.uiText.emptyTitle;
				})
				.finally(() => {
					if (requestSeq === this.listRequestSeq) {
						this.loading = false;
					}
				});
		},
		loadDefaultList() {
			const requestSeq = ++this.listRequestSeq;
			this.visibleCount = 0;
			this.loading = true;
			this.errorText = '';
			tavernApi
				.fetchCharacterList({ limit: SEARCH_DEFAULT_LIMIT })
				.then((list) => {
					if (requestSeq !== this.listRequestSeq) {
						return;
					}
					this.defaultList = Array.isArray(list) ? list.slice(0, 8) : [];
					this.syncVisibleCount();
				})
				.catch((err) => {
					if (requestSeq !== this.listRequestSeq) {
						return;
					}
					this.defaultList = [];
					this.syncVisibleCount();
					this.errorText = (err && err.message) || '';
				})
				.finally(() => {
					if (requestSeq === this.listRequestSeq) {
						this.loading = false;
					}
				});
		},
		openDetail(id) {
			this.util.urlTo('/pages/tavern/charDetail?id=' + id);
		},
		handleCardAction(item) {
			if (item && item.unlocked === false) {
				this.util.urlTo('/pages/user/myvip');
				return;
			}
			this.openDetail(item && item.id);
		},
		setHover(id) {
			this.hoverId = id;
		},
		clearHover() {
			this.hoverId = null;
		},
		coverUrl(item) {
			const src = item && (item.cover_thumb || item.avatar_thumb || item.cover || item.avatar || item.image);
			return tavernApi.resolveJgAssetUrl(src) || '/static/logo.png';
		},
		blurSurfaceStyle(src, fitMode) {
			const safeSrc = src && String(src).trim() ? String(src).trim() : '/static/logo.png';
			const normalized = safeSrc.replace(/"/g, '%22');
			const isContain = fitMode === 'contain';
			return {
				backgroundImage: 'url("' + normalized + '")',
				backgroundSize: isContain ? 'contain' : 'cover',
				backgroundPosition: isContain ? 'center top' : 'center center',
				backgroundRepeat: 'no-repeat'
			};
		},
		displayHandle(item) {
			return (item && (item.creator_handle || item.creatorHandle)) || '@share';
		},
		formatCount(value) {
			const count = Number(value || 0);
			if (count >= 10000) {
				return (count / 10000).toFixed(1).replace(/\.0$/, '') + 'w';
			}
			return String(count);
		},
		safeLabels(item) {
			const list = Array.isArray(item && item.label_array) ? item.label_array : [];
			return list
				.map((entry) => {
					if (typeof entry === 'string') return { code: entry };
					if (entry && entry.code) return entry;
					if (entry && entry.name) return { code: entry.name };
					return null;
				})
				.filter(Boolean)
				.slice(0, 3);
		},
		isPreviewBlurActive(card) {
			return tavernCharacterAccess.isPreviewBlurActive(card);
		},
		previewBlurBadgeText(card) {
			return tavernCharacterAccess.previewBlurBadgeText(card);
		},
		previewBlurHintText(card) {
			return tavernCharacterAccess.previewBlurHintText(card);
		},
		cardMetaBadges(item) {
			const badges = [];
			if (this.isPreviewBlurActive(item)) {
				badges.push({ text: this.previewBlurBadgeText(item), tone: 'blur' });
			}
			if (item && item.gameplay_type) {
				badges.push({ text: item.gameplay_type, tone: 'mode' });
			}
			if (item && item.vip_only) {
				badges.push({ text: this.uiText.vipOnly, tone: 'vip' });
			}
			if (item && Number(item.like_count || 0) <= 3) {
				badges.push({ text: this.uiText.newest, tone: 'new' });
			}
			return badges.slice(0, 3);
		},
		cardHeroCopy(item) {
			return this.truncate(this.normalizeText(item && (item.public_summary || item.publicSummary || item.tagline || item.bio || item.description)), 34);
		},
		cardVisualTier(item) {
			if (!item || typeof item !== 'object') {
				return 'standard';
			}
			const requiredLevel = Math.max(
				0,
				Math.min(2, Math.floor(Number(item.preview_blur_vip_level || item.previewBlurVipLevel || 0) || 0))
			);
			if (requiredLevel >= 2) {
				return 'svip';
			}
			if (requiredLevel >= 1 || item.vip_only || item.vipOnly) {
				return 'vip';
			}
			return 'standard';
		},
		cardTierClass(item) {
			return 'card-tier--' + this.cardVisualTier(item);
		},
		cardTierLabel(item) {
			const tier = this.cardVisualTier(item);
			if (tier === 'svip') return 'SVIP';
			if (tier === 'vip') return 'VIP';
			return 'R';
		},
		cardPreview(item) {
			const candidates = [
				item && (item.public_summary || item.publicSummary),
				item && item.description,
				item && item.bio,
				item && item.gameplay_type,
				this.safeLabels(item).map((entry) => entry.code).join(' / ')
			];
			for (let i = 0; i < candidates.length; i += 1) {
				const text = this.normalizeText(candidates[i]);
				if (text) return this.truncate(text, 68);
			}
			return '';
		},
		normalizeText(text) {
			if (text == null) return '';
			return String(text)
				.replace(/\s+/g, ' ')
				.replace(/^[\s·|/,-]+|[\s·|/,-]+$/g, '')
				.trim();
		},
		truncate(text, max) {
			const value = this.normalizeText(text);
			if (!value) return '';
			if (value.length <= max) return value;
			return value.slice(0, max) + '...';
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	min-height: 100vh;
	background: transparent;
	overflow: hidden;
}

.body {
	padding: 22rpx 0 40rpx;
}

.search-panel {
	margin: 0 24rpx;
	padding: 22rpx;
	border-radius: 28rpx;
	background: rgba(106, 85, 145, 0.18);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow: 0 22rpx 52rpx rgba(7, 8, 20, 0.22);
}

.search-bar {
	display: flex;
	align-items: center;
	height: 92rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: rgba(20, 21, 33, 0.95);
}

.ico {
	width: 30rpx;
	margin-right: 14rpx;
}

.inp {
	flex: 1;
	height: 92rpx;
	color: #fff;
	font-size: 30rpx;
}

.clear-btn {
	width: 46rpx;
	height: 46rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	color: #f87171;
	background: rgba(248, 113, 113, 0.12);
}

.quick-tags {
	margin-top: 28rpx;
}

.section-title {
	display: block;
	font-size: 28rpx;
	color: rgba(233, 233, 249, 0.9);
}

.chip-row {
	display: flex;
	flex-wrap: wrap;
	gap: 18rpx;
	margin-top: 18rpx;
}

.chip {
	padding: 14rpx 24rpx;
	border-radius: 999rpx;
	font-size: 28rpx;
	color: rgba(237, 237, 249, 0.92);
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.chip--active {
	color: #fff;
	background: linear-gradient(135deg, rgba(139, 92, 246, 0.32), rgba(236, 72, 153, 0.3));
	border-color: rgba(234, 179, 255, 0.34);
}

.result-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
	margin: 34rpx 24rpx 0;
}

.result-title {
	display: block;
	font-size: 46rpx;
	font-weight: 700;
	color: #fff;
}

.search-btn {
	flex-shrink: 0;
	height: 68rpx;
	line-height: 68rpx;
	padding: 0 28rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #8b5cf6, #ec4899);
	color: #fff;
	font-size: 24rpx;
	font-weight: 700;
	box-shadow: 0 18rpx 36rpx rgba(195, 80, 172, 0.24);
}

.grid2-wrap {
	display: flex;
	flex-wrap: wrap;
	margin: 0 14rpx;
}

.grid2-item {
	width: 50%;
	padding: 0 10rpx;
	margin-top: 20rpx;
	box-sizing: border-box;
}

.card-disc {
	--tier-rim: linear-gradient(145deg, #f3f4f6 0%, #9ca3af 40%, #e5e7eb 60%, #6b7280 100%);
	--tier-ink-a: 156, 163, 175;
	position: relative;
	width: 100%;
	border-radius: 0;
	overflow: visible;
	background: transparent;
	border: none;
	box-shadow: none;
}

.card-tier--vip {
	--tier-rim: linear-gradient(135deg, #e0f2fe 0%, #38bdf8 25%, #60a5fa 50%, #7dd3fc 75%, #bae6fd 100%);
	--tier-ink-a: 56, 189, 248;
}

.card-tier--svip {
	--tier-rim: linear-gradient(125deg, #f9a8d4 0%, #c084fc 20%, #818cf8 40%, #67e8f9 60%, #fde047 80%, #fb7185 100%);
	--tier-ink-a: 192, 132, 252;
}

.card-grade {
	position: absolute;
	top: -2rpx;
	left: -2rpx;
	z-index: 8;
	padding: 12rpx 16rpx 8rpx 10rpx;
	pointer-events: none;
	transform: rotate(-6deg);
	transform-origin: 0 0;
}

.card-tier--vip .card-grade {
	transform: rotate(-4deg);
}

.card-disc:not(.card-tier--vip):not(.card-tier--svip) .card-grade {
	transform: rotate(-3deg);
	top: 2rpx;
	left: 2rpx;
}

.card-grade-label {
	font-family: Georgia, 'Times New Roman', 'Noto Serif SC', serif;
	font-size: 44rpx;
	font-weight: 900;
	font-style: italic;
	line-height: 0.85;
	color: #e5e7eb;
	text-shadow: 0 2rpx 2rpx rgba(0, 0, 0, 0.75), 0 0 12rpx rgba(var(--tier-ink-a), 0.7);
}

.card-tier--vip .card-grade-label {
	font-size: 40rpx;
	color: #bae6fd;
}

.card-tier--svip .card-grade-label {
	font-size: 48rpx;
	color: #f5d0fe;
}

.card-visual {
	position: relative;
	isolation: isolate;
	height: 550rpx;
	overflow: hidden;
	border-radius: 36rpx;
	background: transparent;
}

.card-visual::before {
	content: '';
	position: absolute;
	inset: 0;
	border-radius: 36rpx;
	padding: 3rpx;
	background: var(--tier-rim);
	background-size: 200% 200%;
	-webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
	-webkit-mask-composite: xor;
	mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
	mask-composite: exclude;
	pointer-events: none;
	z-index: 6;
	opacity: 0.95;
}

.card-tier--vip .card-visual::before,
.card-tier--svip .card-visual::before {
	padding: 4rpx;
}

.card2-bg {
	position: absolute;
	left: 0;
	top: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
	background: transparent;
	transition: transform 0.45s ease;
	/* #ifdef H5 */
	object-fit: cover;
	object-position: center top;
	/* #endif */
}

.card2-bg--blur {
	filter: blur(18rpx) scale(1.08) brightness(0.72);
}

.preview-blur-surface {
	position: absolute;
	inset: -18rpx;
	z-index: 1;
	pointer-events: none;
	filter: blur(30rpx) saturate(0.92) brightness(0.66);
	transform: scale(1.16);
	background-repeat: no-repeat;
	background-position: center center;
	background-size: cover;
	opacity: 1;
}

.card-shade {
	position: absolute;
	inset: 0;
	z-index: 2;
	pointer-events: none;
	background: linear-gradient(
		180deg,
		rgba(0, 0, 0, 0.02) 0%,
		rgba(0, 0, 0, 0.04) 42%,
		rgba(0, 0, 0, 0.38) 68%,
		rgba(0, 0, 0, 0.78) 100%
	);
}

/* #ifdef H5 */
.grid2-item,
.card-disc {
	cursor: pointer;
}

.card-disc:hover .card2-bg:not(.card2-bg--blur),
.card-disc--hover .card2-bg:not(.card2-bg--blur),
.card-disc:active .card2-bg:not(.card2-bg--blur) {
	transform: scale(1.04);
}

.card-disc:active {
	transform: scale(0.975);
}
/* #endif */

.preview-blur-layer {
	position: absolute;
	inset: 0;
	z-index: 3;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 32rpx 28rpx;
	background: linear-gradient(180deg, rgba(8, 10, 18, 0.16) 0%, rgba(8, 10, 18, 0.34) 100%);
	pointer-events: none;
}

.preview-blur-pill {
	padding: 12rpx 22rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	font-weight: 700;
	letter-spacing: 1rpx;
	color: #fff7ed;
	background: rgba(15, 23, 42, 0.46);
	border: 1rpx solid rgba(253, 186, 116, 0.34);
}

.preview-blur-note {
	margin-top: 14rpx;
	font-size: 22rpx;
	line-height: 1.6;
	text-align: center;
	color: rgba(255, 244, 230, 0.92);
}

.card-visual-copy {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 4;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
	padding: 24rpx 20rpx 18rpx;
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

.card-float-tags {
	position: static;
	display: flex;
	flex-wrap: wrap;
	gap: 6rpx;
	margin-top: 4rpx;
}

.float-tag {
	padding: 3rpx 12rpx;
	border-radius: 999rpx;
	font-size: 18rpx;
	font-weight: 650;
	color: rgba(255, 244, 214, 0.96);
	background: rgba(0, 0, 0, 0.28);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
}

.card-inline-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
	margin-top: 6rpx;
	padding-top: 10rpx;
	border-top: 1rpx solid rgba(255, 255, 255, 0.12);
	pointer-events: auto;
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

.card-inline-unlock {
	flex-shrink: 0;
	padding: 4rpx 12rpx;
	border-radius: 999rpx;
	font-size: 18rpx;
	font-weight: 700;
	color: #fff6d6;
	background: rgba(150, 100, 28, 0.42);
	border: 1rpx solid rgba(255, 220, 140, 0.28);
}

.heart.sm {
	color: rgba(255, 214, 120, 0.92);
}

.card-meta,
.card-float-top,
.hdl,
.like-badge,
.meta-badges,
.meta-badge,
.meta-desc,
.meta-foot,
.meta-handle,
.meta-cta {
	display: none;
}

.state-block {
	margin: 36rpx 24rpx 0;
	padding: 42rpx 28rpx;
	border-radius: 28rpx;
	background: rgba(19, 20, 31, 0.72);
	border: 1rpx solid rgba(255, 255, 255, 0.05);
	text-align: center;
}

.state-block--soft {
	background: rgba(19, 20, 31, 0.48);
}

.state-title {
	display: block;
	font-size: 28rpx;
	line-height: 1.7;
	color: rgba(248, 250, 252, 0.88);
}

.retry-btn {
	margin-top: 18rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	height: 64rpx;
	padding: 0 28rpx;
	border-radius: 999rpx;
	background: rgba(139, 92, 246, 0.18);
	border: 1rpx solid rgba(196, 181, 253, 0.26);
	color: #e9d5ff;
	font-size: 24rpx;
	font-weight: 700;
}

.list-more {
	margin: 22rpx 24rpx 0;
	padding: 20rpx 24rpx;
	border-radius: 22rpx;
	text-align: center;
	background: rgba(139, 92, 246, 0.12);
	border: 1rpx solid rgba(196, 181, 253, 0.16);
}

.list-more-text {
	font-size: 22rpx;
	line-height: 1.6;
	color: rgba(233, 213, 255, 0.9);
}

/* Light clover tavern search refresh. */
.page {
	background:
		radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
		radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
}

.search-panel,
.state-block,
.list-more {
	background: rgba(255, 255, 255, 0.88);
	border-color: rgba(255, 255, 255, 0.92);
	box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
}

.search-bar {
	background: rgba(255, 255, 255, 0.86);
	border: 1rpx solid rgba(88, 189, 210, 0.16);
}

.inp,
.section-title,
.result-title,
.state-title {
	color: #244b66;
}

.chip {
	color: #5b7287;
	background: rgba(255, 255, 255, 0.82);
	border-color: rgba(88, 189, 210, 0.16);
}

.chip--active,
.search-btn,
.retry-btn {
	color: #fff;
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
	border-color: rgba(255, 255, 255, 0.72);
	box-shadow: 0 12rpx 26rpx rgba(52, 143, 184, 0.18);
}

.card-disc {
	background: transparent;
	border: none;
	box-shadow: none;
}

.list-more-text {
	color: #687f92;
}
</style>
