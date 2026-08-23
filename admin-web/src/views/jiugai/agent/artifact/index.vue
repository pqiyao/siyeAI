<template>
  <div class="app-container agent-page">
    <div class="page-heading"><div><h2>产物中心</h2><p>跨任务检索报告、JSON、HTML、媒体和 ZIP 交付物。</p></div><el-tag type="info" effect="plain">只读证据</el-tag></div>
    <el-card shadow="never"><el-form :inline="true" class="toolbar"><el-form-item><el-input v-model="query.keyword" placeholder="任务 key、产物 key 或名称" clearable style="width: 260px" @keyup.enter="load" /></el-form-item><el-form-item><el-input v-model="query.artifactType" placeholder="产物类型" clearable style="width: 150px" @keyup.enter="load" /></el-form-item><el-form-item><el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load"><el-option label="READY" value="READY" /><el-option label="FAILED" value="FAILED" /></el-select></el-form-item><el-button type="primary" :loading="loading" @click="load">查询</el-button></el-form><el-table v-loading="loading" :data="rows" empty-text="暂无产物"><el-table-column prop="taskKey" label="任务" min-width="180" /><el-table-column prop="artifactKey" label="产物 Key" min-width="180" /><el-table-column prop="artifactType" label="类型" width="150" /><el-table-column prop="name" label="名称" min-width="180" /><el-table-column prop="version" label="版本" width="80" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="sizeBytes" label="大小" width="110" /><el-table-column prop="contentHash" label="SHA-256" min-width="180" show-overflow-tooltip /><el-table-column prop="createdAt" label="创建时间" width="180" /></el-table><pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" /></el-card>
  </div>
</template>
<script setup name="JgAgentArtifact">
import { listAgentArtifacts } from '@/api/jiugai/agentConfig'
const loading = ref(false); const rows = ref([]); const total = ref(0); const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', artifactType: '', status: undefined })
async function load() { loading.value = true; try { const response = await listAgentArtifacts(query); rows.value = response.rows || []; total.value = response.total || 0 } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>
.agent-page { color: #1f2937; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.page-heading h2 { margin: 0 0 6px; font-size: 22px; }.page-heading p { margin: 0; color: #6b7280; }.toolbar { margin-bottom: 6px; }
</style>
