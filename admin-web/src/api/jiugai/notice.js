import request from '@/utils/request'

export function listJgNotice(query) {
  return request({
    url: '/admin/jiugai/notice/list',
    method: 'get',
    params: query
  })
}

export function getJgNotice(id) {
  return request({
    url: '/admin/jiugai/notice/' + id,
    method: 'get'
  })
}

export function addJgNotice(data) {
  return request({
    url: '/admin/jiugai/notice',
    method: 'post',
    data
  })
}

export function updateJgNotice(data) {
  return request({
    url: '/admin/jiugai/notice',
    method: 'put',
    data
  })
}

export function delJgNotice(ids) {
  return request({
    url: '/admin/jiugai/notice/' + ids,
    method: 'delete'
  })
}
