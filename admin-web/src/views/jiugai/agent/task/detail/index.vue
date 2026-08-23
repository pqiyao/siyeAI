<template>
  <div v-loading="loading" class="app-container agent-detail-page">
    <div class="page-heading">
      <div>
        <el-button link icon="ArrowLeft" @click="goBack">返回任务中心</el-button>
        <h2>{{ task.title || 'Agent 任务详情' }}</h2>
        <p>{{ task.taskKey || '-' }} · {{ task.taskType || '-' }}</p>
      </div>
      <div class="heading-actions">
        <el-tag :type="statusType(task.status)" effect="plain">{{ statusLabel(task.status) }}</el-tag>
        <el-button v-if="task.status === 'DRAFT'" v-hasPermi="['agent:task:control']" type="primary" @click="generatePlan">生成计划</el-button>
        <el-button v-if="task.status === 'WAITING_APPROVAL'" v-hasPermi="['agent:approval:approve']" type="primary" @click="approvePlan">批准计划</el-button>
        <el-button v-if="canPause" v-hasPermi="['agent:task:control']" @click="runAction('pause')">暂停</el-button>
        <el-button v-if="task.status === 'PAUSED'" v-hasPermi="['agent:task:control']" type="primary" @click="runAction('resume')">继续</el-button>
        <el-button v-if="canCancel" v-hasPermi="['agent:task:control']" type="danger" plain @click="runAction('cancel')">取消</el-button>
        <el-button v-if="retryable" v-hasPermi="['agent:task:retry']" type="warning" plain @click="runAction('retry')">重试</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title">任务摘要</div></template>
          <dl class="summary-list">
            <dt>目标</dt><dd class="objective">{{ task.objective || '-' }}</dd>
            <dt>风险等级</dt><dd>{{ task.riskLevel || '-' }}</dd>
            <dt>计划</dt>
            <dd class="plan-summary">
              <span>{{ plan.planVersion ? `v${plan.planVersion} · ${statusLabel(plan.status)}` : '未生成' }}</span>
              <el-button v-if="plan.planJson" link type="primary" @click="viewPlan">查看计划</el-button>
            </dd>
            <dt>步骤进度</dt><dd>{{ task.completedSteps || 0 }} / {{ task.totalSteps || 0 }}</dd>
            <dt>Token / 费用</dt><dd>{{ task.tokenUsage || 0 }} / {{ task.costAmount || 0 }}</dd>
            <dt>预算</dt><dd>{{ task.maxTokens || 0 }} Token / {{ task.maxCost || 0 }} USD</dd>
            <dt>最长运行</dt><dd>{{ task.maxRuntimeSeconds || 0 }} 秒</dd>
            <dt>创建时间</dt><dd>{{ task.createdAt || '-' }}</dd>
            <dt>最近更新</dt><dd>{{ task.updatedAt || '-' }}</dd>
            <dt v-if="task.lastError">最近错误</dt><dd v-if="task.lastError" class="error-text">{{ task.lastError }}</dd>
          </dl>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title">执行步骤</div></template>
          <el-table :data="steps" empty-text="当前还没有计划步骤">
            <el-table-column prop="sortOrder" label="#" width="60" />
            <el-table-column prop="stepName" label="步骤" min-width="190" />
            <el-table-column prop="agentRole" label="Agent 角色" width="140" />
            <el-table-column label="输入摘要" min-width="220" show-overflow-tooltip><template #default="scope">{{ jsonSummary(scope.row.inputJson) }}</template></el-table-column>
            <el-table-column label="输出摘要" min-width="220" show-overflow-tooltip><template #default="scope">{{ scope.row.outputSummary || jsonSummary(scope.row.outputJson) }}</template></el-table-column>
            <el-table-column label="状态" width="120"><template #default="scope"><el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="attemptCount" label="尝试" width="80" />
            <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title">工具调用</div></template>
          <el-table :data="toolCalls" empty-text="当前还没有工具调用记录">
            <el-table-column label="步骤 / 工具" min-width="220">
              <template #default="scope">
                <strong>{{ scope.row.toolName || '-' }}</strong>
                <div class="muted">{{ scope.row.stepName || `步骤 ${scope.row.stepId || '-'}` }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110"><template #default="scope"><el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="attempt" label="尝试" width="80" />
            <el-table-column label="耗时" width="100"><template #default="scope">{{ formatDuration(scope.row.durationMs) }}</template></el-table-column>
            <el-table-column prop="outputSummary" label="结果摘要" min-width="280" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="错误" min-width="240" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title">公开事件</div></template>
          <el-timeline v-if="events.length">
            <el-timeline-item v-for="event in events" :key="event.id" :timestamp="event.createdAt" placement="top">
              <strong>{{ event.eventType }}</strong><div class="event-summary">{{ event.summary || '-' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无公开事件" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title">产物</div></template>
          <el-table :data="artifacts" empty-text="暂无产物">
            <el-table-column label="产物" min-width="220">
              <template #default="scope">
                <div class="artifact-name">
                  <el-image
                    v-if="isImage(scope.row) && scope.row.uri"
                    class="artifact-thumb"
                    :src="assetUrl(scope.row.uri)"
                    :preview-src-list="[assetUrl(scope.row.uri)]"
                    preview-teleported
                    fit="cover"
                  />
                  <div class="artifact-name-text">
                    <strong>{{ scope.row.name || scope.row.artifactKey || '-' }}</strong>
                    <small>{{ scope.row.artifactKey || '-' }}</small>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="scope">{{ artifactTypeLabel(scope.row.artifactType) }}</template>
            </el-table-column>
            <el-table-column label="试听" min-width="190">
              <template #default="scope">
                <audio v-if="isAudio(scope.row) && scope.row.uri" class="artifact-audio" controls preload="metadata" :src="assetUrl(scope.row.uri)">当前浏览器不支持音频播放</audio>
                <span v-else class="artifact-meta">{{ isAudio(scope.row) ? '音频尚未就绪' : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="文件信息" min-width="170">
              <template #default="scope">
                <div class="artifact-meta">{{ formatBytes(scope.row.sizeBytes) }} · {{ scope.row.mimeType || '结构化内容' }}</div>
                <div v-if="scope.row.contentHash" class="artifact-hash" :title="scope.row.contentHash">SHA {{ shortHash(scope.row.contentHash) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button v-if="isImage(scope.row) && scope.row.uri" link type="primary" @click="previewImage(scope.row)">预览</el-button>
                <el-button v-if="isHtml(scope.row) && scope.row.uri" link type="primary" @click="previewHtml(scope.row)">打开</el-button>
                <el-button v-if="scope.row.contentJson" link type="primary" @click="viewArtifactContent(scope.row)">内容</el-button>
                <el-button v-if="scope.row.uri" link type="primary" icon="Download" @click="downloadArtifact(scope.row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="contentVisible" :title="contentArtifact.name || '产物内容'" width="760px" destroy-on-close>
      <pre class="artifact-content">{{ contentText }}</pre>
    </el-dialog>

    <el-dialog v-model="planVisible" title="计划详情" width="860px" destroy-on-close>
      <el-descriptions :column="3" border class="plan-meta">
        <el-descriptions-item label="版本">{{ plan.planVersion ? `v${plan.planVersion}` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(plan.status) }}</el-descriptions-item>
        <el-descriptions-item label="生成方式">{{ plan.generatedBy || '-' }}</el-descriptions-item>
      </el-descriptions>
      <pre class="artifact-content plan-content">{{ planText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup name="JgAgentTaskDetail">
import { saveAs } from 'file-saver'
import { agentTaskEventStreamUrl, approveAgentPlan, cancelAgentTask, downloadAgentArtifact, generateAgentPlan, getAgentTask, listAgentTaskArtifacts, listAgentTaskEvents, pauseAgentTask, resumeAgentTask, retryAgentTask } from '@/api/jiugai/agentTask'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance()
const route = useRoute()
const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const loading = ref(false)
const task = ref({})
const plan = ref({})
const steps = ref([])
const toolCalls = ref([])
const events = ref([])
const artifacts = ref([])
const contentVisible = ref(false)
const contentArtifact = ref({})
const planVisible = ref(false)
let eventStreamController = null
let eventStreamReconnectTimer = null
let eventStreamStopped = false
let eventCursor = 0

const statusNames = {
  DRAFT: '草稿', PLANNING: '规划中', WAITING_APPROVAL: '等待审批', READY: '待执行', RUNNING: '执行中',
  PAUSED: '已暂停', WAITING_CONFIRMATION: '等待确认', CANCEL_REQUESTED: '取消中', SUCCEEDED: '成功',
  PARTIAL_SUCCESS: '部分成功', FAILED: '失败', CANCELLED: '已取消', PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝'
}
const statusLabel = (status) => statusNames[status] || status || '-'
const statusType = (status) => ({ RUNNING: 'primary', WAITING_APPROVAL: 'warning', PAUSED: 'warning', SUCCEEDED: 'success', PARTIAL_SUCCESS: 'warning', FAILED: 'danger', CANCELLED: 'info', CANCEL_REQUESTED: 'warning' }[status] || 'info')
const canPause = computed(() => ['READY', 'RUNNING'].includes(task.value.status))
const canCancel = computed(() => ['DRAFT', 'PLANNING', 'WAITING_APPROVAL', 'READY', 'RUNNING', 'PAUSED', 'WAITING_CONFIRMATION', 'CANCEL_REQUESTED'].includes(task.value.status))
const retryable = computed(() => ['FAILED', 'PARTIAL_SUCCESS'].includes(task.value.status))
const contentText = computed(() => {
  const value = contentArtifact.value.contentJson
  if (!value) return '-'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) }
})
const planText = computed(() => {
  const value = plan.value.planJson
  if (!value) return '-'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) }
})

function taskId() {
  const id = Number(route.params.id)
  return Number.isSafeInteger(id) && id > 0 ? id : 0
}

async function load() {
  const id = taskId()
  if (!id) {
    proxy.$modal.msgError('任务编号无效')
    return
  }
  loading.value = true
  try {
    const [taskResponse, eventResponse, artifactResponse] = await Promise.all([
      getAgentTask(id),
      listAgentTaskEvents(id),
      listAgentTaskArtifacts(id)
    ])
    task.value = taskResponse.data || {}
    plan.value = taskResponse.plan || {}
    steps.value = taskResponse.steps || []
    toolCalls.value = taskResponse.toolCalls || []
    events.value = eventResponse.data || []
    eventCursor = events.value.reduce((cursor, event) => Math.max(cursor, Number(event.eventSeq) || 0), eventCursor)
    artifacts.value = artifactResponse.data || []
  } finally {
    loading.value = false
  }
}

async function generatePlan() {
  await generateAgentPlan(taskId())
  proxy.$modal.msgSuccess('计划已生成，等待审批')
  await load()
}

async function approvePlan() {
  await approveAgentPlan(taskId())
  proxy.$modal.msgSuccess('计划已批准，任务可以执行')
  await load()
}

async function runAction(action) {
  const id = taskId()
  const actions = { pause: pauseAgentTask, resume: resumeAgentTask, cancel: cancelAgentTask, retry: retryAgentTask }
  const handler = actions[action]
  if (!handler) return
  await handler(id)
  proxy.$modal.msgSuccess('任务状态已更新')
  await load()
}

function goBack() {
  proxy.$router.back()
}

function viewPlan() {
  planVisible.value = true
}

async function connectEventStream() {
  if (eventStreamStopped || eventStreamController) return
  const token = getToken()
  if (!token) return
  const controller = new AbortController()
  eventStreamController = controller
  try {
    const response = await fetch(agentTaskEventStreamUrl(taskId(), eventCursor), {
      headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
      signal: controller.signal
    })
    if (!response.ok || !response.body) throw new Error(`事件流连接失败：${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (!eventStreamStopped) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const data = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).join('\n')
        const name = block.split(/\r?\n/).find(line => line.startsWith('event:'))?.slice(6).trim()
        if (name !== 'agent-event' || !data) continue
        const event = JSON.parse(data)
        eventCursor = Math.max(eventCursor, Number(event.eventSeq) || 0)
        await load()
      }
    }
    scheduleEventStreamReconnect()
  } catch (error) {
    if (!eventStreamStopped && error?.name !== 'AbortError') {
      scheduleEventStreamReconnect()
    }
  } finally {
    if (eventStreamController === controller) eventStreamController = null
  }
}

function scheduleEventStreamReconnect() {
  if (eventStreamStopped || eventStreamReconnectTimer) return
  eventStreamReconnectTimer = window.setTimeout(() => {
    eventStreamReconnectTimer = null
    connectEventStream()
  }, 5000)
}

function stopEventStream() {
  eventStreamStopped = true
  if (eventStreamReconnectTimer) window.clearTimeout(eventStreamReconnectTimer)
  eventStreamController?.abort()
  eventStreamController = null
}

function assetUrl(url) {
  const value = String(url || '').trim()
  if (!value || /^(https?:|data:|blob:)/i.test(value)) return value
  return value.startsWith('/') ? baseApi + value : value
}

function isImage(artifact) {
  return String(artifact?.artifactType || '').toUpperCase() === 'IMAGE'
}

function isHtml(artifact) {
  return String(artifact?.artifactType || '').toUpperCase() === 'HTML'
}

function isAudio(artifact) {
  return String(artifact?.artifactType || '').toUpperCase() === 'AUDIO'
}

function artifactTypeLabel(type) {
  return ({ IMAGE: '图片', AUDIO: '音频', HTML: '互动网页', PACKAGE_ZIP: '作品包', WORLD_BOOK: '世界书', CHARACTER_CARD: '角色卡', STORY_OUTLINE: '剧情大纲', CONTINUITY_REPORT: '一致性报告' }[String(type || '').toUpperCase()] || type || '-')
}

function formatBytes(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 1) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function shortHash(value) {
  const hash = String(value || '')
  return hash.length > 16 ? `${hash.slice(0, 8)}...${hash.slice(-8)}` : hash
}

function formatDuration(value) {
  const duration = Number(value)
  if (!Number.isFinite(duration) || duration < 1) return '-'
  return duration < 1000 ? `${duration} ms` : `${(duration / 1000).toFixed(1)} s`
}

function jsonSummary(value) {
  if (!value) return '-'
  try {
    const parsed = JSON.parse(value)
    return JSON.stringify(parsed)
  } catch {
    return String(value)
  }
}

function previewImage(artifact) {
  const url = assetUrl(artifact.uri)
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

function previewHtml(artifact) {
  const url = assetUrl(artifact.uri)
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

function viewArtifactContent(artifact) {
  contentArtifact.value = artifact
  contentVisible.value = true
}

async function downloadArtifact(artifact) {
  const blob = await downloadAgentArtifact(taskId(), artifact.artifactKey)
  saveAs(blob, artifact.name || artifact.artifactKey || 'agent-artifact')
}

onMounted(async () => {
  await load()
  connectEventStream()
})
onBeforeUnmount(stopEventStream)
</script>

<style scoped>
.agent-detail-page { color: #1f2937; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-heading h2 { margin: 8px 0 4px; font-size: 22px; }
.page-heading p { margin: 0; color: #6b7280; }
.heading-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.panel-card { min-height: 220px; margin-bottom: 16px; }
.panel-title { font-weight: 600; }
.summary-list { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 12px 16px; margin: 0; }
.summary-list dt { color: #909399; }
.summary-list dd { margin: 0; word-break: break-word; }
.plan-summary { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.objective { white-space: pre-wrap; line-height: 1.6; }
.error-text { color: #f56c6c; }
.event-summary { margin-top: 4px; color: #606266; }
.muted { margin-top: 3px; color: #909399; font-size: 12px; }
.artifact-name { display: flex; align-items: center; gap: 10px; min-width: 0; }
.artifact-thumb { flex: 0 0 auto; width: 42px; height: 42px; border-radius: 4px; border: 1px solid #ebeef5; background: #f5f7fa; }
.artifact-audio { width: 178px; height: 32px; }
.artifact-name-text { display: flex; flex-direction: column; min-width: 0; gap: 3px; }
.artifact-name-text strong, .artifact-name-text small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-name-text small, .artifact-meta, .artifact-hash { color: #909399; font-size: 12px; }
.artifact-hash { margin-top: 3px; font-family: monospace; }
.artifact-content { max-height: 60vh; margin: 0; padding: 14px; overflow: auto; color: #303133; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px; white-space: pre-wrap; word-break: break-word; }
.plan-meta { margin-bottom: 14px; }
.plan-content { max-height: 55vh; }
@media (max-width: 700px) { .page-heading { flex-direction: column; } .heading-actions { width: 100%; } }
</style>
