<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      :title="alertTitle"
    />

    <el-tabs v-model="activeGroup" class="ledger-tabs" @tab-change="handleGroupChange">
      <el-tab-pane label="充值收益" name="REVENUE" />
      <el-tab-pane label="其他资金变动" name="OTHER" />
    </el-tabs>

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item :label="labels.keyword">
        <el-input
          v-model="queryParams.keyword"
          :placeholder="labels.keywordPh"
          clearable
          style="width: 280px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="labels.bizType">
        <el-select v-model="queryParams.bizType" :placeholder="labels.all" clearable style="width: 180px">
          <el-option
            v-for="option in currentBizTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">{{ labels.search }}</el-button>
        <el-button icon="Refresh" @click="resetQuery">{{ labels.reset }}</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" max-height="680">
      <el-table-column :label="labels.id" prop="id" width="80" />
      <el-table-column :label="labels.user" min-width="150" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.userId != null ? ('user#' + scope.row.userId) : '-' }}</div>
          <div v-if="scope.row.clientUid" class="sub-line">{{ scope.row.clientUid }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="labels.bizType" prop="bizType" width="140" show-overflow-tooltip>
        <template #default="scope">
          {{ formatBizType(scope.row.bizType) }}
        </template>
      </el-table-column>
      <el-table-column :label="labels.bizRef" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.bizRef || scope.row.orderNo || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="labels.score" width="110" align="right">
        <template #default="scope">
          <span :class="deltaClass(scope.row.deltaScore)">{{ formatDelta(scope.row.deltaScore) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="labels.gold" width="110" align="right">
        <template #default="scope">
          <span :class="deltaClass(scope.row.deltaGoldCoin)">{{ formatDelta(scope.row.deltaGoldCoin) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="labels.note" prop="note" min-width="220" show-overflow-tooltip />
      <el-table-column :label="labels.time" prop="createdAt" width="170" />
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="JgWalletLedger">
import { listWalletLedger } from '@/api/jiugai/walletledger'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const alertTitle = '资金流水按用途分开查看：充值收益用于核对用户充值入账，其他资金变动保留签到、消费和退款等完整账本。'
const labels = {
  keyword: '关键字',
  keywordPh: '用户ID / 业务单号 / 备注',
  bizType: '业务类型',
  all: '全部',
  payment: '充值入账',
  checkin: '签到奖励',
  chat: '聊天消耗',
  image: '生图消耗',
  imageRefund: '生图退款',
  tts: '语音消耗',
  ttsRefund: '语音退款',
  stt: '语音识别消耗',
  sttRefund: '语音识别退款',
  vision: '识图消耗',
  visionRefund: '识图退款',
  search: '搜索',
  reset: '重置',
  id: 'ID',
  user: '用户',
  bizRef: '业务单号',
  score: '钻石变动',
  gold: '金币变动',
  note: '备注',
  time: '时间'
}

const dataList = ref([])
const activeGroup = ref('REVENUE')
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    bizType: undefined,
    groupType: 'REVENUE'
  }
})

const { queryParams } = toRefs(data)

const currentBizTypeOptions = computed(() => {
  if (activeGroup.value === 'REVENUE') {
    return [{ label: labels.payment, value: 'PAYMENT' }]
  }
  return [
    { label: labels.checkin, value: 'CHECKIN' },
    { label: labels.chat, value: 'CHAT_CONSUME' },
    { label: labels.image, value: 'IMAGE_CONSUME' },
    { label: labels.imageRefund, value: 'IMAGE_REFUND' },
    { label: labels.tts, value: 'TTS_CONSUME' },
    { label: labels.ttsRefund, value: 'TTS_REFUND' },
    { label: labels.stt, value: 'STT_CONSUME' },
    { label: labels.sttRefund, value: 'STT_REFUND' },
    { label: labels.vision, value: 'VISION_CONSUME' },
    { label: labels.visionRefund, value: 'VISION_REFUND' }
  ]
})

function getList() {
  loading.value = true
  listWalletLedger(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载钱包流水失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function handleGroupChange(group) {
  queryParams.value.pageNum = 1
  queryParams.value.bizType = undefined
  queryParams.value.groupType = group
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.bizType = undefined
  handleQuery()
}

function formatDelta(value) {
  const n = Number(value || 0)
  if (n > 0) return '+' + n
  return String(n)
}

function formatBizType(value) {
  const labelsByType = {
    PAYMENT: labels.payment,
    CHECKIN: labels.checkin,
    CHAT_CONSUME: labels.chat,
    IMAGE_CONSUME: labels.image,
    IMAGE_REFUND: labels.imageRefund,
    TTS_CONSUME: labels.tts,
    TTS_REFUND: labels.ttsRefund,
    STT_CONSUME: labels.stt,
    STT_REFUND: labels.sttRefund,
    VISION_CONSUME: labels.vision,
    VISION_REFUND: labels.visionRefund
  }
  return labelsByType[String(value || '').toUpperCase()] || value || '-'
}

function deltaClass(value) {
  const n = Number(value || 0)
  if (n > 0) return 'delta-plus'
  if (n < 0) return 'delta-minus'
  return ''
}

getList()
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }
.ledger-tabs { margin-bottom: 12px; }
.sub-line { color: var(--el-text-color-secondary); font-size: 12px; }
.delta-plus { color: var(--el-color-success); font-weight: 600; }
.delta-minus { color: var(--el-color-danger); font-weight: 600; }
</style>
