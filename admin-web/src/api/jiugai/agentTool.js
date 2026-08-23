import request from '@/utils/request'

export const listAgentTools = (params) => request({ url: '/admin/jiugai/agent/tools', method: 'get', params })
export const getAgentTool = (id) => request({ url: `/admin/jiugai/agent/tools/${id}`, method: 'get' })
