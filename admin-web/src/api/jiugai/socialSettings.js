import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getSocialSettings() {
  return sillyRequest({
    url: '/admin/jiugai/social-settings',
    method: 'get'
  })
}

export function updateSocialSettings(data) {
  return sillyRequest({
    url: '/admin/jiugai/social-settings',
    method: 'put',
    data
  })
}
