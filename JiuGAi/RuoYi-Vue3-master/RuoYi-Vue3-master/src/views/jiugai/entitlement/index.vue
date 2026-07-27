<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="warning"
      :closable="false"
      show-icon
      title="这里统一控制用户端权益、站点开关和匿名试玩限制。用户管理页只处理单个用户资料、会员等级和账号删除。"
    />

    <el-card shadow="never" class="policy-card mb12">
      <template #header>
        <div class="card-header">
          <div>
            <span class="card-title">站点开关与匿名试玩</span>
            <div class="card-subtitle">保存后会直接影响 H5 登录、注册、用户创建角色卡和匿名设备试用次数。</div>
          </div>
          <el-button type="primary" :loading="runtimeSubmitting" @click="submitRuntimeSettings">保存站点规则</el-button>
        </div>
      </template>

      <div v-loading="runtimeLoading" class="runtime-grid">
        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许登录</div>
            <div class="runtime-item__desc">关闭后，H5 登录按钮会禁用，后端也会直接拒绝登录请求。</div>
          </div>
          <el-switch v-model="runtimeSettings.loginEnabled" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许注册</div>
            <div class="runtime-item__desc">关闭后，用户端注册入口会停用，新账号无法再注册。</div>
          </div>
          <el-switch v-model="runtimeSettings.registerEnabled" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许用户创建角色卡</div>
            <div class="runtime-item__desc">关闭后，新建角色卡和导入 PNG 都会停用，但已创建角色仍可管理。</div>
          </div>
          <el-switch v-model="runtimeSettings.userCharacterCreationEnabled" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许复制用户卡为系统角色</div>
            <div class="runtime-item__desc">默认关闭。开启后，角色管理页可将用户卡深复制为独立 ST 系统角色草稿；原用户卡不会被修改。</div>
          </div>
          <el-switch v-model="runtimeSettings.userCharacterPromotionEnabled" />
        </div>

        <div class="runtime-item runtime-item--stack">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许用户自定义 API Key</div>
            <div class="runtime-item__desc">开启后，H5 用户可以使用自己的平台、模型和 API Key；语音与聊天生图的用户自定义配置也依赖此开关和最低会员等级。</div>
          </div>
          <div class="runtime-limits">
            <div class="runtime-limit runtime-limit--inline">
              <span class="runtime-limit__label">开放开关</span>
              <el-switch v-model="runtimeSettings.userByokEnabled" />
            </div>
            <div class="runtime-limit">
              <span class="runtime-limit__label">最低会员等级</span>
              <el-select v-model="runtimeSettings.userByokVipMinLevel" style="width: 100%">
                <el-option :value="0" label="全部用户" />
                <el-option :value="1" label="普通会员及以上" />
                <el-option :value="2" label="Plus 会员" />
              </el-select>
            </div>
          </div>
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">显示四叶插画入口</div>
            <div class="runtime-item__desc">关闭后隐藏用户端首页和个人页的“四叶插画分享”入口，不影响插画站本身和其他功能。</div>
          </div>
          <el-switch v-model="runtimeSettings.illustrationEntryEnabled" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">显示充值入口</div>
            <div class="runtime-item__desc">关闭后只隐藏用户端的钱包充值、会员套餐入口和充值引导，不会停用支付回调、订单到账或后台对账。</div>
          </div>
          <el-switch v-model="runtimeSettings.rechargeEntryVisible" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">显示每日签到入口</div>
            <div class="runtime-item__desc">关闭后隐藏用户端“我的”页的每日签到入口，不删除签到记录，也不影响后台签到活动与奖励规则。</div>
          </div>
          <el-switch v-model="runtimeSettings.checkinEntryVisible" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">显示系统预设选择</div>
            <div class="runtime-item__desc">关闭后隐藏用户端的官方聊天预设选择和复制入口；已绑定系统预设的会话仍按原预设生成。</div>
          </div>
          <el-switch v-model="runtimeSettings.systemChatPresetEntryVisible" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">显示我的预设设置</div>
            <div class="runtime-item__desc">关闭后隐藏用户端的私有预设选择、编辑和删除入口；已绑定我的预设的会话仍按原预设生成。</div>
          </div>
          <el-switch v-model="runtimeSettings.userChatPresetEntryVisible" />
        </div>

        <div class="runtime-item runtime-item--stack">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">匿名试玩限制</div>
            <div class="runtime-item__desc">0 表示禁止匿名用户继续使用；按服务端 device_token 计数，用于控制匿名聊天次数、可开新会话数量和创建角色次数。</div>
          </div>
          <div class="runtime-limits">
            <div class="runtime-limit">
              <span class="runtime-limit__label">匿名聊天次数</span>
              <el-input-number v-model="runtimeSettings.anonymousTrialChatLimit" :min="0" :step="1" controls-position="right" />
            </div>
            <div class="runtime-limit">
              <span class="runtime-limit__label">匿名新会话数</span>
              <el-input-number v-model="runtimeSettings.anonymousTrialConversationLimit" :min="0" :step="1" controls-position="right" />
            </div>
            <div class="runtime-limit">
              <span class="runtime-limit__label">匿名创建角色次数</span>
              <el-input-number v-model="runtimeSettings.anonymousTrialCharacterCreationLimit" :min="0" :step="1" controls-position="right" />
            </div>
          </div>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="policy-card mb12">
      <template #header>
        <div class="card-header">
          <div>
            <span class="card-title">聊天额度与角色权限</span>
            <div class="card-subtitle">这里只保留聊天额度和 VIP 角色访问权限；生图是否开放由上面的总开关统一控制。</div>
          </div>
          <el-button type="primary" :loading="policySubmitting" @click="submitPolicy">保存权益配置</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-width="150px">
        <el-row :gutter="24">
          <el-col :xs="24" :md="8">
            <div class="group-title">免费用户</div>
            <el-form-item label="每日聊天次数">
              <el-input-number v-model="form.guestDailyChatQuota" :min="0" />
            </el-form-item>
            <el-form-item label="自建角色卡上限">
              <el-input-number v-model="form.guestCharacterCreateLimit" :min="0" />
            </el-form-item>
            <el-form-item label="可访问 VIP 角色">
              <el-switch v-model="form.guestCanAccessVipCharacters" />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8">
            <div class="group-title">周卡会员</div>
            <el-form-item label="每日聊天次数">
              <el-input-number v-model="form.vipDailyChatQuota" :min="0" />
            </el-form-item>
            <el-form-item label="自建角色卡上限">
              <el-input-number v-model="form.vipCharacterCreateLimit" :min="0" />
            </el-form-item>
            <el-form-item label="可访问 VIP 角色">
              <el-switch v-model="form.vipCanAccessVipCharacters" />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8">
            <div class="group-title">Plus 会员</div>
            <el-form-item label="每日聊天次数">
              <el-input-number v-model="form.svipDailyChatQuota" :min="0" />
            </el-form-item>
            <el-form-item label="自建角色卡上限">
              <el-input-number v-model="form.svipCharacterCreateLimit" :min="0" />
            </el-form-item>
            <el-form-item label="可访问 VIP 角色">
              <el-switch v-model="form.svipCanAccessVipCharacters" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <div class="group-title">用户自建音色权益</div>
        <el-alert
          class="mb12"
          type="info"
          :closable="false"
          show-icon
          title="仅允许用户使用自己的硅基流动 API Key 创建。关闭后隐藏用户端创建表单并由后端拒绝新建；更多设置中的管理入口保留，已有音色仍可绑定或删除。"
        />
        <el-form-item label="允许自建音色">
          <el-switch v-model="form.userVoiceCreationEnabled" />
        </el-form-item>
        <el-row :gutter="24">
          <el-col :xs="24" :md="8"><el-form-item label="免费用户上限"><el-input-number v-model="form.guestUserVoiceLimit" :min="0" :max="20" /></el-form-item></el-col>
          <el-col :xs="24" :md="8"><el-form-item label="周卡会员上限"><el-input-number v-model="form.vipUserVoiceLimit" :min="0" :max="20" /></el-form-item></el-col>
          <el-col :xs="24" :md="8"><el-form-item label="Plus 会员上限"><el-input-number v-model="form.svipUserVoiceLimit" :min="0" :max="20" /></el-form-item></el-col>
        </el-row>

        <el-divider />

        <div class="group-title">生成动作计费规则</div>
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="续写计入聊天次数">
              <el-switch v-model="form.continueConsumesQuota" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="重生计入聊天次数">
              <el-switch v-model="form.regenerateConsumesQuota" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <div class="group-title">自定义 API Key 独立额度</div>
        <el-alert
          class="mb12"
          type="info"
          :closable="false"
          show-icon
          title="用户选择自己的 API Key 后，不再扣官方聊天额度；这里单独限制 BYOK 每日生成次数，避免接口被无限占用。"
        />
        <el-row :gutter="24">
          <el-col :xs="24" :md="8">
            <el-form-item label="免费 BYOK 次数">
              <el-input-number v-model="form.guestDailyByokChatQuota" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="会员 BYOK 次数">
              <el-input-number v-model="form.vipDailyByokChatQuota" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="Plus BYOK 次数">
              <el-input-number v-model="form.svipDailyByokChatQuota" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="BYOK 续写计次">
              <el-switch v-model="form.byokContinueConsumesQuota" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="BYOK 重生计次">
              <el-switch v-model="form.byokRegenerateConsumesQuota" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" class="policy-card mb12">
      <template #header>
        <div class="card-header">
          <div>
            <span class="card-title">超额扣费（双轨）</span>
            <div class="card-subtitle">免费每日额度用完后，按此处单价扣钻/币；与上方权益配置一并保存。</div>
          </div>
          <el-button type="primary" :loading="policySubmitting" @click="submitPolicy">保存权益配置</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="150px">
        <el-alert
          class="mb12"
          type="info"
          :closable="false"
          show-icon
          title="免费每日额度用完后，按此处单价扣钻/币；单价为 0 表示该货币不参与扣费。两项都为 0 且超额计费关闭时，用完即不可用。"
        />

        <el-form-item label="开启超额扣费">
          <div class="over-quota-switch">
            <el-switch v-model="form.overQuotaBillingEnabled" />
            <span class="over-quota-switch__hint">免费次数用完后允许扣钻石/金币继续使用</span>
          </div>
        </el-form-item>

        <el-divider content-position="left">聊天</el-divider>
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="聊天扣钻">
              <el-input-number v-model="form.chatScoreCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="聊天扣币">
              <el-input-number v-model="form.chatGoldCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">生图</el-divider>
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="生图扣钻">
              <el-input-number v-model="form.imageScoreCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="生图扣币">
              <el-input-number v-model="form.imageGoldCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">语音 TTS</el-divider>
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="TTS 扣钻">
              <el-input-number v-model="form.ttsScoreCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="TTS 扣币">
              <el-input-number v-model="form.ttsGoldCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">语音识别 STT</el-divider>
        <el-alert class="mb12" type="info" :closable="false" show-icon title="STT 默认免费；两项均为 0 时只受独立并发和频率限制，配置单价后每段录音成功识别扣一次。" />
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="STT 扣钻">
              <el-input-number v-model="form.sttScoreCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="STT 扣币">
              <el-input-number v-model="form.sttGoldCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">视觉理解 VISION</el-divider>
        <el-alert class="mb12" type="info" :closable="false" show-icon title="系统模式发送图片时先扣一次识图费用，再按原规则结算正常聊天；BYOK 识图不扣此费用。两项均为 0 时官方识图免费。" />
        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="识图扣钻">
              <el-input-number v-model="form.visionScoreCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="识图扣币">
              <el-input-number v-model="form.visionGoldCost" :min="0" :step="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

  </div>
</template>

<script setup name="JgEntitlement">
import {
  getEntitlementPolicy,
  getEntitlementRuntimeSettings,
  updateEntitlementPolicy,
  updateEntitlementRuntimeSettings
} from '@/api/jiugai/entitlement'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const policySubmitting = ref(false)
const runtimeLoading = ref(false)
const runtimeSubmitting = ref(false)

const emptyForm = () => ({
  guestDailyChatQuota: 20,
  vipDailyChatQuota: 80,
  svipDailyChatQuota: 200,
  guestDailyByokChatQuota: 100,
  vipDailyByokChatQuota: 300,
  svipDailyByokChatQuota: 1000,
  guestDailyImageQuota: 0,
  vipDailyImageQuota: 5,
  svipDailyImageQuota: 30,
  guestCharacterCreateLimit: 999,
  vipCharacterCreateLimit: 999,
  svipCharacterCreateLimit: 999,
  userVoiceCreationEnabled: false,
  guestUserVoiceLimit: 3,
  vipUserVoiceLimit: 3,
  svipUserVoiceLimit: 3,
  guestCanAccessVipCharacters: false,
  vipCanAccessVipCharacters: true,
  svipCanAccessVipCharacters: true,
  continueConsumesQuota: true,
  regenerateConsumesQuota: true,
  byokContinueConsumesQuota: true,
  byokRegenerateConsumesQuota: true,
  overQuotaBillingEnabled: false,
  chatScoreCost: 0,
  chatGoldCost: 0,
  imageScoreCost: 0,
  imageGoldCost: 0,
  ttsScoreCost: 0,
  ttsGoldCost: 0,
  sttScoreCost: 0,
  sttGoldCost: 0,
  visionScoreCost: 0,
  visionGoldCost: 0
})

const form = ref(emptyForm())

const runtimeSettings = reactive({
  loginEnabled: true,
  registerEnabled: true,
  userCharacterCreationEnabled: true,
  userCharacterPromotionEnabled: false,
  userByokEnabled: false,
  imageGenerationEnabled: true,
  voiceFeatureEnabled: true,
  illustrationEntryEnabled: true,
  rechargeEntryVisible: true,
  checkinEntryVisible: true,
  systemChatPresetEntryVisible: true,
  userChatPresetEntryVisible: true,
  userByokVipMinLevel: 0,
  anonymousTrialChatLimit: 30,
  anonymousTrialConversationLimit: 6,
  anonymousTrialCharacterCreationLimit: 2
})

function normalizeLimit(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : fallback
}

function applyRuntimeSettings(data) {
  runtimeSettings.loginEnabled = data.loginEnabled !== false
  runtimeSettings.registerEnabled = data.registerEnabled !== false
  runtimeSettings.userCharacterCreationEnabled = data.userCharacterCreationEnabled !== false
  runtimeSettings.userCharacterPromotionEnabled = data.userCharacterPromotionEnabled === true
  runtimeSettings.userByokEnabled = data.userByokEnabled === true
  runtimeSettings.illustrationEntryEnabled = data.illustrationEntryEnabled !== false
  runtimeSettings.rechargeEntryVisible = data.rechargeEntryVisible !== false
  runtimeSettings.checkinEntryVisible = data.checkinEntryVisible !== false
  runtimeSettings.systemChatPresetEntryVisible = data.systemChatPresetEntryVisible !== false
  runtimeSettings.userChatPresetEntryVisible = data.userChatPresetEntryVisible !== false
  runtimeSettings.userByokVipMinLevel = normalizeLimit(data.userByokVipMinLevel, 0)
  runtimeSettings.anonymousTrialChatLimit = normalizeLimit(data.anonymousTrialChatLimit, 30)
  runtimeSettings.anonymousTrialConversationLimit = normalizeLimit(data.anonymousTrialConversationLimit, 6)
  runtimeSettings.anonymousTrialCharacterCreationLimit = normalizeLimit(data.anonymousTrialCharacterCreationLimit, 2)
}

function loadPolicy() {
  getEntitlementPolicy()
    .then((res) => {
      const data = res.data || {}
      form.value = {
        ...emptyForm(),
        ...data,
        overQuotaBillingEnabled: data.overQuotaBillingEnabled === true,
        chatScoreCost: normalizeLimit(data.chatScoreCost, 0),
        chatGoldCost: normalizeLimit(data.chatGoldCost, 0),
        imageScoreCost: normalizeLimit(data.imageScoreCost, 0),
        imageGoldCost: normalizeLimit(data.imageGoldCost, 0),
        ttsScoreCost: normalizeLimit(data.ttsScoreCost, 0),
        ttsGoldCost: normalizeLimit(data.ttsGoldCost, 0),
        sttScoreCost: normalizeLimit(data.sttScoreCost, 0),
        sttGoldCost: normalizeLimit(data.sttGoldCost, 0),
        visionScoreCost: normalizeLimit(data.visionScoreCost, 0),
        visionGoldCost: normalizeLimit(data.visionGoldCost, 0)
      }
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载权益配置失败'))
    })
}

function loadRuntimeSettings() {
  runtimeLoading.value = true
  return getEntitlementRuntimeSettings()
    .then((res) => {
      applyRuntimeSettings(res.data || {})
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载站点规则失败'))
    })
    .finally(() => {
      runtimeLoading.value = false
    })
}

function submitPolicy() {
  policySubmitting.value = true
  updateEntitlementPolicy({ ...form.value })
    .then(() => {
      proxy.$modal.msgSuccess('权益配置已保存')
      loadPolicy()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存权益配置失败'))
    })
    .finally(() => {
      policySubmitting.value = false
    })
}

function submitRuntimeSettings() {
  runtimeSubmitting.value = true
  updateEntitlementRuntimeSettings({
    loginEnabled: runtimeSettings.loginEnabled,
    registerEnabled: runtimeSettings.registerEnabled,
    userCharacterCreationEnabled: runtimeSettings.userCharacterCreationEnabled,
    userCharacterPromotionEnabled: runtimeSettings.userCharacterPromotionEnabled,
    userByokEnabled: runtimeSettings.userByokEnabled,
    illustrationEntryEnabled: runtimeSettings.illustrationEntryEnabled,
    rechargeEntryVisible: runtimeSettings.rechargeEntryVisible,
    checkinEntryVisible: runtimeSettings.checkinEntryVisible,
    systemChatPresetEntryVisible: runtimeSettings.systemChatPresetEntryVisible,
    userChatPresetEntryVisible: runtimeSettings.userChatPresetEntryVisible,
    userByokVipMinLevel: runtimeSettings.userByokVipMinLevel,
    anonymousTrialChatLimit: runtimeSettings.anonymousTrialChatLimit,
    anonymousTrialConversationLimit: runtimeSettings.anonymousTrialConversationLimit,
    anonymousTrialCharacterCreationLimit: runtimeSettings.anonymousTrialCharacterCreationLimit
  })
    .then((res) => {
      applyRuntimeSettings(res.data || {})
      proxy.$modal.msgSuccess('站点规则已保存')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存站点规则失败'))
    })
    .finally(() => {
      runtimeSubmitting.value = false
    })
}

loadPolicy()
loadRuntimeSettings()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.policy-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.card-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.group-title {
  margin-bottom: 12px;
  font-weight: 700;
  font-size: 15px;
}

.over-quota-switch {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.over-quota-switch__hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.runtime-grid {
  display: grid;
  gap: 12px;
}

.runtime-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
}

.runtime-item__meta {
  flex: 1;
  min-width: 0;
}

.runtime-item__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.runtime-item__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.runtime-item--stack {
  align-items: stretch;
  flex-direction: column;
}

.runtime-limits {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  width: 100%;
}

.runtime-limit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.runtime-limit--inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.runtime-limit__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

</style>
