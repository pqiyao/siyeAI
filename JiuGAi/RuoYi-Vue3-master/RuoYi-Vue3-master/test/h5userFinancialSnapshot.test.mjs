import assert from 'node:assert/strict'
import test from 'node:test'

import {
  captureH5UserFinancialSnapshot,
  expectedH5UserFinancialFields
} from '../src/views/jiugai/h5user/financialSnapshot.js'

test('captures the original wallet and membership values when detail opens', () => {
  const snapshot = captureH5UserFinancialSnapshot({
    score: 120,
    goldCoin: 340,
    vipType: 2,
    vipExpiresAt: '2030-01-02 03:04:05'
  })

  assert.deepEqual(snapshot, {
    expectedScore: 120,
    expectedGoldCoin: 340,
    expectedVipType: 2,
    expectedVipExpiresAt: '2030-01-02 03:04:05'
  })
})

test('submits the unchanged snapshot after editable financial values change', () => {
  const form = {
    score: 120,
    goldCoin: 340,
    vipType: 1,
    vipExpiresAt: '2030-01-02 03:04:05',
    ...captureH5UserFinancialSnapshot({
      score: 120,
      goldCoin: 340,
      vipType: 1,
      vipExpiresAt: '2030-01-02 03:04:05'
    })
  }
  form.score = 999
  form.goldCoin = 888
  form.vipType = 2
  form.vipExpiresAt = '2031-02-03 04:05:06'

  assert.deepEqual(expectedH5UserFinancialFields(form), {
    expectedScore: 120,
    expectedGoldCoin: 340,
    expectedVipType: 1,
    expectedVipExpiresAt: '2030-01-02 03:04:05'
  })
})

test('normalizes an empty membership expiry snapshot to an empty string', () => {
  assert.equal(captureH5UserFinancialSnapshot({ vipExpiresAt: null }).expectedVipExpiresAt, '')
})
