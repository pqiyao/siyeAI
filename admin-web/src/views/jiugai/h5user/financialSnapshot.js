function integerOrZero(value) {
  const number = Number(value)
  return Number.isInteger(number) && number >= 0 ? number : 0
}

function dateText(value) {
  return value == null ? '' : String(value).trim()
}

export function captureH5UserFinancialSnapshot(user) {
  const source = user || {}
  return {
    expectedScore: integerOrZero(source.score),
    expectedGoldCoin: integerOrZero(source.goldCoin),
    expectedVipType: integerOrZero(source.vipType),
    expectedVipExpiresAt: dateText(source.vipExpiresAt)
  }
}

export function expectedH5UserFinancialFields(form) {
  const source = form || {}
  return {
    expectedScore: integerOrZero(source.expectedScore),
    expectedGoldCoin: integerOrZero(source.expectedGoldCoin),
    expectedVipType: integerOrZero(source.expectedVipType),
    expectedVipExpiresAt: dateText(source.expectedVipExpiresAt)
  }
}
