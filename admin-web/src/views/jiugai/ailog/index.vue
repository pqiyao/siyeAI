<template>
  <div class="app-container">
    <el-alert class="mb12" type="info" :closable="false" show-icon title="聊天任务和独立能力均按一次完整请求展示；打开详情可查看真实供应商调用顺序和 fallback 结果。" />

    <el-tabs v-model="activeView" class="ai-log-tabs" @tab-change="handleViewChange">
      <el-tab-pane label="聊天任务" name="TASK" />
      <el-tab-pane label="独立能力" name="STANDALONE" />
    </el-tabs>

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item v-if="activeView === 'TASK'" label="通道">
        <el-select v-model="queryParams.channel" placeholder="全部" clearable style="width: 160px">
          <el-option label="全部" value="" />
          <el-option label="CHAT_SYNC" value="CHAT_SYNC" />
          <el-option label="CHAT_STREAM" value="CHAT_STREAM" />
          <el-option label="MEMORY" value="MEMORY" />
          <el-option label="REGEN" value="REGEN" />
          <el-option label="CONTINUE" value="CONTINUE" />
          <el-option label="REGEN_STREAM" value="REGEN_STREAM" />
          <el-option label="CONTINUE_STREAM" value="CONTINUE_STREAM" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="能力">
        <el-select v-model="queryParams.capability" placeholder="全部" clearable style="width: 140px">
          <el-option label="VISION" value="VISION" />
          <el-option label="IMAGE" value="IMAGE" />
          <el-option label="TTS" value="TTS" />
          <el-option label="STT" value="STT" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input v-model="queryParams.keyword" placeholder="任务/用户/会话/消息/错误" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商">
        <el-input v-model="queryParams.providerKey" placeholder="provider key" clearable style="width: 170px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="模型">
        <el-input v-model="queryParams.model" placeholder="模型名称" clearable style="width: 170px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="HTTP">
        <el-input-number v-model="queryParams.httpStatus" :min="100" :max="599" :controls="false" style="width: 100px" />
      </el-form-item>
      <el-form-item label="TraceId">
        <el-input
          v-model="queryParams.traceId"
          placeholder="粘贴 traceId"
          clearable
          style="width: 280px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 360px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" @click="handleClean">清理 N 天前</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" max-height="640">
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="时间" prop="createdAt" width="170" />
      <el-table-column label="TraceId" min-width="260" show-overflow-tooltip>
        <template #default="scope">
          <el-button v-if="scope.row.traceId" link type="primary" icon="CopyDocument" @click="copyTraceId(scope.row.traceId)">
            {{ scope.row.traceId }}
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="activeView === 'TASK'" label="用户ID" prop="userId" width="88" />
      <el-table-column :label="activeView === 'TASK' ? '通道' : '能力'" :prop="activeView === 'TASK' ? 'channel' : 'capability'" width="130" />
      <el-table-column label="供应商" prop="providerKey" min-width="150" show-overflow-tooltip />
      <el-table-column label="模型" prop="model" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="activeView === 'TASK'" label="角色ID" prop="characterId" width="88" />
      <el-table-column v-if="activeView === 'TASK'" label="会话ID" prop="conversationId" width="88" />
      <el-table-column :label="activeView === 'TASK' ? '消息ID' : '请求ID'" :prop="activeView === 'TASK' ? 'clientMessageId' : 'requestId'" min-width="150" show-overflow-tooltip />
      <el-table-column label="耗时ms" prop="durationMs" width="88" />
      <el-table-column label="状态" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="尝试" width="80"><template #default="scope"><span>{{ scope.row.attemptCount || 0 }}</span><el-tag v-if="scope.row.wasFallback" class="ml6" type="warning" size="small">F</el-tag></template></el-table-column>
      <el-table-column label="HTTP" prop="httpStatus" width="72" />
      <el-table-column label="prompt" prop="promptTokens" width="80" />
      <el-table-column label="completion" prop="completionTokens" width="96" />
      <el-table-column label="错误" prop="errorMessage" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="88">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="openDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="detailOpen" title="AI 调用详情" width="min(1100px, 96vw)" append-to-body destroy-on-close>
      <div class="ai-log-detail">
        <el-descriptions v-if="detail.id && activeView === 'TASK'" :column="3" border size="small">
          <el-descriptions-item label="任务ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="HTTP">{{ detail.httpStatus }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="角色ID">{{ detail.characterId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ detail.conversationId }}</el-descriptions-item>
          <el-descriptions-item label="通道">{{ detail.channel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ detail.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.durationMs || 0 }} ms</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="3">
            <el-button v-if="detail.traceId" link type="primary" icon="CopyDocument" @click="copyTraceId(detail.traceId)">
              {{ detail.traceId }}
            </el-button>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="消息ID" :span="2">{{ detail.clientMessageId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Token">{{ detail.promptTokens || 0 }} / {{ detail.completionTokens || 0 }}</el-descriptions-item>
          <el-descriptions-item label="有效预设">{{ detail.effectivePresetId || 'ST 全局兜底' }}</el-descriptions-item>
          <el-descriptions-item label="有效上下文">{{ detail.effectiveMaxContext || '-' }}</el-descriptions-item>
          <el-descriptions-item label="有效输出上限">{{ detail.effectiveMaxTokens || '-' }}</el-descriptions-item>
          <el-descriptions-item label="有效供应商">{{ detail.effectiveProvider || '-' }}</el-descriptions-item>
          <el-descriptions-item label="API 来源">{{ detail.effectiveApiSource || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="detail.id && activeView === 'STANDALONE'" :column="3" border size="small">
          <el-descriptions-item label="请求ID" :span="2">{{ detail.requestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="能力">{{ detail.capability || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="最终HTTP">{{ detail.httpStatus == null ? '-' : detail.httpStatus }}</el-descriptions-item>
          <el-descriptions-item label="累计耗时">{{ detail.durationMs || 0 }} ms</el-descriptions-item>
          <el-descriptions-item label="最终供应商">{{ detail.providerKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最终模型">{{ detail.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尝试次数">{{ detail.attemptCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="3">
            <el-button v-if="detail.traceId" link type="primary" icon="CopyDocument" @click="copyTraceId(detail.traceId)">{{ detail.traceId }}</el-button>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="Token">{{ detail.promptTokens || 0 }} / {{ detail.completionTokens || 0 }}</el-descriptions-item>
          <el-descriptions-item label="成本 USD">{{ formatCost(detail.totalCostUsd) }}</el-descriptions-item>
        </el-descriptions>

        <template>
          <el-divider content-position="left">供应商调用链</el-divider>
          <el-table v-loading="attemptLoading" :data="attempts" border max-height="360" empty-text="旧记录或未产生供应商遥测">
            <el-table-column label="顺序" prop="attemptNo" width="64" />
            <el-table-column label="供应商" prop="providerKey" min-width="150" show-overflow-tooltip />
            <el-table-column label="路由" prop="routeKey" min-width="130" show-overflow-tooltip />
            <el-table-column label="模型" prop="model" min-width="150" show-overflow-tooltip />
            <el-table-column label="模式" width="86"><template #default="scope">{{ scope.row.byok ? 'BYOK' : '官方' }}</template></el-table-column>
            <el-table-column label="预设" width="76"><template #default="scope">{{ scope.row.effectivePresetId || '-' }}</template></el-table-column>
            <el-table-column label="上下文/输出" width="118"><template #default="scope">{{ scope.row.effectiveMaxContext || '-' }} / {{ scope.row.effectiveMaxTokens || '-' }}</template></el-table-column>
            <el-table-column label="API 来源" prop="effectiveApiSource" min-width="100" show-overflow-tooltip />
            <el-table-column label="Fallback" width="88"><template #default="scope">{{ scope.row.wasFallback ? '是' : '否' }}</template></el-table-column>
            <el-table-column label="状态" width="92"><template #default="scope"><el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="HTTP" prop="httpStatus" width="68" />
            <el-table-column label="TTFT" width="84"><template #default="scope">{{ scope.row.ttftMs == null ? '-' : scope.row.ttftMs + ' ms' }}</template></el-table-column>
            <el-table-column label="耗时" width="90"><template #default="scope">{{ scope.row.durationMs || 0 }} ms</template></el-table-column>
            <el-table-column label="Token" width="110"><template #default="scope">{{ scope.row.promptTokens || 0 }} / {{ scope.row.completionTokens || 0 }}</template></el-table-column>
            <el-table-column label="成本 USD" width="110"><template #default="scope">{{ formatCost(scope.row.totalCostUsd) }}</template></el-table-column>
            <el-table-column label="错误" prop="errorMessage" min-width="220" show-overflow-tooltip />
          </el-table>
        </template>

        <el-alert
          v-if="String(detail.status || '').toUpperCase() === 'SUCCESS'"
          class="mt12"
          type="success"
          show-icon
          :closable="false"
          title="请求成功，无错误日志"
        />
        <template v-else>
          <el-divider content-position="left">错误日志</el-divider>
          <div class="error-block">
            <div class="error-line">
              <span class="error-label">errorCode</span>
              <span>{{ detail.errorCode || '-' }}</span>
            </div>
            <pre class="detail-error">{{ detail.errorMessage || detail.errorCode || '没有记录到更详细的错误信息，请复制 TraceId 到服务器日志中检索。' }}</pre>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="JgAiLog">
import { listJgAiLog, cleanJgAiLog, listAiTaskAttempts, listStandaloneAiAttempts, listStandaloneAiRequestAttempts } from '@/api/jiugai/ailog'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})
const activeView = ref('TASK')
const attempts = ref([])
const attemptLoading = ref(false)
const timeRange = ref([])
const statusOptions = [
  { value: 'QUEUED', label: '排队中' },
  { value: 'GENERATING', label: '生成中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'STOPPED', label: '已停止' },
  { value: 'CANCELLED', label: '已取消' }
]

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    channel: '',
    capability: '',
    status: '',
    traceId: '',
    keyword: '',
    providerKey: '',
    model: '',
    httpStatus: undefined
  }
})
const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  const q = {
    pageNum: queryParams.value.pageNum,
    pageSize: queryParams.value.pageSize
  }
  for (const key of ['channel', 'capability', 'status', 'traceId', 'keyword', 'providerKey', 'model']) {
    const value = queryParams.value[key]
    if (value != null && String(value).trim() !== '') q[key] = String(value).trim()
  }
  if (queryParams.value.httpStatus) q.httpStatus = queryParams.value.httpStatus
  if (Array.isArray(timeRange.value) && timeRange.value.length === 2) {
    q.startedAfter = timeRange.value[0]
    q.startedBefore = timeRange.value[1]
  }
  const loader = activeView.value === 'TASK' ? listJgAiLog : listStandaloneAiAttempts
  loader(q)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载日志失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.pageNum = 1
  queryParams.value.channel = ''
  queryParams.value.capability = ''
  queryParams.value.status = ''
  queryParams.value.traceId = ''
  queryParams.value.keyword = ''
  queryParams.value.providerKey = ''
  queryParams.value.model = ''
  queryParams.value.httpStatus = undefined
  timeRange.value = []
  getList()
}

function handleViewChange() {
  queryParams.value.pageNum = 1
  queryParams.value.channel = ''
  queryParams.value.capability = ''
  getList()
}

function copyTraceId(traceId) {
  const text = String(traceId || '').trim()
  if (!text) return
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(() => {
      proxy.$modal.msgSuccess('TraceId 已复制')
    })
  }
}

function openDetail(row) {
  if (!row || !row.id) return
  detail.value = { ...row }
  attempts.value = []
  detailOpen.value = true
  if (activeView.value === 'TASK') {
    attemptLoading.value = true
    listAiTaskAttempts(row.id)
      .then((res) => { attempts.value = res.rows || [] })
      .catch((e) => proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载供应商调用链失败')))
      .finally(() => { attemptLoading.value = false })
    return
  }
  if (String(row.requestId || '').startsWith('legacy-')) {
    attempts.value = [{ ...row, attemptNo: row.attemptNo || 1 }]
    return
  }
  attemptLoading.value = true
  listStandaloneAiRequestAttempts(row.requestId)
    .then((res) => { attempts.value = res.rows || [] })
    .catch((e) => proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载供应商调用链失败')))
    .finally(() => { attemptLoading.value = false })
}

function statusLabel(value) {
  return statusOptions.find((item) => item.value === String(value || '').toUpperCase())?.label || value || '-'
}

function statusTagType(value) {
  const status = String(value || '').toUpperCase()
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'STOPPED' || status === 'CANCELLED') return 'warning'
  return 'info'
}

function formatCost(value) {
  if (value == null || value === '') return '-'
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(6) : String(value)
}

function handleClean() {
  proxy.$modal
    .prompt('清理早于“当前时间 - 输入天数”的 AI 日志明细。系统会先固化统计，首页趋势不受影响；日志明细清理后不可恢复，请输入天数。')
    .then((res) => {
      const raw =
        res && typeof res === 'object' && res.value !== undefined && res.value !== null
          ? res.value
          : res
      const days = parseInt(String(raw).trim(), 10)
      if (Number.isNaN(days) || days < 1) {
        proxy.$modal.msgError('请输入正整数')
        return Promise.reject(new Error('invalid'))
      }
      return cleanJgAiLog(days)
    })
    .then(() => {
      proxy.$modal.msgSuccess('清理完成')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '清理失败'))
    })
}

getList()
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.ai-log-tabs { margin-bottom: 12px; }
.ml6 { margin-left: 6px; }
.ai-log-detail {
  min-height: 220px;
}

.detail-error {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  line-height: 1.55;
}

.detail-error {
  color: #b42318;
}

.error-block {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #fff7f7;
}

.error-line {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.error-label {
  color: #64748b;
  font-weight: 600;
}
</style>
