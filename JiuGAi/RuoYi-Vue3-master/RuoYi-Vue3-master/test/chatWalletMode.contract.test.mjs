import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const entitlementSource = fs.readFileSync(
  new URL('../src/views/jiugai/entitlement/index.vue', import.meta.url),
  'utf8'
)
const routingSource = fs.readFileSync(
  new URL('../src/views/jiugai/openrouter/UnifiedAiRoutingPanel.vue', import.meta.url),
  'utf8'
)

test('聊天钱包支持任选余额并明确保留同时扣费模式', () => {
  assert.match(entitlementSource, /value="DIAMOND_OR_GOLD"/)
  assert.match(entitlementSource, /value="DIAMOND_AND_GOLD"/)
  assert.match(entitlementSource, /优先钻石，余额不足再扣金币/)
})

test('分模型价格预览区分或支付和同时支付', () => {
  assert.match(routingSource, /DIAMOND_OR_GOLD: `\$\{d\}钻石 或 \$\{g\}金币\/次`/)
  assert.match(routingSource, /DIAMOND_AND_GOLD: `\$\{d\}钻石 \+ \$\{g\}金币\/次`/)
  assert.match(routingSource, /QUOTA_THEN_DIAMOND_OR_GOLD/)
})
