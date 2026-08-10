import request from '@/utils/request'
export const getVisitorRiskOverview = () => request({ url: '/admin/jiugai/visitor-risk/overview', method: 'get' })
export const listVisitorRisk = (params) => request({ url: '/admin/jiugai/visitor-risk/list', method: 'get', params })
export const getVisitorRiskEvents = (id) => request({ url: `/admin/jiugai/visitor-risk/${id}/events`, method: 'get' })
export const hardDeleteVisitorRisk = (ids) => request({
  url: '/admin/jiugai/visitor-risk/batch',
  method: 'delete',
  data: { ids }
})
