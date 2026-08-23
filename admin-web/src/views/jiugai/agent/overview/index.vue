<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div>
        <h2>智能体总览</h2>
        <p>统一查看 Agent 任务、审批、工具调用和交付产物。</p>
      </div>
      <el-tag v-loading="loading" type="success" effect="plain">实时查询</el-tag>
    </div>

    <div v-loading="loading" class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-title"><span>任务执行状态</span><el-tag size="small" type="info">当前数据</el-tag></div>
          </template>
          <div class="status-list">
            <div v-for="item in statusItems" :key="item.label" class="status-item">
              <span class="status-dot" :class="`status-dot--${item.type}`" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title"><span>首批能力</span><el-tag size="small" type="success">已规划</el-tag></div></template>
          <div class="capability-list">
            <div v-for="item in capabilities" :key="item.name">
              <strong>{{ item.name }}</strong>
              <span>{{ item.description }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title"><span>平台告警</span><el-tag size="small" :type="alertTagType">{{ alerts.length }} 条</el-tag></div></template>
          <el-empty v-if="!alerts.length" description="暂无告警" :image-size="52" />
          <div v-else class="alert-list">
            <div v-for="item in alerts" :key="`${item.code}-${item.message}`" class="alert-item">
              <el-tag size="small" :type="alertType(item.severity)">{{ item.severity }}</el-tag>
              <div><strong>{{ item.code }}</strong><p>{{ item.message }}</p></div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header><div class="panel-title"><span>工具与外部连接</span><el-tag size="small" type="info">只读状态</el-tag></div></template>
          <div class="readiness-grid">
            <div v-for="item in readinessItems" :key="item.label" class="readiness-item">
              <span>{{ item.label }}</span><el-tag size="small" :type="item.ready ? 'success' : 'warning'">{{ item.ready ? '已配置' : '未配置' }}</el-tag>
            </div>
          </div>
          <p class="panel-note">状态仅表示配置就绪，不代表已通过真实供应商验收；执行仍受白名单、风险和审批约束。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="JgAgentOverview">
import { getAgentOverview } from '@/api/jiugai/agentTask'

const loading = ref(false)
const overview = ref({})
const alerts = computed(() => Array.isArray(overview.value.alerts) ? overview.value.alerts : [])
const alertTagType = computed(() => alerts.value.some(item => item.severity === 'CRITICAL') ? 'danger' : alerts.value.some(item => item.severity === 'WARNING') ? 'warning' : 'success')
const alertType = (severity) => severity === 'CRITICAL' ? 'danger' : severity === 'WARNING' ? 'warning' : 'info'
const readinessItems = computed(() => {
  const external = overview.value.externalReadiness || {}
  const tools = overview.value.toolReadiness || {}
  return [
    { label: '启用工具', ready: Number(tools.enabledTools || 0) > 0 },
    { label: 'API 请求', ready: !!external.apiRequest },
    { label: 'Web Search', ready: !!external.webSearch },
    { label: 'Browser', ready: !!external.browser },
    { label: 'MCP', ready: !!external.mcp },
    { label: 'Code Runner', ready: !!external.codeRunner }
  ]
})

const metrics = computed(() => [
  { label: '今日任务', value: overview.value.todayTasks ?? 0, note: `累计任务 ${overview.value.totalTasks ?? 0}` },
  { label: '运行中', value: overview.value.runningTasks ?? 0, note: `运行中工具 ${overview.value.runningToolCalls ?? 0}` },
  { label: '待审批', value: overview.value.pendingApprovals ?? 0, note: '需要人工确认的动作' },
  { label: '可用产物', value: overview.value.readyArtifacts ?? 0, note: `启用工具 ${overview.value.enabledTools ?? 0}` },
  { label: 'Token 消耗', value: overview.value.totalTokenUsage ?? 0, note: 'Agent 任务累计用量' },
  { label: '费用', value: overview.value.totalCostAmount ?? 0, note: '累计 USD，0 表示暂无计价' }
])

const statusItems = computed(() => [
  { label: '任务总数', value: overview.value.totalTasks ?? 0, type: 'info' },
  { label: '等待审批', value: overview.value.waitingApprovalTasks ?? 0, type: 'warning' },
  { label: '执行中', value: overview.value.runningTasks ?? 0, type: 'primary' },
  { label: '成功 / 部分成功', value: overview.value.succeededTasks ?? 0, type: 'success' },
  { label: '失败', value: overview.value.failedTasks ?? 0, type: 'danger' }
])

const capabilities = [
  { name: '任务中心', description: '统一查看任务、步骤和公开事件。' },
  { name: '审批中心', description: '集中处理计划和高风险动作确认。' },
  { name: '工具中心', description: '管理工具 Schema、权限和启用状态。' },
  { name: '内容创作闭环', description: '已接入角色、世界书、剧情、一致性检查、生图、配音和作品打包。' }
]

async function load() {
  loading.value = true
  try {
    const response = await getAgentOverview()
    overview.value = response.data || {}
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
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.metric-card { min-height: 108px; padding: 18px; background: #fff; border: 1px solid #ebeef5; border-radius: 6px; }
.metric-card span, .metric-card small { display: block; color: #909399; }
.metric-card strong { display: block; margin: 8px 0 4px; font-size: 28px; }
.panel-card { min-height: 260px; margin-bottom: 16px; }
.panel-title { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
.status-list, .capability-list { display: grid; gap: 14px; }
.status-item { display: flex; align-items: center; gap: 10px; }
.status-item strong { margin-left: auto; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #909399; }
.status-dot--warning { background: #e6a23c; }
.status-dot--primary { background: #409eff; }
.status-dot--success { background: #67c23a; }
.status-dot--danger { background: #f56c6c; }
.capability-list div { display: flex; flex-direction: column; gap: 4px; }
.capability-list span { color: #6b7280; font-size: 13px; }
.alert-list { display: grid; gap: 12px; }
.alert-item { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f0f2f5; }
.alert-item:last-child { border-bottom: 0; }
.alert-item strong { font-size: 13px; }
.alert-item p { margin: 4px 0 0; color: #6b7280; font-size: 13px; }
.readiness-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.readiness-item { display: flex; align-items: center; justify-content: space-between; padding: 10px; background: #f8fafc; border-radius: 4px; }
.panel-note { margin: 16px 0 0; color: #909399; font-size: 12px; line-height: 1.5; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .metric-grid { grid-template-columns: 1fr; } .page-heading { flex-direction: column; } }
</style>
