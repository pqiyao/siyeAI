import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(currentDir, '..')
const api = fs.readFileSync(path.join(root, 'src/api/jiugai/character.js'), 'utf8')
const characters = fs.readFileSync(path.join(root, 'src/views/jiugai/character/index.vue'), 'utf8')
const entitlement = fs.readFileSync(path.join(root, 'src/views/jiugai/entitlement/index.vue'), 'utf8')

assert.match(api, /\/admin\/jiugai\/character\/' \+ id \+ '\/promote-copy'/)
assert.match(api, /keepCreatorAttribution/)
assert.match(characters, /复制为系统/)
assert.match(characters, /promotionState\.enabled/)
assert.match(characters, /clientVisible/)
assert.match(characters, /promoteCharacterCopy/)
assert.match(entitlement, /userCharacterPromotionEnabled: false/)
assert.match(entitlement, /允许复制用户卡为系统角色/)

console.log('character promotion admin contract passed')
