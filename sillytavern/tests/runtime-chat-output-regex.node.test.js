import test from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

import { setConfigFilePath } from '../src/util.js';

process.env.SILLYTAVERN_BACKUPS_CHAT_ENABLED = 'false';
process.env.SILLYTAVERN_BACKUPS_CHAT_THROTTLEINTERVAL = '0';
setConfigFilePath(fileURLToPath(new URL('../config.yaml', import.meta.url)));
const {
    applyRuntimeRegexScripts,
    replaceRuntimeAssistantMessage,
    upsertRuntimeAssistantMessage,
} = await import('../src/endpoints/runtime-chat.js');

const AI_OUTPUT = 2;

function script(overrides = {}) {
    return {
        placement: [AI_OUTPUT],
        findRegex: '/<status>.*?<\/status>/gs',
        replaceString: '',
        ...overrides,
    };
}

test('ordinary AI output regex produces the canonical stored text', () => {
    const actual = applyRuntimeRegexScripts(
        'Hello.<status>hidden</status>',
        [script()],
        AI_OUTPUT,
    );

    assert.equal(actual, 'Hello.');
});

test('disabled, prompt-only, and markdown-only scripts do not alter stored text', () => {
    const raw = 'Hello.<status>hidden</status>';

    assert.equal(applyRuntimeRegexScripts(raw, [script({ disabled: true })], AI_OUTPUT), raw);
    assert.equal(applyRuntimeRegexScripts(raw, [script({ promptOnly: true })], AI_OUTPUT), raw);
    assert.equal(applyRuntimeRegexScripts(raw, [script({ markdownOnly: true })], AI_OUTPUT), raw);
    assert.equal(
        applyRuntimeRegexScripts(raw, [script({ promptOnly: true })], AI_OUTPUT, { isPrompt: true }),
        'Hello.',
    );
    assert.equal(
        applyRuntimeRegexScripts(raw, [script({ markdownOnly: true })], AI_OUTPUT, { isMarkdown: true }),
        'Hello.',
    );
});

test('regex macros and capture groups use the runtime user and character names', () => {
    const actual = applyRuntimeRegexScripts(
        'Hello, Alice',
        [script({
            findRegex: '/(Hello), ({{char}})/g',
            replaceString: '$2 says $1 to {{user}}',
            substituteRegex: 1,
        })],
        AI_OUTPUT,
        { userName: 'Bob', charName: 'Alice' },
    );

    assert.equal(actual, 'Alice says Hello to Bob');
});

test('invalid regex safely keeps the original model output', () => {
    const raw = 'Keep this text';
    assert.equal(
        applyRuntimeRegexScripts(raw, [script({ findRegex: '/[/' })], AI_OUTPUT),
        raw,
    );
});

test('a successful regex may intentionally remove the entire output', () => {
    assert.equal(
        applyRuntimeRegexScripts('<status>internal only</status>', [script()], AI_OUTPUT),
        '',
    );
});

test('canonical empty assistant output is upserted idempotently by message_ref', () => {
    const chat = [
        { user_name: 'Bob', character_name: 'Alice' },
        {
            name: 'Alice',
            is_user: false,
            send_date: '2026-07-14T00:00:00.000Z',
            mes: 'raw hidden output',
            swipes: ['raw hidden output'],
            swipe_id: 0,
            extra: { message_ref: 'root:7' },
        },
        { name: 'Bob', is_user: true, mes: 'keep me', extra: { message_ref: 'root:7' } },
        { name: 'Alice', is_user: false, mes: 'duplicate', extra: { message_ref: 'root:7' } },
    ];

    const first = upsertRuntimeAssistantMessage(chat, 'Alice', '', 'root:7');
    assert.deepEqual(first, { inserted: false, index: 1, deduplicated: 1, changed: true });
    assert.equal(chat.length, 3);
    assert.equal(chat[1].mes, '');
    assert.equal(chat[1].swipes[0], '');
    assert.equal(chat[1].send_date, '2026-07-14T00:00:00.000Z');
    assert.equal(chat[2].is_user, true);

    const retry = upsertRuntimeAssistantMessage(chat, 'Alice', '', 'root:7');
    assert.deepEqual(retry, { inserted: false, index: 1, deduplicated: 0, changed: false });
    assert.equal(chat.length, 3);
});

test('replace targets the exact assistant message_ref instead of an unrelated last reply', () => {
    const chat = [
        { user_name: 'Bob', character_name: 'Alice' },
        { name: 'Alice', is_user: false, mes: 'target', extra: { message_ref: 'root:1' } },
        { name: 'Bob', is_user: true, mes: 'next turn', extra: { message_ref: 'root:2-user' } },
        { name: 'Alice', is_user: false, mes: 'latest', extra: { message_ref: 'root:2' } },
    ];

    const result = replaceRuntimeAssistantMessage(chat, 'Alice', 'updated target', 'root:1');
    assert.deepEqual(result, { matched: true, inserted: false, index: 1, deduplicated: 0, changed: true });
    assert.equal(chat[1].mes, 'updated target');
    assert.equal(chat[3].mes, 'latest');
});

test('replace with a missing message_ref never overwrites an unrelated last reply', () => {
    const chat = [
        { user_name: 'Bob', character_name: 'Alice' },
        { name: 'Alice', is_user: false, mes: 'keep latest', extra: { message_ref: 'root:2' } },
    ];

    const result = replaceRuntimeAssistantMessage(chat, 'Alice', 'must not be written', 'root:missing');
    assert.deepEqual(result, { matched: false, inserted: false, index: -1, deduplicated: 0, changed: false });
    assert.equal(chat.length, 2);
    assert.equal(chat[1].mes, 'keep latest');
    assert.equal(chat[1].extra.message_ref, 'root:2');
});

test('a finalized non-idempotent regex result is not transformed again on retry', () => {
    const doubling = script({ findRegex: '/a/g', replaceString: 'aa' });
    const canonical = applyRuntimeRegexScripts('a', [doubling], AI_OUTPUT);
    assert.equal(canonical, 'aa');
    assert.equal(applyRuntimeRegexScripts(canonical, [doubling], AI_OUTPUT), 'aaaa');

    const chat = [{ user_name: 'Bob', character_name: 'Alice' }];
    upsertRuntimeAssistantMessage(chat, 'Alice', canonical, 'root:9');
    upsertRuntimeAssistantMessage(chat, 'Alice', canonical, 'root:9');
    assert.equal(chat[1].mes, 'aa');
    assert.equal(chat.length, 2);
});
