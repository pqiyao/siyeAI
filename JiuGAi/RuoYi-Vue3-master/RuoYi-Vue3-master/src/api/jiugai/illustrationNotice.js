import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listIllustrationNotice(query) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-notice/list',
    method: 'get',
    params: query
  })
}

export function getIllustrationNotice(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-notice/' + id,
    method: 'get'
  })
}

export function addIllustrationNotice(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-notice',
    method: 'post',
    data
  })
}

export function updateIllustrationNotice(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-notice',
    method: 'put',
    data
  })
}

export function deleteIllustrationNotice(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-notice/' + id,
    method: 'delete'
  })
}
