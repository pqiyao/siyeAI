/**
 * 若依 / jiugai 管理端：从 axios / 业务错误对象中取出可读文案。
 * Element Plus MessageBox 用户点「取消」时 reject 常为 'cancel'，勿当接口错误提示。
 */
export function isMessageBoxCancelled(err) {
  return err === 'cancel' || err === 'close'
}

export function jiugaiRequestErrorMessage(err, fallback = '请求失败') {
  const fb = fallback != null && String(fallback) !== '' ? String(fallback) : '请求失败'
  if (err == null) return fb
  if (isMessageBoxCancelled(err)) return fb
  if (typeof err === 'string' && String(err).trim()) return String(err).trim()
  const data = err.response && err.response.data
  const fromBody = data && (data.msg != null ? data.msg : data.message)
  if (fromBody != null && String(fromBody).trim()) return String(fromBody).trim()
  if (err.message != null && String(err.message).trim()) return String(err.message).trim()
  return fb
}
