const MAX_SEGMENTS = 20;

function cleanText(value, maxLength) {
	const text = String(value == null ? '' : value).replace(/\r\n?/g, '\n').trim();
	return maxLength > 0 && text.length > maxLength ? text.slice(0, maxLength) : text;
}

function positiveId(value) {
	const id = Math.floor(Number(value) || 0);
	return id > 0 ? id : 0;
}

function normalizeAssistantSegments(raw) {
	if (!Array.isArray(raw)) return [];
	return raw
		.slice(0, MAX_SEGMENTS)
		.map((item, sourceIndex) => {
			if (!item || typeof item !== 'object') return null;
			const type = String(item.type || item.segmentType || '').trim().toUpperCase();
			const content = cleanText(item.content, 0);
			if (!content || (type !== 'CHARACTER' && type !== 'NARRATOR')) return null;
			const memberId = type === 'CHARACTER'
				? positiveId(item.speakerMemberId == null ? item.memberId : item.speakerMemberId)
				: 0;
			if (type === 'CHARACTER' && !memberId) return null;
			const rawIndex = Math.floor(Number(item.index));
			return {
				index: Number.isFinite(rawIndex) && rawIndex >= 0 ? rawIndex : sourceIndex,
				type,
				speakerMemberId: memberId,
				speakerName: type === 'NARRATOR'
					? (cleanText(item.speakerName, 64) || '旁白')
					: cleanText(item.speakerName, 64),
				speakerAvatarUrl: type === 'CHARACTER' ? cleanText(item.speakerAvatarUrl, 512) : '',
				content,
				sourceIndex
			};
		})
		.filter(Boolean)
		.sort((left, right) => left.index - right.index || left.sourceIndex - right.sourceIndex)
		.map((item, index) => ({
			index,
			type: item.type,
			speakerMemberId: item.speakerMemberId,
			speakerName: item.speakerName,
			speakerAvatarUrl: item.speakerAvatarUrl,
			content: item.content
		}));
}

function hasStructuredAssistantSegments(row) {
	if (!row || typeof row !== 'object') return false;
	const role = String(row.role || '').trim().toLowerCase();
	if (role === 'user' || role === 'me' || role === 'human') return false;
	return normalizeAssistantSegments(row.segments).length > 0;
}

function assistantVoiceBlocksFromSegments(raw) {
	const blocks = [];
	normalizeAssistantSegments(raw).forEach((segment) => {
		if (segment.type !== 'CHARACTER' || !segment.speakerMemberId) return;
		const previous = blocks.length ? blocks[blocks.length - 1] : null;
		if (previous && previous.speakerMemberId === segment.speakerMemberId) {
			previous.content += '\n\n' + segment.content;
			return;
		}
		blocks.push({
			content: segment.content,
			speakerMemberId: segment.speakerMemberId
		});
	});
	return blocks;
}

function assistantProtocolDisplayText(raw) {
	let text = String(raw == null ? '' : raw)
		.replace(/<\|(?:speaker:[^|>\r\n]+|narrator)\|>/gi, '\n\n');
	const markerStart = text.lastIndexOf('<');
	if (markerStart >= 0) {
		const candidate = text.slice(markerStart);
		const normalized = candidate.toLowerCase();
		const narratorMarker = '<|narrator|>';
		const speakerPrefix = '<|speaker:';
		const partialFixedMarker = narratorMarker.startsWith(normalized)
			|| speakerPrefix.startsWith(normalized);
		const partialSpeakerMarker = normalized.startsWith(speakerPrefix)
			&& !/[>\r\n]/.test(candidate)
			&& /^[^|]*(?:\|)?$/.test(candidate.slice(speakerPrefix.length));
		if (partialFixedMarker || partialSpeakerMarker) {
			text = text.slice(0, markerStart);
		}
	}
	return text.replace(/^\s+/, '');
}

module.exports = {
	MAX_SEGMENTS,
	normalizeAssistantSegments,
	hasStructuredAssistantSegments,
	assistantVoiceBlocksFromSegments,
	assistantProtocolDisplayText
};
