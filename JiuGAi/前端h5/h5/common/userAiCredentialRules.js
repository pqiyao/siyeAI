function normalizeCustomApiBaseUrl(value) {
	var text = String(value || '').trim();
	while (text.endsWith('/')) {
		text = text.slice(0, -1);
	}
	if (/\/chat\/completions$/i.test(text)) {
		text = text.slice(0, -'/chat/completions'.length);
		while (text.endsWith('/')) {
			text = text.slice(0, -1);
		}
	}
	if (text && !/\/v1$/i.test(text)) {
		text += '/v1';
	}
	return text;
}

function sameProviderCredentialTarget(currentSource, currentUrl, savedSource, savedUrl) {
	var current = String(currentSource || '').trim().toLowerCase();
	var saved = String(savedSource || '').trim().toLowerCase();
	if (!current || current !== saved) {
		return false;
	}
	if (current !== 'custom') {
		return true;
	}
	return normalizeCustomApiBaseUrl(currentUrl) === normalizeCustomApiBaseUrl(savedUrl);
}

module.exports = {
	normalizeCustomApiBaseUrl: normalizeCustomApiBaseUrl,
	sameProviderCredentialTarget: sameProviderCredentialTarget
};
