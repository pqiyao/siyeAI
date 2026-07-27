import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getOpenRouterGeneration() {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation',
    method: 'get'
  })
}

export function updateOpenRouterGeneration(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation',
    method: 'put',
    data
  })
}

export function saveModelProvider(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation/provider',
    method: 'put',
    data
  })
}

export function deleteModelProvider(id) {
  return sillyRequest({
    url: `/admin/jiugai/openrouter-generation/provider/${id}`,
    method: 'delete'
  })
}

export function saveModelRoute(data) {
  return sillyRequest({
    url: '/admin/jiugai/openrouter-generation/route',
    method: 'put',
    data
  })
}

export function deleteModelRoute(id) {
  return sillyRequest({
    url: `/admin/jiugai/openrouter-generation/route/${id}`,
    method: 'delete'
  })
}

export function getAiRouting() {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing',
    method: 'get'
  })
}

export function discoverAiModels(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/models',
    method: 'post',
    data
  })
}

export function probeAiCapability(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/probe',
    method: 'post',
    data
  })
}

export function importLegacyAiChatRoute() {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/import-legacy-chat',
    method: 'post'
  })
}

export function saveAiRoutingRuntimeSettings(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/runtime-settings',
    method: 'put',
    data
  })
}

export function resetAiRoutingRuntimeSettings() {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/runtime-settings',
    method: 'delete'
  })
}

export function saveAiProvider(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/provider',
    method: 'put',
    data
  })
}

export function deleteAiDeployment(id) {
  return sillyRequest({
    url: `/admin/jiugai/ai-routing/deployment/${id}`,
    method: 'delete'
  })
}

export function deleteAiAccount(id) {
  return sillyRequest({
    url: `/admin/jiugai/ai-routing/account/${id}`,
    method: 'delete'
  })
}

export function saveAiRoute(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/route',
    method: 'put',
    data
  })
}

export function deleteAiRoute(id) {
  return sillyRequest({
    url: `/admin/jiugai/ai-routing/route/${id}`,
    method: 'delete'
  })
}

export function saveAiChatModelSettings(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/chat-model-settings',
    method: 'put',
    data
  })
}

export function saveAiChatOffering(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/chat-offering',
    method: 'put',
    data
  })
}

export function saveAiChatOfferingBundle(data) {
  return sillyRequest({
    url: '/admin/jiugai/ai-routing/chat-offering-bundle',
    method: 'put',
    data
  })
}

export function deleteAiChatOffering(id) {
  return sillyRequest({
    url: `/admin/jiugai/ai-routing/chat-offering/${id}`,
    method: 'delete'
  })
}
