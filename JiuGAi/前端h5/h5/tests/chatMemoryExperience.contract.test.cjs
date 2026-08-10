const assert = require('assert');
const fs = require('fs');
const path = require('path');

const chat = fs.readFileSync(
	path.join(__dirname, '..', 'pages/tavern/tavernChat.vue'),
	'utf8'
);
const tavernApi = fs.readFileSync(
	path.join(__dirname, '..', 'common/tavernApi.js'),
	'utf8'
);

assert(
	chat.includes('v-if="longTermMemoryEnabledGlobal !== false" class="tool-i" @tap="onMem"'),
	'memory toolbar entry must be hidden by the global runtime switch'
);
assert(
	chat.includes('longTermMemoryEnabledGlobal: false'),
	'memory UI must fail closed before runtime configuration is available'
);
assert(
	chat.includes('const enabled = !!(config && config.longTermMemoryEnabled === true);'),
	'memory UI must require an explicit enabled runtime value'
);
assert(
	tavernApi.includes('longTermMemoryEnabled: raw.longTermMemoryEnabled === true'),
	'runtime feature normalization must keep long-term memory disabled unless explicitly enabled'
);

assert(
	chat.includes('textarea v-model="memoryPanel.editor.content" class="memory-editor-textarea" maxlength="1200"'),
	'manual memory editor must preserve the 1200-character product contract'
);
assert(
	chat.includes('仅你可以修改、停用或删除，AI 整理不会覆盖或淘汰'),
	'protected memory must explain its hard user-control semantics'
);
assert(chat.includes("mode === 'STRUCTURED_APPLIED'"), 'structured memory refresh must have an explicit success state');
assert(chat.includes("mode === 'SUMMARY_ONLY'"), 'summary-only fallback must not masquerade as fact extraction');
assert(chat.includes("mode === 'HEURISTIC_ONLY'"), 'heuristic fallback must be visible to the user');
assert(chat.includes("mode === 'STALE'"), 'stale refresh results must be disclosed to the user');
assert(chat.includes('已更新摘要，事实记忆未变化'), 'summary-only feedback must state that facts did not change');
assert(chat.includes('聊天已变化，本次结果未写入'), 'stale feedback must state that the result was discarded');

console.log('chat memory experience contract passed');
