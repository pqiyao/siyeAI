import request from '@/utils/request'
export const getChatRuntimeOverview = () => request({ url: '/admin/jiugai/chat-runtime/overview', method: 'get' })
export const listChatRuntime = (params) => request({ url: '/admin/jiugai/chat-runtime/list', method: 'get', params })
export const cancelChatRuntime = (id) => request({ url: `/admin/jiugai/chat-runtime/${id}/cancel`, method: 'post' })
export const hardDeleteChatRuntime = (ids) => request({
  url: '/admin/jiugai/chat-runtime/batch',
  method: 'delete',
  data: { ids }
})
