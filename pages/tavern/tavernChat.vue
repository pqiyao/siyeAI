<template>
	<view class="wrap" :class="{ focused: inputFocus, 'wrap--with-bg': hasChatBackground }" :style="wrapStyle">
		<tavern-nav-bar :title="title" mode="dark" @back="goBack">
			<template #right>
				<text v-if="jgOn" class="nav-link" @tap="goPersona">{{ chatUi.settings }}</text>
			</template>
		</tavern-nav-bar>

		<view v-if="jgOn && jgChatLoadState === 'loading'" class="chat-fill">
			<text class="chat-fill-txt">{{ tx('chat_loading', '加载中...') }}</text>
		</view>

		<view v-else-if="jgOn && jgChatLoadState === 'error'" class="chat-fill chat-fill--err">
			<text class="chat-fill-txt">{{ jgChatErrorMsg || tx('chat_load_failed', '加载失败') }}</text>
			<view class="chat-fill-retry" @tap="retryJgChatLoad">{{ tx('retry', '点击重试') }}</view>
			<view class="chat-fill-back" @tap="goBack">{{ tx('back', '返回') }}</view>
		</view>

		<block v-else>
			<view v-if="jgOn" class="tool-bar">
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onRegen">{{ chatUi.regen }}</text>
				<text class="tool-i" :class="{ 'tool-i--disabled': !assistantTailActionState().ok }" @tap="onContinue">{{ chatUi.continue }}</text>
				<text class="tool-i" @tap="onRestart">{{ chatUi.restart }}</text>
				<text class="tool-i" @tap="onMem">{{ chatUi.memory }}</text>
			</view>
			<view v-if="jgOn && jgChatLoadState === 'ready'" class="tool-hint">
				<text v-if="assistantTailActionHint()" class="tool-hint-txt">{{ assistantTailActionHint() }}</text>
				<text class="tool-hint-txt">{{ tx('multi_swipe_hint', '多版本切换：对同一条 AI 回复“重生”后可用左右切换；只有 1 个版本时不显示切换按钮。') }}</text>
			</view>
			<view v-if="jgOn && jgChatLoadState === 'ready' && memoryBarText" class="memory-bar" @tap="onMem">
				<text class="memory-bar-txt">{{ memoryBarText }}</text>
			</view>

		<scroll-view
			class="chat-scroll"
			scroll-y
			:scroll-into-view="scrollTo"
			scroll-with-animation
			@scroll="onChatScroll"
			@scrolltolower="onChatScrollToLower"
			:lower-threshold="80"
		>
			<view v-for="m in messages" :key="m.id" :id="'m-' + m.id" class="msg-row" :class="m.role === 'user' ? 'me' : 'them'">
				<image
					v-if="m.role !== 'user'"
					class="av av--char"
					:src="charAvatar"
					mode="aspectFill"
					lazy-load
					@tap.stop="openCharImagePreview"
				></image>
				<view class="bubble" :class="{ 'bubble--char': m.role !== 'user' }">
					<!-- #ifdef H5 -->
					<view v-if="m.role === 'char'" class="md-inner" v-html="mdHtml(m.text)" @tap="onMarkdownTap"></view>
					<template v-else>
						<text class="txt">{{ m.text }}</text>
						<text v-if="canEditUserMessage(m)" class="user-edit-tag" @tap.stop="openEditUserMessage(m)">{{ chatUi.edit }}</text>
					</template>
					<!-- #endif -->
					<!-- #ifndef H5 -->
					<view>
						<text class="txt">{{ m.text }}</text>
						<text v-if="canEditUserMessage(m)" class="user-edit-tag" @tap.stop="openEditUserMessage(m)">{{ chatUi.edit }}</text>
					</view>
					<!-- #endif -->
					<view v-if="m.role === 'char' && m.swipes && m.swipes.length > 1" class="swipe-row">
						<text class="swipe-btn" @tap.stop="swipeCharMessage(m, -1)">&lt;</text>
						<text class="swipe-num">{{ swipeLabel(m) }}</text>
						<text class="swipe-btn" @tap.stop="swipeCharMessage(m, 1)">&gt;</text>
					</view>
				</view>
				<image v-if="m.role === 'user'" class="av" :src="resolvedUserAvatar" mode="aspectFill" lazy-load></image>
			</view>
			<view id="bottom-anchor" style="height: 24rpx;"></view>
		</scroll-view>

		<view v-if="sending" class="typing-row">
			<text class="typing-hint">{{ tx('ai_thinking', '思考中...') }}</text>
			<text v-if="showStopStream" class="stop-stream" @tap="stopGeneration">{{ chatUi.stop }}</text>
		</view>
		<view
			v-if="replySuggest.visible || replySuggest.loading || replySuggest.error"
			class="reply-help-panel"
		>
			<view class="reply-help-head">
				<text class="reply-help-title">{{ tx('reply_help_title', 'AI帮答') }}</text>
				<view class="reply-help-head-actions">
					<text
						class="reply-help-head-btn"
						:class="{ 'reply-help-head-btn--disabled': replySuggest.loading }"
						@tap="refreshReplySuggestions(true)"
					>
						{{ tx('reply_help_refresh', '换一批') }}
					</text>
					<text class="reply-help-head-btn" @tap="closeReplySuggestions">{{ tx('collapse', '收起') }}</text>
				</view>
			</view>
			<text v-if="replySuggest.loading" class="reply-help-state">
				{{ tx('reply_help_loading', '正在生成帮答建议') }}
			</text>
			<text v-else-if="replySuggest.error" class="reply-help-state reply-help-state--error">
				{{ replySuggest.error }}
			</text>
			<view v-else class="reply-help-list">
				<view
					v-for="(item, idx) in replySuggest.items"
					:key="idx"
					class="reply-help-card"
					@tap="applyReplySuggestion(item)"
				>
					<text class="reply-help-index">{{ idx + 1 }}</text>
					<text class="reply-help-text">{{ item }}</text>
				</view>
			</view>
		</view>
		<view v-if="editOverlay.visible" class="edit-mask" @tap="closeEditUser">
			<view class="edit-panel" @tap.stop>
				<text class="edit-title">{{ chatUi.editTitle }}</text>
				<text class="edit-sub">{{ chatUi.editSub }}</text>
				<textarea class="edit-ta" v-model="editOverlay.draft" :disabled="editOverlay.saving" auto-height />
				<view class="edit-actions">
					<text class="edit-btn edit-btn--muted" @tap="closeEditUser">{{ tx('cancel', '取消') }}</text>
					<text class="edit-btn edit-btn--primary" @tap="submitEditUser">{{ tx('save', '保存') }}</text>
				</view>
			</view>
		</view>
		<view v-if="commercialPrompt.visible" class="commercial-mask" @tap="closeCommercialPrompt">
			<view class="commercial-card" @tap.stop>
				<text class="commercial-title">{{ commercialPrompt.title }}</text>
				<text class="commercial-sub">{{ commercialPrompt.message }}</text>
				<view class="commercial-actions">
					<text class="commercial-btn commercial-btn--ghost" @tap="closeCommercialPrompt">{{ chatUi.later }}</text>
					<text
						v-if="commercialPrompt.secondaryUrl"
						class="commercial-btn commercial-btn--muted"
						@tap="goCommercial(commercialPrompt.secondaryUrl)"
					>
						{{ commercialPrompt.secondaryText || chatUi.recharge }}
					</text>
					<text class="commercial-btn commercial-btn--primary" @tap="goCommercial(commercialPrompt.primaryUrl)">
						{{ commercialPrompt.primaryText || chatUi.openVip }}
					</text>
				</view>
			</view>
		</view>
		<view v-if="charImagePreviewVisible" class="char-image-mask" @tap="closeCharImagePreview">
			<view class="char-image-shell" @tap.stop>
				<text class="char-image-close" @tap="closeCharImagePreview">×</text>
				<image class="char-image-full" :src="charPreviewImage" mode="aspectFit" lazy-load></image>
			</view>
		</view>

		<view class="input-bar">
			<view
				class="reply-help-trigger"
				:class="{ 'reply-help-trigger--disabled': !canOpenReplySuggestions() }"
				@tap="toggleReplySuggestions"
			>
				{{ tx('reply_help_button', 'AI帮答') }}
			</view>
			<input
				class="inp"
				placeholder-class="inp-ph"
				v-model="draft"
				:placeholder="tx('input_message', '输入消息...')"
				confirm-type="send"
				:disabled="sending"
				@focus="inputFocus = true"
				@blur="inputFocus = false"
				@confirm="send"
			/>
			<view class="send" :class="{ senddisabled: sending }" @tap="send">{{ tx('send', '发送') }}</view>
		</view>
		</block>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
	const { getTavernUiText, formatLocaleText } = require('@/common/tavernUiI18n.js');

	export default {
		components: { TavernNavBar },
		data() {
			return {
				cid: '',
				char: null,
				messages: [],
				draft: '',
				scrollTo: '',
				inputFocus: false,
				sending: false,
				jgOn: false,
				userAvatar: '',
				streamAbortController: null,
				stopRefreshTimer: null,
				followBottom: true,
				editOverlay: { visible: false, messageId: '', draft: '', saving: false },
				commercialPrompt: {
					visible: false,
					title: '',
					message: '',
					primaryText: '',
					primaryUrl: '',
					secondaryText: '',
					secondaryUrl: ''
				},
				charImagePreviewVisible: false,
				jgChatLoadState: 'idle',
				jgChatErrorMsg: '',
				jgMemory: null,
				jgTavernMeta: null,
				replySuggest: {
					visible: false,
					loading: false,
					error: '',
					items: [],
					contextKey: ''
				}
			};
		},
		computed: {
			chatUi() {
				return getTavernUiText('chat');
			},
			memoryBarText() {
				if (!this.jgOn || this.jgChatLoadState !== 'ready') {
					return '';
				}
				const meta = this.jgTavernMeta || {};
				const every = meta.memoryAutoEveryMessages;
				const minM = meta.memoryAutoMinMinutesBetween;
				const divider = this.tx('memory_divider', ' · ');
				var rule = '';
				if (every != null && minM != null) {
					rule = this
						.tx('memory_rule', '约每 {every} 条、间隔至少 {min} 分钟自动整理')
						.replace('{every}', String(every))
						.replace('{min}', String(minM));
				}
				const mem = this.jgMemory;
				if (!mem || !mem.updatedAt) {
					var base = this.tx('memory_empty', '尚未生成长期记忆摘要');
					return rule ? base + divider + rule : base;
				}
				const time = this.formatMemoryTime(mem.updatedAt);
				const n = mem.factsCount != null ? Number(mem.factsCount) : 0;
				var prev = (mem.summaryPreview || '').trim();
				if (prev.length > 40) {
					prev = prev.slice(0, 40) + '...';
				}
				var line = this
					.tx('memory_summary', '记忆 {time} · {n} 条要点')
					.replace('{time}', time)
					.replace('{n}', String(isNaN(n) ? 0 : n));
				if (prev) {
					line += divider + prev;
				}
				return line;
			},
			showStopStream() {
				try {
					const tavernApi = require('@/common/tavernApi.js');
					return this.sending && tavernApi.jgStreamEnabled();
				} catch (e) {
					return false;
				}
			},
			t() {
				return (this.allText && this.allText['酒馆页']) || {};
			},
			title() {
				return this.char ? this.char.nickname : 'Chat';
			},
			charAvatar() {
				if (!this.char) return '/static/logo.png';
				const tavernApi = require('@/common/tavernApi.js');
				const u = this.char.avatar || this.char.cover;
				if (!u || String(u).trim() === '') return '/static/logo.png';
				return tavernApi.resolveJgAssetUrl(u) || '/static/logo.png';
			},
			charPreviewImage() {
				if (!this.char) return this.charAvatar;
				const tavernApi = require('@/common/tavernApi.js');
				const u = this.char.cover || this.char.avatar;
				if (!u || String(u).trim() === '') return this.charAvatar;
				return tavernApi.resolveJgAssetUrl(u) || this.charAvatar;
			},
			resolvedUserAvatar() {
				const u = this.userAvatar;
				if (u != null && String(u).trim() !== '') return u;
				return '/static/logo.png';
			},
			chatBackgroundUrl() {
				if (!this.char) return '';
				const tavernApi = require('@/common/tavernApi.js');
				const raw = this.char.chat_background_url || this.char.chatBackgroundUrl || '';
				return tavernApi.resolveJgAssetUrl(raw);
			},
			hasChatBackground() {
				return !!this.chatBackgroundUrl;
			},
			wrapStyle() {
				const url = this.chatBackgroundUrl;
				if (!url) return {};
				return {
					'--chat-bg-image': "url('" + String(url).replace(/'/g, '%27') + "')"
				};
			}
		},
		onLoad(q) {
			this.cid = (q && q.id) || '';
			const tavernApi = require('@/common/tavernApi.js');
			if (!tavernApi.jgEnabled()) {
				this.jgOn = true;
				this.char = null;
				this.jgChatLoadState = 'error';
				this.jgChatErrorMsg = this.tx('backend_disabled', '后端接口未开启');
				return;
			}
			this.jgOn = true;
			this.char = null;
			this.jgChatLoadState = 'loading';
			this.jgChatErrorMsg = '';
			this.refreshUserAvatar();
			this.loadJgSession();
		},
		onShow() {
			if (this.jgOn) {
				this.refreshUserAvatar();
			}
		},
		onUnload() {
			this.clearStopSyncTimer();
		},
		methods: {
			tx(key, fallback) {
				const extra = this.chatUi || {};
				const extraValue = key ? extra[key] : '';
				if (extraValue != null && String(extraValue).trim() !== '') return extraValue;
				const dict = this.t || {};
				const v = key ? dict[key] : '';
				if (v != null && String(v).trim() !== '') return v;
				return fallback || '';
			},
			jgErrMsg(e, fallback) {
				const tavernErrors = require('@/common/tavernErrors.js');
				return tavernErrors.getTavernErrorMessage(e, fallback);
			},
			resolveCommercialPrompt(e) {
				const tavernErrors = require('@/common/tavernErrors.js');
				return tavernErrors.resolveCommercialPrompt(e);
			},
			showErrorToast(message) {
				const text = String(message || '').trim();
				if (!text) return;
				uni.showToast({
					title: text.length > 120 ? text.slice(0, 120) + '...' : text,
					icon: 'none',
					duration: 3500
				});
			},
			handleCommercialError(e, fallback, options) {
				const msg = this.jgErrMsg(e, fallback);
				const prompt = this.resolveCommercialPrompt(e);
				if (prompt) {
					this.openCommercialPrompt(prompt, msg);
					if (!(options && options.skipToastWhenPrompted)) {
						this.showErrorToast(msg);
					}
					return { message: msg, prompted: true };
				}
				if (!options || options.toast !== false) {
					this.showErrorToast(msg);
				}
				return { message: msg, prompted: false };
			},
			openCommercialPrompt(prompt, rawMessage) {
				const data = prompt || {};
				this.commercialPrompt = {
					visible: true,
					title: data.title || this.tx('membership_title', '会员权益提示'),
					message: data.message || rawMessage || this.tx('membership_message', '当前操作需要更高权益，请先开通会员或充值。'),
					primaryText: data.primaryText || this.chatUi.openVip,
					primaryUrl: data.primaryUrl || '/pages/user/myvip',
					secondaryText: data.secondaryText || this.chatUi.recharge,
					secondaryUrl: data.secondaryUrl || '/pages/user/pay'
				};
			},
			closeCommercialPrompt() {
				this.commercialPrompt = {
					visible: false,
					title: '',
					message: '',
					primaryText: '',
					primaryUrl: '',
					secondaryText: '',
					secondaryUrl: ''
				};
			},
			openCharImagePreview() {
				if (!this.charPreviewImage) return;
				this.charImagePreviewVisible = true;
			},
			closeCharImagePreview() {
				this.charImagePreviewVisible = false;
			},
			goCommercial(url) {
				const target = String(url || '').trim();
				this.closeCommercialPrompt();
				if (!target) return;
				uni.navigateTo({
					url: target,
					fail: () => {
						uni.switchTab({ url: '/pages/user/user' });
					}
				});
			},
			formatMemoryTime(iso) {
				if (!iso) {
					return '';
				}
				try {
					const d = new Date(iso);
					if (isNaN(d.getTime())) {
						return String(iso).slice(0, 16);
					}
					const now = Date.now();
					const diff = now - d.getTime();
					if (diff < 60000) {
						return this.tx('memory_just_now', '刚刚');
					}
					if (diff < 3600000) {
						return formatLocaleText(this.tx('memory_minutes_ago', '{count}分钟前'), {
							count: Math.floor(diff / 60000)
						});
					}
					if (diff < 86400000) {
						return formatLocaleText(this.tx('memory_hours_ago', '{count}小时前'), {
							count: Math.floor(diff / 3600000)
						});
					}
					const mm = d.getMinutes();
					const mp = mm < 10 ? '0' + mm : '' + mm;
					return d.getMonth() + 1 + '/' + d.getDate() + ' ' + d.getHours() + ':' + mp;
				} catch (err) {
					return String(iso).slice(0, 16);
				}
			},
			applyMessagesEnvelope(pack) {
				var rows = [];
				var mem = null;
				var meta = null;
				if (Array.isArray(pack)) {
					rows = pack;
				} else if (pack && typeof pack === 'object') {
					rows = Array.isArray(pack.messages) ? pack.messages : [];
					mem = pack.memory != null ? pack.memory : null;
					meta = pack.tavernMeta != null ? pack.tavernMeta : null;
				}
				this.messages = rows.map((row) => this.normalizeChatRow(row));
				this.jgMemory = mem;
				this.jgTavernMeta = meta;
				this.invalidateReplySuggestions();
			},
			loadJgSession() {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) return;
				this.jgChatLoadState = 'loading';
				this.jgChatErrorMsg = '';
				this.jgMemory = null;
				this.jgTavernMeta = null;
				tavernApi
					.fetchCharacter(this.cid)
					.then((c) => {
						if (!c) {
							throw new Error(this.tx('character_missing', '角色不存在或已下架'));
						}
						this.char = c;
						this.applyVipGate();
						if (!this.char || this.char.unlocked === false) {
							return Promise.reject(new Error('vip'));
						}
						return tavernApi.fetchTavernMessages(this.cid, tavernApi.getClientUid());
					})
					.then((pack) => {
						this.applyMessagesEnvelope(pack);
						this.followBottom = true;
						this.jgChatLoadState = 'ready';
						this.$nextTick(() => {
							this.scrollTo = 'bottom-anchor';
						});
					})
					.catch((e) => {
						if (e && e.message === 'vip') return;
						this.jgChatLoadState = 'error';
						this.jgChatErrorMsg = this.jgErrMsg(
							e,
							this.tx('chat_load_failed', '网络异常，请重试')
						);
					});
			},
			retryJgChatLoad() {
				this.loadJgSession();
			},
			refreshUserAvatar() {
				try {
					const store = uni.getStorageSync('user');
					const a = store && store.avatar;
					if (a != null && String(a).trim() !== '') {
						const s = String(a).trim();
						if (s.indexOf('http://') === 0 || s.indexOf('https://') === 0 || s.indexOf('/') === 0) {
							this.userAvatar = s;
							return;
						}
						if (typeof this.$getimgsrc === 'function') {
							this.userAvatar = this.$getimgsrc(s) || '/static/logo.png';
							return;
						}
					}
				} catch (e) {}
				this.userAvatar = '/static/logo.png';
			},
			normalizeChatRow(m) {
				if (!m) return m;
				const swipes =
					Array.isArray(m.swipes) && m.swipes.length
						? m.swipes.map((s) => String(s))
						: m.text != null && String(m.text) !== ''
							? [String(m.text)]
							: [];
				let si = typeof m.swipeIndex === 'number' ? m.swipeIndex : 0;
				if (swipes.length && si >= swipes.length) si = swipes.length - 1;
				const text = swipes.length ? String(swipes[si] != null ? swipes[si] : '') : String(m.text || '');
				return { id: this.normalizeDbMessageId(m.id), role: m.role, text, swipes, swipeIndex: si };
			},
			invalidateReplySuggestions() {
				this.replySuggest = {
					visible: false,
					loading: false,
					error: '',
					items: [],
					contextKey: ''
				};
			},
			closeReplySuggestions() {
				this.replySuggest.visible = false;
				this.replySuggest.loading = false;
				this.replySuggest.error = '';
			},
			currentReplySuggestionKey() {
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					return '';
				}
				const last = this.messages && this.messages.length ? this.messages[this.messages.length - 1] : null;
				const lastText = last && last.text ? String(last.text) : '';
				return [String(this.cid || ''), String(anchor.targetAssistantMessageId || ''), lastText].join('|');
			},
			canOpenReplySuggestions() {
				if (this.sending || !this.jgOn || !this.char || this.jgChatLoadState !== 'ready') {
					return false;
				}
				return this.assistantTailActionState().ok;
			},
			toggleReplySuggestions() {
				if (!this.canOpenReplySuggestions()) {
					const anchor = this.assistantTailActionState();
					if (anchor.reason === 'sending') {
						uni.showToast({ title: this.tx('reply_help_wait', '等这轮回复完成后再使用帮答'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('reply_help_need_ai', '需要先有一条可用的 AI 回复'), icon: 'none' });
					}
					return;
				}
				if (this.replySuggest.visible) {
					this.closeReplySuggestions();
					return;
				}
				this.replySuggest.visible = true;
				this.refreshReplySuggestions(false);
			},
			refreshReplySuggestions(force) {
				if (!this.canOpenReplySuggestions()) {
					return Promise.resolve([]);
				}
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const key = this.currentReplySuggestionKey();
				if (!force && this.replySuggest.contextKey === key && this.replySuggest.items.length) {
					this.replySuggest.visible = true;
					return Promise.resolve(this.replySuggest.items);
				}
				this.replySuggest.visible = true;
				this.replySuggest.loading = true;
				this.replySuggest.error = '';
				return tavernApi
					.fetchTavernReplySuggestions({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						content: String(this.draft || '').trim()
					})
					.then((items) => {
						const list = Array.isArray(items)
							? items
									.map((item) => String(item == null ? '' : item).trim())
									.filter((item) => item !== '')
							: [];
						if (!list.length) {
							throw new Error(this.tx('reply_help_empty', '这次没有拿到可用建议，换一批再试试'));
						}
						this.replySuggest.items = list;
						this.replySuggest.contextKey = key;
						this.replySuggest.error = '';
						return list;
					})
					.catch((e) => {
						this.replySuggest.items = [];
						this.replySuggest.error = this.jgErrMsg(
							e,
							this.tx('reply_help_failed', 'AI帮答暂时不可用，请稍后再试')
						);
						return [];
					})
					.finally(() => {
						this.replySuggest.loading = false;
					});
			},
			applyReplySuggestion(text) {
				const value = String(text || '').trim();
				if (!value) return;
				this.draft = value;
				this.replySuggest.visible = false;
			},
			followScrollNextTick() {
				if (!this.followBottom) return;
				this.$nextTick(() => {
					this.scrollTo = '';
					this.$nextTick(() => {
						this.scrollTo = 'bottom-anchor';
					});
				});
			},
			onChatScroll(e) {
				const d = (e && e.detail) || {};
				if (typeof d.deltaY === 'number' && d.deltaY < -2) {
					this.followBottom = false;
				}
			},
			onChatScrollToLower() {
				this.followBottom = true;
			},
			normalizeDbMessageId(id) {
				if (id == null) return '';
				const s = String(id).trim();
				if (s.startsWith('db_')) return s;
				if (/^\d+$/.test(s)) return 'db_' + s;
				return s;
			},
			lastAssistantTargetPayload() {
				const n = this.messages.length;
				if (n === 0) return { ok: false, reason: 'empty' };
				const last = this.messages[n - 1];
				if (!last || last.role !== 'char') {
					return { ok: false, reason: 'not_char' };
				}
				if (!String(last.text || '').trim()) {
					return { ok: false, reason: 'empty_char' };
				}
				const nid = this.normalizeDbMessageId(last.id);
				if (!nid.startsWith('db_')) {
					return { ok: false, reason: 'pending_sync' };
				}
				return { ok: true, targetAssistantMessageId: nid };
			},
			assistantTailActionState() {
				if (this.sending) {
					return { ok: false, reason: 'sending' };
				}
				if (!this.jgOn || !this.char) {
					return { ok: false, reason: 'unready' };
				}
				const anchor = this.lastAssistantTargetPayload();
				if (!anchor.ok) {
					return anchor;
				}
				return anchor;
			},
			assistantTailActionHint() {
				const state = this.assistantTailActionState();
				if (state.ok) {
					return '';
				}
				if (state.reason === 'sending') {
					return this.tx('tail_sending', '当前回复还在生成中，等这一轮结束后才能续写或重生。');
				}
				if (state.reason === 'empty') {
					return this.tx('tail_empty', '续写和重生都需要先有一条 AI 回复，空白会话不能直接使用。');
				}
				if (state.reason === 'pending_sync') {
					return this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。');
				}
				if (state.reason === 'empty_char') {
					return this.tx('tail_empty_char', '最后一条 AI 回复内容为空，暂时不能续写或重生。');
				}
				if (state.reason === 'not_char') {
					return this.tx('tail_not_char', '续写和重生只作用于当前会话最后一条 AI 回复。');
				}
				return '';
			},
			clearStopSyncTimer() {
				if (this.stopRefreshTimer) {
					clearTimeout(this.stopRefreshTimer);
					this.stopRefreshTimer = null;
				}
			},
			queueStopSync(delay) {
				if (!this.jgOn || !this.char) return;
				this.clearStopSyncTimer();
				this.stopRefreshTimer = setTimeout(() => {
					this.stopRefreshTimer = null;
					this.refreshJgMessages()
						.then(() => {
							this.followScrollNextTick();
						})
						.catch(() => {});
				}, typeof delay === 'number' ? delay : 700);
			},
			patchLastOptimisticUserId(userMessageId) {
				const uid = this.normalizeDbMessageId(userMessageId);
				if (!uid.startsWith('db_')) return;
				for (let i = this.messages.length - 1; i >= 0; i--) {
					const row = this.messages[i];
					if (row && row.role === 'user' && String(row.id).indexOf('u_') === 0) {
						this.$set(row, 'id', uid);
						return;
					}
				}
			},
			stopGeneration() {
				this.queueStopSync(700);
				if (this.streamAbortController) {
					try {
						this.streamAbortController.abort();
					} catch (err) {}
					this.streamAbortController = null;
				}
				try {
					const tavernApi = require('@/common/tavernApi.js');
					if (tavernApi.jgEnabled() && this.char && this.cid) {
						const cid = Number(this.char && this.char.id) || Number(this.cid);
						tavernApi
							.postTavernChatStop({
								characterId: cid,
								clientUid: tavernApi.getClientUid()
							})
							.finally(() => {
								this.queueStopSync(900);
							})
							.catch(function () {});
					}
				} catch (e) {}
			},
			swipeLabel(m) {
				const s = (m && m.swipes) || [];
				const i = typeof m.swipeIndex === 'number' ? m.swipeIndex : 0;
				const safe = s.length ? Math.min(Math.max(0, i), s.length - 1) : 0;
				return s.length ? safe + 1 + '/' + s.length : '';
			},
			mergeContinuationText(prefix, suffix) {
				const base = String(prefix || '');
				const ext = String(suffix || '');
				if (!ext) return base;
				if (!base) return ext.replace(/^\s+/, '');
				const last = base.slice(-1);
				const first = ext.charAt(0);
				if (/\s/.test(last) || /\s/.test(first)) return base + ext;
				if (/[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/.test(last) || /[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/.test(first)) {
					return base + ext;
				}
				if (',.;:!?)]}"\''.indexOf(first) >= 0) {
					return base + ext;
				}
				if (/[A-Za-z0-9]$/.test(last) && /^[A-Za-z0-9]/.test(first)) {
					return base + ' ' + ext.replace(/^\s+/, '');
				}
				return base + ext;
			},
			swipeCharMessage(m, delta) {
				if (this.sending || !this.jgOn || !m || m.role !== 'char') return;
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				tavernApi
					.postTavernSwipeSelect({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						messageId: this.normalizeDbMessageId(m.id),
						delta: delta
					})
					.then((d) => {
						if (!d) return;
						const row = this.normalizeChatRow({
							id: d.id || m.id,
							role: d.role || 'char',
							text: d.text,
							swipes: d.swipes,
							swipeIndex: d.swipeIndex
						});
						const idx = this.messages.indexOf(m);
						if (idx >= 0) {
							this.$set(this.messages, idx, row);
						}
					})
					.catch((e) => {
						uni.showToast({ title: this.jgErrMsg(e, this.tx('swipe_failed', '切换失败')), icon: 'none' });
					});
			},
			refreshJgMessages() {
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				return tavernApi.fetchTavernMessages(cid, tavernApi.getClientUid()).then((pack) => {
					this.applyMessagesEnvelope(pack);
				});
			},
			canEditUserMessage(m) {
				if (!this.jgOn || !m || m.role !== 'user') return false;
				const id = this.normalizeDbMessageId(m.id);
				return id.indexOf('db_') === 0;
			},
			openEditUserMessage(m) {
				if (this.sending || !this.canEditUserMessage(m)) return;
				this.editOverlay = {
					visible: true,
					messageId: this.normalizeDbMessageId(m.id),
					draft: String(m.text || ''),
					saving: false
				};
			},
			closeEditUser() {
				if (this.editOverlay.saving) return;
				this.editOverlay = { visible: false, messageId: '', draft: '', saving: false };
			},
			submitEditUser() {
				if (this.editOverlay.saving) return;
				const t = String(this.editOverlay.draft || '').trim();
				if (!t) {
					uni.showToast({ title: this.tx('save_empty', '内容不能为空'), icon: 'none' });
					return;
				}
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				this.editOverlay.saving = true;
				tavernApi
					.postTavernEditUserBranch({
						characterId: cid,
						clientUid: tavernApi.getClientUid(),
						messageId: this.normalizeDbMessageId(this.editOverlay.messageId),
						newText: t
					})
					.then(() => this.refreshJgMessages())
					.then(() => {
						this.closeEditUser();
						this.followBottom = true;
						this.$nextTick(() => {
							this.scrollTo = 'bottom-anchor';
						});
						uni.showToast({ title: this.tx('save_success', '已更新，后续消息已清空'), icon: 'none' });
					})
					.catch((e) => {
						uni.showToast({ title: this.jgErrMsg(e, this.tx('save_failed', '保存失败')), icon: 'none', duration: 3200 });
					})
					.finally(() => {
						if (this.editOverlay && this.editOverlay.visible) {
							this.$set(this.editOverlay, 'saving', false);
						}
					});
			},
			onMarkdownTap(e) {
				/* #ifdef H5 */
				try {
					let el = e.target;
					for (let i = 0; i < 12 && el; i++) {
						const tag = el.tagName ? String(el.tagName).toUpperCase() : '';
						if (tag === 'PRE') {
							const txt = (el.textContent || el.innerText || '').trim();
							if (txt) {
								uni.setClipboardData({ data: txt });
								uni.showToast({ title: this.tx('copy_code_success', '代码已复制'), icon: 'none' });
							}
							return;
						}
						el = el.parentElement;
					}
				} catch (err) {}
				/* #endif */
			},
			applyVipGate() {
				if (this.char && !this.char.unlocked) {
					this.jgChatLoadState = 'error';
					this.jgChatErrorMsg = this.tx('need_vip', '当前角色仅会员可用');
					this.openCommercialPrompt(
						{
							title: this.tx('vip_gate_title', '当前角色需要会员权限'),
							message: this.tx('vip_gate_message', '这个角色已设置为会员专属。开通会员后即可进入聊天、续写和重生。'),
							primaryText: this.chatUi.openVip,
							primaryUrl: '/pages/user/myvip',
							secondaryText: this.chatUi.recharge,
							secondaryUrl: '/pages/user/pay'
						},
						this.jgChatErrorMsg
					);
				}
			},
			goBack() {
				this.util.safeNavigateBack('/pages/tavern/tavernInbox');
			},
			goPersona() {
				const query = this.cid ? '?id=' + encodeURIComponent(this.cid) : '';
				uni.navigateTo({ url: '/pages/tavern/chatPersona' + query });
			},
			mdHtml(text) {
				const { renderChatMarkdown } = require('@/common/chatMarkdown.js');
				return renderChatMarkdown(text);
			},
			onRegen() {
				if (this.sending || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const n = this.messages.length;
				if (n === 0 || this.messages[n - 1].role !== 'char') {
					uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					return;
				}
				const last = this.messages[n - 1];
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					if (anchor.reason === 'pending_sync') {
						uni.showToast({ title: this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。'), icon: 'none' });
					} else if (anchor.reason === 'empty_char') {
						uni.showToast({ title: this.tx('tail_regen_empty', '最后一条 AI 回复为空，暂时不能重新生成'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					}
					return;
				}
				const payload = {
					characterId: cid,
					clientUid: tavernApi.getClientUid(),
					targetAssistantMessageId: anchor.targetAssistantMessageId
				};
				this.sending = true;

				if (tavernApi.jgStreamEnabled()) {
					const backup = String(last.text || '');
					let started = false;
					this.streamAbortController = new AbortController();
					this.followBottom = true;
					tavernApi
						.postTavernRegenerateStream(
							payload,
							{
								onDelta: (piece) => {
									if (!started) {
										started = true;
										this.$set(last, 'text', piece);
									} else {
										const next = (last.text || '') + piece;
										this.$set(last, 'text', next);
									}
									this.followScrollNextTick();
								},
								onDone: (data) => {
									const cancelled = data && data.cancelled;
									const c = data && data.content != null ? String(data.content).trim() : '';
									if (cancelled && !c) {
										this.$set(last, 'text', backup);
									} else if (data && data.content != null) {
										this.$set(last, 'text', String(data.content).trim());
									}
									if (Array.isArray(data && data.swipes)) {
										this.$set(
											last,
											'swipes',
											data.swipes.map((x) => String(x))
										);
										if (typeof data.swipeIndex === 'number') {
											this.$set(last, 'swipeIndex', data.swipeIndex);
										}
									}
									if (cancelled && c) {
								uni.showToast({ title: this.tx('stopped_keep', '已停止，已保留本次生成的内容'), icon: 'none' });
										this.queueStopSync(700);
									}
									this.followScrollNextTick();
								},
								onAbort: () => {
									if (!started) {
										this.$set(last, 'text', backup);
									}
									this.queueStopSync(700);
								uni.showToast({ title: this.tx('stopped', '已停止'), icon: 'none' });
								},
								onError: (e) => {
									const result = this.handleCommercialError(e, this.tx('regen_failed', '重新生成失败'), {
										skipToastWhenPrompted: true
									});
									if (!result.prompted) {
										this.showErrorToast(result.message);
									}
									this.$set(last, 'text', backup);
								}
							},
							{ signal: this.streamAbortController.signal }
						)
						.finally(() => {
							this.streamAbortController = null;
							this.sending = false;
						});
					return;
				}

				tavernApi
					.postTavernRegenerate(payload)
					.then((d) => {
						if (d && d.content != null) {
							this.$set(last, 'text', String(d.content));
						}
						return this.refreshJgMessages();
					})
					.then(() => {
						this.$nextTick(() => {
							this.scrollTo = 'bottom-anchor';
						});
					})
					.catch((e) => {
						const result = this.handleCommercialError(e, this.tx('regen_failed', '重新生成失败'), {
							skipToastWhenPrompted: true
						});
						if (!result.prompted) {
							this.showErrorToast(result.message);
						}
					})
					.finally(() => {
						this.sending = false;
					});
			},
			onContinue() {
				if (this.sending || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				const n = this.messages.length;
				if (n === 0 || this.messages[n - 1].role !== 'char') {
					uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					return;
				}
				const last = this.messages[n - 1];
				const prefix = String(last.text || '');
				const anchor = this.assistantTailActionState();
				if (!anchor.ok) {
					if (anchor.reason === 'pending_sync') {
						uni.showToast({ title: this.tx('tail_pending_sync', '最后一条 AI 回复还在同步，请稍等片刻再试。'), icon: 'none' });
					} else if (anchor.reason === 'empty_char') {
						uni.showToast({ title: this.tx('tail_empty_char', '最后一条 AI 回复内容为空，暂时不能续写或重生。'), icon: 'none' });
					} else {
						uni.showToast({ title: this.tx('tail_last_ai', '请确认最后一条消息是 AI 回复'), icon: 'none' });
					}
					return;
				}
				const payload = {
					characterId: cid,
					clientUid: tavernApi.getClientUid(),
					targetAssistantMessageId: anchor.targetAssistantMessageId
				};
				this.sending = true;

				if (tavernApi.jgStreamEnabled()) {
					let acc = '';
					this.streamAbortController = new AbortController();
					this.followBottom = true;
					tavernApi
						.postTavernContinueStream(
							payload,
							{
								onDelta: (piece) => {
									acc += piece;
									this.$set(last, 'text', this.mergeContinuationText(prefix, acc));
									this.followScrollNextTick();
								},
								onDone: (data) => {
									if (data && data.content != null) {
										this.$set(last, 'text', String(data.content).trim());
									}
									if (Array.isArray(data && data.swipes)) {
										this.$set(
											last,
											'swipes',
											data.swipes.map((x) => String(x))
										);
										if (typeof data.swipeIndex === 'number') {
											this.$set(last, 'swipeIndex', data.swipeIndex);
										}
									}
									if (data && data.cancelled && acc) {
								uni.showToast({ title: this.tx('stopped_keep', '已停止，已保留本次生成的内容'), icon: 'none' });
										this.queueStopSync(700);
									}
									// 商用一致性：流式续写完成后也刷新一次消息列表，避免本地临时文本被状态刷新覆盖
									this.refreshJgMessages().catch(() => {});
									this.followScrollNextTick();
								},
								onAbort: () => {
									this.queueStopSync(700);
								uni.showToast({ title: this.tx('stopped', '已停止'), icon: 'none' });
								},
								onError: (e) => {
									const result = this.handleCommercialError(e, this.tx('continue_failed', '续写失败'), {
										skipToastWhenPrompted: true
									});
									if (!result.prompted) {
										this.showErrorToast(result.message);
									}
									this.$set(last, 'text', this.mergeContinuationText(prefix, acc));
								}
							},
							{ signal: this.streamAbortController.signal }
						)
						.finally(() => {
							this.streamAbortController = null;
							this.sending = false;
						});
					return;
				}

				tavernApi
					.postTavernContinue(payload)
					.then((d) => {
						if (d && d.content != null) {
							this.$set(last, 'text', String(d.content));
						}
						return this.refreshJgMessages();
					})
					.then(() => {
						this.$nextTick(() => {
							this.scrollTo = 'bottom-anchor';
						});
					})
					.catch((e) => {
						const result = this.handleCommercialError(e, this.tx('continue_failed', '续写失败'), {
							skipToastWhenPrompted: true
						});
						if (!result.prompted) {
							this.showErrorToast(result.message);
						}
					})
					.finally(() => {
						this.sending = false;
					});
			},
			onRestart() {
				if (this.sending || !this.jgOn || !this.char) return;
				this.closeReplySuggestions();
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				uni.showModal({
					title: this.tx('restart_title', '重新开始聊天'),
					content: this.tx('restart_desc', '将清空与当前角色的本轮会话记录，但不会删除角色。确定继续吗？'),
					confirmText: this.tx('confirm', '确定'),
					cancelText: this.tx('cancel', '取消'),
					success: (res) => {
						if (!res.confirm) return;
						this.sending = true;
						tavernApi
							.postTavernSessionRestart({
								characterId: cid,
								clientUid: tavernApi.getClientUid()
							})
							.then(() => this.refreshJgMessages())
							.then(() => {
								this.followBottom = true;
								this.$nextTick(() => {
									this.scrollTo = 'bottom-anchor';
								});
								uni.showToast({
									title: this.tx('restart_success', '已清空，可重新对话'),
									icon: 'none'
								});
							})
							.catch((e) => {
								uni.showToast({
									title: this.jgErrMsg(e, this.tx('restart_failed', '操作失败')),
									icon: 'none',
									duration: 3200
								});
							})
							.finally(() => {
								this.sending = false;
							});
					}
				});
			},
			onMem() {
				if (this.sending || !this.jgOn || !this.char) return;
				const tavernApi = require('@/common/tavernApi.js');
				const cid = Number(this.char && this.char.id) || Number(this.cid);
				this.sending = true;
				tavernApi
					.postTavernMemoryRefresh({ characterId: cid, clientUid: tavernApi.getClientUid() })
					.then(() => this.refreshJgMessages())
					.then(() => {
						uni.showToast({
							title: this.tx('memory_refresh_success', '已根据近期对话更新记忆摘要'),
							icon: 'none'
						});
					})
					.catch((e) => {
						uni.showToast({
							title: this.jgErrMsg(e, this.tx('memory_refresh_failed', '记忆刷新失败')),
							icon: 'none',
							duration: 3200
						});
					})
					.finally(() => {
						this.sending = false;
					});
			},
			send() {
				const text = (this.draft || '').trim();
				if (!text || !this.char || this.sending) return;
				this.closeReplySuggestions();
				const uid = 'u_' + Date.now();
				this.messages = this.messages.concat({ id: uid, role: 'user', text });
				this.draft = '';
				this.sending = true;
				this.followBottom = true;
				this.$nextTick(() => {
					this.scrollTo = 'm-' + uid;
				});

				const tavernApi = require('@/common/tavernApi.js');
				if (tavernApi.jgEnabled()) {
					const cid = Number(this.char && this.char.id) || Number(this.cid);
					const clientUid = tavernApi.getClientUid();
					const payload = {
						characterId: cid,
						clientUid,
						content: text,
						temperature: 0.85
					};

					if (tavernApi.jgStreamEnabled()) {
						const rid = 'r_' + Date.now();
						this.streamAbortController = new AbortController();
						this.messages = this.messages.concat({
							id: rid,
							role: 'char',
							text: '',
							swipes: [''],
							swipeIndex: 0
						});
						tavernApi
							.postTavernChatStream(
								payload,
								{
									onDelta: (piece) => {
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid) {
											const next = (last.text || '') + piece;
											this.$set(last, 'text', next);
										}
										this.followScrollNextTick();
									},
									onDone: (data) => {
										const row = this.messages[this.messages.length - 1];
										if (!row || row.id !== rid) {
											this.followScrollNextTick();
											return;
										}
										if (data && data.content != null) {
											this.$set(row, 'text', String(data.content).trim());
										}
										if (data && data.messageId) {
											this.$set(row, 'id', this.normalizeDbMessageId(data.messageId));
										}
										if (data && data.userMessageId) {
											this.patchLastOptimisticUserId(data.userMessageId);
										}
										if (Array.isArray(data && data.swipes)) {
											this.$set(
												row,
												'swipes',
												data.swipes.map((x) => String(x))
											);
											if (typeof data.swipeIndex === 'number') {
												this.$set(row, 'swipeIndex', data.swipeIndex);
											}
										}
										if (data && data.cancelled) {
											this.queueStopSync(700);
									uni.showToast({ title: this.tx('stopped', '已停止'), icon: 'none' });
										}
										this.followScrollNextTick();
									},
									onAbort: () => {
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid && !String(last.text || '').trim()) {
											this.messages = this.messages.filter((x) => x.id !== rid);
										}
										this.queueStopSync(700);
								uni.showToast({ title: this.tx('stopped', '已停止'), icon: 'none' });
									},
									onError: (e) => {
										const result = this.handleCommercialError(e, this.tx('chat_failed', '对话失败'), {
											skipToastWhenPrompted: true
										});
										const msg = result.message;
										if (!result.prompted) {
											this.showErrorToast(msg);
										}
										const last = this.messages[this.messages.length - 1];
										if (last && last.id === rid) {
											const wrapped = '[' + msg + ']';
											this.$set(last, 'text', (last.text || '').trim() ? last.text + '\n' + wrapped : wrapped);
										}
									}
								},
								{ signal: this.streamAbortController.signal }
							)
							.finally(() => {
								this.streamAbortController = null;
								this.sending = false;
							});
						return;
					}

					tavernApi
						.postTavernChat(payload)
						.then((data) => {
							if (data && data.userMessageId) {
								this.patchLastOptimisticUserId(data.userMessageId);
							}
							const aid =
								data && data.messageId ? this.normalizeDbMessageId(data.messageId) : '';
							const rid = aid || 'r_' + Date.now();
							const raw = data && data.content;
							const reply =
								raw != null && String(raw).trim() !== ''
									? String(raw).trim()
									: this.tx('empty_ai', '模型未返回内容');
							const sw =
								Array.isArray(data && data.swipes) && data.swipes.length
									? data.swipes.map((x) => String(x))
									: [reply];
							const si = typeof (data && data.swipeIndex) === 'number' ? data.swipeIndex : 0;
							this.messages = this.messages.concat({
								id: rid,
								role: 'char',
								text: reply,
								swipes: sw,
								swipeIndex: si
							});
							this.$nextTick(() => {
								this.scrollTo = 'm-' + rid;
							});
						})
						.catch((e) => {
							const rid = 'r_' + Date.now();
							const result = this.handleCommercialError(e, this.tx('chat_failed', '对话失败'), {
								skipToastWhenPrompted: true
							});
							const msg = result.message;
							if (!result.prompted) {
								this.showErrorToast(msg);
							}
							this.messages = this.messages.concat({
								id: rid,
								role: 'char',
								text: '[' + msg + ']'
							});
							this.$nextTick(() => {
								this.scrollTo = 'm-' + rid;
							});
						})
						.finally(() => {
							this.sending = false;
						});
					return;
				}

					uni.showToast({ title: this.tx('backend_disabled', '后端接口未开启'), icon: 'none' });
					this.sending = false;
					return;
				}

			}
	};
</script>

<style scoped lang="scss">
	$bg: #12122b;
	$card: #1a1a38;
	$text: #f1f5f9;

	.wrap {
		position: relative;
		height: 100vh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		isolation: isolate;
		background:
			radial-gradient(circle at top left, rgba(91, 33, 182, 0.22), transparent 32%),
			linear-gradient(180deg, rgba(18, 18, 43, 0.98) 0%, rgba(12, 15, 28, 0.98) 100%);
	}

	.wrap::before,
	.wrap::after {
		content: '';
		position: absolute;
		inset: 0;
		pointer-events: none;
	}

	.wrap--with-bg::before {
		background-image: var(--chat-bg-image);
		background-size: cover;
		background-position: center center;
		background-repeat: no-repeat;
		filter: saturate(1.08) brightness(1.03) contrast(1.03);
		transform: scale(1.015);
	}

	.wrap--with-bg::after {
		background:
			linear-gradient(180deg, rgba(7, 10, 24, 0.06) 0%, rgba(9, 11, 24, 0.08) 24%, rgba(9, 11, 24, 0.16) 58%, rgba(9, 11, 24, 0.26) 100%);
	}

	.wrap > * {
		position: relative;
		z-index: 1;
	}

	.chat-fill {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 48rpx 40rpx;
		gap: 28rpx;
		min-height: 400rpx;
	}

	.chat-fill-txt {
		font-size: 28rpx;
		color: #94a3b8;
		text-align: center;
		line-height: 1.55;
		padding: 0 24rpx;
	}

	.chat-fill--err .chat-fill-txt {
		color: #fca5a5;
	}

	.chat-fill-retry {
		padding: 16rpx 44rpx;
		background: linear-gradient(90deg, #6366f1, #8b5cf6);
		color: #fff;
		border-radius: 999rpx;
		font-size: 28rpx;
		font-weight: 600;
	}

	.chat-fill-back {
		font-size: 26rpx;
		color: #a78bfa;
		padding: 12rpx;
	}

	.nav-link {
		font-size: 28rpx;
		color: #a78bfa;
		padding: 0 16rpx;
	}

	.tool-bar {
		flex-shrink: 0;
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		padding: 12rpx 24rpx;
		background: rgba(10, 14, 28, 0.18);
		backdrop-filter: blur(12rpx);
		border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
	}

	.tool-i {
		font-size: 24rpx;
		color: #e2e8f0;
		padding: 8rpx 20rpx;
		border-radius: 999rpx;
		background: rgba(124, 58, 237, 0.2);
	}

	.tool-i--disabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.tool-hint {
		flex-shrink: 0;
		padding: 0 24rpx 10rpx;
		background: rgba(10, 14, 28, 0.12);
		backdrop-filter: blur(8rpx);
	}

	.tool-hint-txt {
		font-size: 20rpx;
		color: #64748b;
		line-height: 1.4;
	}

	.memory-bar {
		flex-shrink: 0;
		padding: 10rpx 24rpx 14rpx;
		background: rgba(10, 14, 28, 0.14);
		backdrop-filter: blur(10rpx);
		border-bottom: 1rpx solid rgba(148, 163, 184, 0.12);
	}

	.memory-bar-txt {
		font-size: 22rpx;
		color: #94a3b8;
		line-height: 1.45;
		display: block;
	}

	.chat-scroll {
		flex: 1;
		height: 0;
		padding: 20rpx 24rpx;
		box-sizing: border-box;
	}

	.msg-row {
		display: flex;
		align-items: flex-end;
		margin-bottom: 24rpx;

		&.them {
			justify-content: flex-start;
		}

		&.me {
			justify-content: flex-end;
		}
	}

	.av {
		width: 72rpx;
		height: 72rpx;
		border-radius: 50%;
		flex-shrink: 0;
		overflow: hidden;
		/* #ifdef H5 */
		object-fit: cover;
		/* #endif */
	}

	.av--char {
		/* #ifdef H5 */
		cursor: zoom-in;
		transition: transform 0.22s ease, box-shadow 0.22s ease;
		/* #endif */
	}

	/* #ifdef H5 */
	.av--char:hover {
		transform: translateY(-2rpx) scale(1.03);
		box-shadow: 0 12rpx 20rpx rgba(12, 18, 34, 0.26);
	}
	/* #endif */

	.bubble {
		max-width: 70%;
		margin: 0 16rpx;
		padding: 20rpx 24rpx;
		border-radius: 22rpx;
		background: linear-gradient(180deg, rgba(10, 12, 18, 0.52) 0%, rgba(10, 12, 18, 0.34) 100%);
		backdrop-filter: blur(12rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.14);
	}

	.msg-row.me .bubble {
		background: linear-gradient(135deg, rgba(124, 58, 237, 0.44) 0%, rgba(219, 39, 119, 0.3) 100%);
		backdrop-filter: blur(18rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.12);
	}

	.msg-row.them .bubble {
		max-width: 78%;
		padding: 18rpx 20rpx;
	}

	.txt {
		font-size: 28rpx;
		color: $text;
		line-height: 1.45;
		word-break: break-word;
	}

	.md-inner {
		font-size: 30rpx;
		color: rgba(255, 255, 255, 0.94);
		line-height: 1.82;
		word-break: break-word;
		text-shadow: 0 1rpx 7rpx rgba(0, 0, 0, 0.22);
	}

	.md-inner >>> .st-chat-render {
		display: flex;
		flex-direction: column;
		gap: 10rpx;
	}

	.md-inner >>> .st-chat-seg {
		display: block;
	}

	.md-inner >>> .st-chat-seg > *:first-child {
		margin-top: 0;
	}

	.md-inner >>> .st-chat-seg > *:last-child {
		margin-bottom: 0;
	}

	.md-inner >>> .st-chat-seg--speech {
		color: #ffc2d3;
		font-weight: 700;
		font-size: 29rpx;
		line-height: 1.72;
		letter-spacing: 0.08rpx;
		text-shadow: 0 1rpx 10rpx rgba(0, 0, 0, 0.24);
	}

	.md-inner >>> .st-chat-seg--speech p {
		color: inherit;
		display: block;
	}

	.md-inner >>> .st-chat-seg--thought,
	.md-inner >>> .st-chat-seg--action {
		color: rgba(255, 255, 255, 0.82);
		font-weight: 400;
		font-size: 28rpx;
		line-height: 1.84;
	}

	.md-inner >>> .st-chat-seg--thought p,
	.md-inner >>> .st-chat-seg--action p {
		color: inherit;
	}

	.md-inner >>> .st-chat-seg--thought {
		font-style: italic;
		color: rgba(240, 228, 255, 0.94);
		padding-left: 12rpx;
		border-left: 4rpx solid rgba(240, 228, 255, 0.18);
	}

	.md-inner >>> .st-chat-seg--action {
		font-style: italic;
		letter-spacing: 0.12rpx;
		padding-left: 12rpx;
	}

	.md-inner >>> .st-chat-seg--narration {
		color: rgba(255, 255, 255, 0.98);
		font-weight: 400;
		font-size: 29rpx;
		line-height: 1.82;
	}

	.md-inner >>> p {
		margin: 0.14em 0;
	}

	.md-inner >>> p:first-child {
		margin-top: 0;
	}

	.md-inner >>> p:last-child {
		margin-bottom: 0;
	}

	.md-inner >>> code {
		background: rgba(255, 255, 255, 0.12);
		padding: 2rpx 8rpx;
		border-radius: 6rpx;
		font-size: 26rpx;
		color: rgba(255, 255, 255, 0.96);
	}

	.md-inner >>> pre {
		background: rgba(0, 0, 0, 0.24);
		padding: 16rpx;
		border-radius: 12rpx;
		overflow-x: auto;
		margin: 0.4em 0;
		/* #ifdef H5 */
		cursor: pointer;
		/* #endif */
	}

	.user-edit-tag {
		display: block;
		margin-top: 8rpx;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.55);
	}

	.msg-row.me .user-edit-tag {
		text-align: right;
	}

	.edit-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.55);
		z-index: 2000;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 32rpx;
		padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}

	.commercial-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 2100;
		background: rgba(5, 8, 24, 0.72);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 32rpx;
		box-sizing: border-box;
	}

	.char-image-mask {
		position: fixed;
		inset: 0;
		z-index: 2300;
		background: rgba(3, 6, 16, 0.94);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 36rpx;
		box-sizing: border-box;
	}

	.char-image-shell {
		position: relative;
		width: 100%;
		max-width: 760rpx;
		max-height: calc(100vh - 72rpx);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.char-image-close {
		position: absolute;
		top: -12rpx;
		right: -4rpx;
		z-index: 2;
		width: 72rpx;
		height: 72rpx;
		line-height: 72rpx;
		text-align: center;
		font-size: 48rpx;
		color: rgba(255, 255, 255, 0.92);
	}

	.char-image-full {
		width: 100%;
		height: calc(100vh - 120rpx);
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.03);
	}

	.commercial-card {
		width: 100%;
		max-width: 620rpx;
		padding: 34rpx 30rpx 28rpx;
		border-radius: 26rpx;
		background: linear-gradient(180deg, rgba(24, 24, 48, 0.98) 0%, rgba(20, 20, 40, 0.98) 100%);
		border: 1rpx solid rgba(216, 180, 254, 0.22);
		box-shadow: 0 24rpx 72rpx rgba(15, 23, 42, 0.42);
	}

	.commercial-title {
		display: block;
		font-size: 34rpx;
		font-weight: 700;
		color: #f8fafc;
	}

	.commercial-sub {
		display: block;
		margin-top: 18rpx;
		font-size: 26rpx;
		line-height: 1.7;
		color: #cbd5e1;
	}

	.commercial-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 28rpx;
	}

	.commercial-btn {
		flex: 1;
		height: 76rpx;
		line-height: 76rpx;
		text-align: center;
		border-radius: 999rpx;
		font-size: 26rpx;
		font-weight: 600;
	}

	.commercial-btn--ghost {
		color: #cbd5e1;
		background: rgba(148, 163, 184, 0.12);
	}

	.commercial-btn--muted {
		color: #f8fafc;
		background: rgba(91, 33, 182, 0.34);
	}

	.commercial-btn--primary {
		color: #fff;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
	}

	.edit-panel {
		width: 100%;
		max-height: 70vh;
		background: #1a1a38;
		border-radius: 20rpx;
		padding: 28rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-sizing: border-box;
	}

	.edit-title {
		display: block;
		font-size: 32rpx;
		color: #f1f5f9;
		margin-bottom: 12rpx;
	}

	.edit-sub {
		display: block;
		font-size: 24rpx;
		color: #94a3b8;
		line-height: 1.4;
		margin-bottom: 20rpx;
	}

	.edit-ta {
		width: 100%;
		min-height: 200rpx;
		padding: 20rpx;
		font-size: 28rpx;
		color: #f1f5f9;
		background: rgba(0, 0, 0, 0.25);
		border-radius: 12rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-sizing: border-box;
		margin-bottom: 24rpx;
	}

	.edit-actions {
		display: flex;
		justify-content: flex-end;
		gap: 24rpx;
	}

	.edit-btn {
		font-size: 28rpx;
		padding: 16rpx 28rpx;
		border-radius: 12rpx;
	}

	.edit-btn--muted {
		color: #94a3b8;
	}

	.edit-btn--primary {
		color: #fff;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
	}

	.md-inner >>> a {
		color: #d8b4fe;
	}

	.reply-help-panel {
		flex-shrink: 0;
		margin: 0 18rpx 10rpx;
		padding: 18rpx 18rpx 12rpx;
		border-radius: 24rpx;
		background: rgba(10, 14, 28, 0.22);
		backdrop-filter: blur(16rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow: 0 14rpx 32rpx rgba(15, 23, 42, 0.16);
	}

	.reply-help-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin-bottom: 14rpx;
	}

	.reply-help-title {
		font-size: 24rpx;
		font-weight: 700;
		color: #f8fafc;
	}

	.reply-help-head-actions {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.reply-help-head-btn {
		padding: 8rpx 18rpx;
		border-radius: 999rpx;
		font-size: 22rpx;
		color: #cbd5e1;
		background: rgba(124, 58, 237, 0.16);
	}

	.reply-help-head-btn--disabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.reply-help-state {
		display: block;
		padding: 8rpx 6rpx 12rpx;
		font-size: 24rpx;
		line-height: 1.5;
		color: #94a3b8;
	}

	.reply-help-state--error {
		color: #fda4af;
	}

	.reply-help-list {
		display: flex;
		flex-direction: column;
		gap: 12rpx;
	}

	.reply-help-card {
		display: flex;
		align-items: flex-start;
		gap: 16rpx;
		padding: 18rpx 16rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.06);
		border: 1rpx solid rgba(255, 255, 255, 0.06);
	}

	.reply-help-index {
		flex-shrink: 0;
		width: 34rpx;
		height: 34rpx;
		line-height: 34rpx;
		text-align: center;
		border-radius: 10rpx;
		font-size: 22rpx;
		font-weight: 700;
		color: #ec4899;
		background: rgba(255, 255, 255, 0.88);
	}

	.reply-help-text {
		flex: 1;
		font-size: 26rpx;
		line-height: 1.65;
		color: #f8fafc;
	}

	.input-bar {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
		background: rgba(9, 13, 24, 0.16);
		backdrop-filter: blur(14rpx);
		border-top: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.reply-help-trigger {
		flex-shrink: 0;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 22rpx;
		margin-right: 14rpx;
		border-radius: 36rpx;
		font-size: 24rpx;
		font-weight: 600;
		color: #f8fafc;
		background: linear-gradient(135deg, rgba(124, 58, 237, 0.72) 0%, rgba(236, 72, 153, 0.66) 100%);
		box-shadow: 0 10rpx 24rpx rgba(124, 58, 237, 0.22);
	}

	.reply-help-trigger--disabled {
		opacity: 0.4;
		pointer-events: none;
	}

	.inp {
		flex: 1;
		height: 72rpx;
		padding: 0 24rpx;
		background: rgba(9, 13, 24, 0.18);
		backdrop-filter: blur(16rpx);
		border: 1rpx solid rgba(255, 255, 255, 0.1);
		border-radius: 36rpx;
		font-size: 28rpx;
		color: $text;
	}

	.send {
		margin-left: 16rpx;
		padding: 0 28rpx;
		height: 72rpx;
		line-height: 72rpx;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
		color: #fff;
		font-size: 28rpx;
		border-radius: 36rpx;
	}

	.send.senddisabled {
		opacity: 0.45;
		pointer-events: none;
	}

	.typing-row {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 24rpx;
		padding: 8rpx 24rpx 0;
	}

	.typing-hint {
		font-size: 24rpx;
		color: #94a3b8;
	}

	.stop-stream {
		font-size: 24rpx;
		color: #f87171;
		padding: 6rpx 20rpx;
		border-radius: 999rpx;
		border: 1rpx solid rgba(248, 113, 113, 0.45);
	}

	.swipe-row {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 16rpx;
		margin-top: 12rpx;
		padding-top: 12rpx;
		border-top: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.swipe-btn {
		font-size: 36rpx;
		color: #c4b5fd;
		padding: 0 12rpx;
		line-height: 1;
	}

	.swipe-num {
		font-size: 22rpx;
		color: #94a3b8;
	}
</style>

<style>
	page {
		background-color: #0f1220;
	}

	.inp-ph {
		color: #64748b;
	}
</style>






