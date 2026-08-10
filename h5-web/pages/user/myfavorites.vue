<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="tx('收藏角色', '我的收藏')" mode="dark" @back="goBack" />

		<view v-if="showToolbar" class="toolbar">
			<view class="sort-group">
				<text class="sort-item" :class="{ 'sort-item--on': sortBy === 'favorite' }" @tap="changeSort('favorite')">
					{{ tx('按收藏时间', '按收藏时间') }}
				</text>
				<text class="sort-item" :class="{ 'sort-item--on': sortBy === 'recent_chat' }" @tap="changeSort('recent_chat')">
					{{ tx('按最近聊天', '按最近聊天') }}
				</text>
			</view>
			<text class="batch-switch" @tap="toggleBatchMode">
				{{ batchMode ? tx('完成', '完成') : tx('批量管理', '批量管理') }}
			</text>
		</view>

		<view v-if="batchMode && list.length" class="batch-row">
			<text class="batch-link" @tap="toggleSelectAll">
				{{ allSelected ? tx('取消全选', '取消全选') : tx('全选', '全选') }}
			</text>
			<view class="batch-btn" :class="{ 'batch-btn--disabled': !selectedCount || batchBusy }" @tap="confirmBatchUnfavorite">
				{{ tx('批量取消', '批量取消') }}({{ selectedCount }})
			</view>
		</view>

		<view v-if="loading && !list.length" class="state-box">
			<text class="state-txt">{{ tx('详情加载中', '加载中...') }}</text>
		</view>
		<view v-else-if="errorMsg && !list.length" class="state-box">
			<text class="state-txt state-txt--err">{{ errorMsg }}</text>
			<view class="state-btn" @tap="loadFavorites({ firstLoad: true })">{{ tx('点击重试', '点击重试') }}</view>
		</view>
		<view v-else-if="!list.length" class="state-box">
			<text class="state-txt">{{ tx('暂无收藏角色', '暂无收藏角色') }}</text>
			<view class="state-btn" @tap="goDiscover">{{ tx('去发现角色', '去发现角色') }}</view>
		</view>

		<scroll-view
			v-else
			scroll-y
			class="scroll"
			refresher-enabled
			:refresher-triggered="refreshing"
			:lower-threshold="220"
			@refresherrefresh="onScrollRefresh"
			@scrolltolower="loadMoreFavorites"
		>
			<view v-for="item in visibleList" :key="item.id" class="card" @tap="onCardTap(item)">
				<view v-if="batchMode" class="check-wrap" @tap.stop="toggleSelect(item)">
					<view class="check-dot" :class="{ 'check-dot--on': isSelected(item) }">
						<text v-if="isSelected(item)" class="check-mark">✓</text>
					</view>
				</view>
				<view class="cover-wrap">
					<image class="cover" :class="{ 'cover--blur': isPreviewBlurActive(item) }" :src="coverUrl(item)" mode="aspectFill" lazy-load />
					<view
						v-if="isPreviewBlurActive(item)"
						class="cover-blur-surface"
						:style="blurSurfaceStyle(coverUrl(item), 'cover')"
					></view>
					<view v-if="isPreviewBlurActive(item)" class="cover-blur-layer">
						<text class="cover-blur-pill">{{ previewBlurBadgeText(item) }}</text>
					</view>
				</view>
				<view class="meta">
					<text class="name">{{ item.nickname || '-' }}</text>
					<text class="desc">{{ cardPreview(item) || '-' }}</text>
					<view class="row">
						<text class="likes">❤ {{ formatLikes(item.like_count) }}</text>
						<view v-if="!batchMode" class="btn" :class="{ 'btn--disabled': batchBusy }" @tap.stop="unfavorite(item)">
							{{ tx('取消收藏', '取消收藏') }}
						</view>
					</view>
				</view>
			</view>
			<view v-if="hasMoreFavorites" class="list-more" @tap="loadMoreFavorites">
				<text class="list-more-text">{{ listProgressText(visibleList.length, list.length) }}</text>
			</view>
			<view class="pad"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernCharacterAccess = require('@/common/tavernCharacterAccess.js');
const tavernListPerf = require('@/common/tavernListPerf.js');
const FAVORITES_INITIAL_VISIBLE = 10;
const FAVORITES_BATCH_VISIBLE = 10;

export default {
	components: { TavernNavBar },
	data() {
		return {
			loading: false,
			errorMsg: '',
			list: [],
			sortBy: 'favorite',
			batchMode: false,
			selectedIds: [],
			batchBusy: false,
			refreshing: false,
			visibleCount: 0
		};
	},
	computed: {
		showToolbar() {
			return !this.loading && !this.errorMsg && this.list.length > 0;
		},
		selectedCount() {
			return this.selectedIds.length;
		},
		visibleList() {
			return tavernListPerf.sliceVisibleList(this.list, this.visibleCount, FAVORITES_INITIAL_VISIBLE);
		},
		hasMoreFavorites() {
			return tavernListPerf.hasMoreItems(this.list, this.visibleCount, FAVORITES_INITIAL_VISIBLE);
		},
		selectedVisibleCount() {
			return this.visibleList.filter((item) => this.isSelected(item)).length;
		},
		allSelected() {
			return this.visibleList.length > 0 && this.selectedVisibleCount === this.visibleList.length;
		}
	},
	onShow() {
		this.loadFavorites({ firstLoad: true });
	},
	onPullDownRefresh() {
		this.onRefresh();
	},
	methods: {
		syncVisibleCount() {
			this.visibleCount = tavernListPerf.syncVisibleCount(
				this.visibleCount,
				this.list.length,
				FAVORITES_INITIAL_VISIBLE
			);
		},
		loadMoreFavorites() {
			if (this.loading || this.refreshing) return;
			this.visibleCount = tavernListPerf.expandVisibleCount(
				this.visibleCount,
				this.list.length,
				FAVORITES_BATCH_VISIBLE,
				FAVORITES_INITIAL_VISIBLE
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
		tx(key, fallback) {
			const dict = (this.allText && this.allText['酒馆页']) || {};
			const v = key ? dict[key] : '';
			if (v != null && String(v).trim() !== '') return v;
			return fallback || '';
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		goDiscover() {
			uni.switchTab({ url: '/pages/index/index' });
		},
		coverUrl(c) {
			const tavernApi = require('@/common/tavernApi.js');
			const u = (c && (c.cover_thumb || c.avatar_thumb || c.cover || c.avatar)) || '';
			if (!u || String(u).trim() === '') return '/static/logo.png';
			return tavernApi.resolveJgAssetUrl(u) || '/static/logo.png';
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
		formatLikes(value) {
			const n = Number(value);
			if (!isFinite(n) || n < 0) return '0';
			return String(Math.floor(n));
		},
		isPreviewBlurActive(card) {
			return tavernCharacterAccess.isPreviewBlurActive(card);
		},
		previewBlurBadgeText(card) {
			return tavernCharacterAccess.previewBlurBadgeText(card);
		},
		cardPreview(item) {
			const raw = item ? item.bio || item.persona || item.scenario || item.tagline || '' : '';
			return this.truncatePreviewText(raw, 54);
		},
		normalizePreviewText(value) {
			return value == null ? '' : String(value).replace(/\s+/g, ' ').trim();
		},
		truncatePreviewText(value, maxLen) {
			const text = this.normalizePreviewText(value);
			const safeMax = Math.max(Number(maxLen) || 0, 12);
			if (!text) return '';
			if (text.length <= safeMax) return text;
			return text.slice(0, safeMax).replace(/\s+$/g, '') + '...';
		},
		onRefresh() {
			if (this.refreshing) return;
			this.refreshing = true;
			this.loadFavorites({ pull: true, firstLoad: false });
		},
		onScrollRefresh() {
			this.onRefresh();
		},
		loadFavorites(options) {
			const opts = options || {};
			const firstLoad = !!opts.firstLoad;
			const isPull = !!opts.pull;
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				this.loading = false;
				this.refreshing = false;
				this.list = [];
				this.syncVisibleCount();
				this.selectedIds = [];
				this.errorMsg = this.tx('后端接口未开启', '后端接口未开启');
				try {
					uni.stopPullDownRefresh();
				} catch (e) {}
				return Promise.resolve([]);
			}
			if (firstLoad) {
				this.visibleCount = 0;
				this.loading = true;
			}
			if (!isPull) {
				this.errorMsg = '';
			}
			return tavernApi
				.fetchMeFavorites(tavernApi.getClientUid(), 200, this.sortBy)
				.then((rows) => {
					this.list = Array.isArray(rows) ? rows : [];
					this.syncVisibleCount();
					this.keepSelectionInSync();
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					const msg = tavernErrors.getTavernErrorMessage(e, this.tx('加载失败', '加载失败'));
					if (this.list.length > 0) {
						uni.showToast({ title: msg, icon: 'none', duration: 2600 });
					} else {
						this.list = [];
						this.syncVisibleCount();
						this.selectedIds = [];
						this.errorMsg = msg;
					}
				})
				.finally(() => {
					this.loading = false;
					if (isPull) {
						this.refreshing = false;
						try {
							uni.stopPullDownRefresh();
						} catch (e) {}
					}
				});
		},
		changeSort(nextSort) {
			if (this.sortBy === nextSort || this.loading || this.refreshing) return;
			this.sortBy = nextSort;
			this.batchMode = false;
			this.selectedIds = [];
			this.loadFavorites({ firstLoad: true });
		},
		toggleBatchMode() {
			if (!this.list.length || this.batchBusy) return;
			this.batchMode = !this.batchMode;
			if (!this.batchMode) {
				this.selectedIds = [];
			}
		},
		itemKey(item) {
			if (!item || item.id == null || item.id === '') return '';
			return String(item.id);
		},
		isSelected(item) {
			const key = this.itemKey(item);
			return !!key && this.selectedIds.indexOf(key) >= 0;
		},
		toggleSelect(item) {
			const key = this.itemKey(item);
			if (!key || !this.batchMode) return;
			const idx = this.selectedIds.indexOf(key);
			if (idx >= 0) {
				this.selectedIds = this.selectedIds.filter((id) => id !== key);
			} else {
				this.selectedIds = this.selectedIds.concat(key);
			}
		},
		toggleSelectAll() {
			if (!this.batchMode || !this.visibleList.length) return;
			if (this.allSelected) {
				const visibleKeys = this.visibleList.map((it) => this.itemKey(it)).filter((id) => !!id);
				this.selectedIds = this.selectedIds.filter((id) => visibleKeys.indexOf(id) < 0);
				return;
			}
			const current = {};
			this.selectedIds.forEach((id) => {
				current[id] = true;
			});
			this.visibleList.forEach((it) => {
				const id = this.itemKey(it);
				if (id) {
					current[id] = true;
				}
			});
			this.selectedIds = Object.keys(current);
		},
		keepSelectionInSync() {
			if (!this.selectedIds.length) {
				if (!this.list.length) {
					this.batchMode = false;
				}
				return;
			}
			const exists = {};
			this.list.forEach((item) => {
				const key = this.itemKey(item);
				if (key) exists[key] = true;
			});
			this.selectedIds = this.selectedIds.filter((id) => exists[id]);
			if (!this.list.length) {
				this.batchMode = false;
			}
		},
		onCardTap(item) {
			if (this.batchMode) {
				this.toggleSelect(item);
				return;
			}
			this.openDetail(item);
		},
		openDetail(item) {
			if (!item || item.id == null || item.id === '') return;
			uni.navigateTo({ url: '/pages/tavern/charDetail?id=' + item.id });
		},
		toNumericIds(rawList) {
			const out = [];
			const seen = {};
			(rawList || []).forEach((x) => {
				const n = Number(x);
				if (!isFinite(n) || n <= 0) return;
				const key = String(n);
				if (seen[key]) return;
				seen[key] = true;
				out.push(n);
			});
			return out;
		},
		removeIdsFromList(ids) {
			if (!ids || !ids.length) return;
			const hit = {};
			ids.forEach((n) => {
				hit[String(Number(n))] = true;
			});
			this.list = this.list.filter((item) => !hit[this.itemKey(item)]);
			this.syncVisibleCount();
			this.selectedIds = this.selectedIds.filter((id) => !hit[id]);
			if (!this.list.length) {
				this.batchMode = false;
			}
		},
		requestUnfavorite(ids, doneMsg) {
			if (this.batchBusy) return;
			const normalized = this.toNumericIds(ids);
			if (!normalized.length) return;
			const tavernApi = require('@/common/tavernApi.js');
			this.batchBusy = true;
			tavernApi
				.postMeFavoritesUnfavoriteBatch({
					clientUid: tavernApi.getClientUid(),
					characterIds: normalized
				})
				.then(() => {
					this.removeIdsFromList(normalized);
					uni.showToast({ title: doneMsg || this.tx('已取消收藏', '已取消收藏'), icon: 'none' });
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.tx('操作失败', '操作失败')),
						icon: 'none',
						duration: 2600
					});
				})
				.finally(() => {
					this.batchBusy = false;
				});
		},
		unfavorite(item) {
			if (!item || item.id == null || item.id === '') return;
			this.requestUnfavorite([item.id], this.tx('已取消收藏', '已取消收藏'));
		},
		confirmBatchUnfavorite() {
			if (!this.selectedCount || this.batchBusy) return;
			const ids = this.toNumericIds(this.selectedIds);
			if (!ids.length) return;
			uni.showModal({
				title: this.tx('批量取消收藏', '批量取消收藏'),
				content: this.tx('确认取消选中的收藏角色吗？', '确认取消选中的收藏角色吗？'),
				success: (res) => {
					if (res && res.confirm) {
						this.requestUnfavorite(ids, this.tx('已批量取消', '已批量取消'));
					}
				}
			});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	height: 100vh;
	display: flex;
	flex-direction: column;
	background: $tavern-page-bg;
}

.toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 16rpx 22rpx 8rpx;
	gap: 12rpx;
}

.sort-group {
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
}

.sort-item {
	padding: 10rpx 20rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: $tavern-muted-on-dark;
	background: rgba(148, 163, 184, 0.16);
	border: 1rpx solid rgba(148, 163, 184, 0.28);
}

.sort-item--on {
	color: #fff;
	background: rgba(99, 102, 241, 0.28);
	border-color: rgba(129, 140, 248, 0.55);
}

.batch-switch {
	font-size: 24rpx;
	color: #c4b5fd;
	padding: 8rpx 10rpx;
}

.batch-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 8rpx 24rpx 10rpx;
}

.batch-link {
	font-size: 24rpx;
	color: $tavern-muted-on-dark;
}

.batch-btn {
	padding: 10rpx 24rpx;
	border-radius: 999rpx;
	font-size: 24rpx;
	color: #fff;
	background: $tavern-accent-gradient;
}

.batch-btn--disabled {
	opacity: 0.45;
	pointer-events: none;
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

.state-btn {
	padding: 14rpx 36rpx;
	border-radius: 999rpx;
	font-size: 26rpx;
	color: #fff;
	background: $tavern-accent-gradient;
}

.scroll {
	flex: 1;
	height: 0;
	padding: 8rpx 20rpx 0;
	box-sizing: border-box;
}

.card {
	display: flex;
	align-items: stretch;
	gap: 18rpx;
	padding: 18rpx;
	margin-bottom: 16rpx;
	border-radius: $tavern-radius-lg;
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.check-wrap {
	display: flex;
	align-items: center;
	justify-content: center;
	padding-right: 4rpx;
}

.check-dot {
	width: 36rpx;
	height: 36rpx;
	border-radius: 50%;
	border: 2rpx solid rgba(148, 163, 184, 0.7);
	display: flex;
	align-items: center;
	justify-content: center;
}

.check-dot--on {
	background: rgba(99, 102, 241, 0.95);
	border-color: rgba(129, 140, 248, 0.95);
}

.check-mark {
	font-size: 22rpx;
	color: #fff;
	line-height: 1;
}

.cover-wrap {
	position: relative;
	width: 150rpx;
	height: 180rpx;
	flex-shrink: 0;
	overflow: hidden;
	border-radius: 14rpx;
}

.cover {
	width: 150rpx;
	height: 180rpx;
	border-radius: 14rpx;
}

.cover--blur {
	filter: blur(12rpx) scale(1.04) brightness(0.74);
}

.cover-blur-surface {
	position: absolute;
	inset: -10rpx;
	z-index: 1;
	pointer-events: none;
	filter: blur(22rpx) saturate(0.9) brightness(0.66);
	transform: scale(1.12);
	background-repeat: no-repeat;
	background-position: center center;
	background-size: cover;
}

.cover-blur-layer {
	position: absolute;
	inset: 0;
	z-index: 2;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 16rpx;
	border-radius: 14rpx;
	background: linear-gradient(180deg, rgba(8, 10, 18, 0.16) 0%, rgba(8, 10, 18, 0.34) 100%);
	pointer-events: none;
}

.cover-blur-pill {
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 700;
	color: #fff7ed;
	background: rgba(15, 23, 42, 0.52);
	border: 1rpx solid rgba(253, 186, 116, 0.34);
}

.meta {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.name {
	font-size: 30rpx;
	font-weight: 700;
	color: $tavern-text-on-dark;
}

.desc {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: $tavern-muted-on-dark;
	line-height: 1.45;
	display: -webkit-box;
	-webkit-line-clamp: 3;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.row {
	margin-top: auto;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.likes {
	font-size: 24rpx;
	color: #c4b5fd;
}

.btn {
	padding: 10rpx 22rpx;
	border-radius: 999rpx;
	font-size: 24rpx;
	color: #fde2e7;
	background: rgba(244, 63, 94, 0.2);
	border: 1rpx solid rgba(251, 113, 133, 0.4);
}

.btn--disabled {
	opacity: 0.45;
	pointer-events: none;
}

.list-more {
	padding: 18rpx 0 8rpx;
	text-align: center;
}

.list-more-text {
	font-size: 22rpx;
	line-height: 1.6;
	color: rgba(196, 181, 253, 0.88);
}

.pad {
	height: calc(26rpx + env(safe-area-inset-bottom));
}
</style>
