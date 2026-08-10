import request from '@/utils/request'

export function listJgAiLog(query) {
  return request({
    url: '/admin/jiugai/ai-log/list',
    method: 'get',
    params: query
  })
}

export function listAiTaskAttempts(taskId) {
  return request({
    url: '/admin/jiugai/ai-log/attempts/' + taskId,
    method: 'get'
  })
}

export function listStandaloneAiAttempts(query) {
  return request({
    url: '/admin/jiugai/ai-log/standalone/list',
    method: 'get',
    params: query
  })
}

export function listStandaloneAiRequestAttempts(requestId) {
  return request({
    url: '/admin/jiugai/ai-log/standalone/attempts/' + encodeURIComponent(requestId),
    method: 'get'
  })
}

export function cleanJgAiLog(beforeDays) {
  return request({
    url: '/admin/jiugai/ai-log/clean/' + beforeDays,
    method: 'delete'
  })
}
