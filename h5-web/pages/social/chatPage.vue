<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="chatTitle" mode="dark" @back="goBack">
			<view slot="right" class="nav-action" @tap="reload">
				<text class="cuIcon-refresh nav-ico"></text>
				<text>刷新</text>
			</view>
		</tavern-nav-bar>

		<scroll-view
			scroll-y
			class="messages"
			:scroll-into-view="scrollIntoView"
			:show-scrollbar="false"
		>
			<view v-if="loading && !messages.length" class="state-card">正在加载消息...</view>
			<view v-else-if="errorMsg && !messages.length" class="state-card">
				<text class="state-title">{{ errorMsg }}</text>
				<view class="state-btn" @tap="reload">重试</view>
			</view>
			<view v-else-if="!messages.length" class="state-card">
				<text class="state-title">打个招呼吧</text>
				<text class="state-desc">{{ emptyChatDesc }}</text>
			</view>

			<view
				v-for="message in messages"
				:id="'msg-' + message.messageId"
				:key="message.messageId || message.clientMsgId"
				class="message-row"
				:class="{ mine: message.mine }"
			>
				<image class="avatar" :src="messageAvatar(message)" mode="aspectFill"></image>
				<view class="bubble-wrap">
					<view
						class="bubble"
						:class="{ recalled: message.status === 'recalled', image: message.messageType === 'image' }"
						@longpress="maybeRecall(message)"
					>
						<text v-if="message.status === 'recalled'" class="bubble-text">消息已撤回</text>
						<image
							v-else-if="message.messageType === 'image'"
							class="message-image"
							:src="imagePayloadUrl(message)"
							mode="aspectFill"
							@tap="previewImage(message)"
						></image>
						<text v-else class="bubble-text">{{ textPayload(message) }}</text>
					</view>
					<text class="meta">{{ formatTime(message.createdAt) }}{{ readMeta(message) }}</text>
				</view>
			</view>
			<view id="message-bottom" class="bottom-space"></view>
		</scroll-view>

		<view class="composer">
			<view v-if="socialFeatureConfig.imageMessageEnabled" class="tool-btn" :class="{ disabled: sending || !canSendMessage }" @tap="chooseImage">
				<text class="cuIcon-picfill"></text>
			</view>
			<input
				v-model="draft"
				class="input"
				:placeholder="socialFeatureConfig.textMessageEnabled ? '输入消息' : '文字消息已关闭'"
				placeholder-class="placeholder"
				confirm-type="send"
				:disabled="!socialFeatureConfig.textMessageEnabled || !canSendMessage"
				@confirm="sendText"
			/>
			<view class="send-btn" :class="{ disabled: sending || !canSendText || !canSendMessage }" @tap="sendText">
				<text class="send-label">发送</text>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			peerId: '',
			peerName: '',
			peer: null,
			messages: [],
			draft: '',
			loading: false,
			sending: false,
			errorMsg: '',
			scrollIntoView: '',
			refreshTimer: null,
			ws: null,
			wsAuthed: false,
			wsManualClose: false,
			wsReconnectTimer: null,
			peerOnline: false,
			socialFeatureConfig: tavernApi.getSocialFeatureConfig()
		};
	},
	computed: {
		chatTitle() {
			const name = this.peerName || '聊天';
			if (this.socialFeatureConfig.onlineStatusVisible === false) return name;
			if (!this.wsAuthed) return name;
			return name + (this.peerOnline ? ' · 在线' : ' · 已连接');
		},
		canSendText() {
			return this.socialFeatureConfig.textMessageEnabled !== false && String(this.draft || '').trim().length > 0;
		},
		canSendMessage() {
			const config = this.socialFeatureConfig || {};
			return config.chatEnabled !== false && config.existingChatEnabled !== false;
		},
		emptyChatDesc() {
			const config = this.socialFeatureConfig || {};
			if (config.textMessageEnabled === false && config.imageMessageEnabled === false) {
				return '当前后台已关闭消息发送。';
			}
			if (config.imageMessageEnabled === false) {
				return '当前只支持文字消息。';
			}
			if (config.textMessageEnabled === false) {
				return '当前只支持图片消息。';
			}
			return '当前支持文字和图片消息。';
		}
	},
	onLoad(options) {
		if (!tavernApi.hasLoggedInUser()) {
			uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/chatList') });
			return;
		}
		this.peerId = options && options.peerId ? String(options.peerId) : '';
		this.peerName = options && options.name ? decodeURIComponent(String(options.name)) : '';
		if (!this.peerId) {
			this.errorMsg = '聊天对象不存在';
			return;
		}
		this.loadSocialFeatureConfig(true).then((config) => {
			if (config.chatEnabled === false) {
				uni.showToast({ title: '真人聊天暂未开放', icon: 'none' });
				setTimeout(() => this.goBack(), 350);
				return;
			}
			this.reload();
			this.connectSocket();
		});
	},
	onShow() {
		this.loadSocialFeatureConfig(true);
		this.startRefreshTimer();
		this.connectSocket();
	},
	onHide() {
		this.stopRefreshTimer();
		this.closeSocket(true);
	},
	onUnload() {
		this.stopRefreshTimer();
		this.closeSocket(true);
	},
	methods: {
		connectSocket() {
			if (!this.peerId || !tavernApi.hasLoggedInUser()) return;
			if (this.ws) return;
			const token = this.authToken();
			if (!token) {
				uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/chatPage?peerId=' + encodeURIComponent(this.peerId)) });
				return;
			}
			const url = this.appendSocketToken(tavernApi.wsBaseUrl() + '/ws/social', token);
			if (!url || typeof uni.connectSocket !== 'function') return;
			this.wsManualClose = false;
			this.wsAuthed = false;
			this.peerOnline = false;
			try {
				this.ws = uni.connectSocket({ url, complete: () => {} });
			} catch (e) {
				this.ws = null;
				this.scheduleSocketReconnect();
				return;
			}
			if (!this.ws || typeof this.ws.onOpen !== 'function') {
				this.ws = null;
				this.scheduleSocketReconnect();
				return;
			}
			this.ws.onOpen(() => {
				this.sendSocket({ type: 'auth', token });
			});
			this.ws.onMessage((event) => {
				this.handleSocketMessage(event && event.data);
			});
			this.ws.onClose(() => {
				this.ws = null;
				this.wsAuthed = false;
				this.peerOnline = false;
				this.scheduleSocketReconnect();
			});
			this.ws.onError(() => {
				this.wsAuthed = false;
				this.peerOnline = false;
				this.scheduleSocketReconnect();
			});
		},
		closeSocket(permanent) {
			this.wsManualClose = !!permanent;
			if (this.wsReconnectTimer) {
				clearTimeout(this.wsReconnectTimer);
				this.wsReconnectTimer = null;
			}
			if (this.ws) {
				try {
					this.ws.close({ code: 1000, reason: 'leave' });
				} catch (e) {}
			}
			this.ws = null;
			this.wsAuthed = false;
			this.peerOnline = false;
		},
		scheduleSocketReconnect() {
			if (this.wsManualClose || this.wsReconnectTimer) return;
			this.wsReconnectTimer = setTimeout(() => {
				this.wsReconnectTimer = null;
				this.connectSocket();
			}, 3000);
		},
		sendSocket(payload) {
			if (!this.ws || !payload) return;
			try {
				this.ws.send({ data: JSON.stringify(payload) });
			} catch (e) {}
		},
		handleSocketMessage(raw) {
			let event = null;
			try {
				event = typeof raw === 'string' ? JSON.parse(raw) : raw;
			} catch (e) {}
			if (!event || !event.type) return;
			const data = event.data || {};
			if (event.type === 'auth_ok') {
				this.wsAuthed = true;
				this.sendSocket({ type: 'query_online', peerId: this.peerId });
				return;
			}
			if (event.type === 'auth_failed') {
				this.closeSocket(true);
				uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/chatPage?peerId=' + encodeURIComponent(this.peerId)) });
				return;
			}
			if (event.type === 'online_status') {
				if (String(data.userId) === String(this.peerId)) {
					this.peerOnline = data.online === true;
				}
				return;
			}
			if (event.type === 'private_message_sent' || event.type === 'private_message_received' || event.type === 'message_recalled') {
				if (!this.isCurrentPeerMessage(data)) return;
				this.upsertMessage(data);
				if (!data.mine) {
					this.peerOnline = true;
					this.markRead();
				}
				this.scrollToBottom();
				return;
			}
			if (event.type === 'messages_read') {
				this.applyReadEvent(data);
			}
		},
		isCurrentPeerMessage(message) {
			if (!message) return false;
			return String(message.fromUserId) === String(this.peerId) || String(message.toUserId) === String(this.peerId);
		},
		applyReadEvent(data) {
			const readerUserId = data && data.readerUserId != null ? String(data.readerUserId) : '';
			if (!readerUserId || String(this.peerId) !== readerUserId) return;
			this.messages = this.messages.map((message) => {
				if (message.mine && message.status === 'normal') {
					return Object.assign({}, message, { isRead: true });
				}
				return message;
			});
		},
		authToken() {
			const user = tavernApi.getStoredUser();
			return user && user.token ? String(user.token) : '';
		},
		appendSocketToken(url, token) {
			const safeUrl = String(url || '').trim();
			const safeToken = String(token || '').trim();
			if (!safeUrl || !safeToken) return safeUrl;
			return safeUrl + (safeUrl.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(safeToken);
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/social/chatList' }) });
		},
		startRefreshTimer() {
			this.stopRefreshTimer();
			this.refreshTimer = setInterval(() => {
				if (!this.sending && this.peerId) {
					this.fetchMessages(false);
				}
			}, 8000);
		},
		stopRefreshTimer() {
			if (this.refreshTimer) {
				clearInterval(this.refreshTimer);
				this.refreshTimer = null;
			}
		},
		reload() {
			return this.fetchMessages(true);
		},
		loadSocialFeatureConfig(force) {
			return tavernApi.fetchSocialFeatureConfig(!!force)
				.then((config) => {
					this.socialFeatureConfig = config || tavernApi.getSocialFeatureConfig();
					if (this.socialFeatureConfig.onlineStatusVisible === false) {
						this.peerOnline = false;
					}
					return this.socialFeatureConfig;
				})
				.catch(() => {
					this.socialFeatureConfig = tavernApi.getSocialFeatureConfig();
					return this.socialFeatureConfig;
				});
		},
		fetchMessages(showLoading) {
			if (!this.peerId) return Promise.resolve();
			if (showLoading) {
				this.loading = true;
			}
			this.errorMsg = '';
			return tavernApi.fetchSocialChatMessages(this.peerId, { limit: 80 })
				.then((data) => {
					this.peer = data && data.peer ? data.peer : this.peer;
					if (this.peer && this.peer.nickname) {
						this.peerName = this.peer.nickname;
					}
					this.messages = Array.isArray(data && data.rows) ? data.rows : [];
					this.markRead();
					this.scrollToBottom();
				})
				.catch((error) => {
					this.errorMsg = (error && error.message) || '消息加载失败';
					if (error && (error.statusCode === 401 || error.statusCode === 403)) {
						uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/chatPage?peerId=' + encodeURIComponent(this.peerId)) });
					} else if (showLoading) {
						uni.showToast({ title: this.errorMsg, icon: 'none' });
					}
				})
				.finally(() => {
					this.loading = false;
				});
		},
		markRead() {
			if (!this.peerId) return;
			tavernApi.markSocialChatRead(this.peerId).catch(() => {});
		},
		sendText() {
			if (this.socialFeatureConfig.textMessageEnabled === false) {
				uni.showToast({ title: '当前已关闭文字消息', icon: 'none' });
				return;
			}
			if (!this.canSendMessage) {
				uni.showToast({ title: '当前已关闭真人聊天', icon: 'none' });
				return;
			}
			const text = String(this.draft || '').trim();
			if (!text || this.sending) return;
			this.sending = true;
			const clientMsgId = 'h5_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
			tavernApi.sendSocialChatMessage({
				peerId: this.peerId,
				clientMsgId,
				messageType: 'text',
				payload: { text }
			}).then((message) => {
				this.draft = '';
				this.upsertMessage(message);
				this.scrollToBottom();
			}).catch((error) => {
				uni.showToast({ title: (error && error.message) || '发送失败', icon: 'none' });
			}).finally(() => {
				this.sending = false;
			});
		},
		chooseImage() {
			if (this.socialFeatureConfig.imageMessageEnabled === false) {
				uni.showToast({ title: '当前已关闭图片消息', icon: 'none' });
				return;
			}
			if (!this.canSendMessage) {
				uni.showToast({ title: '当前已关闭真人聊天', icon: 'none' });
				return;
			}
			if (this.sending) return;
			uni.chooseImage({
				count: 1,
				sizeType: ['compressed'],
				sourceType: ['album', 'camera'],
				success: (res) => {
					const path = res.tempFilePaths && res.tempFilePaths[0];
					if (path) this.sendImage(path);
				}
			});
		},
		sendImage(path) {
			this.sending = true;
			tavernApi.uploadSocialChatImage(path)
				.then((mediaKey) => tavernApi.sendSocialChatMessage({
					peerId: this.peerId,
					clientMsgId: 'h5_img_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10),
					messageType: 'image',
					payload: { mediaKey }
				}))
				.then((message) => {
					this.upsertMessage(message);
					this.scrollToBottom();
				})
				.catch((error) => {
					uni.showToast({ title: (error && error.message) || '图片发送失败', icon: 'none' });
				})
				.finally(() => {
					this.sending = false;
				});
		},
		maybeRecall(message) {
			if (this.socialFeatureConfig.messageRecallEnabled === false) {
				uni.showToast({ title: '当前已关闭消息撤回', icon: 'none' });
				return;
			}
			if (!message || !message.mine || message.status === 'recalled') return;
			uni.showModal({
				title: '撤回',
				content: '撤回这条消息？',
				confirmText: '撤回',
				cancelText: '取消',
				success: (res) => {
					if (!res.confirm) return;
					tavernApi.recallSocialChatMessage(message.messageId)
						.then((updated) => {
							this.upsertMessage(updated);
						})
						.catch((error) => {
							uni.showToast({ title: (error && error.message) || '撤回失败', icon: 'none' });
						});
				}
			});
		},
		upsertMessage(message) {
			if (!message) return;
			const id = String(message.messageId || message.clientMsgId || '');
			const index = this.messages.findIndex((item) => String(item.messageId || item.clientMsgId || '') === id);
			if (index >= 0) {
				this.messages.splice(index, 1, Object.assign({}, this.messages[index], message));
			} else {
				this.messages.push(message);
			}
		},
		messageAvatar(message) {
			const user = message.mine ? tavernApi.getStoredUser() : (message.fromUser || this.peer || {});
			const avatar = message.mine
				? (user && (user.avatarUrl || user.avatar || user.photo_url))
				: (user && (user.avatar || user.avatarUrl));
			return tavernApi.resolveJgAssetUrl(avatar || '/static/logo.png');
		},
		textPayload(message) {
			const payload = message && message.payload;
			if (payload && typeof payload === 'object' && payload.text != null) {
				return String(payload.text);
			}
			return message && message.contentPreview ? String(message.contentPreview) : '';
		},
		imagePayloadUrl(message) {
			const payload = message && message.payload;
			const raw = payload && typeof payload === 'object'
				? (payload.mediaKey || payload.url || payload.path)
				: '';
			return tavernApi.resolveJgAssetUrl(raw || '');
		},
		previewImage(message) {
			const url = this.imagePayloadUrl(message);
			if (!url) return;
			uni.previewImage({ urls: [url], current: url });
		},
		readMeta(message) {
			if (this.socialFeatureConfig.onlineStatusVisible === false) return '';
			return message.mine && message.status === 'normal' ? (message.isRead ? ' · 已读' : ' · 已发送') : '';
		},
		scrollToBottom() {
			this.$nextTick(() => {
				this.scrollIntoView = '';
				setTimeout(() => {
					this.scrollIntoView = 'message-bottom';
				}, 30);
			});
		},
		formatTime(value) {
			if (!value) return '';
			const raw = String(value).trim();
			let d = new Date(raw);
			if (isNaN(d.getTime())) {
				d = new Date(raw.replace(/-/g, '/').replace('T', ' '));
			}
			if (isNaN(d.getTime())) {
				const match = raw.match(/(\d{1,2}):(\d{2})/);
				return match ? match[1].padStart(2, '0') + ':' + match[2] : raw;
			}
			const h = String(d.getHours()).padStart(2, '0');
			const m = String(d.getMinutes()).padStart(2, '0');
			const now = new Date();
			const sameDay = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
			if (sameDay) return h + ':' + m;
			return String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + ' ' + h + ':' + m;
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	height: 100vh;
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	background: #ecf8fb;
}

.app-page-bg {
	position: fixed;
	inset: 0;
	width: 100%;
	height: 100%;
	opacity: 0.5;
	z-index: 0;
}

.nav-action {
	padding: 12rpx 18rpx;
	border-radius: 999rpx;
	background: rgba(79, 147, 163, 0.14);
	color: #2f6f92;
	font-size: 24rpx;
	font-weight: 700;
}

.messages {
	position: relative;
	z-index: 1;
	flex: 1;
	min-height: 0;
	padding: 18rpx 24rpx 0;
	box-sizing: border-box;
}

.state-card {
	margin: 20rpx 0;
	padding: 52rpx 34rpx;
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.8);
	border: 1rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
	text-align: center;
	color: #6f7b88;
	font-size: 26rpx;
}

.state-title,
.state-desc {
	display: block;
	line-height: 1.5;
}

.state-title {
	color: #26394d;
	font-size: 30rpx;
	font-weight: 700;
}

.state-desc {
	margin-top: 10rpx;
	color: #6f7b88;
}

.state-btn {
	margin: 28rpx auto 0;
	width: 220rpx;
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #4f93a3;
	color: #fff;
	font-size: 26rpx;
	font-weight: 700;
}

.message-row {
	display: flex;
	align-items: flex-end;
	gap: 12rpx;
	margin: 18rpx 0;
}

.message-row.mine {
	flex-direction: row-reverse;
}

.avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background: rgba(79, 147, 163, 0.12);
	flex-shrink: 0;
}

.bubble-wrap {
	max-width: 72%;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
}

.message-row.mine .bubble-wrap {
	align-items: flex-end;
}

.bubble {
	max-width: 100%;
	min-height: 44rpx;
	padding: 18rpx 20rpx;
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.84);
	color: #2c405a;
	box-shadow: 0 10rpx 24rpx rgba(38, 57, 77, 0.08);
	box-sizing: border-box;
}

.message-row.mine .bubble {
	background: #4f93a3;
	color: #fff;
}

.bubble.recalled {
	background: rgba(95, 118, 136, 0.1);
	color: #7891a4;
}

.bubble-text {
	font-size: 29rpx;
	line-height: 1.55;
	word-break: break-word;
	white-space: pre-wrap;
}

.message-image {
	width: 320rpx;
	height: 320rpx;
	border-radius: 8rpx;
	display: block;
	background: rgba(79, 147, 163, 0.12);
}

.meta {
	margin-top: 8rpx;
	color: #95a6b4;
	font-size: 20rpx;
}

.bottom-space {
	height: 28rpx;
}

.composer {
	position: relative;
	z-index: 2;
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
	background: rgba(248, 252, 255, 0.92);
	border-top: 1rpx solid rgba(79, 147, 163, 0.14);
	box-sizing: border-box;
}

.tool-btn,
.send-btn {
	height: 68rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
	flex-shrink: 0;
}

.tool-btn {
	width: 108rpx;
	background: rgba(79, 147, 163, 0.1);
	color: #2f6f92;
}

.send-btn {
	width: 104rpx;
	background: #4f93a3;
	color: #fff;
}

.tool-btn.disabled,
.send-btn.disabled {
	opacity: 0.55;
}

.input {
	flex: 1;
	min-width: 0;
	height: 68rpx;
	border-radius: 8rpx;
	padding: 0 20rpx;
	box-sizing: border-box;
	background: rgba(79, 147, 163, 0.08);
	color: #2c405a;
	font-size: 26rpx;
}

.placeholder {
	color: #95a6b4;
}

.page {
	background:
		linear-gradient(180deg, #eef7f1 0%, #f7f7f2 42%, #f4f1ea 100%);
}

.app-page-bg {
	opacity: 0.06;
	filter: grayscale(1);
}

.nav-action {
	height: 54rpx;
	min-width: 104rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	background: rgba(255, 255, 255, 0.78);
	color: #2e6b4d;
	border: 1rpx solid rgba(46, 107, 77, 0.12);
	font-weight: 900;
	box-sizing: border-box;
}

.nav-ico {
	font-size: 24rpx;
	line-height: 1;
}

.messages {
	padding: 20rpx 24rpx 0;
}

.state-card {
	background: rgba(255, 255, 255, 0.94);
	border-color: rgba(46, 107, 77, 0.08);
	box-shadow: 0 14rpx 30rpx rgba(34, 52, 42, 0.08);
	color: #65736a;
}

.state-title {
	color: #1f2933;
	font-weight: 900;
}

.state-desc {
	color: #65736a;
}

.state-btn {
	background: #2e6b4d;
	font-weight: 900;
}

.message-row {
	gap: 14rpx;
	margin: 20rpx 0;
}

.avatar {
	width: 68rpx;
	height: 68rpx;
	background: #e6efe8;
	border: 3rpx solid rgba(255, 255, 255, 0.96);
	box-shadow: 0 8rpx 16rpx rgba(34, 52, 42, 0.1);
	box-sizing: border-box;
}

.bubble-wrap {
	max-width: 74%;
}

.bubble {
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.96);
	color: #263238;
	border: 1rpx solid rgba(46, 107, 77, 0.08);
	box-shadow: 0 12rpx 24rpx rgba(34, 52, 42, 0.08);
}

.message-row.mine .bubble {
	background: #2e6b4d;
	color: #ffffff;
	border-color: #2e6b4d;
	box-shadow: 0 14rpx 26rpx rgba(46, 107, 77, 0.18);
}

.bubble.recalled {
	background: rgba(101, 115, 106, 0.1);
	color: #7b877f;
	border-color: transparent;
}

.bubble-text {
	font-size: 29rpx;
	line-height: 1.58;
}

.message-image {
	border-radius: 8rpx;
	background: rgba(46, 107, 77, 0.08);
}

.meta {
	color: #7b877f;
	font-size: 21rpx;
}

.composer {
	gap: 12rpx;
	padding: 16rpx 20rpx calc(18rpx + env(safe-area-inset-bottom));
	background: rgba(255, 255, 255, 0.94);
	border-top: 1rpx solid rgba(46, 107, 77, 0.1);
	box-shadow: 0 -12rpx 26rpx rgba(34, 52, 42, 0.06);
}

.tool-btn,
.send-btn {
	width: 72rpx;
	height: 72rpx;
	border-radius: 8rpx;
	font-size: 34rpx;
	font-weight: 900;
}

.tool-btn {
	background: #f1f5f0;
	color: #2e6b4d;
}

.send-btn {
	background: #e36f5f;
	color: #ffffff;
	box-shadow: 0 12rpx 22rpx rgba(227, 111, 95, 0.18);
}

.input {
	height: 72rpx;
	border-radius: 8rpx;
	background: #f4f6f2;
	color: #263238;
	border: 1rpx solid rgba(46, 107, 77, 0.08);
}

.placeholder {
	color: #8a958e;
}

/* Chat page: keep the bubbles, lose the chunky chrome. */
.messages {
	padding: 18rpx 30rpx 0;
}

.state-card {
	background: transparent;
	border: 0;
	box-shadow: none;
}

.avatar {
	box-shadow: none;
}

.bubble {
	border-radius: 18rpx 18rpx 18rpx 6rpx;
	box-shadow: none;
	border: 0;
	background: rgba(255, 255, 255, 0.82);
}

.message-row.mine .bubble {
	border-radius: 18rpx 18rpx 6rpx 18rpx;
	box-shadow: none;
}

.message-image {
	border-radius: 14rpx;
}

.composer {
	box-shadow: none;
	background: rgba(255, 255, 255, 0.86);
}

.tool-btn,
.send-btn,
.input {
	border-radius: 999rpx;
	box-shadow: none;
}

.tool-btn {
	background: transparent;
}

.composer {
	gap: 10rpx;
	padding: 14rpx 18rpx calc(16rpx + env(safe-area-inset-bottom));
}

.tool-btn {
	width: 62rpx;
	height: 72rpx;
	font-size: 32rpx;
	color: #2e6b4d;
}

.input {
	height: 72rpx;
	padding: 0 24rpx;
	background: rgba(255, 255, 255, 0.88);
	border: 1rpx solid rgba(46, 107, 77, 0.1);
}

.send-btn {
	width: 104rpx;
	height: 72rpx;
	border-radius: 999rpx;
	background: #2e6b4d;
	color: #ffffff;
	font-size: 26rpx;
	font-weight: 900;
	letter-spacing: 0;
}

.send-btn.disabled {
	background: rgba(46, 107, 77, 0.12);
	color: rgba(46, 107, 77, 0.42);
	opacity: 1;
}

.send-label {
	line-height: 1;
}

.nav-action {
	min-width: 54rpx;
	width: 54rpx;
	height: 54rpx;
	padding: 0;
	border-radius: 50%;
	background: transparent;
	border: 0;
	color: #2e6b4d;
}

.nav-action text:not(.nav-ico) {
	display: none;
}

.bubble.image {
	padding: 0;
	background: transparent;
	overflow: hidden;
}

.message-row.mine .bubble.image {
	background: transparent;
	border: 0;
}

.message-image {
	width: 336rpx;
	height: 336rpx;
	border-radius: 18rpx;
	background: rgba(46, 107, 77, 0.06);
}

.meta {
	margin-top: 6rpx;
	padding: 0 6rpx;
	color: rgba(82, 99, 90, 0.68);
	font-size: 20rpx;
}

.message-row.mine .meta {
	color: rgba(82, 99, 90, 0.6);
}

.composer {
	border-top-color: rgba(46, 107, 77, 0.07);
	backdrop-filter: blur(18px);
	-webkit-backdrop-filter: blur(18px);
}
</style>
