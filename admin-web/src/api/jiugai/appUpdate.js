import request from '@/utils/request'

export function listAppReleases(query) {
  return request({ url: '/admin/jiugai/app-update/list', method: 'get', params: query })
}

export function getAppRelease(id) {
  return request({ url: `/admin/jiugai/app-update/${id}`, method: 'get' })
}

export function addAppRelease(data) {
  return request({ url: '/admin/jiugai/app-update', method: 'post', data })
}

export function updateAppRelease(data) {
  return request({ url: '/admin/jiugai/app-update', method: 'put', data })
}

export function publishAppRelease(id) {
  return request({ url: `/admin/jiugai/app-update/${id}/publish`, method: 'put' })
}

export function revokeAppRelease(id) {
  return request({ url: `/admin/jiugai/app-update/${id}/revoke`, method: 'put' })
}

export function remindAppReleaseAgain(id) {
  return request({ url: `/admin/jiugai/app-update/${id}/remind-again`, method: 'put' })
}

export function deleteAppRelease(id) {
  return request({ url: `/admin/jiugai/app-update/${id}`, method: 'delete' })
}
