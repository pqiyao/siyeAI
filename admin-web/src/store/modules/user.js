import { ElMessage } from 'element-plus'
import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from '@/utils/validate'
import defAva from '@/assets/images/profile.jpg'

const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    id: '',
    name: '',
    nickName: '',
    avatar: '',
    roles: [],
    permissions: []
  }),
  actions: {
    resetAuthState() {
      this.token = ''
      this.id = ''
      this.name = ''
      this.nickName = ''
      this.avatar = ''
      this.roles = []
      this.permissions = []
      removeToken()
    },
    login(userInfo) {
      const username = userInfo.username.trim()
      const password = userInfo.password
      const code = userInfo.code
      const uuid = userInfo.uuid
      return new Promise((resolve, reject) => {
        login(username, password, code, uuid)
          .then((res) => {
            setToken(res.token)
            this.token = res.token
            resolve()
          })
          .catch((error) => {
            reject(error)
          })
      })
    },
    getInfo() {
      return new Promise((resolve, reject) => {
        getInfo()
          .then((res) => {
            const user = res.user || {}
            let avatar = user.avatar || ''
            if (!isHttp(avatar)) {
              avatar = isEmpty(avatar) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) {
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName
            this.avatar = avatar

            if (res.isDefaultModifyPwd) {
              ElMessage.warning('当前后台账号仍使用初始密码，建议尽快修改。')
            } else if (res.isPasswordExpired) {
              ElMessage.warning('当前后台账号密码已过期，请尽快在后端配置中更新。')
            }
            resolve(res)
          })
          .catch((error) => {
            reject(error)
          })
      })
    },
    logOut() {
      return new Promise((resolve) => {
        const done = () => {
          this.resetAuthState()
          resolve()
        }
        if (!this.token) {
          done()
          return
        }
        logout(this.token)
          .then(() => {
            done()
          })
          .catch(() => {
            done()
          })
      })
    }
  }
})

export default useUserStore
