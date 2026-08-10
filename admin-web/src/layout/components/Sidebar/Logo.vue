<template>
  <div class="sidebar-logo-container" :class="{ collapse }">
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link sidebar-logo-link--collapse" to="/">
        <span class="sidebar-logo-badge">
          <img v-if="logo" :src="logo" class="sidebar-logo" />
          <span v-else class="sidebar-logo-fallback">{{ shortTitle }}</span>
        </span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <span class="sidebar-logo-badge">
          <img v-if="logo" :src="logo" class="sidebar-logo" />
          <span v-else class="sidebar-logo-fallback">{{ shortTitle }}</span>
        </span>
        <span class="sidebar-logo-copy">
          <h1 class="sidebar-title">{{ title }}</h1>
          <span class="sidebar-subtitle">角色、用户与商业化运营后台</span>
        </span>
      </router-link>
    </transition>
  </div>
</template>

<script setup>
import logo from '@/assets/logo/logo.png'
import useSettingsStore from '@/store/modules/settings'
import variables from '@/assets/styles/variables.module.scss'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const title = import.meta.env.VITE_APP_TITLE
const shortTitle = computed(() => String(title || '四叶').slice(0, 2))
const settingsStore = useSettingsStore()
const sideTheme = computed(() => settingsStore.sideTheme)

const getLogoBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

const getLogoTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? '#fff' : variables.menuLightText
})
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 0.5s ease;
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  padding: 18px 16px 12px;
  background: v-bind(getLogoBackground);
}

.sidebar-logo-link {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 56px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(148, 163, 184, 0.12);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.sidebar-logo-link--collapse {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}

.sidebar-logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.24) 0%, rgba(34, 197, 94, 0.18) 100%);
  box-shadow: 0 12px 28px rgba(14, 165, 233, 0.14);
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar-logo {
  width: 28px;
  height: 28px;
}

.sidebar-logo-fallback {
  font-size: 15px;
  font-weight: 700;
  color: #e0f2fe;
}

.sidebar-logo-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-title {
  margin: 0;
  color: v-bind(getLogoTextColor);
  font-weight: 700;
  line-height: 1.2;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-subtitle {
  font-size: 12px;
  color: rgba(148, 163, 184, 0.86);
  line-height: 1.3;
}

.collapse {
  padding-left: 10px;
  padding-right: 10px;
}
</style>
