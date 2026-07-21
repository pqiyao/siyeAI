import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listChatPreset(query) {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/list',
    method: 'get',
    params: query
  })
}

export function getChatPreset(id) {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/' + id,
    method: 'get'
  })
}

export function syncStChatPresets() {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/sync-st',
    method: 'post',
    timeout: 120000
  })
}

export function updateChatPresetStatus(id, enabled) {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/' + id + '/status',
    method: 'put',
    data: { enabled }
  })
}

export function updateChatPresetSort(id, sortOrder) {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/' + id + '/sort',
    method: 'put',
    data: { sortOrder }
  })
}

export function delChatPreset(id) {
  return sillyRequest({
    url: '/admin/jiugai/chat-preset/' + id,
    method: 'delete'
  })
}
