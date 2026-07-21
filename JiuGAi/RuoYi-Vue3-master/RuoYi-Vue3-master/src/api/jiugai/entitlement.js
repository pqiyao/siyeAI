import request from '@/utils/request'

export function getEntitlementPolicy() {
  return request({
    url: '/admin/jiugai/entitlement',
    method: 'get'
  })
}

export function updateEntitlementPolicy(data) {
  return request({
    url: '/admin/jiugai/entitlement',
    method: 'put',
    data
  })
}

export function getEntitlementRuntimeSettings() {
  return request({
    url: '/admin/jiugai/entitlement/runtime-settings',
    method: 'get'
  })
}

export function updateEntitlementRuntimeSettings(data) {
  return request({
    url: '/admin/jiugai/entitlement/runtime-settings',
    method: 'put',
    data
  })
}

export function getImageGenerationSettings() {
  return request({
    url: '/admin/jiugai/entitlement/image-generation-settings',
    method: 'get'
  })
}

export function updateImageGenerationSettings(data) {
  return request({
    url: '/admin/jiugai/entitlement/image-generation-settings',
    method: 'put',
    data
  })
}
