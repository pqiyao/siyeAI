<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="管理员账号仅用于内部运营，请按岗位分配最小必要权限。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="账号 / 昵称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button v-hasPermi="['system:admin-user:create', 'system:admin-user:edit']" type="success" icon="Plus" @click="handleAdd">新增管理员</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="账号" prop="username" min-width="130" />
      <el-table-column label="昵称" prop="nickName" min-width="140" />
      <el-table-column label="角色" prop="roleNames" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内置" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.builtIn ? 'warning' : 'info'">
            {{ scope.row.builtIn ? '内置' : '自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重置密码" width="130">
        <template #default="scope">
          <el-tag :type="scope.row.mustResetPassword ? 'warning' : 'success'">
            {{ scope.row.mustResetPassword ? '下次登录重置' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" prop="lastLoginAt" min-width="160" />
      <el-table-column label="最近 IP" prop="lastLoginIp" min-width="130" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['system:admin-user:view']" link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button v-hasPermi="['system:admin-user:update', 'system:admin-user:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button
            v-hasPermi="['system:admin-user:reset-password', 'system:admin-user:edit']"
            link
            type="warning"
            icon="Key"
            @click="handleResetPassword(scope.row)"
          >
            重置密码
          </el-button>
          <el-button
            v-hasPermi="['system:admin-user:delete', 'system:admin-user:edit']"
            link
            type="danger"
            icon="Delete"
            :disabled="scope.row.builtIn"
            @click="handleDelete(scope.row)"
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

    <el-dialog :title="title" v-model="open" width="860px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="账号" prop="username">
              <el-input v-model="form.username" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickName">
              <el-input v-model="form.nickName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="form.id ? '新密码' : '密码'" prop="password">
              <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空表示保持当前密码' : '请输入登录密码'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple clearable placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="item in roleOptions"
              :key="item.id"
              :label="`${item.roleName} (${item.roleKey})`"
              :value="item.id"
              :disabled="!item.enabled"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="内置">
              <el-tag :type="form.builtIn ? 'warning' : 'info'">{{ form.builtIn ? '内置' : '自定义' }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="强制重置密码">
              <el-switch v-model="form.mustResetPassword" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="重置密码" v-model="passwordOpen" width="520px" append-to-body destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="140px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="下次登录强制修改">
          <el-switch v-model="passwordForm.mustResetPassword" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitPasswordReset">确认</el-button>
        <el-button @click="passwordOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="管理员账号详情" size="min(760px, 96vw)" append-to-body destroy-on-close>
      <div v-loading="detailLoading" class="account-detail">
        <el-descriptions v-if="detailAccount" :column="2" border>
          <el-descriptions-item label="账号">{{ detailAccount.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detailAccount.nickName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailAccount.status === 'ACTIVE' ? 'success' : 'info'">
              {{ detailAccount.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="内置">
            <el-tag :type="detailAccount.builtIn ? 'warning' : 'info'">
              {{ detailAccount.builtIn ? '内置' : '自定义' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最近登录">{{ detailAccount.lastLoginAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近 IP">{{ detailAccount.lastLoginIp || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detailAccount?.hasFullAccess"
          type="warning"
          :closable="false"
          show-icon
          title="该账号最终拥有全权限"
        />

        <section class="detail-section">
          <div class="detail-section__head">
            <div>
              <div class="detail-section__title">拥有角色</div>
              <div class="detail-section__desc">账号当前绑定的角色及每个角色提供的权限数量。</div>
            </div>
            <el-tag effect="plain">{{ detailAccount?.roleRows?.length || 0 }} 个角色</el-tag>
          </div>
          <el-table :data="detailAccount?.roleRows || []" size="small" border>
            <el-table-column label="角色名称" prop="roleName" min-width="150" />
            <el-table-column label="角色标识" prop="roleKey" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                  {{ scope.row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最终权限" width="120">
              <template #default="scope">
                <el-tag :type="scope.row.contributesEffectivePermissions ? 'success' : 'info'" size="small" effect="plain">
                  {{ scope.row.contributesEffectivePermissions ? '计入' : '不计入' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="权限数" prop="permissionCount" width="90" />
          </el-table>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <div>
              <div class="detail-section__title">最终权限</div>
              <div class="detail-section__desc">按当前后端登录态合并逻辑计算，展示这个账号实际会拿到的权限。</div>
            </div>
            <div class="detail-section__tags">
              <el-tag type="primary" effect="plain">{{ detailEffectivePermissionItems.length }} 项</el-tag>
              <el-tag :type="detailHighestRisk ? riskTagType(detailHighestRisk.value) : 'info'" effect="light">
                最高风险 {{ detailHighestRisk?.label || '无' }}
              </el-tag>
            </div>
          </div>

          <div class="permission-toolbar">
            <el-input
              v-model="detailPermissionKeyword"
              class="permission-toolbar__search"
              clearable
              prefix-icon="Search"
              placeholder="搜索最终权限"
            />
            <el-select v-model="detailPermissionRisk" class="permission-toolbar__risk" clearable placeholder="风险级别">
              <el-option v-for="item in riskOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </div>

          <div class="detail-permission-groups">
            <el-empty v-if="detailFilteredPermissionGroups.length === 0" description="没有匹配的权限" />
            <div v-for="group in detailFilteredPermissionGroups" :key="group.key" class="detail-permission-group">
              <div class="detail-permission-group__title">
                <span>{{ group.label }}</span>
                <el-tag size="small" effect="plain">{{ group.items.length }}</el-tag>
              </div>
              <div class="detail-permission-list">
                <el-tag
                  v-for="item in group.items"
                  :key="item.key"
                  :type="riskTagType(item.riskLevel)"
                  effect="plain"
                  size="small"
                >
                  {{ item.label }}
                </el-tag>
              </div>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <div>
              <div class="detail-section__title">登录记录</div>
              <div class="detail-section__desc">当前数据库只保存最近一次成功登录。</div>
            </div>
          </div>
          <el-table :data="detailAccount?.loginRecords || []" size="small" border>
            <el-table-column label="类型" prop="label" min-width="150" />
            <el-table-column label="时间" prop="loginAt" min-width="180" />
            <el-table-column label="IP" prop="loginIp" min-width="140" />
          </el-table>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="JgAdminAccount">
import {
  addAdminAccount,
  getAdminAccount,
  getAdminAccountMeta,
  listAdminAccount,
  removeAdminAccount,
  resetAdminAccountPassword,
  updateAdminAccount,
  updateAdminAccountStatus
} from '@/api/jiugai/adminAccount'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const title = ref('')
const statusOptions = ref([])
const roleOptions = ref([])
const permissionGroups = ref([])
const formRef = ref()
const passwordFormRef = ref()
const passwordOpen = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailAccount = ref(null)
const detailPermissionKeyword = ref('')
const detailPermissionRisk = ref('')

const riskOptions = [
  { value: 'LOW', label: '低', sortOrder: 10 },
  { value: 'MEDIUM', label: '中', sortOrder: 20 },
  { value: 'HIGH', label: '高', sortOrder: 30 },
  { value: 'CRITICAL', label: '极高', sortOrder: 40 }
]

const emptyForm = () => ({
  id: undefined,
  username: '',
  nickName: '',
  password: '',
  roleIds: [],
  status: 'ACTIVE',
  mustResetPassword: false,
  builtIn: false,
  remark: ''
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    status: undefined
  },
  form: emptyForm(),
  passwordForm: {
    id: undefined,
    password: '',
    mustResetPassword: true
  },
  rules: {
    username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
    nickName: [{ required: true, message: '请输入管理员昵称', trigger: 'blur' }],
    roleIds: [{ required: true, message: '至少选择一个角色', trigger: 'change' }]
  },
  passwordRules: {
    password: [{ required: true, message: '请输入新密码', trigger: 'blur' }]
  }
})

const { queryParams, form, passwordForm, rules, passwordRules } = toRefs(data)

const riskOrderMap = computed(() => {
  const map = {}
  riskOptions.forEach((item) => {
    map[item.value] = item.sortOrder
  })
  return map
})

const riskLabelMap = computed(() => {
  const map = {}
  riskOptions.forEach((item) => {
    map[item.value] = item.label
  })
  permissionGroups.value.forEach((group) => {
    ;(group.items || []).forEach((item) => {
      if (item.riskLevel && item.riskLabel) {
        map[item.riskLevel] = item.riskLabel
      }
    })
  })
  return map
})

const permissionItemMap = computed(() => {
  const map = {}
  permissionGroups.value.forEach((group) => {
    ;(group.items || []).forEach((item) => {
      map[item.key] = item
    })
  })
  return map
})

const detailEffectivePermissionKeys = computed(() => detailAccount.value?.effectivePermissionExpandedKeys || [])

const detailEffectivePermissionItems = computed(() => {
  return detailEffectivePermissionKeys.value.map(toPermissionItem).sort(comparePermissionItem)
})

const detailHighestRisk = computed(() => {
  return detailEffectivePermissionItems.value.reduce((current, item) => {
    const risk = {
      value: item.riskLevel || 'LOW',
      label: item.riskLabel || riskLabelMap.value[item.riskLevel] || item.riskLevel || '低'
    }
    if (!current) return risk
    return (riskOrderMap.value[risk.value] || 0) > (riskOrderMap.value[current.value] || 0) ? risk : current
  }, null)
})

const normalizedDetailPermissionKeyword = computed(() => detailPermissionKeyword.value.trim().toLowerCase())

const detailFilteredPermissionGroups = computed(() => {
  const owned = new Set(detailEffectivePermissionKeys.value)
  const keyword = normalizedDetailPermissionKeyword.value
  return permissionGroups.value
    .map((group) => {
      const items = (group.items || []).filter((item) => {
        if (!owned.has(item.key)) {
          return false
        }
        if (detailPermissionRisk.value && item.riskLevel !== detailPermissionRisk.value) {
          return false
        }
        if (!keyword) {
          return true
        }
        return [
          item.key,
          item.label,
          item.pageKey,
          item.pageLabel,
          item.actionKey,
          item.actionLabel,
          item.description
        ]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword))
      })
      return { ...group, items }
    })
    .filter((group) => group.items.length > 0)
})

function loadMeta() {
  return getAdminAccountMeta()
    .then((res) => {
      const info = res.data || {}
      statusOptions.value = info.statusOptions || []
      roleOptions.value = info.roles || []
      permissionGroups.value = info.permissionGroups || []
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载管理员元数据失败'))
    })
}

function getList() {
  loading.value = true
  listAdminAccount(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载管理员列表失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.status = undefined
  handleQuery()
}

function handleAdd() {
  form.value = emptyForm()
  title.value = '新增管理员账号'
  open.value = true
}

function handleDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detailAccount.value = null
  detailPermissionKeyword.value = ''
  detailPermissionRisk.value = ''
  getAdminAccount(row.id)
    .then((res) => {
      detailAccount.value = res.data || null
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载账号详情失败'))
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function handleUpdate(row) {
  getAdminAccount(row.id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}), password: '' }
      title.value = '编辑管理员账号'
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载账号详情失败'))
    })
}

function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    const payload = { ...form.value }
    if (payload.id && !payload.password) {
      delete payload.password
    }
    const action = payload.id ? updateAdminAccount : addAdminAccount
    action(payload)
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存管理员账号失败'))
      })
  })
}

function handleResetPassword(row) {
  passwordForm.value = {
    id: row.id,
    password: '',
    mustResetPassword: true
  }
  passwordOpen.value = true
}

function submitPasswordReset() {
  passwordFormRef.value.validate((valid) => {
    if (!valid) return
    resetAdminAccountPassword({ ...passwordForm.value })
      .then(() => {
        proxy.$modal.msgSuccess('密码已重置')
        passwordOpen.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '重置密码失败'))
      })
  })
}

function toggleStatus(row, value) {
  updateAdminAccountStatus({ id: row.id, status: value ? 'ACTIVE' : 'DISABLED' })
    .then(() => {
      proxy.$modal.msgSuccess('状态已更新')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '更新状态失败'))
    })
}

function handleDelete(row) {
  proxy.$modal
    .confirm(`确认删除管理员账号“${row.username}”吗？`)
    .then(() => removeAdminAccount(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
    .catch((e) => {
      if (e !== 'cancel') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除管理员账号失败'))
      }
    })
}

function toPermissionItem(key) {
  return permissionItemMap.value[key] || {
    key,
    label: key,
    riskLevel: 'MEDIUM',
    riskLabel: riskLabelMap.value.MEDIUM || '中'
  }
}

function comparePermissionItem(a, b) {
  const riskDiff = (riskOrderMap.value[b.riskLevel] || 0) - (riskOrderMap.value[a.riskLevel] || 0)
  if (riskDiff !== 0) return riskDiff
  return String(a.key).localeCompare(String(b.key))
}

function riskTagType(riskLevel) {
  if (riskLevel === 'CRITICAL') return 'danger'
  if (riskLevel === 'HIGH') return 'warning'
  if (riskLevel === 'MEDIUM') return 'primary'
  if (riskLevel === 'LOW') return 'success'
  return 'info'
}

loadMeta().finally(() => getList())
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mb8 {
  margin-bottom: 8px;
}

.account-detail {
  display: grid;
  gap: 16px;
}

.detail-section {
  display: grid;
  gap: 10px;
}

.detail-section__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.detail-section__title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-section__desc {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.detail-section__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.permission-toolbar__search {
  width: 280px;
}

.permission-toolbar__risk {
  width: 140px;
}

.detail-permission-groups {
  display: grid;
  gap: 10px;
}

.detail-permission-group {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px 12px;
}

.detail-permission-group__title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-permission-list {
  display: flex;
  align-items: flex-start;
  align-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .detail-section__head {
    display: grid;
  }

  .detail-section__tags {
    justify-content: flex-start;
  }

  .permission-toolbar__search,
  .permission-toolbar__risk {
    width: 100%;
  }
}
</style>
