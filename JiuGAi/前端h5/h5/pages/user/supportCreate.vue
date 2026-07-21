<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="hero-card">
				<view class="hero-glow"></view>
				<text class="hero-title">{{ copy.heroTitle }}</text>
				<text class="hero-desc">{{ copy.heroDesc }}</text>
				<view class="hero-points">
					<view v-for="item in heroPoints" :key="item" class="hero-point">{{ item }}</view>
				</view>
			</view>

			<view v-if="contextTags.length" class="card">
				<text class="section-title">{{ copy.contextTitle }}</text>
				<view class="context-tags">
					<view v-for="item in contextTags" :key="item.label + item.value" class="context-tag">
						<text class="context-tag__label">{{ item.label }}</text>
						<text class="context-tag__value">{{ item.value }}</text>
					</view>
				</view>
			</view>

			<view class="card">
				<text class="section-title">{{ copy.typeTitle }}</text>
				<view class="chip-wrap">
					<view
						v-for="item in localizedTypeOptions"
						:key="item.value"
						class="type-chip"
						:class="{ active: form.ticketType === item.value }"
						@tap="selectType(item.value)"
					>
						{{ item.label }}
					</view>
				</view>
			</view>

			<view class="card">
				<text class="section-title">{{ copy.subjectTitle }}</text>
				<input v-model="form.subject" class="input" :placeholder="copy.subjectPlaceholder" />

				<text class="section-title section-gap">{{ copy.contentTitle }}</text>
				<textarea
					v-model="form.content"
					class="textarea"
					:maxlength="5000"
					:placeholder="copy.contentPlaceholder"
				/>
				<text class="helper-text">{{ copy.contentHelper }}</text>
			</view>

			<view class="card">
				<view class="upload-head">
					<view>
						<text class="section-title">{{ copy.attachTitle }}</text>
						<text class="helper-text">{{ attachCountText }}</text>
					</view>
					<view class="upload-btn" @tap="chooseImages">{{ copy.attachAction }}</view>
				</view>

				<view v-if="attachments.length" class="attach-grid">
					<view
						v-for="(item, idx) in attachments"
						:key="item.url + '_' + idx"
						class="attach-item"
						@tap="previewAttachment(item.url)"
					>
						<image class="attach-thumb" :src="resolveAttachmentUrl(item.url)" mode="aspectFill" />
						<view class="attach-overlay"></view>
						<view class="attach-meta">
							<text class="attach-name">{{ attachmentName(item.url) }}</text>
							<text class="attach-remove" @tap.stop="removeAttachment(idx)">{{ copy.remove }}</text>
						</view>
					</view>
				</view>
				<view v-else class="attach-empty">
					<text class="attach-empty__title">{{ copy.attachEmptyTitle }}</text>
					<text class="attach-empty__desc">{{ copy.attachHint }}</text>
				</view>
			</view>

			<view class="submit-card">
				<view class="submit-copy">
					<text class="submit-title">{{ copy.submitTitle }}</text>
					<text class="submit-desc">{{ copy.submitDesc }}</text>
				</view>
				<view class="submit-btn" :class="{ 'submit-btn--busy': submitting }" @tap="submit">
					{{ submitting ? copy.submitting : copy.submit }}
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
const { getLanguageCode, getTavernUiText, formatLocaleText } = require('@/common/tavernUiI18n.js');

const DEFAULT_TYPE_OPTIONS = ['PAYMENT', 'ACCOUNT', 'BUG', 'REPORT', 'OTHER'];

const COPY = {
	'zh-cn': {
		title: '联系客服',
		heroTitle: '遇到问题，直接留给客服',
		heroDesc: '支付异常、账号问题、Bug 反馈和角色举报都会在这里留档，后续进度也会同步回这张工单里。',
		heroPointHuman: '优先人工跟进',
		heroPointContext: '自动带上订单/角色上下文',
		heroPointAttachment: '最多上传 6 张截图',
		contextTitle: '当前关联信息',
		typeTitle: '问题类型',
		subjectTitle: '问题标题',
		subjectPlaceholder: '一句话概括你遇到的问题',
		contentTitle: '问题描述',
		contentPlaceholder: '尽量写清楚发生了什么、你希望什么结果、是否已经尝试过重试或切换设备。',
		contentHelper: '描述越完整，客服定位和回复就越快。',
		contentRequired: '请先填写问题描述',
		orderPrefix: '关联订单',
		characterPrefix: '关联角色',
		attachTitle: '截图附件',
		attachAction: '上传截图',
		attachHint: '建议上传关键报错截图、支付页面截图或角色页面截图，方便客服快速定位。',
		attachEmptyTitle: '还没有上传截图',
		attachCount: '已上传 {count}/6 张',
		remove: '移除',
		submitTitle: '提交后会生成一张客服工单',
		submitDesc: '你可以在“我的工单”里继续补充信息、查看回复和处理进度。',
		submit: '提交工单',
		submitting: '提交中...',
		limitReached: '最多上传 6 张截图',
		loadMetaError: '加载问题类型失败',
		uploadFailed: '上传失败',
		submitFailed: '提交失败',
		typeLabels: {
			PAYMENT: '支付问题',
			ACCOUNT: '账号问题',
			BUG: 'Bug 反馈',
			REPORT: '举报角色',
			OTHER: '其他问题'
		},
		reportSubjectPrefix: '举报角色 - '
	},
	'zh-hk': {
		title: '聯絡客服',
		heroTitle: '遇到問題，直接交給客服',
		heroDesc: '支付異常、帳號問題、Bug 回報和角色舉報都會在這裡留檔，後續進度也會同步回這張工單裡。',
		heroPointHuman: '優先人工跟進',
		heroPointContext: '自動帶上訂單/角色資訊',
		heroPointAttachment: '最多上傳 6 張截圖',
		contextTitle: '目前關聯資訊',
		typeTitle: '問題類型',
		subjectTitle: '問題標題',
		subjectPlaceholder: '用一句話概括你遇到的問題',
		contentTitle: '問題描述',
		contentPlaceholder: '盡量寫清楚發生了什麼、你期望什麼結果、是否已經嘗試過重試或切換裝置。',
		contentHelper: '描述越完整，客服定位和回覆就越快。',
		contentRequired: '請先填寫問題描述',
		orderPrefix: '關聯訂單',
		characterPrefix: '關聯角色',
		attachTitle: '截圖附件',
		attachAction: '上傳截圖',
		attachHint: '建議上傳關鍵報錯截圖、支付頁面截圖或角色頁面截圖，方便客服快速定位。',
		attachEmptyTitle: '尚未上傳截圖',
		attachCount: '已上傳 {count}/6 張',
		remove: '移除',
		submitTitle: '送出後會建立一張客服工單',
		submitDesc: '你可以在「我的工單」中繼續補充資訊、查看回覆與處理進度。',
		submit: '提交工單',
		submitting: '提交中...',
		limitReached: '最多上傳 6 張截圖',
		loadMetaError: '載入問題類型失敗',
		uploadFailed: '上傳失敗',
		submitFailed: '提交失敗',
		typeLabels: {
			PAYMENT: '支付問題',
			ACCOUNT: '帳號問題',
			BUG: 'Bug 回報',
			REPORT: '舉報角色',
			OTHER: '其他問題'
		},
		reportSubjectPrefix: '舉報角色 - '
	},
	en: {
		title: 'Contact Support',
		heroTitle: 'Send it to support right away',
		heroDesc: 'Payment issues, account problems, bug reports, and character reports all stay in one ticket thread so you can track progress later.',
		heroPointHuman: 'Human follow-up first',
		heroPointContext: 'Order and character context included',
		heroPointAttachment: 'Up to 6 screenshots',
		contextTitle: 'Attached context',
		typeTitle: 'Issue type',
		subjectTitle: 'Subject',
		subjectPlaceholder: 'Summarize the issue in one sentence',
		contentTitle: 'Details',
		contentPlaceholder: 'Describe what happened, what result you expected, and whether you already retried or switched devices.',
		contentHelper: 'The clearer the details, the faster support can locate the issue.',
		contentRequired: 'Please add the issue details first',
		orderPrefix: 'Order',
		characterPrefix: 'Character',
		attachTitle: 'Screenshots',
		attachAction: 'Upload',
		attachHint: 'Upload the most useful error, payment, or character screenshots so support can locate the problem quickly.',
		attachEmptyTitle: 'No screenshots uploaded yet',
		attachCount: '{count}/6 uploaded',
		remove: 'Remove',
		submitTitle: 'Submitting creates a support ticket',
		submitDesc: 'You can continue the conversation, add more details, and check progress from My Tickets.',
		submit: 'Submit Ticket',
		submitting: 'Submitting...',
		limitReached: 'Up to 6 screenshots only',
		loadMetaError: 'Failed to load issue types',
		uploadFailed: 'Upload failed',
		submitFailed: 'Submit failed',
		typeLabels: {
			PAYMENT: 'Payment',
			ACCOUNT: 'Account',
			BUG: 'Bug Report',
			REPORT: 'Report Character',
			OTHER: 'Other'
		},
		reportSubjectPrefix: 'Report character - '
	},
	ko: {
		title: '고객센터',
		heroTitle: '문제가 생기면 바로 남겨 주세요',
		heroDesc: '결제 문제, 계정 문제, 버그 제보, 캐릭터 신고를 한 곳에서 접수하고 이후 진행 상황도 같은 티켓에서 확인할 수 있습니다.',
		heroPointHuman: '우선 수동 대응',
		heroPointContext: '주문/캐릭터 정보 자동 포함',
		heroPointAttachment: '최대 6장 업로드',
		contextTitle: '연결된 정보',
		typeTitle: '문의 유형',
		subjectTitle: '제목',
		subjectPlaceholder: '문제를 한 문장으로 요약해 주세요',
		contentTitle: '상세 설명',
		contentPlaceholder: '무슨 일이 있었는지, 어떤 결과를 기대하는지, 재시도나 기기 변경을 해봤는지 적어 주세요.',
		contentHelper: '상세할수록 고객센터가 더 빨리 확인할 수 있습니다.',
		contentRequired: '문의 내용을 먼저 입력해 주세요',
		orderPrefix: '주문',
		characterPrefix: '캐릭터',
		attachTitle: '스크린샷',
		attachAction: '업로드',
		attachHint: '핵심 오류 화면, 결제 화면, 캐릭터 화면을 올리면 고객센터가 더 빨리 확인할 수 있습니다.',
		attachEmptyTitle: '아직 업로드한 스크린샷이 없습니다',
		attachCount: '{count}/6 업로드됨',
		remove: '삭제',
		submitTitle: '제출하면 고객센터 티켓이 생성됩니다',
		submitDesc: '내 티켓에서 추가 설명을 남기고 답변과 진행 상황을 확인할 수 있습니다.',
		submit: '티켓 제출',
		submitting: '제출 중...',
		limitReached: '스크린샷은 최대 6장까지 가능합니다',
		loadMetaError: '문의 유형을 불러오지 못했습니다',
		uploadFailed: '업로드 실패',
		submitFailed: '제출 실패',
		typeLabels: {
			PAYMENT: '결제 문제',
			ACCOUNT: '계정 문제',
			BUG: '버그 제보',
			REPORT: '캐릭터 신고',
			OTHER: '기타 문의'
		},
		reportSubjectPrefix: '캐릭터 신고 - '
	},
	ja: {
		title: 'サポート',
		heroTitle: '困ったらそのままサポートへ',
		heroDesc: '決済トラブル、アカウント問題、Bug 報告、キャラクター通報をここで受け付け、進捗も同じチケットで追跡できます。',
		heroPointHuman: '優先して有人対応',
		heroPointContext: '注文/キャラクター情報を自動添付',
		heroPointAttachment: '画像は最大 6 枚',
		contextTitle: '関連情報',
		typeTitle: '問い合わせ種別',
		subjectTitle: '件名',
		subjectPlaceholder: '問題を一文でまとめてください',
		contentTitle: '詳細',
		contentPlaceholder: '何が起きたか、どんな結果を期待しているか、再試行や端末変更をしたかを書いてください。',
		contentHelper: '情報が具体的なほど、サポートの確認が早くなります。',
		contentRequired: 'まず詳細内容を入力してください',
		orderPrefix: '注文',
		characterPrefix: 'キャラクター',
		attachTitle: 'スクリーンショット',
		attachAction: 'アップロード',
		attachHint: 'エラー画面、決済画面、キャラクター画面の画像があると状況確認が早くなります。',
		attachEmptyTitle: 'まだ画像はアップロードされていません',
		attachCount: '{count}/6 アップロード済み',
		remove: '削除',
		submitTitle: '送信するとサポートチケットが作成されます',
		submitDesc: 'マイチケットで追加情報の送信、返信確認、進捗確認ができます。',
		submit: 'チケット送信',
		submitting: '送信中...',
		limitReached: '画像は最大 6 枚までです',
		loadMetaError: '問い合わせ種別の読み込みに失敗しました',
		uploadFailed: 'アップロードに失敗しました',
		submitFailed: '送信に失敗しました',
		typeLabels: {
			PAYMENT: '決済問題',
			ACCOUNT: 'アカウント問題',
			BUG: 'Bug 報告',
			REPORT: 'キャラクター通報',
			OTHER: 'その他'
		},
		reportSubjectPrefix: 'キャラクター通報 - '
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
			typeOptions: [],
			attachments: [],
			submitting: false,
			form: {
				ticketType: 'PAYMENT',
				subject: '',
				content: '',
				orderNo: '',
				characterId: '',
				characterName: ''
			}
		};
	},
	computed: {
		copy() {
			getTavernUiText('language');
			return currentCopy();
		},
		localizedTypeOptions() {
			const labels = this.copy.typeLabels || {};
			return (this.typeOptions || []).map((item) => ({
				...item,
				label: labels[item.value] || item.label || item.value
			}));
		},
		contextTags() {
			const tags = [];
			if (this.form.orderNo) {
				tags.push({ label: this.copy.orderPrefix, value: this.form.orderNo });
			}
			if (this.form.characterName) {
				tags.push({ label: this.copy.characterPrefix, value: this.form.characterName });
			}
			return tags;
		},
		heroPoints() {
			return [this.copy.heroPointHuman, this.copy.heroPointContext, this.copy.heroPointAttachment];
		},
		attachCountText() {
			return formatLocaleText(this.copy.attachCount, { count: this.attachments.length });
		}
	},
	onLoad(options) {
		this.form.ticketType = options && options.ticketType ? String(options.ticketType) : 'PAYMENT';
		this.form.subject = options && options.subject ? this.safeDecode(options.subject) : '';
		this.form.orderNo = options && options.orderNo ? String(options.orderNo) : '';
		this.form.characterId = options && options.characterId ? String(options.characterId) : '';
		this.form.characterName = options && options.characterName ? this.safeDecode(options.characterName) : '';
		if (this.form.ticketType === 'REPORT' && !this.form.subject) {
			this.form.subject = this.copy.reportSubjectPrefix + (this.form.characterName || '');
		}
	},
	onShow() {
		this.loadMeta();
	},
	methods: {
		safeDecode(value) {
			try {
				return decodeURIComponent(String(value || ''));
			} catch (e) {
				return String(value || '');
			}
		},
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		applyFallbackTypes() {
			this.typeOptions = DEFAULT_TYPE_OPTIONS.map((value) => ({ value, label: value }));
		},
		loadMeta() {
			tavernApi
				.fetchSupportMeta()
				.then((data) => {
					const nextOptions = Array.isArray(data && data.typeOptions) ? data.typeOptions : [];
					this.typeOptions = nextOptions.length ? nextOptions : DEFAULT_TYPE_OPTIONS.map((value) => ({ value, label: value }));
					if (!this.typeOptions.find((item) => item.value === this.form.ticketType) && this.typeOptions.length) {
						this.form.ticketType = this.typeOptions[0].value;
					}
				})
				.catch((e) => {
					this.applyFallbackTypes();
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.loadMetaError),
						icon: 'none'
					});
				});
		},
		selectType(value) {
			this.form.ticketType = value;
		},
		chooseImages() {
			if (this.attachments.length >= 6) {
				uni.showToast({ title: this.copy.limitReached, icon: 'none' });
				return;
			}
			uni.chooseImage({
				count: 6 - this.attachments.length,
				success: (res) => {
					const files = Array.isArray(res.tempFilePaths) ? res.tempFilePaths : [];
					this.uploadFiles(files);
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
						this.attachments.push({ url: data.url });
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
			this.attachments.splice(index, 1);
		},
		resolveAttachmentUrl(url) {
			return tavernApi.resolveJgAssetUrl(url);
		},
		attachmentName(url) {
			const safe = String(url || '');
			if (!safe) return '';
			return safe.split('/').pop() || safe;
		},
		previewAttachment(url) {
			const current = this.resolveAttachmentUrl(url);
			const urls = this.attachments.map((item) => this.resolveAttachmentUrl(item.url)).filter(Boolean);
			if (!current || !urls.length) return;
			uni.previewImage({ current, urls });
		},
		submit() {
			if (this.submitting) return;
			if (!String(this.form.content || '').trim()) {
				uni.showToast({ title: this.copy.contentRequired, icon: 'none' });
				return;
			}
			this.submitting = true;
			const payload = {
				clientUid: tavernApi.getClientUid(),
				ticketType: this.form.ticketType,
				subject: this.form.subject,
				content: this.form.content,
				orderNo: this.form.orderNo || undefined,
				characterId: this.form.characterId || undefined,
				characterName: this.form.characterName || undefined,
				attachments: this.attachments.map((item) => item.url)
			};
			const request =
				this.form.ticketType === 'REPORT'
					? tavernApi.postSupportCharacterReport(payload)
					: tavernApi.postSupportTicketCreate(payload);
			request
				.then((data) => {
					const ticket = data && data.ticket;
					if (ticket && ticket.ticketNo) {
						uni.redirectTo({
							url: '/pages/user/supportDetail?ticketNo=' + encodeURIComponent(ticket.ticketNo)
						});
						return;
					}
					uni.navigateTo({ url: '/pages/user/supportTickets' });
				})
				.catch((e) => {
					uni.showToast({
						title: tavernErrors.getTavernErrorMessage(e, this.copy.submitFailed),
						icon: 'none'
					});
				})
				.finally(() => {
					this.submitting = false;
				});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	min-height: 100vh;
	background:
		radial-gradient(circle at top right, rgba(236, 72, 153, 0.18), transparent 26%),
		radial-gradient(circle at top left, rgba(59, 130, 246, 0.14), transparent 24%),
		$tavern-page-bg;
}

.scroll {
	height: calc(100vh - 88rpx);
	padding: 24rpx;
	box-sizing: border-box;
}

.hero-card,
.card,
.submit-card {
	position: relative;
	overflow: hidden;
	padding: 30rpx;
	margin-bottom: 20rpx;
	border-radius: 28rpx;
	border: 1rpx solid $tavern-border-on-dark;
	background: $tavern-card-dark;
	box-shadow: $tavern-card-shadow;
}

.hero-card {
	background:
		linear-gradient(135deg, rgba(91, 33, 182, 0.34), rgba(15, 23, 42, 0.94) 58%),
		$tavern-card-dark;
}

.hero-glow {
	position: absolute;
	top: -80rpx;
	right: -60rpx;
	width: 240rpx;
	height: 240rpx;
	border-radius: 50%;
	background: radial-gradient(circle, rgba(244, 114, 182, 0.34), transparent 68%);
	pointer-events: none;
}

.hero-title,
.section-title,
.submit-title,
.context-tag__value,
.attach-empty__title,
.attach-name {
	display: block;
	color: $tavern-text-on-dark;
}

.hero-title {
	position: relative;
	font-size: 34rpx;
	font-weight: 700;
}

.hero-desc,
.helper-text,
.attach-empty__desc,
.context-tag__label {
	display: block;
	color: $tavern-muted-on-dark;
}

.hero-desc {
	position: relative;
	margin-top: 14rpx;
	font-size: 24rpx;
	line-height: 1.7;
}

.hero-points {
	position: relative;
	display: flex;
	flex-wrap: wrap;
	gap: 14rpx;
	margin-top: 20rpx;
}

.hero-point,
.type-chip,
.upload-btn,
.attach-remove,
.submit-btn {
	border-radius: 999rpx;
	font-weight: 700;
}

.hero-point {
	padding: 14rpx 22rpx;
	font-size: 22rpx;
	color: #fff;
	background: rgba(255, 255, 255, 0.1);
	border: 1rpx solid rgba(255, 255, 255, 0.14);
}

.section-title {
	font-size: 28rpx;
	font-weight: 700;
}

.section-gap {
	margin-top: 28rpx;
}

.context-tags {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
	margin-top: 18rpx;
}

.context-tag {
	padding: 20rpx 22rpx;
	border-radius: 20rpx;
	background: rgba(148, 163, 184, 0.08);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
}

.context-tag__label {
	font-size: 21rpx;
}

.context-tag__value {
	margin-top: 8rpx;
	font-size: 24rpx;
	font-weight: 700;
	word-break: break-all;
}

.chip-wrap {
	display: flex;
	flex-wrap: wrap;
	gap: 14rpx;
	margin-top: 18rpx;
}

.type-chip {
	padding: 18rpx 24rpx;
	font-size: 24rpx;
	color: #cbd5e1;
	background: rgba(148, 163, 184, 0.12);
	border: 1rpx solid rgba(148, 163, 184, 0.18);
}

.type-chip.active {
	color: #fff;
	background: rgba(168, 85, 247, 0.28);
	border-color: rgba(216, 180, 254, 0.32);
}

.input,
.textarea {
	width: 100%;
	margin-top: 16rpx;
	border-radius: 22rpx;
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	background: rgba(15, 23, 42, 0.42);
	color: $tavern-text-on-dark;
	box-sizing: border-box;
}

.input {
	height: 96rpx;
	padding: 0 24rpx;
	font-size: 26rpx;
}

.textarea {
	min-height: 260rpx;
	padding: 24rpx;
	font-size: 26rpx;
	line-height: 1.7;
}

.helper-text {
	margin-top: 14rpx;
	font-size: 22rpx;
	line-height: 1.6;
}

.upload-head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 16rpx;
}

.upload-btn,
.submit-btn {
	display: inline-flex;
	align-items: center;
	justify-content: center;
}

.upload-btn {
	min-width: 168rpx;
	height: 72rpx;
	padding: 0 24rpx;
	font-size: 24rpx;
	color: #fff;
	background: $tavern-accent-gradient;
	box-shadow: 0 14rpx 28rpx rgba(168, 85, 247, 0.22);
}

.attach-grid {
	margin-top: 20rpx;
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 16rpx;
}

.attach-item {
	position: relative;
	overflow: hidden;
	min-height: 188rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.06);
	border: 1rpx solid rgba(255, 255, 255, 0.08);
	box-shadow: 0 16rpx 32rpx rgba(15, 23, 42, 0.18);
}

.attach-thumb {
	width: 100%;
	height: 188rpx;
}

.attach-overlay {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: 108rpx;
	background: linear-gradient(180deg, rgba(15, 23, 42, 0), rgba(15, 23, 42, 0.88));
}

.attach-meta {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 12rpx;
	padding: 18rpx;
}

.attach-name {
	flex: 1;
	font-size: 22rpx;
	line-height: 1.5;
	color: #fff;
	word-break: break-all;
}

.attach-remove {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	padding: 10rpx 18rpx;
	font-size: 22rpx;
	color: #fff;
	background: rgba(244, 114, 182, 0.22);
	border: 1rpx solid rgba(251, 207, 232, 0.3);
}

.attach-empty {
	margin-top: 20rpx;
	padding: 30rpx 24rpx;
	border-radius: 24rpx;
	border: 1rpx dashed rgba(148, 163, 184, 0.24);
	background: rgba(148, 163, 184, 0.06);
}

.attach-empty__title {
	font-size: 26rpx;
	font-weight: 700;
}

.attach-empty__desc {
	margin-top: 10rpx;
	font-size: 23rpx;
	line-height: 1.7;
}

.submit-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	background:
		linear-gradient(135deg, rgba(17, 24, 39, 0.94), rgba(91, 33, 182, 0.24)),
		$tavern-card-dark;
}

.submit-copy {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}

.submit-title {
	font-size: 30rpx;
	font-weight: 700;
}

.submit-desc {
	display: block;
	color: $tavern-muted-on-dark;
	font-size: 23rpx;
	line-height: 1.7;
}

.submit-btn {
	min-width: 220rpx;
	height: 92rpx;
	padding: 0 28rpx;
	font-size: 28rpx;
	color: #fff;
	background: $tavern-accent-gradient;
	box-shadow: 0 16rpx 28rpx rgba(236, 72, 153, 0.18);
}

.submit-btn--busy {
	opacity: 0.8;
}

@media screen and (max-width: 420px) {
	.submit-card,
	.upload-head {
		flex-direction: column;
		align-items: stretch;
	}

	.upload-btn,
	.submit-btn {
		width: 100%;
	}
}

/* Light clover tavern support-create refresh. */
.hero-card,
.card,
.submit-card {
	background: rgba(255, 255, 255, 0.56);
	border-color: rgba(255, 255, 255, 0.5);
	box-shadow: 0 22rpx 52rpx rgba(67, 112, 142, 0.11);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
}

.hero-card,
.submit-card {
	background: linear-gradient(135deg, rgba(220, 247, 251, 0.74), rgba(255, 235, 243, 0.56));
}

.hero-title,
.section-title,
.submit-title,
.context-tag__value,
.attach-empty__title,
.attach-name {
	color: #244b66;
}

.hero-desc,
.helper-text,
.attach-empty__desc,
.context-tag__label,
.submit-desc {
	color: #687f92;
}

.hero-point,
.type-chip,
.context-tag,
.attach-empty,
.attach-item,
.input,
.textarea {
	background: rgba(255, 255, 255, 0.4);
	border-color: rgba(255, 255, 255, 0.48);
	color: #16384d;
}

.type-chip,
.attach-remove {
	color: #247494;
}

.type-chip.active,
.upload-btn,
.submit-btn {
	background: linear-gradient(135deg, #348fb8 0%, #76d2dd 62%, #f4a6c4 100%);
	color: #fff;
}

.attach-overlay {
	background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(36, 75, 102, 0.54) 100%);
}

.attach-name {
	color: #fff;
}
</style>
