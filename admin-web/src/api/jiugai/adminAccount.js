import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getAdminAccountMeta() {
  return sillyRequest({
    url: '/admin/system/admin-account/meta',
    method: 'get'
  })
}

export function listAdminAccount(query) {
  return sillyRequest({
    url: '/admin/system/admin-account/list',
    method: 'get',
    params: query
  })
}

export function getAdminAccount(id) {
  return sillyRequest({
    url: '/admin/system/admin-account/' + id,
    method: 'get'
  })
}

export function addAdminAccount(data) {
  return sillyRequest({
    url: '/admin/system/admin-account',
    method: 'post',
    data
  })
}

export function updateAdminAccount(data) {
  return sillyRequest({
    url: '/admin/system/admin-account',
    method: 'put',
    data
  })
}

export function updateAdminAccountStatus(data) {
  return sillyRequest({
    url: '/admin/system/admin-account/status',
    method: 'put',
    data
  })
}

export function resetAdminAccountPassword(data) {
  return sillyRequest({
    url: '/admin/system/admin-account/reset-password',
    method: 'put',
    data
  })
}

export function removeAdminAccount(ids) {
  return sillyRequest({
    url: '/admin/system/admin-account/' + ids,
    method: 'delete'
  })
}
