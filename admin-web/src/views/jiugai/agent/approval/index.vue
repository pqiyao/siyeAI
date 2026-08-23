<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div>
        <h2>审批中心</h2>
        <p>集中处理计划审批和需要人工确认的工具动作。</p>
      </div>
      <el-tag type="success" effect="plain">实时查询</el-tag>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="toolbar">
        <el-form-item><el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="load"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-input v-model="query.keyword" placeholder="类型、目标或原因" clearable style="width: 240px" @keyup.enter="load" /></el-form-item>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </el-form>
      <el-table v-loading="loading" :data="rows" empty-text="当前没有可展示的审批记录">
        <el-table-column prop="id" label="审批 ID" width="120" />
        <el-table-column label="类型" width="150"><template #default="scope">{{ approvalTypeLabel(scope.row.approvalType) }}</template></el-table-column>
        <el-table-column prop="targetSummary" label="目标摘要" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="approvalType(scope.row.status)">{{ approvalLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="180" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openTask(scope.row)">查看任务</el-button>
            <template v-if="scope.row.status === 'PENDING'">
              <el-button link type="success" @click="approve(scope.row)">通过</el-button>
              <el-button link type="danger" @click="reject(scope.row)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>
  </div>
</template>

<script setup name="JgAgentApproval">
import { approveAgentApproval, listAgentApprovals, rejectAgentApproval } from '@/api/jiugai/agentApproval'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, status: '', keyword: '' })
const statuses = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'CANCELLED', label: '已取消' }
]
const statusNames = Object.fromEntries(statuses.map(item => [item.value, item.label]))
const approvalLabel = (status) => statusNames[status] || status || '-'
const approvalType = (status) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info', CANCELLED: 'info' }[status] || 'info')
const approvalTypeLabel = (type) => ({ PLAN: '计划审批' }[type] || type || '-')
const router = useRouter()

function openTask(row) {
  if (row.taskId) router.push(`/agent/task/detail/${row.taskId}`)
}

async function approve(row) {
  try {
    await ElMessageBox.confirm('批准后任务将进入执行队列。', '批准计划', { type: 'warning' })
    await approveAgentApproval(row.id, { decisionComment: '后台审批中心批准计划' })
    ElMessage.success('计划已批准')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审批失败')
  }
}

async function reject(row) {
  try {
    const result = await ElMessageBox.prompt('拒绝后任务回到草稿，可重新生成计划。', '拒绝计划', {
      inputPlaceholder: '请输入拒绝原因',
      inputValidator: value => Boolean(value && value.trim()) || '请输入拒绝原因',
      type: 'warning'
    })
    await rejectAgentApproval(row.id, { decisionComment: result.value.trim() })
    ElMessage.success('计划已拒绝')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '拒绝失败')
  }
}

async function load() {
  loading.value = true
  try {
    const response = await listAgentApprovals(query)
    rows.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.agent-page { color: #1f2937; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-heading h2 { margin: 0 0 6px; font-size: 22px; }
.page-heading p { margin: 0; color: #6b7280; }
.toolbar { margin-bottom: 6px; }
@media (max-width: 600px) { .page-heading { flex-direction: column; } }
</style>
