/**
 * Chat markdown renderer for H5.
 * The output keeps the original markdown support, but also separates
 * speech / action / thought / narration into distinct blocks so the UI
 * can style them closer to the chat reader UI.
 */
var markedModule = null;
var DOMPurify = null;
var structuredContent = require('@/common/chatStructuredContent.js');
// #ifdef H5
markedModule = require('marked');
try {
	DOMPurify = require('dompurify');
} catch (e) {
	DOMPurify = null;
}
// #endif

var markedFn = markedModule && (markedModule.marked || markedModule.default || markedModule);
if (markedFn && typeof markedFn.setOptions === 'function') {
	markedFn.setOptions({ gfm: true, breaks: true });
}

var THOUGHT_HINTS = ['心里', '心想', '想道', '脑海', '内心', '念头', '不由得想', '暗想', '默念', '思绪', '心底'];
var ACTION_HINTS = [
	'说着', '问道', '答道', '说道', '开口', '抬头', '低头', '看向', '望向', '转身', '走近', '靠近',
	'伸手', '抬手', '握住', '抱住', '搂住', '亲吻', '点头', '摇头', '笑了', '笑着', '皱眉', '眨眼',
	'看着', '注视', '移开视线', '呼吸', '停顿', '顿了顿', '坐下', '站起', '起身', '躺下', '靠在',
	'贴近', '退后', '后退', '俯身', '弯腰', '闭眼', '睁眼', '咬唇', '抿唇', '轻声', '小声', '声音'
];
var PLAIN_ACTION_HINTS = ACTION_HINTS.filter(function (item) {
	return ['轻声', '小声', '声音'].indexOf(item) < 0;
});
var EXPLICIT_SEGMENT_NAMES = {
	'台词': 'speech',
	'对话': 'speech',
	'对白': 'speech',
	'speech': 'speech',
	'dialogue': 'speech',
	'动作': 'action',
	'action': 'action',
	'心理': 'thought',
	'内心': 'thought',
	'想法': 'thought',
	'thought': 'thought',
	'旁白': 'narration',
	'叙述': 'narration',
	'narration': 'narration'
};
var NON_SPEAKER_PREFIXES = [
	'时间', '地点', '场景', '动作', '心理', '内心', '旁白', '叙述', '系统', '提示', '备注', '状态', '章节',
	'好感', '好感度', '好感进度', '爱心', '爱心值', '爱心进度', '亲密', '亲密度', '亲密进度',
	'关系', '关系状态', '当前状态', '剧情阶段', '当前阶段', '进度', '生命值', '体力值', '心情值'
];
var READ_MODES = ['original', 'novel', 'speechOnly', 'hideThought', 'softAction'];

var QUOTE_PAIRS = [
	{ open: '“', close: '”' },
	{ open: '"', close: '"' },
	{ open: '「', close: '」' },
	{ open: '『', close: '』' }
];

function parseMd(src) {
	if (markedFn && typeof markedFn.parse === 'function') {
		return markedFn.parse(String(src), { async: false });
	}
	if (typeof markedFn === 'function') {
		return markedFn(String(src));
	}
	return String(src);
}

function sanitizeInlineStyleValue(value) {
	return String(value || '').split(';').map(function (declaration) {
		var separator = declaration.indexOf(':');
		if (separator <= 0) return '';
		var property = declaration.slice(0, separator).trim().toLowerCase();
		var propertyValue = declaration.slice(separator + 1).trim();
		if (!property || !propertyValue || !/^(?:--[\w-]+|[a-z-]+)$/.test(property)) return '';
		if (/^(?:behavior|-moz-binding|content|animation(?:-[a-z-]+)?)$/.test(property)) return '';
		if (property === 'position' && !/^(?:static|relative|absolute)(?:\s*!important)?$/i.test(propertyValue)) return '';
		if (/^(?:margin|margin-top|margin-right|margin-bottom|margin-left)$/.test(property) && /(?:^|[\s(])-\d/.test(propertyValue)) return '';
		if (/(?:expression\s*\(|javascript\s*:|@import|url\s*\()/i.test(propertyValue)) return '';
		if (/(?:^|[^a-z])\d+(?:\.\d+)?(?:vw|vh|vmin|vmax)(?:[^a-z]|$)/i.test(propertyValue)) return '';
		return property + ':' + propertyValue;
	}).filter(Boolean).join(';');
}

function sanitizeInlineStyles(html) {
	return String(html || '').replace(/\sstyle\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))/gi, function (all, wrapped, doubleQuoted, singleQuoted, unquoted) {
		var clean = sanitizeInlineStyleValue(doubleQuoted != null ? doubleQuoted : singleQuoted != null ? singleQuoted : unquoted);
		if (!clean) return '';
		return ' style="' + clean.replace(/"/g, '&quot;') + '"';
	});
}

function sanitizeHtml(html) {
	if (!html) {
		return '';
	}
	var raw = sanitizeInlineStyles(String(html));
	if (DOMPurify && typeof DOMPurify.sanitize === 'function') {
		if (typeof window !== 'undefined' && window.document) {
			return DOMPurify.sanitize(raw, {
				ALLOWED_TAGS: [
					'p',
					'br',
					'strong',
					'em',
					'u',
					's',
					'h1',
					'h2',
					'h3',
					'h4',
					'ul',
					'ol',
					'li',
					'blockquote',
					'code',
					'pre',
					'a',
					'hr',
					'table',
					'caption',
					'colgroup',
					'col',
					'thead',
					'tbody',
					'tr',
					'th',
					'td',
					'span',
					'div',
					'details',
					'summary',
					'section',
					'article',
					'aside',
					'fieldset',
					'legend',
					'dl',
					'dt',
					'dd',
					'small',
					'sub',
					'sup',
					'mark',
					'progress',
					'meter'
				],
				ALLOWED_ATTR: [
					'href', 'title', 'class', 'target', 'rel', 'style',
					'open', 'value', 'max', 'min', 'low', 'high', 'optimum',
					'colspan', 'rowspan', 'role', 'aria-label'
				]
			});
		}
	}
	if (typeof window !== 'undefined' && window.document) {
		return escapeHtml(raw);
	}
	return raw
		.replace(/<(script|style|iframe|object)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '')
		.replace(/<(?:script|style|iframe|object|embed|link|meta|base)\b[^>]*\/?\s*>/gi, '')
		.replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
		.replace(/\s(?:href|src)\s*=\s*("\s*javascript:[^"]*"|'\s*javascript:[^']*'|javascript:[^\s>]+)/gi, '');
}

function normalizeText(text) {
	return String(text || '')
		.replace(/\r\n?/g, '\n')
		.replace(/\u00a0/g, ' ')
		.trim();
}

function structuredTextForSemantics(text) {
	return structuredContent.stripStructuredMarkupToText(text).replace(/\n+/g, '\n\n');
}

function escapeHtml(text) {
	return String(text || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;');
}

function indexOfClosingQuote(text, fromIndex, closeChar) {
	for (var i = fromIndex; i < text.length; i += 1) {
		if (text.charAt(i) !== closeChar) {
			continue;
		}
		if (i > fromIndex && text.charAt(i - 1) === '\\') {
			continue;
		}
		return i;
	}
	return -1;
}

function indexOfActionEnd(text, fromIndex) {
	for (var i = fromIndex; i < text.length; i += 1) {
		if (text.charAt(i) !== '*') {
			continue;
		}
		if (i === fromIndex) {
			continue;
		}
		if (text.charAt(i - 1) === '\\') {
			continue;
		}
		return i;
	}
	return -1;
}

function indexOfClosingBracket(text, fromIndex, closeChar) {
	var depth = 1;
	var openChar = closeChar === '）' ? '（' : '(';
	for (var i = fromIndex; i < text.length; i += 1) {
		var current = text.charAt(i);
		if (current === openChar) {
			depth += 1;
			continue;
		}
		if (current === closeChar) {
			depth -= 1;
			if (depth === 0) {
				return i;
			}
		}
	}
	return -1;
}

function splitPlainSentences(text) {
	var source = normalizeText(text);
	if (!source || source.indexOf('\n') >= 0 || source.length > 240) {
		return source ? [source] : [];
	}
	var matches = source.match(/[^。！？!?；;]+[。！？!?；;]?/g);
	if (!matches || matches.length <= 1) {
		return [source];
	}
	return matches.map(function (item) {
		return normalizeText(item);
	}).filter(Boolean);
}

function pushNarrationSegments(target, rawText) {
	var normalized = normalizeText(rawText);
	if (!normalized) {
		return;
	}
	normalized.split(/\n{2,}/).forEach(function (block) {
		var trimmed = normalizeText(block);
		if (!trimmed) {
			return;
		}
		var explicit = parseExplicitSegment(trimmed);
		if (explicit) {
			target.push({ type: explicit.type, text: explicit.text });
			return;
		}
		splitPlainSentences(trimmed).forEach(function (sentence) {
			target.push({
				type: classifyPlainBlock(sentence),
				text: sentence
			});
		});
	});
}

function parseExplicitSegment(text) {
	var source = normalizeText(text);
	if (!source) {
		return null;
	}
	var match = source.match(/^\s*(?:\[|【)?\s*([\u4e00-\u9fa5A-Za-z]+)\s*(?:\]|】)?\s*[：:]\s*([\s\S]+)$/);
	if (!match) {
		return null;
	}
	var type = EXPLICIT_SEGMENT_NAMES[String(match[1] || '').toLowerCase()];
	var body = normalizeText(match[2]);
	return type && body ? { type: type, text: body } : null;
}

function containsHint(text, hints) {
	var source = normalizeText(text);
	for (var i = 0; i < hints.length; i += 1) {
		if (source.indexOf(hints[i]) >= 0) {
			return true;
		}
	}
	return false;
}

function isLikelySpeakerLine(text) {
	var match = normalizeText(text).match(/^([^：:\n]{1,12})[：:]\s*(\S[\s\S]*)$/);
	if (!match) {
		return false;
	}
	var prefix = normalizeText(match[1]).replace(/^[\[【]|[\]】]$/g, '');
	if (!prefix || /[。！？!?；;，,]/.test(prefix)) {
		return false;
	}
	for (var i = 0; i < NON_SPEAKER_PREFIXES.length; i += 1) {
		if (prefix === NON_SPEAKER_PREFIXES[i]) {
			return false;
		}
	}
	return true;
}

function classifyBracketText(text) {
	if (isThoughtText(text)) {
		return 'thought';
	}
	return 'action';
}

function isLikelyRoleplayAction(text) {
	var source = normalizeText(text).replace(/^\*|\*$/g, '');
	if (!source) {
		return false;
	}
	return containsHint(source, ACTION_HINTS) || /^[她他它我你][\s\S]{1,80}$/.test(source);
}

function isLikelyPlainAction(text) {
	var source = normalizeText(text);
	if (!source || source.length > 160 || /^[#>`\-+]|^\d+[.)、]/.test(source)) {
		return false;
	}
	var firstHintIndex = -1;
	for (var i = 0; i < PLAIN_ACTION_HINTS.length; i += 1) {
		var hintIndex = source.indexOf(PLAIN_ACTION_HINTS[i]);
		if (hintIndex >= 0 && (firstHintIndex < 0 || hintIndex < firstHintIndex)) {
			firstHintIndex = hintIndex;
		}
	}
	if (firstHintIndex < 0 || firstHintIndex > 24) {
		return false;
	}
	if (/^(?:时间|地点|场景|章节|系统|提示|备注|状态)[：:]/.test(source)) {
		return false;
	}
	return /^(?:[（(]?[她他它我你]|[\u4e00-\u9fa5A-Za-z0-9·]{1,12})(?:[，,\s]|轻轻|缓缓|忽然|突然|微微|慢慢|悄悄|猛地|不由|终于|随即|便|就|正|又|仍|已经|渐渐|\u7684)?/.test(source);
}

function classifyPlainBlock(text) {
	if (!text) {
		return 'narration';
	}
	var explicit = parseExplicitSegment(text);
	if (explicit) {
		return explicit.type;
	}
	if (
		(text.charAt(0) === '(' && text.charAt(text.length - 1) === ')') ||
		(text.charAt(0) === '（' && text.charAt(text.length - 1) === '）')
	) {
		return classifyBracketText(text);
	}
	if (/^\*[^*\n]{2,}\*$/.test(text) && isLikelyRoleplayAction(text)) {
		return 'action';
	}
	if (/^[“"「『].+[”"」』]$/.test(text) || /[“"「『][^”"」』]{2,}[”"」』]/.test(text)) {
		return 'speech';
	}
	if (isLikelySpeakerLine(text)) {
		return 'speech';
	}
	if (isThoughtText(text) && /^(?:她|他|我|你)?(?:的)?(?:心里|内心|心底|脑海|思绪)|(?:心想|想道|暗想|默念)/.test(text)) {
		return 'thought';
	}
	if (isLikelyPlainAction(text)) {
		return 'action';
	}
	return 'narration';
}

function isThoughtText(text) {
	var normalized = normalizeText(text);
	if (!normalized) {
		return false;
	}
	return containsHint(normalized, THOUGHT_HINTS);
}

function splitPlainSegments(text) {
	var source = normalizeText(text);
	if (!source) {
		return [];
	}

	var segments = [];
	var cursor = 0;
	var plainBuffer = '';

	function flushPlain() {
		if (!plainBuffer) {
			return;
		}
		pushNarrationSegments(segments, plainBuffer);
		plainBuffer = '';
	}

	while (cursor < source.length) {
		var current = source.charAt(cursor);

		if (current === '*') {
			var actionEnd = indexOfActionEnd(source, cursor + 1);
			if (actionEnd > cursor + 1) {
				var actionText = source.slice(cursor, actionEnd + 1).trim();
				if (actionText && isLikelyRoleplayAction(actionText)) {
					flushPlain();
					segments.push({ type: 'action', text: actionText });
				} else {
					plainBuffer += source.slice(cursor, actionEnd + 1);
				}
				cursor = actionEnd + 1;
				continue;
			}
		}

		if (current === '(' || current === '（') {
			var bracketEnd = indexOfClosingBracket(source, cursor + 1, current === '（' ? '）' : ')');
			if (bracketEnd > cursor + 1) {
				flushPlain();
				var bracketText = source.slice(cursor, bracketEnd + 1).trim();
				if (bracketText) {
					segments.push({ type: classifyBracketText(bracketText), text: bracketText });
				}
				cursor = bracketEnd + 1;
				continue;
			}
		}

		var matchedPair = null;
		for (var i = 0; i < QUOTE_PAIRS.length; i += 1) {
			if (current === QUOTE_PAIRS[i].open) {
				matchedPair = QUOTE_PAIRS[i];
				break;
			}
		}
		if (matchedPair) {
			var quoteEnd = indexOfClosingQuote(source, cursor + 1, matchedPair.close);
			if (quoteEnd > cursor + 1) {
				flushPlain();
				var speechText = source.slice(cursor, quoteEnd + 1).trim();
				if (speechText) {
					segments.push({ type: 'speech', text: speechText });
				}
				cursor = quoteEnd + 1;
				continue;
			}
		}

		plainBuffer += current;
		cursor += 1;
	}

	flushPlain();
	return mergeAdjacentSegments(segments);
}

function splitSegments(text) {
	var options = arguments.length > 1 && arguments[1] && typeof arguments[1] === 'object' ? arguments[1] : {};
	var source = normalizeText(text);
	if (!source) {
		return [];
	}
	var annotated = semanticSegmentsForText(String(text == null ? '' : text), options.semantic);
	if (annotated) {
		return annotated;
	}
	var parts = structuredContent.splitStructuredContent(source);
	var hasStructuredPart = parts.some(function (part) { return part && part.type !== 'text'; });
	if (!hasStructuredPart) {
		return splitPlainSegments(source);
	}
	var segments = [];
	parts.forEach(function (part) {
		if (!part || !normalizeText(part.text)) return;
		if (part.type === 'discard') return;
		if (part.type === 'pending') {
			segments.push({ type: 'rich-pending', text: part.text });
			return;
		}
		if (part.type === 'rich') {
			segments.push({ type: part.kind === 'status' ? 'rich' : 'rich-content', text: part.text });
			return;
		}
		segments.push.apply(segments, splitPlainSegments(part.text));
	});
	return mergeAdjacentSegments(segments);
}

function semanticTextFingerprint(text) {
	var source = String(text == null ? '' : text);
	var hash = 0x811c9dc5;
	for (var i = 0; i < source.length; i += 1) {
		var codeUnit = source.charCodeAt(i);
		hash ^= codeUnit & 0xff;
		hash = Math.imul(hash, 0x01000193);
		hash ^= (codeUnit >>> 8) & 0xff;
		hash = Math.imul(hash, 0x01000193);
	}
	return ('00000000' + (hash >>> 0).toString(16)).slice(-8);
}

function validateSemanticAnnotation(text, semantic) {
	var source = String(text == null ? '' : text);
	if (!source || !semantic || typeof semantic !== 'object' || Number(semantic.schemaVersion) !== 1) return null;
	if (String(semantic.textFingerprint || '').toLowerCase() !== semanticTextFingerprint(source)) return null;
	var list = Array.isArray(semantic.segments) ? semantic.segments : [];
	if (!list.length) return null;
	var allowed = { speech: true, action: true, thought: true, narration: true };
	var cursor = 0;
	var normalized = [];
	for (var i = 0; i < list.length; i += 1) {
		var item = list[i] || {};
		var type = String(item.type || '').toLowerCase();
		var start = Number(item.start);
		var end = Number(item.end);
		if (!allowed[type] || !Number.isInteger(start) || !Number.isInteger(end)) return null;
		if (start !== cursor || end <= start || end > source.length) return null;
		normalized.push({
			type: type,
			start: start,
			end: end,
			confidence: Math.max(0, Math.min(1, Number(item.confidence) || 0))
		});
		cursor = end;
	}
	return cursor === source.length ? normalized : null;
}

function semanticSegmentsForText(text, semantic) {
	var source = String(text == null ? '' : text);
	var structuredParts = structuredContent.splitStructuredContent(source);
	if (structuredParts.some(function (part) { return part && part.type !== 'text'; })) return null;
	var validated = validateSemanticAnnotation(source, semantic);
	if (!validated) return null;
	return validated.map(function (item) {
		return {
			type: item.type,
			text: source.slice(item.start, item.end),
			confidence: item.confidence,
			start: item.start,
			end: item.end
		};
	});
}

function sliceSemanticAnnotation(text, semantic, start, end) {
	var source = String(text == null ? '' : text);
	var validated = validateSemanticAnnotation(source, semantic);
	var safeStart = Number(start);
	var safeEnd = Number(end);
	if (!validated || !Number.isInteger(safeStart) || !Number.isInteger(safeEnd) || safeStart < 0 || safeEnd <= safeStart || safeEnd > source.length) return null;
	var slice = source.slice(safeStart, safeEnd);
	var segments = [];
	validated.forEach(function (item) {
		var overlapStart = Math.max(safeStart, item.start);
		var overlapEnd = Math.min(safeEnd, item.end);
		if (overlapEnd <= overlapStart) return;
		segments.push({
			type: item.type,
			start: overlapStart - safeStart,
			end: overlapEnd - safeStart,
			confidence: item.confidence
		});
	});
	if (!segments.length || segments[0].start !== 0 || segments[segments.length - 1].end !== slice.length) return null;
	return {
		schemaVersion: 1,
		classifierVersion: semantic.classifierVersion || '',
		textFingerprint: semanticTextFingerprint(slice),
		confidence: semantic.confidence,
		segments: segments
	};
}

function mergeAdjacentSegments(segments) {
	if (!Array.isArray(segments) || !segments.length) {
		return [];
	}
	var merged = [];
	segments.forEach(function (segment) {
		if (!segment || !segment.text) {
			return;
		}
		var trimmed = normalizeText(segment.text);
		if (!trimmed) {
			return;
		}
		var last = merged.length ? merged[merged.length - 1] : null;
		if (last && last.type === segment.type && ['rich', 'rich-content', 'rich-pending'].indexOf(segment.type) < 0) {
			last.text += '\n\n' + trimmed;
			return;
		}
		merged.push({
			type: segment.type || 'narration',
			text: trimmed
		});
	});
	return merged;
}

function normalizeRenderOptions(options) {
	var opts = options && typeof options === 'object' ? options : {};
	var colors = opts.segmentColors && typeof opts.segmentColors === 'object' ? opts.segmentColors : {};
	var weights = opts.segmentWeights && typeof opts.segmentWeights === 'object' ? opts.segmentWeights : {};
	return {
		semantic: opts.semantic && typeof opts.semantic === 'object' ? opts.semantic : null,
		readMode: READ_MODES.indexOf(opts.readMode) >= 0 ? opts.readMode : 'original',
		showSegmentLabels: opts.showSegmentLabels === true,
		replySplitMode: ['none', 'bubble'].indexOf(opts.replySplitMode) >= 0 ? opts.replySplitMode : 'none',
		segmentColors: {
			speech: normalizeSegmentColor(colors.speech, '#f4b8cf'),
			action: normalizeSegmentColor(colors.action, '#bfe8d2'),
			thought: normalizeSegmentColor(colors.thought, '#d4caef'),
			narration: normalizeSegmentColor(colors.narration, '#f2f4f7')
		},
		segmentWeights: {
			speech: normalizeSegmentWeight(weights.speech, 700),
			action: normalizeSegmentWeight(weights.action, 500),
			thought: normalizeSegmentWeight(weights.thought, 500),
			narration: normalizeSegmentWeight(weights.narration, 500)
		},
		thoughtItalic: opts.thoughtItalic !== false
	};
}

function normalizeSegmentColor(value, fallback) {
	var color = String(value == null ? '' : value).trim();
	return /^#[0-9a-fA-F]{6}$/.test(color) ? color.toLowerCase() : fallback;
}

function normalizeSegmentWeight(value, fallback) {
	var number = Number(value);
	if (!isFinite(number)) return fallback;
	return Math.max(300, Math.min(700, Math.round(number / 100) * 100));
}

function colorWithAlpha(hex, alpha) {
	var value = normalizeSegmentColor(hex, '#f2f4f7').slice(1);
	return 'rgba(' + parseInt(value.slice(0, 2), 16) + ',' + parseInt(value.slice(2, 4), 16) + ',' + parseInt(value.slice(4, 6), 16) + ',' + alpha + ')';
}

function applyReadModeSegments(segments, sourceText, options) {
	var opts = normalizeRenderOptions(options);
	var list = Array.isArray(segments) ? segments.slice() : [];
	if (!list.length) {
		var fallbackText = normalizeText(sourceText);
		list = fallbackText ? [{ type: 'narration', text: fallbackText }] : [];
	}
	if (opts.readMode === 'speechOnly') {
		var speechOnlySegments = [];
		list.forEach(function (item) {
			if (!item || !normalizeText(item.text)) return;
			if (item.type === 'speech' || item.type === 'rich') {
				speechOnlySegments.push(item);
				return;
			}
			if (item.type === 'rich-content') {
				var plainText = structuredTextForSemantics(item.text);
				speechOnlySegments.push.apply(speechOnlySegments, splitPlainSegments(plainText).filter(function (segment) {
					return segment && segment.type === 'speech';
				}));
			}
		});
		return speechOnlySegments;
	}
	if (opts.readMode === 'hideThought') {
		return list.filter(function (item) {
			return !item || item.type !== 'thought';
		});
	}
	return list;
}

function readModeSegmentClass(type, options) {
	var opts = normalizeRenderOptions(options);
	if (opts.readMode === 'softAction' && (type === 'action' || type === 'narration')) {
		return ' st-chat-seg--soft';
	}
	return '';
}

function renderClassName(options) {
	var opts = normalizeRenderOptions(options);
	return 'st-chat-render st-chat-render--' + opts.readMode + ' st-chat-render--reply-' + opts.replySplitMode;
}

function segmentLabel(type) {
	if (type === 'speech') return '台词';
	if (type === 'action') return '动作';
	if (type === 'thought') return '心理';
	if (type === 'rich') return '状态';
	return '旁白';
}

function renderSegmentLabel(type, options) {
	var opts = normalizeRenderOptions(options);
	if (!opts.showSegmentLabels) {
		return '';
	}
	return '<span class="st-chat-seg-label-h5 st-chat-seg-label-h5--' + type + '" style="' + segmentLabelInlineStyle(type, opts) + '">' + segmentLabel(type) + '</span>';
}


function segmentLabelInlineStyle(type, options) {
	var opts = normalizeRenderOptions(options);
	var color = opts.segmentColors[type] || opts.segmentColors.narration;
	return [
		'display:inline-flex',
		'flex:0 0 auto',
		'align-items:center',
		'justify-content:center',
		'min-width:29px',
		'height:17px',
		'margin:2px 6px 0 0',
		'padding:0 5px',
		'box-sizing:border-box',
		'border:1px solid ' + colorWithAlpha(color, 0.34),
		'border-radius:999px',
		'background:' + colorWithAlpha(color, 0.1),
		'color:' + color,
		'font-size:10.5px',
		'font-weight:600',
		'line-height:16px',
		'white-space:nowrap'
	].join(';');
}

function renderSegment(segment, options) {
	var type = segment && segment.type ? segment.type : 'narration';
	var text = segment && segment.text ? segment.text : '';
	if (type === 'rich-pending') {
		var pendingText = structuredContent.stripStructuredMarkupToText(text);
		return pendingText
			? '<div class="st-chat-rich-pending" style="display:block;width:100%;min-width:0;box-sizing:border-box;white-space:pre-wrap">' + escapeHtml(pendingText) + '</div>'
			: '';
	}
	if (type === 'rich' || type === 'rich-content') {
		var richBody = '';
		try {
			richBody = parseMd(text);
		} catch (e) {
			richBody = '<p>' + escapeHtml(text) + '</p>';
		}
		var richClass = type === 'rich' ? ' st-chat-rich-block--status' : ' st-chat-rich-block--content';
		return '<div class="st-chat-rich-block' + richClass + '" style="display:block;position:relative;contain:layout paint;isolation:isolate;width:100%;max-width:100%;min-width:0;box-sizing:border-box;overflow-x:auto;overflow-y:hidden;white-space:normal;color:inherit;-webkit-text-fill-color:currentColor;font-style:normal;font-weight:400">' + richBody + '</div>';
	}
	var opts = normalizeRenderOptions(options);
	var segmentColor = opts.segmentColors[type] || opts.segmentColors.narration;
	var body = '';
	try {
		body = parseMd(text);
	} catch (e) {
		body = '<p>' + escapeHtml(text) + '</p>';
	}
	return (
		'<div class="st-chat-seg st-chat-seg--' +
		type +
		readModeSegmentClass(type, options) +
		(opts.showSegmentLabels ? ' st-chat-seg--has-label' : '') +
		'" style="' +
		segmentInlineStyle(type, opts) +
		'">' +
		'<div class="st-chat-seg-line-h5' + (opts.showSegmentLabels ? ' st-chat-seg-line-h5--labeled' : '') + '" style="' + segmentLineInlineStyle(opts.showSegmentLabels) + '">' +
		renderSegmentLabel(type, options) +
		'<div class="st-chat-seg-body-h5" style="display:block;flex:1;min-width:0;--st-chat-segment-color:' + segmentColor + ';color:' + segmentColor + '!important;-webkit-text-fill-color:' + segmentColor + '!important">' + body + '</div>' +
		'</div>' +
		'</div>'
	);
}

function segmentLineInlineStyle(showLabel) {
	return showLabel
		? 'display:flex;width:100%;min-width:0;align-items:flex-start;box-sizing:border-box'
		: 'display:block;width:100%;min-width:0;box-sizing:border-box';
}

function segmentInlineStyle(type, options) {
	var opts = normalizeRenderOptions(options);
	var color = opts.segmentColors[type] || opts.segmentColors.narration;
	var fontWeight = opts.segmentWeights[type] || opts.segmentWeights.narration;
	var fontStyle = type === 'thought' && opts.thoughtItalic ? 'italic' : 'normal';
	var opacity = opts.readMode === 'softAction' && (type === 'action' || type === 'narration') ? '0.58' : '1';
	return [
		'display:block',
		'margin:0',
		'padding:0',
		'box-sizing:border-box',
		'border-radius:0',
		'border-left:0',
		'background:transparent',
		'--st-chat-segment-color:' + color,
		'color:' + color,
		'font-weight:' + fontWeight,
		'font-style:' + fontStyle,
		'opacity:' + opacity,
		'font-size:1em',
		'line-height:inherit',
		'letter-spacing:0',
		'text-shadow:none'
	].join(';');
}

function renderChatMarkdown(text, options) {
	if (text == null || String(text).trim() === '') {
		return '';
	}
	try {
		var opts = normalizeRenderOptions(options);
		var segments = splitSegments(text, opts);
		segments = applyReadModeSegments(segments, text, opts);
		if (!segments.length) {
			if (opts.readMode === 'speechOnly') {
				return '<div class="' + renderClassName(opts) + ' st-chat-render--empty" aria-hidden="true"></div>';
			}
			var md = parseMd(text);
			return sanitizeHtml(md);
		}
		var html = '<div class="' + renderClassName(options) + '">' + segments.map(function (segment) {
			return renderSegment(segment, options);
		}).join('') + '</div>';
		return sanitizeHtml(html);
	} catch (e) {
		return escapeHtml(text);
	}
}

function extractChatSpeechSegments(text, options) {
	var speechSegments = [];
	splitSegments(text, options).forEach(function (item) {
		if (!item || !normalizeText(item.text)) return;
		if (item.type === 'speech') {
			speechSegments.push(item);
			return;
		}
		if (item.type === 'rich-content') {
			var plainText = structuredTextForSemantics(item.text);
			speechSegments.push.apply(speechSegments, splitPlainSegments(plainText).filter(function (segment) {
				return segment && segment.type === 'speech';
			}));
		}
	});
	return speechSegments;
}

module.exports = {
	extractChatSpeechSegments: extractChatSpeechSegments,
	semanticTextFingerprint: semanticTextFingerprint,
	sliceSemanticAnnotation: sliceSemanticAnnotation,
	validateSemanticAnnotation: validateSemanticAnnotation,
	structuredTextForSemantics: structuredTextForSemantics,
	renderChatMarkdown: renderChatMarkdown,
	splitChatSegments: splitSegments,
	classifyChatBlock: classifyPlainBlock
};
