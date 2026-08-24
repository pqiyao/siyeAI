<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div>
        <h2>任务中心</h2>
        <p>查看 Agent 任务状态、执行进度、预算和最终产物。</p>
      </div>
      <el-button v-hasPermi="['agent:task:create']" type="primary" @click="openCreate">创建任务</el-button>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="toolbar">
        <el-form-item><el-select v-model="query.status" placeholder="全部状态" clearable style="width: 170px" @change="load"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-input v-model="query.keyword" placeholder="任务标题或目标" clearable style="width: 240px" @keyup.enter="load" /></el-form-item>
        <el-form-item><el-select v-if="tenantScopes.length" v-model="query.tenantKey" placeholder="授权租户" clearable style="width: 190px" @change="load"><el-option v-for="scope in tenantScopes" :key="scope.tenantKey" :label="scope.tenantKey" :value="scope.tenantKey" /></el-select><el-input v-else v-model="query.tenantKey" placeholder="租户 Key" clearable style="width: 170px" @keyup.enter="load" /></el-form-item>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </el-form>

      <el-table v-loading="loading" :data="rows" row-key="id" empty-text="当前没有 Agent 任务">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="任务" min-width="240">
          <template #default="scope">
            <strong>{{ scope.row.title }}</strong>
            <div class="muted">{{ scope.row.taskKey }} · {{ scope.row.taskType }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="进度" width="120"><template #default="scope">{{ scope.row.completedSteps || 0 }} / {{ scope.row.totalSteps || 0 }}</template></el-table-column>
        <el-table-column prop="riskLevel" label="风险" width="150" />
        <el-table-column prop="tenantKey" label="租户" width="150" show-overflow-tooltip />
        <el-table-column prop="tokenUsage" label="Token" width="110" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">详情</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="创建 Agent 任务" width="620px" destroy-on-close>
      <el-form :model="createForm" label-position="top">
        <el-form-item label="租户 Key"><el-input v-model="createForm.tenantKey" maxlength="120" placeholder="普通管理员填写已授权租户；平台管理员可留空" /></el-form-item>
        <el-form-item label="任务标题"><el-input v-model="createForm.title" maxlength="255" show-word-limit placeholder="例如：制作三章互动悬疑故事" /></el-form-item>
        <el-form-item label="任务目标" required><el-input v-model="createForm.objective" type="textarea" :rows="6" maxlength="20000" show-word-limit placeholder="清楚描述 Agent 需要完成的目标、范围和交付物" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="任务类型"><el-select v-model="createForm.taskType" style="width: 100%"><el-option label="通用任务" value="GENERAL" /><el-option label="内容创作" value="CONTENT_CREATION" /><el-option label="数据分析" value="DATA_ANALYSIS" /><el-option label="系统检查" value="SYSTEM_INSPECTION" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险等级"><el-select v-model="createForm.riskLevel" style="width: 100%"><el-option label="只读" value="READ_ONLY" /><el-option label="可恢复写入" value="WRITE_REVERSIBLE" /></el-select></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">创建任务</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="JgAgentTask">
import { createAgentTask, listAgentTasks } from '@/api/jiugai/agentTask'
import { listMyAgentTenantScopes } from '@/api/jiugai/agentConfig'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, status: '', keyword: '', tenantKey: '' })
const createForm = reactive({ tenantKey: '', title: '', objective: '', taskType: 'GENERAL', riskLevel: 'READ_ONLY' })
const tenantScopes = ref([])
const statuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PLANNING', label: '规划中' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'WAITING_APPROVAL', label: '等待审批' },
  { value: 'READY', label: '待执行' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'WAITING_CONFIRMATION', label: '等待确认' },
  { value: 'CANCEL_REQUESTED', label: '取消中' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'PARTIAL_SUCCESS', label: '部分成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' }
]
const statusNames = Object.fromEntries(statuses.map(item => [item.value, item.label]))

const statusLabel = (status) => statusNames[status] || status || '-'
const statusType = (status) => ({ RUNNING: 'primary', WAITING_APPROVAL: 'warning', SUCCEEDED: 'success', PARTIAL_SUCCESS: 'warning', FAILED: 'danger', CANCELLED: 'info' }[status] || 'info')

function openCreate() {
  Object.assign(createForm, { title: '', objective: '', taskType: 'GENERAL', riskLevel: 'READ_ONLY' })
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.objective.trim()) {
    proxy.$modal.msgWarning('请填写任务目标')
    return
  }
  if (tenantScopes.value.length && !String(createForm.tenantKey || '').trim()) {
    proxy.$modal.msgWarning('请选择已授权租户')
    return
  }
  saving.value = true
  try {
    await createAgentTask(createForm)
    proxy.$modal.msgSuccess('Agent 任务已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openDetail(row) {
  router.push(`/agent/task/detail/${row.id}`)
}

async function load() {
  loading.value = true
  try {
    const response = await listAgentTasks(query)
    rows.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

async function loadTenantScopes() {
  try {
    const response = await listMyAgentTenantScopes()
    tenantScopes.value = Array.isArray(response) ? response : (response.data || [])
    if (tenantScopes.value.length && !query.tenantKey) query.tenantKey = tenantScopes.value[0].tenantKey
    if (tenantScopes.value.length && !createForm.tenantKey) createForm.tenantKey = tenantScopes.value[0].tenantKey
  } catch (e) {
    tenantScopes.value = []
  }
}

onMounted(async () => { await loadTenantScopes(); await load() })
</script>

<style scoped>
.agent-page { color: #1f2937; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-heading h2 { margin: 0 0 6px; font-size: 22px; }
.page-heading p { margin: 0; color: #6b7280; }
.toolbar { margin-bottom: 6px; }
.muted { margin-top: 4px; color: #909399; font-size: 12px; }
@media (max-width: 600px) { .page-heading { flex-direction: column; } }
</style>
