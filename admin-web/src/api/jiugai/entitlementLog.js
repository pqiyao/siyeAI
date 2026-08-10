import request from '@/utils/request'

export function listJgEntitlementLog(query) {
  return request({
    url: '/admin/jiugai/entitlement-log/list',
    method: 'get',
    params: query
  })
}

export function listEntitlementWalletConsumption(query) {
  return request({
    url: '/admin/jiugai/entitlement-log/wallet-consumption',
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

export function batchDelJgEntitlementLog(ids) {
  return request({
    url: '/admin/jiugai/entitlement-log/batch',
    method: 'delete',
    data: { ids }
  })
}
