<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="pageTitle" mode="dark" @back="goBack" />

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view v-if="loading" class="state-card state-card--loading">
				<view class="loading-mark"></view>
				<text class="state-text">加载中...</text>
			</view>

			<view v-else-if="!loggedIn" class="state-card">
				<image class="state-icon" src="/static/user/checkin-calendar.png" mode="aspectFit"></image>
				<text class="state-kicker">需要登录</text>
				<text class="state-text">登录后领取今日钻石与次数奖励</text>
				<view class="primary-btn" @tap="goLogin">
					<text>去登录</text>
					<text class="primary-btn-icon">→</text>
				</view>
			</view>

			<view v-else class="content">
				<view class="checkin-hero">
					<view class="hero-head">
						<image class="hero-icon" src="/static/user/checkin-calendar.png" mode="aspectFit"></image>
						<view class="hero-copy">
							<text class="hero-tag">{{ activityName || pageTitle }}</text>
							<text class="hero-title">今日奖励</text>
							<text class="hero-subtitle">{{ bizDate || '每天都来领一点' }}</text>
						</view>
						<view class="streak-badge">
							<text class="streak-value">{{ streakDay }}</text>
							<text class="streak-label">天连续</text>
						</view>
					</view>

					<view class="reward-grid">
						<view class="reward-item reward-item--diamond">
							<image class="reward-icon" src="/static/home/zs.png" mode="aspectFit"></image>
							<view class="reward-copy">
								<text class="reward-label">钻石</text>
								<text class="reward-value">{{ todayReward.score }}</text>
							</view>
						</view>
						<view class="reward-item reward-item--gold">
							<image class="reward-icon reward-icon--wallet" src="/static/user/qian.png" mode="aspectFit"></image>
							<view class="reward-copy">
								<text class="reward-label">金币</text>
								<text class="reward-value">{{ todayReward.gold }}</text>
							</view>
						</view>
						<view class="reward-item reward-item--chat">
							<image class="reward-icon" src="/static/home/sendchat.png" mode="aspectFit"></image>
							<view class="reward-copy">
								<text class="reward-label">聊天次数</text>
								<text class="reward-value">+{{ todayReward.chatBonus }}</text>
							</view>
						</view>
						<view class="reward-item reward-item--image">
							<image class="reward-icon" src="/static/chat/image-generate.png" mode="aspectFit"></image>
							<view class="reward-copy">
								<text class="reward-label">生图次数</text>
								<text class="reward-value">+{{ todayReward.imageBonus }}</text>
							</view>
						</view>
					</view>
				</view>

				<view class="claim-btn" :class="{ disabled: !canClaim, busy: claiming }" @tap="onClaim">
					<text class="claim-btn-icon">✓</text>
					<text class="claim-btn-text">{{ claimButtonText }}</text>
				</view>

				<view class="checkin-section">
					<view class="section-head">
						<image class="section-icon" src="/static/user/checkin-calendar.png" mode="aspectFit"></image>
						<view class="section-copy">
							<text class="section-title">近七日</text>
							<text class="section-subtitle">每天签到，奖励持续到账</text>
						</view>
					</view>
					<view class="track">
						<view
							v-for="item in weekTrack"
							:key="item.date"
							class="track-item"
							:class="{ 'is-done': item.claimed, 'is-today': item.isToday }"
						>
							<view class="track-dot">
							<text v-if="item.claimed" class="track-check">✓</text>
							</view>
							<text class="track-day">{{ item.isToday ? todayLabel : (item.dayOfMonth || item.dayIndex) }}</text>
							<text class="track-state">{{ item.claimed ? doneLabel : (item.isToday ? todoLabel : '') }}</text>
						</view>
					</view>
				</view>

				<view class="checkin-section balance-card">
					<view class="section-head">
						<image class="section-icon section-icon--wallet" src="/static/user/qian.png" mode="aspectFit"></image>
						<view class="section-copy">
							<text class="section-title">当前资产</text>
							<text class="section-subtitle">签到奖励会直接存入账户</text>
						</view>
					</view>
					<view class="balance-row">
						<view class="balance-item">
							<view class="balance-label-row">
								<image class="balance-label-icon" src="/static/home/zs.png" mode="aspectFit"></image>
								<text class="balance-label">钻石</text>
							</view>
							<text class="balance-value">{{ profile.score }}</text>
						</view>
						<view class="balance-item">
							<view class="balance-label-row">
								<image class="balance-label-icon balance-label-icon--wallet" src="/static/user/qian.png" mode="aspectFit"></image>
								<text class="balance-label">金币</text>
							</view>
							<text class="balance-value">{{ profile.goldCoin }}</text>
						</view>
					</view>
					<view class="balance-meta">
						<view class="quota-item">
							<image class="quota-icon" src="/static/home/sendchat.png" mode="aspectFit"></image>
							<text>聊天剩余</text>
							<text class="quota-value">{{ profile.dailyChatRemaining }}</text>
						</view>
						<view class="quota-item">
							<image class="quota-icon" src="/static/chat/image-generate.png" mode="aspectFit"></image>
							<text>生图剩余</text>
							<text class="quota-value">{{ profile.dailyImageRemaining }}</text>
						</view>
					</view>
				</view>

				<view class="note-card">
					<image class="note-icon" src="/static/user/checkin-calendar.png" mode="aspectFit"></image>
					<view class="note-copy">
						<text class="note-title">明日可领</text>
						<text class="note-text">{{ tomorrowPreviewText }}</text>
						<text class="note-text note-text--soft">每日 0 点刷新次数与签到奖励</text>
					</view>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

function emptyReward() {
	return { score: 0, gold: 0, chatBonus: 0, imageBonus: 0 };
}
function emptyProfile() {
	return { score: 0, goldCoin: 0, dailyChatRemaining: 0, dailyImageRemaining: 0 };
}
function formatReward(reward) {
	const parts = [];
	const r = reward || emptyReward();
	if (Number(r.score) > 0) parts.push('钻石 ' + r.score);
	if (Number(r.gold) > 0) parts.push('金币 ' + r.gold);
	if (Number(r.chatBonus) > 0) parts.push('聊天 +' + r.chatBonus);
	if (Number(r.imageBonus) > 0) parts.push('生图 +' + r.imageBonus);
	return parts.length ? parts.join(' · ') : '明日继续';
}
function friendlyError(e, fallback) {
	const raw = String((e && (e.message || e.msg)) || fallback || '').trim();
	if (!raw || raw === 'request failed' || raw.indexOf('请求失败') === 0 || raw === 'network') {
		return fallback;
	}
	return raw;
}

export default {
	components: { TavernNavBar },
	data() {
		return {
			pageTitle: '每日签到',
			todayLabel: '今',
			doneLabel: '已领',
			todoLabel: '待领',
			loading: true,
			claiming: false,
			loggedIn: false,
			enabled: false,
			claimedToday: false,
			bizDate: '',
			streakDay: 1,
			activityName: '每日签到',
			todayReward: emptyReward(),
			tomorrowReward: emptyReward(),
			weekTrack: [],
			profile: emptyProfile()
		};
	},
	computed: {
		canClaim() {
			return this.loggedIn && this.enabled && !this.claimedToday && !this.claiming;
		},
		claimButtonText() {
			if (this.claiming) return '领取中...';
			if (!this.enabled) return '活动未开启';
			if (this.claimedToday) return '今日已领';
			return '领取今日奖励';
		},
		tomorrowPreviewText() {
			return formatReward(this.tomorrowReward);
		}
	},
	onShow() {
		this.refresh();
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		goLogin() {
			uni.navigateTo({ url: tavernApi.buildLoginUrl ? tavernApi.buildLoginUrl() : '/pages/login/login' });
		},
		applyStatus(data) {
			const p = data || {};
			this.enabled = !!p.enabled;
			this.claimedToday = !!p.claimedToday;
			this.bizDate = p.bizDate || '';
			this.streakDay = Number(p.streakDay || 1);
			this.activityName = p.activityName || '每日签到';
			this.todayReward = Object.assign(emptyReward(), p.todayReward || {});
			this.tomorrowReward = Object.assign(emptyReward(), p.tomorrowReward || {});
			this.weekTrack = Array.isArray(p.weekTrack) ? p.weekTrack : [];
			this.profile = Object.assign(emptyProfile(), p.profile || {});
		},
		async refresh() {
			this.loading = true;
			try {
				const user = uni.getStorageSync('user') || {};
				this.loggedIn = !!(user && user.token);
				if (!this.loggedIn) {
					this.enabled = false;
					this.claimedToday = false;
					return;
				}
				const data = await tavernApi.fetchCheckinStatus(tavernApi.getClientUid());
				this.applyStatus(data);
			} catch (e) {
				uni.showToast({ title: friendlyError(e, '加载失败，请稍后重试').slice(0, 40), icon: 'none' });
			} finally {
				this.loading = false;
			}
		},
		async onClaim() {
			if (!this.canClaim) return;
			this.claiming = true;
			try {
				const data = await tavernApi.claimCheckin(tavernApi.getClientUid());
				this.applyStatus(data);
				try { uni.setStorageSync('tavern_checkin_refresh_needed', '1'); } catch (e) {}
				const granted = formatReward(data && data.granted);
				uni.showToast({
					title: data && data.justClaimed ? ('已领取 ' + granted) : '今日已领',
					icon: 'none'
				});
			} catch (e) {
				uni.showToast({ title: friendlyError(e, '领取失败，请稍后重试').slice(0, 40), icon: 'none' });
			} finally {
				this.claiming = false;
			}
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	--checkin-ink: #173042;
	--checkin-muted: #3f596a;
	--checkin-primary: #2795c5;
	--checkin-primary-dark: #1f719e;
	--checkin-line: rgba(41, 83, 104, 0.14);
	--checkin-card: rgba(255, 255, 255, 0.92);
	min-height: 100vh;
	background: linear-gradient(180deg, rgba(234, 247, 251, 0.76), rgba(251, 247, 248, 0.88));
	font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
	color: var(--checkin-ink);
}
.app-page-bg {
	position: fixed;
	inset: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
	opacity: 0.24;
}
.scroll {
	position: relative;
	z-index: 1;
	height: calc(100vh - 88rpx);
	padding: 28rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}
.content,
.state-card {
	width: 100%;
	max-width: 760px;
	margin-left: auto;
	margin-right: auto;
	box-sizing: border-box;
}
.state-card,
.checkin-hero,
.checkin-section,
.note-card {
	background: var(--checkin-card);
	border-radius: 20rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.9);
	box-shadow: 0 18rpx 44rpx rgba(36, 72, 91, 0.1);
	padding: 28rpx;
	margin-bottom: 20rpx;
	box-sizing: border-box;
}
.state-card {
	margin-top: 56rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 14rpx;
	padding: 54rpx 32rpx;
}
.state-icon { width: 66rpx; height: 66rpx; margin-bottom: 8rpx; }
.state-kicker { font-size: 32rpx; font-weight: 700; color: var(--checkin-ink); }
.state-text { font-size: 28rpx; color: var(--checkin-muted); text-align: center; line-height: 1.6; font-weight: 500; }
.state-card--loading { min-height: 260rpx; justify-content: center; }
.loading-mark {
	width: 44rpx;
	height: 44rpx;
	border-radius: 50%;
	border: 5rpx solid #d8e8ee;
	border-top-color: var(--checkin-primary);
	animation: checkin-spin 0.8s linear infinite;
}
@keyframes checkin-spin { to { transform: rotate(360deg); } }
.primary-btn,
.claim-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 92rpx;
	border-radius: 18rpx;
	background: linear-gradient(135deg, var(--checkin-primary), var(--checkin-primary-dark));
	box-shadow: 0 14rpx 30rpx rgba(8, 124, 167, 0.22);
	letter-spacing: 0;
}
.primary-btn {
	margin-top: 18rpx;
	padding: 0 36rpx;
	height: 76rpx;
	gap: 16rpx;
	color: #fff;
	font-size: 26rpx;
	font-weight: 700;
}
.primary-btn-icon { font-size: 32rpx; line-height: 1; }
.claim-btn-text {
	color: #fff;
	font-size: 28rpx;
	font-weight: 700;
	letter-spacing: 0;
}
.claim-btn-icon {
	margin-right: 12rpx;
	color: #fff;
	font-size: 26rpx;
	font-weight: 700;
}
.checkin-hero {
	position: relative;
	overflow: hidden;
	background: linear-gradient(145deg, #ffffff 0%, #eef8fd 58%, #fff4f7 100%);
	border-color: rgba(255, 255, 255, 0.98);
	box-shadow: 0 20rpx 46rpx rgba(53, 91, 111, 0.12);
	padding: 30rpx;
}
.checkin-hero::after {
	content: '';
	position: absolute;
	right: -16%;
	top: 0;
	width: 52%;
	height: 100%;
	background: repeating-linear-gradient(120deg, transparent 0, transparent 22rpx, rgba(38, 137, 179, 0.04) 22rpx, rgba(38, 137, 179, 0.04) 24rpx);
	pointer-events: none;
}
.hero-head { position: relative; z-index: 1; display: flex; align-items: center; min-width: 0; }
.hero-icon {
	width: 62rpx;
	height: 62rpx;
	flex-shrink: 0;
}
.hero-copy { flex: 1; min-width: 0; margin-left: 20rpx; }
.hero-tag {
	display: block;
	font-size: 24rpx;
	color: #147da8;
	font-weight: 700;
}
.hero-title {
	display: block;
	margin-top: 4rpx;
	font-size: 38rpx;
	font-weight: 700;
	color: var(--checkin-ink);
	line-height: 1.22;
}
.hero-subtitle {
	display: block;
	margin-top: 6rpx;
	font-size: 24rpx;
	color: var(--checkin-muted);
	font-weight: 500;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
.streak-badge {
	width: 92rpx;
	height: 82rpx;
	flex-shrink: 0;
	margin-left: 14rpx;
	border-radius: 18rpx;
	background: #ffe6a8;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	color: #7f4b08;
}
.streak-value { font-size: 32rpx; line-height: 1; font-weight: 800; }
.streak-label { margin-top: 5rpx; font-size: 21rpx; font-weight: 700; }
.reward-grid {
	position: relative;
	z-index: 1;
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	margin-top: 28rpx;
	border-top: 1rpx solid var(--checkin-line);
	border-bottom: 1rpx solid var(--checkin-line);
}
.reward-item {
	min-width: 0;
	padding: 22rpx 12rpx;
	display: flex;
	align-items: center;
	border-bottom: 1rpx solid var(--checkin-line);
	box-sizing: border-box;
}
.reward-item:nth-child(odd) { border-right: 1rpx solid var(--checkin-line); }
.reward-item:nth-child(n + 3) { border-bottom: 0; }
.reward-icon { width: 38rpx; height: 38rpx; flex-shrink: 0; }
.reward-icon--wallet { width: 46rpx; height: 46rpx; }
.reward-copy { min-width: 0; margin-left: 14rpx; display: flex; flex-direction: column; }
.reward-label {
	font-size: 24rpx;
	color: var(--checkin-muted);
	font-weight: 600;
	white-space: nowrap;
}
.reward-value {
	display: block;
	margin-top: 2rpx;
	font-size: 34rpx;
	line-height: 1.1;
	color: var(--checkin-ink);
	font-weight: 800;
}
.reward-item--diamond .reward-value { color: #7353b7; }
.reward-item--gold .reward-value { color: #a76112; }
.reward-item--chat .reward-value { color: #177660; }
.reward-item--image .reward-value { color: #bb5264; }
.claim-btn { margin: 8rpx 0 20rpx; }
.claim-btn.disabled,
.claim-btn.busy {
	background: #a9b8bf;
	box-shadow: none;
	opacity: 0.78;
}
.checkin-section { border-color: rgba(255, 255, 255, 0.96); }
.section-head { display: flex; align-items: center; margin-bottom: 24rpx; }
.section-icon {
	width: 40rpx;
	height: 40rpx;
	flex-shrink: 0;
}
.section-icon--wallet { width: 50rpx; height: 50rpx; }
.section-copy { min-width: 0; margin-left: 16rpx; display: flex; flex-direction: column; }
.section-title {
	display: block;
	font-size: 29rpx;
	line-height: 1.25;
	color: var(--checkin-ink);
	font-weight: 700;
}
.section-subtitle {
	margin-top: 5rpx;
	font-size: 24rpx;
	line-height: 1.35;
	color: var(--checkin-muted);
	font-weight: 500;
}
.track {
	position: relative;
	display: flex;
	justify-content: space-between;
	gap: 8rpx;
}
.track::before {
	content: '';
	position: absolute;
	top: 23rpx;
	left: 6%;
	right: 6%;
	height: 2rpx;
	background: #d9e6eb;
}
.track-item {
	position: relative;
	z-index: 1;
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
}
.track-dot {
	width: 44rpx;
	height: 44rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #fff;
	border: 2rpx solid #c9d9df;
}
.track-item.is-done .track-dot {
	background: #218a78;
	border-color: #218a78;
}
.track-item.is-today .track-dot {
	box-shadow: 0 0 0 6rpx rgba(8, 124, 167, 0.12);
	border-color: var(--checkin-primary);
}
.track-check {
	color: #fff;
	font-size: 22rpx;
	font-weight: 700;
}
.track-day { font-size: 24rpx; color: var(--checkin-ink); font-weight: 700; }
.track-state { font-size: 23rpx; min-height: 32rpx; color: var(--checkin-muted); font-weight: 600; }
.track-item.is-today .track-day,
.track-item.is-today .track-state { color: var(--checkin-primary-dark); font-weight: 700; }
.balance-row {
	display: flex;
	border-top: 1rpx solid var(--checkin-line);
	border-bottom: 1rpx solid var(--checkin-line);
}
.balance-item {
	flex: 1;
	min-width: 0;
	padding: 22rpx 18rpx;
}
.balance-item + .balance-item { border-left: 1rpx solid var(--checkin-line); }
.balance-label-row { display: flex; align-items: center; }
.balance-label-icon { width: 28rpx; height: 28rpx; margin-right: 9rpx; }
.balance-label-icon--wallet { width: 34rpx; height: 34rpx; }
.balance-label { display: block; font-size: 25rpx; color: var(--checkin-muted); font-weight: 600; }
.balance-value { display: block; margin-top: 8rpx; font-size: 36rpx; color: var(--checkin-ink); font-weight: 800; }
.balance-meta {
	margin-top: 18rpx;
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 12rpx;
}
.quota-item {
	min-width: 0;
	height: 64rpx;
	padding: 0 14rpx;
	border-radius: 14rpx;
	background: #f2f7f9;
	display: flex;
	align-items: center;
	font-size: 24rpx;
	color: var(--checkin-muted);
	font-weight: 600;
	box-sizing: border-box;
}
.quota-icon { width: 28rpx; height: 28rpx; margin-right: 10rpx; flex-shrink: 0; }
.quota-value { margin-left: auto; color: var(--checkin-ink); font-size: 25rpx; font-weight: 800; }
.note-card {
	display: flex;
	align-items: flex-start;
	background: rgba(255, 249, 235, 0.94);
	border-color: rgba(230, 181, 83, 0.2);
}
.note-icon {
	width: 38rpx;
	height: 38rpx;
	flex-shrink: 0;
	margin-top: 4rpx;
}
.note-copy { min-width: 0; margin-left: 16rpx; display: flex; flex-direction: column; }
.note-title { display: block; font-size: 26rpx; color: #66430b; font-weight: 700; }
.note-text {
	display: block;
	margin-top: 7rpx;
	font-size: 24rpx;
	line-height: 1.55;
	color: #4d4232;
}
.note-text--soft { color: #675841; font-size: 24rpx; font-weight: 500; }

@media screen and (min-width: 720px) {
	.scroll { padding-left: 36px; padding-right: 36px; }
	.reward-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
	.reward-item { border-bottom: 0; border-right: 1rpx solid var(--checkin-line); }
	.reward-item:last-child { border-right: 0; }
}
</style>
