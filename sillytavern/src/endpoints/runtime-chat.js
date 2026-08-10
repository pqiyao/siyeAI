import fs from 'node:fs';
import path from 'node:path';

import express from 'express';
import sanitize from 'sanitize-filename';
import iconv from 'iconv-lite';

import { SETTINGS_FILE } from '../constants.js';
import validateAvatarUrlMiddleware from '../middleware/validateFileName.js';
import { tryParse } from '../util.js';
import { parse as parseCharacterCard } from '../character-card-parser.js';
import { getChatData, trySaveChat } from './chats.js';
import { handleGenerateRequest } from './backends/chat-completions.js';
import {
    getSentencepiceTokenizer,
    getTiktokenTokenizer,
    getTokenizerModel,
    getWebTokenizer,
} from './tokenizers.js';
import { buildRuntimeMessages } from '../runtime/prompt/build-runtime-messages.js';
import { buildWorldInfoPrompt } from '../runtime/prompt/worldinfo-prompt.js';
import { buildLorebookBlocks } from '../runtime/prompt/lorebook-engine.js';
import { updateRuntimeDynamicSheetsFromMessage } from '../runtime/prompt/dynamic-sheets-engine.js';

/**
 * Runtime Chat API: use ST chat jsonl as the source of truth for history and prompt state.
 * Spring stays on business concerns, while ST owns prompt assembly and final generation shape.
 */
export const router = express.Router();
const activeRuntimeGenerations = new Map();
const LORE_CHARS_PER_TOKEN = 4;
const ST_DEBUG_RUNTIME_GENERATE = String(process.env.ST_DEBUG_RUNTIME_GENERATE || '').trim().toLowerCase() === 'true';
const DEFAULT_CONTINUE_NUDGE_PROMPT = '[Continue your last message without repeating its original content.]';
const DEFAULT_CONTINUE_NUDGE_ROLE = 'system';
const RUNTIME_REGEX_PLACEMENT = Object.freeze({
    USER_INPUT: 1,
    AI_OUTPUT: 2,
    WORLD_INFO: 5,
});
const RUNTIME_REGEX_SUBSTITUTE = Object.freeze({
    NONE: 0,
    RAW: 1,
    ESCAPED: 2,
});
const RUNTIME_REGEX_SCRIPT_LIMIT = 64;
const RUNTIME_REGEX_TEXT_LIMIT = 200_000;
const DEFAULT_RUNTIME_RESPONSE_TOKENS = 1024;
const MIN_RUNTIME_PROMPT_TOKENS = 512;
const RUNTIME_SHEETS_MAX_SHEETS = 8;
const RUNTIME_SHEETS_MAX_ROWS_PER_SHEET = 8;
const RUNTIME_SHEETS_MAX_PROMPT_CHARS = 12_000;

function logRuntimeGenerateStep(traceId, step, extra = undefined) {
    if (!ST_DEBUG_RUNTIME_GENERATE) {
        return;
    }
    const suffix = extra && typeof extra === 'object'
        ? ` ${JSON.stringify(extra)}`
        : extra != null
            ? ` ${String(extra)}`
            : '';
    console.info(`[runtime-chat/generate] trace=${traceId} step=${step}${suffix}`);
}

function buildRuntimeGenerationKey(handle, avatarUrl, fileName) {
    return [
        String(handle || '').trim(),
        String(avatarUrl || '').trim(),
        String(fileName || '').trim(),
    ].join('\u0000');
}

function abortActiveRuntimeGeneration(entry, reason = 'runtime_stop') {
    if (!entry || entry.cancelled) {
        return false;
    }
    entry.cancelled = true;
    entry.stoppedAt = new Date().toISOString();
    const error = new Error(String(reason || 'runtime_stop'));
    try {
        if (entry.request?.destroy && !entry.request.destroyed) {
            entry.request.destroy(error);
        }
    } catch {
        // Best-effort abort.
    }
    try {
        if (entry.request?.socket && !entry.request.socket.destroyed) {
            entry.request.socket.destroy(error);
        }
    } catch {
        // Best-effort abort.
    }
    try {
        if (entry.response && !entry.response.writableEnded) {
            entry.response.end();
        }
    } catch {
        // Best-effort abort.
    }
    return true;
}

function registerActiveRuntimeGeneration(key, entry) {
    const previous = activeRuntimeGenerations.get(key);
    if (previous && previous !== entry) {
        abortActiveRuntimeGeneration(previous, 'runtime_generation_replaced');
    }
    activeRuntimeGenerations.set(key, entry);
}

function clearActiveRuntimeGeneration(key, entry) {
    if (activeRuntimeGenerations.get(key) === entry) {
        activeRuntimeGenerations.delete(key);
    }
}

function normalizeGoldenSourceTag(sourceTag) {
    const raw = String(sourceTag || '').trim().toLowerCase();
    if (!raw) return '';
    if (['runtime', 'h5'].includes(raw)) return 'runtime';
    if (['browser', 'web', 'st'].includes(raw)) return 'browser';
    return raw.replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
}

function readLatestGoldenGenerateBody(directories, sourceTag = '') {
    try {
        const tag = normalizeGoldenSourceTag(sourceTag);
        const debugDir = path.join(directories.user, 'st-debug');
        const candidates = tag
            ? [path.join(debugDir, `last-generate-body-${tag}.json`), path.join(debugDir, 'last-generate-body.json')]
            : [path.join(debugDir, 'last-generate-body.json')];
        for (const p of candidates) {
            if (!fs.existsSync(p)) continue;
            const raw = fs.readFileSync(p, 'utf8');
            const parsed = tryParse(raw) || null;
            const body = parsed?.body ?? null;
            if (!body) {
                return { ok: false, error: 'golden_capture_invalid', path: p, tag: parsed?.tag || tag || '' };
            }
            return {
                ok: true,
                path: p,
                captured_at: parsed?.captured_at,
                source: parsed?.source || '',
                tag: parsed?.tag || tag || '',
                body,
            };
        }
        return {
            ok: false,
            error: 'no_golden_capture',
            path: candidates[0],
            tag: tag || '',
        };
    } catch (err) {
        return { ok: false, error: 'golden_capture_read_failed', message: err?.message ?? String(err) };
    }
}

function normalizeMessages(arr) {
    if (!Array.isArray(arr)) return [];
    return arr.map(m => ({
        role: String(m?.role ?? ''),
        name: m?.name != null ? String(m.name) : undefined,
        content: m?.content != null ? String(m.content) : '',
    }));
}

function diffMessages(goldenMessages, runtimeMessages) {
    const g = normalizeMessages(goldenMessages);
    const r = normalizeMessages(runtimeMessages);
    const max = Math.max(g.length, r.length);
    let firstDiff = null;
    for (let i = 0; i < max; i++) {
        const a = g[i];
        const b = r[i];
        if (!a || !b) {
            firstDiff = { index: i, golden: a ?? null, runtime: b ?? null, reason: 'length_mismatch' };
            break;
        }
        if (a.role !== b.role || a.name !== b.name || a.content !== b.content) {
            firstDiff = {
                index: i,
                golden: a,
                runtime: b,
                reason: 'field_mismatch',
            };
            break;
        }
    }
    return {
        golden_count: g.length,
        runtime_count: r.length,
        first_diff: firstDiff,
    };
}

const GOLDEN_CASE_BODY_FIELDS = [
    'chat_completion_source',
    'model',
    'user_name',
    'char_name',
    'stream',
    'temperature',
    'max_tokens',
    'top_p',
    'top_k',
    'min_p',
    'top_a',
    'frequency_penalty',
    'presence_penalty',
    'repetition_penalty',
    'middleout',
    'allow_fallbacks',
    'verbosity',
    'include_reasoning',
    'reasoning_effort',
    'custom_prompt_post_processing',
    'stop',
    'group_names',
    'allowed_features',
];

function diffGenerateBodyFields(goldenBody, runtimeBody) {
    const diffs = [];
    for (const field of GOLDEN_CASE_BODY_FIELDS) {
        const goldenValue = goldenBody?.[field] ?? null;
        const runtimeValue = runtimeBody?.[field] ?? null;
        if (JSON.stringify(goldenValue) !== JSON.stringify(runtimeValue)) {
            diffs.push({
                field,
                golden: goldenValue,
                runtime: runtimeValue,
            });
        }
    }
    return {
        checked_fields: GOLDEN_CASE_BODY_FIELDS,
        diff_count: diffs.length,
        differences: diffs,
    };
}

function ensureGoldenCasesDirectory(directories) {
    const dir = path.join(directories.user, 'st-debug', 'golden-cases');
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    return dir;
}

function normalizeGoldenCaseName(caseName) {
    const raw = sanitize(String(caseName || '').trim());
    return raw.replace(/[^a-zA-Z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 96);
}

function resolveGoldenCasePath(directories, caseName) {
    const safeName = normalizeGoldenCaseName(caseName);
    if (!safeName) {
        throw new Error('case_name required');
    }
    const dir = ensureGoldenCasesDirectory(directories);
    return {
        caseName: safeName,
        path: path.join(dir, `${safeName}.json`),
    };
}

function serializeChatData(chatData) {
    if (!Array.isArray(chatData)) {
        return '';
    }
    return chatData.map(item => JSON.stringify(item)).join('\n');
}

function readGoldenCaseFile(directories, caseName) {
    const resolved = resolveGoldenCasePath(directories, caseName);
    if (!fs.existsSync(resolved.path)) {
        return null;
    }
    const raw = fs.readFileSync(resolved.path, 'utf8');
    const parsed = tryParse(raw);
    if (!parsed || typeof parsed !== 'object') {
        return null;
    }
    return {
        caseName: resolved.caseName,
        path: resolved.path,
        data: parsed,
    };
}

function buildNameCandidates(rawValue) {
    const values = new Set();
    const push = (value) => {
        const text = String(value ?? '').trim();
        if (!text) return;
        values.add(text);
        try {
            values.add(text.normalize('NFC'));
        } catch {
            // Best-effort normalization only.
        }
    };

    push(rawValue);
    try {
        const recovered = Buffer.from(String(rawValue ?? ''), 'latin1').toString('utf8');
        if (recovered && !recovered.includes('\uFFFD')) {
            push(recovered);
        }
    } catch {
        // Best-effort mojibake recovery only.
    }
    try {
        const recovered = iconv.encode(String(rawValue ?? ''), 'gbk').toString('utf8');
        if (recovered && !recovered.includes('\uFFFD')) {
            push(recovered);
        }
    } catch {
        // Best-effort mojibake recovery only.
    }

    return [...values];
}

function findMatchingEntry(candidates, entries, { caseInsensitive = false } = {}) {
    const safeEntries = Array.isArray(entries) ? entries.filter(Boolean) : [];
    for (const candidate of candidates) {
        const candidateText = String(candidate ?? '').trim();
        if (!candidateText) continue;
        const candidateNfc = (() => {
            try {
                return candidateText.normalize('NFC');
            } catch {
                return candidateText;
            }
        })();
        const candidateLower = candidateText.toLowerCase();
        const candidateNfcLower = candidateNfc.toLowerCase();
        const matched = safeEntries.find(entry => {
            const entryText = String(entry ?? '').trim();
            if (!entryText) return false;
            if (entryText === candidateText) return true;
            let entryNfc = entryText;
            try {
                entryNfc = entryText.normalize('NFC');
            } catch {
                // Keep original.
            }
            if (entryNfc === candidateNfc) return true;
            if (!caseInsensitive) return false;
            return entryText.toLowerCase() === candidateLower || entryNfc.toLowerCase() === candidateNfcLower;
        });
        if (matched) {
            return matched;
        }
    }
    return '';
}

function resolveCanonicalAvatarUrl(directories, avatarUrl) {
    const raw = String(avatarUrl || '').trim();
    if (!raw) return '';
    const candidates = buildNameCandidates(raw);
    const characterFiles = fs.existsSync(directories.characters)
        ? fs.readdirSync(directories.characters, { withFileTypes: true })
            .filter(entry => entry.isFile())
            .map(entry => entry.name)
        : [];
    return findMatchingEntry(candidates, characterFiles, { caseInsensitive: true }) || candidates[0] || raw;
}

function resolveCanonicalChatDirName(directories, avatarUrl) {
    const canonicalAvatarUrl = resolveCanonicalAvatarUrl(directories, avatarUrl);
    const rawBaseName = canonicalAvatarUrl.replace(/\.png$/i, '');
    const candidates = buildNameCandidates(rawBaseName);
    const chatDirs = fs.existsSync(directories.chats)
        ? fs.readdirSync(directories.chats, { withFileTypes: true })
            .filter(entry => entry.isDirectory())
            .map(entry => entry.name)
        : [];
    return findMatchingEntry(candidates, chatDirs, { caseInsensitive: false }) || rawBaseName;
}

function resolveChatFilePath(directories, avatarUrl, fileName) {
    const chatFileName = `${String(fileName)}.jsonl`;
    const safeChatFileName = sanitize(chatFileName);

    if (fs.existsSync(directories.chats)) {
        const existingDir = fs.readdirSync(directories.chats, { withFileTypes: true })
            .filter(entry => entry.isDirectory())
            .map(entry => entry.name)
            .find(name => fs.existsSync(path.join(directories.chats, name, safeChatFileName)));
        if (existingDir) {
            return {
                dirName: existingDir,
                chatFilePath: path.join(directories.chats, existingDir, safeChatFileName),
            };
        }
    }

    const dirName = resolveCanonicalChatDirName(directories, avatarUrl);
    const chatDir = path.join(directories.chats, dirName);
    if (!fs.existsSync(chatDir)) {
        fs.mkdirSync(chatDir, { recursive: true });
    }
    const chatFilePath = path.join(chatDir, safeChatFileName);
    return { dirName, chatFilePath };
}

function ensureChatHeader(chatData, userName, charName) {
    if (Array.isArray(chatData) && chatData.length > 0 && chatData[0]?.chat_metadata) {
        const header = chatData[0];
        const currentUserName = String(header?.user_name ?? '').trim();
        const currentCharName = String(header?.character_name ?? '').trim();
        const nextUserName = String(userName || '').trim();
        const nextCharName = String(charName || '').trim();
        const patchUserName = !currentUserName || currentUserName === 'unused';
        const patchCharName = !currentCharName || currentCharName === 'unused';
        if (!patchUserName && !patchCharName) {
            return chatData;
        }
        return [{
            ...header,
            user_name: patchUserName ? (nextUserName || currentUserName || 'unused') : header.user_name,
            character_name: patchCharName ? (nextCharName || currentCharName || 'unused') : header.character_name,
        }, ...chatData.slice(1)];
    }
    const header = {
        chat_metadata: {},
        user_name: userName || 'unused',
        character_name: charName || 'unused',
    };
    return [header, ...(Array.isArray(chatData) ? chatData : [])];
}

function runtimeObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? { ...value } : {};
}

function runtimeArray(value) {
    return Array.isArray(value) ? [...value] : [];
}

function runtimeInt(value, fallback = 0) {
    const n = Number(value);
    return Number.isFinite(n) ? Math.trunc(n) : fallback;
}

function mergeRuntimeMessageRef(message, messageRef) {
    const extra = runtimeObject(message.extra);
    if (messageRef) {
        extra.message_ref = messageRef;
    }
    message.extra = extra;
}

function syncRuntimeAssistantSwipes(message, mes, messageRef) {
    const swipeId = Math.max(0, runtimeInt(message.swipe_id, 0));
    const swipes = runtimeArray(message.swipes);
    while (swipes.length <= swipeId) {
        swipes.push('');
    }
    swipes[swipeId] = mes;
    message.swipes = swipes;
    message.swipe_id = swipeId;

    const swipeInfo = runtimeArray(message.swipe_info);
    while (swipeInfo.length <= swipeId) {
        swipeInfo.push({});
    }
    const info = runtimeObject(swipeInfo[swipeId]);
    const extra = runtimeObject(info.extra);
    if (messageRef) {
        extra.message_ref = messageRef;
    }
    info.extra = extra;
    if (!info.send_date && message.send_date) {
        info.send_date = message.send_date;
    }
    swipeInfo[swipeId] = info;
    message.swipe_info = swipeInfo;
}

function buildRuntimeAssistantMessage(existing, charName, mes, messageRef) {
    const next = runtimeObject(existing);
    next.name = charName || next.name || 'Assistant';
    next.is_user = false;
    next.send_date = next.send_date || new Date().toISOString();
    next.mes = mes;
    mergeRuntimeMessageRef(next, messageRef);
    syncRuntimeAssistantSwipes(next, mes, messageRef);
    return next;
}

function runtimeMessageRef(message) {
    const extra = message && typeof message.extra === 'object' && !Array.isArray(message.extra)
        ? message.extra
        : null;
    return String(extra?.message_ref ?? '').trim();
}

/**
 * Inserts the current user message once, or updates the existing logical message on a retry.
 * The message body must already have passed through the runtime input regex pipeline.
 *
 * @param {Array} chatData ST chat data including its header.
 * @param {string} userName Display name stored in the ST message.
 * @param {string} mes Regex-processed user message.
 * @param {string} messageRef Stable business message reference.
 * @param {string} [sendDate] Date used only when a new message is inserted.
 * @returns {{inserted: boolean, index: number, deduplicated: number}}
 */
export function upsertRuntimeUserMessage(chatData, userName, mes, messageRef, sendDate = new Date().toISOString()) {
    if (!Array.isArray(chatData)) {
        throw new TypeError('chatData must be an array');
    }

    const targetRef = String(messageRef || '').trim();
    const matchingIndexes = [];
    if (targetRef) {
        for (let i = 1; i < chatData.length; i++) {
            const message = chatData[i];
            if (message?.is_user === true && runtimeMessageRef(message) === targetRef) {
                matchingIndexes.push(i);
            }
        }
    }

    const existingIndex = matchingIndexes[0] ?? -1;
    const existing = existingIndex >= 1 ? chatData[existingIndex] : null;
    const next = runtimeObject(existing);
    next.name = userName || next.name || 'User';
    next.is_user = true;
    next.send_date = next.send_date || sendDate;
    next.mes = mes;
    mergeRuntimeMessageRef(next, targetRef);

    if (existingIndex < 1) {
        chatData.push(next);
        return { inserted: true, index: chatData.length - 1, deduplicated: 0 };
    }

    chatData[existingIndex] = next;
    for (let i = matchingIndexes.length - 1; i >= 1; i--) {
        chatData.splice(matchingIndexes[i], 1);
    }
    return { inserted: false, index: existingIndex, deduplicated: Math.max(0, matchingIndexes.length - 1) };
}

/**
 * Inserts an assistant message once, or updates the same logical message on a completion retry.
 * The message body must already have passed through the runtime output regex pipeline when
 * output_regex_applied=true is sent by the caller.
 *
 * @param {Array} chatData ST chat data including its header.
 * @param {string} charName Display name stored in the ST message.
 * @param {string} mes Canonical assistant message, which may intentionally be empty.
 * @param {string} messageRef Stable business message reference.
 * @returns {{inserted: boolean, index: number, deduplicated: number, changed: boolean}}
 */
export function upsertRuntimeAssistantMessage(chatData, charName, mes, messageRef) {
    if (!Array.isArray(chatData)) {
        throw new TypeError('chatData must be an array');
    }

    const targetRef = String(messageRef || '').trim();
    const matchingIndexes = [];
    if (targetRef) {
        for (let i = 1; i < chatData.length; i++) {
            const message = chatData[i];
            if (message?.is_user === false && runtimeMessageRef(message) === targetRef) {
                matchingIndexes.push(i);
            }
        }
    }

    const existingIndex = matchingIndexes[0] ?? -1;
    const existing = existingIndex >= 1 ? chatData[existingIndex] : null;
    const changed = !existing || existing.mes !== mes;
    const next = buildRuntimeAssistantMessage(existing, charName, mes, targetRef);

    if (existingIndex < 1) {
        chatData.push(next);
        return { inserted: true, index: chatData.length - 1, deduplicated: 0, changed: true };
    }

    chatData[existingIndex] = next;
    for (let i = matchingIndexes.length - 1; i >= 1; i--) {
        chatData.splice(matchingIndexes[i], 1);
    }
    return {
        inserted: false,
        index: existingIndex,
        deduplicated: Math.max(0, matchingIndexes.length - 1),
        changed,
    };
}

/**
 * Replaces an assistant by exact message_ref when possible, falling back to the legacy last
 * assistant behavior for callers that do not yet have a matching reference.
 *
 * @param {Array} chatData ST chat data including its header.
 * @param {string} charName Display name stored in the ST message.
 * @param {string} mes Canonical assistant message, which may intentionally be empty.
 * @param {string} messageRef Stable business message reference.
 * @returns {{matched: boolean, inserted: boolean, index: number, deduplicated: number, changed: boolean}}
 */
export function replaceRuntimeAssistantMessage(chatData, charName, mes, messageRef) {
    if (!Array.isArray(chatData)) {
        throw new TypeError('chatData must be an array');
    }

    const targetRef = String(messageRef || '').trim();
    const matchingIndexes = [];
    if (targetRef) {
        for (let i = 1; i < chatData.length; i++) {
            const message = chatData[i];
            if (message?.is_user === false && runtimeMessageRef(message) === targetRef) {
                matchingIndexes.push(i);
            }
        }
    }

    let targetIndex = matchingIndexes[0] ?? -1;
    if (targetRef && targetIndex < 1) {
        return { matched: false, inserted: false, index: -1, deduplicated: 0, changed: false };
    }
    if (!targetRef && targetIndex < 1) {
        for (let i = chatData.length - 1; i >= 1; i--) {
            const message = chatData[i];
            if (message?.is_user === false) {
                targetIndex = i;
                break;
            }
        }
    }

    const previous = targetIndex >= 1 ? chatData[targetIndex] : null;
    const changed = !previous || previous.mes !== mes;
    const next = buildRuntimeAssistantMessage(previous, charName, mes, targetRef);
    if (targetIndex >= 1) {
        chatData[targetIndex] = next;
    } else {
        chatData.push(next);
        targetIndex = chatData.length - 1;
    }
    for (let i = matchingIndexes.length - 1; i >= 1; i--) {
        chatData.splice(matchingIndexes[i], 1);
    }

    return {
        matched: true,
        inserted: previous == null,
        index: targetIndex,
        deduplicated: Math.max(0, matchingIndexes.length - 1),
        changed,
    };
}

function removeRuntimeRegenerateTarget(chatData, messageRef) {
    if (!Array.isArray(chatData) || chatData.length <= 1) {
        return { removed: false, index: -1, byRef: false };
    }
    const targetRef = String(messageRef || '').trim();
    if (targetRef) {
        for (let i = chatData.length - 1; i >= 1; i--) {
            const message = chatData[i];
            if (message && message.is_user === false && runtimeMessageRef(message) === targetRef) {
                chatData.splice(i, 1);
                return { removed: true, index: i, byRef: true };
            }
        }
    }
    for (let i = chatData.length - 1; i >= 1; i--) {
        const message = chatData[i];
        if (message && message.is_user === false) {
            chatData.splice(i, 1);
            return { removed: true, index: i, byRef: false };
        }
    }
    return { removed: false, index: -1, byRef: false };
}

function normalizeRuntimeSheetText(value, macroOptions = {}) {
    if (value == null) {
        return '';
    }
    const raw = typeof value === 'string' ? value : String(value);
    return expandRuntimeMacros(raw, macroOptions)
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n')
        .trim();
}

function readRuntimeSheetCellText(cell, macroOptions = {}) {
    if (!cell || typeof cell !== 'object') {
        return '';
    }
    const data = cell.data && typeof cell.data === 'object' ? cell.data : {};
    return normalizeRuntimeSheetText(
        data.value ?? data.text ?? data.content ?? data.note ?? '',
        macroOptions,
    );
}

function pushRuntimeSheetLine(lines, label, value, macroOptions = {}) {
    const text = normalizeRuntimeSheetText(value, macroOptions);
    if (text) {
        lines.push(`${label}: ${text}`);
    }
}

function buildRuntimeDynamicSheetsPrompt(headerMeta, macroOptions = {}) {
    const meta = headerMeta && typeof headerMeta === 'object' ? headerMeta : {};
    const sheets = Array.isArray(meta.sheets) ? meta.sheets : [];
    if (!sheets.length) {
        return '';
    }

    const selected = new Set(
        (Array.isArray(meta.selected_sheets) ? meta.selected_sheets : [])
            .map(item => String(item || '').trim())
            .filter(Boolean),
    );
    const lines = [
        'ST dynamic sheets',
        'These sheets are persistent chat state from SillyTavern metadata. Use them as RPG/state memory when deciding the next reply. Keep the reply consistent with the sheet instructions and known rows. Do not reveal this metadata unless the character card explicitly asks for it.',
    ];
    let emitted = 0;

    for (const sheet of sheets) {
        if (!sheet || typeof sheet !== 'object') {
            continue;
        }
        const uid = String(sheet.uid || '').trim();
        if (selected.size && uid && !selected.has(uid)) {
            continue;
        }
        if (sheet.enable === false || sheet.enabled === false) {
            continue;
        }
        const config = sheet.config && typeof sheet.config === 'object' ? sheet.config : {};
        const toChat = sheet.tochat ?? sheet.toChat ?? config.toChat ?? config.tochat;
        if (toChat === false) {
            continue;
        }

        const cellHistory = Array.isArray(sheet.cellHistory) ? sheet.cellHistory : [];
        const byUid = new Map();
        for (const cell of cellHistory) {
            const cellUid = String(cell?.uid || '').trim();
            if (cellUid) {
                byUid.set(cellUid, cell);
            }
        }

        const origin = cellHistory.find(cell => cell?.type === 'sheet_origin');
        const originData = origin?.data && typeof origin.data === 'object' ? origin.data : {};
        const name = normalizeRuntimeSheetText(sheet.name || uid || `sheet_${emitted + 1}`, macroOptions);
        lines.push('');
        lines.push(`Sheet: ${name}`);
        if (sheet.required === true) {
            lines.push('Required: true');
        }
        pushRuntimeSheetLine(lines, 'Note', originData.note, macroOptions);
        pushRuntimeSheetLine(lines, 'Init rule', originData.initNode, macroOptions);
        pushRuntimeSheetLine(lines, 'Update rule', originData.updateNode, macroOptions);
        pushRuntimeSheetLine(lines, 'Insert rule', originData.insertNode, macroOptions);
        pushRuntimeSheetLine(lines, 'Delete rule', originData.deleteNode, macroOptions);

        const columns = cellHistory
            .filter(cell => cell?.type === 'column_header')
            .map(cell => readRuntimeSheetCellText(cell, macroOptions))
            .filter(Boolean);
        if (columns.length) {
            lines.push(`Columns: ${columns.join(' | ')}`);
        }

        const rows = [];
        const hashSheet = Array.isArray(sheet.hashSheet) ? sheet.hashSheet : [];
        for (const row of hashSheet) {
            if (!Array.isArray(row)) {
                continue;
            }
            const values = row
                .map(cellUid => byUid.get(String(cellUid || '').trim()))
                .filter(cell => cell && cell.type !== 'sheet_origin' && cell.type !== 'column_header')
                .map(cell => readRuntimeSheetCellText(cell, macroOptions))
                .filter(Boolean);
            if (values.length) {
                rows.push(values.join(' | '));
            }
            if (rows.length >= RUNTIME_SHEETS_MAX_ROWS_PER_SHEET) {
                break;
            }
        }
        if (rows.length) {
            lines.push('Known rows:');
            for (const row of rows) {
                lines.push(`- ${row}`);
            }
        }

        emitted++;
        if (emitted >= RUNTIME_SHEETS_MAX_SHEETS) {
            break;
        }
        if (lines.join('\n').length >= RUNTIME_SHEETS_MAX_PROMPT_CHARS) {
            break;
        }
    }

    if (emitted === 0) {
        return '';
    }
    const prompt = lines.join('\n').trim();
    return prompt.length > RUNTIME_SHEETS_MAX_PROMPT_CHARS
        ? `${prompt.slice(0, RUNTIME_SHEETS_MAX_PROMPT_CHARS).trim()}\n[dynamic sheets truncated]`
        : prompt;
}

async function loadCharacterPrompt(directories, avatarUrl) {
    const filename = sanitize(String(avatarUrl || ''));
    if (!filename || !filename.toLowerCase().endsWith('.png')) {
        return '';
    }
    const cardPath = path.join(directories.characters, filename);
    if (!fs.existsSync(cardPath)) {
        return '';
    }
    const raw = await parseCharacterCard(cardPath, 'png');
    const card = tryParse(raw) || {};
    const parts = [];
    const name = String(card?.name || '').trim();
    const description = String(card?.description || '').trim();
    const personality = String(card?.personality || '').trim();
    const scenario = String(card?.scenario || '').trim();
    const firstMes = String(card?.first_mes || '').trim();
    const example = String(card?.mes_example || '').trim();
    const systemPrompt = String(card?.system_prompt || '').trim();

    if (name) parts.push(`Name: ${name}`);
    if (description) parts.push(`Description: ${description}`);
    if (personality) parts.push(`Personality: ${personality}`);
    if (scenario) parts.push(`Scenario: ${scenario}`);
    if (systemPrompt) parts.push(`System: ${systemPrompt}`);
    if (firstMes) parts.push(`First message: ${firstMes}`);
    if (example) parts.push(`Example dialogue:\n${example}`);

    return parts.length ? `Character card\n${parts.join('\n')}` : '';
}

async function loadCharacterCard(directories, avatarUrl) {
    const filename = sanitize(String(avatarUrl || ''));
    if (!filename || !filename.toLowerCase().endsWith('.png')) {
        return null;
    }
    const cardPath = path.join(directories.characters, filename);
    if (!fs.existsSync(cardPath)) {
        return null;
    }
    const raw = await parseCharacterCard(cardPath, 'png');
    return tryParse(raw) || null;
}

function expandRuntimeMacros(text, { userName = '', charName = '', lastChatMessage = '' } = {}) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/\{\{\s*user\s*\}\}/gi, userName || 'user')
        .replace(/\{\{\s*name1\s*\}\}/gi, userName || 'user')
        .replace(/\{\{\s*char\s*\}\}/gi, charName || 'assistant')
        .replace(/\{\{\s*name2\s*\}\}/gi, charName || 'assistant')
        .replace(/\{\{\s*lastChatMessage\s*\}\}/gi, lastChatMessage || '');
}

function readCharacterCardText(card, key) {
    if (!card || typeof card !== 'object' || !key) {
        return '';
    }
    const direct = String(card?.[key] ?? '').trim();
    if (direct) {
        return direct;
    }
    return String(card?.data?.[key] ?? '').trim();
}

function buildCharacterPromptSections(card, { userName = '', charName = '' } = {}) {
    if (!card || typeof card !== 'object') {
        return [];
    }
    const description = readCharacterCardText(card, 'description');
    const personality = readCharacterCardText(card, 'personality');
    const scenario = readCharacterCardText(card, 'scenario');

    const sections = [];
    if (description) sections.push({ identifier: 'charDescription', content: description });
    if (personality) sections.push({ identifier: 'charPersonality', content: personality });
    if (scenario) {
        sections.push({ identifier: 'scenario', content: scenario });
    }
    return sections
        .map(section => ({
            ...section,
            content: expandRuntimeMacros(section.content, { userName, charName }),
        }))
        .filter(section => section.content);
}

function buildCharacterMainPrompt(card, { userName = '', charName = '' } = {}) {
    return expandRuntimeMacros(readCharacterCardText(card, 'system_prompt'), { userName, charName });
}

function buildCharacterDialogueExamples(card, { userName = '', charName = '' } = {}) {
    if (!card || typeof card !== 'object') {
        return [];
    }
    const example = readCharacterCardText(card, 'mes_example');
    if (example) {
        return parseRuntimeDialogueExamples(example, { userName, charName });
    }
    return [];
}

function parseRuntimeDialogueExamples(rawExamples, { userName = '', charName = '' } = {}) {
    let source = expandRuntimeMacros(rawExamples, { userName, charName })
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n')
        .trim();
    if (!source || source === '<START>') {
        return [];
    }
    if (!source.match(/^<START>/i)) {
        source = `<START>\n${source}`;
    }
    return source
        .split(/<START>/gi)
        .slice(1)
        .map(block => parseRuntimeDialogueExampleBlock(block, { userName, charName }))
        .filter(block => Array.isArray(block) && block.length > 0);
}

function parseRuntimeDialogueExampleBlock(rawBlock, { userName = '', charName = '' } = {}) {
    const block = String(rawBlock ?? '').trim();
    if (!block) {
        return [];
    }

    const lines = ['{Example Dialogue:}', ...block.split('\n')];
    const userPrefix = `${userName || 'User'}:`;
    const charPrefix = `${charName || 'Assistant'}:`;
    const messages = [];
    let currentLines = [];
    let currentName = '';
    let currentSystemName = '';

    const flush = () => {
        if (!currentSystemName || currentLines.length === 0) {
            currentLines = [];
            return;
        }
        const prefix = `${currentName}:`;
        let content = currentLines.join('\n').trim();
        if (content.startsWith(prefix)) {
            content = content.slice(prefix.length).trim();
        }
        if (content) {
            messages.push({
                role: 'system',
                content,
                name: currentSystemName,
            });
        }
        currentLines = [];
    };

    for (let i = 1; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith(userPrefix)) {
            flush();
            currentName = userName || 'User';
            currentSystemName = 'example_user';
        } else if (line.startsWith(charPrefix)) {
            flush();
            currentName = charName || 'Assistant';
            currentSystemName = 'example_assistant';
        }
        currentLines.push(line);
    }
    flush();

    if (messages.length > 0) {
        return messages;
    }
    return [{ role: 'system', content: block }];
}

function buildCharacterFirstMes(card, { userName = '', charName = '' } = {}) {
    return expandRuntimeMacros(readCharacterCardText(card, 'first_mes'), { userName, charName });
}

function buildCharacterPostHistoryInstructions(card, { userName = '', charName = '' } = {}) {
    return expandRuntimeMacros(readCharacterCardText(card, 'post_history_instructions'), { userName, charName });
}

function buildCharacterDepthPrompt(card, { userName = '', charName = '' } = {}) {
    const raw =
        card?.data?.extensions?.depth_prompt ??
        card?.extensions?.depth_prompt ??
        null;
    if (!raw) {
        return null;
    }

    const promptRaw = typeof raw === 'string'
        ? raw
        : raw && typeof raw === 'object'
            ? raw.prompt ?? raw.value ?? raw.text ?? ''
            : '';
    const content = expandRuntimeMacros(String(promptRaw ?? '').trim(), { userName, charName });
    if (!content) {
        return null;
    }

    const depthRaw = raw && typeof raw === 'object' ? raw.depth : undefined;
    const depthValue = toFiniteNumber(depthRaw);
    const depth = Math.max(0, Math.min(10_000, Math.trunc(depthValue ?? 4)));
    const role = raw && typeof raw === 'object' ? raw.role ?? 0 : 0;

    return {
        depth,
        role,
        content,
        source: 'character_depth_prompt',
    };
}

function escapeRuntimeRegexMacro(value) {
    return String(value ?? '').replace(/[\n\r\t\v\f\0.^$*+?{}[\]\\/|()]/g, (s) => {
        switch (s) {
            case '\n': return '\\n';
            case '\r': return '\\r';
            case '\t': return '\\t';
            case '\v': return '\\v';
            case '\f': return '\\f';
            case '\0': return '\\0';
            default: return `\\${s}`;
        }
    });
}

function expandRuntimeRegexMacros(text, { userName = '', charName = '' } = {}, sanitizer = null) {
    const apply = typeof sanitizer === 'function' ? sanitizer : (value) => value;
    return String(text ?? '')
        .replace(/\{\{\s*user\s*\}\}/gi, apply(userName || 'user'))
        .replace(/\{\{\s*char\s*\}\}/gi, apply(charName || 'assistant'));
}

function runtimeRegexFromString(input) {
    try {
        const value = String(input ?? '');
        const m = value.match(/(\/?)(.+)\1([a-z]*)/i);
        if (!m) {
            return null;
        }
        if (m[3] && !/^(?!.*?(.).*?\1)[gmixXsuUAJ]+$/.test(m[3])) {
            return new RegExp(value);
        }
        return new RegExp(m[2], m[3]);
    } catch {
        return null;
    }
}

function readCharacterRegexScripts(card) {
    const raw =
        card?.data?.extensions?.regex_scripts ??
        card?.extensions?.regex_scripts ??
        [];
    if (!Array.isArray(raw)) {
        return [];
    }
    return raw
        .filter(script => script && typeof script === 'object')
        .slice(0, RUNTIME_REGEX_SCRIPT_LIMIT);
}

async function readRuntimeRegexContext(request, {
    avatarUrl = '',
    userName = '',
    charName = '',
    headerUserName = '',
    headerCharName = '',
    cardName = '',
} = {}) {
    let card = null;
    try {
        card = await loadCharacterCard(request.user.directories, avatarUrl);
    } catch (error) {
        console.warn('runtime-chat regex context card fallback', {
            avatarUrl,
            error: error?.message || String(error),
        });
    }
    const settings = readRuntimeSettings(request.user.directories);
    const resolvedUserName = firstNonBlank(userName, headerUserName, settings?.username);
    const resolvedCharName = firstNonBlank(charName, String(card?.name ?? '').trim(), headerCharName, cardName);
    return {
        card,
        regexScripts: readCharacterRegexScripts(card),
        userName: resolvedUserName,
        charName: resolvedCharName,
    };
}

export function applyRuntimeStoredMessageRegex(mes, isUser, regexContext) {
    const raw = String(mes ?? '');
    const userName = regexContext?.userName ?? '';
    const charName = regexContext?.charName ?? '';
    const regexScripts = Array.isArray(regexContext?.regexScripts) ? regexContext.regexScripts : [];
    const placement = isUser ? RUNTIME_REGEX_PLACEMENT.USER_INPUT : RUNTIME_REGEX_PLACEMENT.AI_OUTPUT;
    const regexed = applyRuntimeRegexScripts(
        raw,
        regexScripts,
        placement,
        { userName, charName },
    );
    return isUser ? expandRuntimeMacros(regexed, { userName, charName }) : regexed;
}

function runtimeRegexPlacementAllowed(script, placement) {
    if (!script || placement === undefined) {
        return false;
    }
    const placements = Array.isArray(script.placement)
        ? script.placement.map(item => Number(item))
        : [];
    return placements.includes(Number(placement));
}

function runtimeRegexPhaseAllowed(script, { isMarkdown = false, isPrompt = false } = {}) {
    return Boolean(
        (script.markdownOnly && isMarkdown) ||
        (script.promptOnly && isPrompt) ||
        (!script.markdownOnly && !script.promptOnly && !isMarkdown && !isPrompt)
    );
}

function runtimeRegexDepthAllowed(script, depth) {
    if (typeof depth !== 'number' || !Number.isFinite(depth)) {
        return true;
    }
    const minDepth = Number(script?.minDepth);
    if (!Number.isNaN(minDepth) && minDepth >= -1 && depth < minDepth) {
        return false;
    }
    const maxDepth = Number(script?.maxDepth);
    if (!Number.isNaN(maxDepth) && maxDepth >= 0 && depth > maxDepth) {
        return false;
    }
    return true;
}

function runtimeRegexFindString(script, options = {}) {
    switch (Number(script?.substituteRegex)) {
        case RUNTIME_REGEX_SUBSTITUTE.RAW:
            return expandRuntimeRegexMacros(script.findRegex, options);
        case RUNTIME_REGEX_SUBSTITUTE.ESCAPED:
            return expandRuntimeRegexMacros(script.findRegex, options, escapeRuntimeRegexMacro);
        case RUNTIME_REGEX_SUBSTITUTE.NONE:
        default:
            return String(script?.findRegex ?? '');
    }
}

function filterRuntimeRegexReplacement(value, trimStrings, options = {}) {
    let finalString = String(value ?? '');
    if (!Array.isArray(trimStrings)) {
        return finalString;
    }
    for (const trimString of trimStrings) {
        const needle = expandRuntimeMacros(trimString, options);
        if (needle) {
            finalString = finalString.split(needle).join('');
        }
    }
    return finalString;
}

function runtimeRegexReplaceString(script, args, options = {}) {
    const match = args[0];
    const groups = args[args.length - 1] && typeof args[args.length - 1] === 'object'
        ? args[args.length - 1]
        : {};
    const template = String(script?.replaceString ?? '').replace(/{{match}}/gi, '$0');
    const replaced = template.replaceAll(/\$(\d+)|\$<([^>]+)>/g, (_, num, groupName) => {
        const value = num ? args[Number(num)] : groups?.[groupName];
        if (!value) {
            return '';
        }
        return filterRuntimeRegexReplacement(value, script?.trimStrings, options);
    });
    return expandRuntimeMacros(replaced, options);
}

function runRuntimeRegexScript(script, rawString, options = {}) {
    if (!script || script.disabled || !script.findRegex || !rawString || typeof rawString !== 'string') {
        return rawString;
    }
    if (rawString.length > RUNTIME_REGEX_TEXT_LIMIT) {
        return rawString;
    }
    const regex = runtimeRegexFromString(runtimeRegexFindString(script, options));
    if (!regex) {
        return rawString;
    }
    try {
        return rawString.replace(regex, function () {
            return runtimeRegexReplaceString(script, Array.from(arguments), options);
        });
    } catch {
        return rawString;
    }
}

export function applyRuntimeRegexScripts(rawString, scripts, placement, options = {}) {
    if (typeof rawString !== 'string' || !rawString || !Array.isArray(scripts) || scripts.length === 0) {
        return rawString;
    }
    let finalString = rawString;
    for (const script of scripts) {
        if (!runtimeRegexPlacementAllowed(script, placement)) {
            continue;
        }
        if (!runtimeRegexPhaseAllowed(script, options)) {
            continue;
        }
        if (!runtimeRegexDepthAllowed(script, options.depth)) {
            continue;
        }
        finalString = runRuntimeRegexScript(script, finalString, options);
    }
    return finalString;
}

function readDefaultWorldNames(directories, card, charName = '') {
    const root = readRuntimeSettings(directories);
    const worldInfo = root?.world_info_settings?.world_info;
    const globalSelect = Array.isArray(worldInfo?.globalSelect)
        ? worldInfo.globalSelect.map(item => String(item ?? '').trim()).filter(Boolean)
        : [];
    const targetNames = new Set(
        [String(charName || '').trim(), String(card?.name || '').trim()]
            .filter(Boolean)
            .map(name => name.toLowerCase()),
    );
    const charLore = Array.isArray(worldInfo?.charLore) ? worldInfo.charLore : [];
    const extraBooks = [];
    for (const item of charLore) {
        const itemName = String(item?.name ?? '').trim().toLowerCase();
        if (!itemName || !targetNames.has(itemName)) {
            continue;
        }
        const books = Array.isArray(item?.extraBooks)
            ? item.extraBooks.map(book => String(book ?? '').trim()).filter(Boolean)
            : [];
        extraBooks.push(...books);
    }
    const cardWorld = String(
        card?.data?.extensions?.world ??
        card?.extensions?.world ??
        card?.world ??
        '',
    ).trim();
    return [...new Set([...globalSelect, ...extraBooks, ...(cardWorld ? [cardWorld] : [])])];
}

function readEmbeddedCharacterBooks(card) {
    const book = card?.data?.character_book ?? card?.character_book;
    if (!book || typeof book !== 'object') {
        return [];
    }
    return [book];
}

function expandWorldInfoBlocks(blocks, { userName = '', charName = '', regexScripts = [] } = {}) {
    if (!blocks || typeof blocks !== 'object') {
        return blocks;
    }
    const macroOptions = { userName, charName };
    const regexOptions = { userName, charName, regexScripts, isPrompt: true };
    const beforeChar = applyRuntimeRegexScripts(
        expandRuntimeMacros(blocks.beforeChar ?? '', macroOptions),
        regexScripts,
        RUNTIME_REGEX_PLACEMENT.WORLD_INFO,
        regexOptions,
    );
    const afterChar = applyRuntimeRegexScripts(
        expandRuntimeMacros(blocks.afterChar ?? '', macroOptions),
        regexScripts,
        RUNTIME_REGEX_PLACEMENT.WORLD_INFO,
        regexOptions,
    );
    const depthInjections = Array.isArray(blocks.depthInjections)
        ? blocks.depthInjections
            .map(injection => {
                const rawDepth = toFiniteNumber(injection?.depth);
                const depth = Math.max(0, Math.min(10_000, Math.trunc(rawDepth ?? 4)));
                const content = applyRuntimeRegexScripts(
                    expandRuntimeMacros(injection?.content ?? injection?.prompt ?? injection?.text ?? '', macroOptions),
                    regexScripts,
                    RUNTIME_REGEX_PLACEMENT.WORLD_INFO,
                    { ...regexOptions, depth },
                ).trim();
                if (!content) {
                    return null;
                }
                return {
                    ...injection,
                    depth,
                    content,
                };
            })
            .filter(Boolean)
        : [];
    return {
        ...blocks,
        beforeChar,
        afterChar,
        depthInjections,
    };
}

function emptyWorldInfoBlocks(state = {}, extraDebug = {}) {
    return {
        beforeChar: '',
        afterChar: '',
        depthInjections: [],
        debug: {
            activatedCount: 0,
            totalEntries: 0,
            triggeredKeys: [],
            updatedState: state && typeof state === 'object' ? { ...state } : {},
            ...extraDebug,
        },
    };
}

function normalizeRuntimeChatMessages(chatData, { userName = '', charName = '', regexScripts = [] } = {}) {
    if (!Array.isArray(chatData)) {
        return [];
    }
    return chatData.map((message, index) => {
        if (!message || typeof message !== 'object') {
            return message;
        }
        const depth = Math.max(0, chatData.length - index - 1);
        const placement = message.is_user === true
            ? RUNTIME_REGEX_PLACEMENT.USER_INPUT
            : RUNTIME_REGEX_PLACEMENT.AI_OUTPUT;
        const nextMes =
            typeof message.mes === 'string'
                ? applyRuntimeRegexScripts(
                    expandRuntimeMacros(message.mes, { userName, charName }),
                    regexScripts,
                    placement,
                    { userName, charName, isPrompt: true, depth },
                )
                : message.mes;
        const nextName =
            typeof message.name === 'string'
                ? expandRuntimeMacros(message.name, { userName, charName })
                : message.name;
        if (nextMes === message.mes && nextName === message.name) {
            return message;
        }
        return {
            ...message,
            mes: nextMes,
            name: nextName,
        };
    });
}

function firstNonBlank(...values) {
    for (const value of values) {
        const text = String(value ?? '').trim();
        if (text) {
            return text;
        }
    }
    return '';
}

function toFiniteNumber(value) {
    const num = Number(value);
    return Number.isFinite(num) ? num : null;
}

function toBoolean(value, fallback = false) {
    if (typeof value === 'boolean') return value;
    if (value === 'true') return true;
    if (value === 'false') return false;
    return fallback;
}

function firstDefined(...values) {
    for (const value of values) {
        if (value !== undefined && value !== null) {
            return value;
        }
    }
    return undefined;
}

function cloneRuntimeJson(value) {
    if (value === undefined || value === null) {
        return value;
    }
    try {
        return JSON.parse(JSON.stringify(value));
    } catch {
        return value;
    }
}

function normalizeRuntimePresetBundle(raw) {
    if (!raw) {
        return null;
    }
    let bundle = raw;
    if (typeof raw === 'string') {
        try {
            bundle = JSON.parse(raw);
        } catch {
            return null;
        }
    }
    if (!bundle || typeof bundle !== 'object' || Array.isArray(bundle)) {
        return null;
    }
    const generation = bundle.generation && typeof bundle.generation === 'object' && !Array.isArray(bundle.generation)
        ? bundle.generation
        : bundle;
    if (!generation || typeof generation !== 'object' || Array.isArray(generation)) {
        return null;
    }
    return { ...bundle, generation };
}

function applyRuntimePresetBundle(root, presetBundle) {
    const bundle = normalizeRuntimePresetBundle(presetBundle);
    if (!bundle) {
        return root;
    }
    const generation = cloneRuntimeJson(bundle.generation);
    if (!generation || typeof generation !== 'object' || Array.isArray(generation)) {
        return root;
    }
    const merged = root && typeof root === 'object' && !Array.isArray(root)
        ? cloneRuntimeJson(root)
        : {};
    const oaiSettings = merged.oai_settings && typeof merged.oai_settings === 'object' && !Array.isArray(merged.oai_settings)
        ? cloneRuntimeJson(merged.oai_settings)
        : {};
    merged.oai_settings = {
        ...oaiSettings,
        ...generation,
    };
    for (const [key, value] of Object.entries(generation)) {
        merged[key] = cloneRuntimeJson(value);
    }
    return merged;
}

function parseWorldNamesRaw(raw, fromQuery = false) {
    if (Array.isArray(raw)) {
        return raw.map(item => String(item ?? '').trim()).filter(Boolean);
    }
    if (!fromQuery) {
        return [];
    }
    return String(raw || '')
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);
}

function readRuntimeSettings(directories, presetBundle = null) {
    try {
        const settingsPath = path.join(directories.root, SETTINGS_FILE);
        if (!fs.existsSync(settingsPath)) {
            return applyRuntimePresetBundle({}, presetBundle);
        }
        const raw = fs.readFileSync(settingsPath, 'utf8');
        return applyRuntimePresetBundle(tryParse(raw) || {}, presetBundle);
    } catch {
        return applyRuntimePresetBundle({}, presetBundle);
    }
}

function readRuntimePowerUserSettings(root) {
    if (root?.power_user && typeof root.power_user === 'object') {
        return root.power_user;
    }
    return root && typeof root === 'object' ? root : {};
}

function readRuntimeContextSettings(root) {
    const powerUser = readRuntimePowerUserSettings(root);
    if (powerUser?.context && typeof powerUser.context === 'object') {
        return powerUser.context;
    }
    if (root?.context && typeof root.context === 'object') {
        return root.context;
    }
    return {};
}

function readRuntimeInstructSettings(root) {
    const powerUser = readRuntimePowerUserSettings(root);
    if (powerUser?.instruct && typeof powerUser.instruct === 'object') {
        return powerUser.instruct;
    }
    if (root?.instruct && typeof root.instruct === 'object') {
        return root.instruct;
    }
    return {};
}

function uniqueRuntimeStrings(values) {
    const seen = new Set();
    const out = [];
    for (const value of Array.isArray(values) ? values : []) {
        const text = String(value ?? '');
        if (!text || seen.has(text)) {
            continue;
        }
        seen.add(text);
        out.push(text);
    }
    return out;
}

function parseRuntimeStringList(value) {
    if (Array.isArray(value)) {
        return value.map(item => String(item ?? '')).filter(item => item.length > 0);
    }
    const text = String(value ?? '');
    if (!text) {
        return [];
    }
    try {
        const parsed = JSON.parse(text);
        if (Array.isArray(parsed)) {
            return parsed.map(item => String(item ?? '')).filter(item => item.length > 0);
        }
    } catch {
        // Fall back to newline-separated text below.
    }
    return text.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
}

function readRuntimeCustomStoppingStrings(root, macroOptions = {}) {
    const powerUser = readRuntimePowerUserSettings(root);
    const raw = powerUser.custom_stopping_strings ?? root?.custom_stopping_strings;
    const macroEnabled = powerUser.custom_stopping_strings_macro ?? root?.custom_stopping_strings_macro;
    const strings = parseRuntimeStringList(raw);
    if (macroEnabled === false) {
        return strings;
    }
    return strings.map(item => expandRuntimeMacros(item, macroOptions));
}

function pushRuntimeInstructSequence(out, sequence, instruct, macroOptions = {}) {
    const raw = String(sequence ?? '');
    if (!raw || raw.trim().length === 0) {
        return;
    }
    const wrapped = instruct?.wrap === false ? raw : `\n${raw}`;
    out.push(instruct?.macro === false ? wrapped : expandRuntimeMacros(wrapped, macroOptions));
}

function readRuntimeInstructStoppingSequences(root, { userName = '', charName = '' } = {}) {
    const instruct = readRuntimeInstructSettings(root);
    const context = readRuntimeContextSettings(root);
    const enabled = toBoolean(instruct?.enabled, false);
    const out = [];
    if (enabled) {
        const macroOptions = { userName, charName };
        const stopSequence = String(instruct.stop_sequence ?? '');
        const inputSequence = String(instruct.input_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, userName || 'user');
        const outputSequence = String(instruct.output_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, charName || 'assistant');
        const firstOutputSequence = String(instruct.first_output_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, charName || 'assistant');
        const lastOutputSequence = String(instruct.last_output_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, charName || 'assistant');
        const systemSequence = String(instruct.system_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, 'System');
        const lastSystemSequence = String(instruct.last_system_sequence ?? '').replace(/\{\{\s*name\s*\}\}/gi, 'System');
        const sequences = [stopSequence];
        if (instruct.sequences_as_stop_strings !== false) {
            sequences.push(
                inputSequence,
                outputSequence,
                firstOutputSequence,
                lastOutputSequence,
                systemSequence,
                lastSystemSequence,
            );
        }
        uniqueRuntimeStrings(sequences.join('\n').split('\n'))
            .forEach(sequence => pushRuntimeInstructSequence(out, sequence, instruct, macroOptions));
    }

    if (context.use_stop_strings !== false) {
        const macroOptions = { userName, charName };
        if (context.chat_start) {
            out.push(`\n${expandRuntimeMacros(context.chat_start, macroOptions)}`);
        }
        if (context.example_separator) {
            out.push(`\n${expandRuntimeMacros(context.example_separator, macroOptions)}`);
        }
    }
    return out;
}

function readRuntimeStoppingStrings(directories, {
    userName = '',
    charName = '',
    isImpersonate = false,
    isContinue = false,
    lastMessageIsUser = false,
    runtimePresetBundle = null,
} = {}) {
    const root = readRuntimeSettings(directories, runtimePresetBundle);
    const powerUser = readRuntimePowerUserSettings(root);
    const context = readRuntimeContextSettings(root);
    const out = [];
    const safeUserName = String(userName || '').trim();
    const safeCharName = String(charName || '').trim();

    if (context.names_as_stop_strings !== false) {
        const userStop = safeUserName ? `\n${safeUserName}:` : '';
        const charStop = safeCharName ? `\n${safeCharName}:` : '';
        if (isImpersonate) {
            if (charStop) out.push(charStop);
        } else if (userStop) {
            out.push(userStop);
        }
        if (userStop) {
            out.push(userStop);
        }
        if (isContinue && lastMessageIsUser && charStop) {
            out.push(charStop);
        }
    }

    out.push(...readRuntimeInstructStoppingSequences(root, { userName: safeUserName, charName: safeCharName }));
    out.push(...readRuntimeCustomStoppingStrings(root, { userName: safeUserName, charName: safeCharName }));
    if (powerUser.single_line) {
        out.unshift('\n');
    }
    return uniqueRuntimeStrings(out);
}

function normalizeContinueNudgeRole(role) {
    const value = String(role || '').trim().toLowerCase();
    if (['system', 'user', 'assistant', 'developer'].includes(value)) {
        return value;
    }
    return DEFAULT_CONTINUE_NUDGE_ROLE;
}

function findLastRuntimeMessageContent(messages, role = '') {
    if (!Array.isArray(messages)) {
        return '';
    }
    const targetRole = String(role || '').trim().toLowerCase();
    for (let i = messages.length - 1; i >= 0; i--) {
        const message = messages[i];
        if (!message || typeof message !== 'object') {
            continue;
        }
        if (targetRole && String(message.role || '').trim().toLowerCase() !== targetRole) {
            continue;
        }
        const content = String(message.content ?? '');
        if (content) {
            return content;
        }
    }
    return '';
}

function readContinueNudgeMessage(directories, options = {}) {
    const root = readRuntimeSettings(directories, options.runtimePresetBundle);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const envRole = String(process.env.ST_RUNTIME_CONTINUE_NUDGE_ROLE || '').trim();
    const prompt = expandRuntimeMacros(firstNonBlank(
        options.continueNudgePrompt,
        options.continue_nudge_prompt,
        oaiSettings?.continue_nudge_prompt,
        root?.continue_nudge_prompt,
        DEFAULT_CONTINUE_NUDGE_PROMPT,
    ), {
        userName: options.userName,
        charName: options.charName,
        lastChatMessage: options.lastChatMessage,
    });
    const role = normalizeContinueNudgeRole(firstNonBlank(
        options.continueNudgeRole,
        options.continue_nudge_role,
        envRole,
        oaiSettings?.continue_nudge_role,
        root?.continue_nudge_role,
        DEFAULT_CONTINUE_NUDGE_ROLE,
    ));
    return { role, content: prompt };
}

function readRuntimeContinueOptions(directories, runtimePresetBundle = null) {
    const root = readRuntimeSettings(directories, runtimePresetBundle);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    return {
        prefill: toBoolean(oaiSettings?.continue_prefill ?? root?.continue_prefill, false),
        postfix: String(oaiSettings?.continue_postfix ?? root?.continue_postfix ?? ' '),
    };
}

function applyContinuePostfix(messages, directories, runtimePresetBundle = null) {
    if (!Array.isArray(messages)) {
        return false;
    }
    const { postfix } = readRuntimeContinueOptions(directories, runtimePresetBundle);
    if (!postfix) {
        return false;
    }
    for (let i = messages.length - 1; i >= 0; i--) {
        const message = messages[i];
        if (!message || String(message.role || '').trim().toLowerCase() !== 'assistant') {
            continue;
        }
        const content = String(message.content ?? '');
        if (!content || content.endsWith(' ')) {
            return false;
        }
        message.content = content + postfix;
        return true;
    }
    return false;
}

function appendContinueNudgeMessage(messages, directories, options = {}) {
    if (!Array.isArray(messages)) {
        return { role: DEFAULT_CONTINUE_NUDGE_ROLE, content: '' };
    }
    const message = readContinueNudgeMessage(directories, {
        ...options,
        lastChatMessage: firstNonBlank(options.lastChatMessage, findLastRuntimeMessageContent(messages, 'assistant')),
    });
    if (!message.content) {
        return message;
    }
    messages.push(message);
    return message;
}

function applyRuntimeContinueBehavior(messages, directories, options = {}) {
    const postfixApplied = applyContinuePostfix(messages, directories, options.runtimePresetBundle);
    const { prefill } = readRuntimeContinueOptions(directories, options.runtimePresetBundle);
    if (prefill) {
        return {
            postfixApplied,
            nudge: null,
            nudgeSkipped: true,
        };
    }
    return {
        postfixApplied,
        nudge: appendContinueNudgeMessage(messages, directories, options),
        nudgeSkipped: false,
    };
}

function modelFieldForChatSource(source) {
    switch (String(source || '').trim().toLowerCase()) {
        case 'openai': return 'openai_model';
        case 'claude': return 'claude_model';
        case 'openrouter': return 'openrouter_model';
        case 'ai21': return 'ai21_model';
        case 'makersuite': return 'google_model';
        case 'vertexai': return 'vertexai_model';
        case 'mistralai': return 'mistralai_model';
        case 'custom': return 'custom_model';
        case 'cohere': return 'cohere_model';
        case 'perplexity': return 'perplexity_model';
        case 'groq': return 'groq_model';
        case 'chutes': return 'chutes_model';
        case 'electronhub': return 'electronhub_model';
        case 'nanogpt': return 'nanogpt_model';
        case 'deepseek': return 'deepseek_model';
        case 'aimlapi': return 'aimlapi_model';
        case 'xai': return 'xai_model';
        case 'pollinations': return 'pollinations_model';
        case 'moonshot': return 'moonshot_model';
        case 'fireworks': return 'fireworks_model';
        case 'cometapi': return 'cometapi_model';
        case 'azure_openai': return 'azure_openai_model';
        case 'zai': return 'zai_model';
        case 'siliconflow': return 'siliconflow_model';
        default: return 'openai_model';
    }
}

function readRuntimeOaiSettings(directories, runtimePresetBundle = null) {
    const root = readRuntimeSettings(directories, runtimePresetBundle);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const source = firstNonBlank(oaiSettings?.chat_completion_source, 'openai');
    const modelField = modelFieldForChatSource(source);
    return {
        source,
        model: firstNonBlank(oaiSettings?.[modelField], oaiSettings?.openai_model),
        reverseProxy: firstNonBlank(oaiSettings?.reverse_proxy),
        proxyPassword: firstNonBlank(oaiSettings?.proxy_password),
        reasoningEffort: firstNonBlank(oaiSettings?.reasoning_effort),
        includeReasoning: toBoolean(firstDefined(oaiSettings?.show_thoughts, root?.show_thoughts), true),
        customUrl: firstNonBlank(oaiSettings?.custom_url),
        temperature: toFiniteNumber(firstDefined(oaiSettings?.temperature, oaiSettings?.temp_openai, root?.temperature, root?.temp_openai)),
        maxContext: toFiniteNumber(oaiSettings?.openai_max_context ?? root?.openai_max_context ?? root?.max_context),
        maxTokens: toFiniteNumber(firstDefined(oaiSettings?.openai_max_tokens, oaiSettings?.max_tokens, root?.openai_max_tokens, root?.max_tokens)),
        topP: toFiniteNumber(firstDefined(oaiSettings?.top_p, oaiSettings?.top_p_openai, root?.top_p, root?.top_p_openai)),
        topK: toFiniteNumber(firstDefined(oaiSettings?.top_k, oaiSettings?.top_k_openai, root?.top_k, root?.top_k_openai)),
        minP: toFiniteNumber(firstDefined(oaiSettings?.min_p, oaiSettings?.min_p_openai, root?.min_p, root?.min_p_openai)),
        topA: toFiniteNumber(firstDefined(oaiSettings?.top_a, oaiSettings?.top_a_openai, root?.top_a, root?.top_a_openai)),
        frequencyPenalty: toFiniteNumber(firstDefined(oaiSettings?.frequency_penalty, oaiSettings?.freq_pen_openai, root?.frequency_penalty, root?.freq_pen_openai)),
        presencePenalty: toFiniteNumber(firstDefined(oaiSettings?.presence_penalty, oaiSettings?.pres_pen_openai, root?.presence_penalty, root?.pres_pen_openai)),
        repetitionPenalty: toFiniteNumber(firstDefined(oaiSettings?.repetition_penalty, oaiSettings?.repetition_penalty_openai, root?.repetition_penalty, root?.repetition_penalty_openai)),
        openrouterMiddleout: firstNonBlank(oaiSettings?.openrouter_middleout, root?.openrouter_middleout),
        openrouterAllowFallbacks:
            oaiSettings?.openrouter_allow_fallbacks === undefined || oaiSettings?.openrouter_allow_fallbacks === null
                ? (
                    root?.openrouter_allow_fallbacks === undefined || root?.openrouter_allow_fallbacks === null
                        ? null
                        : toBoolean(root.openrouter_allow_fallbacks, false)
                )
                : toBoolean(oaiSettings.openrouter_allow_fallbacks, false),
        verbosity: firstNonBlank(oaiSettings?.verbosity_openai, oaiSettings?.verbosity, root?.verbosity_openai, root?.verbosity),
        customPromptPostProcessing: firstNonBlank(oaiSettings?.custom_prompt_post_processing, root?.custom_prompt_post_processing, 'merge_tools'),
        provider: Array.isArray(oaiSettings?.openrouter_providers)
            ? cloneRuntimeJson(oaiSettings.openrouter_providers)
            : Array.isArray(root?.openrouter_providers)
                ? cloneRuntimeJson(root.openrouter_providers)
                : [],
        quantizations: Array.isArray(oaiSettings?.openrouter_quantizations)
            ? cloneRuntimeJson(oaiSettings.openrouter_quantizations)
            : Array.isArray(root?.openrouter_quantizations)
                ? cloneRuntimeJson(root.openrouter_quantizations)
                : [],
        useFallback: toBoolean(firstDefined(oaiSettings?.openrouter_use_fallback, root?.openrouter_use_fallback), false),
        enableWebSearch: toBoolean(firstDefined(oaiSettings?.enable_web_search, root?.enable_web_search), false),
        requestImages: toBoolean(firstDefined(oaiSettings?.request_images, root?.request_images), false),
        requestImageResolution: firstNonBlank(oaiSettings?.request_image_resolution, root?.request_image_resolution),
        requestImageAspectRatio: firstNonBlank(oaiSettings?.request_image_aspect_ratio, root?.request_image_aspect_ratio),
    };
}

function readRuntimePromptOrder(directories, runtimePresetBundle = null) {
    const root = readRuntimeSettings(directories, runtimePresetBundle);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const lists = Array.isArray(oaiSettings?.prompt_order) ? oaiSettings.prompt_order : [];
    const preferred =
        lists.find(item => String(item?.character_id ?? '') === '100001') ||
        lists.find(item => String(item?.character_id ?? '') === '100000') ||
        lists.find(item => Array.isArray(item?.order));
    return Array.isArray(preferred?.order) ? preferred.order : [];
}

function readRuntimeSystemPrompts(directories, { userName = '', charName = '', runtimePresetBundle = null } = {}) {
    const root = readRuntimeSettings(directories, runtimePresetBundle);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const prompts = Array.isArray(oaiSettings?.prompts) ? oaiSettings.prompts : [];
    const out = {};
    for (const prompt of prompts) {
        const identifier = String(prompt?.identifier ?? '').trim();
        const content = String(prompt?.content ?? '').trim();
        if (!identifier || !content) {
            continue;
        }
        out[identifier] = expandRuntimeMacros(content, { userName, charName });
    }
    return out;
}

function normalizeRuntimeTopK(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) {
        return 2;
    }
    const topK = Math.trunc(n);
    if (topK === 0) {
        return -1;
    }
    if (topK < -1) {
        return -1;
    }
    if (topK > 100) {
        return 100;
    }
    return topK;
}

function resolveRuntimePromptTokenBudget(directories, runtimePresetBundle = null) {
    const runtime = readRuntimeOaiSettings(directories, runtimePresetBundle);
    const maxContext = runtime.maxContext ?? 0;
    if (!Number.isFinite(maxContext) || maxContext <= 0) {
        return 0;
    }
    const responseTokens = runtime.maxTokens && runtime.maxTokens > 0
        ? runtime.maxTokens
        : DEFAULT_RUNTIME_RESPONSE_TOKENS;
    const requestedBudget = Math.max(MIN_RUNTIME_PROMPT_TOKENS, Math.trunc(maxContext - responseTokens));
    return Math.min(Math.trunc(maxContext), requestedBudget);
}

async function createLoreTokenEstimator(directories, runtimePresetBundle = null) {
    const runtime = readRuntimeOaiSettings(directories, runtimePresetBundle);
    const requestedModel = String(runtime?.model || '').trim();
    const tokenizerModel = getTokenizerModel(requestedModel || 'gpt-3.5-turbo');
    const fallback = (text) => Math.ceil(String(text ?? '').length / LORE_CHARS_PER_TOKEN);

    try {
        const sentencepiece = getSentencepiceTokenizer(tokenizerModel);
        if (sentencepiece) {
            const instance = await sentencepiece.get();
            if (instance && typeof instance.encodeIds === 'function') {
                return (text) => instance.encodeIds(String(text ?? '')).length;
            }
        }

        const webTokenizer = getWebTokenizer(tokenizerModel);
        if (webTokenizer) {
            const instance = await webTokenizer.get();
            if (instance && typeof instance.encode === 'function') {
                return (text) => instance.encode(String(text ?? '')).length;
            }
        }

        const tiktokenTokenizer = getTiktokenTokenizer(tokenizerModel);
        if (tiktokenTokenizer && typeof tiktokenTokenizer.encode === 'function') {
            return (text) => tiktokenTokenizer.encode(String(text ?? '')).length;
        }
    } catch (error) {
        console.warn('runtime-chat lore tokenizer fallback', requestedModel || tokenizerModel, error?.message || error);
    }

    return fallback;
}

function buildRuntimeGenerateBody(directories, messages, options = {}) {
    const runtime = readRuntimeOaiSettings(directories, options.runtimePresetBundle);
    const source = firstNonBlank(options.chatCompletionSource, runtime.source);
    const model = firstNonBlank(options.model, runtime.model);
    const reverseProxy = firstNonBlank(options.reverseProxy, runtime.reverseProxy);
    const proxyPassword = firstNonBlank(options.proxyPassword, runtime.proxyPassword);
    const customUrl = firstNonBlank(options.customUrl, runtime.customUrl);
    const mode = String(options.mode || '').trim().toLowerCase();
    const stop = readRuntimeStoppingStrings(directories, {
        userName: options.userName,
        charName: options.charName,
        isContinue: mode === 'continue',
        isImpersonate: mode === 'impersonate',
        runtimePresetBundle: options.runtimePresetBundle,
        lastMessageIsUser: Array.isArray(messages) && messages.length > 0
            ? messages[messages.length - 1]?.role === 'user'
            : false,
    });
    const body = {
        stream: options.stream !== false,
        chat_completion_source: source,
        model,
        reverse_proxy: reverseProxy,
        proxy_password: proxyPassword,
        messages: Array.isArray(messages) ? messages : [],
        enable_web_search: runtime.enableWebSearch,
        request_images: runtime.requestImages,
        request_image_resolution: runtime.requestImageResolution,
        request_image_aspect_ratio: runtime.requestImageAspectRatio,
        custom_prompt_post_processing: runtime.customPromptPostProcessing || 'merge_tools',
        use_fallback: runtime.useFallback,
        provider: runtime.provider,
        quantizations: runtime.quantizations,
        allow_fallbacks: runtime.openrouterAllowFallbacks ?? false,
        middleout: runtime.openrouterMiddleout || 'on',
        verbosity: runtime.verbosity || 'low',
        top_k: normalizeRuntimeTopK(runtime.topK ?? 2),
        min_p: runtime.minP ?? 0,
        top_a: runtime.topA ?? 0,
        repetition_penalty: runtime.repetitionPenalty ?? 1,
    };

    if (runtime.includeReasoning) {
        body.include_reasoning = true;
    }
    if (runtime.reasoningEffort) {
        body.reasoning_effort = runtime.reasoningEffort;
    }
    if (runtime.temperature !== null) {
        body.temperature = runtime.temperature;
    }
    if (runtime.maxTokens !== null && runtime.maxTokens > 0) {
        body.max_tokens = runtime.maxTokens;
    }
    if (runtime.topP !== null && runtime.topP > 0) {
        body.top_p = runtime.topP;
    }
    if (runtime.frequencyPenalty !== null) {
        body.frequency_penalty = runtime.frequencyPenalty;
    }
    if (runtime.presencePenalty !== null) {
        body.presence_penalty = runtime.presencePenalty;
    }
    if (source === 'custom' && customUrl) {
        body.custom_url = customUrl;
    }
    if (stop.length > 0) {
        body.stop = stop;
    }
    if (Array.isArray(options.allowedFeatures) && options.allowedFeatures.length > 0) {
        body.allowed_features = options.allowedFeatures;
    }
    if (String(options.userName || '').trim()) {
        body.user_name = String(options.userName).trim();
    }
    if (String(options.charName || '').trim()) {
        body.char_name = String(options.charName).trim();
    }
    body.group_names = Array.isArray(options.groupNames) ? options.groupNames : [];
    return body;
}

async function prepareRuntimeBuildContext(request, {
    fromQuery = false,
    defaultLoreMode = 'trigger',
    chatData: providedChatData = null,
} = {}) {
    const source = fromQuery ? request.query : request.body;
    const avatarUrl = String(source?.avatar_url || '').trim();
    const fileName = String(source?.file_name || '').trim();
    const userName = String(source?.user_name ?? '').trim();
    const charName = String(source?.char_name ?? '').trim();
    const worldNamesRaw = source?.world_names;
    const loreMaxCharsBefore = source?.lore_max_chars_before;
    const loreMaxCharsAfter = source?.lore_max_chars_after;
    const loreMaxTokensBefore = source?.lore_max_tokens_before;
    const loreMaxTokensAfter = source?.lore_max_tokens_after;
    const loreMode = String(source?.lore_mode ?? source?.loreMode ?? '').trim() || defaultLoreMode;
    const tailSystemPrompt = String(source?.tail_system_prompt ?? source?.tailSystemPrompt ?? '').trim();
    const runtimePresetBundle = normalizeRuntimePresetBundle(source?.runtime_preset_bundle ?? source?.runtimePresetBundle);

    if (!avatarUrl || !fileName) {
        throw new Error('avatar_url/file_name required');
    }

    const handle = request.user.profile.handle;
    const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
    const cardName = dirName;
    let chatData = Array.isArray(providedChatData) ? providedChatData : getChatData(chatFilePath);
    const headerUserName = String(chatData?.[0]?.user_name ?? '').trim();
    const headerCharName = String(chatData?.[0]?.character_name ?? '').trim();
    chatData = ensureChatHeader(chatData, userName, charName);

    let card = null;
    try {
        card = await loadCharacterCard(request.user.directories, avatarUrl);
    } catch (error) {
        console.warn('runtime-chat loadCharacterCard fallback', {
            avatarUrl,
            fileName,
            error: error?.message || String(error),
        });
    }
    const settings = readRuntimeSettings(request.user.directories, runtimePresetBundle);
    const resolvedUserName = firstNonBlank(userName, headerUserName, settings?.username);
    const resolvedCharName = firstNonBlank(charName, String(card?.name ?? '').trim(), headerCharName, cardName);
    const regexScripts = readCharacterRegexScripts(card);
    const explicitWorldNames = parseWorldNamesRaw(worldNamesRaw, fromQuery);
    let defaultWorldNames = [];
    if (explicitWorldNames.length === 0) {
        try {
            defaultWorldNames = readDefaultWorldNames(request.user.directories, card, resolvedCharName);
        } catch (error) {
            console.warn('runtime-chat defaultWorldNames fallback', {
                avatarUrl,
                fileName,
                charName: resolvedCharName,
                error: error?.message || String(error),
            });
        }
    }
    const worldNames = explicitWorldNames.length > 0 ? explicitWorldNames : defaultWorldNames;
    const embeddedBooks = readEmbeddedCharacterBooks(card);
    const hasLorebookSources = worldNames.length > 0 || embeddedBooks.length > 0;
    const loreTokenEstimator = await createLoreTokenEstimator(request.user.directories, runtimePresetBundle);
    const maxPromptTokens = resolveRuntimePromptTokenBudget(request.user.directories, runtimePresetBundle);
    const rawRuntimeChatData = normalizeRuntimeChatMessages(chatData.slice(1), {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const runtimeChatData = normalizeRuntimeChatMessages(chatData.slice(1), {
        userName: resolvedUserName,
        charName: resolvedCharName,
        regexScripts,
    });
    const cardPromptSections = buildCharacterPromptSections(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const presetSystemPrompts = readRuntimeSystemPrompts(request.user.directories, {
        userName: resolvedUserName,
        charName: resolvedCharName,
        runtimePresetBundle,
    });
    const mainPrompt = firstNonBlank(buildCharacterMainPrompt(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    }), presetSystemPrompts.main);
    const dialogueExamples = buildCharacterDialogueExamples(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const firstMes = applyRuntimeRegexScripts(
        applyRuntimeRegexScripts(
            buildCharacterFirstMes(card, {
                userName: resolvedUserName,
                charName: resolvedCharName,
            }),
            regexScripts,
            RUNTIME_REGEX_PLACEMENT.AI_OUTPUT,
            { userName: resolvedUserName, charName: resolvedCharName, depth: 0 },
        ),
        regexScripts,
        RUNTIME_REGEX_PLACEMENT.AI_OUTPUT,
        { userName: resolvedUserName, charName: resolvedCharName, isPrompt: true, depth: runtimeChatData.length },
    );
    const postHistoryInstructions = firstNonBlank(buildCharacterPostHistoryInstructions(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    }), presetSystemPrompts.jailbreak);
    const characterDepthPrompt = buildCharacterDepthPrompt(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const depthInjections = characterDepthPrompt ? [characterDepthPrompt] : [];
    const headerMeta = chatData?.[0]?.chat_metadata && typeof chatData[0].chat_metadata === 'object'
        ? chatData[0].chat_metadata
        : {};
    const dynamicSheetsPrompt = buildRuntimeDynamicSheetsPrompt(headerMeta, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const loreState = headerMeta.lorebook_state && typeof headerMeta.lorebook_state === 'object'
        ? headerMeta.lorebook_state
        : {};
    const tick = chatData.length - 1;
    const probabilitySeed = String(chatData?.[chatData.length - 1]?.send_date ?? tick);
    let worldInfoBlocks = emptyWorldInfoBlocks(loreState, { mode: loreMode });
    if (hasLorebookSources) {
        try {
            worldInfoBlocks = expandWorldInfoBlocks(
                buildLorebookBlocks(request.user.directories, worldNames, rawRuntimeChatData, {
                    maxCharsBefore: loreMaxCharsBefore,
                    maxCharsAfter: loreMaxCharsAfter,
                    maxTokensBefore: loreMaxTokensBefore,
                    maxTokensAfter: loreMaxTokensAfter,
                    recursiveScanning: true,
                    recursionMaxRounds: 2,
                    state: loreState,
                    tick,
                    probabilitySeed,
                    tokenEstimator: loreTokenEstimator,
                    mode: loreMode,
                    embeddedBooks,
                }),
                { userName: resolvedUserName, charName: resolvedCharName, regexScripts },
            );
        } catch (error) {
            console.warn('runtime-chat worldInfoBlocks fallback', {
                avatarUrl,
                fileName,
                worldNames,
                error: error?.message || String(error),
            });
            worldInfoBlocks = emptyWorldInfoBlocks(loreState, {
                mode: loreMode,
                build_error: error?.message || String(error),
            });
        }
    }
    let worldInfoPrompt = '';
    if (!hasLorebookSources) {
        try {
            worldInfoPrompt = expandRuntimeMacros(
                buildWorldInfoPrompt(request.user.directories, card),
                { userName: resolvedUserName, charName: resolvedCharName },
            );
            worldInfoPrompt = applyRuntimeRegexScripts(
                worldInfoPrompt,
                regexScripts,
                RUNTIME_REGEX_PLACEMENT.WORLD_INFO,
                { userName: resolvedUserName, charName: resolvedCharName, isPrompt: true },
            );
        } catch (error) {
            console.warn('runtime-chat worldInfoPrompt fallback', {
                avatarUrl,
                fileName,
                error: error?.message || String(error),
            });
        }
    }
    const built = buildRuntimeMessages({
        userName: resolvedUserName,
        charName: resolvedCharName,
        mainPrompt,
        cardPromptSections,
        dialogueExamples,
        promptOrder: readRuntimePromptOrder(request.user.directories, runtimePresetBundle),
        extraSystemPrompts: presetSystemPrompts,
        worldInfoPrompt,
        worldInfoBlocks,
        dynamicSheetsPrompt,
        depthInjections,
        chatData: runtimeChatData,
        firstMes,
        postHistoryInstructions,
        tailSystemPrompt,
        tokenEstimator: loreTokenEstimator,
        maxPromptTokens,
    });

    if (hasLorebookSources && worldInfoBlocks?.debug?.updatedState) {
        const nextState = worldInfoBlocks.debug.updatedState;
        const currentJson = JSON.stringify(loreState);
        const nextJson = JSON.stringify(nextState);
        if (currentJson !== nextJson) {
            try {
                chatData[0].chat_metadata = { ...headerMeta, lorebook_state: nextState };
                await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            } catch (error) {
                console.warn('runtime-chat loreState persist skipped', {
                    avatarUrl,
                    fileName,
                    error: error?.message || String(error),
                });
            }
        }
    }

    return {
        avatarUrl,
        fileName,
        userName: resolvedUserName,
        charName: resolvedCharName,
        worldNames,
        embeddedLorebookCount: embeddedBooks.length,
        handle,
        dirName,
        cardName,
        chatFilePath,
        chatData,
        worldInfoBlocks,
        runtimePresetBundle,
        built: {
            ...built,
            worldInfoBlocks,
            debug: worldInfoBlocks?.debug ?? null,
        },
    };
}

async function buildRuntimeBodyFromGoldenCase(request, goldenCase) {
    const avatarUrl = String(goldenCase?.avatar_url || '').trim();
    const fileName = String(goldenCase?.file_name || '').trim();
    const caseName = normalizeGoldenCaseName(goldenCase?.case_name || goldenCase?.caseName || 'golden-case');
    const tempFileName = `.golden-case-${caseName || 'case'}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
    const { chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, tempFileName);
    const serialized = serializeChatData(goldenCase?.chat_data);
    if (!avatarUrl || !fileName || !serialized) {
        throw new Error('golden_case_invalid');
    }

    fs.writeFileSync(chatFilePath, serialized, 'utf8');
    try {
        const fakeRequest = {
            body: {
                avatar_url: avatarUrl,
                file_name: tempFileName,
                user_name: String(goldenCase?.user_name ?? '').trim(),
                char_name: String(goldenCase?.char_name ?? '').trim(),
                world_names: Array.isArray(goldenCase?.world_names) ? goldenCase.world_names : [],
                lore_mode: String(goldenCase?.lore_mode ?? '').trim(),
                runtime_preset_bundle: goldenCase?.runtime_preset_bundle ?? goldenCase?.runtimePresetBundle,
            },
            user: request.user,
        };
        const defaultLoreMode = String(goldenCase?.lore_mode ?? '').trim() || 'full';
        const ctx = await prepareRuntimeBuildContext(fakeRequest, { fromQuery: false, defaultLoreMode });
        let messages = Array.isArray(ctx.built?.messages) ? [...ctx.built.messages] : [];
        const mode = String(goldenCase?.mode ?? '').trim().toLowerCase();
        if (mode === 'continue') {
            applyRuntimeContinueBehavior(messages, request.user.directories, {
                ...goldenCase,
                userName: ctx.userName,
                charName: ctx.charName,
                runtimePresetBundle: ctx.runtimePresetBundle,
            });
        }
        const runtimeBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: goldenCase?.stream !== false,
            allowedFeatures: Array.isArray(goldenCase?.allowed_features) ? goldenCase.allowed_features : [],
            mode,
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames: Array.isArray(goldenCase?.group_names) ? goldenCase.group_names : [],
            runtimePresetBundle: ctx.runtimePresetBundle,
        });
        return { ctx, runtimeBody };
    } finally {
        try {
            fs.unlinkSync(chatFilePath);
        } catch {
            // Best-effort cleanup for regression temp files.
        }
    }
}

router.post('/append', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const isUser = request.body?.is_user !== undefined ? Boolean(request.body.is_user) : true;
        const messageRef = String(request.body?.message_ref ?? '').trim();
        const outputRegexApplied = request.body?.output_regex_applied === true;
        const hasMes = Object.prototype.hasOwnProperty.call(request.body ?? {}, 'mes');
        const mes = !isUser && outputRegexApplied
            ? String(request.body?.mes ?? '')
            : String(request.body?.mes ?? '').trim();
        const allowEmptyCanonicalAssistant = !isUser && outputRegexApplied && hasMes;

        if (!avatarUrl || !fileName || (!mes && !allowEmptyCanonicalAssistant)) {
            return response.status(400).send({ error: 'avatar_url/file_name/mes required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;

        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);
        const headerUserName = String(chatData?.[0]?.user_name ?? '').trim();
        const headerCharName = String(chatData?.[0]?.character_name ?? '').trim();
        const regexContext = await readRuntimeRegexContext(request, {
            avatarUrl,
            userName,
            charName,
            headerUserName,
            headerCharName,
            cardName,
        });
        const effectiveMes = !isUser && outputRegexApplied
            ? mes
            : applyRuntimeStoredMessageRegex(mes, isUser, regexContext);

        let msg;
        let writeResult;
        if (isUser) {
            writeResult = upsertRuntimeUserMessage(chatData, userName, effectiveMes, messageRef);
            msg = chatData[writeResult.index];
        } else {
            writeResult = upsertRuntimeAssistantMessage(chatData, charName, effectiveMes, messageRef);
            msg = chatData[writeResult.index];
        }
        if (!isUser && (writeResult.inserted || writeResult.changed)) {
            updateRuntimeDynamicSheetsFromMessage(chatData, msg, { userName, charName });
        }

        await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
        return response.send({
            ok: true,
            message_ref: messageRef,
            mes: effectiveMes,
            regex_applied: !isUser && !outputRegexApplied,
            inserted: writeResult.inserted,
            deduplicated: writeResult.deduplicated,
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'append_failed' });
    }
});

router.post('/apply-output-regex', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = String(request.body?.avatar_url || '').trim();
        const fileName = String(request.body?.file_name || '').trim();
        const mes = String(request.body?.mes ?? '').trim();
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();

        if (!avatarUrl || !fileName || !mes) {
            return response.status(400).send({ error: 'avatar_url/file_name/mes required' });
        }

        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const chatData = ensureChatHeader(getChatData(chatFilePath), userName, charName);
        const regexContext = await readRuntimeRegexContext(request, {
            avatarUrl,
            userName,
            charName,
            headerUserName: String(chatData?.[0]?.user_name ?? '').trim(),
            headerCharName: String(chatData?.[0]?.character_name ?? '').trim(),
            cardName: dirName,
        });
        const effectiveMes = applyRuntimeStoredMessageRegex(mes, false, regexContext);

        return response.send({
            ok: true,
            mes: effectiveMes,
            changed: effectiveMes !== mes,
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'apply_output_regex_failed' });
    }
});

router.post('/golden-case/save', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const requestedCaseName = firstNonBlank(request.body?.case_name, request.body?.caseName);
        const resolvedCase = resolveGoldenCasePath(request.user.directories, requestedCaseName);
        const goldenSource = String(request.body?.golden_source ?? request.body?.goldenSource ?? 'browser').trim() || 'browser';
        const capturedGolden = readLatestGoldenGenerateBody(request.user.directories, goldenSource);
        if (!capturedGolden.ok) {
            return response.status(400).send({
                error: 'golden_not_available',
                detail: capturedGolden,
                hint: `Set ST_DEBUG_GOLDEN_DIFF=true and do one ${goldenSource} generation first.`,
            });
        }

        const requestedLoreMode = String(request.body?.lore_mode ?? request.body?.loreMode ?? '').trim() || 'full';
        const requestedMode = String(request.body?.mode ?? '').trim().toLowerCase() || 'generate';
        const groupNames = Array.isArray(request.body?.group_names) ? request.body.group_names : [];
        const allowedFeatures = Array.isArray(request.body?.allowed_features) ? request.body.allowed_features : [];
        const ctx = await prepareRuntimeBuildContext(request, { fromQuery: false, defaultLoreMode: requestedLoreMode });
        let messages = Array.isArray(ctx.built?.messages) ? [...ctx.built.messages] : [];
        if (requestedMode === 'continue') {
            applyRuntimeContinueBehavior(messages, request.user.directories, {
                ...request.body,
                userName: ctx.userName,
                charName: ctx.charName,
                runtimePresetBundle: ctx.runtimePresetBundle,
            });
        }

        const runtimeBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: request.body?.stream !== false,
            allowedFeatures,
            mode: requestedMode,
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames,
            runtimePresetBundle: ctx.runtimePresetBundle,
        });
        const snapshot = {
            case_name: resolvedCase.caseName,
            saved_at: new Date().toISOString(),
            golden_source: capturedGolden.tag || goldenSource,
            avatar_url: ctx.avatarUrl,
            file_name: ctx.fileName,
            user_name: ctx.userName,
            char_name: ctx.charName,
            world_names: ctx.worldNames,
            group_names: groupNames,
            allowed_features: allowedFeatures,
            lore_mode: requestedLoreMode,
            mode: requestedMode,
            stream: request.body?.stream !== false,
            chat_data: ctx.chatData,
            golden_body: capturedGolden.body,
            runtime_body: runtimeBody,
        };
        fs.writeFileSync(resolvedCase.path, JSON.stringify(snapshot, null, 2), 'utf8');

        return response.send({
            ok: true,
            case_name: resolvedCase.caseName,
            path: resolvedCase.path,
            diff: {
                messages: diffMessages(capturedGolden.body?.messages, runtimeBody?.messages),
                body: diffGenerateBodyFields(capturedGolden.body, runtimeBody),
            },
        });
    } catch (err) {
        if (err?.message === 'case_name required') {
            return response.status(400).send({ error: 'case_name required' });
        }
        console.error(err);
        return response.status(500).send({ error: 'golden_case_save_failed' });
    }
});

router.get('/golden-case/list', async (request, response) => {
    try {
        const dir = ensureGoldenCasesDirectory(request.user.directories);
        const items = fs.readdirSync(dir, { withFileTypes: true })
            .filter(entry => entry.isFile() && entry.name.toLowerCase().endsWith('.json'))
            .map(entry => path.join(dir, entry.name))
            .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)
            .map(filePath => {
                const raw = fs.readFileSync(filePath, 'utf8');
                const parsed = tryParse(raw) || {};
                return {
                    case_name: String(parsed?.case_name ?? path.basename(filePath, '.json')),
                    saved_at: parsed?.saved_at ?? null,
                    avatar_url: parsed?.avatar_url ?? '',
                    file_name: parsed?.file_name ?? '',
                    golden_source: parsed?.golden_source ?? '',
                    path: filePath,
                };
            });
        return response.send({ ok: true, items });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'golden_case_list_failed' });
    }
});

router.get('/golden-case/run', async (request, response) => {
    try {
        const caseName = firstNonBlank(request.query?.case_name, request.query?.caseName);
        const goldenCase = readGoldenCaseFile(request.user.directories, caseName);
        if (!goldenCase) {
            return response.status(404).send({ error: 'golden_case_not_found' });
        }
        const runtime = await buildRuntimeBodyFromGoldenCase(request, goldenCase.data);
        const messageDiff = diffMessages(goldenCase.data?.golden_body?.messages, runtime.runtimeBody?.messages);
        const bodyDiff = diffGenerateBodyFields(goldenCase.data?.golden_body, runtime.runtimeBody);
        const pass = !messageDiff.first_diff && bodyDiff.diff_count === 0;
        return response.send({
            ok: true,
            case_name: goldenCase.caseName,
            path: goldenCase.path,
            pass,
            golden: {
                saved_at: goldenCase.data?.saved_at ?? null,
                golden_source: goldenCase.data?.golden_source ?? '',
                avatar_url: goldenCase.data?.avatar_url ?? '',
                file_name: goldenCase.data?.file_name ?? '',
                mode: goldenCase.data?.mode ?? '',
                lore_mode: goldenCase.data?.lore_mode ?? '',
                model: goldenCase.data?.golden_body?.model ?? '',
                chat_completion_source: goldenCase.data?.golden_body?.chat_completion_source ?? '',
            },
            runtime: {
                model: runtime.runtimeBody?.model ?? '',
                chat_completion_source: runtime.runtimeBody?.chat_completion_source ?? '',
                lore_debug: runtime.ctx?.worldInfoBlocks?.debug ?? runtime.ctx?.built?.debug ?? null,
            },
            diff: {
                messages: messageDiff,
                body: bodyDiff,
            },
        });
    } catch (err) {
        if (err?.message === 'case_name required') {
            return response.status(400).send({ error: 'case_name required' });
        }
        console.error(err);
        return response.status(500).send({ error: 'golden_case_run_failed' });
    }
});

router.post('/build', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        let handledByRuntimeBuilder = false;
        try {
            const ctx = await prepareRuntimeBuildContext(request, { fromQuery: false, defaultLoreMode: 'trigger' });
            response.send(ctx.built);
            handledByRuntimeBuilder = true;
        } catch (err) {
            if (err?.message === 'avatar_url/file_name required') {
                return response.status(400).send({ error: 'avatar_url/file_name required' });
            }
            throw err;
        }
        if (handledByRuntimeBuilder) {
            return;
        }

        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const worldNamesRaw = request.body?.world_names;
        const loreMaxCharsBefore = request.body?.lore_max_chars_before;
        const loreMaxCharsAfter = request.body?.lore_max_chars_after;
        const loreMaxTokensBefore = request.body?.lore_max_tokens_before;
        const loreMaxTokensAfter = request.body?.lore_max_tokens_after;
        const loreMode = String(request.body?.lore_mode ?? request.body?.loreMode ?? '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        const cardPrompt = await loadCharacterPrompt(request.user.directories, avatarUrl);
        const card = await loadCharacterCard(request.user.directories, avatarUrl);
        // 1) Prefer explicit world names passed by caller (chat-bound worldbooks)
        // 2) Else fall back to card-bound world (extensions.world / embedded character_book)
        const worldNames = Array.isArray(worldNamesRaw) ? worldNamesRaw : [];
        const headerMeta = chatData?.[0]?.chat_metadata && typeof chatData[0].chat_metadata === 'object'
            ? chatData[0].chat_metadata
            : {};
        const loreState = (headerMeta.lorebook_state && typeof headerMeta.lorebook_state === 'object')
            ? headerMeta.lorebook_state
            : {};
        const tick = chatData.length - 1; // messages excluding header
        const probabilitySeed = String(chatData?.[chatData.length - 1]?.send_date ?? tick);
        const worldInfoBlocks =
            worldNames.length > 0
                ? buildLorebookBlocks(request.user.directories, worldNames, chatData.slice(1), {
                    maxCharsBefore: loreMaxCharsBefore,
                    maxCharsAfter: loreMaxCharsAfter,
                    maxTokensBefore: loreMaxTokensBefore,
                    maxTokensAfter: loreMaxTokensAfter,
                    recursiveScanning: true,
                    recursionMaxRounds: 2,
                    state: loreState,
                    tick,
                    probabilitySeed,
                    mode: loreMode || 'trigger',
                })
                : { beforeChar: '', afterChar: '' };
        // 商用/像 ST：只要 caller 显式传 world_names，就不做“全量世界书兜底注入”。
        // 没有触发条目时，worldInfoBlocks 会为空，从而不污染 prompt。
        const worldInfoPrompt =
            worldNames.length > 0
                ? ''
                : buildWorldInfoPrompt(request.user.directories, card);
        const built = buildRuntimeMessages({
            userName,
            charName,
            cardPrompt,
            worldInfoPrompt,
            worldInfoBlocks,
            chatData: chatData.slice(1),
        });

        // Persist lorebook state for cooldown behavior across turns (best-effort).
        if (worldNames.length > 0 && worldInfoBlocks?.debug?.updatedState) {
            const nextState = worldInfoBlocks.debug.updatedState;
            const currentJson = JSON.stringify(loreState);
            const nextJson = JSON.stringify(nextState);
            if (currentJson !== nextJson) {
                chatData[0].chat_metadata = { ...headerMeta, lorebook_state: nextState };
                await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            }
        }

        return response.send(built);
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'build_failed' });
    }
});

// Debug-friendly variant: GET does not require CSRF token.
router.get('/build', async (request, response) => {
    try {
        let handledByRuntimeBuilder = false;
        try {
            const ctx = await prepareRuntimeBuildContext(request, { fromQuery: true, defaultLoreMode: 'trigger' });
            response.send(ctx.built);
            handledByRuntimeBuilder = true;
        } catch (err) {
            if (err?.message === 'avatar_url/file_name required') {
                return response.status(400).send({ error: 'avatar_url/file_name required' });
            }
            throw err;
        }
        if (handledByRuntimeBuilder) {
            return;
        }

        const avatarUrl = String(request.query?.avatar_url || '').trim();
        const fileName = String(request.query?.file_name || '').trim();
        const userName = String(request.query?.user_name || '').trim();
        const charName = String(request.query?.char_name || '').trim();
        const worldNamesRaw = request.query?.world_names;
        const loreMaxCharsBefore = request.query?.lore_max_chars_before;
        const loreMaxCharsAfter = request.query?.lore_max_chars_after;
        const loreMaxTokensBefore = request.query?.lore_max_tokens_before;
        const loreMaxTokensAfter = request.query?.lore_max_tokens_after;
        const loreMode = String(request.query?.lore_mode ?? request.query?.loreMode ?? '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        const cardPrompt = await loadCharacterPrompt(request.user.directories, avatarUrl);
        const card = await loadCharacterCard(request.user.directories, avatarUrl);

        // Accept world_names as CSV in query string
        const worldNames = Array.isArray(worldNamesRaw)
            ? worldNamesRaw.map(x => String(x).trim()).filter(Boolean)
            : String(worldNamesRaw || '').split(',').map(x => x.trim()).filter(Boolean);

        const headerMeta = chatData?.[0]?.chat_metadata && typeof chatData[0].chat_metadata === 'object'
            ? chatData[0].chat_metadata
            : {};
        const loreState = (headerMeta.lorebook_state && typeof headerMeta.lorebook_state === 'object')
            ? headerMeta.lorebook_state
            : {};
        const tick = chatData.length - 1;
        const probabilitySeed = String(chatData?.[chatData.length - 1]?.send_date ?? tick);

        const worldInfoBlocks =
            worldNames.length > 0
                ? buildLorebookBlocks(request.user.directories, worldNames, chatData.slice(1), {
                    maxCharsBefore: loreMaxCharsBefore,
                    maxCharsAfter: loreMaxCharsAfter,
                    maxTokensBefore: loreMaxTokensBefore,
                    maxTokensAfter: loreMaxTokensAfter,
                    recursiveScanning: true,
                    recursionMaxRounds: 2,
                    state: loreState,
                    tick,
                    probabilitySeed,
                    mode: loreMode || 'trigger',
                })
                : { beforeChar: '', afterChar: '' };
        const worldInfoPrompt =
            worldNames.length > 0
                ? ''
                : buildWorldInfoPrompt(request.user.directories, card);

        const built = buildRuntimeMessages({
            userName,
            charName,
            cardPrompt,
            worldInfoPrompt,
            worldInfoBlocks,
            chatData: chatData.slice(1),
        });

        if (worldNames.length > 0 && worldInfoBlocks?.debug?.updatedState) {
            const nextState = worldInfoBlocks.debug.updatedState;
            const currentJson = JSON.stringify(loreState);
            const nextJson = JSON.stringify(nextState);
            if (currentJson !== nextJson) {
                chatData[0].chat_metadata = { ...headerMeta, lorebook_state: nextState };
                await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            }
        }

        return response.send(built);
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'build_failed' });
    }
});

router.post('/stop', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = String(request.body?.avatar_url || '').trim();
        const fileName = String(request.body?.file_name || '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const key = buildRuntimeGenerationKey(request.user.profile.handle, avatarUrl, fileName);
        const entry = activeRuntimeGenerations.get(key);
        if (!entry) {
            return response.send({
                ok: true,
                stopped: false,
                active: false,
            });
        }

        const stopped = abortActiveRuntimeGeneration(entry, 'runtime_stop_requested');
        return response.send({
            ok: true,
            stopped,
            active: true,
            avatar_url: avatarUrl,
            file_name: fileName,
            started_at: entry.startedAt,
            stopped_at: entry.stoppedAt ?? null,
            mode: entry.mode || '',
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'stop_failed' });
    }
});

router.post('/generate', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const traceId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
        request._runtimeTraceId = traceId;
        const startedAt = Date.now();
        const avatarUrl = String(request.body?.avatar_url || '').trim();
        const fileName = String(request.body?.file_name || '').trim();
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const mode = String(request.body?.mode ?? '').trim().toLowerCase();
        const userMessage = firstNonBlank(request.body?.user_message, request.body?.userMessage, request.body?.mes);
        const messageRef = String(request.body?.message_ref ?? request.body?.messageRef ?? '').trim();
        const groupNames = Array.isArray(request.body?.group_names) ? request.body.group_names : [];
        const allowedFeatures = Array.isArray(request.body?.allowed_features) ? request.body.allowed_features : [];

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }
        logRuntimeGenerateStep(traceId, 'start', {
            avatarUrl,
            fileName,
            mode: mode || 'generate',
            hasUserMessage: Boolean(userMessage),
        });

        const runtimeKey = buildRuntimeGenerationKey(request.user.profile.handle, avatarUrl, fileName);
        const runtimeEntry = {
            key: runtimeKey,
            handle: request.user.profile.handle,
            avatarUrl,
            fileName,
            mode,
            startedAt: new Date().toISOString(),
            request,
            response,
            cancelled: false,
            stoppedAt: null,
        };
        registerActiveRuntimeGeneration(runtimeKey, runtimeEntry);
        const cleanup = () => clearActiveRuntimeGeneration(runtimeKey, runtimeEntry);
        request.on('close', cleanup);
        response.on('close', cleanup);
        response.on('finish', cleanup);
        response.on('close', () => logRuntimeGenerateStep(traceId, 'response_close', { elapsedMs: Date.now() - startedAt }));
        response.on('finish', () => logRuntimeGenerateStep(traceId, 'response_finish', { elapsedMs: Date.now() - startedAt }));

        const handle = request.user.profile.handle;
        logRuntimeGenerateStep(traceId, 'resolve_chat_path_begin');
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        logRuntimeGenerateStep(traceId, 'resolve_chat_path_done', { dirName, chatFilePath });
        const cardName = dirName;
        logRuntimeGenerateStep(traceId, 'read_chat_begin');
        let chatData = getChatData(chatFilePath);
        logRuntimeGenerateStep(traceId, 'read_chat_done', { messageCount: Array.isArray(chatData) ? chatData.length : 0 });
        chatData = ensureChatHeader(chatData, userName, charName);
        logRuntimeGenerateStep(traceId, 'ensure_header_done', {
            headerUser: String(chatData?.[0]?.user_name ?? ''),
            headerChar: String(chatData?.[0]?.character_name ?? ''),
        });
        let effectiveUserMessage = userMessage;
        if (userMessage && mode !== 'regenerate') {
            const regexContext = await readRuntimeRegexContext(request, {
                avatarUrl,
                userName,
                charName,
                headerUserName: String(chatData?.[0]?.user_name ?? '').trim(),
                headerCharName: String(chatData?.[0]?.character_name ?? '').trim(),
                cardName,
            });
            effectiveUserMessage = applyRuntimeStoredMessageRegex(userMessage, true, regexContext);
        }

        if (mode === 'regenerate') {
            logRuntimeGenerateStep(traceId, 'regenerate_mutation_begin');
            const removed = removeRuntimeRegenerateTarget(chatData, messageRef);
            logRuntimeGenerateStep(traceId, 'regenerate_target_removed', removed);
            logRuntimeGenerateStep(traceId, 'regenerate_save_begin', { messageCount: chatData.length });
            await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            logRuntimeGenerateStep(traceId, 'regenerate_save_done', { messageCount: chatData.length });
        } else if (userMessage) {
            logRuntimeGenerateStep(traceId, 'append_user_begin', { messageLength: String(userMessage).length });
            const upserted = upsertRuntimeUserMessage(chatData, userName, effectiveUserMessage, messageRef);
            logRuntimeGenerateStep(traceId, 'append_user_upserted', upserted);
            logRuntimeGenerateStep(traceId, 'append_user_save_begin', { messageCount: chatData.length });
            await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            logRuntimeGenerateStep(traceId, 'append_user_save_done', { messageCount: chatData.length });
        }

        if (runtimeEntry.cancelled || request.aborted || response.writableEnded) {
            logRuntimeGenerateStep(traceId, 'cancelled_before_build', {
                cancelled: runtimeEntry.cancelled,
                requestAborted: request.aborted,
                responseWritableEnded: response.writableEnded,
            });
            return;
        }

        logRuntimeGenerateStep(traceId, 'prepare_build_begin');
        const ctx = await prepareRuntimeBuildContext(request, {
            fromQuery: false,
            defaultLoreMode: 'trigger',
            chatData,
        });
        logRuntimeGenerateStep(traceId, 'prepare_build_done', {
            builtMessages: Array.isArray(ctx?.built?.messages) ? ctx.built.messages.length : 0,
            worldNames: Array.isArray(ctx?.worldNames) ? ctx.worldNames : [],
        });
        let messages = Array.isArray(ctx.built?.messages) ? [...ctx.built.messages] : [];
        if (!messages.length) {
            logRuntimeGenerateStep(traceId, 'build_failed_empty');
            return response.status(502).send({ error: 'generate_build_failed' });
        }
        if (mode === 'continue') {
            const continueBehavior = applyRuntimeContinueBehavior(messages, request.user.directories, {
                ...request.body,
                userName: ctx.userName,
                charName: ctx.charName,
                runtimePresetBundle: ctx.runtimePresetBundle,
            });
            logRuntimeGenerateStep(traceId, 'continue_hint_appended', {
                messageCount: messages.length,
                role: continueBehavior.nudge?.role ?? '',
                postfixApplied: continueBehavior.postfixApplied,
                nudgeSkipped: continueBehavior.nudgeSkipped,
            });
        }

        logRuntimeGenerateStep(traceId, 'build_generate_body_begin', { messageCount: messages.length });
        const generateBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: request.body?.stream !== false,
            allowedFeatures,
            mode,
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames,
            chatCompletionSource: String(request.body?.chat_completion_source || '').trim(),
            model: String(request.body?.model || '').trim(),
            reverseProxy: String(request.body?.reverse_proxy || '').trim(),
            proxyPassword: String(request.body?.proxy_password || '').trim(),
            customUrl: String(request.body?.custom_url || '').trim(),
            runtimePresetBundle: ctx.runtimePresetBundle,
        });
        logRuntimeGenerateStep(traceId, 'build_generate_body_done', {
            source: generateBody?.chat_completion_source,
            model: generateBody?.model,
            stream: generateBody?.stream !== false,
        });
        request._stDebugSource = '/api/runtime/chat/generate';
        request._stDebugCaptureTag = 'runtime';
        request.body = generateBody;
        logRuntimeGenerateStep(traceId, 'delegate_generate_begin');
        return await handleGenerateRequest(request, response);
    } catch (err) {
        console.error(err);
        logRuntimeGenerateStep(request?._runtimeTraceId || 'n/a', 'error', err?.message || String(err));
        if (request.aborted) {
            return;
        }
        if (!response.headersSent) {
            return response.status(500).send({ error: 'generate_failed' });
        }
        if (!response.writableEnded) {
            response.end();
        }
    }
});

/**
 * One-click golden diff:
 * 1) Turn on env `ST_DEBUG_GOLDEN_DIFF=true`
 * 2) Use ST web UI to generate once (server captures last /generate body)
 * 3) Call this endpoint to compare last captured body.messages with runtime-chat/build result messages
 */
router.get('/golden-diff', async (request, response) => {
    try {
        const goldenSource = String(request.query?.golden_source ?? request.query?.goldenSource ?? '').trim();
        const capturedGolden = readLatestGoldenGenerateBody(request.user.directories, goldenSource);
        if (!capturedGolden.ok) {
            return response.status(400).send({
                error: 'golden_not_available',
                detail: capturedGolden,
                hint: goldenSource
                    ? `Set ST_DEBUG_GOLDEN_DIFF=true and do one ${goldenSource} generation first.`
                    : 'Set ST_DEBUG_GOLDEN_DIFF=true and do one web UI generation first.',
            });
        }

        let handledByRuntimeBuilder = false;
        try {
            const ctx = await prepareRuntimeBuildContext(request, { fromQuery: true, defaultLoreMode: 'full' });
            const diff = diffMessages(capturedGolden.body?.messages, ctx.built?.messages);
            response.send({
                ok: true,
                golden: {
                    path: capturedGolden.path,
                    captured_at: capturedGolden.captured_at,
                    source: capturedGolden.source || '',
                    tag: capturedGolden.tag || '',
                    model: capturedGolden.body?.model,
                    chat_completion_source: capturedGolden.body?.chat_completion_source,
                },
                runtime: {
                    lore_debug: ctx.worldInfoBlocks?.debug ?? ctx.built?.debug ?? null,
                },
                diff,
            });
            handledByRuntimeBuilder = true;
        } catch (err) {
            if (err?.message === 'avatar_url/file_name required') {
                return response.status(400).send({ error: 'avatar_url/file_name required' });
            }
            throw err;
        }
        if (handledByRuntimeBuilder) {
            return;
        }

        const avatarUrl = String(request.query?.avatar_url || '').trim();
        const fileName = String(request.query?.file_name || '').trim();
        const userName = String(request.query?.user_name || '').trim();
        const charName = String(request.query?.char_name || '').trim();
        const worldNamesRaw = request.query?.world_names;
        const loreMaxCharsBefore = request.query?.lore_max_chars_before;
        const loreMaxCharsAfter = request.query?.lore_max_chars_after;
        const loreMaxTokensBefore = request.query?.lore_max_tokens_before;
        const loreMaxTokensAfter = request.query?.lore_max_tokens_after;
        const loreMode = String(request.query?.lore_mode ?? request.query?.loreMode ?? '').trim() || 'full';

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const golden = readLatestGoldenGenerateBody(request.user.directories);
        if (!golden.ok) {
            return response.status(400).send({
                error: 'golden_not_available',
                detail: golden,
                hint: 'Set ST_DEBUG_GOLDEN_DIFF=true and do one web UI generation first.',
            });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        const cardPrompt = await loadCharacterPrompt(request.user.directories, avatarUrl);
        const card = await loadCharacterCard(request.user.directories, avatarUrl);

        const worldNames = Array.isArray(worldNamesRaw)
            ? worldNamesRaw.map(x => String(x).trim()).filter(Boolean)
            : String(worldNamesRaw || '').split(',').map(x => x.trim()).filter(Boolean);

        const headerMeta = chatData?.[0]?.chat_metadata && typeof chatData[0].chat_metadata === 'object'
            ? chatData[0].chat_metadata
            : {};
        const loreState = (headerMeta.lorebook_state && typeof headerMeta.lorebook_state === 'object')
            ? headerMeta.lorebook_state
            : {};
        const tick = chatData.length - 1;
        const probabilitySeed = String(chatData?.[chatData.length - 1]?.send_date ?? tick);

        const worldInfoBlocks =
            worldNames.length > 0
                ? buildLorebookBlocks(request.user.directories, worldNames, chatData.slice(1), {
                    maxCharsBefore: loreMaxCharsBefore,
                    maxCharsAfter: loreMaxCharsAfter,
                    maxTokensBefore: loreMaxTokensBefore,
                    maxTokensAfter: loreMaxTokensAfter,
                    recursiveScanning: true,
                    recursionMaxRounds: 2,
                    state: loreState,
                    tick,
                    probabilitySeed,
                    mode: loreMode,
                })
                : { beforeChar: '', afterChar: '' };
        const worldInfoPrompt =
            worldNames.length > 0
                ? ''
                : buildWorldInfoPrompt(request.user.directories, card);

        const built = buildRuntimeMessages({
            userName,
            charName,
            cardPrompt,
            worldInfoPrompt,
            worldInfoBlocks,
            chatData: chatData.slice(1),
        });

        if (worldNames.length > 0 && worldInfoBlocks?.debug?.updatedState) {
            const nextState = worldInfoBlocks.debug.updatedState;
            const currentJson = JSON.stringify(loreState);
            const nextJson = JSON.stringify(nextState);
            if (currentJson !== nextJson) {
                chatData[0].chat_metadata = { ...headerMeta, lorebook_state: nextState };
                await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            }
        }

        const diff = diffMessages(golden.body?.messages, built?.messages);

        return response.send({
            ok: true,
            golden: {
                path: golden.path,
                captured_at: golden.captured_at,
                model: golden.body?.model,
                chat_completion_source: golden.body?.chat_completion_source,
            },
            runtime: {
                lore_debug: built?.worldInfoBlocks?.debug ?? built?.debug ?? null,
            },
            diff,
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'golden_diff_failed' });
    }
});

router.post('/header', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const { chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        return response.send({ ok: true, header: chatData[0] || null });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'header_failed' });
    }
});

// Debug-friendly variant: GET does not require CSRF token.
router.get('/header', async (request, response) => {
    try {
        const avatarUrl = String(request.query?.avatar_url || '').trim();
        const fileName = String(request.query?.file_name || '').trim();
        const userName = String(request.query?.user_name || '').trim();
        const charName = String(request.query?.char_name || '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const { chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        return response.send({ ok: true, header: chatData[0] || null });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'header_failed' });
    }
});

router.post('/pop-last-assistant', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;

        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        // Remove the last assistant message (best-effort). Keep header at index 0.
        for (let i = chatData.length - 1; i >= 1; i--) {
            const m = chatData[i];
            if (m && m.is_user === false) {
                chatData.splice(i, 1);
                break;
            }
        }

        await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
        return response.send({ ok: true });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'pop_failed' });
    }
});

router.post('/replace-last-assistant', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const messageRef = String(request.body?.message_ref ?? '').trim();
        const outputRegexApplied = request.body?.output_regex_applied === true;
        const hasMes = Object.prototype.hasOwnProperty.call(request.body ?? {}, 'mes');
        const mes = outputRegexApplied
            ? String(request.body?.mes ?? '')
            : String(request.body?.mes ?? '').trim();
        const allowEmptyCanonicalAssistant = outputRegexApplied && hasMes;

        if (!avatarUrl || !fileName || (!mes && !allowEmptyCanonicalAssistant)) {
            return response.status(400).send({ error: 'avatar_url/file_name/mes required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;

        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);
        const headerUserName = String(chatData?.[0]?.user_name ?? '').trim();
        const headerCharName = String(chatData?.[0]?.character_name ?? '').trim();
        const regexContext = await readRuntimeRegexContext(request, {
            avatarUrl,
            userName,
            charName,
            headerUserName,
            headerCharName,
            cardName,
        });
        const effectiveMes = outputRegexApplied
            ? mes
            : applyRuntimeStoredMessageRegex(mes, false, regexContext);

        const writeResult = replaceRuntimeAssistantMessage(chatData, charName, effectiveMes, messageRef);
        if (!writeResult.matched) {
            return response.status(409).send({ error: 'assistant_message_ref_not_found', message_ref: messageRef });
        }
        if (writeResult.changed) {
            updateRuntimeDynamicSheetsFromMessage(chatData, chatData[writeResult.index], { userName, charName });
        }

        await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
        return response.send({
            ok: true,
            message_ref: messageRef,
            mes: effectiveMes,
            regex_applied: !outputRegexApplied,
            inserted: writeResult.inserted,
            deduplicated: writeResult.deduplicated,
            changed: writeResult.changed,
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'replace_failed' });
    }
});

router.post('/tail', validateAvatarUrlMiddleware, async (request, response) => {
    try {
        const avatarUrl = request.body?.avatar_url;
        const fileName = request.body?.file_name;
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const limitRaw = request.body?.limit;
        const limit = Math.max(1, Math.min(200, Number.isFinite(Number(limitRaw)) ? Number(limitRaw) : 50));

        if (!avatarUrl || !fileName) {
            return response.status(400).send({ error: 'avatar_url/file_name required' });
        }

        const { chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        // Expose only the last N messages (excluding header), but include `extra` so caller can verify message_ref.
        const msgs = chatData.slice(1);
        const tail = msgs.slice(Math.max(0, msgs.length - limit));

        return response.send({
            ok: true,
            count: tail.length,
            messages: tail,
        });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'tail_failed' });
    }
});

