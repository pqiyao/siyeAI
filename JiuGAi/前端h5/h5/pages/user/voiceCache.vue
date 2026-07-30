<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />
		<scroll-view class="body" scroll-y :show-scrollbar="false">
			<view class="storage-head">
				<view class="storage-icon"><u-icon name="volume-up" color="#ffffff" size="34"></u-icon></view>
				<view class="storage-head-copy">
					<text class="storage-title">{{ copy.deviceStorage }}</text>
					<text class="storage-desc">{{ copy.localOnly }}</text>
				</view>
			</view>

			<view v-if="loading" class="state-block">
				<u-loading mode="circle" color="#347f97" size="36"></u-loading>
				<text>{{ copy.loading }}</text>
			</view>
			<view v-else-if="error" class="state-block state-block--error">
				<text>{{ error }}</text>
				<text class="retry" @tap="loadSummary">{{ copy.retry }}</text>
			</view>
			<template v-else>
				<view class="usage-panel">
					<view class="usage-row">
						<text class="usage-label">{{ copy.used }}</text>
						<text class="usage-value">{{ formatBytes(summary.totalBytes) }} / {{ formatBytes(summary.maxBytes) }}</text>
					</view>
					<view class="progress-track">
						<view class="progress-value" :style="{ width: usagePercent + '%' }"></view>
					</view>
					<view class="metric-row">
						<view class="metric">
							<text class="metric-value">{{ summary.count }}</text>
							<text class="metric-label">{{ copy.segments }}</text>
						</view>
						<view class="metric-divider"></view>
						<view class="metric">
							<text class="metric-value">{{ formatBytes(summary.totalBytes) }}</text>
							<text class="metric-label">{{ copy.cacheSize }}</text>
						</view>
					</view>
				</view>

				<view class="storage-note">
					<u-icon name="info-circle" color="#347f97" size="25"></u-icon>
					<text>{{ copy.autoCleanup }}</text>
				</view>

				<view
					class="clear-button"
					:class="{ 'clear-button--disabled': clearing || summary.count === 0 }"
					@tap="confirmClear"
				>
					<u-icon name="trash" :color="clearing || summary.count === 0 ? '#9aa9b1' : '#ffffff'" size="28"></u-icon>
					<text>{{ clearing ? copy.clearing : (summary.count ? copy.clear : copy.empty) }}</text>
				</view>
				<text class="clear-help">{{ copy.clearHelp }}</text>
			</template>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');
const localMediaStore = require('@/common/localMediaStore.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '语音缓存',
		deviceStorage: '此设备上的角色语音',
		localOnly: '语音缓存在当前设备，不会保存到服务器。',
		loading: '正在读取本地缓存...',
		retry: '重新读取',
		used: '存储占用',
		segments: '语音片段',
		cacheSize: '缓存大小',
		autoCleanup: '达到设备上限后，会自动清理较早的语音，不影响聊天内容。',
		clear: '清理语音缓存',
		clearing: '正在清理...',
		empty: '暂无可清理语音',
		clearHelp: '清理后，之后播放这些台词时会重新生成语音。',
		loadFailed: '本地语音缓存读取失败，请重试',
		clearFailed: '语音缓存清理失败，请重试',
		clearDone: '语音缓存已清理',
		confirmTitle: '清理语音缓存',
		confirmContent: '将删除此设备上当前账号的角色语音缓存。聊天内容和图片不会受到影响。',
		confirm: '清理'
	},
	'zh-hk': {
		title: '語音快取', deviceStorage: '此裝置上的角色語音', localOnly: '語音只快取在目前裝置，不會儲存到伺服器。',
		loading: '正在讀取本機快取...', retry: '重新讀取', used: '儲存佔用', segments: '語音片段', cacheSize: '快取大小',
		autoCleanup: '達到裝置上限後，會自動清理較早的語音，不影響聊天內容。', clear: '清理語音快取', clearing: '正在清理...',
		empty: '暫無可清理語音', clearHelp: '清理後，再次播放這些台詞時會重新產生語音。', loadFailed: '本機語音快取讀取失敗，請重試',
		clearFailed: '語音快取清理失敗，請重試', clearDone: '語音快取已清理', confirmTitle: '清理語音快取',
		confirmContent: '將刪除此裝置上目前帳號的角色語音快取。聊天內容和圖片不會受到影響。', confirm: '清理'
	},
	en: {
		title: 'Voice Cache', deviceStorage: 'Character voice on this device', localOnly: 'Voice is cached on this device and is not stored on the server.',
		loading: 'Reading local cache...', retry: 'Try Again', used: 'Storage used', segments: 'Voice clips', cacheSize: 'Cache size',
		autoCleanup: 'Older voice clips are removed automatically at the device limit. Chats are unaffected.', clear: 'Clear Voice Cache', clearing: 'Clearing...',
		empty: 'No voice cache to clear', clearHelp: 'Cleared dialogue audio will be generated again when played.', loadFailed: 'Could not read the local voice cache.',
		clearFailed: 'Could not clear the voice cache.', clearDone: 'Voice cache cleared', confirmTitle: 'Clear Voice Cache',
		confirmContent: 'This removes character voice cached for the current account on this device. Chats and images are unaffected.', confirm: 'Clear'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			loading: true,
			clearing: false,
			error: '',
			summary: { count: 0, totalBytes: 0, maxBytes: 0, latestAt: 0 }
		};
	},
	computed: {
		copy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		usagePercent() {
			const total = Math.max(0, Number(this.summary.totalBytes) || 0);
			const max = Math.max(0, Number(this.summary.maxBytes) || 0);
			if (!total || !max) return 0;
			return Math.min(100, Math.max(1, Math.round(total / max * 1000) / 10));
		}
	},
	onShow() {
		this.loadSummary();
	},
	methods: {
		ownerKey() {
			try {
				return String(tavernApi.getClientUid() || '').trim() || 'guest_local';
			} catch (e) {
				return 'guest_local';
			}
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.redirectTo({ url: '/pages/user/set' }) });
		},
		formatBytes(value) {
			const bytes = Math.max(0, Number(value) || 0);
			if (bytes < 1024) return bytes + ' B';
			if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(bytes >= 10240 ? 0 : 1) + ' KB';
			return (bytes / 1024 / 1024).toFixed(bytes >= 10 * 1024 * 1024 ? 0 : 1) + ' MB';
		},
		loadSummary() {
			if (this.clearing) return;
			this.loading = true;
			this.error = '';
			return localMediaStore.summary({ ownerKey: this.ownerKey(), kind: 'assistant_tts' })
				.then((result) => {
					this.summary = Object.assign({ count: 0, totalBytes: 0, maxBytes: 0, latestAt: 0 }, result || {});
				})
				.catch((error) => {
					console.error('[voice-cache] load summary failed', error);
					this.error = this.copy.loadFailed;
				})
				.finally(() => { this.loading = false; });
		},
		confirmClear() {
			if (this.clearing || !this.summary.count) return;
			uni.showModal({
				title: this.copy.confirmTitle,
				content: this.copy.confirmContent,
				confirmText: this.copy.confirm,
				confirmColor: '#b64f62',
				success: (result) => {
					if (result.confirm) this.clearVoiceCache();
				}
			});
		},
		clearVoiceCache() {
			if (this.clearing) return;
			this.clearing = true;
			this.error = '';
			localMediaStore.removeByOwnerKind(this.ownerKey(), 'assistant_tts')
				.then(() => {
					this.summary = Object.assign({}, this.summary, { count: 0, totalBytes: 0, latestAt: 0 });
					uni.showToast({ title: this.copy.clearDone, icon: 'none' });
				})
				.catch((error) => {
					console.error('[voice-cache] clear failed', error);
					this.error = this.copy.clearFailed;
				})
				.finally(() => { this.clearing = false; });
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background: $tavern-page-bg;
	display: flex;
	flex-direction: column;
}

.body {
	flex: 1;
	min-height: 0;
	padding: 30rpx 28rpx calc(56rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.storage-head {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 12rpx 4rpx 28rpx;
}

.storage-icon {
	width: 70rpx;
	height: 70rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	border-radius: 16rpx;
	background: #347f97;
	box-shadow: 0 12rpx 26rpx rgba(52, 127, 151, 0.22);
}

.storage-head-copy { min-width: 0; flex: 1; }
.storage-title, .storage-desc { display: block; }
.storage-title { font-size: 30rpx; font-weight: 800; color: #244b66; }
.storage-desc { margin-top: 7rpx; font-size: 22rpx; line-height: 1.55; color: #657d8c; }

.usage-panel {
	padding: 28rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(255, 255, 255, 0.86);
	box-shadow: 0 18rpx 42rpx rgba(67, 112, 142, 0.1);
}

.usage-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.usage-label { font-size: 24rpx; font-weight: 700; color: #345267; }
.usage-value { font-size: 22rpx; color: #607785; text-align: right; }
.progress-track { height: 12rpx; margin-top: 18rpx; overflow: hidden; border-radius: 6rpx; background: rgba(102, 140, 157, 0.15); }
.progress-value { height: 100%; border-radius: 6rpx; background: #347f97; transition: width 180ms ease; }

.metric-row { display: flex; align-items: stretch; margin-top: 30rpx; padding-top: 24rpx; border-top: 1rpx solid rgba(91, 139, 157, 0.13); }
.metric { min-width: 0; flex: 1; text-align: center; }
.metric-value, .metric-label { display: block; }
.metric-value { font-size: 32rpx; font-weight: 800; color: #244b66; }
.metric-label { margin-top: 7rpx; font-size: 21rpx; color: #758793; }
.metric-divider { width: 1rpx; background: rgba(91, 139, 157, 0.14); }

.storage-note {
	display: flex;
	align-items: flex-start;
	gap: 12rpx;
	margin-top: 20rpx;
	padding: 18rpx 20rpx;
	border-left: 5rpx solid #5c9fb2;
	background: rgba(224, 242, 246, 0.72);
	font-size: 22rpx;
	line-height: 1.6;
	color: #536d7a;
}

.clear-button {
	height: 86rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	margin-top: 32rpx;
	border-radius: 12rpx;
	background: #b64f62;
	box-shadow: 0 14rpx 28rpx rgba(147, 54, 73, 0.18);
	font-size: 26rpx;
	font-weight: 700;
	color: #ffffff;
}

.clear-button--disabled { background: rgba(121, 143, 153, 0.16); box-shadow: none; color: #9aa9b1; }
.clear-help { display: block; margin-top: 14rpx; text-align: center; font-size: 21rpx; line-height: 1.55; color: #758793; }

.state-block {
	min-height: 300rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 20rpx;
	font-size: 23rpx;
	line-height: 1.6;
	text-align: center;
	color: #687f8c;
}

.state-block--error { color: #a44747; }
.retry { padding: 12rpx 20rpx; font-weight: 700; color: #28758a; }
</style>
