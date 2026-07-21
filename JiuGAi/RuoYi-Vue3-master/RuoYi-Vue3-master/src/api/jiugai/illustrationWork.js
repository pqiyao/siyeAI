import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getIllustrationWorkMeta() {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work/meta',
    method: 'get'
  })
}

export function listIllustrationWork(query) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work/list',
    method: 'get',
    params: query
  })
}

export function getIllustrationWork(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work/' + id,
    method: 'get'
  })
}

export function addIllustrationWork(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work',
    method: 'post',
    data
  })
}

export function updateIllustrationWork(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work',
    method: 'put',
    data
  })
}

export function updateIllustrationWorkStatus(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work/status',
    method: 'put',
    data
  })
}

export function deleteIllustrationWork(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-work/' + id,
    method: 'delete'
  })
}
