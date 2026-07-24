<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      :title="pageTip"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item :label="labels.title">
        <el-input
          v-model="queryParams.title"
          :placeholder="labels.titleKeyword"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="labels.enabled">
        <el-select v-model="queryParams.enabled" clearable :placeholder="labels.all" style="width: 120px">
          <el-option :label="labels.yes" :value="true" />
          <el-option :label="labels.no" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">{{ labels.search }}</el-button>
        <el-button icon="Refresh" @click="resetQuery">{{ labels.reset }}</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">{{ labels.add }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">{{ labels.edit }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">{{ labels.remove }}</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column :label="labels.preview" width="96" align="center">
        <template #default="scope">
          <el-image
            v-if="scope.row.imageUrl"
            style="width: 56px; height: 56px; border-radius: 8px"
            :src="displayUploadUrl(scope.row.imageUrl)"
            :preview-src-list="[displayUploadUrl(scope.row.imageUrl)]"
            preview-teleported
            fit="cover"
          />
          <span v-else class="muted">{{ labels.noImage }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="labels.title" prop="title" min-width="140" show-overflow-tooltip />
      <el-table-column :label="labels.content" prop="content" min-width="180" show-overflow-tooltip />
      <el-table-column :label="labels.link" prop="linkUrl" min-width="140" show-overflow-tooltip />
      <el-table-column :label="labels.sort" prop="sortOrder" width="80" />
      <el-table-column :label="labels.enabled" prop="enabled" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? labels.yes : labels.no }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="labels.startAt" prop="startAt" width="170" />
      <el-table-column :label="labels.endAt" prop="endAt" width="170" />
      <el-table-column :label="labels.updateTime" prop="updateTime" width="170" />
      <el-table-column :label="labels.ops" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">{{ labels.edit }}</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">{{ labels.remove }}</el-button>
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

    <el-dialog :title="title" v-model="open" width="680px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="108px">
        <el-form-item :label="labels.title" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit :placeholder="labels.titlePh" />
        </el-form-item>
        <el-form-item :label="labels.content" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :placeholder="labels.contentPh"
          />
        </el-form-item>
        <el-form-item :label="labels.image">
          <div class="upload-row">
            <el-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeImageUpload"
              :on-success="onImageUploadSuccess"
              :on-error="onImageUploadError"
              accept="image/png,image/jpeg,image/webp,image/gif"
            >
              <el-button type="primary" plain>{{ labels.upload }}</el-button>
            </el-upload>
            <el-button v-if="form.imageUrl" link type="danger" @click="form.imageUrl = ''">{{ labels.clearImage }}</el-button>
          </div>
          <el-input v-model="form.imageUrl" class="mt8" :placeholder="labels.imagePh" />
          <el-image
            v-if="form.imageUrl"
            class="preview-img"
            :src="displayUploadUrl(form.imageUrl)"
            :preview-src-list="[displayUploadUrl(form.imageUrl)]"
            preview-teleported
            fit="cover"
          />
        </el-form-item>
        <el-form-item :label="labels.link">
          <el-input v-model="form.linkUrl" :placeholder="labels.linkPh" />
        </el-form-item>
        <el-form-item :label="labels.sort">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999999" />
          <span class="hint">{{ labels.sortHint }}</span>
        </el-form-item>
        <el-form-item :label="labels.enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item :label="labels.startAt">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="labels.startPh"
            clearable
          />
        </el-form-item>
        <el-form-item :label="labels.endAt">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="labels.endPh"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">{{ labels.ok }}</el-button>
        <el-button @click="open = false">{{ labels.cancel }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgInboxAd">
import { getToken } from '@/utils/auth'
import { listJgInboxAd, getJgInboxAd, addJgInboxAd, updateJgInboxAd, delJgInboxAd } from '@/api/jiugai/inboxAd'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const labels = Object.freeze({
  title: '\u6807\u9898',
  titleKeyword: '\u6807\u9898\u5173\u952e\u8bcd',
  enabled: '\u542f\u7528',
  all: '\u5168\u90e8',
  yes: '\u662f',
  no: '\u5426',
  search: '\u641c\u7d22',
  reset: '\u91cd\u7f6e',
  add: '\u65b0\u589e',
  edit: '\u4fee\u6539',
  remove: '\u5220\u9664',
  preview: '\u9884\u89c8\u56fe',
  noImage: '\u65e0\u56fe',
  content: '\u6587\u6848',
  link: '\u8df3\u8f6c',
  sort: '\u6392\u5e8f',
  startAt: '\u751f\u6548\u5f00\u59cb',
  endAt: '\u751f\u6548\u7ed3\u675f',
  updateTime: '\u66f4\u65b0\u65f6\u95f4',
  ops: '\u64cd\u4f5c',
  titlePh: '\u5217\u8868\u4e2d\u5c55\u793a\u7684\u4e3b\u6807\u9898',
  contentPh: '\u6d3b\u52a8\u8bf4\u660e\u6b63\u6587\uff0c\u53ef\u7559\u7a7a',
  image: '\u5e7f\u544a\u56fe',
  upload: '\u4e0a\u4f20\u56fe\u7247',
  clearImage: '\u6e05\u9664\u56fe\u7247',
  imagePh: '\u4e5f\u53ef\u7c98\u8d34 /uploads/... \u6216 http(s) \u56fe\u7247\u5730\u5740',
  linkPh: '\u53ef\u9009\u3002\u7ad9\u5185\u5982 /pages/user/vip\uff0c\u5916\u94fe\u5982 https://example.com',
  sortHint: '\u6570\u5b57\u8d8a\u5927\u8d8a\u9760\u524d',
  startPh: '\u7559\u7a7a\u8868\u793a\u7acb\u5373\u751f\u6548',
  endPh: '\u7559\u7a7a\u8868\u793a\u957f\u671f\u6709\u6548',
  ok: '\u786e\u5b9a',
  cancel: '\u53d6\u6d88'
})

const pageTip =
  '\u4f1a\u8bdd\u5e7f\u544a\u6309\u300c\u7cfb\u7edf\u516c\u544a\u300d\u540c\u6b3e\u6a21\u5f0f\u6295\u653e\uff1a\u7528\u6237\u5728\u4f1a\u8bdd Tab \u770b\u5230\u5e38\u9a7b\u5165\u53e3\u300c\u6d3b\u52a8\u63a8\u8350\u300d\uff0c\u70b9\u8fdb\u5217\u8868\u53ef\u53cd\u590d\u67e5\u770b\u3002\u53ef\u5199\u6587\u6848\u3001\u4e0a\u4f20\u56fe\u7247\u3001\u914d\u7f6e\u8df3\u8f6c\uff1b\u542f\u7528\u4e14\u5728\u6709\u6548\u671f\u5185\u7684\u5e7f\u544a\u90fd\u4f1a\u51fa\u73b0\u5728\u5217\u8868\u4e2d\u3002'

const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const useProxyUploadsInAdmin = import.meta.env.DEV
const uploadAction = baseApi + '/admin/jiugai/upload/image'
const uploadHeaders = ref({ Authorization: 'Bearer ' + getToken() })

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const emptyForm = () => ({
  id: undefined,
  title: '',
  content: '',
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  enabled: true,
  startAt: '',
  endAt: ''
})

const data = reactive({
  form: emptyForm(),
  queryParams: { pageNum: 1, pageSize: 10, title: undefined, enabled: undefined },
  rules: {}
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  uploadHeaders.value = { Authorization: 'Bearer ' + getToken() }
  listJgInboxAd(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '\u52a0\u8f7d\u4f1a\u8bdd\u5e7f\u544a\u5931\u8d25'))
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
  queryParams.value.title = undefined
  queryParams.value.enabled = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function reset() {
  form.value = emptyForm()
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '\u65b0\u589e\u4f1a\u8bdd\u5e7f\u544a'
}

function handleUpdate(row) {
  reset()
  const id = row?.id || ids.value[0]
  if (!id) return
  getJgInboxAd(id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      open.value = true
      title.value = '\u4fee\u6539\u4f1a\u8bdd\u5e7f\u544a'
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '\u83b7\u53d6\u4f1a\u8bdd\u5e7f\u544a\u5931\u8d25'))
    })
}

function submitForm() {
  const payload = { ...form.value }
  if (!String(payload.title || '').trim() && !String(payload.content || '').trim() && !String(payload.imageUrl || '').trim()) {
    proxy.$modal.msgError('\u8bf7\u81f3\u5c11\u586b\u5199\u6807\u9898\u3001\u6587\u6848\u6216\u4e0a\u4f20\u56fe\u7247\u5176\u4e00')
    return
  }
  const api = payload.id ? updateJgInboxAd : addJgInboxAd
  api(payload)
    .then(() => {
      proxy.$modal.msgSuccess(payload.id ? '\u4fee\u6539\u6210\u529f' : '\u65b0\u589e\u6210\u529f')
      open.value = false
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '\u4fdd\u5b58\u5931\u8d25'))
    })
}

function handleDelete(row) {
  const delIds = row?.id || ids.value.join(',')
  if (!delIds) return
  proxy.$modal
    .confirm('\u662f\u5426\u786e\u8ba4\u5220\u9664\u6240\u9009\u4f1a\u8bdd\u5e7f\u544a\uff1f')
    .then(() => delJgInboxAd(delIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('\u5220\u9664\u6210\u529f')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '\u5220\u9664\u5931\u8d25'))
    })
}

function beforeImageUpload(file) {
  const okType = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.type)
  if (!okType) {
    proxy.$modal.msgError('\u4ec5\u652f\u6301 PNG / JPG / WEBP / GIF')
    return false
  }
  if (file.size > 8 * 1024 * 1024) {
    proxy.$modal.msgError('\u56fe\u7247\u4e0d\u80fd\u8d85\u8fc7 8MB')
    return false
  }
  uploadHeaders.value = { Authorization: 'Bearer ' + getToken() }
  return true
}

function onImageUploadSuccess(res) {
  if (res && (res.code === 200 || res.code === 0 || res.fileName)) {
    form.value.imageUrl = res.fileName || ''
    proxy.$modal.msgSuccess('\u56fe\u7247\u5df2\u4e0a\u4f20')
    return
  }
  proxy.$modal.msgError((res && res.msg) || '\u4e0a\u4f20\u5931\u8d25')
}

function onImageUploadError() {
  proxy.$modal.msgError('\u4e0a\u4f20\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6743\u9650\u6216\u7f51\u7edc')
}

function displayUploadUrl(url) {
  const value = (url || '').trim()
  if (!value) return ''
  if (value.startsWith('/uploads/') || value.startsWith('/art/')) {
    return useProxyUploadsInAdmin ? baseApi + value : value
  }
  return value
}

getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}
.mt8 {
  margin-top: 8px;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.preview-img {
  margin-top: 10px;
  width: 160px;
  height: 90px;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f7fa;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
