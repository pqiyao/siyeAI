import request from '@/utils/request'

export function listTtsVoiceTemplates(params) {
  return request({
    url: '/admin/jiugai/tts-voice-template/list',
    method: 'get',
    params
  })
}

export function getTtsVoiceTemplate(id) {
  return request({
    url: '/admin/jiugai/tts-voice-template/' + id,
    method: 'get'
  })
}

export function addTtsVoiceTemplate(data) {
  return request({
    url: '/admin/jiugai/tts-voice-template',
    method: 'post',
    data
  })
}

export function updateTtsVoiceTemplate(data) {
  return request({
    url: '/admin/jiugai/tts-voice-template',
    method: 'put',
    data
  })
}

export function deleteTtsVoiceTemplate(id) {
  return request({
    url: '/admin/jiugai/tts-voice-template/' + id,
    method: 'delete'
  })
}
