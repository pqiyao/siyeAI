<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里只管理安卓正式包。先把 APK 上传到 HTTPS 下载站，再创建版本并发布；客户端会按 versionCode 判断是否升级。"
    />

    <el-form v-show="showSearch" :model="queryParams" inline>
      <el-form-item label="关键词">
        <el-input v-model="queryParams.keyword" clearable placeholder="版本号、包名或标题" style="width: 230px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下架" value="REVOKED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:app-update:edit']" type="primary" plain icon="Plus" @click="openCreate">新建版本</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="load" />
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column label="版本" min-width="150">
        <template #default="{ row }">
          <div class="version-cell">
            <strong>V{{ row.versionName }}</strong>
            <span>versionCode {{ row.versionCode }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="packageName" label="安卓包名" min-width="210" show-overflow-tooltip />
      <el-table-column label="更新策略" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.updateMode === 'FORCE' ? 'danger' : 'primary'">{{ row.updateMode === 'FORCE' ? '强制更新' : '普通更新' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最低支持" width="110" align="center">
        <template #default="{ row }">{{ row.minSupportedVersionCode || '不限制' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row)">{{ statusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishAt" label="发布时间" width="170">
        <template #default="{ row }">{{ row.publishAt || '立即生效' }}</template>
      </el-table-column>
      <el-table-column prop="title" label="更新标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['system:app-update:edit']" link type="primary" icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['system:app-update:edit']" link type="success" icon="Promotion" @click="publishRow(row)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" v-hasPermi="['system:app-update:edit']" link type="warning" icon="SwitchButton" @click="revokeRow(row)">下架</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" v-hasPermi="['system:app-update:edit']" link type="primary" icon="Bell" @click="remindAgain(row)">重新提醒</el-button>
          <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['system:app-update:edit']" link type="danger" icon="Delete" @click="deleteRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="load" />

    <el-dialog v-model="open" :title="dialogTitle" width="760px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="132px">
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="AppID" prop="appId"><el-input v-model="form.appId" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安卓包名" prop="packageName"><el-input v-model="form.packageName" placeholder="如 com.example.app" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本名称" prop="versionName"><el-input v-model="form.versionName" placeholder="如 1.3.7" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="versionCode" prop="versionCode"><el-input-number v-model="form.versionCode" :min="1" :max="2147483647" controls-position="right" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="更新模式"><el-radio-group v-model="form.updateMode"><el-radio-button value="NORMAL">普通更新</el-radio-button><el-radio-button value="FORCE">强制更新</el-radio-button></el-radio-group></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最低支持版本"><el-input-number v-model="form.minSupportedVersionCode" :min="0" :max="2147483647" controls-position="right" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="策略修订号"><el-input-number v-model="form.policyRevision" :min="1" :max="999999" controls-position="right" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="稍后提醒"><el-input-number v-model="form.remindLaterHours" :min="1" :max="168" controls-position="right" /><span class="unit">小时</span></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="更新标题" prop="title"><el-input v-model="form.title" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="更新说明" prop="changelog"><el-input v-model="form.changelog" type="textarea" :rows="6" maxlength="10000" show-word-limit placeholder="每行一项，客户端会在更新页内滚动展示" /></el-form-item>
        <el-form-item label="APK下载地址" prop="downloadUrl"><el-input v-model="form.downloadUrl" placeholder="必须是 https:// 地址" /></el-form-item>
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="APK大小"><el-input-number v-model="form.apkSizeBytes" :min="0" :max="9999999999" controls-position="right" /><span class="unit">字节</span></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布时间"><el-date-picker v-model="form.publishAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" clearable placeholder="留空表示发布后立即生效" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="APK SHA-256"><el-input v-model="form.apkSha256" maxlength="64" placeholder="可选，64位十六进制" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgAppUpdate">
import { addAppRelease, deleteAppRelease, getAppRelease, listAppReleases, publishAppRelease, remindAppReleaseAgain, revokeAppRelease, updateAppRelease } from '@/api/jiugai/appUpdate'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const saving = ref(false)
const showSearch = ref(true)
const open = ref(false)
const rows = ref([])
const total = ref(0)

const emptyForm = () => ({
  id: undefined,
  appId: '__UNI__200F612',
  packageName: '',
  channelCode: 'official',
  versionName: '',
  versionCode: 102,
  updateMode: 'NORMAL',
  minSupportedVersionCode: 0,
  policyRevision: 1,
  title: '发现新版本',
  changelog: '',
  downloadUrl: '',
  remindLaterHours: 6,
  apkSizeBytes: undefined,
  apkSha256: '',
  publishAt: ''
})

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, keyword: '', status: '' },
  form: emptyForm(),
  rules: {
    appId: [{ required: true, message: '请输入 AppID', trigger: 'blur' }],
    packageName: [{ required: true, message: '请输入安卓包名', trigger: 'blur' }],
    versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
    versionCode: [{ required: true, message: '请输入 versionCode', trigger: 'change' }],
    title: [{ required: true, message: '请输入更新标题', trigger: 'blur' }],
    changelog: [{ required: true, message: '请输入更新说明', trigger: 'blur' }],
    downloadUrl: [
      { required: true, message: '请输入 APK 下载地址', trigger: 'blur' },
      { pattern: /^https:\/\/[^\s]+$/i, message: '下载地址必须使用 HTTPS', trigger: 'blur' }
    ]
  }
})
const { queryParams, form, rules } = toRefs(data)
const dialogTitle = computed(() => form.value.id ? '编辑安卓版本' : '新建安卓版本')

function load() {
  loading.value = true
  listAppReleases(queryParams.value).then(res => {
    rows.value = res.rows || []
    total.value = res.total || 0
  }).catch(error => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载版本列表失败')))
    .finally(() => { loading.value = false })
}

function handleQuery() { queryParams.value.pageNum = 1; load() }
function resetQuery() { Object.assign(queryParams.value, { pageNum: 1, keyword: '', status: '' }); load() }
function openCreate() { form.value = emptyForm(); open.value = true; nextTick(() => proxy.resetForm('formRef')) }
function openEdit(row) {
  getAppRelease(row.id).then(res => {
    form.value = { ...emptyForm(), ...(res.data || {}) }
    open.value = true
  }).catch(error => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '读取版本失败')))
}

function submit() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    saving.value = true
    const action = form.value.id ? updateAppRelease : addAppRelease
    action({ ...form.value }).then(() => {
      proxy.$modal.msgSuccess('版本草稿已保存')
      open.value = false
      load()
    }).catch(error => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存版本失败')))
      .finally(() => { saving.value = false })
  })
}

function publishRow(row) {
  proxy.$modal.confirm(`确认发布 V${row.versionName}（${row.versionCode}）吗？发布后不能直接修改。`)
    .then(() => publishAppRelease(row.id)).then(() => { proxy.$modal.msgSuccess('版本已发布'); load() })
    .catch(error => { if (!isMessageBoxCancelled(error)) proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '发布失败')) })
}

function revokeRow(row) {
  proxy.$modal.confirm(`确认下架 V${row.versionName} 吗？下架后客户端将不再收到此版本。`)
    .then(() => revokeAppRelease(row.id)).then(() => { proxy.$modal.msgSuccess('版本已下架'); load() })
    .catch(error => { if (!isMessageBoxCancelled(error)) proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '下架失败')) })
}

function remindAgain(row) {
  proxy.$modal.confirm(`确认让忽略过 V${row.versionName} 的用户再次收到更新提醒吗？`)
    .then(() => remindAppReleaseAgain(row.id)).then(() => { proxy.$modal.msgSuccess('已重新启用提醒'); load() })
    .catch(error => { if (!isMessageBoxCancelled(error)) proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '操作失败')) })
}

function deleteRow(row) {
  proxy.$modal.confirm(`确认删除草稿 V${row.versionName} 吗？`)
    .then(() => deleteAppRelease(row.id)).then(() => { proxy.$modal.msgSuccess('草稿已删除'); load() })
    .catch(error => { if (!isMessageBoxCancelled(error)) proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除失败')) })
}

function isScheduled(row) { return row.status === 'PUBLISHED' && row.publishAt && new Date(row.publishAt.replace(' ', 'T')).getTime() > Date.now() }
function statusText(row) { return row.status === 'DRAFT' ? '草稿' : row.status === 'REVOKED' ? '已下架' : isScheduled(row) ? '待发布' : '已发布' }
function statusType(row) { return row.status === 'DRAFT' ? 'info' : row.status === 'REVOKED' ? 'warning' : isScheduled(row) ? 'primary' : 'success' }

load()
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.version-cell { display: flex; flex-direction: column; gap: 4px; }
.version-cell strong { color: var(--el-text-color-primary); }
.version-cell span { color: var(--el-text-color-secondary); font-size: 12px; }
.unit { margin-left: 8px; color: var(--el-text-color-secondary); }
</style>
