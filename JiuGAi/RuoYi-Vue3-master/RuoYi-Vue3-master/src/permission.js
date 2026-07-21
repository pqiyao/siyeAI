import router from './router'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isHttp, isPathMatch } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login']

function isWhiteList(path) {
  return whiteList.some((pattern) => isPathMatch(pattern, path))
}

router.beforeEach((to, from, next) => {
  NProgress.start()
  if (getToken()) {
    if (to.meta.title) {
      useSettingsStore().setTitle(to.meta.title)
    }
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
      return
    }
    if (isWhiteList(to.path)) {
      next()
      return
    }
    if (useUserStore().roles.length === 0) {
      isRelogin.show = true
      useUserStore()
        .getInfo()
        .then(() => {
          isRelogin.show = false
          usePermissionStore().generateRoutes().then((accessRoutes) => {
            accessRoutes.forEach((route) => {
              if (!isHttp(route.path)) {
                router.addRoute(route)
              }
            })
            next({ ...to, replace: true })
          })
        })
        .catch((error) => {
          useUserStore().logOut().then(() => {
            ElMessage.error(error?.message || error || '登录状态已失效，请重新登录')
            next({ path: '/' })
          })
        })
      return
    }
    next()
    return
  }

  if (isWhiteList(to.path)) {
    next()
  } else {
    next(`/login?redirect=${to.fullPath}`)
    NProgress.done()
  }
})

router.afterEach(() => {
  NProgress.done()
})
