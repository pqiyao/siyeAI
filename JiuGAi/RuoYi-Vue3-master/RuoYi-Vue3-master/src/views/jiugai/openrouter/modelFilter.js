function normalizedSearchText(value) {
  return String(value ?? '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
    .replace(/\s+/g, ' ')
}

export function matchesModelSearch(model, query) {
  const normalizedQuery = normalizedSearchText(query)
  if (!normalizedQuery) return true

  const searchable = normalizedSearchText([
    model?.id,
    model?.label,
    model?.ownedBy
  ].filter(Boolean).join(' '))
  const compactSearchable = searchable.replace(/\s+/g, '')

  return normalizedQuery.split(' ').every((token) => (
    searchable.includes(token) || compactSearchable.includes(token)
  ))
}

export function filterDiscoveredModels(models, options = {}) {
  const source = Array.isArray(models) ? models : []
  const matchedOnly = options.matchedOnly === true
  const query = options.query || ''

  return source.filter((model) => (
    (!matchedOnly || model?.capabilityMatch === true)
      && matchesModelSearch(model, query)
  ))
}
