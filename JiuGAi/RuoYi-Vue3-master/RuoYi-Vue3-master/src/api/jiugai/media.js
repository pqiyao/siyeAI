import request from '@/utils/request'

export function getMediaImagePolicy() {
  return request({ url: '/admin/jiugai/media/image-policy', method: 'get' })
}

export function updateMediaImagePolicy(data) {
  return request({ url: '/admin/jiugai/media/image-policy', method: 'put', data })
}

export function listCharacterImagePolicies(params) {
  return request({ url: '/admin/jiugai/media/image-policy/characters', method: 'get', params })
}

export function getCharacterImagePolicy(characterId) {
  return request({ url: `/admin/jiugai/media/image-policy/characters/${characterId}`, method: 'get' })
}

export function updateCharacterImagePolicy(characterId, data) {
  return request({ url: `/admin/jiugai/media/image-policy/characters/${characterId}`, method: 'put', data })
}

export function deleteCharacterImagePolicy(characterId) {
  return request({ url: `/admin/jiugai/media/image-policy/characters/${characterId}`, method: 'delete' })
}

export function getMediaVoicePolicy() {
  return request({ url: '/admin/jiugai/media/voice-policy', method: 'get' })
}

export function updateMediaVoicePolicy(data) {
  return request({ url: '/admin/jiugai/media/voice-policy', method: 'put', data })
}
