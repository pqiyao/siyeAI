import request from '@/utils/request'

export const getAgentRuntime = () => request({ url: '/admin/jiugai/agent/runtime', method: 'get' })
export const getAgentReleaseReadiness = () => request({ url: '/admin/jiugai/agent/release-readiness', method: 'get' })
