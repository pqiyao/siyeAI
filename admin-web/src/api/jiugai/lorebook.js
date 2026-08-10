import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listLorebook(query) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook/list',
    method: 'get',
    params: query
  })
}

export function getLorebook(id) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook/' + id,
    method: 'get'
  })
}

export function addLorebook(data) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook',
    method: 'post',
    data
  })
}

export function updateLorebook(data) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook',
    method: 'put',
    data
  })
}

export function delLorebook(ids) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook/' + ids,
    method: 'delete'
  })
}

/** 批量启用/禁用 */
export function batchLorebookEnabled(ids, enabled) {
  return sillyRequest({
    url: '/admin/jiugai/lorebook/batch-enabled',
    method: 'post',
    data: { ids, enabled }
  })
}
