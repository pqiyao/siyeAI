import request from '@/utils/request'

export function listJgAiLog(query) {
  return request({
    url: '/admin/jiugai/ai-log/list',
    method: 'get',
    params: query
  })
}

export function cleanJgAiLog(beforeDays) {
  return request({
    url: '/admin/jiugai/ai-log/clean/' + beforeDays,
    method: 'delete'
  })
}
