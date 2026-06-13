import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listAdminPermissionLog(query) {
  return sillyRequest({
    url: '/admin/system/permission-log/list',
    method: 'get',
    params: query
  })
}
