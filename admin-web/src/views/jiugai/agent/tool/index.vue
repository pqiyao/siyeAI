<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div>
        <h2>工具中心</h2>
        <p>管理工具版本、输入输出 Schema、风险等级和 Agent 绑定。</p>
      </div>
      <el-tag type="info" effect="plain">内置工具只读</el-tag>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="toolbar">
        <el-form-item><el-input v-model="query.keyword" placeholder="工具名称或描述" clearable style="width: 240px" @keyup.enter="load" /></el-form-item>
        <el-form-item><el-select v-model="query.enabled" placeholder="全部状态" clearable style="width: 140px" @change="load"><el-option label="已启用" :value="true" /><el-option label="已停用" :value="false" /></el-select></el-form-item>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </el-form>
      <el-table v-loading="loading" :data="rows" empty-text="当前没有注册工具">
        <el-table-column prop="toolKey" label="工具名称" min-width="220" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="category" label="分类" width="150" />
        <el-table-column prop="riskLevel" label="风险等级" width="130" />
        <el-table-column label="需要审批" width="120"><template #default="scope">{{ scope.row.approvalRequired ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">详情</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>

    <el-drawer v-model="detailVisible" title="工具详情" size="640px" destroy-on-close>
      <template v-if="selectedTool.toolKey">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工具名称">{{ selectedTool.toolKey }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedTool.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ selectedTool.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理类型">{{ selectedTool.handlerType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ selectedTool.riskLevel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="需要审批">{{ selectedTool.approvalRequired ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="超时">{{ selectedTool.timeoutSeconds ? `${selectedTool.timeoutSeconds} 秒` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="最大重试">{{ selectedTool.maxRetries ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="幂等">{{ selectedTool.idempotent ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedTool.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ selectedTool.description || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="schema-heading">允许的 Agent 角色</div>
        <pre class="schema-content">{{ formatJson(selectedTool.allowedRolesJson) }}</pre>
        <div class="schema-heading">输入 Schema</div>
        <pre class="schema-content">{{ formatJson(selectedTool.inputSchema) }}</pre>
        <div class="schema-heading">输出 Schema</div>
        <pre class="schema-content">{{ formatJson(selectedTool.outputSchema) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="JgAgentTool">
import { getAgentTool, listAgentTools } from '@/api/jiugai/agentTool'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const detailVisible = ref(false)
const selectedTool = ref({})
const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', enabled: undefined })

async function openDetail(row) {
  const response = await getAgentTool(row.id)
  selectedTool.value = response.data || row
  detailVisible.value = true
}

function formatJson(value) {
  if (!value) return '-'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) }
}

async function load() {
  loading.value = true
  try {
    const response = await listAgentTools(query)
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
.schema-heading { margin: 22px 0 8px; color: #606266; font-weight: 600; }
.schema-content { max-height: 260px; margin: 0; padding: 12px; overflow: auto; color: #303133; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 600px) { .page-heading { flex-direction: column; } }
</style>
