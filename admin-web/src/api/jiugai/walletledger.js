import request from '@/utils/request'

const sillyApiBase = import.meta.env.VITE_SILLY_API || '/silly-api'

function sillyRequest(config) {
  return request({
    baseURL: sillyApiBase,
    ...config
  })
}

export function listWalletLedger(query) {
  return sillyRequest({
    url: '/admin/jiugai/wallet-ledger/list',
    method: 'get',
    params: query
  })
}
