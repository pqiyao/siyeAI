import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { after, test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { buildRuntimeMessages } from '../src/runtime/prompt/build-runtime-messages.js';
import { setConfigFilePath } from '../src/util.js';

process.env.SILLYTAVERN_BACKUPS_CHAT_ENABLED = 'false';
process.env.SILLYTAVERN_BACKUPS_CHAT_THROTTLEINTERVAL = '0';
setConfigFilePath(fileURLToPath(new URL('../config.yaml', import.meta.url)));
const { getChatData, trySaveChat } = await import('../src/endpoints/chats.js');
const { upsertRuntimeUserMessage } = await import('../src/endpoints/runtime-chat.js');

const temporaryDirectories = [];

after(() => {
    for (const directory of temporaryDirectories) {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});

test('successful snapshot replay keeps one regex-processed user message for the prompt', () => {
    const chat = [
        header(),
        userMessage('root:41', 'raw database text', { source: 'snapshot' }),
        userMessage('root:40', 'unrelated queued message'),
    ];

    const result = upsertRuntimeUserMessage(
        chat,
        'Alice',
        'regex-processed text',
        'root:41',
        '2026-01-01T00:00:00.000Z',
    );

    assert.deepEqual(result, { inserted: false, index: 1, deduplicated: 0 });
    assert.equal(messagesByRef(chat, 'root:41').length, 1);
    assert.equal(messagesByRef(chat, 'root:41')[0].mes, 'regex-processed text');
    assert.equal(messagesByRef(chat, 'root:41')[0].extra.source, 'snapshot');
    assert.equal(messagesByRef(chat, 'root:40').length, 1);
    const prompt = buildRuntimeMessages({ userName: 'Alice', charName: 'Character', chatData: chat.slice(1) });
    assert.equal(prompt.messages.filter(message => message.role === 'user' && message.content === 'regex-processed text').length, 1);
    assert.equal(prompt.messages.filter(message => message.content === 'raw database text').length, 0);
});

test('failed generation retry upserts the same message_ref instead of accumulating prompt entries', () => {
    const chat = [header()];

    upsertRuntimeUserMessage(chat, 'Alice', 'first attempt', 'root:52', '2026-01-01T00:00:00.000Z');
    upsertRuntimeUserMessage(chat, 'Alice', 'retry attempt', 'root:52', '2026-01-01T00:01:00.000Z');

    assert.equal(messagesByRef(chat, 'root:52').length, 1);
    assert.equal(messagesByRef(chat, 'root:52')[0].mes, 'retry attempt');
    assert.equal(messagesByRef(chat, 'root:52')[0].send_date, '2026-01-01T00:00:00.000Z');
    const prompt = buildRuntimeMessages({ userName: 'Alice', charName: 'Character', chatData: chat.slice(1) });
    assert.equal(prompt.messages.filter(message => message.role === 'user').length, 1);
});

test('retry repairs old duplicate rows only for the exact user message_ref', () => {
    const chat = [
        header(),
        userMessage('root:60', 'first duplicate'),
        { name: 'Character', is_user: false, mes: 'reply', extra: { message_ref: 'root:60' } },
        userMessage('root:61', 'other queued message'),
        userMessage('root:60', 'second duplicate'),
    ];

    const result = upsertRuntimeUserMessage(chat, 'Alice', 'canonical retry', 'root:60');

    assert.equal(result.deduplicated, 1);
    assert.equal(messagesByRef(chat, 'root:60', true).length, 1);
    assert.equal(messagesByRef(chat, 'root:60', false).length, 1);
    assert.equal(messagesByRef(chat, 'root:61', true).length, 1);
});

test('messages without a stable reference keep legacy append behavior', () => {
    const chat = [header()];
    upsertRuntimeUserMessage(chat, 'Alice', 'first', '');
    upsertRuntimeUserMessage(chat, 'Alice', 'second', '');
    assert.equal(chat.length, 3);
});

test('trySaveChat skips only byte-identical JSONL writes', async () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'st-chat-save-'));
    temporaryDirectories.push(directory);
    const backupDirectory = path.join(directory, 'backups');
    fs.mkdirSync(backupDirectory);
    const filePath = path.join(directory, 'chat.jsonl');
    const chat = [header(), userMessage('root:70', 'same content')];

    assert.equal(await trySaveChat(chat, filePath, true, 'idempotency-test', 'Character', backupDirectory), true);
    const original = fs.readFileSync(filePath, 'utf8');
    const loadedChat = getChatData(filePath);
    assert.equal(await trySaveChat(loadedChat, filePath, true, 'idempotency-test', 'Character', backupDirectory), false);
    assert.equal(fs.readFileSync(filePath, 'utf8'), original);

    loadedChat[1].mes = 'changed in place';
    assert.equal(await trySaveChat(loadedChat, filePath, true, 'idempotency-test', 'Character', backupDirectory), true);
    const changedInPlace = fs.readFileSync(filePath, 'utf8');
    assert.notEqual(changedInPlace, original);
    assert.equal(await trySaveChat(loadedChat, filePath, true, 'idempotency-test', 'Character', backupDirectory), false);
    assert.equal(fs.readFileSync(filePath, 'utf8'), changedInPlace);

    const changed = [header(), userMessage('root:70', 'changed content')];
    assert.equal(await trySaveChat(changed, filePath, true, 'idempotency-test', 'Character', backupDirectory), true);
    assert.notEqual(fs.readFileSync(filePath, 'utf8'), original);
});

function header() {
    return { chat_metadata: {}, user_name: 'Alice', character_name: 'Character' };
}

function userMessage(messageRef, mes, extra = {}) {
    return {
        name: 'Alice',
        is_user: true,
        send_date: '2026-01-01T00:00:00.000Z',
        mes,
        extra: { ...extra, message_ref: messageRef },
    };
}

function messagesByRef(chat, messageRef, isUser = true) {
    return chat.filter(message => message?.is_user === isUser && message?.extra?.message_ref === messageRef);
}
