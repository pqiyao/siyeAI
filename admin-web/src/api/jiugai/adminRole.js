import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getAdminRoleMeta() {
  return sillyRequest({
    url: '/admin/system/admin-role/meta',
    method: 'get'
  })
}

export function listAdminRole(query) {
  return sillyRequest({
    url: '/admin/system/admin-role/list',
    method: 'get',
    params: query
  })
}

export function getAdminRole(id) {
  return sillyRequest({
    url: '/admin/system/admin-role/' + id,
    method: 'get'
  })
}

export function addAdminRole(data) {
  return sillyRequest({
    url: '/admin/system/admin-role',
    method: 'post',
    data
  })
}

export function updateAdminRole(data) {
  return sillyRequest({
    url: '/admin/system/admin-role',
    method: 'put',
    data
  })
}

export function updateAdminRoleStatus(data) {
  return sillyRequest({
    url: '/admin/system/admin-role/status',
    method: 'put',
    data
  })
}

export function removeAdminRole(ids) {
  return sillyRequest({
    url: '/admin/system/admin-role/' + ids,
    method: 'delete'
  })
}
