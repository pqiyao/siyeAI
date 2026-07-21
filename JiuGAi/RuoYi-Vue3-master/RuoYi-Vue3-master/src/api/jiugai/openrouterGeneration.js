import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getOpenRouterGeneration() {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation',
    method: 'get'
  })
}

export function updateOpenRouterGeneration(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation',
    method: 'put',
    data
  })
}

export function saveModelProvider(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation/provider',
    method: 'put',
    data
  })
}

export function deleteModelProvider(id) {
  return sillyRequest({
    url: `/admin/jiugai/openrouter-generation/provider/${id}`,
    method: 'delete'
  })
}

export function saveModelRoute(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation/route',
    method: 'put',
    data
  })
}

export function deleteModelRoute(id) {
  return sillyRequest({
    url: `/admin/jiugai/openrouter-generation/route/${id}`,
    method: 'delete'
  })
}
