<template>
  <section class="routing-shell">
    <header class="routing-header">
      <div>
        <div class="routing-kicker">UNIFIED PROVIDER ROUTING</div>
        <h2>统一模型供应商</h2>
      </div>
      <div class="routing-header-actions">
        <el-tag :type="flags.enabled ? 'success' : 'info'" effect="plain">
          {{ flags.enabled ? '新路由已启用' : '旧路由生效中' }}
        </el-tag>
        <el-button
          v-if="activeCapability === 'CHAT' && chatWorkspace === 'providers'"
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Upload"
          :loading="importingLegacy"
          @click="importLegacy"
        >
          导入旧聊天路由
        </el-button>
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Setting"
          @click="openRuntimeDialog"
        >
          运行开关
        </el-button>
        <el-button :icon="Refresh" circle :loading="loading" title="刷新" @click="load" />
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          type="primary"
          :icon="Plus"
          @click="openProviderDialog()"
        >
          新增供应商能力
        </el-button>
      </div>
    </header>

    <div class="rollout-strip">
      <div class="rollout-item">
        <span>影子对比</span>
        <strong>{{ flags.shadowEnabled ? '开启' : '关闭' }}</strong>
      </div>
      <div class="rollout-item">
        <span>聊天灰度</span>
        <strong>{{ flags.chatCanaryPercent || 0 }}%</strong>
      </div>
      <div class="rollout-item">
        <span>BYOK 转官方</span>
        <strong>{{ flags.byokFallbackToOfficial ? '允许' : '禁止' }}</strong>
      </div>
      <div class="rollout-item rollout-wide">
        <span>当前能力开关</span>
        <strong>{{ capabilityRuntimeText }}</strong>
      </div>
      <div class="rollout-item">
        <span>开关来源</span>
        <strong>{{ flags.source === 'database' ? '后台动态配置' : '环境默认值' }}</strong>
      </div>
    </div>

    <div class="capability-toolbar">
      <el-radio-group v-model="activeCapability" size="large">
        <el-radio-button v-for="item in capabilityOptions" :key="item.value" :value="item.value">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-radio-button>
      </el-radio-group>
      <div class="capability-count">
        <strong>{{ capabilityDeployments.length }}</strong>
        <span>个已配置模型</span>
      </div>
    </div>

    <div v-if="activeCapability === 'CHAT'" class="chat-workspace-tabs">
      <el-radio-group v-model="chatWorkspace">
        <el-radio-button value="offerings">用户可选模型</el-radio-button>
        <el-radio-button value="providers">供应商与兜底</el-radio-button>
      </el-radio-group>
      <span>公开名称、价格与权限和真实上游账户分开管理</span>
    </div>

    <template v-if="activeCapability !== 'CHAT' || chatWorkspace === 'providers'">
    <div class="provider-table-wrap">
      <el-table v-loading="loading" :data="capabilityDeployments" row-key="id">
        <el-table-column label="供应商" min-width="190">
          <template #default="{ row }">
            <div class="provider-name">
              <span class="provider-mark">{{ providerInitial(row) }}</span>
              <div>
                <strong>{{ accountFor(row)?.displayName || '--' }}</strong>
                <small>{{ accountFor(row)?.vendor || '--' }} · {{ accountFor(row)?.providerKey || '--' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="上游模型" min-width="220" />
        <el-table-column label="协议" min-width="135">
          <template #default="{ row }">{{ protocolLabel(row.capability) }}</template>
        </el-table-column>
        <el-table-column label="API 地址" min-width="230">
          <template #default="{ row }">
            <span class="mono-cell">{{ accountFor(row)?.baseUrl || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="deploymentStatus(row).type" effect="plain">
              {{ deploymentStatus(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连续失败" width="90" align="center">
          <template #default="{ row }">{{ row.consecutiveFailures || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProviderDialog(row)">编辑</el-button>
            <el-button link type="success" @click="probeSaved(row)">实测</el-button>
            <el-button link type="danger" @click="removeDeployment(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="`${capabilityLabel} 尚未配置供应商模型`" :image-size="72" />
        </template>
      </el-table>
    </div>

    <div class="route-band">
      <div class="route-title">
        <div>
          <span>执行顺序</span>
          <strong>{{ capabilityRoute?.displayName || `${capabilityLabel}默认路由` }}</strong>
        </div>
        <el-button
          v-hasPermi="['ops:openrouter:edit']"
          :icon="Sort"
          :disabled="capabilityDeployments.length === 0"
          @click="openRouteDialog"
        >
          编辑顺序
        </el-button>
      </div>
      <div v-if="routeDeploymentRows.length" class="route-chain">
        <template v-for="(row, index) in routeDeploymentRows" :key="row.id">
          <div class="route-node">
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ accountFor(row)?.displayName || '--' }}</strong>
              <small>{{ row.modelName }}</small>
            </div>
          </div>
          <el-icon v-if="index < routeDeploymentRows.length - 1" class="route-arrow"><Right /></el-icon>
        </template>
      </div>
      <el-empty v-else description="当前能力尚未建立执行路由" :image-size="58" />
    </div>

    <div v-if="emptyAccounts.length" class="orphan-band">
      <span>未绑定能力的账户</span>
      <div v-for="account in emptyAccounts" :key="account.id" class="orphan-item">
        <span>{{ account.displayName }}</span>
        <el-button link type="danger" @click="removeAccount(account)">删除账户</el-button>
      </div>
    </div>
    </template>

    <template v-else>
      <div class="offering-toolbar">
        <div class="offering-rollout-state">
          <el-tag :type="chatModelSettings.enabled ? 'success' : 'info'" effect="plain">
            {{ chatModelSettings.enabled ? `已开放 ${chatModelSettings.canaryPercent}%` : '用户端未开放' }}
          </el-tag>
          <span>先配置并验证模型，再逐步提高灰度；关闭后旧聊天链路继续生效。</span>
        </div>
        <div class="offering-toolbar-actions">
          <el-button :icon="Setting" @click="openChatModelSettings">开放策略</el-button>
          <el-button type="primary" :icon="Plus" @click="openOfferingDrawer()">新增用户模型</el-button>
        </div>
      </div>

      <div class="provider-table-wrap offering-table-wrap">
        <el-table v-loading="loading" :data="chatOfferings" row-key="id">
          <el-table-column label="用户看到的模型" min-width="240">
            <template #default="{ row }">
              <div class="offering-name-cell">
                <div>
                  <strong>{{ row.displayName }}</strong>
                  <el-tag v-if="row.badge" size="small" effect="plain">{{ row.badge }}</el-tag>
                  <el-tag v-if="row.defaultOffering" size="small" type="success" effect="plain">默认</el-tag>
                </div>
                <span>{{ row.shortDescription || '尚未填写用户说明' }}</span>
                <small>{{ row.offeringCode }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="体验标签" min-width="180">
            <template #default="{ row }">
              <div class="tag-line">
                <el-tag v-for="tag in row.tags" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
              </div>
              <small>速度 {{ row.speedLevel }}/5 · 质量 {{ row.qualityLevel }}/5</small>
            </template>
          </el-table-column>
          <el-table-column label="用户价格" min-width="190">
            <template #default="{ row }">
              <strong>{{ offeringPriceSummary(row) }}</strong>
              <small v-if="row.prices?.length > 1">含 {{ row.prices.length - 1 }} 条会员价格</small>
            </template>
          </el-table-column>
          <el-table-column label="绑定路由" min-width="190">
            <template #default="{ row }">
              <span class="mono-cell">{{ row.routeKey }}</span>
              <small :class="row.routeReady ? 'route-ready' : 'route-missing'">
                {{ row.routeReady ? `${row.routeMemberCount} 个供应商节点` : '路由未就绪' }}
              </small>
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="130">
            <template #default="{ row }">
              <el-tag :type="row.maintenance ? 'warning' : (row.enabled ? 'success' : 'info')" effect="plain">
                {{ row.maintenance ? '维护中' : (row.enabled ? '已发布' : '草稿') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOfferingDrawer(row)">编辑</el-button>
              <el-button link type="danger" :disabled="row.enabled" @click="removeOffering(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="还没有用户可选模型。先创建专属 CHAT 路由，再发布模型。" :image-size="72" />
          </template>
        </el-table>
      </div>
    </template>

    <el-dialog
      v-model="providerDialogVisible"
      :title="providerForm.deploymentId ? '编辑供应商能力' : '新增供应商能力'"
      width="820px"
      destroy-on-close
    >
      <el-form label-position="top" class="provider-form">
        <el-alert
          v-if="sharedAccountDeployments.length > 1"
          class="shared-account-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`共享账户：同时用于 ${sharedAccountCapabilityText}`"
          description="修改 API 地址、Key、账户启用状态或超时会影响这些能力；只修改下方模型配置不会影响其他能力。"
        />
        <div class="form-section-title">账户连接配置</div>
        <div class="form-grid two-col">
          <el-form-item v-if="!providerForm.deploymentId" label="使用已有账户">
            <el-select v-model="accountChoice" clearable placeholder="新建供应商账户" @change="applyAccountChoice">
              <el-option
                v-for="account in accounts"
                :key="account.id"
                :label="`${account.displayName} (${account.providerKey})`"
                :value="account.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="能力">
            <el-select v-model="providerForm.capability" :disabled="!!providerForm.deploymentId" @change="clearModels">
              <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="供应商类型">
            <el-select v-model="providerForm.vendor" :disabled="!!providerForm.accountId" @change="applyVendorDefaults">
              <el-option
                v-for="item in availableVendors"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="providerForm.displayName" maxlength="128" />
          </el-form-item>
          <el-form-item label="Provider Key">
            <el-input v-model="providerForm.providerKey" :disabled="!!providerForm.accountId" maxlength="64" />
          </el-form-item>
          <el-form-item class="span-two" label="API 基础地址">
            <el-input v-model="providerForm.baseUrl" :disabled="!selectedVendor?.customBaseUrl && !!selectedVendor?.defaultBaseUrl" />
            <div class="normalized-url">实际使用：{{ normalizedBaseUrl || '--' }}</div>
          </el-form-item>
          <el-form-item class="span-two" label="API Key">
            <div class="key-row">
              <el-radio-group v-model="providerForm.apiKeyAction" @change="handleKeyAction">
                <el-radio-button v-if="providerForm.accountId" value="preserve">保留</el-radio-button>
                <el-radio-button value="replace">替换</el-radio-button>
                <el-radio-button v-if="providerForm.accountId" value="clear">清除并停用</el-radio-button>
              </el-radio-group>
              <el-input
                v-if="providerForm.apiKeyAction === 'replace'"
                v-model="providerForm.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="输入新的 API Key"
              />
              <span v-else class="key-mask">{{ selectedAccount?.apiKeyMask || '未配置' }}</span>
            </div>
          </el-form-item>
        </div>

        <div class="form-section-title">当前能力模型配置</div>
        <div class="model-picker">
          <div class="model-picker-head">
            <span>上游模型</span>
            <div>
              <el-checkbox v-model="matchedOnly" :disabled="modelOptions.length === 0">只看能力匹配</el-checkbox>
              <el-button
                v-hasPermi="['ops:openrouter:edit']"
                :icon="Download"
                :loading="discovering"
                :disabled="providerForm.apiKeyAction === 'clear'"
                @click="discoverModels"
              >
                获取模型
              </el-button>
            </div>
          </div>
          <el-select
            v-model="providerForm.modelName"
            filterable
            allow-create
            default-first-option
            :filter-method="filterModelOptions"
            :no-data-text="modelNoDataText"
            placeholder="获取模型或直接填写模型 ID"
            @visible-change="handleModelDropdownVisible"
          >
            <el-option
              v-for="model in filteredModels"
              :key="model.id"
              :label="model.label || model.id"
              :value="model.id"
            >
              <div class="model-option">
                <span>{{ model.id }}</span>
                <el-tag v-if="model.capabilityMatch" size="small" type="success" effect="plain">匹配</el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="modelSummaryText" class="model-summary">{{ modelSummaryText }}</div>
        </div>

        <div class="form-grid three-col compact-grid">
          <el-form-item v-if="providerForm.capability === 'TTS'" label="音色">
            <el-input v-model="providerForm.voiceName" placeholder="alloy" />
          </el-form-item>
          <el-form-item label="失败阈值">
            <el-input-number v-model="providerForm.failureThreshold" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="熔断秒数">
            <el-input-number v-model="providerForm.cooldownSeconds" :min="30" :max="3600" />
          </el-form-item>
          <el-form-item label="请求超时">
            <el-input-number v-model="providerForm.requestTimeoutSeconds" :min="5" :max="600" />
          </el-form-item>
          <el-form-item label="账户启用">
            <el-switch v-model="providerForm.accountEnabled" />
          </el-form-item>
          <el-form-item label="模型启用">
            <el-switch v-model="providerForm.deploymentEnabled" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="providerForm.note" maxlength="255" />
          </el-form-item>
        </div>
      </el-form>
      <el-alert
        v-if="probeMayIncurCost"
        class="probe-cost-alert"
        type="warning"
        :closable="false"
        show-icon
        title="本次测试会真实调用上游接口，可能产生费用"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button
            v-hasPermi="['ops:openrouter:edit']"
            type="success"
            plain
            :icon="Connection"
            :loading="probing"
            :disabled="providerForm.apiKeyAction === 'clear'"
            @click="probeDraft"
          >
            测试 {{ capabilityName(providerForm.capability) }}
          </el-button>
          <div>
            <el-button @click="providerDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingProvider" @click="submitProvider">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="routeDialogVisible" :title="`${capabilityLabel}执行顺序`" width="680px">
      <div class="route-editor">
        <draggable v-model="routeDraft" item-key="id" handle=".drag-handle" :animation="180">
          <template #item="{ element, index }">
            <div class="route-editor-item">
              <el-icon class="drag-handle"><Rank /></el-icon>
              <span class="order-number">{{ index + 1 }}</span>
              <div>
                <strong>{{ accountFor(element)?.displayName || '--' }}</strong>
                <small>{{ element.modelName }}</small>
              </div>
              <el-button :icon="Close" circle text title="移除" @click="removeRouteDraft(index)" />
            </div>
          </template>
        </draggable>
        <el-select v-model="routeCandidateId" placeholder="添加供应商模型" @change="addRouteCandidate">
          <el-option
            v-for="item in routeCandidates"
            :key="item.id"
            :label="`${accountFor(item)?.displayName || '--'} · ${item.modelName}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button
          :type="routeDraft.length === 0 ? 'danger' : 'primary'"
          :loading="savingRoute"
          @click="submitRoute"
        >
          {{ routeDraft.length === 0 ? '删除执行路由' : '保存执行顺序' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deploymentDeleteDialogVisible" title="迁移路由引用并删除模型" width="620px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该模型仍被执行路由引用"
        description="选择替换模型后会在同一事务中更新全部引用路由；不选择时，只能从仍有其他节点的路由中解除引用。"
      />
      <div class="deployment-reference-list">
        <div v-for="route in deploymentDeleteReferences" :key="route.id" class="deployment-reference-item">
          <div>
            <strong>{{ route.displayName }}</strong>
            <small>{{ route.routeKey }}</small>
          </div>
          <el-tag effect="plain">{{ route.deploymentIds?.length || 0 }} 个节点</el-tag>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item label="替换为同能力模型">
          <el-select v-model="deploymentReplacementId" clearable placeholder="可选；唯一节点路由必须选择">
            <el-option
              v-for="item in deploymentReplacementCandidates"
              :key="item.id"
              :label="`${accountFor(item)?.displayName || '--'} · ${item.modelName}`"
              :value="item.id"
            />
          </el-select>
          <small v-if="deploymentDeleteRequiresReplacement" class="delete-migration-hint">
            至少一条有效路由仅剩当前节点，必须选择替换模型。
          </small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deploymentDeleteDialogVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="deletingDeployment"
          :disabled="deploymentDeleteRequiresReplacement && !deploymentReplacementId"
          @click="submitDeploymentMigration"
        >
          {{ deploymentReplacementId ? '迁移并删除' : '解除引用并删除' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runtimeDialogVisible" title="AI 路由运行开关" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="开关保存后会在约 2 秒内影响新请求"
        description="保持总开关关闭和聊天灰度 0% 时，现有聊天继续使用旧路由。BYOK 失败后始终禁止转用官方 Key。"
      />
      <el-form label-position="left" label-width="150px" class="runtime-form">
        <el-form-item label="新路由总开关">
          <el-switch v-model="runtimeDraft.enabled" />
        </el-form-item>
        <el-form-item label="影子对比">
          <el-switch v-model="runtimeDraft.shadowEnabled" />
        </el-form-item>
        <el-form-item label="聊天灰度">
          <el-slider v-model="runtimeDraft.chatCanaryPercent" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="官方识图路由">
          <el-switch v-model="runtimeDraft.visionEnabled" />
        </el-form-item>
        <el-form-item label="官方生图路由">
          <el-switch v-model="runtimeDraft.imageEnabled" />
        </el-form-item>
        <el-form-item label="官方 TTS 路由">
          <el-switch v-model="runtimeDraft.ttsEnabled" />
        </el-form-item>
        <el-form-item label="官方 STT 路由">
          <el-switch v-model="runtimeDraft.sttEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="savingRuntime" @click="resetRuntimeSettings">恢复环境默认值</el-button>
        <el-button @click="runtimeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRuntime" @click="submitRuntimeSettings">确认并保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="offeringDrawerVisible"
      :title="offeringForm.id ? '编辑用户可选模型' : '新增用户可选模型'"
      size="min(860px, 96vw)"
      destroy-on-close
    >
      <el-form label-position="top" class="offering-form">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="用户只会看到公开名称、说明和价格"
          description="真实供应商、API Key 和上游模型由绑定路由管理；同一方案内可在首 Token 前 fallback。"
        />

        <div class="offering-section-head">
          <div>
            <strong>展示信息</strong>
            <span>这些内容会出现在用户端模型选择器中</span>
          </div>
          <el-button :icon="Connection" @click="smartFillOffering">根据路由智能填写</el-button>
        </div>
        <div class="form-grid two-col">
          <el-form-item label="公开编码">
            <el-input v-model="offeringForm.offeringCode" :disabled="!!offeringForm.id" placeholder="例如 immersive_chat" maxlength="64" />
          </el-form-item>
          <el-form-item label="用户看到的名称">
            <el-input v-model="offeringForm.displayName" placeholder="例如 沉浸创作" maxlength="128" />
          </el-form-item>
          <el-form-item label="短说明" class="span-two">
            <el-input v-model="offeringForm.shortDescription" placeholder="一句话说明模型特点与适用场景" maxlength="255" show-word-limit />
          </el-form-item>
          <el-form-item label="完整说明" class="span-two">
            <el-input v-model="offeringForm.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="体验标签">
            <el-input v-model="offeringTagsText" placeholder="沉浸、长文本、创作" />
          </el-form-item>
          <el-form-item label="角标文案">
            <el-input v-model="offeringForm.badge" placeholder="推荐 / 高质量 / 免费" maxlength="64" />
          </el-form-item>
          <el-form-item label="上下文说明">
            <el-input v-model="offeringForm.contextLabel" placeholder="例如 长上下文" maxlength="64" />
          </el-form-item>
          <el-form-item label="最低会员等级">
            <el-input-number v-model="offeringForm.vipMinLevel" :min="0" :max="99" />
          </el-form-item>
          <el-form-item label="速度等级">
            <el-slider v-model="offeringForm.speedLevel" :min="1" :max="5" show-stops />
          </el-form-item>
          <el-form-item label="质量等级">
            <el-slider v-model="offeringForm.qualityLevel" :min="1" :max="5" show-stops />
          </el-form-item>
        </div>

        <div class="offering-section-head">
          <div>
            <strong>模型路由</strong>
            <span>只允许绑定 CHAT 路由；fallback 顺序在“供应商与兜底”中维护</span>
          </div>
        </div>
        <el-form-item label="绑定 CHAT 路由">
          <el-select v-model="offeringForm.routeKey" filterable allow-create placeholder="选择路由或填写 chat.offer.模型编码" @change="syncOfferingRouteMembers">
            <el-option
              v-for="route in chatRoutes"
              :key="route.routeKey"
              :label="`${route.displayName} (${route.routeKey})`"
              :value="route.routeKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商 fallback 顺序">
          <el-select v-model="offeringForm.routeDeploymentIds" multiple filterable placeholder="按希望的执行顺序选择 CHAT 供应商模型">
            <el-option
              v-for="deployment in chatDeployments"
              :key="deployment.id"
              :label="`${accountFor(deployment)?.displayName || '--'} · ${deployment.modelName}`"
              :value="deployment.id"
            />
          </el-select>
          <div class="normalized-url">选择顺序就是首 Token 前的 fallback 顺序；保存方案时会同步保存这条专属路由。</div>
          <div v-if="offeringForm.routeDeploymentIds.length" class="offering-fallback-order">
            <div v-for="(deploymentId, index) in offeringForm.routeDeploymentIds" :key="deploymentId" class="offering-fallback-row">
              <b>{{ index + 1 }}</b>
              <div>
                <strong>{{ accountFor(deployments.find((item) => item.id === deploymentId))?.displayName || '--' }}</strong>
                <span>{{ deployments.find((item) => item.id === deploymentId)?.modelName || '模型已删除' }}</span>
              </div>
              <el-button :icon="SortUp" circle text title="上移" :disabled="index === 0" @click="moveOfferingDeployment(index, -1)" />
              <el-button :icon="SortDown" circle text title="下移" :disabled="index === offeringForm.routeDeploymentIds.length - 1" @click="moveOfferingDeployment(index, 1)" />
            </div>
          </div>
        </el-form-item>

        <div class="offering-section-head">
          <div>
            <strong>计费与会员价格</strong>
            <span>普通用户价格必填；系统会自动生成用户端价格说明</span>
          </div>
          <el-button :icon="Plus" @click="addOfferingPrice">增加会员价格</el-button>
        </div>
        <div class="price-rule-list">
          <div v-for="(price, index) in offeringForm.prices" :key="`${price.vipLevel}-${index}`" class="price-rule-row">
            <el-form-item label="会员等级">
              <el-input-number v-model="price.vipLevel" :min="0" :max="99" :disabled="index === 0" />
            </el-form-item>
            <el-form-item label="计费方式">
              <el-select v-model="price.billingMode" @change="normalizeOfferingPrice(price)">
                <el-option v-for="mode in billingModes" :key="mode.value" :label="mode.label" :value="mode.value">
                  <div class="billing-mode-option">
                    <strong>{{ mode.label }}</strong>
                    <span>{{ mode.description }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item v-if="priceNeedsQuota(price)" label="每次消耗次数">
              <el-input-number v-model="price.quotaUnits" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item v-if="priceNeedsDiamonds(price)" label="钻石">
              <el-input-number v-model="price.diamondCost" :min="1" :max="1000000" />
            </el-form-item>
            <el-form-item v-if="priceNeedsGold(price)" label="金币">
              <el-input-number v-model="price.goldCost" :min="1" :max="1000000" />
            </el-form-item>
            <el-button v-if="index > 0" :icon="Close" circle text title="删除会员价格" @click="removeOfferingPrice(index)" />
            <div class="price-rule-preview">用户看到：{{ priceText(price) }}</div>
          </div>
        </div>

        <div class="offering-section-head">
          <div>
            <strong>发布控制</strong>
            <span>发布前会校验路由和价格；维护状态会阻止新请求</span>
          </div>
        </div>
        <div class="form-grid three-col offering-switches">
          <el-form-item label="推荐展示"><el-switch v-model="offeringForm.recommended" /></el-form-item>
          <el-form-item label="设为默认"><el-switch v-model="offeringForm.defaultOffering" /></el-form-item>
          <el-form-item label="维护状态"><el-switch v-model="offeringForm.maintenance" /></el-form-item>
          <el-form-item label="发布给用户"><el-switch v-model="offeringForm.enabled" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="offeringForm.sortOrder" :min="0" :max="100000" /></el-form-item>
        </div>

        <div class="offering-preview">
          <span>用户端预览</span>
          <div>
            <strong>{{ offeringForm.displayName || '模型名称' }}</strong>
            <el-tag v-if="offeringForm.badge" size="small" effect="plain">{{ offeringForm.badge }}</el-tag>
            <b>{{ offeringDraftPriceText }}</b>
          </div>
          <p>{{ offeringForm.shortDescription || '这里会显示模型的简短说明。' }}</p>
        </div>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="offeringDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingOffering" @click="submitOffering">保存模型方案</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="chatModelSettingsVisible" title="用户聊天模型开放策略" width="600px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="关闭时不会接管现有聊天"
        description="建议先用少量灰度验证真实发送、继续和重新生成，再逐步全量。"
      />
      <el-form label-position="top" class="runtime-form">
        <el-form-item label="开放用户模型选择">
          <el-switch v-model="chatModelSettingsDraft.enabled" />
        </el-form-item>
        <el-form-item label="用户灰度比例">
          <el-slider v-model="chatModelSettingsDraft.canaryPercent" :min="0" :max="100" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chatModelSettingsVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingChatModelSettings" @click="submitChatModelSettings">保存开放策略</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import draggable from 'vuedraggable'
import {
  ChatDotRound,
  Close,
  Connection,
  Download,
  Headset,
  Microphone,
  Picture,
  Plus,
  Rank,
  Refresh,
  Right,
  Setting,
  Sort,
  SortDown,
  SortUp,
  Upload,
  View
} from '@element-plus/icons-vue'
import {
  deleteAiAccount,
  deleteAiChatOffering,
  deleteAiDeployment,
  deleteAiRoute,
  discoverAiModels,
  getAiRouting,
  importLegacyAiChatRoute,
  migrateDeleteAiDeployment,
  probeAiCapability,
  resetAiRoutingRuntimeSettings,
  saveAiProvider,
  saveAiChatModelSettings,
  saveAiChatOfferingBundle,
  saveAiRoutingRuntimeSettings,
  saveAiRoute
} from '@/api/jiugai/openrouterGeneration'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'
import { filterDiscoveredModels } from './modelFilter'

const { proxy } = getCurrentInstance()
const pageRoute = useRoute()

const capabilityOptions = [
  { value: 'CHAT', label: '文本聊天', icon: ChatDotRound },
  { value: 'VISION', label: '视觉理解', icon: View },
  { value: 'IMAGE', label: '生图', icon: Picture },
  { value: 'TTS', label: '语音合成', icon: Headset },
  { value: 'STT', label: '语音识别', icon: Microphone }
]

function capabilityFromQuery(value) {
  const normalized = String(value || '').trim().toUpperCase()
  return capabilityOptions.some((item) => item.value === normalized) ? normalized : 'CHAT'
}

const activeCapability = ref(capabilityFromQuery(pageRoute.query.capability))
const loading = ref(false)
const accounts = ref([])
const deployments = ref([])
const routes = ref([])
const catalog = ref([])
const flags = reactive({})
const providerDialogVisible = ref(false)
const routeDialogVisible = ref(false)
const runtimeDialogVisible = ref(false)
const savingProvider = ref(false)
const savingRoute = ref(false)
const discovering = ref(false)
const probing = ref(false)
const importingLegacy = ref(false)
const deletingDeployment = ref(false)
const savingRuntime = ref(false)
const accountChoice = ref(null)
const modelOptions = ref([])
const matchedOnly = ref(true)
const modelSearch = ref('')
const modelSummary = ref('')
const routeDraft = ref([])
const routeCandidateId = ref(null)
const deploymentDeleteDialogVisible = ref(false)
const deploymentDeleteTarget = ref(null)
const deploymentReplacementId = ref(null)
const chatWorkspace = ref('offerings')
const chatOfferings = ref([])
const billingModes = ref([])
const offeringDrawerVisible = ref(false)
const chatModelSettingsVisible = ref(false)
const savingOffering = ref(false)
const savingChatModelSettings = ref(false)
const chatModelSettings = reactive({ enabled: false, canaryPercent: 0 })
const chatModelSettingsDraft = reactive({ enabled: false, canaryPercent: 0 })
const offeringForm = reactive(defaultOfferingForm())
const providerForm = reactive(defaultProviderForm())
const runtimeDraft = reactive({
  enabled: false,
  shadowEnabled: true,
  chatCanaryPercent: 0,
  visionEnabled: false,
  imageEnabled: false,
  ttsEnabled: false,
  sttEnabled: false
})

const capabilityLabel = computed(() => capabilityName(activeCapability.value))
const chatRoutes = computed(() => routes.value.filter((item) => item.capability === 'CHAT'))
const chatDeployments = computed(() => deployments.value.filter((item) => item.capability === 'CHAT' && item.enabled !== false))
const offeringTagsText = computed({
  get: () => (Array.isArray(offeringForm.tags) ? offeringForm.tags : []).join('、'),
  set: (value) => {
    offeringForm.tags = String(value || '').split(/[|,，、]/).map((item) => item.trim()).filter(Boolean).slice(0, 8)
  }
})
const offeringDraftPriceText = computed(() => priceText(offeringForm.prices?.[0]))
const capabilityDeployments = computed(() => deployments.value.filter((item) => item.capability === activeCapability.value))
const capabilityRoute = computed(() => routes.value.find((item) => item.capability === activeCapability.value && item.routeKey === defaultRouteKey(activeCapability.value)))
const routeDeploymentRows = computed(() => {
  const ids = capabilityRoute.value?.deploymentIds || []
  return ids.map((id) => deployments.value.find((item) => item.id === id)).filter(Boolean)
})
const emptyAccounts = computed(() => accounts.value.filter((account) => !deployments.value.some((item) => item.accountId === account.id)))
const selectedAccount = computed(() => accounts.value.find((item) => item.id === providerForm.accountId))
const selectedVendor = computed(() => catalog.value.find((item) => item.value === providerForm.vendor))
const availableVendors = computed(() => catalog.value.filter((item) => item.capabilities?.includes(providerForm.capability)))
const normalizedBaseUrl = computed(() => normalizeBaseUrl(providerForm.baseUrl || selectedVendor.value?.defaultBaseUrl || ''))
const filteredModels = computed(() => filterDiscoveredModels(modelOptions.value, {
  matchedOnly: matchedOnly.value,
  query: modelSearch.value
}))
const modelSummaryText = computed(() => {
  if (!modelSummary.value) return ''
  const suffix = modelSearch.value.trim()
    ? `，搜索结果 ${filteredModels.value.length} 个`
    : `，当前显示 ${filteredModels.value.length} 个`
  return `${modelSummary.value}${suffix}`
})
const modelNoDataText = computed(() => {
  if (!modelOptions.value.length) return '请先获取模型，或直接填写完整模型 ID'
  if (matchedOnly.value && !modelOptions.value.some((item) => item.capabilityMatch === true)) {
    return '未识别到当前能力模型，请取消“只看能力匹配”后搜索或手填模型 ID'
  }
  if (modelSearch.value.trim()) return '没有搜索到模型，可直接填写完整模型 ID'
  return '没有符合当前筛选条件的模型'
})
const routeCandidates = computed(() => capabilityDeployments.value.filter((item) => !routeDraft.value.some((selected) => selected.id === item.id)))
const probeMayIncurCost = computed(() => ['IMAGE', 'TTS', 'STT'].includes(providerForm.capability))
const deploymentDeleteReferences = computed(() => {
  const id = deploymentDeleteTarget.value?.id
  return id == null ? [] : routes.value.filter((route) => route.deploymentIds?.includes(id))
})
const deploymentReplacementCandidates = computed(() => {
  const target = deploymentDeleteTarget.value
  if (!target) return []
  return deployments.value.filter((item) => item.id !== target.id
    && item.capability === target.capability
    && item.enabled !== false
    && accountFor(item)?.enabled !== false)
})
const deploymentDeleteRequiresReplacement = computed(() => deploymentDeleteReferences.value.some((route) => {
  const remaining = (route.deploymentIds || []).filter((id) => id !== deploymentDeleteTarget.value?.id)
  const orphanDedicatedRoute = String(route.routeKey || '').startsWith('chat.offer.')
    && !chatOfferings.value.some((offering) => offering.routeKey === route.routeKey)
  return remaining.length === 0 && !orphanDedicatedRoute
}))
const sharedAccountDeployments = computed(() => deployments.value.filter((item) => item.accountId === providerForm.accountId))
const sharedAccountCapabilityText = computed(() => [...new Set(sharedAccountDeployments.value.map((item) => capabilityName(item.capability)))].join('、'))
const accountConnectionFieldsChanged = computed(() => {
  const account = selectedAccount.value
  if (!account) return false
  return providerForm.vendor !== account.vendor
    || normalizedBaseUrl.value !== normalizeBaseUrl(account.baseUrl)
    || providerForm.apiKeyAction !== 'preserve'
    || providerForm.accountEnabled !== (account.enabled !== false)
    || Number(providerForm.connectTimeoutSeconds) !== Number(account.connectTimeoutSeconds || 10)
    || Number(providerForm.requestTimeoutSeconds) !== Number(account.requestTimeoutSeconds || 90)
})
const capabilityRuntimeText = computed(() => {
  if (!flags.enabled) return '旧路由'
  if (activeCapability.value === 'CHAT') return Number(flags.chatCanaryPercent || 0) > 0 ? `灰度 ${flags.chatCanaryPercent}%` : '未灰度'
  const key = `${activeCapability.value.toLowerCase()}Enabled`
  return flags[key] ? '新路由' : '旧链路'
})

watch(activeCapability, () => {
  modelOptions.value = []
  modelSummary.value = ''
})

watch(() => pageRoute.query.capability, (value) => {
  activeCapability.value = capabilityFromQuery(value)
})

function defaultOfferingForm() {
  return {
    id: null,
    versionNo: 0,
    offeringCode: '',
    displayName: '',
    shortDescription: '',
    description: '',
    tags: [],
    badge: '',
    contextLabel: '',
    speedLevel: 3,
    qualityLevel: 3,
    routeKey: '',
    routeDeploymentIds: [],
    vipMinLevel: 0,
    recommended: false,
    defaultOffering: false,
    sortOrder: 100,
    enabled: false,
    maintenance: false,
    prices: [{ vipLevel: 0, billingMode: 'QUOTA_ONLY', quotaUnits: 1, diamondCost: 0, goldCost: 0 }]
  }
}

function defaultProviderForm() {
  return {
    accountId: null,
    accountVersion: null,
    deploymentId: null,
    providerKey: '',
    displayName: '',
    vendor: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    apiKeyAction: 'replace',
    capability: activeCapability.value || 'CHAT',
    protocolType: '',
    modelName: '',
    voiceName: '',
    accountEnabled: true,
    deploymentEnabled: true,
    connectTimeoutSeconds: 10,
    requestTimeoutSeconds: 90,
    failureThreshold: 3,
    cooldownSeconds: 180,
    note: ''
  }
}

function load() {
  loading.value = true
  return getAiRouting()
    .then((res) => {
      const data = res?.data || {}
      accounts.value = data.accounts || []
      deployments.value = data.deployments || []
      routes.value = data.routes || []
      catalog.value = data.catalog || []
      Object.assign(flags, data.flags || {})
      const chatCatalog = data.chatModelCatalog || {}
      chatOfferings.value = chatCatalog.offerings || []
      billingModes.value = chatCatalog.billingModes || []
      Object.assign(chatModelSettings, chatCatalog.settings || {})
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载统一模型路由失败')))
    .finally(() => { loading.value = false })
}

function openOfferingDrawer(row) {
  const draft = defaultOfferingForm()
  if (row) {
    Object.assign(draft, JSON.parse(JSON.stringify(row)))
    draft.prices = Array.isArray(row.prices) && row.prices.length
      ? JSON.parse(JSON.stringify(row.prices))
      : defaultOfferingForm().prices
    const route = routes.value.find((item) => item.routeKey === row.routeKey)
    draft.routeDeploymentIds = route?.deploymentIds ? [...route.deploymentIds] : []
  }
  Object.assign(offeringForm, draft)
  if (!offeringForm.routeKey && offeringForm.offeringCode) {
    offeringForm.routeKey = `chat.offer.${offeringForm.offeringCode}`
  }
  offeringDrawerVisible.value = true
}

function syncOfferingRouteMembers(routeKey) {
  const route = routes.value.find((item) => item.routeKey === routeKey)
  offeringForm.routeDeploymentIds = route?.deploymentIds ? [...route.deploymentIds] : []
}

function moveOfferingDeployment(index, offset) {
  const target = index + offset
  if (index < 0 || target < 0 || target >= offeringForm.routeDeploymentIds.length) return
  const next = [...offeringForm.routeDeploymentIds]
  const current = next[index]
  next[index] = next[target]
  next[target] = current
  offeringForm.routeDeploymentIds = next
}

function smartFillOffering() {
  const route = routes.value.find((item) => item.routeKey === offeringForm.routeKey)
  const deploymentId = offeringForm.routeDeploymentIds?.[0] || route?.deploymentIds?.[0]
  const deployment = deployments.value.find((item) => item.id === deploymentId)
  if (!deployment) {
    proxy.$modal.msgWarning('请先选择路由或至少一个 CHAT 供应商模型')
    return
  }
  const model = String(deployment.modelName || '').toLowerCase()
  let preset = {
    name: '精选对话', desc: '兼顾回复质量和速度，适合日常角色扮演与连续剧情。',
    tags: ['均衡', '角色扮演', '日常对话'], speed: 3, quality: 3
  }
  if (/claude/.test(model)) preset = { name: '沉浸创作', desc: '更擅长细腻表达、长篇剧情和角色一致性。', tags: ['沉浸', '长文本', '创作'], speed: 3, quality: 5 }
  else if (/deepseek|reason|r1/.test(model)) preset = { name: '深度推理', desc: '适合复杂设定、逻辑分析和需要充分思考的剧情。', tags: ['推理', '逻辑', '复杂设定'], speed: 2, quality: 5 }
  else if (/grok/.test(model)) preset = { name: '自然畅聊', desc: '回复自然直接，适合节奏轻快的日常互动。', tags: ['自然', '畅聊', '快速'], speed: 4, quality: 4 }
  else if (/gpt|openai/.test(model)) preset = { name: '全能对话', desc: '理解稳定、风格均衡，适合大多数角色扮演场景。', tags: ['全能', '稳定', '均衡'], speed: 4, quality: 4 }
  else if (/gemini/.test(model)) preset = { name: '长篇理解', desc: '适合长上下文、复杂人物关系和连续剧情。', tags: ['长上下文', '理解', '剧情'], speed: 4, quality: 4 }
  if (!offeringForm.displayName) offeringForm.displayName = preset.name
  if (!offeringForm.shortDescription) offeringForm.shortDescription = preset.desc
  if (!offeringForm.description) offeringForm.description = `${preset.desc} 实际体验取决于当前角色卡、预设、世界书和会话上下文。`
  if (!offeringForm.tags?.length) offeringForm.tags = preset.tags
  if (!offeringForm.contextLabel && preset.tags.includes('长上下文')) offeringForm.contextLabel = '长上下文'
  offeringForm.speedLevel = preset.speed
  offeringForm.qualityLevel = preset.quality
  if (!offeringForm.offeringCode) {
    offeringForm.offeringCode = `model_${deployment.id}`
    offeringForm.routeKey = `chat.offer.${offeringForm.offeringCode}`
  }
}

function addOfferingPrice() {
  const levels = offeringForm.prices.map((item) => Number(item.vipLevel || 0))
  let nextLevel = 1
  while (levels.includes(nextLevel)) nextLevel += 1
  offeringForm.prices.push({ vipLevel: nextLevel, billingMode: 'QUOTA_THEN_DIAMOND', quotaUnits: 1, diamondCost: 1, goldCost: 0 })
}

function removeOfferingPrice(index) {
  offeringForm.prices.splice(index, 1)
}

function priceNeedsQuota(price) {
  return String(price?.billingMode || '').includes('QUOTA')
}

function priceNeedsDiamonds(price) {
  const mode = String(price?.billingMode || '')
  return mode.includes('DIAMOND') || mode === 'QUOTA_THEN_MIXED'
}

function priceNeedsGold(price) {
  const mode = String(price?.billingMode || '')
  return mode.includes('GOLD') || mode === 'QUOTA_THEN_MIXED'
}

function normalizeOfferingPrice(price) {
  if (!priceNeedsQuota(price)) price.quotaUnits = 0
  else if (!Number(price.quotaUnits)) price.quotaUnits = 1
  if (!priceNeedsDiamonds(price)) price.diamondCost = 0
  else if (!Number(price.diamondCost)) price.diamondCost = 1
  if (!priceNeedsGold(price)) price.goldCost = 0
  else if (!Number(price.goldCost)) price.goldCost = 1
}

function priceText(price) {
  if (!price) return '--'
  const mode = String(price.billingMode || '')
  const q = Number(price.quotaUnits || 0)
  const d = Number(price.diamondCost || 0)
  const g = Number(price.goldCost || 0)
  const labels = {
    FREE: '免费', QUOTA_ONLY: `${q}次`, DIAMOND_ONLY: `${d}钻石/次`, GOLD_ONLY: `${g}金币/次`,
    QUOTA_THEN_DIAMOND: `${q}次，用完后${d}钻石`, QUOTA_THEN_GOLD: `${q}次，用完后${g}金币`,
    DIAMOND_AND_GOLD: `${d}钻石 + ${g}金币/次`, QUOTA_THEN_MIXED: `${q}次，用完后${d}钻石 + ${g}金币`,
    DIAMOND_OR_GOLD: `${d}钻石 或 ${g}金币/次`,
    QUOTA_THEN_DIAMOND_OR_GOLD: `${q}次，用完后${d}钻石 或 ${g}金币`
  }
  return labels[mode] || '--'
}

function offeringPriceSummary(row) {
  const base = (row?.prices || []).find((item) => Number(item.vipLevel || 0) === 0) || row?.prices?.[0]
  return priceText(base)
}

function submitOffering() {
  if (!offeringForm.offeringCode || !offeringForm.displayName) {
    proxy.$modal.msgWarning('请填写公开编码和用户看到的名称')
    return
  }
  if (!offeringForm.routeKey) offeringForm.routeKey = `chat.offer.${offeringForm.offeringCode}`
  if (!offeringForm.routeDeploymentIds?.length) {
    proxy.$modal.msgWarning('请至少选择一个 CHAT 供应商模型')
    return
  }
  const vipLevels = offeringForm.prices.map((item) => Number(item.vipLevel || 0))
  if (!offeringForm.prices.length || !vipLevels.includes(0) || new Set(vipLevels).size !== vipLevels.length) {
    proxy.$modal.msgWarning('普通用户价格必填，且会员等级不能重复')
    return
  }
  offeringForm.prices.forEach((price) => normalizeOfferingPrice(price))
  const existingRoute = routes.value.find((item) => item.routeKey === offeringForm.routeKey)
  savingOffering.value = true
  const routePayload = {
    id: existingRoute?.id || null,
    routeKey: offeringForm.routeKey,
    displayName: `${offeringForm.displayName}路由`,
    capability: 'CHAT',
    deploymentIds: [...offeringForm.routeDeploymentIds],
    enabled: true,
    note: `用户可选模型 ${offeringForm.offeringCode} 专属路由`
  }
  const offeringPayload = {
      ...offeringForm,
      tags: [...offeringForm.tags],
      prices: offeringForm.prices.map((item) => ({ ...item }))
  }
  saveAiChatOfferingBundle({ route: routePayload, offering: offeringPayload })
    .then(() => {
      proxy.$modal.msgSuccess('用户可选模型已保存')
      offeringDrawerVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存用户可选模型失败')))
    .finally(() => { savingOffering.value = false })
}

function removeOffering(row) {
  proxy.$modal.confirm(`确认删除用户模型“${row.displayName}”吗？无人使用的专属执行路由会同时回收。`)
    .then(() => deleteAiChatOffering(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('用户模型及其无人使用的专属路由已删除')
      return load()
    })
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除用户模型失败'))
    })
}

function openChatModelSettings() {
  Object.assign(chatModelSettingsDraft, chatModelSettings)
  chatModelSettingsVisible.value = true
}

function submitChatModelSettings() {
  savingChatModelSettings.value = true
  saveAiChatModelSettings({ ...chatModelSettingsDraft })
    .then(() => {
      proxy.$modal.msgSuccess('用户聊天模型开放策略已保存')
      chatModelSettingsVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存开放策略失败')))
    .finally(() => { savingChatModelSettings.value = false })
}

function importLegacy() {
  importingLegacy.value = true
  return importLegacyAiChatRoute()
    .then((res) => {
      const data = res?.data || {}
      proxy.$modal.msgSuccess(`已导入 ${data.importedAccounts || 0} 个账户，跳过 ${data.skipped || 0} 个`)
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '导入旧聊天路由失败')))
    .finally(() => { importingLegacy.value = false })
}

function openRuntimeDialog() {
  Object.assign(runtimeDraft, {
    enabled: flags.enabled === true,
    shadowEnabled: flags.shadowEnabled !== false,
    chatCanaryPercent: Number(flags.chatCanaryPercent || 0),
    visionEnabled: flags.visionEnabled === true,
    imageEnabled: flags.imageEnabled === true,
    ttsEnabled: flags.ttsEnabled === true,
    sttEnabled: flags.sttEnabled === true
  })
  runtimeDialogVisible.value = true
}

function submitRuntimeSettings() {
  proxy.$modal.confirm('确认修改 AI 路由运行开关吗？开启能力后只会影响新的请求。')
    .then(() => {
      savingRuntime.value = true
      return saveAiRoutingRuntimeSettings({ ...runtimeDraft, confirmed: true })
    })
    .then(() => {
      proxy.$modal.msgSuccess('运行开关已更新')
      runtimeDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '运行开关保存失败'))
      }
    })
    .finally(() => { savingRuntime.value = false })
}

function resetRuntimeSettings() {
  proxy.$modal.confirm('确认删除后台动态开关并恢复服务器环境变量中的默认值吗？')
    .then(() => {
      savingRuntime.value = true
      return resetAiRoutingRuntimeSettings()
    })
    .then(() => {
      proxy.$modal.msgSuccess('已恢复环境默认值')
      runtimeDialogVisible.value = false
      return load()
    })
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '恢复环境默认值失败'))
      }
    })
    .finally(() => { savingRuntime.value = false })
}

function openProviderDialog(row) {
  Object.assign(providerForm, defaultProviderForm(), { capability: activeCapability.value })
  accountChoice.value = null
  clearModels()
  if (row) {
    const account = accountFor(row)
    Object.assign(providerForm, {
      accountId: row.accountId,
      accountVersion: account?.versionNo ?? 0,
      deploymentId: row.id,
      providerKey: account?.providerKey || '',
      displayName: account?.displayName || '',
      vendor: account?.vendor || 'custom',
      baseUrl: account?.baseUrl || '',
      apiKeyAction: 'preserve',
      capability: row.capability,
      protocolType: row.protocolType,
      modelName: row.modelName,
      voiceName: row.voiceName || '',
      accountEnabled: account?.enabled !== false,
      deploymentEnabled: row.enabled !== false,
      connectTimeoutSeconds: account?.connectTimeoutSeconds || 10,
      requestTimeoutSeconds: account?.requestTimeoutSeconds || 90,
      failureThreshold: row.failureThreshold || 3,
      cooldownSeconds: row.cooldownSeconds || 180,
      note: account?.note || ''
    })
  }
  providerDialogVisible.value = true
}

function applyAccountChoice(id) {
  const account = accounts.value.find((item) => item.id === id)
  if (!account) {
    Object.assign(providerForm, defaultProviderForm(), { capability: activeCapability.value })
    return
  }
  Object.assign(providerForm, {
    accountId: account.id,
    accountVersion: account.versionNo ?? 0,
    providerKey: account.providerKey,
    displayName: account.displayName,
    vendor: account.vendor,
    baseUrl: account.baseUrl,
    apiKey: '',
    apiKeyAction: 'preserve',
    accountEnabled: account.enabled !== false,
    connectTimeoutSeconds: account.connectTimeoutSeconds || 10,
    requestTimeoutSeconds: account.requestTimeoutSeconds || 90,
    note: account.note || ''
  })
}

function applyVendorDefaults(vendor) {
  const definition = catalog.value.find((item) => item.value === vendor)
  providerForm.baseUrl = definition?.defaultBaseUrl || ''
  if (!providerForm.displayName) providerForm.displayName = definition?.label || ''
  if (!providerForm.providerKey || /^\w+_(chat|vision|image|tts|stt)$/.test(providerForm.providerKey)) {
    providerForm.providerKey = `${vendor}_${providerForm.capability.toLowerCase()}`
  }
  clearModels()
}

function clearModels() {
  modelOptions.value = []
  modelSearch.value = ''
  modelSummary.value = ''
}

function filterModelOptions(query) {
  modelSearch.value = String(query || '')
}

function handleModelDropdownVisible(visible) {
  if (!visible) modelSearch.value = ''
}

function handleKeyAction(action) {
  providerForm.apiKey = ''
  if (action === 'clear') {
    providerForm.accountEnabled = false
    providerForm.deploymentEnabled = false
  }
}

function draftPayload(confirmSharedAccountChange = false) {
  return {
    ...providerForm,
    baseUrl: normalizedBaseUrl.value,
    confirmSharedAccountChange
  }
}

function discoverModels() {
  discovering.value = true
  return discoverAiModels(draftPayload())
    .then((res) => {
      const data = res?.data || {}
      modelOptions.value = data.models || []
      matchedOnly.value = true
      modelSearch.value = ''
      modelSummary.value = `上游 ${data.totalCount || 0} 个，当前能力匹配 ${data.matchedCount || 0} 个`
      if (!providerForm.modelName && filteredModels.value.length) providerForm.modelName = filteredModels.value[0].id
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '获取模型失败')))
    .finally(() => { discovering.value = false })
}

function probeDraft() {
  probing.value = true
  return probeAiCapability(draftPayload())
    .then((res) => {
      proxy.$modal.msgSuccess(`${res?.data?.message || '测试成功'}，${res?.data?.latencyMs || 0} ms`)
      return providerForm.deploymentId ? load() : undefined
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '能力测试失败')))
    .finally(() => { probing.value = false })
}

function probeSaved(row) {
  openProviderDialog(row)
  nextTick(() => probeDraft())
}

function submitProvider() {
  const save = (confirmed = false) => {
    savingProvider.value = true
    return saveAiProvider(draftPayload(confirmed))
    .then(() => {
      proxy.$modal.msgSuccess('供应商能力已保存')
      providerDialogVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存供应商能力失败')))
    .finally(() => { savingProvider.value = false })
  }
  if (sharedAccountDeployments.value.length > 1 && accountConnectionFieldsChanged.value) {
    const affected = sharedAccountCapabilityText.value
    proxy.$modal.confirm(`该账户还用于 ${affected}。确认同时修改这些能力共用的账户连接配置吗？`)
      .then(() => save(true))
      .catch((error) => {
        if (error !== 'cancel' && error !== 'close') {
          proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '确认共享账户变更失败'))
        }
      })
    return
  }
  save(false)
}

function removeDeployment(row) {
  const references = routes.value.filter((route) => route.deploymentIds?.includes(row.id))
  if (references.length) {
    deploymentDeleteTarget.value = row
    deploymentReplacementId.value = null
    deploymentDeleteDialogVisible.value = true
    return
  }
  proxy.$modal.confirm(`确认删除模型 ${row.modelName} 吗？`)
    .then(() => deleteAiDeployment(row.id))
    .then(() => load())
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除能力模型失败'))
      }
    })
}

function submitDeploymentMigration() {
  const target = deploymentDeleteTarget.value
  if (!target) return
  deletingDeployment.value = true
  migrateDeleteAiDeployment(target.id, { replacementDeploymentId: deploymentReplacementId.value })
    .then((res) => {
      const updatedRoutes = res?.data?.updatedRoutes || []
      proxy.$modal.msgSuccess(updatedRoutes.length
        ? `已更新 ${updatedRoutes.length} 条路由并删除模型`
        : '能力模型已删除')
      deploymentDeleteDialogVisible.value = false
      deploymentDeleteTarget.value = null
      deploymentReplacementId.value = null
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '迁移路由并删除模型失败')))
    .finally(() => { deletingDeployment.value = false })
}

function removeAccount(account) {
  proxy.$modal.confirm(`确认删除空账户 ${account.displayName} 吗？`)
    .then(() => deleteAiAccount(account.id))
    .then(() => load())
    .catch((error) => {
      if (error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除供应商账户失败'))
      }
    })
}

function openRouteDialog() {
  routeDraft.value = routeDeploymentRows.value.length ? [...routeDeploymentRows.value] : [...capabilityDeployments.value]
  routeCandidateId.value = null
  routeDialogVisible.value = true
}

function addRouteCandidate(id) {
  const item = deployments.value.find((deployment) => deployment.id === id)
  if (item) routeDraft.value.push(item)
  routeCandidateId.value = null
}

function removeRouteDraft(index) {
  routeDraft.value.splice(index, 1)
}

function submitRoute() {
  const route = capabilityRoute.value
  if (!routeDraft.value.length) {
    if (!route?.id) {
      proxy.$modal.msgWarning('当前没有可删除的执行路由')
      return
    }
    proxy.$modal.confirm(`确认删除“${route.displayName}”吗？删除后该能力将不再有默认执行路由。`)
      .then(() => {
        savingRoute.value = true
        return deleteAiRoute(route.id)
      })
      .then(() => {
        proxy.$modal.msgSuccess('执行路由已删除，现在可以删除不再被其他路由引用的模型')
        routeDialogVisible.value = false
        return load()
      })
      .catch((error) => {
        if (error !== 'cancel' && error !== 'close') {
          proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除执行路由失败'))
        }
      })
      .finally(() => { savingRoute.value = false })
    return
  }
  savingRoute.value = true
  saveAiRoute({
    id: route?.id || null,
    routeKey: defaultRouteKey(activeCapability.value),
    displayName: `${capabilityLabel.value}默认路由`,
    capability: activeCapability.value,
    deploymentIds: routeDraft.value.map((item) => item.id),
    enabled: true,
    note: ''
  })
    .then(() => {
      proxy.$modal.msgSuccess('执行顺序已保存')
      routeDialogVisible.value = false
      return load()
    })
    .catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存执行顺序失败')))
    .finally(() => { savingRoute.value = false })
}

function accountFor(deployment) {
  return accounts.value.find((item) => item.id === deployment?.accountId)
}

function providerInitial(row) {
  const name = accountFor(row)?.displayName || accountFor(row)?.vendor || '?'
  return name.slice(0, 1).toUpperCase()
}

function deploymentStatus(row) {
  if (accountFor(row)?.enabled === false || row.enabled === false) return { text: '停用', type: 'info' }
  if (row.circuitOpenUntil && Date.parse(row.circuitOpenUntil) > Date.now()) return { text: '熔断中', type: 'danger' }
  if (row.lastHealthStatus === 'healthy') return { text: '健康', type: 'success' }
  if (row.lastHealthStatus === 'configuration_error') return { text: '配置错误', type: 'danger' }
  if (row.lastHealthStatus === 'authentication_error') return { text: '鉴权失败', type: 'danger' }
  if (row.lastHealthStatus === 'incompatible_response') return { text: '响应不兼容', type: 'warning' }
  if (row.lastHealthStatus === 'failing') return { text: '失败中', type: 'warning' }
  return { text: '未探测', type: '' }
}

function capabilityName(value) {
  return capabilityOptions.find((item) => item.value === value)?.label || value || '--'
}

function protocolLabel(capability) {
  return `${capabilityName(capability)} · OpenAI 兼容`
}

function defaultRouteKey(capability) {
  return `${String(capability || '').toLowerCase()}.default`
}

function normalizeBaseUrl(raw) {
  let value = String(raw || '').trim().replace(/\/+$/, '')
  value = value.replace(/\/(chat\/completions|images\/generations|audio\/speech|audio\/transcriptions|models)$/i, '')
  return /\/v1$/i.test(value) ? value : (value ? `${value}/v1` : '')
}

load()
</script>

<style scoped>
.routing-shell {
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.routing-header,
.capability-toolbar,
.route-title,
.dialog-footer,
.model-picker-head,
.key-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.routing-header {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.routing-header h2 {
  margin: 3px 0 0;
  font-family: "Microsoft YaHei UI", sans-serif;
  font-size: 21px;
  letter-spacing: 0;
}

.routing-kicker {
  color: var(--el-color-primary);
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

.routing-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rollout-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr)) minmax(180px, 1.4fr) minmax(130px, 1fr);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.rollout-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 13px 18px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.rollout-item:last-child { border-right: 0; }
.rollout-item span { color: var(--el-text-color-secondary); font-size: 12px; }
.rollout-item strong { color: var(--el-text-color-primary); font-size: 14px; }

.capability-toolbar {
  padding: 18px 24px;
}

.capability-toolbar :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 128px;
  justify-content: center;
}

.capability-count { display: flex; align-items: baseline; gap: 7px; color: var(--el-text-color-secondary); }
.capability-count strong { color: var(--el-text-color-primary); font-size: 22px; }
.capability-count span { font-size: 12px; }
.provider-table-wrap { padding: 0 24px 20px; }

.provider-name { display: flex; align-items: center; gap: 10px; min-width: 0; }
.provider-name > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.provider-name strong, .provider-name small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.provider-name small { color: var(--el-text-color-secondary); }
.provider-mark {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

.mono-cell { font-family: Consolas, monospace; font-size: 12px; }
.route-band { padding: 18px 24px 22px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-extra-light); }
.route-title > div { display: flex; flex-direction: column; gap: 4px; }
.route-title span { color: var(--el-text-color-secondary); font-size: 12px; }
.route-title strong { font-size: 16px; }
.route-chain { display: flex; align-items: center; gap: 9px; overflow-x: auto; margin-top: 16px; padding-bottom: 4px; }
.route-node { display: flex; align-items: center; gap: 10px; min-width: 190px; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.route-node > span, .order-number { display: grid; width: 24px; height: 24px; flex: 0 0 24px; place-items: center; border-radius: 50%; background: var(--el-color-primary); color: white; font-size: 12px; font-weight: 700; }
.route-node > div, .route-editor-item > div { display: flex; flex-direction: column; min-width: 0; }
.route-node small, .route-editor-item small { overflow: hidden; color: var(--el-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
.route-arrow { flex: 0 0 auto; color: var(--el-text-color-placeholder); }
.orphan-band { display: flex; align-items: center; gap: 16px; padding: 10px 24px; border-top: 1px dashed var(--el-border-color); color: var(--el-text-color-secondary); font-size: 12px; }
.orphan-item { display: flex; align-items: center; gap: 4px; }

.provider-form { max-height: 62vh; overflow-y: auto; padding-right: 8px; }
.runtime-form { margin-top: 18px; }
.runtime-form :deep(.el-slider) { width: 100%; }
.shared-account-alert { margin-bottom: 16px; }
.form-section-title { margin: 4px 0 12px; color: var(--el-text-color-primary); font-size: 14px; font-weight: 700; }
.form-grid { display: grid; gap: 0 18px; }
.two-col { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.three-col { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.span-two { grid-column: 1 / -1; }
.normalized-url, .model-summary { margin-top: 5px; color: var(--el-text-color-secondary); font-family: Consolas, monospace; font-size: 12px; }
.key-row { width: 100%; justify-content: flex-start; }
.key-row .el-input { flex: 1; }
.key-mask { color: var(--el-text-color-secondary); font-family: Consolas, monospace; }
.model-picker { margin: 3px 0 18px; padding: 14px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.model-picker-head { margin-bottom: 10px; }
.model-picker-head > span { font-weight: 600; }
.model-picker-head > div { display: flex; align-items: center; gap: 14px; }
.model-picker .el-select { width: 100%; }
.model-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.compact-grid :deep(.el-input-number) { width: 100%; }
.route-editor { display: grid; gap: 14px; }
.route-editor-item { display: grid; grid-template-columns: 24px 28px minmax(0, 1fr) 34px; align-items: center; gap: 10px; margin-bottom: 8px; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.drag-handle { cursor: grab; color: var(--el-text-color-secondary); }
.deployment-reference-list { display: grid; gap: 8px; margin: 16px 0; }
.deployment-reference-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.deployment-reference-item > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.deployment-reference-item small { overflow: hidden; color: var(--el-text-color-secondary); font-family: Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.deployment-reference-list + .el-form :deep(.el-select) { width: 100%; }
.delete-migration-hint { display: block; margin-top: 6px; color: var(--el-color-danger); }
.chat-workspace-tabs { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 24px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-extra-light); }
.chat-workspace-tabs > span { color: var(--el-text-color-secondary); font-size: 12px; }
.offering-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 24px; border-top: 1px solid var(--el-border-color-lighter); }
.offering-rollout-state { display: flex; align-items: center; gap: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.offering-toolbar-actions { display: flex; gap: 10px; }
.offering-name-cell, .offering-table-wrap td small { display: flex; flex-direction: column; gap: 5px; }
.offering-name-cell > div { display: flex; align-items: center; gap: 7px; }
.offering-name-cell > span { overflow: hidden; color: var(--el-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
.offering-name-cell > small { color: var(--el-text-color-placeholder); font-family: Consolas, monospace; }
.tag-line { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 6px; }
.route-ready { color: var(--el-color-success); }
.route-missing { color: var(--el-color-danger); }
.offering-form { padding-right: 8px; }
.offering-form > .el-alert { margin-bottom: 24px; }
.offering-section-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 26px 0 14px; padding-bottom: 10px; border-bottom: 1px solid var(--el-border-color-lighter); }
.offering-section-head > div { display: flex; flex-direction: column; gap: 4px; }
.offering-section-head strong { font-size: 15px; }
.offering-section-head span { color: var(--el-text-color-secondary); font-size: 12px; }
.price-rule-list { display: grid; gap: 10px; }
.price-rule-row { display: grid; grid-template-columns: 110px minmax(180px, 1.5fr) repeat(3, minmax(110px, 1fr)) 34px; align-items: end; gap: 10px; padding: 12px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.price-rule-row .el-form-item { margin-bottom: 0; }
.price-rule-row :deep(.el-input-number), .offering-form :deep(.el-select) { width: 100%; }
.offering-switches :deep(.el-input-number) { width: 100%; }
.offering-preview { margin: 24px 0 8px; padding: 16px 18px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.offering-preview > span { color: var(--el-text-color-secondary); font-size: 12px; }
.offering-preview > div { display: flex; align-items: center; gap: 8px; margin-top: 9px; }
.offering-preview b { margin-left: auto; color: var(--el-color-primary); }
.offering-preview p { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.offering-fallback-order { display: grid; gap: 7px; margin-top: 12px; }
.offering-fallback-row { display: grid; grid-template-columns: 28px minmax(0, 1fr) 32px 32px; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.offering-fallback-row > b { display: flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 4px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; }
.offering-fallback-row > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.offering-fallback-row span { overflow: hidden; color: var(--el-text-color-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.billing-mode-option { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.billing-mode-option span { color: var(--el-text-color-secondary); font-size: 12px; }
.price-rule-preview { grid-column: 1 / -1; color: var(--el-color-primary); font-size: 12px; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 10px; }

@media (max-width: 900px) {
  .routing-header, .capability-toolbar { align-items: flex-start; flex-direction: column; }
  .routing-header-actions { flex-wrap: wrap; }
  .rollout-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .rollout-item:nth-child(2) { border-right: 0; }
  .capability-toolbar :deep(.el-radio-group) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; }
  .capability-toolbar :deep(.el-radio-button__inner) { width: 100%; min-width: 0; border-left: var(--el-border); border-radius: 0; }
  .two-col, .three-col { grid-template-columns: 1fr; }
  .span-two { grid-column: auto; }
  .key-row { align-items: stretch; flex-direction: column; }
  .chat-workspace-tabs, .offering-toolbar, .offering-rollout-state { align-items: flex-start; flex-direction: column; }
  .offering-toolbar-actions { width: 100%; flex-wrap: wrap; }
  .price-rule-row { grid-template-columns: 1fr; }
}
</style>
