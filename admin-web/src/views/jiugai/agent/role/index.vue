<template>
  <div class="app-container agent-page">
    <div class="page-heading">
      <div><h2>Agent 角色</h2><p>管理版本化角色、模型路由和工具白名单。</p></div>
      <el-button type="primary" @click="openCreate">创建新版本</el-button>
    </div>
    <el-card shadow="never">
      <el-form :inline="true" class="toolbar">
        <el-form-item><el-input v-model="query.keyword" placeholder="角色 key 或名称" clearable style="width: 240px" @keyup.enter="load" /></el-form-item>
        <el-form-item><el-select v-model="query.enabled" placeholder="全部状态" clearable style="width: 140px" @change="load"><el-option label="已启用" :value="true" /><el-option label="已停用" :value="false" /></el-select></el-form-item>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </el-form>
      <el-table v-loading="loading" :data="rows" empty-text="暂无 Agent 角色">
        <el-table-column prop="roleKey" label="角色 Key" min-width="180" />
        <el-table-column prop="displayName" label="名称" min-width="160" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="routeKey" label="模型路由" width="130" />
        <el-table-column prop="riskLevel" label="风险等级" width="150" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="!row.enabled" link type="warning" @click="activate(row)">启用</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>

    <el-drawer v-model="detailVisible" title="角色详情" size="680px" destroy-on-close>
      <template v-if="selected.roleKey">
        <el-descriptions :column="2" border><el-descriptions-item label="角色 Key">{{ selected.roleKey }}</el-descriptions-item><el-descriptions-item label="版本">{{ selected.version }}</el-descriptions-item><el-descriptions-item label="名称">{{ selected.displayName }}</el-descriptions-item><el-descriptions-item label="路由">{{ selected.routeKey || '-' }}</el-descriptions-item><el-descriptions-item label="Token 上限">{{ selected.maxTokens }}</el-descriptions-item><el-descriptions-item label="最大循环">{{ selected.maxLoops }}</el-descriptions-item><el-descriptions-item label="提示词">{{ selected.systemPromptPresent ? '已配置（不展示）' : '未配置' }}</el-descriptions-item><el-descriptions-item label="状态">{{ selected.enabled ? '启用' : '停用' }}</el-descriptions-item></el-descriptions>
        <div class="schema-heading">允许工具</div><pre class="schema-content">{{ formatJson(selected.allowedToolsJson) }}</pre>
        <div class="schema-heading">输入 Schema</div><pre class="schema-content">{{ formatJson(selected.inputSchema) }}</pre>
        <div class="schema-heading">输出 Schema</div><pre class="schema-content">{{ formatJson(selected.outputSchema) }}</pre>
      </template>
    </el-drawer>

    <el-dialog v-model="createVisible" title="创建 Agent 角色版本" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="125px">
        <el-form-item label="角色 Key" prop="roleKey"><el-input v-model="form.roleKey" placeholder="例如 content_creator" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt"><el-input v-model="form.systemPrompt" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="输入 Schema" prop="inputSchema"><el-input v-model="form.inputSchema" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="输出 Schema" prop="outputSchema"><el-input v-model="form.outputSchema" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="工具白名单" prop="allowedToolsJson"><el-input v-model="form.allowedToolsJson" type="textarea" :rows="2" placeholder='["analytics.query"]' /></el-form-item>
        <el-form-item label="模型路由"><el-input v-model="form.routeKey" placeholder="CHAT" /></el-form-item>
        <el-form-item label="风险等级"><el-select v-model="form.riskLevel"><el-option v-for="item in riskLevels" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">创建停用版本</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="JgAgentRole">
import { activateAgentRole, createAgentRole, getAgentRole, listAgentRoles } from '@/api/jiugai/agentConfig'
import { ElMessageBox } from 'element-plus'

const loading = ref(false); const saving = ref(false); const rows = ref([]); const total = ref(0); const detailVisible = ref(false); const createVisible = ref(false); const selected = ref({}); const formRef = ref()
const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', enabled: undefined })
const riskLevels = ['READ_ONLY', 'WRITE_REVERSIBLE', 'EXTERNAL_SIDE_EFFECT', 'DESTRUCTIVE', 'PRODUCTION_OPERATION']
const form = reactive({ roleKey: '', displayName: '', systemPrompt: '', inputSchema: '{"type":"object"}', outputSchema: '{"type":"object"}', allowedToolsJson: '[]', routeKey: 'CHAT', riskLevel: 'READ_ONLY', maxTokens: 8000, maxLoops: 2 })
const rules = { roleKey: [{ required: true, message: '请输入角色 Key', trigger: 'blur' }], displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }], systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }], inputSchema: [{ required: true, message: '请输入输入 Schema', trigger: 'blur' }], outputSchema: [{ required: true, message: '请输入输出 Schema', trigger: 'blur' }], allowedToolsJson: [{ required: true, message: '请输入工具白名单 JSON', trigger: 'blur' }] }
function formatJson(value) { if (!value) return '-'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) } }
async function load() { loading.value = true; try { const response = await listAgentRoles(query); rows.value = response.rows || []; total.value = response.total || 0 } finally { loading.value = false } }
async function openDetail(row) { const response = await getAgentRole(row.id); selected.value = response.data || row; detailVisible.value = true }
function openCreate() { Object.assign(form, { roleKey: '', displayName: '', systemPrompt: '', inputSchema: '{"type":"object"}', outputSchema: '{"type":"object"}', allowedToolsJson: '[]', routeKey: 'CHAT', riskLevel: 'READ_ONLY', maxTokens: 8000, maxLoops: 2 }); createVisible.value = true }
async function submit() { await formRef.value.validate(); saving.value = true; try { await createAgentRole({ ...form }); createVisible.value = false; await load() } finally { saving.value = false } }
async function activate(row) { await ElMessageBox.confirm(`确认启用 ${row.roleKey} v${row.version}？同 key 的其他版本会停用。`, '版本切换', { type: 'warning' }); await activateAgentRole(row.id); await load() }
onMounted(load)
</script>

<style scoped>
.agent-page { color: #1f2937; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.page-heading h2 { margin: 0 0 6px; font-size: 22px; }.page-heading p { margin: 0; color: #6b7280; }.toolbar { margin-bottom: 6px; }.schema-heading { margin: 22px 0 8px; color: #606266; font-weight: 600; }.schema-content { max-height: 220px; margin: 0; padding: 12px; overflow: auto; color: #303133; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px; white-space: pre-wrap; word-break: break-word; }
</style>
