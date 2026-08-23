import request from '@/utils/request'

export const getAgentOverview = () => request({ url: '/admin/jiugai/agent/overview', method: 'get' })
export const listAgentTasks = (params) => request({ url: '/admin/jiugai/agent/tasks', method: 'get', params })
export const createAgentTask = (data) => request({ url: '/admin/jiugai/agent/tasks', method: 'post', data })
export const getAgentTask = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}`, method: 'get' })
export const generateAgentPlan = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/plan`, method: 'post' })
export const approveAgentPlan = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/plan/approve`, method: 'post' })
export const listAgentTaskEvents = (id, params) => request({ url: `/admin/jiugai/agent/tasks/${id}/events`, method: 'get', params })
export const agentTaskEventStreamUrl = (id, afterSeq = 0) => `${import.meta.env.VITE_APP_BASE_API || '/dev-api'}/admin/jiugai/agent/tasks/${id}/events/stream?afterSeq=${Number(afterSeq) || 0}`
export const listAgentTaskArtifacts = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/artifacts`, method: 'get' })
export const downloadAgentArtifact = (taskId, artifactKey) => request({ url: `/admin/jiugai/agent/tasks/${taskId}/artifacts/${encodeURIComponent(artifactKey)}/download`, method: 'get', responseType: 'blob' })
export const pauseAgentTask = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/pause`, method: 'post' })
export const resumeAgentTask = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/resume`, method: 'post' })
export const cancelAgentTask = (id) => request({ url: `/admin/jiugai/agent/tasks/${id}/cancel`, method: 'post' })
export const retryAgentTask = (id, data) => request({ url: `/admin/jiugai/agent/tasks/${id}/retry`, method: 'post', data })
