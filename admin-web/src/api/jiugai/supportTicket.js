import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function getSupportTicketMeta() {
  return sillyRequest({
    url: '/admin/jiugai/support-ticket/meta',
    method: 'get'
  })
}

export function listSupportTicket(query) {
  return sillyRequest({
    url: '/admin/jiugai/support-ticket/list',
    method: 'get',
    params: query
  })
}

export function getSupportTicket(ticketNo) {
  return sillyRequest({
    url: '/admin/jiugai/support-ticket/' + ticketNo,
    method: 'get'
  })
}

export function replySupportTicket(data) {
  return sillyRequest({
    url: '/admin/jiugai/support-ticket/reply',
    method: 'post',
    data
  })
}

export function updateSupportTicketStatus(data) {
  return sillyRequest({
    url: '/admin/jiugai/support-ticket/status',
    method: 'put',
    data
  })
}
