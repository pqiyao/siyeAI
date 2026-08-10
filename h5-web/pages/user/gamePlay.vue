<template>
	<view class="game-play-page">
		<view v-if="currentGame" class="game-stage">
			<!-- #ifdef H5 -->
			<iframe
				class="game-frame"
				:src="currentGame.url"
				:sandbox="currentGame.sandbox || null"
				allow="autoplay; fullscreen"
				allowfullscreen="true"
				referrerpolicy="no-referrer"
				frameborder="0"
			></iframe>
			<!-- #endif -->
			<!-- #ifndef H5 -->
			<web-view class="game-frame" :src="currentGame.url"></web-view>
			<!-- #endif -->

			<view class="game-back" @tap="goBack">
				<text class="game-back-text">返回</text>
			</view>
		</view>

		<view v-else class="game-empty">
			<text class="game-empty-title">游戏不存在</text>
			<view class="game-empty-action" @tap="goBack">
				<text class="game-empty-action-text">返回</text>
			</view>
		</view>
	</view>
</template>

<script>
const GAME_MAP = {
	mikutap: {
		name: 'Mikutap',
		url: '/static/games/mikutap/index.html'
	},
	cube: {
		name: '3D 魔方',
		url: '/static/games/cube/index.html'
	},
	gobang: {
		name: '五子棋',
		url: '/static/games/gobang/index.html',
		sandbox: 'allow-scripts'
	},
	fishjoy: {
		name: '捕鱼达人',
		url: '/static/games/fishjoy/index.html'
	},
	tetris: {
		name: '俄罗斯方块',
		url: '/static/games/tetris/index.html'
	}
};

export default {
	data() {
		return {
			gameId: ''
		};
	},
	computed: {
		currentGame() {
			return GAME_MAP[this.gameId] || null;
		}
	},
	onLoad(query) {
		this.gameId = query && query.id ? decodeURIComponent(query.id) : '';
	},
	methods: {
		goBack() {
			uni.navigateBack({
				fail() {
					uni.redirectTo({ url: '/pages/user/gameCenter' });
				}
			});
		}
	}
};
</script>

<style lang="scss" scoped>
.game-play-page {
	position: fixed;
	inset: 0;
	width: 100vw;
	height: 100vh;
	background: #000;
	overflow: hidden;
}

/* #ifdef H5 */
.game-play-page {
	width: 100dvw;
	height: 100dvh;
}
/* #endif */

.game-stage,
.game-frame {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
	border: 0;
	background: #000;
	overflow: hidden;
}

.game-back {
	position: fixed;
	z-index: 20;
	top: calc(18rpx + env(safe-area-inset-top));
	left: 18rpx;
	min-width: 92rpx;
	height: 52rpx;
	padding: 0 18rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(0, 0, 0, 0.46);
	border: 1rpx solid rgba(255, 255, 255, 0.22);
	backdrop-filter: blur(8px);
}

.game-back-text {
	font-size: 22rpx;
	font-weight: 800;
	color: #ffffff;
}

.game-empty {
	position: absolute;
	inset: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 24rpx;
	background: #07101a;
}

.game-empty-title {
	font-size: 32rpx;
	font-weight: 900;
	color: #ffffff;
}

.game-empty-action {
	min-width: 150rpx;
	height: 60rpx;
	padding: 0 24rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.14);
	display: flex;
	align-items: center;
	justify-content: center;
}

.game-empty-action-text {
	font-size: 24rpx;
	font-weight: 900;
	color: #ffffff;
}
</style>
