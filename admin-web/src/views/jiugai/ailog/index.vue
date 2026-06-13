<template>
  <div class="app-container">
    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="通道">
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
      <el-form-item label="结果">
        <el-select v-model="successFilter" placeholder="全部" clearable style="width: 120px">
          <el-option label="成功" value="ok" />
          <el-option label="失败" value="fail" />
        </el-select>
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
      <el-table-column label="用户ID" prop="userId" width="88" />
      <el-table-column label="通道" prop="channel" width="130" />
      <el-table-column label="模型" prop="model" min-width="140" show-overflow-tooltip />
      <el-table-column label="角色ID" prop="characterId" width="88" />
      <el-table-column label="会话ID" prop="conversationId" width="88" />
      <el-table-column label="消息ID" prop="clientMessageId" min-width="150" show-overflow-tooltip />
      <el-table-column label="耗时ms" prop="durationMs" width="88" />
      <el-table-column label="成功" prop="success" width="72">
        <template #default="scope">
          <el-tag :type="scope.row.success ? 'success' : 'danger'">{{ scope.row.success ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
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

    <el-dialog v-model="detailOpen" title="AI 错误详情" width="760px" append-to-body destroy-on-close>
      <div class="ai-log-detail">
        <el-descriptions v-if="detail.id" :column="3" border size="small">
          <el-descriptions-item label="任务ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.success ? 'success' : 'danger'">{{ detail.status || (detail.success ? 'SUCCESS' : 'FAILED') }}</el-tag>
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
        </el-descriptions>

        <el-alert
          v-if="detail.success"
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
import { listJgAiLog, cleanJgAiLog } from '@/api/jiugai/ailog'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})

const successFilter = ref('')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    channel: '',
    traceId: ''
  }
})
const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  const q = {
    pageNum: queryParams.value.pageNum,
    pageSize: queryParams.value.pageSize
  }
  const ch = queryParams.value.channel
  if (ch != null && String(ch).trim() !== '') {
    q.channel = String(ch).trim()
  }
  const traceId = queryParams.value.traceId
  if (traceId != null && String(traceId).trim() !== '') {
    q.traceId = String(traceId).trim()
  }
  if (successFilter.value === 'ok') {
    q.success = true
  } else if (successFilter.value === 'fail') {
    q.success = false
  }
  listJgAiLog(q)
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
  queryParams.value.traceId = ''
  successFilter.value = ''
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
  detailOpen.value = true
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
