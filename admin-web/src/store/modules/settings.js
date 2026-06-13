import defaultSettings from '@/settings'
import { useDark, useToggle } from '@vueuse/core'
import { useDynamicTitle } from '@/utils/dynamicTitle'

const isDark = useDark()
const toggleDark = useToggle(isDark)

const {
  sideTheme,
  showSettings,
  tagsView,
  tagsViewPersist,
  tagsIcon,
  fixedHeader,
  sidebarLogo,
  dynamicTitle,
  footerVisible,
  footerContent
} = defaultSettings

const storageSetting = JSON.parse(localStorage.getItem('layout-setting') || '{}')

const useSettingsStore = defineStore('settings', {
  state: () => ({
    title: '',
    theme: storageSetting.theme || '#409EFF',
    sideTheme: storageSetting.sideTheme || sideTheme,
    showSettings,
    tagsView: storageSetting.tagsView === undefined ? tagsView : storageSetting.tagsView,
    tagsViewPersist: storageSetting.tagsViewPersist === undefined ? tagsViewPersist : storageSetting.tagsViewPersist,
    tagsIcon: storageSetting.tagsIcon === undefined ? tagsIcon : storageSetting.tagsIcon,
    fixedHeader: storageSetting.fixedHeader === undefined ? fixedHeader : storageSetting.fixedHeader,
    sidebarLogo: storageSetting.sidebarLogo === undefined ? sidebarLogo : storageSetting.sidebarLogo,
    dynamicTitle: storageSetting.dynamicTitle === undefined ? dynamicTitle : storageSetting.dynamicTitle,
    footerVisible: storageSetting.footerVisible === undefined ? footerVisible : storageSetting.footerVisible,
    footerContent,
    isDark: isDark.value
  }),
  actions: {
    changeSetting(data) {
      const { key, value } = data
      if (Object.prototype.hasOwnProperty.call(this, key)) {
        this[key] = value
      }
    },
    setTitle(title) {
      this.title = title
      useDynamicTitle()
    },
    toggleTheme() {
      this.isDark = !this.isDark
      toggleDark()
    }
  }
})

export default useSettingsStore
