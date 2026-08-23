<template>
	<view class="page">
		<image class="page-bg" src="/static/login.png" mode="aspectFill" />
		<tavern-nav-bar title="AI 创作工作台" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="hero panel">
				<view>
					<text class="eyebrow">AGENT WORKSPACE</text>
					<text class="hero-title">把一句想法，变成可交付作品</text>
					<text class="hero-subtitle">角色、世界书、剧情和互动网页统一留在任务里。</text>
				</view>
				<view class="hero-mark">✦</view>
			</view>

			<view class="panel create-panel">
				<view class="section-head">
					<text class="section-title">新建创作任务</text>
					<text class="section-note">需要批准计划后开始执行</text>
				</view>
				<input v-model.trim="draft.title" class="input" maxlength="80" placeholder="作品标题（可选）" />
				<textarea v-model.trim="draft.objective" class="textarea" maxlength="6000" placeholder="描述你想创作的角色、世界观和故事" />
				<button class="primary-btn" :disabled="creating || !draft.objective" @tap="createTask">
					{{ creating ? '创建中…' : '创建任务' }}
				</button>
			</view>

			<view class="section-head list-head">
				<text class="section-title">我的任务</text>
				<text class="section-note">{{ total }} 个任务</text>
			</view>
			<view v-if="loading && !tasks.length" class="state panel">加载中…</view>
			<view v-else-if="!tasks.length" class="state panel">还没有创作任务</view>
			<view v-else class="task-list">
				<view v-for="item in tasks" :key="item.id" class="task-card panel" :class="{ active: selectedId === item.id }" @tap="selectTask(item.id)">
					<view class="task-card-top">
						<text class="task-title">{{ item.title || '未命名作品' }}</text>
						<text class="status" :class="'status-' + String(item.status || '').toLowerCase()">{{ statusLabel(item.status) }}</text>
					</view>
					<text class="task-objective">{{ item.objective || '-' }}</text>
					<view class="task-meta">
						<text>{{ item.completedSteps || 0 }} / {{ item.totalSteps || 0 }} 步骤</text>
						<text>{{ formatTime(item.updatedAt) }}</text>
					</view>
					<view class="progress"><view class="progress-fill" :style="{ width: progress(item) + '%' }" /></view>
				</view>
			</view>

			<view v-if="detail" class="detail panel">
				<view class="section-head">
					<view>
						<text class="section-title">任务详情</text>
						<text class="detail-key">{{ detail.task && detail.task.taskKey }}</text>
					</view>
					<text class="status" :class="'status-' + String(detail.task.status || '').toLowerCase()">{{ statusLabel(detail.task.status) }}</text>
				</view>
				<view class="action-row">
					<button v-if="detail.task.status === 'DRAFT'" class="action-btn action-primary" :disabled="busy" @tap="generatePlan">生成计划</button>
					<button v-if="detail.task.status === 'WAITING_APPROVAL'" class="action-btn action-primary" :disabled="busy" @tap="approvePlan">批准计划</button>
					<button v-if="['READY','RUNNING'].includes(detail.task.status)" class="action-btn" :disabled="busy" @tap="control('pause')">暂停</button>
					<button v-if="detail.task.status === 'PAUSED'" class="action-btn action-primary" :disabled="busy" @tap="control('resume')">继续</button>
					<button v-if="['DRAFT','PLANNING','WAITING_APPROVAL','READY','RUNNING','PAUSED'].includes(detail.task.status)" class="action-btn action-danger" :disabled="busy" @tap="control('cancel')">取消</button>
					<button v-if="['FAILED','PARTIAL_SUCCESS'].includes(detail.task.status)" class="action-btn" :disabled="busy" @tap="control('retry')">重试</button>
				</view>

				<view v-if="detail.plan" class="plan-box">
					<text class="subhead">执行计划 · v{{ detail.plan.planVersion }}</text>
					<text class="plan-state">{{ statusLabel(detail.plan.status) }}</text>
				</view>
				<view class="subhead step-heading">步骤</view>
				<view v-for="step in detail.steps || []" :key="step.id" class="step-row">
					<view class="step-dot" :class="'dot-' + String(step.status || '').toLowerCase()" />
					<view class="step-copy"><text class="step-name">{{ step.stepName }}</text><text class="step-tool">{{ step.toolName }}</text></view>
					<text class="step-status">{{ statusLabel(step.status) }}</text>
				</view>

				<view v-if="detail.artifacts && detail.artifacts.length" class="artifact-section">
					<text class="subhead">产物</text>
					<view v-for="artifact in detail.artifacts" :key="artifact.id" class="artifact-row">
						<view><text class="artifact-name">{{ artifact.name || artifact.artifactKey }}</text><text class="artifact-meta">{{ artifact.artifactType }} · {{ artifact.mimeType || 'JSON' }}</text></view>
						<button v-if="artifact.uri && artifact.uri.indexOf('/uploads/') === 0" class="mini-btn" @tap.stop="openArtifact(artifact)">打开</button>
						<button v-else-if="artifact.contentJson" class="mini-btn" @tap.stop="showArtifact(artifact)">查看</button>
					</view>
				</view>

				<view v-if="detail.events && detail.events.length" class="event-section">
					<text class="subhead">最近动态</text>
					<view v-for="event in recentEvents" :key="event.id" class="event-row"><text class="event-type">{{ event.eventType }}</text><text class="event-summary">{{ event.summary }}</text></view>
				</view>
			</view>
			<view class="bottom-space" />
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');
const authSession = require('@/common/authSession.js');

const ACTIVE = ['PLANNING', 'WAITING_APPROVAL', 'READY', 'RUNNING', 'PAUSED', 'CANCEL_REQUESTED'];

export default {
	components: { TavernNavBar },
	data() {
		return {
			tasks: [], total: 0, loading: false, creating: false, busy: false,
			selectedId: 0, detail: null, pollTimer: null,
			draft: { title: '', objective: '' }
		};
	},
	computed: {
		recentEvents() { return (this.detail && this.detail.events ? this.detail.events : []).slice(-8).reverse(); }
	},
	onShow() {
		if (!authSession.requireAuth('/pages/user/agentWorkbench')) return;
		this.loadTasks();
	},
	onUnload() { this.stopPolling(); },
	methods: {
		clientUid() { return tavernApi.getClientUid(); },
		goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) }); },
		async loadTasks() {
			this.loading = true;
			try {
				const data = await tavernApi.fetchAgentTasks(this.clientUid(), { pageSize: 30 });
				this.tasks = data && Array.isArray(data.rows) ? data.rows : [];
				this.total = Number(data && data.total) || this.tasks.length;
				if (this.selectedId && this.tasks.some((row) => Number(row.id) === Number(this.selectedId))) await this.loadDetail(this.selectedId);
			} catch (error) { uni.showToast({ title: error.message || '任务加载失败', icon: 'none' }); }
			finally { this.loading = false; }
		},
		async createTask() {
			if (!this.draft.objective || this.creating) return;
			this.creating = true;
			try {
				const task = await tavernApi.createAgentTask(this.clientUid(), this.draft);
				this.draft = { title: '', objective: '' };
				uni.showToast({ title: '任务已创建', icon: 'success' });
				await this.loadTasks();
				if (task && task.id) await this.selectTask(task.id);
			} catch (error) { uni.showToast({ title: error.message || '创建失败', icon: 'none' }); }
			finally { this.creating = false; }
		},
		async selectTask(id) { this.selectedId = Number(id); await this.loadDetail(this.selectedId); this.startPolling(); },
		async loadDetail(id) {
			if (!id) return;
			try { this.detail = await tavernApi.fetchAgentTask(id, this.clientUid()); }
			catch (error) { uni.showToast({ title: error.message || '详情加载失败', icon: 'none' }); }
		},
		startPolling() { this.stopPolling(); this.pollTimer = setInterval(() => { if (this.detail && ACTIVE.includes(this.detail.task.status)) this.loadDetail(this.selectedId); }, 3500); },
		stopPolling() { if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; } },
		async generatePlan() { await this.run(() => tavernApi.generateAgentPlan(this.selectedId, this.clientUid()), '计划已生成'); },
		async approvePlan() { await this.run(() => tavernApi.approveAgentPlan(this.selectedId, this.clientUid()), '计划已批准'); },
		async control(action) { await this.run(() => tavernApi.controlAgentTask(this.selectedId, action, this.clientUid()), '操作已提交'); },
		async run(fn, success) { if (this.busy) return; this.busy = true; try { await fn(); uni.showToast({ title: success, icon: 'success' }); await this.loadTasks(); await this.loadDetail(this.selectedId); } catch (error) { uni.showToast({ title: error.message || '操作失败', icon: 'none' }); } finally { this.busy = false; } },
		showArtifact(artifact) { let text = artifact.contentJson || ''; try { text = JSON.stringify(JSON.parse(text), null, 2); } catch (e) {} uni.showModal({ title: artifact.name || '产物', content: text.slice(0, 6000), showCancel: false, confirmText: '关闭' }); },
		openArtifact(artifact) {
			const url = tavernApi.resolveJgAssetUrl(artifact.uri);
			if (!url) return;
			// #ifdef H5
			window.open(url, '_blank');
			// #endif
			// #ifndef H5
			uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '链接已复制', icon: 'none' }) });
			// #endif
		},
		progress(item) { const total = Number(item && item.totalSteps) || 0; return total ? Math.min(100, Math.round((Number(item.completedSteps) || 0) * 100 / total)) : 0; },
		statusLabel(status) { return ({ DRAFT: '草稿', PLANNING: '规划中', WAITING_APPROVAL: '待批准', READY: '待执行', RUNNING: '执行中', PAUSED: '已暂停', SUCCEEDED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败', CANCEL_REQUESTED: '取消中', CANCELLED: '已取消', PENDING: '待处理', APPROVED: '已批准', REJECTED: '已拒绝', BLOCKED: '等待前置', SKIPPED: '已跳过' }[status] || status || '-'); },
		formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-'; }
	}
};
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: #edf6fb; position: relative; color: #203747; }
.page-bg { position: fixed; inset: 0; width: 100%; height: 100%; opacity: .18; pointer-events: none; }
.scroll { position: relative; height: calc(100vh - 88rpx); padding: 24rpx; box-sizing: border-box; }
.panel { background: rgba(255,255,255,.9); border: 1rpx solid rgba(120,163,181,.22); border-radius: 24rpx; box-shadow: 0 16rpx 40rpx rgba(72,112,132,.08); }
.hero { padding: 34rpx; display: flex; justify-content: space-between; align-items: center; background: linear-gradient(135deg, #f9fdff, #e5f4fa); }
.eyebrow { display: block; color: #2b809b; font-size: 20rpx; letter-spacing: 3rpx; }
.hero-title { display: block; margin-top: 12rpx; font-size: 38rpx; font-weight: 700; line-height: 1.35; }
.hero-subtitle { display: block; margin-top: 12rpx; color: #718896; font-size: 24rpx; }
.hero-mark { width: 92rpx; height: 92rpx; border-radius: 50%; background: #287e99; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 50rpx; }
.create-panel { margin-top: 22rpx; padding: 28rpx; }
.section-head { display: flex; justify-content: space-between; align-items: center; gap: 20rpx; }
.section-title { font-size: 30rpx; font-weight: 700; }
.section-note, .detail-key { color: #8297a2; font-size: 22rpx; }
.input, .textarea { width: 100%; box-sizing: border-box; margin-top: 20rpx; padding: 22rpx; border-radius: 16rpx; background: #f3f8fb; color: #203747; font-size: 26rpx; }
.textarea { min-height: 190rpx; line-height: 1.5; }
.primary-btn, .action-btn, .mini-btn { border: 0; margin: 0; }
.primary-btn { margin-top: 20rpx; background: #287e99; color: white; border-radius: 16rpx; font-size: 28rpx; }
.list-head { margin: 30rpx 6rpx 14rpx; }
.state { padding: 56rpx 24rpx; text-align: center; color: #8499a5; }
.task-list { display: flex; flex-direction: column; gap: 16rpx; }
.task-card { padding: 24rpx; }
.task-card.active { border-color: #4b9bb3; box-shadow: 0 14rpx 34rpx rgba(56,132,157,.15); }
.task-card-top, .task-meta, .artifact-row { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; }
.task-title { font-size: 29rpx; font-weight: 700; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-objective { display: block; margin-top: 12rpx; color: #637b89; font-size: 24rpx; line-height: 1.45; max-height: 70rpx; overflow: hidden; }
.task-meta, .artifact-meta, .step-tool { margin-top: 14rpx; color: #91a1aa; font-size: 21rpx; }
.progress { height: 8rpx; margin-top: 18rpx; border-radius: 8rpx; overflow: hidden; background: #e8f0f3; }
.progress-fill { height: 100%; background: #4b9bb3; border-radius: inherit; }
.status { flex-shrink: 0; padding: 8rpx 14rpx; border-radius: 20rpx; font-size: 21rpx; background: #edf2f4; color: #71838c; }
.status-running, .status-ready, .status-approved { color: #217b63; background: #e4f5ee; }
.status-waiting_approval, .status-planning { color: #9a681f; background: #fff2dc; }
.status-failed, .status-cancelled { color: #b04f4f; background: #fde8e8; }
.detail { margin-top: 22rpx; padding: 28rpx; }
.action-row { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 22rpx; }
.action-btn { padding: 0 22rpx; min-width: 132rpx; height: 68rpx; line-height: 68rpx; border-radius: 14rpx; color: #315364; background: #edf4f6; font-size: 24rpx; }
.action-primary { color: #fff; background: #287e99; }
.action-danger { color: #a84f51; background: #fdebec; }
.plan-box { margin-top: 24rpx; padding: 18rpx; border-radius: 14rpx; background: #f3f8fb; display: flex; justify-content: space-between; }
.subhead { display: block; margin-top: 24rpx; font-size: 25rpx; font-weight: 700; }
.step-heading { margin-bottom: 12rpx; }
.step-row { display: flex; align-items: center; gap: 14rpx; padding: 17rpx 0; border-bottom: 1rpx solid #edf2f4; }
.step-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #b9c8ce; }
.dot-succeeded { background: #45a77e; }.dot-running { background: #287e99; }.dot-failed { background: #d46868; }
.step-copy { min-width: 0; flex: 1; }.step-name { display: block; font-size: 24rpx; }.step-tool { display: block; margin-top: 5rpx; }
.step-status { color: #82949e; font-size: 22rpx; }
.artifact-section, .event-section { margin-top: 8rpx; }.artifact-row { padding: 16rpx 0; border-bottom: 1rpx solid #edf2f4; }.artifact-name { display: block; font-size: 24rpx; }.mini-btn { height: 54rpx; line-height: 54rpx; padding: 0 18rpx; color: #287e99; background: #e8f5f8; border-radius: 12rpx; font-size: 22rpx; }
.event-row { display: flex; gap: 14rpx; padding: 14rpx 0; border-bottom: 1rpx solid #edf2f4; font-size: 22rpx; }.event-type { color: #287e99; flex-shrink: 0; }.event-summary { color: #71838c; }
.bottom-space { height: 80rpx; }
</style>
