<template>
  <div class="app-container agent-page">
    <div class="page-heading"><div><h2>配置审计</h2><p>追踪 Agent 角色和工作流版本创建、发布与回滚。</p></div><el-tag type="info" effect="plain">只读</el-tag></div>
    <el-card shadow="never">
      <el-form :inline="true" class="toolbar"><el-form-item><el-select v-model="query.configType" placeholder="配置类型" clearable style="width: 140px" @change="load"><el-option label="角色" value="ROLE" /><el-option label="工作流" value="WORKFLOW" /></el-select></el-form-item><el-form-item><el-select v-model="query.action" placeholder="动作" clearable style="width: 140px" @change="load"><el-option label="创建版本" value="CREATE" /><el-option label="启用/回滚" value="ACTIVATE" /></el-select></el-form-item><el-form-item><el-input v-model="query.keyword" placeholder="配置 key 或摘要" clearable style="width: 240px" @keyup.enter="load" /></el-form-item><el-button type="primary" :loading="loading" @click="load">查询</el-button></el-form>
      <el-table v-loading="loading" :data="rows" empty-text="暂无配置审计记录"><el-table-column prop="createdAt" label="时间" width="180" /><el-table-column label="类型" width="100"><template #default="{ row }">{{ row.configType === 'ROLE' ? '角色' : '工作流' }}</template></el-table-column><el-table-column prop="configKey" label="配置 Key" min-width="190" /><el-table-column prop="version" label="版本" width="80" /><el-table-column label="动作" width="120"><template #default="{ row }">{{ row.action === 'CREATE' ? '创建版本' : '启用/回滚' }}</template></el-table-column><el-table-column prop="operatorId" label="操作人" width="100" /><el-table-column prop="summary" label="摘要" min-width="220" /></el-table><pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>
  </div>
</template>
<script setup name="JgAgentConfigAudit">
import { listAgentConfigAudits } from '@/api/jiugai/agentConfig'
const loading = ref(false); const rows = ref([]); const total = ref(0); const query = reactive({ pageNum: 1, pageSize: 20, configType: undefined, action: undefined, keyword: '' })
async function load() { loading.value = true; try { const response = await listAgentConfigAudits(query); rows.value = response.rows || []; total.value = response.total || 0 } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>
.agent-page { color: #1f2937; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.page-heading h2 { margin: 0 0 6px; font-size: 22px; }.page-heading p { margin: 0; color: #6b7280; }.toolbar { margin-bottom: 6px; }
</style>
