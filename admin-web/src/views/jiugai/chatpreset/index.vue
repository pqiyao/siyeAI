<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="官方预设是 ST 同步快照；下方全局默认运行参数单独保存，ST 同步不会覆盖。"
    />

    <el-card class="mb12" shadow="never">
      <template #header>
        <div class="card-head">
          <span>系统默认聊天参数</span>
          <el-tag :type="globalDefault.source === 'database' ? 'success' : 'info'" effect="plain">
            {{ globalDefault.source === 'database' ? '数据库配置' : '兼容默认值' }}
          </el-tag>
        </div>
      </template>
      <el-alert
        class="mb12"
        type="info"
        :closable="false"
        title="仅对使用“系统默认”的会话生效；会话明确选择的官方预设或我的预设不受影响。"
      />
      <el-form :model="globalDefault" inline label-width="130px">
        <el-form-item label="最大输出 Token">
          <el-input-number v-model="globalDefault.maxTokens" :min="64" :max="8192" :step="64" />
        </el-form-item>
        <el-form-item label="最大上下文长度">
          <el-input-number v-model="globalDefault.maxContext" :min="2048" :max="300000" :step="1024" />
        </el-form-item>
        <el-form-item>
          <el-button
            v-hasPermi="['content:chat-preset:edit']"
            type="primary"
            :loading="savingGlobalDefault"
            @click="handleSaveGlobalDefault"
          >保存全局参数</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="预设名称 / 来源名"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型" prop="apiType">
        <el-select v-model="queryParams.apiType" clearable style="width: 160px">
          <el-option label="OpenAI / Chat Completion" value="openai" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-select v-model="queryParams.enabled" clearable style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['content:chat-preset:edit']"
          type="primary"
          plain
          icon="Refresh"
          :loading="syncing"
          @click="handleSyncSt"
        >
          同步 ST 预设
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button icon="RefreshRight" plain @click="getList">刷新</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="presetList" border>
      <el-table-column label="ID" prop="id" width="76" align="center" />
      <el-table-column label="预设名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="preset-name">
            {{ row.name || '--' }}
            <el-tag v-if="row.customized" class="customized-tag" type="warning" effect="plain" size="small">后台已配置</el-tag>
          </div>
          <div class="preset-sub">{{ row.description || row.sourceName || '--' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="170">
        <template #default="{ row }">
          <el-tag type="success" effect="plain">{{ sourceTypeText(row.sourceType) }}</el-tag>
          <div class="muted mt4">{{ row.sourceName || '--' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="接口类型" width="135">
        <template #default="{ row }">
          <el-tag effect="plain">{{ apiTypeText(row.apiType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="ST 来源" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.sourceAvailable === false ? 'danger' : 'success'" effect="plain">
            {{ row.sourceAvailable === false ? '已失效' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数摘要" min-width="300">
        <template #default="{ row }">
          <div class="summary-line">
            <span>source：{{ summaryValue(row, 'source') }}</span>
            <span>model：{{ summaryValue(row, 'model') }}</span>
          </div>
          <div class="summary-line">
            <span>temp：{{ summaryValue(row, 'temperature') }}</span>
            <span>top_p：{{ summaryValue(row, 'topP') }}</span>
            <span>tokens：{{ summaryValue(row, 'maxTokens') }}</span>
            <span>ctx：{{ summaryValue(row, 'maxContext') }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-hasPermi="['content:chat-preset:edit']"
            v-model="row.enabled"
            :loading="row._statusSaving"
            :disabled="row.sourceAvailable === false"
            @change="(val) => handleStatusChange(row, val)"
          />
          <el-tag v-if="!hasEditPermission" :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" width="120" align="center">
        <template #default="{ row }">
          <el-input-number
            v-hasPermi="['content:chat-preset:edit']"
            v-model="row.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
            size="small"
            style="width: 96px"
            @change="(val) => handleSortChange(row, val)"
          />
          <span v-if="!hasEditPermission">{{ row.sortOrder }}</span>
        </template>
      </el-table-column>
      <el-table-column label="同步时间" prop="lastSyncedAt" width="170" show-overflow-tooltip />
      <el-table-column label="更新时间" prop="updatedAt" width="170" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="handleDetail(row)">详情</el-button>
          <el-button
            v-hasPermi="['content:chat-preset:edit']"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
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

    <el-dialog v-model="detailOpen" title="预设详情" width="860px" append-to-body destroy-on-close>
      <div class="detail-head">
        <div>
          <div class="detail-name">{{ detail.name || '--' }}</div>
          <div class="muted">{{ detail.description || detail.sourceName || '--' }}</div>
        </div>
        <el-tag :type="detail.enabled ? 'success' : 'info'">{{ detail.enabled ? '启用' : '停用' }}</el-tag>
      </div>
      <el-descriptions :column="3" border size="small" class="mb12">
        <el-descriptions-item label="来源">{{ sourceTypeText(detail.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ apiTypeText(detail.apiType) }}</el-descriptions-item>
        <el-descriptions-item label="排序">{{ detail.sortOrder ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="source">{{ detail.summary?.source || '--' }}</el-descriptions-item>
        <el-descriptions-item label="model">{{ detail.summary?.model || '--' }}</el-descriptions-item>
        <el-descriptions-item label="temperature">{{ detail.summary?.temperature || '--' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="mb12"
        type="warning"
        :closable="false"
        title="保存后仅覆盖该预设的运行配置；ST 原始同步快照仍会保留，聊天链路和会话绑定不变。"
      />
      <el-form label-width="110px">
        <el-form-item label="后台运行配置">
          <el-input v-model="configDraft" class="json-box" type="textarea" :rows="18" />
        </el-form-item>
      </el-form>
      <el-collapse class="mb12">
        <el-collapse-item title="查看 ST 原始同步快照" name="st-snapshot">
          <el-input class="json-box" :model-value="detail.bundleJson || ''" type="textarea" :rows="12" readonly />
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button
          v-if="detail.customized"
          v-hasPermi="['content:chat-preset:edit']"
          type="warning"
          plain
          :loading="restoringConfig"
          @click="handleRestoreConfig"
        >恢复 ST 配置</el-button>
        <el-button
          v-hasPermi="['content:chat-preset:edit']"
          type="primary"
          :loading="savingConfig"
          @click="handleSaveConfig"
        >保存预设配置</el-button>
        <el-button @click="detailOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgChatPreset">
import {
  delChatPreset,
  getChatPresetGlobalDefault,
  getChatPreset,
  listChatPreset,
  syncStChatPresets,
  saveChatPresetGlobalDefault,
  restoreChatPresetConfig,
  updateChatPresetConfig,
  updateChatPresetSort,
  updateChatPresetStatus
} from '@/api/jiugai/chatPreset'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()

const loading = ref(false)
const syncing = ref(false)
const presetList = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})
const configDraft = ref('')
const savingConfig = ref(false)
const restoringConfig = ref(false)
const savingGlobalDefault = ref(false)
const globalDefault = reactive({ maxTokens: 1000, maxContext: 65536, source: 'legacy-default' })

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  apiType: 'openai',
  enabled: undefined
})

const hasEditPermission = computed(() => {
  const permissions = userStore.permissions || []
  return permissions.includes('*:*:*') || permissions.includes('content:chat-preset:edit')
})

function getList() {
  loading.value = true
  listChatPreset(queryParams)
    .then((res) => {
      presetList.value = res.rows || []
      total.value = Number(res.total || 0)
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载聊天预设失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function loadGlobalDefault() {
  getChatPresetGlobalDefault()
    .then((res) => {
      Object.assign(globalDefault, res.data || {})
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载系统默认聊天参数失败'))
    })
}

function handleSaveGlobalDefault() {
  const maxTokens = Number(globalDefault.maxTokens)
  const maxContext = Number(globalDefault.maxContext)
  if (!Number.isInteger(maxTokens) || maxTokens < 64 || maxTokens > 8192) {
    proxy.$modal.msgError('最大输出 Token 必须为 64–8192 的整数')
    return
  }
  if (!Number.isInteger(maxContext) || maxContext < Math.max(2048, maxTokens + 512) || maxContext > 300000) {
    proxy.$modal.msgError('最大上下文长度必须为 maxTokens + 512 至 300000')
    return
  }
  savingGlobalDefault.value = true
  saveChatPresetGlobalDefault({ maxTokens, maxContext })
    .then((res) => {
      Object.assign(globalDefault, res.data || {})
      proxy.$modal.msgSuccess('系统默认聊天参数已保存')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存系统默认聊天参数失败'))
    })
    .finally(() => {
      savingGlobalDefault.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.keyword = ''
  queryParams.apiType = 'openai'
  queryParams.enabled = undefined
  handleQuery()
}

function handleSyncSt() {
  syncing.value = true
  syncStChatPresets()
    .then((res) => {
      const data = res.data || {}
      proxy.$modal.msgSuccess(`同步完成：导入 ${data.imported || 0} 个，跳过 ${data.skipped || 0} 个，来源失效 ${data.unavailable || 0} 个`)
      const warnings = Array.isArray(data.warnings)
        ? data.warnings.filter((item) => typeof item === 'string' && item.trim()).map((item) => item.trim())
        : []
      if (warnings.length > 0) {
        const visibleWarnings = warnings.slice(0, 5)
        const remainingText = warnings.length > visibleWarnings.length
          ? `\n另有 ${warnings.length - visibleWarnings.length} 条警告，请检查 ST 预设配置。`
          : ''
        proxy.$modal.msgWarning(`同步警告：\n${visibleWarnings.join('\n')}${remainingText}`)
      }
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '同步 ST 预设失败'))
    })
    .finally(() => {
      syncing.value = false
    })
}

function handleStatusChange(row, enabled) {
  row._statusSaving = true
  updateChatPresetStatus(row.id, enabled)
    .then(() => {
      proxy.$modal.msgSuccess('保存成功')
    })
    .catch((e) => {
      row.enabled = !enabled
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存状态失败'))
    })
    .finally(() => {
      row._statusSaving = false
    })
}

function handleSortChange(row, sortOrder) {
  updateChatPresetSort(row.id, Number(sortOrder || 0))
    .then(() => {
      proxy.$modal.msgSuccess('排序已保存')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存排序失败'))
      getList()
    })
}

function handleDetail(row) {
  getChatPreset(row.id)
    .then((res) => {
      detail.value = res.data || {}
      configDraft.value = detail.value.adminOverrideJson || detail.value.bundleJson || ''
      detailOpen.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载详情失败'))
    })
}

function handleSaveConfig() {
  let parsed
  try {
    parsed = JSON.parse(configDraft.value)
  } catch (e) {
    proxy.$modal.msgError('预设配置必须是有效 JSON')
    return
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || !parsed.generation || Array.isArray(parsed.generation) || typeof parsed.generation !== 'object') {
    proxy.$modal.msgError('预设配置必须是包含 generation 对象的 JSON 对象')
    return
  }
  savingConfig.value = true
  updateChatPresetConfig(detail.value.id, JSON.stringify(parsed, null, 2))
    .then(() => {
      proxy.$modal.msgSuccess('预设配置已保存')
      detail.value.customized = true
      detail.value.adminOverrideJson = JSON.stringify(parsed, null, 2)
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存预设配置失败'))
    })
    .finally(() => {
      savingConfig.value = false
    })
}

function handleRestoreConfig() {
  proxy.$modal
    .confirm('确认恢复此预设的 ST 原始配置吗？后台覆盖将被清除。')
    .then(() => {
      restoringConfig.value = true
      return restoreChatPresetConfig(detail.value.id)
    })
    .then(() => getChatPreset(detail.value.id))
    .then((res) => {
      detail.value = res.data || {}
      configDraft.value = detail.value.bundleJson || ''
      proxy.$modal.msgSuccess('已恢复 ST 配置')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '恢复 ST 配置失败'))
    })
    .finally(() => {
      restoringConfig.value = false
    })
}

function handleDelete(row) {
  proxy.$modal
    .confirm(`确认删除预设“${row.name || row.id}”吗？删除的是后台同步记录，不会删除 ST 原始预设文件。`)
    .then(() => delChatPreset(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除失败'))
    })
}

function sourceTypeText(value) {
  if (value === 'ST_PLATFORM') return 'ST 官方预设'
  if (value === 'USER_UPLOAD') return '用户上传'
  return value || '--'
}

function apiTypeText(value) {
  if (value === 'openai') return 'OpenAI'
  if (value === 'textgen') return 'TextGen'
  return value || '--'
}

function summaryValue(row, key) {
  const value = row?.summary?.[key]
  return value === undefined || value === null || value === '' ? '--' : value
}

getList()
loadGlobalDefault()
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mb12 {
  margin-bottom: 12px;
}

.mt4 {
  margin-top: 4px;
}

.preset-name {
  font-weight: 600;
  color: #1f2d3d;
}

.customized-tag {
  margin-left: 6px;
  vertical-align: 1px;
}

.preset-sub,
.muted {
  color: #7a8793;
  font-size: 12px;
  line-height: 1.6;
}

.summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #4f5f6f;
  line-height: 1.7;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.detail-name {
  font-size: 16px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 4px;
}

.json-box :deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
}
</style>
