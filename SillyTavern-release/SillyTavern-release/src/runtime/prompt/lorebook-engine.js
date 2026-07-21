import { readWorldInfoFile } from '../../endpoints/worldinfo.js';

function toBool(v, defaultValue = false) {
    if (v === undefined || v === null) return defaultValue;
    if (typeof v === 'boolean') return v;
    const s = String(v).trim().toLowerCase();
    if (!s) return defaultValue;
    return s === 'true' || s === '1' || s === 'yes';
}

function toInt(v, defaultValue = 0) {
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : defaultValue;
}

function firstInt(defaultValue, ...values) {
    for (const value of values) {
        const n = Number(value);
        if (Number.isFinite(n)) {
            return Math.trunc(n);
        }
    }
    return defaultValue;
}

const WORLD_INFO_POSITION = Object.freeze({
    BEFORE: 0,
    AFTER: 1,
    AT_DEPTH: 4,
});
const DEFAULT_INJECTION_DEPTH = 4;
const MAX_INJECTION_DEPTH = 10_000;
const EXTENSION_PROMPT_ROLE = Object.freeze({
    SYSTEM: 0,
    USER: 1,
    ASSISTANT: 2,
});

function asKeyList(v) {
    if (!v) return [];
    if (Array.isArray(v)) return v.map(x => String(x).trim()).filter(Boolean);
    return String(v).split(',').map(x => x.trim()).filter(Boolean);
}

function compileRegexSafe(pattern, caseSensitive = false) {
    const src = String(pattern || '').trim();
    if (!src) return null;
    const flags = caseSensitive ? '' : 'i';
    try {
        return new RegExp(src, flags);
    } catch {
        // fallback to literal contains via escaped regex
        try {
            const escaped = src.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            return new RegExp(escaped, flags);
        } catch {
            return null;
        }
    }
}

function normalizeWorldInfoPosition(posRaw) {
    if (typeof posRaw === 'string') {
        const raw = posRaw.trim().toLowerCase();
        if (!raw) return 'before_char';
        if (raw === 'before' || raw === 'before_char' || raw === String(WORLD_INFO_POSITION.BEFORE)) {
            return 'before_char';
        }
        if (raw === 'after' || raw === 'after_char' || raw === String(WORLD_INFO_POSITION.AFTER)) {
            return 'after_char';
        }
        if (raw === 'atdepth' || raw === 'at_depth' || raw === 'depth' || raw === 'in_chat' || raw === String(WORLD_INFO_POSITION.AT_DEPTH)) {
            return 'at_depth';
        }
        const n = Number(raw);
        if (Number.isFinite(n)) {
            return normalizeWorldInfoPosition(n);
        }
        return 'before_char';
    }

    if (posRaw !== undefined && posRaw !== null) {
        const position = toInt(posRaw, WORLD_INFO_POSITION.BEFORE);
        if (position === WORLD_INFO_POSITION.BEFORE) {
            return 'before_char';
        }
        if (position === WORLD_INFO_POSITION.AT_DEPTH) {
            return 'at_depth';
        }
        return 'after_char';
    }

    return 'before_char';
}

function normalizeInjectionDepth(...values) {
    return Math.max(
        0,
        Math.min(
            MAX_INJECTION_DEPTH,
            firstInt(DEFAULT_INJECTION_DEPTH, ...values),
        ),
    );
}

function normalizeInjectionRole(...values) {
    for (const value of values) {
        if (value === undefined || value === null || value === '') {
            continue;
        }
        if (typeof value === 'number') {
            if (value === EXTENSION_PROMPT_ROLE.USER || value === EXTENSION_PROMPT_ROLE.ASSISTANT) {
                return value;
            }
            return EXTENSION_PROMPT_ROLE.SYSTEM;
        }
        const raw = String(value).trim().toLowerCase();
        if (raw === 'user' || raw === String(EXTENSION_PROMPT_ROLE.USER)) {
            return EXTENSION_PROMPT_ROLE.USER;
        }
        if (raw === 'assistant' || raw === 'char' || raw === String(EXTENSION_PROMPT_ROLE.ASSISTANT)) {
            return EXTENSION_PROMPT_ROLE.ASSISTANT;
        }
        if (raw === 'system' || raw === 'narrator' || raw === String(EXTENSION_PROMPT_ROLE.SYSTEM)) {
            return EXTENSION_PROMPT_ROLE.SYSTEM;
        }
    }
    return EXTENSION_PROMPT_ROLE.SYSTEM;
}

function normalizeWorldInfoEntry(e, worldName, defaultScanDepth = 4, entryIndex = 0) {
    // Legacy worldinfo entry format (worldinfo.js)
    // keys: key (regex string), secondary: keysecondary
    const keys = asKeyList(e?.keys ?? e?.key);
    const secondary = asKeyList(e?.secondary_keys ?? e?.secondaryKeys ?? e?.keysecondary);
    const content = String(e?.content ?? '').trim();
    if (!content) return null;

    const enabled = e?.enabled !== undefined ? toBool(e.enabled, true) : !toBool(e?.disable, false);
    const constant = toBool(e?.constant ?? e?.constantInjection, false);
    const selective = toBool(e?.selective, false);
    const uid = toInt(e?.uid ?? e?.id, NaN);
    const priority = firstInt(0, e?.priority, e?.extensions?.priority);

    // ST world_info_position: 0 before, 1 after, 4 at-depth. Other positions are
    // kept on the old after_char fallback until the server runtime supports them.
    const posRaw = e?.extensions?.position ?? e?.position;
    const position = normalizeWorldInfoPosition(posRaw);
    const injectionDepth = normalizeInjectionDepth(e?.extensions?.depth, position === 'at_depth' ? e?.depth : undefined);
    const injectionRole = normalizeInjectionRole(e?.extensions?.role, e?.role);

    const insertionOrder = firstInt(
        0,
        e?.insertion_order,
        e?.insertionOrder,
        e?.order,
        e?.extensions?.insertion_order,
        e?.extensions?.insertionOrder,
    );

    // legacy exports often use `depth` for scan depth
    const scanDepth = Math.max(
        1,
        Math.min(
            64,
            toInt(
                e?.extensions?.scan_depth ??
                e?.scanDepth ??
                e?.scan_depth ??
                (position === 'at_depth' ? undefined : e?.depth),
                defaultScanDepth,
            ),
        ),
    );
    const matchWholeWords = toBool(
        e?.extensions?.match_whole_words ??
        e?.extensions?.matchWholeWords ??
        e?.matchWholeWords ??
        e?.match_whole_words,
        false,
    );
    const caseSensitive = toBool(e?.extensions?.case_sensitive ?? e?.caseSensitive ?? e?.case_sensitive, false);

    const cooldown = Math.max(0, toInt(e?.cooldown ?? e?.extensions?.cooldown, 0));
    const delay = Math.max(0, toInt(e?.delay ?? e?.extensions?.delay, 0));
    const useProbability = toBool(e?.useProbability ?? e?.extensions?.useProbability, true);
    const probability = Math.max(0, Math.min(100, toInt(e?.probability ?? e?.extensions?.probability, 100)));

    const excludeRecursion = toBool(e?.excludeRecursion ?? e?.extensions?.excludeRecursion, false);
    const preventRecursion = toBool(e?.preventRecursion ?? e?.extensions?.preventRecursion, false);
    const delayUntilRecursion = toBool(e?.delayUntilRecursion ?? e?.extensions?.delayUntilRecursion, false);

    const keyRegexes = keys.map(k => compileRegexSafe(k, caseSensitive)).filter(Boolean);
    const secondaryRegexes = secondary.map(k => compileRegexSafe(k, caseSensitive)).filter(Boolean);

    return {
        worldName: String(worldName || '').trim(),
        uid: Number.isFinite(uid) ? uid : null,
        enabled,
        constant,
        selective,
        position,
        depth: injectionDepth,
        role: injectionRole,
        entryIndex,
        insertionOrder,
        priority,
        comment: String(e?.comment ?? e?.name ?? '').trim(),
        content,
        scanDepth,
        matchWholeWords,
        caseSensitive,
        cooldown,
        delay,
        useProbability,
        probability,
        excludeRecursion,
        preventRecursion,
        delayUntilRecursion,
        keyRegexes,
        secondaryRegexes,
        rawKeys: keys,
        rawSecondaryKeys: secondary,
    };
}

function normalizeEmbeddedBooks(raw) {
    const list = Array.isArray(raw) ? raw : (raw ? [raw] : []);
    return list
        .filter(book => book && typeof book === 'object')
        .map((book, index) => {
            const displayName = String(book?.name ?? book?.comment ?? `embedded_character_book_${index + 1}`).trim();
            return {
                displayName: displayName || `embedded_character_book_${index + 1}`,
                stateName: `embedded:${displayName || index + 1}`,
                entries: getBookEntries(book),
            };
        })
        .filter(book => book.entries.length > 0);
}

function normalizeSourceLabelKey(label) {
    return String(label ?? '').trim().toLowerCase();
}

function addSourceLabel(labels, seen, label) {
    const value = String(label ?? '').trim();
    if (!value) {
        return;
    }
    const key = normalizeSourceLabelKey(value);
    if (seen.has(key)) {
        return;
    }
    seen.add(key);
    labels.push(value);
}

function loreEntryIdentity(e) {
    return JSON.stringify([
        e.constant,
        e.position,
        e.depth,
        e.role,
        e.comment,
        e.content,
        e.rawKeys,
        e.rawSecondaryKeys,
    ]);
}

function dedupeLoreEntries(entries) {
    const seen = new Set();
    const result = [];
    let duplicateCount = 0;
    for (const entry of entries) {
        const key = loreEntryIdentity(entry);
        if (seen.has(key)) {
            duplicateCount += 1;
            continue;
        }
        seen.add(key);
        result.push(entry);
    }
    return { entries: result, duplicateCount };
}

function getBookEntries(book) {
    const entries = book?.entries ?? book;
    if (Array.isArray(entries)) {
        return entries;
    }
    if (entries && typeof entries === 'object') {
        return Object.values(entries);
    }
    return [];
}

function buildScanText(chatData, scanDepth) {
    if (!Array.isArray(chatData) || chatData.length === 0) return '';
    const n = Math.max(1, Math.min(64, scanDepth));
    const slice = chatData.slice(Math.max(0, chatData.length - n));
    return slice
        .map(m => (m && typeof m.mes === 'string' ? m.mes : ''))
        .filter(Boolean)
        .join('\n');
}

function wordBoundaryWrap(re) {
    // best-effort for "match whole words" in latin text; does not attempt CJK boundaries.
    try {
        return new RegExp(`\\b(?:${re.source})\\b`, re.flags);
    } catch {
        return re;
    }
}

function matchesAny(regexes, text, matchWholeWords) {
    if (!regexes.length) return false;
    for (const re0 of regexes) {
        const re = matchWholeWords ? wordBoundaryWrap(re0) : re0;
        if (re && re.test(text)) return true;
    }
    return false;
}

function stableHash32(s) {
    // FNV-1a 32-bit
    let h = 0x811c9dc5;
    const str = String(s ?? '');
    for (let i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 0x01000193);
    }
    return h >>> 0;
}

function xorshift32(seed) {
    let x = seed >>> 0;
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    return x >>> 0;
}

function probabilityPass(percent, seedStr) {
    const p = Math.max(0, Math.min(100, Number(percent)));
    if (p >= 100) return true;
    if (p <= 0) return false;
    const h = stableHash32(seedStr);
    const r = xorshift32(h) / 0xffffffff;
    return r < (p / 100);
}

function estimateTokensFallback(text) {
    // ST's server fallback for token counting uses chars/token ~ 4.
    // We keep it consistent here unless caller provides a better estimator.
    const s = String(text ?? '');
    return Math.ceil(s.length / 4);
}

/**
 * Trigger-based lorebook engine (StepB+).
 * - constant entries always included
 * - non-constant entries included if keys match recent chat (scanDepth)
 * - selective secondary_keys, case sensitivity, and whole-word matching are honored
 * - position before_char/after_char supported
 * - probability/cooldown/recursion support (best-effort; state is stored in chat header metadata by runtime-chat endpoint)
 * - token budget trimming via estimator (defaults to chars/4 fallback)
 * - opts.embeddedBooks can pass character-card embedded character_book objects through the same trigger path
 *
 * @param {import('../users.js').UserDirectoryList} directories
 * @param {string[]} worldNames ST worldinfo filenames
 * @param {Array<{is_user?: boolean, mes?: string}>} chatData messages excluding header
 * @param {object} [opts]
 * @param {number} [opts.maxCharsBefore] max chars for before_char block
 * @param {number} [opts.maxCharsAfter] max chars for after_char block
 * @param {number} [opts.maxTokensBefore] max tokens for before_char block
 * @param {number} [opts.maxTokensAfter] max tokens for after_char block
 * @param {number} [opts.defaultScanDepth] default scan depth if missing in entry
 * @param {boolean} [opts.recursiveScanning] enable recursion scanning
 * @param {number} [opts.recursionMaxRounds] max recursion rounds (guardrail)
 * @param {Record<string, number>} [opts.state] lorebook state map (entryKey -> lastActivatedTick)
 * @param {number} [opts.tick] current activation tick (e.g. chat message count)
 * @param {(text:string)=>number} [opts.tokenEstimator] token estimator
 * @param {string} [opts.probabilitySeed] stable per-turn seed string
 * @param {object|object[]} [opts.embeddedBooks] embedded character_book object(s)
 * @returns {{beforeChar: string, afterChar: string, depthInjections: Array<{depth:number, role:number|string, content:string, source?:string}>, debug: {activatedCount:number, totalEntries:number, triggeredKeys:string[], updatedState: Record<string, number>}}}
 */
export function buildLorebookBlocks(directories, worldNames = [], chatData = [], opts = {}) {
    const names = Array.isArray(worldNames) ? worldNames.map(x => String(x).trim()).filter(Boolean) : [];
    const embeddedBooks = normalizeEmbeddedBooks(opts.embeddedBooks ?? opts.embeddedBook);
    if ((!directories || !names.length) && embeddedBooks.length === 0) {
        return { beforeChar: '', afterChar: '', depthInjections: [], debug: { activatedCount: 0, totalEntries: 0, triggeredKeys: [], updatedState: opts?.state ?? {} } };
    }

    const modeRaw = String(opts.mode ?? opts.loreMode ?? 'trigger').trim().toLowerCase();
    const mode = (modeRaw === 'full' || modeRaw === 'all') ? 'full' : 'trigger';

    const maxCharsBefore = Math.max(500, Math.min(50_000, toInt(opts.maxCharsBefore, 8_000)));
    const maxCharsAfter = Math.max(500, Math.min(50_000, toInt(opts.maxCharsAfter, 8_000)));
    const maxTokensBefore = opts.maxTokensBefore !== undefined && opts.maxTokensBefore !== null
        ? Math.max(0, toInt(opts.maxTokensBefore, 0))
        : null;
    const maxTokensAfter = opts.maxTokensAfter !== undefined && opts.maxTokensAfter !== null
        ? Math.max(0, toInt(opts.maxTokensAfter, 0))
        : null;
    const defaultScanDepth = Math.max(1, Math.min(64, toInt(opts.defaultScanDepth, 6)));
    const recursiveScanning = toBool(opts.recursiveScanning, false);
    const recursionMaxRounds = Math.max(0, Math.min(8, toInt(opts.recursionMaxRounds, 2)));
    const state = (opts.state && typeof opts.state === 'object') ? { ...opts.state } : {};
    const tick = Math.max(0, toInt(opts.tick, Array.isArray(chatData) ? chatData.length : 0));
    const probabilitySeed = String(opts.probabilitySeed ?? tick);
    const tokenEstimator = typeof opts.tokenEstimator === 'function' ? opts.tokenEstimator : estimateTokensFallback;

    let all = [];
    const sourceLabels = [];
    const sourceLabelSeen = new Set();
    if (directories) {
        for (const wn of names) {
            const file = readWorldInfoFile(directories, wn, true);
            const entriesObj = file && file.entries ? file.entries : {};
            const entries = Object.values(entriesObj);
            if (entries.length > 0) {
                addSourceLabel(sourceLabels, sourceLabelSeen, wn);
            }
            for (let i = 0; i < entries.length; i++) {
                const ne = normalizeWorldInfoEntry(entries[i], wn, defaultScanDepth, i);
                if (ne) all.push(ne);
            }
        }
    }
    for (const book of embeddedBooks) {
        addSourceLabel(sourceLabels, sourceLabelSeen, book.displayName);
        for (let i = 0; i < book.entries.length; i++) {
            const ne = normalizeWorldInfoEntry(book.entries[i], book.stateName, defaultScanDepth, i);
            if (ne) all.push(ne);
        }
    }
    const deduped = dedupeLoreEntries(all);
    all = deduped.entries;
    const duplicateEntryCount = deduped.duplicateCount;
    const sourceLabel = sourceLabels.length ? sourceLabels.join(' + ') : 'World info';

    function buildWorldInfoTextFromEntries(entries, maxChars, maxTokens) {
        const lines = [];
        lines.push('World info');
        lines.push(`Name: ${sourceLabel}`);
        lines.push('');

        let usedChars = lines.join('\n').length;
        let usedTokens = tokenEstimator(lines.join('\n'));

        for (const e of entries) {
            const headerParts = [];
            if (e.rawKeys.length) headerParts.push(`keys=${e.rawKeys.join(', ')}`);
            if (e.rawSecondaryKeys.length) headerParts.push(`secondary=${e.rawSecondaryKeys.join(', ')}`);
            if (e.comment) headerParts.push(`note=${e.comment}`);
            if (e.constant) headerParts.push('constant');
            const header = headerParts.length ? `- (${headerParts.join(' | ')})` : '-';
            const chunk = `${header}\n${e.content}\n\n`;
            const chunkTokens = tokenEstimator(chunk);

            if (usedChars + chunk.length > maxChars) continue;
            if (maxTokens !== null && maxTokens !== undefined && maxTokens > 0 && (usedTokens + chunkTokens) > maxTokens) continue;

            lines.push(header);
            lines.push(e.content);
            lines.push('');
            usedChars += chunk.length;
            usedTokens += chunkTokens;
        }

        const body = lines.slice(3).join('\n').trim();
        if (!body) return '';
        return lines.join('\n').trim();
    }

    if (mode === 'full') {
        const enabled = all.filter(e => e.enabled);
        // Deterministic ordering: priority desc then insertionOrder asc (matches trigger path)
        enabled.sort((a, b) => (b.priority - a.priority) || (a.insertionOrder - b.insertionOrder));
        const beforeChar = buildWorldInfoTextFromEntries(enabled, maxCharsBefore, maxTokensBefore);
        return {
            beforeChar,
            afterChar: '',
            depthInjections: [],
            debug: {
                activatedCount: enabled.length,
                totalEntries: all.length,
                triggeredKeys: [],
                updatedState: state,
                mode: 'full',
                sources: sourceLabels,
                duplicateEntries: duplicateEntryCount,
            },
        };
    }

    function entryStateKey(e) {
        const idPart = e.uid !== null ? `uid=${e.uid}` : `order=${e.insertionOrder};index=${e.entryIndex}`;
        return `${e.worldName}::${idPart}::${e.position}`;
    }

    function isOnCooldown(e) {
        if (!e.cooldown) return false;
        const k = entryStateKey(e);
        const last = state[k];
        if (!Number.isFinite(last)) return false;
        return (tick - last) <= e.cooldown;
    }

    function delaySatisfied(e) {
        // Best-effort: treat `delay` as "minimum messages since chat start".
        // ST has richer semantics; for runtime server we keep it predictable.
        if (!e.delay) return true;
        return tick >= e.delay;
    }

    function triggersFromText(e, text, allow) {
        if (!allow) return false;
        if (!text) return false;
        const keyOk = matchesAny(e.keyRegexes, text, e.matchWholeWords);
        if (!keyOk) return false;
        if (e.selective && e.secondaryRegexes.length) {
            const secOk = matchesAny(e.secondaryRegexes, text, e.matchWholeWords);
            if (!secOk) return false;
        }
        return true;
    }

    const activatedMap = new Map(); // stateKey -> entry
    const triggeredKeys = [];

    // Round 0: scan recent chat only
    const baseScanTextByDepth = new Map();
    function getBaseScanText(depth) {
        const d = Math.max(1, Math.min(64, depth));
        if (baseScanTextByDepth.has(d)) return baseScanTextByDepth.get(d);
        const t = buildScanText(chatData, d);
        baseScanTextByDepth.set(d, t);
        return t;
    }

    for (const e of all) {
        if (!e.enabled) continue;
        if (e.constant) {
            const k = entryStateKey(e);
            activatedMap.set(k, e);
            continue;
        }
        if (e.delayUntilRecursion) continue;
        if (!delaySatisfied(e)) continue;
        if (isOnCooldown(e)) continue;
        if (e.useProbability && !probabilityPass(e.probability, `${probabilitySeed}|${entryStateKey(e)}|round=0`)) continue;

        const text = getBaseScanText(e.scanDepth);
        if (!triggersFromText(e, text, true)) continue;
        const k = entryStateKey(e);
        activatedMap.set(k, e);
    }

    // Recursion rounds: allow triggered entries to expand scan text and trigger additional entries
    if (recursiveScanning && recursionMaxRounds > 0) {
        let scanText = getBaseScanText(defaultScanDepth);
        for (let round = 1; round <= recursionMaxRounds; round++) {
            const newlyActivated = [];

            // Expand scan text with activated entry contents that allow recursion
            const addParts = [];
            for (const e of activatedMap.values()) {
                if (e.preventRecursion) continue;
                addParts.push(e.content);
            }
            if (!addParts.length) break;
            scanText = `${scanText}\n${addParts.join('\n')}`;

            for (const e of all) {
                if (!e.enabled) continue;
                if (e.constant) continue;
                if (activatedMap.has(entryStateKey(e))) continue;
                if (e.excludeRecursion) continue;
                if (!delaySatisfied(e)) continue;
                if (isOnCooldown(e)) continue;
                if (e.useProbability && !probabilityPass(e.probability, `${probabilitySeed}|${entryStateKey(e)}|round=${round}`)) continue;

                // For recursion rounds we use the expanded scanText; scanDepth still acts as a guardrail
                const truncatedScan = scanText.slice(-Math.max(1_000, Math.min(200_000, e.scanDepth * 10_000)));
                if (!triggersFromText(e, truncatedScan, true)) continue;
                newlyActivated.push(e);
            }

            if (!newlyActivated.length) break;
            for (const e of newlyActivated) {
                const k = entryStateKey(e);
                activatedMap.set(k, e);
            }
        }
    }

    const activated = Array.from(activatedMap.values());

    // Sorting: ST effectively uses priority + insertion order; keep deterministic.
    activated.sort((a, b) => (b.priority - a.priority) || (a.insertionOrder - b.insertionOrder));

    function markEntryUsed(e) {
        const k = entryStateKey(e);
        triggeredKeys.push(k);
        state[k] = tick;
    }

    function buildBlock(position, maxChars, maxTokens) {
        const lines = [];
        lines.push('World info');
        lines.push(`Name: ${sourceLabel}`);
        lines.push('');

        let usedChars = lines.join('\n').length;
        let usedTokens = tokenEstimator(lines.join('\n'));
        const list = activated.filter(x => x.position === position);
        for (const e of list) {
            const headerParts = [];
            if (e.rawKeys.length) headerParts.push(`keys=${e.rawKeys.join(', ')}`);
            if (e.rawSecondaryKeys.length) headerParts.push(`secondary=${e.rawSecondaryKeys.join(', ')}`);
            if (e.comment) headerParts.push(`note=${e.comment}`);
            if (e.constant) headerParts.push('constant');
            const header = headerParts.length ? `- (${headerParts.join(' | ')})` : '-';
            const chunk = `${header}\n${e.content}\n\n`;
            const chunkTokens = tokenEstimator(chunk);
            if (usedChars + chunk.length > maxChars) continue;
            if (maxTokens !== null && maxTokens !== undefined && maxTokens > 0 && (usedTokens + chunkTokens) > maxTokens) continue;
            lines.push(header);
            lines.push(e.content);
            lines.push('');
            usedChars += chunk.length;
            usedTokens += chunkTokens;

            markEntryUsed(e);
        }
        // If nothing besides header, return empty to avoid polluting prompt.
        const body = lines.slice(4).join('\n').trim();
        if (!body) return '';
        return lines.join('\n').trim();
    }

    function buildDepthInjections() {
        const groups = new Map();
        const list = activated.filter(x => x.position === 'at_depth');
        for (const e of list) {
            const content = String(e.content ?? '').trim();
            if (!content) {
                continue;
            }
            const depth = normalizeInjectionDepth(e.depth);
            const role = normalizeInjectionRole(e.role);
            const key = `${depth}\u0000${role}`;
            if (!groups.has(key)) {
                groups.set(key, {
                    depth,
                    role,
                    entries: [],
                });
            }
            groups.get(key).entries.push(content);
            markEntryUsed(e);
        }
        return Array.from(groups.values())
            .sort((a, b) => (a.depth - b.depth) || (a.role - b.role))
            .map(group => ({
                depth: group.depth,
                role: group.role,
                content: group.entries.join('\n').trim(),
                source: 'world_info',
            }))
            .filter(item => item.content);
    }

    const beforeChar = buildBlock('before_char', maxCharsBefore, maxTokensBefore);
    const afterChar = buildBlock('after_char', maxCharsAfter, maxTokensAfter);
    const depthInjections = buildDepthInjections();
    return {
        beforeChar,
        afterChar,
        depthInjections,
        debug: { activatedCount: activated.length, totalEntries: all.length, triggeredKeys, updatedState: state, sources: sourceLabels, duplicateEntries: duplicateEntryCount },
    };
}

