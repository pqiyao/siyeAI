<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里集中维护 H5 用户资料、额度和 ST 运行时名字；会话级覆盖名与 worldbooks 也能在这里直接调整。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="账号 / 昵称 / 用户名"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会员等级">
        <el-select v-model="queryParams.vipType" clearable placeholder="全部" style="width: 140px">
          <el-option label="免费用户" :value="0" />
          <el-option label="VIP" :value="1" />
          <el-option label="SVIP" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户类型">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="匿名设备" value="anonymous" />
          <el-option label="Telegram" value="telegram" />
          <el-option label="平台账号" value="platform" />
          <el-option label="正常" value="normal" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </el-form-item>
      <el-form-item label="资料状态">
        <el-select v-model="queryParams.needEdit" clearable placeholder="全部" style="width: 140px">
          <el-option label="待完善" :value="1" />
          <el-option label="已完善" :value="0" />
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
          v-hasPermi="['commerce:user:batch-policy', 'commerce:user:edit']"
          type="success"
          plain
          icon="CircleCheck"
          :disabled="multiple"
          :loading="batchCharacterCreateSubmitting"
          @click="handleBatchCharacterCreateAllowed(true)"
        >
          批量开启自建
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['commerce:user:batch-policy', 'commerce:user:edit']"
          type="warning"
          plain
          icon="CircleClose"
          :disabled="multiple"
          :loading="batchCharacterCreateSubmitting"
          @click="handleBatchCharacterCreateAllowed(false)"
        >
          批量关闭自建
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['commerce:user:delete', 'commerce:user:edit']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">批量删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="昵称" prop="nickname" min-width="140" show-overflow-tooltip />
      <el-table-column label="账号标识" prop="account" min-width="170" show-overflow-tooltip />
      <el-table-column label="默认 ST 名" prop="stDisplayName" min-width="140" show-overflow-tooltip />
      <el-table-column label="用户类型" width="110">
        <template #default="scope">
          <el-tag :type="identityTagType(scope.row.identityType)">{{ identityLabel(scope.row.identityType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'disabled' ? 'danger' : 'success'">
            {{ scope.row.status === 'disabled' ? '停用' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="会员" width="110">
        <template #default="scope">
          <el-tag :type="vipTagType(scope.row)">
            {{ vipLabel(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="聊天额度" width="120">
        <template #default="scope">
          {{ scope.row.dailyChatUsed || 0 }}/{{ scope.row.dailyChatQuota || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="自建角色" width="96" align="center">
        <template #default="scope">
          <el-tag :type="Number(scope.row.characterCreateAllowed || 0) === 1 ? 'success' : 'info'">
            {{ Number(scope.row.characterCreateAllowed || 0) === 1 ? '允许' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模型配置" min-width="210" show-overflow-tooltip>
        <template #default="scope">
          <div class="model-provider-cell">
            <el-tag :type="isCustomAiProvider(scope.row) ? 'warning' : 'info'" size="small">
              {{ aiProviderModeLabel(scope.row) }}
            </el-tag>
            <span class="model-provider-text">{{ aiProviderSummary(scope.row) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="BYOK次数" width="96">
        <template #default="scope">
          {{ scope.row.dailyByokChatUsed || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="最近对话" prop="lastConversationAt" width="168" />
      <el-table-column label="创建时间" prop="createTime" width="168" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['commerce:user:update', 'commerce:user:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑资料</el-button>
          <el-button link type="primary" icon="Tickets" @click="openUsageLogs(scope.row)">消耗记录</el-button>
          <el-button v-if="canResetPassword(scope.row)" link type="warning" icon="Key" @click="handlePasswordReset(scope.row)">
            账号安全
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

    <el-dialog :title="title" v-model="open" width="980px" append-to-body destroy-on-close>
      <el-alert
        class="mb12"
        type="info"
        :closable="false"
        show-icon
        title="默认 ST 名是这个用户发给 ST 运行时的默认 user_name；下面的会话运行时列表可以单独改某个角色会话的覆盖名和 worldbooks。"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="116px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="账号标识">
              <el-input :model-value="form.account" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户类型">
              <el-input :model-value="identityLabel(form.identityType)" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账号状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="正常" value="normal" />
                <el-option label="停用" value="disabled" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="默认 ST 名">
              <el-input v-model="form.stDisplayName" placeholder="留空时回退到资料昵称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="头像 URL">
              <el-input v-model="form.avatar" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="个人简介">
          <el-input v-model="form.bio" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item label="聊天人设">
          <el-input v-model="form.persona" type="textarea" :rows="4" />
        </el-form-item>

        <el-divider content-position="left">会员与钱包</el-divider>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="会员等级">
              <el-select v-model="form.vipType" style="width: 100%" @change="handleVipTypeChange">
                <el-option label="免费" :value="0" />
                <el-option label="VIP" :value="1" />
                <el-option label="SVIP" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="钻石">
              <el-input-number v-model="form.score" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金币">
              <el-input-number v-model="form.goldCoin" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="会员到期">
              <div class="vip-expiry-field">
                <el-date-picker
                  v-model="form.vipExpiresAt"
                  class="vip-expiry-picker"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :disabled="Number(form.vipType || 0) <= 0"
                  clearable
                  placeholder="请选择会员到期时间"
                />
                <div class="vip-expiry-shortcuts">
                  <el-button size="small" @click="setVipExpiryTodayEnd">今天到期</el-button>
                  <el-button size="small" @click="applyVipExpiryPreset(7)">顺延 7 天</el-button>
                  <el-button size="small" @click="applyVipExpiryPreset(30)">顺延 30 天</el-button>
                  <el-button size="small" @click="applyVipExpiryPreset(90)">顺延 90 天</el-button>
                  <el-button size="small" @click="applyVipExpiryPreset(365)">顺延 365 天</el-button>
                  <el-button size="small" type="danger" plain @click="clearVipExpiry">清空</el-button>
                </div>
                <div class="muted mt8 vip-expiry-note">
                  默认优先从当前到期时间顺延；如果已经过期，则从现在开始计算。
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资料待完善">
              <el-radio-group v-model="form.needEdit">
                <el-radio :label="1">是</el-radio>
                <el-radio :label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">额度控制</el-divider>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="自建角色卡">
              <el-switch
                v-model="form.characterCreateAllowed"
                :active-value="1"
                :inactive-value="0"
                active-text="允许"
                inactive-text="关闭"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="当前聊天总额度">
              <el-input :model-value="String(form.dailyChatQuota || 0)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="当前生图总额度">
              <el-input :model-value="String(form.dailyImageQuota || 0)" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="聊天额度覆盖">
              <el-input
                v-model="form.chatQuotaOverrideInput"
                placeholder="留空表示跟随全局权益配置"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生图额度覆盖">
              <el-input
                v-model="form.imageQuotaOverrideInput"
                placeholder="留空表示跟随全局权益配置"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="聊天已用次数">
              <el-input-number v-model="form.dailyChatUsed" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生图已用次数">
              <el-input-number v-model="form.dailyImageUsed" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">自定义 API Key 状态</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模型模式">
              <el-input :model-value="aiProviderModeLabel(form)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="平台">
              <el-input :model-value="aiProviderSourceLabel(form.aiProviderSource)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Key 状态">
              <el-input :model-value="Number(form.aiApiKeySaved || 0) ? '已保存' : '未保存'" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模型">
              <el-input :model-value="form.aiModelName || '未填写'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BYOK今日已用">
              <el-input :model-value="String(form.dailyByokChatUsed || 0)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="更新时间">
              <el-input :model-value="form.aiProviderUpdatedAt || '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-collapse>
          <el-collapse-item title="更多资料字段" name="more">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="性别">
                  <el-select v-model="form.gender" style="width: 100%">
                    <el-option label="未知" :value="0" />
                    <el-option label="男" :value="1" />
                    <el-option label="女" :value="2" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="生日">
                  <el-input v-model="form.birthday" placeholder="例如：2000-01-01" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="国家/地区">
                  <el-input v-model="form.country" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="职业">
                  <el-input v-model="form.occupation" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="身高">
                  <el-input v-model="form.height" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="体重">
                  <el-input v-model="form.weight" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="角色倾向">
              <el-input v-model="form.characters" />
            </el-form-item>

            <el-form-item label="关系描述">
              <el-input v-model="form.relation" />
            </el-form-item>

            <el-form-item label="自定义标签">
              <el-input v-model="form.label" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <el-divider content-position="left">最近会话运行时</el-divider>

        <el-empty v-if="!conversationRows.length" description="该用户最近没有可管理的会话" />
        <el-table v-else :data="conversationRows" size="small" border>
          <el-table-column label="会话" min-width="220">
            <template #default="scope">
              <div class="mini-stack">
                <span>{{ scope.row.title }}</span>
                <span class="muted">#{{ scope.row.conversationId }} · {{ scope.row.characterName || '未命名角色' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="生效 ST 名" prop="effectiveStDisplayName" min-width="140" show-overflow-tooltip />
          <el-table-column label="覆盖名" prop="stDisplayNameOverride" min-width="120" show-overflow-tooltip />
          <el-table-column label="Worldbooks" min-width="180">
            <template #default="scope">
              <span>{{ formatWorldNames(scope.row.worldNames) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" prop="updatedAt" width="168" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openConversationEditor(scope.row)">编辑运行时</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button v-hasPermi="['commerce:user:update', 'commerce:user:edit']" type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="账号安全" v-model="passwordOpen" width="520px" append-to-body destroy-on-close>
      <el-alert
        class="mb12"
        type="warning"
        :closable="false"
        show-icon
        :title="`正在重置用户 #${passwordForm.id} 的登录密码，只影响这个平台账号。`"
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="120px">
        <el-form-item label="账号标识">
          <el-input v-model="passwordForm.account" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password maxlength="64" />
        </el-form-item>
        <el-form-item label="踢下线">
          <el-switch
            v-model="passwordForm.revokeSessions"
            active-text="重置后重新登录"
            inactive-text="保留当前登录"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="passwordSaving" @click="submitPasswordReset">确认重置</el-button>
        <el-button @click="passwordOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog
      title="编辑会话运行时"
      v-model="conversationOpen"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-alert
        class="mb12"
        type="info"
        :closable="false"
        show-icon
        title="会话覆盖名只影响这一条角色会话；worldbooks 现在直接从 ST worldinfo 列表里选择，避免手填文件名出错。"
      />
      <el-alert
        v-if="worldbookOptionsError"
        class="mb12"
        type="warning"
        :closable="false"
        show-icon
        :title="worldbookOptionsError"
      />
      <el-form :model="conversationForm" label-width="120px">
        <el-form-item label="会话">
          <el-input :model-value="conversationForm.title" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="conversationForm.characterName" disabled />
        </el-form-item>
        <el-form-item label="会话覆盖名">
          <el-input
            v-model="conversationForm.stDisplayNameOverride"
            placeholder="留空时回退到用户默认 ST 名"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="Worldbooks">
          <el-select
            v-model="conversationForm.worldNames"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="10"
            :loading="worldbookOptionsLoading"
            placeholder="选择当前会话要绑定的 ST worldbooks"
            style="width: 100%"
          >
            <el-option
              v-for="item in conversationWorldbookOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="muted mt8">
            最多绑定 10 个；带“已失效”的项表示会话里还残留旧 worldbook，保存前建议清理掉。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-hasPermi="['conversation:runtime:edit']" type="primary" :loading="conversationSaving" @click="submitConversationForm">保存运行时</el-button>
        <el-button @click="conversationOpen = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgH5User">
import {
  batchUpdateJgH5UserCharacterCreateAllowed,
  delJgH5User,
  getJgH5User,
  listJgConversationWorldbookOptions,
  listJgH5User,
  resetJgH5UserPassword,
  updateJgConversationStDisplayName,
  updateJgConversationWorldbooks,
  updateJgH5User
} from '@/api/jiugai/h5user'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(true)
const showSearch = ref(true)
const dataList = ref([])
const total = ref(0)
const title = ref('')
const open = ref(false)
const formRef = ref()
const ids = ref([])
const multiple = ref(true)
const passwordOpen = ref(false)
const passwordSaving = ref(false)
const batchCharacterCreateSubmitting = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({
  id: undefined,
  account: '',
  password: '',
  revokeSessions: true
})

const conversationRows = ref([])
const conversationOpen = ref(false)
const conversationSaving = ref(false)
const worldbookOptions = ref([])
const worldbookOptionsLoading = ref(false)
const worldbookOptionsError = ref('')
const conversationForm = reactive(emptyConversationForm())

function emptyForm() {
  return {
    id: undefined,
    account: '',
    passwordLogin: 0,
    nickname: '',
    avatar: '',
    bio: '',
    persona: '',
    stDisplayName: '',
    identityType: 'platform',
    vipType: 0,
    vipExpiresAt: '',
    score: 0,
    goldCoin: 0,
    dailyChatQuota: 0,
    chatQuotaOverride: null,
    chatQuotaOverrideInput: '',
    dailyChatUsed: 0,
    dailyByokChatUsed: 0,
    aiProviderMode: 'system',
    aiProviderSource: '',
    aiModelName: '',
    aiCustomUrl: '',
    aiApiKeySaved: 0,
    aiProviderUpdatedAt: '',
    dailyImageQuota: 0,
    imageQuotaOverride: null,
    imageQuotaOverrideInput: '',
    dailyImageUsed: 0,
    characterCreateAllowed: 0,
    needEdit: 0,
    status: 'normal',
    gender: 0,
    birthday: '',
    height: '',
    weight: '',
    country: '',
    characters: '',
    relation: '',
    occupation: '',
    label: '',
    conversationCount: 0,
    lastConversationAt: ''
  }
}

function emptyConversationForm() {
  return {
    conversationId: undefined,
    title: '',
    characterName: '',
    stDisplayNameOverride: '',
    worldNames: []
  }
}

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    vipType: undefined,
    status: undefined,
    needEdit: undefined
  },
  rules: {
    nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }]
  },
  passwordRules: {
    password: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 6, max: 64, message: '密码长度必须为 6-64 位', trigger: 'blur' }
    ]
  }
})

const { queryParams, form, rules, passwordRules } = toRefs(data)

function identityLabel(type) {
  if (type === 'anonymous') return '匿名设备'
  if (type === 'telegram') return 'Telegram'
  return '平台账号'
}

function identityTagType(type) {
  if (type === 'anonymous') return 'warning'
  if (type === 'telegram') return 'success'
  return 'info'
}

function canResetPassword(row) {
  return hasAnyPermi(['commerce:user:security', 'commerce:user:edit']) && row?.identityType === 'platform' && Number(row?.passwordLogin || 0) === 1
}

function hasAnyPermi(permissions) {
  return proxy?.$auth?.hasPermiOr ? proxy.$auth.hasPermiOr(permissions) : permissions.some((item) => proxy?.$auth?.hasPermi(item))
}

const AI_PROVIDER_LABELS = {
  siliconflow: '硅基流动',
  deepseek: 'DeepSeek',
  openrouter: 'OpenRouter',
  openai: 'OpenAI',
  groq: 'Groq',
  mistralai: 'Mistral AI',
  moonshot: 'Moonshot',
  xai: 'xAI',
  fireworks: 'Fireworks',
  custom: '自定义接口'
}

function isCustomAiProvider(row) {
  return String(row?.aiProviderMode || 'system') === 'custom'
}

function aiProviderModeLabel(row) {
  return isCustomAiProvider(row) ? '自定义 API' : '官方 API'
}

function aiProviderSourceLabel(source) {
  const key = String(source || '').trim()
  return AI_PROVIDER_LABELS[key] || key || '未选择'
}

function aiProviderSummary(row) {
  if (!isCustomAiProvider(row)) return '官方模型服务'
  const source = aiProviderSourceLabel(row?.aiProviderSource)
  const model = String(row?.aiModelName || '').trim() || '未填写模型'
  const key = Number(row?.aiApiKeySaved || 0) ? '已保存Key' : '未保存Key'
  return `${source} / ${model} / ${key}`
}

function parseDateValue(value) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return null
  const date = new Date(text.replace(/-/g, '/'))
  return Number.isNaN(date.getTime()) ? null : date
}

function isVipActive(row) {
  const vipType = Number(row?.vipType || 0)
  if (vipType <= 0) return false
  if (row?.vipActive === 1 || row?.vipActive === '1' || row?.vipActive === true) return true
  if (row?.vipActive === 0 || row?.vipActive === '0' || row?.vipActive === false) return false
  const expiresAt = parseDateValue(row?.vipExpiresAt)
  return !!(expiresAt && expiresAt.getTime() > Date.now())
}

function padDateNumber(value) {
  return String(value).padStart(2, '0')
}

function formatDateTimeValue(value) {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return ''
  return [
    value.getFullYear(),
    padDateNumber(value.getMonth() + 1),
    padDateNumber(value.getDate())
  ].join('-') + ' ' + [
    padDateNumber(value.getHours()),
    padDateNumber(value.getMinutes()),
    padDateNumber(value.getSeconds())
  ].join(':')
}

function setDateToDayEnd(date) {
  date.setHours(23, 59, 59, 0)
  return date
}

function resolveVipExpiryBaseDate() {
  const current = parseDateValue(form.value.vipExpiresAt)
  const now = new Date()
  if (current && current.getTime() > now.getTime()) {
    return new Date(current.getTime())
  }
  return now
}

function ensureVipTypeSelected() {
  if (Number(form.value.vipType || 0) > 0) return true
  proxy.$modal.msgWarning('请先选择 VIP 或 SVIP')
  return false
}

function applyVipExpiryPreset(days) {
  if (!ensureVipTypeSelected()) return
  const safeDays = Math.max(0, Number(days) || 0)
  const next = resolveVipExpiryBaseDate()
  next.setDate(next.getDate() + safeDays)
  form.value.vipExpiresAt = formatDateTimeValue(setDateToDayEnd(next))
}

function setVipExpiryTodayEnd() {
  if (!ensureVipTypeSelected()) return
  form.value.vipExpiresAt = formatDateTimeValue(setDateToDayEnd(new Date()))
}

function clearVipExpiry() {
  form.value.vipExpiresAt = ''
}

function handleVipTypeChange(value) {
  if (Number(value || 0) <= 0) {
    clearVipExpiry()
  }
}

function vipLabel(row) {
  const type = Number(row?.vipType || 0)
  if (type >= 2) return isVipActive(row) ? 'SVIP' : 'SVIP（未生效）'
  if (type === 1) return isVipActive(row) ? 'VIP' : 'VIP（未生效）'
  return '免费'
}

function vipTagType(row) {
  const type = Number(row?.vipType || 0)
  if (type > 0 && !isVipActive(row)) return 'info'
  if (type >= 2) return 'danger'
  if (type === 1) return 'warning'
  return 'info'
}

function toNullableInteger(value) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return null
  const n = Number(text)
  return Number.isFinite(n) ? Math.max(0, Math.floor(n)) : null
}

function normalizeConversationRow(row) {
  const worldNames = Array.isArray(row?.worldNames) ? row.worldNames.filter(Boolean) : []
  return {
    ...row,
    worldNames
  }
}

function formatWorldNames(worldNames) {
  if (!Array.isArray(worldNames) || !worldNames.length) {
    return '未绑定'
  }
  return worldNames
    .map((item) => {
      const option = findWorldbookOption(item)
      return worldbookOptionLabel(option || { fileId: item, name: item, missing: true })
    })
    .join(' / ')
}

function patchForm(payload) {
  const next = { ...emptyForm(), ...(payload || {}) }
  next.chatQuotaOverrideInput =
    next.chatQuotaOverride == null || next.chatQuotaOverride === '' ? '' : String(next.chatQuotaOverride)
  next.imageQuotaOverrideInput =
    next.imageQuotaOverride == null || next.imageQuotaOverride === '' ? '' : String(next.imageQuotaOverride)
  form.value = next
}

function resetConversationForm() {
  Object.assign(conversationForm, emptyConversationForm())
}

function worldbookOptionLabel(option) {
  const fileId = String(option?.fileId || '').trim()
  const name = String(option?.name || '').trim()
  const missing = !!option?.missing
  const base = name && name !== fileId ? `${name} (${fileId})` : (name || fileId)
  return missing ? `${base} [已失效]` : base
}

function findWorldbookOption(fileId) {
  const target = String(fileId || '').trim()
  if (!target) return null
  return worldbookOptions.value.find((item) => String(item?.fileId || '').trim() === target) || null
}

const conversationWorldbookOptions = computed(() => {
  const merged = new Map()
  worldbookOptions.value.forEach((item) => {
    const fileId = String(item?.fileId || '').trim()
    if (!fileId) return
    merged.set(fileId, {
      value: fileId,
      label: worldbookOptionLabel(item),
      missing: false
    })
  })
  const selected = Array.isArray(conversationForm.worldNames) ? conversationForm.worldNames : []
  selected.forEach((item) => {
    const fileId = String(item || '').trim()
    if (!fileId || merged.has(fileId)) return
    merged.set(fileId, {
      value: fileId,
      label: worldbookOptionLabel({ fileId, name: fileId, missing: true }),
      missing: true
    })
  })
  return Array.from(merged.values())
})

function loadWorldbookOptions() {
  worldbookOptionsLoading.value = true
  worldbookOptionsError.value = ''
  return listJgConversationWorldbookOptions()
    .then((res) => {
      worldbookOptions.value = Array.isArray(res.data) ? res.data : []
    })
    .catch((e) => {
      worldbookOptions.value = []
      worldbookOptionsError.value = jiugaiRequestErrorMessage(e, '加载 ST worldbook 列表失败')
    })
    .finally(() => {
      worldbookOptionsLoading.value = false
    })
}

function loadUserDetail(id) {
  return getJgH5User(id).then((res) => {
    const payload = res.data || {}
    conversationRows.value = Array.isArray(payload.conversations)
      ? payload.conversations.map(normalizeConversationRow)
      : []
    patchForm(payload)
    return payload
  })
}

function getList() {
  loading.value = true
  listJgH5User(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载用户列表失败'))
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
  queryParams.value.vipType = undefined
  queryParams.value.status = undefined
  queryParams.value.needEdit = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = Array.isArray(selection) ? selection.map((item) => item.id).filter(Boolean) : []
  multiple.value = ids.value.length === 0
}

function handleUpdate(row) {
  const id = row?.id
  if (!id) return
  loadUserDetail(id)
    .then(() => {
      title.value = '编辑 H5 用户资料'
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取用户详情失败'))
    })
}

function handlePasswordReset(row) {
  if (!canResetPassword(row)) {
    proxy.$modal.msgWarning('只有平台账号支持重置密码')
    return
  }
  passwordForm.id = row.id
  passwordForm.account = row.account || `user_${row.id}`
  passwordForm.password = ''
  passwordForm.revokeSessions = true
  passwordOpen.value = true
}

function openUsageLogs(row) {
  const id = Number(row?.id || 0)
  if (id <= 0) {
    proxy.$modal.msgWarning('用户ID无效')
    return
  }
  router.push({
    path: '/jiugai/commerce/entitlementlog',
    query: {
      scopeType: 'USAGE',
      targetUserId: String(id)
    }
  })
}

function handleBatchCharacterCreateAllowed(allowed) {
  const enabled = allowed === true
  const actionText = enabled ? '批量开启' : '批量关闭'
  const resultText = enabled ? '允许自建' : '关闭自建'
  const targetIds = ids.value.filter(Boolean)
  if (!targetIds.length) {
    proxy.$modal.msgWarning('请先勾选要操作的用户')
    return
  }
  proxy.$modal
    .confirm(`确认${actionText}已选中的 ${targetIds.length} 个用户的自建角色卡权限吗？这会直接修改这些用户详情里的小开关，之后仍可单独调整。`)
    .then(() => {
      batchCharacterCreateSubmitting.value = true
      return batchUpdateJgH5UserCharacterCreateAllowed({ ids: targetIds, allowed: enabled })
    })
    .then((res) => {
      const affectedRows = Number(res?.data?.affectedRows || 0)
      proxy.$modal.msgSuccess(res?.msg || `已将 ${affectedRows} 个用户设置为${resultText}`)
      getList()
    })
    .catch((e) => {
      if (e !== 'cancel') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, `${actionText}失败`))
      }
    })
    .finally(() => {
      batchCharacterCreateSubmitting.value = false
    })
}
function submitPasswordReset() {
  passwordFormRef.value.validate((valid) => {
    if (!valid) return
    const account = passwordForm.account || `#${passwordForm.id}`
    proxy.$modal
      .confirm(`确认重置账号“${account}”的密码吗？`)
      .then(() => {
        passwordSaving.value = true
        return resetJgH5UserPassword({
          id: passwordForm.id,
          password: passwordForm.password,
          revokeSessions: passwordForm.revokeSessions
        })
      })
      .then((res) => {
        const revoked = Number(res?.data?.revokedSessions || 0)
        proxy.$modal.msgSuccess(revoked > 0 ? `密码已重置，已踢下线 ${revoked} 个会话` : '密码已重置')
        passwordOpen.value = false
        getList()
      })
      .catch((e) => {
        if (e !== 'cancel') {
          proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '重置密码失败'))
        }
      })
      .finally(() => {
        passwordSaving.value = false
      })
  })
}

function handleDelete(row) {
  const targetIds = row?.id ? [row.id] : ids.value.slice()
  if (!targetIds.length) {
    proxy.$modal.msgWarning('请先选择要删除的用户')
    return
  }
  const message = row?.id
    ? `确定删除用户 #${row.id} 吗？删除后会一并清理账号资料、会话记录、匿名设备绑定和相关订单数据。`
    : `确定批量删除这 ${targetIds.length} 个用户吗？删除后会一并清理账号资料、会话记录、匿名设备绑定和相关订单数据。`
  proxy.$modal
    .confirm(message)
    .then(() => delJgH5User(targetIds.join(',')))
    .then((res) => {
      const failedCount = Number(res?.data?.failedCount || 0)
      const failed = Array.isArray(res?.data?.failed) ? res.data.failed : []
      const detail = failed
        .map((item) => `#${item?.id || '-'}：${item?.reason || '未知原因'}`)
        .join('\n')
      const message = res?.msg || (row?.id ? '账号已删除' : '批量删除完成')
      if (failedCount > 0) {
        proxy.$modal.msgWarning(detail ? `${message}\n${detail}` : message)
      } else {
        proxy.$modal.msgSuccess(message)
      }
      ids.value = []
      multiple.value = true
      getList()
    })
    .catch(() => {})
}

function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    const vipType = Number(form.value.vipType || 0)
    const vipExpiresAt = String(form.value.vipExpiresAt || '').trim()
    if (vipType > 0 && !vipExpiresAt) {
      proxy.$modal.msgError('设置 VIP/SVIP 时必须填写会员到期时间')
      return
    }
    if (vipType > 0) {
      const expiresAt = parseDateValue(vipExpiresAt)
      if (!expiresAt || expiresAt.getTime() <= Date.now()) {
        proxy.$modal.msgError('会员到期时间必须晚于当前时间')
        return
      }
    }
    updateJgH5User({
      ...form.value,
      vipExpiresAt: vipType > 0 ? vipExpiresAt : '',
      chatQuotaOverride: toNullableInteger(form.value.chatQuotaOverrideInput),
      imageQuotaOverride: toNullableInteger(form.value.imageQuotaOverrideInput)
    })
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存失败'))
      })
  })
}

function openConversationEditor(row) {
  resetConversationForm()
  conversationForm.conversationId = row?.conversationId
  conversationForm.title = row?.title || ''
  conversationForm.characterName = row?.characterName || ''
  conversationForm.stDisplayNameOverride = row?.stDisplayNameOverride || ''
  conversationForm.worldNames = Array.isArray(row?.worldNames) ? [...row.worldNames] : []
  conversationOpen.value = true
  if (!worldbookOptions.value.length && !worldbookOptionsLoading.value) {
    loadWorldbookOptions()
  }
}

function submitConversationForm() {
  if (!conversationForm.conversationId) return
  conversationSaving.value = true
  Promise.all([
    updateJgConversationStDisplayName(conversationForm.conversationId, {
      stDisplayNameOverride: String(conversationForm.stDisplayNameOverride || '').trim()
    }),
    updateJgConversationWorldbooks(conversationForm.conversationId, {
      worldNames: Array.isArray(conversationForm.worldNames)
        ? conversationForm.worldNames.filter(Boolean).slice(0, 10)
        : []
    })
  ])
    .then(() => {
      proxy.$modal.msgSuccess('会话运行时已保存')
      conversationOpen.value = false
      if (form.value.id) {
        return loadUserDetail(form.value.id).then(() => {
          open.value = true
        })
      }
      return null
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存会话运行时失败'))
    })
    .finally(() => {
      conversationSaving.value = false
    })
}

loadWorldbookOptions()
getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mb8 {
  margin-bottom: 8px;
}

.mt8 {
  margin-top: 8px;
}

.mini-stack {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.muted {
  color: var(--el-text-color-secondary);
}

.vip-expiry-field {
  width: 100%;
}

.vip-expiry-picker {
  width: 100%;
}

.vip-expiry-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.vip-expiry-note {
  line-height: 1.4;
}

.model-provider-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.model-provider-text {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
