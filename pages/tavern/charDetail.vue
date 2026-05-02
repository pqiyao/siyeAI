<template>
	<view class="page" :class="localeFontClass">
			<view v-if="charLoading" class="loading-wrap">
			<text class="loading-txt">{{ detailLoadingText }}</text>
		</view>

		<template v-else-if="char">
			<view class="hero-stage">
				<image class="hero-image" :class="{ 'hero-image--blur': isPreviewBlurActive(char) }" :src="heroCoverSrc" mode="aspectFit" lazy-load></image>
				<view
					v-if="isPreviewBlurActive(char)"
					class="hero-blur-surface"
					:style="blurSurfaceStyle(heroCoverSrc, 'contain')"
				></view>
				<view v-if="isPreviewBlurActive(char)" class="hero-blur-layer">
					<view class="hero-blur-pill">{{ previewBlurBadgeText(char) }}</view>
					<text class="hero-blur-note">{{ previewBlurHintText(char) }}</text>
				</view>
				<view class="hero-mask"></view>
				<view class="hero-fade"></view>

				<view class="chrome" :style="{ paddingTop: statusBarPx + 'px' }">
					<view class="chrome-btn" @tap="goBack">{{ uiText.back }}</view>
					<view class="chrome-title-wrap">
						<text class="chrome-main-title">{{ char.nickname }}</text>
						<text class="chrome-sub-title">{{ uiText.subtitle }}</text>
					</view>
					<view class="chrome-btn chrome-btn--ghost" @tap="onMore">{{ uiText.more }}</view>
				</view>
			</view>

			<view class="chrome chrome--floating" :style="{ paddingTop: statusBarPx + 'px' }">
				<view class="chrome-btn" @tap="goBack">{{ uiText.back }}</view>
				<view class="chrome-title-wrap">
					<text class="chrome-main-title">{{ char.nickname }}</text>
					<text class="chrome-sub-title">{{ uiText.subtitle }}</text>
				</view>
				<view class="chrome-btn chrome-btn--ghost" @tap="onMore">{{ uiText.more }}</view>
			</view>

			<scroll-view scroll-y class="page-scroll" :show-scrollbar="false" enable-back-to-top>
				<view class="sheet-spacer"></view>
				<view class="sheet-shell">
					<view class="sheet-card">
						<view class="sheet-handle"></view>

						<view class="panel-glass action-panel">
							<view class="action-row">
								<view class="action-pill" :class="{ on: voteLikeOn }" @tap="bumpLike">
									<text class="action-pill-icon">♥</text>
									<text class="action-pill-text">{{ displayLike }}</text>
								</view>
								<view class="action-pill" :class="{ on: voteDislikeOn }" @tap="bumpDislike">
									<text class="action-pill-icon">×</text>
									<text class="action-pill-text">{{ displayDislike }}</text>
								</view>
								<view class="action-pill action-pill--wide" :class="{ on: localFav }" @tap="toggleFav">
									<text class="action-pill-icon">★</text>
									<text class="action-pill-text">{{ localFav ? uiText.favorited : uiText.favorite }}</text>
								</view>
							</view>
							<view v-if="publicLabels.length" class="tag-row">
								<text v-for="(lab, idx) in publicLabels" :key="'tag_' + idx" class="tag-chip">
									{{ lab.code }}
								</text>
							</view>
						</view>

						<view class="panel-glass info-panel">
							<text class="block-title">{{ uiText.infoTitle }}</text>
							<view class="info-list">
								<view class="info-row">
									<text class="info-label">{{ uiText.tokensLabel }}</text>
									<text class="info-value">{{ char.token_display || '<2000' }}</text>
								</view>
								<view class="info-row">
									<text class="info-label">{{ uiText.authorLabel }}</text>
									<text class="info-value">{{ authorLabel || uiText.authorFallback }}</text>
								</view>
								<view class="info-row">
									<text class="info-label">{{ uiText.gameplayLabel }}</text>
									<text class="info-value">{{ char.gameplay_type || uiText.gameplayFallback }}</text>
								</view>
								<view class="info-row">
									<text class="info-label">{{ uiText.chatModeLabel }}</text>
									<text class="info-value info-value--wrap">{{ modeSummary }}</text>
								</view>
							</view>
						</view>

						<view class="panel-glass intro-panel">
							<text class="block-title">{{ uiText.introTitle }}</text>
							<text class="intro-summary">{{ publicSummary }}</text>
							<view class="privacy-card">
								<text class="privacy-title">{{ uiText.privacyTitle }}</text>
								<text class="privacy-desc">{{ uiText.privacyDesc }}</text>
							</view>
						</view>

						<view v-if="teaserPoints.length" class="panel-glass detail-panel">
							<text class="block-title">{{ uiText.teaserTitle }}</text>
							<view class="detail-list">
								<view v-for="(point, idx) in teaserPoints" :key="'point_' + idx" class="detail-item">
									<text class="detail-item-label">{{ point.label }}</text>
									<text class="detail-item-value">{{ point.value }}</text>
								</view>
							</view>
						</view>

						<view v-if="chatModes.length" class="panel-glass mode-panel">
							<text class="block-title">{{ uiText.modeTitle }}</text>
							<view class="mode-list">
								<view v-for="(mode, idx) in chatModes" :key="'mode_' + idx" class="mode-item">
									<view class="mode-icon">{{ mode.icon || '✦' }}</view>
									<view class="mode-copy">
										<text class="mode-name">{{ mode.name }}</text>
										<text v-if="mode.sub" class="mode-sub">{{ mode.sub }}</text>
									</view>
									<text v-if="mode.recommend" class="mode-rec">{{ uiText.modeRecommend }}</text>
								</view>
							</view>
						</view>

						<view class="panel-glass note-panel">
							<text class="block-title">{{ uiText.noteTitle }}</text>
							<view class="note-list">
								<text class="note-item">{{ uiText.note1 }}</text>
								<text class="note-item">{{ uiText.note2 }}</text>
								<text class="note-item">{{ uiText.note3 }}</text>
							</view>
							<text class="note-desc">
								{{ uiText.noteDesc }}
							</text>
						</view>
					</view>
				</view>
				<view class="page-bottom-space"></view>
			</scroll-view>

			<view class="cta-shell">
				<view class="cta-panel">
					<view class="cta-copy">
						<text class="cta-title">{{ char.unlocked ? uiText.ctaOpenTitle : uiText.ctaClosedTitle }}</text>
						<text class="cta-desc">
							{{ char.unlocked ? uiText.ctaOpenDesc : uiText.ctaClosedDesc }}
						</text>
					</view>
					<view class="cta-btn" :class="{ 'cta-btn--vip': !char.unlocked }" @tap="openChat">
						{{ char.unlocked ? uiText.ctaOpenButton : uiText.ctaClosedButton }}
					</view>
				</view>
			</view>
		</template>

		<view v-else class="empty-wrap">
			<text class="empty-txt">{{ charEmptyLine }}</text>
			<view v-if="charLoadError && jgDetailOn" class="cta-mini" @tap="retryJgCharacter">{{ retryText }}</view>
			<view class="cta-mini cta-mini--ghost" @tap="goBack">{{ uiText.back }}</view>
		</view>
	</view>
</template>

<script>
const tavernApi = require('@/common/tavernApi.js');
const tavernCharacterAccess = require('@/common/tavernCharacterAccess.js');
const INTERACTION_PATCH_KEY = 'jg_character_interaction_patch';
const { getTavernUiText, getLanguageCode, formatLocaleText } = require('@/common/tavernUiI18n.js');

export default {
	data() {
		return {
			char: null,
			id: '',
			statusBarPx: 20,
			localLike: 0,
			localDislike: 0,
			localFav: false,
			charLoading: false,
			charLoadError: '',
			charNotFound: false,
			userVote: 'none',
			viewerSignature: ''
		};
	},
	computed: {
		uiText() {
			return getTavernUiText('detail');
		},
		detailPatchText() {
			const code = getLanguageCode();
			const map = {
				'zh-cn': {
					modeRoleplay: '角色扮演',
					listJoiner: '、',
					tagJoiner: ' / ',
					vibeTags: '围绕「{tags}」展开',
					vibeDefault: '以情境互动为主',
					summaryFallback:
						'这是一张偏 {gameplay} 的角色卡，公开页只展示氛围摘要，完整设定会在进入私聊后逐步展开。当前内容 {vibe}。',
					teaserPersona: '角色气质',
					teaserScene: '主要场景',
					teaserGameplay: '互动方式',
					teaserUnlock: '开启方式',
					teaserUnlockValue: '进入私聊后逐步解锁完整设定',
					characterClosed: '该角色暂未开放'
				},
				'zh-hk': {
					modeRoleplay: '角色扮演',
					listJoiner: '、',
					tagJoiner: ' / ',
					vibeTags: '圍繞「{tags}」展開',
					vibeDefault: '以情境互動為主',
					summaryFallback:
						'這是一張偏 {gameplay} 的角色卡，公開頁只展示氛圍摘要，完整設定會在進入私聊後逐步展開。當前內容 {vibe}。',
					teaserPersona: '角色氣質',
					teaserScene: '主要場景',
					teaserGameplay: '互動方式',
					teaserUnlock: '開啟方式',
					teaserUnlockValue: '進入私聊後逐步解鎖完整設定',
					characterClosed: '該角色暫未開放'
				},
				en: {
					modeRoleplay: 'Roleplay',
					listJoiner: ' / ',
					tagJoiner: ' / ',
					vibeTags: 'built around "{tags}"',
					vibeDefault: 'focused on contextual interaction',
					summaryFallback:
						'This is a {gameplay} card. The public page only shows a brief vibe summary, and the full setup unlocks gradually after private chat starts. Current focus: {vibe}.',
					teaserPersona: 'Character Vibe',
					teaserScene: 'Main Scene',
					teaserGameplay: 'Interaction Style',
					teaserUnlock: 'Unlock Path',
					teaserUnlockValue: 'Full setup unlocks gradually after private chat starts',
					characterClosed: 'This character is not open yet'
				},
				ko: {
					modeRoleplay: '역할 연기',
					listJoiner: ' · ',
					tagJoiner: ' / ',
					vibeTags: '"{tags}" 중심으로 구성됨',
					vibeDefault: '상황형 상호작용에 초점',
					summaryFallback:
						'이 카드는 {gameplay} 중심의 캐릭터 카드입니다. 공개 페이지에서는 분위기 요약만 보여 주며, 전체 설정은 개인 채팅을 시작한 뒤 점차 열립니다. 현재 포인트: {vibe}.',
					teaserPersona: '캐릭터 분위기',
					teaserScene: '주요 장면',
					teaserGameplay: '상호작용 방식',
					teaserUnlock: '열리는 방식',
					teaserUnlockValue: '개인 채팅 진입 후 전체 설정이 단계적으로 열립니다',
					characterClosed: '이 캐릭터는 아직 열려 있지 않습니다'
				},
				ja: {
					modeRoleplay: 'ロールプレイ',
					listJoiner: '・',
					tagJoiner: ' / ',
					vibeTags: '「{tags}」を軸に展開',
					vibeDefault: 'シチュエーション重視のやり取り',
					summaryFallback:
						'これは {gameplay} 寄りのキャラクターカードです。公開ページでは雰囲気の要約のみを表示し、完全設定は個別チャット開始後に段階的に開放されます。現在の見どころ: {vibe}。',
					teaserPersona: 'キャラの空気感',
					teaserScene: '主なシーン',
					teaserGameplay: 'やり取りの形',
					teaserUnlock: '解放方法',
					teaserUnlockValue: '個別チャット開始後に完全設定が段階的に開放されます',
					characterClosed: 'このキャラクターはまだ公開されていません'
				}
			};
			return map[code] || map['zh-cn'];
		},
		detailLoadingText() {
			const map = {
				'zh-cn': '正在加载角色详情...',
				'zh-hk': '正在加載角色詳情...',
				en: 'Loading character details...',
				ko: '캐릭터 상세 정보를 불러오는 중...',
				ja: 'キャラクター詳細を読み込み中...'
			};
			return map[getLanguageCode()] || map['zh-cn'];
		},
		retryText() {
			const map = {
				'zh-cn': '点击重试',
				'zh-hk': '點擊重試',
				en: 'Retry',
				ko: '다시 시도',
				ja: '再試行'
			};
			return map[getLanguageCode()] || map['zh-cn'];
		},
		notFoundText() {
			const map = {
				'zh-cn': '角色不存在',
				'zh-hk': '角色不存在',
				en: 'Character unavailable',
				ko: '캐릭터를 찾을 수 없습니다',
				ja: 'キャラクターが見つかりません'
			};
			return map[getLanguageCode()] || map['zh-cn'];
		},
		jgDetailOn() {
			try {
				return tavernApi.jgEnabled();
			} catch (e) {
				return false;
			}
		},
		charEmptyLine() {
			if (this.charNotFound) return this.notFoundText;
			if (this.charLoadError) return this.charLoadError;
			return this.notFoundText;
		},
		authorLabel() {
			if (!this.char) return '';
			if (this.char.creator === '匿名') return this.uiText.authorFallback;
			return this.char.creator || '';
		},
		displayLike() {
			return this.localLike;
		},
		displayDislike() {
			return this.localDislike;
		},
		voteLikeOn() {
			return this.userVote === 'like';
		},
		voteDislikeOn() {
			return this.userVote === 'dislike';
		},
		heroCoverSrc() {
			if (!this.char) return '/static/logo.png';
			const src = this.char.cover || this.char.avatar;
			if (src == null || String(src).trim() === '') return '/static/logo.png';
			return tavernApi.resolveJgAssetUrl(src) || '/static/logo.png';
		},
		publicLabels() {
			return this.safeLabels(this.char).slice(0, 8);
		},
		chatModes() {
			return Array.isArray(this.char && this.char.chat_modes) ? this.char.chat_modes.slice(0, 4) : [];
		},
		modeSummary() {
			if (!this.chatModes.length) return this.detailPatchText.modeRoleplay;
			return this.chatModes
				.map((item) => item && item.name)
				.filter(Boolean)
				.join(this.detailPatchText.listJoiner);
		},
		publicSummary() {
			if (!this.char) return '';
			const preferred = [this.char.bio, this.char.tagline, this.char.persona, this.char.scenario];
			for (let i = 0; i < preferred.length; i += 1) {
				const normalized = this.normalizeText(preferred[i]);
				if (normalized) return this.truncateText(normalized, 180);
			}
			const tags = this.publicLabels.map((item) => item.code).filter(Boolean);
			const vibe = tags.length
				? formatLocaleText(this.detailPatchText.vibeTags, {
						tags: tags.slice(0, 3).join(this.detailPatchText.tagJoiner)
					})
				: this.detailPatchText.vibeDefault;
			const gameplay = this.char.gameplay_type || this.uiText.gameplayFallback;
			return formatLocaleText(this.detailPatchText.summaryFallback, { gameplay: gameplay, vibe: vibe });
		},
		teaserPoints() {
			if (!this.char) return [];
			const list = [];
			const persona = this.normalizeText(this.char.persona);
			const scenario = this.normalizeText(this.char.scenario);
			if (persona) list.push({ label: this.detailPatchText.teaserPersona, value: this.truncateText(persona, 30) });
			if (scenario) list.push({ label: this.detailPatchText.teaserScene, value: this.truncateText(scenario, 30) });
			if (this.char.gameplay_type) list.push({ label: this.detailPatchText.teaserGameplay, value: this.char.gameplay_type });
			list.push({ label: this.detailPatchText.teaserUnlock, value: this.detailPatchText.teaserUnlockValue });
			return list.slice(0, 4);
		}
	},
	onLoad(q) {
		this.viewerSignature = tavernApi.getViewerStateSignature();
		try {
			const system = uni.getSystemInfoSync();
			this.statusBarPx = system.statusBarHeight || 20;
		} catch (e) {
			this.statusBarPx = 20;
		}
		this.id = (q && q.id) || '';
		if (!tavernApi.jgEnabled()) {
			this.char = null;
			this.charNotFound = false;
			this.charLoadError =
				getLanguageCode() === 'en'
					? 'API unavailable'
					: getLanguageCode() === 'ko'
						? 'API를 사용할 수 없습니다'
						: getLanguageCode() === 'ja'
							? 'API を利用できません'
							: getLanguageCode() === 'zh-hk'
								? '後端接口未開啟'
								: '后端接口未开启';
			return;
		}
		this.loadJgCharacter();
	},
	onShow() {
		const currentViewerSignature = tavernApi.getViewerStateSignature();
		const shouldRefresh =
			tavernApi.consumeCharacterAccessRefreshNeeded() ||
			currentViewerSignature !== this.viewerSignature;
		if (!shouldRefresh || !this.id) {
			return;
		}
		this.viewerSignature = currentViewerSignature;
		this.loadJgCharacter();
	},
	methods: {
		isPreviewBlurActive(card) {
			return tavernCharacterAccess.isPreviewBlurActive(card);
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
		previewBlurBadgeText(card) {
			return tavernCharacterAccess.previewBlurBadgeText(card);
		},
		previewBlurHintText(card) {
			return tavernCharacterAccess.previewBlurHintText(card);
		},
		loadJgCharacter() {
			if (!tavernApi.jgEnabled()) return;
			this.char = null;
			this.charLoading = true;
			this.charLoadError = '';
			this.charNotFound = false;
			tavernApi
				.fetchCharacter(this.id)
				.then((remote) => {
					if (!remote) {
						this.char = null;
						this.charNotFound = true;
						return;
					}
					this.char = remote;
					this.syncFromChar();
				})
				.catch((e) => {
					const tavernErrors = require('@/common/tavernErrors.js');
					this.char = null;
					this.charNotFound = false;
					this.charLoadError = tavernErrors.getTavernErrorMessage(e, '加载失败');
				})
				.finally(() => {
					this.charLoading = false;
				});
		},
		retryJgCharacter() {
			this.loadJgCharacter();
		},
		normalizeText(raw) {
			if (raw == null) return '';
			return String(raw)
				.replace(/<[^>]+>/g, ' ')
				.replace(/\{\{[^}]+\}\}/g, ' ')
				.replace(/\[[^\]]+\]/g, ' ')
				.replace(/\s+/g, ' ')
				.trim();
		},
		truncateText(text, maxLen) {
			const clean = String(text || '').trim();
			if (!clean) return '';
			if (clean.length <= maxLen) return clean;
			return clean.slice(0, maxLen) + '...';
		},
		safeLabels(card) {
			const source = Array.isArray(card && card.label_array) ? card.label_array : [];
			return source
				.map((item) => {
					if (item == null) return null;
					if (typeof item === 'string') return { code: String(item).trim() };
					const code = String(item.code || item.name || item.label || '').trim();
					if (!code) return null;
					return Object.assign({}, item, { code: code });
				})
				.filter(Boolean);
		},
		syncFromChar() {
			const current = this.char;
			if (!current) return;
			this.localLike = typeof current.like_count === 'number' ? current.like_count : 0;
			this.localDislike = typeof current.dislike_count === 'number' ? current.dislike_count : 0;
			this.localFav = !!current.is_favorite;
			this.userVote = current.user_vote === 'like' || current.user_vote === 'dislike' ? current.user_vote : 'none';
		},
		applyInteractionData(data) {
			if (!data || typeof data !== 'object') return;
			this.localLike = Number(data.like_count) || 0;
			this.localDislike = Number(data.dislike_count) || 0;
			this.localFav = !!data.is_favorite;
			this.userVote = data.user_vote === 'like' || data.user_vote === 'dislike' ? data.user_vote : 'none';
			if (this.char) {
				this.char.like_count = this.localLike;
				this.char.dislike_count = this.localDislike;
				this.char.is_favorite = this.localFav;
				this.char.user_vote = this.userVote;
			}
			this.persistInteractionPatch();
		},
		persistInteractionPatch() {
			if (!this.char || this.char.id == null) return;
			try {
				uni.setStorageSync(INTERACTION_PATCH_KEY, {
					id: Number(this.char.id),
					like_count: this.localLike,
					dislike_count: this.localDislike,
					is_favorite: this.localFav,
					user_vote: this.userVote,
					updated_at: Date.now()
				});
			} catch (e) {}
		},
		goBack() {
			this.util.safeNavigateBack('/pages/index/index');
		},
		onMore() {
			const code = getLanguageCode();
			const menuMap = {
				'zh-cn': ['分享角色', '举报角色'],
				'zh-hk': ['分享角色', '舉報角色'],
				en: ['Share Character', 'Report Character'],
				ko: ['캐릭터 공유', '캐릭터 신고'],
				ja: ['キャラクターを共有', 'キャラクターを通報']
			};
			uni.showActionSheet({
				itemList: menuMap[code] || menuMap.en,
				success: (res) => {
					if (res.tapIndex === 0) {
						this.shareCharacter();
					} else if (res.tapIndex === 1) {
						this.reportCharacter();
					}
				}
			});
		},
		shareCharacter() {
			if (!this.char) return;
			const code = getLanguageCode();
			const openMap = {
				'zh-cn': '在角色聊天中打开',
				'zh-hk': '在酒館中打開',
				en: 'Open in Tavern',
				ko: 'Tavern에서 열기',
				ja: 'Tavern で開く'
			};
			const copiedMap = {
				'zh-cn': '已复制分享文案',
				'zh-hk': '已複製分享文案',
				en: 'Copied',
				ko: '공유 문구를 복사했습니다',
				ja: '共有文をコピーしました'
			};
			const text =
				(this.char.nickname || '') +
				'\n' +
				(this.publicSummary || '') +
				'\n' +
				(openMap[code] || openMap.en);
			uni.setClipboardData({
				data: text,
				success: () => {
					uni.showToast({ title: copiedMap[code] || copiedMap.en, icon: 'none' });
				}
			});
		},
		reportCharacter() {
			if (!this.char || this.char.id == null) return;
			const code = getLanguageCode();
			const prefixMap = {
				'zh-cn': '举报角色 - ',
				'zh-hk': '舉報角色 - ',
				en: 'Report character - ',
				ko: '캐릭터 신고 - ',
				ja: 'キャラクター通報 - '
			};
			const subject = encodeURIComponent((prefixMap[code] || prefixMap.en) + (this.char.nickname || ''));
			const name = encodeURIComponent(this.char.nickname || '');
			uni.navigateTo({
				url:
					'/pages/user/supportCreate?ticketType=REPORT&characterId=' +
					encodeURIComponent(String(this.char.id)) +
					'&characterName=' +
					name +
					'&subject=' +
					subject
			});
		},
		bumpLike() {
			if (!this.char || this.char.id == null) return;
			tavernApi
				.postCharacterInteraction({
					characterId: Number(this.char.id),
					clientUid: tavernApi.getClientUid(),
					action: 'like'
				})
				.then((data) => this.applyInteractionData(data))
				.catch((e) => {
					uni.showToast({
						title: require('@/common/tavernErrors.js').getTavernErrorMessage(e, '操作失败'),
						icon: 'none'
					});
				});
		},
		bumpDislike() {
			if (!this.char || this.char.id == null) return;
			tavernApi
				.postCharacterInteraction({
					characterId: Number(this.char.id),
					clientUid: tavernApi.getClientUid(),
					action: 'dislike'
				})
				.then((data) => this.applyInteractionData(data))
				.catch((e) => {
					uni.showToast({
						title: require('@/common/tavernErrors.js').getTavernErrorMessage(e, '操作失败'),
						icon: 'none'
					});
				});
		},
		toggleFav() {
			if (!this.char || this.char.id == null) return;
			tavernApi
				.postCharacterInteraction({
					characterId: Number(this.char.id),
					clientUid: tavernApi.getClientUid(),
					action: 'favorite'
				})
				.then((data) => this.applyInteractionData(data))
				.catch((e) => {
					uni.showToast({
						title: require('@/common/tavernErrors.js').getTavernErrorMessage(e, '操作失败'),
						icon: 'none'
					});
				});
		},
		openChat() {
			if (!this.char) return;
			if (!this.char.unlocked) {
				const lockReason = this.char.lock_reason || this.detailPatchText.characterClosed;
				uni.showToast({ title: lockReason, icon: 'none' });
				this.util.urlTo('/pages/user/myvip');
				return;
			}
			uni.navigateTo({ url: '/pages/tavern/tavernChat?id=' + this.char.id });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	height: 100vh;
	position: relative;
	background: #090b14;
	overflow: hidden;
}

.page-scroll {
	position: relative;
	z-index: 4;
	height: 100vh;
}

.hero-stage {
	position: absolute;
	inset: 0;
	z-index: 1;
	background:
		radial-gradient(circle at top, rgba(255, 255, 255, 0.05), transparent 26%),
		linear-gradient(180deg, #0b0d17 0%, #121420 100%);
	overflow: hidden;
}

.hero-stage > .chrome {
	display: none;
}

.hero-image {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
	display: block;
	/* #ifdef H5 */
	object-fit: contain;
	object-position: center top;
	/* #endif */
}

.hero-image--blur {
	filter: blur(18rpx) scale(1.05) brightness(0.72);
}

.hero-blur-surface {
	position: absolute;
	inset: -24rpx;
	z-index: 1;
	pointer-events: none;
	filter: blur(34rpx) saturate(0.9) brightness(0.64);
	transform: scale(1.08);
	background-repeat: no-repeat;
	background-position: center top;
	background-size: contain;
	opacity: 1;
}

.hero-blur-layer {
	position: absolute;
	inset: 0;
	z-index: 3;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 40rpx;
	background: linear-gradient(180deg, rgba(8, 10, 18, 0.12) 0%, rgba(8, 10, 18, 0.28) 100%);
	pointer-events: none;
}

.hero-blur-pill {
	padding: 14rpx 26rpx;
	border-radius: 999rpx;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff7ed;
	background: rgba(15, 23, 42, 0.48);
	border: 1rpx solid rgba(253, 186, 116, 0.34);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
}

.hero-blur-note {
	margin-top: 18rpx;
	font-size: 24rpx;
	line-height: 1.65;
	text-align: center;
	color: rgba(255, 244, 230, 0.92);
}

.hero-mask {
	position: absolute;
	inset: 0;
	z-index: 2;
	background: linear-gradient(180deg, rgba(7, 10, 18, 0.03) 0%, rgba(7, 10, 18, 0) 32%, rgba(9, 11, 20, 0.08) 74%, rgba(9, 11, 20, 0.22) 100%);
}

.hero-fade {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 2;
	height: 180rpx;
	background: linear-gradient(180deg, rgba(9, 11, 20, 0) 0%, rgba(9, 11, 20, 0.78) 100%);
}

.chrome {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	z-index: 24;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding-left: 22rpx;
	padding-right: 22rpx;
	padding-bottom: 18rpx;
	background: linear-gradient(180deg, rgba(7, 10, 18, 0.72) 0%, rgba(7, 10, 18, 0.24) 72%, rgba(7, 10, 18, 0) 100%);
	pointer-events: auto;
}

.chrome--floating {
	z-index: 40;
}

.chrome-btn {
	min-width: 108rpx;
	height: 66rpx;
	padding: 0 20rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
	color: #f3e8ff;
	background: rgba(10, 12, 20, 0.24);
	border: 1rpx solid rgba(255, 255, 255, 0.1);
	backdrop-filter: blur(10px);
	-webkit-backdrop-filter: blur(10px);
}

.chrome-btn--ghost {
	min-width: 94rpx;
}

.chrome-title-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 6rpx;
	min-width: 0;
	padding: 0 12rpx;
}

.chrome-main-title {
	max-width: 340rpx;
	font-size: 34rpx;
	font-weight: 800;
	color: #fff;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.chrome-sub-title {
	font-size: 22rpx;
	color: rgba(255, 255, 255, 0.72);
}

.sheet-spacer {
	height: 64vh;
	min-height: 780rpx;
}

.sheet-shell {
	padding: 0 18rpx;
}

.sheet-card {
	border-radius: 34rpx 34rpx 0 0;
	background: rgba(19, 20, 27, 0.56);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow:
		0 26rpx 60rpx rgba(4, 8, 18, 0.42),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
	backdrop-filter: blur(28px);
	-webkit-backdrop-filter: blur(28px);
	overflow: hidden;
}

.sheet-handle {
	width: 108rpx;
	height: 10rpx;
	margin: 16rpx auto 14rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.22);
}

.panel-glass {
	margin: 0 18rpx 18rpx;
	padding: 24rpx;
	border-radius: 28rpx;
	background: rgba(255, 255, 255, 0.045);
	border: 1rpx solid rgba(255, 255, 255, 0.06);
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.04);
}

.action-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.action-pill {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	height: 76rpx;
	min-width: 116rpx;
	padding: 0 20rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.05);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.action-pill--wide {
	flex: 1;
}

.action-pill.on {
	background: rgba(236, 72, 153, 0.12);
	border-color: rgba(244, 114, 182, 0.28);
}

.action-pill-icon {
	font-size: 26rpx;
	color: #fda4af;
}

.action-pill-text {
	font-size: 24rpx;
	font-weight: 700;
	color: #f8fafc;
}

.tag-row {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 18rpx;
}

.tag-chip {
	padding: 8rpx 16rpx;
	border-radius: 14rpx;
	font-size: 22rpx;
	line-height: 1.2;
	color: #f3e8ff;
	background: rgba(76, 29, 149, 0.34);
	border: 1rpx solid rgba(192, 132, 252, 0.24);
}

.block-title {
	display: block;
	font-size: 30rpx;
	font-weight: 800;
	color: #f8fafc;
}

.info-list {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
	margin-top: 18rpx;
}

.info-row {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 22rpx;
}

.info-label {
	font-size: 23rpx;
	color: #9aa4b8;
	flex-shrink: 0;
}

.info-value {
	font-size: 25rpx;
	font-weight: 700;
	line-height: 1.6;
	color: #fff;
	text-align: right;
}

.info-value--wrap {
	max-width: 430rpx;
}

.intro-summary {
	display: block;
	margin-top: 18rpx;
	font-size: 26rpx;
	line-height: 1.82;
	color: #e5ebf5;
}

.privacy-card {
	margin-top: 22rpx;
	padding: 20rpx;
	border-radius: 24rpx;
	background:
		linear-gradient(135deg, rgba(76, 29, 149, 0.28) 0%, rgba(157, 23, 77, 0.18) 100%),
		rgba(255, 255, 255, 0.02);
	border: 1rpx solid rgba(192, 132, 252, 0.18);
}

.privacy-title {
	display: block;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
}

.privacy-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 23rpx;
	line-height: 1.7;
	color: #ddd7eb;
}

.detail-list,
.mode-list,
.note-list {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
	margin-top: 18rpx;
}

.detail-item,
.mode-item {
	padding: 18rpx;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.04);
	border: 1rpx solid rgba(255, 255, 255, 0.06);
}

.detail-item-label {
	display: block;
	font-size: 22rpx;
	color: #a5b4cb;
}

.detail-item-value {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	line-height: 1.65;
	color: #f8fafc;
}

.mode-item {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.mode-icon {
	width: 58rpx;
	height: 58rpx;
	border-radius: 18rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	background: rgba(124, 58, 237, 0.18);
	flex-shrink: 0;
}

.mode-copy {
	flex: 1;
	min-width: 0;
}

.mode-name {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: #f8fafc;
}

.mode-sub {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #94a3b8;
}

.mode-rec {
	flex-shrink: 0;
	padding: 8rpx 14rpx;
	border-radius: 999rpx;
	font-size: 20rpx;
	font-weight: 700;
	color: #fde68a;
	background: rgba(250, 204, 21, 0.16);
	border: 1rpx solid rgba(250, 204, 21, 0.24);
}

.note-item {
	font-size: 25rpx;
	line-height: 1.6;
	color: #e5e7eb;
}

.note-desc {
	display: block;
	margin-top: 16rpx;
	font-size: 23rpx;
	line-height: 1.7;
	color: #95a1b6;
}

.page-bottom-space {
	height: 300rpx;
}

.cta-shell {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 10;
	padding: 20rpx 22rpx calc(22rpx + env(safe-area-inset-bottom));
	background: linear-gradient(180deg, rgba(18, 18, 27, 0) 0%, rgba(18, 18, 27, 0.84) 30%, rgba(18, 18, 27, 1) 100%);
}

.cta-panel {
	display: flex;
	align-items: center;
	gap: 18rpx;
	padding: 18rpx 18rpx 20rpx;
	border-radius: 28rpx;
	background: rgba(10, 12, 22, 0.9);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow: 0 20rpx 44rpx rgba(2, 5, 13, 0.45);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
}

.cta-copy {
	flex: 1;
	min-width: 0;
}

.cta-title {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: #fff;
}

.cta-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.5;
	color: #9ca3af;
}

.cta-btn {
	flex-shrink: 0;
	min-width: 236rpx;
	height: 84rpx;
	padding: 0 30rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #7c3aed 0%, #ec4899 100%);
	box-shadow: 0 16rpx 30rpx rgba(124, 58, 237, 0.28);
}

.cta-btn--vip {
	background: linear-gradient(135deg, rgba(124, 58, 237, 0.92), rgba(236, 72, 153, 0.92));
	color: #fff;
	box-shadow: 0 18rpx 34rpx rgba(168, 85, 247, 0.28);
}

.loading-wrap,
.empty-wrap {
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 0 48rpx;
	text-align: center;
	background: #090b14;
}

.loading-txt,
.empty-txt {
	font-size: 28rpx;
	line-height: 1.7;
	color: #cbd5e1;
}

.cta-mini {
	margin-top: 24rpx;
	min-width: 220rpx;
	height: 80rpx;
	padding: 0 30rpx;
	border-radius: 999rpx;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #7c3aed 0%, #db2777 100%);
}

.cta-mini--ghost {
	background: rgba(255, 255, 255, 0.08);
	color: #d1d5db;
}
</style>
