<template>
  <div class="app-container">
    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="目标">
        <el-select v-model="queryParams.targetType" clearable placeholder="全部" style="width: 140px">
          <el-option label="全部" value="" />
          <el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="queryParams.action" clearable placeholder="全部" style="width: 190px">
          <el-option label="全部" value="" />
          <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input
          v-model="queryParams.operator"
          placeholder="账号"
          clearable
          style="width: 150px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="目标 / 摘要"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" max-height="680">
      <el-table-column label="时间" prop="createdAt" width="170" />
      <el-table-column label="目标" width="120">
        <template #default="scope">
          <el-tag :type="targetTypeTagType(scope.row.targetType)" effect="plain">
            {{ targetTypeLabel(scope.row.targetType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="150">
        <template #default="scope">
          <el-tag :type="actionTagType(scope.row.action)" effect="light">
            {{ actionLabel(scope.row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="对象" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.targetName || scope.row.targetKey || scope.row.targetId || '-' }}</span>
          <span v-if="scope.row.targetKey" class="muted"> / {{ scope.row.targetKey }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作人" prop="operator" width="140" show-overflow-tooltip />
      <el-table-column label="摘要" prop="changeSummary" min-width="260" show-overflow-tooltip />
      <el-table-column label="操作" width="110" fixed="right">
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

    <el-drawer v-model="detailOpen" title="权限变更详情" size="min(960px, 96vw)" append-to-body destroy-on-close>
      <template v-if="detailRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="时间">{{ detailRow.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailRow.operator || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标">{{ targetTypeLabel(detailRow.targetType) }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ actionLabel(detailRow.action) }}</el-descriptions-item>
          <el-descriptions-item label="对象" :span="2">
            {{ detailRow.targetName || '-' }}
            <span v-if="detailRow.targetKey" class="muted"> / {{ detailRow.targetKey }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detailRow.changeSummary || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="snapshot-grid">
          <div class="snapshot-panel">
            <div class="snapshot-panel__title">变更前</div>
            <pre>{{ beforePretty }}</pre>
          </div>
          <div class="snapshot-panel">
            <div class="snapshot-panel__title">变更后</div>
            <pre>{{ afterPretty }}</pre>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="JgPermissionLog">
import { listAdminPermissionLog } from '@/api/jiugai/adminPermissionLog'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const detailOpen = ref(false)
const detailRow = ref(null)

const targetTypeOptions = [
  { value: 'ROLE', label: '角色' },
  { value: 'ACCOUNT', label: '账号' }
]

const actionOptions = [
  { value: 'ROLE_CREATE', label: '新增角色' },
  { value: 'ROLE_UPDATE', label: '更新角色' },
  { value: 'ROLE_STATUS', label: '角色状态' },
  { value: 'ROLE_DELETE', label: '删除角色' },
  { value: 'ACCOUNT_CREATE', label: '新增账号权限' },
  { value: 'ACCOUNT_UPDATE', label: '更新账号角色' },
  { value: 'ACCOUNT_STATUS', label: '账号状态' },
  { value: 'ACCOUNT_DELETE', label: '删除账号' }
]

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  targetType: '',
  action: '',
  operator: '',
  keyword: ''
})

const beforePretty = computed(() => prettyJson(detailRow.value?.beforeJson))
const afterPretty = computed(() => prettyJson(detailRow.value?.afterJson))

function getList() {
  loading.value = true
  listAdminPermissionLog(queryParams)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载权限变更日志失败'))
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
  queryParams.targetType = ''
  queryParams.action = ''
  queryParams.operator = ''
  queryParams.keyword = ''
  getList()
}

function openDetail(row) {
  detailRow.value = row
  detailOpen.value = true
}

function targetTypeLabel(value) {
  return targetTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

function actionLabel(value) {
  return actionOptions.find((item) => item.value === value)?.label || value || '-'
}

function targetTypeTagType(value) {
  if (value === 'ROLE') return 'primary'
  if (value === 'ACCOUNT') return 'success'
  return 'info'
}

function actionTagType(value) {
  if (String(value || '').includes('DELETE')) return 'danger'
  if (String(value || '').includes('STATUS')) return 'warning'
  if (String(value || '').includes('CREATE')) return 'success'
  return 'info'
}

function prettyJson(raw) {
  if (!raw) {
    return '{}'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch (e) {
    return raw
  }
}

getList()
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.snapshot-panel {
  min-width: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.snapshot-panel__title {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
}

.snapshot-panel pre {
  margin: 0;
  padding: 12px;
  min-height: 320px;
  max-height: 560px;
  overflow: auto;
  background: #111827;
  color: #dbeafe;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .snapshot-grid {
    grid-template-columns: 1fr;
  }
}
</style>
