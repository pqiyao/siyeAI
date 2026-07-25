<template>
  <section class="routing-shell">
    <header class="routing-header">
      <div>
        <div class="routing-kicker">UNIFIED PROVIDER ROUTING</div>
        <h2>统一模型供应商</h2>
      </div>
      <div class="routing-header-actions">
        <el-tag :type="flags.enabled ? 'success' : 'info'" effect="plain">
          {{ flags.enabled ? '新路由已启用' : '旧路由生效中' }}
        </el-tag>
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Upload"
          :loading="importingLegacy"
          @click="importLegacy"
        >
          导入旧聊天路由
        </el-button>
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Setting"
          @click="openRuntimeDialog"
        >
          运行开关
        </el-button>
        <el-button :icon="Refresh" circle :loading="loading" title="刷新" @click="load" />
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          type="primary"
          :icon="Plus"
          @click="openProviderDialog()"
        >
          新增供应商能力
        </el-button>
      </div>
    </header>

    <div class="rollout-strip">
      <div class="rollout-item">
        <span>影子对比</span>
        <strong>{{ flags.shadowEnabled ? '开启' : '关闭' }}</strong>
      </div>
      <div class="rollout-item">
        <span>聊天灰度</span>
        <strong>{{ flags.chatCanaryPercent || 0 }}%</strong>
      </div>
      <div class="rollout-item">
        <span>BYOK 转官方</span>
        <strong>{{ flags.byokFallbackToOfficial ? '允许' : '禁止' }}</strong>
      </div>
      <div class="rollout-item rollout-wide">
        <span>当前能力开关</span>
        <strong>{{ capabilityRuntimeText }}</strong>
      </div>
      <div class="rollout-item">
        <span>开关来源</span>
        <strong>{{ flags.source === 'database' ? '后台动态配置' : '环境默认值' }}</strong>
      </div>
    </div>

    <div class="capability-toolbar">
      <el-radio-group v-model="activeCapability" size="large">
        <el-radio-button v-for="item in capabilityOptions" :key="item.value" :value="item.value">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-radio-button>
      </el-radio-group>
      <div class="capability-count">
        <strong>{{ capabilityDeployments.length }}</strong>
        <span>个已配置模型</span>
      </div>
    </div>

    <div class="provider-table-wrap">
      <el-table v-loading="loading" :data="capabilityDeployments" row-key="id">
        <el-table-column label="供应商" min-width="190">
          <template #default="{ row }">
            <div class="provider-name">
              <span class="provider-mark">{{ providerInitial(row) }}</span>
              <div>
                <strong>{{ accountFor(row)?.displayName || '--' }}</strong>
                <small>{{ accountFor(row)?.vendor || '--' }} · {{ accountFor(row)?.providerKey || '--' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="上游模型" min-width="220" />
        <el-table-column label="协议" min-width="135">
          <template #default="{ row }">{{ protocolLabel(row.capability) }}</template>
        </el-table-column>
        <el-table-column label="API 地址" min-width="230">
          <template #default="{ row }">
            <span class="mono-cell">{{ accountFor(row)?.baseUrl || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="deploymentStatus(row).type" effect="plain">
              {{ deploymentStatus(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连续失败" width="90" align="center">
          <template #default="{ row }">{{ row.consecutiveFailures || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProviderDialog(row)">编辑</el-button>
            <el-button link type="success" @click="probeSaved(row)">实测</el-button>
            <el-button link type="danger" @click="removeDeployment(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="`${capabilityLabel} 尚未配置供应商模型`" :image-size="72" />
        </template>
      </el-table>
    </div>

    <div class="route-band">
      <div class="route-title">
        <div>
          <span>执行顺序</span>
          <strong>{{ capabilityRoute?.displayName || `${capabilityLabel}默认路由` }}</strong>
        </div>
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Sort"
          :disabled="capabilityDeployments.length === 0"
          @click="openRouteDialog"
        >
          编辑顺序
        </el-button>
      </div>
      <div v-if="routeDeploymentRows.length" class="route-chain">
        <template v-for="(row, index) in routeDeploymentRows" :key="row.id">
          <div class="route-node">
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ accountFor(row)?.displayName || '--' }}</strong>
              <small>{{ row.modelName }}</small>
            </div>
          </div>
          <el-icon v-if="index < routeDeploymentRows.length - 1" class="route-arrow"><Right /></el-icon>
        </template>
      </div>
      <el-empty v-else description="当前能力尚未建立执行路由" :image-size="58" />
    </div>

    <div v-if="emptyAccounts.length" class="orphan-band">
      <span>未绑定能力的账户</span>
      <div v-for="account in emptyAccounts" :key="account.id" class="orphan-item">
        <span>{{ account.displayName }}</span>
        <el-button link type="danger" @click="removeAccount(account)">删除账户</el-button>
      </div>
    </div>

    <el-dialog
      v-model="providerDialogVisible"
      :title="providerForm.deploymentId ? '编辑供应商能力' : '新增供应商能力'"
      width="820px"
      destroy-on-close
    >
      <el-form label-position="top" class="provider-form">
        <el-alert
          v-if="sharedAccountDeployments.length > 1"
          class="shared-account-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`共享账户：同时用于 ${sharedAccountCapabilityText}`"
          description="修改 API 地址、Key、账户启用状态或超时会影响这些能力；只修改下方模型配置不会影响其他能力。"
        />
        <div class="form-section-title">账户连接配置</div>
        <div class="form-grid two-col">
          <el-form-item v-if="!providerForm.deploymentId" label="使用已有账户">
            <el-select v-model="accountChoice" clearable placeholder="新建供应商账户" @change="applyAccountChoice">
              <el-option
                v-for="account in accounts"
                :key="account.id"
                :label="`${account.displayName} (${account.providerKey})`"
                :value="account.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="能力">
            <el-select v-model="providerForm.capability" :disabled="!!providerForm.deploymentId" @change="clearModels">
              <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="供应商类型">
            <el-select v-model="providerForm.vendor" :disabled="!!providerForm.accountId" @change="applyVendorDefaults">
              <el-option
                v-for="item in availableVendors"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="providerForm.displayName" maxlength="128" />
          </el-form-item>
          <el-form-item label="Provider Key">
            <el-input v-model="providerForm.providerKey" :disabled="!!providerForm.accountId" maxlength="64" />
          </el-form-item>
          <el-form-item class="span-two" label="API 基础地址">
            <el-input v-model="providerForm.baseUrl" :disabled="!selectedVendor?.customBaseUrl && !!selectedVendor?.defaultBaseUrl" />
            <div class="normalized-url">实际使用：{{ normalizedBaseUrl || '--' }}</div>
          </el-form-item>
          <el-form-item class="span-two" label="API Key">
            <div class="key-row">
              <el-radio-group v-model="providerForm.apiKeyAction" @change="handleKeyAction">
                <el-radio-button v-if="providerForm.accountId" value="preserve">保留</el-radio-button>
                <el-radio-button value="replace">替换</el-radio-button>
                <el-radio-button v-if="providerForm.accountId" value="clear">清除并停用</el-radio-button>
              </el-radio-group>
              <el-input
                v-if="providerForm.apiKeyAction === 'replace'"
                v-model="providerForm.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="输入新的 API Key"
              />
              <span v-else class="key-mask">{{ selectedAccount?.apiKeyMask || '未配置' }}</span>
            </div>
          </el-form-item>
        </div>

        <div class="form-section-title">当前能力模型配置</div>
        <div class="model-picker">
          <div class="model-picker-head">
            <span>上游模型</span>
            <div>
              <el-checkbox v-model="matchedOnly" :disabled="modelOptions.length === 0">只看能力匹配</el-checkbox>
              <el-button
                v-hasPermi="['ops:openrouter:edit']"
                :icon="Download"
                :loading="discovering"
                :disabled="providerForm.apiKeyAction === 'clear'"
                @click="discoverModels"
              >
                获取模型
              </el-button>
            </div>
          </div>
          <el-select
            v-model="providerForm.modelName"
            filterable
            allow-create
            default-first-option
            placeholder="获取模型或直接填写模型 ID"
          >
            <el-option
              v-for="model in filteredModels"
              :key="model.id"
              :label="model.label || model.id"
              :value="model.id"
            >
              <div class="model-option">
                <span>{{ model.id }}</span>
                <el-tag v-if="model.capabilityMatch" size="small" type="success" effect="plain">匹配</el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="modelSummary" class="model-summary">{{ modelSummary }}</div>
        </div>

        <div class="form-grid three-col compact-grid">
          <el-form-item v-if="providerForm.capability === 'TTS'" label="音色">
            <el-input v-model="providerForm.voiceName" placeholder="alloy" />
          </el-form-item>
          <el-form-item label="失败阈值">
            <el-input-number v-model="providerForm.failureThreshold" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="熔断秒数">
            <el-input-number v-model="providerForm.cooldownSeconds" :min="30" :max="3600" />
          </el-form-item>
          <el-form-item label="请求超时">
            <el-input-number v-model="providerForm.requestTimeoutSeconds" :min="5" :max="600" />
          </el-form-item>
          <el-form-item label="账户启用">
            <el-switch v-model="providerForm.accountEnabled" />
          </el-form-item>
          <el-form-item label="模型启用">
            <el-switch v-model="providerForm.deploymentEnabled" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="providerForm.note" maxlength="255" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button
            v-hasPermi="['ops:openrouter:edit']"
            type="success"
            plain
            :icon="Connection"
            :loading="probing"
            :disabled="providerForm.apiKeyAction === 'clear'"
            @click="probeDraft"
          >
            测试 {{ capabilityName(providerForm.capability) }}
          </el-button>
          <div>
            <el-button @click="providerDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingProvider" @click="submitProvider">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="routeDialogVisible" :title="`${capabilityLabel}执行顺序`" width="680px">
      <div class="route-editor">
        <draggable v-model="routeDraft" item-key="id" handle=".drag-handle" :animation="180">
          <template #item="{ element, index }">
            <div class="route-editor-item">
              <el-icon class="drag-handle"><Rank /></el-icon>
              <span class="order-number">{{ index + 1 }}</span>
              <div>
                <strong>{{ accountFor(element)?.displayName || '--' }}</strong>
                <small>{{ element.modelName }}</small>
              </div>
              <el-button :icon="Close" circle text title="移除" @click="removeRouteDraft(index)" />
            </div>
          </template>
        </draggable>
        <el-select v-model="routeCandidateId" placeholder="添加供应商模型" @change="addRouteCandidate">
          <el-option
            v-for="item in routeCandidates"
            :key="item.id"
            :label="`${accountFor(item)?.displayName || '--'} · ${item.modelName}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoute" :disabled="routeDraft.length === 0" @click="submitRoute">
          保存执行顺序
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runtimeDialogVisible" title="AI 路由运行开关" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="开关保存后会在约 2 秒内影响新请求"
        description="保持总开关关闭和聊天灰度 0% 时，现有聊天继续使用旧路由。BYOK 失败后始终禁止转用官方 Key。"
      />
      <el-form label-position="left" label-width="150px" class="runtime-form">
        <el-form-item label="新路由总开关">
          <el-switch v-model="runtimeDraft.enabled" />
        </el-form-item>
        <el-form-item label="影子对比">
          <el-switch v-model="runtimeDraft.shadowEnabled" />
        </el-form-item>
        <el-form-item label="聊天灰度">
          <el-slider v-model="runtimeDraft.chatCanaryPercent" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="官方识图路由">
          <el-switch v-model="runtimeDraft.visionEnabled" />
        </el-form-item>
        <el-form-item label="官方生图路由">
          <el-switch v-model="runtimeDraft.imageEnabled" />
        </el-form-item>
        <el-form-item label="官方 TTS 路由">
          <el-switch v-model="runtimeDraft.ttsEnabled" />
        </el-form-item>
        <el-form-item label="官方 STT 路由">
          <el-switch v-model="runtimeDraft.sttEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="savingRuntime" @click="resetRuntimeSettings">恢复环境默认值</el-button>
        <el-button @click="runtimeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRuntime" @click="submitRuntimeSettings">确认并保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import draggable from 'vuedraggable'
import {
  ChatDotRound,
  Close,
  Connection,
  Download,
  Headset,
  Microphone,
  Picture,
  Plus,
  Rank,
  Refresh,
  Right,
  Setting,
  Sort,
  Upload,
  View
} from '@element-plus/icons-vue'
import {
  deleteAiAccount,
  deleteAiDeployment,
  discoverAiModels,
  getAiRouting,
  importLegacyAiChatRoute,
  probeAiCapability,
  resetAiRoutingRuntimeSettings,
  saveAiProvider,
  saveAiRoutingRuntimeSettings,
  saveAiRoute
} from '@/api/jiugai/openrouterGeneration'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const pageRoute = useRoute()

const capabilityOptions = [
  { value: 'CHAT', label: '文本聊天', icon: ChatDotRound },
  { value: 'VISION', label: '视觉理解', icon: View },
  { value: 'IMAGE', label: '生图', icon: Picture },
  { value: 'TTS', label: '语音合成', icon: Headset },
  { value: 'STT', label: '语音识别', icon: Microphone }
]

function capabilityFromQuery(value) {
  const normalized = String(value || '').trim().toUpperCase()
  return capabilityOptions.some((item) => item.value === normalized) ? normalized : 'CHAT'
}

const activeCapability = ref(capabilityFromQuery(pageRoute.query.capability))
const loading = ref(false)
const accounts = ref([])
const deployments = ref([])
const routes = ref([])
const catalog = ref([])
const flags = reactive({})
const providerDialogVisible = ref(false)
const routeDialogVisible = ref(false)
const runtimeDialogVisible = ref(false)
const savingProvider = ref(false)
const savingRoute = ref(false)
const discovering = ref(false)
const probing = ref(false)
const importingLegacy = ref(false)
const savingRuntime = ref(false)
const accountChoice = ref(null)
const modelOptions = ref([])
const matchedOnly = ref(true)
const modelSummary = ref('')
const routeDraft = ref([])
const routeCandidateId = ref(null)
const providerForm = reactive(defaultProviderForm())
const runtimeDraft = reactive({
  enabled: false,
  shadowEnabled: true,
  chatCanaryPercent: 0,
  visionEnabled: false,
  imageEnabled: false,
  ttsEnabled: false,
  sttEnabled: false
})

const capabilityLabel = computed(() => capabilityName(activeCapability.value))
const capabilityDeployments = computed(() => deployments.value.filter((item) => item.capability === activeCapability.value))
const capabilityRoute = computed(() => routes.value.find((item) => item.capability === activeCapability.value && item.routeKey === defaultRouteKey(activeCapability.value)))
const routeDeploymentRows = computed(() => {
  const ids = capabilityRoute.value?.deploymentIds || []
  return ids.map((id) => deployments.value.find((item) => item.id === id)).filter(Boolean)
})
const emptyAccounts = computed(() => accounts.value.filter((account) => !deployments.value.some((item) => item.accountId === account.id)))
const selectedAccount = computed(() => accounts.value.find((item) => item.id === providerForm.accountId))
const selectedVendor = computed(() => catalog.value.find((item) => item.value === providerForm.vendor))
const availableVendors = computed(() => catalog.value.filter((item) => item.capabilities?.includes(providerForm.capability)))
const normalizedBaseUrl = computed(() => normalizeBaseUrl(providerForm.baseUrl || selectedVendor.value?.defaultBaseUrl || ''))
const filteredModels = computed(() => matchedOnly.value ? modelOptions.value.filter((item) => item.capabilityMatch) : modelOptions.value)
const routeCandidates = computed(() => capabilityDeployments.value.filter((item) => !routeDraft.value.some((selected) => selected.id === item.id)))
const sharedAccountDeployments = computed(() => deployments.value.filter((item) => item.accountId === providerForm.accountId))
const sharedAccountCapabilityText = computed(() => [...new Set(sharedAccountDeployments.value.map((item) => capabilityName(item.capability)))].join('、'))
const accountConnectionFieldsChanged = computed(() => {
  const account = selectedAccount.value
  if (!account) return false
  return providerForm.vendor !== account.vendor
    || normalizedBaseUrl.value !== normalizeBaseUrl(account.baseUrl)
    || providerForm.apiKeyAction !== 'preserve'
    || providerForm.accountEnabled !== (account.enabled !== false)
    || Number(providerForm.connectTimeoutSeconds) !== Number(account.connectTimeoutSeconds || 10)
    || Number(providerForm.requestTimeoutSeconds) !== Number(account.requestTimeoutSeconds || 90)
})
const capabilityRuntimeText = computed(() => {
  if (!flags.enabled) return '旧路由'
  if (activeCapability.value === 'CHAT') return Number(flags.chatCanaryPercent || 0) > 0 ? `灰度 ${flags.chatCanaryPercent}%` : '未灰度'
  const key = `${activeCapability.value.toLowerCase()}Enabled`
  return flags[key] ? '新路由' : '旧链路'
})

watch(activeCapability, () => {
  modelOptions.value = []
  modelSummary.value = ''
})

watch(() => pageRoute.query.capability, (value) => {
  activeCapability.value = capabilityFromQuery(value)
})

function defaultProviderForm() {
  return {
    accountId: null,
    accountVersion: null,
    deploymentId: null,
    providerKey: '',
    displayName: '',
    vendor: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    apiKeyAction: 'replace',
    capability: activeCapability.value || 'CHAT',
    protocolType: '',
    modelName: '',
    voiceName: '',
    accountEnabled: true,
    deploymentEnabled: true,
    connectTimeoutSeconds: 10,
    requestTimeoutSeconds: 90,
    failureThreshold: 3,
    cooldownSeconds: 180,
    note: ''
  }
}

function load() {
  loading.value = true
  return getAiRouting()
    .then((res) => {
      const data = res?.data || {}
      accounts.value = data.accounts || []
      deployments.value = data.deployments || []
      routes.value = data.routes || []
      catalog.value = data.catalog || []
      Object.assign(flags, data.flags || {})
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载统一模型路由失败')))
    .finally(() => { loading.value = false })
}

function importLegacy() {
  importingLegacy.value = true
  return importLegacyAiChatRoute()
    .then((res) => {
      const data = res?.data || {}
      proxy.$modal.msgSuccess(`已导入 ${data.importedAccounts || 0} 个账户，跳过 ${data.skipped || 0} 个`)
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '导入旧聊天路由失败')))
    .finally(() => { importingLegacy.value = false })
}

function openRuntimeDialog() {
  Object.assign(runtimeDraft, {
    enabled: flags.enabled === true,
    shadowEnabled: flags.shadowEnabled !== false,
    chatCanaryPercent: Number(flags.chatCanaryPercent || 0),
    visionEnabled: flags.visionEnabled === true,
    imageEnabled: flags.imageEnabled === true,
    ttsEnabled: flags.ttsEnabled === true,
    sttEnabled: flags.sttEnabled === true
  })
  runtimeDialogVisible.value = true
}

function submitRuntimeSettings() {
  proxy.$modal.confirm('确认修改 AI 路由运行开关吗？开启能力后只会影响新的请求。')
    .then(() => {
      savingRuntime.value = true
      return saveAiRoutingRuntimeSettings({ ...runtimeDraft, confirmed: true })
    })
    .then(() => {
      proxy.$modal.msgSuccess('运行开关已更新')
      runtimeDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '运行开关保存失败'))
      }
    })
    .finally(() => { savingRuntime.value = false })
}

function resetRuntimeSettings() {
  proxy.$modal.confirm('确认删除后台动态开关并恢复服务器环境变量中的默认值吗？')
    .then(() => {
      savingRuntime.value = true
      return resetAiRoutingRuntimeSettings()
    })
    .then(() => {
      proxy.$modal.msgSuccess('已恢复环境默认值')
      runtimeDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '恢复环境默认值失败'))
      }
    })
    .finally(() => { savingRuntime.value = false })
}

function openProviderDialog(row) {
  Object.assign(providerForm, defaultProviderForm(), { capability: activeCapability.value })
  accountChoice.value = null
  clearModels()
  if (row) {
    const account = accountFor(row)
    Object.assign(providerForm, {
      accountId: row.accountId,
      accountVersion: account?.versionNo ?? 0,
      deploymentId: row.id,
      providerKey: account?.providerKey || '',
      displayName: account?.displayName || '',
      vendor: account?.vendor || 'custom',
      baseUrl: account?.baseUrl || '',
      apiKeyAction: 'preserve',
      capability: row.capability,
      protocolType: row.protocolType,
      modelName: row.modelName,
      voiceName: row.voiceName || '',
      accountEnabled: account?.enabled !== false,
      deploymentEnabled: row.enabled !== false,
      connectTimeoutSeconds: account?.connectTimeoutSeconds || 10,
      requestTimeoutSeconds: account?.requestTimeoutSeconds || 90,
      failureThreshold: row.failureThreshold || 3,
      cooldownSeconds: row.cooldownSeconds || 180,
      note: account?.note || ''
    })
  }
  providerDialogVisible.value = true
}

function applyAccountChoice(id) {
  const account = accounts.value.find((item) => item.id === id)
  if (!account) {
    Object.assign(providerForm, defaultProviderForm(), { capability: activeCapability.value })
    return
  }
  Object.assign(providerForm, {
    accountId: account.id,
    accountVersion: account.versionNo ?? 0,
    providerKey: account.providerKey,
    displayName: account.displayName,
    vendor: account.vendor,
    baseUrl: account.baseUrl,
    apiKey: '',
    apiKeyAction: 'preserve',
    accountEnabled: account.enabled !== false,
    connectTimeoutSeconds: account.connectTimeoutSeconds || 10,
    requestTimeoutSeconds: account.requestTimeoutSeconds || 90,
    note: account.note || ''
  })
}

function applyVendorDefaults(vendor) {
  const definition = catalog.value.find((item) => item.value === vendor)
  providerForm.baseUrl = definition?.defaultBaseUrl || ''
  if (!providerForm.displayName) providerForm.displayName = definition?.label || ''
  if (!providerForm.providerKey || /^\w+_(chat|image|tts|stt)$/.test(providerForm.providerKey)) {
    providerForm.providerKey = `${vendor}_${providerForm.capability.toLowerCase()}`
  }
  clearModels()
}

function clearModels() {
  modelOptions.value = []
  modelSummary.value = ''
}

function handleKeyAction(action) {
  providerForm.apiKey = ''
  if (action === 'clear') {
    providerForm.accountEnabled = false
    providerForm.deploymentEnabled = false
  }
}

function draftPayload(confirmSharedAccountChange = false) {
  return {
    ...providerForm,
    baseUrl: normalizedBaseUrl.value,
    confirmSharedAccountChange
  }
}

function discoverModels() {
  discovering.value = true
  return discoverAiModels(draftPayload())
    .then((res) => {
      const data = res?.data || {}
      modelOptions.value = data.models || []
      matchedOnly.value = Number(data.matchedCount || 0) > 0
      modelSummary.value = `上游 ${data.totalCount || 0} 个，当前能力匹配 ${data.matchedCount || 0} 个`
      if (!providerForm.modelName && filteredModels.value.length) providerForm.modelName = filteredModels.value[0].id
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '获取模型失败')))
    .finally(() => { discovering.value = false })
}

function probeDraft() {
  probing.value = true
  return probeAiCapability(draftPayload())
    .then((res) => {
      proxy.$modal.msgSuccess(`${res?.data?.message || '测试成功'}，${res?.data?.latencyMs || 0} ms`)
      return providerForm.deploymentId ? load() : undefined
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '能力测试失败')))
    .finally(() => { probing.value = false })
}

function probeSaved(row) {
  openProviderDialog(row)
  nextTick(() => probeDraft())
}

function submitProvider() {
  const save = (confirmed = false) => {
    savingProvider.value = true
    return saveAiProvider(draftPayload(confirmed))
    .then(() => {
      proxy.$modal.msgSuccess('供应商能力已保存')
      providerDialogVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存供应商能力失败')))
    .finally(() => { savingProvider.value = false })
  }
  if (sharedAccountDeployments.value.length > 1 && accountConnectionFieldsChanged.value) {
    const affected = sharedAccountCapabilityText.value
    proxy.$modal.confirm(`该账户还用于 ${affected}。确认同时修改这些能力共用的账户连接配置吗？`)
      .then(() => save(true))
      .catch((error) => {
        if (error !== 'cancel' && error !== 'close') {
          proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '确认共享账户变更失败'))
        }
      })
    return
  }
  save(false)
}

function removeDeployment(row) {
  proxy.$modal.confirm(`确认删除模型 ${row.modelName} 吗？`)
    .then(() => deleteAiDeployment(row.id))
    .then(() => load())
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除能力模型失败'))
      }
    })
}

function removeAccount(account) {
  proxy.$modal.confirm(`确认删除空账户 ${account.displayName} 吗？`)
    .then(() => deleteAiAccount(account.id))
    .then(() => load())
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除供应商账户失败'))
      }
    })
}

function openRouteDialog() {
  routeDraft.value = routeDeploymentRows.value.length ? [...routeDeploymentRows.value] : [...capabilityDeployments.value]
  routeCandidateId.value = null
  routeDialogVisible.value = true
}

function addRouteCandidate(id) {
  const item = deployments.value.find((deployment) => deployment.id === id)
  if (item) routeDraft.value.push(item)
  routeCandidateId.value = null
}

function removeRouteDraft(index) {
  routeDraft.value.splice(index, 1)
}

function submitRoute() {
  savingRoute.value = true
  const route = capabilityRoute.value
  saveAiRoute({
    id: route?.id || null,
    routeKey: defaultRouteKey(activeCapability.value),
    displayName: `${capabilityLabel.value}默认路由`,
    capability: activeCapability.value,
    deploymentIds: routeDraft.value.map((item) => item.id),
    enabled: true,
    note: ''
  })
    .then(() => {
      proxy.$modal.msgSuccess('执行顺序已保存')
      routeDialogVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存执行顺序失败')))
    .finally(() => { savingRoute.value = false })
}

function accountFor(deployment) {
  return accounts.value.find((item) => item.id === deployment?.accountId)
}

function providerInitial(row) {
  const name = accountFor(row)?.displayName || accountFor(row)?.vendor || '?'
  return name.slice(0, 1).toUpperCase()
}

function deploymentStatus(row) {
  if (accountFor(row)?.enabled === false || row.enabled === false) return { text: '停用', type: 'info' }
  if (row.circuitOpenUntil && Date.parse(row.circuitOpenUntil) > Date.now()) return { text: '熔断中', type: 'danger' }
  if (row.lastHealthStatus === 'healthy') return { text: '健康', type: 'success' }
  if (row.lastHealthStatus === 'configuration_error') return { text: '配置错误', type: 'danger' }
  if (row.lastHealthStatus === 'failing') return { text: '失败中', type: 'warning' }
  return { text: '未探测', type: '' }
}

function capabilityName(value) {
  return capabilityOptions.find((item) => item.value === value)?.label || value || '--'
}

function protocolLabel(capability) {
  return `${capabilityName(capability)} · OpenAI 兼容`
}

function defaultRouteKey(capability) {
  return `${String(capability || '').toLowerCase()}.default`
}

function normalizeBaseUrl(raw) {
  let value = String(raw || '').trim().replace(/\/+$/, '')
  value = value.replace(/\/(chat\/completions|images\/generations|audio\/speech|audio\/transcriptions|models)$/i, '')
  return /\/v1$/i.test(value) ? value : (value ? `${value}/v1` : '')
}

load()
</script>

<style scoped>
.routing-shell {
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.routing-header,
.capability-toolbar,
.route-title,
.dialog-footer,
.model-picker-head,
.key-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.routing-header {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.routing-header h2 {
  margin: 3px 0 0;
  font-family: "Microsoft YaHei UI", sans-serif;
  font-size: 21px;
  letter-spacing: 0;
}

.routing-kicker {
  color: var(--el-color-primary);
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

.routing-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rollout-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr)) minmax(180px, 1.4fr) minmax(130px, 1fr);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.rollout-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 13px 18px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.rollout-item:last-child { border-right: 0; }
.rollout-item span { color: var(--el-text-color-secondary); font-size: 12px; }
.rollout-item strong { color: var(--el-text-color-primary); font-size: 14px; }

.capability-toolbar {
  padding: 18px 24px;
}

.capability-toolbar :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 128px;
  justify-content: center;
}

.capability-count { display: flex; align-items: baseline; gap: 7px; color: var(--el-text-color-secondary); }
.capability-count strong { color: var(--el-text-color-primary); font-size: 22px; }
.capability-count span { font-size: 12px; }
.provider-table-wrap { padding: 0 24px 20px; }

.provider-name { display: flex; align-items: center; gap: 10px; min-width: 0; }
.provider-name > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.provider-name strong, .provider-name small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.provider-name small { color: var(--el-text-color-secondary); }
.provider-mark {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

.mono-cell { font-family: Consolas, monospace; font-size: 12px; }
.route-band { padding: 18px 24px 22px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-extra-light); }
.route-title > div { display: flex; flex-direction: column; gap: 4px; }
.route-title span { color: var(--el-text-color-secondary); font-size: 12px; }
.route-title strong { font-size: 16px; }
.route-chain { display: flex; align-items: center; gap: 9px; overflow-x: auto; margin-top: 16px; padding-bottom: 4px; }
.route-node { display: flex; align-items: center; gap: 10px; min-width: 190px; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.route-node > span, .order-number { display: grid; width: 24px; height: 24px; flex: 0 0 24px; place-items: center; border-radius: 50%; background: var(--el-color-primary); color: white; font-size: 12px; font-weight: 700; }
.route-node > div, .route-editor-item > div { display: flex; flex-direction: column; min-width: 0; }
.route-node small, .route-editor-item small { overflow: hidden; color: var(--el-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
.route-arrow { flex: 0 0 auto; color: var(--el-text-color-placeholder); }
.orphan-band { display: flex; align-items: center; gap: 16px; padding: 10px 24px; border-top: 1px dashed var(--el-border-color); color: var(--el-text-color-secondary); font-size: 12px; }
.orphan-item { display: flex; align-items: center; gap: 4px; }

.provider-form { max-height: 62vh; overflow-y: auto; padding-right: 8px; }
.runtime-form { margin-top: 18px; }
.runtime-form :deep(.el-slider) { width: 100%; }
.shared-account-alert { margin-bottom: 16px; }
.form-section-title { margin: 4px 0 12px; color: var(--el-text-color-primary); font-size: 14px; font-weight: 700; }
.form-grid { display: grid; gap: 0 18px; }
.two-col { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.three-col { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.span-two { grid-column: 1 / -1; }
.normalized-url, .model-summary { margin-top: 5px; color: var(--el-text-color-secondary); font-family: Consolas, monospace; font-size: 12px; }
.key-row { width: 100%; justify-content: flex-start; }
.key-row .el-input { flex: 1; }
.key-mask { color: var(--el-text-color-secondary); font-family: Consolas, monospace; }
.model-picker { margin: 3px 0 18px; padding: 14px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.model-picker-head { margin-bottom: 10px; }
.model-picker-head > span { font-weight: 600; }
.model-picker-head > div { display: flex; align-items: center; gap: 14px; }
.model-picker .el-select { width: 100%; }
.model-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.compact-grid :deep(.el-input-number) { width: 100%; }
.route-editor { display: grid; gap: 14px; }
.route-editor-item { display: grid; grid-template-columns: 24px 28px minmax(0, 1fr) 34px; align-items: center; gap: 10px; margin-bottom: 8px; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.drag-handle { cursor: grab; color: var(--el-text-color-secondary); }

@media (max-width: 900px) {
  .routing-header, .capability-toolbar { align-items: flex-start; flex-direction: column; }
  .routing-header-actions { flex-wrap: wrap; }
  .rollout-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .rollout-item:nth-child(2) { border-right: 0; }
  .capability-toolbar :deep(.el-radio-group) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; }
  .capability-toolbar :deep(.el-radio-button__inner) { width: 100%; min-width: 0; border-left: var(--el-border); border-radius: 0; }
  .two-col, .three-col { grid-template-columns: 1fr; }
  .span-two { grid-column: auto; }
  .key-row { align-items: stretch; flex-direction: column; }
}
</style>
