import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getCheckinActivity() {
  return sillyRequest({
    url: '/admin/jiugai/checkin/activity',
    method: 'get'
  })
}

export function saveCheckinActivity(data) {
  return sillyRequest({
    url: '/admin/jiugai/checkin/activity',
    method: 'put',
    data
  })
}

export function listCheckinClaims(query) {
  return sillyRequest({
    url: '/admin/jiugai/checkin/claims/list',
    method: 'get',
    params: query
  })
}
