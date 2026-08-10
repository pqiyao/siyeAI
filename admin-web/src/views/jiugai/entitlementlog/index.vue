<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      :title="pageAlertTitle"
    />

    <el-tabs v-model="activeGroup" class="log-tabs" @tab-change="handleGroupChange">
      <el-tab-pane label="后台权益操作" name="OPERATIONS" />
      <el-tab-pane label="功能权益消耗" name="CONSUMPTION" />
    </el-tabs>

    <el-tabs
      v-if="activeGroup === 'CONSUMPTION'"
      v-model="consumptionSource"
      type="card"
      class="source-tabs"
      @tab-change="handleConsumptionSourceChange"
    >
      <el-tab-pane label="次数额度" name="QUOTA" />
      <el-tab-pane label="钻石/金币" name="WALLET" />
    </el-tabs>

    <el-form v-if="consumptionSource !== 'WALLET' || activeGroup !== 'CONSUMPTION'" :model="queryParams" inline v-show="showSearch">
      <el-form-item label="范围">
        <el-select v-model="queryParams.scopeType" clearable placeholder="全部" style="width: 160px">
          <el-option v-for="option in scopeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="queryParams.actionType" clearable placeholder="全部" style="width: 220px">
          <el-option v-for="option in actionOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标用户">
        <el-input
          v-model="queryParams.targetUserId"
          placeholder="用户ID"
          clearable
          style="width: 120px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="用户ID / clientUid / 订单号 / 摘要 / 操作人"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-form v-else :model="walletQuery" inline v-show="showSearch">
      <el-form-item label="业务类型">
        <el-select v-model="walletQuery.bizType" clearable placeholder="全部" style="width: 190px">
          <el-option v-for="option in walletBizOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="walletQuery.keyword" placeholder="用户ID / 业务单号 / 备注" clearable style="width: 280px" @keyup.enter="handleWalletQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleWalletQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetWalletQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-if="activeGroup === 'OPERATIONS'" v-hasPermi="['commerce:entitlement-log:delete', 'commerce:entitlement:edit']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshCurrentList" />
    </el-row>

    <el-table v-if="consumptionSource !== 'WALLET' || activeGroup !== 'CONSUMPTION'" v-loading="loading" :data="dataList" max-height="680" @selection-change="handleSelectionChange">
      <el-table-column v-if="activeGroup === 'OPERATIONS'" type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="74" />
      <el-table-column label="时间" prop="createdAt" width="170" />
      <el-table-column label="范围" prop="scopeType" width="110" />
      <el-table-column label="动作" prop="actionType" width="170" />
      <el-table-column label="操作人" width="150">
        <template #default="scope">
          {{ scope.row.operatorType || '-' }} / {{ scope.row.operatorName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="目标用户" prop="targetUserId" width="100" />
      <el-table-column label="clientUid" prop="clientUid" min-width="160" show-overflow-tooltip />
      <el-table-column label="订单号" prop="orderNo" width="180" show-overflow-tooltip />
      <el-table-column label="摘要" prop="summary" min-width="280" show-overflow-tooltip />
      <el-table-column label="详情" width="170" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">查看详情</el-button>
          <el-button v-if="activeGroup === 'OPERATIONS'" v-hasPermi="['commerce:entitlement-log:delete', 'commerce:entitlement:edit']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else v-loading="walletLoading" :data="walletDataList" max-height="680">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="时间" prop="createdAt" width="170" />
      <el-table-column label="用户" prop="userId" width="110" />
      <el-table-column label="业务类型" width="150"><template #default="scope">{{ formatWalletBizType(scope.row.bizType) }}</template></el-table-column>
      <el-table-column label="业务单号" min-width="190" show-overflow-tooltip><template #default="scope">{{ scope.row.bizRef || scope.row.orderNo || '-' }}</template></el-table-column>
      <el-table-column label="钻石变动" width="110" align="right"><template #default="scope"><span :class="deltaClass(scope.row.deltaScore)">{{ formatDelta(scope.row.deltaScore) }}</span></template></el-table-column>
      <el-table-column label="金币变动" width="110" align="right"><template #default="scope"><span :class="deltaClass(scope.row.deltaGoldCoin)">{{ formatDelta(scope.row.deltaGoldCoin) }}</span></template></el-table-column>
      <el-table-column label="备注" prop="note" min-width="220" show-overflow-tooltip />
    </el-table>

    <pagination
      v-if="consumptionSource !== 'WALLET' || activeGroup !== 'CONSUMPTION'"
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :page-sizes="[10, 20, 30, 50, 100, 500, 1000]"
      @pagination="getList"
    />

    <pagination v-else v-show="walletTotal > 0" :total="walletTotal" v-model:page="walletQuery.pageNum" v-model:limit="walletQuery.pageSize" @pagination="getWalletList" />

    <el-dialog v-model="detailOpen" title="权益日志详情" width="860px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-if="detailRow">
        <el-descriptions-item label="时间">{{ detailRow.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="范围">{{ detailRow.scopeType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detailRow.actionType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailRow.operatorType || '-' }} / {{ detailRow.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标用户">{{ detailRow.targetUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="clientUid">{{ detailRow.clientUid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单号" :span="2">{{ detailRow.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ detailRow.summary || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-json">
        <div class="detail-title">detail_json</div>
        <pre>{{ prettyDetail }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="JgEntitlementLog">
import { listJgEntitlementLog, batchDelJgEntitlementLog, listEntitlementWalletConsumption } from '@/api/jiugai/entitlementLog'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const route = useRoute()
const activeGroup = ref('OPERATIONS')
const consumptionSource = ref('QUOTA')

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const ids = ref([])
const multiple = ref(true)
const detailOpen = ref(false)
const detailRow = ref(null)
const walletLoading = ref(false)
const walletTotal = ref(0)
const walletDataList = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  scopeType: '',
  actionType: '',
  targetUserId: '',
  keyword: '',
  groupType: 'OPERATIONS'
})

const walletQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '', bizType: '' })
const pageAlertTitle = computed(() => activeGroup.value === 'OPERATIONS'
  ? '记录后台权益配置和用户权益调整，便于追踪运营操作。'
  : '集中查看聊天、生图、语音和识图等功能产生的次数额度及钻石/金币消耗与退款。')
const scopeOptions = computed(() => activeGroup.value === 'OPERATIONS'
  ? [{ label: '全部', value: '' }, { label: '全局策略', value: 'POLICY' }, { label: '用户权益', value: 'USER' }]
  : [{ label: '全部', value: '' }, { label: '图片生成', value: 'IMAGE' }, { label: '次数消耗', value: 'USAGE' }])
const actionOptions = computed(() => activeGroup.value === 'OPERATIONS'
  ? [{ label: '全部', value: '' }, { label: '策略更新', value: 'POLICY_UPDATED' }, { label: '用户权益调整', value: 'USER_PROFILE_UPDATED' }, { label: '账号安全', value: 'USER_SECURITY_UPDATED' }]
  : [{ label: '全部', value: '' }, { label: '图片生成', value: 'IMAGE_GENERATED' }, { label: '聊天次数消耗', value: 'CHAT_QUOTA_CONSUMED' }, { label: '生图次数消耗', value: 'IMAGE_QUOTA_CONSUMED' }])
const walletBizOptions = [
  ['CHAT_CONSUME', '聊天消耗'], ['IMAGE_CONSUME', '生图消耗'], ['IMAGE_REFUND', '生图退款'],
  ['TTS_CONSUME', '语音合成消耗'], ['TTS_REFUND', '语音合成退款'], ['STT_CONSUME', '语音识别消耗'],
  ['STT_REFUND', '语音识别退款'], ['VISION_CONSUME', '识图消耗'], ['VISION_REFUND', '识图退款']
].map(([value, label]) => ({ value, label }))

const prettyDetail = computed(() => {
  if (!detailRow.value || !detailRow.value.detailJson) {
    return '{}'
  }
  try {
    return JSON.stringify(JSON.parse(detailRow.value.detailJson), null, 2)
  } catch (e) {
    return detailRow.value.detailJson
  }
})

function getList() {
  loading.value = true
  listJgEntitlementLog(queryParams)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载权益日志失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function handleGroupChange(group) {
  activeGroup.value = group
  queryParams.pageNum = 1
  queryParams.scopeType = ''
  queryParams.actionType = ''
  queryParams.groupType = group
  if (group === 'CONSUMPTION' && consumptionSource.value === 'WALLET') getWalletList()
  else getList()
}

function handleConsumptionSourceChange(source) {
  if (source === 'WALLET') getWalletList()
  else getList()
}

function refreshCurrentList() {
  if (activeGroup.value === 'CONSUMPTION' && consumptionSource.value === 'WALLET') getWalletList()
  else getList()
}

function getWalletList() {
  walletLoading.value = true
  listEntitlementWalletConsumption(walletQuery).then((res) => { walletDataList.value = res.rows || []; walletTotal.value = res.total || 0 })
    .catch((e) => proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载功能消耗记录失败')))
    .finally(() => { walletLoading.value = false })
}
function handleWalletQuery() { walletQuery.pageNum = 1; getWalletList() }
function resetWalletQuery() { walletQuery.pageNum = 1; walletQuery.keyword = ''; walletQuery.bizType = ''; getWalletList() }
function formatDelta(value) { const n = Number(value || 0); return n > 0 ? '+' + n : String(n) }
function deltaClass(value) { const n = Number(value || 0); return n > 0 ? 'delta-plus' : n < 0 ? 'delta-minus' : '' }
function formatWalletBizType(value) { return walletBizOptions.find((item) => item.value === value)?.label || value || '-' }

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.scopeType = ''
  queryParams.actionType = ''
  queryParams.targetUserId = ''
  queryParams.keyword = ''
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  multiple.value = !selection.length
}

function openDetail(row) {
  detailRow.value = row
  detailOpen.value = true
}

function handleDelete(row) {
  const delIds = row?.id ? [row.id] : ids.value
  if (!delIds.length) return
  proxy.$modal
    .confirm('是否确认硬删除选中的权益日志？')
    .then(() => batchDelJgEntitlementLog(delIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除权益日志失败'))
    })
}

function applyRouteQuery() {
  const query = route.query || {}
  queryParams.scopeType = typeof query.scopeType === 'string' ? query.scopeType : queryParams.scopeType
  queryParams.actionType = typeof query.actionType === 'string' ? query.actionType : queryParams.actionType
  queryParams.targetUserId = typeof query.targetUserId === 'string' ? query.targetUserId : queryParams.targetUserId
  queryParams.keyword = typeof query.keyword === 'string' ? query.keyword : queryParams.keyword
}

watch(
  () => route.query,
  () => {
    applyRouteQuery()
    handleQuery()
  }
)

applyRouteQuery()
getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}
.log-tabs { margin-bottom: 12px; }
.source-tabs { margin-bottom: 12px; }
.delta-plus { color: var(--el-color-success); font-weight: 600; }
.delta-minus { color: var(--el-color-danger); font-weight: 600; }

.detail-json {
  margin-top: 16px;
}

.detail-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-json pre {
  margin: 0;
  padding: 14px;
  border-radius: 10px;
  background: #0f172a;
  color: #dbeafe;
  max-height: 380px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
