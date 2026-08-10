import crypto from 'node:crypto';

const MAX_DYNAMIC_ROWS_PER_SHEET = 24;
const STATUS_KEY_ALIASES = new Map([
    ['date', '日期'],
    ['day', '日期'],
    ['日期', '日期'],
    ['时间', '时间'],
    ['time', '时间'],
    ['天气', '天气'],
    ['weather', '天气'],
    ['地点', '地点'],
    ['位置', '地点'],
    ['location', '地点'],
    ['place', '地点'],
    ['场景', '地点'],
    ['此地角色', '此地角色'],
    ['当前角色', '此地角色'],
    ['人物', '此地角色'],
    ['角色', '此地角色'],
    ['能力', '能力'],
    ['状态', '状态'],
    ['特质', '能力'],
]);

function normalizeText(value) {
    return String(value ?? '')
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n')
        .trim();
}

function stripValue(value) {
    return normalizeText(value)
        .replace(/^[`*_#>\-\s]+/, '')
        .replace(/[。；;，,\s]+$/, '')
        .trim();
}

function normalizeKey(value) {
    const raw = normalizeText(value)
        .replace(/[：:：\s]+$/g, '')
        .replace(/[<>{}\[\]【】]/g, '')
        .trim();
    return STATUS_KEY_ALIASES.get(raw) || STATUS_KEY_ALIASES.get(raw.toLowerCase()) || raw;
}

function runtimeUid(prefix) {
    return `${prefix}_runtime_${Date.now().toString(36)}_${crypto.randomBytes(4).toString('hex')}`;
}

function messageRefOf(message) {
    const extra = message && typeof message.extra === 'object' && !Array.isArray(message.extra)
        ? message.extra
        : null;
    return String(extra?.message_ref ?? '').trim();
}

function sheetEnabled(sheet) {
    if (!sheet || typeof sheet !== 'object') return false;
    if (sheet.enable === false || sheet.enabled === false) return false;
    const config = sheet.config && typeof sheet.config === 'object' ? sheet.config : {};
    const toChat = sheet.tochat ?? sheet.toChat ?? config.toChat ?? config.tochat;
    return toChat !== false;
}

function getCellText(cell) {
    if (!cell || typeof cell !== 'object') return '';
    const data = cell.data && typeof cell.data === 'object' ? cell.data : {};
    return normalizeText(data.value ?? data.text ?? data.content ?? data.note ?? '');
}

function setCellText(cell, value) {
    if (!cell || typeof cell !== 'object') return;
    const data = cell.data && typeof cell.data === 'object' ? { ...cell.data } : {};
    data.value = stripValue(value);
    cell.data = data;
}

function getColumns(sheet) {
    const cellHistory = Array.isArray(sheet?.cellHistory) ? sheet.cellHistory : [];
    return cellHistory
        .filter(cell => cell?.type === 'column_header')
        .map(cell => getCellText(cell))
        .filter(Boolean);
}

function buildCellMap(sheet) {
    const out = new Map();
    const cellHistory = Array.isArray(sheet?.cellHistory) ? sheet.cellHistory : [];
    for (const cell of cellHistory) {
        const uid = String(cell?.uid || '').trim();
        if (uid) out.set(uid, cell);
    }
    return out;
}

function isDataCell(cell) {
    return cell && typeof cell === 'object' && cell.type !== 'sheet_origin' && cell.type !== 'column_header';
}

function getDataRows(sheet) {
    const cellMap = buildCellMap(sheet);
    const rows = [];
    const hashSheet = Array.isArray(sheet?.hashSheet) ? sheet.hashSheet : [];
    for (const row of hashSheet) {
        if (!Array.isArray(row)) continue;
        const cells = row.map(uid => cellMap.get(String(uid || '').trim())).filter(isDataCell);
        if (cells.length) rows.push({ row, cells });
    }
    return rows;
}

function createDataCell(value) {
    return {
        uid: runtimeUid('cell'),
        coordUid: runtimeUid('coo'),
        data: { value: stripValue(value) },
        type: 'data_cell',
    };
}

function ensureSheetArrays(sheet) {
    if (!Array.isArray(sheet.hashSheet)) sheet.hashSheet = [];
    if (!Array.isArray(sheet.cellHistory)) sheet.cellHistory = [];
}

function makeDataRow(sheet, columns, values) {
    ensureSheetArrays(sheet);
    const row = [];
    for (let i = 0; i < columns.length; i++) {
        const cell = createDataCell(values[i] ?? '');
        sheet.cellHistory.push(cell);
        row.push(cell.uid);
    }
    sheet.hashSheet.push(row);
    return row;
}

function setRowValues(sheet, row, columns, values) {
    ensureSheetArrays(sheet);
    const cellMap = buildCellMap(sheet);
    while (row.length < columns.length) {
        const cell = createDataCell('');
        sheet.cellHistory.push(cell);
        row.push(cell.uid);
        cellMap.set(cell.uid, cell);
    }
    for (let i = 0; i < columns.length; i++) {
        const cell = cellMap.get(String(row[i] || '').trim());
        if (cell && values[i] != null && String(values[i]).trim()) {
            setCellText(cell, values[i]);
        }
    }
}

function upsertSingleRow(sheet, valuesByColumn) {
    const columns = getColumns(sheet);
    if (!columns.length) return false;
    const values = columns.map(column => valuesByColumn.get(column) ?? '');
    if (!values.some(value => String(value || '').trim())) return false;

    const dataRows = getDataRows(sheet);
    if (dataRows.length) {
        setRowValues(sheet, dataRows[0].row, columns, values);
        if (dataRows.length > 1) {
            const keep = new Set([dataRows[0].row]);
            sheet.hashSheet = (Array.isArray(sheet.hashSheet) ? sheet.hashSheet : []).filter(row => !Array.isArray(row) || keep.has(row) || !row.some(uid => buildCellMap(sheet).get(String(uid || '').trim())?.type === 'data_cell'));
        }
    } else {
        makeDataRow(sheet, columns, values);
    }
    return true;
}

function upsertRowsByFirstColumn(sheet, rows) {
    const columns = getColumns(sheet);
    if (!columns.length || !rows.length) return false;
    const existingRows = getDataRows(sheet);
    const byKey = new Map();
    for (const existing of existingRows) {
        const key = stripValue(getCellText(existing.cells[0]));
        if (key) byKey.set(key, existing.row);
    }

    let changed = false;
    for (const rowValues of rows) {
        const normalized = columns.map((_, i) => stripValue(rowValues[i] ?? ''));
        if (!normalized.some(Boolean)) continue;
        const key = normalized[0];
        const existing = key ? byKey.get(key) : null;
        if (existing) {
            setRowValues(sheet, existing, columns, normalized);
        } else if (getDataRows(sheet).length < MAX_DYNAMIC_ROWS_PER_SHEET) {
            makeDataRow(sheet, columns, normalized);
        }
        changed = true;
    }
    return changed;
}

function parseStatusPairs(text) {
    const pairs = new Map();
    const lines = normalizeText(text)
        .split('\n')
        .map(line => line.trim())
        .filter(Boolean);

    for (const line of lines) {
        const cleaned = line.replace(/^[-*>#`\s]+/, '').trim();
        const chunks = cleaned.split(/\s+\|\s+|[|｜]/g);
        for (const chunk of chunks) {
            const match = chunk.match(/^(.{1,20}?)[：:]\s*(.+)$/);
            if (!match) continue;
            const key = normalizeKey(match[1]);
            const value = stripValue(match[2]);
            if (key && value) pairs.set(key, value);
        }
    }
    return pairs;
}

function parseMarkdownTables(text) {
    const lines = normalizeText(text).split('\n');
    const tables = [];
    for (let i = 0; i < lines.length - 1; i++) {
        const header = parseTableRow(lines[i]);
        const divider = parseTableDivider(lines[i + 1]);
        if (!header.length || !divider) continue;
        const rows = [];
        i += 2;
        while (i < lines.length) {
            const row = parseTableRow(lines[i]);
            if (!row.length) break;
            rows.push(row);
            i++;
        }
        if (rows.length) tables.push({ header, rows });
    }
    return tables;
}

function parseTableRow(line) {
    const text = normalizeText(line);
    if (!text.includes('|')) return [];
    return text
        .replace(/^\s*\|/, '')
        .replace(/\|\s*$/, '')
        .split('|')
        .map(cell => stripValue(cell))
        .filter((_, index, arr) => arr.length > 1 || index === 0);
}

function parseTableDivider(line) {
    return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
}

function scoreHeaderMatch(sheetColumns, tableHeader) {
    const sheetSet = new Set(sheetColumns.map(column => normalizeKey(column)));
    let score = 0;
    for (const column of tableHeader) {
        if (sheetSet.has(normalizeKey(column))) score++;
    }
    return score;
}

function applyMarkdownTableUpdates(sheets, text) {
    const tables = parseMarkdownTables(text);
    let changed = 0;
    for (const table of tables) {
        let best = null;
        let bestScore = 0;
        for (const sheet of sheets) {
            const columns = getColumns(sheet);
            const score = scoreHeaderMatch(columns, table.header);
            if (score > bestScore) {
                best = sheet;
                bestScore = score;
            }
        }
        if (!best || bestScore < Math.min(2, table.header.length)) continue;
        const targetColumns = getColumns(best);
        const indexByHeader = new Map(table.header.map((name, index) => [normalizeKey(name), index]));
        const rows = table.rows.map(row => targetColumns.map(column => row[indexByHeader.get(normalizeKey(column))] ?? ''));
        if (upsertRowsByFirstColumn(best, rows)) changed++;
    }
    return changed;
}

function applyStatusUpdates(sheets, statusPairs, { userName = '', charName = '' } = {}) {
    let changed = 0;
    const characterName = stripValue(userName) || '<user>';
    for (const sheet of sheets) {
        const name = normalizeText(sheet.name);
        const columns = getColumns(sheet);
        const columnSet = new Set(columns);
        if (name.includes('时空') || (columnSet.has('日期') && columnSet.has('时间') && columns.some(c => c.includes('地点')))) {
            const values = new Map();
            for (const column of columns) {
                if (column.includes('日期')) values.set(column, statusPairs.get('日期'));
                else if (column.includes('时间')) values.set(column, statusPairs.get('时间'));
                else if (column.includes('天气')) values.set(column, statusPairs.get('天气'));
                else if (column.includes('地点')) values.set(column, statusPairs.get('地点'));
                else if (column.includes('角色')) values.set(column, statusPairs.get('此地角色') || charName || characterName);
            }
            if (upsertSingleRow(sheet, values)) changed++;
            continue;
        }

        if (name.includes('角色特征') && (statusPairs.has('能力') || statusPairs.has('状态'))) {
            const values = new Map();
            for (const column of columns) {
                if (column.includes('角色名')) values.set(column, characterName);
                else if (column.includes('身体') || column.includes('特征') || column.includes('其他')) {
                    values.set(column, statusPairs.get('能力') || statusPairs.get('状态'));
                }
            }
            if (upsertRowsByFirstColumn(sheet, [columns.map(column => values.get(column) ?? '')])) changed++;
        }
    }
    return changed;
}

function applyExplicitJsonUpdates(sheets, text) {
    const blocks = [];
    const tagRe = /<st_dynamic_sheets>([\s\S]*?)<\/st_dynamic_sheets>/gi;
    let match;
    while ((match = tagRe.exec(text)) !== null) {
        blocks.push(match[1]);
    }
    let changed = 0;
    for (const raw of blocks) {
        try {
            const parsed = JSON.parse(raw);
            const updates = Array.isArray(parsed?.sheets) ? parsed.sheets : [];
            for (const update of updates) {
                const name = normalizeText(update.name ?? update.sheet ?? update.uid);
                const sheet = sheets.find(item => normalizeText(item.uid) === name || normalizeText(item.name) === name);
                if (!sheet) continue;
                const rows = Array.isArray(update.rows) ? update.rows : [];
                const normalizedRows = rows
                    .map(row => Array.isArray(row) ? row : Object.values(row || {}))
                    .filter(row => row.length);
                if (upsertRowsByFirstColumn(sheet, normalizedRows)) changed++;
            }
        } catch {
            // Ignore malformed model-side metadata; visible reply must remain valid.
        }
    }
    return changed;
}

function mirrorHashSheetsToMessage(message, sheets) {
    if (!message || typeof message !== 'object') return;
    const hashSheets = message.hash_sheets && typeof message.hash_sheets === 'object' && !Array.isArray(message.hash_sheets)
        ? { ...message.hash_sheets }
        : {};
    for (const sheet of sheets) {
        const uid = String(sheet?.uid || '').trim();
        if (uid && Array.isArray(sheet.hashSheet)) {
            hashSheets[uid] = sheet.hashSheet;
        }
    }
    if (Object.keys(hashSheets).length) {
        message.hash_sheets = hashSheets;
    }
}

export function updateRuntimeDynamicSheetsFromMessage(chatData, assistantMessage, options = {}) {
    if (!Array.isArray(chatData) || !assistantMessage || assistantMessage.is_user === true) {
        return { updated: false, changedSheets: 0, reason: 'not_assistant' };
    }
    const header = chatData[0];
    const meta = header?.chat_metadata && typeof header.chat_metadata === 'object' ? header.chat_metadata : null;
    const sheets = Array.isArray(meta?.sheets) ? meta.sheets.filter(sheetEnabled) : [];
    if (!meta || !sheets.length) {
        return { updated: false, changedSheets: 0, reason: 'no_sheets' };
    }

    const text = normalizeText(assistantMessage.mes);
    if (!text) {
        return { updated: false, changedSheets: 0, reason: 'empty_message' };
    }

    const statusPairs = parseStatusPairs(text);
    let changedSheets = 0;
    changedSheets += applyStatusUpdates(sheets, statusPairs, options);
    changedSheets += applyMarkdownTableUpdates(sheets, text);
    changedSheets += applyExplicitJsonUpdates(sheets, text);

    if (changedSheets > 0) {
        meta.siye_runtime_sheet_state = {
            updated_at: new Date().toISOString(),
            source: 'runtime_dynamic_sheets',
            message_ref: messageRefOf(assistantMessage),
            changed_sheets: changedSheets,
        };
        mirrorHashSheetsToMessage(assistantMessage, sheets);
        return { updated: true, changedSheets };
    }

    return { updated: false, changedSheets: 0, reason: 'no_changes' };
}
