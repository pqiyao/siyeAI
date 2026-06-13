<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里管理支付渠道的用户可见性、启用状态和展示文案。真正的商户密钥仍建议放在环境变量中。"
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

    <el-dialog v-model="open" title="编辑支付渠道" width="620px">
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

function defaultForm() {
  return {
    channelCode: '',
    displayName: '',
    description: '',
    sortOrder: 100,
    enabled: false,
    clientVisible: true,
    note: ''
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
    note: row.note || ''
  })
  open.value = true
}

function submit() {
  saving.value = true
  updatePaymentChannel({ ...form })
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
</style>
