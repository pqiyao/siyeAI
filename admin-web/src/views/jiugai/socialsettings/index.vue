<template>
  <div class="app-container social-settings-page">
    <el-alert
      class="mb12"
      type="warning"
      :closable="false"
      show-icon
      title="这里是用户端社区、真人聊天、好友关系的全局开关。关闭后后端会拦截接口，H5 只负责隐藏入口和给用户提示。"
    />

    <el-form v-loading="loading" :model="form" label-width="170px" class="settings-form">
      <el-card class="mb12" shadow="never">
        <template #header>
          <div class="card-head">
            <span>社区动态</span>
            <el-tag :type="form.communityEnabled ? 'success' : 'info'" effect="plain">
              {{ form.communityEnabled ? '已开放' : '已关闭' }}
            </el-tag>
          </div>
        </template>

        <div class="settings-grid">
          <el-form-item label="社区总开关">
            <el-switch v-model="form.communityEnabled" />
            <div class="form-tip">关闭后社区列表、详情、互动接口都会拒绝访问。</div>
          </el-form-item>
          <el-form-item label="显示社区入口">
            <el-switch v-model="form.communityEntryVisible" :disabled="!form.communityEnabled" />
            <div class="form-tip">控制 H5 首页/导航是否露出社区入口。</div>
          </el-form-item>
          <el-form-item label="游客可浏览">
            <el-switch v-model="form.guestCommunityReadEnabled" :disabled="!form.communityEnabled" />
            <div class="form-tip">关闭后游客进入社区会被引导登录。</div>
          </el-form-item>
          <el-form-item label="允许发布动态">
            <el-switch v-model="form.postCreateEnabled" :disabled="!form.communityEnabled" />
            <div class="form-tip">关闭后发动态接口不可用。</div>
          </el-form-item>
          <el-form-item label="允许图片动态">
            <el-switch v-model="form.postImageEnabled" :disabled="!form.communityEnabled || !form.postCreateEnabled" />
            <div class="form-tip">关闭后只允许文字动态。</div>
          </el-form-item>
          <el-form-item label="发布模式">
            <el-select v-model="form.postPublishMode" :disabled="!form.communityEnabled || !form.postCreateEnabled" style="width: 220px">
              <el-option label="直接发布" value="direct" />
              <el-option label="先审后发（预留）" value="review" />
              <el-option label="暂停发布" value="closed" />
            </el-select>
            <div class="form-tip">先审后发当前作为策略预留，后端会提示尚未接入。</div>
          </el-form-item>
          <el-form-item label="允许点赞">
            <el-switch v-model="form.likeEnabled" :disabled="!form.communityEnabled" />
          </el-form-item>
          <el-form-item label="允许评论">
            <el-switch v-model="form.commentEnabled" :disabled="!form.communityEnabled" />
          </el-form-item>
          <el-form-item label="允许关注/加好友">
            <el-switch v-model="form.followEnabled" :disabled="!form.communityEnabled" />
            <div class="form-tip">当前“加好友”先复用关注关系。</div>
          </el-form-item>
          <el-form-item label="动态页私信入口">
            <el-switch v-model="form.postMessageEntryVisible" :disabled="!form.communityEnabled || !form.chatEnabled" />
            <div class="form-tip">控制动态详情和信息流里的发消息按钮。</div>
          </el-form-item>
        </div>
      </el-card>

      <el-card class="mb12" shadow="never">
        <template #header>
          <div class="card-head">
            <span>真人聊天</span>
            <el-tag :type="form.chatEnabled ? 'success' : 'info'" effect="plain">
              {{ form.chatEnabled ? chatPolicyLabel(form.privateChatPolicy) : '已关闭' }}
            </el-tag>
          </div>
        </template>

        <div class="settings-grid">
          <el-form-item label="聊天总开关">
            <el-switch v-model="form.chatEnabled" />
            <div class="form-tip">关闭后真人聊天列表、消息、发送接口都会拒绝访问。</div>
          </el-form-item>
          <el-form-item label="显示聊天入口">
            <el-switch v-model="form.chatEntryVisible" :disabled="!form.chatEnabled" />
            <div class="form-tip">控制 H5 中“真人聊天/私信”入口是否显示。</div>
          </el-form-item>
          <el-form-item label="允许发起新私聊">
            <el-switch v-model="form.newChatEnabled" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="允许继续已有会话">
            <el-switch v-model="form.existingChatEnabled" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="允许文字消息">
            <el-switch v-model="form.textMessageEnabled" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="允许图片消息">
            <el-switch v-model="form.imageMessageEnabled" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="允许用户撤回">
            <el-switch v-model="form.messageRecallEnabled" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="显示在线状态">
            <el-switch v-model="form.onlineStatusVisible" :disabled="!form.chatEnabled" />
          </el-form-item>
          <el-form-item label="私聊关系规则">
            <el-select v-model="form.privateChatPolicy" :disabled="!form.chatEnabled" style="width: 260px">
              <el-option label="所有登录用户可私聊" value="all" />
              <el-option label="互相关注后可私聊" value="mutual_follow" />
              <el-option label="好友后可私聊" value="friend_only" />
              <el-option label="完全关闭私聊" value="closed" />
            </el-select>
            <div class="form-tip">开启好友功能后，好友私聊会按真实好友关系校验。</div>
          </el-form-item>
          <el-form-item label="允许非好友私聊">
            <el-switch v-model="form.nonFriendChatEnabled" :disabled="!form.chatEnabled || form.privateChatPolicy !== 'all'" />
            <div class="form-tip">策略为“所有登录用户可私聊”时有效。</div>
          </el-form-item>
        </div>
      </el-card>

      <el-card class="mb12" shadow="never">
        <template #header>
          <div class="card-head">
            <span>好友体系</span>
            <el-tag type="info" effect="plain">分阶段接入</el-tag>
          </div>
        </template>

        <div class="settings-grid">
          <el-form-item label="好友功能总开关">
            <el-switch v-model="form.friendEnabled" />
            <div class="form-tip">开启后可逐步接入真正的好友申请、黑名单和好友私聊规则。</div>
          </el-form-item>
          <el-form-item label="显示好友入口">
            <el-switch v-model="form.friendEntryVisible" :disabled="!form.friendEnabled" />
          </el-form-item>
          <el-form-item label="允许好友申请">
            <el-switch v-model="form.friendRequestEnabled" :disabled="!form.friendEnabled" />
          </el-form-item>
          <el-form-item label="申请需要通过">
            <el-switch v-model="form.friendRequestApprovalRequired" :disabled="!form.friendEnabled || !form.friendRequestEnabled" />
          </el-form-item>
          <el-form-item label="黑名单能力">
            <el-switch v-model="form.blockEnabled" :disabled="!form.friendEnabled" />
          </el-form-item>
        </div>
      </el-card>

      <div class="footer-bar">
        <el-button icon="Refresh" @click="loadSettings">重新读取</el-button>
        <el-button v-hasPermi="['social:settings:edit']" type="primary" icon="Check" :loading="saving" @click="submit">
          保存设置
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup name="JgSocialSettings">
import { getSocialSettings, updateSocialSettings } from '@/api/jiugai/socialSettings'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const saving = ref(false)

const defaultForm = () => ({
  communityEnabled: true,
  communityEntryVisible: true,
  guestCommunityReadEnabled: true,
  postCreateEnabled: true,
  postImageEnabled: true,
  likeEnabled: true,
  commentEnabled: true,
  followEnabled: true,
  postMessageEntryVisible: true,
  postPublishMode: 'direct',
  chatEnabled: true,
  chatEntryVisible: true,
  newChatEnabled: true,
  existingChatEnabled: true,
  textMessageEnabled: true,
  imageMessageEnabled: true,
  messageRecallEnabled: true,
  onlineStatusVisible: true,
  privateChatPolicy: 'all',
  friendEnabled: true,
  friendRequestEnabled: true,
  friendEntryVisible: true,
  nonFriendChatEnabled: true,
  friendRequestApprovalRequired: true,
  blockEnabled: false
})

const form = reactive(defaultForm())

function assignForm(data) {
  Object.assign(form, defaultForm(), data || {})
}

function chatPolicyLabel(value) {
  const map = {
    all: '全员私聊',
    mutual_follow: '互相关注',
    friend_only: '好友私聊',
    closed: '私聊关闭'
  }
  return map[value] || map.all
}

function loadSettings() {
  loading.value = true
  getSocialSettings()
    .then((res) => {
      assignForm(res.data || {})
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载社交设置失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeBeforeSubmit() {
  if (!form.communityEnabled) {
    form.communityEntryVisible = false
  }
  if (!form.postCreateEnabled) {
    form.postImageEnabled = false
  }
  if (!form.chatEnabled) {
    form.chatEntryVisible = false
    form.newChatEnabled = false
    form.existingChatEnabled = false
  }
  if (!form.friendEnabled) {
    form.friendEntryVisible = false
    form.friendRequestEnabled = false
    form.blockEnabled = false
    if (form.privateChatPolicy === 'friend_only') {
      form.privateChatPolicy = 'mutual_follow'
    }
  }
}

function submit() {
  normalizeBeforeSubmit()
  saving.value = true
  updateSocialSettings({ ...form })
    .then((res) => {
      assignForm(res.data || form)
      proxy.$modal.msgSuccess('保存成功')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存社交设置失败'))
    })
    .finally(() => {
      saving.value = false
    })
}

loadSettings()
</script>

<style scoped>
.social-settings-page {
  color: #1f2933;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 700;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(320px, 1fr));
  column-gap: 28px;
  row-gap: 6px;
}

.settings-form :deep(.el-form-item) {
  align-items: flex-start;
  margin-bottom: 18px;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: #7a8794;
  font-size: 12px;
  line-height: 1.5;
}

.footer-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 0;
  background: linear-gradient(180deg, rgba(246, 248, 251, 0), #f6f8fb 42%);
}

@media (max-width: 1180px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
