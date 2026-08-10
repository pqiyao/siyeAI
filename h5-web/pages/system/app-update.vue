<template>
	<view class="page" :class="{ 'page--force': release.force }">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<view class="page-shade"></view>

		<view class="update-shell">
			<view class="update-panel">
				<view class="update-mark">
					<u-icon name="arrow-upward" color="#ffffff" size="42"></u-icon>
				</view>
				<text class="update-title">{{ release.title || copy.title }}</text>
				<view class="version-row">
					<text>{{ copy.version }} {{ release.versionName || release.versionCode }}</text>
					<view v-if="release.force" class="force-badge"><u-icon name="warning-fill" color="#a85c67" size="18"></u-icon><text>{{ copy.force }}</text></view>
				</view>

				<view class="release-note">
					<text class="note-title">{{ copy.changes }}</text>
					<scroll-view scroll-y class="note-scroll" :show-scrollbar="false">
						<view v-for="(line, index) in changelogLines" :key="index" class="note-line">
							<view class="note-dot"></view>
							<text>{{ line }}</text>
						</view>
						<text v-if="!changelogLines.length" class="note-empty">{{ copy.defaultNote }}</text>
					</scroll-view>
				</view>

				<view class="release-meta">
					<view v-if="sizeText" class="meta-item"><u-icon name="download" color="#66818e" size="20"></u-icon><text>{{ sizeText }}</text></view>
					<view class="meta-item"><u-icon name="lock-fill" color="#66818e" size="19"></u-icon><text>{{ copy.secure }}</text></view>
				</view>

				<view class="actions">
					<view class="update-button" :class="{ 'update-button--busy': opening }" @tap="openDownload">
						<u-icon name="download" color="#ffffff" size="24"></u-icon>
						<text>{{ opening ? copy.opening : copy.updateNow }}</text>
					</view>
					<view v-if="!release.force" class="secondary-actions">
						<view class="secondary-button" @tap="later"><u-icon name="clock" color="#587381" size="21"></u-icon><text>{{ copy.later }}</text></view>
						<view class="secondary-button secondary-button--quiet" @tap="ignore"><u-icon name="close-circle" color="#7d8e97" size="21"></u-icon><text>{{ copy.ignore }}</text></view>
					</view>
				</view>
			</view>
			<text class="browser-tip">{{ copy.browserTip }}</text>
		</view>
	</view>
</template>

<script>
const appUpdate = require('@/common/appUpdate.js');
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '发现新版本', version: '版本', force: '必须更新', changes: '本次更新', defaultNote: '优化使用体验并修复已知问题。',
		secure: 'HTTPS 安全下载', updateNow: '立即更新', opening: '正在打开', later: '稍后再说', ignore: '忽略此版本',
		browserTip: '将打开系统浏览器下载 APK，安装时请按系统提示完成覆盖更新。'
	},
	'zh-hk': {
		title: '發現新版本', version: '版本', force: '必須更新', changes: '本次更新', defaultNote: '改善使用體驗並修正已知問題。',
		secure: 'HTTPS 安全下載', updateNow: '立即更新', opening: '正在開啟', later: '稍後再說', ignore: '忽略此版本',
		browserTip: '將開啟系統瀏覽器下載 APK，安裝時請按系統提示完成覆蓋更新。'
	},
	en: {
		title: 'Update available', version: 'Version', force: 'Required', changes: 'What is new', defaultNote: 'Experience improvements and fixes.',
		secure: 'Secure HTTPS download', updateNow: 'Update now', opening: 'Opening', later: 'Remind me later', ignore: 'Skip this version',
		browserTip: 'Your browser will download the APK. Follow Android instructions to install the update.'
	},
	ko: {
		title: '새 버전이 있습니다', version: '버전', force: '필수 업데이트', changes: '업데이트 내용', defaultNote: '사용 환경 개선 및 알려진 문제 수정.',
		secure: 'HTTPS 보안 다운로드', updateNow: '지금 업데이트', opening: '여는 중', later: '나중에 알림', ignore: '이 버전 건너뛰기',
		browserTip: '시스템 브라우저에서 APK를 다운로드한 뒤 Android 안내에 따라 설치하세요.'
	},
	ja: {
		title: '新しいバージョンがあります', version: 'バージョン', force: '必須アップデート', changes: '更新内容', defaultNote: '使いやすさの改善と既知の問題の修正。',
		secure: 'HTTPS 安全ダウンロード', updateNow: '今すぐ更新', opening: '開いています', later: '後で通知', ignore: 'このバージョンを無視',
		browserTip: 'システムブラウザで APK をダウンロードし、Android の案内に従ってインストールしてください。'
	}
};

export default {
	data() {
		return {
			release: appUpdate.getPendingUpdate() || { force: false, changelog: '' },
			opening: false
		};
	},
	computed: {
		copy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		changelogLines() {
			return String(this.release.changelog || '')
				.split(/\r?\n/)
				.map((line) => line.replace(/^\s*[-*•·]\s*/, '').trim())
				.filter(Boolean)
				.slice(0, 100);
		},
		sizeText() {
			const bytes = Number(this.release.apkSizeBytes || 0);
			if (!(bytes > 0)) return '';
			if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(bytes >= 100 * 1024 * 1024 ? 0 : 1) + ' MB';
			return Math.max(1, Math.round(bytes / 1024)) + ' KB';
		}
	},
	onShow() {
		const pending = appUpdate.getPendingUpdate();
		if (pending) this.release = pending;
	},
	onBackPress() {
		return this.release.force === true;
	},
	methods: {
		openDownload() {
			if (this.opening) return;
			this.opening = true;
			appUpdate.openDownload(this.release);
			setTimeout(() => { this.opening = false; }, 1000);
		},
		later() {
			if (this.release.force) return;
			appUpdate.remindLater(this.release);
			this.closePage();
		},
		ignore() {
			if (this.release.force) return;
			appUpdate.ignoreRelease(this.release);
			this.closePage();
		},
		closePage() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	width: 100%;
	height: 100vh;
	overflow: hidden;
	color: #273e4b;
}

.page-shade {
	position: fixed;
	inset: 0;
	z-index: 1;
	background: rgba(32, 57, 73, 0.24);
	backdrop-filter: blur(10rpx);
	-webkit-backdrop-filter: blur(10rpx);
}

.update-shell {
	position: relative;
	z-index: 2;
	height: 100%;
	padding: calc(36rpx + env(safe-area-inset-top)) 28rpx calc(26rpx + env(safe-area-inset-bottom));
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.update-panel {
	width: 100%;
	max-width: 680rpx;
	max-height: 82vh;
	padding: 40rpx 32rpx 30rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	border: 1rpx solid rgba(255, 255, 255, 0.82);
	border-radius: 40rpx;
	background: linear-gradient(150deg, rgba(255, 255, 255, 0.9) 0%, rgba(238, 248, 250, 0.82) 58%, rgba(255, 240, 247, 0.76) 100%);
	box-shadow: 0 30rpx 80rpx rgba(34, 67, 87, 0.22), inset 0 1rpx 0 rgba(255, 255, 255, 0.94);
	backdrop-filter: blur(28rpx);
	-webkit-backdrop-filter: blur(28rpx);
	box-sizing: border-box;
}

.update-mark {
	width: 92rpx;
	height: 92rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 30rpx;
	background: linear-gradient(145deg, #4f93a3, #79b4be);
	border: 3rpx solid rgba(255, 255, 255, 0.76);
	box-shadow: 0 18rpx 38rpx rgba(48, 103, 117, 0.24), inset 0 1rpx 0 rgba(255, 255, 255, 0.24);
}

.page--force .update-mark { background: linear-gradient(145deg, #a85c67, #ca8290); box-shadow: 0 18rpx 38rpx rgba(168, 92, 103, 0.23); }
.update-title { margin-top: 24rpx; font-size: 38rpx; line-height: 1.3; font-weight: 800; text-align: center; }
.version-row { min-height: 42rpx; margin-top: 12rpx; display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10rpx; color: #64808e; font-size: 23rpx; }
.force-badge { height: 38rpx; padding: 0 12rpx; display: inline-flex; align-items: center; gap: 5rpx; border-radius: 999rpx; color: #a85c67; background: rgba(255, 239, 243, 0.88); border: 1rpx solid rgba(168, 92, 103, 0.12); font-size: 19rpx; font-weight: 700; }

.release-note {
	width: 100%;
	min-height: 170rpx;
	margin-top: 28rpx;
	padding: 24rpx;
	border-radius: 26rpx;
	background: rgba(255, 255, 255, 0.56);
	border: 1rpx solid rgba(79, 147, 163, 0.11);
	box-shadow: inset 0 2rpx 8rpx rgba(64, 96, 112, 0.035);
	box-sizing: border-box;
}
.note-title { display: block; font-size: 25rpx; font-weight: 800; color: #304d5a; }
.note-scroll { height: 230rpx; margin-top: 15rpx; }
.note-line { display: flex; align-items: flex-start; gap: 12rpx; margin-bottom: 14rpx; color: #58717f; font-size: 23rpx; line-height: 1.65; }
.note-dot { width: 9rpx; height: 9rpx; margin-top: 14rpx; flex: 0 0 auto; border-radius: 50%; background: #6ba8b4; box-shadow: 0 0 0 5rpx rgba(107, 168, 180, 0.1); }
.note-empty { color: #738995; font-size: 23rpx; line-height: 1.7; }
.release-meta { width: 100%; margin-top: 18rpx; display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 18rpx; }
.meta-item { display: inline-flex; align-items: center; gap: 7rpx; color: #66818e; font-size: 20rpx; }
.actions { width: 100%; margin-top: 26rpx; }
.update-button { width: 100%; height: 78rpx; display: flex; align-items: center; justify-content: center; gap: 9rpx; border-radius: 22rpx; color: #fff; background: linear-gradient(135deg, #4f93a3, #72b5c0); box-shadow: 0 16rpx 34rpx rgba(48, 103, 117, 0.22); font-size: 26rpx; font-weight: 800; transition: transform 160ms ease; }
.page--force .update-button { background: linear-gradient(135deg, #9d5965, #c57c89); box-shadow: 0 16rpx 34rpx rgba(157, 89, 101, 0.22); }
.update-button:active { transform: scale(0.98); }
.update-button--busy { opacity: 0.74; }
.secondary-actions { margin-top: 14rpx; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; }
.secondary-button { min-height: 66rpx; padding: 0 12rpx; display: flex; align-items: center; justify-content: center; gap: 7rpx; border-radius: 20rpx; color: #587381; background: rgba(238, 247, 249, 0.74); border: 1rpx solid rgba(79, 147, 163, 0.1); font-size: 22rpx; font-weight: 700; box-sizing: border-box; }
.secondary-button--quiet { color: #748892; background: rgba(246, 247, 248, 0.68); border-color: rgba(100, 120, 130, 0.09); }
.browser-tip { max-width: 620rpx; margin-top: 18rpx; color: rgba(255, 255, 255, 0.9); font-size: 20rpx; line-height: 1.55; text-align: center; text-shadow: 0 2rpx 8rpx rgba(29, 54, 70, 0.24); }

@media (max-width: 360px) {
	.update-panel { padding: 32rpx 24rpx 24rpx; border-radius: 34rpx; }
	.update-mark { width: 82rpx; height: 82rpx; border-radius: 26rpx; }
	.update-title { font-size: 34rpx; }
	.note-scroll { height: 200rpx; }
	.secondary-button { font-size: 20rpx; }
}

@media (orientation: landscape) and (max-height: 520px) {
	.update-shell { justify-content: flex-start; overflow-y: auto; }
	.update-panel { max-width: 920rpx; max-height: none; padding-top: 28rpx; }
	.update-mark { width: 72rpx; height: 72rpx; border-radius: 22rpx; }
	.update-title { margin-top: 14rpx; font-size: 32rpx; }
	.release-note { margin-top: 18rpx; }
	.note-scroll { height: 150rpx; }
}
</style>
