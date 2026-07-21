<template>
	<view class="game-page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="小游戏" mode="dark" @back="goBack" />

		<scroll-view class="game-scroll" scroll-y :show-scrollbar="false">
			<view class="game-hero">
				<view class="game-hero-copy">
					<text class="game-kicker">GAME CENTER</text>
					<text class="game-title">小游戏中心</text>
					<text class="game-subtitle">轻量 HTML 小游戏，随时点开玩一局。</text>
				</view>
				<view class="game-count">
					<text class="game-count-num">{{ games.length }}</text>
					<text class="game-count-label">款游戏</text>
				</view>
			</view>

			<view v-if="highlightGame" class="section-head">
				<text class="section-title">推荐游玩</text>
			</view>

			<view v-if="highlightGame" class="game-feature" @tap="openGame(highlightGame)">
				<image class="feature-cover" :src="highlightGame.cover" mode="aspectFill"></image>
				<view class="feature-main">
					<view class="feature-line">
						<text class="feature-name">{{ highlightGame.name }}</text>
					</view>
					<text class="feature-desc">{{ highlightGame.desc }}</text>
				</view>
				<view class="feature-play">
					<text class="feature-play-text">开始</text>
				</view>
			</view>

			<view class="section-head section-head--library">
				<text class="section-title">游戏库</text>
			</view>

			<view class="game-list">
				<view
					v-for="game in libraryGames"
					:key="game.id"
					class="game-row"
					@tap="openGame(game)"
				>
					<image class="game-cover" :src="game.cover" mode="aspectFill"></image>
					<view class="game-info">
						<view class="game-head">
							<text class="game-name">{{ game.name }}</text>
						</view>
						<text class="game-desc">{{ game.desc }}</text>
					</view>
					<view class="game-play">
						<text class="game-play-text">玩</text>
					</view>
				</view>
			</view>

			<view v-if="!libraryGames.length" class="game-empty">
				<text class="game-empty-title">暂无更多游戏</text>
				<text class="game-empty-desc">当前分类只有上面的推荐项。</text>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

export default {
	components: { TavernNavBar },
	data() {
		return {
			games: [
				{
					id: 'gobang',
					name: '五子棋',
					desc: '默认人机对战，也可以切换成双人轮流落子。',
					tag: 'AI 棋类',
					mode: '人机/双人',
					size: '约 200KB',
					control: '点击落子',
					category: 'ai',
					cover: '/static/games/gobang/picture/bak.jpg'
				},
				{
					id: 'mikutap',
					name: 'Mikutap',
					desc: '点击屏幕触发声音和动画，适合碎片时间放松。',
					tag: '音乐互动',
					mode: '单人',
					size: '约 2.1MB',
					control: '点击/滑动',
					category: 'music',
					cover: '/static/games/mikutap/icon.png'
				},
				{
					id: 'cube',
					name: '3D 魔方',
					desc: '旋转魔方挑战复原，支持计时和主题切换。',
					tag: '休闲解谜',
					mode: '单人',
					size: '约 620KB',
					control: '拖动旋转',
					category: 'puzzle',
					cover: '/static/games/cube/cover.svg'
				},
				{
					id: 'fishjoy',
					name: '捕鱼达人',
					desc: '横屏点击开炮，适合手机 H5 和 APP web-view 游玩。',
					tag: '街机射击',
					mode: '单人横屏',
					size: '约 4MB',
					control: '点击开炮',
					category: 'arcade',
					cover: '/static/games/fishjoy/images/startbg.jpg'
				},
				{
					id: 'tetris',
					name: '俄罗斯方块',
					desc: '已补手机触控按钮和滑动操作，可在手机端控制。',
					tag: '经典益智',
					mode: '单人',
					size: '约 1MB',
					control: '按钮/滑动',
					category: 'puzzle',
					cover: '/static/games/tetris/images/1.jpg'
				}
			]
		};
	},
	computed: {
		filteredGames() {
			return this.games;
		},
		highlightGame() {
			return this.filteredGames[0] || null;
		},
		libraryGames() {
			return this.filteredGames.slice(1);
		}
	},
	methods: {
		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/user/user' });
				}
			});
		},
		openGame(game) {
			if (!game || !game.id) return;
			uni.navigateTo({
				url: '/pages/user/gamePlay?id=' + encodeURIComponent(game.id)
			});
		}
	}
};
</script>

<style lang="scss" scoped>
.game-page {
	position: relative;
	min-height: 100vh;
	box-sizing: border-box;
	overflow: hidden;
}

/* #ifdef H5 */
.game-page {
	min-height: 100dvh;
}
/* #endif */

.app-page-bg {
	position: fixed;
	inset: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
}

.game-scroll {
	position: relative;
	z-index: 1;
	height: 100vh;
	padding: 112rpx 24rpx calc(44rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

/* #ifdef H5 */
.game-scroll {
	height: 100dvh;
}
/* #endif */

.game-hero {
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 24rpx;
	padding: 32rpx 28rpx;
	border-radius: 28rpx;
	background:
		linear-gradient(135deg, rgba(20, 32, 44, 0.96) 0%, rgba(36, 66, 78, 0.94) 58%, rgba(89, 73, 48, 0.94) 100%);
	border: 1rpx solid rgba(255, 255, 255, 0.13);
	box-shadow: 0 18rpx 44rpx rgba(4, 8, 18, 0.28);
}

.game-hero-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}

.game-kicker {
	font-size: 21rpx;
	font-weight: 900;
	color: rgba(180, 222, 229, 0.94);
}

.game-title {
	font-size: 42rpx;
	font-weight: 900;
	color: #ffffff;
}

.game-subtitle {
	font-size: 24rpx;
	line-height: 1.55;
	color: rgba(241, 245, 249, 0.78);
}

.game-count {
	width: 116rpx;
	height: 116rpx;
	border-radius: 28rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.12);
	border: 1rpx solid rgba(255, 255, 255, 0.16);
	flex-shrink: 0;
}

.game-count-num {
	font-size: 38rpx;
	font-weight: 900;
	color: #ffffff;
	line-height: 1;
}

.game-count-label {
	margin-top: 8rpx;
	font-size: 19rpx;
	font-weight: 800;
	color: rgba(226, 232, 240, 0.76);
}

.section-head {
	margin-top: 24rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.section-head--library {
	margin-top: 28rpx;
}

.section-title {
	font-size: 28rpx;
	font-weight: 900;
	color: #f8fafc;
}

.section-note {
	font-size: 21rpx;
	font-weight: 800;
	color: rgba(226, 232, 240, 0.64);
}

.game-feature {
	margin-top: 14rpx;
	display: flex;
	align-items: center;
	gap: 20rpx;
	min-height: 176rpx;
	padding: 20rpx;
	border-radius: 26rpx;
	background: rgba(255, 255, 255, 0.94);
	border: 1rpx solid rgba(255, 255, 255, 0.68);
	box-shadow: 0 18rpx 42rpx rgba(31, 61, 92, 0.16);
	box-sizing: border-box;
}

.feature-cover {
	width: 132rpx;
	height: 132rpx;
	border-radius: 24rpx;
	flex-shrink: 0;
	background: rgba(235, 241, 245, 0.9);
}

.feature-main {
	flex: 1;
	min-width: 0;
}

.feature-line,
.game-head {
	display: flex;
	align-items: center;
	gap: 12rpx;
	min-width: 0;
}

.feature-name,
.game-name {
	min-width: 0;
	flex: 1;
	font-size: 29rpx;
	font-weight: 900;
	color: #172033;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.feature-tag,
.game-tag {
	flex-shrink: 0;
	padding: 7rpx 12rpx;
	border-radius: 999rpx;
	background: rgba(79, 147, 163, 0.12);
	color: #236f82;
	font-size: 19rpx;
	font-weight: 900;
}

.feature-desc,
.game-desc {
	display: -webkit-box;
	margin-top: 8rpx;
	font-size: 22rpx;
	line-height: 1.45;
	color: #5f7280;
	overflow: hidden;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.feature-meta,
.game-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.feature-meta-item,
.game-meta-item {
	padding: 6rpx 10rpx;
	border-radius: 999rpx;
	background: rgba(238, 242, 247, 0.92);
	color: #56677a;
	font-size: 19rpx;
	font-weight: 800;
}

.feature-play {
	width: 92rpx;
	height: 92rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #4f93a3;
	box-shadow: 0 14rpx 26rpx rgba(48, 103, 117, 0.22);
	flex-shrink: 0;
}

.feature-play-text {
	font-size: 22rpx;
	font-weight: 900;
	color: #ffffff;
}

.game-list {
	margin-top: 14rpx;
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}

.game-row {
	display: flex;
	align-items: center;
	gap: 18rpx;
	min-height: 140rpx;
	padding: 16rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.9);
	border: 1rpx solid rgba(255, 255, 255, 0.64);
	box-shadow: 0 14rpx 30rpx rgba(31, 61, 92, 0.12);
	box-sizing: border-box;
}

.game-cover {
	width: 96rpx;
	height: 96rpx;
	border-radius: 20rpx;
	flex-shrink: 0;
	background: rgba(235, 241, 245, 0.9);
}

.game-info {
	flex: 1;
	min-width: 0;
}

.game-name {
	font-size: 27rpx;
}

.game-play {
	flex-shrink: 0;
	width: 60rpx;
	height: 60rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #172033;
}

.game-play-text {
	font-size: 21rpx;
	font-weight: 900;
	color: #ffffff;
}

.game-empty {
	margin-top: 14rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.72);
	border: 1rpx solid rgba(255, 255, 255, 0.58);
}

.game-empty-title {
	display: block;
	font-size: 25rpx;
	font-weight: 900;
	color: #223244;
}

.game-empty-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: #657586;
}
</style>
