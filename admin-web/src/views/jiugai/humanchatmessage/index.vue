<template>
  <div class="app-container social-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里治理真人聊天消息。第一版支持消息检索、详情查看、Payload 审阅，以及后台强制撤回。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="消息摘要 / 用户昵称 / 用户名"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会话 Key">
        <el-input v-model="queryParams.conversationKey" clearable placeholder="conversationKey" style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="发送方ID">
        <el-input v-model="queryParams.fromUserId" clearable placeholder="发送用户ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="接收方ID">
        <el-input v-model="queryParams.toUserId" clearable placeholder="接收用户ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.messageType" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="item in meta.messageTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="item in meta.statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
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
      <el-table-column label="消息ID" prop="messageId" width="90" />
      <el-table-column label="会话 Key" prop="conversationKey" min-width="220" show-overflow-tooltip />
      <el-table-column label="发送方" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.fromUser?.nickname || `用户${scope.row.fromUserId}` }}</div>
          <div class="sub-line">UID {{ scope.row.fromUserId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="接收方" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.toUser?.nickname || `用户${scope.row.toUserId}` }}</div>
          <div class="sub-line">UID {{ scope.row.toUserId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" prop="messageType" width="90" />
      <el-table-column label="摘要" prop="contentPreview" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'recalled' ? 'warning' : 'success'">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已读" width="80">
        <template #default="scope">
          {{ scope.row.isRead ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column label="发送时间" prop="createdAt" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button
            v-hasPermi="['social:chat-message:recall']"
            link
            type="warning"
            icon="RefreshLeft"
            :disabled="scope.row.status === 'recalled'"
            @click="handleRecall(scope.row)"
          >
            强制撤回
          </el-button>
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

    <el-dialog v-model="open" title="真人聊天消息详情" width="920px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="消息ID">{{ detail.messageId }}</el-descriptions-item>
          <el-descriptions-item label="会话 Key">{{ detail.conversationKey }}</el-descriptions-item>
          <el-descriptions-item label="发送方">{{ detail.fromUser?.nickname || `用户${detail.fromUserId}` }}</el-descriptions-item>
          <el-descriptions-item label="发送方ID">{{ detail.fromUserId }}</el-descriptions-item>
          <el-descriptions-item label="接收方">{{ detail.toUser?.nickname || `用户${detail.toUserId}` }}</el-descriptions-item>
          <el-descriptions-item label="接收方ID">{{ detail.toUserId }}</el-descriptions-item>
          <el-descriptions-item label="消息类型">{{ detail.messageType }}</el-descriptions-item>
          <el-descriptions-item label="消息状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="已读">{{ detail.isRead ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="已读时间">{{ detail.readAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="撤回时间">{{ detail.recalledAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ detail.createdAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="消息摘要" :span="2">
            <div class="pre-wrap">{{ detail.contentPreview || '--' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="Payload" :span="2">
            <pre class="json-box">{{ formatJson(detail.payload) }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgHumanChatMessage">
import { adminRecallHumanChatMessage, getHumanChatMessage, getHumanChatMessageMeta, listHumanChatMessage } from '@/api/jiugai/humanChatMessage'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const route = useRoute()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const detail = ref(null)
const meta = reactive({
  messageTypeOptions: [],
  statusOptions: []
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    conversationKey: undefined,
    fromUserId: undefined,
    toUserId: undefined,
    messageType: undefined,
    status: undefined
  }
})

const { queryParams } = toRefs(data)

function loadMeta() {
  return getHumanChatMessageMeta().then((res) => {
    meta.messageTypeOptions = res.data?.messageTypeOptions || []
    meta.statusOptions = res.data?.statusOptions || []
  })
}

function applyRouteQuery() {
  if (route.query?.conversationKey) {
    queryParams.value.conversationKey = String(route.query.conversationKey)
  }
}

function getList() {
  loading.value = true
  listHumanChatMessage(normalizeQuery(queryParams.value))
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载消息列表失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeQuery(query) {
  const next = { ...query }
  next.fromUserId = toLongOrUndefined(next.fromUserId)
  next.toUserId = toLongOrUndefined(next.toUserId)
  return next
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.conversationKey = undefined
  queryParams.value.fromUserId = undefined
  queryParams.value.toUserId = undefined
  queryParams.value.messageType = undefined
  queryParams.value.status = undefined
  handleQuery()
}

function handleView(row) {
  getHumanChatMessage(row.messageId)
    .then((res) => {
      detail.value = res.data || null
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载消息详情失败'))
    })
}

function handleRecall(row) {
  proxy.$modal
    .confirm('确认强制撤回这条消息吗？撤回后会同步推送给聊天双方。')
    .then(() => adminRecallHumanChatMessage({ messageId: row.messageId }))
    .then((res) => {
      const latest = res.data || {}
      row.status = latest.status || 'recalled'
      row.recalledAt = latest.recalledAt || row.recalledAt
      row.payload = latest.payload || row.payload
      row.contentPreview = latest.contentPreview || row.contentPreview
      if (detail.value && detail.value.messageId === row.messageId) {
        detail.value = latest
      }
      proxy.$modal.msgSuccess('强制撤回成功')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '强制撤回失败'))
    })
}

function statusLabel(value) {
  if (value === 'recalled') return '已撤回'
  return '正常'
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

applyRouteQuery()
loadMeta().finally(() => getList())
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.sub-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.pre-wrap {
  white-space: pre-wrap;
  line-height: 1.7;
}

.json-box {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}
</style>
