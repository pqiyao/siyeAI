import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listStoreOrder(query) {
  return sillyRequest({
    url: '/admin/jiugai/store-order/list',
    method: 'get',
    params: query
  })
}

export function getStoreOrder(orderNo) {
  return sillyRequest({
    url: '/admin/jiugai/store-order/' + orderNo,
    method: 'get'
  })
}
