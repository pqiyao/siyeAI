import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listPaymentChannels() {
  return sillyRequest({
    url: '/admin/jiugai/payment-channel/list',
    method: 'get'
  })
}

export function updatePaymentChannel(data) {
  return sillyRequest({
    url: '/admin/jiugai/payment-channel',
    method: 'put',
    data
  })
}
