<template>
	<view class="root" :class="localeFontClass">
		<image class="global-bg-image" src="/static/login.png" mode="aspectFill"></image>
		<scroll-view
			scroll-y
			class="page-scroll"
			:enable-back-to-top="true"
			:lower-threshold="220"
			@scrolltolower="onDiscoverScrollToLower"
		>
			<view class="disc-header" :style="{ paddingTop: statusBarH + 'px' }">
				<view class="brand-row">
					<view class="brand-main">
						<image class="brand-logo" src="/static/logo.png" mode="aspectFill" />
						<text class="brand-title">{{ discoverUi.promo }}</text>
					</view>
					<view class="brand-actions">
						<view class="icon-btn" @tap="goSystemMsg">
							<text class="notice-ico">✉</text>
							<view v-if="noticeUnread > 0" class="notice-badge">{{ noticeUnread > 99 ? '99+' : noticeUnread }}</view>
						</view>
						<view class="icon-btn" @tap="onMoreMenu">
							<text class="icon-more">⋯</text>
						</view>
					</view>
				</view>

				<view class="search-row" @tap="toastSearch">
					<image class="search-ico" src="/static/cha.png" mode="aspectFit" />
					<text class="search-ph" :class="{ 'search-ph--active': !!searchKeyword }">{{ searchDisplayText }}</text>
					<text
						v-if="searchKeyword"
						class="search-clear"
						@tap.stop="clearSearchKeyword"
					>{{ discoverUi.clear }}</text>
				</view>

				<scroll-view scroll-x class="feed-tabs" :show-scrollbar="false">
					<view class="feed-tabs-inner">
						<view
							v-for="(ft, i) in feedTabList"
							:key="'f' + i"
							class="feed-tab"
							:class="{ on: feedTab === i }"
							@tap="setFeedTab(i)"
						>
							<text class="feed-tab-txt">{{ ft.label }}</text>
							<view v-if="ft.dot" class="feed-dot" />
						</view>
					</view>
				</scroll-view>

				<view v-if="noticeBannerVisible" class="notice-banner" @tap="openNoticeBanner">
					<text class="notice-banner-mark">公告</text>
					<text class="notice-banner-title">{{ noticeBanner.title }}</text>
					<text class="notice-banner-close" @tap.stop="dismissNoticeBanner">×</text>
				</view>

				<view v-if="isJgDiscover" class="tag-filter-row">
					<scroll-view scroll-x class="tag-scroll" :show-scrollbar="false">
						<view class="tag-inner">
							<text
								class="tag-link"
								:class="{ on: !selectedTag && !selectedGameplay }"
								@tap="clearDiscoverFilters"
							>{{ discoverUi.all }}</text>
							<block v-for="(tg, ti) in discoverVisibleTags" :key="'tg' + ti">
								<text class="tag-sep">·</text>
								<text
									class="tag-link"
									:class="{ on: selectedTag === tagOptionValue(tg) }"
									@tap="toggleDiscoverTag(tagOptionValue(tg))"
								>{{ displayDiscoverTag(tg) }}</text>
							</block>
							<block v-if="hasDiscoverTagOverflow">
								<text class="tag-sep">·</text>
								<text class="tag-link tag-link--more" @tap="openDiscoverTagPopup">更多</text>
							</block>
							<block v-if="discoverPopupText.gameplayLabel">
								<text class="tag-sep">·</text>
								<text
									class="tag-link"
									:class="{ on: selectedGameplay === discoverPopupText.gameplayLabel }"
									@tap="toggleGameplayFilter(discoverPopupText.gameplayLabel)"
								>{{ discoverPopupText.gameplayLabel }}</text>
							</block>
							<block v-if="selectedTag || selectedGameplay">
								<text class="tag-sep">·</text>
								<text class="tag-link tag-link--clear" @tap="clearDiscoverFilters">{{ discoverUi.clear }}</text>
							</block>
						</view>
					</scroll-view>
				</view>
			</view>

			<view v-show="swiperCurrent === 0" class="page-box">
				<view v-if="isJgDiscover && illustrationEntryEnabled" class="illustration-entry-card" @tap="openIllustrationSite">
					<view class="illustration-entry-copy">
						<text class="illustration-entry-kicker">四叶插画分享</text>
						<text class="illustration-entry-title">去画廊看看灵感与壁纸</text>
						<text class="illustration-entry-desc">精选插画、角色参考和用户投稿集中展示</text>
					</view>
					<view class="illustration-entry-stack">
						<view class="illustration-entry-thumb thumb-a"></view>
						<view class="illustration-entry-thumb thumb-b"></view>
						<view class="illustration-entry-thumb thumb-c"></view>
					</view>
					<view class="illustration-entry-action">打开</view>
				</view>
				<view v-if="isJgDiscover && discoverLoading && !allChars.length" class="discover-skeleton">
					<view class="discover-skeleton-top">
						<view v-for="n in 3" :key="'discover_top_' + n" class="discover-skeleton-hero"></view>
					</view>
					<view class="discover-skeleton-grid">
						<view v-for="n in 4" :key="'discover_card_' + n" class="discover-skeleton-card">
							<view class="discover-skeleton-visual"></view>
							<view class="discover-skeleton-line discover-skeleton-line--lg"></view>
							<view class="discover-skeleton-line"></view>
							<view class="discover-skeleton-line discover-skeleton-line--sm"></view>
						</view>
					</view>
				</view>
				<view
					v-else-if="isJgDiscover && !allChars.length"
					class="discover-status discover-status--empty"
				>
					<text class="discover-status-txt">{{ discoverLoadError ? discoverLoadError : discoverUi.empty }}</text>
					<view
						v-if="discoverLoadError"
						class="discover-retry"
						@tap="tryLoadCharsFromBackend"
					>{{ discoverUi.retry }}</view>
				</view>
				<template v-else>
				<view v-if="showTopPick" class="card-top">
					<view class="card-top-hd">
						<text class="card-title">{{ discoverUi.discoverTitle }}</text>
						<view class="refresh" @tap.stop="refreshTopList">
							<image src="/static/rights1.png" mode="widthFix" class="refresh-ico"></image>
							<text>{{ discoverUi.refreshList }}</text>
						</view>
					</view>
					<view class="grid3">
						<view class="g3" v-for="(c, k) in topPick" :key="'t' + k" @tap="goDetail(c.id)">
						<view
							class="g3-inner"
							:class="[cardTierClass(c), { 'g3-inner--hover': hoverTopPickId === c.id }]"
								@mouseenter="setDiscoverHover('top', c.id)"
								@mouseleave="clearDiscoverHover('top')"
								@touchstart="setDiscoverHover('top', c.id)"
								@touchend="clearDiscoverHover('top')"
								@touchcancel="clearDiscoverHover('top')"
							>
								<view class="card-grade card-grade--compact">
									<text class="card-grade-label">{{ cardTierLabel(c) }}</text>
									<text class="card-grade-caption">{{ cardTierCaption(c) }}</text>
								</view>
								<view class="g3-media">
								<image
									class="g3-img"
									:class="{ 'g3-img--blur': isPreviewBlurActive(c) }"
									:src="charCoverUrl(c)"
									mode="aspectFill"
									lazy-load
								></image>
								<view
									v-if="isPreviewBlurActive(c)"
									class="preview-blur-surface preview-blur-surface--compact"
									:style="blurSurfaceStyle(charCoverUrl(c), 'cover')"
								></view>
								<view v-if="isPreviewBlurActive(c)" class="preview-blur-layer preview-blur-layer--compact">
									<view class="preview-blur-pill">{{ previewBlurBadgeText(c) }}</view>
								</view>
								<view class="card-shade card-shade--compact" aria-hidden="true"></view>
								<view class="g3-mask">
									<view class="g3-name">{{ c.nickname }}</view>
									<view class="g3-sub">{{ cardHeroCopy(c) }}</view>
									<view class="g3-tags" v-if="safeLabels(c, 2).length">
										<text
											v-for="(lb, li) in safeLabels(c, 2)"
											:key="li"
											class="mini-tag"
										>{{ lb.code }}</text>
									</view>
									<view class="g3-foot">
										<text class="g3-foot-handle">{{ displayHandle(c) }}</text>
										<view class="g3-foot-heat">
											<text class="heart sm">❤</text>
											<text>{{ formatLikes(c.like_count) }}</text>
										</view>
									</view>
								</view>
								</view>
							</view>
						</view>
					</view>
				</view>

				<view v-if="feedTab === 4" class="empty-feed empty-feed--block">
					<view class="group-preview-card">
						<view class="group-preview-badge">BETA</view>
						<text class="empty-feed-title">{{ discoverUi.groupTitle }}</text>
						<text class="empty-feed-sub">{{ discoverUi.groupDesc }}</text>
						<view class="group-preview-points">
							<view class="group-preview-point">{{ discoverUi.groupPoint1 }}</view>
							<view class="group-preview-point">{{ discoverUi.groupPoint2 }}</view>
							<view class="group-preview-point">{{ discoverUi.groupPoint3 }}</view>
						</view>
						<view class="group-preview-action" @tap="setFeedTab(0)">{{ discoverUi.groupAction }}</view>
					</view>
				</view>

				<view v-if="displayGridList.length" class="grid-section-head">
					<view class="grid-section-copy">
						<text class="grid-section-title">{{ discoverGridHeadline }}</text>
					</view>
				</view>
				<view v-if="displayGridList.length" class="grid2-wrap">
					<view class="grid2-item" v-for="c in displayGridList" :key="c.id" @tap="goDetail(c.id)">
						<view
							class="card-disc"
							:class="[cardTierClass(c), { 'card-disc--hover': hoverGridId === c.id }]"
							@mouseenter="setDiscoverHover('grid', c.id)"
							@mouseleave="clearDiscoverHover('grid')"
							@touchstart="setDiscoverHover('grid', c.id)"
							@touchend="clearDiscoverHover('grid')"
							@touchcancel="clearDiscoverHover('grid')"
						>
							<view class="card-grade">
								<text class="card-grade-label">{{ cardTierLabel(c) }}</text>
								<text class="card-grade-caption">{{ cardTierCaption(c) }}</text>
							</view>
							<view class="card-visual">
								<image
									class="card2-bg"
									:class="{ 'card2-bg--blur': isPreviewBlurActive(c) }"
									:src="charCoverUrl(c)"
									mode="aspectFill"
									lazy-load
								></image>
								<view
									v-if="isPreviewBlurActive(c)"
									class="preview-blur-surface"
									:style="blurSurfaceStyle(charCoverUrl(c), 'cover')"
								></view>
								<view v-if="isPreviewBlurActive(c)" class="preview-blur-layer">
									<view class="preview-blur-pill">{{ previewBlurBadgeText(c) }}</view>
									<text class="preview-blur-note">{{ previewBlurHintText(c) }}</text>
								</view>

								<view class="card-shade" aria-hidden="true"></view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ c.nickname }}</text>
									<text class="card-visual-desc">{{ cardPreview(c) }}</text>
									<view class="card-float-tags" v-if="safeLabels(c, 3).length">
										<text
											v-for="(lb, li) in safeLabels(c, 3)"
											:key="li"
											class="float-tag"
										>{{ lb.code }}</text>
									</view>
									<view class="card-inline-foot">
										<text class="card-inline-handle">{{ displayHandle(c) }}</text>
										<text
											v-if="c.unlocked === false"
											class="card-inline-unlock"
											@tap.stop="handleDiscoverCardAction(c)"
										>{{ discoverUi.openVip }}</text>
										<view v-else class="card-inline-heat">
											<text class="heart sm">❤</text>
											<text>{{ formatLikes(c.like_count) }}</text>
										</view>
									</view>
								</view>
							</view>
						</view>
					</view>
				</view>
				<view v-if="hasMoreDiscoverCards" class="list-more">
					<text class="list-more-text">{{ listProgressText(displayGridList.length, sortedDiscoverList.length, discoverServerHasMore, discoverLoading) }}</text>
				</view>
				<view v-else-if="feedTab !== 4" class="empty-feed">
					<text>{{ discoverUi.empty }}</text>
				</view>
				</template>
			</view>

			<view v-show="swiperCurrent === 1" class="page-box">
				<view v-if="unlockedDisplayList.length" class="grid-section-head grid-section-head--compact">
					<view class="grid-section-copy">
						<text class="grid-section-title">{{ discoverUi.unlockedTitle }}</text>
					</view>
				</view>
				<view class="grid2-wrap">
					<view class="grid2-item" v-for="c in unlockedDisplayList" :key="'u' + c.id" @tap="goDetail(c.id)">
						<view
							class="card-disc"
							:class="[cardTierClass(c), { 'card-disc--hover': hoverGridId === c.id }]"
							@mouseenter="setDiscoverHover('grid', c.id)"
							@mouseleave="clearDiscoverHover('grid')"
							@touchstart="setDiscoverHover('grid', c.id)"
							@touchend="clearDiscoverHover('grid')"
							@touchcancel="clearDiscoverHover('grid')"
						>
							<view class="card-grade">
								<text class="card-grade-label">{{ cardTierLabel(c) }}</text>
								<text class="card-grade-caption">{{ cardTierCaption(c) }}</text>
							</view>
							<view class="card-visual">
								<image
									class="card2-bg"
									:class="{ 'card2-bg--blur': isPreviewBlurActive(c) }"
									:src="charCoverUrl(c)"
									mode="aspectFill"
									lazy-load
								></image>
								<view
									v-if="isPreviewBlurActive(c)"
									class="preview-blur-surface"
									:style="blurSurfaceStyle(charCoverUrl(c), 'cover')"
								></view>
								<view v-if="isPreviewBlurActive(c)" class="preview-blur-layer">
									<view class="preview-blur-pill">{{ previewBlurBadgeText(c) }}</view>
									<text class="preview-blur-note">{{ previewBlurHintText(c) }}</text>
								</view>
								<view class="card-shade" aria-hidden="true"></view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ c.nickname }}</text>
									<text class="card-visual-desc">{{ cardPreview(c) }}</text>
									<view class="card-float-tags" v-if="safeLabels(c, 3).length">
										<text
											v-for="(lb, li) in safeLabels(c, 3)"
											:key="li"
											class="float-tag"
										>{{ lb.code }}</text>
									</view>
									<view class="card-inline-foot">
										<text class="card-inline-handle">{{ displayHandle(c) }}</text>
										<text
											v-if="c.unlocked === false"
											class="card-inline-unlock"
											@tap.stop="handleDiscoverCardAction(c)"
										>{{ discoverUi.openVip }}</text>
										<view v-else class="card-inline-heat">
											<text class="heart sm">❤</text>
											<text>{{ formatLikes(c.like_count) }}</text>
										</view>
									</view>
								</view>
							</view>
						</view>
					</view>
				</view>
				<view v-if="hasMoreUnlockedCards" class="list-more">
					<text class="list-more-text">{{ listProgressText(unlockedDisplayList.length, unlockedSourceList.length) }}</text>
				</view>
				<view v-if="!unlockedDisplayList.length" class="empty-tab">{{ discoverUi.empty }}</view>
			</view>

			<u-gap height="200"></u-gap>
		</scroll-view>
		<u-popup v-model="discoverTagPopupShow" mode="bottom" border-radius="28" closeable>
			<view class="tag-popup">
				<view class="tag-popup-head">
					<view class="tag-popup-head-main">
						<text class="tag-popup-title">{{ discoverPopupText.title }}</text>
						<text class="tag-popup-sub">{{ discoverPopupText.subtitle }}</text>
					</view>
					<text
						v-if="selectedTag || selectedGameplay"
						class="tag-popup-reset"
						@tap="clearDiscoverFiltersAndClose"
					>{{ discoverUi.clearFilter }}</text>
				</view>
				<scroll-view scroll-y class="tag-popup-scroll">
					<view v-if="selectedTag || selectedGameplay" class="tag-popup-group">
						<view class="tag-popup-group-head">
							<text class="tag-popup-group-title">{{ discoverUi.currentFilter }}</text>
						</view>
						<view class="tag-popup-grid">
							<view v-if="selectedTag" class="tag-chip on" @tap="pickDiscoverTagFromPopup(selectedTag)">{{ selectedTag }}</view>
							<view
								v-if="selectedGameplay"
								class="tag-chip tag-chip--ghost on"
								@tap="toggleGameplayFromPopup(selectedGameplay)"
							>{{ selectedGameplay }}</view>
						</view>
					</view>
					<view v-if="discoverTagHint" class="tag-popup-notice">
						{{ discoverTagHint }}
					</view>
					<view class="tag-popup-group">
						<text class="tag-popup-group-title">{{ discoverPopupText.quickTitle }}</text>
						<view class="tag-popup-grid">
							<view
								class="tag-chip"
								:class="{ on: !selectedTag && !selectedGameplay }"
								@tap="clearDiscoverFiltersAndClose"
							>{{ discoverPopupText.allLabel }}</view>
							<view
								class="tag-chip tag-chip--ghost"
								:class="{ on: selectedGameplay === discoverPopupText.gameplayLabel }"
								@tap="toggleGameplayFromPopup(discoverPopupText.gameplayLabel)"
							>{{ discoverPopupText.gameplayLabel }}</view>
						</view>
					</view>
					<view v-if="discoverQuickPopupTags.length" class="tag-popup-group">
						<view class="tag-popup-group-head">
							<text class="tag-popup-group-title">{{ discoverUi.recommendGroup }}</text>
						</view>
						<view class="tag-popup-grid">
							<view
								v-for="(tg, ti) in discoverQuickPopupTags"
								:key="'tag_quick_live_' + ti + '_' + tagOptionValue(tg)"
								class="tag-chip"
								:class="{ on: selectedTag === tagOptionValue(tg) }"
								@tap="pickDiscoverTagFromPopup(tg)"
							>{{ displayDiscoverTag(tg) }}</view>
						</view>
					</view>
					<view
						v-for="(group, gi) in discoverTagGroups"
						:key="'tag_group_live_' + gi + '_' + group.name"
						class="tag-popup-group"
					>
						<view class="tag-popup-group-head">
							<text class="tag-popup-group-title">{{ group.name }}</text>
						</view>
						<view class="tag-popup-grid">
							<view
								v-for="(tg, ti) in group.items"
								:key="'tag_item_live_' + gi + '_' + ti + '_' + tagOptionValue(tg)"
								class="tag-chip"
								:class="{ on: selectedTag === tagOptionValue(tg) }"
								@tap="pickDiscoverTagFromPopup(tg)"
							>{{ displayDiscoverTag(tg) }}</view>
						</view>
					</view>
					<view v-if="!discoverQuickPopupTags.length && !discoverTagGroups.length" class="tag-popup-empty">
						{{ discoverUi.tagEmpty }}
					</view>
				</scroll-view>
			</view>
		</u-popup>
		<view v-if="noticePopupVisible" class="notice-popup-mask">
			<view class="notice-popup-card">
				<view class="notice-popup-top">
					<view class="notice-popup-icon">!</view>
					<view class="notice-popup-heading">
						<text class="notice-popup-mark">重要公告</text>
						<text class="notice-popup-title">{{ noticePopup.title }}</text>
					</view>
				</view>
				<scroll-view scroll-y class="notice-popup-scroll" :show-scrollbar="false">
					<text class="notice-popup-content">{{ noticePopup.content }}</text>
				</scroll-view>
				<view class="notice-popup-actions">
					<view class="notice-popup-btn notice-popup-btn--ghost" @tap="openNoticePopupDetail">查看详情</view>
					<view class="notice-popup-btn" @tap="confirmNoticePopup">我知道了</view>
				</view>
			</view>
		</view>
		<view v-if="discoverMenuShow" class="discover-menu-overlay" @tap="discoverMenuShow = false">
			<view class="discover-menu-layer" :style="{ paddingTop: statusBarH + 92 + 'px' }">
				<view class="discover-menu-card" @tap.stop>
					<view class="discover-menu-head">
						<text class="discover-menu-title">{{ discoverUi.menuMoreTitle || '更多' }}</text>
						<text class="discover-menu-close" @tap="discoverMenuShow = false">×</text>
					</view>
					<view class="discover-menu-item" @tap="handleDiscoverMenu('notice')">
						<view class="discover-menu-item-main">
							<view class="discover-menu-item-title-row">
								<text class="discover-menu-item-title">{{ discoverUi.menuNoticeTitle }}</text>
								<view v-if="noticeUnread > 0" class="discover-menu-unread">{{ noticeUnread > 99 ? '99+' : noticeUnread }}</view>
							</view>
							<text class="discover-menu-item-desc">{{ discoverUi.menuNoticeDesc }}</text>
						</view>
						<text class="discover-menu-item-arrow">›</text>
					</view>
					<view class="discover-menu-item" @tap="handleDiscoverMenu('favorites')">
						<view class="discover-menu-item-main">
							<text class="discover-menu-item-title">{{ discoverUi.menuFavoritesTitle }}</text>
							<text class="discover-menu-item-desc">{{ discoverUi.menuFavoritesDesc }}</text>
						</view>
						<text class="discover-menu-item-arrow">›</text>
					</view>
				</view>
			</view>
		</view>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="104" />
		<!-- #endif -->
	</view>
</template>

<script>
	import { applyTavernTabBarLabels, syncTavernTabBar } from '@/common/tavernTabBar.js';
	const tavernInboxBadge = require('@/common/tavernInboxBadge.js');
	const { getTavernUiText } = require('@/common/tavernUiI18n.js');
	const tavernNoticeState = require('@/common/tavernNoticeState.js');
	const tavernCharacterAccess = require('@/common/tavernCharacterAccess.js');
	const tavernListPerf = require('@/common/tavernListPerf.js');

	const INTERACTION_PATCH_KEY = 'jg_character_interaction_patch';
	const DISCOVER_TAG_CATEGORY_ORDER = ['推荐', '题材', '场景', '关系', '玩法', '风格', '背景', '来源', '成人向', '当前标签'];
	const DISCOVER_FETCH_LIMIT = 500;
	const DISCOVER_INITIAL_VISIBLE = 8;
	const DISCOVER_BATCH_VISIBLE = 8;
	const NOTICE_BANNER_DISMISSED_KEY = 'jg_notice_banner_dismissed_';
	const NOTICE_POPUP_ACK_KEY = 'jg_notice_popup_ack_';

	function discoverTagCategoryRank(name) {
		const label = name == null ? '' : String(name).trim();
		const index = DISCOVER_TAG_CATEGORY_ORDER.indexOf(label);
		return index >= 0 ? index : DISCOVER_TAG_CATEGORY_ORDER.length;
	}

	export default {
		data() {
			const runtimeFeatureConfig = require('@/common/tavernApi.js').getRuntimeFeatureConfig();
			return {
				current: 0,
				swiperCurrent: 0,
				allChars: [],
				topPick: [],
				shuffleKey: 0,
				statusBarH: 20,
				feedTab: 0,
				hoverTopPickId: '',
				hoverGridId: '',
				searchKeyword: '',
				discoverMenuShow: false,
				discoverTagOptions: [],
				discoverTagLoading: false,
				discoverTagPopupShow: false,
				discoverTagHint: '',
				selectedTag: '',
				selectedGameplay: '',
				discoverLoading: false,
				discoverLoadError: '',
				noticeUnread: 0,
				noticeUnreadIdentitySignature: '',
				noticeUnreadRequestVersion: 0,
				discoverRequestSeq: 0,
				discoverFetchKey: '',
				discoverHasLoaded: false,
				discoverViewerSignature: '',
				discoverVisibleCount: 0,
				unlockedVisibleCount: 0,
				discoverServerOffset: 0,
				discoverServerHasMore: false,
				noticeBanner: null,
				noticePopup: null,
				noticePopupVisible: false,
				noticeExposureLoading: false,
				illustrationEntryEnabled: runtimeFeatureConfig.illustrationEntryEnabled !== false
			};
		},
		computed: {
			discoverUi() {
				return getTavernUiText('discover');
			},
			t() {
				return (this.allText && this.allText['酒馆页']) || {};
			},
			isJgDiscover() {
				try {
					return require('@/common/tavernApi.js').jgEnabled();
				} catch (e) {
					return false;
				}
			},
			feedTabList() {
				return [
					{ label: this.discoverUi.tabFeatured, dot: false },
					{ label: this.discoverUi.tabMonth, dot: false },
					{ label: this.discoverUi.tabHot, dot: false },
					{ label: this.discoverUi.tabNew, dot: true },
					{ label: this.discoverUi.tabGroup, dot: false }
				];
			},

			showTopPick() {
				return this.swiperCurrent === 0 && this.feedTab === 0;
			},

			baseList() {
				return this.allChars.slice();
			},
			sortedDiscoverList() {
				if (this.feedTab === 4) return [];
				let list = this.baseList.slice();
				if (this.feedTab === 1) {
					list.sort((a, b) => (b.like_count || 0) - (a.like_count || 0));
				} else if (this.feedTab === 2) {
					list.sort((a, b) => a.id - b.id);
				} else if (this.feedTab === 3) {
					list.sort(this.compareCharactersRecentFirst);
				} else {
					list.sort(this.compareCharactersRecentFirst);
				}
				return list;
			},
			displayGridList() {
				return tavernListPerf.sliceVisibleList(this.sortedDiscoverList, this.discoverVisibleCount, DISCOVER_INITIAL_VISIBLE);
			},
			hasMoreDiscoverCards() {
				return tavernListPerf.hasMoreItems(this.sortedDiscoverList, this.discoverVisibleCount, DISCOVER_INITIAL_VISIBLE);
			},
			unlockedSourceList() {
				return this.allChars.filter((c) => c.unlocked);
			},
			unlockedDisplayList() {
				return tavernListPerf.sliceVisibleList(this.unlockedSourceList, this.unlockedVisibleCount, DISCOVER_INITIAL_VISIBLE);
			},
			hasMoreUnlockedCards() {
				return tavernListPerf.hasMoreItems(this.unlockedSourceList, this.unlockedVisibleCount, DISCOVER_INITIAL_VISIBLE);
			},
			discoverAllTags() {
				const base = Array.isArray(this.discoverTagOptions) ? this.discoverTagOptions.slice() : [];
				if (this.selectedTag && !base.some((item) => this.tagOptionValue(item) === this.selectedTag)) {
					base.unshift({
						code: this.selectedTag,
						name: this.selectedTag,
						category: '当前标签',
						color: '',
						recommended: false
					});
				}
				return base;
			},
			discoverOrderedTags() {
				const list = this.discoverAllTags.slice();
				const preferred = list.filter((item) => !!(item && item.recommended));
				const rest = list.filter((item) => !(item && item.recommended));
				return preferred.concat(rest);
			},
			discoverVisibleTags() {
				const list = this.discoverOrderedTags;
				const limit = 6;
				if (list.length <= limit) {
					return list;
				}
				if (!this.selectedTag) {
					return list.slice(0, limit);
				}
				const selected = list.find((item) => this.tagOptionValue(item) === this.selectedTag);
				if (!selected) {
					return list.slice(0, limit);
				}
				const rest = list.filter((item) => this.tagOptionValue(item) !== this.selectedTag);
				return [selected].concat(rest.slice(0, limit - 1));
			},
			hasDiscoverTagOverflow() {
				return this.discoverOrderedTags.length > this.discoverVisibleTags.length;
			},
			discoverHiddenTagCount() {
				return Math.max(0, this.discoverOrderedTags.length - this.discoverVisibleTags.length);
			},
			discoverQuickPopupTags() {
				return this.discoverOrderedTags.filter((item) => !!(item && item.recommended)).slice(0, 8);
			},
			discoverActiveFilterCount() {
				let count = 0;
				if (this.selectedTag) count += 1;
				if (this.selectedGameplay) count += 1;
				return count;
			},
			discoverGridHeadline() {
				if (this.selectedTag || this.selectedGameplay) {
					return this.discoverUi.filteredTitle;
				}
				return this.swiperCurrent === 1 ? this.discoverUi.unlockedTitle : this.discoverUi.allTitle;
			},
			discoverGridSubtitle() {
				const chips = [];
				if (this.selectedTag) chips.push(this.selectedTag);
				if (this.selectedGameplay) chips.push(this.selectedGameplay);
				return chips.join(' · ');
			},
			unlockedGridSubtitle() {
				const chips = [];
				if (this.selectedTag) chips.push(this.selectedTag);
				if (this.selectedGameplay) chips.push(this.selectedGameplay);
				return chips.join(' · ');
			},
			searchDisplayText() {
				return this.searchKeyword ? this.searchKeyword : this.discoverUi.searchPlaceholder;
			},
			discoverTagGroups() {
				const quickKeys = {};
				this.discoverQuickPopupTags.forEach((item) => {
					const value = this.tagOptionValue(item);
					if (value) {
						quickKeys[value] = true;
					}
				});
				const buckets = {};
				const ordered = [];
				this.discoverOrderedTags.forEach((item) => {
					const value = this.tagOptionValue(item);
					if (value && quickKeys[value]) {
						return;
					}
					const groupName = (item && item.category) || this.discoverUi.recommendGroup;
					if (!buckets[groupName]) {
						buckets[groupName] = {
							name: groupName,
							items: []
						};
						ordered.push(buckets[groupName]);
					}
					buckets[groupName].items.push(item);
				});
				return ordered.sort((a, b) => {
					const delta = discoverTagCategoryRank(a.name) - discoverTagCategoryRank(b.name);
					if (delta !== 0) return delta;
					return String(a.name || '').localeCompare(String(b.name || ''));
				});
			},
			discoverPopupText() {
				return {
					title: this.discoverUi.popupTitle,
					subtitle: this.discoverUi.popupSubtitle,
					quickTitle: this.discoverUi.popupQuickTitle,
					allLabel: this.discoverUi.popupAll,
					gameplayLabel: this.discoverUi.popupGameplay
				};
			},
			noticeBannerVisible() {
				return !!(this.noticeBanner && this.noticeBanner.id && !this.noticePopupVisible);
			}
		},
		watch: {
			noticeUnread() {
				this.loadNoticeExposure();
			}
		},
		onLoad() {
			const tavernApi = require('@/common/tavernApi.js');
			try {
				const sys = uni.getSystemInfoSync();
				this.statusBarH = sys.statusBarHeight || 20;
			} catch (e) {}
			this.discoverViewerSignature = tavernApi.getViewerStateSignature();
			this.noticeUnreadIdentitySignature = tavernApi.getViewerIdentitySignature();
			this.applyDiscoverFilterFromStorage();
			this.allChars = [];
			this.syncDiscoverVisibleCounts();
			this.discoverLoading = true;
			this.discoverLoadError = '';
			this.pickTop();
			this.tryLoadDiscoverTags();
			this.tryLoadCharsFromBackend({ force: true });
			this.syncIllustrationEntryVisibility(false);
		},
		onShow() {
			applyTavernTabBarLabels(this.allText, this);
			syncTavernTabBar(this, 'pages/index/index', this.allText);
			this.refreshNoticeUnread().then(() => {
				this.loadNoticeExposure();
			});
			this.applyLatestInteractionPatch();
			this.applyDiscoverFilterFromStorage();
			this.syncIllustrationEntryVisibility(true);
			if (this.isJgDiscover) {
				const tavernApi = require('@/common/tavernApi.js');
				const currentViewerSignature = tavernApi.getViewerStateSignature();
				const shouldRefreshDiscover =
					tavernApi.consumeCharacterAccessRefreshNeeded() ||
					currentViewerSignature !== this.discoverViewerSignature;
				if (shouldRefreshDiscover) {
					this.discoverViewerSignature = currentViewerSignature;
					this.tryLoadCharsFromBackend({ force: true });
				}
			}
			let clearDiscoverSearch = '';
			try {
				clearDiscoverSearch = uni.getStorageSync('tavern_discover_clear_search');
				if (clearDiscoverSearch) {
					uni.removeStorageSync('tavern_discover_clear_search');
				}
			} catch (e) {}
			if (clearDiscoverSearch && this.searchKeyword) {
				this.searchKeyword = '';
				if (this.isJgDiscover) {
					this.tryLoadCharsFromBackend({ force: true });
				}
			}
			if (!this.discoverTagOptions.length && this.isJgDiscover) {
				this.tryLoadDiscoverTags();
			}
			const token = this.util.getStoredToken();
			if (token) {
				this.util
					.request('user/user_info', { token })
					.then((res) => {
						if (res && res.need_edit === 0) {
							uni.reLaunch({ url: '/pages/perfect/perfect' });
						}
					})
					.catch(() => {});
			}
		},
		methods: {
			syncIllustrationEntryVisibility(forceRefresh) {
				const tavernApi = require('@/common/tavernApi.js');
				this.illustrationEntryEnabled = tavernApi.isIllustrationEntryEnabled();
				return tavernApi.fetchAppRuntimeConfig(forceRefresh === true).then((config) => {
					this.illustrationEntryEnabled = !(config && config.illustrationEntryEnabled === false);
					return this.illustrationEntryEnabled;
				});
			},
			syncDiscoverVisibleCounts() {
				this.discoverVisibleCount = tavernListPerf.syncVisibleCount(
					this.discoverVisibleCount,
					this.sortedDiscoverList.length,
					DISCOVER_INITIAL_VISIBLE
				);
				this.unlockedVisibleCount = tavernListPerf.syncVisibleCount(
					this.unlockedVisibleCount,
					this.unlockedSourceList.length,
					DISCOVER_INITIAL_VISIBLE
				);
			},
			loadMoreDiscoverCards() {
				if (this.swiperCurrent === 1) {
					this.unlockedVisibleCount = tavernListPerf.expandVisibleCount(
						this.unlockedVisibleCount,
						this.unlockedSourceList.length,
						DISCOVER_BATCH_VISIBLE,
						DISCOVER_INITIAL_VISIBLE
					);
					return;
				}
				this.discoverVisibleCount = tavernListPerf.expandVisibleCount(
					this.discoverVisibleCount,
					this.sortedDiscoverList.length,
					DISCOVER_BATCH_VISIBLE,
					DISCOVER_INITIAL_VISIBLE
				);
			},
			onDiscoverScrollToLower() {
				if (this.discoverLoading || this.feedTab === 4) {
					return;
				}
				this.loadMoreDiscoverCards();
			},
			listProgressText(visibleCount, totalCount, serverHasMore, loadingMore) {
				const safeVisible = Math.max(0, Number(visibleCount) || 0);
				const safeTotal = Math.max(0, Number(totalCount) || 0);
				if (loadingMore && safeVisible > 0) {
					return '正在加载更多角色...';
				}
				if (!safeTotal || safeVisible >= safeTotal) {
					return '';
				}
				return '已显示 ' + safeVisible + ' / ' + safeTotal + '，继续下滑自动加载更多';
			},
			getNoticeUnreadIdentitySignature(tavernApi) {
				try {
					if (tavernApi && typeof tavernApi.getViewerIdentitySignature === 'function') {
						return String(tavernApi.getViewerIdentitySignature() || '');
					}
					return tavernApi && typeof tavernApi.getClientUid === 'function'
						? 'client:' + String(tavernApi.getClientUid() || '')
						: 'none';
				} catch (e) {
					return 'unknown';
				}
			},
			prepareNoticeUnreadIdentity(tavernApi) {
				const currentIdentity = this.getNoticeUnreadIdentitySignature(tavernApi);
				if (
					this.noticeUnreadIdentitySignature &&
					this.noticeUnreadIdentitySignature !== currentIdentity
				) {
					this.noticeUnreadRequestVersion += 1;
					this.noticeUnread = 0;
					tavernInboxBadge
						.refreshCombinedInboxBadge(this, tavernApi, { noticeUnread: 0, adUnread: 0 })
						.catch(() => {});
				}
				this.noticeUnreadIdentitySignature = currentIdentity;
				return currentIdentity;
			},
			isNoticeUnreadRequestCurrent(tavernApi, identitySignature, requestVersion) {
				return (
					this.noticeUnreadRequestVersion === requestVersion &&
					this.noticeUnreadIdentitySignature === identitySignature &&
					this.getNoticeUnreadIdentitySignature(tavernApi) === identitySignature
				);
			},
			refreshNoticeUnread() {
				if (!this.isJgDiscover) {
					this.noticeUnreadRequestVersion += 1;
					this.noticeUnreadIdentitySignature = 'none';
					this.noticeUnread = 0;
					return tavernInboxBadge
						.refreshCombinedInboxBadge(this, null, { noticeUnread: 0, adUnread: 0 })
						.then((r) => r.noticeUnread);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const identitySignature = this.prepareNoticeUnreadIdentity(tavernApi);
				const requestVersion = ++this.noticeUnreadRequestVersion;
				return tavernNoticeState
					.fetchUnreadState(tavernApi, 30)
					.then((state) => {
						if (!this.isNoticeUnreadRequestCurrent(tavernApi, identitySignature, requestVersion)) {
							return { noticeUnread: this.noticeUnread, stale: true };
						}
						this.noticeUnread = Math.max(0, Number(state && state.unreadCount) || 0);
						return tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi, {
							noticeUnread: this.noticeUnread
						});
					})
					.then((r) => (r && r.noticeUnread != null ? r.noticeUnread : this.noticeUnread))
					.catch(() => {
						if (!this.isNoticeUnreadRequestCurrent(tavernApi, identitySignature, requestVersion)) {
							return this.noticeUnread;
						}
						return tavernInboxBadge
							.refreshCombinedInboxBadge(this, tavernApi, { noticeUnread: this.noticeUnread })
							.then((r) => r.noticeUnread)
							.catch(() => this.noticeUnread);
					});
			},
			normalizeNoticeDisplayType(value) {
				const type = String(value || '').toLowerCase();
				if (type === 'banner' || type === 'popup') return type;
				return 'inbox';
			},
			noticeDismissedTodayKey(item) {
				const id = item && item.id != null ? String(item.id) : '';
				const d = new Date();
				const day =
					d.getFullYear() +
					'-' +
					String(d.getMonth() + 1).padStart(2, '0') +
					'-' +
					String(d.getDate()).padStart(2, '0');
				return NOTICE_BANNER_DISMISSED_KEY + this.noticeStorageViewerKey() + '_' + id + '_' + day;
			},
			noticeStorageViewerKey() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					const clientUid = tavernApi && typeof tavernApi.getClientUid === 'function' ? tavernApi.getClientUid() : '';
					return encodeURIComponent(String(clientUid || 'guest'));
				} catch (e) {
					return 'guest';
				}
			},
			noticePopupAckKey(item) {
				const id = item && item.id != null ? String(item.id) : '';
				return NOTICE_POPUP_ACK_KEY + this.noticeStorageViewerKey() + '_' + id;
			},
			isNoticeBannerDismissed(item) {
				try {
					return !!uni.getStorageSync(this.noticeDismissedTodayKey(item));
				} catch (e) {
					return false;
				}
			},
			isNoticePopupAcked(item) {
				try {
					return !!uni.getStorageSync(this.noticePopupAckKey(item));
				} catch (e) {
					return false;
				}
			},
			loadNoticeExposure() {
				if (!this.isJgDiscover || this.noticeExposureLoading) {
					return Promise.resolve();
				}
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi || typeof tavernApi.fetchAppNotices !== 'function') {
					return Promise.resolve();
				}
				const hasUnreadNotice = Number(this.noticeUnread) > 0;
				this.noticeExposureLoading = true;
				return tavernApi
					.fetchAppNotices()
					.then((rows) => {
						const list = Array.isArray(rows) ? rows : [];
						const popup = list.find((item) => this.normalizeNoticeDisplayType(item && item.displayType) === 'popup' && !this.isNoticePopupAcked(item));
						const banner = list.find((item) => {
							const type = this.normalizeNoticeDisplayType(item && item.displayType);
							return (
								(type === 'banner' || type === 'popup') &&
								(hasUnreadNotice || type === 'popup') &&
								!this.isNoticeBannerDismissed(item) &&
								!(type === 'popup' && this.isNoticePopupAcked(item))
							);
						});
						this.noticePopup = popup || null;
						this.noticePopupVisible = !!popup;
						this.noticeBanner = banner || null;
					})
					.catch(() => {
						this.noticeBanner = null;
						this.noticePopup = null;
						this.noticePopupVisible = false;
					})
					.finally(() => {
						this.noticeExposureLoading = false;
					});
			},
			dismissNoticeBanner() {
				if (!this.noticeBanner) return;
				try {
					uni.setStorageSync(this.noticeDismissedTodayKey(this.noticeBanner), 1);
				} catch (e) {}
				this.noticeBanner = null;
			},
			openNoticeBanner() {
				this.goSystemMsg();
			},
			openNoticePopupDetail() {
				if (this.noticePopup && this.noticePopup.id) {
					try {
						uni.setStorageSync(this.noticePopupAckKey(this.noticePopup), 1);
						uni.setStorageSync(this.noticeDismissedTodayKey(this.noticePopup), 1);
					} catch (e) {}
				}
				this.noticePopupVisible = false;
				this.goSystemMsg();
			},
			confirmNoticePopup() {
				const item = this.noticePopup;
				if (!item || !item.id) {
					this.noticePopupVisible = false;
					return;
				}
				try {
					uni.setStorageSync(this.noticePopupAckKey(item), 1);
					uni.setStorageSync(this.noticeDismissedTodayKey(item), 1);
				} catch (e) {}
				this.noticePopupVisible = false;
				if (this.noticeBanner && String(this.noticeBanner.id) === String(item.id)) {
					this.noticeBanner = null;
				}
				const tavernApi = require('@/common/tavernApi.js');
				const done = (state) => {
					const count = Math.max(0, Number(state && state.unreadCount) || 0);
					this.noticeUnread = count;
					tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi, {
						noticeUnread: count
					});
				};
				if (tavernApi && typeof tavernApi.markNoticeRead === 'function') {
					tavernApi
						.markNoticeRead(tavernApi.getClientUid(), item.id)
						.then(done)
						.catch(() => {});
				}
			},
			tagOptionValue(item) {
				if (item == null) return '';
				if (typeof item === 'string') return String(item).trim();
				return String(item.name || item.code || item.displayLabel || '').trim();
			},
			displayDiscoverTag(item) {
				if (item == null) return '';
				if (typeof item === 'string') return String(item).trim();
				return String(item.displayLabel || item.name || item.code || '').trim();
			},
			normalizeDiscoverTagOptions(list) {
				const source = Array.isArray(list) ? list : [];
				const seen = {};
				const normalized = [];
				source.forEach((item, index) => {
					const code = this.tagOptionValue(item);
					if (!code) return;
					const key = code.toLowerCase();
					if (seen[key]) return;
					seen[key] = true;
					normalized.push({
						code: code,
						name: this.displayDiscoverTag(item),
						displayLabel: this.displayDiscoverTag(item),
						category: (item && item.category) || '推荐',
						color: (item && item.color) || '',
						sortOrder: Number(item && item.sortOrder) || index + 1,
						recommended: !!(item && item.recommended)
					});
				});
				return normalized.sort((a, b) => {
					if (a.sortOrder !== b.sortOrder) {
						return a.sortOrder - b.sortOrder;
					}
					return this.tagOptionValue(a).localeCompare(this.tagOptionValue(b));
				});
			},
			tryLoadDiscoverTags() {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) {
					this.discoverTagLoading = false;
					this.discoverTagOptions = [];
					this.discoverTagHint = this.discoverUi.tagDisabled;
					return;
				}
				this.discoverTagLoading = true;
				this.discoverTagHint = '';
				tavernApi
					.fetchCharacterTags()
					.then((list) => {
						const normalized = this.normalizeDiscoverTagOptions(list);
						this.discoverTagOptions = normalized;
						if (!normalized.length) {
							this.discoverTagHint = this.discoverUi.tagEmpty;
						}
					})
					.catch(() => {
						this.discoverTagOptions = [];
						this.discoverTagHint = this.discoverUi.tagFailed;
					})
					.finally(() => {
						this.discoverTagLoading = false;
					});
			},
			applyDiscoverFilterFromStorage() {
				try {
					const t = uni.getStorageSync('tavern_discover_tag');
					if (t != null && String(t).trim() !== '') {
						this.selectedTag = String(t).trim();
					}
					const g = uni.getStorageSync('tavern_discover_gameplay');
					if (g != null && String(g).trim() !== '') {
						this.selectedGameplay = String(g).trim();
					}
				} catch (e) {}
			},
			persistDiscoverTag(tag) {
				try {
					if (tag) {
						uni.setStorageSync('tavern_discover_tag', tag);
					} else {
						uni.removeStorageSync('tavern_discover_tag');
					}
				} catch (e) {}
			},
			persistDiscoverGameplay(g) {
				try {
					if (g) {
						uni.setStorageSync('tavern_discover_gameplay', g);
					} else {
						uni.removeStorageSync('tavern_discover_gameplay');
					}
				} catch (e) {}
			},
			toggleDiscoverTag(t) {
				if (this.selectedTag === t) {
					this.selectedTag = '';
					this.persistDiscoverTag('');
				} else {
					this.selectedTag = t;
					this.persistDiscoverTag(t);
				}
				this.tryLoadCharsFromBackend({ force: true });
			},
			openDiscoverTagPopup() {
				if (!this.discoverTagOptions.length && !this.discoverTagLoading) {
					this.tryLoadDiscoverTags();
				}
				this.discoverTagPopupShow = true;
			},
			pickDiscoverTagFromPopup(tag) {
				const value = this.tagOptionValue(tag);
				if (!value) return;
				this.discoverTagPopupShow = false;
				this.toggleDiscoverTag(value);
			},
			toggleGameplayFilter(g) {
				if (this.selectedGameplay === g) {
					this.selectedGameplay = '';
					this.persistDiscoverGameplay('');
				} else {
					this.selectedGameplay = g;
					this.persistDiscoverGameplay(g);
				}
				this.tryLoadCharsFromBackend({ force: true });
			},
			toggleGameplayFromPopup(g) {
				this.discoverTagPopupShow = false;
				this.toggleGameplayFilter(g);
			},
			clearDiscoverFilters() {
				this.selectedTag = '';
				this.selectedGameplay = '';
				this.persistDiscoverTag('');
				this.persistDiscoverGameplay('');
				this.tryLoadCharsFromBackend({ force: true });
			},
			clearDiscoverFiltersAndClose() {
				this.discoverTagPopupShow = false;
				this.clearDiscoverFilters();
			},
			buildDiscoverParams(offset) {
				return {
					limit: DISCOVER_FETCH_LIMIT,
					offset: Math.max(0, Number(offset) || 0),
					sort: this.feedTab === 1 ? 'likes' : this.feedTab === 2 ? 'old' : 'new',
					q: this.searchKeyword ? this.searchKeyword.trim() : undefined,
					tag: this.selectedTag ? String(this.selectedTag).trim() : undefined,
					gameplay: this.selectedGameplay ? String(this.selectedGameplay).trim() : undefined
				};
			},
			buildDiscoverFetchKey(params) {
				const safe = params || {};
				return JSON.stringify({
					q: safe.q || '',
					tag: safe.tag || '',
					gameplay: safe.gameplay || '',
					sort: safe.sort || 'new',
					limit: safe.limit || DISCOVER_FETCH_LIMIT
				});
			},
			tryLoadCharsFromBackend(options) {
				const opts = options || {};
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) {
					this.discoverLoading = false;
					this.allChars = [];
					this.syncDiscoverVisibleCounts();
					this.pickTop();
					this.discoverLoadError = this.discoverUi.tagDisabled;
					this.discoverFetchKey = '';
					this.discoverHasLoaded = false;
					return;
				}
				const params = this.buildDiscoverParams(0);
				const fetchKey = this.buildDiscoverFetchKey(params);
				if (!opts.force && this.discoverHasLoaded && this.discoverFetchKey === fetchKey) {
					this.pickTop();
					return;
				}
				const requestSeq = ++this.discoverRequestSeq;
				this.discoverVisibleCount = 0;
				this.unlockedVisibleCount = 0;
				this.discoverServerOffset = 0;
				this.discoverServerHasMore = false;
				this.discoverLoading = true;
				this.discoverLoadError = '';
				tavernApi
					.fetchCharacterList(params)
					.then((list) => {
						if (requestSeq !== this.discoverRequestSeq) {
							return;
						}
						const incoming = Array.isArray(list) ? list : [];
						this.allChars = incoming;
						this.discoverServerOffset = incoming.length;
						this.discoverServerHasMore = false;
						this.discoverFetchKey = fetchKey;
						this.discoverHasLoaded = true;
						this.applyLatestInteractionPatch();
						this.syncDiscoverVisibleCounts();
						this.pickTop();
					})
					.catch((e) => {
						if (requestSeq !== this.discoverRequestSeq) {
							return;
						}
						const tavernErrors = require('@/common/tavernErrors.js');
						this.allChars = [];
						this.syncDiscoverVisibleCounts();
						this.pickTop();
						this.discoverFetchKey = '';
						this.discoverHasLoaded = false;
						this.discoverServerOffset = 0;
						this.discoverServerHasMore = false;
						this.discoverLoadError = tavernErrors.getTavernErrorMessage(e, this.discoverUi.tagFailed);
						uni.showToast({
							title: this.discoverLoadError,
							icon: 'none',
							duration: 2800
						});
					})
					.finally(() => {
						if (requestSeq === this.discoverRequestSeq) {
							this.discoverLoading = false;
						}
					});
			},

			loadNextDiscoverPage() {
				const tavernApi = require('@/common/tavernApi.js');
				if (this.discoverLoading || this.feedTab === 4 || !this.discoverServerHasMore || !tavernApi.jgEnabled()) {
					return;
				}
				const params = this.buildDiscoverParams(this.discoverServerOffset);
				const fetchKey = this.buildDiscoverFetchKey(params);
				const requestSeq = ++this.discoverRequestSeq;
				this.discoverLoading = true;
				this.discoverLoadError = '';
				tavernApi
					.fetchCharacterList(params)
					.then((list) => {
						if (requestSeq !== this.discoverRequestSeq) {
							return;
						}
						const incoming = Array.isArray(list) ? list : [];
						const seen = new Set(this.allChars.map((item) => String(item && item.id)));
						const merged = this.allChars.slice();
						incoming.forEach((item) => {
							const key = String(item && item.id);
							if (!key || seen.has(key)) {
								return;
							}
							seen.add(key);
							merged.push(item);
						});
						this.allChars = merged;
						this.discoverServerOffset += incoming.length;
						this.discoverServerHasMore = incoming.length >= DISCOVER_FETCH_LIMIT;
						this.discoverFetchKey = fetchKey;
						this.discoverHasLoaded = true;
						this.applyLatestInteractionPatch();
						this.syncDiscoverVisibleCounts();
						this.pickTop();
					})
					.catch(() => {
						if (requestSeq === this.discoverRequestSeq) {
							this.discoverServerHasMore = false;
						}
					})
					.finally(() => {
						if (requestSeq === this.discoverRequestSeq) {
							this.discoverLoading = false;
						}
					});
			},


			safeLabels(c, maxLen) {
				const source = Array.isArray(c && c.label_array) ? c.label_array : [];
				const limit = Number(maxLen) > 0 ? Number(maxLen) : source.length;
				return source
					.map((item) => {
						if (item == null) return null;
						if (typeof item === 'string') return { code: String(item).trim() };
						const code = String(item.code || item.name || item.label || '').trim();
						if (!code) return null;
						return Object.assign({}, item, { code: code });
					})
					.filter(Boolean)
					.slice(0, limit);
			},
			displayHandle(c) {
				if (c.creator_handle) return c.creator_handle;
				const n = c.creator || '';
				return n.startsWith('@') ? n : '@' + n;
			},
			isNonEmptyImg(u) {
				return u != null && String(u).trim() !== '';
			},
			charAvatarUrl(c) {
				const tavernApi = require('@/common/tavernApi.js');
				const u = c && (c.avatar_thumb || c.cover_thumb || c.avatar || c.cover);
				if (!this.isNonEmptyImg(u)) return '/static/logo.png';
				const r = tavernApi.resolveJgAssetUrl(u);
				return r || '/static/logo.png';
			},
			charCoverUrl(c) {
				const tavernApi = require('@/common/tavernApi.js');
				const u = c && (
					c.cover_detail ||
					c.coverDetail ||
					c.cover ||
					c.avatar ||
					c.cover_thumb ||
					c.avatar_thumb
				);
				if (!this.isNonEmptyImg(u)) return '/static/logo.png';
				const r = tavernApi.resolveJgAssetUrl(u);
				return r || '/static/logo.png';
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
			formatLikes(n) {
				const v = Number(n);
				if (!isFinite(v) || v < 0) {
					return '0';
				}
				return String(Math.floor(v));
			},
			cardFallbackCopy(c) {
				if (!c || typeof c !== 'object') {
					return '';
				}
				const labels = this.safeLabels(c);
				if (labels.length && labels[0] && labels[0].code) {
					return String(labels[0].code).trim();
				}
				return (c.gameplay_type && String(c.gameplay_type).trim()) || '';
			},
			cardPreview(c) {
				if (!c || typeof c !== 'object') {
					return '';
				}
				const raw = c.public_summary || c.publicSummary || c.bio || c.persona || c.scenario || c.tagline || '';
				const text = this.normalizePreviewText(raw);
				if (!text) {
					return this.cardFallbackCopy(c);
				}
				return this.truncatePreviewText(text, 38);
			},
			cardHeroCopy(c) {
				if (!c || typeof c !== 'object') {
					return '';
				}
				const raw = c.public_summary || c.publicSummary || c.tagline || c.bio || c.persona || c.scenario || '';
				const text = this.normalizePreviewText(raw);
				if (!text) {
					return this.truncatePreviewText(this.cardFallbackCopy(c), 20);
				}
				return this.truncatePreviewText(text, 20);
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
			cardMetaBadges(c) {
				if (!c || typeof c !== 'object') {
					return [];
				}
				const badges = [];
				if (this.isPreviewBlurActive(c)) {
					badges.push({ text: this.previewBlurBadgeText(c), tone: 'blur' });
				}
				if (c.vip_only) {
					badges.push({ text: 'VIP', tone: 'vip' });
				}
				if (c.gameplay_type) {
					badges.push({ text: String(c.gameplay_type).trim(), tone: 'mode' });
				}
				if (c.token_display) {
					badges.push({ text: 'Tokens ' + String(c.token_display).trim(), tone: 'token' });
				}
				if (!badges.length) {
					const firstLabel = this.safeLabels(c, 1)[0];
					if (firstLabel && firstLabel.code) {
						badges.push({ text: firstLabel.code, tone: 'label' });
					}
				}
				return badges.slice(0, 3);
			},
			cardVisualTier(c) {
				if (!c || typeof c !== 'object') {
					return 'standard';
				}
				const requiredLevel = Math.max(
					0,
					Math.min(2, Math.floor(Number(c.preview_blur_vip_level || c.previewBlurVipLevel || 0) || 0))
				);
				if (requiredLevel >= 2) {
					return 'svip';
				}
				if (requiredLevel >= 1 || c.vip_only || c.vipOnly) {
					return 'vip';
				}
				return 'standard';
			},
			cardTierClass(c) {
				return 'card-tier--' + this.cardVisualTier(c);
			},
			cardTierLabel(c) {
				const tier = this.cardVisualTier(c);
				if (tier === 'svip') return 'SVIP';
				if (tier === 'vip') return 'VIP';
				return 'R';
			},
			cardTierCaption(c) {
				const tier = this.cardVisualTier(c);
				if (tier === 'svip') return 'SUPREME';
				if (tier === 'vip') return 'PREMIUM';
				return 'CHARACTER';
			},
			normalizePreviewText(value) {
				return value == null ? '' : String(value).replace(/\s+/g, ' ').trim();
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
			applyLatestInteractionPatch() {
				let patch = null;
				try {
					patch = uni.getStorageSync(INTERACTION_PATCH_KEY);
				} catch (e) {
					patch = null;
				}
				if (!patch || typeof patch !== 'object') {
					return;
				}
				const targetId = Number(patch.id);
				if (!isFinite(targetId) || targetId <= 0) {
					return;
				}
				let changed = false;
				const applyPatchToList = (list) => {
					if (!Array.isArray(list) || !list.length) {
						return list;
					}
					return list.map((item) => {
						if (!item || Number(item.id) !== targetId) {
							return item;
						}
						changed = true;
						return Object.assign({}, item, {
							like_count: this.normalizeCount(patch.like_count),
							dislike_count: this.normalizeCount(patch.dislike_count),
							is_favorite: !!patch.is_favorite,
							user_vote: this.normalizeVote(patch.user_vote)
						});
					});
				};
				this.allChars = applyPatchToList(this.allChars);
				this.topPick = applyPatchToList(this.topPick);
				this.syncDiscoverVisibleCounts();
				if (changed) {
					try {
						uni.removeStorageSync(INTERACTION_PATCH_KEY);
					} catch (e) {}
				}
			},
			normalizeCount(value) {
				const n = Number(value);
				if (!isFinite(n) || n < 0) {
					return 0;
				}
				return Math.floor(n);
			},
			normalizeVote(value) {
				return value === 'like' || value === 'dislike' ? value : 'none';
			},
			setFeedTab(i) {
				if (this.feedTab === i) {
					return;
				}
				this.feedTab = i;
				if (!this.discoverLoading && i !== 4) {
					this.tryLoadCharsFromBackend({ force: true });
					return;
				}
				this.syncDiscoverVisibleCounts();
				this.pickTop();
			},
			setMainTab(i) {
				this.swiperCurrent = i;
				this.current = i;
				this.syncDiscoverVisibleCounts();
			},
			setDiscoverHover(type, id) {
				const value = id == null ? '' : id;
				if (type === 'top') {
					this.hoverTopPickId = value;
					return;
				}
				this.hoverGridId = value;
			},
			clearDiscoverHover(type) {
				if (type === 'top') {
					this.hoverTopPickId = '';
					return;
				}
				this.hoverGridId = '';
			},
			goSystemMsg() {
				uni.navigateTo({ url: '/pages/chat/systemmsg' });
			},
			openIllustrationSite() {
				const target = 'https://siyeai.pengqiyao.cn/illustration/';
				// #ifdef H5
				window.location.href = '/illustration/';
				return;
				// #endif
				// #ifndef H5
				uni.setClipboardData({
					data: target,
					success: () => uni.showToast({ title: '插画站链接已复制', icon: 'none' })
				});
				// #endif
			},
			pickTop() {
				const arr = this.allChars.slice().sort(this.compareCharactersRecentFirst);
				if (!arr.length) {
					this.topPick = [];
					return;
				}
				const k = this.shuffleKey;
				this.topPick = [arr[k % arr.length], arr[(k + 1) % arr.length], arr[(k + 2) % arr.length]];
			},
			compareCharactersRecentFirst(a, b) {
				return (Number(b && b.id) || 0) - (Number(a && a.id) || 0);
			},
			refreshTopList() {
				if (this.allChars.length) {
					this.shuffleKey += 1;
					this.pickTop();
					return;
				}
				this.tryLoadCharsFromBackend({ force: true });
			},
			goDetail(id) {
				uni.navigateTo({ url: '/pages/tavern/charDetail?id=' + id });
			},
			handleDiscoverCardAction(card) {
				if (card && card.unlocked === false) {
					this.util.urlTo('/pages/user/myvip');
					return;
				}
				this.goDetail(card && card.id);
			},
			toastSearch() {
				const q = String(this.searchKeyword || '').trim();
				const url = q ? '/pages/index/search?q=' + encodeURIComponent(q) : '/pages/index/search';
				uni.navigateTo({ url });
			},

			onMoreMenu() {
				this.discoverMenuShow = true;
			},
			handleDiscoverMenu(action) {
				this.discoverMenuShow = false;
				if (action === 'notice') {
					this.goSystemMsg();
					return;
				}
				if (action === 'favorites') {
					this.util.urlTo('/pages/user/myfavorites');
				}
			},
			clearSearchKeyword() {
				this.searchKeyword = '';
				try {
					uni.removeStorageSync('tavern_discover_q');
				} catch (e) {}
				this.tryLoadCharsFromBackend({ force: true });
			}
		}
	};
</script>

<style lang="scss" scoped>
	.root {
		position: relative;
		height: 100vh;
		min-height: 100vh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		background: transparent;
		box-sizing: border-box;
	}

	.global-bg-image {
		position: absolute;
		inset: 0;
		top: 0;
		right: 0;
		bottom: 0;
		left: 0;
		z-index: 0;
		width: 100%;
		height: 100%;
		display: block;
		pointer-events: none;
	}

	.page-scroll {
		position: relative;
		z-index: 1;
		flex: 1;
		height: 0;
		min-height: 0;
		width: 100%;
		box-sizing: border-box;
	}

	.disc-header {
		position: relative;
		overflow: hidden;
		padding: 12rpx 28rpx 24rpx;
		background: transparent;
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.1);
		box-shadow: none;
	}

	.disc-header::after {
		display: none;
	}

	.brand-row {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 20rpx;
		min-height: 80rpx;
		padding-top: 8rpx;
	}

	.brand-main {
		display: flex;
		align-items: center;
		gap: 18rpx;
		min-width: 0;
		flex: 1;
	}

	.brand-logo {
		width: 72rpx;
		height: 72rpx;
		border-radius: 50%;
		flex-shrink: 0;
		background: rgba(255, 255, 255, 0.55);
		border: 2rpx solid rgba(255, 255, 255, 0.75);
	}

	.brand-title {
		font-size: 34rpx;
		font-weight: 700;
		letter-spacing: 1rpx;
		color: #1f3a4d;
		line-height: 1.25;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.brand-actions {
		display: flex;
		align-items: center;
		gap: 14rpx;
		flex-shrink: 0;
	}

	.icon-btn {
		width: 68rpx;
		height: 68rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.58);
		border: 1rpx solid rgba(255, 255, 255, 0.6);
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.icon-btn:active {
		opacity: 0.86;
	}

	.notice-ico {
		font-size: 30rpx;
		color: #3d5f74;
		line-height: 1;
	}

	.icon-more {
		font-size: 36rpx;
		color: #3d5f74;
		line-height: 1;
		transform: translateY(-2rpx);
	}

	.notice-badge {
		position: absolute;
		top: -4rpx;
		right: -4rpx;
		min-width: 28rpx;
		height: 28rpx;
		padding: 0 6rpx;
		border-radius: 999rpx;
		background: #f43f5e;
		color: #fff;
		font-size: 16rpx;
		font-weight: 700;
		line-height: 28rpx;
		text-align: center;
		border: 2rpx solid rgba(255, 255, 255, 0.95);
		box-sizing: border-box;
	}

	.search-row {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		margin-top: 22rpx;
		padding: 0 24rpx;
		height: 76rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.66);
		border: 1rpx solid rgba(255, 255, 255, 0.58);
		box-sizing: border-box;
	}

	.search-ico {
		width: 28rpx;
		height: 28rpx;
		margin-right: 14rpx;
		opacity: 0.4;
		flex-shrink: 0;
	}

	.search-ph {
		flex: 1;
		font-size: 26rpx;
		line-height: 1.3;
		color: #7a8fa3;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.search-ph--active {
		color: #24445f;
		font-weight: 600;
	}

	.search-clear {
		margin-left: 12rpx;
		padding: 8rpx 16rpx;
		border-radius: 999rpx;
		font-size: 22rpx;
		font-weight: 600;
		color: #9a6b7f;
		background: rgba(255, 255, 255, 0.72);
		flex-shrink: 0;
	}

	.feed-tabs {
		position: relative;
		z-index: 1;
		width: 100%;
		margin-top: 24rpx;
		white-space: nowrap;
	}

	.feed-tabs-inner {
		display: inline-flex;
		flex-direction: row;
		align-items: center;
		gap: 16rpx;
		padding: 4rpx 0;
	}

	.feed-tab {
		position: relative;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		height: 60rpx;
		padding: 0 28rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.48);
		border: 1rpx solid rgba(255, 255, 255, 0.55);
		flex-shrink: 0;
		box-sizing: border-box;
	}

	.feed-tab-txt {
		font-size: 26rpx;
		font-weight: 560;
		color: #5f7690;
		line-height: 1;
	}

	.feed-tab.on {
		background: #4f93a3;
		border-color: #4f93a3;
	}

	.feed-tab.on .feed-tab-txt {
		color: #fff;
		font-weight: 700;
	}

	.feed-dot {
		position: absolute;
		top: 10rpx;
		right: 12rpx;
		width: 10rpx;
		height: 10rpx;
		border-radius: 50%;
		background: #f43f5e;
	}

	.notice-banner {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: 12rpx;
		margin-top: 20rpx;
		padding: 16rpx 52rpx 16rpx 18rpx;
		border-radius: 16rpx;
		background: rgba(255, 255, 255, 0.72);
		border: 1rpx solid rgba(124, 183, 207, 0.18);
		box-sizing: border-box;
	}

	.notice-banner-mark {
		flex-shrink: 0;
		height: 32rpx;
		padding: 0 12rpx;
		border-radius: 8rpx;
		background: rgba(83, 161, 194, 0.12);
		color: #3e8dab;
		font-size: 20rpx;
		font-weight: 700;
		line-height: 32rpx;
	}

	.notice-banner-title {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 24rpx;
		font-weight: 650;
		color: #254962;
		line-height: 1.3;
	}

	.notice-banner-close {
		position: absolute;
		right: 12rpx;
		top: 50%;
		width: 40rpx;
		height: 40rpx;
		margin-top: -20rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.55);
		color: #7a91a2;
		text-align: center;
		line-height: 38rpx;
		font-size: 28rpx;
	}

	.tag-filter-row {
		position: relative;
		z-index: 1;
		width: 100%;
		margin-top: 18rpx;
		padding-bottom: 4rpx;
	}

	.tag-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.tag-inner {
		display: inline-flex;
		flex-direction: row;
		align-items: center;
		gap: 4rpx;
		padding: 8rpx 2rpx;
		min-height: 48rpx;
	}

	.tag-link {
		flex-shrink: 0;
		padding: 8rpx 10rpx;
		font-size: 26rpx;
		font-weight: 500;
		color: #6f8498;
		line-height: 1.2;
	}

	.tag-link.on {
		color: #2a6d87;
		font-weight: 700;
	}

	.tag-link--more {
		color: #5d8aa0;
	}

	.tag-link--clear {
		color: #9a6b7f;
	}

	.tag-sep {
		flex-shrink: 0;
		padding: 0 2rpx;
		font-size: 22rpx;
		color: rgba(111, 132, 152, 0.45);
		line-height: 1;
	}

	.tag-popup {
		background: #f7fbff;
		padding: 30rpx 28rpx 24rpx;
		max-height: 70vh;
	}

	.tag-popup-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 16rpx;
		padding-bottom: 18rpx;
		border-bottom: 1rpx solid rgba(89, 145, 174, 0.12);
	}

	.tag-popup-head-main {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.tag-popup-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #24445f;
	}

	.tag-popup-sub {
		font-size: 22rpx;
		color: #6f8295;
	}

	.tag-popup-reset {
		flex-shrink: 0;
		font-size: 22rpx;
		font-weight: 600;
		color: #4c93b4;
		padding-top: 4rpx;
	}

	.tag-popup-scroll {
		max-height: 54vh;
		padding-top: 8rpx;
	}

	.tag-popup-notice {
		margin-top: 18rpx;
		padding: 18rpx 20rpx;
		border-radius: 18rpx;
		font-size: 22rpx;
		line-height: 1.6;
		color: #b65f83;
		background: rgba(255, 230, 238, 0.7);
		border: 1rpx solid rgba(244, 160, 190, 0.24);
	}

	.tag-popup-group {
		margin-top: 18rpx;
		padding: 18rpx 18rpx 20rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.78);
		border: 1rpx solid rgba(89, 145, 174, 0.12);
	}

	.tag-popup-group-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-bottom: 14rpx;
	}

	.tag-popup-group-title {
		font-size: 22rpx;
		font-weight: 600;
		color: #557089;
	}

	.tag-popup-group-count {
		font-size: 20rpx;
		color: #8796a6;
	}

	.tag-popup-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 14rpx;
	}

	.tag-popup-empty {
		margin-top: 22rpx;
		padding: 30rpx 20rpx;
		border-radius: 20rpx;
		font-size: 22rpx;
		line-height: 1.7;
		text-align: center;
		color: #72849a;
		background: rgba(255, 255, 255, 0.62);
		border: 1rpx dashed rgba(89, 145, 174, 0.2);
	}

	.discover-menu-overlay {
		position: fixed;
		inset: 0;
		z-index: 998;
		background:
			linear-gradient(180deg, rgba(59, 108, 142, 0.08) 0%, rgba(59, 108, 142, 0.14) 24%, rgba(59, 108, 142, 0.02) 58%);
	}

	.notice-popup-mask {
		position: fixed;
		inset: 0;
		z-index: 1200;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 48rpx 36rpx;
		background: rgba(29, 55, 74, 0.2);
		backdrop-filter: blur(6px);
		-webkit-backdrop-filter: blur(6px);
		box-sizing: border-box;
	}

	.notice-popup-card {
		width: 100%;
		max-width: 690rpx;
		max-height: 78vh;
		padding: 30rpx 28rpx 28rpx;
		border-radius: 30rpx;
		background:
			linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(246, 252, 255, 0.98) 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.92);
		box-shadow: 0 28rpx 72rpx rgba(48, 88, 116, 0.22);
		box-sizing: border-box;
	}

	.notice-popup-top {
		display: flex;
		align-items: flex-start;
		gap: 18rpx;
	}

	.notice-popup-icon {
		flex-shrink: 0;
		width: 54rpx;
		height: 54rpx;
		border-radius: 18rpx;
		background: linear-gradient(135deg, #58aace 0%, #88d3e5 100%);
		color: #fff;
		font-size: 32rpx;
		font-weight: 900;
		text-align: center;
		line-height: 54rpx;
		box-shadow: 0 12rpx 24rpx rgba(87, 169, 206, 0.18);
	}

	.notice-popup-heading {
		flex: 1;
		min-width: 0;
	}

	.notice-popup-mark {
		display: block;
		height: 28rpx;
		color: #5593ad;
		font-size: 20rpx;
		font-weight: 800;
	}

	.notice-popup-title {
		display: block;
		margin-top: 6rpx;
		font-size: 32rpx;
		font-weight: 900;
		line-height: 1.34;
		color: #213f58;
	}

	.notice-popup-scroll {
		max-height: 38vh;
		margin-top: 24rpx;
		padding: 20rpx 22rpx;
		border-radius: 22rpx;
		background: rgba(239, 249, 253, 0.66);
		box-sizing: border-box;
	}

	.notice-popup-content {
		display: block;
		font-size: 25rpx;
		line-height: 1.72;
		color: #5d7488;
		white-space: pre-wrap;
	}

	.notice-popup-actions {
		display: flex;
		align-items: center;
		gap: 14rpx;
		margin-top: 24rpx;
	}

	.notice-popup-btn {
		flex: 1;
		height: 78rpx;
		border-radius: 20rpx;
		background: linear-gradient(135deg, #55a8cd 0%, #86d2e5 100%);
		color: #fff;
		font-size: 25rpx;
		font-weight: 800;
		text-align: center;
		line-height: 78rpx;
		box-shadow: 0 14rpx 26rpx rgba(87, 169, 206, 0.2);
	}

	.notice-popup-btn--ghost {
		background: rgba(255, 255, 255, 0.74);
		color: #4a839d;
		box-shadow: none;
		border: 1rpx solid rgba(87, 169, 206, 0.18);
	}

	.discover-menu-layer {
		display: flex;
		justify-content: flex-end;
		padding-left: 28rpx;
		padding-right: 28rpx;
	}

	.discover-menu-card {
		width: 360rpx;
		padding: 14rpx;
		border-radius: 28rpx;
		background:
			linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(246, 251, 255, 0.96) 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.86);
		box-shadow:
			0 26rpx 52rpx rgba(70, 112, 145, 0.18),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
		backdrop-filter: blur(18px);
		-webkit-backdrop-filter: blur(18px);
	}

	.discover-menu-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12rpx 12rpx 10rpx;
	}

	.discover-menu-title {
		font-size: 22rpx;
		font-weight: 700;
		letter-spacing: 1rpx;
		color: #6c7f91;
	}

	.discover-menu-close {
		width: 46rpx;
		height: 46rpx;
		border-radius: 999rpx;
		text-align: center;
		line-height: 46rpx;
		font-size: 28rpx;
		color: #6c7f91;
		background: rgba(218, 239, 248, 0.72);
	}

	.discover-menu-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 18rpx;
		padding: 20rpx 18rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.66);
		border: 1rpx solid transparent;
	}

	.discover-menu-item + .discover-menu-item {
		margin-top: 10rpx;
	}

	.discover-menu-item:active {
		background: rgba(232, 246, 251, 0.8);
		border-color: rgba(87, 169, 206, 0.2);
	}

	.discover-menu-item-main {
		flex: 1;
		min-width: 0;
	}

	.discover-menu-item-title {
		display: block;
		font-size: 25rpx;
		font-weight: 700;
		color: #24445f;
	}

	.discover-menu-item-title-row {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.discover-menu-unread {
		min-width: 28rpx;
		height: 28rpx;
		padding: 0 8rpx;
		border-radius: 999rpx;
		background: #f43f5e;
		color: #fff;
		font-size: 18rpx;
		font-weight: 700;
		line-height: 28rpx;
		text-align: center;
	}

	.discover-menu-item-desc {
		display: block;
		margin-top: 8rpx;
		font-size: 21rpx;
		line-height: 1.55;
		color: #70859a;
	}

	.discover-menu-item-arrow {
		flex-shrink: 0;
		font-size: 30rpx;
		font-weight: 700;
		color: rgba(76, 147, 180, 0.86);
	}

	.main-tabs {
		position: relative;
		z-index: 1;
		display: inline-flex;
		gap: 16rpx;
		margin-top: 20rpx;
		padding: 8rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.44);
		border: 1rpx solid rgba(255, 255, 255, 0.66);
	}

	.main-pill {
		min-width: 120rpx;
		padding: 12rpx 30rpx;
		border-radius: 14rpx;
		font-size: 26rpx;
		text-align: center;
		color: #5f7489;
		background: transparent;
		border: 1rpx solid transparent;
	}

	.main-pill.on {
		color: #fff;
		font-weight: 700;
		background: linear-gradient(135deg, #57a9ce 0%, #89d3e6 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		box-shadow: 0 8rpx 18rpx rgba(87, 169, 206, 0.2);
	}

	.filter-chips {
		width: 100%;
		margin-top: 16rpx;
	}

	.filter-chips-inner {
		display: inline-flex;
		gap: 12rpx;
	}

	.filter-chip {
		display: inline-flex;
		align-items: center;
		gap: 6rpx;
		padding: 10rpx 20rpx;
		border-radius: 12rpx;
		font-size: 22rpx;
		color: #a8b3cf;
		background: rgba(255, 255, 255, 0.04);
		flex-shrink: 0;
	}

	.chev {
		font-size: 18rpx;
		opacity: 0.7;
	}

	.hash-row {
		width: 100%;
		margin-top: 12rpx;
	}

	.hash-inner {
		display: inline-flex;
		gap: 20rpx;
		padding-bottom: 8rpx;
	}

	.hash-tag {
		font-size: 24rpx;
		color: #4c93b4;
		flex-shrink: 0;
	}

	.hash-tag--on {
		color: #f9a8d4;
		font-weight: 700;
		text-decoration: underline;
	}

	.page-box {
		padding-bottom: 20rpx;
	}

	.illustration-entry-card {
		position: relative;
		display: flex;
		align-items: center;
		min-height: 168rpx;
		margin: 16rpx 28rpx 0;
		padding: 24rpx 24rpx 24rpx 28rpx;
		border-radius: 28rpx;
		overflow: hidden;
		background:
			radial-gradient(circle at 82% 18%, rgba(255, 159, 202, 0.34), transparent 32%),
			linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(239, 252, 255, 0.86) 48%, rgba(255, 243, 249, 0.9) 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.88);
		box-shadow: 0 18rpx 44rpx rgba(70, 116, 132, 0.12);
	}

	.illustration-entry-copy {
		position: relative;
		z-index: 2;
		flex: 1;
		min-width: 0;
		padding-right: 18rpx;
	}

	.illustration-entry-kicker,
	.illustration-entry-title,
	.illustration-entry-desc {
		display: block;
	}

	.illustration-entry-kicker {
		font-size: 22rpx;
		font-weight: 700;
		color: #2f8793;
	}

	.illustration-entry-title {
		margin-top: 8rpx;
		font-size: 32rpx;
		font-weight: 900;
		line-height: 1.25;
		color: #1f3d55;
	}

	.illustration-entry-desc {
		margin-top: 10rpx;
		font-size: 23rpx;
		line-height: 1.45;
		color: rgba(55, 78, 95, 0.72);
	}

	.illustration-entry-stack {
		position: relative;
		z-index: 2;
		width: 124rpx;
		height: 112rpx;
		margin-right: 18rpx;
		flex-shrink: 0;
	}

	.illustration-entry-thumb {
		position: absolute;
		width: 72rpx;
		height: 92rpx;
		border-radius: 18rpx;
		border: 3rpx solid rgba(255, 255, 255, 0.92);
		box-shadow: 0 12rpx 28rpx rgba(45, 79, 95, 0.16);
	}

	.illustration-entry-thumb.thumb-a {
		left: 0;
		top: 14rpx;
		transform: rotate(-8deg);
		background: linear-gradient(160deg, #fff6fb 0%, #ffb8d6 48%, #83d5e1 100%);
	}

	.illustration-entry-thumb.thumb-b {
		left: 34rpx;
		top: 0;
		transform: rotate(5deg);
		background: linear-gradient(160deg, #ffffff 0%, #dff9ff 48%, #f7a7c9 100%);
	}

	.illustration-entry-thumb.thumb-c {
		right: 0;
		bottom: 0;
		transform: rotate(12deg);
		background: linear-gradient(160deg, #fff 0%, #b7f0ea 44%, #ffd2e4 100%);
	}

	.illustration-entry-action {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 92rpx;
		height: 58rpx;
		border-radius: 999rpx;
		background: #233c55;
		box-shadow: 0 12rpx 22rpx rgba(35, 60, 85, 0.18);
		font-size: 24rpx;
		font-weight: 800;
		color: #fff;
		flex-shrink: 0;
	}

	.card-top {
		margin: 16rpx 28rpx 0;
		background: rgba(255, 255, 255, 0.78);
		border-radius: 24rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.86);
		box-shadow: 0 18rpx 48rpx rgba(73, 112, 137, 0.12);
		padding-bottom: 24rpx;
		overflow: hidden;
	}

	.card-top-hd {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 28rpx 24rpx 16rpx;
	}

	.card-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #24445f;
	}

	.refresh {
		display: flex;
		align-items: center;
		font-size: 24rpx;
		color: #5f7891;
		gap: 8rpx;
	}

	.refresh-ico {
		width: 28rpx;
		height: 28rpx;
		opacity: 0.85;
	}

	.grid3 {
		display: flex;
		padding: 0 12rpx;
	}

	.g3 {
		width: 33.33%;
	}

	.g3-inner {
		position: relative;
		width: 216rpx;
		height: 344rpx;
		margin: 0 auto;
		border-radius: 36rpx;
		overflow: visible;
		background: transparent;
		box-shadow: none;
		transition: transform 0.18s ease;
		will-change: transform;
	}

	.g3-img {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
		display: block;
		/* #ifdef H5 */
		object-fit: cover;
		object-position: center top;
		/* #endif */
		transform-origin: center center;
		transition: transform 0.36s ease, filter 0.36s ease;
	}

	.g3-img--blur {
		filter: blur(18rpx) scale(1.08) brightness(0.74);
	}

	.g3-overlay-top {
		position: absolute;
		left: 0;
		right: 0;
		top: 0;
		z-index: 5;
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		padding: 10rpx;
		pointer-events: none;
		transition: transform 0.24s ease;
	}

	.g3-handle {
		font-size: 20rpx;
		color: rgba(255, 255, 255, 0.95);
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.6);
		max-width: 52%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.g3-likes {
		display: flex;
		align-items: center;
		gap: 4rpx;
		padding: 4rpx 10rpx;
		border-radius: 999rpx;
		background: rgba(12, 18, 24, 0.48);
		font-size: 20rpx;
		color: rgba(255, 245, 220, 0.94);
	}

	.g3-tags {
		position: absolute;
		left: 8rpx;
		right: 8rpx;
		bottom: 102rpx;
		z-index: 5;
		display: flex;
		flex-wrap: wrap;
		gap: 6rpx;
		pointer-events: none;
		transition: transform 0.24s ease;
	}

	.mini-tag {
		font-size: 18rpx;
		padding: 4rpx 10rpx;
		border-radius: 8rpx;
		color: #fff;
	}

	.g3-mask {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 5;
		width: 100%;
		padding: 112rpx 12rpx 20rpx;
		background: linear-gradient(0deg, rgba(21, 42, 58, 0.88) 0%, rgba(21, 42, 58, 0.08) 100%);
	}

	.g3-name {
		font-size: 24rpx;
		color: #fff;
		font-weight: bold;
	}

	.g3-sub {
		font-size: 20rpx;
		color: rgba(255, 255, 255, 0.85);
		margin-top: 6rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		line-height: 1.35;
	}

	.grid2-wrap {
		display: flex;
		flex-wrap: wrap;
		margin: 22rpx 12rpx 0;
	}

	.grid2-item {
		width: 50%;
		margin-top: 20rpx;
	}

	.card-disc {
		position: relative;
		width: 346rpx;
		margin: 0 auto;
		border-radius: 0;
		overflow: visible;
		background: transparent;
		border: none;
		box-shadow: none;
		transition: transform 0.18s ease;
		will-change: transform;
	}

	.card-visual {
		position: relative;
		width: 100%;
		height: 550rpx;
		overflow: hidden;
		border-radius: 36rpx;
		background: transparent;
	}

	.card2-bg {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
		z-index: 0;
		display: block;
		/* #ifdef H5 */
		object-fit: cover;
		object-position: center top;
		/* #endif */
		transform-origin: center center;
		transition: transform 0.4s ease, filter 0.4s ease;
	}

	.card2-bg--blur {
		filter: blur(18rpx) scale(1.08) brightness(0.72);
	}

	.preview-blur-layer {
		position: absolute;
		inset: 0;
		z-index: 4;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 32rpx 28rpx;
		background: linear-gradient(180deg, rgba(8, 10, 18, 0.16) 0%, rgba(8, 10, 18, 0.34) 100%);
		pointer-events: none;
	}

	.preview-blur-layer--compact {
		inset: 14rpx 14rpx auto 14rpx;
		padding: 0;
		justify-content: flex-start;
		align-items: flex-start;
		background: none;
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

	.preview-blur-surface--compact {
		inset: -10rpx;
		filter: blur(24rpx) saturate(0.9) brightness(0.68);
		transform: scale(1.1);
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

	/* #ifdef H5 */
	.g3-inner,
	.card-disc {
		cursor: pointer;
	}

	.g3-inner:hover .g3-img--blur,
	.g3-inner--hover .g3-img--blur,
	.g3-inner:active .g3-img--blur {
		filter: blur(18rpx) scale(1.08) brightness(0.76);
	}

	.card-disc:hover .card2-bg--blur,
	.card-disc--hover .card2-bg--blur,
	.card-disc:active .card2-bg--blur {
		filter: blur(18rpx) scale(1.08) brightness(0.74);
	}

	.grid2-item:hover .card-disc,
	.grid2-item:active .card-disc,
	.g3:hover .g3-inner,
	.g3:active .g3-inner {
		transform: none;
	}
	/* #endif */

	.vip-corner {
		z-index: 3;
		position: absolute;
		top: 12rpx;
		left: 12rpx;
		font-size: 20rpx;
		color: #8b6914;
		background: linear-gradient(90deg, #f9e9ce, #f1ca98);
		padding: 6rpx 14rpx;
		border-radius: 12rpx;
	}

	.card-float-top {
		position: absolute;
		left: 0;
		right: 0;
		top: 0;
		z-index: 5;
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		padding: 12rpx;
		pointer-events: none;
		transition: transform 0.26s ease;
	}

	.hdl {
		font-size: 20rpx;
		color: #fff;
		text-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.65);
		max-width: 55%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.like-badge {
		display: flex;
		align-items: center;
		gap: 4rpx;
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
		background: rgba(12, 18, 24, 0.48);
		font-size: 22rpx;
		color: rgba(255, 245, 220, 0.94);
		backdrop-filter: blur(12px);
		border: 1rpx solid rgba(255, 255, 255, 0.16);
	}

	.heart {
		font-size: 22rpx;
		color: #ffd5e1;
	}

	.heart.sm {
		font-size: 20rpx;
	}

	.card-float-tags {
		position: absolute;
		left: 12rpx;
		right: 12rpx;
		bottom: 128rpx;
		z-index: 5;
		display: flex;
		flex-wrap: wrap;
		gap: 6rpx;
		pointer-events: none;
		transition: transform 0.26s ease;
	}

	.card-visual-copy {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 5;
		padding: 120rpx 18rpx 18rpx;
		background: linear-gradient(
			180deg,
			rgba(8, 14, 20, 0) 0%,
			rgba(8, 14, 20, 0.5) 48%,
			rgba(8, 14, 20, 0.92) 100%
		);
		transition: transform 0.24s ease;
	}

	.card-visual-title {
		display: block;
		font-size: 30rpx;
		font-weight: 800;
		color: #fff;
		line-height: 1.2;
	}

	.card-visual-desc {
		display: block;
		margin-top: 6rpx;
		font-size: 20rpx;
		line-height: 1.4;
		color: rgba(245, 248, 250, 0.78);
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
	}

	.float-tag {
		font-size: 20rpx;
		padding: 4rpx 12rpx;
		border-radius: 999rpx;
		color: rgba(255, 248, 230, 0.94);
		border: 1rpx solid rgba(255, 255, 255, 0.14);
		backdrop-filter: blur(8px);
	}

	.tone-0 {
		background: rgba(59, 130, 246, 0.88);
	}
	.tone-1 {
		background: rgba(180, 83, 9, 0.88);
	}
	.tone-2 {
		background: rgba(220, 38, 38, 0.88);
	}

	.card-meta {
		display: none !important;
		height: 0 !important;
		min-height: 0 !important;
		max-height: 0 !important;
		padding: 0 !important;
		margin: 0 !important;
		overflow: hidden !important;
		background: transparent !important;
		border: none !important;
		box-shadow: none !important;
	}

	.meta-kicker {
		display: block;
		margin-top: 12rpx;
		font-size: 20rpx;
		font-weight: 700;
		letter-spacing: 1rpx;
		color: #4c93b4;
	}

	.grid-section-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 20rpx 28rpx 14rpx;
	}

	.grid-section-head--compact {
		margin-top: 16rpx;
	}

	.grid-section-copy {
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.grid-section-title {
		font-size: 28rpx;
		font-weight: 700;
		color: #24445f;
	}

	.grid-section-sub {
		font-size: 22rpx;
		color: #6f8295;
	}

	.meta-title-row {
		display: flex;
		align-items: center;
		gap: 8rpx;
	}

	.meta-title {
		font-size: 28rpx;
		font-weight: 700;
		color: #24445f;
		flex: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.vip-ico {
		width: 36rpx;
		height: 28rpx;
		flex-shrink: 0;
	}

	.meta-desc {
		margin-top: 10rpx;
		font-size: 22rpx;
		color: #64798d;
		line-height: 1.55;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		-webkit-box-orient: vertical;
	}

	.meta-foot {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12rpx;
		margin-top: 14rpx;
		padding-top: 12rpx;
		border-top: 1rpx solid rgba(89, 145, 174, 0.12);
	}

	.meta-handle {
		flex: 1;
		min-width: 0;
		font-size: 21rpx;
		color: #70859a;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta-cta,
	.meta-foot-stat {
		flex-shrink: 0;
		font-size: 20rpx;
		font-weight: 700;
		color: #2f6f92;
		padding: 6rpx 14rpx;
		border-radius: 999rpx;
		background: rgba(217, 242, 249, 0.82);
		border: 1rpx solid rgba(87, 169, 206, 0.18);
	}

	.meta-badges {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		margin-top: 10rpx;
	}

	.meta-badge {
		display: inline-flex;
		align-items: center;
		height: 40rpx;
		padding: 0 14rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		font-weight: 600;
	}

	.meta-badge--vip {
		background: rgba(255, 238, 196, 0.8);
		color: #9a6b18;
		border: 1rpx solid rgba(238, 184, 73, 0.22);
	}

	.meta-badge--blur {
		background: rgba(255, 230, 218, 0.82);
		color: #b06639;
		border: 1rpx solid rgba(251, 146, 60, 0.22);
	}

	.meta-badge--mode {
		background: rgba(217, 242, 249, 0.82);
		color: #2f6f92;
		border: 1rpx solid rgba(87, 169, 206, 0.18);
	}

	.meta-badge--token {
		background: rgba(218, 245, 237, 0.86);
		color: #277b68;
		border: 1rpx solid rgba(16, 185, 129, 0.18);
	}

	.meta-badge--label {
		background: rgba(255, 230, 238, 0.86);
		color: #b65f83;
		border: 1rpx solid rgba(244, 160, 190, 0.22);
	}

	.empty-tab {
		text-align: center;
		color: #70859a;
		padding: 80rpx;
		font-size: 28rpx;
	}

	.discover-status {
		text-align: center;
		padding: 80rpx 40rpx 48rpx;
		box-sizing: border-box;
	}

	.discover-skeleton {
		padding: 18rpx 28rpx 24rpx;
		display: flex;
		flex-direction: column;
		gap: 24rpx;
	}

	.discover-skeleton-top {
		display: flex;
		gap: 14rpx;
	}

	.discover-skeleton-hero,
	.discover-skeleton-visual,
	.discover-skeleton-line {
		position: relative;
		overflow: hidden;
		background: linear-gradient(90deg, rgba(255, 255, 255, 0.56), rgba(196, 226, 240, 0.6), rgba(255, 255, 255, 0.56));
	}

	.discover-skeleton-hero::after,
	.discover-skeleton-visual::after,
	.discover-skeleton-line::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.72) 50%, transparent 100%);
		transform: translateX(-100%);
		animation: discover-shimmer 1.2s infinite;
	}

	.discover-skeleton-hero {
		flex: 1;
		height: 260rpx;
		border-radius: 22rpx;
	}

	.discover-skeleton-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 20rpx;
	}

	.discover-skeleton-card {
		padding: 14rpx;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.64);
		border: 1rpx solid rgba(255, 255, 255, 0.78);
	}

	.discover-skeleton-visual {
		height: 420rpx;
		border-radius: 18rpx;
	}

	.discover-skeleton-line {
		height: 22rpx;
		border-radius: 999rpx;
		margin-top: 16rpx;
	}

	.discover-skeleton-line--lg {
		width: 72%;
	}

	.discover-skeleton-line--sm {
		width: 48%;
	}

	.discover-status--empty {
		padding-top: 64rpx;
	}

	.discover-status-txt {
		display: block;
		color: #70859a;
		font-size: 28rpx;
		line-height: 1.5;
	}

	.discover-retry {
		margin-top: 32rpx;
		display: inline-block;
		padding: 16rpx 40rpx;
		border-radius: 999rpx;
		background: rgba(217, 242, 249, 0.88);
		color: #2f6f92;
		font-size: 26rpx;
	}

	.empty-feed {
		text-align: center;
		color: #70859a;
		padding: 48rpx 32rpx;
		font-size: 26rpx;
	}

	.list-more {
		padding: 22rpx 32rpx 8rpx;
		text-align: center;
	}

	.list-more-text {
		font-size: 22rpx;
		line-height: 1.6;
		color: rgba(76, 147, 180, 0.84);
	}

.empty-feed--block {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16rpx;
}

.group-preview-card {
	width: 100%;
	max-width: 690rpx;
	padding: 34rpx 30rpx 30rpx;
	border-radius: 30rpx;
	background:
		linear-gradient(145deg, rgba(255, 255, 255, 0.88) 0%, rgba(239, 250, 255, 0.9) 48%, rgba(255, 240, 247, 0.9) 100%),
		radial-gradient(circle at top right, rgba(245, 167, 195, 0.22), transparent 34%);
	border: 1rpx solid rgba(255, 255, 255, 0.86);
	box-shadow:
		0 24rpx 58rpx rgba(73, 112, 137, 0.14),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
}

.group-preview-badge {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	padding: 8rpx 18rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 700;
	letter-spacing: 2rpx;
	color: #b65f83;
	background: rgba(255, 230, 238, 0.82);
	border: 1rpx solid rgba(244, 160, 190, 0.22);
}

.group-preview-points {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 20rpx;
}

.group-preview-point {
	padding: 12rpx 18rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	color: #2f6f92;
	background: rgba(217, 242, 249, 0.82);
	border: 1rpx solid rgba(87, 169, 206, 0.18);
}

.group-preview-action {
	margin-top: 24rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-width: 220rpx;
	padding: 18rpx 30rpx;
	border-radius: 999rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #57a9ce 0%, #89d3e6 52%, #f3a7c1 100%);
	box-shadow: 0 18rpx 34rpx rgba(87, 169, 206, 0.22);
}

.empty-feed-title {
	font-size: 28rpx;
	color: #24445f;
	font-weight: 600;
	margin-top: 18rpx;
}

	.empty-feed-sub {
		font-size: 24rpx;
		color: #70859a;
		line-height: 1.5;
		max-width: 560rpx;
	}

	/* Home clean pass: image-first, low-key surfaces. */
	.main-tabs,
	.card-top {
		background: rgba(255, 255, 255, 0.58);
		border-color: rgba(255, 255, 255, 0.42);
		box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
	}

	.main-pill.on {
		background: #4f93a3;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.16);
	}

	.meta-badges {
		min-height: 44rpx;
		max-height: 88rpx;
		margin-top: 0;
		overflow: hidden;
		align-content: flex-start;
	}

	.meta-badges--empty {
		visibility: hidden;
	}

	.meta-desc {
		flex: 1;
	}

	.meta-foot {
		margin-top: auto;
	}

	/*
	 * Character cards mirrored from JiuGuanSJ/copy (Saki discover).
	 * Light-client rule: no outer black frame; rounded cover only.
	 */
	.g3-inner,
	.card-disc {
		--tier-rim: linear-gradient(145deg, #f3f4f6 0%, #9ca3af 40%, #e5e7eb 60%, #6b7280 100%);
		--tier-ink-a: 156, 163, 175;
		--tier-ink-b: 209, 213, 219;
		background: transparent;
		border: none;
		box-shadow: none;
		overflow: visible;
	}

	.card-tier--vip {
		--tier-rim: linear-gradient(135deg, #e0f2fe 0%, #38bdf8 25%, #60a5fa 50%, #7dd3fc 75%, #bae6fd 100%);
		--tier-ink-a: 56, 189, 248;
		--tier-ink-b: 96, 165, 250;
	}

	.card-tier--svip {
		--tier-rim: linear-gradient(125deg, #f9a8d4 0%, #c084fc 20%, #818cf8 40%, #67e8f9 60%, #fde047 80%, #fb7185 100%);
		--tier-ink-a: 192, 132, 252;
		--tier-ink-b: 249, 168, 212;
	}

	.card-grade {
		position: absolute;
		top: -2rpx;
		left: -2rpx;
		z-index: 8;
		display: inline-flex;
		flex-direction: column;
		align-items: flex-start;
		padding: 12rpx 16rpx 8rpx 10rpx;
		pointer-events: none;
		transform: rotate(-6deg);
		transform-origin: 0 0;
	}

	.card-tier--vip .card-grade {
		transform: rotate(-4deg);
	}

	.card-tier--standard .card-grade,
	.g3-inner:not(.card-tier--vip):not(.card-tier--svip) .card-grade,
	.card-disc:not(.card-tier--vip):not(.card-tier--svip) .card-grade {
		transform: rotate(-3deg);
		top: 2rpx;
		left: 2rpx;
	}

	.card-grade-label {
		font-family: Georgia, 'Times New Roman', 'Noto Serif SC', 'Songti SC', serif;
		font-size: 44rpx;
		font-weight: 900;
		font-style: italic;
		line-height: 0.85;
		letter-spacing: -0.02em;
		text-transform: uppercase;
		color: #e5e7eb;
		text-shadow: 0 2rpx 2rpx rgba(0, 0, 0, 0.75), 0 0 12rpx rgba(var(--tier-ink-a), 0.7);
	}

	.card-grade-caption {
		display: none;
	}

	.card-grade--compact .card-grade-label {
		font-size: 34rpx;
	}

	.card-tier--vip .card-grade-label {
		font-size: 40rpx;
		color: #bae6fd;
	}

	.card-tier--svip .card-grade-label {
		font-size: 48rpx;
		color: #f5d0fe;
	}

	.g3-img:not(.g3-img--blur),
	.card2-bg:not(.card2-bg--blur) {
		filter: none;
		image-rendering: auto;
	}

	.card-disc {
		position: relative;
		display: block;
		width: 346rpx;
		margin: 0 auto;
		height: auto;
		background: transparent !important;
		border: none !important;
		box-shadow: none !important;
	}

	.card-visual {
		position: relative;
		isolation: isolate;
		overflow: hidden;
		border-radius: 36rpx;
		background: transparent !important;
		width: 100%;
		height: 550rpx;
	}

	.g3-inner {
		position: relative;
		isolation: isolate;
		overflow: visible;
		background: transparent;
		width: 216rpx;
		height: 344rpx;
		margin: 0 auto;
	}

	.g3-media {
		position: absolute;
		inset: 0;
		border-radius: 36rpx;
		overflow: hidden;
	}

	.card-visual::before,
	.g3-media::before {
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
	.card-tier--vip .g3-media::before {
		padding: 4rpx;
		animation: saki-grade-shift 4.5s ease infinite;
	}

	.card-tier--svip .card-visual::before,
	.card-tier--svip .g3-media::before {
		padding: 5rpx;
		animation: saki-grade-shift 3.2s ease infinite;
	}

	.card2-bg,
	.g3-img {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
		z-index: 0;
		display: block;
		/* #ifdef H5 */
		object-fit: cover;
		object-position: center top;
		/* #endif */
		transform-origin: center center;
		transition: transform 0.45s ease;
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

	.g3-overlay-top,
	.card-float-top,
	.g3-likes,
	.like-badge {
		display: none;
	}

	.g3-mask,
	.card-visual-copy {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 3;
		display: flex;
		flex-direction: column;
		gap: 6rpx;
		padding: 24rpx 20rpx 18rpx;
		pointer-events: none;
		background: transparent;
	}

	.g3-name,
	.card-visual-title {
		margin: 0;
		color: #fff;
		font-size: 30rpx;
		font-weight: 800;
		line-height: 1.25;
		letter-spacing: 0.01em;
		text-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.55);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.g3-name {
		font-size: 24rpx;
	}

	.g3-sub,
	.card-visual-desc {
		margin: 0;
		min-height: 0;
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

	.g3-sub {
		font-size: 19rpx;
		-webkit-line-clamp: 1;
	}

	.g3-tags,
	.card-float-tags {
		position: static;
		display: flex;
		flex-wrap: wrap;
		gap: 6rpx;
		margin-top: 4rpx;
		left: auto;
		right: auto;
		bottom: auto;
	}

	.mini-tag,
	.float-tag,
	.tone-0,
	.tone-1,
	.tone-2 {
		max-width: 100%;
		padding: 3rpx 12rpx;
		border-radius: 999rpx;
		font-size: 20rpx;
		font-weight: 650;
		color: rgba(255, 244, 214, 0.96);
		background: rgba(0, 0, 0, 0.28);
		border: 1rpx solid rgba(255, 255, 255, 0.16);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		backdrop-filter: blur(6px);
		-webkit-backdrop-filter: blur(6px);
	}

	.g3-foot,
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

	.g3-foot-handle,
	.card-inline-handle {
		flex: 1;
		min-width: 0;
		font-size: 20rpx;
		font-weight: 650;
		color: rgba(255, 255, 255, 0.78);
		text-shadow: 0 1rpx 6rpx rgba(0, 0, 0, 0.5);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.g3-foot-heat,
	.card-inline-heat {
		flex-shrink: 0;
		display: inline-flex;
		align-items: center;
		gap: 6rpx;
		font-size: 20rpx;
		font-weight: 700;
		color: rgba(255, 214, 120, 0.92);
		text-shadow: 0 1rpx 6rpx rgba(0, 0, 0, 0.5);
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

	.card-meta {
		display: none !important;
		height: 0 !important;
		min-height: 0 !important;
		padding: 0 !important;
		margin: 0 !important;
		overflow: hidden !important;
		background: transparent !important;
	}

	/* #ifdef H5 */
	.card-grade-label {
		color: transparent;
		background-image: conic-gradient(from 0deg at 50% 50%, #e5e7eb, #9ca3af, #f3f4f6, #d1d5db, #e5e7eb);
		background-size: 300% 300%;
		-webkit-background-clip: text;
		background-clip: text;
		-webkit-text-fill-color: transparent;
		filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.8)) drop-shadow(0 0 6px rgba(var(--tier-ink-a), 0.75)) drop-shadow(0 0 12px rgba(var(--tier-ink-b), 0.45));
		text-shadow: none;
		animation: saki-holo-rotate 12s steps(180, end) infinite;
	}

	.card-tier--vip .card-grade-label {
		background-image: conic-gradient(from 0deg at 50% 50%, #c8e0ff, #90c8ff, #e0f0ff, #b0d4ff, #d8ecff, #c8e0ff);
	}

	.card-tier--svip .card-grade-label {
		background-image: conic-gradient(from 0deg at 50% 50%, #d8c8ff, #ffc8e8, #b5f0ff, #fff0c8, #ffc8d8, #d8c8ff);
	}
	/* #endif */

	/* Keep full-resolution covers crisp during pointer interaction. */
	.g3-inner:hover .g3-img:not(.g3-img--blur),
	.g3-inner--hover .g3-img:not(.g3-img--blur),
	.g3-inner:active .g3-img:not(.g3-img--blur),
	.card-disc:hover .card2-bg:not(.card2-bg--blur),
	.card-disc--hover .card2-bg:not(.card2-bg--blur),
	.card-disc:active .card2-bg:not(.card2-bg--blur) {
		transform: scale(1.04);
	}

	.g3-inner:active,
	.card-disc:active {
		transform: scale(0.975);
	}

	@keyframes saki-grade-shift {
		0% { background-position: 0% 50%; }
		50% { background-position: 100% 50%; }
		100% { background-position: 0% 50%; }
	}

	@keyframes saki-holo-rotate {
		to { background-position: 100% 50%; }
	}

	/* #ifdef H5 */
	@media (prefers-reduced-motion: reduce) {
		.card-visual::before,
		.g3-media::before,
		.card-grade-label {
			animation: none;
		}
	}
	/* #endif */

	@keyframes discover-shimmer {
		100% {
			transform: translateX(100%);
		}
	}
</style>
