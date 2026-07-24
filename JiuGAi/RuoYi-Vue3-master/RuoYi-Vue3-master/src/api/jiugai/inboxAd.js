import request from '@/utils/request'

export function listJgInboxAd(query) {
  return request({
    url: '/admin/jiugai/inbox-ad/list',
    method: 'get',
    params: query
  })
}

export function getJgInboxAd(id) {
  return request({
    url: '/admin/jiugai/inbox-ad/' + id,
    method: 'get'
  })
}

export function addJgInboxAd(data) {
  return request({
    url: '/admin/jiugai/inbox-ad',
    method: 'post',
    data
  })
}

export function updateJgInboxAd(data) {
  return request({
    url: '/admin/jiugai/inbox-ad',
    method: 'put',
    data
  })
}

export function delJgInboxAd(ids) {
  return request({
    url: '/admin/jiugai/inbox-ad/' + ids,
    method: 'delete'
  })
}
