import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listCharacter(query) {
  return sillyRequest({
    url: '/admin/jiugai/character/list',
    method: 'get',
    params: query
  })
}

export function getUserCreatedCharacterStats(limit) {
  return sillyRequest({
    url: '/admin/jiugai/character/user-created-stats',
    method: 'get',
    params: { limit }
  })
}

export function getCharacter(id) {
  return sillyRequest({
    url: '/admin/jiugai/character/' + id,
    method: 'get'
  })
}

export function listCharacterWorldbookOptions() {
  return sillyRequest({
    url: '/admin/jiugai/character/worldbooks/options',
    method: 'get'
  })
}

export function addCharacter(data) {
  return sillyRequest({
    url: '/admin/jiugai/character',
    method: 'post',
    data
  })
}

export function updateCharacter(data) {
  return sillyRequest({
    url: '/admin/jiugai/character',
    method: 'put',
    data
  })
}

export function delCharacter(ids, options = {}) {
  return sillyRequest({
    url: '/admin/jiugai/character/' + ids,
    method: 'delete',
    params: {
      syncStFile: options.syncStFile === true
    }
  })
}

export function reviewCharacter(data) {
  return sillyRequest({
    url: '/admin/jiugai/character/review',
    method: 'post',
    data
  })
}

export function batchEvictCharacterLoreCache(characterIds) {
  return sillyRequest({
    url: '/admin/jiugai/character/batch-evict-lore-cache',
    method: 'post',
    data: { characterIds }
  })
}

export function importSillyTavernJson(data) {
  return sillyRequest({
    url: '/admin/jiugai/character/import-sillytavern',
    method: 'post',
    data,
    timeout: 120000
  })
}

export function importSillyTavernPng(file) {
  const fd = new FormData()
  fd.append('file', file)
  return sillyRequest({
    url: '/admin/jiugai/character/import-sillytavern-png',
    method: 'post',
    data: fd,
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    }
  })
}
