import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listIllustrationAccessKey(query) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-access-key/list',
    method: 'get',
    params: query
  })
}

export function generateIllustrationAccessKey(data) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-access-key/generate',
    method: 'post',
    data
  })
}

export function disableIllustrationAccessKey(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-access-key/disable/' + id,
    method: 'put'
  })
}

export function deleteIllustrationAccessKey(id) {
  return sillyRequest({
    url: '/admin/jiugai/illustration-access-key/' + id,
    method: 'delete'
  })
}
