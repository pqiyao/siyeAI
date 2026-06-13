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
					<image class="brand-logo" src="/static/logo.png" mode="aspectFill" />
					<view class="promo-pill">{{ discoverUi.promo }}</view>
					<view class="brand-actions">
						<view class="icon-btn icon-btn--mail" @tap="goSystemMsg">
							<text class="mail-ico">✉</text>
							<view v-if="noticeUnread > 0" class="mail-badge">{{ noticeUnread > 99 ? '99+' : noticeUnread }}</view>
						</view>
						<view class="lang-chip" @tap.stop="goLanguage">A/文</view>
						<view class="icon-btn icon-btn--more" @tap="onMoreMenu">
							<text class="icon-more">...</text>
						</view>
					</view>
				</view>

				<view class="search-row" @tap="toastSearch">
					<image class="search-ico" src="/static/cha.png" mode="widthFix" />
					<text class="search-ph" :class="{ 'search-ph--active': !!searchKeyword }">{{ searchDisplayText }}</text>
					<view class="search-row-right">
						<text v-if="searchKeyword" class="search-clear" @tap.stop="clearSearchKeyword">{{ discoverUi.clear }}</text>
						<text v-else class="search-enter">{{ discoverUi.search }}</text>
					</view>
				</view>

				<view v-if="showSocialEntry" class="social-entry-card">
					<view class="social-entry-copy" @tap="openCommunityFeed">
						<view class="social-entry-mark">
							<text class="cuIcon-communityfill"></text>
						</view>
						<text class="social-entry-kicker">四叶社区</text>
						<text class="social-entry-title">动态、同好、真人私信</text>
						<text class="social-entry-desc">发布文字图片，关注喜欢的人，再从私信开始聊天</text>
					</view>
					<view class="social-entry-actions">
						<view class="social-entry-btn social-entry-btn--primary" @tap.stop="openCommunityFeed">
							<text class="cuIcon-discoverfill"></text>
							<text>进入</text>
						</view>
						<view v-if="showSocialChatEntry" class="social-entry-btn" @tap.stop="openCommunityChatList">
							<text class="cuIcon-messagefill"></text>
							<text>私信</text>
						</view>
						<view v-if="socialFeatureConfig.postCreateEnabled" class="social-entry-btn" @tap.stop="openCommunityPostCreate">
							<text class="cuIcon-add"></text>
							<text>发布</text>
						</view>
					</view>
				</view>

				<scroll-view scroll-x class="feed-tabs" :show-scrollbar="false" enable-flex>
					<view class="feed-tabs-inner">
						<view
							v-for="(ft, i) in feedTabList"
							:key="'f' + i"
							class="feed-pill"
							:class="{ on: feedTab === i }"
							@tap="setFeedTab(i)"
						>
							<text>{{ ft.label }}</text>
							<view v-if="ft.dot" class="feed-dot" />
						</view>
					</view>
				</scroll-view>

				<view v-if="noticeBannerVisible" class="notice-banner" @tap="openNoticeBanner">
					<view class="notice-banner-icon">i</view>
					<view class="notice-banner-copy">
						<view class="notice-banner-head">
							<text class="notice-banner-mark">公告</text>
							<text class="notice-banner-title">{{ noticeBanner.title }}</text>
						</view>
						<text class="notice-banner-desc">{{ noticeBanner.content }}</text>
					</view>
					<text class="notice-banner-close" @tap.stop="dismissNoticeBanner">×</text>
				</view>

				<view v-if="isJgDiscover" class="tag-filter-row">
					<view class="tag-filter-head">
						<view class="tag-filter-head-left">
							<text class="tag-filter-title">{{ discoverUi.themeTitle }}</text>
						</view>
						<text v-if="selectedTag || selectedGameplay" class="tag-filter-reset" @tap="clearDiscoverFilters">{{ discoverUi.clear }}</text>
					</view>
					<view class="tag-filter-bar">
						<scroll-view scroll-x class="tag-scroll" :show-scrollbar="false" enable-flex>
						<view class="tag-inner">
							<view
								class="tag-chip"
								:class="{ on: !selectedTag && !selectedGameplay }"
								@tap="clearDiscoverFilters"
							>{{ discoverUi.all }}</view>
							<view
								v-for="(tg, ti) in discoverVisibleTags"
								:key="'tg' + ti + '_' + tagOptionValue(tg)"
								class="tag-chip"
								:class="{ on: selectedTag === tagOptionValue(tg) }"
								@tap="toggleDiscoverTag(tagOptionValue(tg))"
							>{{ displayDiscoverTag(tg) }}</view>
							<view
								v-if="hasDiscoverTagOverflow"
								class="tag-chip tag-chip--more"
								@tap="openDiscoverTagPopup"
							>
								{{ discoverUi.moreFilters }}
								<text class="tag-chip-count">+{{ discoverHiddenTagCount }}</text>
							</view>
							<view
								class="tag-chip tag-chip--ghost"
								:class="{ on: selectedGameplay === discoverPopupText.gameplayLabel }"
								@tap="toggleGameplayFilter(discoverPopupText.gameplayLabel)"
							>{{ discoverPopupText.gameplayLabel }}</view>
						</view>
						</scroll-view>
					</view>
					<view class="tag-filter-tip-row">
						<view v-if="discoverTagLoading && !discoverAllTags.length" class="tag-filter-tip">
							{{ discoverUi.tagLoading }}
						</view>
						<view
							v-else-if="discoverTagHint"
							class="tag-filter-tip"
							:class="{ 'tag-filter-tip--warn': !discoverAllTags.length }"
						>
							{{ discoverTagHint }}
						</view>
						<view v-else-if="hasDiscoverTagOverflow" class="tag-filter-tip">
							{{ discoverUi.searchHint }}
						</view>
					</view>
				</view>

</view>

			<view v-show="swiperCurrent === 0" class="page-box">
				<view v-if="isJgDiscover" class="illustration-entry-card" @tap="openIllustrationSite">
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
								:class="{ 'g3-inner--hover': hoverTopPickId === c.id }"
								@mouseenter="setDiscoverHover('top', c.id)"
								@mouseleave="clearDiscoverHover('top')"
								@touchstart="setDiscoverHover('top', c.id)"
								@touchend="clearDiscoverHover('top')"
								@touchcancel="clearDiscoverHover('top')"
							>
								<image
									class="g3-img"
									:class="{ 'g3-img--blur': isPreviewBlurActive(c) }"
									:src="charAvatarUrl(c)"
									mode="aspectFill"
									lazy-load
								></image>
								<view
									v-if="isPreviewBlurActive(c)"
									class="preview-blur-surface preview-blur-surface--compact"
									:style="blurSurfaceStyle(charAvatarUrl(c), 'cover')"
								></view>
								<view v-if="isPreviewBlurActive(c)" class="preview-blur-layer preview-blur-layer--compact">
									<view class="preview-blur-pill">{{ previewBlurBadgeText(c) }}</view>
								</view>
								<view class="g3-overlay-top">
									<text class="g3-handle">{{ displayHandle(c) }}</text>
									<view class="g3-likes">
										<text class="heart">❤</text>
										<text>{{ formatLikes(c.like_count) }}</text>
									</view>
								</view>
								<view class="g3-tags" v-if="safeLabels(c, 2).length">
									<text
										v-for="(lb, li) in safeLabels(c, 2)"
										:key="li"
										class="mini-tag"
										:class="'tone-' + (li % 3)"
									>{{ lb.code }}</text>
								</view>
								<view class="g3-mask">
									<view class="g3-name">{{ c.nickname }}</view>
									<view class="g3-sub">{{ cardHeroCopy(c) }}</view>
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
						<view class="group-preview-action" @tap="openCommunityFeed">{{ discoverUi.groupAction }}</view>
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
							:class="{ 'card-disc--hover': hoverGridId === c.id }"
							@mouseenter="setDiscoverHover('grid', c.id)"
							@mouseleave="clearDiscoverHover('grid')"
							@touchstart="setDiscoverHover('grid', c.id)"
							@touchend="clearDiscoverHover('grid')"
							@touchcancel="clearDiscoverHover('grid')"
						>
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

								<view class="card-float-top">
									<text class="hdl">{{ displayHandle(c) }}</text>
									<view class="like-badge">
										<text class="heart sm">❤</text>
										<text>{{ formatLikes(c.like_count) }}</text>
									</view>
								</view>
								<view class="card-float-tags" v-if="safeLabels(c, 3).length">
									<text
										v-for="(lb, li) in safeLabels(c, 3)"
										:key="li"
										class="float-tag"
										:class="'tone-' + (li % 3)"
									>{{ lb.code }}</text>
								</view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ c.nickname }}</text>
									<text class="card-visual-desc">{{ cardHeroCopy(c) }}</text>
								</view>
							</view>
							<view class="card-meta">
								<view class="meta-badges" :class="{ 'meta-badges--empty': !cardMetaBadges(c).length }">
									<text
										v-for="(badge, bi) in cardMetaBadges(c)"
										:key="'badge_main_' + bi"
										class="meta-badge"
										:class="'meta-badge--' + badge.tone"
									>{{ badge.text }}</text>
								</view>
								<text class="meta-desc">{{ cardPreview(c) }}</text>
								<view class="meta-foot">
									<text class="meta-handle">{{ displayHandle(c) }}</text>
									<text class="meta-cta" @tap.stop="handleDiscoverCardAction(c)">{{ c.unlocked === false ? discoverUi.openVip : discoverUi.viewDetail }}</text>
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
							:class="{ 'card-disc--hover': hoverGridId === c.id }"
							@mouseenter="setDiscoverHover('grid', c.id)"
							@mouseleave="clearDiscoverHover('grid')"
							@touchstart="setDiscoverHover('grid', c.id)"
							@touchend="clearDiscoverHover('grid')"
							@touchcancel="clearDiscoverHover('grid')"
						>
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
								<view class="card-float-top">
									<text class="hdl">{{ displayHandle(c) }}</text>
									<view class="like-badge">
										<text class="heart sm">❤</text>
										<text>{{ formatLikes(c.like_count) }}</text>
									</view>
								</view>
								<view class="card-float-tags" v-if="safeLabels(c, 3).length">
									<text
										v-for="(lb, li) in safeLabels(c, 3)"
										:key="li"
										class="float-tag"
										:class="'tone-' + (li % 3)"
									>{{ lb.code }}</text>
								</view>
								<view class="card-visual-copy">
									<text class="card-visual-title">{{ c.nickname }}</text>
									<text class="card-visual-desc">{{ cardHeroCopy(c) }}</text>
								</view>
							</view>
							<view class="card-meta">
								<view class="meta-badges" :class="{ 'meta-badges--empty': !cardMetaBadges(c).length }">
									<text
										v-for="(badge, bi) in cardMetaBadges(c)"
										:key="'badge_unlock_' + bi"
										class="meta-badge"
										:class="'meta-badge--' + badge.tone"
									>{{ badge.text }}</text>
								</view>
								<text class="meta-desc">{{ cardPreview(c) }}</text>
								<view class="meta-foot">
									<text class="meta-handle">{{ displayHandle(c) }}</text>
									<text class="meta-cta" @tap.stop="handleDiscoverCardAction(c)">{{ c.unlocked === false ? discoverUi.openVip : discoverUi.viewDetail }}</text>
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
						<text class="discover-menu-title">{{ discoverUi.moreFilters }}</text>
						<text class="discover-menu-close" @tap="discoverMenuShow = false">×</text>
					</view>
					<view class="discover-menu-item" @tap="handleDiscoverMenu('notice')">
						<view class="discover-menu-item-main">
							<text class="discover-menu-item-title">{{ discoverUi.menuNoticeTitle }}</text>
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
					<view class="discover-menu-item" @tap="handleDiscoverMenu('language')">
						<view class="discover-menu-item-main">
							<text class="discover-menu-item-title">{{ discoverUi.menuLanguageTitle }}</text>
							<text class="discover-menu-item-desc">{{ discoverUi.menuLanguageDesc }}</text>
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
	import { applyTavernTabBarLabels, syncTavernTabBar, syncTavernInboxBadge } from '@/common/tavernTabBar.js';
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
				socialFeatureConfig: require('@/common/tavernApi.js').getSocialFeatureConfig(),
				noticeExposureLoading: false
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
			showSocialEntry() {
				const config = this.socialFeatureConfig || {};
				return config.communityEnabled !== false && config.communityEntryVisible !== false;
			},
			showSocialChatEntry() {
				const config = this.socialFeatureConfig || {};
				return config.chatEnabled !== false && config.chatEntryVisible !== false;
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
			this.applyDiscoverFilterFromStorage();
			this.allChars = [];
			this.syncDiscoverVisibleCounts();
			this.discoverLoading = true;
			this.discoverLoadError = '';
			this.pickTop();
			this.tryLoadDiscoverTags();
			this.loadSocialFeatureConfig(true);
			this.tryLoadCharsFromBackend({ force: true });
		},
		onShow() {
			applyTavernTabBarLabels(this.allText, this);
			syncTavernTabBar(this, 'pages/index/index', this.allText);
			this.refreshNoticeUnread();
			this.loadNoticeExposure();
			this.loadSocialFeatureConfig(true);
			this.applyLatestInteractionPatch();
			this.applyDiscoverFilterFromStorage();
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
			const u = uni.getStorageSync('user');
			if (u && u.token) {
				this.util
					.request('user/user_info', { token: u.token })
					.then((res) => {
						if (res && res.need_edit === 0) {
							uni.reLaunch({ url: '/pages/perfect/perfect' });
						}
					})
					.catch(() => {});
			}
		},
		methods: {
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
			refreshNoticeUnread() {
				if (!this.isJgDiscover) {
					this.noticeUnread = 0;
					syncTavernInboxBadge(this, 0);
					return Promise.resolve(0);
				}
				const tavernApi = require('@/common/tavernApi.js');
				return tavernNoticeState.fetchUnreadState(tavernApi, 30)
					.then((state) => {
						this.noticeUnread = state.unreadCount;
						try {
							this.$store.commit('setUnreadTotal', state.unreadCount);
						} catch (e) {}
						syncTavernInboxBadge(this, state.unreadCount);
						return state.unreadCount;
					})
					.catch(() => {
						this.noticeUnread = 0;
						try {
							this.$store.commit('setUnreadTotal', 0);
						} catch (e) {}
						syncTavernInboxBadge(this, 0);
						return 0;
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
				return NOTICE_BANNER_DISMISSED_KEY + id + '_' + day;
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
					return !!uni.getStorageSync(NOTICE_POPUP_ACK_KEY + String(item && item.id));
				} catch (e) {
					return false;
				}
			},
			loadNoticeExposure() {
				if (!this.isJgDiscover || this.noticeExposureLoading) {
					return Promise.resolve();
				}
				if (Number(this.noticeUnread) <= 0) {
					this.noticeBanner = null;
					this.noticePopup = null;
					this.noticePopupVisible = false;
					return Promise.resolve();
				}
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi || typeof tavernApi.fetchAppNotices !== 'function') {
					return Promise.resolve();
				}
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
						uni.setStorageSync(NOTICE_POPUP_ACK_KEY + String(this.noticePopup.id), 1);
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
					uni.setStorageSync(NOTICE_POPUP_ACK_KEY + String(item.id), 1);
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
					try {
						this.$store.commit('setUnreadTotal', count);
					} catch (e) {}
					syncTavernInboxBadge(this, count);
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
				const u = c && (c.cover_thumb || c.avatar_thumb || c.cover || c.avatar);
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
				const raw = c.bio || c.persona || c.scenario || c.tagline || '';
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
				const raw = c.tagline || c.bio || c.persona || c.scenario || '';
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
			loadSocialFeatureConfig(force) {
				const tavernApi = require('@/common/tavernApi.js');
				return tavernApi.fetchSocialFeatureConfig(!!force)
					.then((config) => {
						this.socialFeatureConfig = config || tavernApi.getSocialFeatureConfig();
						return this.socialFeatureConfig;
					})
					.catch(() => {
						this.socialFeatureConfig = tavernApi.getSocialFeatureConfig();
						return this.socialFeatureConfig;
					});
			},
			getSocialFeatureConfig() {
				const tavernApi = require('@/common/tavernApi.js');
				return this.socialFeatureConfig || tavernApi.getSocialFeatureConfig();
			},
			openCommunityFeed() {
				const config = this.getSocialFeatureConfig();
				if (config.communityEnabled === false || config.communityEntryVisible === false) {
					uni.showToast({ title: '社区暂未开放', icon: 'none' });
					return;
				}
				uni.navigateTo({ url: '/pages/social/feed' });
			},
			openCommunityChatList() {
				const config = this.getSocialFeatureConfig();
				if (config.chatEnabled === false || config.chatEntryVisible === false) {
					uni.showToast({ title: '真人聊天暂未开放', icon: 'none' });
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser()) {
					uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/chatList') });
					return;
				}
				uni.navigateTo({ url: '/pages/social/chatList' });
			},
			openCommunityPostCreate() {
				const config = this.getSocialFeatureConfig();
				if (config.communityEnabled === false || config.postCreateEnabled === false || config.postPublishMode === 'closed') {
					uni.showToast({ title: '当前已关闭动态发布', icon: 'none' });
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.hasLoggedInUser()) {
					uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/postCreate') });
					return;
				}
				uni.navigateTo({ url: '/pages/social/postCreate' });
			},
			openIllustrationSite() {
				const target = '/illustration/';
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

			goLanguage() {
				this.util.urlTo('/pages/user/language');
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
					return;
				}
				if (action === 'language') {
					this.goLanguage();
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
		padding-left: 28rpx;
		padding-right: 28rpx;
		padding-bottom: 18rpx;
		background: transparent;
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.18);
		box-shadow: none;
	}

	.disc-header::after {
		display: none;
	}

	.icon-more {
		font-size: 34rpx;
		color: #497089;
		line-height: 1;
		padding-bottom: 10rpx;
	}

	.brand-row {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: 16rpx;
		margin-top: 6rpx;
	}

	.brand-logo {
		width: 76rpx;
		height: 76rpx;
		border-radius: 50%;
		flex-shrink: 0;
		background: rgba(255, 255, 255, 0.5);
		border: 3rpx solid rgba(255, 255, 255, 0.58);
		box-shadow: 0 10rpx 22rpx rgba(38, 57, 77, 0.1);
	}

	.promo-pill {
		flex: 1;
		font-size: 30rpx;
		font-weight: 800;
		letter-spacing: 0;
		color: #26394d;
		padding: 10rpx 0;
		background: transparent;
		border: 0;
		box-shadow: none;
		text-align: left;
		max-width: 300rpx;
	}

	.brand-actions {
		display: flex;
		align-items: center;
		gap: 12rpx;
		flex-shrink: 0;
	}

	.icon-btn {
		width: 64rpx;
		height: 64rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.44);
		border: 1rpx solid rgba(255, 255, 255, 0.42);
		box-shadow: 0 10rpx 22rpx rgba(38, 57, 77, 0.08);
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.mail-badge {
		position: absolute;
		top: -8rpx;
		right: -8rpx;
		min-width: 34rpx;
		height: 34rpx;
		padding: 0 8rpx;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 999rpx;
		background: linear-gradient(135deg, #ff8fb1 0%, #ffb5c9 100%);
		color: #fff;
		font-size: 18rpx;
		font-weight: 700;
		line-height: 1;
		box-shadow: 0 8rpx 18rpx rgba(244, 131, 162, 0.26);
		border: 2rpx solid rgba(255, 255, 255, 0.92);
	}

	.mail-ico {
		font-size: 0;
		color: transparent;
		position: relative;
	}

	.mail-ico::after {
		content: '✉';
		color: #497089;
		font-size: 28rpx;
	}

	.lang-chip {
		padding: 8rpx 18rpx;
		font-size: 22rpx;
		font-weight: 600;
		color: #3f6f7f;
		background: rgba(255, 255, 255, 0.44);
		border-radius: 24rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.42);
		box-shadow: 0 10rpx 22rpx rgba(38, 57, 77, 0.08);
		position: relative;
	}

	.search-row {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		margin-top: 20rpx;
		padding: 0 20rpx 0 24rpx;
		min-height: 80rpx;
		border-radius: 36rpx;
		background: rgba(255, 255, 255, 0.56);
		border: 1rpx solid rgba(255, 255, 255, 0.44);
		box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
	}

	.search-ico {
		width: 30rpx;
		height: 30rpx;
		margin-right: 16rpx;
		opacity: 0.45;
	}

	.search-ph {
		flex: 1;
		font-size: 26rpx;
		line-height: 1.45;
		color: #70859a;
		padding: 18rpx 0;
	}

	.search-ph--active {
		color: #24445f;
		font-weight: 600;
	}

	.search-row-right {
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
		margin-left: 16rpx;
	}

	.search-clear,
	.search-enter {
		padding: 10rpx 18rpx;
		border-radius: 999rpx;
		font-size: 22rpx;
		font-weight: 600;
	}

	.search-clear {
		color: #c85f85;
		background: rgba(255, 214, 226, 0.62);
		border: 1rpx solid rgba(255, 164, 193, 0.34);
	}

	.search-enter {
		color: #2f6f92;
		background: rgba(206, 238, 247, 0.72);
		border: 1rpx solid rgba(107, 188, 214, 0.24);
	}

	.social-entry-card {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: 20rpx;
		margin-top: 18rpx;
		padding: 22rpx 22rpx 22rpx 26rpx;
		border-radius: 28rpx;
		background:
			linear-gradient(135deg, rgba(255, 255, 255, 0.82) 0%, rgba(234, 249, 253, 0.86) 52%, rgba(255, 239, 247, 0.86) 100%);
		border: 1rpx solid rgba(255, 255, 255, 0.76);
		box-shadow: 0 16rpx 34rpx rgba(38, 57, 77, 0.1);
	}

	.social-entry-card::before {
		content: '';
		position: absolute;
		left: 0;
		top: 18rpx;
		bottom: 18rpx;
		width: 7rpx;
		border-radius: 0 8rpx 8rpx 0;
		background: linear-gradient(180deg, #2e6b4d 0%, #e36f5f 100%);
	}

	.social-entry-copy {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.social-entry-mark {
		width: 58rpx;
		height: 58rpx;
		border-radius: 8rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(46, 107, 77, 0.1);
		color: #2e6b4d;
		font-size: 32rpx;
	}

	.social-entry-kicker {
		font-size: 22rpx;
		font-weight: 900;
		color: #2e6b4d;
	}

	.social-entry-title {
		font-size: 30rpx;
		line-height: 1.3;
		font-weight: 900;
		color: #1f2933;
	}

	.social-entry-desc {
		font-size: 23rpx;
		line-height: 1.45;
		color: rgba(55, 74, 62, 0.76);
	}

	.social-entry-actions {
		width: 154rpx;
		display: flex;
		flex-direction: column;
		gap: 10rpx;
		flex-shrink: 0;
	}

	.social-entry-btn {
		height: 54rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6rpx;
		border-radius: 8rpx;
		font-size: 23rpx;
		font-weight: 900;
		color: #2e6b4d;
		background: rgba(255, 255, 255, 0.74);
		border: 1rpx solid rgba(46, 107, 77, 0.14);
	}

	.social-entry-btn--primary {
		color: #fff;
		background: #2e6b4d;
		border-color: rgba(255, 255, 255, 0.74);
		box-shadow: 0 12rpx 24rpx rgba(46, 107, 77, 0.18);
	}

	.feed-tabs {
		position: relative;
		z-index: 1;
		width: 100%;
		margin-top: 20rpx;
		white-space: nowrap;
	}

	.feed-tabs-inner {
		display: inline-flex;
		flex-direction: row;
		gap: 16rpx;
		padding: 4rpx 0;
	}

	.feed-pill {
		position: relative;
		padding: 14rpx 28rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.54);
		border: 1rpx solid rgba(255, 255, 255, 0.74);
		font-size: 26rpx;
		color: #557089;
		flex-shrink: 0;
	}

	.feed-pill.on {
		color: #fff;
		font-weight: 600;
		background: linear-gradient(135deg, #67b7d6 0%, #8ecfe3 48%, #f5a7c3 100%);
		border-color: rgba(255, 255, 255, 0.82);
		box-shadow: 0 14rpx 24rpx rgba(93, 174, 207, 0.22);
	}

	.feed-dot {
		position: absolute;
		top: 8rpx;
		right: 12rpx;
		width: 12rpx;
		height: 12rpx;
		border-radius: 50%;
		background: #f43f5e;
	}

	.notice-banner {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: 14rpx;
		margin-top: 14rpx;
		padding: 14rpx 58rpx 14rpx 16rpx;
		border-radius: 18rpx;
		background:
			linear-gradient(135deg, rgba(255, 255, 255, 0.82) 0%, rgba(236, 249, 253, 0.72) 100%);
		border: 1rpx solid rgba(124, 183, 207, 0.2);
		box-shadow: 0 10rpx 24rpx rgba(66, 112, 139, 0.08);
		box-sizing: border-box;
	}

	.notice-banner:active {
		transform: scale(0.995);
	}

	.notice-banner-icon {
		flex-shrink: 0;
		width: 42rpx;
		height: 42rpx;
		border-radius: 50%;
		background: rgba(83, 161, 194, 0.14);
		color: #2e86a8;
		font-size: 25rpx;
		font-weight: 900;
		text-align: center;
		line-height: 42rpx;
		font-style: italic;
	}

	.notice-banner-head {
		display: flex;
		align-items: center;
		gap: 10rpx;
		min-width: 0;
	}

	.notice-banner-mark {
		display: inline-block;
		flex-shrink: 0;
		height: 32rpx;
		padding: 0 12rpx;
		border-radius: 8rpx;
		background: rgba(255, 255, 255, 0.7);
		color: #3e8dab;
		font-size: 19rpx;
		font-weight: 800;
		line-height: 32rpx;
	}

	.notice-banner-copy {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.notice-banner-title {
		display: block;
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.notice-banner-desc {
		display: block;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.notice-banner-title {
		font-size: 24rpx;
		font-weight: 800;
		color: #254962;
		line-height: 1.25;
	}

	.notice-banner-desc {
		font-size: 20rpx;
		color: #72899b;
		line-height: 1.35;
	}

	.notice-banner-close {
		position: absolute;
		right: 12rpx;
		top: 50%;
		width: 40rpx;
		height: 40rpx;
		margin-top: -20rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.58);
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
	}

	.tag-filter-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 6rpx 10rpx;
	}

	.tag-filter-head-left {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.tag-filter-title {
		font-size: 24rpx;
		font-weight: 700;
		color: #24445f;
		letter-spacing: 1rpx;
	}

	.tag-filter-meta {
		font-size: 20rpx;
		color: #7c89a3;
	}

	.tag-filter-active {
		font-size: 20rpx;
		color: #2f6f92;
		padding: 4rpx 12rpx;
		border-radius: 999rpx;
		background: rgba(217, 242, 249, 0.82);
		border: 1rpx solid rgba(87, 169, 206, 0.18);
	}

	.tag-filter-reset {
		font-size: 22rpx;
		color: #4c93b4;
	}

	.tag-filter-bar {
		padding: 12rpx 14rpx 14rpx;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.42);
		border: 1rpx solid rgba(255, 255, 255, 0.68);
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
	}

	.tag-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.tag-inner {
		display: inline-flex;
		flex-direction: row;
		gap: 12rpx;
		padding: 2rpx 2rpx 6rpx;
		min-width: 100%;
	}

	.tag-chip {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 58rpx;
		padding: 0 24rpx;
		border-radius: 999rpx;
		font-size: 24rpx;
		color: #5d7289;
		background: rgba(255, 255, 255, 0.76);
		flex-shrink: 0;
		border: 1rpx solid rgba(122, 181, 209, 0.16);
	}

	.tag-chip.on {
		color: #fff;
		font-weight: 700;
		background: linear-gradient(135deg, #57a9ce 0%, #7fd0e4 54%, #f3a7c1 100%);
		border-color: rgba(255, 255, 255, 0.72);
		box-shadow: 0 10rpx 24rpx rgba(87, 169, 206, 0.2);
	}

	.tag-chip--more {
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
		background: rgba(232, 246, 251, 0.82);
		color: #3d7f9e;
		border-color: rgba(87, 169, 206, 0.22);
	}

	.tag-chip-count {
		font-size: 20rpx;
		opacity: 0.78;
	}

	.tag-chip--ghost {
		background: rgba(255, 240, 247, 0.86);
		color: #bd6d91;
	}

	.tag-filter-tip-row {
		display: flex;
		align-items: center;
		min-height: 34rpx;
		padding: 10rpx 8rpx 0;
	}

	.tag-filter-tip {
		font-size: 21rpx;
		color: #75889a;
		line-height: 1.55;
	}

	.tag-filter-tip--warn {
		color: #f9a8d4;
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
		height: 386rpx;
		margin: 0 auto;
		border-radius: 22rpx;
		overflow: hidden;
		background:
			radial-gradient(circle at top, rgba(255, 255, 255, 0.28), transparent 26%),
			linear-gradient(180deg, rgba(237, 248, 253, 0.96) 0%, rgba(255, 246, 250, 0.98) 100%);
		box-shadow:
			0 18rpx 34rpx rgba(72, 112, 142, 0.16),
			0 6rpx 16rpx rgba(72, 112, 142, 0.1);
		transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease, filter 0.22s ease;
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
		background: rgba(87, 169, 206, 0.66);
		font-size: 20rpx;
		color: #fff;
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
		width: 352rpx;
		margin: 0 auto;
		border-radius: 24rpx;
		overflow: hidden;
		background: rgba(255, 255, 255, 0.84);
		border: 1rpx solid rgba(255, 255, 255, 0.88);
		box-shadow:
			0 20rpx 42rpx rgba(73, 112, 137, 0.13),
			0 8rpx 18rpx rgba(73, 112, 137, 0.08);
		transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease, filter 0.24s ease;
		will-change: transform;
	}

	.card-visual {
		position: relative;
		width: 100%;
		height: 592rpx;
		overflow: hidden;
		background:
			radial-gradient(circle at top, rgba(255, 255, 255, 0.24), transparent 28%),
			linear-gradient(180deg, rgba(232, 246, 251, 0.78) 0%, rgba(255, 246, 250, 0.98) 100%);
	}

	.card-visual::after {
		content: '';
		position: absolute;
		inset: 0;
		z-index: 1;
		background:
			linear-gradient(180deg, rgba(21, 42, 58, 0.01) 0%, rgba(21, 42, 58, 0.02) 28%, rgba(21, 42, 58, 0.16) 72%, rgba(21, 42, 58, 0.62) 100%);
		pointer-events: none;
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

	.g3-inner:hover .g3-img,
	.g3-inner--hover .g3-img,
	.g3-inner:active .g3-img {
		transform: translateY(-34rpx) scale(1.2);
		filter: brightness(1.1) saturate(1.12);
	}

	.g3-inner:hover .g3-img--blur,
	.g3-inner--hover .g3-img--blur,
	.g3-inner:active .g3-img--blur {
		filter: blur(18rpx) scale(1.18) brightness(0.76);
	}

	.g3-inner:active {
		filter: saturate(1.04) brightness(1.02);
	}

	.card-disc:hover .card2-bg,
	.card-disc--hover .card2-bg,
	.card-disc:active .card2-bg {
		transform: translateY(-36rpx) scale(1.2);
		filter: brightness(1.1) saturate(1.12);
	}

	.card-disc:hover .card2-bg--blur,
	.card-disc--hover .card2-bg--blur,
	.card-disc:active .card2-bg--blur {
		filter: blur(18rpx) scale(1.18) brightness(0.74);
	}

	.card-disc:active {
		border-color: rgba(220, 181, 255, 0.16);
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
		background: rgba(87, 169, 206, 0.66);
		font-size: 22rpx;
		color: #fff;
		backdrop-filter: blur(8px);
		border: 1rpx solid rgba(255, 255, 255, 0.1);
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
		left: 10rpx;
		right: 10rpx;
		bottom: 204rpx;
		z-index: 5;
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		pointer-events: none;
		transition: transform 0.26s ease;
	}

	.card-visual-copy {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 5;
		padding: 176rpx 20rpx 24rpx;
		background: linear-gradient(180deg, rgba(21, 42, 58, 0) 0%, rgba(21, 42, 58, 0.86) 100%);
		transition: transform 0.24s ease;
	}

	.card-visual-title {
		display: block;
		font-size: 36rpx;
		font-weight: 800;
		color: #fff;
		line-height: 1.15;
	}

	.card-visual-desc {
		display: block;
		margin-top: 10rpx;
		font-size: 22rpx;
		line-height: 1.55;
		color: rgba(241, 245, 249, 0.82);
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
		color: #fff;
		border: 1rpx solid rgba(255, 255, 255, 0.16);
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
		padding: 22rpx 18rpx 24rpx;
		min-height: 188rpx;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 252, 255, 0.96) 100%);
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
	.feed-pill,
	.tag-chip,
	.main-tabs,
	.tag-filter-bar,
	.card-top,
	.card-disc,
	.g3-inner {
		background: rgba(255, 255, 255, 0.58);
		border-color: rgba(255, 255, 255, 0.42);
		box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
	}

	.feed-pill.on,
	.tag-chip.on,
	.main-pill.on {
		background: #4f93a3;
		box-shadow: 0 12rpx 24rpx rgba(48, 103, 117, 0.16);
	}

	.card-meta {
		background: rgba(255, 255, 255, 0.62);
	}

	/* Keep card rows aligned even when tag counts differ. */
	.card-disc {
		display: flex;
		flex-direction: column;
	}

	.card-visual {
		flex-shrink: 0;
	}

	.card-meta {
		height: 244rpx;
		min-height: 244rpx;
		display: flex;
		flex-direction: column;
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

	/* Community entry: a quiet doorway instead of another chunky card. */
	.social-entry-card {
		margin-top: 14rpx;
		padding: 18rpx 0 16rpx;
		border-radius: 0;
		background: transparent;
		border: 0;
		border-top: 1rpx solid rgba(36, 75, 102, 0.08);
		border-bottom: 1rpx solid rgba(36, 75, 102, 0.08);
		box-shadow: none;
		gap: 18rpx;
	}

	.social-entry-card::before {
		display: none;
	}

	.social-entry-copy {
		position: relative;
		gap: 4rpx;
		padding-left: 64rpx;
	}

	.social-entry-mark {
		position: absolute;
		left: 0;
		top: 50%;
		width: 48rpx;
		height: 48rpx;
		border-radius: 50%;
		transform: translateY(-50%);
		background: rgba(46, 107, 77, 0.1);
		color: #2e6b4d;
		font-size: 28rpx;
	}

	.social-entry-kicker,
	.social-entry-title,
	.social-entry-desc {
		padding-left: 0;
	}

	.social-entry-kicker {
		font-size: 21rpx;
		line-height: 1.2;
	}

	.social-entry-title {
		font-size: 29rpx;
		line-height: 1.25;
	}

	.social-entry-desc {
		max-width: 420rpx;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		font-size: 22rpx;
		line-height: 1.35;
	}

	.social-entry-actions {
		width: auto;
		flex-direction: row;
		align-items: center;
		gap: 10rpx;
	}

	.social-entry-btn {
		width: 54rpx;
		height: 54rpx;
		padding: 0;
		border-radius: 50%;
		background: transparent;
		border: 0;
		color: #2e6b4d;
		font-size: 30rpx;
		box-shadow: none;
	}

	.social-entry-btn text:last-child {
		display: none;
	}

	.social-entry-btn--primary {
		width: 116rpx;
		border-radius: 999rpx;
		background: #2e6b4d;
		color: #fff;
		box-shadow: none;
	}

	.social-entry-btn--primary text:last-child {
		display: inline;
		margin-left: 6rpx;
		font-size: 23rpx;
		font-weight: 900;
	}

	.social-entry-actions {
		margin-left: auto;
	}

	.social-entry-btn {
		flex-shrink: 0;
	}

	.social-entry-btn--primary {
		width: 108rpx;
	}

	.social-entry-title {
		max-width: 100%;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	@media screen and (max-width: 360px) {
		.social-entry-card {
			gap: 10rpx;
		}

		.social-entry-copy {
			padding-left: 54rpx;
		}

		.social-entry-mark {
			width: 42rpx;
			height: 42rpx;
			font-size: 25rpx;
		}

		.social-entry-kicker {
			font-size: 19rpx;
		}

		.social-entry-title {
			font-size: 26rpx;
		}

		.social-entry-desc {
			max-width: 310rpx;
			font-size: 20rpx;
		}

		.social-entry-actions {
			gap: 4rpx;
		}

		.social-entry-btn {
			width: 46rpx;
			height: 46rpx;
			font-size: 26rpx;
		}

		.social-entry-btn--primary {
			width: 88rpx;
		}

		.social-entry-btn--primary text:last-child {
			font-size: 21rpx;
		}
	}

	@keyframes discover-shimmer {
		100% {
			transform: translateX(100%);
		}
	}
</style>
