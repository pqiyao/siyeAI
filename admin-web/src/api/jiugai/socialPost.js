import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getSocialPostMeta() {
  return sillyRequest({
    url: '/admin/jiugai/social-post/meta',
    method: 'get'
  })
}

export function listSocialPost(query) {
  return sillyRequest({
    url: '/admin/jiugai/social-post/list',
    method: 'get',
    params: query
  })
}

export function getSocialPost(postId) {
  return sillyRequest({
    url: '/admin/jiugai/social-post/' + postId,
    method: 'get'
  })
}

export function updateSocialPostStatus(data) {
  return sillyRequest({
    url: '/admin/jiugai/social-post/status',
    method: 'put',
    data
  })
}

export function delSocialPost(ids) {
  return sillyRequest({
    url: '/admin/jiugai/social-post/' + ids,
    method: 'delete'
  })
}
