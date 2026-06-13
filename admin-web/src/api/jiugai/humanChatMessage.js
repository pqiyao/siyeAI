import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getHumanChatMessageMeta() {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-message/meta',
    method: 'get'
  })
}

export function listHumanChatMessage(query) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-message/list',
    method: 'get',
    params: query
  })
}

export function getHumanChatMessage(messageId) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-message/' + messageId,
    method: 'get'
  })
}

export function adminRecallHumanChatMessage(data) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-message/admin-recall',
    method: 'post',
    data
  })
}
