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
	tavern: '角色',
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
	padding: 0 20rpx calc(18rpx + env(safe-area-inset-bottom));
	pointer-events: none;
}

.tavern-tabbar-card {
	display: flex;
	align-items: stretch;
	padding: 10rpx;
	border-radius: 34rpx;
	background: linear-gradient(180deg, rgba(20, 24, 36, 0.96) 0%, rgba(11, 13, 22, 0.96) 100%);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow:
		0 20rpx 46rpx rgba(4, 8, 18, 0.46),
		inset 0 1rpx 0 rgba(255, 255, 255, 0.05);
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
	gap: 10rpx;
	height: 100rpx;
	border-radius: 18rpx;
	background: transparent;
	transition: all 0.22s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-pill {
	transform: translateY(-3rpx);
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
	background: linear-gradient(135deg, #fb7185 0%, #ec4899 100%);
	color: #fff;
	font-size: 18rpx;
	font-weight: 700;
	line-height: 1;
	box-shadow: 0 10rpx 18rpx rgba(236, 72, 153, 0.34);
	border: 2rpx solid rgba(9, 11, 18, 0.92);
}

.tavern-tabbar-icon {
	width: 42rpx;
	height: 42rpx;
	display: block;
	opacity: 0.88;
	transition: transform 0.2s ease, opacity 0.2s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-icon {
	opacity: 1;
	transform: scale(1.12);
}

.tavern-tabbar-label {
	max-width: 100%;
	font-size: 22rpx;
	font-weight: 600;
	line-height: 1;
	color: #8f97ae;
	letter-spacing: 1rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	transition: all 0.22s ease;
}

.tavern-tabbar-item--active .tavern-tabbar-label {
	color: #ec4899;
	font-weight: 700;
	text-shadow: 0 4rpx 10rpx rgba(244, 114, 182, 0.16);
}

.tavern-tabbar-item--active .tavern-tabbar-pill::after {
	content: '';
	position: absolute;
	left: 50%;
	bottom: 8rpx;
	width: 34rpx;
	height: 6rpx;
	border-radius: 999rpx;
	background: linear-gradient(90deg, rgba(244, 114, 182, 0.18) 0%, rgba(236, 72, 153, 0.92) 50%, rgba(244, 114, 182, 0.18) 100%);
	transform: translateX(-50%);
	box-shadow: 0 0 16rpx rgba(236, 72, 153, 0.28);
}
</style>
