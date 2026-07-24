const assert = require('assert');
const fs = require('fs');
const path = require('path');

function read(relativePath) {
	return fs.readFileSync(path.join(__dirname, '..', relativePath), 'utf8');
}

const chat = read('pages/tavern/tavernChat.vue');

function methodSource(startMarker, endMarker) {
	const start = chat.indexOf(startMarker);
	const end = chat.indexOf(endMarker, start + startMarker.length);
	assert(start >= 0 && end > start, `${startMarker} method markers must exist`);
	return chat.slice(start, end);
}

const regenerate = methodSource('\t\t\tonRegen() {', '\t\t\tonContinue() {');
const continuation = methodSource('\t\t\tonContinue() {', '\t\t\tonRestart() {');
const outgoing = methodSource(
	'\t\t\tsubmitOutgoingMessage(rawText, rawImageUrls, options) {',
	'\t\t\tsend() {',
);
const stopSync = methodSource('\t\t\tqueueStopSync(delay) {', '\t\t\tclearPendingVoiceStart() {');

for (const [name, source] of [
	['regenerate', regenerate],
	['continue', continuation],
	['generate', outgoing],
]) {
	assert(source.includes('onDone:'), `${name} stream must handle the terminal done event`);
	assert(
		/data\s*&&\s*data\.content\s*!=\s*null/.test(source),
		`${name} stream must prefer the canonical done.content`,
	);
	assert(
		/['"]text['"],\s*String\(data\.content\)\.trim\(\)/.test(source),
		`${name} stream must replace the displayed bubble with canonical content`,
	);
	assert(
		!source.includes('prepareStreamingAssistantVoice('),
		`${name} stream must not synthesize speech from unnormalized deltas`,
	);
	assert(
		/onDone:[\s\S]*prepareAssistantVoiceForRow\(/.test(source),
		`${name} stream must prepare speech from canonical final content`,
	);
}

assert(
	/regenerate[\s\S]*/.test('regenerate' + regenerate) &&
		/d\s*&&\s*d\.content\s*!=\s*null[\s\S]{0,120}String\(d\.content\)/.test(regenerate),
	'APP regenerate must use the canonical synchronous content',
);
for (const [name, source] of [
	['continue', continuation],
	['generate', outgoing],
]) {
	assert(
		/const raw = (?:d|data) && (?:d|data)\.content;[\s\S]{0,140}raw != null[\s\S]{0,80}String\(raw\)\.trim\(\)/.test(source),
		`APP ${name} must use the canonical synchronous content`,
	);
	assert(
		!source.includes("String(raw).trim() !== ''"),
		`APP ${name} must preserve an intentionally empty canonical result`,
	);
}

const api = read('common/tavernApi.js');
assert(
	/if \(ev === 'done' && h\.onDone\) \{\s*h\.onDone\(obj\);\s*\}/.test(api),
	'SSE parser must forward the complete done payload without turning it into a delta',
);

assert(
	stopSync.includes('const retryDelays = [firstDelay, 1500, 3000]') &&
		stopSync.includes('syncVersion !== this.stopSyncVersion'),
	'stop reconciliation must retry for a bounded window and cancel stale retry rounds',
);

console.log('chat output finalization contract passed');
