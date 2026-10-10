import request from '@/utils/request'

export const getRemoteOpsConfig = () => request({ url: '/admin/jiugai/agent/remote-ops/config', method: 'get' })
export const saveRemoteOpsConfig = (data) => request({ url: '/admin/jiugai/agent/remote-ops/config', method: 'post', data })
export const testRemoteOpsServer = (serverId) => request({ url: `/admin/jiugai/agent/remote-ops/test/${encodeURIComponent(serverId)}`, method: 'post' })
export const deleteRemoteOpsServer = (serverId) => request({ url: `/admin/jiugai/agent/remote-ops/config/${encodeURIComponent(serverId)}`, method: 'delete' })
