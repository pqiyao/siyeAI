import request from '@/utils/request'

export const getAgentRuntime = () => request({ url: '/admin/jiugai/agent/runtime', method: 'get' })
