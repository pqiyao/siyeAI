<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="客服工单会汇总支付问题、账号问题、Bug 反馈和角色举报。回复后会同步通知用户。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="工单号 / 标题 / 订单号 / 角色名"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 150px">
          <el-option v-for="item in meta.statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.ticketType" clearable placeholder="全部" style="width: 150px">
          <el-option v-for="item in meta.typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="queryParams.priority" clearable placeholder="全部" style="width: 150px">
          <el-option v-for="item in meta.priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
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
      <el-table-column label="工单号" prop="ticketNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="标题" prop="subject" min-width="220" show-overflow-tooltip />
      <el-table-column label="用户" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.username || '用户' }}</div>
          <div class="sub-line">UID {{ scope.row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" prop="ticketTypeLabel" width="110" />
      <el-table-column label="优先级" prop="priorityLabel" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">
            {{ scope.row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近消息" prop="latestMessagePreview" min-width="220" show-overflow-tooltip />
      <el-table-column label="最后更新时间" prop="lastMessageAt" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">处理</el-button>
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

    <el-dialog v-model="open" title="客服工单详情" width="920px" append-to-body destroy-on-close>
      <template v-if="detail.ticket">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工单号">{{ detail.ticket.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.ticket.statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.ticket.ticketTypeLabel }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.ticket.priorityLabel }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.ticket.username || '用户' }}</el-descriptions-item>
          <el-descriptions-item label="clientUid">{{ detail.ticket.clientUidSnapshot || '--' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.ticket.subject }}</el-descriptions-item>
          <el-descriptions-item label="关联订单" :span="2">{{ detail.ticket.orderNo || '--' }}</el-descriptions-item>
          <el-descriptions-item label="关联角色" :span="2">{{ detail.ticket.characterName || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">沟通记录</div>
        <div class="message-thread">
          <div
            v-for="item in detail.messages || []"
            :key="item.id"
            class="message-card"
            :class="{ 'message-card--admin': item.senderType === 'ADMIN' }"
          >
            <div class="message-head">
              <span class="message-name">{{ item.senderName || item.senderLabel }}</span>
              <span class="message-time">{{ item.createdAt }}</span>
            </div>
            <div class="message-content">{{ item.content }}</div>
            <div v-if="item.attachments && item.attachments.length" class="message-attachments">
              <el-link
                v-for="(url, idx) in item.attachments"
                :key="url + idx"
                type="primary"
                :href="assetUrl(url)"
                target="_blank"
              >
                {{ attachmentName(url) }}
              </el-link>
            </div>
          </div>
        </div>

        <div class="section-title">状态处理</div>
        <el-form :model="statusForm" inline>
          <el-form-item label="状态">
            <el-select v-model="statusForm.nextStatus" style="width: 180px">
              <el-option v-for="item in meta.statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="statusForm.nextPriority" style="width: 160px">
              <el-option v-for="item in meta.priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button :loading="savingStatus" @click="saveStatus">保存状态</el-button>
          </el-form-item>
        </el-form>

        <div class="section-title">客服回复</div>
        <el-input
          v-model="replyForm.content"
          type="textarea"
          :rows="5"
          maxlength="5000"
          show-word-limit
          placeholder="告诉用户你已经确认了什么、下一步怎么处理、还需要对方补充什么。"
        />
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgSupportTicket">
import {
  getSupportTicket,
  getSupportTicketMeta,
  listSupportTicket,
  replySupportTicket,
  updateSupportTicketStatus
} from '@/api/jiugai/supportTicket'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const replying = ref(false)
const savingStatus = ref(false)
const detail = ref({})
const meta = reactive({
  statusOptions: [],
  typeOptions: [],
  priorityOptions: []
})

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, keyword: undefined, status: undefined, ticketType: undefined, priority: undefined },
  replyForm: { content: '' },
  statusForm: { nextStatus: 'OPEN', nextPriority: 'NORMAL' }
})

const { queryParams, replyForm, statusForm } = toRefs(data)

function loadMeta() {
  return getSupportTicketMeta().then((res) => {
    const info = res.data || {}
    meta.statusOptions = info.statusOptions || []
    meta.typeOptions = info.typeOptions || []
    meta.priorityOptions = info.priorityOptions || []
  })
}

function getList() {
  loading.value = true
  listSupportTicket(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载客服工单失败'))
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
  queryParams.value.keyword = undefined
  queryParams.value.status = undefined
  queryParams.value.ticketType = undefined
  queryParams.value.priority = undefined
  handleQuery()
}

function handleView(row) {
  getSupportTicket(row.ticketNo)
    .then((res) => {
      detail.value = res.data || {}
      statusForm.value.nextStatus = detail.value.ticket?.status || 'OPEN'
      statusForm.value.nextPriority = detail.value.ticket?.priority || 'NORMAL'
      replyForm.value.content = ''
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载工单详情失败'))
    })
}

function submitReply() {
  const ticketNo = detail.value.ticket?.ticketNo
  if (!ticketNo) return
  if (!replyForm.value.content || !replyForm.value.content.trim()) {
    proxy.$modal.msgWarning('请先填写回复内容')
    return
  }
  replying.value = true
  replySupportTicket({
    ticketNo,
    content: replyForm.value.content,
    nextStatus: statusForm.value.nextStatus,
    nextPriority: statusForm.value.nextPriority
  })
    .then((res) => {
      detail.value = res.data || detail.value
      statusForm.value.nextStatus = detail.value.ticket?.status || statusForm.value.nextStatus
      statusForm.value.nextPriority = detail.value.ticket?.priority || statusForm.value.nextPriority
      replyForm.value.content = ''
      proxy.$modal.msgSuccess('回复已发送')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '发送回复失败'))
    })
    .finally(() => {
      replying.value = false
    })
}

function saveStatus() {
  const ticketNo = detail.value.ticket?.ticketNo
  if (!ticketNo) return
  savingStatus.value = true
  updateSupportTicketStatus({
    ticketNo,
    nextStatus: statusForm.value.nextStatus,
    nextPriority: statusForm.value.nextPriority
  })
    .then((res) => {
      detail.value = res.data || detail.value
      proxy.$modal.msgSuccess('状态已更新')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存状态失败'))
    })
    .finally(() => {
      savingStatus.value = false
    })
}

function statusTagType(status) {
  if (status === 'OPEN') return 'warning'
  if (status === 'WAIT_USER') return ''
  if (status === 'RESOLVED') return 'success'
  return 'info'
}

function assetUrl(url) {
  if (!url) return '#'
  if (/^https?:\/\//.test(url)) return url
  return `${String(sillyApiBase).replace(/\/$/, '')}${url}`
}

function attachmentName(url) {
  return String(url || '').split('/').pop() || 'attachment'
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

.section-title {
  margin: 18px 0 12px;
  font-size: 15px;
  font-weight: 700;
}

.message-thread {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.message-card--admin {
  background: rgba(64, 158, 255, 0.08);
}

.message-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.message-name {
  font-weight: 700;
}

.message-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.message-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}
</style>
