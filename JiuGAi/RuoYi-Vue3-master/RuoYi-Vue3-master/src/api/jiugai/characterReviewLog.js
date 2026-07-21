import request from '@/utils/request'

export function listJgCharacterReviewLog(query) {
  return request({
    url: '/admin/jiugai/character-review-log/list',
    method: 'get',
    params: query
  })
}

export function summaryJgCharacterReviewLog() {
  return request({
    url: '/admin/jiugai/character-review-log/summary',
    method: 'get'
  })
}

export function delJgCharacterReviewLog(ids) {
  return request({
    url: '/admin/jiugai/character-review-log/' + ids,
    method: 'delete'
  })
}
