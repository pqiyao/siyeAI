import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listTagLibrary(query) {
  return sillyRequest({
    url: '/admin/jiugai/tag/list',
    method: 'get',
    params: query
  })
}

export function listTagOptions() {
  return sillyRequest({
    url: '/admin/jiugai/tag/options',
    method: 'get'
  })
}

export function getTagLibrary(id) {
  return sillyRequest({
    url: '/admin/jiugai/tag/' + id,
    method: 'get'
  })
}

export function addTagLibrary(data) {
  return sillyRequest({
    url: '/admin/jiugai/tag',
    method: 'post',
    data
  })
}

export function updateTagLibrary(data) {
  return sillyRequest({
    url: '/admin/jiugai/tag',
    method: 'put',
    data
  })
}

export function delTagLibrary(id) {
  return sillyRequest({
    url: '/admin/jiugai/tag/' + id,
    method: 'delete'
  })
}

export function batchDelTagLibrary(ids) {
  return sillyRequest({
    url: '/admin/jiugai/tag/batch-delete',
    method: 'post',
    data: { ids }
  })
}

export function syncExistingTagLibrary() {
  return sillyRequest({
    url: '/admin/jiugai/tag/sync-existing',
    method: 'post'
  })
}
