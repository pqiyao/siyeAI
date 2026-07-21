<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里维护 H5 充值与 VIP 套餐。当前支付先走模拟链路，但商品、订单、到账都已经接入 SillySpringboot 真库。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="商品编码 / 名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.productType" placeholder="全部" clearable style="width: 140px">
          <el-option label="钻石商品" value="COIN" />
          <el-option label="VIP 商品" value="VIP" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" icon="Plus" @click="handleAdd">新增商品</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="商品编码" prop="code" min-width="140" show-overflow-tooltip />
      <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" prop="productType" width="100" />
      <el-table-column label="价格" width="120">
        <template #default="scope">
          ¥ {{ ((scope.row.priceCents || 0) / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="钻石" prop="scoreAmount" width="90" />
      <el-table-column label="金币" prop="goldCoinAmount" width="90" />
      <el-table-column label="VIP天数" prop="vipDays" width="100" />
      <el-table-column label="角标" prop="badgeLabel" width="140" show-overflow-tooltip />
      <el-table-column label="启用" width="84">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
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

    <el-dialog :title="title" v-model="open" width="760px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品编码" prop="code">
              <el-input v-model="form.code" placeholder="例如 vip_month" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="商品类型" prop="productType">
              <el-select v-model="form.productType">
                <el-option label="钻石商品" value="COIN" />
                <el-option label="VIP 商品" value="VIP" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="价格(分)" prop="priceCents">
              <el-input-number v-model="form.priceCents" :min="0" :step="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="赠送钻石">
              <el-input-number v-model="form.scoreAmount" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="赠送金币">
              <el-input-number v-model="form.goldCoinAmount" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="VIP天数">
              <el-input-number v-model="form.vipDays" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="VIP等级">
              <el-input-number v-model="form.vipType" :min="0" :max="2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签">
              <el-input v-model="form.tagLabel" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="角标">
              <el-input v-model="form.badgeLabel" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgStoreProduct">
import { addStoreProduct, getStoreProduct, listStoreProduct, updateStoreProduct } from '@/api/jiugai/storeproduct'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const emptyForm = () => ({
  id: undefined,
  code: '',
  name: '',
  subtitle: '',
  productType: 'COIN',
  priceCents: 0,
  scoreAmount: 0,
  goldCoinAmount: 0,
  vipType: 0,
  vipDays: 0,
  tagLabel: '',
  badgeLabel: '',
  enabled: true,
  sortOrder: 0
})

const data = reactive({
  form: emptyForm(),
  queryParams: { pageNum: 1, pageSize: 10, keyword: undefined, productType: undefined },
  rules: {
    code: [{ required: true, message: '商品编码不能为空', trigger: 'blur' }],
    name: [{ required: true, message: '商品名称不能为空', trigger: 'blur' }],
    productType: [{ required: true, message: '商品类型不能为空', trigger: 'change' }]
  }
})

const { form, queryParams, rules } = toRefs(data)

function getList() {
  loading.value = true
  listStoreProduct(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载商品列表失败'))
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
  queryParams.value.productType = undefined
  handleQuery()
}

function handleAdd() {
  form.value = emptyForm()
  title.value = '新增商品'
  open.value = true
}

function handleUpdate(row) {
  getStoreProduct(row.id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      title.value = '编辑商品'
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取商品详情失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const action = form.value.id ? updateStoreProduct : addStoreProduct
    action({ ...form.value })
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存商品失败'))
      })
  })
}

getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}
</style>
