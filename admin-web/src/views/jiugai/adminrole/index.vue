<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="管理员角色决定商业后台的菜单可见范围和接口访问权限。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="角色标识 / 角色名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.enabled" clearable placeholder="全部" style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button v-hasPermi="['system:admin-role:create', 'system:admin-role:edit']" type="success" icon="Plus" @click="handleAdd">新增角色</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="角色标识" prop="roleKey" min-width="150" show-overflow-tooltip />
      <el-table-column label="角色名称" prop="roleName" min-width="180" show-overflow-tooltip />
      <el-table-column label="权限数" width="110">
        <template #default="scope">
          {{ scope.row.permissionCount || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="模板状态" width="140">
        <template #default="scope">
          <el-tag :type="templateStatusTagType(scope.row)" effect="plain" size="small">
            {{ templateStatusLabel(scope.row) }}
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
      <el-table-column label="启用" width="100">
        <template #default="scope">
          <el-switch
            :model-value="!!scope.row.enabled"
            :disabled="!proxy?.$auth?.hasPermiOr(['system:admin-role:status', 'system:admin-role:edit']) || scope.row.roleKey === 'super-admin'"
            @change="toggleStatus(scope.row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="90" />
      <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['system:admin-role:update', 'system:admin-role:edit']" link type="primary" icon="View" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button v-hasPermi="['system:admin-role:create', 'system:admin-role:edit']" link type="success" icon="CopyDocument" @click="handleCopy(scope.row)">复制</el-button>
          <el-button v-hasPermi="['system:admin-role:view']" link type="primary" icon="DataAnalysis" @click="handleCompare(scope.row)">对比</el-button>
          <el-button
            v-hasPermi="['system:admin-role:delete', 'system:admin-role:edit']"
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

    <el-dialog :title="title" v-model="open" width="min(1080px, 96vw)" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色标识" prop="roleKey">
              <el-input v-model="form.roleKey" :disabled="!!form.id" placeholder="例如：content-reviewer" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="form.roleName" placeholder="用于后台显示的角色名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :disabled="form.roleKey === 'super-admin'" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="内置">
              <el-tag :type="form.builtIn ? 'warning' : 'info'">{{ form.builtIn ? '内置' : '自定义' }}</el-tag>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>

        <el-form-item label="权限项" prop="permissionKeys">
          <div class="permission-editor">
            <div class="permission-toolbar">
              <el-input
                v-model="permissionKeyword"
                class="permission-toolbar__search"
                clearable
                prefix-icon="Search"
                placeholder="搜索权限名称 / 标识 / 页面"
              />
              <el-select v-model="permissionRisk" clearable placeholder="风险级别" class="permission-toolbar__risk">
                <el-option v-for="risk in riskOptions" :key="risk.value" :label="risk.label" :value="risk.value" />
              </el-select>
              <el-checkbox v-model="showSelectedOnly">仅看已选</el-checkbox>
            </div>

            <div class="permission-summary">
              <div class="permission-summary__item">
                <span class="permission-summary__label">已选权限</span>
                <strong>{{ selectedPermissionCount }}</strong>
              </div>
              <div class="permission-summary__item">
                <span class="permission-summary__label">最高风险</span>
                <el-tag :type="riskTagType(highestSelectedRisk?.value)" effect="light">
                  {{ highestSelectedRisk?.label || '无' }}
                </el-tag>
              </div>
              <div class="permission-summary__item permission-summary__risks">
                <el-tag
                  v-for="risk in selectedRiskStats"
                  :key="risk.value"
                  :type="riskTagType(risk.value)"
                  effect="plain"
                  size="small"
                >
                  {{ risk.label }} {{ risk.count }}
                </el-tag>
              </div>
            </div>

            <el-alert
              v-if="hasFullAccess"
              class="permission-full-access"
              type="warning"
              :closable="false"
              show-icon
              title="当前角色拥有全权限"
            />

            <div class="role-template-panel">
              <div class="role-template-panel__head">
                <div>
                  <div class="role-template-panel__title">角色模板对照</div>
                  <div v-if="activeTemplate" class="role-template-panel__desc">{{ activeTemplate.description }}</div>
                </div>
                <el-select v-model="selectedTemplateKey" clearable placeholder="选择模板" class="role-template-panel__select">
                  <el-option
                    v-for="template in roleTemplates"
                    :key="template.key"
                    :label="template.label"
                    :value="template.key"
                  />
                </el-select>
              </div>
              <div v-if="activeTemplate" class="role-template-panel__body">
                <div class="role-template-panel__progress">
                  <el-progress :percentage="templateMatchPercent" :status="templateMatchStatus" />
                </div>
                <div class="role-template-panel__stats">
                  <el-tag type="success" effect="plain" size="small">匹配 {{ templateMatchedCount }}</el-tag>
                  <el-tag type="warning" effect="plain" size="small">缺少 {{ templateMissingItems.length }}</el-tag>
                  <el-tag type="info" effect="plain" size="small">额外 {{ templateExtraItems.length }}</el-tag>
                </div>
                <div v-if="templateMissingItems.length" class="role-template-panel__diff">
                  <span>缺少：</span>
                  <el-tag v-for="item in templateMissingItems.slice(0, 8)" :key="item.key" size="small" effect="plain">
                    {{ item.label }}
                  </el-tag>
                  <span v-if="templateMissingItems.length > 8" class="role-template-panel__more">
                    +{{ templateMissingItems.length - 8 }}
                  </span>
                </div>
              </div>
            </div>

            <div class="permission-groups">
              <el-empty v-if="filteredPermissionGroups.length === 0" description="没有匹配的权限项" />
              <div v-for="group in filteredPermissionGroups" :key="group.key" class="permission-group">
                <div class="permission-group__head">
                  <div>
                    <div class="permission-group__title">{{ group.label }}</div>
                    <div v-if="group.description" class="permission-group__desc">{{ group.description }}</div>
                  </div>
                  <el-tag effect="plain" size="small">{{ group.selectedCount }} / {{ group.totalCount }}</el-tag>
                </div>
                <el-checkbox-group v-model="form.permissionKeys" class="permission-list">
                  <el-checkbox v-for="item in group.items" :key="item.key" :label="item.key" class="permission-item">
                    <span class="permission-item__main">
                      <span class="permission-item__name">{{ item.label }}</span>
                      <el-tag :type="riskTagType(item.riskLevel)" effect="light" size="small">
                        {{ item.riskLabel || item.riskLevel }}
                      </el-tag>
                    </span>
                    <span class="permission-item__meta">
                      <span>{{ item.key }}</span>
                      <span v-if="item.pageLabel"> · {{ item.pageLabel }}</span>
                      <span v-if="item.actionLabel"> · {{ item.actionLabel }}</span>
                    </span>
                    <span v-if="item.description" class="permission-item__desc">{{ item.description }}</span>
                  </el-checkbox>
                </el-checkbox-group>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="权限对比" v-model="compareOpen" width="min(980px, 96vw)" append-to-body destroy-on-close>
      <div v-loading="compareLoading" class="compare-panel">
        <div class="compare-head">
          <div>
            <div class="compare-head__label">当前角色</div>
            <div class="compare-head__title">{{ compareSourceLabel }}</div>
          </div>
          <div class="compare-target">
            <el-radio-group v-model="compareTargetType" @change="handleCompareTargetTypeChange">
              <el-radio-button label="template">模板</el-radio-button>
              <el-radio-button label="role">角色</el-radio-button>
            </el-radio-group>
            <el-select
              v-model="compareTargetKey"
              filterable
              clearable
              placeholder="选择对比对象"
              class="compare-target__select"
              @change="handleCompareTargetChange"
            >
              <el-option
                v-for="item in compareTargetOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                :disabled="item.disabled"
              />
            </el-select>
          </div>
        </div>

        <el-empty v-if="!compareReady" description="请选择一个模板或角色进行对比" />
        <template v-else>
          <div class="compare-summary">
            <div class="compare-summary__item">
              <span>共同权限</span>
              <strong>{{ compareSameItems.length }}</strong>
            </div>
            <div class="compare-summary__item">
              <span>{{ compareLeftTitle }}</span>
              <strong>{{ compareLeftOnlyItems.length }}</strong>
            </div>
            <div class="compare-summary__item">
              <span>{{ compareRightTitle }}</span>
              <strong>{{ compareRightOnlyItems.length }}</strong>
            </div>
            <div class="compare-summary__item">
              <span>差异高风险</span>
              <el-tag :type="compareHighRiskDiffCount ? 'danger' : 'success'" effect="light">
                {{ compareHighRiskDiffCount }}
              </el-tag>
            </div>
          </div>

          <el-alert
            v-if="compareSourceHasFullAccess || compareTargetHasFullAccess"
            type="warning"
            :closable="false"
            show-icon
            title="对比中包含全权限角色，已按所有已登记权限展开计算。"
          />

          <div class="compare-lists">
            <div class="compare-list">
              <div class="compare-list__title">共同权限</div>
              <div class="compare-list__body">
                <el-empty v-if="compareSameItems.length === 0" description="无共同权限" :image-size="64" />
                <el-tag
                  v-for="item in compareSameItems"
                  :key="item.key"
                  :type="riskTagType(item.riskLevel)"
                  effect="plain"
                  size="small"
                >
                  {{ item.label }}
                </el-tag>
              </div>
            </div>
            <div class="compare-list">
              <div class="compare-list__title">{{ compareLeftTitle }}</div>
              <div class="compare-list__body">
                <el-empty v-if="compareLeftOnlyItems.length === 0" description="无独有权限" :image-size="64" />
                <el-tag
                  v-for="item in compareLeftOnlyItems"
                  :key="item.key"
                  :type="riskTagType(item.riskLevel)"
                  effect="plain"
                  size="small"
                >
                  {{ item.label }}
                </el-tag>
              </div>
            </div>
            <div class="compare-list">
              <div class="compare-list__title">{{ compareRightTitle }}</div>
              <div class="compare-list__body">
                <el-empty v-if="compareRightOnlyItems.length === 0" description="无独有权限" :image-size="64" />
                <el-tag
                  v-for="item in compareRightOnlyItems"
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
        </template>
      </div>
      <template #footer>
        <el-button @click="compareOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgAdminRole">
import {
  addAdminRole,
  getAdminRole,
  getAdminRoleMeta,
  listAdminRole,
  removeAdminRole,
  updateAdminRole,
  updateAdminRoleStatus
} from '@/api/jiugai/adminRole'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const open = ref(false)
const title = ref('')
const permissionGroups = ref([])
const roleTemplates = ref([])
const permissionKeyword = ref('')
const permissionRisk = ref('')
const showSelectedOnly = ref(false)
const selectedTemplateKey = ref('')
const formRef = ref()
const compareOpen = ref(false)
const compareLoading = ref(false)
const compareSource = ref(null)
const compareTargetType = ref('template')
const compareTargetKey = ref('')
const compareTarget = ref(null)
const compareRoleOptions = ref([])

const riskOptions = [
  { value: 'LOW', label: '低', sortOrder: 10 },
  { value: 'MEDIUM', label: '中', sortOrder: 20 },
  { value: 'HIGH', label: '高', sortOrder: 30 },
  { value: 'CRITICAL', label: '极高', sortOrder: 40 }
]

const emptyForm = () => ({
  id: undefined,
  roleKey: '',
  roleName: '',
  permissionKeys: [],
  enabled: true,
  builtIn: false,
  sortOrder: 0,
  remark: ''
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    enabled: undefined
  },
  form: emptyForm(),
  rules: {
    roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
    roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
    permissionKeys: [{ required: true, message: '至少选择一个权限项', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

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

const allPermissionKeys = computed(() => Object.keys(permissionItemMap.value))

const selectedPermissionKeys = computed(() => new Set(form.value.permissionKeys || []))

const hasFullAccess = computed(() => selectedPermissionKeys.value.has('*:*:*'))

const selectedPermissionItems = computed(() => {
  if (hasFullAccess.value) {
    return Object.values(permissionItemMap.value)
  }
  return (form.value.permissionKeys || [])
    .map((key) => permissionItemMap.value[key])
    .filter(Boolean)
})

const selectedPermissionCount = computed(() => form.value.permissionKeys?.length || 0)

const selectedRiskStats = computed(() => {
  const counts = {}
  selectedPermissionItems.value.forEach((item) => {
    const risk = item.riskLevel || 'LOW'
    counts[risk] = (counts[risk] || 0) + 1
  })
  return riskOptions
    .filter((risk) => counts[risk.value])
    .map((risk) => ({
      ...risk,
      label: riskLabelMap.value[risk.value] || risk.label,
      count: counts[risk.value]
    }))
})

const highestSelectedRisk = computed(() => {
  return selectedRiskStats.value.reduce((current, item) => {
    if (!current) return item
    return (riskOrderMap.value[item.value] || 0) > (riskOrderMap.value[current.value] || 0) ? item : current
  }, null)
})

const normalizedPermissionKeyword = computed(() => permissionKeyword.value.trim().toLowerCase())

const filteredPermissionGroups = computed(() => {
  const keyword = normalizedPermissionKeyword.value
  const selectedKeys = selectedPermissionKeys.value
  return permissionGroups.value
    .map((group) => {
      const rawItems = group.items || []
      const items = rawItems.filter((item) => {
        if (permissionRisk.value && item.riskLevel !== permissionRisk.value) {
          return false
        }
        if (showSelectedOnly.value && !selectedKeys.has(item.key)) {
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
      return {
        ...group,
        items,
        totalCount: rawItems.length,
        selectedCount: rawItems.filter((item) => selectedKeys.has(item.key)).length
      }
    })
    .filter((group) => group.items.length > 0)
})

const activeTemplate = computed(() => {
  return roleTemplates.value.find((item) => item.key === selectedTemplateKey.value) || null
})

const activeTemplatePermissionKeys = computed(() => activeTemplate.value?.permissionKeys || [])

const templateExpectedKeys = computed(() => {
  if (!activeTemplate.value) {
    return []
  }
  if (activeTemplate.value.fullAccess) {
    return Object.keys(permissionItemMap.value)
  }
  return activeTemplatePermissionKeys.value.filter((key) => key !== '*:*:*')
})

const templateMatchedCount = computed(() => {
  if (!activeTemplate.value) {
    return 0
  }
  if (hasFullAccess.value) {
    return templateExpectedKeys.value.length
  }
  const selectedKeys = selectedPermissionKeys.value
  return templateExpectedKeys.value.filter((key) => selectedKeys.has(key)).length
})

const templateMissingItems = computed(() => {
  if (!activeTemplate.value || hasFullAccess.value) {
    return []
  }
  const selectedKeys = selectedPermissionKeys.value
  return templateExpectedKeys.value
    .filter((key) => !selectedKeys.has(key))
    .map((key) => permissionItemMap.value[key] || { key, label: key })
})

const templateExtraItems = computed(() => {
  if (!activeTemplate.value || hasFullAccess.value) {
    return []
  }
  const expected = new Set(templateExpectedKeys.value)
  return (form.value.permissionKeys || [])
    .filter((key) => key !== '*:*:*' && !expected.has(key))
    .map((key) => permissionItemMap.value[key] || { key, label: key })
})

const templateMatchPercent = computed(() => {
  if (!activeTemplate.value) {
    return 0
  }
  const total = templateExpectedKeys.value.length
  if (total === 0) {
    return 100
  }
  return Math.round((templateMatchedCount.value / total) * 100)
})

const templateMatchStatus = computed(() => {
  if (templateMatchPercent.value >= 100 && templateExtraItems.value.length === 0) {
    return 'success'
  }
  if (templateMissingItems.value.length > 0) {
    return 'warning'
  }
  return undefined
})

const compareSourceLabel = computed(() => roleLabel(compareSource.value))

const compareTargetOptions = computed(() => {
  if (compareTargetType.value === 'template') {
    return roleTemplates.value.map((item) => ({
      value: item.key,
      label: item.label
    }))
  }
  return compareRoleOptions.value.map((item) => ({
    value: String(item.id),
    label: roleLabel(item),
    disabled: compareSource.value && String(item.id) === String(compareSource.value.id)
  }))
})

const compareReady = computed(() => !!compareSource.value && !!compareTarget.value)

const compareSourceHasFullAccess = computed(() => hasAllPermission(compareSource.value?.permissionKeys))

const compareTargetHasFullAccess = computed(() => hasAllPermission(compareTarget.value?.permissionKeys))

const compareSourceExpandedKeys = computed(() => expandPermissionKeys(compareSource.value?.permissionKeys))

const compareTargetExpandedKeys = computed(() => expandPermissionKeys(compareTarget.value?.permissionKeys))

const compareSourceKeySet = computed(() => new Set(compareSourceExpandedKeys.value))

const compareTargetKeySet = computed(() => new Set(compareTargetExpandedKeys.value))

const compareSameItems = computed(() => {
  if (!compareReady.value) return []
  return compareSourceExpandedKeys.value
    .filter((key) => compareTargetKeySet.value.has(key))
    .map(toPermissionItem)
    .sort(comparePermissionItem)
})

const compareLeftOnlyItems = computed(() => {
  if (!compareReady.value) return []
  return compareSourceExpandedKeys.value
    .filter((key) => !compareTargetKeySet.value.has(key))
    .map(toPermissionItem)
    .sort(comparePermissionItem)
})

const compareRightOnlyItems = computed(() => {
  if (!compareReady.value) return []
  return compareTargetExpandedKeys.value
    .filter((key) => !compareSourceKeySet.value.has(key))
    .map(toPermissionItem)
    .sort(comparePermissionItem)
})

const compareLeftTitle = computed(() => `${compareSourceLabel.value || '当前角色'}独有`)

const compareRightTitle = computed(() => `${roleLabel(compareTarget.value) || '对比对象'}独有`)

const compareHighRiskDiffCount = computed(() => {
  return [...compareLeftOnlyItems.value, ...compareRightOnlyItems.value].filter((item) => isHighRisk(item.riskLevel)).length
})

function loadMeta() {
  return getAdminRoleMeta()
    .then((res) => {
      const info = res.data || {}
      permissionGroups.value = info.permissionGroups || []
      roleTemplates.value = info.roleTemplates || []
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色元数据失败'))
    })
}

function getList() {
  loading.value = true
  listAdminRole(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色列表失败'))
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
  queryParams.value.enabled = undefined
  handleQuery()
}

function handleAdd() {
  form.value = emptyForm()
  title.value = '新增管理员角色'
  resetPermissionAssist()
  open.value = true
}

function handleUpdate(row) {
  getAdminRole(row.id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      title.value = form.value.id ? '编辑管理员角色' : '查看角色'
      resetPermissionAssist(form.value.roleKey)
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色详情失败'))
    })
}

function handleCopy(row) {
  getAdminRole(row.id)
    .then((res) => {
      const source = { ...emptyForm(), ...(res.data || {}) }
      form.value = {
        ...emptyForm(),
        roleKey: `${source.roleKey || 'role'}-copy`,
        roleName: `${source.roleName || source.roleKey || '角色'} 副本`,
        permissionKeys: [...(source.permissionKeys || [])],
        enabled: true,
        builtIn: false,
        sortOrder: source.sortOrder || 0,
        remark: source.remark || ''
      }
      title.value = `复制管理员角色：${source.roleName || source.roleKey}`
      resetPermissionAssist(source.roleKey)
      open.value = true
      proxy.$modal.msgSuccess('已复制为新角色草稿，保存后才会生效')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '复制角色失败'))
    })
}

function handleCompare(row) {
  compareOpen.value = true
  compareSource.value = null
  compareTarget.value = null
  compareTargetType.value = 'template'
  compareTargetKey.value = roleTemplates.value.some((item) => item.key === row.roleKey) ? row.roleKey : ''
  compareLoading.value = true
  getAdminRole(row.id)
    .then((res) => {
      compareSource.value = { ...row, ...(res.data || {}) }
      if (compareTargetKey.value) {
        setTemplateCompareTarget(compareTargetKey.value)
      }
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载对比角色失败'))
    })
    .finally(() => {
      compareLoading.value = false
    })
}

function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    const action = form.value.id ? updateAdminRole : addAdminRole
    action({ ...form.value })
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存角色失败'))
      })
  })
}

function toggleStatus(row, value) {
  if (value) {
    updateRoleStatus(row, value).catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '更新状态失败'))
    })
    return
  }
  getAdminRole(row.id)
    .then((res) => {
      const detail = { ...row, ...(res.data || {}) }
      const message = buildDisableImpactMessage(detail)
      return proxy.$modal.confirm(message)
    })
    .then(() => updateRoleStatus(row, value))
    .catch((e) => {
      row.enabled = !value
      if (e !== 'cancel') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '更新状态失败'))
      }
    })
}

function updateRoleStatus(row, value) {
  return updateAdminRoleStatus({ id: row.id, enabled: value })
    .then(() => {
      proxy.$modal.msgSuccess('状态已更新')
      getList()
    })
    .catch((e) => {
      row.enabled = !value
      throw e
    })
}

function handleDelete(row) {
  proxy.$modal
    .confirm(`确认删除角色“${row.roleName || row.roleKey}”吗？`)
    .then(() => removeAdminRole(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
    .catch((e) => {
      if (e !== 'cancel') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除角色失败'))
      }
    })
}

function resetPermissionAssist(roleKey) {
  permissionKeyword.value = ''
  permissionRisk.value = ''
  showSelectedOnly.value = false
  selectedTemplateKey.value = roleTemplates.value.some((item) => item.key === roleKey) ? roleKey : ''
}

function riskTagType(riskLevel) {
  if (riskLevel === 'CRITICAL') return 'danger'
  if (riskLevel === 'HIGH') return 'warning'
  if (riskLevel === 'MEDIUM') return 'primary'
  if (riskLevel === 'LOW') return 'success'
  return 'info'
}

function handleCompareTargetTypeChange() {
  compareTargetKey.value = ''
  compareTarget.value = null
  if (compareTargetType.value === 'role') {
    loadCompareRoleOptions()
  }
}

function handleCompareTargetChange(value) {
  compareTarget.value = null
  if (!value) {
    return
  }
  if (compareTargetType.value === 'template') {
    setTemplateCompareTarget(value)
    return
  }
  compareLoading.value = true
  getAdminRole(value)
    .then((res) => {
      compareTarget.value = res.data || null
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载对比对象失败'))
    })
    .finally(() => {
      compareLoading.value = false
    })
}

function setTemplateCompareTarget(key) {
  const template = roleTemplates.value.find((item) => item.key === key)
  compareTarget.value = template
    ? {
        id: `template:${template.key}`,
        roleKey: template.key,
        roleName: template.label,
        permissionKeys: template.permissionKeys || [],
        fullAccess: template.fullAccess
      }
    : null
}

function loadCompareRoleOptions() {
  return listAdminRole({ pageNum: 1, pageSize: 100 })
    .then((res) => {
      compareRoleOptions.value = res.rows || []
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色选项失败'))
    })
}

function roleLabel(role) {
  if (!role) return ''
  return role.roleName ? `${role.roleName}（${role.roleKey}）` : role.roleKey || role.label || ''
}

function templateStatusLabel(row) {
  const status = row?.templateStatus
  if (!status) {
    return '-'
  }
  if (status.status === 'MATCH') {
    return '模板一致'
  }
  if (status.status === 'DRIFT') {
    return `缺${status.missingCount || 0} 多${status.extraCount || 0}`
  }
  return '自定义'
}

function templateStatusTagType(row) {
  const status = row?.templateStatus?.status
  if (status === 'MATCH') return 'success'
  if (status === 'DRIFT') return 'warning'
  return 'info'
}

function hasAllPermission(keys = []) {
  return (keys || []).includes('*:*:*')
}

function expandPermissionKeys(keys = []) {
  if (hasAllPermission(keys)) {
    return allPermissionKeys.value
  }
  return [...new Set(keys || [])].filter((key) => key && key !== '*:*:*')
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

function isHighRisk(riskLevel) {
  return riskLevel === 'HIGH' || riskLevel === 'CRITICAL'
}

function buildDisableImpactMessage(role) {
  const count = role.assignedAccountCount || 0
  const keys = expandPermissionKeys(role.permissionKeys || [])
  const highRiskCount = keys.map(toPermissionItem).filter((item) => isHighRisk(item.riskLevel)).length
  return [
    `确认停用角色“${role.roleName || role.roleKey}”吗？`,
    `当前分配账号数：${count}`,
    `停用后这些账号会失去该角色提供的 ${keys.length} 个权限。`,
    `其中高风险权限：${highRiskCount} 个。`,
    '保存前请确认这些账号仍有其他角色可以支撑日常操作。'
  ].join('\n')
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

.permission-groups {
  width: 100%;
  display: grid;
  gap: 14px;
}

.permission-editor {
  width: 100%;
  display: grid;
  gap: 12px;
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.permission-toolbar__search {
  width: 300px;
}

.permission-toolbar__risk {
  width: 140px;
}

.permission-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.permission-summary__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
}

.permission-summary__label {
  color: var(--el-text-color-secondary);
}

.permission-summary__risks {
  flex-wrap: wrap;
}

.permission-full-access {
  margin: 0;
}

.role-template-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}

.role-template-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.role-template-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.role-template-panel__desc {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.role-template-panel__select {
  width: 180px;
  flex: none;
}

.role-template-panel__body {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.role-template-panel__stats,
.role-template-panel__diff {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.role-template-panel__diff {
  color: var(--el-text-color-secondary);
}

.role-template-panel__more {
  color: var(--el-text-color-secondary);
}

.permission-group {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px 14px;
}

.permission-group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.permission-group__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.permission-group__desc {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.permission-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.permission-item {
  height: auto;
  min-height: 84px;
  margin: 0;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  align-items: flex-start;
}

.permission-item :deep(.el-checkbox__label) {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 6px;
  white-space: normal;
  line-height: 1.45;
}

.permission-item__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.permission-item__name {
  min-width: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.permission-item__meta,
.permission-item__desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}

.compare-panel {
  display: grid;
  gap: 14px;
  min-height: 220px;
}

.compare-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.compare-head__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.compare-head__title {
  margin-top: 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.compare-target {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.compare-target__select {
  width: 240px;
}

.compare-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
}

.compare-summary__item {
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  display: grid;
  align-content: center;
  gap: 6px;
}

.compare-summary__item span {
  color: var(--el-text-color-secondary);
}

.compare-summary__item strong {
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.compare-lists {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.compare-list {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  min-width: 0;
}

.compare-list__title {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.compare-list__body {
  min-height: 140px;
  max-height: 320px;
  overflow: auto;
  padding: 10px;
  display: flex;
  align-content: flex-start;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .permission-toolbar__search,
  .permission-toolbar__risk,
  .role-template-panel__select {
    width: 100%;
  }

  .role-template-panel__head {
    display: grid;
  }

  .permission-list {
    grid-template-columns: 1fr;
  }

  .compare-head,
  .compare-target {
    display: grid;
    justify-content: stretch;
  }

  .compare-target__select {
    width: 100%;
  }

  .compare-summary,
  .compare-lists {
    grid-template-columns: 1fr;
  }
}
</style>
