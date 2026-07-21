<template>
	<view class="tavern-tabbar-shell">
		<view class="tavern-tabbar-card">
			<view
				v-for="item in items"
				:key="item.pagePath"
				class="tavern-tabbar-item"
				:class="{ 'tavern-tabbar-item--active': isActive(item) }"
				@tap="switchTab(item)"
			>
				<view class="tavern-tabbar-pill">
					<view class="tavern-tabbar-icon-wrap">
						<image class="tavern-tabbar-icon" :src="displayIconPath(item)" mode="aspectFit"></image>
						<view v-if="itemBadge(item)" class="tavern-tabbar-badge">
							{{ itemBadge(item) }}
						</view>
					</view>
					<text class="tavern-tabbar-label">{{ itemLabel(item) }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { getTavernTabBarState } from '@/common/tavernTabBar.js';

const DEFAULT_LABELS = {
	discover: '发现',
	tavern: '酒馆',
	inbox: '会话',
	user: '我的'
};

export default {
	data() {
		return {
			activeRoute: 'pages/index/index',
			labels: Object.assign({}, DEFAULT_LABELS),
			badges: { inbox: 0 },
			items: [
				{
					key: 'discover',
					pagePath: 'pages/index/index',
					iconPath: '/static/tabbar/home.png',
					activeIconPath: '/static/tabbar/home1.png'
				},
				{
					key: 'tavern',
					pagePath: 'pages/tavern/tavern',
					iconPath: '/static/tabbar/work.png',
					activeIconPath: '/static/tabbar/work1.png'
				},
				{
					key: 'inbox',
					pagePath: 'pages/tavern/tavernInbox',
					iconPath: '/static/tabbar/chat.png',
					activeIconPath: '/static/tabbar/chat1.png'
				},
				{
					key: 'user',
					pagePath: 'pages/user/user',
					iconPath: '/static/tabbar/user.png',
					activeIconPath: '/static/tabbar/user1.png'
				}
			]
		};
	},
	created() {
		this.applyState(getTavernTabBarState());
	},
	mounted() {
		this.applyState(getTavernTabBarState());
	},
	methods: {
		applyState(state) {
			const next = state && typeof state === 'object' ? state : {};
			this.activeRoute = next.activeRoute || this.activeRoute || 'pages/index/index';
			this.labels = Object.assign({}, DEFAULT_LABELS, next.labels || {});
			this.badges = Object.assign({ inbox: 0 }, next.badges || {});
		},
		isActive(item) {
			return !!item && item.pagePath === this.activeRoute;
		},
		displayIconPath(item) {
			if (!item) return '';
			return this.isActive(item) ? item.activeIconPath || item.iconPath : item.iconPath;
		},
		itemLabel(item) {
			if (!item) return '';
			return this.labels[item.key] || DEFAULT_LABELS[item.key] || '';
		},
		itemBadge(item) {
			if (!item || !item.key) return '';
			const count = Math.max(0, Number(this.badges[item.key]) || 0);
			if (!count) return '';
			return count > 99 ? '99+' : String(count);
		},
		switchTab(item) {
			if (!item || !item.pagePath || item.pagePath === this.activeRoute) return;
			this.activeRoute = item.pagePath;
			uni.switchTab({ url: '/' + item.pagePath });
		}
	}
};
</script>

<style lang="scss" scoped>
.tavern-tabbar-shell {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 998;
	padding: 0 20rpx calc(16rpx + env(safe-area-inset-bottom));
	pointer-events: none;
}

.tavern-tabbar-card {
	display: flex;
	align-items: stretch;
	padding: 8rpx;
	border-radius: 32rpx;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(246, 252, 255, 0.92) 100%),
		radial-gradient(circle at 12% 0%, rgba(190, 230, 247, 0.36), transparent 40%),
		radial-gradient(circle at 92% 0%, rgba(255, 220, 238, 0.38), transparent 34%);
	border: 1rpx solid rgba(255, 255, 255, 0.92);
	box-shadow:
		0 18rpx 42rpx rgba(67, 112, 142, 0.16),
		0 4rpx 14rpx rgba(67, 112, 142, 0.08),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.74);
	backdrop-filter: blur(16px);
	-webkit-backdrop-filter: blur(16px);
	pointer-events: auto;
}

.tavern-tabbar-item {
	flex: 1;
	min-width: 0;
	padding: 4rpx;
}

.tavern-tabbar-pill {
	position: relative;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	height: 96rpx;
	border-radius: 24rpx;
	background: transparent;
	transition: all 0.22s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-pill {
	transform: translateY(-4rpx);
	background:
		linear-gradient(135deg, rgba(219, 246, 250, 0.96) 0%, rgba(255, 235, 243, 0.9) 100%);
	box-shadow:
		0 12rpx 24rpx rgba(52, 143, 184, 0.14),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
}

.tavern-tabbar-icon-wrap {
	position: relative;
	width: 48rpx;
	height: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.tavern-tabbar-badge {
	position: absolute;
	top: -10rpx;
	right: -16rpx;
	min-width: 34rpx;
	height: 34rpx;
	padding: 0 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #ff8fb1 0%, #f4a6c4 100%);
	color: #fff;
	font-size: 18rpx;
	font-weight: 700;
	line-height: 1;
	box-shadow: 0 10rpx 18rpx rgba(244, 131, 162, 0.28);
	border: 2rpx solid rgba(255, 255, 255, 0.96);
}

.tavern-tabbar-icon {
	width: 42rpx;
	height: 42rpx;
	display: block;
	opacity: 0.68;
	filter: saturate(0.82);
	transition: transform 0.2s ease, opacity 0.2s ease, filter 0.2s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-icon {
	opacity: 1;
	transform: scale(1.1);
	filter: saturate(1.08) brightness(1.03);
}

.tavern-tabbar-label {
	max-width: 100%;
	font-size: 22rpx;
	font-weight: 600;
	line-height: 1;
	color: #6d8293;
	letter-spacing: 1rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	transition: all 0.22s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-label {
	color: #247494;
	font-weight: 700;
	text-shadow: 0 4rpx 10rpx rgba(52, 143, 184, 0.12);
}

.tavern-tabbar-item--active .tavern-tabbar-pill::after {
	content: '';
	position: absolute;
	left: 50%;
	bottom: 7rpx;
	width: 36rpx;
	height: 6rpx;
	border-radius: 999rpx;
	background: linear-gradient(90deg, rgba(52, 143, 184, 0.14) 0%, rgba(52, 143, 184, 0.9) 50%, rgba(244, 166, 196, 0.18) 100%);
	transform: translateX(-50%);
	box-shadow: 0 0 16rpx rgba(52, 143, 184, 0.2);
}
</style>
