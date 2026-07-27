<template>
  <div class="app-container media-center">
    <section class="media-heading">
      <div>
        <div class="media-heading__eyebrow">AI MEDIA CONTROL</div>
        <h1>AI 媒体中心</h1>
        <p>统一管理聊天生图、语音运行限制和角色音色模板。媒体文件仍保存在用户设备，不在服务器长期留存。</p>
      </div>
      <div class="media-heading__status">
        <div class="status-item">
          <span :class="['status-dot', imageForm.featureEnabled ? 'is-on' : '']" />
          <span>生图 {{ imageForm.featureEnabled ? '开放' : '关闭' }}</span>
        </div>
        <div class="status-item">
          <span :class="['status-dot', voiceForm.featureEnabled ? 'is-on' : '']" />
          <span>语音 {{ voiceForm.featureEnabled ? '开放' : '关闭' }}</span>
        </div>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="media-tabs">
      <el-tab-pane name="image">
        <template #label><span class="tab-label"><el-icon><Picture /></el-icon>生图策略</span></template>

        <div v-loading="imageLoading" class="workspace">
          <section class="control-section">
            <header class="section-header">
              <div>
                <h2>全局策略</h2>
                <p>自由模式保留原始文生图；平衡与强一致性才注入角色资料。</p>
              </div>
              <el-button v-hasPermi="['ops:media:image:edit']" type="primary" :loading="imageSaving" :icon="Check" @click="saveImagePolicy">保存生图策略</el-button>
            </header>

            <div class="setting-grid">
              <div class="setting-block setting-block--switch">
                <div>
                  <strong>聊天生图总开关</strong>
                  <span>关闭后用户端入口与后端请求同时停用。</span>
                </div>
                <el-switch v-model="imageForm.featureEnabled" />
              </div>

              <div class="setting-block setting-block--wide">
                <label>默认一致性</label>
                <el-radio-group v-model="imageForm.defaultConsistencyMode" class="mode-selector">
                  <el-radio-button v-for="item in modeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
                </el-radio-group>
                <small>{{ currentModeDescription }}</small>
              </div>

              <div class="setting-block">
                <label>允许用户选择</label>
                <el-checkbox-group v-model="imageForm.allowedConsistencyModes">
                  <el-checkbox v-for="item in modeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="setting-block">
                <label>默认参考图来源</label>
                <el-select v-model="imageForm.defaultReferenceSourceMode" style="width: 100%">
                  <el-option v-for="item in referenceOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </div>

              <div class="setting-block">
                <label>允许参考图来源</label>
                <el-checkbox-group v-model="imageForm.allowedReferenceSourceModes">
                  <el-checkbox v-for="item in referenceOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="setting-block setting-block--switch">
                <div>
                  <strong>允许参考图</strong>
                  <span>关闭后平衡模式只使用角色文字设定，强一致性会被拒绝。</span>
                </div>
                <el-switch v-model="imageForm.referenceImagesEnabled" />
              </div>

              <div class="setting-block setting-block--switch">
                <div>
                  <strong>加入最近剧情</strong>
                  <span>只取当前聊天的短场景摘要，不发送世界书或完整对话。</span>
                </div>
                <el-switch v-model="imageForm.recentSceneContextEnabled" />
              </div>

              <div class="setting-block setting-block--wide">
                <label>全局负面词</label>
                <el-input v-model="imageForm.negativePrompt" type="textarea" :rows="3" maxlength="2000" show-word-limit />
              </div>
            </div>
          </section>

          <section class="control-section">
            <header class="section-header">
              <div>
                <h2>服务通道与容量</h2>
                <p>用户自定义和官方平台是两条独立通道；这里只管理策略，不重复保存平台 Key。</p>
              </div>
            </header>

            <div class="channel-grid">
              <article class="channel-item">
                <div class="channel-item__icon"><el-icon><Connection /></el-icon></div>
                <div class="channel-item__body">
                  <div class="channel-item__title"><strong>用户自定义 API</strong><el-tag :type="imageForm.userByokEnabled ? 'success' : 'info'" effect="plain">{{ imageForm.userByokEnabled ? '已开放' : '未开放' }}</el-tag></div>
                  <p>用户在客户端配置自己的供应商、模型和 Key；选择自定义模式时始终优先走此通道。</p>
                  <span v-if="imageForm.userByokEnabled">准入等级：VIP {{ imageForm.userByokVipMinLevel }}+</span>
                </div>
              </article>
              <article class="channel-item">
                <div class="channel-item__icon channel-item__icon--official"><el-icon><Cpu /></el-icon></div>
                <div class="channel-item__body">
                  <div class="channel-item__title"><strong>官方平台 API</strong><el-tag :type="routingTagType(imageRouting)" effect="plain">{{ routingStatusLabel(imageRouting) }}</el-tag></div>
                  <p>系统模式统一使用模型路由中的 IMAGE 供应商池、执行顺序、故障切换和熔断状态。</p>
                  <span>{{ routingNodeSummary(imageRouting) }}</span>
                </div>
                <el-button :icon="Connection" @click="openModelRouting('IMAGE')">配置 IMAGE 路由</el-button>
              </article>
            </div>

            <div class="compatibility-band">
              <div>
                <strong>本地 Comfy 兼容通道</strong>
                <span>仅在用户选择系统模式且官方 IMAGE 路由未启用时接管；不会覆盖用户自定义 API。</span>
              </div>
              <el-switch v-model="imageForm.comfyFallbackEnabled" active-text="启用" inactive-text="关闭" />
            </div>

            <el-form :model="imageForm" label-position="top">
              <el-row :gutter="16">
                <el-col :xs="12" :md="6"><el-form-item label="全局并发"><el-input-number v-model="imageForm.globalConcurrentLimit" :min="1" :max="64" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                <el-col :xs="12" :md="6"><el-form-item label="单用户并发"><el-input-number v-model="imageForm.perUserConcurrentLimit" :min="1" :max="8" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                <el-col :xs="12" :md="6"><el-form-item label="计数 TTL（秒）"><el-input-number v-model="imageForm.counterTtlSeconds" :min="10" :max="7200" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                <el-col :xs="12" :md="6"><el-form-item label="请求超时（秒）"><el-input-number v-model="imageForm.requestTimeoutSeconds" :min="1" :max="600" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              </el-row>

              <el-row v-if="imageForm.comfyFallbackEnabled" :gutter="16" class="comfy-fields">
                <el-col :xs="24" :md="8"><el-form-item label="Comfy 地址"><el-input v-model="imageForm.comfyUrl" /></el-form-item></el-col>
                <el-col :xs="24" :md="8"><el-form-item label="文生图工作流"><el-input v-model="imageForm.workflow" /></el-form-item></el-col>
                <el-col :xs="24" :md="8"><el-form-item label="参考图工作流"><el-input v-model="imageForm.referenceWorkflow" /></el-form-item></el-col>
              </el-row>
            </el-form>
          </section>

          <section class="control-section">
            <header class="section-header section-header--table">
              <div><h2>角色覆盖策略</h2><p>未设置的角色自动继承全局；覆盖规则只存策略，不修改角色卡。</p></div>
              <div class="table-tools"><el-input v-model="characterQuery.keyword" clearable placeholder="角色名或 ID" @keyup.enter="loadCharacters"><el-icon><Search /></el-icon></el-input><el-button :icon="Refresh" @click="loadCharacters">刷新</el-button></div>
            </header>
            <el-table v-loading="characterLoading" :data="characterRows" stripe>
              <el-table-column label="角色" min-width="220">
                <template #default="scope"><div class="character-cell"><el-avatar :size="38" :src="assetUrl(characterValue(scope.row, 'avatarUrl') || characterValue(scope.row, 'stAvatarUrl'))">{{ characterValue(scope.row, 'characterName').slice(0, 1) }}</el-avatar><div><strong>{{ characterValue(scope.row, 'characterName') || `角色 #${scope.row.characterId}` }}</strong><span>ID {{ scope.row.characterId }}</span></div></div></template>
              </el-table-column>
              <el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="scope.row.hasOverride ? 'warning' : 'info'" effect="plain">{{ scope.row.hasOverride ? '单独覆盖' : '继承全局' }}</el-tag></template></el-table-column>
              <el-table-column label="默认模式" width="130"><template #default="scope"><el-tag :type="modeTagType(scope.row.effective && scope.row.effective.defaultMode)">{{ modeLabel(scope.row.effective && scope.row.effective.defaultMode) }}</el-tag></template></el-table-column>
              <el-table-column label="允许模式" min-width="220"><template #default="scope"><div class="tag-list"><el-tag v-for="mode in (scope.row.effective && scope.row.effective.allowedModes) || []" :key="mode" size="small" effect="plain">{{ modeLabel(mode) }}</el-tag></div></template></el-table-column>
              <el-table-column label="参考图" width="110"><template #default="scope"><span>{{ scope.row.effective && scope.row.effective.referenceImagesEnabled ? '允许' : '关闭' }}</span></template></el-table-column>
              <el-table-column label="操作" width="170" fixed="right"><template #default="scope"><el-button v-hasPermi="['ops:media:image:edit']" link type="primary" :icon="Edit" @click="openCharacterPolicy(scope.row)">配置</el-button><el-button v-if="scope.row.hasOverride" v-hasPermi="['ops:media:image:edit']" link type="danger" :icon="RefreshLeft" @click="restoreCharacterPolicy(scope.row)">恢复继承</el-button></template></el-table-column>
            </el-table>
            <pagination v-show="characterTotal > 0" :total="characterTotal" v-model:page="characterQuery.pageNum" v-model:limit="characterQuery.pageSize" @pagination="loadCharacters" />
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane name="voice">
        <template #label><span class="tab-label"><el-icon><Microphone /></el-icon>语音服务</span></template>
        <div v-loading="voiceLoading" class="workspace">
          <section class="control-section">
            <header class="section-header"><div><h2>语音运行策略</h2><p>TTS 与 STT 使用独立并发门和频率窗口，互不挤占。</p></div><el-button v-hasPermi="['ops:media:voice:edit']" type="primary" :loading="voiceSaving" :icon="Check" @click="saveVoicePolicy">保存语音策略</el-button></header>
            <div class="voice-summary">
              <div class="setting-block setting-block--switch"><div><strong>语音功能总开关</strong><span>同时控制语音输入、角色朗读和播放入口。</span></div><el-switch v-model="voiceForm.featureEnabled" /></div>
              <div class="summary-stat"><span>用户自定义语音</span><strong>{{ voiceForm.userByokEnabled ? `开放 · VIP ${voiceForm.userByokVipMinLevel}+` : '关闭' }}</strong></div>
              <div class="summary-stat"><span>官方 TTS 平台</span><strong :class="ttsRouting.ready ? 'text-ok' : 'text-muted'">{{ routingStatusLabel(ttsRouting) }}</strong></div>
              <div class="summary-stat"><span>官方 STT 平台</span><strong :class="sttRouting.ready ? 'text-ok' : 'text-muted'">{{ routingStatusLabel(sttRouting) }}</strong></div>
            </div>
          </section>

          <section class="control-section">
            <header class="section-header"><div><h2>语音平台 API</h2><p>平台地址、API Key、模型、TTS 默认音色与故障切换统一在模型路由配置。</p></div></header>
            <div class="route-card-grid">
              <article v-for="item in voiceRoutes" :key="item.capability" class="route-card">
                <div class="route-card__head">
                  <div><el-icon><component :is="item.icon" /></el-icon><strong>{{ item.title }}</strong></div>
                  <el-tag :type="routingTagType(item.routing)" effect="plain">{{ routingStatusLabel(item.routing) }}</el-tag>
                </div>
                <p>{{ item.description }}</p>
                <div class="route-card__nodes">{{ routingNodeSummary(item.routing) }}</div>
                <el-button :icon="Connection" @click="openModelRouting(item.capability)">前往模型路由</el-button>
              </article>
            </div>
          </section>

          <div class="voice-columns">
            <section class="control-section voice-panel"><header class="service-title"><el-icon><Headset /></el-icon><div><h2>TTS 语音合成</h2><p>按 AI 消息稳定任务 ID 分段生成，整条回复只计费一次。</p></div></header><limit-form v-model="voiceForm.runtime.tts" :window-seconds="voiceForm.runtime.rateWindowSeconds" /><div class="billing-line"><span>当前计费</span><strong>{{ costLabel(voiceForm.ttsScoreCost, voiceForm.ttsGoldCost) }}</strong></div></section>
            <section class="control-section voice-panel"><header class="service-title"><el-icon><Microphone /></el-icon><div><h2>STT 语音识别</h2><p>每段录音成功识别后计费，失败会退款。</p></div></header><limit-form v-model="voiceForm.runtime.stt" :window-seconds="voiceForm.runtime.rateWindowSeconds" /><div class="billing-line"><span>当前计费</span><strong>{{ costLabel(voiceForm.sttScoreCost, voiceForm.sttGoldCost) }}</strong></div></section>
            <section class="control-section voice-panel"><header class="service-title"><el-icon><UserFilled /></el-icon><div><h2>用户音色克隆</h2><p>只允许用户自己的硅基流动 Key；独立并发与频率限制，不占 TTS 播放通道。</p></div></header><limit-form v-model="voiceForm.runtime.voiceClone" :window-seconds="voiceForm.runtime.rateWindowSeconds" /><div class="billing-line"><span>平台计费</span><strong>不扣平台钱包</strong></div></section>
          </div>

          <section class="control-section"><header class="section-header"><div><h2>公共窗口</h2><p>仅影响语音请求计数，不改变聊天生成链路。</p></div></header><el-form label-position="top"><el-row :gutter="16"><el-col :xs="24" :md="8"><el-form-item label="频率窗口（秒）"><el-input-number v-model="voiceForm.runtime.rateWindowSeconds" :min="10" :max="3600" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :md="8"><el-form-item label="并发计数 TTL（秒）"><el-input-number v-model="voiceForm.runtime.counterTtlSeconds" :min="10" :max="7200" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row></el-form></section>
        </div>
      </el-tab-pane>

      <el-tab-pane name="templates">
        <template #label><span class="tab-label"><el-icon><Headset /></el-icon>音色模板</span></template>
        <section class="control-section template-section">
          <header class="section-header"><div><h2>角色音色模板</h2><p>模板保存参考素材；用户选择后使用自己的 Key 创建专属 voice，并在其账号下复用。</p></div><el-button v-hasPermi="['content:voice-template:edit']" type="primary" :icon="Plus" @click="openVoiceTemplate()">新增音色</el-button></header>
          <el-table v-loading="templateLoading" :data="templateRows" stripe>
            <el-table-column label="音色" min-width="250"><template #default="scope"><div class="template-name"><el-avatar shape="square" :size="44" :src="assetUrl(scope.row.coverImageUrl)"><el-icon><Headset /></el-icon></el-avatar><div><strong>{{ scope.row.displayName }}</strong><span>{{ scope.row.templateCode }}</span></div></div></template></el-table-column>
            <el-table-column prop="ttsModelName" label="推荐模型" min-width="220" />
            <el-table-column label="参考音频" min-width="260"><template #default="scope"><audio v-if="scope.row.referenceAudioUrl" :src="assetUrl(scope.row.referenceAudioUrl)" controls preload="none" /></template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><el-button v-hasPermi="['content:voice-template:edit']" link type="primary" :icon="Edit" @click="openVoiceTemplate(scope.row)">编辑</el-button><el-button v-hasPermi="['content:voice-template:edit']" link type="danger" :icon="Delete" @click="removeVoiceTemplate(scope.row)">删除</el-button></template></el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane v-if="canViewUserVoices" name="userVoices">
        <template #label><span class="tab-label"><el-icon><UserFilled /></el-icon>用户自建音色</span></template>
        <section class="control-section template-section">
          <header class="section-header section-header--table">
            <div><h2>用户私有音色运行状态</h2><p>这里只提供风控停用与恢复；私有 voice URI 和用户 API Key 不会返回后台页面。</p></div>
            <div class="table-tools">
              <el-input v-model="userVoiceQuery.keyword" clearable placeholder="用户 ID 或音色名称" @keyup.enter="loadUserVoices"><template #prefix><el-icon><Search /></el-icon></template></el-input>
              <el-select v-model="userVoiceQuery.status" clearable placeholder="全部状态" style="width: 130px" @change="loadUserVoices"><el-option label="可用" value="READY" /><el-option label="创建失败" value="FAILED" /><el-option label="创建中" value="PROVISIONING" /><el-option label="等待中" value="PENDING" /></el-select>
              <el-button :icon="Refresh" @click="loadUserVoices">刷新</el-button>
            </div>
          </header>
          <el-table v-loading="userVoiceLoading" :data="userVoiceRows" stripe>
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="userId" label="用户 ID" width="110" />
            <el-table-column prop="displayName" label="音色名称" min-width="180" />
            <el-table-column prop="modelName" label="创建模型" min-width="230" show-overflow-tooltip />
            <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="userVoiceStatusType(scope.row)">{{ userVoiceStatusText(scope.row) }}</el-tag></template></el-table-column>
            <el-table-column prop="lastError" label="最近错误" min-width="230" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column v-if="canManageUserVoices" label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button v-if="isUserVoiceProvisioning(scope.row)" link type="warning" @click="finishUserVoiceProvisioning(scope.row)">结束异常任务</el-button>
                <el-button v-else link :type="scope.row.disabled ? 'success' : 'danger'" @click="toggleUserVoice(scope.row)">{{ scope.row.disabled ? '恢复' : '停用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="userVoiceTotal > 0" :total="userVoiceTotal" v-model:page="userVoiceQuery.pageNum" v-model:limit="userVoiceQuery.pageSize" @pagination="loadUserVoices" />
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="characterDialog" width="680px" title="角色生图策略" append-to-body>
      <div class="dialog-character"><el-avatar :size="42" :src="assetUrl(characterPolicy.avatarUrl)">{{ characterPolicy.characterName.slice(0, 1) }}</el-avatar><div><strong>{{ characterPolicy.characterName }}</strong><span>未填写的项目继续继承全局策略</span></div></div>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12"><el-form-item label="该角色生图"><el-select v-model="characterPolicy.imageEnabled" style="width: 100%"><el-option label="继承全局" value="inherit" /><el-option label="允许" value="on" /><el-option label="关闭" value="off" /></el-select></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="默认模式"><el-select v-model="characterPolicy.defaultMode" style="width: 100%"><el-option label="继承全局" value="" /><el-option v-for="item in modeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :xs="24"><el-form-item label="允许模式"><el-switch v-model="characterPolicy.allowedModesInherit" active-text="继承全局" /><el-checkbox-group v-if="!characterPolicy.allowedModesInherit" v-model="characterPolicy.allowedModes" class="dialog-mode-list"><el-checkbox v-for="item in modeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="参考图来源"><el-select v-model="characterPolicy.referenceSourceMode" style="width: 100%"><el-option label="继承全局" value="" /><el-option v-for="item in referenceOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="允许参考图"><el-select v-model="characterPolicy.referenceImagesEnabled" style="width: 100%"><el-option label="继承全局" value="inherit" /><el-option label="允许" value="on" /><el-option label="关闭" value="off" /></el-select></el-form-item></el-col>
          <el-col :xs="24"><el-form-item label="负面词"><el-switch v-model="characterPolicy.negativePromptInherit" active-text="继承全局" /><el-input v-if="!characterPolicy.negativePromptInherit" v-model="characterPolicy.negativePrompt" class="mt8" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="effective-preview"><span>最终生效</span><strong>{{ modeLabel(characterPolicy.effective.defaultMode) }}</strong><span>{{ characterPolicy.effective.referenceImagesEnabled ? '允许参考图' : '仅文字设定' }}</span></div>
      <template #footer><el-button @click="characterDialog = false">取消</el-button><el-button type="primary" :loading="characterSaving" @click="saveCharacterPolicy">保存覆盖</el-button></template>
    </el-dialog>

    <el-dialog v-model="templateDialog" :title="templateForm.id ? '编辑音色模板' : '新增音色模板'" width="760px" append-to-body>
      <el-form :model="templateForm" label-position="top">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12"><el-form-item label="模板名称"><el-input v-model="templateForm.displayName" /></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="模板编码"><el-input v-model="templateForm.templateCode" :disabled="!!templateForm.id" placeholder="留空自动生成" /></el-form-item></el-col>
          <el-col :xs="24" :md="16"><el-form-item label="推荐 TTS 模型"><el-input v-model="templateForm.ttsModelName" /></el-form-item></el-col>
          <el-col :xs="12" :md="4"><el-form-item label="启用"><el-switch v-model="templateForm.enabled" /></el-form-item></el-col>
          <el-col :xs="12" :md="4"><el-form-item label="排序"><el-input-number v-model="templateForm.sortOrder" :min="0" :max="9999" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24"><el-form-item label="模板描述"><el-input v-model="templateForm.description" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :xs="24"><el-form-item label="示例文案"><el-input v-model="templateForm.sampleScript" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="参考音频"><div class="upload-field"><audio v-if="templateForm.referenceAudioUrl" :src="assetUrl(templateForm.referenceAudioUrl)" controls preload="none" /><el-upload :action="audioUploadAction" :headers="uploadHeaders" :show-file-list="false" accept="audio/*" :before-upload="beforeAudioUpload" :on-success="audioUploadSuccess"><el-button :icon="Upload">上传音频</el-button></el-upload></div></el-form-item></el-col>
          <el-col :xs="24" :md="12"><el-form-item label="封面图片"><div class="upload-field upload-field--cover"><el-image v-if="templateForm.coverImageUrl" :src="assetUrl(templateForm.coverImageUrl)" fit="cover" /><el-upload :action="imageUploadAction" :headers="uploadHeaders" :show-file-list="false" accept="image/*" :before-upload="beforeImageUpload" :on-success="imageUploadSuccess"><el-button :icon="Upload">上传封面</el-button></el-upload></div></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="templateDialog = false">取消</el-button><el-button type="primary" :loading="templateSaving" @click="saveVoiceTemplate">保存模板</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="JgMediaCenter">
import { computed, defineComponent, getCurrentInstance, h, onMounted, reactive, ref } from 'vue'
import { ElForm, ElFormItem, ElInputNumber, ElMessageBox } from 'element-plus'
import { Check, Connection, Cpu, Delete, Edit, Headset, Microphone, Picture, Plus, Refresh, RefreshLeft, Search, Upload, UserFilled } from '@element-plus/icons-vue'
import {
  deleteCharacterImagePolicy,
  getCharacterImagePolicy,
  getMediaImagePolicy,
  getMediaVoicePolicy,
  finishUserTtsVoiceProvisioning,
  listUserTtsVoices,
  listCharacterImagePolicies,
  updateCharacterImagePolicy,
  updateMediaImagePolicy,
  updateMediaVoicePolicy,
  updateUserTtsVoiceDisabled
} from '@/api/jiugai/media'
import { addTtsVoiceTemplate, deleteTtsVoiceTemplate, listTtsVoiceTemplates, updateTtsVoiceTemplate } from '@/api/jiugai/ttsVoiceTemplate'
import { getToken } from '@/utils/auth'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const LimitForm = defineComponent({
  name: 'LimitForm',
  props: { modelValue: { type: Object, required: true }, windowSeconds: { type: Number, default: 60 } },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const field = (key, label, max) => h(ElFormItem, { label }, () => h(ElInputNumber, {
      modelValue: props.modelValue[key], min: 1, max, 'controls-position': 'right', style: 'width:100%',
      'onUpdate:modelValue': (value) => emit('update:modelValue', { ...props.modelValue, [key]: value })
    }))
    return () => h(ElForm, { labelPosition: 'top', class: 'limit-form' }, () => [
      field('globalConcurrentLimit', '全局并发', 128),
      field('perUserConcurrentLimit', '单用户并发', 8),
      field('perUserRequestsPerWindow', `每 ${props.windowSeconds} 秒单用户请求`, 300)
    ])
  }
})

const { proxy } = getCurrentInstance()
const router = useRouter()
const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }
const audioUploadAction = baseApi + '/admin/jiugai/tts-voice-template/upload/audio'
const imageUploadAction = baseApi + '/admin/jiugai/tts-voice-template/upload/image'
const activeTab = ref('image')
const canViewUserVoices = computed(() => hasMediaPermission('ops:media:user-voice:view'))
const canManageUserVoices = computed(() => hasMediaPermission('ops:media:user-voice:manage'))

function hasMediaPermission(permission) {
  if (proxy?.$auth?.hasPermiOr) return proxy.$auth.hasPermiOr([permission])
  return !!proxy?.$auth?.hasPermi?.(permission)
}

const modeOptions = ref([
  { value: 'free', label: '自由文生图', description: '只使用用户输入，保留原来的纯文生图体验。' },
  { value: 'balanced', label: '平衡一致性', description: '注入角色视觉设定，参考图不可用时允许降级。' },
  { value: 'strong', label: '强一致性', description: '必须有参考图和支持能力，不满足时明确拒绝。' }
])
const referenceOptions = ref([
  { value: 'latest_generated_first', label: '最近生成优先' },
  { value: 'avatar_only', label: '仅角色头像' }
])
const imageLoading = ref(false)
const imageSaving = ref(false)
const emptyRouting = (capability) => ({ capability, routeKey: `${capability.toLowerCase()}.default`, runtimeEnabled: false, routeDefined: false, routeEnabled: false, routeConfigured: false, ready: false, status: 'runtime_disabled', deploymentCount: 0, configuredNodeCount: 0, availableNodeCount: 0, nodes: [] })
const imageRouting = reactive(emptyRouting('IMAGE'))
const imageForm = reactive({
  featureEnabled: true, userByokEnabled: false, userByokVipMinLevel: 0, comfyFallbackEnabled: false, globalConcurrentLimit: 2,
  perUserConcurrentLimit: 1, counterTtlSeconds: 600, requestTimeoutSeconds: 90,
  defaultConsistencyMode: 'balanced', allowedConsistencyModes: ['free', 'balanced', 'strong'],
  defaultReferenceSourceMode: 'latest_generated_first', allowedReferenceSourceModes: ['latest_generated_first', 'avatar_only'],
  referenceImagesEnabled: true, recentSceneContextEnabled: true, negativePrompt: '',
  comfyUrl: 'http://127.0.0.1:8188', workflow: 'Default_Comfy_Workflow.json',
  referenceWorkflow: 'Char_Avatar_Comfy_Workflow.json', model: '', sampler: 'euler', scheduler: 'normal', steps: 28, scale: 7, seed: -1, denoise: 1
})
const currentModeDescription = computed(() => (modeOptions.value.find((item) => item.value === imageForm.defaultConsistencyMode) || {}).description || '')

function applyImagePolicy(data) {
  Object.keys(imageForm).forEach((key) => {
    if (Object.prototype.hasOwnProperty.call(data, key)) imageForm[key] = data[key]
  })
  imageForm.featureEnabled = data.featureEnabled !== false
  imageForm.allowedConsistencyModes = Array.isArray(data.allowedConsistencyModes) ? data.allowedConsistencyModes : ['free', 'balanced', 'strong']
  imageForm.allowedReferenceSourceModes = Array.isArray(data.allowedReferenceSourceModes) ? data.allowedReferenceSourceModes : ['latest_generated_first', 'avatar_only']
  if (Array.isArray(data.modeOptions)) modeOptions.value = data.modeOptions
  if (Array.isArray(data.referenceSourceOptions)) referenceOptions.value = data.referenceSourceOptions
  Object.assign(imageRouting, emptyRouting('IMAGE'), data.imageRouting || {})
}

async function loadImagePolicy() {
  imageLoading.value = true
  try { const res = await getMediaImagePolicy(); applyImagePolicy(res.data || {}) }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载生图策略失败')) }
  finally { imageLoading.value = false }
}

async function saveImagePolicy() {
  if (!imageForm.allowedConsistencyModes.length) return proxy.$modal.msgError('至少开放一种生图模式')
  if (!imageForm.allowedConsistencyModes.includes(imageForm.defaultConsistencyMode)) return proxy.$modal.msgError('默认模式必须包含在允许模式中')
  if (!imageForm.allowedReferenceSourceModes.length) return proxy.$modal.msgError('至少开放一种参考图来源')
  if (!imageForm.allowedReferenceSourceModes.includes(imageForm.defaultReferenceSourceMode)) return proxy.$modal.msgError('默认参考图来源必须包含在允许来源中')
  if (!imageForm.referenceImagesEnabled && imageForm.allowedConsistencyModes.includes('strong')) return proxy.$modal.msgError('关闭参考图前，请先移除强一致性模式')
  imageSaving.value = true
  const payload = {
    featureEnabled: imageForm.featureEnabled,
    comfyFallbackEnabled: imageForm.comfyFallbackEnabled,
    globalConcurrentLimit: imageForm.globalConcurrentLimit,
    perUserConcurrentLimit: imageForm.perUserConcurrentLimit,
    counterTtlSeconds: imageForm.counterTtlSeconds,
    requestTimeoutSeconds: imageForm.requestTimeoutSeconds,
    defaultConsistencyMode: imageForm.defaultConsistencyMode,
    allowedConsistencyModes: imageForm.allowedConsistencyModes,
    defaultReferenceSourceMode: imageForm.defaultReferenceSourceMode,
    allowedReferenceSourceModes: imageForm.allowedReferenceSourceModes,
    referenceImagesEnabled: imageForm.referenceImagesEnabled,
    recentSceneContextEnabled: imageForm.recentSceneContextEnabled,
    negativePrompt: imageForm.negativePrompt,
    comfyUrl: imageForm.comfyUrl,
    workflow: imageForm.workflow,
    referenceWorkflow: imageForm.referenceWorkflow,
    model: imageForm.model,
    sampler: imageForm.sampler,
    scheduler: imageForm.scheduler,
    steps: imageForm.steps,
    scale: imageForm.scale,
    seed: imageForm.seed,
    denoise: imageForm.denoise
  }
  try { const res = await updateMediaImagePolicy(payload); applyImagePolicy(res.data || {}); proxy.$modal.msgSuccess('生图策略已保存') }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存生图策略失败')) }
  finally { imageSaving.value = false }
}

const characterLoading = ref(false)
const characterRows = ref([])
const characterTotal = ref(0)
const characterQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadCharacters() {
  characterLoading.value = true
  try { const res = await listCharacterImagePolicies(characterQuery); characterRows.value = Array.isArray(res.rows) ? res.rows : []; characterTotal.value = Number(res.total || 0) }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载角色策略失败')) }
  finally { characterLoading.value = false }
}

function characterValue(row, key) {
  if (!row) return ''
  return String(row[key] ?? row[key.toLowerCase()] ?? '')
}

const characterDialog = ref(false)
const characterSaving = ref(false)
const characterPolicy = reactive({ characterId: 0, characterName: '', avatarUrl: '', imageEnabled: 'inherit', defaultMode: '', allowedModesInherit: true, allowedModes: [], referenceSourceMode: '', referenceImagesEnabled: 'inherit', negativePromptInherit: true, negativePrompt: '', effective: {} })

async function openCharacterPolicy(row) {
  try {
    const res = await getCharacterImagePolicy(row.characterId)
    const data = res.data || {}; const override = data.override || {}
    Object.assign(characterPolicy, {
      characterId: data.characterId, characterName: data.characterName || '', avatarUrl: data.avatarUrl || '',
      imageEnabled: override.imageEnabled == null ? 'inherit' : override.imageEnabled ? 'on' : 'off',
      defaultMode: override.defaultMode || '', allowedModesInherit: !Array.isArray(override.allowedModes),
      allowedModes: Array.isArray(override.allowedModes) ? override.allowedModes : [],
      referenceSourceMode: override.referenceSourceMode || '',
      referenceImagesEnabled: override.referenceImagesEnabled == null ? 'inherit' : override.referenceImagesEnabled ? 'on' : 'off',
      negativePromptInherit: override.negativePrompt == null, negativePrompt: override.negativePrompt || '', effective: data.effective || {}
    })
    characterDialog.value = true
  } catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载角色策略失败')) }
}

function triState(value) { return value === 'inherit' ? null : value === 'on' }

async function saveCharacterPolicy() {
  if (!characterPolicy.allowedModesInherit && !characterPolicy.allowedModes.length) return proxy.$modal.msgError('至少开放一种角色生图模式')
  if (!characterPolicy.allowedModesInherit && characterPolicy.defaultMode && !characterPolicy.allowedModes.includes(characterPolicy.defaultMode)) return proxy.$modal.msgError('角色默认模式必须包含在允许模式中')
  if (characterPolicy.referenceImagesEnabled === 'off' && !characterPolicy.allowedModesInherit && characterPolicy.allowedModes.includes('strong')) return proxy.$modal.msgError('该角色关闭参考图后不能开放强一致性模式')
  characterSaving.value = true
  try {
    await updateCharacterImagePolicy(characterPolicy.characterId, {
      imageEnabled: triState(characterPolicy.imageEnabled), defaultMode: characterPolicy.defaultMode || null,
      allowedModes: characterPolicy.allowedModesInherit ? null : characterPolicy.allowedModes,
      referenceSourceMode: characterPolicy.referenceSourceMode || null,
      referenceImagesEnabled: triState(characterPolicy.referenceImagesEnabled),
      negativePrompt: characterPolicy.negativePromptInherit ? null : characterPolicy.negativePrompt
    })
    proxy.$modal.msgSuccess('角色生图策略已保存'); characterDialog.value = false; await loadCharacters()
  } catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存角色策略失败')) }
  finally { characterSaving.value = false }
}

async function restoreCharacterPolicy(row) {
  try { await ElMessageBox.confirm('恢复后该角色立即继承全局生图策略。', '恢复继承', { type: 'warning' }); await deleteCharacterImagePolicy(row.characterId); proxy.$modal.msgSuccess('已恢复继承全局'); await loadCharacters() }
  catch (error) { if (error !== 'cancel' && error !== 'close') proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '恢复失败')) }
}

function modeLabel(mode) { return ({ free: '自由文生图', balanced: '平衡一致性', strong: '强一致性' })[mode] || '未配置' }
function modeTagType(mode) { return mode === 'strong' ? 'danger' : mode === 'balanced' ? 'warning' : 'info' }

const voiceLoading = ref(false)
const voiceSaving = ref(false)
const defaultLimits = () => ({ globalConcurrentLimit: 1, perUserConcurrentLimit: 1, perUserRequestsPerWindow: 6 })
const voiceForm = reactive({ featureEnabled: true, userByokEnabled: false, userByokVipMinLevel: 0, ttsScoreCost: 0, ttsGoldCost: 0, sttScoreCost: 0, sttGoldCost: 0, runtime: { counterTtlSeconds: 180, rateWindowSeconds: 60, tts: { ...defaultLimits(), globalConcurrentLimit: 8, perUserRequestsPerWindow: 12 }, stt: { ...defaultLimits(), globalConcurrentLimit: 4 }, voiceClone: { ...defaultLimits(), globalConcurrentLimit: 3, perUserConcurrentLimit: 1, perUserRequestsPerWindow: 3 } } })
const ttsRouting = reactive(emptyRouting('TTS'))
const sttRouting = reactive(emptyRouting('STT'))
const voiceRoutes = computed(() => [
  { capability: 'TTS', title: 'TTS 语音合成', icon: Headset, routing: ttsRouting, description: '配置官方语音合成供应商、模型、默认音色和 fallback 顺序。' },
  { capability: 'STT', title: 'STT 语音识别', icon: Microphone, routing: sttRouting, description: '配置官方语音识别供应商、模型和 fallback 顺序。' }
])

function applyVoicePolicy(data) {
  Object.assign(voiceForm, data || {})
  voiceForm.runtime = { counterTtlSeconds: 180, rateWindowSeconds: 60, ...(data.runtime || {}), tts: { ...defaultLimits(), ...(data.runtime && data.runtime.tts || {}) }, stt: { ...defaultLimits(), ...(data.runtime && data.runtime.stt || {}) }, voiceClone: { ...defaultLimits(), globalConcurrentLimit: 3, perUserConcurrentLimit: 1, perUserRequestsPerWindow: 3, ...(data.runtime && data.runtime.voiceClone || {}) } }
  Object.assign(ttsRouting, emptyRouting('TTS'), data.ttsRouting || {})
  Object.assign(sttRouting, emptyRouting('STT'), data.sttRouting || {})
}

async function loadVoicePolicy() {
  voiceLoading.value = true
  try { const res = await getMediaVoicePolicy(); applyVoicePolicy(res.data || {}) }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载语音策略失败')) }
  finally { voiceLoading.value = false }
}

async function saveVoicePolicy() {
  voiceSaving.value = true
  try { await updateMediaVoicePolicy({ featureEnabled: voiceForm.featureEnabled, runtime: voiceForm.runtime }); proxy.$modal.msgSuccess('语音策略已保存'); await loadVoicePolicy() }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存语音策略失败')) }
  finally { voiceSaving.value = false }
}

function costLabel(score, gold) { const parts = []; if (Number(score) > 0) parts.push(`${score} 钻`); if (Number(gold) > 0) parts.push(`${gold} 币`); return parts.length ? parts.join(' + ') : '免费' }
function routingStatusLabel(routing) { return ({ ready: '运行就绪', runtime_disabled: '运行开关关闭', route_missing: '未建默认路由', route_disabled: '默认路由停用', no_configured_nodes: '缺少可用节点', temporarily_unavailable: '节点暂不可用' })[routing && routing.status] || '未配置' }
function routingTagType(routing) { return routing && routing.ready ? 'success' : routing && routing.routeConfigured ? 'warning' : 'info' }
function routingNodeSummary(routing) {
  const nodes = Array.isArray(routing && routing.nodes) ? routing.nodes : []
  if (!nodes.length) return '尚未配置供应商模型'
  const names = nodes.slice(0, 3).map((item) => `${item.displayName || item.vendor || '供应商'} · ${item.modelName || '未填模型'}`)
  return `${names.join(' → ')}${nodes.length > 3 ? ` 等 ${nodes.length} 个节点` : ''}`
}
function openModelRouting(capability) { router.push({ path: '/jiugai/content/openrouter', query: { capability } }) }

const templateLoading = ref(false)
const templateSaving = ref(false)
const templateRows = ref([])
const templateDialog = ref(false)
const emptyTemplate = () => ({ id: null, templateCode: '', displayName: '', providerSource: 'siliconflow', ttsModelName: '', description: '', referenceAudioUrl: '', coverImageUrl: '', sampleScript: '请用温柔自然的语气说话。', enabled: true, sortOrder: 100 })
const templateForm = reactive(emptyTemplate())

async function loadTemplates() {
  templateLoading.value = true
  try { const res = await listTtsVoiceTemplates(); templateRows.value = Array.isArray(res.rows) ? res.rows : [] }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载音色模板失败')) }
  finally { templateLoading.value = false }
}

function openVoiceTemplate(row) { Object.assign(templateForm, emptyTemplate(), row || {}); templateDialog.value = true }
async function saveVoiceTemplate() {
  if (!String(templateForm.displayName || '').trim()) return proxy.$modal.msgError('请填写模板名称')
  if (!String(templateForm.referenceAudioUrl || '').trim()) return proxy.$modal.msgError('请上传参考音频')
  if (!String(templateForm.sampleScript || '').trim()) return proxy.$modal.msgError('请填写示例文案')
  templateSaving.value = true
  try { const payload = { ...templateForm, providerSource: 'siliconflow' }; await (payload.id ? updateTtsVoiceTemplate(payload) : addTtsVoiceTemplate(payload)); proxy.$modal.msgSuccess('音色模板已保存'); templateDialog.value = false; await loadTemplates() }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存音色模板失败')) }
  finally { templateSaving.value = false }
}
async function removeVoiceTemplate(row) {
  try { await ElMessageBox.confirm(`确认删除“${row.displayName}”吗？`, '删除音色', { type: 'warning' }); await deleteTtsVoiceTemplate(row.id); proxy.$modal.msgSuccess('音色模板已删除'); await loadTemplates() }
  catch (error) { if (error !== 'cancel' && error !== 'close') proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除失败')) }
}
function beforeAudioUpload(file) { if (Number(file.size || 0) > 10 * 1024 * 1024) { proxy.$modal.msgError('参考音频请控制在 10MB 以内'); return false } return true }
function beforeImageUpload(file) { if (Number(file.size || 0) > 10 * 1024 * 1024) { proxy.$modal.msgError('封面图片请控制在 10MB 以内'); return false } return true }
function audioUploadSuccess(response) { if (response && Number(response.code) === 200 && response.fileName) templateForm.referenceAudioUrl = response.fileName; else proxy.$modal.msgError((response && response.msg) || '参考音频上传失败') }
function imageUploadSuccess(response) { if (response && Number(response.code) === 200 && response.fileName) templateForm.coverImageUrl = response.fileName; else proxy.$modal.msgError((response && response.msg) || '封面上传失败') }
function assetUrl(url) { const value = String(url || '').trim(); if (!value || /^(https?:|data:|blob:)/i.test(value)) return value; return value.startsWith('/') ? baseApi + value : value }

const userVoiceLoading = ref(false)
const userVoiceRows = ref([])
const userVoiceTotal = ref(0)
const userVoiceQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
async function loadUserVoices() {
  if (!canViewUserVoices.value) {
    userVoiceRows.value = []
    userVoiceTotal.value = 0
    return
  }
  userVoiceLoading.value = true
  try { const res = await listUserTtsVoices({ ...userVoiceQuery }); userVoiceRows.value = Array.isArray(res.rows) ? res.rows : []; userVoiceTotal.value = Number(res.total || 0) }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载用户音色失败')) }
  finally { userVoiceLoading.value = false }
}
function userVoiceStatusType(row) { if (row && row.disabled) return 'info'; return row && row.status === 'READY' ? 'success' : row && row.status === 'FAILED' ? 'danger' : 'warning' }
function userVoiceStatusText(row) { if (row && row.disabled) return '已停用'; return ({ READY: '可用', FAILED: '失败', PROVISIONING: '创建中', PENDING: '等待中' })[row && row.status] || '未知' }
function isUserVoiceProvisioning(row) { return ['PENDING', 'PROVISIONING'].includes(String(row && row.status || '').toUpperCase()) }
async function finishUserVoiceProvisioning(row) {
  try {
    await ElMessageBox.confirm(
      `确认结束音色“${row && row.displayName ? row.displayName : row.id}”的异常创建任务吗？结束后会释放用户名额。`,
      '结束异常任务',
      { type: 'warning', confirmButtonText: '确认结束', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  try {
    await finishUserTtsVoiceProvisioning(row.id)
    proxy.$modal.msgSuccess('异常创建任务已结束，用户名额已释放')
    await loadUserVoices()
  } catch (error) {
    proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '结束任务失败'))
  }
}
async function toggleUserVoice(row) {
  const next = !(row && row.disabled)
  try { await updateUserTtsVoiceDisabled(row.id, next); proxy.$modal.msgSuccess(next ? '音色已停用' : '音色已恢复'); await loadUserVoices() }
  catch (error) { proxy.$modal.msgError(jiugaiRequestErrorMessage(error, next ? '停用失败' : '恢复失败')) }
}

onMounted(() => {
  loadImagePolicy()
  loadCharacters()
  loadVoicePolicy()
  loadTemplates()
  if (canViewUserVoices.value) loadUserVoices()
})
</script>

<style scoped>
.media-center { --ink: #18211d; --muted: #66726c; --line: #dfe6e2; --green: #16845b; --amber: #b96f18; background: #f4f7f5; min-height: calc(100vh - 84px); }
.media-heading { min-height: 118px; padding: 22px 26px; background: #18211d; color: #fff; display: flex; align-items: center; justify-content: space-between; gap: 24px; border-radius: 6px; }
.media-heading__eyebrow { color: #7fd7ae; font-size: 11px; font-weight: 700; letter-spacing: 1.4px; }
.media-heading h1 { margin: 5px 0 4px; font-family: 'Microsoft YaHei UI', sans-serif; font-size: 26px; letter-spacing: 0; }
.media-heading p { margin: 0; color: #bdc8c2; font-size: 13px; }
.media-heading__status { display: flex; gap: 10px; flex-wrap: wrap; }
.status-item { height: 34px; padding: 0 12px; border: 1px solid #3b4942; border-radius: 4px; display: flex; align-items: center; gap: 8px; color: #dce4df; font-size: 12px; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #7b8780; }.status-dot.is-on { background: #5ed19b; box-shadow: 0 0 0 3px rgba(94, 209, 155, .14); }
.media-tabs { margin-top: 14px; }.media-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 18px; background: #fff; border: 1px solid var(--line); border-radius: 6px 6px 0 0; }.media-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: var(--line); }.tab-label { display: inline-flex; align-items: center; gap: 7px; }
.workspace { display: grid; gap: 14px; padding-top: 14px; }.control-section { background: #fff; border: 1px solid var(--line); border-radius: 6px; padding: 20px; }.section-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }.section-header h2,.service-title h2 { margin: 0 0 4px; font-size: 16px; color: var(--ink); letter-spacing: 0; }.section-header p,.service-title p { margin: 0; color: var(--muted); font-size: 12px; }.section-header--table { align-items: center; }
.setting-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; }.setting-block { min-height: 88px; padding: 14px; border: 1px solid var(--line); border-radius: 5px; display: flex; flex-direction: column; gap: 10px; }.setting-block--wide { grid-column: 1 / -1; }.setting-block--switch { flex-direction: row; align-items: center; justify-content: space-between; }.setting-block strong,.setting-block label { color: var(--ink); font-size: 13px; }.setting-block span,.setting-block small { display: block; margin-top: 4px; color: var(--muted); font-size: 11px; line-height: 1.5; }.mode-selector { align-self: flex-start; }
.channel-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin-bottom: 12px; }.channel-item { min-height: 112px; padding: 14px; border: 1px solid var(--line); border-radius: 5px; display: grid; grid-template-columns: 36px minmax(0,1fr) auto; align-items: start; gap: 12px; }.channel-item__icon { width: 34px; height: 34px; border-radius: 5px; display: grid; place-items: center; background: #eef3f0; color: #52615a; }.channel-item__icon--official { background: #e8f5ee; color: var(--green); }.channel-item__body { min-width: 0; }.channel-item__title { display: flex; align-items: center; gap: 8px; }.channel-item__title strong { color: var(--ink); font-size: 13px; }.channel-item p { margin: 7px 0 5px; color: var(--muted); font-size: 11px; line-height: 1.6; }.channel-item__body > span { display: block; overflow: hidden; color: #52615a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.compatibility-band { margin-bottom: 16px; padding: 12px 14px; border: 1px dashed #c9d5ce; background: #f8faf9; display: flex; align-items: center; justify-content: space-between; gap: 16px; }.compatibility-band strong,.compatibility-band span { display: block; }.compatibility-band strong { color: var(--ink); font-size: 12px; }.compatibility-band span { margin-top: 3px; color: var(--muted); font-size: 11px; }.comfy-fields { padding-top: 12px; border-top: 1px solid var(--line); }
.table-tools { display: flex; gap: 8px; }.table-tools .el-input { width: 220px; }.character-cell,.template-name,.dialog-character { display: flex; align-items: center; gap: 10px; }.character-cell strong,.character-cell span,.template-name strong,.template-name span,.dialog-character strong,.dialog-character span { display: block; }.character-cell span,.template-name span,.dialog-character span { color: var(--muted); font-size: 11px; margin-top: 2px; }.tag-list { display: flex; gap: 5px; flex-wrap: wrap; }
.voice-summary { display: grid; grid-template-columns: minmax(300px,2fr) repeat(3,minmax(130px,1fr)); gap: 10px; }.summary-stat { border-left: 3px solid #dfe6e2; padding: 10px 12px; background: #f8faf9; }.summary-stat span,.summary-stat strong { display: block; }.summary-stat span { color: var(--muted); font-size: 11px; }.summary-stat strong { margin-top: 7px; color: var(--ink); font-size: 13px; }.text-ok { color: var(--green)!important; }.text-muted { color: var(--muted)!important; }
.voice-columns { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; }.service-title { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 16px; }.service-title .el-icon { margin-top: 1px; color: var(--green); font-size: 20px; }.voice-panel :deep(.limit-form) { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 12px; }.billing-line { display: flex; justify-content: space-between; padding-top: 12px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; }.billing-line strong { color: var(--amber); }
.route-card-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }.route-card { padding: 14px; border: 1px solid var(--line); border-radius: 5px; }.route-card__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.route-card__head > div { display: flex; align-items: center; gap: 8px; color: var(--green); }.route-card__head strong { color: var(--ink); font-size: 13px; }.route-card p { min-height: 36px; margin: 10px 0; color: var(--muted); font-size: 11px; line-height: 1.6; }.route-card__nodes { min-height: 34px; margin-bottom: 12px; padding: 8px 10px; overflow: hidden; background: #f4f7f5; color: #52615a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.template-section { margin-top: 14px; }.template-section audio { width: 235px; height: 32px; }.dialog-character { padding: 10px 12px; margin-bottom: 16px; background: #f4f7f5; border-radius: 5px; }.dialog-mode-list { margin-top: 10px; }.effective-preview { padding: 10px 12px; border: 1px solid #cfe3d9; background: #f3fbf7; display: flex; align-items: center; gap: 12px; border-radius: 5px; font-size: 12px; color: var(--muted); }.effective-preview strong { color: var(--green); }.upload-field { min-height: 88px; padding: 10px; border: 1px dashed #cbd6d0; display: flex; flex-direction: column; align-items: flex-start; gap: 10px; border-radius: 5px; }.upload-field audio { width: 100%; height: 32px; }.upload-field--cover { flex-direction: row; align-items: center; }.upload-field--cover .el-image { width: 68px; height: 68px; border-radius: 4px; }.mt8 { margin-top: 8px; }
@media (max-width: 900px) { .media-heading { align-items: flex-start; flex-direction: column; }.setting-grid,.channel-grid,.voice-columns,.route-card-grid { grid-template-columns: 1fr; }.setting-block--wide { grid-column: auto; }.voice-summary { grid-template-columns: 1fr 1fr; }.voice-panel :deep(.limit-form) { grid-template-columns: 1fr; }.section-header--table { align-items: flex-start; flex-direction: column; }.table-tools { width: 100%; }.table-tools .el-input { flex: 1; width: auto; } }
@media (max-width: 520px) { .media-center { padding: 10px; }.media-heading { padding: 18px; }.voice-summary { grid-template-columns: 1fr; }.section-header { flex-direction: column; }.section-header .el-button { width: 100%; }.mode-selector { display: flex; flex-wrap: wrap; }.control-section { padding: 14px; } }
</style>
