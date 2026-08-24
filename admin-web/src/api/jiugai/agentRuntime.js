import request from '@/utils/request'

export const getAgentRuntime = () => request({ url: '/admin/jiugai/agent/runtime', method: 'get' })
export const getAgentReleaseReadiness = () => request({ url: '/admin/jiugai/agent/release-readiness', method: 'get' })
export const getAgentDshStatus = () => request({ url: '/admin/jiugai/agent/dsh/status', method: 'get' })
export const callAgentDshReadRpc = (method, data = {}) => request({ url: `/admin/jiugai/agent/dsh/rpc/${encodeURIComponent(method)}`, method: 'post', data })
export const downloadAgentDshSession = (sessionId) => `${import.meta.env.VITE_APP_BASE_API || '/dev-api'}/admin/jiugai/agent/dsh/sessions/${encodeURIComponent(sessionId)}/export`
