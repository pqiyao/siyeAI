<template>
  <div class="app-container">
    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="标题">
        <el-input
          v-model="queryParams.title"
          placeholder="标题关键词"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
      <el-table-column label="展示方式" prop="displayType" width="110">
        <template #default="scope">
          <el-tag :type="displayTypeTag(scope.row.displayType)" effect="plain">
            {{ displayTypeLabel(scope.row.displayType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" />
      <el-table-column label="启用" prop="enabled" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="游客可见" prop="guestVisible" width="100">
        <template #default="scope">
          {{ scope.row.guestVisible ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog :title="title" v-model="open" width="640px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" />
        </el-form-item>
        <el-form-item label="展示方式">
          <el-select v-model="form.displayType" style="width: 220px">
            <el-option label="只进消息页" value="inbox" />
            <el-option label="首页横幅" value="banner" />
            <el-option label="重要弹层" value="popup" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="游客可见">
          <el-switch v-model="form.guestVisible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgNotice">
import { listJgNotice, getJgNotice, addJgNotice, updateJgNotice, delJgNotice } from '@/api/jiugai/notice'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

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
  sortOrder: 0,
  enabled: true,
  guestVisible: true,
  displayType: 'inbox'
})

const data = reactive({
  form: emptyForm(),
  queryParams: { pageNum: 1, pageSize: 10, title: undefined },
  rules: {
    title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
    content: [{ required: true, message: '内容不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listJgNotice(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载公告失败'))
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
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function displayTypeLabel(value) {
  const map = {
    inbox: '消息页',
    banner: '首页横幅',
    popup: '重要弹层'
  }
  return map[value] || map.inbox
}

function displayTypeTag(value) {
  const map = {
    inbox: 'info',
    banner: 'success',
    popup: 'warning'
  }
  return map[value] || map.inbox
}

function reset() {
  form.value = emptyForm()
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增公告'
}

function handleUpdate(row) {
  reset()
  const id = row?.id || ids.value[0]
  if (!id) return
  getJgNotice(id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      open.value = true
      title.value = '修改公告'
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取公告失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const api = form.value.id ? updateJgNotice : addJgNotice
    api(form.value)
      .then(() => {
        proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存失败'))
      })
  })
}

function handleDelete(row) {
  const delIds = row?.id || ids.value.join(',')
  if (!delIds) return
  proxy.$modal
    .confirm('是否确认删除？')
    .then(() => delJgNotice(delIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除失败'))
    })
}

getList()
</script>
