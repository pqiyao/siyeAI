/**
 * Chat markdown renderer for H5.
 * The output keeps the original markdown support, but also separates
 * speech / action / thought / narration into distinct blocks so the UI
 * can style them for role-chat messages.
 */
var markedModule = require('marked');
var DOMPurify = null;
try {
	DOMPurify = require('dompurify');
} catch (e) {
	DOMPurify = null;
}

var markedFn = markedModule.marked || markedModule.default || markedModule;
if (markedFn && typeof markedFn.setOptions === 'function') {
	markedFn.setOptions({ gfm: true, breaks: true });
}

var THOUGHT_HINTS = ['心里', '心想', '想着', '想道', '脑海', '内心', '念头', '觉得', '不由得想', '暗想', '默念'];

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

function sanitizeHtml(html) {
	if (!html) {
		return '';
	}
	var raw = String(html);
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
					'thead',
					'tbody',
					'tr',
					'th',
					'td',
					'span',
					'div'
				],
				ALLOWED_ATTR: ['href', 'title', 'class', 'target', 'rel', 'style']
			});
		}
	}
	return raw
		.replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
		.replace(/<iframe[\s\S]*?>[\s\S]*?<\/iframe>/gi, '')
		.replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '');
}

function normalizeText(text) {
	return String(text || '')
		.replace(/\r\n?/g, '\n')
		.replace(/\u00a0/g, ' ')
		.trim();
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
		target.push({
			type: classifyPlainBlock(trimmed),
			text: trimmed
		});
	});
}

function classifyPlainBlock(text) {
	if (!text) {
		return 'narration';
	}
	if (
		(text.charAt(0) === '(' && text.charAt(text.length - 1) === ')') ||
		(text.charAt(0) === '（' && text.charAt(text.length - 1) === '）')
	) {
		return isThoughtText(text) ? 'thought' : 'action';
	}
	return 'narration';
}

function isThoughtText(text) {
	var normalized = normalizeText(text);
	if (!normalized) {
		return false;
	}
	for (var i = 0; i < THOUGHT_HINTS.length; i += 1) {
		if (normalized.indexOf(THOUGHT_HINTS[i]) >= 0) {
			return true;
		}
	}
	return false;
}

function splitSegments(text) {
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
				flushPlain();
				var actionText = source.slice(cursor, actionEnd + 1).trim();
				if (actionText) {
					segments.push({ type: 'action', text: actionText });
				}
				cursor = actionEnd + 1;
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
		if (last && last.type === segment.type) {
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

function renderSegment(segment) {
	var type = segment && segment.type ? segment.type : 'narration';
	var text = segment && segment.text ? segment.text : '';
	var body = '';
	try {
		body = parseMd(text);
	} catch (e) {
		body = '<p>' + escapeHtml(text) + '</p>';
	}
	return (
		'<div class="st-chat-seg st-chat-seg--' +
		type +
		'" style="' +
		segmentInlineStyle(type) +
		'">' +
		body +
		'</div>'
	);
}

function segmentInlineStyle(type) {
	if (type === 'speech') {
		return [
			'color:#ffc2d3',
			'font-weight:700',
			'font-size:1em',
			'line-height:1.72',
			'letter-spacing:0.01em',
			'text-shadow:0 1px 10px rgba(0,0,0,0.24)'
		].join(';');
	}
	if (type === 'thought') {
		return [
			'color:rgba(240,228,255,0.94)',
			'font-weight:400',
			'font-style:italic',
			'font-size:0.92em',
			'line-height:1.86',
			'padding-left:0.3em',
			'text-shadow:0 1px 8px rgba(0,0,0,0.24)'
		].join(';');
	}
	if (type === 'action') {
		return [
			'color:rgba(255,255,255,0.84)',
			'font-weight:400',
			'font-style:italic',
			'font-size:0.94em',
			'line-height:1.84',
			'padding-left:0.28em',
			'text-shadow:0 1px 8px rgba(0,0,0,0.24)'
		].join(';');
	}
	return [
		'color:rgba(255,255,255,0.98)',
		'font-weight:400',
		'font-size:0.97em',
		'line-height:1.82',
		'text-shadow:0 1px 8px rgba(0,0,0,0.22)'
	].join(';');
}

function renderChatMarkdown(text) {
	if (text == null || String(text).trim() === '') {
		return '';
	}
	try {
		var segments = splitSegments(text);
		if (!segments.length) {
			var md = parseMd(text);
			return sanitizeHtml(md);
		}
		var html = '<div class="st-chat-render">' + segments.map(renderSegment).join('') + '</div>';
		return sanitizeHtml(html);
	} catch (e) {
		return escapeHtml(text);
	}
}

module.exports = {
	renderChatMarkdown: renderChatMarkdown
};
