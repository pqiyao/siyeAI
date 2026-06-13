<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里记录角色卡自动初筛、人工审核、批量处理和驳回原因，方便运营追踪审核质量与处理结果。"
    />

    <div class="summary-grid" v-loading="summaryLoading">
      <div class="summary-card">
        <div class="summary-num">{{ summary.total || 0 }}</div>
        <div class="summary-label">总台账</div>
      </div>
      <div class="summary-card">
        <div class="summary-num">{{ summary.pending || 0 }}</div>
        <div class="summary-label">待审核</div>
      </div>
      <div class="summary-card">
        <div class="summary-num">{{ summary.approved || 0 }}</div>
        <div class="summary-label">已通过</div>
      </div>
      <div class="summary-card">
        <div class="summary-num">{{ summary.rejected || 0 }}</div>
        <div class="summary-label">已驳回</div>
      </div>
      <div class="summary-card">
        <div class="summary-num">{{ summary.autoScreened || 0 }}</div>
        <div class="summary-label">自动初筛</div>
      </div>
      <div class="summary-card">
        <div class="summary-num">{{ summary.highRisk || 0 }}</div>
        <div class="summary-label">高风险命中</div>
      </div>
    </div>

    <div class="flag-panel" v-if="summary.topFlags && summary.topFlags.length">
      <span class="flag-panel-title">高频风险标签</span>
      <el-tag
        v-for="item in summary.topFlags"
        :key="item.flag"
        class="flag-tag"
        type="danger"
        effect="plain"
      >
        {{ item.flag }} · {{ item.count }}
      </el-tag>
    </div>

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.reviewStatus" clearable placeholder="全部状态" style="width: 180px">
          <el-option label="全部状态" value="" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="角色名 / Owner UID / 审核人 / 批次号 / 风险标签"
          clearable
          style="width: 340px"
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
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" max-height="700" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="74" />
      <el-table-column label="时间" prop="createdAt" width="170" />
      <el-table-column label="事件" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.eventType === 'AUTO_SCREEN' ? 'warning' : 'primary'">
            {{ scope.row.eventType === 'AUTO_SCREEN' ? '自动初筛' : '人工审核' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色名称" prop="characterName" min-width="160" show-overflow-tooltip />
      <el-table-column label="审核状态" width="110">
        <template #default="scope">
          <el-tag :type="reviewStatusTagType(scope.row.reviewStatus)">
            {{ reviewStatusLabel(scope.row.reviewStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险等级" width="110">
        <template #default="scope">
          <el-tag :type="screeningTagType(scope.row.screeningLevel)">
            {{ screeningLabel(scope.row.screeningLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Owner UID" prop="ownerClientUid" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作人" prop="operatorName" width="120" show-overflow-tooltip />
      <el-table-column label="批次号" prop="batchNo" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.batchNo || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="命中标签" prop="screeningFlags" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.screeningFlags || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="摘要" prop="summary" min-width="240" show-overflow-tooltip />
      <el-table-column label="驳回原因" prop="reviewReason" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.reviewReason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="详情" width="170" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">查看详情</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog v-model="detailOpen" title="角色审核详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-if="detailRow">
        <el-descriptions-item label="时间">{{ detailRow.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件">{{ detailRow.eventType === 'AUTO_SCREEN' ? '自动初筛' : '人工审核' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ reviewStatusLabel(detailRow.reviewStatus) }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">{{ screeningLabel(detailRow.screeningLevel) }}</el-descriptions-item>
        <el-descriptions-item label="角色名称">{{ detailRow.characterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色 ID">{{ detailRow.characterId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Owner UID">{{ detailRow.ownerClientUid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailRow.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号" :span="2">{{ detailRow.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="命中标签" :span="2">{{ detailRow.screeningFlags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核摘要" :span="2">{{ detailRow.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因" :span="2">{{ detailRow.reviewReason || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-json">
        <div class="detail-title">detail_json</div>
        <pre>{{ prettyDetail }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="JgCharacterReviewLog">
import { getCurrentInstance, ref, reactive, computed } from 'vue'
import { listJgCharacterReviewLog, summaryJgCharacterReviewLog, delJgCharacterReviewLog } from '@/api/jiugai/characterReviewLog'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const summaryLoading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const ids = ref([])
const multiple = ref(true)
const detailOpen = ref(false)
const detailRow = ref(null)
const summary = ref({
  total: 0,
  pending: 0,
  approved: 0,
  rejected: 0,
  autoScreened: 0,
  manualReviewed: 0,
  mediumRisk: 0,
  highRisk: 0,
  topFlags: []
})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  reviewStatus: '',
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

function reviewStatusLabel(status) {
  if (status === 'PENDING') return '待审核'
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  return '未知'
}

function reviewStatusTagType(status) {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function screeningLabel(level) {
  if (level === 'HIGH') return '高风险'
  if (level === 'MEDIUM') return '中风险'
  if (level === 'LOW') return '低风险'
  return '无风险'
}

function screeningTagType(level) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return ''
  return 'info'
}

function getList() {
  loading.value = true
  listJgCharacterReviewLog(queryParams)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色审核台账失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function getSummary() {
  summaryLoading.value = true
  summaryJgCharacterReviewLog()
    .then((res) => {
      summary.value = res.data || summary.value
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载审核统计失败'))
    })
    .finally(() => {
      summaryLoading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.reviewStatus = ''
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
    .confirm('是否确认硬删除选中的审核日志？')
    .then(() => delJgCharacterReviewLog(delIds))
    .then(() => {
      getList()
      getSummary()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除审核日志失败'))
    })
}

getSummary()
getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #101827 0%, #1f2937 100%);
  color: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14);
}

.summary-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.summary-label {
  margin-top: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
}

.flag-panel {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.flag-panel-title {
  display: inline-block;
  margin-right: 12px;
  font-weight: 600;
  color: #334155;
}

.flag-tag {
  margin-right: 8px;
  margin-bottom: 6px;
}

.mb8 {
  margin-bottom: 8px;
}

.detail-json {
  margin-top: 16px;
  padding: 14px;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
}

.detail-title {
  margin-bottom: 10px;
  font-weight: 600;
}

.detail-json pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}
</style>
