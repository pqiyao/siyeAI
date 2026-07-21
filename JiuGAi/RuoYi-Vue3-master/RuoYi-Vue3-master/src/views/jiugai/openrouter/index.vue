<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="上半区会直接读取并写回 ST settings；下半区模型路由用于正式聊天的 provider/model 切换与熔断。"
    />

    <el-alert
      v-if="samplerForm.stLinked === false"
      class="mb12"
      type="warning"
      :closable="false"
      show-icon
      :title="samplerForm.stError || '未能读取 ST settings，当前展示的是本地兜底参数。'"
    />

    <el-card class="mb12" shadow="never">
      <template #header>
        <div class="card-head">
          <span>ST 默认模型与采样参数（直连 ST settings）</span>
          <el-button v-hasPermi="['ops:openrouter:edit']" link type="primary" :loading="savingSampler" @click="submitSampler">保存</el-button>
        </div>
      </template>

      <el-form :model="samplerForm" label-width="180px" class="jg-form">
        <el-form-item label="chat source">
          <el-select v-model="samplerForm.chatCompletionSource" style="width: 100%">
            <el-option v-for="item in sourceOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <div class="form-tip">{{ hint.chatCompletionSource }}</div>
        </el-form-item>
        <el-form-item label="默认模型 ID">
          <el-input v-model="samplerForm.defaultModel" placeholder="例如 deepseek/deepseek-chat" />
          <div class="form-tip">{{ hint.defaultModel }}</div>
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number v-model="samplerForm.defaultTemperature" :min="0" :max="2" :step="0.05" :precision="2" />
          <div class="form-tip">{{ hint.defaultTemperature }}</div>
        </el-form-item>
        <el-form-item label="max tokens">
          <el-input-number v-model="samplerForm.defaultMaxOutputTokens" :min="0" :max="128000" :step="64" />
          <div class="form-tip">{{ hint.defaultMaxOutputTokens }}</div>
        </el-form-item>
        <el-form-item label="解锁上下文长度">
          <el-switch v-model="samplerForm.maxContextUnlocked" />
          <div class="form-tip">{{ hint.maxContextUnlocked }}</div>
        </el-form-item>
        <el-form-item label="AI 可见最大上下文长度">
          <el-input-number v-model="samplerForm.openaiMaxContext" :min="0" :max="2000000" :step="1024" />
          <div class="form-tip">{{ hint.openaiMaxContext }}</div>
        </el-form-item>
        <el-form-item label="top_p">
          <el-input-number v-model="samplerForm.topP" :min="0" :max="1" :step="0.05" :precision="2" />
          <div class="form-tip">{{ hint.topP }}</div>
        </el-form-item>
        <el-form-item label="frequency_penalty">
          <el-input-number v-model="samplerForm.frequencyPenalty" :min="-2" :max="2" :step="0.05" :precision="2" />
          <div class="form-tip">{{ hint.frequencyPenalty }}</div>
        </el-form-item>
        <el-form-item label="presence_penalty">
          <el-input-number v-model="samplerForm.presencePenalty" :min="-2" :max="2" :step="0.05" :precision="2" />
          <div class="form-tip">{{ hint.presencePenalty }}</div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mb12" shadow="never">
      <template #header>
        <div class="card-head">
          <span>正式聊天当前优先</span>
        </div>
      </template>

      <div class="effective-box">
        <div class="effective-main">
          <div class="effective-title">{{ effectiveStatus.title }}</div>
          <div class="effective-model">{{ effectiveStatus.model }}</div>
        </div>
        <div class="effective-meta">
          <div>ST source：{{ effectiveStatus.source }}</div>
          <div>provider：{{ effectiveStatus.provider }}</div>
          <div>route：{{ effectiveStatus.route }}</div>
        </div>
        <div class="form-tip">{{ effectiveStatus.tip }}</div>
      </div>
    </el-card>

    <el-card class="mb12" shadow="never">
      <template #header>
        <div class="card-head">
          <span>模型供应商池</span>
          <el-button v-hasPermi="['ops:openrouter:edit']" type="primary" @click="openProviderDialog()">新增供应商</el-button>
        </div>
      </template>

      <div class="legacy-box mb12">
        <div>当前系统兜底 source：{{ routing.legacy?.source || '--' }}</div>
        <div>当前系统兜底模型：{{ routing.legacy?.defaultModel || '--' }}</div>
      </div>

      <el-table :data="routing.providers || []" border>
        <el-table-column prop="providerKey" label="Key" min-width="130" />
        <el-table-column prop="displayName" label="名称" min-width="150" />
        <el-table-column prop="stSource" label="ST Source" min-width="120" />
        <el-table-column prop="modelName" label="模型" min-width="220" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="providerStatusType(row)">{{ providerStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="熔断到期" width="180">
          <template #default="{ row }">
            <span>{{ row.circuitOpenUntil || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" min-width="220">
          <template #default="{ row }">
            <span class="muted">{{ row.lastError || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['ops:openrouter:edit']" link type="primary" @click="openProviderDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['ops:openrouter:delete', 'ops:openrouter:edit']" link type="danger" @click="removeProvider(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>正式聊天模型路由</span>
          <el-button v-hasPermi="['ops:openrouter:edit']" type="primary" @click="openRouteDialog()">新增路由</el-button>
        </div>
      </template>

      <el-table :data="routing.routes || []" border>
        <el-table-column prop="sceneKey" label="Scene Key" min-width="140" />
        <el-table-column prop="displayName" label="名称" min-width="160" />
        <el-table-column prop="primaryProviderKey" label="主供应商" min-width="140" />
        <el-table-column label="Fallback" min-width="220">
          <template #default="{ row }">
            <span>{{ row.fallbackProviderKeys || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['ops:openrouter:edit']" link type="primary" @click="openRouteDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['ops:openrouter:delete', 'ops:openrouter:edit']" link type="danger" @click="removeRoute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="providerDialogVisible" :title="providerDialogTitle" width="760px">
      <el-form :model="providerForm" label-width="120px">
        <el-form-item label="Provider Key">
          <el-input v-model="providerForm.providerKey" placeholder="例如 openrouter_main" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="providerForm.displayName" placeholder="例如 OpenRouter 主路由" />
        </el-form-item>
        <el-form-item label="ST Source">
          <el-select v-model="providerForm.stSource" style="width: 100%">
            <el-option v-for="item in sourceOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="providerForm.modelName" placeholder="例如 deepseek/deepseek-chat" />
        </el-form-item>
        <el-form-item label="Reverse Proxy">
          <el-input v-model="providerForm.reverseProxy" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="Proxy Password">
          <el-input v-model="providerForm.proxyPassword" placeholder="可留空" show-password />
        </el-form-item>
        <el-form-item label="Custom URL">
          <el-input v-model="providerForm.customUrl" placeholder="仅 custom source 需要" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="providerForm.priority" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="失败阈值">
          <el-input-number v-model="providerForm.failureThreshold" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="熔断秒数">
          <el-input-number v-model="providerForm.cooldownSeconds" :min="30" :max="3600" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="providerForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="providerForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button v-hasPermi="['ops:openrouter:edit']" type="primary" :loading="savingProvider" @click="submitProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="routeDialogVisible" :title="routeDialogTitle" width="720px">
      <el-form :model="routeForm" label-width="120px">
        <el-form-item label="Scene Key">
          <el-input v-model="routeForm.sceneKey" placeholder="例如 default_chat" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="routeForm.displayName" placeholder="例如 默认聊天" />
        </el-form-item>
        <el-form-item label="主供应商">
          <el-select v-model="routeForm.primaryProviderKey" style="width: 100%">
            <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Fallback 链">
          <el-select v-model="routeForm.fallbackProviderKeys" multiple collapse-tags style="width: 100%">
            <el-option v-for="item in fallbackOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="routeForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="routeForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button v-hasPermi="['ops:openrouter:edit']" type="primary" :loading="savingRoute" @click="submitRoute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgOpenRouterGeneration">
import {
  deleteModelProvider,
  deleteModelRoute,
  getOpenRouterGeneration,
  saveModelProvider,
  saveModelRoute,
  updateOpenRouterGeneration
} from '@/api/jiugai/openrouterGeneration'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const hint = reactive({
  chatCompletionSource: '这里会直接写入 ST settings 的 chat_completion_source。',
  defaultModel: '这里会直接写入 ST 当前 source 对应的模型字段；下方模型路由仍会覆盖正式聊天的 provider/model。',
  defaultTemperature: '控制回复发散度。数值越高越活跃，越低越稳定。',
  defaultMaxOutputTokens: '单次回复最大输出长度。填 0 表示不主动覆盖。',
  maxContextUnlocked: '对应 ST 的 max_context_unlocked。开启后允许填写更大的上下文长度。',
  openaiMaxContext: '对应 ST 的 openai_max_context，表示 AI 可见的最大上下文长度。',
  topP: '控制采样范围。通常与 temperature 配合使用。',
  frequencyPenalty: '降低重复词频，越高越不容易重复用词。',
  presencePenalty: '鼓励模型引入新内容，越高越容易跳出已说过的话题。'
})
const routing = reactive({
  providers: [],
  routes: [],
  legacy: {},
  defaultSceneKey: 'default_chat'
})

const samplerForm = reactive({
  chatCompletionSource: 'openrouter',
  defaultModel: '',
  defaultTemperature: 0.85,
  defaultMaxOutputTokens: 2048,
  maxContextUnlocked: false,
  openaiMaxContext: 0,
  topP: 1,
  frequencyPenalty: 0,
  presencePenalty: 0,
  stLinked: true,
  stError: ''
})

const providerDialogVisible = ref(false)
const routeDialogVisible = ref(false)
const savingSampler = ref(false)
const savingProvider = ref(false)
const savingRoute = ref(false)

const providerForm = reactive(defaultProviderForm())
const routeForm = reactive(defaultRouteForm())

const sourceOptions = [
  'openrouter', 'openai', 'custom', 'claude', 'deepseek', 'groq',
  'xai', 'mistralai', 'cohere', 'perplexity', 'vertexai',
  'makersuite', 'fireworks', 'moonshot', 'siliconflow', 'azure_openai'
]

const providerDialogTitle = computed(() => (providerForm.id ? '编辑供应商' : '新增供应商'))
const routeDialogTitle = computed(() => (routeForm.id ? '编辑路由' : '新增路由'))
const providerOptions = computed(() => (routing.providers || []).map((item) => ({
  label: `${item.displayName} (${item.providerKey})`,
  value: item.providerKey
})))
const fallbackOptions = computed(() => providerOptions.value.filter((item) => item.value !== routeForm.primaryProviderKey))
const effectiveStatus = computed(() => {
  const defaultModel = samplerForm.defaultModel || '--'
  const defaultSource = samplerForm.chatCompletionSource || '--'
  const providers = Array.isArray(routing.providers) ? routing.providers : []
  const routes = Array.isArray(routing.routes) ? routing.routes : []
  const route = routes.find((item) => item?.sceneKey === (routing.defaultSceneKey || 'default_chat') && item?.enabled)
  if (!route) {
    return {
      title: '当前直接使用 ST 默认模型',
      model: defaultModel,
      source: defaultSource,
      provider: '--',
      route: '未配置 default_chat 路由',
      tip: '如果下方没有启用 default_chat 模型路由，正式聊天会直接按 ST 当前默认 source/model 发送。'
    }
  }
  const keys = [route.primaryProviderKey]
    .concat(String(route.fallbackProviderKeys || '').split('|').map((item) => item.trim()).filter(Boolean))
  const current = keys
    .map((key) => providers.find((item) => item.providerKey === key && item.enabled && !isCircuitOpen(item)))
    .find(Boolean)
    || providers.find((item) => item.providerKey === route.primaryProviderKey)
  if (!current) {
    return {
      title: '当前路由未找到可用供应商',
      model: defaultModel,
      source: defaultSource,
      provider: route.primaryProviderKey || '--',
      route: route.displayName || route.sceneKey,
      tip: 'default_chat 路由已存在，但当前没有可用供应商，因此正式聊天会回退到 ST 默认 source/model。'
    }
  }
  return {
    title: '当前正式聊天优先使用模型路由',
    model: current.modelName || '--',
    source: current.stSource || '--',
    provider: current.displayName || current.providerKey || '--',
    route: route.displayName || route.sceneKey,
    tip: '只要 default_chat 路由启用且供应商可用，正式聊天会优先使用这里的 provider/model；上半区 ST 默认值仍作为兜底。'
  }
})

function defaultProviderForm() {
  return {
    id: null,
    providerKey: '',
    displayName: '',
    stSource: 'openrouter',
    modelName: '',
    reverseProxy: '',
    proxyPassword: '',
    customUrl: '',
    priority: 100,
    enabled: true,
    failureThreshold: 3,
    cooldownSeconds: 180,
    note: ''
  }
}

function defaultRouteForm() {
  return {
    id: null,
    sceneKey: routing.defaultSceneKey || 'default_chat',
    displayName: '',
    primaryProviderKey: '',
    fallbackProviderKeys: [],
    enabled: true,
    note: ''
  }
}

function applyOverview(res) {
  Object.assign(samplerForm, {
    chatCompletionSource: 'openrouter',
    defaultModel: '',
    defaultTemperature: 0.85,
    defaultMaxOutputTokens: 2048,
    maxContextUnlocked: false,
    openaiMaxContext: 0,
    topP: 1,
    frequencyPenalty: 0,
    presencePenalty: 0,
    stLinked: true,
    stError: '',
    chatCompletionSource: res?.data?.chatCompletionSource || 'openrouter',
    defaultModel: res?.data?.defaultModel || '',
    defaultTemperature: res?.data?.defaultTemperature ?? 0.85,
    defaultMaxOutputTokens: res?.data?.defaultMaxOutputTokens ?? 2048,
    maxContextUnlocked: !!res?.data?.maxContextUnlocked,
    openaiMaxContext: res?.data?.openaiMaxContext ?? 0,
    topP: res?.data?.topP ?? 1,
    frequencyPenalty: res?.data?.frequencyPenalty ?? 0,
    presencePenalty: res?.data?.presencePenalty ?? 0,
    stLinked: res?.data?.stLinked !== false,
    stError: res?.data?.stError || ''
  })
  Object.assign(hint, res.hint || {})
  Object.assign(routing, {
    providers: res.routing?.providers || [],
    routes: res.routing?.routes || [],
    legacy: res.routing?.legacy || {},
    defaultSceneKey: res.routing?.defaultSceneKey || 'default_chat'
  })
}

function load() {
  return getOpenRouterGeneration()
    .then((res) => {
      applyOverview(res)
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载模型路由失败'))
    })
}

function submitSampler() {
  savingSampler.value = true
  updateOpenRouterGeneration({
    chatCompletionSource: samplerForm.chatCompletionSource,
    defaultModel: samplerForm.defaultModel,
    defaultTemperature: samplerForm.defaultTemperature,
    defaultMaxOutputTokens: samplerForm.defaultMaxOutputTokens,
    maxContextUnlocked: samplerForm.maxContextUnlocked,
    openaiMaxContext: samplerForm.openaiMaxContext,
    topP: samplerForm.topP,
    frequencyPenalty: samplerForm.frequencyPenalty,
    presencePenalty: samplerForm.presencePenalty
  })
    .then((res) => {
      proxy.$modal.msgSuccess(res?.msg || 'ST 设置已保存')
      return load()
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存 ST 设置失败'))
    })
    .finally(() => {
      savingSampler.value = false
    })
}

function openProviderDialog(row) {
  Object.assign(providerForm, defaultProviderForm(), row ? { ...row } : {})
  providerDialogVisible.value = true
}

function submitProvider() {
  savingProvider.value = true
  saveModelProvider({ ...providerForm })
    .then(() => {
      proxy.$modal.msgSuccess('供应商已保存')
      providerDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存供应商失败'))
    })
    .finally(() => {
      savingProvider.value = false
    })
}

function removeProvider(row) {
  proxy.$modal.confirm(`确认删除供应商 ${row.displayName || row.providerKey} 吗？`)
    .then(() => deleteModelProvider(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('已删除')
      return load()
    })
    .catch(() => {})
}

function openRouteDialog(row) {
  const fallbackProviderKeys = String(row?.fallbackProviderKeys || '')
    .split('|')
    .map((item) => item.trim())
    .filter(Boolean)
  Object.assign(routeForm, defaultRouteForm(), row ? { ...row, fallbackProviderKeys } : {})
  routeDialogVisible.value = true
}

function submitRoute() {
  savingRoute.value = true
  saveModelRoute({
    ...routeForm,
    fallbackProviderKeys: (routeForm.fallbackProviderKeys || []).join('|')
  })
    .then(() => {
      proxy.$modal.msgSuccess('模型路由已保存')
      routeDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存模型路由失败'))
    })
    .finally(() => {
      savingRoute.value = false
    })
}

function removeRoute(row) {
  proxy.$modal.confirm(`确认删除路由 ${row.displayName || row.sceneKey} 吗？`)
    .then(() => deleteModelRoute(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('已删除')
      return load()
    })
    .catch(() => {})
}

function providerStatusText(row) {
  if (!row?.enabled) return '停用'
  if (isCircuitOpen(row)) return '熔断中'
  if (row.lastHealthStatus === 'healthy') return '健康'
  if (row.lastHealthStatus === 'failing') return '失败中'
  return '未探测'
}

function providerStatusType(row) {
  if (!row?.enabled) return 'info'
  if (isCircuitOpen(row)) return 'danger'
  if (row.lastHealthStatus === 'healthy') return 'success'
  if (row.lastHealthStatus === 'failing') return 'warning'
  return ''
}

function isCircuitOpen(row) {
  if (!row?.circuitOpenUntil) return false
  const ts = Date.parse(row.circuitOpenUntil)
  return Number.isFinite(ts) && ts > Date.now()
}

load()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.jg-form {
  max-width: 820px;
}

.form-tip {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.legacy-box {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}

.effective-box {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.effective-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.effective-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.effective-model {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.effective-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  color: var(--el-text-color-regular);
}

.muted {
  color: var(--el-text-color-secondary);
}
</style>
