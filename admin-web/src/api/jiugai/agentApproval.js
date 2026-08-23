import request from '@/utils/request'

export const listAgentApprovals = (params) => request({ url: '/admin/jiugai/agent/approvals', method: 'get', params })
export const approveAgentApproval = (id, data) => request({ url: `/admin/jiugai/agent/approvals/${id}/approve`, method: 'post', data })
export const rejectAgentApproval = (id, data) => request({ url: `/admin/jiugai/agent/approvals/${id}/reject`, method: 'post', data })
