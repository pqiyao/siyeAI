/**
 * StepB (bootstrap): minimal, DOM-free message builder for server-side runtime.
 *
 * Goal: concentrate the prompt-building surface so we can incrementally port
 * ST web logic (world-info/templates/instruct/truncation) into Node server code.
 */

/**
 * @param {object} args
 * @param {string} args.userName
 * @param {string} args.charName
 * @param {string} [args.mainPrompt]
 * @param {string} [args.cardPrompt]
 * @param {Array<string|{identifier?: string, content?: string}>} [args.cardPromptSections]
 * @param {Array<string|Array<{role?: string, content?: string, name?: string}>>} [args.dialogueExamples]
 * @param {Array<string|{identifier?: string, enabled?: boolean}>} [args.promptOrder]
 * @param {Record<string, string>} [args.extraSystemPrompts]
 * @param {string} [args.worldInfoPrompt]
 * @param {{beforeChar?: string, afterChar?: string, depthInjections?: Array<{depth?: number, role?: number|string, content?: string, prompt?: string, text?: string}>}} [args.worldInfoBlocks]
 * @param {string} [args.dynamicSheetsPrompt]
 * @param {Array<{depth?: number, role?: number|string, content?: string, prompt?: string, text?: string}>} [args.depthInjections]
 * @param {Array<{is_user?: boolean, mes?: string}>} args.chatData
 * @param {string} [args.firstMes]
 * @param {string} [args.postHistoryInstructions]
 * @param {string} [args.tailSystemPrompt]
 * @param {(text:string)=>number} [args.tokenEstimator]
 * @param {number} [args.maxPromptTokens]
 * @returns {{messages: Array<{role: string, content: string}>}}
 */
export function buildRuntimeMessages({
    userName = '',
    charName = '',
    mainPrompt = '',
    cardPrompt = '',
    cardPromptSections = [],
    dialogueExamples = [],
    promptOrder = [],
    extraSystemPrompts = {},
    worldInfoPrompt = '',
    worldInfoBlocks = null,
    dynamicSheetsPrompt = '',
    depthInjections = [],
    chatData = [],
    firstMes = '',
    postHistoryInstructions = '',
    tailSystemPrompt = '',
    tokenEstimator = null,
    maxPromptTokens = 0,
} = {}) {
    const messages = [];

    const resolvedMainPrompt = String(mainPrompt ?? '').trim();
    const defaultMainPrompt = charName && userName
        ? `Write ${charName}'s next reply in a fictional chat between ${charName} and ${userName}.`
        : '';
    const beforeChar = worldInfoBlocks && typeof worldInfoBlocks.beforeChar === 'string' ? worldInfoBlocks.beforeChar.trim() : '';
    const afterChar = worldInfoBlocks && typeof worldInfoBlocks.afterChar === 'string' ? worldInfoBlocks.afterChar.trim() : '';
    const fallbackWorldInfo = worldInfoPrompt ? String(worldInfoPrompt).trim() : '';
    const normalizedCardSections = normalizeCardPromptSections(cardPromptSections);
    const normalizedDialogueExamples = normalizeDialogueExamples(dialogueExamples);
    const normalizedDepthInjections = normalizeDepthInjections([
        ...(
            worldInfoBlocks && Array.isArray(worldInfoBlocks.depthInjections)
                ? worldInfoBlocks.depthInjections
                : []
        ),
        ...(
            Array.isArray(depthInjections)
                ? depthInjections
                : []
        ),
    ]);

    const conversationMessages = [];
    const normalizedFirstMes = normalizeMessageText(firstMes);
    if (
        normalizedFirstMes &&
        !chatDataHasAssistantMessage(chatData) &&
        !chatDataContainsAssistantMessage(chatData, normalizedFirstMes)
    ) {
        conversationMessages.push({ role: 'assistant', content: normalizedFirstMes });
    }

    if (Array.isArray(chatData)) {
        for (const m of chatData) {
            if (!m || typeof m.mes !== 'string') continue;
            const content = m.mes;
            const role = m.is_user ? 'user' : 'assistant';
            conversationMessages.push({ role, content });
        }
    }
    const historyMessages = [
        { role: 'system', content: '[Start a new Chat]' },
        ...injectDepthMessages(conversationMessages, normalizedDepthInjections),
    ];

    const sections = new Map();
    setSection(sections, 'main', textToSystemMessages(resolvedMainPrompt || defaultMainPrompt));
    setSection(sections, 'worldInfoBefore', textToSystemMessages(beforeChar));
    setSection(sections, 'worldInfoAfter', textToSystemMessages(afterChar || fallbackWorldInfo));
    setSection(sections, 'dynamicSheets', textToSystemMessages(dynamicSheetsPrompt));
    setSection(sections, 'dialogueExamples', flattenDialogueExampleMessages(normalizedDialogueExamples));
    setSection(sections, 'chatHistory', historyMessages);
    setSection(sections, 'jailbreak', textToSystemMessages(postHistoryInstructions));
    for (const [identifier, content] of Object.entries(extraSystemPrompts || {})) {
        if (identifier === 'main' || identifier === 'jailbreak') {
            continue;
        }
        setSection(sections, identifier, textToSystemMessages(content));
    }

    if (normalizedCardSections.length) {
        for (const section of normalizedCardSections) {
            appendSection(sections, section.identifier, textToSystemMessages(section.content));
        }
    } else if (cardPrompt) {
        setSection(sections, 'charDescription', textToSystemMessages(cardPrompt));
    }

    for (const entry of normalizePromptOrder(promptOrder)) {
        const section = sections.get(entry.identifier) || [];
        if (entry.enabled === false || section.length === 0) {
            continue;
        }
        messages.push(...section);
    }
    messages.push(...textToSystemMessages(tailSystemPrompt));

    return {
        messages: trimMessagesToBudget(messages, {
            tokenEstimator,
            maxPromptTokens,
        }),
    };
}

function normalizeMessageText(text) {
    return String(text ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
}

const DEFAULT_PROMPT_ORDER = Object.freeze([
    { identifier: 'main', enabled: true },
    { identifier: 'worldInfoBefore', enabled: true },
    { identifier: 'charDescription', enabled: true },
    { identifier: 'charPersonality', enabled: true },
    { identifier: 'scenario', enabled: true },
    { identifier: 'nsfw', enabled: true },
    { identifier: 'worldInfoAfter', enabled: true },
    { identifier: 'dynamicSheets', enabled: true },
    { identifier: 'dialogueExamples', enabled: true },
    { identifier: 'chatHistory', enabled: true },
    { identifier: 'jailbreak', enabled: true },
]);

function normalizePromptOrder(order) {
    const seen = new Set();
    const normalized = [];
    if (Array.isArray(order)) {
        for (const entry of order) {
            const identifier = typeof entry === 'string'
                ? entry.trim()
                : String(entry?.identifier ?? '').trim();
            if (!identifier || seen.has(identifier)) {
                continue;
            }
            seen.add(identifier);
            normalized.push({
                identifier,
                enabled: typeof entry === 'object' && entry?.enabled === false ? false : true,
            });
        }
    }
    for (const entry of DEFAULT_PROMPT_ORDER) {
        if (!seen.has(entry.identifier)) {
            normalized.push(entry);
        }
    }
    return normalized;
}

function normalizeCardPromptSections(sections) {
    if (!Array.isArray(sections) || sections.length === 0) {
        return [];
    }
    return sections
        .map((section, index) => {
            if (section && typeof section === 'object') {
                const content = normalizeMessageText(section.content);
                if (!content) return null;
                return {
                    identifier: String(section.identifier ?? defaultCardSectionIdentifier(index)).trim() || defaultCardSectionIdentifier(index),
                    content,
                };
            }
            const content = normalizeMessageText(section);
            if (!content) return null;
            return {
                identifier: defaultCardSectionIdentifier(index),
                content,
            };
        })
        .filter(Boolean);
}

function defaultCardSectionIdentifier(index) {
    if (index === 0) return 'charDescription';
    if (index === 1) return 'charPersonality';
    if (index === 2) return 'scenario';
    return `charExtra${index}`;
}

function textToSystemMessages(text) {
    const content = normalizeMessageText(text);
    return content ? [{ role: 'system', content }] : [];
}

function setSection(sections, identifier, messages) {
    const key = String(identifier || '').trim();
    if (!key || !Array.isArray(messages) || messages.length === 0) {
        return;
    }
    sections.set(key, messages);
}

function appendSection(sections, identifier, messages) {
    const key = String(identifier || '').trim();
    if (!key || !Array.isArray(messages) || messages.length === 0) {
        return;
    }
    const current = sections.get(key) || [];
    sections.set(key, [...current, ...messages]);
}

function flattenDialogueExampleMessages(examples) {
    const out = [];
    for (const example of examples) {
        out.push({ role: 'system', content: '[Example Chat]' });
        out.push(...example);
    }
    return out;
}

function trimMessagesToBudget(messages, { tokenEstimator = null, maxPromptTokens = 0 } = {}) {
    if (!Array.isArray(messages) || messages.length === 0) {
        return [];
    }
    const budget = Math.trunc(Number(maxPromptTokens));
    if (!Number.isFinite(budget) || budget <= 0 || typeof tokenEstimator !== 'function') {
        return messages;
    }
    const estimateTotal = (items) => items.reduce((sum, item) => sum + estimateMessageTokens(item, tokenEstimator), 0);
    let total = estimateTotal(messages);
    if (total <= budget) {
        return messages;
    }

    const trimmed = [...messages];
    const startIndex = trimmed.findIndex(item => item?.role === 'system' && item?.content === '[Start a new Chat]');
    if (startIndex >= 0) {
        // Keep the newest history, which is what ST prioritizes when context is tight.
        for (let i = startIndex + 1; i < trimmed.length && total > budget;) {
            const item = trimmed[i];
            const isHistoryMessage = item && (item.role === 'user' || item.role === 'assistant');
            if (!isHistoryMessage) {
                i++;
                continue;
            }
            total -= estimateMessageTokens(item, tokenEstimator);
            trimmed.splice(i, 1);
        }
    }

    if (total <= budget) {
        return trimmed;
    }

    // If the fixed prompt itself is too large, drop example blocks before core character/world info.
    for (let i = 0; i < trimmed.length && total > budget;) {
        const item = trimmed[i];
        if (!(item?.role === 'system' && item?.content === '[Example Chat]')) {
            i++;
            continue;
        }
        total -= estimateMessageTokens(item, tokenEstimator);
        trimmed.splice(i, 1);
        while (i < trimmed.length && total > budget) {
            const next = trimmed[i];
            if (!next || next.content === '[Start a new Chat]' || next.content === '[Example Chat]') {
                break;
            }
            if (next.name !== 'example_user' && next.name !== 'example_assistant') {
                break;
            }
            total -= estimateMessageTokens(next, tokenEstimator);
            trimmed.splice(i, 1);
        }
    }

    return trimmed;
}

function estimateMessageTokens(message, tokenEstimator) {
    if (!message || typeof message !== 'object') {
        return 0;
    }
    const content = String(message.content ?? '');
    const name = String(message.name ?? '');
    const role = String(message.role ?? '');
    return Math.max(1, tokenEstimator(content) + tokenEstimator(name) + tokenEstimator(role) + 4);
}

function normalizeDialogueExamples(examples) {
    if (!Array.isArray(examples) || examples.length === 0) {
        return [];
    }
    return examples
        .map(example => {
            if (Array.isArray(example)) {
                return example
                    .map(message => normalizeDialogueExampleMessage(message))
                    .filter(Boolean);
            }
            const content = normalizeMessageText(example);
            return content ? [{ role: 'system', content }] : [];
        })
        .filter(block => block.length > 0);
}

function normalizeDialogueExampleMessage(message) {
    if (!message || typeof message !== 'object') {
        return null;
    }
    const content = normalizeMessageText(message.content);
    if (!content) {
        return null;
    }
    const role = normalizeRole(message.role);
    const name = String(message.name ?? '').trim();
    return name ? { role, content, name } : { role, content };
}

const DEFAULT_DEPTH = 4;
const MAX_INJECTION_DEPTH = 10_000;
const ROLE_ORDER = Object.freeze({
    system: 0,
    user: 1,
    assistant: 2,
});

function normalizeInjectionDepth(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) {
        return DEFAULT_DEPTH;
    }
    return Math.max(0, Math.min(MAX_INJECTION_DEPTH, Math.trunc(n)));
}

function normalizeRole(role) {
    if (typeof role === 'number') {
        if (role === 1) return 'user';
        if (role === 2) return 'assistant';
        return 'system';
    }
    const raw = String(role ?? '').trim().toLowerCase();
    if (raw === 'user' || raw === '1') return 'user';
    if (raw === 'assistant' || raw === 'char' || raw === '2') return 'assistant';
    return 'system';
}

function normalizeDepthInjections(injections) {
    if (!Array.isArray(injections) || injections.length === 0) {
        return [];
    }
    return injections
        .map((injection, index) => {
            const content = normalizeMessageText(injection?.content ?? injection?.prompt ?? injection?.text);
            if (!content) {
                return null;
            }
            return {
                content,
                depth: normalizeInjectionDepth(injection?.depth),
                role: normalizeRole(injection?.role),
                order: index,
            };
        })
        .filter(Boolean);
}

function sortDepthGroup(a, b) {
    const roleDiff = (ROLE_ORDER[a.role] ?? 0) - (ROLE_ORDER[b.role] ?? 0);
    return roleDiff || (a.order - b.order);
}

function injectDepthMessages(conversationMessages, injections) {
    if (!Array.isArray(conversationMessages) || conversationMessages.length === 0) {
        return injections
            .slice()
            .sort(sortDepthGroup)
            .map(injection => ({ role: injection.role, content: injection.content }));
    }
    if (!Array.isArray(injections) || injections.length === 0) {
        return conversationMessages;
    }

    const baseLength = conversationMessages.length;
    const byIndex = new Map();
    for (const injection of injections) {
        const index = Math.max(0, Math.min(baseLength, baseLength - injection.depth));
        if (!byIndex.has(index)) {
            byIndex.set(index, []);
        }
        byIndex.get(index).push(injection);
    }

    const result = [];
    for (let i = 0; i <= baseLength; i++) {
        const group = byIndex.get(i);
        if (group && group.length) {
            result.push(...group
                .slice()
                .sort(sortDepthGroup)
                .map(injection => ({ role: injection.role, content: injection.content })));
        }
        if (i < baseLength) {
            result.push(conversationMessages[i]);
        }
    }
    return result;
}

function chatDataContainsAssistantMessage(chatData, content) {
    const target = normalizeMessageText(content);
    if (!target || !Array.isArray(chatData)) {
        return false;
    }
    return chatData.some(m => m && m.is_user === false && normalizeMessageText(m.mes) === target);
}

function chatDataHasAssistantMessage(chatData) {
    if (!Array.isArray(chatData)) {
        return false;
    }
    return chatData.some(m => m && m.is_user === false && normalizeMessageText(m.mes));
}
