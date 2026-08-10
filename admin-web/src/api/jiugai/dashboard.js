import request from '@/utils/request'

export function getJgDashboardOverview(trendRange) {
  return request({
    url: '/admin/jiugai/dashboard/overview',
    method: 'get',
    params: trendRange ? { trendRange } : undefined
  })
}

export function listGenerationModelPricing() {
  return request({
    url: '/admin/jiugai/dashboard/model-pricing',
    method: 'get'
  })
}

export function saveGenerationModelPricing(data) {
  return request({
    url: '/admin/jiugai/dashboard/model-pricing',
    method: 'post',
    data
  })
}

export function deleteGenerationModelPricing(id) {
  return request({
    url: `/admin/jiugai/dashboard/model-pricing/${id}`,
    method: 'delete'
  })
}
