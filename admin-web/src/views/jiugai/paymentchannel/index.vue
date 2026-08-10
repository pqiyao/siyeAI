<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="易支付密钥可在此填写（加密存储）；微信/支付宝仍可用环境变量。这里同时管理渠道可见性、启用状态和展示文案。"
    />

    <el-card shadow="never" class="mb12">
      <template #header>
        <div class="card-head">
          <span>渠道说明</span>
        </div>
      </template>
      <div class="hint-grid">
        <div v-for="(value, key) in hint" :key="key" class="hint-item">
          <div class="hint-key">{{ key }}</div>
          <div class="hint-value">{{ value }}</div>
        </div>
      </div>
    </el-card>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="code" label="渠道编码" min-width="130" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="运行状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.ready ? 'success' : row.enabled ? 'warning' : 'info'">
            {{ row.ready ? '就绪' : row.enabled ? '待配置' : '未开启' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="密钥" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.code === 'epay'" :type="row.secretConfigured ? 'success' : 'info'">
            {{ row.secretConfigured ? '已配置' : '未配置' }}
          </el-tag>
          <span v-else class="muted">环境变量</span>
        </template>
      </el-table-column>
      <el-table-column label="客户端可见" width="110">
        <template #default="{ row }">
          <el-tag :type="row.clientVisible ? 'success' : 'info'">{{ row.clientVisible ? '显示' : '隐藏' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="provider" label="Provider" width="140" />
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column prop="desc" label="展示说明" min-width="260" show-overflow-tooltip />
      <el-table-column prop="note" label="运维备注" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="open" title="编辑支付渠道" width="680px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item label="渠道编码">
          <el-input v-model="form.channelCode" disabled />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="展示说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="启用渠道">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="客户端可见">
          <el-switch v-model="form.clientVisible" />
        </el-form-item>
        <el-form-item label="运维备注">
          <el-input v-model="form.note" type="textarea" :rows="2" />
        </el-form-item>

        <template v-if="form.channelCode === 'epay'">
          <el-divider content-position="left">易支付商户配置</el-divider>
          <el-alert
            class="mb12"
            type="warning"
            :closable="false"
            show-icon
            :title="epaySecretHint"
          />
          <el-alert
            v-if="form.runtimeOverrideActive"
            class="mb12"
            type="info"
            :closable="false"
            show-icon
            title="服务器运行环境中存在易支付覆盖配置；实际支付会优先使用环境变量，本页保存值仅作为缺省配置。"
          />
          <el-form-item label="商户 ID">
            <el-input v-model="form.pid" :placeholder="form.pidMasked ? `当前：${form.pidMasked}` : '请输入商户 ID（pid）'" />
          </el-form-item>
          <el-form-item label="商户密钥">
            <el-input
              v-model="form.key"
              type="password"
              show-password
              placeholder="留空则不修改"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="网关地址">
            <el-input v-model="form.apiUrl" placeholder="如 https://pay.example.com/" />
          </el-form-item>
          <el-form-item label="异步通知">
            <el-input v-model="form.notifyUrl" placeholder="notify_url，服务端回调地址" />
          </el-form-item>
          <el-form-item label="同步跳转">
            <el-input v-model="form.returnUrl" placeholder="return_url，支付完成回跳" />
          </el-form-item>
          <el-form-item label="默认支付类型">
            <el-select v-model="form.typeDefault" clearable placeholder="可选" style="width: 100%">
              <el-option label="支付宝（alipay）" value="alipay" />
              <el-option label="微信（wxpay）" value="wxpay" />
            </el-select>
          </el-form-item>
        </template>

        <el-alert
          v-else-if="isEnvSecretChannel"
          class="mb12"
          type="info"
          :closable="false"
          show-icon
          title="微信 / 支付宝商户密钥请继续通过环境变量配置，本页仅管理展示与开关。"
        />
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgPaymentChannel">
import { listPaymentChannels, updatePaymentChannel } from '@/api/jiugai/paymentChannel'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const saving = ref(false)
const open = ref(false)
const rows = ref([])
const hint = ref({})
const form = reactive(defaultForm())

const isEnvSecretChannel = computed(() => ['wechat', 'alipay', 'wxpay'].includes(String(form.channelCode || '').toLowerCase()))

const epaySecretHint = computed(() => {
  if (form.secretConfigured) {
    return `密钥已配置${form.pidMasked ? `（商户 ${form.pidMasked}）` : ''}；密钥输入框留空则不修改。`
  }
  return '尚未配置易支付密钥，保存后将加密存储。'
})

function defaultForm() {
  return {
    channelCode: '',
    displayName: '',
    description: '',
    sortOrder: 100,
    enabled: false,
    clientVisible: true,
    note: '',
    pid: '',
    key: '',
    apiUrl: '',
    notifyUrl: '',
    returnUrl: '',
    typeDefault: '',
    secretConfigured: false,
    pidMasked: '',
    runtimeOverrideActive: false
  }
}

function load() {
  loading.value = true
  listPaymentChannels()
    .then((res) => {
      rows.value = res.rows || []
      hint.value = res.hint || {}
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载支付渠道失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function openEdit(row) {
  Object.assign(form, defaultForm(), {
    channelCode: row.code,
    displayName: row.name,
    description: row.desc,
    sortOrder: row.sortOrder || 100,
    enabled: !!row.enabled,
    clientVisible: !!row.clientVisible,
    note: row.note || '',
    pid: '',
    key: '',
    apiUrl: row.apiUrl || '',
    notifyUrl: row.notifyUrl || '',
    returnUrl: row.returnUrl || '',
    typeDefault: row.typeDefault || '',
    secretConfigured: !!row.secretConfigured,
    pidMasked: row.pidMasked || '',
    runtimeOverrideActive: !!row.runtimeOverrideActive
  })
  open.value = true
}

function buildPayload() {
  const payload = {
    channelCode: form.channelCode,
    displayName: form.displayName,
    description: form.description,
    sortOrder: form.sortOrder,
    enabled: form.enabled,
    clientVisible: form.clientVisible,
    note: form.note
  }
  if (form.channelCode === 'epay') {
    payload.pid = String(form.pid || '').trim()
    payload.apiUrl = String(form.apiUrl || '').trim()
    payload.notifyUrl = String(form.notifyUrl || '').trim()
    payload.returnUrl = String(form.returnUrl || '').trim()
    payload.typeDefault = String(form.typeDefault || '').trim()
    const key = String(form.key || '').trim()
    if (key) {
      payload.key = key
    }
  }
  return payload
}

function submit() {
  saving.value = true
  updatePaymentChannel(buildPayload())
    .then(() => {
      proxy.$modal.msgSuccess('支付渠道已保存')
      open.value = false
      return load()
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存支付渠道失败'))
    })
    .finally(() => {
      saving.value = false
    })
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

.hint-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.hint-item {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.hint-key {
  font-weight: 600;
  margin-bottom: 4px;
}

.hint-value {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
