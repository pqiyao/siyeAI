<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="AI看板娘" mode="dark" @back="goBack" />
		<view class="body">
			<view class="panel model-panel">
				<image class="model-icon" src="/static/live2d/models/ug/icon.png" mode="aspectFill"></image>
				<view class="model-meta">
					<text class="model-name">UG</text>
					<text class="model-desc">Live2D 悬浮助手</text>
				</view>
				<switch class="native-switch" :checked="config.enabled" color="#4f93a3" @change="onToggle('enabled', $event)" />
			</view>

			<view class="panel">
				<view class="setting-row">
					<view class="setting-copy">
						<text class="setting-title">聊天联动</text>
						<text class="setting-desc">AI 回复时显示气泡和表情</text>
					</view>
					<switch :checked="config.chatLink" color="#4f93a3" @change="onToggle('chatLink', $event)" />
				</view>
				<view class="setting-row">
					<view class="setting-copy">
						<text class="setting-title">气泡文字</text>
						<text class="setting-desc">显示回复摘要和状态提示</text>
					</view>
					<switch :checked="config.showBubble" color="#4f93a3" @change="onToggle('showBubble', $event)" />
				</view>
				<view class="setting-row setting-row--last">
					<view class="setting-copy">
						<text class="setting-title">点击互动</text>
						<text class="setting-desc">点击模型随机切换表情</text>
					</view>
					<switch :checked="config.clickAction" color="#4f93a3" @change="onToggle('clickAction', $event)" />
				</view>
			</view>

			<view class="panel">
				<view class="slider-head">
					<text class="setting-title">模型大小</text>
					<text class="setting-value">{{ sizePercent }}%</text>
				</view>
				<slider
					:value="sizePercent"
					:min="70"
					:max="130"
					:step="5"
					activeColor="#4f93a3"
					backgroundColor="rgba(79, 147, 163, 0.16)"
					block-color="#ffffff"
					@change="onSizeChange"
				/>
				<view class="slider-head opacity-head">
					<text class="setting-title">透明度</text>
					<text class="setting-value">{{ opacityPercent }}%</text>
				</view>
				<slider
					:value="opacityPercent"
					:min="35"
					:max="100"
					:step="5"
					activeColor="#4f93a3"
					backgroundColor="rgba(79, 147, 163, 0.16)"
					block-color="#ffffff"
					@change="onOpacityChange"
				/>
			</view>

			<view class="panel">
				<view class="position-head">
					<text class="setting-title">默认位置</text>
				</view>
				<view class="segmented">
					<view class="segment" :class="{ 'segment--active': config.side === 'left' }" @tap="setSide('left')">左下</view>
					<view class="segment" :class="{ 'segment--active': config.side === 'right' }" @tap="setSide('right')">右下</view>
				</view>
				<view class="reset-btn" @tap="resetPosition">恢复默认位置</view>
			</view>
		</view>
		<!-- #ifdef APP-PLUS -->
		<live2d-companion :avoid-bottom="32" />
		<!-- #endif -->
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const companionStore = require('@/common/companionStore.js');

const BASE_WIDTH = 230;
const BASE_HEIGHT = 260;
const BASE_SCALE = 0.22;

export default {
	components: { TavernNavBar },
	data() {
		return {
			config: companionStore.getConfig()
		};
	},
	computed: {
		sizePercent() {
			return Math.round((Number(this.config.width || BASE_WIDTH) / BASE_WIDTH) * 100);
		},
		opacityPercent() {
			return Math.round(Number(this.config.opacity || 1) * 100);
		}
	},
	onShow() {
		this.config = companionStore.getConfig();
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/user/set' }) });
		},
		save(partial) {
			this.config = companionStore.saveConfig(partial);
		},
		onToggle(key, event) {
			const value = !!(event && event.detail && event.detail.value);
			const next = {};
			next[key] = value;
			this.save(next);
		},
		onSizeChange(event) {
			const value = Number(event && event.detail && event.detail.value) || 100;
			const ratio = value / 100;
			this.save({
				width: Math.round(BASE_WIDTH * ratio),
				height: Math.round(BASE_HEIGHT * ratio),
				scale: Number((BASE_SCALE * ratio).toFixed(3)),
				x: null,
				y: null
			});
		},
		onOpacityChange(event) {
			const value = Number(event && event.detail && event.detail.value) || 100;
			this.save({ opacity: Number((value / 100).toFixed(2)) });
		},
		setSide(side) {
			this.save({ side, x: null, y: null });
		},
		resetPosition() {
			this.save({ x: null, y: null });
			uni.showToast({ title: '已恢复默认位置', icon: 'none' });
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
	padding: 24rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
}

.panel {
	margin-bottom: 24rpx;
	padding: 24rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.62);
	border: 1rpx solid rgba(255, 255, 255, 0.54);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.1);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.model-panel {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.model-icon {
	width: 96rpx;
	height: 96rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.8);
}

.model-meta {
	flex: 1;
	min-width: 0;
}

.model-name,
.setting-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: #244b66;
}

.model-desc,
.setting-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 24rpx;
	line-height: 1.45;
	color: #687f92;
}

.setting-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 24rpx;
	padding: 0 0 24rpx;
	margin-bottom: 24rpx;
	border-bottom: 1rpx solid rgba(88, 189, 210, 0.12);
}

.setting-row--last {
	padding-bottom: 0;
	margin-bottom: 0;
	border-bottom: none;
}

.setting-copy {
	flex: 1;
	min-width: 0;
}

.slider-head,
.position-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 12rpx;
}

.opacity-head {
	margin-top: 24rpx;
}

.setting-value {
	font-size: 26rpx;
	color: #3f7f91;
	font-weight: 700;
}

.segmented {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 12rpx;
	margin-top: 12rpx;
}

.segment {
	height: 76rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	color: #477082;
	background: rgba(255, 255, 255, 0.58);
	border: 1rpx solid rgba(79, 147, 163, 0.18);
}

.segment--active {
	color: #fff;
	background: #4f93a3;
	box-shadow: 0 14rpx 30rpx rgba(48, 103, 117, 0.18);
}

.reset-btn {
	margin-top: 18rpx;
	height: 76rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	color: #2f6f7f;
	background: rgba(79, 147, 163, 0.12);
}
</style>
