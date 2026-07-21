import { createApp } from 'vue'

import Cookies from 'js-cookie'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import locale from 'element-plus/es/locale/lang/zh-cn'

import '@/assets/styles/index.scss'

import App from './App'
import store from './store'
import router from './router'
import directive from './directive'
import plugins from './plugins'
import { download } from '@/utils/request'

import 'virtual:svg-icons-register'
import SvgIcon from '@/components/SvgIcon'
import elementIcons from '@/components/SvgIcon/svgicon'

import './permission'

import { useDict } from '@/utils/dict'
import { getConfigKey } from '@/api/system/config'
import { parseTime, resetForm, addDateRange, handleTree, selectDictLabel, selectDictLabels } from '@/utils/ruoyi'

import Pagination from '@/components/Pagination'
import RightToolbar from '@/components/RightToolbar'

const app = createApp(App)

app.config.globalProperties.useDict = useDict
app.config.globalProperties.download = download
app.config.globalProperties.parseTime = parseTime
app.config.globalProperties.resetForm = resetForm
app.config.globalProperties.handleTree = handleTree
app.config.globalProperties.addDateRange = addDateRange
app.config.globalProperties.getConfigKey = getConfigKey
app.config.globalProperties.selectDictLabel = selectDictLabel
app.config.globalProperties.selectDictLabels = selectDictLabels

app.component('Pagination', Pagination)
app.component('RightToolbar', RightToolbar)

app.use(router)
app.use(store)
app.use(plugins)
app.use(elementIcons)
app.component('svg-icon', SvgIcon)

directive(app)

app.use(ElementPlus, {
  locale,
  size: Cookies.get('size') || 'default'
})

app.mount('#app')
