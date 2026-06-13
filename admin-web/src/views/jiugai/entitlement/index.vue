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

        <div class="runtime-item runtime-item--stack">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许用户自定义 API Key</div>
            <div class="runtime-item__desc">开启后，H5 用户端会出现“模型与 API 设置”入口，用户可以切换到自己的平台、模型和 API Key。</div>
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
            <div class="runtime-item__title">允许聊天生图</div>
            <div class="runtime-item__desc">关闭后，用户端不再显示聊天内生图入口；开启后是否使用、生图平台和模型都交给用户端自己配置。</div>
          </div>
          <el-switch v-model="runtimeSettings.imageGenerationEnabled" />
        </div>

        <div class="runtime-item">
          <div class="runtime-item__meta">
            <div class="runtime-item__title">允许语音功能</div>
            <div class="runtime-item__desc">关闭后，用户端不再显示语音输入、角色语音和语音播放入口。</div>
          </div>
          <el-switch v-model="runtimeSettings.voiceFeatureEnabled" />
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
            <span class="card-title">聊天生图 API 配置</span>
            <div class="card-subtitle">APP 仍然只调用后端生图接口；这里选择用户自定义 API 或平台统一 API，不需要本地 ComfyUI。</div>
          </div>
          <el-button type="primary" :loading="imageGenerationSubmitting" @click="submitImageGenerationSettings">保存生图配置</el-button>
        </div>
      </template>

      <el-form v-loading="imageGenerationLoading" :model="imageGenerationSettings" label-width="150px">
        <el-row :gutter="24">
          <el-col :xs="24" :md="8">
            <el-form-item label="当前引擎">
              <el-select v-model="imageGenerationSettings.engine" style="width: 100%">
                <el-option label="用户自定义 API" value="user_openai_compatible" />
                <el-option label="平台统一 API" value="managed_openai_compatible" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="全局并发">
              <el-input-number v-model="imageGenerationSettings.globalConcurrentLimit" :min="1" :max="64" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="单用户并发">
              <el-input-number v-model="imageGenerationSettings.perUserConcurrentLimit" :min="1" :max="8" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <el-row :gutter="24">
          <el-col :xs="24" :md="12">
            <el-form-item label="请求超时(秒)">
              <el-input-number v-model="imageGenerationSettings.requestTimeoutSeconds" :min="1" :max="600" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col v-if="imageGenerationSettings.engine === 'managed_openai_compatible'" :xs="24" :md="12">
            <el-form-item label="平台">
              <el-select v-model="imageGenerationSettings.managedProviderSource" style="width: 100%">
                <el-option
                  v-for="item in imageProviderOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="imageGenerationSettings.engine === 'managed_openai_compatible'" :xs="24" :md="12">
            <el-form-item label="生图模型">
              <el-input v-model="imageGenerationSettings.managedImageModelName" placeholder="例如：Kwai-Kolors/Kolors" />
            </el-form-item>
          </el-col>
          <el-col v-if="imageGenerationSettings.engine === 'managed_openai_compatible'" :xs="24" :md="12">
            <el-form-item label="平台 API Key">
              <el-input
                v-model="imageGenerationSettings.managedApiKey"
                type="password"
                show-password
                :placeholder="imageGenerationSettings.managedApiKeyConfigured ? `已配置：${imageGenerationSettings.managedApiKeyMask}` : '请输入平台 API Key'"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="imageGenerationSettings.engine === 'managed_openai_compatible'" :xs="24" :md="12">
            <el-form-item label="自定义 Base URL">
              <el-input v-model="imageGenerationSettings.managedCustomUrl" placeholder="仅自定义平台需要填写" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="false" :gutter="24">
          <el-col :xs="24" :md="8">
            <el-form-item label="采样器">
              <el-input v-model="imageGenerationSettings.sampler" placeholder="euler" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="调度器">
              <el-input v-model="imageGenerationSettings.scheduler" placeholder="normal" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="步数">
              <el-input-number v-model="imageGenerationSettings.steps" :min="1" :max="150" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="CFG">
              <el-input-number v-model="imageGenerationSettings.scale" :min="1" :max="30" :step="0.5" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="Seed">
              <el-input-number v-model="imageGenerationSettings.seed" :min="-1" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="Denoise">
              <el-input-number v-model="imageGenerationSettings.denoise" :min="0" :max="1" :step="0.05" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="24">
            <el-form-item label="负面提示词">
              <el-input v-model="imageGenerationSettings.negativePrompt" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" class="policy-card mb12">
      <template #header>
        <div class="card-header">
          <div>
            <span class="card-title">SiliconFlow 音色模板</span>
            <div class="card-subtitle">这里配置的是“模板”，不是共享 voice id。用户第一次选择模板时，会使用他们自己的 SiliconFlow Key 自动生成一份专属 voice 并长期复用。</div>
          </div>
          <el-button type="primary" @click="openVoiceTemplateDialog()">新增模板</el-button>
        </div>
      </template>

      <el-alert
        class="mb12"
        type="info"
        :closable="false"
        show-icon
        title="推荐做法：给每个角色准备 5 到 20 秒、干净少噪声的参考音频，并填写一段稳定的人声示例文案。模板更新后，用户下次播放时会自动重新生成新的专属音色。"
      />

      <div v-loading="voiceTemplateLoading">
        <div v-if="voiceTemplateRows.length" class="voice-template-list">
          <div v-for="row in voiceTemplateRows" :key="row.id" class="voice-template-card">
            <div class="voice-template-card__cover">
              <el-image
                v-if="row.coverImageUrl"
                :src="displayUploadUrl(row.coverImageUrl)"
                fit="cover"
                class="voice-template-card__image"
                preview-teleported
                :preview-src-list="[displayUploadUrl(row.coverImageUrl)]"
              />
              <div v-else class="voice-template-card__placeholder">Voice</div>
            </div>

            <div class="voice-template-card__body">
              <div class="voice-template-card__head">
                <div>
                  <div class="voice-template-card__title">{{ row.displayName }}</div>
                  <div class="voice-template-card__code">{{ row.templateCode }}</div>
                </div>
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用中' : '已停用' }}</el-tag>
              </div>

              <div class="voice-template-card__meta">
                <el-tag size="small" effect="plain">{{ row.providerSource || 'siliconflow' }}</el-tag>
                <el-tag size="small" effect="plain" type="warning">{{ row.ttsModelName || '未填写推荐模型' }}</el-tag>
              </div>

              <div class="voice-template-card__desc">{{ row.description || '未填写模板描述' }}</div>

              <div class="voice-template-card__sample">
                <span class="voice-template-card__sample-label">示例文案</span>
                <span class="voice-template-card__sample-text">{{ row.sampleScript || '未填写' }}</span>
              </div>

              <div class="voice-template-card__audio">
                <audio v-if="row.referenceAudioUrl" :src="displayUploadUrl(row.referenceAudioUrl)" controls preload="none" />
                <span v-else class="voice-template-card__audio-empty">未上传参考音频</span>
              </div>

              <div class="voice-template-card__actions">
                <el-button link type="primary" @click="openVoiceTemplateDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteVoiceTemplate(row)">删除</el-button>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-else description="还没有配置模板音色" />
      </div>
    </el-card>

    <el-card shadow="never" class="policy-card">
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

    <el-dialog
      v-model="voiceTemplateDialogOpen"
      :title="voiceTemplateForm.id ? '编辑音色模板' : '新增音色模板'"
      width="760px"
      destroy-on-close
    >
      <el-form :model="voiceTemplateForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="模板名称">
              <el-input v-model="voiceTemplateForm.displayName" placeholder="例如：雷姆" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="模板编码">
              <el-input
                v-model="voiceTemplateForm.templateCode"
                :disabled="!!voiceTemplateForm.id"
                placeholder="留空时按名称自动生成"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="Provider">
              <el-input v-model="voiceTemplateForm.providerSource" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="推荐模型">
              <el-input v-model="voiceTemplateForm.ttsModelName" placeholder="例如：FunAudioLLM/CosyVoice2-0.5B" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="模板状态">
              <el-switch v-model="voiceTemplateForm.enabled" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="排序">
              <el-input-number v-model="voiceTemplateForm.sortOrder" :min="0" :max="9999" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="模板描述">
              <el-input v-model="voiceTemplateForm.description" type="textarea" :rows="2" placeholder="给用户看的简短说明，例如：更贴近动画版声线、语气偏温柔。" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="示例文案">
              <el-input v-model="voiceTemplateForm.sampleScript" type="textarea" :rows="3" placeholder="用于在 SiliconFlow 侧生成动态音色时对齐音色特征的文案。" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="参考音频">
              <div class="upload-surface">
                <div class="upload-surface__preview">
                  <audio v-if="voiceTemplateForm.referenceAudioUrl" :src="displayUploadUrl(voiceTemplateForm.referenceAudioUrl)" controls preload="none" />
                  <div v-else class="upload-surface__empty">上传参考音频后，这里会显示试听</div>
                </div>
                <div class="upload-surface__actions">
                  <el-upload
                    :action="voiceTemplateAudioUploadAction"
                    :headers="uploadHeaders"
                    :show-file-list="false"
                    accept=".mp3,.wav,.m4a,.ogg,.aac,.amr,audio/*"
                    :before-upload="beforeVoiceTemplateAudioUpload"
                    :on-success="onVoiceTemplateAudioUploadSuccess"
                  >
                    <el-button type="primary">上传参考音频</el-button>
                  </el-upload>
                  <div class="upload-surface__tip">建议 5 到 20 秒，单人声、少噪声、说话稳定。</div>
                </div>
              </div>
              <el-input
                v-model="voiceTemplateForm.referenceAudioUrl"
                class="mt10"
                placeholder="也可以直接填写外部可访问的音频 URL"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="封面图">
              <div class="upload-surface upload-surface--image">
                <div class="upload-surface__preview upload-surface__preview--image">
                  <el-image
                    v-if="voiceTemplateForm.coverImageUrl"
                    :src="displayUploadUrl(voiceTemplateForm.coverImageUrl)"
                    fit="cover"
                    class="voice-dialog-cover"
                    preview-teleported
                    :preview-src-list="[displayUploadUrl(voiceTemplateForm.coverImageUrl)]"
                  />
                  <div v-else class="upload-surface__empty">可选，用于用户端模板卡片展示</div>
                </div>
                <div class="upload-surface__actions">
                  <el-upload
                    :action="voiceTemplateImageUploadAction"
                    :headers="uploadHeaders"
                    :show-file-list="false"
                    accept=".png,.jpg,.jpeg,.webp,.gif,image/*"
                    :before-upload="beforeVoiceTemplateImageUpload"
                    :on-success="onVoiceTemplateImageUploadSuccess"
                  >
                    <el-button type="primary" plain>上传封面</el-button>
                  </el-upload>
                  <div class="upload-surface__tip">建议方图或 3:4 立绘，用户端会显示为模板视觉卡片。</div>
                </div>
              </div>
              <el-input
                v-model="voiceTemplateForm.coverImageUrl"
                class="mt10"
                placeholder="也可以直接填写外部可访问的图片 URL"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="voiceTemplateDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="voiceTemplateSubmitting" @click="submitVoiceTemplate">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgEntitlement">
import { ElMessageBox } from 'element-plus'
import {
  getEntitlementPolicy,
  getEntitlementRuntimeSettings,
  getImageGenerationSettings,
  updateEntitlementPolicy,
  updateEntitlementRuntimeSettings,
  updateImageGenerationSettings
} from '@/api/jiugai/entitlement'
import {
  addTtsVoiceTemplate,
  deleteTtsVoiceTemplate,
  listTtsVoiceTemplates,
  updateTtsVoiceTemplate
} from '@/api/jiugai/ttsVoiceTemplate'
import { getToken } from '@/utils/auth'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const uploadHeaders = ref({ Authorization: 'Bearer ' + getToken() })
const voiceTemplateAudioUploadAction = baseApi + '/admin/jiugai/tts-voice-template/upload/audio'
const voiceTemplateImageUploadAction = baseApi + '/admin/jiugai/tts-voice-template/upload/image'

const policySubmitting = ref(false)
const runtimeLoading = ref(false)
const runtimeSubmitting = ref(false)
const imageGenerationLoading = ref(false)
const imageGenerationSubmitting = ref(false)
const voiceTemplateLoading = ref(false)
const voiceTemplateSubmitting = ref(false)
const voiceTemplateDialogOpen = ref(false)
const voiceTemplateRows = ref([])

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
  guestCanAccessVipCharacters: false,
  vipCanAccessVipCharacters: true,
  svipCanAccessVipCharacters: true,
  continueConsumesQuota: true,
  regenerateConsumesQuota: true,
  byokContinueConsumesQuota: true,
  byokRegenerateConsumesQuota: true
})

const defaultVoiceTemplateForm = () => ({
  id: null,
  templateCode: '',
  displayName: '',
  providerSource: 'siliconflow',
  ttsModelName: '',
  description: '',
  referenceAudioUrl: '',
  coverImageUrl: '',
  sampleScript: '请用温柔自然的语气说话。',
  enabled: true,
  sortOrder: 100
})

const form = ref(emptyForm())
const voiceTemplateForm = reactive(defaultVoiceTemplateForm())

const runtimeSettings = reactive({
  loginEnabled: true,
  registerEnabled: true,
  userCharacterCreationEnabled: true,
  userByokEnabled: false,
  imageGenerationEnabled: true,
  voiceFeatureEnabled: true,
  userByokVipMinLevel: 0,
  anonymousTrialChatLimit: 30,
  anonymousTrialConversationLimit: 6,
  anonymousTrialCharacterCreationLimit: 2
})

const imageProviderOptions = [
  { label: 'SiliconFlow', value: 'siliconflow' },
  { label: 'OpenRouter', value: 'openrouter' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Fireworks', value: 'fireworks' },
  { label: '自定义 OpenAI 兼容', value: 'custom' }
]

const imageGenerationSettings = reactive({
  engine: 'user_openai_compatible',
  globalConcurrentLimit: 2,
  perUserConcurrentLimit: 1,
  counterTtlSeconds: 600,
  managedProviderSource: 'siliconflow',
  managedImageModelName: '',
  managedApiKey: '',
  managedApiKeyConfigured: false,
  managedApiKeyMask: '',
  managedCustomUrl: '',
  comfyUrl: 'http://127.0.0.1:8188',
  workflow: 'Default_Comfy_Workflow.json',
  referenceWorkflow: 'Char_Avatar_Comfy_Workflow.json',
  model: '',
  sampler: 'euler',
  scheduler: 'normal',
  negativePrompt: 'low quality, blurry, bad anatomy, extra fingers, watermark, text',
  steps: 28,
  scale: 7,
  seed: -1,
  denoise: 1,
  requestTimeoutSeconds: 90
})

function normalizeLimit(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : fallback
}

function normalizeUploadUrl(url) {
  const text = String(url || '').trim()
  if (!text) return ''
  if (/^https?:\/\//i.test(text) || text.startsWith('data:')) {
    return text
  }
  if (text.startsWith('/')) {
    return baseApi + text
  }
  return text
}

function displayUploadUrl(url) {
  return normalizeUploadUrl(url)
}

function applyRuntimeSettings(data) {
  runtimeSettings.loginEnabled = data.loginEnabled !== false
  runtimeSettings.registerEnabled = data.registerEnabled !== false
  runtimeSettings.userCharacterCreationEnabled = data.userCharacterCreationEnabled !== false
  runtimeSettings.userByokEnabled = data.userByokEnabled === true
  runtimeSettings.imageGenerationEnabled = data.imageGenerationEnabled !== false
  runtimeSettings.voiceFeatureEnabled = data.voiceFeatureEnabled !== false
  runtimeSettings.userByokVipMinLevel = normalizeLimit(data.userByokVipMinLevel, 0)
  runtimeSettings.anonymousTrialChatLimit = normalizeLimit(data.anonymousTrialChatLimit, 30)
  runtimeSettings.anonymousTrialConversationLimit = normalizeLimit(data.anonymousTrialConversationLimit, 6)
  runtimeSettings.anonymousTrialCharacterCreationLimit = normalizeLimit(data.anonymousTrialCharacterCreationLimit, 2)
}

function normalizeRange(value, fallback, min, max) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.max(min, Math.min(max, number))
}

function applyImageGenerationSettings(data) {
  const normalizedEngine = data.engine === 'openai_compatible' ? 'user_openai_compatible' : data.engine
  imageGenerationSettings.engine = ['user_openai_compatible', 'managed_openai_compatible'].includes(normalizedEngine) ? normalizedEngine : 'user_openai_compatible'
  imageGenerationSettings.globalConcurrentLimit = normalizeRange(data.globalConcurrentLimit, 2, 1, 64)
  imageGenerationSettings.perUserConcurrentLimit = normalizeRange(data.perUserConcurrentLimit, 1, 1, 8)
  imageGenerationSettings.counterTtlSeconds = normalizeRange(data.counterTtlSeconds, 600, 10, 7200)
  imageGenerationSettings.managedProviderSource = String(data.managedProviderSource || 'siliconflow')
  imageGenerationSettings.managedImageModelName = String(data.managedImageModelName || '')
  imageGenerationSettings.managedApiKey = ''
  imageGenerationSettings.managedApiKeyConfigured = data.managedApiKeyConfigured === true
  imageGenerationSettings.managedApiKeyMask = String(data.managedApiKeyMask || '')
  imageGenerationSettings.managedCustomUrl = String(data.managedCustomUrl || '')
  imageGenerationSettings.comfyUrl = String(data.comfyUrl || 'http://127.0.0.1:8188')
  imageGenerationSettings.workflow = String(data.workflow || 'Default_Comfy_Workflow.json')
  imageGenerationSettings.referenceWorkflow = String(data.referenceWorkflow || 'Char_Avatar_Comfy_Workflow.json')
  imageGenerationSettings.model = String(data.model || '')
  imageGenerationSettings.sampler = String(data.sampler || 'euler')
  imageGenerationSettings.scheduler = String(data.scheduler || 'normal')
  imageGenerationSettings.negativePrompt = String(data.negativePrompt || '')
  imageGenerationSettings.steps = normalizeRange(data.steps, 28, 1, 150)
  imageGenerationSettings.scale = normalizeRange(data.scale, 7, 1, 30)
  imageGenerationSettings.seed = Number.isFinite(Number(data.seed)) ? Number(data.seed) : -1
  imageGenerationSettings.denoise = normalizeRange(data.denoise, 1, 0, 1)
  imageGenerationSettings.requestTimeoutSeconds = normalizeRange(data.requestTimeoutSeconds, 90, 1, 600)
}

function loadPolicy() {
  getEntitlementPolicy()
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
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

function loadImageGenerationSettings() {
  imageGenerationLoading.value = true
  return getImageGenerationSettings()
    .then((res) => {
      applyImageGenerationSettings(res.data || {})
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载生图配置失败'))
    })
    .finally(() => {
      imageGenerationLoading.value = false
    })
}

function loadVoiceTemplates() {
  voiceTemplateLoading.value = true
  return listTtsVoiceTemplates()
    .then((res) => {
      voiceTemplateRows.value = Array.isArray(res.rows) ? res.rows : []
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载音色模板失败'))
    })
    .finally(() => {
      voiceTemplateLoading.value = false
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
    userByokEnabled: runtimeSettings.userByokEnabled,
    imageGenerationEnabled: runtimeSettings.imageGenerationEnabled,
    voiceFeatureEnabled: runtimeSettings.voiceFeatureEnabled,
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

function submitImageGenerationSettings() {
  imageGenerationSubmitting.value = true
  updateImageGenerationSettings({
    ...imageGenerationSettings,
    globalConcurrentLimit: Math.floor(imageGenerationSettings.globalConcurrentLimit),
    perUserConcurrentLimit: Math.floor(imageGenerationSettings.perUserConcurrentLimit),
    counterTtlSeconds: Math.floor(imageGenerationSettings.counterTtlSeconds),
    steps: Math.floor(imageGenerationSettings.steps),
    requestTimeoutSeconds: Math.floor(imageGenerationSettings.requestTimeoutSeconds)
  })
    .then((res) => {
      applyImageGenerationSettings(res.data || {})
      proxy.$modal.msgSuccess('生图配置已保存')
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存生图配置失败'))
    })
    .finally(() => {
      imageGenerationSubmitting.value = false
    })
}

function openVoiceTemplateDialog(row) {
  Object.assign(voiceTemplateForm, defaultVoiceTemplateForm(), row || {})
  voiceTemplateDialogOpen.value = true
}

function validateVoiceTemplateForm() {
  if (!String(voiceTemplateForm.displayName || '').trim()) {
    proxy.$modal.msgError('请先填写模板名称')
    return false
  }
  if (!String(voiceTemplateForm.referenceAudioUrl || '').trim()) {
    proxy.$modal.msgError('请先上传参考音频')
    return false
  }
  if (!String(voiceTemplateForm.sampleScript || '').trim()) {
    proxy.$modal.msgError('请先填写示例文案')
    return false
  }
  return true
}

function submitVoiceTemplate() {
  if (voiceTemplateSubmitting.value || !validateVoiceTemplateForm()) {
    return
  }
  const payload = {
    ...voiceTemplateForm,
    providerSource: 'siliconflow',
    templateCode: String(voiceTemplateForm.templateCode || '').trim(),
    displayName: String(voiceTemplateForm.displayName || '').trim(),
    ttsModelName: String(voiceTemplateForm.ttsModelName || '').trim(),
    description: String(voiceTemplateForm.description || '').trim(),
    referenceAudioUrl: String(voiceTemplateForm.referenceAudioUrl || '').trim(),
    coverImageUrl: String(voiceTemplateForm.coverImageUrl || '').trim(),
    sampleScript: String(voiceTemplateForm.sampleScript || '').trim(),
    sortOrder: Number(voiceTemplateForm.sortOrder || 100),
    enabled: !!voiceTemplateForm.enabled
  }
  voiceTemplateSubmitting.value = true
  const request = payload.id ? updateTtsVoiceTemplate(payload) : addTtsVoiceTemplate(payload)
  request
    .then(() => {
      proxy.$modal.msgSuccess(payload.id ? '音色模板已保存' : '音色模板已创建')
      voiceTemplateDialogOpen.value = false
      return loadVoiceTemplates()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存音色模板失败'))
    })
    .finally(() => {
      voiceTemplateSubmitting.value = false
    })
}

function handleDeleteVoiceTemplate(row) {
  ElMessageBox.confirm(
    `删除后，已经选中过这个模板的用户下次播放会提示模板失效。确认删除“${row.displayName}”吗？`,
    '删除模板',
    { type: 'warning' }
  )
    .then(() => deleteTtsVoiceTemplate(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('音色模板已删除')
      return loadVoiceTemplates()
    })
    .catch(() => {})
}

function beforeVoiceTemplateAudioUpload(file) {
  const sizeMb = Number(file.size || 0) / 1024 / 1024
  if (sizeMb > 10) {
    proxy.$modal.msgError('参考音频请控制在 10MB 以内')
    return false
  }
  return true
}

function beforeVoiceTemplateImageUpload(file) {
  const sizeMb = Number(file.size || 0) / 1024 / 1024
  if (sizeMb > 10) {
    proxy.$modal.msgError('封面图片请控制在 10MB 以内')
    return false
  }
  return true
}

function onVoiceTemplateAudioUploadSuccess(response) {
  if (response && Number(response.code) === 200 && response.fileName) {
    voiceTemplateForm.referenceAudioUrl = response.fileName
    proxy.$modal.msgSuccess('参考音频上传成功')
    return
  }
  proxy.$modal.msgError((response && response.msg) || '参考音频上传失败')
}

function onVoiceTemplateImageUploadSuccess(response) {
  if (response && Number(response.code) === 200 && response.fileName) {
    voiceTemplateForm.coverImageUrl = response.fileName
    proxy.$modal.msgSuccess('封面上传成功')
    return
  }
  proxy.$modal.msgError((response && response.msg) || '封面上传失败')
}

loadPolicy()
loadRuntimeSettings()
loadImageGenerationSettings()
loadVoiceTemplates()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mt10 {
  margin-top: 10px;
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

.voice-template-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 560px;
  overflow-y: auto;
  padding-right: 4px;
}

.voice-template-card {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 12px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 1));
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.voice-template-card__cover {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.voice-template-card__image {
  width: 100%;
  height: 100%;
}

.voice-template-card__placeholder {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.voice-template-card__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.voice-template-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.voice-template-card__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-template-card__code {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.voice-template-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.voice-template-card__desc {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-template-card__sample {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.voice-template-card__sample-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.voice-template-card__sample-text {
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-template-card__audio audio {
  width: 280px;
  max-width: 100%;
  height: 34px;
}

.voice-template-card__audio-empty {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.voice-template-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 0;
}

.upload-surface {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 16px;
}

.upload-surface--image {
  align-items: stretch;
}

.upload-surface__preview {
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px dashed var(--el-border-color);
  background: var(--el-fill-color-lighter);
  padding: 12px;
}

.upload-surface__preview audio {
  width: 100%;
}

.upload-surface__preview--image {
  min-height: 180px;
}

.upload-surface__empty {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.upload-surface__actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.upload-surface__tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.voice-dialog-cover {
  width: 132px;
  height: 176px;
  border-radius: 12px;
}

@media (max-width: 900px) {
  .voice-template-card {
    grid-template-columns: 88px minmax(0, 1fr);
  }

  .voice-template-card__cover {
    width: 88px;
    height: 118px;
  }

  .upload-surface {
    grid-template-columns: 1fr;
  }

  .voice-dialog-cover {
    width: 112px;
    height: 148px;
  }
}
</style>
