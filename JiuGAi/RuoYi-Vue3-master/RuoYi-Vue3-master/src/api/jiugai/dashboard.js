import request from '@/utils/request'

export function getJgDashboardOverview(trendRange) {
  return request({
    url: '/admin/jiugai/dashboard/overview',
    method: 'get',
    params: trendRange ? { trendRange } : undefined
  })
}
