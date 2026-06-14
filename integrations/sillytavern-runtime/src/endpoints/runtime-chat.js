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

/**
 * Runtime Chat API: use ST chat jsonl as the source of truth for history and prompt state.
 * Spring stays on business concerns, while ST owns prompt assembly and final generation shape.
 */
export const router = express.Router();
const activeRuntimeGenerations = new Map();
const LORE_CHARS_PER_TOKEN = 4;
const ST_DEBUG_RUNTIME_GENERATE = String(process.env.ST_DEBUG_RUNTIME_GENERATE || '').trim().toLowerCase() === 'true';
const DEFAULT_CONTINUE_NUDGE_PROMPT = '[Continue your last message without repeating its original content.]';
const DEFAULT_CONTINUE_NUDGE_ROLE = 'user';

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

function expandRuntimeMacros(text, { userName = '', charName = '' } = {}) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/\{\{\s*user\s*\}\}/gi, userName || 'user')
        .replace(/\{\{\s*char\s*\}\}/gi, charName || 'assistant');
}

function buildCharacterPromptSections(card, { userName = '', charName = '' } = {}) {
    if (!card || typeof card !== 'object') {
        return [];
    }
    const description = String(card?.description || '').trim();
    const personality = String(card?.personality || '').trim();
    const scenario = String(card?.scenario || '').trim();
    const systemPrompt = String(card?.system_prompt || '').trim();
    const example = String(card?.mes_example || '').trim();

    const sections = [];
    const summaryParts = [];
    if (description) summaryParts.push(description);
    if (personality) summaryParts.push(`Personality: ${personality}`);
    if (summaryParts.length) {
        sections.push(summaryParts.join('\n'));
    }
    if (scenario) {
        sections.push(scenario);
    }
    if (systemPrompt) {
        sections.push(systemPrompt);
    }
    if (example) {
        sections.push(`Example dialogue:\n${example}`);
    }
    return sections
        .map(section => expandRuntimeMacros(section, { userName, charName }))
        .filter(Boolean);
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

function expandWorldInfoBlocks(blocks, { userName = '', charName = '' } = {}) {
    if (!blocks || typeof blocks !== 'object') {
        return blocks;
    }
    return {
        ...blocks,
        beforeChar: expandRuntimeMacros(blocks.beforeChar ?? '', { userName, charName }),
        afterChar: expandRuntimeMacros(blocks.afterChar ?? '', { userName, charName }),
    };
}

function emptyWorldInfoBlocks(state = {}, extraDebug = {}) {
    return {
        beforeChar: '',
        afterChar: '',
        debug: {
            activatedCount: 0,
            totalEntries: 0,
            triggeredKeys: [],
            updatedState: state && typeof state === 'object' ? { ...state } : {},
            ...extraDebug,
        },
    };
}

function normalizeRuntimeChatMessages(chatData, { userName = '', charName = '' } = {}) {
    if (!Array.isArray(chatData)) {
        return [];
    }
    return chatData.map(message => {
        if (!message || typeof message !== 'object') {
            return message;
        }
        if (message.is_user === true) {
            return message;
        }
        const nextMes =
            typeof message.mes === 'string'
                ? expandRuntimeMacros(message.mes, { userName, charName })
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

function readRuntimeSettings(directories) {
    try {
        const settingsPath = path.join(directories.root, SETTINGS_FILE);
        if (!fs.existsSync(settingsPath)) {
            return {};
        }
        const raw = fs.readFileSync(settingsPath, 'utf8');
        return tryParse(raw) || {};
    } catch {
        return {};
    }
}

function normalizeContinueNudgeRole(role) {
    const value = String(role || '').trim().toLowerCase();
    if (['system', 'user', 'assistant', 'developer'].includes(value)) {
        return value;
    }
    return DEFAULT_CONTINUE_NUDGE_ROLE;
}

function readContinueNudgeMessage(directories, options = {}) {
    const root = readRuntimeSettings(directories);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const envRole = String(process.env.ST_RUNTIME_CONTINUE_NUDGE_ROLE || '').trim();
    const prompt = firstNonBlank(
        options.continueNudgePrompt,
        options.continue_nudge_prompt,
        oaiSettings?.continue_nudge_prompt,
        root?.continue_nudge_prompt,
        DEFAULT_CONTINUE_NUDGE_PROMPT,
    );
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

function appendContinueNudgeMessage(messages, directories, options = {}) {
    if (!Array.isArray(messages)) {
        return { role: DEFAULT_CONTINUE_NUDGE_ROLE, content: '' };
    }
    const message = readContinueNudgeMessage(directories, options);
    if (!message.content) {
        return message;
    }
    messages.push(message);
    return message;
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

function readRuntimeOaiSettings(directories) {
    const root = readRuntimeSettings(directories);
    const oaiSettings = root?.oai_settings && typeof root.oai_settings === 'object' ? root.oai_settings : root;
    const source = firstNonBlank(oaiSettings?.chat_completion_source, 'openai');
    const modelField = modelFieldForChatSource(source);
    return {
        source,
        model: firstNonBlank(oaiSettings?.[modelField], oaiSettings?.openai_model),
        reverseProxy: firstNonBlank(oaiSettings?.reverse_proxy),
        proxyPassword: firstNonBlank(oaiSettings?.proxy_password),
        reasoningEffort: firstNonBlank(oaiSettings?.reasoning_effort),
        includeReasoning: toBoolean(oaiSettings?.show_thoughts, true),
        customUrl: firstNonBlank(oaiSettings?.custom_url),
        temperature: toFiniteNumber(oaiSettings?.temp_openai ?? root?.temp_openai),
        maxTokens: toFiniteNumber(oaiSettings?.openai_max_tokens ?? root?.openai_max_tokens),
        topP: toFiniteNumber(oaiSettings?.top_p_openai ?? root?.top_p_openai),
        topK: toFiniteNumber(oaiSettings?.top_k_openai ?? root?.top_k_openai),
        minP: toFiniteNumber(oaiSettings?.min_p_openai ?? root?.min_p_openai),
        topA: toFiniteNumber(oaiSettings?.top_a_openai ?? root?.top_a_openai),
        frequencyPenalty: toFiniteNumber(oaiSettings?.freq_pen_openai ?? root?.freq_pen_openai),
        presencePenalty: toFiniteNumber(oaiSettings?.pres_pen_openai ?? root?.pres_pen_openai),
        repetitionPenalty: toFiniteNumber(oaiSettings?.repetition_penalty_openai ?? root?.repetition_penalty_openai),
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
    };
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

async function createLoreTokenEstimator(directories) {
    const runtime = readRuntimeOaiSettings(directories);
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
    const runtime = readRuntimeOaiSettings(directories);
    const source = firstNonBlank(options.chatCompletionSource, runtime.source);
    const model = firstNonBlank(options.model, runtime.model);
    const reverseProxy = firstNonBlank(options.reverseProxy, runtime.reverseProxy);
    const proxyPassword = firstNonBlank(options.proxyPassword, runtime.proxyPassword);
    const customUrl = firstNonBlank(options.customUrl, runtime.customUrl);
    const body = {
        stream: options.stream !== false,
        chat_completion_source: source,
        model,
        reverse_proxy: reverseProxy,
        proxy_password: proxyPassword,
        messages: Array.isArray(messages) ? messages : [],
        enable_web_search: false,
        request_images: false,
        request_image_resolution: '',
        request_image_aspect_ratio: '',
        custom_prompt_post_processing: 'merge_tools',
        use_fallback: false,
        provider: [],
        quantizations: [],
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

    if (!avatarUrl || !fileName) {
        throw new Error('avatar_url/file_name required');
    }

    const handle = request.user.profile.handle;
    const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
    const cardName = dirName;
    let chatData = getChatData(chatFilePath);
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
    const settings = readRuntimeSettings(request.user.directories);
    const resolvedUserName = firstNonBlank(userName, headerUserName, settings?.username);
    const resolvedCharName = firstNonBlank(charName, String(card?.name ?? '').trim(), headerCharName, cardName);
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
    const loreTokenEstimator = await createLoreTokenEstimator(request.user.directories);
    const runtimeChatData = normalizeRuntimeChatMessages(chatData.slice(1), {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const cardPromptSections = buildCharacterPromptSections(card, {
        userName: resolvedUserName,
        charName: resolvedCharName,
    });
    const headerMeta = chatData?.[0]?.chat_metadata && typeof chatData[0].chat_metadata === 'object'
        ? chatData[0].chat_metadata
        : {};
    const loreState = headerMeta.lorebook_state && typeof headerMeta.lorebook_state === 'object'
        ? headerMeta.lorebook_state
        : {};
    const tick = chatData.length - 1;
    const probabilitySeed = String(chatData?.[chatData.length - 1]?.send_date ?? tick);
    let worldInfoBlocks = emptyWorldInfoBlocks(loreState, { mode: loreMode });
    if (hasLorebookSources) {
        try {
            worldInfoBlocks = expandWorldInfoBlocks(
                buildLorebookBlocks(request.user.directories, worldNames, runtimeChatData, {
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
                { userName: resolvedUserName, charName: resolvedCharName },
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
        cardPromptSections,
        worldInfoPrompt,
        worldInfoBlocks,
        chatData: runtimeChatData,
        tailSystemPrompt,
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
            },
            user: request.user,
        };
        const defaultLoreMode = String(goldenCase?.lore_mode ?? '').trim() || 'full';
        const ctx = await prepareRuntimeBuildContext(fakeRequest, { fromQuery: false, defaultLoreMode });
        let messages = Array.isArray(ctx.built?.messages) ? [...ctx.built.messages] : [];
        if (String(goldenCase?.mode ?? '').trim().toLowerCase() === 'continue') {
            appendContinueNudgeMessage(messages, request.user.directories, goldenCase);
        }
        const runtimeBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: goldenCase?.stream !== false,
            allowedFeatures: Array.isArray(goldenCase?.allowed_features) ? goldenCase.allowed_features : [],
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames: Array.isArray(goldenCase?.group_names) ? goldenCase.group_names : [],
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
        const mes = String(request.body?.mes ?? '').trim();
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const isUser = request.body?.is_user !== undefined ? Boolean(request.body.is_user) : true;
        const messageRef = String(request.body?.message_ref ?? '').trim();

        if (!avatarUrl || !fileName || !mes) {
            return response.status(400).send({ error: 'avatar_url/file_name/mes required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;

        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        const msg = {
            name: isUser ? (userName || 'User') : (charName || 'Assistant'),
            is_user: isUser,
            send_date: new Date().toISOString(),
            mes,
            extra: messageRef ? { message_ref: messageRef } : {},
        };
        chatData.push(msg);

        await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
        return response.send({ ok: true, message_ref: messageRef });
    } catch (err) {
        console.error(err);
        return response.status(500).send({ error: 'append_failed' });
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
            appendContinueNudgeMessage(messages, request.user.directories, request.body);
        }

        const runtimeBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: request.body?.stream !== false,
            allowedFeatures,
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames,
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

        if (mode === 'regenerate') {
            logRuntimeGenerateStep(traceId, 'regenerate_mutation_begin');
            for (let i = chatData.length - 1; i >= 1; i--) {
                const message = chatData[i];
                if (message && message.is_user === false) {
                    chatData.splice(i, 1);
                    break;
                }
            }
            logRuntimeGenerateStep(traceId, 'regenerate_save_begin', { messageCount: chatData.length });
            await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
            logRuntimeGenerateStep(traceId, 'regenerate_save_done', { messageCount: chatData.length });
        } else if (userMessage) {
            logRuntimeGenerateStep(traceId, 'append_user_begin', { messageLength: String(userMessage).length });
            chatData.push({
                name: userName || 'User',
                is_user: true,
                send_date: new Date().toISOString(),
                mes: userMessage,
                extra: messageRef ? { message_ref: messageRef } : {},
            });
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
        const ctx = await prepareRuntimeBuildContext(request, { fromQuery: false, defaultLoreMode: 'trigger' });
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
            const nudge = appendContinueNudgeMessage(messages, request.user.directories, request.body);
            logRuntimeGenerateStep(traceId, 'continue_hint_appended', {
                messageCount: messages.length,
                role: nudge.role,
            });
        }

        logRuntimeGenerateStep(traceId, 'build_generate_body_begin', { messageCount: messages.length });
        const generateBody = buildRuntimeGenerateBody(request.user.directories, messages, {
            stream: request.body?.stream !== false,
            allowedFeatures,
            userName: ctx.userName,
            charName: ctx.charName,
            groupNames,
            chatCompletionSource: String(request.body?.chat_completion_source || '').trim(),
            model: String(request.body?.model || '').trim(),
            reverseProxy: String(request.body?.reverse_proxy || '').trim(),
            proxyPassword: String(request.body?.proxy_password || '').trim(),
            customUrl: String(request.body?.custom_url || '').trim(),
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
        const mes = String(request.body?.mes ?? '').trim();
        const userName = String(request.body?.user_name ?? '').trim();
        const charName = String(request.body?.char_name ?? '').trim();
        const messageRef = String(request.body?.message_ref ?? '').trim();

        if (!avatarUrl || !fileName || !mes) {
            return response.status(400).send({ error: 'avatar_url/file_name/mes required' });
        }

        const handle = request.user.profile.handle;
        const { dirName, chatFilePath } = resolveChatFilePath(request.user.directories, avatarUrl, fileName);
        const cardName = dirName;

        let chatData = getChatData(chatFilePath);
        chatData = ensureChatHeader(chatData, userName, charName);

        // Remove last assistant
        for (let i = chatData.length - 1; i >= 1; i--) {
            const m = chatData[i];
            if (m && m.is_user === false) {
                chatData.splice(i, 1);
                break;
            }
        }

        chatData.push({
            name: charName || 'Assistant',
            is_user: false,
            send_date: new Date().toISOString(),
            mes,
            extra: messageRef ? { message_ref: messageRef } : {},
        });

        await trySaveChat(chatData, chatFilePath, true, handle, cardName, request.user.directories.backups);
        return response.send({ ok: true });
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

