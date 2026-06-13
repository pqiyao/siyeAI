import request from '@/utils/request'

export function listJgEntitlementLog(query) {
  return request({
    url: '/admin/jiugai/entitlement-log/list',
    method: 'get',
    params: query
  })
}

export function delJgEntitlementLog(ids) {
  return request({
    url: '/admin/jiugai/entitlement-log/' + ids,
    method: 'delete'
  })
}
