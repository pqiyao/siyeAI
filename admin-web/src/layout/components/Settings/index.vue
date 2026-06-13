<template>
  <el-drawer v-model="showSettings" :with-header="false" :lock-scroll="false" direction="rtl" size="320px">
    <div class="setting-drawer-title">
      <h3 class="drawer-title">后台界面设置</h3>
      <p class="drawer-copy">仅保留当前运营后台仍在使用的布局开关，去掉了若依模板原有的多导航模式分支。</p>
    </div>

    <div class="setting-drawer-title">
      <h3 class="drawer-title">主题风格</h3>
    </div>
    <div class="setting-drawer-block-checbox">
      <div class="setting-drawer-block-checbox-item" @click="handleTheme('theme-dark')">
        <img src="@/assets/images/dark.svg" alt="dark" />
        <div v-if="sideTheme === 'theme-dark'" class="setting-drawer-block-checbox-selectIcon">
          <i aria-label="图标: check" class="anticon anticon-check">
            <svg viewBox="64 64 896 896" data-icon="check" width="1em" height="1em" :fill="theme" aria-hidden="true" focusable="false">
              <path d="M912 190h-69.9c-9.8 0-19.1 4.5-25.1 12.2L404.7 724.5 207 474a32 32 0 0 0-25.1-12.2H112c-6.7 0-10.4 7.7-6.3 12.9l273.9 347c12.8 16.2 37.4 16.2 50.3 0l488.4-618.9c4.1-5.1.4-12.8-6.3-12.8z" />
            </svg>
          </i>
        </div>
      </div>
      <div class="setting-drawer-block-checbox-item" @click="handleTheme('theme-light')">
        <img src="@/assets/images/light.svg" alt="light" />
        <div v-if="sideTheme === 'theme-light'" class="setting-drawer-block-checbox-selectIcon">
          <i aria-label="图标: check" class="anticon anticon-check">
            <svg viewBox="64 64 896 896" data-icon="check" width="1em" height="1em" :fill="theme" aria-hidden="true" focusable="false">
              <path d="M912 190h-69.9c-9.8 0-19.1 4.5-25.1 12.2L404.7 724.5 207 474a32 32 0 0 0-25.1-12.2H112c-6.7 0-10.4 7.7-6.3 12.9l273.9 347c12.8 16.2 37.4 16.2 50.3 0l488.4-618.9c4.1-5.1.4-12.8-6.3-12.8z" />
            </svg>
          </i>
        </div>
      </div>
    </div>

    <div class="drawer-item">
      <span>主题色</span>
      <span class="comp-style">
        <el-color-picker v-model="theme" :predefine="predefineColors" @change="themeChange" />
      </span>
    </div>

    <el-divider />

    <h3 class="drawer-title">界面布局</h3>

    <div class="drawer-item">
      <span>开启标签页</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.tagsView" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>持久化标签页</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.tagsViewPersist" :disabled="!settingsStore.tagsView" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>显示标签图标</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.tagsIcon" :disabled="!settingsStore.tagsView" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>固定顶部栏</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.fixedHeader" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>显示侧栏 Logo</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.sidebarLogo" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>动态标题</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.dynamicTitle" @change="dynamicTitleChange" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>显示底部版权</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.footerVisible" class="drawer-switch" />
      </span>
    </div>

    <el-divider />

    <el-button type="primary" plain icon="DocumentAdd" @click="saveSetting">保存配置</el-button>
    <el-button plain icon="Refresh" @click="resetSetting">重置配置</el-button>
  </el-drawer>
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

const { proxy } = getCurrentInstance()
const settingsStore = useSettingsStore()
const showSettings = ref(false)
const theme = ref(settingsStore.theme)
const sideTheme = ref(settingsStore.sideTheme)
const storeSettings = computed(() => settingsStore)
const predefineColors = ref(['#409EFF', '#ff4500', '#ff8c00', '#ffd700', '#90ee90', '#00ced1', '#1e90ff', '#c71585'])

function dynamicTitleChange() {
  settingsStore.setTitle(settingsStore.title)
}

function themeChange(value) {
  settingsStore.theme = value
  handleThemeStyle(value)
}

function handleTheme(value) {
  settingsStore.sideTheme = value
  sideTheme.value = value
}

function saveSetting() {
  proxy.$modal.loading('正在保存本地布局配置，请稍候...')
  if (!settingsStore.tagsViewPersist) {
    proxy.$cache.local.remove('tags-view-visited')
  }
  const layoutSetting = {
    tagsView: storeSettings.value.tagsView,
    tagsIcon: storeSettings.value.tagsIcon,
    tagsViewPersist: storeSettings.value.tagsViewPersist,
    fixedHeader: storeSettings.value.fixedHeader,
    sidebarLogo: storeSettings.value.sidebarLogo,
    dynamicTitle: storeSettings.value.dynamicTitle,
    footerVisible: storeSettings.value.footerVisible,
    sideTheme: storeSettings.value.sideTheme,
    theme: storeSettings.value.theme
  }
  localStorage.setItem('layout-setting', JSON.stringify(layoutSetting))
  setTimeout(() => proxy.$modal.closeLoading(), 300)
}

function resetSetting() {
  proxy.$cache.local.remove('tags-view-visited')
  proxy.$modal.loading('正在重置本地布局配置，请稍候...')
  localStorage.removeItem('layout-setting')
  setTimeout(() => window.location.reload(), 300)
}

function openSetting() {
  showSettings.value = true
}

defineExpose({
  openSetting
})
</script>

<style lang="scss" scoped>
.setting-drawer-title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary, rgba(0, 0, 0, 0.85));
  line-height: 22px;

  .drawer-title {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
  }

  .drawer-copy {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }
}

.setting-drawer-block-checbox {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-top: 10px;
  margin-bottom: 20px;

  .setting-drawer-block-checbox-item {
    position: relative;
    margin-right: 16px;
    border-radius: 2px;
    cursor: pointer;

    img {
      width: 48px;
      height: 48px;
    }

    .setting-drawer-block-checbox-selectIcon {
      position: absolute;
      top: 0;
      right: 0;
      width: 100%;
      height: 100%;
      padding-top: 15px;
      padding-left: 24px;
      color: #1890ff;
      font-weight: 700;
      font-size: 14px;
    }
  }
}

.drawer-item {
  color: var(--el-text-color-regular, rgba(0, 0, 0, 0.65));
  padding: 12px 0;
  font-size: 14px;

  .comp-style {
    float: right;
    margin: -3px 8px 0 0;
  }
}
</style>
