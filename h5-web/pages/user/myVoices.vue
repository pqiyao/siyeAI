<template>
  <view class="page">
    <image class="page-bg" src="/static/login.png" mode="aspectFill" />
    <tavern-nav-bar title="声线工作室" mode="dark" @back="goBack" />

    <scroll-view v-if="featureEnabled" scroll-y class="body">
      <view class="studio-stage">
        <view class="stage-main">
          <view class="sound-disc">
            <view class="disc-ring disc-ring--outer"></view>
            <view class="disc-ring disc-ring--inner"></view>
            <image class="studio-emblem" src="/static/logo.png" mode="aspectFill"></image>
          </view>
          <view class="stage-copy">
            <text class="stage-kicker">VOICE LAB</text>
            <text class="stage-title">收藏你的专属声线</text>
            <text class="stage-desc">克隆、试听和管理都只使用你的硅基流动 API。</text>
          </view>
        </view>
        <view class="stage-wave">
          <view v-for="bar in 18" :key="bar" class="wave-bar" :class="'wave-bar--' + ((bar % 6) + 1)"></view>
        </view>
        <view class="account-ribbon">
          <view class="account-state">
            <view class="provider-dot" :class="{ online: providerStatus.connected }"></view>
            <view class="account-copy">
              <text class="account-title">{{ providerStatusText }}</text>
              <text class="account-sub">{{ runtimeLabel }}</text>
            </view>
          </view>
          <view class="round-action round-action--light" :class="{ disabled: providerStatusLoading }" aria-label="刷新账号状态" @tap="refreshProviderStatus(true)">
            <u-icon name="reload" color="#164b55" size="30"></u-icon>
          </view>
        </view>
        <view class="stage-foot">
          <text>{{ scopeLabel }}</text>
          <text>{{ overview.used || 0 }} / {{ overview.limit || 0 }} 个音色</text>
        </view>
      </view>

      <view class="mode-dock">
        <view v-for="item in studioSections" :key="item.key" class="mode-item" :class="{ active: activeSection === item.key }" @tap="activeSection = item.key">
          <view class="mode-icon"><u-icon :name="item.icon" :color="activeSection === item.key ? '#315f72' : '#647b8b'" size="30"></u-icon></view>
          <text>{{ item.label }}</text>
        </view>
      </view>

      <view v-if="activeSection === 'voices'" class="workspace workspace--library">
        <view class="section-heading">
          <view>
            <text class="section-kicker">MY COLLECTION</text>
            <text class="section-title">我的音色</text>
          </view>
          <view class="section-tools">
            <text v-if="boundVoiceId" class="text-action text-action--warm" @tap="clearBinding">解除绑定</text>
            <view class="round-action" aria-label="刷新我的音色" @tap="load"><u-icon name="reload" color="#286b72" size="28"></u-icon></view>
          </view>
        </view>

        <view class="sync-arc">
          <view class="sync-icon"><u-icon name="download" color="#ffffff" size="38"></u-icon></view>
          <view class="sync-copy">
            <text class="sync-title">硅基流动音色库</text>
            <text class="sync-desc">把账号里已有的自定义音色同步到这里</text>
          </view>
          <button v-if="isSiliconFlowByok" class="sync-button" :disabled="syncingProvider" @tap="syncProviderVoices">{{ syncingProvider ? '同步中' : '同步' }}</button>
          <button v-else class="sync-button sync-button--setup" @tap="openAiSettings">配置</button>
        </view>

        <view v-if="providerVoicesVisible" class="provider-drawer">
          <view v-if="syncingProvider" class="empty-state empty-state--small"><text>正在读取供应商音色…</text></view>
          <view v-else-if="!providerVoices.length" class="empty-state empty-state--small"><text>当前账号没有可同步音色</text></view>
          <block v-else>
            <view v-for="item in providerVoices" :key="item.voiceUri" class="provider-row">
              <view class="mini-disc"><u-icon name="volume-up" color="#2e7777" size="24"></u-icon></view>
              <view class="voice-copy">
                <text class="voice-name">{{ item.displayName }}</text>
                <text class="voice-meta">{{ item.modelName || '当前 TTS 模型' }}</text>
              </view>
              <text v-if="item.imported" class="imported">已收藏</text>
              <button v-else class="compact-action" :disabled="!overview.canCreate || importingUri === item.voiceUri" @tap="importProviderVoice(item)">{{ importingUri === item.voiceUri ? '导入中' : '收藏' }}</button>
            </view>
          </block>
        </view>

        <view v-if="loading" class="empty-state"><view class="empty-pulse"></view><text>正在整理音色库…</text></view>
        <view v-else-if="!voices.length" class="empty-state">
          <view class="empty-record"><u-icon name="volume-up" color="#7f989f" size="42"></u-icon></view>
          <text class="empty-title">还没有收藏声线</text>
          <text class="empty-desc">去“创建音色”录下第一段声音，或同步硅基流动已有音色。</text>
        </view>
        <view v-else class="voice-orbit-list">
          <view v-for="(voice, index) in voices" :key="voice.id" class="voice-orbit" :class="{ 'voice-orbit--alt': index % 2 === 1 }">
            <view class="voice-preview" :class="{ disabled: !voice.available }" aria-label="试听音色" @tap="openPreview(voice)">
              <view class="voice-groove"></view>
              <u-icon :name="previewPlaying && previewVoice && Number(previewVoice.id) === Number(voice.id) ? 'pause' : 'play-right'" :color="voice.available ? '#ffffff' : '#bac5c8'" size="27"></u-icon>
            </view>
            <view class="voice-copy">
              <text class="voice-name">{{ voice.displayName }}</text>
              <text class="voice-meta">{{ voice.statusText }}</text>
            </view>
            <view class="voice-actions">
              <text v-if="voice.available" class="bind" :class="{ active: Number(boundVoiceId) === Number(voice.id) }" @tap="bindVoice(voice)">{{ Number(boundVoiceId) === Number(voice.id) ? scopeBoundText : scopeActionText }}</text>
              <view class="round-action round-action--danger" aria-label="删除音色" @tap="removeVoice(voice)"><u-icon name="trash" color="#b85e6b" size="26"></u-icon></view>
            </view>
          </view>
        </view>
      </view>

      <view v-else-if="activeSection === 'create'" class="workspace workspace--create">
        <view class="section-heading">
          <view>
            <text class="section-kicker">RECORD A VOICE</text>
            <text class="section-title">创建新音色</text>
          </view>
          <view class="create-step">5–60s</view>
        </view>

        <view v-if="!loading && !overview.canCreate" class="notice-ribbon">
          <view class="notice-sign"><u-icon name="info-circle" color="#9a5260" size="31"></u-icon></view>
          <view class="notice-copy"><text>暂时无法创建</text><text>{{ overview.denyReason || '请先完成硅基流动 BYOK 配置，或稍后刷新页面。' }}</text></view>
          <view v-if="needsAiSetup || !providerState" class="round-action" aria-label="配置我的 API" @tap="openAiSettings"><u-icon name="arrow-right" color="#8d4e5b" size="27"></u-icon></view>
        </view>

        <view v-if="overview.canCreate" class="creation-flow">
          <view class="field-group">
            <text class="field-no">01</text>
            <view class="field-body"><text class="field-label">给它一个名字</text><input v-model="form.displayName" class="studio-input" maxlength="64" placeholder="例如：温柔女声" /></view>
          </view>
          <view class="field-group field-group--wide">
            <text class="field-no">02</text>
            <view class="field-body"><text class="field-label">准确写下参考台词</text><textarea v-model="form.sampleText" class="studio-textarea" maxlength="255" placeholder="必须与音频实际朗读内容一致" /></view>
          </view>
          <view class="field-group field-group--audio">
            <text class="field-no">03</text>
            <view class="field-body">
              <text class="field-label">加入清晰的人声音频</text>
              <view class="source-actions">
                <view class="source-action" :class="{ disabled: recording }" @tap="chooseAudio"><u-icon name="attach" color="#247878" size="31"></u-icon><text>选择音频</text></view>
                <view class="source-action source-action--record" :class="{ recording: recording }" @tap="toggleRecording"><u-icon :name="recording ? 'pause' : 'mic'" :color="recording ? '#ffffff' : '#a4515d'" size="31"></u-icon><text>{{ recording ? '停止 ' + recordSeconds + 's' : '现在录制' }}</text></view>
              </view>
              <view v-if="fileName" class="selected-audio"><view class="selected-wave"><view v-for="bar in 9" :key="bar" class="selected-bar" :class="'selected-bar--' + ((bar % 4) + 1)"></view></view><view class="file-copy"><text class="file-name">{{ fileName }}</text><text class="file-meta">{{ audioDurationText }}</text></view><view class="round-action" aria-label="移除音频" @tap="clearSelectedAudio"><u-icon name="close" color="#667c84" size="25"></u-icon></view></view>
            </view>
          </view>
          <button class="create-button" :disabled="!canSubmitCreation" @tap="createVoice"><u-icon name="mic" color="#ffffff" size="30"></u-icon><text>{{ creating ? '正在创建声线…' : '使用我的 Key 创建' }}</text></button>
          <text class="creation-note">仅可使用本人声音或已取得明确授权的声音。官方平台模式不提供私有音色克隆。</text>
        </view>
      </view>

      <view v-else class="workspace workspace--resources">
        <view class="section-heading">
          <view><text class="section-kicker">VOICE RESOURCES</text><text class="section-title">音色资源站</text></view>
          <view class="resource-spark"><u-icon name="link" color="#ffffff" size="28"></u-icon></view>
        </view>
        <text class="resource-intro">这里提供第三方音色资源和模型 API 服务入口。打开外部站点时不会携带你的 API Key。</text>
        <view class="resource-flow">
          <view v-for="(item, index) in voiceResourceLinks" :key="item.url" class="resource-island" :class="['resource-island--' + item.tone, { 'resource-island--featured': index === 0 }]" @tap="openExternalVoiceResource(item)">
            <view class="resource-index">0{{ index + 1 }}</view>
            <view class="resource-symbol"><u-icon :name="item.icon" color="#4f93a3" size="34"></u-icon></view>
            <view class="resource-copy"><text class="resource-name">{{ item.name }}</text><text class="resource-desc">{{ item.description }}</text><text class="resource-domain">{{ item.domain }}</text></view>
            <view class="resource-arrow"><u-icon name="arrow-right" color="#ffffff" size="26"></u-icon></view>
          </view>
        </view>
        <view class="license-wave"><u-icon name="info-circle" color="#6e7f87" size="26"></u-icon><text>第三方站点与本应用相互独立，使用前请自行核对服务内容、价格、授权范围、使用条款和隐私规则。</text></view>
      </view>
      <view class="bottom-space"></view>
    </scroll-view>

    <view v-else-if="featureEnabled === false" class="disabled-view">
      <view class="empty-record empty-record--large"><u-icon name="volume-off" color="#81969d" size="52"></u-icon></view>
      <text class="empty-title">语音功能暂未开放</text>
      <text class="empty-desc">当前无法创建、绑定或播放自建音色。</text>
    </view>

    <view v-if="previewVoice" class="preview-mask" @tap="closePreview">
      <view class="preview-sheet" @tap.stop>
        <view class="preview-handle"></view>
        <view class="preview-head">
          <view class="preview-record"><u-icon name="volume-up" color="#ffffff" size="31"></u-icon></view>
          <view class="preview-copy"><text class="preview-title">{{ previewVoice.displayName }}</text><text class="voice-meta">试听使用你的硅基流动额度</text></view>
          <view class="round-action" aria-label="关闭试听" @tap="closePreview"><u-icon name="close" color="#5f7680" size="30"></u-icon></view>
        </view>
        <textarea v-model="previewText" class="studio-textarea preview-textarea" maxlength="160" placeholder="输入一小段试听文字" />
        <view class="preview-footer"><text class="preview-count">{{ previewText.length }} / 160</text><button class="preview-button" :disabled="previewLoading || !previewText.trim()" @tap="submitPreview">{{ previewLoading ? '生成中…' : (previewPlaying ? '停止播放' : '生成并播放') }}</button></view>
      </view>
    </view>
  </view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

export default {
  components: { TavernNavBar },
  data() {
    return {
      loading: false,
      creating: false,
      activeSection: 'voices',
      studioSections: [
        { key: 'voices', label: '我的音色', icon: 'volume-up' },
        { key: 'create', label: '创建音色', icon: 'mic' },
        { key: 'resources', label: '音色资源', icon: 'grid' }
      ],
      voiceResourceLinks: [
        { name: '共享音色广场', description: 'SiliconFlow 音色管理与共享参考声线', domain: 'voice.gbkgov.cn', url: 'https://voice.gbkgov.cn/', icon: 'volume-up', iconColor: '#ffffff', tone: 'night' },
        { name: 'GPT API 中转站', description: '第三方模型 API 接入服务', domain: 'api.zhouz.online', url: 'https://api.zhouz.online/register?aff=QQVK7ZZ7H66U', icon: 'link', iconColor: '#286b72', tone: 'mint' }
      ],
      createRequestId: '',
      overview: { used: 0, limit: 0, canCreate: false, denyReason: '', globalVoiceId: 0 },
      providerState: null,
      providerStatus: { connected: false, modelName: '' },
      providerStatusLoading: false,
      providerVoices: [],
      providerVoicesVisible: false,
      syncingProvider: false,
      importingUri: '',
      importRequestIds: {},
      voices: [],
      scopeType: 'GLOBAL',
      characterId: 0,
      memberId: 0,
      boundVoiceId: 0,
      selectedFile: '',
      fileName: '',
      audioDurationMs: 0,
      recording: false,
      recordSeconds: 0,
      recordStartedAt: 0,
      recordTimer: null,
      nativeRecorder: null,
      nativeRecorderReady: false,
      browserRecorder: null,
      browserStream: null,
      browserChunks: [],
      discardRecordingResult: false,
      pageActive: false,
      featureEnabled: null,
      form: { displayName: '', sampleText: '' },
      previewVoice: null,
      previewText: '你好，很高兴在四叶酒馆与你见面。',
      previewLoading: false,
      previewPlaying: false,
      previewAudioDataUrl: '',
      previewGeneratedText: '',
      previewPlayer: null
    };
  },
  computed: {
    canSubmitCreation() {
      return this.overview.canCreate === true
        && !this.creating
        && !this.recording
        && !!this.selectedFile
        && !!String(this.form.displayName || '').trim()
        && !!String(this.form.sampleText || '').trim();
    },
    needsAiSetup() {
      if (this.overview.canCreate === true) return false;
      if (this.overview.featureEnabled === false) return false;
      const limit = Math.max(0, Number(this.overview.limit) || 0);
      const used = Math.max(0, Number(this.overview.used) || 0);
      if (limit <= 0 || used >= limit) return false;
      if (!this.providerState || typeof this.providerState !== 'object') return false;
      const state = this.providerState;
      if (String(state.mode || '').trim() !== 'custom') return true;
      const source = String(
        state.effectiveTtsProviderSource || state.ttsProviderSource || state.providerSource || ''
      ).trim().toLowerCase();
      if (source !== 'siliconflow') return true;
      return state.effectiveTtsApiKeyConfigured !== true || !String(state.ttsModelName || '').trim();
    },
    isSiliconFlowByok() {
      if (!this.providerState || typeof this.providerState !== 'object') return false;
      const state = this.providerState;
      const source = String(
        state.effectiveTtsProviderSource || state.ttsProviderSource || state.providerSource || ''
      ).trim().toLowerCase();
      return String(state.mode || '').trim() === 'custom'
        && source === 'siliconflow'
        && state.effectiveTtsApiKeyConfigured === true
        && !!String(state.ttsModelName || '').trim();
    },
    providerStatusText() {
      if (this.providerStatusLoading) return '正在连接';
      if (this.providerStatus.connected) return 'BYOK 已连接';
      return this.isSiliconFlowByok ? '连接待刷新' : '尚未配置 BYOK';
    },
    runtimeLabel() {
      if (!this.providerState || typeof this.providerState !== 'object') return '当前：正在读取配置';
      const state = this.providerState;
      if (String(state.mode || '').trim() !== 'custom') return '当前：官方 API';
      const source = String(
        state.effectiveTtsProviderSource || state.ttsProviderSource || state.providerSource || ''
      ).trim().toLowerCase();
      return source === 'siliconflow' ? '当前：我的硅基流动 API' : '当前：我的其他 TTS API';
    },
    scopeActionText() {
      if (this.scopeType === 'MEMBER') return '用于此成员';
      return this.scopeType === 'CHARACTER' ? '用于此角色' : '用于全局';
    },
    scopeBoundText() {
      if (this.scopeType === 'MEMBER') return '此成员使用中';
      return this.scopeType === 'CHARACTER' ? '此角色使用中' : '全局使用中';
    },
    scopeLabel() {
      if (this.scopeType === 'MEMBER') return '当前管理：群聊成员音色';
      return this.scopeType === 'CHARACTER' ? '当前管理：角色专属音色' : '当前管理：全局默认音色';
    },
    audioDurationText() {
      const seconds = Math.round(Number(this.audioDurationMs || 0) / 100) / 10;
      return seconds > 0 ? seconds + ' 秒 · 推荐 20 到 30 秒' : '将在创建时由平台校验真实时长';
    }
  },
  watch: {
    'form.displayName'() { this.resetCreateRequestId(); },
    'form.sampleText'() { this.resetCreateRequestId(); }
  },
  onLoad(options) {
    this.pageActive = true;
    const characterId = Math.max(0, Math.floor(Number(options && options.characterId) || 0));
    const memberId = Math.max(0, Math.floor(Number(options && options.memberId) || 0));
    this.characterId = characterId;
    this.memberId = characterId > 0 ? memberId : 0;
    this.scopeType = characterId > 0 && memberId > 0 ? 'MEMBER' : (characterId > 0 ? 'CHARACTER' : 'GLOBAL');
  },
  onShow() { this.checkFeatureAndLoad(); },
  onUnload() {
    this.pageActive = false;
    this.releaseRecording();
    this.releasePreviewPlayer();
  },
  methods: {
    clientUid() { return tavernApi.getClientUid(); },
    goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) }); },
    openAiSettings() { uni.navigateTo({ url: '/pages/user/aiSettings' }); },
    resetCreateRequestId(force) {
      if (force === true || !this.creating) this.createRequestId = '';
    },
    checkFeatureAndLoad() {
      this.providerVoices = [];
      this.providerVoicesVisible = false;
      tavernApi.fetchAppRuntimeConfig(true).then((config) => {
        this.featureEnabled = !(config && config.voiceFeatureEnabled === false);
        if (this.featureEnabled) this.load();
      }).catch(() => {
        this.featureEnabled = true;
        this.load();
      });
    },
    load() {
      if (this.featureEnabled === false) return Promise.resolve();
      this.loading = true;
      return Promise.all([
        tavernApi.getUserTtsVoices(this.clientUid()),
        tavernApi.getTavernUserAiProvider(this.clientUid()).catch(() => null)
      ]).then(([data, providerState]) => {
        const value = data || {};
        this.overview = Object.assign({}, this.overview, value);
        this.providerState = providerState && typeof providerState === 'object' ? providerState : null;
        this.voices = Array.isArray(value.voices) ? value.voices : [];
        return Promise.all([
          tavernApi.getUserTtsVoiceBinding(this.clientUid(), {
            scopeType: this.scopeType,
            characterId: this.characterId,
            memberId: this.memberId
          }),
          this.refreshProviderStatus(false)
        ]);
      }).then(([binding]) => {
        this.boundVoiceId = Number(binding && binding.voiceId) || 0;
      }).catch((error) => {
        uni.showToast({ title: String(error.message || '加载音色失败'), icon: 'none' });
      }).finally(() => { this.loading = false; });
    },
    refreshProviderStatus(showError) {
      if (!this.isSiliconFlowByok) {
        this.providerStatus = { connected: false, modelName: '' };
        if (showError) uni.showToast({ title: '请先在 AI 设置中启用硅基流动 BYOK', icon: 'none' });
        return Promise.resolve(null);
      }
      if (this.providerStatusLoading) return Promise.resolve(this.providerStatus);
      this.providerStatusLoading = true;
      return tavernApi.getUserTtsProviderStatus(this.clientUid()).then((status) => {
        this.providerStatus = Object.assign(
          { connected: false, modelName: '' },
          status || {}
        );
        return this.providerStatus;
      }).catch((error) => {
        this.providerStatus = { connected: false, modelName: '' };
        if (showError) uni.showToast({ title: String(error.message || '账号状态读取失败'), icon: 'none' });
        return null;
      }).finally(() => { this.providerStatusLoading = false; });
    },
    syncProviderVoices() {
      if (!this.isSiliconFlowByok || this.syncingProvider) return;
      this.providerVoicesVisible = true;
      this.syncingProvider = true;
      tavernApi.getUserTtsProviderVoices(this.clientUid()).then((rows) => {
        this.providerVoices = Array.isArray(rows) ? rows : [];
      }).catch((error) => {
        this.providerVoices = [];
        uni.showToast({ title: String(error.message || '音色同步失败'), icon: 'none' });
      }).finally(() => { this.syncingProvider = false; });
    },
    importProviderVoice(item) {
      if (!item || item.imported || this.importingUri || !this.overview.canCreate) return;
      const voiceUri = String(item.voiceUri || '').trim();
      if (!voiceUri) return;
      let requestId = this.importRequestIds[voiceUri];
      if (!requestId) {
        requestId = 'import-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
        this.$set(this.importRequestIds, voiceUri, requestId);
      }
      this.importingUri = voiceUri;
      tavernApi.importUserTtsProviderVoice(this.clientUid(), { requestId, voiceUri }).then((voice) => {
        item.imported = true;
        item.localVoiceId = Number(voice && voice.id) || 0;
        this.$delete(this.importRequestIds, voiceUri);
        uni.showToast({ title: '音色已导入', icon: 'none' });
        return this.load();
      }).catch((error) => {
        uni.showToast({ title: String(error.message || '音色导入失败'), icon: 'none' });
      }).finally(() => { this.importingUri = ''; });
    },
    copyVoiceResourceLink(link) {
      uni.setClipboardData({
        data: link,
        success: () => uni.showToast({ title: '链接已复制，请在浏览器打开', icon: 'none' }),
        fail: () => uni.showToast({ title: '无法打开链接', icon: 'none' })
      });
    },
    openExternalVoiceResource(item) {
      const link = String(item && item.url || '').trim();
      const allowed = this.voiceResourceLinks.some((entry) => entry && entry.url === link);
      if (!allowed || !/^https:\/\//i.test(link)) return;
      /* #ifdef APP-PLUS */
      try {
        if (typeof plus !== 'undefined' && plus.runtime && typeof plus.runtime.openURL === 'function') {
          plus.runtime.openURL(link, () => this.copyVoiceResourceLink(link));
          return;
        }
      } catch (e) {
        this.copyVoiceResourceLink(link);
        return;
      }
      /* #endif */
      /* #ifdef H5 */
      if (typeof window !== 'undefined' && typeof window.open === 'function') {
        const opened = window.open(link, '_blank', 'noopener,noreferrer');
        if (opened) opened.opener = null;
        else this.copyVoiceResourceLink(link);
        return;
      }
      /* #endif */
      this.copyVoiceResourceLink(link);
    },
    chooseAudio() {
      if (this.recording) return;
      if (typeof tavernApi.pickBrowserAudioFile === 'function' && typeof window !== 'undefined' && typeof plus === 'undefined') {
        tavernApi.pickBrowserAudioFile()
          .then((file) => this.acceptSelectedAudio(file, file && file.name))
          .catch((error) => {
            if (String(error && error.message || '') !== 'cancelled') {
              uni.showToast({ title: '音频文件选择失败', icon: 'none' });
            }
          });
        return;
      }
      const picker = typeof uni.chooseFile === 'function'
        ? uni.chooseFile.bind(uni)
        : (typeof uni.chooseMessageFile === 'function' ? uni.chooseMessageFile.bind(uni) : null);
      if (!picker) {
        uni.showToast({ title: '当前平台请直接录制参考音频', icon: 'none' });
        return;
      }
      picker({ count: 1, type: 'file', extension: ['mp3', 'wav', 'm4a', 'ogg'], success: (res) => {
        const file = res && res.tempFiles && res.tempFiles[0];
        if (!file) return;
        const source = file.path || file.tempFilePath || file;
        this.acceptSelectedAudio(source, file.name, file.size);
      } });
    },
    acceptSelectedAudio(source, name, declaredSize) {
      const size = Number(declaredSize || (source && source.size) || 0);
      if (!source) return;
      if (size > 8 * 1024 * 1024) {
        uni.showToast({ title: '音频不能超过 8MB', icon: 'none' });
        return;
      }
      this.clearSelectedAudio();
      this.selectedFile = source;
      this.fileName = String(name || (source && source.name) || '已选择参考音频');
      this.readAudioDuration(source);
    },
    clearSelectedAudio() {
      this.selectedFile = '';
      this.fileName = '';
      this.audioDurationMs = 0;
      this.resetCreateRequestId();
    },
    readAudioDuration(filePath) {
      this.audioDurationMs = 0;
      if (filePath && typeof filePath === 'object' && typeof URL !== 'undefined' && typeof Audio !== 'undefined') {
        const url = URL.createObjectURL(filePath);
        const audio = new Audio();
        const finish = () => {
          const duration = Math.round(Number(audio.duration || 0) * 1000);
          this.audioDurationMs = Number.isFinite(duration) ? Math.max(0, duration) : 0;
          try { URL.revokeObjectURL(url); } catch (e) {}
        };
        audio.preload = 'metadata';
        audio.onloadedmetadata = finish;
        audio.onerror = finish;
        audio.src = url;
        return;
      }
      if (typeof uni.createInnerAudioContext !== 'function') return;
      const audio = uni.createInnerAudioContext();
      let settled = false;
      const finish = () => {
        if (settled) return;
        settled = true;
        const duration = Math.round(Number(audio.duration || 0) * 1000);
        this.audioDurationMs = Number.isFinite(duration) ? Math.max(0, duration) : 0;
        try { audio.destroy(); } catch (e) {}
      };
      audio.autoplay = false;
      audio.onCanplay(() => setTimeout(finish, 180));
      audio.onError(finish);
      audio.src = filePath;
      setTimeout(finish, 3000);
    },
    toggleRecording() {
      if (this.creating) return;
      if (this.recording) {
        this.stopRecording();
        return;
      }
      if (this.canUseBrowserRecorder()) {
        this.startBrowserRecording();
        return;
      }
      this.startNativeRecording();
    },
    canUseBrowserRecorder() {
      return typeof plus === 'undefined' && typeof navigator !== 'undefined' && navigator.mediaDevices
        && typeof navigator.mediaDevices.getUserMedia === 'function'
        && typeof MediaRecorder !== 'undefined';
    },
    beginRecordTimer() {
      this.recordStartedAt = Date.now();
      this.recordSeconds = 0;
      if (this.recordTimer) clearInterval(this.recordTimer);
      this.recordTimer = setInterval(() => {
        this.recordSeconds = Math.min(60, Math.floor((Date.now() - this.recordStartedAt) / 1000));
        if (Date.now() - this.recordStartedAt >= 60000) this.stopRecording();
      }, 250);
    },
    finishRecordTimer() {
      const duration = this.recordStartedAt > 0 ? Date.now() - this.recordStartedAt : 0;
      if (this.recordTimer) clearInterval(this.recordTimer);
      this.recordTimer = null;
      this.recordStartedAt = 0;
      this.recordSeconds = 0;
      return Math.min(60000, Math.max(0, Math.round(duration)));
    },
    startBrowserRecording() {
      navigator.mediaDevices.getUserMedia({ audio: true }).then((stream) => {
        if (!this.pageActive) {
          if (stream && typeof stream.getTracks === 'function') {
            stream.getTracks().forEach((track) => { try { track.stop(); } catch (e) {} });
          }
          return;
        }
        const preferred = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm', 'audio/ogg'];
        const mimeType = preferred.find((item) => typeof MediaRecorder.isTypeSupported !== 'function' || MediaRecorder.isTypeSupported(item)) || '';
        const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
        this.browserStream = stream;
        this.browserRecorder = recorder;
        this.browserChunks = [];
        this.discardRecordingResult = false;
        recorder.ondataavailable = (event) => {
          if (event && event.data && event.data.size > 0) this.browserChunks.push(event.data);
        };
        recorder.onerror = () => {
          this.releaseBrowserRecorder();
          this.recording = false;
          this.finishRecordTimer();
          uni.showToast({ title: '录音失败，请重试', icon: 'none' });
        };
        recorder.onstop = () => {
          const duration = this.finishRecordTimer();
          const discarded = this.discardRecordingResult;
          const blob = new Blob(this.browserChunks, { type: recorder.mimeType || 'audio/webm' });
          this.releaseBrowserRecorder();
          this.recording = false;
          this.discardRecordingResult = false;
          if (discarded) return;
          if (duration < 5000) {
            uni.showToast({ title: '请至少录制 5 秒', icon: 'none' });
            return;
          }
          this.convertRecordingToWav(blob).then((file) => {
            this.acceptSelectedAudio(file, '录制的参考音频.wav', file.size);
            this.audioDurationMs = duration;
          }).catch(() => {
            uni.showToast({ title: '录音格式转换失败，请改为选择 WAV 或 MP3', icon: 'none' });
          });
        };
        recorder.start(250);
        this.recording = true;
        this.beginRecordTimer();
      }).catch(() => uni.showToast({ title: '请允许使用麦克风', icon: 'none' }));
    },
    getNativeRecorder() {
      if (!this.nativeRecorder && typeof uni.getRecorderManager === 'function') {
        this.nativeRecorder = uni.getRecorderManager();
      }
      if (this.nativeRecorder && !this.nativeRecorderReady) {
        this.nativeRecorder.onStop((res) => {
          const measured = this.finishRecordTimer();
          const duration = Math.max(0, Math.round(Number(res && res.duration) || measured));
          const discarded = this.discardRecordingResult;
          this.recording = false;
          this.discardRecordingResult = false;
          if (discarded) return;
          const path = res && res.tempFilePath;
          if (!path) {
            uni.showToast({ title: '没有获取到录音文件', icon: 'none' });
            return;
          }
          if (duration < 5000) {
            uni.showToast({ title: '请至少录制 5 秒', icon: 'none' });
            return;
          }
          this.acceptSelectedAudio(path, '录制的参考音频.mp3', res && res.fileSize);
          this.audioDurationMs = duration;
        });
        this.nativeRecorder.onError(() => {
          this.recording = false;
          this.finishRecordTimer();
          uni.showToast({ title: '录音失败，请检查麦克风权限', icon: 'none' });
        });
        this.nativeRecorderReady = true;
      }
      return this.nativeRecorder;
    },
    startNativeRecording() {
      const recorder = this.getNativeRecorder();
      if (!recorder) {
        uni.showToast({ title: '当前平台不支持录音', icon: 'none' });
        return;
      }
      this.ensureNativeMicrophonePermission().then((allowed) => {
        if (!allowed || !this.pageActive) return;
        try {
          this.discardRecordingResult = false;
          recorder.start({ duration: 60000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 96000, format: 'mp3' });
          this.recording = true;
          this.beginRecordTimer();
        } catch (error) {
          uni.showToast({ title: '录音启动失败', icon: 'none' });
        }
      });
    },
    ensureNativeMicrophonePermission() {
      if (typeof plus === 'undefined' || !plus.os || String(plus.os.name || '').toLowerCase() !== 'android'
          || !plus.android || typeof plus.android.requestPermissions !== 'function') {
        return Promise.resolve(true);
      }
      return new Promise((resolve) => {
        try {
          plus.android.requestPermissions(
            ['android.permission.RECORD_AUDIO'],
            (result) => {
              const granted = Array.isArray(result && result.granted)
                && result.granted.indexOf('android.permission.RECORD_AUDIO') >= 0;
              if (!granted) uni.showToast({ title: '请在系统设置中允许麦克风权限', icon: 'none' });
              resolve(granted);
            },
            () => {
              uni.showToast({ title: '麦克风权限申请失败', icon: 'none' });
              resolve(false);
            }
          );
        } catch (error) {
          resolve(true);
        }
      });
    },
    stopRecording() {
      if (!this.recording) return;
      if (this.browserRecorder && this.browserRecorder.state !== 'inactive') {
        this.browserRecorder.stop();
        return;
      }
      if (this.nativeRecorder) {
        try { this.nativeRecorder.stop(); } catch (e) {
          this.recording = false;
          this.finishRecordTimer();
        }
      }
    },
    releaseBrowserRecorder() {
      const stream = this.browserStream;
      if (stream && typeof stream.getTracks === 'function') {
        stream.getTracks().forEach((track) => { try { track.stop(); } catch (e) {} });
      }
      this.browserStream = null;
      this.browserRecorder = null;
      this.browserChunks = [];
    },
    convertRecordingToWav(blob) {
      const AudioContextClass = typeof window !== 'undefined'
        ? (window.AudioContext || window.webkitAudioContext)
        : null;
      if (!blob || !AudioContextClass) return Promise.reject(new Error('audio_context_unavailable'));
      const read = typeof blob.arrayBuffer === 'function'
        ? blob.arrayBuffer()
        : new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reject;
          reader.readAsArrayBuffer(blob);
        });
      return read.then((buffer) => {
        const context = new AudioContextClass();
        return context.decodeAudioData(buffer.slice ? buffer.slice(0) : buffer)
          .then((audioBuffer) => this.encodeAudioBufferAsWav(audioBuffer))
          .finally(() => { try { context.close(); } catch (e) {} });
      });
    },
    encodeAudioBufferAsWav(audioBuffer) {
      const frameCount = Math.max(0, Number(audioBuffer && audioBuffer.length) || 0);
      const sampleRate = Math.max(8000, Number(audioBuffer && audioBuffer.sampleRate) || 16000);
      const channelCount = Math.max(1, Number(audioBuffer && audioBuffer.numberOfChannels) || 1);
      if (!frameCount || !audioBuffer || typeof audioBuffer.getChannelData !== 'function') {
        throw new Error('invalid_audio_buffer');
      }
      const output = new ArrayBuffer(44 + frameCount * 2);
      const view = new DataView(output);
      const writeText = (offset, value) => {
        for (let index = 0; index < value.length; index += 1) view.setUint8(offset + index, value.charCodeAt(index));
      };
      writeText(0, 'RIFF');
      view.setUint32(4, 36 + frameCount * 2, true);
      writeText(8, 'WAVE');
      writeText(12, 'fmt ');
      view.setUint32(16, 16, true);
      view.setUint16(20, 1, true);
      view.setUint16(22, 1, true);
      view.setUint32(24, sampleRate, true);
      view.setUint32(28, sampleRate * 2, true);
      view.setUint16(32, 2, true);
      view.setUint16(34, 16, true);
      writeText(36, 'data');
      view.setUint32(40, frameCount * 2, true);
      const channels = [];
      for (let channel = 0; channel < channelCount; channel += 1) {
        channels.push(audioBuffer.getChannelData(channel));
      }
      for (let frame = 0; frame < frameCount; frame += 1) {
        let sample = 0;
        for (let channel = 0; channel < channels.length; channel += 1) sample += channels[channel][frame] || 0;
        sample = Math.max(-1, Math.min(1, sample / channels.length));
        view.setInt16(44 + frame * 2, sample < 0 ? sample * 32768 : sample * 32767, true);
      }
      const blob = new Blob([output], { type: 'audio/wav' });
      return typeof File !== 'undefined'
        ? new File([blob], 'voice-sample.wav', { type: 'audio/wav' })
        : blob;
    },
    releaseRecording() {
      if (this.recordTimer) clearInterval(this.recordTimer);
      this.recordTimer = null;
      this.discardRecordingResult = true;
      if (this.browserRecorder && this.browserRecorder.state !== 'inactive') {
        try { this.browserRecorder.stop(); } catch (e) {}
      }
      this.releaseBrowserRecorder();
      if (this.recording && this.nativeRecorder) {
        try { this.nativeRecorder.stop(); } catch (e) {}
      }
      this.recording = false;
      this.recordStartedAt = 0;
      this.recordSeconds = 0;
    },
    createVoice() {
      if (!this.selectedFile || this.creating) return;
      if (!String(this.form.displayName || '').trim() || !String(this.form.sampleText || '').trim()) {
        uni.showToast({ title: '请填写名称和参考台词', icon: 'none' });
        return;
      }
      if (this.audioDurationMs > 0 && (this.audioDurationMs < 5000 || this.audioDurationMs > 60000)) {
        uni.showToast({ title: '参考音频时长需为 5 到 60 秒', icon: 'none' });
        return;
      }
      this.creating = true;
      if (!this.createRequestId) {
        this.createRequestId = 'h5-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
      }
      tavernApi.createUserTtsVoice(this.clientUid(), this.selectedFile, {
        requestId: this.createRequestId,
        displayName: this.form.displayName,
        sampleText: this.form.sampleText,
        durationMs: this.audioDurationMs
      }).then(() => {
        uni.showToast({ title: '自建音色已创建', icon: 'none' });
        this.form = { displayName: '', sampleText: '' };
        this.clearSelectedAudio();
        this.resetCreateRequestId(true);
        this.load();
      }).catch((error) => {
        uni.showToast({ title: String(error.message || '创建失败'), icon: 'none' });
      }).finally(() => { this.creating = false; });
    },
    bindVoice(voice) {
      tavernApi.putUserTtsVoiceBinding(this.clientUid(), {
        scopeType: this.scopeType,
        characterId: this.characterId,
        memberId: this.memberId,
        voiceId: voice.id
      })
        .then(() => {
          this.boundVoiceId = voice.id;
          if (this.scopeType === 'GLOBAL') this.overview.globalVoiceId = voice.id;
          const title = this.scopeType === 'MEMBER'
            ? '已绑定到当前群聊成员'
            : (this.scopeType === 'CHARACTER' ? '已绑定到当前角色' : '已绑定为全局音色');
          uni.showToast({ title, icon: 'none' });
        })
        .catch((error) => uni.showToast({ title: String(error.message || '绑定失败'), icon: 'none' }));
    },
    clearBinding() {
      if (!this.boundVoiceId) return;
      tavernApi.putUserTtsVoiceBinding(this.clientUid(), {
        scopeType: this.scopeType,
        characterId: this.characterId,
        memberId: this.memberId,
        voiceId: null
      }).then(() => {
        this.boundVoiceId = 0;
        if (this.scopeType === 'GLOBAL') this.overview.globalVoiceId = 0;
        uni.showToast({ title: '已解除当前范围绑定', icon: 'none' });
      }).catch((error) => uni.showToast({ title: String(error.message || '解绑失败'), icon: 'none' }));
    },
    openPreview(voice) {
      if (!voice || !voice.available) return;
      this.releasePreviewPlayer();
      this.previewVoice = voice;
      this.previewAudioDataUrl = '';
      this.previewGeneratedText = '';
    },
    closePreview() {
      this.releasePreviewPlayer();
      this.previewVoice = null;
      this.previewAudioDataUrl = '';
      this.previewGeneratedText = '';
      this.previewLoading = false;
    },
    submitPreview() {
      if (!this.previewVoice || this.previewLoading) return;
      const text = String(this.previewText || '').replace(/\s+/g, ' ').trim();
      if (!text) return;
      if (this.previewPlaying) {
        this.releasePreviewPlayer();
        return;
      }
      if (this.previewAudioDataUrl && this.previewGeneratedText === text) {
        this.playPreviewAudio(this.previewAudioDataUrl);
        return;
      }
      this.previewLoading = true;
      const requestId = 'preview-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
      tavernApi.previewUserTtsVoice(this.clientUid(), this.previewVoice.id, { requestId, text }).then((result) => {
        const dataUrl = String(result && result.audioDataUrl || '').trim();
        if (dataUrl.indexOf('data:audio/') !== 0) throw new Error('试听音频格式无效');
        this.previewAudioDataUrl = dataUrl;
        this.previewGeneratedText = text;
        this.playPreviewAudio(dataUrl);
      }).catch((error) => {
        uni.showToast({ title: String(error.message || '试听生成失败'), icon: 'none' });
      }).finally(() => { this.previewLoading = false; });
    },
    playPreviewAudio(dataUrl) {
      this.releasePreviewPlayer();
      if (typeof uni.createInnerAudioContext !== 'function') {
        uni.showToast({ title: '当前设备无法播放试听音频', icon: 'none' });
        return;
      }
      const player = uni.createInnerAudioContext();
      this.previewPlayer = player;
      this.previewPlaying = true;
      player.autoplay = true;
      player.onEnded(() => this.releasePreviewPlayer());
      player.onError(() => {
        this.releasePreviewPlayer();
        uni.showToast({ title: '试听播放失败', icon: 'none' });
      });
      player.src = dataUrl;
    },
    releasePreviewPlayer() {
      const player = this.previewPlayer;
      this.previewPlayer = null;
      this.previewPlaying = false;
      if (!player) return;
      try { player.stop(); } catch (e) {}
      try { player.destroy(); } catch (e) {}
    },
    removeVoice(voice) {
      if (!voice) return;
      const canDeleteProvider = voice.configMatches === true && voice.disabled !== true
        && String(voice.status || '').toUpperCase() === 'READY';
      const itemList = canDeleteProvider
        ? ['同时删除硅基流动资源', '仅从本应用移除']
        : ['仅从本应用移除'];
      uni.showActionSheet({ itemList, success: (choice) => {
        const deleteProvider = canDeleteProvider && Number(choice.tapIndex) === 0;
        const content = deleteProvider
          ? '将先删除硅基流动账号中的音色，再解除本应用内绑定。供应商删除失败时会保留本地记录，确定继续吗？'
          : '只移除本应用记录并解除相关绑定，硅基流动账号中的音色仍会保留。确定继续吗？';
        uni.showModal({ title: '删除自建音色', content, success: (res) => {
          if (!res.confirm) return;
          tavernApi.deleteUserTtsVoice(this.clientUid(), voice.id, deleteProvider).then(() => {
            if (this.previewVoice && Number(this.previewVoice.id) === Number(voice.id)) this.closePreview();
            uni.showToast({ title: deleteProvider ? '平台与本地音色已删除' : '本地记录已删除', icon: 'none' });
            this.load();
            if (this.providerVoicesVisible) this.syncProviderVoices();
          }).catch((error) => uni.showToast({ title: String(error.message || '删除失败'), icon: 'none' }));
        } });
      } });
    }
  }
};
</script>

<style scoped lang="scss">
.page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #203846;
  background: #eaf4f7;
}

.page-bg {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.32;
  filter: saturate(0.76) brightness(1.05);
  pointer-events: none;
}

.body {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 960px;
  height: calc(100vh - 88rpx);
  margin: 0 auto;
  padding: 24rpx 28rpx 0;
  box-sizing: border-box;
}

.studio-stage {
  position: relative;
  padding: 30rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.84);
  border-radius: 32rpx;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(235, 248, 250, 0.62));
  box-shadow: 0 20rpx 48rpx rgba(44, 83, 103, 0.12), inset 0 1rpx 0 rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(24rpx) saturate(1.08);
  -webkit-backdrop-filter: blur(24rpx) saturate(1.08);
}

.stage-main { display: block; }
.sound-disc, .stage-wave, .stage-kicker, .section-kicker { display: none; }
.stage-copy { min-width: 0; }
.stage-title { display: block; color: #203846; font-size: 34rpx; line-height: 1.3; font-weight: 800; }
.stage-desc { display: block; max-width: 650rpx; margin-top: 9rpx; color: #647b8b; font-size: 22rpx; line-height: 1.65; }

.account-ribbon {
  min-height: 82rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 24rpx;
  padding: 13rpx 14rpx 13rpx 18rpx;
  border: 1rpx solid rgba(79, 147, 163, 0.14);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.58);
  box-sizing: border-box;
}

.account-state { min-width: 0; flex: 1; display: flex; align-items: center; gap: 14rpx; }
.provider-dot { width: 15rpx; height: 15rpx; flex: 0 0 15rpx; border-radius: 50%; background: #91a2a8; box-shadow: 0 0 0 6rpx rgba(105, 124, 131, 0.1); }
.provider-dot.online { background: #3a9d7c; box-shadow: 0 0 0 6rpx rgba(58, 157, 124, 0.12); }
.account-copy { min-width: 0; }
.account-title, .account-sub { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-title { color: #315866; font-size: 23rpx; font-weight: 800; }
.account-sub { margin-top: 4rpx; color: #718791; font-size: 18rpx; }

.round-action {
  width: 58rpx;
  height: 58rpx;
  flex: 0 0 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid rgba(79, 147, 163, 0.14);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 8rpx 20rpx rgba(44, 83, 103, 0.08);
  box-sizing: border-box;
}

.round-action--light { background: rgba(255, 255, 255, 0.76); }
.round-action--danger { border-color: rgba(182, 95, 131, 0.16); background: rgba(255, 239, 246, 0.78); }
.round-action.disabled { opacity: 0.45; pointer-events: none; }
.stage-foot { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 18rpx 4rpx 0; color: #718791; font-size: 19rpx; }

.mode-dock {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7rpx;
  margin: 18rpx 0 30rpx;
  padding: 7rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.78);
  border-radius: 28rpx;
  background: rgba(232, 244, 247, 0.68);
  box-shadow: 0 14rpx 34rpx rgba(44, 83, 103, 0.1), inset 0 1rpx 0 rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(18rpx);
  -webkit-backdrop-filter: blur(18rpx);
}

.mode-item { min-width: 0; height: 68rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; border-radius: 21rpx; color: #647b8b; font-size: 22rpx; font-weight: 700; transition: background-color 160ms ease, color 160ms ease, box-shadow 160ms ease; }
.mode-item.active { color: #315f72; background: rgba(255, 255, 255, 0.88); box-shadow: 0 8rpx 20rpx rgba(44, 83, 103, 0.1); }
.mode-icon { width: 38rpx; height: 38rpx; display: flex; align-items: center; justify-content: center; border-radius: 13rpx; background: rgba(255, 255, 255, 0.52); }
.mode-item.active .mode-icon { background: rgba(224, 244, 248, 0.9); }

.workspace { position: relative; padding: 0 4rpx 32rpx; background: transparent; border: 0; box-shadow: none; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 0 6rpx; }
.section-title { display: block; color: #203846; font-size: 31rpx; line-height: 1.35; font-weight: 800; }
.section-tools { display: flex; align-items: center; gap: 13rpx; }
.text-action { color: #4f7f8e; font-size: 21rpx; font-weight: 700; }
.text-action--warm { color: #a24f72; }

.sync-arc {
  min-height: 100rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 20rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 26rpx;
  background: rgba(248, 252, 253, 0.72);
  box-shadow: 0 14rpx 32rpx rgba(44, 83, 103, 0.09), inset 0 1rpx 0 rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  box-sizing: border-box;
}

.sync-icon { width: 62rpx; height: 62rpx; flex: 0 0 62rpx; display: flex; align-items: center; justify-content: center; border-radius: 19rpx; background: linear-gradient(145deg, #4f93a3, #72bdc8); box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, 0.2); }
.sync-copy { min-width: 0; flex: 1; }
.sync-title, .sync-desc { display: block; }
.sync-title { color: #315866; font-size: 24rpx; font-weight: 800; }
.sync-desc { margin-top: 5rpx; color: #718791; font-size: 19rpx; line-height: 1.45; }
.sync-button, .compact-action { flex-shrink: 0; margin: 0; border: 0; }
.sync-button { min-width: 108rpx; height: 60rpx; line-height: 60rpx; padding: 0 20rpx; border-radius: 999rpx; color: #ffffff; background: #4f93a3; font-size: 21rpx; font-weight: 700; box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, 0.18); }
.sync-button--setup { background: #b65f83; }
.sync-button::after, .compact-action::after, .create-button::after, .preview-button::after { border: 0; }
.sync-button[disabled], .compact-action[disabled], .create-button[disabled], .preview-button[disabled] { opacity: 0.45; }

.provider-drawer { overflow: hidden; margin-top: 14rpx; padding: 6rpx 18rpx; border: 1rpx solid rgba(255, 255, 255, 0.72); border-radius: 24rpx; background: rgba(240, 248, 249, 0.74); }
.provider-row { min-height: 88rpx; display: flex; align-items: center; gap: 14rpx; border-bottom: 1rpx solid rgba(79, 147, 163, 0.12); }
.provider-row:last-child { border-bottom: 0; }
.mini-disc { width: 46rpx; height: 46rpx; flex: 0 0 46rpx; display: flex; align-items: center; justify-content: center; border-radius: 15rpx; background: rgba(222, 242, 245, 0.92); }
.voice-copy { min-width: 0; flex: 1; }
.voice-name, .voice-meta { display: block; }
.voice-name { color: #294752; font-size: 25rpx; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.voice-meta { margin-top: 5rpx; color: #748993; font-size: 19rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.imported { flex-shrink: 0; color: #318064; font-size: 19rpx; font-weight: 700; }
.compact-action { min-width: 96rpx; height: 54rpx; line-height: 54rpx; padding: 0 16rpx; border-radius: 999rpx; color: #4f7f8e; background: rgba(255, 255, 255, 0.82); font-size: 20rpx; font-weight: 700; }

.empty-state { min-height: 270rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 34rpx 20rpx; box-sizing: border-box; color: #718791; text-align: center; }
.empty-state--small { min-height: 104rpx; padding: 18rpx; font-size: 21rpx; }
.empty-record { width: 98rpx; height: 98rpx; display: flex; align-items: center; justify-content: center; border: 1rpx solid rgba(79, 147, 163, 0.15); border-radius: 28rpx; background: rgba(240, 248, 250, 0.82); box-shadow: 0 12rpx 28rpx rgba(44, 83, 103, 0.08); }
.empty-title { display: block; margin-top: 18rpx; color: #315866; font-size: 27rpx; font-weight: 800; }
.empty-desc { display: block; max-width: 520rpx; margin-top: 8rpx; color: #718791; font-size: 21rpx; line-height: 1.6; }
.empty-pulse { width: 24rpx; height: 24rpx; margin-bottom: 18rpx; border-radius: 50%; background: #4f93a3; animation: pulse 1s ease-in-out infinite alternate; }

.voice-orbit-list { display: grid; gap: 13rpx; margin-top: 20rpx; }
.voice-orbit { min-height: 104rpx; display: flex; align-items: center; gap: 16rpx; padding: 14rpx 16rpx; border: 1rpx solid rgba(255, 255, 255, 0.82); border-radius: 26rpx; background: rgba(248, 252, 253, 0.74); box-shadow: 0 13rpx 30rpx rgba(44, 83, 103, 0.08), inset 0 1rpx 0 rgba(255, 255, 255, 0.88); backdrop-filter: blur(18rpx); -webkit-backdrop-filter: blur(18rpx); box-sizing: border-box; }
.voice-orbit--alt { background: rgba(248, 252, 253, 0.74); border-color: rgba(255, 255, 255, 0.82); }
.voice-preview { position: relative; width: 72rpx; height: 72rpx; flex: 0 0 72rpx; display: flex; align-items: center; justify-content: center; border-radius: 22rpx; background: linear-gradient(145deg, #4f93a3, #72bdc8); overflow: hidden; box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, 0.18); }
.voice-orbit--alt .voice-preview { background: linear-gradient(145deg, #4f93a3, #72bdc8); }
.voice-preview.disabled { background: #b9c8cc; box-shadow: none; }
.voice-groove { position: absolute; inset: 10rpx; border: 1rpx solid rgba(255, 255, 255, 0.26); border-radius: 15rpx; }
.voice-actions { flex-shrink: 0; display: flex; align-items: center; gap: 12rpx; }
.bind { padding: 8rpx 12rpx; border-radius: 999rpx; color: #4f7f8e; background: rgba(224, 244, 248, 0.74); font-size: 20rpx; font-weight: 700; }
.bind.active { color: #28705f; background: rgba(218, 243, 235, 0.88); }

.create-step { min-width: 88rpx; padding: 9rpx 15rpx; border-radius: 999rpx; background: rgba(224, 244, 248, 0.88); color: #4f7f8e; font-size: 20rpx; font-weight: 700; text-align: center; }
.notice-ribbon { display: flex; align-items: center; gap: 15rpx; margin-top: 20rpx; padding: 18rpx; border: 1rpx solid rgba(182, 95, 131, 0.14); border-radius: 24rpx; background: rgba(255, 238, 245, 0.72); }
.notice-sign { width: 56rpx; height: 56rpx; flex: 0 0 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 18rpx; background: rgba(255, 255, 255, 0.7); }
.notice-copy { min-width: 0; flex: 1; }
.notice-copy text { display: block; }
.notice-copy text:first-child { color: #944e6c; font-size: 23rpx; font-weight: 800; }
.notice-copy text:last-child { margin-top: 5rpx; color: #8d6a78; font-size: 20rpx; line-height: 1.5; }
.creation-flow { margin-top: 14rpx; }
.field-group { display: flex; align-items: flex-start; gap: 17rpx; padding: 24rpx 4rpx; border-bottom: 1rpx solid rgba(79, 147, 163, 0.13); }
.field-group--audio { border-bottom: 0; }
.field-no { width: 50rpx; height: 50rpx; flex: 0 0 50rpx; display: flex; align-items: center; justify-content: center; border-radius: 16rpx; background: rgba(224, 244, 248, 0.9); color: #4f7f8e; font-size: 19rpx; font-weight: 800; }
.field-body { min-width: 0; flex: 1; }
.field-label { display: block; margin-bottom: 12rpx; color: #315866; font-size: 23rpx; font-weight: 800; }
.studio-input, .studio-textarea { width: 100%; box-sizing: border-box; border: 1rpx solid rgba(79, 147, 163, 0.16); border-radius: 22rpx; background: rgba(255, 255, 255, 0.68); box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.88); color: #294752; font-size: 24rpx; }
.studio-input { height: 78rpx; padding: 0 22rpx; }
.studio-textarea { min-height: 148rpx; padding: 19rpx 22rpx; line-height: 1.6; }
.source-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13rpx; }
.source-action { min-height: 78rpx; display: flex; align-items: center; justify-content: center; gap: 10rpx; border: 1rpx solid rgba(79, 147, 163, 0.17); border-radius: 22rpx; background: rgba(238, 249, 251, 0.74); color: #315f72; font-size: 22rpx; font-weight: 700; }
.source-action--record { border-color: rgba(182, 95, 131, 0.18); background: rgba(255, 238, 245, 0.72); color: #944e6c; }
.source-action--record.recording { background: #b65f83; color: #fff; }
.source-action.disabled { opacity: 0.45; }
.selected-audio { min-height: 82rpx; display: flex; align-items: center; gap: 14rpx; margin-top: 13rpx; padding: 12rpx 14rpx; border: 1rpx solid rgba(79, 147, 163, 0.13); border-radius: 22rpx; background: rgba(238, 249, 251, 0.72); box-sizing: border-box; }
.selected-wave { width: 62rpx; height: 50rpx; flex: 0 0 62rpx; display: flex; align-items: center; justify-content: center; gap: 3rpx; }
.selected-bar { width: 4rpx; border-radius: 3rpx; background: #4f93a3; }
.selected-bar--1 { height: 12rpx; }.selected-bar--2 { height: 24rpx; }.selected-bar--3 { height: 38rpx; }.selected-bar--4 { height: 19rpx; }
.file-copy { min-width: 0; flex: 1; }
.file-name, .file-meta { display: block; }
.file-name { color: #315866; font-size: 21rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { margin-top: 4rpx; color: #718791; font-size: 18rpx; }
.create-button { min-height: 84rpx; display: flex; align-items: center; justify-content: center; gap: 11rpx; margin: 22rpx 0 0; border: 0; border-radius: 999rpx; background: linear-gradient(135deg, #4f93a3, #72bdc8); box-shadow: 0 15rpx 30rpx rgba(79, 147, 163, 0.22); color: #fff; font-size: 25rpx; font-weight: 800; }
.creation-note { display: block; margin-top: 14rpx; color: #718791; font-size: 19rpx; line-height: 1.6; text-align: center; }

.resource-spark { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 18rpx; background: #4f93a3; box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, 0.18); }
.resource-intro { display: block; margin: 14rpx 6rpx 20rpx; color: #647b8b; font-size: 21rpx; line-height: 1.65; }
.resource-flow { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; }
.resource-island, .resource-island:nth-child(even), .resource-island--featured { position: relative; min-height: 146rpx; display: flex; align-items: center; gap: 15rpx; overflow: hidden; padding: 20rpx; border: 1rpx solid rgba(255, 255, 255, 0.82); border-radius: 26rpx; background: rgba(248, 252, 253, 0.74); color: #203846; box-shadow: 0 13rpx 30rpx rgba(44, 83, 103, 0.08), inset 0 1rpx 0 rgba(255, 255, 255, 0.88); box-sizing: border-box; }
.resource-island--featured { grid-column: auto; }
.resource-index { display: none; }
.resource-symbol, .resource-island--night .resource-symbol { width: 64rpx; height: 64rpx; flex: 0 0 64rpx; display: flex; align-items: center; justify-content: center; border-radius: 20rpx; background: rgba(225, 244, 248, 0.88); }
.resource-copy { min-width: 0; flex: 1; }
.resource-name, .resource-desc, .resource-domain { display: block; }
.resource-name { color: #315866; font-size: 25rpx; font-weight: 800; }
.resource-desc { margin-top: 6rpx; color: #647b8b; font-size: 20rpx; line-height: 1.45; }
.resource-domain { margin-top: 7rpx; color: #82959e; font-size: 17rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-arrow, .resource-island--night .resource-arrow, .resource-island--rose .resource-arrow, .resource-island--lilac .resource-arrow { width: 48rpx; height: 48rpx; flex: 0 0 48rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: #4f93a3; }
.license-wave { display: flex; align-items: flex-start; gap: 12rpx; margin-top: 16rpx; padding: 16rpx 18rpx; border-left: 5rpx solid rgba(79, 147, 163, 0.55); border-radius: 0 20rpx 20rpx 0; background: rgba(238, 249, 251, 0.58); color: #647b8b; font-size: 19rpx; line-height: 1.6; }

.bottom-space { height: calc(42rpx + env(safe-area-inset-bottom)); }
.disabled-view { position: relative; z-index: 1; min-height: 66vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40rpx; text-align: center; }
.empty-record--large { width: 124rpx; height: 124rpx; }

.preview-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: flex-end; padding: 24rpx 24rpx 0; background: rgba(25, 45, 58, 0.34); backdrop-filter: blur(8rpx); -webkit-backdrop-filter: blur(8rpx); box-sizing: border-box; }
.preview-sheet { width: 100%; max-width: 760px; margin: 0 auto; padding: 15rpx 26rpx calc(26rpx + env(safe-area-inset-bottom)); border: 1rpx solid rgba(255, 255, 255, 0.84); border-bottom: 0; border-radius: 32rpx 32rpx 0 0; background: rgba(248, 252, 253, 0.9); box-shadow: 0 -20rpx 50rpx rgba(31, 70, 84, 0.18); backdrop-filter: blur(24rpx); -webkit-backdrop-filter: blur(24rpx); box-sizing: border-box; }
.preview-handle { width: 64rpx; height: 6rpx; margin: 0 auto 20rpx; border-radius: 999rpx; background: rgba(79, 147, 163, 0.3); }
.preview-head { display: flex; align-items: center; gap: 14rpx; }
.preview-record { width: 62rpx; height: 62rpx; display: flex; align-items: center; justify-content: center; border-radius: 19rpx; background: #4f93a3; }
.preview-copy { min-width: 0; flex: 1; }
.preview-title { display: block; color: #294752; font-size: 27rpx; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-textarea { margin-top: 18rpx; min-height: 160rpx; }
.preview-footer { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-top: 15rpx; }
.preview-count { color: #718791; font-size: 19rpx; }
.preview-button { min-width: 240rpx; height: 70rpx; line-height: 70rpx; margin: 0; padding: 0 22rpx; border: 0; border-radius: 999rpx; background: #4f93a3; color: #fff; font-size: 23rpx; font-weight: 700; }

@keyframes pulse { from { opacity: 0.42; transform: scale(0.84); } to { opacity: 1; transform: scale(1); } }

@media (min-width: 760px) {
  .voice-orbit-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 520px) {
  .body { padding: 18rpx 18rpx 0; }
  .studio-stage { padding: 25rpx 22rpx; border-radius: 28rpx; }
  .stage-title { font-size: 31rpx; }
  .stage-desc { font-size: 20rpx; }
  .account-ribbon { margin-top: 20rpx; }
  .mode-dock { margin-bottom: 26rpx; }
  .mode-item { gap: 5rpx; font-size: 19rpx; }
  .mode-icon { width: 34rpx; height: 34rpx; }
  .workspace { padding-left: 0; padding-right: 0; }
  .sync-arc { flex-wrap: wrap; }
  .sync-copy { min-width: calc(100% - 82rpx); }
  .sync-button { width: 100%; }
  .voice-orbit { flex-wrap: wrap; align-items: flex-start; }
  .voice-copy { min-width: calc(100% - 92rpx); padding-top: 5rpx; }
  .voice-actions { width: 100%; justify-content: flex-end; }
  .source-actions, .resource-flow { grid-template-columns: 1fr; }
  .resource-island { min-height: 138rpx; }
  .preview-footer { align-items: stretch; flex-direction: column; }
  .preview-button { width: 100%; }
}

@media (max-width: 360px) {
  .mode-icon { display: none; }
  .stage-foot { align-items: flex-start; flex-direction: column; gap: 6rpx; }
  .resource-arrow { display: none; }
}

@media (hover: hover) and (pointer: fine) {
  .mode-item, .round-action, .sync-button, .compact-action, .voice-orbit, .source-action, .create-button, .resource-island, .preview-button { transition: transform 180ms ease, box-shadow 180ms ease; }
  .round-action:hover, .sync-button:hover, .compact-action:hover, .voice-orbit:hover, .source-action:hover, .create-button:hover, .resource-island:hover, .preview-button:hover { transform: translateY(-2rpx); }
}

@media (prefers-reduced-motion: reduce) {
  .empty-pulse { animation: none; }
}
</style>
