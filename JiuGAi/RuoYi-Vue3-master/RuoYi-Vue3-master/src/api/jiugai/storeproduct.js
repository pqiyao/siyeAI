import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listStoreProduct(query) {
  return sillyRequest({
    url: '/admin/jiugai/store-product/list',
    method: 'get',
    params: query
  })
}

export function getStoreProduct(id) {
  return sillyRequest({
    url: '/admin/jiugai/store-product/' + id,
    method: 'get'
  })
}

export function addStoreProduct(data) {
  return sillyRequest({
    url: '/admin/jiugai/store-product',
    method: 'post',
    data
  })
}

export function updateStoreProduct(data) {
  return sillyRequest({
    url: '/admin/jiugai/store-product',
    method: 'put',
    data
  })
}
