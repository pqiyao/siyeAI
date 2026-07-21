import { readWorldInfoFile } from '../../endpoints/worldinfo.js';

function asArrayKeys(v) {
    if (!v) return [];
    if (Array.isArray(v)) return v.map(x => String(x).trim()).filter(Boolean);
    return String(v).split(',').map(x => x.trim()).filter(Boolean);
}

/**
 * Minimal StepB world-info prompt builder.
 * This is NOT the full ST lorebook engine (selective triggers/recursion/budget).
 * It provides a safe, deterministic "good enough" injection surface we can
 * later replace with the real ST implementation.
 *
 * @param {import('../users.js').UserDirectoryList} directories
 * @param {object} card Parsed character card object (png json)
 * @returns {string} prompt text
 */
export function buildWorldInfoPrompt(directories, card) {
    if (!directories || !card) return '';

    // ST card objects may come in different shapes depending on parser/source.
    // Support common variants:
    // - card.data.extensions.world (v2)
    // - card.extensions.world
    // - card.world
    const worldName = String(
        card?.data?.extensions?.world ??
        card?.extensions?.world ??
        card?.world ??
        ''
    ).trim();
    const embedded = card?.data?.character_book ?? card?.character_book;
    const hasEmbeddedBook = !!embedded;
    let book = null;

    if (hasEmbeddedBook) {
        book = embedded;
    } else if (worldName) {
        const file = readWorldInfoFile(directories, worldName, true);
        // worldinfo.js file format is { entries: { ... } } (legacy). Convert to simple list.
        if (file && file.entries) {
            book = { name: worldName, entries: Object.values(file.entries) };
        }
    }

    const entriesRaw = Array.isArray(book?.entries) ? book.entries : [];
    const enabledEntries = entriesRaw
        .filter(e => e && (e.enabled === undefined ? true : !!e.enabled))
        .slice(0, 30);

    if (!enabledEntries.length) return '';

    const lines = [];
    lines.push('World info');
    if (book?.name) lines.push(`Name: ${String(book.name).trim()}`);
    lines.push('');

    for (const e of enabledEntries) {
        const keys = asArrayKeys(e.keys || e.key || e.keysecondary);
        const comment = String(e.comment || '').trim();
        const content = String(e.content || '').trim();
        if (!content) continue;

        const headerParts = [];
        if (keys.length) headerParts.push(`keys=${keys.join(', ')}`);
        if (comment) headerParts.push(`note=${comment}`);
        const header = headerParts.length ? `- (${headerParts.join(' | ')})` : '-';
        lines.push(header);
        lines.push(content);
        lines.push('');
    }

    return lines.join('\n').trim();
}

/**
 * Build world info prompt from explicit world names (main + extras).
 * This matches ST UI concept where worldbooks are bound to a chat/session,
 * not necessarily stored inside the character card.
 *
 * @param {import('../users.js').UserDirectoryList} directories
 * @param {string[]} worldNames
 * @returns {string}
 */
export function buildWorldInfoPromptFromWorldNames(directories, worldNames = []) {
    if (!directories) return '';
    const names = Array.isArray(worldNames) ? worldNames.map(x => String(x).trim()).filter(Boolean) : [];
    if (!names.length) return '';

    const lines = [];
    lines.push('World info');
    lines.push(`Name: ${names.join(' + ')}`);
    lines.push('');

    for (const wn of names) {
        const file = readWorldInfoFile(directories, wn, true);
        const entriesObj = file && file.entries ? file.entries : {};
        const entries = Object.values(entriesObj).slice(0, 30);
        if (!entries.length) continue;
        lines.push(`## ${wn}`);
        for (const e of entries) {
            const content = String(e?.content || '').trim();
            if (!content) continue;
            lines.push('-');
            lines.push(content);
        }
        lines.push('');
    }

    return lines.join('\n').trim();
}

