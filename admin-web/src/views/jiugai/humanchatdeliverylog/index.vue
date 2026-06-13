<template>
  <div class="app-container social-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里查看真人聊天 WebSocket 投递日志。第一版支持事件检索、状态检索、投递体与返回体审阅。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="会话 key / channel / event / status"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="事件">
        <el-select v-model="queryParams.eventType" clearable placeholder="全部" style="width: 210px">
          <el-option v-for="item in meta.eventTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="item in meta.statusOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标用户ID">
        <el-input v-model="queryParams.targetUserId" clearable placeholder="targetUserId" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="消息ID">
        <el-input v-model="queryParams.messageId" clearable placeholder="messageId" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="日志ID" prop="id" width="88" />
      <el-table-column label="消息ID" prop="messageId" width="90" />
      <el-table-column label="会话 Key" prop="conversationKey" min-width="220" show-overflow-tooltip />
      <el-table-column label="目标用户ID" prop="targetUserId" width="110" />
      <el-table-column label="Channel" prop="channel" width="100" />
      <el-table-column label="事件" prop="eventType" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdAt" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
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

    <el-dialog v-model="open" title="投递日志详情" width="980px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="日志ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="消息ID">{{ detail.messageId || '--' }}</el-descriptions-item>
          <el-descriptions-item label="会话 Key">{{ detail.conversationKey || '--' }}</el-descriptions-item>
          <el-descriptions-item label="目标用户ID">{{ detail.targetUserId || '--' }}</el-descriptions-item>
          <el-descriptions-item label="Channel">{{ detail.channel || '--' }}</el-descriptions-item>
          <el-descriptions-item label="事件">{{ detail.eventType || '--' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="payload-section">
          <div>
            <div class="section-title">请求体</div>
            <pre class="json-box">{{ formatJson(detail.requestPayload) }}</pre>
          </div>
          <div>
            <div class="section-title">响应体</div>
            <pre class="json-box">{{ formatJson(detail.responsePayload) }}</pre>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgHumanChatDeliveryLog">
import { getHumanChatDeliveryLog, getHumanChatDeliveryLogMeta, listHumanChatDeliveryLog } from '@/api/jiugai/humanChatDeliveryLog'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const detail = ref(null)
const meta = reactive({
  statusOptions: [],
  eventTypeOptions: []
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    eventType: undefined,
    status: undefined,
    targetUserId: undefined,
    messageId: undefined
  }
})

const { queryParams } = toRefs(data)

function loadMeta() {
  return getHumanChatDeliveryLogMeta().then((res) => {
    meta.statusOptions = res.data?.statusOptions || []
    meta.eventTypeOptions = res.data?.eventTypeOptions || []
  })
}

function getList() {
  loading.value = true
  listHumanChatDeliveryLog(normalizeQuery(queryParams.value))
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载投递日志失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeQuery(query) {
  const next = { ...query }
  next.targetUserId = toLongOrUndefined(next.targetUserId)
  next.messageId = toLongOrUndefined(next.messageId)
  return next
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.eventType = undefined
  queryParams.value.status = undefined
  queryParams.value.targetUserId = undefined
  queryParams.value.messageId = undefined
  handleQuery()
}

function handleView(row) {
  getHumanChatDeliveryLog(row.id)
    .then((res) => {
      detail.value = res.data || null
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载投递日志详情失败'))
    })
}

function statusTag(value) {
  if (value === 'success') return 'success'
  if (value === 'offline') return 'info'
  if (value === 'partial') return 'warning'
  return 'danger'
}

function formatJson(value) {
  if (value == null) return '--'
  try {
    return JSON.stringify(value, null, 2)
  } catch (_) {
    return String(value)
  }
}

function toLongOrUndefined(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return undefined
  }
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

loadMeta().finally(() => getList())
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.section-title {
  margin: 18px 0 12px;
  font-size: 15px;
  font-weight: 700;
}

.payload-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.json-box {
  margin: 0;
  padding: 12px;
  min-height: 260px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}
</style>
