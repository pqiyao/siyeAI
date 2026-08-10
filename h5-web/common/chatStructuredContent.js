var BLOCK_TAGS = Object.freeze({
	address: true,
	article: true,
	aside: true,
	blockquote: true,
	body: true,
	dd: true,
	details: true,
	div: true,
	dl: true,
	dt: true,
	fieldset: true,
	figcaption: true,
	figure: true,
	footer: true,
	form: true,
	h1: true,
	h2: true,
	h3: true,
	h4: true,
	h5: true,
	h6: true,
	header: true,
	html: true,
	li: true,
	main: true,
	meter: true,
	nav: true,
	ol: true,
	p: true,
	pre: true,
	progress: true,
	section: true,
	summary: true,
	table: true,
	tbody: true,
	td: true,
	tfoot: true,
	th: true,
	thead: true,
	tr: true,
	ul: true
});

var VOID_TAGS = Object.freeze({
	area: true,
	base: true,
	br: true,
	col: true,
	embed: true,
	hr: true,
	img: true,
	input: true,
	link: true,
	meta: true,
	param: true,
	source: true,
	track: true,
	wbr: true
});

var UNSAFE_BLOCK_TAGS = Object.freeze({
	iframe: true,
	object: true,
	script: true,
	style: true
});

var STATUS_TAG_HINT = /(?:status|state|progress|affection|relationship|love|heart|memory|thought|panel|card|sheet|content)/i;
var STRONG_STATUS_HINT = /(?:status|state|progress|affection|relationship|love|heart|memory|thought|panel|sheet)/i;
var STATUS_TEXT_HINT = /(?:好感|爱心|亲密|关系状态|当前状态|状态栏|进度|剧情阶段|阶段|生命值|体力值|心情值|affection|relationship|status|progress|heart|love\s*level)/i;
var STATUS_VALUE_HINT = /(?:[:：]\s*\S|\d+(?:\.\d+)?\s*(?:%|％|\/)|[❤♥♡💗💖💕]+)/;

function isNameStart(character) {
	return /[A-Za-z]/.test(character || '');
}

function isNameCharacter(character) {
	return /[A-Za-z0-9:_-]/.test(character || '');
}

function readTagAt(source, start) {
	var text = String(source || '');
	if (start < 0 || text.charAt(start) !== '<') return null;
	if (text.slice(start, start + 4) === '<!--') {
		var commentEnd = text.indexOf('-->', start + 4);
		return commentEnd >= 0
			? { start: start, end: commentEnd + 3, name: '', closing: false, selfClosing: true, special: true }
			: null;
	}
	if (text.charAt(start + 1) === '!' || text.charAt(start + 1) === '?') {
		var declarationEnd = text.indexOf('>', start + 2);
		return declarationEnd >= 0
			? { start: start, end: declarationEnd + 1, name: '', closing: false, selfClosing: true, special: true }
			: null;
	}

	var cursor = start + 1;
	var closing = false;
	if (text.charAt(cursor) === '/') {
		closing = true;
		cursor += 1;
	}
	if (!isNameStart(text.charAt(cursor))) return null;
	var nameStart = cursor;
	while (cursor < text.length && isNameCharacter(text.charAt(cursor))) cursor += 1;
	var name = text.slice(nameStart, cursor).toLowerCase();
	var quote = '';
	for (; cursor < text.length; cursor += 1) {
		var current = text.charAt(cursor);
		if (quote) {
			if (current === quote) quote = '';
			continue;
		}
		if (current === '"' || current === "'") {
			quote = current;
			continue;
		}
		if (current === '>') {
			var raw = text.slice(start, cursor + 1);
			return {
				start: start,
				end: cursor + 1,
				name: name,
				closing: closing,
				selfClosing: !closing && (VOID_TAGS[name] === true || /\/\s*>$/.test(raw)),
				special: false
			};
		}
	}
	return null;
}

function nextTag(source, fromIndex) {
	var text = String(source || '');
	var cursor = Math.max(0, Number(fromIndex || 0));
	while (cursor < text.length) {
		var start = text.indexOf('<', cursor);
		if (start < 0) return null;
		var tag = readTagAt(text, start);
		if (tag) return tag;
		cursor = start + 1;
	}
	return null;
}

function fencedCodeRanges(source) {
	var ranges = [];
	var text = String(source || '');
	var expression = /^( {0,3})(`{3,}|~{3,})[^\n]*(?:\n|$)/gm;
	var match = null;
	while ((match = expression.exec(text))) {
		var marker = match[2];
		var markerCharacter = marker.charAt(0);
		var closingExpression = new RegExp('^ {0,3}' + markerCharacter + '{' + marker.length + ',}[ \\t]*(?:\\n|$)', 'gm');
		closingExpression.lastIndex = expression.lastIndex;
		var closingMatch = closingExpression.exec(text);
		var end = closingMatch ? closingMatch.index + closingMatch[0].length : text.length;
		ranges.push({ start: match.index, end: end });
		expression.lastIndex = end;
	}
	return ranges;
}

function markdownCodeRanges(source) {
	var text = String(source || '');
	var ranges = fencedCodeRanges(text);
	var cursor = 0;
	while (cursor < text.length) {
		var start = text.indexOf('`', cursor);
		if (start < 0) break;
		var fencedRange = containingRange(ranges, start);
		if (fencedRange) {
			cursor = fencedRange.end;
			continue;
		}
		var markerEnd = start + 1;
		while (text.charAt(markerEnd) === '`') markerEnd += 1;
		var marker = text.slice(start, markerEnd);
		var lineEnd = text.indexOf('\n', markerEnd);
		if (lineEnd < 0) lineEnd = text.length;
		var closingStart = text.indexOf(marker, markerEnd);
		if (closingStart >= 0 && closingStart <= lineEnd) {
			ranges.push({ start: start, end: closingStart + marker.length });
			cursor = closingStart + marker.length;
		} else {
			cursor = markerEnd;
		}
	}
	ranges.sort(function (left, right) { return left.start - right.start; });
	return ranges;
}

function containingRange(ranges, index) {
	for (var i = 0; i < ranges.length; i += 1) {
		if (index >= ranges[i].start && index < ranges[i].end) return ranges[i];
	}
	return null;
}

function isStructuredBlockTag(name) {
	var normalized = String(name || '').toLowerCase();
	return !!(
		BLOCK_TAGS[normalized] ||
		normalized.indexOf('-') >= 0 ||
		STATUS_TAG_HINT.test(normalized)
	);
}

function openingTagHasStatusHint(source, tag) {
	if (!tag || !tag.name) return false;
	if (STRONG_STATUS_HINT.test(tag.name)) return true;
	var openingTag = String(source || '').slice(tag.start, tag.end);
	return /(?:class|id|role|data-[\w:-]+)\s*=\s*(?:"[^"]*(?:status|state|progress|affection|relationship|love|heart|panel|sheet)[^"]*"|'[^']*(?:status|state|progress|affection|relationship|love|heart|panel|sheet)[^']*'|[^\s>]*(?:status|state|progress|affection|relationship|love|heart|panel|sheet)[^\s>]*)/i.test(openingTag);
}

function classifyStructuredKind(source, openingTag, blockEnd) {
	var block = String(source || '').slice(openingTag.start, blockEnd);
	if (openingTagHasStatusHint(source, openingTag)) return 'status';
	var plainText = stripStructuredMarkupToText(block);
	if (/[“"「『][^”"」』]{2,}[”"」』]/.test(plainText)) return 'content';
	if (/<(?:progress|meter)\b/i.test(block)) return 'status';
	if (STATUS_TEXT_HINT.test(plainText) && STATUS_VALUE_HINT.test(plainText)) return 'status';
	if (openingTag.name === 'table' && STATUS_TEXT_HINT.test(plainText)) return 'status';
	if (openingTag.name === 'details' && STATUS_TEXT_HINT.test(plainText)) return 'status';
	return 'content';
}

function matchingCloseEnd(source, openingTag) {
	if (!openingTag || openingTag.closing || openingTag.selfClosing || !openingTag.name) return -1;
	var depth = 1;
	var cursor = openingTag.end;
	while (cursor < source.length) {
		var tag = nextTag(source, cursor);
		if (!tag) return -1;
		cursor = tag.end;
		if (!tag.closing && !tag.selfClosing && (tag.name === 'script' || tag.name === 'style') && tag.name !== openingTag.name) {
			var rawCloseExpression = new RegExp('<\\/' + tag.name + '\\s*>', 'ig');
			rawCloseExpression.lastIndex = tag.end;
			var rawCloseMatch = rawCloseExpression.exec(source);
			if (!rawCloseMatch) return -1;
			cursor = rawCloseMatch.index + rawCloseMatch[0].length;
			continue;
		}
		if (tag.special || tag.name !== openingTag.name) continue;
		if (tag.closing) {
			depth -= 1;
			if (depth === 0) return tag.end;
		} else if (!tag.selfClosing) {
			depth += 1;
		}
	}
	return -1;
}

function splitStructuredContent(value) {
	var source = String(value == null ? '' : value);
	if (!source || source.indexOf('<') < 0) {
		return source ? [{ type: 'text', text: source }] : [];
	}
	var codeRanges = markdownCodeRanges(source);
	var parts = [];
	var contentCursor = 0;
	var searchCursor = 0;
	while (searchCursor < source.length) {
		var tag = nextTag(source, searchCursor);
		if (!tag) break;
		var codeRange = containingRange(codeRanges, tag.start);
		if (codeRange) {
			searchCursor = codeRange.end;
			continue;
		}
		if (tag.start > 0 && source.charAt(tag.start - 1) === '\\') {
			searchCursor = tag.end;
			continue;
		}
		if (tag.special) {
			if (tag.start > contentCursor) {
				parts.push({ type: 'text', text: source.slice(contentCursor, tag.start) });
			}
			parts.push({ type: 'discard', text: source.slice(tag.start, tag.end), tagName: '' });
			contentCursor = tag.end;
			searchCursor = tag.end;
			continue;
		}
		var unsafeBlock = UNSAFE_BLOCK_TAGS[tag.name] === true;
		var statusOpening = openingTagHasStatusHint(source, tag);
		if (tag.closing || tag.selfClosing || (!unsafeBlock && !statusOpening && !isStructuredBlockTag(tag.name))) {
			searchCursor = tag.end;
			continue;
		}
		var blockEnd = matchingCloseEnd(source, tag);
		if (blockEnd < 0) {
			if (tag.start > contentCursor) {
				parts.push({ type: 'text', text: source.slice(contentCursor, tag.start) });
			}
			parts.push({
				type: unsafeBlock ? 'discard' : 'pending',
				text: source.slice(tag.start),
				tagName: tag.name,
				kind: unsafeBlock ? '' : 'content'
			});
			contentCursor = source.length;
			searchCursor = source.length;
			break;
		}
		if (tag.start > contentCursor) {
			parts.push({ type: 'text', text: source.slice(contentCursor, tag.start) });
		}
		parts.push({
			type: unsafeBlock ? 'discard' : 'rich',
			text: source.slice(tag.start, blockEnd),
			tagName: tag.name,
			kind: unsafeBlock ? '' : classifyStructuredKind(source, tag, blockEnd)
		});
		contentCursor = blockEnd;
		searchCursor = blockEnd;
	}
	if (contentCursor < source.length) {
		var tail = source.slice(contentCursor);
		var incompleteMatch = /<\/?[A-Za-z][A-Za-z0-9:_-]*(?:\s[^<>]*)?$/.exec(tail);
		if (incompleteMatch && (incompleteMatch.index === 0 || tail.charAt(incompleteMatch.index - 1) !== '\\')) {
			if (incompleteMatch.index > 0) {
				parts.push({ type: 'text', text: tail.slice(0, incompleteMatch.index) });
			}
			parts.push({ type: 'pending', text: tail.slice(incompleteMatch.index), tagName: '', kind: 'content' });
		} else {
			parts.push({ type: 'text', text: tail });
		}
	}
	return parts.length ? parts : [{ type: 'text', text: source }];
}

function hasStructuredContent(value) {
	return splitStructuredContent(value).some(function (part) { return part.type === 'rich'; });
}

function decodeHtmlEntities(value) {
	var named = {
		amp: '&',
		apos: "'",
		gt: '>',
		lt: '<',
		nbsp: ' ',
		quot: '"'
	};
	return String(value || '').replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, function (all, entity) {
		var normalized = String(entity || '').toLowerCase();
		if (named[normalized] != null) return named[normalized];
		if (normalized.charAt(0) !== '#') return all;
		var hexadecimal = normalized.charAt(1) === 'x';
		var number = parseInt(normalized.slice(hexadecimal ? 2 : 1), hexadecimal ? 16 : 10);
		if (!isFinite(number) || number < 0 || number > 0x10ffff) return all;
		try {
			return String.fromCodePoint(number);
		} catch (e) {
			return all;
		}
	});
}

function stripStructuredMarkupToText(value) {
	var source = String(value == null ? '' : value)
		.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi, '')
		.replace(/<style\b[^>]*>[\s\S]*?<\/style\s*>/gi, '')
		.replace(/<!--[\s\S]*?-->/g, '')
		.replace(/<\/?[A-Za-z][A-Za-z0-9:_-]*(?:\s[^<>]*)?$/, '');
	var result = '';
	var cursor = 0;
	while (cursor < source.length) {
		var tag = nextTag(source, cursor);
		if (!tag) {
			result += source.slice(cursor);
			break;
		}
		result += source.slice(cursor, tag.start);
		if (tag.name === 'br') {
			result += '\n';
		} else if (tag.closing && (tag.name === 'td' || tag.name === 'th')) {
			result += ' · ';
		} else if (tag.closing && (isStructuredBlockTag(tag.name) || tag.name === 'li')) {
			result += '\n';
		}
		cursor = tag.end;
	}
	return decodeHtmlEntities(result)
		.replace(/\u00a0/g, ' ')
		.replace(/[ \t]+/g, ' ')
		.replace(/ *\n */g, '\n')
		.replace(/\n{3,}/g, '\n\n')
		.trim();
}

module.exports = {
	classifyStructuredKind: classifyStructuredKind,
	hasStructuredContent: hasStructuredContent,
	isStructuredBlockTag: isStructuredBlockTag,
	readTagAt: readTagAt,
	splitStructuredContent: splitStructuredContent,
	stripStructuredMarkupToText: stripStructuredMarkupToText
};
