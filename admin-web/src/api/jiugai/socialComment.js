import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getSocialCommentMeta() {
  return sillyRequest({
    url: '/admin/jiugai/social-comment/meta',
    method: 'get'
  })
}

export function listSocialComment(query) {
  return sillyRequest({
    url: '/admin/jiugai/social-comment/list',
    method: 'get',
    params: query
  })
}

export function getSocialComment(commentId) {
  return sillyRequest({
    url: '/admin/jiugai/social-comment/' + commentId,
    method: 'get'
  })
}

export function delSocialComment(ids) {
  return sillyRequest({
    url: '/admin/jiugai/social-comment/' + ids,
    method: 'delete'
  })
}
