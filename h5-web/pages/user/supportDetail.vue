<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view v-if="ticket.ticketNo" class="card ticket-card">
				<view class="head-row">
					<text class="ticket-no">{{ ticket.ticketNo }}</text>
					<text class="ticket-status" :class="statusClass(ticket.status)">{{ formatStatus(ticket.status) }}</text>
				</view>
				<text class="ticket-subject">{{ ticket.subject }}</text>
				<text class="ticket-meta">{{ formatType(ticket.ticketType) }} · {{ formatTime(ticket.lastMessageAt || ticket.createdAt) }}</text>
				<text v-if="ticket.orderNo" class="ticket-extra">{{ copy.orderPrefix }} {{ ticket.orderNo }}</text>
				<text v-if="ticket.characterName" class="ticket-extra">{{ copy.characterPrefix }} {{ ticket.characterName }}</text>
			</view>

			<view class="card">
				<text class="section-title">{{ copy.threadTitle }}</text>
				<view v-if="loading" class="thread-empty">{{ copy.loading }}</view>
				<view v-else-if="!messages.length" class="thread-empty">{{ copy.empty }}</view>
				<view v-else class="message-list">
					<view
						v-for="item in messages"
						:key="item.id"
						class="message-item"
						:class="{ 'message-item--admin': item.senderType === 'ADMIN' }"
					>
						<text class="message-name">{{ displaySender(item) }}</text>
						<text class="message-content">{{ item.content }}</text>
						<view v-if="item.attachments && item.attachments.length" class="attach-list">
							<text
								v-for="(url, idx) in item.attachments"
								:key="url + idx"
								class="attach-link"
								@tap="openAttachment(url)"
							>
								{{ attachmentName(url) }}
							</text>
						</view>
						<text class="message-time">{{ formatTime(item.createdAt) }}</text>
					</view>
				</view>
			</view>

			<view v-if="ticket.canReply" class="card">
				<text class="section-title">{{ copy.replyTitle }}</text>
				<textarea
					v-model="replyContent"
					class="textarea"
					:maxlength="5000"
					:placeholder="copy.replyPlaceholder"
				/>
				<view class="reply-row">
					<view class="reply-action" @tap="chooseImages">{{ copy.attach }}</view>
					<view class="reply-action reply-action--primary" @tap="submitReply">
						{{ replying ? copy.replying : copy.reply }}
					</view>
				</view>
				<view v-if="replyAttachments.length" class="reply-attach-list">
					<view v-for="(item, idx) in replyAttachments" :key="item.url + idx" class="reply-attach-item">
						<text class="reply-attach-name">{{ attachmentName(item.url) }}</text>
						<text class="reply-attach-remove" @tap="removeAttachment(idx)">{{ copy.remove }}</text>
					</view>
				</view>
			</view>
			<u-gap height="48"></u-gap>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

const tavernApi = require('@/common/tavernApi.js');
const tavernErrors = require('@/common/tavernErrors.js');
const { getLanguageCode, getTavernUiText } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '工单详情',
		orderPrefix: '关联订单：',
		characterPrefix: '关联角色：',
		threadTitle: '沟通记录',
		loading: '正在加载...',
		empty: '暂时还没有消息。',
		replyTitle: '继续回复',
		replyPlaceholder: '补充更多细节，或告诉客服你已经尝试过哪些操作。',
		replyEmpty: '请先填写回复内容',
		attach: '上传截图',
		reply: '发送回复',
		replying: '发送中...',
		remove: '移除',
		limitReached: '最多上传 6 张截图',
		loadFailed: '加载失败',
		uploadFailed: '上传失败',
		sendFailed: '发送失败',
		attachmentCopied: '附件链接已复制',
		typeLabels: {
			PAYMENT: '支付问题',
			ACCOUNT: '账号问题',
			BUG: 'Bug 反馈',
			REPORT: '举报角色',
			OTHER: '其他问题'
		},
		statusLabels: {
			OPEN: '待处理',
			WAIT_USER: '待你补充',
			RESOLVED: '已解决',
			CLOSED: '已关闭'
		},
		senderLabels: {
			USER: '你',
			ADMIN: '客服'
		}
	},
	'zh-hk': {
		title: '工單詳情',
		orderPrefix: '關聯訂單：',
		characterPrefix: '關聯角色：',
		threadTitle: '溝通記錄',
		loading: '正在載入...',
		empty: '暫時還沒有訊息。',
		replyTitle: '繼續回覆',
		replyPlaceholder: '補充更多細節，或告訴客服你已經嘗試過哪些操作。',
		replyEmpty: '請先填寫回覆內容',
		attach: '上傳截圖',
		reply: '送出回覆',
		replying: '送出中...',
		remove: '移除',
		limitReached: '最多上傳 6 張截圖',
		loadFailed: '載入失敗',
		uploadFailed: '上傳失敗',
		sendFailed: '送出失敗',
		attachmentCopied: '附件連結已複製',
		typeLabels: {
			PAYMENT: '支付問題',
			ACCOUNT: '帳號問題',
			BUG: 'Bug 回報',
			REPORT: '舉報角色',
			OTHER: '其他問題'
		},
		statusLabels: {
			OPEN: '待處理',
			WAIT_USER: '待你補充',
			RESOLVED: '已解決',
			CLOSED: '已關閉'
		},
		senderLabels: {
			USER: '你',
			ADMIN: '客服'
		}
	},
	en: {
		title: 'Ticket Detail',
		orderPrefix: 'Order: ',
		characterPrefix: 'Character: ',
		threadTitle: 'Conversation',
		loading: 'Loading...',
		empty: 'No messages yet.',
		replyTitle: 'Reply',
		replyPlaceholder: 'Add more details or tell support what you already tried.',
		replyEmpty: 'Please enter a reply first',
		attach: 'Upload',
		reply: 'Send',
		replying: 'Sending...',
		remove: 'Remove',
		limitReached: 'Up to 6 screenshots only',
		loadFailed: 'Load failed',
		uploadFailed: 'Upload failed',
		sendFailed: 'Send failed',
		attachmentCopied: 'Attachment link copied',
		typeLabels: {
			PAYMENT: 'Payment',
			ACCOUNT: 'Account',
			BUG: 'Bug Report',
			REPORT: 'Report',
			OTHER: 'Other'
		},
		statusLabels: {
			OPEN: 'Open',
			WAIT_USER: 'Need You',
			RESOLVED: 'Resolved',
			CLOSED: 'Closed'
		},
		senderLabels: {
			USER: 'You',
			ADMIN: 'Support'
		}
	},
	ko: {
		title: '티켓 상세',
		orderPrefix: '주문: ',
		characterPrefix: '캐릭터: ',
		threadTitle: '대화 기록',
		loading: '불러오는 중...',
		empty: '아직 메시지가 없습니다.',
		replyTitle: '추가 답변',
		replyPlaceholder: '추가 정보를 남기거나 이미 시도한 내용을 적어 주세요.',
		replyEmpty: '답변 내용을 먼저 입력해 주세요',
		attach: '업로드',
		reply: '답변 보내기',
		replying: '전송 중...',
		remove: '삭제',
		limitReached: '스크린샷은 최대 6장까지 가능합니다',
		loadFailed: '불러오기에 실패했습니다',
		uploadFailed: '업로드 실패',
		sendFailed: '전송 실패',
		attachmentCopied: '첨부 링크가 복사되었습니다',
		typeLabels: {
			PAYMENT: '결제 문제',
			ACCOUNT: '계정 문제',
			BUG: '버그 제보',
			REPORT: '캐릭터 신고',
			OTHER: '기타 문의'
		},
		statusLabels: {
			OPEN: '처리 중',
			WAIT_USER: '추가 입력 필요',
			RESOLVED: '해결됨',
			CLOSED: '종료됨'
		},
		senderLabels: {
			USER: '나',
			ADMIN: '고객센터'
		}
	},
	ja: {
		title: 'チケット詳細',
		orderPrefix: '注文: ',
		characterPrefix: 'キャラクター: ',
		threadTitle: 'やり取り',
		loading: '読み込み中...',
		empty: 'まだメッセージはありません。',
		replyTitle: '追加返信',
		replyPlaceholder: '追加情報や、すでに試した内容を書いてください。',
		replyEmpty: 'まず返信内容を入力してください',
		attach: 'アップロード',
		reply: '返信を送信',
		replying: '送信中...',
		remove: '削除',
		limitReached: '画像は最大 6 枚までです',
		loadFailed: '読み込みに失敗しました',
		uploadFailed: 'アップロードに失敗しました',
		sendFailed: '送信に失敗しました',
		attachmentCopied: '添付リンクをコピーしました',
		typeLabels: {
			PAYMENT: '決済問題',
			ACCOUNT: 'アカウント問題',
			BUG: 'Bug 報告',
			REPORT: 'キャラクター通報',
			OTHER: 'その他'
		},
		statusLabels: {
			OPEN: '対応中',
			WAIT_USER: '追加入力待ち',
			RESOLVED: '解決済み',
			CLOSED: '終了'
		},
		senderLabels: {
			USER: 'あなた',
			ADMIN: 'サポート'
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
			replying: false,
			ticketNo: '',
			ticket: {},
			messages: [],
			replyContent: '',
			replyAttachments: []
		};
	},
	computed: {
		copy() {
			getTavernUiText('language');
			return currentCopy();
		}
	},
	onLoad(options) {
		this.ticketNo = options && options.ticketNo ? String(options.ticketNo) : '';
	},
	onShow() {
		this.loadDetail();
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/user/supportTickets' }) });
		},
		loadDetail() {
			if (!this.ticketNo) return;
			this.loading = true;
			tavernApi
				.fetchSupportTicketDetail(tavernApi.getClientUid(), this.ticketNo)
				.then((data) => {
					this.ticket = (data && data.ticket) || {};
					this.messages = Array.isArray(data && data.messages) ? data.messages : [];
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.loadFailed),
						icon: 'none'
					});
				})
				.finally(() => {
					this.loading = false;
				});
		},
		chooseImages() {
			if (this.replyAttachments.length >= 6) {
				uni.showToast({ title: this.copy.limitReached, icon: 'none' });
				return;
			}
			uni.chooseImage({
				count: 6 - this.replyAttachments.length,
				success: (res) => {
					this.uploadFiles(Array.isArray(res.tempFilePaths) ? res.tempFilePaths : []);
				}
			});
		},
		uploadFiles(files) {
			if (!files || !files.length) return;
			const next = files[0];
			tavernApi
				.uploadSupportImage(next, tavernApi.getClientUid())
				.then((data) => {
					if (data && data.url) {
						this.replyAttachments.push({ url: data.url });
					}
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.uploadFailed),
						icon: 'none'
					});
				})
				.finally(() => {
					this.uploadFiles(files.slice(1));
				});
		},
		removeAttachment(index) {
			this.replyAttachments.splice(index, 1);
		},
		submitReply() {
			if (this.replying || !this.ticketNo) return;
			if (!String(this.replyContent || '').trim()) {
				uni.showToast({ title: this.copy.replyEmpty, icon: 'none' });
				return;
			}
			this.replying = true;
			tavernApi
				.postSupportTicketReply({
					clientUid: tavernApi.getClientUid(),
					ticketNo: this.ticketNo,
					content: this.replyContent,
					attachments: this.replyAttachments.map((item) => item.url)
				})
				.then((data) => {
					this.ticket = (data && data.ticket) || this.ticket;
					this.messages = Array.isArray(data && data.messages) ? data.messages : this.messages;
					this.replyContent = '';
					this.replyAttachments = [];
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.sendFailed),
						icon: 'none'
					});
				})
				.finally(() => {
					this.replying = false;
				});
		},
		resolveAttachmentUrl(url) {
			return tavernApi.resolveJgAssetUrl(url);
		},
		attachmentName(url) {
			const safe = String(url || '');
			if (!safe) return '';
			return safe.split('/').pop() || safe;
		},
		openAttachment(url) {
			const finalUrl = this.resolveAttachmentUrl(url);
			if (!finalUrl) return;
			uni.previewImage({
				current: finalUrl,
				urls: [finalUrl],
				fail: () => {
					uni.setClipboardData({
						data: finalUrl,
						success: () => {
							uni.showToast({ title: this.copy.attachmentCopied, icon: 'none' });
						}
					});
				}
			});
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
		displaySender(item) {
			if (item && item.senderName) return item.senderName;
			return (this.copy.senderLabels || {})[item && item.senderType] || '';
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

.scroll {
	height: calc(100vh - 88rpx);
	padding: 24rpx;
	box-sizing: border-box;
}

.card {
	padding: 28rpx;
	margin-bottom: 20rpx;
	border-radius: 24rpx;
	background: $tavern-card-dark;
	border: 1rpx solid $tavern-border-on-dark;
	box-shadow: $tavern-card-shadow;
}

.ticket-card {
	background:
		linear-gradient(135deg, rgba(91, 33, 182, 0.2), rgba(15, 23, 42, 0.92)),
		$tavern-card-dark;
}

.head-row,
.reply-row,
.reply-attach-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.ticket-no,
.ticket-subject,
.section-title,
.message-name,
.message-content {
	display: block;
	color: $tavern-text-on-dark;
}

.ticket-status,
.ticket-meta,
.ticket-extra,
.thread-empty,
.message-time,
.attach-link,
.reply-attach-name {
	display: block;
	color: $tavern-muted-on-dark;
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
	font-weight: 700;
}

.ticket-meta,
.ticket-extra {
	margin-top: 10rpx;
	font-size: 23rpx;
	line-height: 1.6;
}

.section-title {
	font-size: 28rpx;
	font-weight: 700;
}

.message-list {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	margin-top: 18rpx;
}

.message-item {
	padding: 22rpx;
	border-radius: 20rpx;
	background: rgba(148, 163, 184, 0.08);
}

.message-item--admin {
	background: rgba(168, 85, 247, 0.12);
}

.message-name {
	font-size: 24rpx;
	font-weight: 700;
}

.message-content {
	margin-top: 10rpx;
	font-size: 24rpx;
	line-height: 1.7;
	word-break: break-word;
}

.attach-list,
.reply-attach-list {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 12rpx;
}

.attach-link {
	padding: 10rpx 16rpx;
	border-radius: 14rpx;
	font-size: 22rpx;
	background: rgba(255, 255, 255, 0.06);
}

.message-time {
	margin-top: 12rpx;
	font-size: 21rpx;
}

.thread-empty {
	margin-top: 18rpx;
	font-size: 24rpx;
	line-height: 1.7;
}

.textarea {
	width: 100%;
	min-height: 240rpx;
	margin-top: 18rpx;
	padding: 22rpx;
	box-sizing: border-box;
	border-radius: 22rpx;
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	background: rgba(15, 23, 42, 0.42);
	color: $tavern-text-on-dark;
	font-size: 26rpx;
	line-height: 1.7;
}

.reply-row {
	margin-top: 18rpx;
}

.reply-action,
.reply-attach-remove {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	border-radius: 999rpx;
	font-weight: 700;
}

.reply-action {
	min-width: 168rpx;
	height: 72rpx;
	padding: 0 24rpx;
	font-size: 24rpx;
	color: #d8b4fe;
	background: rgba(91, 33, 182, 0.16);
	border: 1rpx solid rgba(196, 181, 253, 0.26);
}

.reply-action--primary {
	color: #fff;
	background: $tavern-accent-gradient;
	border-color: transparent;
}

.reply-attach-item {
	padding: 12rpx 16rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.06);
}

.reply-attach-name {
	flex: 1;
	font-size: 22rpx;
	word-break: break-all;
}

.reply-attach-remove {
	padding: 8rpx 14rpx;
	font-size: 22rpx;
	color: #fff;
	background: rgba(244, 114, 182, 0.22);
}

@media screen and (max-width: 420px) {
	.reply-row {
		flex-direction: column;
		align-items: stretch;
	}

	.reply-action {
		width: 100%;
	}
}

/* Light clover tavern support-detail refresh. */
.card,
.ticket-card {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.ticket-card {
	background: linear-gradient(135deg, rgba(220, 247, 251, 0.74), rgba(255, 235, 243, 0.56));
}

.ticket-no,
.ticket-subject,
.section-title,
.message-name,
.message-content {
	color: #244b66;
}

.ticket-status,
.ticket-meta,
.ticket-extra,
.thread-empty,
.message-time,
.attach-link,
.reply-attach-name {
	color: #687f92;
}

.message-item,
.reply-attach-item,
.attach-link,
.textarea {
	background: rgba(255, 255, 255, 0.4);
	border-color: rgba(255, 255, 255, 0.48);
	color: #16384d;
}

.message-item--admin {
	background: rgba(220, 247, 251, 0.54);
}

.reply-action {
	background: rgba(255, 255, 255, 0.42);
	border-color: rgba(255, 255, 255, 0.5);
	color: #247494;
}

.reply-action--primary {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
	color: #fff;
}

.reply-attach-remove {
	background: rgba(244, 166, 196, 0.22);
	color: #9f4464;
}
</style>
