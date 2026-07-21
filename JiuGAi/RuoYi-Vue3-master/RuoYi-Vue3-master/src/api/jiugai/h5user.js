import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listJgH5User(query) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user/list',
    method: 'get',
    params: query
  })
}

export function getJgH5User(id) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user/' + id,
    method: 'get'
  })
}

export function addJgH5User(data) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user',
    method: 'post',
    data
  })
}

export function updateJgH5User(data) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user',
    method: 'put',
    data
  })
}

export function updateJgConversationWorldbooks(conversationId, data) {
  return sillyRequest({
    url: '/admin/jiugai/conversations/' + conversationId + '/worldbooks',
    method: 'put',
    data
  })
}

export function listJgConversationWorldbookOptions() {
  return sillyRequest({
    url: '/admin/jiugai/conversations/worldbooks/options',
    method: 'get'
  })
}

export function updateJgConversationStDisplayName(conversationId, data) {
  return sillyRequest({
    url: '/admin/jiugai/conversations/' + conversationId + '/st-display-name',
    method: 'put',
    data
  })
}

export function resetJgH5UserPassword(data) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user/reset-password',
    method: 'post',
    data
  })
}

export function batchUpdateJgH5UserCharacterCreateAllowed(data) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user/character-create-allowed/batch',
    method: 'put',
    data
  })
}

export function delJgH5User(ids) {
  return sillyRequest({
    url: '/admin/jiugai/h5-user/' + ids,
    method: 'delete'
  })
}
