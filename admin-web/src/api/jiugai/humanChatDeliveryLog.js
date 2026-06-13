import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getHumanChatDeliveryLogMeta() {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-delivery-log/meta',
    method: 'get'
  })
}

export function listHumanChatDeliveryLog(query) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-delivery-log/list',
    method: 'get',
    params: query
  })
}

export function getHumanChatDeliveryLog(id) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-delivery-log/' + id,
    method: 'get'
  })
}
