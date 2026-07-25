<template>
  <view class="page">
    <image class="page-bg" src="/static/login.png" mode="aspectFill" />
    <tavern-nav-bar title="我的自建音色" mode="dark" @back="goBack" />

    <scroll-view scroll-y class="body">
      <view class="hero">
        <text class="eyebrow">PRIVATE VOICES</text>
        <text class="title">只使用你的 API Key</text>
        <text class="desc">参考音频仅用于即时克隆请求，不会作为音频文件长期保存在本应用服务器。</text>
        <text class="scope-label">{{ scopeLabel }}</text>
        <view class="quota"><text>{{ overview.used || 0 }} / {{ overview.limit || 0 }}</text><text>个音色</text></view>
      </view>

      <view v-if="overview.denyReason && !overview.canCreate" class="notice">{{ overview.denyReason }}</view>

      <view v-if="overview.canCreate" class="panel">
        <text class="panel-title">创建新的音色</text>
        <text class="panel-tip">请上传 5 到 20 秒、清晰无背景音乐的人声，并填写音频中的准确台词。仅可使用本人声音或已获得明确授权的声音。</text>
        <input v-model="form.displayName" class="input" maxlength="64" placeholder="音色名称，例如：温柔女声" />
        <textarea v-model="form.sampleText" class="textarea" maxlength="255" placeholder="参考音频中实际朗读的文字" />
        <view class="source-actions">
          <view class="source-action" :class="{ disabled: recording }" @tap="chooseAudio">
            <u-icon name="attach" color="#167f78" size="32"></u-icon>
            <text>选择音频</text>
          </view>
          <view class="source-action source-action--record" :class="{ recording: recording }" @tap="toggleRecording">
            <u-icon :name="recording ? 'pause' : 'mic'" :color="recording ? '#fff' : '#a4515d'" size="32"></u-icon>
            <text>{{ recording ? '停止 ' + recordSeconds + 's' : '录制参考音频' }}</text>
          </view>
        </view>
        <view v-if="fileName" class="file-row">
          <view class="file-copy">
            <text class="file-name">{{ fileName }}</text>
            <text class="file-meta">{{ audioDurationText }}</text>
          </view>
          <text class="file-action" @tap="clearSelectedAudio">移除</text>
        </view>
        <button class="primary" :disabled="creating || recording || !selectedFile" @tap="createVoice">{{ creating ? '创建中…' : '使用我的 Key 创建' }}</button>
        <text class="small-tip">官方平台模式不提供自建音色服务，也不会使用平台额度创建私有音色。</text>
      </view>

      <view class="panel">
        <view class="panel-head">
          <text class="panel-title">我的音色</text>
          <view class="panel-tools">
            <text v-if="boundVoiceId" class="unbind" @tap="clearBinding">解除当前绑定</text>
            <text class="refresh" @tap="load">刷新</text>
          </view>
        </view>
        <view v-if="loading" class="empty">正在加载…</view>
        <view v-else-if="!voices.length" class="empty">还没有自建音色</view>
        <view v-else>
          <view v-for="voice in voices" :key="voice.id" class="voice-row">
            <view class="voice-copy">
              <text class="voice-name">{{ voice.displayName }}</text>
              <text class="voice-meta">{{ voice.statusText }}</text>
            </view>
            <view class="voice-actions">
              <text v-if="voice.available" class="bind" :class="{ active: Number(boundVoiceId) === Number(voice.id) }" @tap="bindVoice(voice)">{{ Number(boundVoiceId) === Number(voice.id) ? scopeBoundText : scopeActionText }}</text>
              <text class="delete" @tap="removeVoice(voice)">删除</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
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
      overview: { used: 0, limit: 0, canCreate: false, denyReason: '', globalVoiceId: 0 },
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
      form: { displayName: '', sampleText: '' }
    };
  },
  computed: {
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
      return seconds > 0 ? seconds + ' 秒 · 建议 5 到 20 秒' : '将在创建时由平台校验音频';
    }
  },
  onLoad(options) {
    this.pageActive = true;
    const characterId = Math.max(0, Math.floor(Number(options && options.characterId) || 0));
    const memberId = Math.max(0, Math.floor(Number(options && options.memberId) || 0));
    this.characterId = characterId;
    this.memberId = characterId > 0 ? memberId : 0;
    this.scopeType = characterId > 0 && memberId > 0 ? 'MEMBER' : (characterId > 0 ? 'CHARACTER' : 'GLOBAL');
  },
  onShow() { this.load(); },
  onUnload() {
    this.pageActive = false;
    this.releaseRecording();
  },
  methods: {
    clientUid() { return tavernApi.getClientUid(); },
    goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) }); },
    load() {
      this.loading = true;
      tavernApi.getUserTtsVoices(this.clientUid()).then((data) => {
        const value = data || {};
        this.overview = Object.assign(this.overview, value);
        this.voices = Array.isArray(value.voices) ? value.voices : [];
        return tavernApi.getUserTtsVoiceBinding(this.clientUid(), {
          scopeType: this.scopeType,
          characterId: this.characterId,
          memberId: this.memberId
        });
      }).then((binding) => {
        this.boundVoiceId = Number(binding && binding.voiceId) || 0;
      }).catch((error) => {
        uni.showToast({ title: String(error.message || '加载音色失败'), icon: 'none' });
      }).finally(() => { this.loading = false; });
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
        this.recordSeconds = Math.min(20, Math.floor((Date.now() - this.recordStartedAt) / 1000));
        if (Date.now() - this.recordStartedAt >= 20000) this.stopRecording();
      }, 250);
    },
    finishRecordTimer() {
      const duration = this.recordStartedAt > 0 ? Date.now() - this.recordStartedAt : 0;
      if (this.recordTimer) clearInterval(this.recordTimer);
      this.recordTimer = null;
      this.recordStartedAt = 0;
      this.recordSeconds = 0;
      return Math.min(20000, Math.max(0, Math.round(duration)));
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
          recorder.start({ duration: 20000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 96000, format: 'mp3' });
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
      if (this.audioDurationMs > 0 && (this.audioDurationMs < 5000 || this.audioDurationMs > 20000)) {
        uni.showToast({ title: '参考音频时长需为 5 到 20 秒', icon: 'none' });
        return;
      }
      this.creating = true;
      tavernApi.createUserTtsVoice(this.clientUid(), this.selectedFile, {
        requestId: 'h5-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10),
        displayName: this.form.displayName,
        sampleText: this.form.sampleText,
        durationMs: this.audioDurationMs
      }).then(() => {
        uni.showToast({ title: '自建音色已创建', icon: 'none' });
        this.form = { displayName: '', sampleText: '' };
        this.clearSelectedAudio();
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
    removeVoice(voice) {
      uni.showModal({ title: '删除自建音色', content: '删除后会解除本应用内的所有相关绑定并移除记录；硅基流动账号侧资源需由你在供应商平台管理。确定继续吗？', success: (res) => {
        if (!res.confirm) return;
        tavernApi.deleteUserTtsVoice(this.clientUid(), voice.id).then(() => {
          uni.showToast({ title: '已删除', icon: 'none' });
          this.load();
        }).catch((error) => uni.showToast({ title: String(error.message || '删除失败'), icon: 'none' }));
      } });
    }
  }
};
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: #edf3f5; position: relative; }
.page-bg { position: fixed; inset: 0; width: 100%; height: 100%; opacity: .18; }
.body { position: relative; height: calc(100vh - 88rpx); box-sizing: border-box; padding: 24rpx 24rpx 60rpx; }
.hero, .panel, .notice { position: relative; background: rgba(255,255,255,.9); border: 1rpx solid rgba(110,150,166,.16); border-radius: 18rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 18rpx 40rpx rgba(54,89,110,.08); }
.hero { background: #183b4a; color: #fff; }
.eyebrow { display: block; font-size: 20rpx; letter-spacing: 3rpx; color: #9dd8d5; }
.title { display: block; margin-top: 12rpx; font-size: 40rpx; font-weight: 700; }
.desc { display: block; margin-top: 12rpx; color: rgba(255,255,255,.78); font-size: 24rpx; line-height: 1.6; }
.scope-label { display: inline-flex; margin-top: 18rpx; padding: 8rpx 14rpx; border: 1rpx solid rgba(185,237,219,.34); border-radius: 8rpx; color: #b9eddb; font-size: 21rpx; }
.quota { display: flex; gap: 10rpx; align-items: baseline; margin-top: 22rpx; color: #b9eddb; font-size: 26rpx; }
.quota text:first-child { font-size: 36rpx; font-weight: 700; }
.notice { color: #a45e21; background: #fff6ea; }
.panel-title { color: #203f4e; font-size: 30rpx; font-weight: 700; }
.panel-tip, .small-tip { display: block; color: #718592; font-size: 22rpx; line-height: 1.6; margin: 12rpx 0 18rpx; }
.input, .textarea { width: 100%; box-sizing: border-box; border: 1rpx solid #dbe6ea; border-radius: 12rpx; background: #f8fbfc; padding: 20rpx; color: #274654; font-size: 26rpx; margin-bottom: 14rpx; }
.textarea { min-height: 150rpx; }
.source-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-bottom: 14rpx; }
.source-action { min-height: 86rpx; display: flex; align-items: center; justify-content: center; gap: 10rpx; border: 1rpx solid #b9d4d8; border-radius: 12rpx; background: #f4faf9; color: #315d66; font-size: 24rpx; }
.source-action--record { border-color: #e4c5ca; background: #fff7f8; color: #8e4450; }
.source-action--record.recording { border-color: #a4515d; background: #a4515d; color: #fff; }
.source-action.disabled { opacity: .45; }
.file-row { display: flex; align-items: center; justify-content: space-between; padding: 20rpx; border: 1rpx dashed #9fc2c8; border-radius: 12rpx; background: #f4faf9; }
.file-copy { min-width: 0; max-width: 78%; }
.file-name, .file-meta { display: block; }
.file-name { color: #52717d; font-size: 23rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { margin-top: 6rpx; color: #84999f; font-size: 19rpx; }
.file-action, .refresh, .bind, .delete, .unbind { color: #167f78; font-size: 23rpx; }
.primary { margin-top: 18rpx; background: #177f78; color: #fff; border-radius: 12rpx; font-size: 27rpx; }
.primary[disabled] { opacity: .5; }
.small-tip { margin: 14rpx 0 0; font-size: 20rpx; }
.panel-head, .voice-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.panel-tools { display: flex; align-items: center; gap: 18rpx; flex-shrink: 0; }
.unbind { color: #a45e21; }
.voice-row { padding: 22rpx 0; border-bottom: 1rpx solid #edf1f2; }
.voice-row:last-child { border-bottom: 0; }
.voice-copy { min-width: 0; }
.voice-name, .voice-meta { display: block; }
.voice-name { color: #294a58; font-size: 27rpx; font-weight: 600; }
.voice-meta { margin-top: 6rpx; color: #82939a; font-size: 21rpx; }
.voice-actions { display: flex; gap: 18rpx; flex-shrink: 0; }
.bind.active { color: #16845b; font-weight: 700; }
.delete { color: #ba6672; }
.empty { padding: 30rpx 0 10rpx; color: #82939a; font-size: 23rpx; text-align: center; }
@media (max-width: 360px) {
  .source-actions { grid-template-columns: 1fr; }
  .panel-head { align-items: flex-start; }
  .panel-tools { flex-direction: column; align-items: flex-end; gap: 8rpx; }
}
</style>
