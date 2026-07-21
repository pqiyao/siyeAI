<template>
  <div class="app-container">
    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="名称">
        <el-input v-model="queryParams.name" placeholder="角色名" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item :label="uiText.scopeLabel">
        <el-radio-group v-model="queryParams.scope" @change="handleScopeChange">
          <el-radio-button v-for="opt in scopeOptions" :key="opt.value" :label="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="queryParams.scope !== 'system'" :label="uiText.ownerUid">
        <el-input
          v-model="queryParams.ownerClientUid"
          :placeholder="uiText.ownerPlaceholder"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="uiText.reviewLabel">
        <el-select v-model="queryParams.reviewStatus" clearable style="width: 160px" @change="handleQuery">
          <el-option v-for="opt in reviewStatusOptions" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-if="queryParams.scope !== 'user'" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain :disabled="multiple" @click="handleApprove()">通过审核</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain :disabled="multiple" @click="handleReject()">驳回审核</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Tickets" @click="openReviewLog">审核台账</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-if="queryParams.scope !== 'user'"
          type="warning"
          plain
          icon="Upload"
          @click="openImport = true"
        >导入 SillyTavern</el-button>
      </el-col>
      <el-col v-if="false" :span="1.5">
        <el-button type="info" plain icon="Refresh" :disabled="multiple" @click="batchEvictLore">失效世界书缓存</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <div v-if="queryParams.scope === 'user'" class="jg-stats">
      <div class="jg-stat-card">
        <div class="jg-stat-num">{{ userCreatedStats.totalUserCreated || 0 }}</div>
        <div class="jg-stat-label">{{ uiText.totalCreated }}</div>
      </div>
      <div class="jg-stat-card">
        <div class="jg-stat-num">{{ userCreatedStats.ownerCount || 0 }}</div>
        <div class="jg-stat-label">{{ uiText.ownerCount }}</div>
      </div>
      <div class="jg-stat-card jg-stat-card--wide">
        <div class="jg-stat-label">{{ uiText.topOwners }}</div>
        <div v-if="userCreatedStats.topOwners && userCreatedStats.topOwners.length" class="jg-owner-list">
          <div v-for="row in userCreatedStats.topOwners" :key="row.ownerClientUid" class="jg-owner-row">
            <div class="jg-owner-lines">
              <span class="jg-owner-id">{{ row.ownerDisplayName || row.ownerClientUid || uiText.noOwner }}</span>
              <span v-if="row.ownerSubLabel" class="jg-owner-sub">{{ row.ownerSubLabel }}</span>
            </div>
            <el-tag size="small" type="info">{{ row.count }}</el-tag>
          </div>
        </div>
        <div v-else class="jg-owner-empty">-</div>
      </div>
    </div>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="头像" width="72" align="center">
        <template #default="scope">
          <el-image
            v-if="scope.row.avatarUrl"
            class="jg-av"
            :src="displayThumbUrl(scope.row.avatarUrl, 'avatar')"
            fit="cover"
            lazy
            :preview-src-list="[displayImgUrl(scope.row.avatarUrl)]"
            preview-teleported
          />
          <el-tag v-else-if="scope.row.avatarHasInlineImage" type="info" size="small">内嵌图</el-tag>
          <span v-else class="jg-av-ph">—</span>
        </template>
      </el-table-column>
      <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="标语" prop="tagline" min-width="120" show-overflow-tooltip />
      <el-table-column label="世界书" width="150">
        <template #default="scope">
          <el-button
            link
            :type="lorebookTotal(scope.row.lorebookSummary) > 0 ? 'primary' : 'info'"
            @click="openCharacterLorebook(scope.row)"
          >
            {{ lorebookSummaryText(scope.row.lorebookSummary) }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column :label="uiText.sourceLabel" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.userCreated ? 'warning' : 'success'">
            {{ scope.row.userCreated ? uiText.userCreated : uiText.systemRole }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="uiText.ownerUid" prop="ownerClientUid" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <div class="jg-owner-lines">
            <span>{{ scope.row.ownerDisplayName || scope.row.ownerClientUid || '-' }}</span>
            <span v-if="scope.row.ownerSubLabel" class="jg-owner-sub">{{ scope.row.ownerSubLabel }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="uiText.reviewLabel" width="120">
        <template #default="scope">
          <el-tag :type="reviewStatusTagType(scope.row.reviewStatus)">
            {{ reviewStatusLabel(scope.row.reviewStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="驳回原因" prop="reviewReason" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.reviewReason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="VIP" prop="vipOnly" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.vipOnly ? 'warning' : 'info'">{{ scope.row.vipOnly ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用户端显示" prop="clientVisible" width="96">
        <template #default="scope">
          <el-tag :type="scope.row.clientVisible === false ? 'danger' : 'success'">
            {{ scope.row.clientVisible === false ? '隐藏' : '显示' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="清晰预览" prop="previewBlurVipLevel" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.previewBlurVipLevel > 0 ? 'warning' : 'info'">
            {{ previewBlurLevelLabel(scope.row.previewBlurVipLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="70" />
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button
            v-if="scope.row.userCreated && scope.row.reviewStatus !== 'APPROVED'"
            link
            type="success"
            @click="handleApprove(scope.row)"
          >通过</el-button>
          <el-button
            v-if="scope.row.userCreated && scope.row.reviewStatus !== 'REJECTED'"
            link
            type="warning"
            @click="handleReject(scope.row)"
          >驳回</el-button>
          <el-button link type="info" icon="Notebook" @click="openCharacterLorebook(scope.row)">世界书</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 编辑：对齐 SillyTavern 字段 + 酒馆 H5 发现页 -->
    <el-dialog :title="title" v-model="open" width="1100px" append-to-body destroy-on-close class="jg-char-dialog" align-center>
      <el-scrollbar max-height="72vh">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="132px" class="jg-form">
          <el-tabs v-model="editTab" type="border-card" class="jg-tabs">
            <el-tab-pane label="基础与展示" name="base">
              <el-alert type="info" :closable="false" class="jg-pane-alert" title="对应 H5 发现页卡片：名称、标语、标签、封面；与 SillyTavern 卡名、展示用描述一致。" />
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="角色名称" prop="name">
                    <el-input v-model="form.name" placeholder="与 ST name / data.name 一致" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="标语 tagline">
                    <el-input v-model="form.tagline" placeholder="列表短句，可留空由简介自动生成" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="职业/角标">
                    <el-input v-model="form.occupationLabel" placeholder="发现页小字，如「对手戏」" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Token 展示">
                    <el-input v-model="form.tokenDisplay" placeholder="如 &lt;2000，仅展示" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="标签">
                    <el-select
                      v-model="tagList"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      collapse-tags
                      collapse-tags-tooltip
                      placeholder="输入后回车添加，对应 ST tags → 存 tagsJson"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in tagOptions"
                        :key="item.code"
                        :label="item.name"
                        :value="item.name"
                      />
                    </el-select>
                    <div class="jg-tag-preview">
                      <div class="jg-tag-preview-head">
                        <span class="jg-tag-preview-title">标签展示面预览</span>
                        <span class="jg-tag-preview-sub">发现页和详情页最终展示以标签库开关为准</span>
                      </div>
                      <div class="jg-tag-preview-grid">
                        <div class="jg-tag-preview-card">
                          <div class="jg-tag-preview-card-head">
                            <span>发现页展示</span>
                            <el-tag size="small" type="primary">{{ discoverTagPreview.length }}</el-tag>
                          </div>
                          <div class="jg-tag-preview-list">
                            <el-tag
                              v-for="item in discoverTagPreview"
                              :key="'discover_' + item.value"
                              size="small"
                              effect="plain"
                            >{{ item.label }}</el-tag>
                            <span v-if="!discoverTagPreview.length" class="jg-tag-preview-empty">当前没有会出现在发现页的标签</span>
                          </div>
                        </div>
                        <div class="jg-tag-preview-card">
                          <div class="jg-tag-preview-card-head">
                            <span>详情页展示</span>
                            <el-tag size="small" type="success">{{ detailTagPreview.length }}</el-tag>
                          </div>
                          <div class="jg-tag-preview-list">
                            <el-tag
                              v-for="item in detailTagPreview"
                              :key="'detail_' + item.value"
                              size="small"
                              effect="plain"
                              type="success"
                            >{{ item.label }}</el-tag>
                            <span v-if="!detailTagPreview.length" class="jg-tag-preview-empty">当前没有会出现在详情页的标签</span>
                          </div>
                        </div>
                        <div class="jg-tag-preview-card">
                          <div class="jg-tag-preview-card-head">
                            <span>发现推荐与限制</span>
                            <el-tag size="small" type="warning">{{ recommendedTagPreview.length + restrictedTagPreview.length }}</el-tag>
                          </div>
                          <div class="jg-tag-preview-list">
                            <el-tag
                              v-for="item in recommendedTagPreview"
                              :key="'recommended_' + item.value"
                              size="small"
                              effect="dark"
                              type="warning"
                            >推荐 · {{ item.label }}</el-tag>
                            <el-tag
                              v-for="item in restrictedTagPreview"
                              :key="'restricted_' + item.value"
                              size="small"
                              effect="plain"
                              type="danger"
                            >{{ item.restrictionText }} · {{ item.label }}</el-tag>
                            <span
                              v-if="!recommendedTagPreview.length && !restrictedTagPreview.length"
                              class="jg-tag-preview-empty"
                            >当前没有推荐或限制标签</span>
                          </div>
                        </div>
                        <div class="jg-tag-preview-card">
                          <div class="jg-tag-preview-card-head">
                            <span>待补充或隐藏</span>
                            <el-tag size="small" type="info">{{ hiddenTagPreview.length + customTagPreview.length }}</el-tag>
                          </div>
                          <div class="jg-tag-preview-list">
                            <el-tag
                              v-for="item in hiddenTagPreview"
                              :key="'hidden_' + item.value"
                              size="small"
                              effect="plain"
                              type="info"
                            >隐藏 · {{ item.label }}</el-tag>
                            <el-tag
                              v-for="item in customTagPreview"
                              :key="'custom_' + item.value"
                              size="small"
                              effect="plain"
                            >待入库 · {{ item.label }}</el-tag>
                            <span
                              v-if="!hiddenTagPreview.length && !customTagPreview.length"
                              class="jg-tag-preview-empty"
                            >当前没有隐藏标签，未入库标签也会在保存后同步到标签库</span>
                          </div>
                        </div>
                      </div>
                      <p class="jg-field-hint jg-tag-preview-tip">
                        运营建议：标签“是否推荐 / 是否详情展示 / 是否在发现页展示”统一在标签库里控制；角色页只负责选择使用哪些标签。
                      </p>
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="排序">
                    <el-input-number v-model="form.sortOrder" :min="0" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="玩法类型">
                    <el-input v-model="form.gameplayType" placeholder="如 对手戏" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="仅 VIP">
                    <el-switch v-model="form.vipOnly" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="默认解锁">
                    <el-switch v-model="form.unlockedDefault" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="用户端显示">
                    <el-switch v-model="form.clientVisible" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="清晰预览门槛">
                    <el-select v-model="form.previewBlurVipLevel" style="width: 100%">
                      <el-option
                        v-for="item in previewBlurOptions"
                        :key="'preview_blur_' + item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                    <p class="jg-field-hint jg-img-hint">
                      仅影响用户端图片预览是否加模糊玻璃层，不影响现有 VIP 解锁与聊天权限。
                    </p>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="创作者">
                    <el-input v-model="form.creatorName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="创作者 @">
                    <el-input v-model="form.creatorHandle" placeholder="handle，勿带 @" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="头像 / 封面">
                    <div class="jg-img-row">
                      <div class="jg-img-cell">
                        <div class="jg-img-lab">头像</div>
                        <el-image
                          v-if="form.avatarUrl || form.stAvatarUrl"
                          class="jg-prev"
                          :src="displayImgUrl(form.avatarUrl || form.stAvatarUrl)"
                          fit="cover"
                          lazy
                        />
                        <span v-else class="jg-img-empty">无</span>
                        <div class="jg-upload-btns">
                          <el-upload
                            :action="uploadAction"
                            :headers="uploadHeaders"
                            :show-file-list="false"
                            accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                            :before-upload="beforeCharacterImg"
                            :on-success="onAvatarUploadSuccess"
                          >
                            <el-button type="primary" size="small">上传头像</el-button>
                          </el-upload>
                        </div>
                      </div>
                      <div class="jg-img-cell">
                        <div class="jg-img-lab">封面</div>
                        <el-image
                          v-if="form.coverUrl || form.stAvatarUrl"
                          class="jg-prev jg-prev-wide"
                          :src="displayImgUrl(form.coverUrl || form.stAvatarUrl)"
                          fit="cover"
                          lazy
                        />
                        <span v-else class="jg-img-empty">无</span>
                        <div class="jg-upload-btns">
                          <el-upload
                            :action="uploadAction"
                            :headers="uploadHeaders"
                            :show-file-list="false"
                            accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                            :before-upload="beforeCharacterImg"
                            :on-success="onCoverUploadSuccess"
                          >
                            <el-button type="primary" size="small" plain>上传封面</el-button>
                          </el-upload>
                        </div>
                      </div>
                      <div class="jg-img-cell">
                        <div class="jg-img-lab">聊天背景</div>
                        <el-image
                          v-if="form.chatBackgroundUrl || form.coverUrl"
                          class="jg-prev jg-prev-wide"
                          :src="displayImgUrl(form.chatBackgroundUrl || form.coverUrl)"
                          fit="cover"
                          lazy
                        />
                        <span v-else class="jg-img-empty">无</span>
                        <div class="jg-upload-btns">
                          <el-upload
                            :action="uploadAction"
                            :headers="uploadHeaders"
                            :show-file-list="false"
                            accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                            :before-upload="beforeCharacterImg"
                            :on-success="onBackgroundUploadSuccess"
                          >
                            <el-button type="primary" size="small" plain>上传聊天背景</el-button>
                          </el-upload>
                        </div>
                      </div>
                    </div>
                    <p class="jg-img-hint">
                      头像用于列表和详情展示；封面用于详情展示；聊天背景用于聊天页官方背景。未配置聊天背景时，H5 会退回默认深色背景。
                    </p>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="角色 Worldbooks">
                    <el-alert
                      v-if="worldbookOptionsError"
                      class="mb8"
                      type="warning"
                      :closable="false"
                      :title="worldbookOptionsError"
                    />
                    <el-select
                      v-model="form.stWorldNames"
                      multiple
                      filterable
                      clearable
                      collapse-tags
                      collapse-tags-tooltip
                      :multiple-limit="10"
                      :loading="worldbookOptionsLoading"
                      placeholder="为这个角色选择固定绑定的 ST worldbooks；留空则回退 ST 默认"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in characterWorldbookOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                    <p class="jg-field-hint">
                      这里是角色级配置，会影响所有使用这个角色的新对话；会话级 worldbook 仅保留给后台运营做临时 override。
                    </p>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-collapse>
                    <el-collapse-item title="粘贴图片地址（可选）" name="url">
                      <el-row :gutter="12">
                        <el-col :span="12">
                          <el-form-item label="头像 URL / Base64">
                            <el-input v-model="form.avatarUrl" type="textarea" :rows="2" />
                          </el-form-item>
                        </el-col>
                        <el-col :span="12">
                          <el-form-item label="封面 URL / Base64">
                            <el-input v-model="form.coverUrl" type="textarea" :rows="2" />
                          </el-form-item>
                        </el-col>
                        <el-col :span="12">
                          <el-form-item label="聊天背景 URL / Base64">
                            <el-input v-model="form.chatBackgroundUrl" type="textarea" :rows="2" />
                          </el-form-item>
                        </el-col>
                      </el-row>
                    </el-collapse-item>
                  </el-collapse>
                </el-col>
              </el-row>
            </el-tab-pane>

            <el-tab-pane label="角色与剧情" name="story">
              <el-alert type="info" :closable="false" class="jg-pane-alert" title="对齐 SillyTavern：description→bio，personality→persona，scenario，first_mes；后端 TavernPromptBuilder / H5 详情一致。" />
              <el-form-item label="角色描述 bio">
                <el-input v-model="form.bio" type="textarea" :rows="5" placeholder="ST: description — 发现页/详情介绍长文" />
              </el-form-item>
              <el-form-item label="性格人设 persona">
                <el-input v-model="form.persona" type="textarea" :rows="6" placeholder="ST: personality — 写入 system 角色段" />
              </el-form-item>
              <el-form-item label="情景 scenario">
                <el-input v-model="form.scenario" type="textarea" :rows="4" placeholder="ST: scenario — 当前世界/背景" />
              </el-form-item>
              <el-form-item label="第一条消息">
                <el-input v-model="form.firstMessage" type="textarea" :rows="4" placeholder="ST: first_mes — 开场白（与随机开场二选一逻辑在后端）" />
              </el-form-item>
              <el-form-item label="其他开场">
                <p class="jg-field-hint">对应 ST alternate_greetings；多条时后端与首条随机选用。</p>
                <div v-for="(line, idx) in greetingLines" :key="idx" class="jg-greet-row">
                  <el-input v-model="greetingLines[idx]" type="textarea" :rows="2" placeholder="一条备选开场白" />
                  <el-button type="danger" link :disabled="greetingLines.length <= 1" @click="removeGreeting(idx)">删除</el-button>
                </div>
                <el-button type="primary" link icon="Plus" @click="addGreeting">添加开场</el-button>
              </el-form-item>
            </el-tab-pane>

            <el-tab-pane label="提示词与示例" name="prompt">
              <el-alert type="warning" :closable="false" class="jg-pane-alert" title="system_prompt 非空时覆盖服务端拼装的 system；post_history 在历史后注入；mes_example 为示例对话。" />
              <el-form-item label="系统提示 system">
                <el-input v-model="form.systemPrompt" type="textarea" :rows="5" placeholder="留空则后端按角色卡分段拼装（见 TavernContextComposer）" />
              </el-form-item>
              <el-form-item label="post-history 说明">
                <el-input v-model="form.postHistoryInstructions" type="textarea" :rows="3" placeholder="ST: post_history_instructions" />
              </el-form-item>
              <el-form-item label="对话示例 mes_example">
                <el-input v-model="form.mesExample" type="textarea" :rows="8" placeholder="ST: mes_example；常用 &lt;START&gt; 分段，与 SillyTavern 一致" />
              </el-form-item>
              <el-form-item label="创作者备注">
                <el-input v-model="form.creatorNotes" type="textarea" :rows="3" placeholder="ST: creator_notes — 仅后台备忘，不进模型（TavernPromptBuilder）" />
              </el-form-item>
            </el-tab-pane>

            <el-tab-pane label="世界书" name="lorebook">
              <div class="jg-lorebook-head">
                <div class="jg-lorebook-stat">
                  <span class="jg-lorebook-stat-num">{{ lorebookTotal(form.lorebookSummary) }}</span>
                  <span>总条目</span>
                </div>
                <div class="jg-lorebook-stat">
                  <span class="jg-lorebook-stat-num">{{ lorebookCount(form.lorebookSummary, 'embedded') }}</span>
                  <span>卡内同步</span>
                </div>
                <div class="jg-lorebook-stat">
                  <span class="jg-lorebook-stat-num">{{ lorebookCount(form.lorebookSummary, 'manual') }}</span>
                  <span>手工条目</span>
                </div>
                <div class="jg-lorebook-actions">
                  <el-button type="primary" icon="Notebook" :disabled="!form.id" @click="openCharacterLorebook(form)">打开管理页</el-button>
                  <el-button icon="Refresh" :disabled="!form.id" :loading="characterLorebookLoading" @click="loadFormLorebookEntries(form.id)">刷新</el-button>
                </div>
              </div>
              <el-alert
                :type="form.stWorldNames && form.stWorldNames.length ? 'warning' : 'success'"
                :closable="false"
                class="jg-pane-alert"
                :title="lorebookRuntimeHint"
              />
              <el-table v-loading="characterLorebookLoading" :data="characterLorebookRows" border>
                <el-table-column label="来源" width="100">
                  <template #default="scope">
                    <el-tag :type="isEmbeddedLorebook(scope.row) ? 'success' : 'info'">
                      {{ isEmbeddedLorebook(scope.row) ? '卡内同步' : '手工' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="启用" width="72">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '是' : '否' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="关键词" prop="keywordsCsv" min-width="180" show-overflow-tooltip />
                <el-table-column label="优先级" prop="priority" width="80" />
                <el-table-column label="内容预览" prop="content" min-width="280" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!characterLorebookLoading && !characterLorebookRows.length" description="这张角色卡还没有同步到本地世界书条目" />
            </el-tab-pane>

            <el-tab-pane label="高级 / 原始" name="adv">
              <el-alert type="info" :closable="false" class="jg-pane-alert" title="一般无需修改；chat_modes_json 为 H5 详情「聊天模式」列表 JSON。" />
              <el-form-item label="聊天模式 JSON">
                <el-input v-model="form.chatModesJson" type="textarea" :rows="4" placeholder='[{"icon":"🚀","name":"极速模式","sub":"2.0"}]' />
              </el-form-item>
              <el-form-item label="stExtraJson">
                <el-input v-model="form.stExtraJson" type="textarea" :rows="6" placeholder="导入 ST 时写入的完整原始 JSON，便于再导出" />
              </el-form-item>
              <el-form-item label="alternate JSON（只读同步）">
                <el-input v-model="form.alternateGreetingsJson" type="textarea" :rows="2" readonly />
              </el-form-item>
              <el-form-item label="tags JSON（只读同步）">
                <el-input v-model="form.tagsJson" type="textarea" :rows="1" readonly />
              </el-form-item>
            </el-tab-pane>
          </el-tabs>
        </el-form>
      </el-scrollbar>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 导入：PNG（ST 同款 tEXt） / JSON -->
    <el-dialog title="从 SillyTavern 导入" v-model="openImport" width="640px" append-to-body destroy-on-close>
      <el-tabs v-model="importTab">
        <el-tab-pane label="PNG 角色卡" name="png">
            <p class="jg-import-tip">
              仅支持从 SillyTavern 直接导出的角色卡 PNG，不支持普通立绘 PNG，也不支持把网站二次压缩后丢失元数据的图片。
              当前会读取 PNG <code>tEXt</code> 中的角色卡信息，并同步基础字段、标签、候选开场；JSON 角色卡导入暂未实现。
            </p>
          <el-upload
            class="jg-png-upload"
            drag
            accept=".png,image/png"
            :show-file-list="true"
            :limit="1"
            :before-upload="beforePngImport"
            :http-request="httpRequestPngImport"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将 PNG 拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">单文件上传，最大 28MB；建议直接使用 ST「导出角色卡」生成的 PNG 原文件</div>
            </template>
          </el-upload>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="openImport = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgCharacter">
import { ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  listCharacter,
  listCharacterWorldbookOptions,
  getUserCreatedCharacterStats,
    getCharacter,
    addCharacter,
    updateCharacter,
    delCharacter,
    reviewCharacter as reviewCharacterApi,
    batchEvictCharacterLoreCache,
    importSillyTavernPng
} from '@/api/jiugai/character'
import { listLorebook } from '@/api/jiugai/lorebook'
import { listTagOptions } from '@/api/jiugai/taglibrary'
import { getToken } from '@/utils/auth'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const SILLY_TAVERN_PNG_MAX_MB = 28
const SILLY_TAVERN_PNG_MAX_BYTES = SILLY_TAVERN_PNG_MAX_MB * 1024 * 1024
const uploadAction = baseApi + '/admin/jiugai/upload/image'
const uploadHeaders = ref({ Authorization: 'Bearer ' + getToken() })
const useProxyUploadsInAdmin = import.meta.env.DEV

const editTab = ref('base')
const importTab = ref('png')
const greetingLines = ref([''])
const tagList = ref([])
const tagOptions = ref([])
const worldbookOptions = ref([])
const worldbookOptionsLoading = ref(false)
const worldbookOptionsError = ref('')
const userCreatedStats = ref({ totalUserCreated: 0, ownerCount: 0, topOwners: [] })
const characterLorebookRows = ref([])
const characterLorebookLoading = ref(false)
const uiText = Object.freeze({
  systemRole: '\u7cfb\u7edf\u89d2\u8272',
  userCreated: '\u7528\u6237\u521b\u5efa',
  allRoles: '\u5168\u90e8',
  ownerUid: '用户',
  scopeLabel: '\u89d2\u8272\u8303\u56f4',
  sourceLabel: '\u6765\u6e90',
    totalCreated: '\u7528\u6237\u5361\u603b\u6570',
    ownerCount: '\u521b\u4f5c\u7528\u6237\u6570',
    topOwners: '\u521b\u4f5c\u6392\u884c',
    noOwner: '\u672a\u7ed1\u5b9a',
    ownerPlaceholder: '\u6309 owner \u6807\u8bc6\u7b5b\u9009',
    reviewLabel: '\u5ba1\u6838\u72b6\u6001',
    reviewAll: '\u5168\u90e8\u72b6\u6001',
    reviewPending: '\u5f85\u5ba1\u6838',
    reviewApproved: '\u5df2\u901a\u8fc7',
    reviewRejected: '\u5df2\u9a73\u56de'
  })
  const scopeOptions = Object.freeze([
    { value: 'system', label: uiText.systemRole },
    { value: 'user', label: uiText.userCreated },
    { value: 'all', label: uiText.allRoles }
  ])
  const reviewStatusOptions = Object.freeze([
    { value: undefined, label: uiText.reviewAll },
    { value: 'PENDING', label: uiText.reviewPending },
    { value: 'APPROVED', label: uiText.reviewApproved },
    { value: 'REJECTED', label: uiText.reviewRejected }
  ])
const previewBlurOptions = Object.freeze([
  { value: 0, label: '所有用户清晰' },
  { value: 1, label: 'VIP 及以上清晰' },
  { value: 2, label: '仅 SVIP 清晰' }
])

function normalizePreviewBlurLevel(value) {
  const level = Number(value)
  if (!Number.isFinite(level) || level <= 0) return 0
  return level >= 2 ? 2 : 1
}

function previewBlurLevelLabel(value) {
  const level = normalizePreviewBlurLevel(value)
  if (level >= 2) return 'SVIP 清晰'
  if (level >= 1) return 'VIP 清晰'
  return '全部清晰'
}

function normalizeTagKey(value) {
  return String(value || '').trim().toLowerCase()
}

const tagOptionIndex = computed(() => {
  const index = {}
  ;(tagOptions.value || []).forEach((item) => {
    if (!item || typeof item !== 'object') return
    const byCode = normalizeTagKey(item.code)
    const byName = normalizeTagKey(item.name)
    if (byCode) {
      index[byCode] = item
    }
    if (byName) {
      index[byName] = item
    }
  })
  return index
})

const selectedTagPreview = computed(() => {
  return (tagList.value || [])
    .map((value) => {
      const label = String(value || '').trim()
      if (!label) return null
      const option = tagOptionIndex.value[normalizeTagKey(label)] || null
      return {
        value: label,
        label: label,
        option: option,
        restrictionText: option?.vipOnly ? '会员限定' : option?.adultOnly ? '成人向' : ''
      }
    })
    .filter(Boolean)
})

const discoverTagPreview = computed(() => selectedTagPreview.value.filter((item) => item.option?.discoverVisible))
const detailTagPreview = computed(() => selectedTagPreview.value.filter((item) => item.option?.detailVisible))
const recommendedTagPreview = computed(() => selectedTagPreview.value.filter((item) => item.option?.discoverRecommended))
const restrictedTagPreview = computed(() => selectedTagPreview.value.filter((item) => item.option?.vipOnly || item.option?.adultOnly))
const hiddenTagPreview = computed(() => selectedTagPreview.value.filter((item) => item.option && !item.option.discoverVisible && !item.option.detailVisible))
const customTagPreview = computed(() => selectedTagPreview.value.filter((item) => !item.option))

function normalizeWorldNameList(input) {
  const source = Array.isArray(input) ? input : []
  const ordered = []
  const seen = {}
  source.forEach((item) => {
    const fileId = String(item || '').trim()
    if (!fileId || seen[fileId]) return
    seen[fileId] = true
    ordered.push(fileId)
  })
  return ordered.slice(0, 10)
}

const characterWorldbookOptions = computed(() => {
  const merged = []
  const seen = {}
  ;(worldbookOptions.value || []).forEach((item) => {
    const fileId = String(item?.fileId || item?.value || '').trim()
    if (!fileId || seen[fileId]) return
    seen[fileId] = true
    merged.push({
      value: fileId,
      label: String(item?.name || item?.label || fileId).trim() || fileId,
      missing: false
    })
  })
  normalizeWorldNameList(form.value.stWorldNames).forEach((fileId) => {
    if (seen[fileId]) return
    seen[fileId] = true
    merged.push({
      value: fileId,
      label: fileId + '（已失效）',
      missing: true
    })
  })
  return merged
})

const lorebookRuntimeHint = computed(() => {
  const embedded = lorebookCount(form.value.lorebookSummary, 'embedded')
  const explicit = normalizeWorldNameList(form.value.stWorldNames)
  if (!embedded && !explicit.length) {
    return '没有卡内世界书，也没有固定绑定 ST worldbooks。'
  }
  if (embedded && explicit.length) {
    return '这张卡有卡内世界书，但当前固定绑定了 ST worldbooks；聊天会优先使用固定绑定，卡内世界书可能不会参与。'
  }
  if (embedded) {
    return '聊天未固定绑定 ST worldbooks 时，会按 ST 原逻辑使用角色卡内世界书。'
  }
  return '当前使用固定绑定的 ST worldbooks。'
})

function lorebookCount(summary, key) {
  const value = Number(summary?.[key] || 0)
  return Number.isFinite(value) && value > 0 ? value : 0
}

function lorebookTotal(summary) {
  return lorebookCount(summary, 'total')
}

function lorebookSummaryText(summary) {
  const embedded = lorebookCount(summary, 'embedded')
  const manual = lorebookCount(summary, 'manual')
  if (!embedded && !manual) return '无'
  const parts = []
  if (embedded) parts.push(`卡内 ${embedded}`)
  if (manual) parts.push(`手工 ${manual}`)
  return parts.join(' + ')
}

function isEmbeddedLorebook(row) {
  return row?.source === 'embedded_character_book'
}

function displayImgUrl(u) {
  if (!u) return ''
  const s = String(u).trim()
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('//')) {
    return s
  }
  if (s.startsWith('/uploads/')) {
    return useProxyUploadsInAdmin ? baseApi + s : s
  }
  if (s.startsWith('/')) {
    return baseApi + s
  }
  return baseApi + '/api/v1/st-assets/characters/' + encodeURIComponent(s)
}

function displayThumbUrl(u, preset = 'avatar') {
  if (!u) return ''
  const s = String(u).trim()
  const safePreset = ['avatar', 'card', 'detail'].includes(String(preset || '').trim()) ? String(preset).trim() : 'avatar'
  const thumbPrefix = '/api/v1/st-assets/characters-thumb/'
  const originalPrefix = '/api/v1/st-assets/characters/'
  if (s.startsWith('/uploads/') || s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('//')) {
    return displayImgUrl(s)
  }
  if (s.startsWith(originalPrefix)) {
    return baseApi + thumbPrefix + encodeURIComponent(s.substring(originalPrefix.length)) + '?preset=' + safePreset
  }
  if (s.startsWith('/')) {
    return displayImgUrl(s)
  }
  return baseApi + thumbPrefix + encodeURIComponent(s) + '?preset=' + safePreset
}

function beforeCharacterImg(file) {
  const allow = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allow.includes(file.type)) {
    proxy.$modal.msgError('仅支持 jpg / png / webp / gif')
    return false
  }
  const maxMb = 5
  if (file.size / 1024 / 1024 > maxMb) {
    proxy.$modal.msgError('图片不能超过 ' + maxMb + 'MB')
    return false
  }
  return true
}

function parseGreetingsJson(str) {
  if (!str || !String(str).trim()) {
    return ['']
  }
  try {
    const a = JSON.parse(str)
    if (Array.isArray(a)) {
      const texts = a.filter((x) => typeof x === 'string').map((x) => x)
      return texts.length ? [...texts] : ['']
    }
  } catch (e) {
    /* ignore */
  }
  return ['']
}

function parseTagsJson(str) {
  if (!str || !String(str).trim()) {
    return []
  }
  try {
    const a = JSON.parse(str)
    if (Array.isArray(a)) {
      return a.filter((x) => typeof x === 'string').map((x) => x.trim()).filter(Boolean)
    }
  } catch (e) {
    /* ignore */
  }
  return []
}

function loadTagOptions() {
  listTagOptions()
    .then((res) => {
      tagOptions.value = res.data || []
    })
    .catch(() => {
      tagOptions.value = []
    })
}

function loadCharacterWorldbookOptions() {
  worldbookOptionsLoading.value = true
  worldbookOptionsError.value = ''
  listCharacterWorldbookOptions()
    .then((res) => {
      worldbookOptions.value = Array.isArray(res.data) ? res.data : []
    })
    .catch((e) => {
      worldbookOptions.value = []
      worldbookOptionsError.value = jiugaiRequestErrorMessage(e, '加载 ST worldbooks 失败')
    })
    .finally(() => {
      worldbookOptionsLoading.value = false
    })
}

function loadFormLorebookEntries(characterId) {
  if (!characterId) {
    characterLorebookRows.value = []
    form.value.lorebookSummary = summarizeLoadedLorebookRows([], 0)
    return Promise.resolve()
  }
  characterLorebookLoading.value = true
  return listLorebook({ characterId, pageNum: 1, pageSize: 200 })
    .then((res) => {
      characterLorebookRows.value = res.rows || []
      form.value.lorebookSummary = summarizeLoadedLorebookRows(characterLorebookRows.value, res.total)
    })
    .catch((e) => {
      characterLorebookRows.value = []
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色世界书失败'))
    })
    .finally(() => {
      characterLorebookLoading.value = false
    })
}

function summarizeLoadedLorebookRows(rows, total) {
  const sourceRows = Array.isArray(rows) ? rows : []
  const embedded = sourceRows.filter((row) => isEmbeddedLorebook(row)).length
  const manual = sourceRows.filter((row) => !isEmbeddedLorebook(row)).length
  const enabled = sourceRows.filter((row) => row?.enabled).length
  const safeTotal = Number(total || 0)
  return {
    total: Number.isFinite(safeTotal) && safeTotal > sourceRows.length ? safeTotal : sourceRows.length,
    embedded,
    manual,
    enabled,
    hasEmbedded: embedded > 0,
    hasManual: manual > 0
  }
}

function openCharacterLorebook(row) {
  const id = row?.id
  if (!id) {
    proxy.$modal.msgWarning('请先保存角色卡')
    return
  }
  const name = row?.name ? `角色世界书：${row.name}` : '角色世界书'
  sessionStorage.setItem('jgLorebookCharacterId', String(id))
  proxy.$tab.openPage(name, '/jiugai/content/lorebook', { characterId: id })
}

function addGreeting() {
  greetingLines.value.push('')
}

function removeGreeting(idx) {
  if (greetingLines.value.length <= 1) return
  greetingLines.value.splice(idx, 1)
}

function beforePngImport(file) {
  const fileName = String(file?.name || file?.raw?.name || '').toLowerCase()
  const mime = String(file?.type || '').toLowerCase()
  if (!fileName.endsWith('.png') && mime !== 'image/png') {
    proxy.$modal.msgError('请选择 .png 格式的 ST 角色卡')
    return false
  }
  if (Number(file?.size || 0) > SILLY_TAVERN_PNG_MAX_BYTES) {
    proxy.$modal.msgError('PNG 不能超过 ' + SILLY_TAVERN_PNG_MAX_MB + 'MB')
    return false
  }
  return true
}

function httpRequestPngImport(options) {
  if (!beforePngImport(options.file)) {
    return
  }
  importSillyTavernPng(options.file)
    .then((res) => {
      const importedId = res?.data?.id
      const importedTags = Array.isArray(res?.data?.importedTags) ? res.data.importedTags.length : 0
      const importedWorlds = Array.isArray(res?.data?.importedWorldNames) ? res.data.importedWorldNames.length : 0
      const importedLorebookEntries = Number(res?.data?.importedLorebookEntries || 0)
      const summary = res?.msg || `PNG 导入成功${importedTags > 0 ? `，标签 ${importedTags}` : ''}${importedWorlds > 0 ? `，ST world ${importedWorlds}` : ''}${importedLorebookEntries > 0 ? `，卡内世界书 ${importedLorebookEntries} 条` : '，未检测到卡内世界书'}`
      proxy.$modal.msgSuccess(summary)
      openImport.value = false
      importTab.value = 'png'
      queryParams.value.scope = 'system'
      queryParams.value.name = undefined
      queryParams.value.ownerClientUid = undefined
      queryParams.value.reviewStatus = undefined
      queryParams.value.pageNum = 1
      return getList().then(() => {
        if (importedId) {
          handleUpdate({ id: importedId })
        }
        options.onSuccess?.(res)
      })
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, 'PNG 导入失败'))
      options.onError?.(e)
    })
}

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const openImport = ref(false)

const emptyForm = () => ({
  id: undefined,
  name: '',
  tagline: '',
  bio: '',
  persona: '',
  scenario: '',
  firstMessage: '',
  alternateGreetingsJson: '[]',
  mesExample: '',
  systemPrompt: '',
  postHistoryInstructions: '',
  creatorNotes: '',
  stExtraJson: '',
  avatarUrl: '',
  coverUrl: '',
  chatBackgroundUrl: '',
  stWorldNames: [],
  occupationLabel: '',
  tagsJson: '[]',
  vipOnly: false,
  unlockedDefault: true,
  clientVisible: true,
  previewBlurVipLevel: 0,
  likeCount: 0,
  dislikeCount: 0,
  creatorName: '',
  creatorHandle: '',
  tokenDisplay: '',
  gameplayType: '',
  chatModesJson: '',
  sortOrder: 0,
  lorebookSummary: { total: 0, embedded: 0, manual: 0, enabled: 0 }
})

const data = reactive({
  form: emptyForm(),
    queryParams: { pageNum: 1, pageSize: 10, name: undefined, scope: 'system', ownerClientUid: undefined, reviewStatus: undefined },
  rules: {
    name: [{ required: true, message: '名称不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

watch(
  [greetingLines, tagList],
  () => {
    const trimmed = greetingLines.value.map((s) => (s || '').trim()).filter(Boolean)
    form.value.alternateGreetingsJson = JSON.stringify(trimmed)
    form.value.tagsJson = JSON.stringify(tagList.value.filter(Boolean))
  },
  { deep: true }
)

function onAvatarUploadSuccess(res) {
  if (res.code === 200 && res.fileName) {
    form.value.avatarUrl = res.fileName
    proxy.$modal.msgSuccess('头像已上传，请点确定保存角色卡')
  } else {
    proxy.$modal.msgError(res.msg || '上传失败')
  }
}

function onCoverUploadSuccess(res) {
  if (res.code === 200 && res.fileName) {
    form.value.coverUrl = res.fileName
    proxy.$modal.msgSuccess('封面已上传，请点确定保存角色卡')
  } else {
    proxy.$modal.msgError(res.msg || '上传失败')
  }
}

function onBackgroundUploadSuccess(res) {
  if (res.code === 200 && res.fileName) {
    form.value.chatBackgroundUrl = res.fileName
    proxy.$modal.msgSuccess('聊天背景已上传，请点确定保存角色卡')
  } else {
    proxy.$modal.msgError(res.msg || '上传失败')
  }
}

function resetUserCreatedStats() {
  userCreatedStats.value = { totalUserCreated: 0, ownerCount: 0, topOwners: [] }
}

function loadUserCreatedStats() {
  if (queryParams.value.scope !== 'user') {
    resetUserCreatedStats()
    return
  }
  getUserCreatedCharacterStats(12)
    .then((res) => {
      userCreatedStats.value = res.data || { totalUserCreated: 0, ownerCount: 0, topOwners: [] }
    })
    .catch(() => {
      resetUserCreatedStats()
    })
}

function getList() {
  loading.value = true
  return listCharacter(queryParams.value)
    .then((res) => {
      dataList.value = res.rows
      total.value = res.total
      loadUserCreatedStats()
      loading.value = false
    })
    .catch((e) => {
      loading.value = false
      resetUserCreatedStats()
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色列表失败'))
    })
}

function handleScopeChange() {
  queryParams.value.pageNum = 1
  if (queryParams.value.scope === 'system') {
    queryParams.value.ownerClientUid = undefined
  }
  getList()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.name = undefined
  queryParams.value.ownerClientUid = undefined
  queryParams.value.reviewStatus = undefined
  handleQuery()
}

function reviewStatusLabel(status) {
  if (status === 'PENDING') return uiText.reviewPending
  if (status === 'REJECTED') return uiText.reviewRejected
  if (status === 'APPROVED') return uiText.reviewApproved
  return uiText.reviewAll
}

function reviewStatusTagType(status) {
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  if (status === 'APPROVED') return 'success'
  return 'info'
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function resolveReviewIds(row) {
  if (row?.id) {
    return [row.id]
  }
  return ids.value.slice()
}

function reset() {
  form.value = emptyForm()
  form.value.stWorldNames = []
  characterLorebookRows.value = []
  greetingLines.value = ['']
  tagList.value = []
  editTab.value = 'base'
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增角色卡'
}

function handleUpdate(row) {
  reset()
  const id = row?.id || ids.value[0]
  if (!id) return
  getCharacter(id)
    .then((res) => {
      form.value = { ...emptyForm(), ...res.data }
      form.value.stWorldNames = normalizeWorldNameList(form.value.stWorldNames)
      form.value.clientVisible = form.value.clientVisible !== false
      form.value.previewBlurVipLevel = normalizePreviewBlurLevel(form.value.previewBlurVipLevel)
      greetingLines.value = parseGreetingsJson(form.value.alternateGreetingsJson)
      tagList.value = parseTagsJson(form.value.tagsJson)
      open.value = true
      title.value = '修改角色卡'
      loadFormLorebookEntries(id)
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取角色失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const api = form.value.id ? updateCharacter : addCharacter
    const rawWorldNames = normalizeWorldNameList(form.value.stWorldNames)
    const missingWorldNames = rawWorldNames.filter((fileId) =>
      characterWorldbookOptions.value.some((item) => item.value === fileId && item.missing)
    )
    const safeWorldNames = rawWorldNames.filter((fileId) => !missingWorldNames.includes(fileId))
    if (missingWorldNames.length) {
      proxy.$modal.msgWarning('已自动移除失效世界书：' + missingWorldNames.join('、'))
    }
    api({
      ...form.value,
      clientVisible: form.value.clientVisible !== false,
      previewBlurVipLevel: normalizePreviewBlurLevel(form.value.previewBlurVipLevel),
      stWorldNames: safeWorldNames
    })
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
    .confirm('是否确认删除？本地角色会被软删除。')
    .then(() =>
      ElMessageBox.confirm(
        '是否同步清理 ST 角色文件？选择“取消”则只做本地软删除，不会删除 ST 聊天记录。',
        '同步删除 ST 文件',
        {
          confirmButtonText: '同步删除 ST 文件',
          cancelButtonText: '仅本地删除',
          distinguishCancelAndClose: true,
          type: 'warning'
        }
      )
        .then(() => ({ syncStFile: true }))
        .catch((action) => {
          if (action === 'cancel') {
            return { syncStFile: false }
          }
          throw action
        })
    )
    .then((decision) => delCharacter(delIds, decision))
    .then((res) => {
        getList()
        proxy.$modal.msgSuccess(res?.msg || '删除成功')
      })
      .catch((e) => {
        if (isMessageBoxCancelled(e)) return
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除失败'))
      })
}

function handleApprove(row) {
  const reviewIds = resolveReviewIds(row)
  if (!reviewIds.length) {
    proxy.$modal.msgWarning('请先选择角色卡')
    return
  }
  const payload = { ids: reviewIds, reviewStatus: 'APPROVED', reviewReason: '' }
  if (reviewIds.length === 1) {
    payload.id = reviewIds[0]
  }
  reviewCharacterApi(payload)
    .then(() => {
      proxy.$modal.msgSuccess(reviewIds.length > 1 ? '已批量通过审核' : '审核已通过')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '审核通过失败'))
    })
}

function handleReject(row) {
  const reviewIds = resolveReviewIds(row)
  if (!reviewIds.length) {
    proxy.$modal.msgWarning('请先选择角色卡')
    return
  }
  proxy.$modal
    .prompt('请输入驳回原因')
    .then(({ value }) =>
      {
        const payload = {
          ids: reviewIds,
          reviewStatus: 'REJECTED',
          reviewReason: String(value || '').trim()
        }
        if (reviewIds.length === 1) {
          payload.id = reviewIds[0]
        }
        return reviewCharacterApi(payload)
      }
    )
    .then(() => {
      proxy.$modal.msgSuccess(reviewIds.length > 1 ? '已批量驳回并发送站内消息' : '已驳回并发送站内消息')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '驳回失败'))
    })
}

function openReviewLog() {
  proxy.$tab.openPage('角色审核台账', '/jiugai/content/reviewlog')
}

function batchEvictLore() {
  if (!ids.value.length) {
    proxy.$modal.msgWarning('请先勾选角色')
    return
  }
  proxy.$modal
    .confirm('确定使选中角色的世界书缓存失效？下次对话将重新从数据库加载世界书。')
    .then(() => batchEvictCharacterLoreCache(ids.value))
    .then(() => {
      proxy.$modal.msgSuccess('已失效世界书缓存')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '操作失败'))
    })
}

getList()
loadTagOptions()
loadCharacterWorldbookOptions()
</script>

<style scoped>
.jg-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.jg-tabs :deep(.el-tabs__content) {
  padding: 12px 8px 4px;
}
.jg-pane-alert {
  margin-bottom: 14px;
}
.jg-field-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.jg-greet-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.jg-greet-row .el-input {
  flex: 1;
}
.jg-tag-preview {
  margin-top: 12px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98) 0%, rgba(241, 245, 249, 0.96) 100%);
  border: 1px solid var(--el-border-color-lighter);
}
.jg-tag-preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.jg-tag-preview-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.jg-tag-preview-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.jg-tag-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.jg-tag-preview-card {
  min-height: 104px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.16);
}
.jg-tag-preview-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.jg-tag-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.jg-tag-preview-empty {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}
.jg-tag-preview-tip {
  margin-top: 12px;
  margin-bottom: 0;
}
.jg-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.jg-stat-card {
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
}
.jg-stat-card--wide {
  grid-column: span 1;
}
.jg-stat-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-color-primary);
}
.jg-stat-label {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.jg-owner-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.jg-owner-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.jg-owner-lines {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.jg-owner-id {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.jg-owner-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
.jg-owner-empty {
  margin-top: 12px;
  color: var(--el-text-color-placeholder);
}
.jg-av {
  width: 44px;
  height: 44px;
  border-radius: 8px;
}
.jg-av-ph {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.jg-img-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.jg-img-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.jg-img-lab {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.jg-prev {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
}
.jg-prev-wide {
  width: 120px;
  height: 72px;
}
.jg-img-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.jg-import-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
  line-height: 1.55;
}
.jg-upload-btns {
  margin-top: 8px;
}
.jg-img-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.jg-lorebook-head {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 120px)) 1fr;
  gap: 12px;
  align-items: stretch;
  margin-bottom: 14px;
}
.jg-lorebook-stat {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.jg-lorebook-stat-num {
  display: block;
  margin-bottom: 4px;
  color: var(--el-color-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.jg-lorebook-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
.jg-png-upload {
  width: 100%;
}
.jg-import-json-actions {
  margin-top: 12px;
}
@media (max-width: 1280px) {
  .jg-tag-preview-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .jg-lorebook-head {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .jg-lorebook-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }
}
</style>
