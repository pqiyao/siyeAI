<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />
		<view class="body">
			<view class="toolbar-card">
				<view class="toolbar-copy">
					<text class="toolbar-title">{{ copy.toolbarTitle }}</text>
					<text class="toolbar-desc">{{ copy.toolbarDesc }}</text>
				</view>
				<view class="primary-btn" @tap="openCreate">{{ copy.create }}</view>
			</view>

			<scroll-view scroll-x class="status-scroll" :show-scrollbar="false">
				<view class="status-row">
					<view
						v-for="item in tabs"
						:key="item.value"
						class="status-chip"
						:class="{ active: status === item.value }"
						@tap="switchStatus(item.value)"
					>
						{{ item.label }}
					</view>
				</view>
			</scroll-view>

			<view v-if="loading" class="empty-card">{{ copy.loading }}</view>

			<view v-else-if="!tickets.length" class="empty-card">
				<text class="empty-title">{{ copy.emptyTitle }}</text>
				<text class="empty-desc">{{ copy.emptyDesc }}</text>
				<view class="ghost-btn" @tap="openCreate">{{ copy.create }}</view>
			</view>

			<view v-else class="ticket-list">
				<view
					v-for="item in tickets"
					:key="item.ticketNo"
					class="ticket-card"
					@tap="openDetail(item.ticketNo)"
				>
					<view class="ticket-head">
						<text class="ticket-no">{{ item.ticketNo }}</text>
						<text class="ticket-status" :class="statusClass(item.status)">
							{{ formatStatus(item.status) }}
						</text>
					</view>
					<text class="ticket-subject">{{ item.subject }}</text>
					<text class="ticket-meta">{{ formatType(item.ticketType) }} · {{ formatTime(item.lastMessageAt || item.createdAt) }}</text>
					<text v-if="item.latestMessagePreview" class="ticket-preview">{{ item.latestMessagePreview }}</text>
					<text v-if="item.orderNo" class="ticket-extra">{{ copy.orderPrefix }} {{ item.orderNo }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const tavernErrors = require('@/common/tavernErrors.js');
const { getLanguageCode, getTavernUiText } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '我的工单',
		toolbarTitle: '客服记录会保留在这里',
		toolbarDesc: '支付问题、账号问题、Bug 反馈和角色举报都会沉淀成可追踪的工单记录。',
		create: '新建工单',
		loading: '正在加载工单...',
		emptyTitle: '还没有客服工单',
		emptyDesc: '一旦你联系过客服，后续回复、处理状态和补充说明都会集中展示在这里。',
		orderPrefix: '关联订单',
		loadFailed: '加载失败',
		typeLabels: {
			PAYMENT: '支付问题',
			ACCOUNT: '账号问题',
			BUG: 'Bug 反馈',
			REPORT: '举报角色',
			OTHER: '其他问题'
		},
		statusLabels: {
			'': '全部',
			OPEN: '待处理',
			WAIT_USER: '待你补充',
			RESOLVED: '已解决',
			CLOSED: '已关闭'
		}
	},
	'zh-hk': {
		title: '我的工單',
		toolbarTitle: '客服記錄會保留在這裡',
		toolbarDesc: '支付問題、帳號問題、Bug 回報和角色舉報都會沉澱成可追蹤的工單記錄。',
		create: '新建工單',
		loading: '正在載入工單...',
		emptyTitle: '還沒有客服工單',
		emptyDesc: '只要你聯絡過客服，後續回覆、處理狀態與補充說明都會集中顯示在這裡。',
		orderPrefix: '關聯訂單',
		loadFailed: '載入失敗',
		typeLabels: {
			PAYMENT: '支付問題',
			ACCOUNT: '帳號問題',
			BUG: 'Bug 回報',
			REPORT: '舉報角色',
			OTHER: '其他問題'
		},
		statusLabels: {
			'': '全部',
			OPEN: '待處理',
			WAIT_USER: '待你補充',
			RESOLVED: '已解決',
			CLOSED: '已關閉'
		}
	},
	en: {
		title: 'My Tickets',
		toolbarTitle: 'Your support history stays here',
		toolbarDesc: 'Payment issues, account problems, bug reports, and character reports all become trackable tickets.',
		create: 'New Ticket',
		loading: 'Loading tickets...',
		emptyTitle: 'No tickets yet',
		emptyDesc: 'Once you contact support, replies, progress, and follow-up details will all stay here.',
		orderPrefix: 'Order',
		loadFailed: 'Load failed',
		typeLabels: {
			PAYMENT: 'Payment',
			ACCOUNT: 'Account',
			BUG: 'Bug Report',
			REPORT: 'Report',
			OTHER: 'Other'
		},
		statusLabels: {
			'': 'All',
			OPEN: 'Open',
			WAIT_USER: 'Need You',
			RESOLVED: 'Resolved',
			CLOSED: 'Closed'
		}
	},
	ko: {
		title: '내 티켓',
		toolbarTitle: '고객센터 기록이 여기에 남습니다',
		toolbarDesc: '결제 문제, 계정 문제, 버그 제보, 캐릭터 신고가 모두 추적 가능한 티켓으로 정리됩니다.',
		create: '새 티켓',
		loading: '티켓을 불러오는 중...',
		emptyTitle: '아직 티켓이 없습니다',
		emptyDesc: '고객센터에 문의하면 이후 답변과 진행 상황이 모두 이곳에 쌓입니다.',
		orderPrefix: '주문',
		loadFailed: '불러오기에 실패했습니다',
		typeLabels: {
			PAYMENT: '결제 문제',
			ACCOUNT: '계정 문제',
			BUG: '버그 제보',
			REPORT: '캐릭터 신고',
			OTHER: '기타 문의'
		},
		statusLabels: {
			'': '전체',
			OPEN: '처리 중',
			WAIT_USER: '추가 입력 필요',
			RESOLVED: '해결됨',
			CLOSED: '종료됨'
		}
	},
	ja: {
		title: 'マイチケット',
		toolbarTitle: 'サポート履歴はここに残ります',
		toolbarDesc: '決済問題、アカウント問題、Bug 報告、キャラクター通報が追跡可能なチケットとして整理されます。',
		create: '新規チケット',
		loading: 'チケットを読み込み中...',
		emptyTitle: 'まだチケットはありません',
		emptyDesc: 'サポートへ連絡すると、その後の返信や進捗がここにまとまります。',
		orderPrefix: '注文',
		loadFailed: '読み込みに失敗しました',
		typeLabels: {
			PAYMENT: '決済問題',
			ACCOUNT: 'アカウント問題',
			BUG: 'Bug 報告',
			REPORT: 'キャラクター通報',
			OTHER: 'その他'
		},
		statusLabels: {
			'': 'すべて',
			OPEN: '対応中',
			WAIT_USER: '追加入力待ち',
			RESOLVED: '解決済み',
			CLOSED: '終了'
		}
	}
};

function currentCopy() {
	const code = getLanguageCode();
	return COPY[code] || COPY.en || COPY['zh-cn'];
}

export default {
	components: { TavernNavBar },
	data() {
		return {
			loading: false,
			status: '',
			tickets: []
		};
	},
	computed: {
		copy() {
			getTavernUiText('language');
			return currentCopy();
		},
		tabs() {
			const labels = this.copy.statusLabels || {};
			return ['', 'OPEN', 'WAIT_USER', 'RESOLVED', 'CLOSED'].map((value) => ({
				value,
				label: labels[value] || value
			}));
		}
	},
	onShow() {
		this.loadTickets();
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		switchStatus(nextStatus) {
			if (this.status === nextStatus) return;
			this.status = nextStatus;
			this.loadTickets();
		},
		loadTickets() {
			this.loading = true;
			tavernApi
				.fetchSupportTickets(tavernApi.getClientUid(), this.status, 30)
				.then((list) => {
					this.tickets = Array.isArray(list) ? list : [];
				})
				.catch((e) => {
					this.tickets = [];
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.loadFailed),
						icon: 'none'
					});
				})
				.finally(() => {
					this.loading = false;
				});
		},
		openCreate() {
			uni.navigateTo({ url: '/pages/user/supportCreate' });
		},
		openDetail(ticketNo) {
			uni.navigateTo({ url: '/pages/user/supportDetail?ticketNo=' + encodeURIComponent(ticketNo) });
		},
		formatType(value) {
			return (this.copy.typeLabels || {})[value] || value || '';
		},
		formatStatus(value) {
			return (this.copy.statusLabels || {})[value] || value || '';
		},
		statusClass(value) {
			return 'ticket-status--' + String(value || '').toLowerCase();
		},
		formatTime(value) {
			if (!value) return '';
			return String(value).replace('T', ' ').slice(0, 16);
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background: $tavern-page-bg;
}

.body {
	padding: 24rpx;
}

.toolbar-card,
.empty-card,
.ticket-card {
	margin-top: 20rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.toolbar-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	background:
		linear-gradient(135deg, rgba(91, 33, 182, 0.22), rgba(15, 23, 42, 0.94)),
		$tavern-card-dark;
}

.toolbar-copy {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}

.toolbar-title,
.empty-title,
.ticket-no,
.ticket-subject {
	display: block;
	color: $tavern-text-on-dark;
}

.toolbar-title,
.empty-title,
.ticket-subject {
	font-weight: 700;
}

.toolbar-title,
.empty-title {
	font-size: 30rpx;
}

.toolbar-desc,
.ticket-status,
.ticket-meta,
.ticket-preview,
.ticket-extra,
.empty-desc {
	display: block;
	color: $tavern-muted-on-dark;
}

.toolbar-desc,
.ticket-meta,
.ticket-preview,
.ticket-extra,
.empty-desc {
	font-size: 23rpx;
	line-height: 1.6;
}

.status-scroll {
	margin-top: 18rpx;
	white-space: nowrap;
}

.status-row {
	display: inline-flex;
	gap: 14rpx;
}

.status-chip,
.primary-btn,
.ghost-btn {
	border-radius: 999rpx;
	font-size: 24rpx;
	font-weight: 700;
}

.status-chip {
	padding: 18rpx 28rpx;
	color: #cbd5e1;
	background: rgba(148, 163, 184, 0.12);
	border: 1rpx solid rgba(148, 163, 184, 0.18);
}

.status-chip.active {
	color: #fff;
	background: rgba(168, 85, 247, 0.28);
	border-color: rgba(216, 180, 254, 0.34);
}

.primary-btn,
.ghost-btn {
	height: 84rpx;
	line-height: 84rpx;
	text-align: center;
}

.primary-btn {
	min-width: 220rpx;
	color: #fff;
	background: $tavern-accent-gradient;
}

.ghost-btn {
	margin-top: 24rpx;
	color: #d8b4fe;
	background: rgba(91, 33, 182, 0.16);
	border: 1rpx solid rgba(196, 181, 253, 0.26);
}

.ticket-list {
	display: flex;
	flex-direction: column;
}

.ticket-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.ticket-no {
	font-size: 24rpx;
	font-weight: 700;
}

.ticket-status {
	padding: 8rpx 18rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	background: rgba(148, 163, 184, 0.1);
}

.ticket-status--open {
	color: #f9fafb;
	background: rgba(59, 130, 246, 0.2);
}

.ticket-status--wait_user {
	color: #fef3c7;
	background: rgba(245, 158, 11, 0.18);
}

.ticket-status--resolved {
	color: #d1fae5;
	background: rgba(16, 185, 129, 0.18);
}

.ticket-status--closed {
	color: #e5e7eb;
	background: rgba(107, 114, 128, 0.22);
}

.ticket-subject {
	margin-top: 12rpx;
	font-size: 30rpx;
}

.ticket-meta,
.ticket-preview,
.ticket-extra {
	margin-top: 10rpx;
}

.ticket-preview {
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

@media screen and (max-width: 420px) {
	.toolbar-card {
		flex-direction: column;
		align-items: stretch;
	}

	.primary-btn {
		width: 100%;
	}
}

/* Light clover tavern support-list refresh. */
.toolbar-card,
.empty-card,
.ticket-card {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.52);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.toolbar-card {
	background: linear-gradient(135deg, rgba(220, 247, 251, 0.72), rgba(255, 235, 243, 0.54));
}

.toolbar-title,
.empty-title,
.ticket-no,
.ticket-subject {
	color: #244b66;
}

.toolbar-desc,
.ticket-status,
.ticket-meta,
.ticket-preview,
.ticket-extra,
.empty-desc {
	color: #687f92;
}

.status-chip,
.ghost-btn {
	background: rgba(255, 255, 255, 0.38);
	border-color: rgba(255, 255, 255, 0.46);
	color: #247494;
}

.status-chip.active {
	background: rgba(220, 247, 251, 0.82);
	border-color: rgba(88, 189, 210, 0.26);
	color: #247494;
}

.primary-btn {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
}
</style>
