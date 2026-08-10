<template>
  <div :class="['sidebar-theme-wrapper', { 'has-logo': showLogo }, sideTheme]" class="sidebar-container">
    <div class="sidebar-shell">
      <logo v-if="showLogo" :collapse="isCollapse" />
      <div v-if="!isCollapse" class="sidebar-caption">
        <span class="sidebar-caption-dot"></span>
        <span class="sidebar-caption-text">运营导航</span>
      </div>
      <el-scrollbar wrap-class="scrollbar-wrapper">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :background-color="getMenuBackground"
          :text-color="getMenuTextColor"
          :unique-opened="true"
          :active-text-color="theme"
          :collapse-transition="false"
          mode="vertical"
          :class="sideTheme"
        >
          <sidebar-item
            v-for="(route, index) in sidebarRouters"
            :key="route.path + index"
            :item="route"
            :base-path="route.path"
          />
        </el-menu>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const showLogo = computed(() => settingsStore.sidebarLogo)
const sideTheme = computed(() => settingsStore.sideTheme)
const theme = computed(() => settingsStore.theme)
const isCollapse = computed(() => !appStore.sidebar.opened)

const getMenuBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

const getMenuTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuText : variables.menuLightText
})

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})
</script>

<style lang="scss" scoped>
.sidebar-container {
  height: 100%;
  background:
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.08), transparent 26%),
    linear-gradient(180deg, rgba(16, 23, 42, 0.98) 0%, rgba(15, 23, 42, 1) 100%);
  border-right: 1px solid rgba(148, 163, 184, 0.12);
}

.sidebar-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-caption {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 18px 10px;
  padding: 0 6px;
}

.sidebar-caption-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: linear-gradient(135deg, #38bdf8 0%, #22c55e 100%);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
}

.sidebar-caption-text {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(148, 163, 184, 0.82);
}

:deep(.scrollbar-wrapper) {
  padding: 0 10px 16px;
  background: transparent;
}

:deep(.el-scrollbar__view) {
  height: 100%;
}

:deep(.el-menu) {
  width: 100% !important;
  border: none;
  background: transparent !important;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  margin: 4px 0;
  border-radius: 14px;
  color: rgba(226, 232, 240, 0.88) !important;
  font-weight: 500;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(59, 130, 246, 0.12) !important;
  color: #ffffff !important;
  transform: translateX(1px);
}

:deep(.el-menu-item .svg-icon),
:deep(.el-sub-menu__title .svg-icon) {
  margin-right: 12px;
  color: rgba(147, 197, 253, 0.94);
}

:deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  background: rgba(15, 23, 42, 0.82) !important;
  color: #f8fafc !important;
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.16);
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.95) 0%, rgba(14, 165, 233, 0.84) 100%) !important;
  color: #ffffff !important;
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.22);
}

:deep(.el-menu-item.is-active .svg-icon) {
  color: #ffffff !important;
}

:deep(.el-sub-menu .el-menu) {
  background: transparent !important;
}

:deep(.el-sub-menu .el-menu-item) {
  margin-left: 14px;
  padding-right: 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid transparent;
}

:deep(.el-sub-menu .el-menu-item:hover) {
  border-color: rgba(59, 130, 246, 0.18);
}

:deep(.menu-title) {
  font-size: 14px;
  letter-spacing: 0.01em;
}

:deep(.nest-menu .menu-title) {
  font-size: 13px;
}

:deep(.hideSidebar .sidebar-container .el-menu-item),
:deep(.hideSidebar .sidebar-container .el-sub-menu__title) {
  margin-left: 6px;
  margin-right: 6px;
}
</style>
