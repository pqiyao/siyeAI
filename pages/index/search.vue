<template>
	<view class="page" :class="localeFontClass">
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
							:class="{ 'card-disc--hover': hoverId === item.id }"
							@mouseenter="setHover(item.id)"
							@mouseleave="clearHover"
							@touchstart="setHover(item.id)"
							@touchend="clearHover"
							@touchcancel="clearHover"
						>
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
								<view class="card-float-top">
									<text class="hdl">{{ displayHandle(item) }}</text>
									<view class="like-badge">
										<text class="heart sm">❤</text>
										<text>{{ formatCount(item.like_count) }}</text>
									</view>
								</view>
								<view class="card-float-tags" v-if="safeLabels(item).length">
									<text
										v-for="(label, idx) in safeLabels(item)"
										:key="item.id + '_label_' + idx"
										class="float-tag"
										:class="'tone-' + (idx % 3)"
									>{{ label.code }}</text>
								</view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ item.nickname }}</text>
									<text class="card-visual-desc">{{ cardHeroCopy(item) }}</text>
								</view>
							</view>
							<view class="card-meta">
								<view v-if="cardMetaBadges(item).length" class="meta-badges">
									<text
										v-for="(badge, index) in cardMetaBadges(item)"
										:key="item.id + '_badge_' + index"
										class="meta-badge"
										:class="'meta-badge--' + badge.tone"
									>{{ badge.text }}</text>
								</view>
								<text class="meta-desc">{{ cardPreview(item) }}</text>
								<view class="meta-foot">
									<text class="meta-handle">{{ displayHandle(item) }}</text>
									<text class="meta-cta" @tap.stop="handleCardAction(item)">{{ item.unlocked === false ? discoverUi.openVip : discoverUi.viewDetail }}</text>
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
			const src = item && (item.cover || item.avatar || item.image);
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
			return this.truncate(this.normalizeText(item && (item.tagline || item.bio || item.description)), 34);
		},
		cardPreview(item) {
			const candidates = [
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
	min-height: 100vh;
	background:
		radial-gradient(circle at top, rgba(110, 72, 255, 0.2), transparent 34%),
		linear-gradient(180deg, #161425 0%, #161223 100%);
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
	width: 100%;
	border-radius: 28rpx;
	overflow: hidden;
	background: rgba(17, 19, 31, 0.94);
	border: 1rpx solid rgba(255, 255, 255, 0.06);
	box-shadow: 0 22rpx 44rpx rgba(6, 8, 16, 0.28);
}

.card-visual {
	position: relative;
	height: 592rpx;
	overflow: hidden;
	background: #141726;
}

.card2-bg {
	position: absolute;
	left: 0;
	top: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
	background: #141726;
	transition: transform 0.4s ease, filter 0.4s ease;
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

.card-visual::after {
	content: '';
	position: absolute;
	inset: 0;
	background:
		linear-gradient(180deg, rgba(10, 12, 20, 0.02) 0%, rgba(10, 12, 20, 0.12) 35%, rgba(10, 12, 20, 0.9) 100%),
		linear-gradient(135deg, rgba(173, 104, 255, 0.12), transparent 42%);
}

/* #ifdef H5 */
.grid2-item,
.card-disc {
	cursor: pointer;
}

.card-disc:hover .card2-bg,
.card-disc--hover .card2-bg,
.card-disc:active .card2-bg {
	transform: translateY(-28rpx) scale(1.14);
	filter: brightness(1.08) saturate(1.08);
}

.card-disc:hover .card2-bg--blur,
.card-disc--hover .card2-bg--blur,
.card-disc:active .card2-bg--blur {
	filter: blur(18rpx) scale(1.14) brightness(0.74);
}
/* #endif */

.card-float-top,
.card-float-tags,
.card-visual-copy {
	position: absolute;
	left: 18rpx;
	right: 18rpx;
	z-index: 4;
}

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
	box-shadow: 0 12rpx 24rpx rgba(8, 10, 18, 0.22);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
}

.preview-blur-note {
	margin-top: 14rpx;
	font-size: 22rpx;
	line-height: 1.6;
	text-align: center;
	color: rgba(255, 244, 230, 0.92);
}

.card-float-top {
	top: 18rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 14rpx;
}

.hdl,
.like-badge {
	background: rgba(8, 10, 18, 0.48);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	backdrop-filter: blur(20rpx);
	-webkit-backdrop-filter: blur(20rpx);
}

.hdl {
	max-width: 66%;
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: #fff;
}

.like-badge {
	display: inline-flex;
	align-items: center;
	gap: 8rpx;
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: #fbe1ff;
}

.heart.sm {
	color: #ff79c8;
}

.card-float-tags {
	bottom: 108rpx;
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
}

.float-tag {
	padding: 8rpx 14rpx;
	border-radius: 16rpx;
	font-size: 20rpx;
	color: #f6ebff;
	background: rgba(255, 255, 255, 0.08);
	border: 1rpx solid rgba(255, 255, 255, 0.1);
}

.tone-0 { background: rgba(59, 130, 246, 0.22); }
.tone-1 { background: rgba(236, 72, 153, 0.22); }
.tone-2 { background: rgba(168, 85, 247, 0.22); }

.card-visual-copy {
	bottom: 20rpx;
}

.card-visual-title {
	display: block;
	font-size: 42rpx;
	font-weight: 700;
	line-height: 1.12;
	color: #fff;
}

.card-visual-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 24rpx;
	line-height: 1.52;
	color: rgba(241, 245, 249, 0.86);
}

.card-meta {
	padding: 22rpx 20rpx 20rpx;
}

.meta-badges {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
}

.meta-badge {
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 600;
}

.meta-badge--mode {
	color: #ede9fe;
	background: rgba(91, 33, 182, 0.24);
}

.meta-badge--vip {
	color: #fff7ed;
	background: rgba(249, 115, 22, 0.24);
}

.meta-badge--blur {
	color: #fed7aa;
	background: rgba(249, 115, 22, 0.18);
	border: 1rpx solid rgba(251, 146, 60, 0.24);
}

.meta-badge--new {
	color: #ecfeff;
	background: rgba(13, 148, 136, 0.24);
}

.meta-desc {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	line-height: 1.65;
	color: rgba(226, 232, 240, 0.78);
	min-height: 120rpx;
}

.meta-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	margin-top: 18rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.meta-handle,
.meta-cta {
	font-size: 22rpx;
}

.meta-handle {
	color: rgba(226, 232, 240, 0.62);
}

.meta-cta {
	color: #f9a8d4;
	font-weight: 700;
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
</style>
