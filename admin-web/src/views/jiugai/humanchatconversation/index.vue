<template>
  <div class="app-container social-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里查看真人聊天会话。第一版支持会话检索、双方用户查看、最后消息摘要查看，以及进入消息治理。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="会话 key / 用户昵称 / 用户名"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="queryParams.userId" clearable placeholder="会话用户ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="对方ID">
        <el-input v-model="queryParams.peerUserId" clearable placeholder="对端用户ID" style="width: 150px" @keyup.enter="handleQuery" />
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
      <el-table-column label="会话ID" prop="conversationId" width="90" />
      <el-table-column label="会话 Key" prop="conversationKey" min-width="240" show-overflow-tooltip />
      <el-table-column label="用户 A" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.user?.nickname || `用户${scope.row.userId}` }}</div>
          <div class="sub-line">UID {{ scope.row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="用户 B" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.peerUser?.nickname || `用户${scope.row.peerUserId}` }}</div>
          <div class="sub-line">UID {{ scope.row.peerUserId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="最后消息" prop="lastMessagePreview" min-width="220" show-overflow-tooltip />
      <el-table-column label="消息类型" prop="lastMessageType" width="100" />
      <el-table-column label="未读数" prop="unreadCount" width="88" />
      <el-table-column label="最后消息时间" prop="lastMessageAt" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button link type="primary" icon="ChatLineSquare" @click="handleJumpMessages(scope.row)">消息</el-button>
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

    <el-dialog v-model="open" title="真人聊天会话详情" width="920px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="会话ID">{{ detail.conversationId }}</el-descriptions-item>
          <el-descriptions-item label="会话 Key">{{ detail.conversationKey }}</el-descriptions-item>
          <el-descriptions-item label="用户 A">{{ detail.user?.nickname || `用户${detail.userId}` }}</el-descriptions-item>
          <el-descriptions-item label="A 用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户 B">{{ detail.peerUser?.nickname || `用户${detail.peerUserId}` }}</el-descriptions-item>
          <el-descriptions-item label="B 用户ID">{{ detail.peerUserId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status || '--' }}</el-descriptions-item>
          <el-descriptions-item label="未读数">{{ detail.unreadCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="最后消息ID">{{ detail.lastMessageId || '--' }}</el-descriptions-item>
          <el-descriptions-item label="最后消息类型">{{ detail.lastMessageType || '--' }}</el-descriptions-item>
          <el-descriptions-item label="最后消息时间">{{ detail.lastMessageAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="最后消息摘要" :span="2">
            <div class="pre-wrap">{{ detail.lastMessagePreview || '--' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="最后消息 Payload" :span="2">
            <pre class="json-box">{{ formatJson(detail.lastMessagePayload) }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgHumanChatConversation">
import { getHumanChatConversation, getHumanChatConversationMeta, listHumanChatConversation } from '@/api/jiugai/humanChatConversation'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const detail = ref(null)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    userId: undefined,
    peerUserId: undefined
  }
})

const { queryParams } = toRefs(data)

function loadMeta() {
  return getHumanChatConversationMeta()
}

function getList() {
  loading.value = true
  listHumanChatConversation(normalizeQuery(queryParams.value))
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载会话列表失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeQuery(query) {
  const next = { ...query }
  next.userId = toLongOrUndefined(next.userId)
  next.peerUserId = toLongOrUndefined(next.peerUserId)
  return next
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.userId = undefined
  queryParams.value.peerUserId = undefined
  handleQuery()
}

function handleView(row) {
  getHumanChatConversation(row.conversationId)
    .then((res) => {
      detail.value = res.data || null
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载会话详情失败'))
    })
}

function handleJumpMessages(row) {
  proxy.$router.push({
    path: '/jiugai/humanchatmessage',
    query: { conversationKey: row.conversationKey }
  })
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
