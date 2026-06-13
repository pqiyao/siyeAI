import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getHumanChatConversationMeta() {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-conversation/meta',
    method: 'get'
  })
}

export function listHumanChatConversation(query) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-conversation/list',
    method: 'get',
    params: query
  })
}

export function getHumanChatConversation(conversationId) {
  return sillyRequest({
    url: '/admin/jiugai/human-chat-conversation/' + conversationId,
    method: 'get'
  })
}
