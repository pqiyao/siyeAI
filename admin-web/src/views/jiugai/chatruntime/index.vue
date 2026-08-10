<template>
  <div class="app-container runtime-page">
    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </div>
    </div>

    <el-form inline class="toolbar">
      <el-form-item>
        <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
          <el-option
            v-for="status in statuses"
            :key="status"
            :label="statusLabel(status)"
            :value="status"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="query.keyword"
          clearable
          maxlength="128"
          placeholder="任务 / 用户 / 会话 / TraceId"
          style="width: 280px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-button type="primary" icon="Search" @click="search">查询</el-button>
      <el-button icon="Refresh" @click="load">刷新</el-button>
      <el-button
        v-hasPermi="['ops:chat-runtime:delete']"
        type="danger"
        plain
        icon="Delete"
        :disabled="selectedIds.length === 0 || deleting"
        :loading="deleting"
        @click="deleteSelected()"
      >批量硬删除</el-button>
    </el-form>

    <el-table v-loading="loading" :data="rows" row-key="id" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="48" align="center" :selectable="canDeleteTask" />
      <el-table-column label="任务" prop="id" width="82" />
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="tagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用户 / 会话" min-width="150">
        <template #default="scope">
          <b>#{{ scope.row.userId }}</b>
          <div class="muted">会话 {{ scope.row.conversationId }} · 角色 {{ scope.row.characterId || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="通道 / 模型" min-width="170">
        <template #default="scope">
          {{ scope.row.channel || scope.row.requestType }}
          <div class="muted">{{ scope.row.model || '默认路由' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="排队时间" prop="queuedAt" width="170" />
      <el-table-column label="耗时" width="100">
        <template #default="scope">{{ scope.row.durationMs || 0 }} ms</template>
      </el-table-column>
      <el-table-column label="错误" prop="errorMessage" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="150">
        <template #default="scope">
          <el-button
            v-if="activeStatuses.includes(scope.row.status)"
            v-hasPermi="['ops:chat-runtime:cancel']"
            link
            type="danger"
            :loading="cancellingId === scope.row.id"
            @click="cancelTask(scope.row)"
          >取消</el-button>
          <el-button
            v-if="canDeleteTask(scope.row)"
            v-hasPermi="['ops:chat-runtime:delete']"
            link
            type="danger"
            icon="Delete"
            :loading="deletingId === scope.row.id"
            @click="deleteSelected(scope.row)"
          >硬删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      :page-sizes="[10, 20, 30, 50, 100, 500, 1000]"
      @pagination="load"
    />

    <div class="worker-band">
      <div>
        <b>桥接 Worker</b>
        <span>{{ bridgeLabel }}</span>
      </div>
      <el-tag :type="bridgeTagType">{{ bridgeState }}</el-tag>
    </div>
    <div class="worker-band">
      <div>
        <b>后端运行节点</b>
        <span>{{ clusterLabel }}</span>
      </div>
      <el-tag :type="clusterDistributed ? 'success' : 'info'">
        {{ clusterDistributed ? '集群协调' : '单实例模式' }}
      </el-tag>
    </div>
  </div>
</template>

<script setup name="JgChatRuntime">
import {
  cancelChatRuntime,
  getChatRuntimeOverview,
  hardDeleteChatRuntime,
  listChatRuntime
} from '@/api/jiugai/chatRuntime'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const statuses = ['QUEUED', 'GENERATING', 'SUCCESS', 'FAILED', 'STOPPED']
const statusLabels = {
  QUEUED: '等待中',
  GENERATING: '生成中',
  SUCCESS: '成功',
  FAILED: '失败',
  STOPPED: '已停止'
}
const activeStatuses = ['QUEUED', 'GENERATING']
const loading = ref(false)
const cancellingId = ref(null)
const deleting = ref(false)
const deletingId = ref(null)
const selectedIds = ref([])
const rows = ref([])
const total = ref(0)
const overview = ref({})
const query = reactive({ pageNum: 1, pageSize: 20, status: '', keyword: '' })
let refreshTimer = null

const metrics = computed(() => [
  {
    label: '等待中',
    value: overview.value.tasks?.queued || 0,
    note: `线程池队列 ${overview.value.dispatcher?.queued || 0}`
  },
  {
    label: '运行中',
    value: overview.value.tasks?.generating || 0,
    note: `活动线程 ${overview.value.dispatcher?.active || 0}`
  },
  {
    label: '一小时失败',
    value: overview.value.tasks?.failedHour || 0,
    note: `超时 ${overview.value.tasks?.timeoutHour || 0}`
  },
  {
    label: '桥接任务',
    value: overview.value.bridge?.activeJobs || 0,
    note: `Worker ${overview.value.bridge?.onlineWorkers || 0}`
  },
  {
    label: '运行节点',
    value: overview.value.cluster?.instanceCount || 1,
    note: overview.value.cluster?.distributed ? 'Redis 全局汇总' : '当前节点数据'
  }
])

const bridgeEnabled = computed(() => overview.value.bridge?.enabled === true)
const bridgeOnline = computed(() => Number(overview.value.bridge?.onlineWorkers || 0) > 0)
const bridgeState = computed(() => !bridgeEnabled.value ? '未启用' : bridgeOnline.value ? '可用' : '离线')
const bridgeTagType = computed(() => !bridgeEnabled.value ? 'info' : bridgeOnline.value ? 'success' : 'danger')
const bridgeLabel = computed(() => !bridgeEnabled.value
  ? '当前使用服务端运行时'
  : `${overview.value.bridge?.onlineWorkers || 0} 在线`)
const clusterDistributed = computed(() => overview.value.cluster?.distributed === true)
const clusterLabel = computed(() => {
  const count = Number(overview.value.cluster?.instanceCount || 1)
  const current = overview.value.cluster?.currentInstanceId || '-'
  return `${count} 个在线 · 当前 ${current}`
})

async function load(options = {}) {
  const silent = options.silent === true
  if (!silent) loading.value = true
  try {
    const [overviewResponse, listResponse] = await Promise.all([
      getChatRuntimeOverview(),
      listChatRuntime(query)
    ])
    overview.value = overviewResponse.data || {}
    rows.value = listResponse.rows || []
    total.value = listResponse.total || 0
  } catch (error) {
    if (!silent) {
      proxy.$modal.msgError(error?.msg || error?.message || '聊天运行数据加载失败')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function tagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'GENERATING') return 'warning'
  return 'info'
}

function statusLabel(status) {
  return statusLabels[status] || status || '未知'
}

function canDeleteTask(row) {
  return row && ['SUCCESS', 'FAILED', 'STOPPED'].includes(row.status)
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map((row) => row.id)
}

async function cancelTask(row) {
  try {
    await proxy.$modal.confirm(`确认取消任务 #${row.id}？`)
  } catch (error) {
    return
  }

  cancellingId.value = row.id
  try {
    const response = await cancelChatRuntime(row.id)
    proxy.$modal.msgSuccess(response.msg || '取消请求已提交')
    await load({ silent: true })
  } catch (error) {
    proxy.$modal.msgError(error?.msg || error?.message || '取消任务失败')
  } finally {
    cancellingId.value = null
  }
}

async function deleteSelected(row) {
  const ids = row?.id ? [row.id] : [...selectedIds.value]
  if (!ids.length) {
    proxy.$modal.msgWarning('请先勾选已结束的任务')
    return
  }
  const label = row?.id ? `任务 #${row.id}` : `选中的 ${ids.length} 个任务`
  try {
    await proxy.$modal.confirm(
      `确认永久删除${label}吗？对应生成尝试、成本统计和 AI 日志记录也会删除，聊天消息不会删除。此操作不可恢复。`
    )
  } catch (error) {
    return
  }

  deleting.value = true
  deletingId.value = row?.id || null
  try {
    const response = await hardDeleteChatRuntime(ids)
    proxy.$modal.msgSuccess(response.msg || '硬删除完成')
    selectedIds.value = []
    await load({ silent: true })
  } catch (error) {
    if (!isMessageBoxCancelled(error)) {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '硬删除聊天运行任务失败'))
    }
  } finally {
    deleting.value = false
    deletingId.value = null
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = window.setInterval(() => {
    if (
      document.visibilityState === 'visible'
      && !loading.value
      && cancellingId.value == null
      && !deleting.value
      && selectedIds.value.length === 0
    ) {
      load({ silent: true })
    }
  }, 60000)
}

function stopAutoRefresh() {
  if (refreshTimer != null) window.clearInterval(refreshTimer)
  refreshTimer = null
}

onMounted(() => {
  load()
  startAutoRefresh()
})
onBeforeUnmount(stopAutoRefresh)
</script>

<style scoped>
.runtime-page { min-height: 100%; background: #f5f7f8; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric { padding: 18px 20px; background: #fff; border: 1px solid #e2e8ea; border-radius: 6px; }
.metric span, .metric small { display: block; color: #728087; }
.metric strong { display: block; margin: 8px 0; color: #173d46; font-size: 30px; }
.toolbar { padding: 14px 16px 0; background: #fff; border: 1px solid #e2e8ea; border-bottom: 0; }
.muted { margin-top: 4px; color: #8b989d; font-size: 12px; }
.worker-band { display: flex; justify-content: space-between; margin-top: 16px; padding: 16px 20px; background: #fff; border: 1px solid #e2e8ea; }
.worker-band span { margin-left: 16px; color: #7a878d; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
