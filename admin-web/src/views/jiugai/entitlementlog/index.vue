<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里记录权益配置、用户额度调整、支付到账和生图额度消耗，方便运营追踪商业化规则是否按预期生效。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="范围">
        <el-select v-model="queryParams.scopeType" clearable placeholder="全部" style="width: 160px">
          <el-option label="全部" value="" />
          <el-option label="全局策略" value="POLICY" />
          <el-option label="用户权益" value="USER" />
          <el-option label="支付到账" value="ORDER" />
          <el-option label="图片生成" value="IMAGE" />
          <el-option label="次数消耗" value="USAGE" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="queryParams.actionType" clearable placeholder="全部" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option label="策略更新" value="POLICY_UPDATED" />
          <el-option label="用户权益调整" value="USER_PROFILE_UPDATED" />
          <el-option label="账号安全" value="USER_SECURITY_UPDATED" />
          <el-option label="支付到账" value="PAYMENT_APPLIED" />
          <el-option label="图片生成" value="IMAGE_GENERATED" />
          <el-option label="聊天次数消耗" value="CHAT_QUOTA_CONSUMED" />
          <el-option label="生图次数消耗" value="IMAGE_QUOTA_CONSUMED" />
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['commerce:entitlement-log:delete', 'commerce:entitlement:edit']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" max-height="680" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
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
          <el-button v-hasPermi="['commerce:entitlement-log:delete', 'commerce:entitlement:edit']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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
import { listJgEntitlementLog, delJgEntitlementLog } from '@/api/jiugai/entitlementLog'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const route = useRoute()

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const ids = ref([])
const multiple = ref(true)
const detailOpen = ref(false)
const detailRow = ref(null)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  scopeType: '',
  actionType: '',
  targetUserId: '',
  keyword: ''
})

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
  const delIds = row?.id || ids.value.join(',')
  if (!delIds) return
  proxy.$modal
    .confirm('是否确认硬删除选中的权益日志？')
    .then(() => delJgEntitlementLog(delIds))
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
