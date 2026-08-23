<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div><h2>运行中心</h2><p>只读观察 Worker、任务队列、审批和外部连接就绪度。</p></div>
      <div class="heading-actions"><el-tag type="info" effect="plain">{{ checkedAt || '未查询' }}</el-tag><el-button :loading="loading" type="primary" @click="load">刷新</el-button></div>
    </div>

    <el-row :gutter="14" class="summary-row">
      <el-col v-for="item in cards" :key="item.key" :xs="12" :sm="6" :lg="4"><el-card shadow="never" class="summary-card"><div class="summary-label">{{ item.label }}</div><div class="summary-value" :class="item.tone">{{ item.value }}</div><div class="summary-note">{{ item.note }}</div></el-card></el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="15"><el-card shadow="never" class="panel-card"><template #header><div class="panel-title"><span>当前任务队列</span><el-tag size="small" type="info">最多展示 50 条</el-tag></div></template><el-table v-loading="loading" :data="activeTasks" empty-text="暂无待运行任务"><el-table-column prop="taskKey" label="任务" min-width="190" /><el-table-column prop="taskType" label="类型" width="150" /><el-table-column prop="status" label="状态" width="150"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="进度" width="120"><template #default="{ row }">{{ row.completedSteps || 0 }} / {{ row.totalSteps || 0 }}</template></el-table-column><el-table-column prop="updatedAt" label="更新时间" width="180" /></el-table></el-card></el-col>
      <el-col :xs="24" :lg="9"><el-card shadow="never" class="panel-card"><template #header><div class="panel-title"><span>Worker 状态</span><el-tag size="small" type="success">{{ worker.mode || 'unknown' }}</el-tag></div></template><el-descriptions :column="1" border><el-descriptions-item label="服务">{{ runtime.service || '-' }}</el-descriptions-item><el-descriptions-item label="Java">{{ runtime.javaVersion || '-' }}</el-descriptions-item><el-descriptions-item label="调度间隔">{{ worker.intervalSeconds || '-' }} 秒</el-descriptions-item><el-descriptions-item label="租约时长">{{ worker.leaseSeconds || '-' }} 秒</el-descriptions-item><el-descriptions-item label="快照时间">{{ checkedAt || '-' }}</el-descriptions-item></el-descriptions></el-card></el-col>
    </el-row>

    <el-row :gutter="14"><el-col :xs="24" :lg="12"><el-card shadow="never" class="panel-card"><template #header>待处理审批</template><el-table :data="pendingApprovals" empty-text="暂无待处理审批"><el-table-column prop="taskKey" label="任务" min-width="180" /><el-table-column prop="approvalType" label="类型" width="130" /><el-table-column prop="createdAt" label="创建时间" width="180" /></el-table></el-card></el-col><el-col :xs="24" :lg="12"><el-card shadow="never" class="panel-card"><template #header>外部连接就绪度</template><div class="readiness-grid"><div v-for="item in readiness" :key="item.key" class="readiness-item"><span>{{ item.label }}</span><el-tag size="small" :type="item.value ? 'success' : 'info'">{{ item.value ? '已配置' : '未配置' }}</el-tag></div></div><p class="panel-note">就绪状态不代表真实供应商验收通过，调用仍受白名单、风险和审批链约束。</p></el-card></el-col></el-row>

    <el-card shadow="never" class="panel-card"><template #header>发布准备</template><el-alert :title="releaseReadiness.status || '未知'" :type="releaseReadiness.status === 'READY_FOR_APPROVAL' ? 'success' : 'warning'" :closable="false" show-icon /><div v-if="releaseReadiness.blockers?.length" class="blocker-list"><el-tag v-for="item in releaseReadiness.blockers" :key="item" type="danger" effect="plain">{{ item }}</el-tag></div><p class="panel-note">快照时间：{{ releaseReadiness.checkedAt ? new Date(releaseReadiness.checkedAt).toLocaleString() : '未知' }}。只读聚合数据库、构建、Git 和备份状态；构建、部署和回滚仍需人工审批。</p></el-card>
    <el-card shadow="never" class="panel-card"><template #header>当前告警</template><el-alert v-for="item in alerts" :key="`${item.code}-${item.message}`" :title="item.message" :type="alertType(item.severity)" :closable="false" show-icon /><el-empty v-if="!alerts.length" description="暂无告警" :image-size="52" /></el-card>
  </div>
</template>

<script setup name="JgAgentRuntime">
import { getAgentRuntime, getAgentReleaseReadiness } from '@/api/jiugai/agentRuntime'

const loading = ref(false)
const runtime = ref({})
const queue = ref({})
const overview = ref({})
const toolReadiness = ref({})
const externalReadiness = ref({})
const alerts = ref([])
const releaseReadiness = ref({})
const worker = computed(() => runtime.value.worker || {})
const checkedAt = computed(() => runtime.value.checkedAt ? new Date(runtime.value.checkedAt).toLocaleString() : '')
const activeTasks = computed(() => [...(queue.value.runningTasks || []), ...(queue.value.readyTasks || []), ...(queue.value.waitingApprovalTasks || [])])
const pendingApprovals = computed(() => queue.value.pendingApprovals || [])
const dailyBudgetUsage = computed(() => externalReadiness.value.dailyBudgetUsage || {})
const cards = computed(() => [
  { key: 'running', label: '运行中任务', value: overview.value.runningTasks || 0, note: 'RUNNING' },
  { key: 'ready', label: '就绪任务', value: (queue.value.readyTasks || []).length, note: '等待 Worker 领取' },
  { key: 'approval', label: '待审批', value: overview.value.pendingApprovals || 0, note: '计划或工具动作' },
  { key: 'tools', label: '启用工具', value: toolReadiness.value.enabledTools || 0, note: `注册 ${toolReadiness.value.totalTools || 0}` },
  { key: 'artifacts', label: '可用产物', value: overview.value.readyArtifacts || 0, note: 'READY' },
  { key: 'failed', label: '失败任务', value: overview.value.failedTasks || 0, note: '需要关注', tone: overview.value.failedTasks ? 'danger' : '' },
  { key: 'dailyTokens', label: '今日 Token', value: dailyBudgetUsage.value.tokenUsage || 0, note: `${dailyBudgetUsage.value.taskCount || 0} 个任务` },
  { key: 'dailyCost', label: '今日费用', value: `$${money(dailyBudgetUsage.value.costUsage)}`, note: '日配额用量' },
  { key: 'dailyTasks', label: '今日任务', value: dailyBudgetUsage.value.taskCount || 0, note: '日配额用量' }
])
function money(value) { const number = Number(value || 0); return Number.isFinite(number) ? number.toFixed(6) : '0.000000' }
const readiness = computed(() => { const e = externalReadiness.value; return [{ key: 'apiRequest', label: 'API Request', value: !!e.apiRequest }, { key: 'webSearch', label: 'Web Search', value: !!e.webSearch }, { key: 'browser', label: 'Browser', value: !!e.browser }, { key: 'mcp', label: 'MCP', value: !!e.mcp }, { key: 'codeRunner', label: 'Code Runner', value: !!e.codeRunner }] })
function statusType(status) { return ({ RUNNING: 'primary', READY: 'success', WAITING_APPROVAL: 'warning' })[status] || 'info' }
function alertType(severity) { return severity === 'CRITICAL' ? 'error' : severity === 'WARNING' ? 'warning' : 'info' }
async function load() { loading.value = true; try { const [runtimeResult, readinessResult] = await Promise.allSettled([getAgentRuntime(), getAgentReleaseReadiness()]); if (runtimeResult.status === 'fulfilled') { const data = runtimeResult.value.data || {}; runtime.value = data; queue.value = data.queue || {}; overview.value = data.overview || {}; toolReadiness.value = data.toolReadiness || {}; externalReadiness.value = data.externalReadiness || {}; alerts.value = data.alerts || [] } else { alerts.value = [{ severity: 'WARNING', code: 'RUNTIME_SNAPSHOT_UNAVAILABLE', message: '运行中心快照暂时不可用，请稍后刷新' }] } releaseReadiness.value = readinessResult.status === 'fulfilled' ? (readinessResult.value.data || {}) : { status: 'UNAVAILABLE', blockers: ['RELEASE_READINESS_UNAVAILABLE'] } } finally { loading.value = false } }
onMounted(load)
</script>

<style scoped>
.agent-page { color: #1f2937; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.page-heading h2 { margin: 0 0 6px; font-size: 22px; }.page-heading p { margin: 0; color: #6b7280; }.heading-actions { display: flex; align-items: center; gap: 10px; }.summary-row { margin-bottom: 14px; }.summary-card { min-height: 105px; }.summary-label, .summary-note { color: #6b7280; font-size: 13px; }.summary-value { margin: 9px 0 4px; color: #1f2937; font-size: 24px; font-weight: 600; }.summary-value.danger { color: #dc2626; }.panel-card { margin-bottom: 14px; }.panel-title { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }.readiness-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.readiness-item { display: flex; align-items: center; justify-content: space-between; padding: 10px; background: #f8fafc; border-radius: 4px; }.blocker-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }.panel-note { margin: 14px 0 0; color: #909399; font-size: 12px; line-height: 1.5; }.panel-card :deep(.el-alert + .el-alert) { margin-top: 8px; } @media (max-width: 700px) { .page-heading { flex-direction: column; }.heading-actions { width: 100%; justify-content: space-between; } }
</style>
